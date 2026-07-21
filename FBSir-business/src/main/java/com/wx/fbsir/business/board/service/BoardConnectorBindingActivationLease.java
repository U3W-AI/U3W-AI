package com.wx.fbsir.business.board.service;

import com.wx.fbsir.business.board.domain.BoardConnectorBinding;
import com.wx.fbsir.business.board.domain.BoardEnterpriseMemberScope;
import com.wx.fbsir.business.board.domain.BoardProductEntitlement;
import com.wx.fbsir.business.board.domain.BoardProductPlan;
import com.wx.fbsir.business.board.dto.BoardConnectorBindingKey;
import com.wx.fbsir.business.board.dto.BoardConnectorBindingSnapshot;
import java.util.Date;
import java.util.List;

/**
 * Opaque, transaction-bound lease for the W4a portion of OAuth family activation.
 *
 * <p>Only {@link IndependentBoardConnectorBindingService} can create or advance
 * this lease. Package-internal accessors expose only the values needed by the
 * trusted coordinator and never expose mutable persistence state.</p>
 */
final class BoardConnectorBindingActivationLease {
    enum Mode {
        FIRST_ACTIVATION,
        EXPLICIT_REAUTHORIZATION
    }

    enum Phase {
        LOCKED,
        PREPARED_AND_BINDING_APPLIED,
        W4A_RECEIPT_WRITTEN,
        COMPLETE,
        INVALID
    }

    private final Object ownerToken;
    private final Object transactionMarker;
    private final Object boardTransactionResource;
    private final Object boardPhysicalConnection;
    private final Thread ownerThread;
    private final BoardConnectorBindingKey key;
    private final Mode mode;
    private final BoardConnectorBinding existingBinding;
    private final Long expectedVersion;
    private final Long actorUserId;
    private Date transitionAt;
    private boolean transitionAtFinalized;
    private final MemberImage memberImage;
    private final EntitlementImage entitlementImage;
    private final PlanImage planImage;
    private BoardConnectorBinding appliedBinding;
    private BoardOAuthFamilyActivationProof activationProof;
    private Object transactionSynchronization;
    private List<Object> trustedBaselineSynchronizations = List.of();
    private boolean trustedBaselineAttached;
    private Object finalizerSynchronization;
    private Phase phase = Phase.LOCKED;

    BoardConnectorBindingActivationLease(
            Object ownerToken,
            Object transactionMarker,
            Object boardTransactionResource,
            Object boardPhysicalConnection,
            BoardConnectorBindingKey key,
            Mode mode,
            BoardConnectorBinding existingBinding,
            Long expectedVersion,
            Long actorUserId,
            Date transitionAt,
            BoardEnterpriseMemberScope member,
            BoardProductEntitlement entitlement,
            BoardProductPlan plan) {
        this.ownerToken = ownerToken;
        this.transactionMarker = transactionMarker;
        this.boardTransactionResource = boardTransactionResource;
        this.boardPhysicalConnection = boardPhysicalConnection;
        this.ownerThread = Thread.currentThread();
        this.key = key;
        this.mode = mode;
        this.existingBinding = existingBinding;
        this.expectedVersion = expectedVersion;
        this.actorUserId = actorUserId;
        this.transitionAt = copy(transitionAt);
        this.transitionAtFinalized = false;
        this.memberImage = new MemberImage(member);
        this.entitlementImage = new EntitlementImage(entitlement);
        this.planImage = new PlanImage(plan);
    }

    Mode mode() {
        return mode;
    }

    BoardConnectorBindingKey key() {
        return key;
    }

    BoardConnectorBinding existingBinding(Object ownerCapability) {
        requireOwner(ownerCapability);
        return existingBinding;
    }

    BoardConnectorBinding appliedBinding(Object ownerCapability) {
        requireOwner(ownerCapability);
        return appliedBinding;
    }

    void setAppliedBinding(Object ownerCapability, BoardConnectorBinding binding) {
        requireOwner(ownerCapability);
        this.appliedBinding = binding;
    }

    BoardOAuthFamilyActivationProof activationProof(Object ownerCapability) {
        requireOwner(ownerCapability);
        return activationProof;
    }

    void attachActivationProof(
            Object ownerCapability,
            BoardOAuthFamilyActivationProof proof) {
        requireOwner(ownerCapability);
        if (activationProof != null || proof == null) {
            throw new IllegalStateException("activation proof is already attached or missing");
        }
        activationProof = proof;
    }

    Long expectedVersion() {
        return expectedVersion;
    }

    Long actorUserId() {
        return actorUserId;
    }

    Date transitionAt() {
        if (!transitionAtFinalized) {
            throw new IllegalStateException("activation transition time is not finalized");
        }
        return copy(transitionAt);
    }

    Date provisionalTransitionAt(Object ownerCapability) {
        requireOwner(ownerCapability);
        return copy(transitionAt);
    }

    Date finalizeTransitionAt(Object ownerCapability, Date value) {
        requireOwner(ownerCapability);
        if (phase != Phase.LOCKED
                || transitionAtFinalized
                || value == null
                || transitionAt == null) {
            throw new IllegalStateException("activation transition time cannot be finalized");
        }
        transitionAt = copy(value.before(transitionAt) ? transitionAt : value);
        transitionAtFinalized = true;
        return copy(transitionAt);
    }

    MemberImage memberImage(Object ownerCapability) {
        requireOwner(ownerCapability);
        return memberImage;
    }

    EntitlementImage entitlementImage(Object ownerCapability) {
        requireOwner(ownerCapability);
        return entitlementImage;
    }

    PlanImage planImage(Object ownerCapability) {
        requireOwner(ownerCapability);
        return planImage;
    }

