package com.wx.fbsir.business.board.service;

import com.wx.fbsir.business.board.domain.BoardEnterpriseMemberScope;
import com.wx.fbsir.business.board.domain.BoardEntitlementReceipt;
import com.wx.fbsir.business.board.domain.BoardProductEntitlement;
import com.wx.fbsir.business.board.domain.BoardProductPlan;
import com.wx.fbsir.business.board.domain.BoardUsageBudget;
import com.wx.fbsir.business.board.dto.BoardEntitlementAdminView;
import com.wx.fbsir.business.board.dto.BoardConnectorBindingKey;
import com.wx.fbsir.business.board.dto.BoardEntitlementGrantRequest;
import com.wx.fbsir.business.board.dto.BoardEntitlementReceiptAuditEnvelope;
import com.wx.fbsir.business.board.dto.BoardEntitlementReceiptView;
import com.wx.fbsir.business.board.dto.BoardEntitlementRevokeRequest;
import com.wx.fbsir.business.board.dto.BoardEntitlementSnapshot;
import com.wx.fbsir.business.board.dto.BoardProductPlanAdminView;
import com.wx.fbsir.business.board.mapper.IndependentBoardMapper;
import com.wx.fbsir.business.board.plan.domain.BoardPlanPolicyName;
import com.wx.fbsir.business.board.plan.service.BoardPlanPolicyDigest;
import com.wx.fbsir.common.exception.ServiceException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
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
    public static final int ADMIN_RECEIPT_LIMIT = 500;
    public static final int ADMIN_RECEIPT_FETCH_LIMIT = ADMIN_RECEIPT_LIMIT + 1;

    private static final BoardConnectorBindingPort FAIL_CLOSED_CONNECTOR_PORT =
            new BoardConnectorBindingPort() {
                @Override
                public boolean hasAuthoritativeCurrentBinding(
                        Long tenantId, Long memberId, Long userId,
                        String productCode, boolean lockBinding) {
                    return false;
                }

                @Override
                public Set<BoardConnectorBindingKey> selectAuthoritativeCurrentBindingKeys(
                        Long tenantId, String productCode) {
                    return Set.of();
                }

                @Override
                public void revokeForEntitlement(
                        Long tenantId, Long memberId, Long userId,
                        String productCode, Long actorUserId) {
                    // Compatibility constructor is intentionally fail closed.
                }
            };

    private final IndependentBoardMapper mapper;
    private final BoardConnectorBindingPort connectorBindingPort;
    private final Clock clock;

    @Autowired
    public IndependentBoardEntitlementService(
            IndependentBoardMapper mapper,
            BoardConnectorBindingPort connectorBindingPort) {
        this(mapper, Clock.system(INITIAL_TENANT_ZONE), connectorBindingPort);
    }

    public IndependentBoardEntitlementService(IndependentBoardMapper mapper) {
        this(mapper, Clock.system(INITIAL_TENANT_ZONE), FAIL_CLOSED_CONNECTOR_PORT);
    }

    IndependentBoardEntitlementService(IndependentBoardMapper mapper, Clock clock) {
        this(mapper, clock, FAIL_CLOSED_CONNECTOR_PORT);
    }

    IndependentBoardEntitlementService(
            IndependentBoardMapper mapper,
            Clock clock,
            BoardConnectorBindingPort connectorBindingPort) {
        this.mapper = mapper;
        this.clock = clock;
        this.connectorBindingPort = connectorBindingPort;
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
        Set<BoardConnectorBindingKey> currentBindings = rows.isEmpty()
                ? Set.of()
                : connectorBindingPort.selectAuthoritativeCurrentBindingKeys(
                        tenantId, PRODUCT_CODE);
        return rows.stream()
                .map(entitlement -> toAdminView(
                        entitlement,
                        now,
                        tenantId,
                        currentBindings.contains(new BoardConnectorBindingKey(
                                tenantId, entitlement.getMemberId(), entitlement.getUserId(), PRODUCT_CODE))))
                .toList();
    }

    /**
     * Reads the exact current policy catalog without exposing a write surface.
     * The entitlement-query permission is intentionally reused because the
     * catalog is a prerequisite to the existing entitlement grant form.
     */
    public List<BoardProductPlanAdminView> listPlans() {
        List<BoardProductPlan> plans;
        try {
            plans = mapper.selectPlansByProduct(PRODUCT_CODE);
        } catch (DataAccessException persistence) {
            throw new ServiceException("BOARD_PLAN_CURRENT_READ_UNAVAILABLE", 503);
        }
        if (plans == null || plans.size() != 2) {
            throw new ServiceException("BOARD_PLAN_CURRENT_READ_FAILED", 500);
        }
        Set<String> planCodes = new HashSet<>();
        List<BoardProductPlanAdminView> result = new ArrayList<>(2);
        for (BoardProductPlan plan : plans) {
            if (plan == null) {
                throw new ServiceException("BOARD_PLAN_CONTRACT_DRIFT", 500);
            }
            validatePlanContract(plan);
            if (!planCodes.add(plan.getPlanCode())) {
                throw new ServiceException("BOARD_PLAN_CONTRACT_DRIFT", 500);
            }
            result.add(toPlanAdminView(plan));
        }
        if (!planCodes.equals(Set.of(FREE_PLAN, VIP_PLAN))) {
            throw new ServiceException("BOARD_PLAN_CONTRACT_DRIFT", 500);
        }
        validateCatalogInvariant(plans);
        result.sort(java.util.Comparator.comparing(BoardProductPlanAdminView::planCode));
        return List.copyOf(result);
    }

    @Transactional(readOnly = true)
    public BoardEntitlementReceiptAuditEnvelope listReceipts(Long tenantId) {
        requirePositive(tenantId, "TENANT_REQUIRED");
        List<BoardEntitlementReceipt> rows = mapper.selectEntitlementReceiptsByTenant(tenantId);
        if (rows == null || rows.size() > ADMIN_RECEIPT_FETCH_LIMIT) {
            throw new ServiceException("BOARD_ENTITLEMENT_RECEIPT_CURRENT_READ_FAILED", 500);
        }
        boolean truncated = rows.size() > ADMIN_RECEIPT_LIMIT;
        int resultSize = Math.min(rows.size(), ADMIN_RECEIPT_LIMIT);
        List<BoardEntitlementReceiptView> records = new ArrayList<>(resultSize);
        for (int index = 0; index < rows.size(); index++) {
            BoardEntitlementReceiptView view = toReceiptView(rows.get(index), tenantId);
            if (index < ADMIN_RECEIPT_LIMIT) {
                records.add(view);
            }
        }
        return new BoardEntitlementReceiptAuditEnvelope(records, ADMIN_RECEIPT_LIMIT, truncated);
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
            if (Objects.equals(request.expectedVersion(), Long.MAX_VALUE)) {
                throw new ServiceException("ENTITLEMENT_EXPECTED_VERSION_OUT_OF_RANGE", 400);
            }
            if (!Objects.equals(current.getUserId(), request.userId())
                    || !Objects.equals(current.getVersion(), request.expectedVersion())
                    || !Objects.equals(current.getStatus(), "ACTIVE")) {
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

        if (!Boolean.TRUE.equals(plan.getConnectorRequired())) {
            connectorBindingPort.revokeForEntitlement(
                    request.tenantId(), request.memberId(), request.userId(), PRODUCT_CODE, actorUserId);
        }
        boolean connectorVerified = Boolean.TRUE.equals(plan.getConnectorRequired())
                && connectorBindingPort.hasAuthoritativeCurrentBinding(
                        request.tenantId(), request.memberId(), request.userId(), PRODUCT_CODE, true);
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
        return toAdminView(next, now, request.tenantId(), connectorVerified);
    }

    @Transactional(rollbackFor = Exception.class)
    public BoardEntitlementAdminView revoke(BoardEntitlementRevokeRequest request, Long actorUserId) {
        requirePositive(actorUserId, "AUTHENTICATED_PRINCIPAL_REQUIRED");
        requirePositive(request.tenantId(), "TENANT_REQUIRED");
        requirePositive(request.memberId(), "MEMBER_REQUIRED");
        requirePositive(request.userId(), "USER_REQUIRED");
        requirePositive(request.expectedVersion(), "ENTITLEMENT_EXPECTED_VERSION_REQUIRED");
        if (Objects.equals(request.expectedVersion(), Long.MAX_VALUE)) {
            throw new ServiceException("ENTITLEMENT_EXPECTED_VERSION_OUT_OF_RANGE", 400);
        }

        BoardProductEntitlement current = mapper.selectEntitlementForUpdate(
                request.tenantId(), request.memberId(), PRODUCT_CODE);
        if (!isExactActiveEntitlement(current, request)) {
            throw new ServiceException("ENTITLEMENT_SCOPE_OR_VERSION_CONFLICT", 409);
        }

        Date now = Date.from(clock.instant());
        Date revocationValidUntil = revokeValidUntil(current, now);
        current.setStatus("REVOKED");
        current.setValidUntil(revocationValidUntil);
        current.setUpdatedAt(now);
        current.setVersion(request.expectedVersion() + 1L);
        if (mapper.updateEntitlementIfVersion(current, request.expectedVersion()) != 1) {
            throw new ServiceException("ENTITLEMENT_VERSION_CONFLICT", 409);
        }

        connectorBindingPort.revokeForEntitlement(
                request.tenantId(), request.memberId(), request.userId(), PRODUCT_CODE, actorUserId);

        BoardEntitlementReceipt receipt = new BoardEntitlementReceipt();
        receipt.setReceiptId(UUID.randomUUID().toString());
        receipt.setTenantId(request.tenantId());
        receipt.setActorUserId(actorUserId);
        receipt.setTargetMemberId(request.memberId());
        receipt.setAction("ENTITLEMENT_REVOKED");
        receipt.setPayloadDigest(revokeDigest(request, actorUserId));
        receipt.setEvidenceLevel("ACTION_COMPLETED");
        receipt.setCreatedAt(now);
        if (mapper.insertEntitlementReceipt(receipt) != 1) {
            throw new ServiceException("ENTITLEMENT_AUDIT_WRITE_FAILED", 500);
        }
        return toAdminView(current, now, request.tenantId(), false);
    }

    BoardEntitlementSnapshot getSnapshotForReservation(Long tenantId, Long authenticatedUserId) {
        return resolveSnapshot(tenantId, authenticatedUserId, true);
    }

    private BoardEntitlementSnapshot resolveSnapshot(Long tenantId, Long authenticatedUserId, boolean lockEntitlement) {
        requirePositive(tenantId, "TENANT_REQUIRED");
        requirePositive(authenticatedUserId, "AUTHENTICATED_PRINCIPAL_REQUIRED");
        BoardEnterpriseMemberScope member = lockEntitlement
                ? mapper.selectActiveContextForUpdate(tenantId, authenticatedUserId)
                : mapper.selectActiveContext(tenantId, authenticatedUserId);
        verifyMember(member, tenantId, authenticatedUserId);

        BoardProductPlan free = requirePlan(FREE_PLAN);
        BoardProductEntitlement entitlement = lockEntitlement
                ? mapper.selectEntitlementForUpdate(tenantId, member.getMemberId(), PRODUCT_CODE)
                : mapper.selectEntitlement(tenantId, member.getMemberId(), authenticatedUserId, PRODUCT_CODE);
        if (entitlement != null && !Objects.equals(entitlement.getUserId(), authenticatedUserId)) {
            throw new ServiceException("ENTITLEMENT_SCOPE_INVALID", 403);
        }

        Date now = Date.from(clock.instant());
        boolean revoked = entitlement != null && Objects.equals(entitlement.getStatus(), "REVOKED");
        boolean temporallyValid = isTemporallyValid(entitlement, now);
        BoardProductPlan granted = entitlement == null || revoked
                ? null : mapper.selectActivePlan(PRODUCT_CODE, entitlement.getPlanCode());
        if (entitlement != null && !revoked && temporallyValid && granted == null) {
            throw new ServiceException("BOARD_PLAN_CONTRACT_DRIFT", 500);
        }
        if (granted != null) {
            validatePlanContract(granted);
        }
        boolean connectorVerified = granted != null
                && Boolean.TRUE.equals(granted.getConnectorRequired())
                && connectorBindingPort.hasAuthoritativeCurrentBinding(
                        tenantId, member.getMemberId(), authenticatedUserId,
                        PRODUCT_CODE, lockEntitlement);
        BoardProductPlan effective = free;
        String activationState = "FREE";
        if (revoked) {
            activationState = "REVOKED";
        } else if (entitlement != null && temporallyValid && granted != null) {
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
            Long expectedTenantId,
            boolean connectorVerified) {
        if (entitlement == null
                || !Objects.equals(entitlement.getTenantId(), expectedTenantId)
                || !Objects.equals(entitlement.getProductCode(), PRODUCT_CODE)
                || entitlement.getMemberId() == null || entitlement.getMemberId() <= 0L
                || entitlement.getUserId() == null || entitlement.getUserId() <= 0L) {
            throw new ServiceException("BOARD_ENTITLEMENT_SCOPE_INVALID", 500);
        }
        boolean revoked = Objects.equals(entitlement.getStatus(), "REVOKED");
        boolean temporallyValid = isTemporallyValid(entitlement, now);
        BoardProductPlan plan = revoked
                ? null : mapper.selectActivePlan(PRODUCT_CODE, entitlement.getPlanCode());
        if (!revoked && temporallyValid && plan == null) {
            throw new ServiceException("BOARD_PLAN_CONTRACT_DRIFT", 500);
        }
        if (plan != null) {
            validatePlanContract(plan);
        }
        String state;
        if (revoked) {
            state = "REVOKED";
        } else if (!temporallyValid) {
            state = "EXPIRED";
        } else if (plan == null) {
            state = "INVALID_ENTITLEMENT";
        } else if (Boolean.TRUE.equals(plan.getConnectorRequired()) && !connectorVerified) {
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

    private BoardEntitlementReceiptView toReceiptView(
            BoardEntitlementReceipt receipt,
            Long expectedTenantId) {
        if (receipt == null
                || !Objects.equals(receipt.getTenantId(), expectedTenantId)
                || receipt.getReceiptId() == null || receipt.getReceiptId().isBlank()
                || receipt.getActorUserId() == null || receipt.getActorUserId() <= 0L
                || receipt.getTargetMemberId() == null || receipt.getTargetMemberId() <= 0L
                || !isKnownReceiptAction(receipt.getAction())
                || !Objects.equals(receipt.getEvidenceLevel(), "ACTION_COMPLETED")
                || receipt.getCreatedAt() == null) {
            throw new ServiceException("BOARD_ENTITLEMENT_RECEIPT_SCOPE_INVALID", 500);
        }
        return new BoardEntitlementReceiptView(
                receipt.getReceiptId(), receipt.getTenantId(), receipt.getActorUserId(),
                receipt.getTargetMemberId(), receipt.getAction(), receipt.getEvidenceLevel(),
                receipt.getCreatedAt());
    }

    private boolean isKnownReceiptAction(String action) {
        return Objects.equals(action, "ENTITLEMENT_GRANTED")
                || Objects.equals(action, "ENTITLEMENT_UPDATED")
                || Objects.equals(action, "ENTITLEMENT_REVOKED");
    }

    private boolean isExactActiveEntitlement(
            BoardProductEntitlement entitlement,
            BoardEntitlementRevokeRequest request) {
        return entitlement != null
                && Objects.equals(entitlement.getTenantId(), request.tenantId())
                && Objects.equals(entitlement.getMemberId(), request.memberId())
                && Objects.equals(entitlement.getUserId(), request.userId())
                && Objects.equals(entitlement.getProductCode(), PRODUCT_CODE)
                && Objects.equals(entitlement.getVersion(), request.expectedVersion())
                && Objects.equals(entitlement.getStatus(), "ACTIVE");
    }

    private Date revokeValidUntil(BoardProductEntitlement entitlement, Date now) {
        Date validFrom = entitlement.getValidFrom();
        if (validFrom == null) {
            return now;
        }
        long validFromMillis = validFrom.getTime();
        if (validFromMillis == Long.MAX_VALUE) {
            throw new ServiceException("ENTITLEMENT_VALIDITY_CONFLICT", 409);
        }
        long validityFloor = validFromMillis + 1L;
        return now.getTime() >= validityFloor ? now : new Date(validityFloor);
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
                && Boolean.FALSE.equals(plan.getConnectorRequired());
        boolean vipValid = VIP_PLAN.equals(plan.getPlanCode())
                && Boolean.TRUE.equals(plan.getVip())
                && Boolean.TRUE.equals(plan.getConnectorRequired());
        if (!Objects.equals(plan.getProductCode(), PRODUCT_CODE)
                || !Objects.equals(plan.getStatus(), "ACTIVE")
                || (!freeValid && !vipValid)
                || plan.getDailyMeetingLimit() == null
                || plan.getDailyMeetingLimit() < 1
                || plan.getDailyMeetingLimit() > 10_000
                || plan.getAgendaLimit() == null
                || plan.getAgendaLimit() < 1
                || plan.getAgendaLimit() > 30
                || (plan.getSeatLimit() != null
                    && (plan.getSeatLimit() < 1 || plan.getSeatLimit() > 100))
                || (freeValid && plan.getSeatLimit() == null)
                || plan.getSecretaryEnabled() == null) {
            throw new ServiceException("BOARD_PLAN_CONTRACT_DRIFT", 500);
        }
        validatePlanReadMetadata(plan);
    }

    private void validatePlanReadMetadata(BoardProductPlan plan) {
        String name = plan.getPlanName();
        if (!BoardPlanPolicyName.isValid(name)
                || plan.getVersion() == null || plan.getVersion() < 1L
                || plan.getUpdatedAt() == null
                || plan.getPolicyReceiptId() == null
                || !plan.getPolicyReceiptId().matches(
                        "[A-Za-z0-9][A-Za-z0-9._:-]{15,127}")
                || plan.getPolicyDigest() == null
                || !plan.getPolicyDigest().matches("[0-9a-f]{64}")
                || !BoardPlanPolicyDigest.equal(
                        plan.getPolicyDigest(), BoardPlanPolicyDigest.policyDigest(
                                plan.getProductCode(), plan.getPlanCode(), plan.getPlanName(),
                                plan.getVip(), plan.getConnectorRequired(),
                                plan.getDailyMeetingLimit(), plan.getAgendaLimit(),
                                plan.getSeatLimit(), plan.getSecretaryEnabled(),
                                plan.getStatus()))) {
            throw new ServiceException("BOARD_PLAN_CONTRACT_DRIFT", 500);
        }
    }

    private void validateCatalogInvariant(List<BoardProductPlan> plans) {
        BoardProductPlan free = plans.stream()
                .filter(plan -> FREE_PLAN.equals(plan.getPlanCode()))
                .findFirst()
                .orElseThrow(() -> new ServiceException("BOARD_PLAN_CONTRACT_DRIFT", 500));
        BoardProductPlan vip = plans.stream()
                .filter(plan -> VIP_PLAN.equals(plan.getPlanCode()))
                .findFirst()
                .orElseThrow(() -> new ServiceException("BOARD_PLAN_CONTRACT_DRIFT", 500));
        if (vip.getDailyMeetingLimit() < free.getDailyMeetingLimit()
                || vip.getAgendaLimit() < free.getAgendaLimit()
                || (vip.getSeatLimit() != null
                    && vip.getSeatLimit() < free.getSeatLimit())
                || (Boolean.TRUE.equals(free.getSecretaryEnabled())
                    && !Boolean.TRUE.equals(vip.getSecretaryEnabled()))) {
            throw new ServiceException("BOARD_PLAN_CONTRACT_DRIFT", 500);
        }
    }

    private BoardProductPlanAdminView toPlanAdminView(BoardProductPlan plan) {
        return new BoardProductPlanAdminView(
                plan.getProductCode(), plan.getPlanCode(), plan.getPlanName(),
                Boolean.TRUE.equals(plan.getVip()), Boolean.TRUE.equals(plan.getConnectorRequired()),
                plan.getDailyMeetingLimit(), plan.getAgendaLimit(), plan.getSeatLimit(),
                Boolean.TRUE.equals(plan.getSecretaryEnabled()), plan.getStatus(),
                plan.getVersion(), new Date(plan.getUpdatedAt().getTime()));
    }

    private boolean isTemporallyValid(BoardProductEntitlement entitlement, Date now) {
        return entitlement != null
                && Objects.equals(entitlement.getStatus(), "ACTIVE")
                && (entitlement.getValidFrom() == null || !entitlement.getValidFrom().after(now))
                && (entitlement.getValidUntil() == null || entitlement.getValidUntil().after(now));
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

    private String revokeDigest(BoardEntitlementRevokeRequest request, Long actorUserId) {
        String canonical = String.join("\n",
                PRODUCT_CODE,
                "ENTITLEMENT_REVOKED",
                String.valueOf(request.tenantId()),
                String.valueOf(request.memberId()),
                String.valueOf(request.userId()),
                String.valueOf(request.expectedVersion()),
                String.valueOf(request.expectedVersion() + 1L),
                String.valueOf(actorUserId));
        return BoardDigest.sha256(canonical);
    }

    private void requirePositive(Long value, String code) {
        if (value == null || value <= 0L) {
            throw new ServiceException(code, 400);
        }
    }
}
