package com.wx.fbsir.business.board.service;

import com.wx.fbsir.business.board.domain.BoardUsageBudget;
import com.wx.fbsir.business.board.domain.BoardUsageOperation;
import com.wx.fbsir.business.board.domain.BoardUsageOperationPolicyReceipt;
import com.wx.fbsir.business.board.dto.BoardEntitlementSnapshot;
import com.wx.fbsir.business.board.dto.BoardMeetingReservationRequest;
import com.wx.fbsir.business.board.dto.BoardMeetingReservationView;
import com.wx.fbsir.business.board.mapper.IndependentBoardMapper;
import com.wx.fbsir.business.board.plan.domain.BoardPlanPolicyName;
import com.wx.fbsir.business.board.plan.domain.BoardPlanPolicySnapshot;
import com.wx.fbsir.business.board.plan.service.BoardPlanPolicyDigest;
import com.wx.fbsir.common.exception.ServiceException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Transactional execution boundary for meeting reservation claims and replay reads. */
@Service
public class IndependentBoardMeetingTransactionService {
    private final IndependentBoardMapper mapper;
    private final IndependentBoardEntitlementService entitlementService;
    private final Clock clock;

    @Autowired
    public IndependentBoardMeetingTransactionService(
            IndependentBoardMapper mapper,
            IndependentBoardEntitlementService entitlementService) {
        this(mapper, entitlementService,
                Clock.system(IndependentBoardEntitlementService.INITIAL_TENANT_ZONE));
    }

    IndependentBoardMeetingTransactionService(
            IndependentBoardMapper mapper,
            IndependentBoardEntitlementService entitlementService,
            Clock clock) {
        this.mapper = mapper;
        this.entitlementService = entitlementService;
        this.clock = clock;
    }

    /**
     * Claims a new operation with a plain INSERT and reserves its budget in one transaction.
     * DuplicateKeyException intentionally escapes so Spring rolls this transaction back before
     * the coordinator opens a separate replay transaction.
     */
    @Transactional(rollbackFor = Exception.class)
    public BoardMeetingReservationView reserveFresh(
            BoardMeetingReservationRequest request, Long authenticatedUserId) {
        BoardEntitlementSnapshot snapshot = entitlementService.getSnapshotForReservation(
                request.tenantId(), authenticatedUserId);
        BoardPlanPolicySnapshot policy = mapper.selectCurrentPlanPolicy(
                IndependentBoardEntitlementService.PRODUCT_CODE,
                snapshot.effectivePlanCode());
        validateCurrentPolicy(snapshot, policy);
        validateMeetingShape(request, policy);
        LocalDate today = LocalDate.now(clock);
        BoardUsageOperation pending = operation(request, authenticatedUserId, snapshot, today);

        if (mapper.insertOperation(pending) != 1) {
            throw new ServiceException("MEETING_OPERATION_WRITE_FAILED", 500);
        }

        mapper.prepareUsageBudget(
                request.tenantId(), snapshot.memberId(), IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.MEETING_METRIC, today, policy.getDailyMeetingLimit());
        if (mapper.reserveOneMeeting(
                request.tenantId(), snapshot.memberId(), IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.MEETING_METRIC, today, policy.getDailyMeetingLimit()) != 1) {
            throw new ServiceException("DAILY_MEETING_QUOTA_EXHAUSTED", 409);
        }
        BoardUsageBudget budget = mapper.selectUsageBudget(
                request.tenantId(), snapshot.memberId(), IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.MEETING_METRIC, today);
        if (budget == null) {
            throw new ServiceException("USAGE_BUDGET_READBACK_FAILED", 500);
        }
        int remaining = Math.max(0, policy.getDailyMeetingLimit()
                - nullSafe(budget.getUsedCount()) - nullSafe(budget.getReservedCount()));
        if (mapper.markOperationReserved(request.tenantId(), request.operationId(), remaining) != 1) {
            throw new ServiceException("MEETING_RESERVATION_FINALIZE_FAILED", 500);
        }
        if (mapper.insertUsageOperationPolicyReceipt(lineage(pending, policy)) != 1) {
            throw new ServiceException("MEETING_POLICY_LINEAGE_WRITE_FAILED", 500);
        }
        pending.setStatus("RESERVED");
        pending.setRemainingCount(remaining);
        return toView(pending);
    }

