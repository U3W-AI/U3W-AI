package com.wx.fbsir.business.board.service;

import com.wx.fbsir.business.board.domain.BoardUsageBudget;
import com.wx.fbsir.business.board.domain.BoardUsageOperation;
import com.wx.fbsir.business.board.dto.BoardEntitlementSnapshot;
import com.wx.fbsir.business.board.dto.BoardMeetingReservationRequest;
import com.wx.fbsir.business.board.dto.BoardMeetingReservationView;
import com.wx.fbsir.business.board.mapper.IndependentBoardMapper;
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
        validateMeetingShape(request, snapshot);
        LocalDate today = LocalDate.now(clock);
        BoardUsageOperation pending = operation(request, authenticatedUserId, snapshot, today);

        if (mapper.insertOperation(pending) != 1) {
            throw new ServiceException("MEETING_OPERATION_WRITE_FAILED", 500);
        }

        mapper.prepareUsageBudget(
                request.tenantId(), snapshot.memberId(), IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.MEETING_METRIC, today, snapshot.dailyMeetingLimit());
        if (mapper.reserveOneMeeting(
                request.tenantId(), snapshot.memberId(), IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.MEETING_METRIC, today, snapshot.dailyMeetingLimit()) != 1) {
            throw new ServiceException("DAILY_MEETING_QUOTA_EXHAUSTED", 409);
        }
        BoardUsageBudget budget = mapper.selectUsageBudget(
                request.tenantId(), snapshot.memberId(), IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.MEETING_METRIC, today);
        if (budget == null) {
            throw new ServiceException("USAGE_BUDGET_READBACK_FAILED", 500);
        }
        int remaining = Math.max(0, snapshot.dailyMeetingLimit()
                - nullSafe(budget.getUsedCount()) - nullSafe(budget.getReservedCount()));
        if (mapper.markOperationReserved(request.tenantId(), request.operationId(), remaining) != 1) {
            throw new ServiceException("MEETING_RESERVATION_FINALIZE_FAILED", 500);
        }
        pending.setStatus("RESERVED");
        pending.setRemainingCount(remaining);
        return toView(pending);
    }

    /**
     * Reads a committed winner only after the failed claim transaction has been rolled back.
     * REQUIRES_NEW makes the fresh current-read boundary explicit if a caller later gains a
     * transaction of its own.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public BoardMeetingReservationView replayCommitted(
            BoardMeetingReservationRequest request, Long authenticatedUserId) {
        BoardEntitlementSnapshot snapshot = entitlementService.getSnapshotForReservation(
                request.tenantId(), authenticatedUserId);
        BoardUsageOperation requested = operation(
                request, authenticatedUserId, snapshot, LocalDate.now(clock));
        BoardUsageOperation existing = mapper.selectOperationForUpdate(
                request.tenantId(), request.operationId());
        return verifyIdempotentReplay(existing, requested);
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
            BoardUsageOperation existing, BoardUsageOperation requested) {
        if (existing == null) {
            throw new ServiceException("IDEMPOTENCY_COLLISION_READBACK_FAILED", 409);
        }
        boolean sameIdentity = Objects.equals(existing.getTenantId(), requested.getTenantId())
                && Objects.equals(existing.getMemberId(), requested.getMemberId())
                && Objects.equals(existing.getUserId(), requested.getUserId())
                && Objects.equals(existing.getProductCode(), requested.getProductCode())
                && Objects.equals(existing.getMetricCode(), requested.getMetricCode())
                && BoardDigest.equal(existing.getRequestDigest(), requested.getRequestDigest());
        if (!sameIdentity) {
            throw new ServiceException("IDEMPOTENCY_DIGEST_CONFLICT", 409);
        }
        if (!Objects.equals(existing.getStatus(), "RESERVED")) {
            throw new ServiceException("IDEMPOTENT_OPERATION_NOT_FINAL", 409);
        }
        return toView(existing);
    }

    private void validateMeetingShape(
            BoardMeetingReservationRequest request, BoardEntitlementSnapshot snapshot) {
        if (request.agendaCount() > snapshot.agendaLimit()) {
            throw new ServiceException("AGENDA_LIMIT_EXCEEDED", 400);
        }
        if (request.seatCount() > BoardMeetingReservationRequest.INITIAL_SAFETY_MAX_SEAT_COUNT) {
            throw new ServiceException("SEAT_SAFETY_LIMIT_EXCEEDED", 400);
        }
        if (snapshot.seatLimit() != null && request.seatCount() > snapshot.seatLimit()) {
            throw new ServiceException("SEAT_LIMIT_EXCEEDED", 400);
        }
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