    Object transactionMarker(Object ownerCapability) {
        requireOwner(ownerCapability);
        return transactionMarker;
    }

    Object boardTransactionResource(Object ownerCapability) {
        requireOwner(ownerCapability);
        return boardTransactionResource;
    }

    Object boardPhysicalConnection(Object ownerCapability) {
        requireOwner(ownerCapability);
        return boardPhysicalConnection;
    }

    Object transactionSynchronization(Object ownerCapability) {
        requireOwner(ownerCapability);
        return transactionSynchronization;
    }

    void attachTransactionSynchronization(
            Object ownerCapability,
            Object synchronization) {
        requireOwner(ownerCapability);
        if (transactionSynchronization != null) {
            throw new IllegalStateException("transaction synchronization already attached");
        }
        transactionSynchronization = synchronization;
    }

    void attachTrustedBaselineSynchronizations(
            Object ownerCapability,
            List<?> synchronizations) {
        requireOwner(ownerCapability);
        if (trustedBaselineAttached || synchronizations == null) {
            throw new IllegalStateException("trusted synchronization baseline already attached or missing");
        }
        trustedBaselineSynchronizations = List.copyOf(synchronizations);
        trustedBaselineAttached = true;
    }

    List<Object> trustedBaselineSynchronizations(Object ownerCapability) {
        requireOwner(ownerCapability);
        return trustedBaselineSynchronizations;
    }

    void attachFinalizerSynchronization(
            Object ownerCapability,
            Object synchronization) {
        requireOwner(ownerCapability);
        if (finalizerSynchronization != null || synchronization == null) {
            throw new IllegalStateException("transaction finalizer already attached or missing");
        }
        finalizerSynchronization = synchronization;
    }

    Object finalizerSynchronization(Object ownerCapability) {
        requireOwner(ownerCapability);
        return finalizerSynchronization;
    }

    boolean isOwnedBy(Object candidate) {
        return ownerToken == candidate;
    }

    boolean isOwnedByCurrentThread() {
        return ownerThread == Thread.currentThread();
    }

    synchronized Phase phase() {
        return phase;
    }

    synchronized void advance(Object ownerCapability, Phase expected, Phase next) {
        requireOwner(ownerCapability);
        if (phase != expected) {
            throw new IllegalStateException("invalid activation lease phase");
        }
        phase = next;
    }

    synchronized void invalidate(Object ownerCapability) {
        requireOwner(ownerCapability);
        phase = Phase.INVALID;
    }

    private void requireOwner(Object candidate) {
        if (ownerToken != candidate) {
            throw new IllegalStateException("activation lease owner capability mismatch");
        }
    }

    static final class MemberImage {
        final Long memberId;
        final Long tenantId;
        final String tenantName;
        final Long userId;
        final String memberRole;
        final Integer status;
        final String delFlag;

        MemberImage(BoardEnterpriseMemberScope value) {
            this.memberId = value.getMemberId();
            this.tenantId = value.getTenantId();
            this.tenantName = value.getTenantName();
            this.userId = value.getUserId();
            this.memberRole = value.getMemberRole();
            this.status = value.getStatus();
            this.delFlag = value.getDelFlag();
        }
    }

    static final class EntitlementImage {
        final Long id;
        final Long tenantId;
        final Long memberId;
        final Long userId;
        final String productCode;
        final String planCode;
        final String status;
        final String connectorBindingId;
        final Date connectorVerifiedAt;
        final Date validFrom;
        final Date validUntil;
        final Long version;
        final Date createdAt;
        final Date updatedAt;

        EntitlementImage(BoardProductEntitlement value) {
            this.id = value.getId();
            this.tenantId = value.getTenantId();
            this.memberId = value.getMemberId();
            this.userId = value.getUserId();
            this.productCode = value.getProductCode();
            this.planCode = value.getPlanCode();
            this.status = value.getStatus();
            this.connectorBindingId = value.getConnectorBindingId();
            this.connectorVerifiedAt = copy(value.getConnectorVerifiedAt());
            this.validFrom = copy(value.getValidFrom());
            this.validUntil = copy(value.getValidUntil());
            this.version = value.getVersion();
            this.createdAt = copy(value.getCreatedAt());
            this.updatedAt = copy(value.getUpdatedAt());
        }
    }

    static final class PlanImage {
        final String productCode;
        final String planCode;
        final Boolean vip;
        final Boolean connectorRequired;
        final Integer dailyMeetingLimit;
        final Integer agendaLimit;
        final Integer seatLimit;
        final Boolean secretaryEnabled;
        final String status;

        PlanImage(BoardProductPlan value) {
            this.productCode = value.getProductCode();
            this.planCode = value.getPlanCode();
            this.vip = value.getVip();
            this.connectorRequired = value.getConnectorRequired();
            this.dailyMeetingLimit = value.getDailyMeetingLimit();
            this.agendaLimit = value.getAgendaLimit();
            this.seatLimit = value.getSeatLimit();
            this.secretaryEnabled = value.getSecretaryEnabled();
            this.status = value.getStatus();
        }
    }

    BoardConnectorBindingSnapshot appliedSnapshot(Object ownerCapability) {
        requireOwner(ownerCapability);
        BoardConnectorBinding binding = appliedBinding;
        if (binding == null) {
            throw new IllegalStateException("binding has not been applied");
        }
        return new BoardConnectorBindingSnapshot(
                binding.getBindingId(),
                binding.getTenantId(),
                binding.getMemberId(),
                binding.getUserId(),
                binding.getProductCode(),
                binding.getStatus(),
                binding.getVerifiedAt(),
                binding.getValidUntil(),
                binding.getVersion());
    }

    private static Date copy(Date value) {
        return value == null ? null : new Date(value.getTime());
    }
}