    /**
     * Returns an exact committed replay before any current membership, entitlement or policy read.
     * A missing operation is the only condition that permits the coordinator to attempt a new claim.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public BoardMeetingReservationView replayCommittedIfPresent(
            BoardMeetingReservationRequest request, Long authenticatedUserId) {
        return replayCommittedInternal(request, authenticatedUserId, false);
    }

    /**
     * Reads a committed winner only after the failed claim transaction has been rolled back.
     * REQUIRES_NEW makes the fresh current-read boundary explicit if a caller later gains a
     * transaction of its own.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public BoardMeetingReservationView replayCommitted(
            BoardMeetingReservationRequest request, Long authenticatedUserId) {
        return replayCommittedInternal(request, authenticatedUserId, true);
    }

    private BoardMeetingReservationView replayCommittedInternal(
            BoardMeetingReservationRequest request,
            Long authenticatedUserId,
            boolean required) {
        BoardUsageOperation existing = mapper.selectOperationForUpdate(
                request.tenantId(), request.operationId());
        if (existing == null) {
            if (required) {
                throw new ServiceException("IDEMPOTENCY_COLLISION_READBACK_FAILED", 409);
            }
            return null;
        }
        if (!Objects.equals(existing.getOperationId(), request.operationId())) {
            throw new ServiceException("IDEMPOTENCY_DIGEST_CONFLICT", 409);
        }
        BoardUsageOperationPolicyReceipt policy = mapper.selectOperationPolicyReceipt(
                request.tenantId(), request.operationId());
        validateHistoricalPolicyLineage(existing, policy);
        return verifyIdempotentReplay(existing, request, authenticatedUserId);
    }

    private BoardUsageOperation operation(
            BoardMeetingReservationRequest request,
            Long authenticatedUserId,
            BoardEntitlementSnapshot snapshot,
            LocalDate bucketDate) {
        BoardUsageOperation pending = new BoardUsageOperation();
        pending.setOperationId(request.operationId());
        pending.setRequestDigest(reservationDigest(request, authenticatedUserId));
        pending.setTenantId(request.tenantId());
        pending.setMemberId(snapshot.memberId());
        pending.setUserId(authenticatedUserId);
        pending.setProductCode(IndependentBoardEntitlementService.PRODUCT_CODE);
        pending.setMetricCode(IndependentBoardEntitlementService.MEETING_METRIC);
        pending.setBucketDate(bucketDate);
        pending.setUnits(1);
        pending.setStatus("PENDING");
        pending.setEffectivePlanCode(snapshot.effectivePlanCode());
        pending.setAgendaCount(request.agendaCount());
        pending.setSeatCount(request.seatCount());
        pending.setRemainingCount(0);
        return pending;
    }

    private BoardMeetingReservationView verifyIdempotentReplay(
            BoardUsageOperation existing,
            BoardMeetingReservationRequest request,
            Long authenticatedUserId) {
        boolean sameIdentity = Objects.equals(existing.getTenantId(), request.tenantId())
                && Objects.equals(existing.getOperationId(), request.operationId())
                && Objects.equals(existing.getUserId(), authenticatedUserId)
                && Objects.equals(existing.getProductCode(), IndependentBoardEntitlementService.PRODUCT_CODE)
                && Objects.equals(existing.getMetricCode(), IndependentBoardEntitlementService.MEETING_METRIC)
                && BoardDigest.equal(existing.getRequestDigest(),
                        reservationDigest(request, authenticatedUserId));
        if (!sameIdentity) {
            throw new ServiceException("IDEMPOTENCY_DIGEST_CONFLICT", 409);
        }
        if (!Objects.equals(existing.getStatus(), "RESERVED")) {
            throw new ServiceException("IDEMPOTENT_OPERATION_NOT_FINAL", 409);
        }
        return toView(existing);
    }

    private void validateHistoricalPolicyLineage(
            BoardUsageOperation operation,
            BoardUsageOperationPolicyReceipt policy) {
        boolean exact = policy != null
                && Objects.equals(policy.getTenantId(), operation.getTenantId())
                && Objects.equals(policy.getOperationId(), operation.getOperationId())
                && Objects.equals(policy.getProductCode(), operation.getProductCode())
                && Objects.equals(policy.getPlanCode(), operation.getEffectivePlanCode())
                && validPolicyReceiptId(policy.getPolicyReceiptId())
                && policy.getPolicyVersion() != null
                && policy.getPolicyVersion() > 0L
                && policy.getPolicyDigest() != null
                && policy.getPolicyDigest().matches("[0-9a-f]{64}");
        if (!exact) {
            throw new ServiceException("MEETING_POLICY_LINEAGE_READBACK_FAILED", 500);
        }
    }

    private void validateMeetingShape(
            BoardMeetingReservationRequest request, BoardPlanPolicySnapshot policy) {
        if (request.agendaCount() > policy.getAgendaLimit()) {
            throw new ServiceException("AGENDA_LIMIT_EXCEEDED", 400);
        }
        if (request.seatCount() > BoardMeetingReservationRequest.INITIAL_SAFETY_MAX_SEAT_COUNT) {
            throw new ServiceException("SEAT_SAFETY_LIMIT_EXCEEDED", 400);
        }
        if (policy.getSeatLimit() != null && request.seatCount() > policy.getSeatLimit()) {
            throw new ServiceException("SEAT_LIMIT_EXCEEDED", 400);
        }
    }

    private void validateCurrentPolicy(
            BoardEntitlementSnapshot entitlement,
            BoardPlanPolicySnapshot policy) {
        String planCode = entitlement.effectivePlanCode();
        Boolean expectedVip = IndependentBoardEntitlementService.FREE_PLAN.equals(planCode)
                ? Boolean.FALSE
                : IndependentBoardEntitlementService.VIP_PLAN.equals(planCode)
                    ? Boolean.TRUE : null;
        boolean exact = policy != null
                && expectedVip != null
                && Objects.equals(policy.getIdentityProductCode(),
                        IndependentBoardEntitlementService.PRODUCT_CODE)
                && Objects.equals(policy.getIdentityProductCode(), policy.getProductCode())
                && Objects.equals(policy.getIdentityPlanCode(), planCode)
                && Objects.equals(policy.getIdentityPlanCode(), policy.getPlanCode())
                && policy.getIdentityVip() != null
                && policy.getVip() != null
                && Objects.equals(policy.getIdentityVip(), expectedVip)
                && Objects.equals(policy.getIdentityVip(), policy.getVip())
                && policy.getIdentityConnectorRequired() != null
                && policy.getConnectorRequired() != null
                && Objects.equals(policy.getIdentityConnectorRequired(), expectedVip)
                && Objects.equals(
                        policy.getIdentityConnectorRequired(), policy.getConnectorRequired())
                && Objects.equals(policy.getIdentityStatus(), "ACTIVE")
                && Objects.equals(policy.getStatus(), "ACTIVE")
                && validPolicyReceiptId(policy.getReceiptId())
                && policy.getPolicyVersion() != null
                && policy.getPolicyVersion() > 0L
                && policy.getPolicyDigest() != null
                && policy.getPolicyDigest().matches("[0-9a-f]{64}")
                && BoardPlanPolicyDigest.equal(
                        policy.getPolicyDigest(), BoardPlanPolicyDigest.policyDigest(policy))
                && BoardPlanPolicyName.isValid(policy.getPlanName())
                && policy.getDailyMeetingLimit() != null
                && policy.getDailyMeetingLimit() >= 1
                && policy.getDailyMeetingLimit() <= 10_000
                && policy.getAgendaLimit() != null
                && policy.getAgendaLimit() >= 1
                && policy.getAgendaLimit() <= BoardMeetingReservationRequest.MAX_AGENDA_COUNT
                && policy.getSecretaryEnabled() != null
                && (!IndependentBoardEntitlementService.FREE_PLAN.equals(planCode)
                    || policy.getSeatLimit() != null)
                && (policy.getSeatLimit() == null
                    || (policy.getSeatLimit() >= 1
                        && policy.getSeatLimit()
                            <= BoardMeetingReservationRequest.INITIAL_SAFETY_MAX_SEAT_COUNT));
        if (!exact) {
            throw new ServiceException("MEETING_POLICY_CURRENT_READ_FAILED", 500);
        }
    }

    private boolean validPolicyReceiptId(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{15,127}");
    }

    private BoardUsageOperationPolicyReceipt lineage(
            BoardUsageOperation operation,
            BoardPlanPolicySnapshot policy) {
        BoardUsageOperationPolicyReceipt lineage = new BoardUsageOperationPolicyReceipt();
        lineage.setTenantId(operation.getTenantId());
        lineage.setOperationId(operation.getOperationId());
        lineage.setProductCode(policy.getProductCode());
        lineage.setPlanCode(policy.getPlanCode());
        lineage.setPolicyReceiptId(policy.getReceiptId());
        lineage.setPolicyVersion(policy.getPolicyVersion());
        lineage.setPolicyDigest(policy.getPolicyDigest());
        return lineage;
    }

    private String reservationDigest(BoardMeetingReservationRequest request, Long authenticatedUserId) {
        String canonical = String.join("\n",
                IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.MEETING_METRIC,
                String.valueOf(request.tenantId()),
                String.valueOf(authenticatedUserId),
                String.valueOf(request.agendaCount()),
                String.valueOf(request.seatCount()));
        return BoardDigest.sha256(canonical);
    }

    private BoardMeetingReservationView toView(BoardUsageOperation operation) {
        return new BoardMeetingReservationView(
                operation.getOperationId(), operation.getStatus(), operation.getEffectivePlanCode(),
                operation.getAgendaCount(), operation.getSeatCount(), operation.getRemainingCount());
    }

    private int nullSafe(Integer value) {
        return value == null ? 0 : value;
    }
}
