package com.wx.fbsir.business.board.service;

import com.wx.fbsir.business.board.domain.BoardEnterpriseMemberScope;
import com.wx.fbsir.business.board.domain.BoardEntitlementReceipt;
import com.wx.fbsir.business.board.domain.BoardProductEntitlement;
import com.wx.fbsir.business.board.domain.BoardProductPlan;
import com.wx.fbsir.business.board.domain.BoardUsageBudget;
import com.wx.fbsir.business.board.dto.BoardEntitlementAdminView;
import com.wx.fbsir.business.board.dto.BoardEntitlementGrantRequest;
import com.wx.fbsir.business.board.dto.BoardEntitlementSnapshot;
import com.wx.fbsir.business.board.mapper.IndependentBoardMapper;
import com.wx.fbsir.common.exception.ServiceException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IndependentBoardEntitlementService {
    public static final String PRODUCT_CODE = "FBSIR_INDEPENDENT_BOARD";
    public static final String FREE_PLAN = "BOARD_FREE";
    public static final String VIP_PLAN = "BOARD_VIP";
    public static final String MEETING_METRIC = "DAILY_MEETING";
    public static final ZoneId INITIAL_TENANT_ZONE = ZoneId.of("Asia/Shanghai");
    public static final int ADMIN_ENTITLEMENT_LIMIT = 100;

    private final IndependentBoardMapper mapper;
    private final Clock clock;

    @Autowired
    public IndependentBoardEntitlementService(IndependentBoardMapper mapper) {
        this(mapper, Clock.system(INITIAL_TENANT_ZONE));
    }

    IndependentBoardEntitlementService(IndependentBoardMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public BoardEntitlementSnapshot getSnapshot(Long tenantId, Long authenticatedUserId) {
        return resolveSnapshot(tenantId, authenticatedUserId, false);
    }

    @Transactional(readOnly = true)
    public List<BoardEntitlementAdminView> listEntitlements(Long tenantId) {
        requirePositive(tenantId, "TENANT_REQUIRED");
        Date now = Date.from(clock.instant());
        List<BoardProductEntitlement> rows = mapper.selectEntitlementsByTenant(
                tenantId, PRODUCT_CODE);
        if (rows == null) {
            throw new ServiceException("BOARD_ENTITLEMENT_CURRENT_READ_FAILED", 500);
        }
        if (rows.size() > ADMIN_ENTITLEMENT_LIMIT) {
            throw new ServiceException("BOARD_ENTITLEMENT_LIMIT_EXCEEDED", 500);
        }
        return rows.stream()
                .map(entitlement -> toAdminView(entitlement, now, tenantId))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public BoardEntitlementAdminView grant(BoardEntitlementGrantRequest request, Long actorUserId) {
        requirePositive(actorUserId, "AUTHENTICATED_PRINCIPAL_REQUIRED");
        BoardEnterpriseMemberScope member = requireExactMember(
                request.tenantId(), request.memberId(), request.userId());
        BoardProductPlan plan = requirePlan(request.planCode());
        Date now = Date.from(clock.instant());
        if (request.validUntil() != null && !request.validUntil().after(now)) {
            throw new ServiceException("ENTITLEMENT_VALID_UNTIL_MUST_BE_FUTURE", 400);
        }

        BoardProductEntitlement current = mapper.selectEntitlementForUpdate(
                request.tenantId(), request.memberId(), PRODUCT_CODE);
        BoardProductEntitlement next = new BoardProductEntitlement();
        next.setTenantId(request.tenantId());
        next.setMemberId(member.getMemberId());
        next.setUserId(member.getUserId());
        next.setProductCode(PRODUCT_CODE);
        next.setPlanCode(plan.getPlanCode());
        next.setStatus("ACTIVE");
        next.setValidFrom(now);
        next.setValidUntil(request.validUntil());
        next.setUpdatedAt(now);

        String action;
        if (current == null) {
            if (request.expectedVersion() != 0L) {
                throw new ServiceException("ENTITLEMENT_VERSION_CONFLICT", 409);
            }
            next.setVersion(1L);
            next.setCreatedAt(now);
            try {
                if (mapper.insertEntitlement(next) != 1) {
                    throw new ServiceException("ENTITLEMENT_WRITE_FAILED", 500);
                }
            } catch (DuplicateKeyException | PessimisticLockingFailureException conflict) {
                throw new ServiceException("ENTITLEMENT_VERSION_CONFLICT", 409);
            }
            action = "ENTITLEMENT_GRANTED";
        } else {
            if (!Objects.equals(current.getUserId(), request.userId())
                    || !Objects.equals(current.getVersion(), request.expectedVersion())) {
                throw new ServiceException("ENTITLEMENT_SCOPE_OR_VERSION_CONFLICT", 409);
            }
            next.setId(current.getId());
            next.setConnectorBindingId(current.getConnectorBindingId());
            next.setConnectorVerifiedAt(current.getConnectorVerifiedAt());
            next.setVersion(request.expectedVersion() + 1L);
            next.setCreatedAt(current.getCreatedAt());
            if (mapper.updateEntitlementIfVersion(next, request.expectedVersion()) != 1) {
                throw new ServiceException("ENTITLEMENT_VERSION_CONFLICT", 409);
            }
            action = "ENTITLEMENT_UPDATED";
        }

        BoardEntitlementReceipt receipt = new BoardEntitlementReceipt();
        receipt.setReceiptId(UUID.randomUUID().toString());
        receipt.setTenantId(request.tenantId());
        receipt.setActorUserId(actorUserId);
        receipt.setTargetMemberId(request.memberId());
        receipt.setAction(action);
        receipt.setPayloadDigest(grantDigest(request, actorUserId));
        receipt.setEvidenceLevel("ACTION_COMPLETED");
        receipt.setCreatedAt(now);
        if (mapper.insertEntitlementReceipt(receipt) != 1) {
            throw new ServiceException("ENTITLEMENT_AUDIT_WRITE_FAILED", 500);
        }
        return toAdminView(next, now, request.tenantId());
    }

    BoardEntitlementSnapshot getSnapshotForReservation(Long tenantId, Long authenticatedUserId) {
        return resolveSnapshot(tenantId, authenticatedUserId, true);
    }

    private BoardEntitlementSnapshot resolveSnapshot(Long tenantId, Long authenticatedUserId, boolean lockEntitlement) {
        requirePositive(tenantId, "TENANT_REQUIRED");
        requirePositive(authenticatedUserId, "AUTHENTICATED_PRINCIPAL_REQUIRED");
        BoardEnterpriseMemberScope member = mapper.selectActiveContext(tenantId, authenticatedUserId);
        verifyMember(member, tenantId, authenticatedUserId);

        BoardProductPlan free = requirePlan(FREE_PLAN);
        BoardProductEntitlement entitlement = lockEntitlement
                ? mapper.selectEntitlementForUpdate(tenantId, member.getMemberId(), PRODUCT_CODE)
                : mapper.selectEntitlement(tenantId, member.getMemberId(), authenticatedUserId, PRODUCT_CODE);
        if (entitlement != null && !Objects.equals(entitlement.getUserId(), authenticatedUserId)) {
            throw new ServiceException("ENTITLEMENT_SCOPE_INVALID", 403);
        }

        Date now = Date.from(clock.instant());
        BoardProductPlan granted = entitlement == null ? null : mapper.selectActivePlan(PRODUCT_CODE, entitlement.getPlanCode());
        if (granted != null) {
            validatePlanContract(granted);
        }
        boolean temporallyValid = isTemporallyValid(entitlement, now);
        boolean connectorVerified = hasAuthoritativeConnectorCurrentRead();
        BoardProductPlan effective = free;
        String activationState = "FREE";
        if (entitlement != null && temporallyValid && granted != null) {
            boolean connectorSatisfied = !Boolean.TRUE.equals(granted.getConnectorRequired()) || connectorVerified;
            if (connectorSatisfied) {
                effective = granted;
                activationState = Boolean.TRUE.equals(granted.getVip()) ? "ACTIVE" : "FREE";
            } else {
                activationState = "PENDING_CONNECTOR";
            }
        } else if (entitlement != null) {
            activationState = temporallyValid ? "INVALID_ENTITLEMENT" : "EXPIRED";
        }

        LocalDate today = LocalDate.now(clock);
        BoardUsageBudget budget = mapper.selectUsageBudget(
                tenantId, member.getMemberId(), PRODUCT_CODE, MEETING_METRIC, today);
        int used = budget == null || budget.getUsedCount() == null ? 0 : budget.getUsedCount();
        int reserved = budget == null || budget.getReservedCount() == null ? 0 : budget.getReservedCount();
        int remaining = Math.max(0, effective.getDailyMeetingLimit() - used - reserved);
        return new BoardEntitlementSnapshot(
                tenantId, member.getMemberId(), authenticatedUserId,
                entitlement == null ? null : entitlement.getPlanCode(), effective.getPlanCode(), activationState,
                effective.getDailyMeetingLimit(), effective.getAgendaLimit(), effective.getSeatLimit(),
                Boolean.TRUE.equals(effective.getSecretaryEnabled()),
                granted != null && Boolean.TRUE.equals(granted.getConnectorRequired()), connectorVerified,
                used, reserved, remaining);
    }

    private BoardEntitlementAdminView toAdminView(
            BoardProductEntitlement entitlement,
            Date now,
            Long expectedTenantId) {
        if (entitlement == null
                || !Objects.equals(entitlement.getTenantId(), expectedTenantId)
                || !Objects.equals(entitlement.getProductCode(), PRODUCT_CODE)
                || entitlement.getMemberId() == null || entitlement.getMemberId() <= 0L
                || entitlement.getUserId() == null || entitlement.getUserId() <= 0L) {
            throw new ServiceException("BOARD_ENTITLEMENT_SCOPE_INVALID", 500);
        }
        BoardProductPlan plan = mapper.selectActivePlan(PRODUCT_CODE, entitlement.getPlanCode());
        if (plan != null) {
            validatePlanContract(plan);
        }
        String state;
        if (!isTemporallyValid(entitlement, now)) {
            state = "EXPIRED";
        } else if (plan == null) {
            state = "INVALID_ENTITLEMENT";
        } else if (Boolean.TRUE.equals(plan.getConnectorRequired()) && !hasAuthoritativeConnectorCurrentRead()) {
            state = "PENDING_CONNECTOR";
        } else {
            state = Boolean.TRUE.equals(plan.getVip()) ? "ACTIVE" : "FREE";
        }
        return new BoardEntitlementAdminView(
                entitlement.getTenantId(), entitlement.getMemberId(), entitlement.getUserId(),
                entitlement.getPlanCode(), entitlement.getStatus(), state,
                entitlement.getValidFrom(), entitlement.getValidUntil(), entitlement.getVersion(),
                entitlement.getUpdatedAt());
    }

    private BoardEnterpriseMemberScope requireExactMember(Long tenantId, Long memberId, Long userId) {
        requirePositive(tenantId, "TENANT_REQUIRED");
        requirePositive(memberId, "MEMBER_REQUIRED");
        requirePositive(userId, "USER_REQUIRED");
        BoardEnterpriseMemberScope member = mapper.selectExactActiveMemberForUpdate(
                tenantId, memberId, userId);
        verifyMember(member, tenantId, userId);
        if (!Objects.equals(member.getMemberId(), memberId)) {
            throw new ServiceException("TENANT_MEMBER_USER_SCOPE_INVALID", 403);
        }
        return member;
    }

    private void verifyMember(BoardEnterpriseMemberScope member, Long tenantId, Long userId) {
        if (member == null || !Objects.equals(member.getTenantId(), tenantId)
                || !Objects.equals(member.getUserId(), userId)
                || !Objects.equals(member.getStatus(), 1)
                || !Objects.equals(member.getDelFlag(), "0")) {
            throw new ServiceException("TENANT_MEMBER_USER_SCOPE_INVALID", 403);
        }
    }

    private BoardProductPlan requirePlan(String planCode) {
        BoardProductPlan plan = mapper.selectActivePlan(PRODUCT_CODE, planCode);
        if (plan == null) {
            throw new ServiceException("BOARD_PLAN_CONFIGURATION_MISSING", 500);
        }
        validatePlanContract(plan);
        return plan;
    }

    private void validatePlanContract(BoardProductPlan plan) {
        boolean freeValid = FREE_PLAN.equals(plan.getPlanCode())
                && Boolean.FALSE.equals(plan.getVip())
                && Boolean.FALSE.equals(plan.getConnectorRequired())
                && Objects.equals(plan.getDailyMeetingLimit(), 1)
                && Objects.equals(plan.getAgendaLimit(), 5)
                && Objects.equals(plan.getSeatLimit(), 3)
                && Boolean.FALSE.equals(plan.getSecretaryEnabled());
        boolean vipValid = VIP_PLAN.equals(plan.getPlanCode())
                && Boolean.TRUE.equals(plan.getVip())
                && Boolean.TRUE.equals(plan.getConnectorRequired())
                && Objects.equals(plan.getDailyMeetingLimit(), 5)
                && Objects.equals(plan.getAgendaLimit(), 30)
                && plan.getSeatLimit() == null
                && Boolean.TRUE.equals(plan.getSecretaryEnabled());
        if (!Objects.equals(plan.getProductCode(), PRODUCT_CODE)
                || !Objects.equals(plan.getStatus(), "ACTIVE") || (!freeValid && !vipValid)) {
            throw new ServiceException("BOARD_PLAN_CONTRACT_DRIFT", 500);
        }
    }

    private boolean isTemporallyValid(BoardProductEntitlement entitlement, Date now) {
        return entitlement != null
                && Objects.equals(entitlement.getStatus(), "ACTIVE")
                && (entitlement.getValidFrom() == null || !entitlement.getValidFrom().after(now))
                && (entitlement.getValidUntil() == null || entitlement.getValidUntil().after(now));
    }

    private boolean hasAuthoritativeConnectorCurrentRead() {
        // W4 gate: entitlement-row legacy fields are not an authoritative Connector current-read.
        // Keep VIP fail-closed until the reviewed binding source, status and freshness query lands.
        return false;
    }

    private String grantDigest(BoardEntitlementGrantRequest request, Long actorUserId) {
        String canonical = String.join("\n",
                PRODUCT_CODE,
                String.valueOf(request.tenantId()),
                String.valueOf(request.memberId()),
                String.valueOf(request.userId()),
                request.planCode(),
                request.validUntil() == null ? "" : String.valueOf(request.validUntil().getTime()),
                String.valueOf(request.expectedVersion()),
                String.valueOf(actorUserId));
        return BoardDigest.sha256(canonical);
    }

    private void requirePositive(Long value, String code) {
        if (value == null || value <= 0L) {
            throw new ServiceException(code, 400);
        }
    }
}
