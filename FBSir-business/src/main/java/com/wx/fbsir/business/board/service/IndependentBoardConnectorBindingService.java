package com.wx.fbsir.business.board.service;

import com.wx.fbsir.business.board.config.IndependentBoardConnectorProperties;
import com.wx.fbsir.business.board.domain.BoardConnectorBinding;
import com.wx.fbsir.business.board.domain.BoardConnectorBindingReceipt;
import com.wx.fbsir.business.board.domain.BoardEnterpriseMemberScope;
import com.wx.fbsir.business.board.domain.BoardProductEntitlement;
import com.wx.fbsir.business.board.domain.BoardProductPlan;
import com.wx.fbsir.business.board.dto.BoardConnectorBindingKey;
import com.wx.fbsir.business.board.dto.BoardConnectorBindingRevokeRequest;
import com.wx.fbsir.business.board.dto.BoardConnectorBindingSnapshot;
import com.wx.fbsir.business.board.dto.BoardConnectorProtectedRequestAttestation;
import com.wx.fbsir.business.board.mapper.IndependentBoardMapper;
import com.wx.fbsir.common.exception.ServiceException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IndependentBoardConnectorBindingService implements BoardConnectorBindingPort {
    public static final String SOURCE_CODE = "WORKBUDDY";
    public static final String CONNECTOR_CODE = "fbs-connector";
    public static final String RESOURCE_URI = "https://api2.u3w.com/fbs-mcp/mcp";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_REVOKED = "REVOKED";
    public static final String STATUS_COMPROMISED = "COMPROMISED";
    public static final String VERIFY_INITIALIZE = "MCP_INITIALIZE";
    public static final String VERIFY_TOOLS_LIST = "MCP_TOOLS_LIST";
    public static final int BATCH_LIMIT = 100;
    public static final Set<String> REQUIRED_SCOPES = Collections.unmodifiableSet(
            new LinkedHashSet<>(List.of(
                    "identity.read",
                    "entitlement.read",
                    "board.meeting.reserve",
                    "board.receipt.write")));

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern CLIENT_ID = Pattern.compile("[\\x21-\\x7e]{1,191}");
    private static final Set<String> KNOWN_STATUSES = Set.of(
            STATUS_ACTIVE, STATUS_REVOKED, STATUS_COMPROMISED);
    private static final Set<String> KNOWN_VERIFICATION_METHODS = Set.of(
            VERIFY_INITIALIZE, VERIFY_TOOLS_LIST);

    private final IndependentBoardMapper mapper;
    private final IndependentBoardConnectorProperties properties;
    private final Clock clock;

    @Autowired
    public IndependentBoardConnectorBindingService(
            IndependentBoardMapper mapper,
            IndependentBoardConnectorProperties properties) {
        this(mapper, properties, Clock.system(IndependentBoardEntitlementService.INITIAL_TENANT_ZONE));
    }

    IndependentBoardConnectorBindingService(
            IndependentBoardMapper mapper,
            IndependentBoardConnectorProperties properties,
            Clock clock) {
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BoardConnectorBindingSnapshot confirmProtectedRequest(
            BoardConnectorProtectedRequestAttestation attestation,
            Long actorUserId) {
        requireConfiguredTrustPolicy();
        validateAttestation(attestation, actorUserId);

        BoardEnterpriseMemberScope member = mapper.selectExactActiveMemberForUpdate(
                attestation.tenantId(), attestation.memberId(), attestation.userId());
        requireExactMember(member, attestation.tenantId(), attestation.memberId(), attestation.userId());

        BoardProductEntitlement entitlement = mapper.selectEntitlementForUpdate(
                attestation.tenantId(), attestation.memberId(),
                IndependentBoardEntitlementService.PRODUCT_CODE);
        requireCurrentVipEntitlement(entitlement, attestation, Date.from(clock.instant()));

        BoardConnectorBinding existing = mapper.selectConnectorBindingForUpdate(
                attestation.tenantId(), attestation.memberId(), attestation.userId(),
                IndependentBoardEntitlementService.PRODUCT_CODE, SOURCE_CODE, CONNECTOR_CODE);
        if (existing != null) {
            List<String> scopes = mapper.selectConnectorBindingScopesForUpdate(existing.getBindingId());
            if (scopes == null) {
                throw new ServiceException("BOARD_CONNECTOR_BINDING_CURRENT_READ_FAILED", 500);
            }
            existing.setScopes(scopes);
            requireExistingBindingCompatible(existing, attestation);
            return toSnapshot(existing);
        }

        Date now = Date.from(clock.instant());
        BoardConnectorBinding binding = new BoardConnectorBinding();
        binding.setBindingId(UUID.randomUUID().toString());
        binding.setTenantId(attestation.tenantId());
        binding.setMemberId(attestation.memberId());
        binding.setUserId(attestation.userId());
        binding.setProductCode(IndependentBoardEntitlementService.PRODUCT_CODE);
        binding.setSourceCode(SOURCE_CODE);
        binding.setConnectorCode(CONNECTOR_CODE);
        binding.setIssuerUri(attestation.issuerUri());
        binding.setResourceUri(attestation.resourceUri());
        binding.setClientId(attestation.clientId());
        binding.setPrincipalSubjectDigest(attestation.principalSubjectDigest());
        binding.setStatus(STATUS_ACTIVE);
        binding.setVerificationMethod(attestation.verificationMethod());
        binding.setEvidenceDigest(attestation.evidenceDigest());
        binding.setVerifiedAt(now);
        binding.setLastSeenAt(now);
        binding.setValidUntil(copy(attestation.validUntil()));
        binding.setVersion(1L);
        binding.setScopes(sortedScopes(attestation.scopes()));
        try {
            if (mapper.insertConnectorBinding(binding) != 1) {
                throw new ServiceException("BOARD_CONNECTOR_BINDING_WRITE_FAILED", 500);
            }
            for (String scope : binding.getScopes()) {
                if (mapper.insertConnectorBindingScope(binding.getBindingId(), scope, now) != 1) {
                    throw new ServiceException("BOARD_CONNECTOR_BINDING_SCOPE_WRITE_FAILED", 500);
                }
            }
        } catch (DuplicateKeyException | PessimisticLockingFailureException conflict) {
            throw new ServiceException("BOARD_CONNECTOR_BINDING_CONFLICT", 409);
        }
        insertReceipt(binding, actorUserId, "CONNECTOR_BINDING_VERIFIED", now);
        return toSnapshot(binding);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean hasAuthoritativeCurrentBinding(
            Long tenantId,
            Long memberId,
            Long userId,
            String productCode,
            boolean lockBinding) {
        requirePositive(tenantId, "TENANT_REQUIRED");
        requirePositive(memberId, "MEMBER_REQUIRED");
        requirePositive(userId, "USER_REQUIRED");
        requireProduct(productCode);
        if (!isTrustPolicyConfigured()) {
            return false;
        }
        if (!hasCurrentVipEntitlement(tenantId, memberId, userId, productCode, lockBinding)) {
            return false;
        }
        BoardConnectorBinding binding = lockBinding
                ? mapper.selectConnectorBindingForUpdate(
                        tenantId, memberId, userId, productCode, SOURCE_CODE, CONNECTOR_CODE)
                : mapper.selectConnectorBinding(
                        tenantId, memberId, userId, productCode, SOURCE_CODE, CONNECTOR_CODE);
        if (binding == null) {
            return false;
        }
        List<String> scopes = lockBinding
                ? mapper.selectConnectorBindingScopesForUpdate(binding.getBindingId())
                : mapper.selectConnectorBindingScopes(binding.getBindingId());
        if (scopes == null) {
            throw new ServiceException("BOARD_CONNECTOR_BINDING_CURRENT_READ_FAILED", 500);
        }
        binding.setScopes(scopes);
        validateProjectionScope(binding, tenantId, memberId, userId, productCode);
        return isAuthoritativeCurrent(binding, Date.from(clock.instant()));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<BoardConnectorBindingKey> selectAuthoritativeCurrentBindingKeys(
            Long tenantId,
            String productCode) {
        requirePositive(tenantId, "TENANT_REQUIRED");
        requireProduct(productCode);
        if (!isTrustPolicyConfigured()) {
            return Set.of();
        }
        BoardProductPlan plan = mapper.selectActivePlan(
                productCode, IndependentBoardEntitlementService.VIP_PLAN);
        if (plan == null) {
            throw new ServiceException("BOARD_PLAN_CONTRACT_DRIFT", 500);
        }
        requireCurrentVipPlan(plan);
        List<BoardConnectorBinding> rows = mapper.selectConnectorBindingsByTenant(
                tenantId, productCode, SOURCE_CODE, CONNECTOR_CODE);
        if (rows == null || rows.size() > BATCH_LIMIT) {
            throw new ServiceException("BOARD_CONNECTOR_BINDING_CURRENT_READ_FAILED", 500);
        }
        Date now = Date.from(clock.instant());
        Set<BoardConnectorBindingKey> result = new HashSet<>();
        Set<String> bindingIds = new HashSet<>();
        for (BoardConnectorBinding binding : rows) {
            if (binding == null || !bindingIds.add(binding.getBindingId())) {
                throw new ServiceException("BOARD_CONNECTOR_BINDING_SCOPE_INVALID", 500);
            }
            validateProjectionScope(
                    binding, tenantId, binding.getMemberId(), binding.getUserId(), productCode);
            if (isAuthoritativeCurrent(binding, now)) {
                BoardConnectorBindingKey key = new BoardConnectorBindingKey(
                        tenantId, binding.getMemberId(), binding.getUserId(), productCode);
                if (!result.add(key)) {
                    throw new ServiceException("BOARD_CONNECTOR_BINDING_SCOPE_INVALID", 500);
                }
            }
        }
        return Set.copyOf(result);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void revokeForEntitlement(
            Long tenantId,
            Long memberId,
            Long userId,
            String productCode,
            Long actorUserId) {
        requirePositive(actorUserId, "AUTHENTICATED_PRINCIPAL_REQUIRED");
        revokeLockedBinding(tenantId, memberId, userId, productCode, null, null, actorUserId, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public BoardConnectorBindingSnapshot revoke(
            BoardConnectorBindingRevokeRequest request,
            Long actorUserId) {
        requirePositive(actorUserId, "AUTHENTICATED_PRINCIPAL_REQUIRED");
        if (request == null) {
            throw new ServiceException("CONNECTOR_BINDING_REVOKE_REQUEST_REQUIRED", 400);
        }
        requirePositive(request.tenantId(), "TENANT_REQUIRED");
        requirePositive(request.memberId(), "MEMBER_REQUIRED");
        requirePositive(request.userId(), "USER_REQUIRED");
        requirePositive(request.expectedVersion(), "CONNECTOR_BINDING_EXPECTED_VERSION_REQUIRED");
        requireUuid(request.bindingId(), "CONNECTOR_BINDING_ID_INVALID");
        if (Objects.equals(request.expectedVersion(), Long.MAX_VALUE)) {
            throw new ServiceException("CONNECTOR_BINDING_EXPECTED_VERSION_OUT_OF_RANGE", 400);
        }

        BoardProductEntitlement entitlement = mapper.selectEntitlementForUpdate(
                request.tenantId(), request.memberId(), IndependentBoardEntitlementService.PRODUCT_CODE);
        if (entitlement == null
                || !Objects.equals(entitlement.getTenantId(), request.tenantId())
                || !Objects.equals(entitlement.getMemberId(), request.memberId())
                || !Objects.equals(entitlement.getUserId(), request.userId())
                || !Objects.equals(entitlement.getProductCode(), IndependentBoardEntitlementService.PRODUCT_CODE)) {
            throw new ServiceException("CONNECTOR_BINDING_SCOPE_OR_VERSION_CONFLICT", 409);
        }
        BoardConnectorBinding revoked = revokeLockedBinding(
                request.tenantId(), request.memberId(), request.userId(),
                IndependentBoardEntitlementService.PRODUCT_CODE,
                request.bindingId(), request.expectedVersion(), actorUserId, true);
        return toSnapshot(revoked);
    }

    private BoardConnectorBinding revokeLockedBinding(
            Long tenantId,
            Long memberId,
            Long userId,
            String productCode,
            String expectedBindingId,
            Long expectedVersion,
            Long actorUserId,
            boolean strict) {
        requirePositive(tenantId, "TENANT_REQUIRED");
        requirePositive(memberId, "MEMBER_REQUIRED");
        requirePositive(userId, "USER_REQUIRED");
        requireProduct(productCode);
        BoardConnectorBinding binding = mapper.selectConnectorBindingForUpdate(
                tenantId, memberId, userId, productCode, SOURCE_CODE, CONNECTOR_CODE);
        if (binding == null || !Objects.equals(binding.getStatus(), STATUS_ACTIVE)) {
            if (strict) {
                throw new ServiceException("CONNECTOR_BINDING_SCOPE_OR_VERSION_CONFLICT", 409);
            }
            return binding;
        }
        List<String> scopes = mapper.selectConnectorBindingScopesForUpdate(binding.getBindingId());
        if (scopes == null) {
            throw new ServiceException("BOARD_CONNECTOR_BINDING_CURRENT_READ_FAILED", 500);
        }
        binding.setScopes(scopes);
        validateProjectionScope(binding, tenantId, memberId, userId, productCode);
        if (strict && (!Objects.equals(binding.getBindingId(), expectedBindingId)
                || !Objects.equals(binding.getVersion(), expectedVersion))) {
            throw new ServiceException("CONNECTOR_BINDING_SCOPE_OR_VERSION_CONFLICT", 409);
        }
        if (binding.getVersion() == null || Objects.equals(binding.getVersion(), Long.MAX_VALUE)) {
            throw new ServiceException("CONNECTOR_BINDING_VERSION_CONFLICT", 409);
        }
        Date now = Date.from(clock.instant());
        Date revokedAt = revocationTime(binding, now);
        Long previousVersion = binding.getVersion();
        binding.setStatus(STATUS_REVOKED);
        binding.setRevokedAt(revokedAt);
        binding.setVersion(previousVersion + 1L);
        if (mapper.revokeConnectorBindingIfVersion(binding, previousVersion) != 1) {
            throw new ServiceException("CONNECTOR_BINDING_VERSION_CONFLICT", 409);
        }
        insertReceipt(binding, actorUserId, "CONNECTOR_BINDING_REVOKED", revokedAt);
        return binding;
    }

    private void validateAttestation(
            BoardConnectorProtectedRequestAttestation attestation,
            Long actorUserId) {
        requirePositive(actorUserId, "AUTHENTICATED_PRINCIPAL_REQUIRED");
        if (attestation == null) {
            throw new ServiceException("CONNECTOR_ATTESTATION_REQUIRED", 400);
        }
        requirePositive(attestation.tenantId(), "TENANT_REQUIRED");
        requirePositive(attestation.memberId(), "MEMBER_REQUIRED");
        requirePositive(attestation.userId(), "USER_REQUIRED");
        if (!Objects.equals(actorUserId, attestation.userId())) {
            throw new ServiceException("CONNECTOR_ATTESTATION_ACTOR_MISMATCH", 403);
        }
        if (!Objects.equals(attestation.issuerUri(), properties.getIssuerUri())) {
            throw new ServiceException("CONNECTOR_ISSUER_MISMATCH", 403);
        }
        if (!Objects.equals(attestation.resourceUri(), properties.getResourceUri())) {
            throw new ServiceException("CONNECTOR_RESOURCE_MISMATCH", 403);
        }
        if (attestation.clientId() == null || !CLIENT_ID.matcher(attestation.clientId()).matches()) {
            throw new ServiceException("CONNECTOR_CLIENT_ID_INVALID", 400);
        }
        requireSha256(attestation.principalSubjectDigest(), "CONNECTOR_SUBJECT_DIGEST_INVALID");
        requireSha256(attestation.evidenceDigest(), "CONNECTOR_EVIDENCE_DIGEST_INVALID");
        if (!KNOWN_VERIFICATION_METHODS.contains(attestation.verificationMethod())) {
            throw new ServiceException("CONNECTOR_VERIFICATION_METHOD_INVALID", 400);
        }
        List<String> scopes = attestation.scopes();
        if (scopes == null || scopes.size() != REQUIRED_SCOPES.size()
                || new HashSet<>(scopes).size() != scopes.size()
                || !new HashSet<>(scopes).equals(REQUIRED_SCOPES)) {
            throw new ServiceException("CONNECTOR_SCOPE_SET_INVALID", 403);
        }
        Date now = Date.from(clock.instant());
        if (attestation.validUntil() == null || !attestation.validUntil().after(now)) {
            throw new ServiceException("CONNECTOR_CREDENTIAL_EXPIRED", 403);
        }
    }

    private boolean hasCurrentVipEntitlement(
            Long tenantId,
            Long memberId,
            Long userId,
            String productCode,
            boolean lockEntitlement) {
        BoardEnterpriseMemberScope member = lockEntitlement
                ? mapper.selectExactActiveMemberForUpdate(tenantId, memberId, userId)
                : mapper.selectActiveContext(tenantId, userId);
        if (member == null) {
            return false;
        }
        requireExactMember(member, tenantId, memberId, userId);

        BoardProductEntitlement entitlement = lockEntitlement
                ? mapper.selectEntitlementForUpdate(tenantId, memberId, productCode)
                : mapper.selectEntitlement(tenantId, memberId, userId, productCode);
        if (entitlement == null) {
            return false;
        }
        if (!Objects.equals(entitlement.getTenantId(), tenantId)
                || !Objects.equals(entitlement.getMemberId(), memberId)
                || !Objects.equals(entitlement.getUserId(), userId)
                || !Objects.equals(entitlement.getProductCode(), productCode)) {
            throw new ServiceException("BOARD_CONNECTOR_ENTITLEMENT_SCOPE_INVALID", 500);
        }
        Date now = Date.from(clock.instant());
        boolean currentEntitlement = Objects.equals(
                        entitlement.getPlanCode(), IndependentBoardEntitlementService.VIP_PLAN)
                && Objects.equals(entitlement.getStatus(), STATUS_ACTIVE)
                && entitlement.getValidFrom() != null
                && !entitlement.getValidFrom().after(now)
                && (entitlement.getValidUntil() == null || entitlement.getValidUntil().after(now));
        if (!currentEntitlement) {
            return false;
        }
        BoardProductPlan plan = mapper.selectActivePlan(
                productCode, IndependentBoardEntitlementService.VIP_PLAN);
        if (plan == null) {
            throw new ServiceException("BOARD_PLAN_CONTRACT_DRIFT", 500);
        }
        requireCurrentVipPlan(plan);
        return true;
    }

    private void requireExistingBindingCompatible(
            BoardConnectorBinding binding,
            BoardConnectorProtectedRequestAttestation attestation) {
        validateProjectionScope(
                binding, attestation.tenantId(), attestation.memberId(), attestation.userId(),
                IndependentBoardEntitlementService.PRODUCT_CODE);
        if (!Objects.equals(binding.getStatus(), STATUS_ACTIVE)
                || !Objects.equals(binding.getIssuerUri(), attestation.issuerUri())
                || !Objects.equals(binding.getResourceUri(), attestation.resourceUri())
                || !Objects.equals(binding.getClientId(), attestation.clientId())
                || !Objects.equals(binding.getPrincipalSubjectDigest(), attestation.principalSubjectDigest())
                || !new HashSet<>(binding.getScopes()).equals(REQUIRED_SCOPES)
                || binding.getScopes().size() != REQUIRED_SCOPES.size()) {
            throw new ServiceException("BOARD_CONNECTOR_BINDING_CONFLICT", 409);
        }
        if (!isAuthoritativeCurrent(binding, Date.from(clock.instant()))) {
            throw new ServiceException("BOARD_CONNECTOR_BINDING_NOT_CURRENT", 409);
        }
    }

    private void requireCurrentVipEntitlement(
            BoardProductEntitlement entitlement,
            BoardConnectorProtectedRequestAttestation attestation,
            Date now) {
        if (entitlement == null
                || !Objects.equals(entitlement.getTenantId(), attestation.tenantId())
                || !Objects.equals(entitlement.getMemberId(), attestation.memberId())
                || !Objects.equals(entitlement.getUserId(), attestation.userId())
                || !Objects.equals(entitlement.getProductCode(), IndependentBoardEntitlementService.PRODUCT_CODE)
                || !Objects.equals(entitlement.getPlanCode(), IndependentBoardEntitlementService.VIP_PLAN)
                || !Objects.equals(entitlement.getStatus(), STATUS_ACTIVE)
                || entitlement.getValidFrom() == null
                || entitlement.getValidFrom().after(now)
                || (entitlement.getValidUntil() != null && !entitlement.getValidUntil().after(now))) {
            throw new ServiceException("CONNECTOR_VIP_ENTITLEMENT_NOT_CURRENT", 403);
        }
        BoardProductPlan plan = mapper.selectActivePlan(
                IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.VIP_PLAN);
        if (plan == null) {
            throw new ServiceException("BOARD_PLAN_CONTRACT_DRIFT", 500);
        }
        requireCurrentVipPlan(plan);
    }

    private void requireCurrentVipPlan(BoardProductPlan plan) {
        if (!Objects.equals(plan.getProductCode(), IndependentBoardEntitlementService.PRODUCT_CODE)
                || !Objects.equals(plan.getPlanCode(), IndependentBoardEntitlementService.VIP_PLAN)
                || !Boolean.TRUE.equals(plan.getVip())
                || !Boolean.TRUE.equals(plan.getConnectorRequired())
                || !Objects.equals(plan.getDailyMeetingLimit(), 5)
                || !Objects.equals(plan.getAgendaLimit(), 30)
                || plan.getSeatLimit() != null
                || !Boolean.TRUE.equals(plan.getSecretaryEnabled())
                || !Objects.equals(plan.getStatus(), STATUS_ACTIVE)) {
            throw new ServiceException("BOARD_PLAN_CONTRACT_DRIFT", 500);
        }
    }

    private boolean isAuthoritativeCurrent(BoardConnectorBinding binding, Date now) {
        if (!Objects.equals(binding.getStatus(), STATUS_ACTIVE)) {
            return false;
        }
        if (!Objects.equals(binding.getIssuerUri(), properties.getIssuerUri())
                || !Objects.equals(binding.getResourceUri(), properties.getResourceUri())) {
            return false;
        }
        if (binding.getVerifiedAt().after(now)
                || binding.getLastSeenAt().after(now)
                || !binding.getValidUntil().after(now)
                || binding.getRevokedAt() != null) {
            return false;
        }
        List<String> scopes = binding.getScopes();
        return scopes != null
                && scopes.size() == REQUIRED_SCOPES.size()
                && new HashSet<>(scopes).size() == scopes.size()
                && new HashSet<>(scopes).equals(REQUIRED_SCOPES);
    }

    private void validateProjectionScope(
            BoardConnectorBinding binding,
            Long tenantId,
            Long memberId,
            Long userId,
            String productCode) {
        if (binding == null
                || binding.getId() == null || binding.getId() <= 0L
                || !isUuid(binding.getBindingId())
                || !Objects.equals(binding.getTenantId(), tenantId)
                || !Objects.equals(binding.getMemberId(), memberId)
                || !Objects.equals(binding.getUserId(), userId)
                || binding.getMemberId() == null || binding.getMemberId() <= 0L
                || binding.getUserId() == null || binding.getUserId() <= 0L
                || !Objects.equals(binding.getProductCode(), productCode)
                || !Objects.equals(binding.getSourceCode(), SOURCE_CODE)
                || !Objects.equals(binding.getConnectorCode(), CONNECTOR_CODE)
                || binding.getIssuerUri() == null || binding.getIssuerUri().isBlank()
                || binding.getResourceUri() == null || binding.getResourceUri().isBlank()
                || binding.getClientId() == null || !CLIENT_ID.matcher(binding.getClientId()).matches()
                || !isSha256(binding.getPrincipalSubjectDigest())
                || !isSha256(binding.getEvidenceDigest())
                || !KNOWN_STATUSES.contains(binding.getStatus())
                || !KNOWN_VERIFICATION_METHODS.contains(binding.getVerificationMethod())
                || binding.getVerifiedAt() == null
                || binding.getLastSeenAt() == null
                || binding.getLastSeenAt().before(binding.getVerifiedAt())
                || binding.getValidUntil() == null
                || !binding.getValidUntil().after(binding.getLastSeenAt())
                || binding.getVersion() == null || binding.getVersion() <= 0L
                || (Objects.equals(binding.getStatus(), STATUS_ACTIVE) && binding.getRevokedAt() != null)
                || (!Objects.equals(binding.getStatus(), STATUS_ACTIVE) && binding.getRevokedAt() == null)
                || (binding.getRevokedAt() != null && binding.getRevokedAt().before(binding.getLastSeenAt()))) {
            throw new ServiceException("BOARD_CONNECTOR_BINDING_SCOPE_INVALID", 500);
        }
    }

    private void requireExactMember(
            BoardEnterpriseMemberScope member,
            Long tenantId,
            Long memberId,
            Long userId) {
        if (member == null
                || !Objects.equals(member.getTenantId(), tenantId)
                || !Objects.equals(member.getMemberId(), memberId)
                || !Objects.equals(member.getUserId(), userId)
                || !Objects.equals(member.getStatus(), 1)
                || !Objects.equals(member.getDelFlag(), "0")) {
            throw new ServiceException("TENANT_MEMBER_USER_SCOPE_INVALID", 403);
        }
    }

    private void insertReceipt(
            BoardConnectorBinding binding,
            Long actorUserId,
            String action,
            Date createdAt) {
        BoardConnectorBindingReceipt receipt = new BoardConnectorBindingReceipt();
        receipt.setReceiptId(UUID.randomUUID().toString());
        receipt.setBindingId(binding.getBindingId());
        receipt.setTenantId(binding.getTenantId());
        receipt.setMemberId(binding.getMemberId());
        receipt.setUserId(binding.getUserId());
        receipt.setActorUserId(actorUserId);
        receipt.setAction(action);
        receipt.setPayloadDigest(bindingDigest(binding, actorUserId, action));
        receipt.setEvidenceLevel("ACTION_COMPLETED");
        receipt.setCreatedAt(createdAt);
        if (mapper.insertConnectorBindingReceipt(receipt) != 1) {
            throw new ServiceException("BOARD_CONNECTOR_BINDING_RECEIPT_WRITE_FAILED", 500);
        }
    }

    private String bindingDigest(
            BoardConnectorBinding binding,
            Long actorUserId,
            String action) {
        List<String> scopes = sortedScopes(binding.getScopes());
        String canonical = String.join("\n",
                binding.getBindingId(),
                String.valueOf(binding.getTenantId()),
                String.valueOf(binding.getMemberId()),
                String.valueOf(binding.getUserId()),
                binding.getProductCode(),
                binding.getSourceCode(),
                binding.getConnectorCode(),
                binding.getIssuerUri(),
                binding.getResourceUri(),
                binding.getClientId(),
                binding.getPrincipalSubjectDigest(),
                String.join(" ", scopes),
                binding.getVerificationMethod(),
                binding.getEvidenceDigest(),
                binding.getStatus(),
                String.valueOf(binding.getVerifiedAt().getTime()),
                String.valueOf(binding.getLastSeenAt().getTime()),
                String.valueOf(binding.getValidUntil().getTime()),
                binding.getRevokedAt() == null ? "" : String.valueOf(binding.getRevokedAt().getTime()),
                String.valueOf(binding.getVersion()),
                String.valueOf(actorUserId),
                action);
        return BoardDigest.sha256(canonical);
    }

    private BoardConnectorBindingSnapshot toSnapshot(BoardConnectorBinding binding) {
        return new BoardConnectorBindingSnapshot(
                binding.getBindingId(), binding.getTenantId(), binding.getMemberId(),
                binding.getUserId(), binding.getProductCode(), binding.getStatus(),
                copy(binding.getVerifiedAt()), copy(binding.getValidUntil()), binding.getVersion());
    }

    private Date revocationTime(BoardConnectorBinding binding, Date now) {
        long lastSeenMillis = binding.getLastSeenAt().getTime();
        if (lastSeenMillis == Long.MAX_VALUE) {
            throw new ServiceException("CONNECTOR_BINDING_VALIDITY_CONFLICT", 409);
        }
        return new Date(Math.max(now.getTime(), lastSeenMillis + 1L));
    }

    private void requireConfiguredTrustPolicy() {
        if (!isTrustPolicyConfigured()) {
            throw new ServiceException("CONNECTOR_TRUST_POLICY_NOT_CONFIGURED", 503);
        }
    }

    private boolean isTrustPolicyConfigured() {
        return isExactHttpsUri(properties.getIssuerUri())
                && Objects.equals(properties.getResourceUri(), RESOURCE_URI)
                && isExactHttpsUri(properties.getResourceUri());
    }

    private boolean isExactHttpsUri(String value) {
        if (value == null || value.isBlank() || value.length() > 512
                || !Objects.equals(value, value.trim())
                || !StandardCharsets.US_ASCII.newEncoder().canEncode(value)) {
            return false;
        }
        try {
            URI uri = new URI(value);
            return Objects.equals(uri.getScheme(), "https")
                    && uri.getHost() != null
                    && uri.getUserInfo() == null
                    && uri.getQuery() == null
                    && uri.getFragment() == null
                    && uri.normalize().toString().equals(value);
        } catch (URISyntaxException invalid) {
            return false;
        }
    }

    private List<String> sortedScopes(List<String> scopes) {
        List<String> sorted = new ArrayList<>(scopes == null ? List.of() : scopes);
        Collections.sort(sorted);
        return sorted;
    }

    private void requireProduct(String productCode) {
        if (!Objects.equals(productCode, IndependentBoardEntitlementService.PRODUCT_CODE)) {
            throw new ServiceException("BOARD_CONNECTOR_PRODUCT_SCOPE_INVALID", 403);
        }
    }

    private void requirePositive(Long value, String code) {
        if (value == null || value <= 0L) {
            throw new ServiceException(code, 400);
        }
    }

    private void requireSha256(String value, String code) {
        if (!isSha256(value)) {
            throw new ServiceException(code, 400);
        }
    }

    private boolean isSha256(String value) {
        return value != null && SHA256.matcher(value).matches();
    }

    private void requireUuid(String value, String code) {
        if (!isUuid(value)) {
            throw new ServiceException(code, 400);
        }
    }

    private boolean isUuid(String value) {
        if (value == null) {
            return false;
        }
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private Date copy(Date value) {
        return value == null ? null : new Date(value.getTime());
    }
}
