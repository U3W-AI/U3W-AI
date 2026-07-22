package com.wx.fbsir.business.board.service;

import com.wx.fbsir.business.board.config.IndependentBoardConnectorProperties;
import com.wx.fbsir.business.board.domain.BoardConnectorBinding;
import com.wx.fbsir.business.board.domain.BoardConnectorBindingReceipt;
import com.wx.fbsir.business.board.domain.BoardEnterpriseAuthority;
import com.wx.fbsir.business.board.domain.BoardEnterpriseMemberScope;
import com.wx.fbsir.business.board.domain.BoardProductEntitlement;
import com.wx.fbsir.business.board.domain.BoardProductPlan;
import com.wx.fbsir.business.board.dto.BoardConnectorBindingKey;
import com.wx.fbsir.business.board.dto.BoardConnectorBindingRevokeRequest;
import com.wx.fbsir.business.board.dto.BoardConnectorBindingSnapshot;
import com.wx.fbsir.business.board.dto.BoardConnectorProtectedRequestAttestation;
import com.wx.fbsir.business.board.mapper.IndependentBoardMapper;
import com.wx.fbsir.business.board.oauth.BoardOAuthCrypto;
import com.wx.fbsir.business.board.oauth.BoardOAuthConsentIntent;
import com.wx.fbsir.business.board.oauth.BoardOAuthProfile;
import com.wx.fbsir.business.board.oauth.BoardOAuthTokenFamilyCreatedReceiptFactory;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthAuthorizationCode;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthAuthorizationRequest;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthClient;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthReceipt;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthToken;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthTokenFamily;
import com.wx.fbsir.business.board.oauth.mapper.IndependentBoardOAuthMapper;
import com.wx.fbsir.business.board.oauth.service.BoardOAuthRefreshAuthorityPort;
import com.wx.fbsir.business.board.oauth.service.IndependentBoardOAuthClientRegistrationService;
import com.wx.fbsir.business.board.oauth.service.BoardOAuthTokenExchangeAuthorityPort;
import com.wx.fbsir.common.exception.ServiceException;
import java.sql.Connection;
import java.sql.SQLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.jdbc.datasource.ConnectionHolder;

@Service
public class IndependentBoardConnectorBindingService
        implements BoardConnectorBindingPort,
                BoardOAuthTokenExchangeAuthorityPort,
                BoardOAuthRefreshAuthorityPort {
    private enum OAuthActivationTransactionResource {
        KEY
    }

    public static final String SOURCE_CODE = "WORKBUDDY";
    public static final String CONNECTOR_CODE = "fbs-connector";
    public static final String RESOURCE_URI = "https://api2.u3w.com/fbs-mcp/mcp";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_REVOKED = "REVOKED";
    public static final String STATUS_COMPROMISED = "COMPROMISED";
    public static final String VERIFY_INITIALIZE = "MCP_INITIALIZE";
    public static final String VERIFY_TOOLS_LIST = "MCP_TOOLS_LIST";
    public static final int BATCH_LIMIT = 100;
    public static final int MAX_OAUTH_FAMILY_TOKEN_HISTORY = 512;
    public static final Set<String> REQUIRED_SCOPES = Collections.unmodifiableSet(
            new LinkedHashSet<>(List.of(
                    "identity.read",
                    "entitlement.read",
                    "board.meeting.reserve",
                    "board.receipt.write")));

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern CLIENT_ID = Pattern.compile("[\\x21-\\x7e]{1,191}");
    private static final Pattern OAUTH_CLIENT_ID =
            Pattern.compile("[A-Za-z0-9_-]{43,191}");
    private static final Pattern FAMILY_ID = Pattern.compile("[\\x21-\\x7e]{1,128}");
    private static final byte[] OAUTH_SCOPE_DIGEST =
            BoardOAuthCrypto.sha256Ascii(BoardOAuthProfile.CANONICAL_SCOPE);
    private static final Set<String> KNOWN_STATUSES = Set.of(
            STATUS_ACTIVE, STATUS_REVOKED, STATUS_COMPROMISED);
    private static final Set<String> KNOWN_VERIFICATION_METHODS = Set.of(
            VERIFY_INITIALIZE, VERIFY_TOOLS_LIST);
    private static final Set<String> TRUSTED_TRANSACTION_SYNCHRONIZATIONS = Set.of(
            "org.mybatis.spring.SqlSessionUtils$SqlSessionSynchronization",
            "org.springframework.jdbc.datasource.DataSourceUtils$ConnectionSynchronization");

    private final IndependentBoardMapper mapper;
    private final IndependentBoardOAuthMapper oauthMapper;
    private final IndependentBoardConnectorProperties properties;
    private final DataSource boardDataSource;
    private final Clock clock;
    private final Object oauthActivationOwnerToken = new Object();
    private final Object oauthRefreshOwnerToken = new Object();

    @Autowired
    public IndependentBoardConnectorBindingService(
            IndependentBoardMapper mapper,
            IndependentBoardOAuthMapper oauthMapper,
            IndependentBoardConnectorProperties properties,
            DataSource boardDataSource) {
        this(
                mapper,
                oauthMapper,
                properties,
                boardDataSource,
                Clock.system(IndependentBoardEntitlementService.INITIAL_TENANT_ZONE));
    }

    IndependentBoardConnectorBindingService(
            IndependentBoardMapper mapper,
            IndependentBoardOAuthMapper oauthMapper,
            IndependentBoardConnectorProperties properties,
            DataSource boardDataSource,
            Clock clock) {
        this.mapper = mapper;
        this.oauthMapper = oauthMapper;
        this.properties = properties;
        this.boardDataSource = boardDataSource;
        this.clock = clock;
    }

    @Override
    public BoardOAuthTokenExchangeAuthorityPort.LockResult lockForTokenExchange(
            Long tenantId,
            Long memberId,
            Long userId,
            String productCode,
            String sourceCode,
            String connectorCode,
            java.time.Instant lockRequestedAt) {
        Objects.requireNonNull(lockRequestedAt, "lockRequestedAt");

        // Lock-only sequence. Never fail early on inactive authority: the caller
        // may still need to contain a replayed, already-consumed code.
        BoardEnterpriseAuthority enterprise = mapper.selectEnterpriseSlotForUpdate(
                tenantId);
        BoardEnterpriseMemberScope member = mapper.selectMemberSlotForUpdate(
                tenantId, memberId);
        BoardProductEntitlement entitlement = mapper.selectEntitlementForUpdate(
                tenantId, memberId, productCode);
        BoardProductPlan plan = mapper.selectPlanSlotForUpdate(
                productCode, IndependentBoardEntitlementService.VIP_PLAN);
        BoardConnectorBinding binding = mapper.selectConnectorBindingSlotForUpdate(
                tenantId, memberId, productCode, sourceCode, connectorCode);
        List<String> scopes = null;
        if (binding != null && binding.getBindingId() != null) {
            scopes = mapper.selectConnectorBindingScopesForUpdate(binding.getBindingId());
        }

        java.time.Instant observedAt = java.time.Instant.ofEpochMilli(
                clock.instant().toEpochMilli());
        if (observedAt.isBefore(lockRequestedAt)) {
            observedAt = lockRequestedAt;
        }
        Date now = Date.from(observedAt);

        boolean enterpriseCurrent = isTokenExchangeEnterpriseCurrent(
                enterprise, tenantId);
        boolean memberCurrent = isTokenExchangeMemberCurrent(
                member, tenantId, memberId, userId);
        boolean entitlementCurrent = isTokenExchangeEntitlementCurrent(
                entitlement, tenantId, memberId, userId, productCode, now);
        boolean planCurrent = isTokenExchangePlanCurrent(plan, productCode);
        boolean bindingCurrent = binding == null
                || isTokenExchangeBindingShapeCurrent(
                        binding,
                        scopes,
                        tenantId,
                        memberId,
                        userId,
                        productCode,
                        sourceCode,
                        connectorCode,
                        now);
        // EXPLICIT_REAUTHORIZATION deliberately permits an existing terminal or
        // expired binding to be recovered. The entitlement is therefore the only
        // temporal upper bound on issuance authority; binding.valid_until is
        // shape/provenance evidence, not a reauthorization cutoff.
        Date entitlementValidUntil =
                entitlement == null ? null : entitlement.getValidUntil();
        return new BoardOAuthTokenExchangeAuthorityPort.LockResult(
                enterpriseCurrent,
                memberCurrent,
                entitlementCurrent,
                planCurrent,
                binding == null
                        ? BindingTopology.ABSENT
                        : BindingTopology.PRESENT,
                bindingCurrent,
                observedAt,
                entitlementValidUntil == null
                        ? null
                        : entitlementValidUntil.toInstant());
    }

    @Override
    public BoardOAuthRefreshAuthorityPort.LockResult lockForRefresh(
            Long tenantId,
            Long memberId,
            Long userId,
            String productCode,
            String sourceCode,
            String connectorCode,
            Instant lockRequestedAt) {
        Objects.requireNonNull(lockRequestedAt, "lockRequestedAt");
        BoardTransactionBoundary boundary = captureRefreshBoardTransactionBoundary();

        // This is the shared global lock prefix. No OAuth family/token row may
        // be locked before these authority slots and the W4a binding scopes.
        BoardEnterpriseAuthority enterprise = mapper.selectEnterpriseSlotForUpdate(tenantId);
        BoardEnterpriseMemberScope member = mapper.selectMemberSlotForUpdate(
                tenantId, memberId);
        BoardProductEntitlement entitlement = mapper.selectEntitlementForUpdate(
                tenantId, memberId, productCode);
        BoardProductPlan plan = mapper.selectPlanSlotForUpdate(
                productCode, IndependentBoardEntitlementService.VIP_PLAN);
        BoardConnectorBinding binding = mapper.selectConnectorBindingSlotForUpdate(
                tenantId, memberId, productCode, sourceCode, connectorCode);
        List<String> scopes = binding == null || binding.getBindingId() == null
                ? List.of()
                : mapper.selectConnectorBindingScopesForUpdate(binding.getBindingId());

        Instant observedAt = Instant.ofEpochMilli(clock.instant().toEpochMilli());
        if (observedAt.isBefore(lockRequestedAt)) {
            observedAt = lockRequestedAt;
        }
        Date observedDate = Date.from(observedAt);
        boolean enterpriseCurrent = isTokenExchangeEnterpriseCurrent(enterprise, tenantId);
        boolean memberCurrent = isTokenExchangeMemberCurrent(
                member, tenantId, memberId, userId);
        boolean entitlementCurrent = isTokenExchangeEntitlementCurrent(
                entitlement, tenantId, memberId, userId, productCode, observedDate);
        boolean planCurrent = isTokenExchangePlanCurrent(plan, productCode);
        boolean bindingShapeCurrent = binding != null
                && isTokenExchangeBindingShapeCurrent(
                        binding,
                        scopes,
                        tenantId,
                        memberId,
                        userId,
                        productCode,
                        sourceCode,
                        connectorCode,
                        observedDate);
        boolean bindingActive = bindingShapeCurrent
                && STATUS_ACTIVE.equals(binding.getStatus())
                && binding.getRevokedAt() == null
                && binding.getValidUntil() != null
                && binding.getValidUntil().after(observedDate);
        Date authorityValidUntil = earlierDate(
                entitlement == null ? null : entitlement.getValidUntil(),
                binding == null ? null : binding.getValidUntil());

        BoardOAuthRefreshAuthorityLease lease = new BoardOAuthRefreshAuthorityLease(
                oauthRefreshOwnerToken,
                boundary.resource(),
                boundary.connection(),
                binding,
                scopes,
                observedAt);
        return new BoardOAuthRefreshAuthorityPort.LockResult(
                lease,
                enterpriseCurrent,
                memberCurrent,
                entitlementCurrent,
                planCurrent,
                bindingShapeCurrent,
                bindingActive,
                binding == null ? null : binding.getBindingId(),
                binding == null ? null : binding.getVersion(),
                binding == null ? null : binding.getClientId(),
                binding == null ? null : parseSha256(binding.getPrincipalSubjectDigest()),
                scopes,
                observedAt,
                authorityValidUntil == null ? null : authorityValidUntil.toInstant());
    }

    @Override
    public void lockReceiptsForRefresh(
            BoardOAuthRefreshAuthorityPort.Lease opaqueLease) {
        if (!(opaqueLease instanceof BoardOAuthRefreshAuthorityLease lease)) {
            throw new ServiceException("BOARD_OAUTH_REFRESH_AUTHORITY_LEASE_INVALID", 500);
        }
        requireRefreshLease(lease);
        BoardConnectorBinding binding = lease.binding(oauthRefreshOwnerToken);
        if (binding == null || binding.getBindingId() == null) {
            throw new ServiceException(
                    "BOARD_OAUTH_REFRESH_AUTHORITY_RECEIPT_LOCK_FAILED", 500);
        }
        // The caller invokes this only after locking the OAuth token set and
        // before locking W4b receipts. The indexed range lock also protects the
        // same-binding receipt insertion gap under REPEATABLE_READ.
        List<BoardConnectorBindingReceipt> bindingReceipts =
                mapper.selectConnectorBindingReceiptsForUpdate(
                        binding.getBindingId());
        if (bindingReceipts == null) {
            throw new ServiceException(
                    "BOARD_OAUTH_REFRESH_AUTHORITY_RECEIPT_LOCK_FAILED", 500);
        }
        try {
            lease.markW4aReceiptsLocked(oauthRefreshOwnerToken);
        } catch (IllegalStateException invalidLease) {
            throw new ServiceException("BOARD_OAUTH_REFRESH_AUTHORITY_LEASE_INVALID", 500);
        }
    }

    @Override
    public BoardOAuthRefreshAuthorityPort.BindingRevocation revokeForRefreshReplay(
            BoardOAuthRefreshAuthorityPort.Lease opaqueLease,
            String expectedBindingId,
            Long expectedBindingVersion,
            Instant transitionAt) {
        if (!(opaqueLease instanceof BoardOAuthRefreshAuthorityLease lease)) {
            throw new ServiceException("BOARD_OAUTH_REFRESH_AUTHORITY_LEASE_INVALID", 500);
        }
        requireRefreshLease(lease);
        if (!lease.w4aReceiptsLocked(oauthRefreshOwnerToken)) {
            throw new ServiceException("BOARD_OAUTH_REFRESH_AUTHORITY_LEASE_INVALID", 500);
        }
        BoardConnectorBinding binding = lease.binding(oauthRefreshOwnerToken);
        List<String> scopes = lease.scopes(oauthRefreshOwnerToken);
        if (binding == null
                || !Objects.equals(binding.getBindingId(), expectedBindingId)
                || !Objects.equals(binding.getVersion(), expectedBindingVersion)
                || !STATUS_ACTIVE.equals(binding.getStatus())
                || binding.getRevokedAt() != null
                || binding.getVersion() == null
                || Objects.equals(binding.getVersion(), Long.MAX_VALUE)
                || transitionAt == null
                || transitionAt.isBefore(lease.observedAt(oauthRefreshOwnerToken))) {
            throw new ServiceException("BOARD_OAUTH_REFRESH_BINDING_CONFLICT", 409);
        }
        Date revokedAt = Date.from(transitionAt);
        requireBindingTransitionTimes(binding, revokedAt);
        binding.setScopes(scopes);
        Long previousVersion = binding.getVersion();
        binding.setStatus(STATUS_REVOKED);
        binding.setRevokedAt(revokedAt);
        binding.setVersion(previousVersion + 1L);
        if (mapper.revokeConnectorBindingIfVersion(binding, previousVersion) != 1) {
            throw new ServiceException("BOARD_OAUTH_REFRESH_BINDING_CONFLICT", 409);
        }
        BoardConnectorBindingReceipt receipt = insertReceipt(
                binding,
                binding.getUserId(),
                "CONNECTOR_BINDING_REVOKED",
                revokedAt);

        // Raw same-transaction current-read: containment must still succeed
        // when enterprise/member authority has just become terminal. The row
        // was already locked through the unique binding slot, so this is a
        // re-entrant exact-row lock rather than a new lock-order edge.
        BoardConnectorBinding current = mapper.selectConnectorBindingForUpdate(
                binding.getTenantId(),
                binding.getMemberId(),
                binding.getUserId(),
                binding.getProductCode(),
                binding.getSourceCode(),
                binding.getConnectorCode());
        List<String> currentScopes = mapper.selectConnectorBindingScopes(binding.getBindingId());
        BoardConnectorBindingReceipt currentReceipt =
                mapper.selectConnectorBindingReceiptForUpdate(receipt.getReceiptId());
        if (current == null
                || currentScopes == null
                || !Objects.equals(current.getBindingId(), binding.getBindingId())
                || !Objects.equals(current.getStatus(), STATUS_REVOKED)
                || !Objects.equals(current.getVersion(), previousVersion + 1L)
                || !sameDate(current.getRevokedAt(), revokedAt)
                || !new HashSet<>(currentScopes).equals(new HashSet<>(scopes))
                || currentReceipt == null
                || !Objects.equals(currentReceipt.getReceiptId(), receipt.getReceiptId())
                || !Objects.equals(currentReceipt.getBindingId(), binding.getBindingId())
                || !Objects.equals(currentReceipt.getAction(), "CONNECTOR_BINDING_REVOKED")
                || !Objects.equals(currentReceipt.getPayloadDigest(), receipt.getPayloadDigest())
                || !Objects.equals(currentReceipt.getEvidenceLevel(), "ACTION_COMPLETED")
                || !sameDate(currentReceipt.getCreatedAt(), revokedAt)) {
            throw new ServiceException("BOARD_OAUTH_REFRESH_BINDING_CURRENT_READ_INVALID", 500);
        }
        try {
            lease.markReplayRevoked(oauthRefreshOwnerToken);
        } catch (IllegalStateException invalidLease) {
            throw new ServiceException("BOARD_OAUTH_REFRESH_AUTHORITY_LEASE_INVALID", 500);
        }
        return new BoardOAuthRefreshAuthorityPort.BindingRevocation(
                binding.getBindingId(),
                previousVersion,
                binding.getVersion(),
                transitionAt,
                currentReceipt.getReceiptId(),
                currentReceipt.getPayloadDigest());
    }

    @Transactional(rollbackFor = Exception.class)
    BoardConnectorBindingSnapshot confirmProtectedRequest(
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
        throw new ServiceException("BOARD_CONNECTOR_ACTIVATION_COORDINATOR_REQUIRED", 409);
    }

    /**
     * Single package-internal production coordinator for a first protected
     * request. The caller supplies only a digest derived by the public facade;
     * family identity and the W4a attestation are always current-read here.
     */
    BoardConnectorBindingSnapshot activateFirstProtectedOAuthFamily(
            BoardConnectorBindingKey key,
            Long actorUserId,
            byte[] presentedAccessTokenDigest,
            String verificationMethod) {
        BoardConnectorBindingActivationLease lease =
                lockOAuthFamilyActivation(key, actorUserId);
        BoardOAuthFamilyActivationContext context =
                prepareAndApplyLockedOAuthFamilyActivation(
                        lease, presentedAccessTokenDigest, verificationMethod);
        applyLockedOAuthFamilyStateTransition(lease);
        verifyAndAppendLockedOAuthFamilyBindingReceipt(lease);
        insertLockedOAuthFamilyActivationReceipt(lease);
        completeLockedOAuthFamilyActivation(lease);
        return context.binding();
    }

    BoardConnectorBindingActivationLease lockOAuthFamilyActivation(
            BoardConnectorBindingKey key,
            Long actorUserId) {
        requireFreshOAuthActivationTransaction();
        BoardTransactionBoundary boardTransaction = captureBoardTransactionBoundary();
        requireConfiguredTrustPolicy();
        validateActivationKey(key, actorUserId);

        BoardEnterpriseAuthority enterprise = mapper.selectEnterpriseSlotForUpdate(
                key.tenantId());
        BoardEnterpriseMemberScope member = mapper.selectExactActiveMemberForUpdate(
                key.tenantId(), key.memberId(), key.userId());
        requireCurrentEnterprise(enterprise, key.tenantId());
        requireExactMember(member, key.tenantId(), key.memberId(), key.userId());

        Date wallClockNow = Date.from(clock.instant());
        BoardProductEntitlement entitlement = mapper.selectEntitlementForUpdate(
                key.tenantId(), key.memberId(), key.productCode());
        requireCurrentVipEntitlementState(
                entitlement,
                key.tenantId(),
                key.memberId(),
                key.userId(),
                key.productCode(),
                wallClockNow);
        BoardProductPlan plan = mapper.selectActivePlanForUpdate(
                key.productCode(), IndependentBoardEntitlementService.VIP_PLAN);
        if (plan == null) {
            throw new ServiceException("BOARD_PLAN_CONTRACT_DRIFT", 500);
        }
        requireCurrentVipPlan(plan);

        BoardConnectorBinding existing = mapper.selectConnectorBindingSlotForUpdate(
                key.tenantId(), key.memberId(), key.productCode(), SOURCE_CODE, CONNECTOR_CODE);
        BoardConnectorBindingActivationLease.Mode mode =
                BoardConnectorBindingActivationLease.Mode.FIRST_ACTIVATION;
        Long expectedVersion = null;
        Date transitionAt = wallClockNow;
        if (existing != null) {
            List<String> scopes = mapper.selectConnectorBindingScopesForUpdate(existing.getBindingId());
            if (scopes == null) {
                throw new ServiceException("BOARD_CONNECTOR_BINDING_CURRENT_READ_FAILED", 500);
            }
            existing.setScopes(scopes);
            validateProjectionScope(
                    existing, key.tenantId(), key.memberId(), key.userId(), key.productCode());
            requireExactRequiredScopes(scopes);
            requireBindingTransitionTimes(existing, wallClockNow);
            expectedVersion = existing.getVersion();
            if (Objects.equals(expectedVersion, Long.MAX_VALUE)) {
                throw new ServiceException("BOARD_CONNECTOR_BINDING_VERSION_CONFLICT", 409);
            }
            transitionAt = activationTransitionAt(existing, wallClockNow);
            mode = BoardConnectorBindingActivationLease.Mode.EXPLICIT_REAUTHORIZATION;
        }

        BoardConnectorBindingActivationLease lease = new BoardConnectorBindingActivationLease(
                oauthActivationOwnerToken,
                new Object(),
                boardTransaction.resource(),
                boardTransaction.connection(),
                key,
                mode,
                existing,
                expectedVersion,
                actorUserId,
                transitionAt,
                member,
                entitlement,
                plan);
        bindActivationLease(lease);
        return lease;
    }

    BoardOAuthFamilyActivationContext prepareAndApplyLockedOAuthFamilyActivation(
            BoardConnectorBindingActivationLease lease,
            byte[] presentedAccessTokenDigest,
            String verificationMethod) {
        requireBoundLease(lease, BoardConnectorBindingActivationLease.Phase.LOCKED);
        if (presentedAccessTokenDigest == null
                || presentedAccessTokenDigest.length != BoardOAuthCrypto.SHA256_BYTES
                || !KNOWN_VERIFICATION_METHODS.contains(verificationMethod)) {
            throw new ServiceException(
                    IndependentBoardOAuthFirstProtectedRequestTransactionRunner.ACCESS_TOKEN_INVALID,
                    401);
        }

        IndependentBoardOAuthMapper.TokenContextLocator tokenLocator =
                oauthMapper.selectTokenContextLocatorByDigest(
                        presentedAccessTokenDigest.clone());
        requirePresentedTokenLocatorMatchesLease(
                tokenLocator, lease, presentedAccessTokenDigest);
        BoardOAuthTokenFamily familyLocator =
                oauthMapper.selectTokenFamilyLocator(tokenLocator.getFamilyId());
        requireFamilyLocatorForPresentedToken(familyLocator, tokenLocator);
        BoardOAuthAuthorizationCode codeLocator =
                oauthMapper.selectAuthorizationCodeLocatorById(
                        familyLocator.getOriginAuthorizationCodeId());
        requireCodeLocatorForPresentedToken(
                codeLocator, familyLocator, tokenLocator);

        BoardOAuthClient client = oauthMapper.selectClientForUpdate(
                tokenLocator.getClientId());
        BoardOAuthAuthorizationRequest request =
                oauthMapper.selectAuthorizationRequestByIdAndClientForUpdate(
                        codeLocator.getAuthorizationRequestId(),
                        tokenLocator.getClientId());
        BoardOAuthAuthorizationCode code =
                oauthMapper.selectAuthorizationCodeByIdAndClientForUpdate(
                        familyLocator.getOriginAuthorizationCodeId(),
                        tokenLocator.getClientId());

        BoardConnectorBindingKey key = lease.key();
        BoardOAuthTokenFamily oldActiveFamily =
                oauthMapper.selectActiveTokenFamilySlotForUpdate(
                        key.tenantId(), key.memberId(), key.productCode(),
                        SOURCE_CODE, CONNECTOR_CODE);
        BoardOAuthTokenFamily pendingFamily =
                oauthMapper.selectPendingTokenFamilySlotForUpdate(
                        key.tenantId(), key.memberId(), key.productCode(),
                        SOURCE_CODE, CONNECTOR_CODE);
        List<BoardOAuthToken> oldActiveTokens = oldActiveFamily == null
                ? List.of()
                : requireTokenCurrentRead(oldActiveFamily.getFamilyId());
        List<BoardOAuthToken> pendingTokens = pendingFamily == null
                ? List.of()
                : requireTokenCurrentRead(pendingFamily.getFamilyId());
        requireConsentIntentLineage(request, code, pendingFamily, tokenLocator, lease);

        List<BoardOAuthReceipt> creationReceipts =
                oauthMapper.selectTokenFamilyCreatedReceiptCandidatesForUpdate(
                        pendingFamily.getFamilyId(),
                        pendingFamily.getClientId(),
                        pendingFamily.getOriginAuthorizationCodeId());
        BoardOAuthReceipt creationReceipt =
                requireTokenFamilyCreationProvenance(
                        request,
                        code,
                        pendingFamily,
                        pendingTokens,
                        creationReceipts);

        // Freeze the causal event time only after every OAuth before-image and
        // its creation receipt are locked. All time-sensitive predicates are
        // evaluated against this one value before the first durable mutation.
        finalizeOAuthActivationTransitionAt(lease);
        BoardConnectorProtectedRequestAttestation attestation =
                authoritativeAttestation(
                        lease,
                        client,
                        pendingFamily,
                        pendingTokens,
                        tokenLocator,
                        presentedAccessTokenDigest,
                        verificationMethod);
        validateAttestation(attestation, lease.actorUserId());
        requireAttestationMatchesKey(attestation, key);
        requireCurrentOAuthClientForBearer(
                client, attestation, lease.transitionAt());
        byte[] principalDigest = parseSha256(
                attestation.principalSubjectDigest());
        requirePendingFamilyBeforeImage(
                pendingFamily,
                pendingTokens,
                tokenLocator.getFamilyId(),
                attestation,
                key,
                principalDigest,
                lease.transitionAt());
        requirePresentedAccessTokenCurrentRead(
                tokenLocator,
                pendingFamily,
                pendingTokens,
                presentedAccessTokenDigest,
                lease.transitionAt());
        requireOldActiveFamilyBeforeImage(
                lease, oldActiveFamily, oldActiveTokens, key, lease.transitionAt());

        BoardConnectorBinding binding = requireAppliedBindingMutationCurrentRead(
                applyBindingMutation(lease, attestation));
        lease.setAppliedBinding(oauthActivationOwnerToken, binding);

        BoardOAuthFamilyActivationContext context = newActivationContext(
                lease,
                binding,
                pendingFamily,
                pendingTokens,
                oldActiveFamily,
                oldActiveTokens,
                principalDigest,
                client,
                request,
                code,
                creationReceipt,
                tokenLocator.getTokenId(),
                presentedAccessTokenDigest,
                verificationMethod);
        try {
            lease.attachActivationProof(
                    oauthActivationOwnerToken,
                    new BoardOAuthFamilyActivationProof(
                            oauthActivationOwnerToken,
                            context,
                            binding,
                            client,
                            request,
                            code,
                            creationReceipt,
                            tokenLocator.getTokenId(),
                            presentedAccessTokenDigest,
                            verificationMethod,
                            pendingFamily,
                            pendingTokens,
                            oldActiveFamily,
                            oldActiveTokens));
        } catch (IllegalStateException invalidProof) {
            throw new ServiceException("BOARD_CONNECTOR_ACTIVATION_LEASE_INVALID", 500);
        }
        advanceLease(
                lease,
                BoardConnectorBindingActivationLease.Phase.LOCKED,
                BoardConnectorBindingActivationLease.Phase.PREPARED_AND_BINDING_APPLIED);
        return context;
    }

    /** Test-only compatibility seam; production uses the bearer-derived overload. */
    BoardOAuthFamilyActivationContext prepareAndApplyLockedOAuthFamilyActivation(
            BoardConnectorBindingActivationLease lease,
            BoardConnectorProtectedRequestAttestation attestation,
            String pendingFamilyId) {
        requireBoundLease(lease, BoardConnectorBindingActivationLease.Phase.LOCKED);
        validateAttestation(attestation, lease.actorUserId());
        requireAttestationMatchesKey(attestation, lease.key());
        requireFamilyId(pendingFamilyId);

        BoardOAuthClient client = oauthMapper.selectClientForUpdate(attestation.clientId());

        BoardConnectorBindingKey key = lease.key();
        BoardOAuthTokenFamily oldActiveFamily = oauthMapper.selectActiveTokenFamilySlotForUpdate(
                key.tenantId(), key.memberId(), key.productCode(), SOURCE_CODE, CONNECTOR_CODE);
        BoardOAuthTokenFamily pendingFamily = oauthMapper.selectPendingTokenFamilySlotForUpdate(
                key.tenantId(), key.memberId(), key.productCode(), SOURCE_CODE, CONNECTOR_CODE);
        List<BoardOAuthToken> oldActiveTokens = oldActiveFamily == null
                ? List.of()
                : requireTokenCurrentRead(oldActiveFamily.getFamilyId());
        List<BoardOAuthToken> pendingTokens = pendingFamily == null
                ? List.of()
                : requireTokenCurrentRead(pendingFamily.getFamilyId());
        if (pendingFamily == null) {
            throw new ServiceException("BOARD_OAUTH_PENDING_FAMILY_STATE_INVALID", 409);
        }
        requireIntentMatchesBindingTopology(pendingFamily.getConsentIntent(), lease);
        finalizeOAuthActivationTransitionAt(lease);
        requireCurrentOAuthClient(client, attestation, lease.transitionAt());

        byte[] principalDigest = parseSha256(attestation.principalSubjectDigest());
        requirePendingFamilyBeforeImage(
                pendingFamily,
                pendingTokens,
                pendingFamilyId,
                attestation,
                key,
                principalDigest,
                lease.transitionAt());
        requireOldActiveFamilyBeforeImage(
                lease, oldActiveFamily, oldActiveTokens, key, lease.transitionAt());

        BoardConnectorBinding binding = requireAppliedBindingMutationCurrentRead(
                applyBindingMutation(lease, attestation));
        lease.setAppliedBinding(oauthActivationOwnerToken, binding);

        BoardOAuthFamilyActivationContext context = newActivationContext(
                lease,
                binding,
                pendingFamily,
                pendingTokens,
                oldActiveFamily,
                oldActiveTokens,
                principalDigest,
                client,
                null,
                null,
                null,
                null,
                null,
                attestation.verificationMethod());
        try {
            lease.attachActivationProof(
                    oauthActivationOwnerToken,
                    new BoardOAuthFamilyActivationProof(
                            oauthActivationOwnerToken,
                            context,
                            binding,
                            client,
                            pendingFamily,
                            pendingTokens,
                            oldActiveFamily,
                            oldActiveTokens));
        } catch (IllegalStateException invalidProof) {
            throw new ServiceException("BOARD_CONNECTOR_ACTIVATION_LEASE_INVALID", 500);
        }
        advanceLease(
                lease,
                BoardConnectorBindingActivationLease.Phase.LOCKED,
                BoardConnectorBindingActivationLease.Phase.PREPARED_AND_BINDING_APPLIED);
        return context;
    }

    void verifyAndAppendLockedOAuthFamilyBindingReceipt(
            BoardConnectorBindingActivationLease lease) {
        requireBoundLease(
                lease,
                BoardConnectorBindingActivationLease.Phase.PREPARED_AND_BINDING_APPLIED);
        BoardConnectorBinding verifiedBinding = verifyOAuthFamilyActivationFinalState(lease);
        BoardConnectorBindingReceipt receipt = insertReceipt(
                verifiedBinding,
                lease.actorUserId(),
                "CONNECTOR_BINDING_VERIFIED",
                lease.transitionAt());
        try {
            lease.activationProof(oauthActivationOwnerToken).recordW4aReceipt(
                    oauthActivationOwnerToken,
                    receipt.getReceiptId(), receipt.getPayloadDigest());
        } catch (IllegalStateException duplicateProof) {
            throw new ServiceException("BOARD_CONNECTOR_ACTIVATION_LEASE_INVALID", 500);
        }
        advanceLease(
                lease,
                BoardConnectorBindingActivationLease.Phase.PREPARED_AND_BINDING_APPLIED,
                BoardConnectorBindingActivationLease.Phase.W4A_RECEIPT_WRITTEN);
    }

    void completeLockedOAuthFamilyActivation(BoardConnectorBindingActivationLease lease) {
        requireBoundLease(lease, BoardConnectorBindingActivationLease.Phase.W4A_RECEIPT_WRITTEN);
        verifyOAuthFamilyActivationFinalState(lease);
        verifyW4aReceipt(lease);
        verifyW4bActivationReceipt(lease);
        advanceLease(
                lease,
                BoardConnectorBindingActivationLease.Phase.W4A_RECEIPT_WRITTEN,
                BoardConnectorBindingActivationLease.Phase.COMPLETE);
        bindActivationFinalizer(lease);
    }

    private BoardOAuthFamilyActivationContext newActivationContext(
            BoardConnectorBindingActivationLease lease,
            BoardConnectorBinding binding,
            BoardOAuthTokenFamily pendingFamily,
            List<BoardOAuthToken> pendingTokens,
            BoardOAuthTokenFamily oldActiveFamily,
            List<BoardOAuthToken> oldActiveTokens,
            byte[] principalDigest,
            BoardOAuthClient client,
            BoardOAuthAuthorizationRequest authorizationRequest,
            BoardOAuthAuthorizationCode authorizationCode,
            BoardOAuthReceipt tokenFamilyCreatedReceipt,
            Long presentedTokenId,
            byte[] presentedTokenDigest,
            String verificationMethod) {
        String receiptId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();
        BoardOAuthConsentIntent consentIntent = pendingFamily.getConsentIntent();
        requireIntentMatchesBindingTopology(consentIntent, lease);
        String action = consentIntent == BoardOAuthConsentIntent.FIRST_CONNECT
                ? "TOKEN_FAMILY_ACTIVATED"
                : "TOKEN_FAMILY_REAUTHORIZED";
        byte[] payloadDigest = oauthActivationReceiptDigest(
                receiptId,
                action,
                correlationId,
                binding,
                pendingFamily,
                pendingTokens,
                oldActiveFamily,
                oldActiveTokens,
                principalDigest,
                lease.actorUserId(),
                lease.transitionAt(),
                client,
                authorizationRequest,
                authorizationCode,
                tokenFamilyCreatedReceipt,
                presentedTokenId,
                presentedTokenDigest,
                verificationMethod,
                consentIntent,
                lease.mode());
        return new BoardOAuthFamilyActivationContext(
                lease.appliedSnapshot(oauthActivationOwnerToken),
                receiptId,
                action,
                pendingFamily.getClientId(),
                pendingFamily.getFamilyId(),
                consentIntent,
                lease.mode(),
                principalDigest,
                lease.actorUserId(),
                correlationId,
                payloadDigest,
                lease.transitionAt());
    }

    private void applyLockedOAuthFamilyStateTransition(
            BoardConnectorBindingActivationLease lease) {
        requireBoundLease(
                lease,
                BoardConnectorBindingActivationLease.Phase.PREPARED_AND_BINDING_APPLIED);
        BoardOAuthFamilyActivationProof proof = requireActivationProof(lease);
        if (!proof.hasPresentedBearerProvenance()) {
            throw new ServiceException("BOARD_OAUTH_PRESENTED_BEARER_PROOF_REQUIRED", 500);
        }
        BoardOAuthFamilyActivationProof.FamilyImage oldFamily =
                proof.oldActiveFamily();
        if (oldFamily != null) {
            long activeTokenCount = proof.oldActiveTokens().stream()
                    .filter(token -> Objects.equals(token.status, STATUS_ACTIVE))
                    .count();
            if (activeTokenCount <= 0L || activeTokenCount > Integer.MAX_VALUE
                    || oauthMapper.revokeActiveFamilyTokensAtLogicalTime(
                            oldFamily.familyId, lease.transitionAt())
                            != (int) activeTokenCount) {
                throw new ServiceException(
                        "BOARD_OAUTH_REAUTHORIZATION_TOKEN_CONFLICT", 409);
            }
            if (oauthMapper.revokeTokenFamilyIfVersion(
                            oldFamily.familyId,
                            oldFamily.clientId,
                            oldFamily.version,
                            lease.transitionAt())
                    != 1) {
                throw new ServiceException(
                        "BOARD_OAUTH_REAUTHORIZATION_FAMILY_CONFLICT", 409);
            }
        }

        BoardOAuthFamilyActivationProof.FamilyImage pending =
                proof.pendingFamily();
        BoardOAuthTokenFamily activation = new BoardOAuthTokenFamily();
        activation.setFamilyId(pending.familyId);
        activation.setClientId(pending.clientId);
        activation.setTenantId(pending.tenantId);
        activation.setMemberId(pending.memberId);
        activation.setUserId(pending.userId);
        activation.setProductCode(pending.productCode);
        activation.setSourceCode(pending.sourceCode);
        activation.setConnectorCode(pending.connectorCode);
        activation.setIssuerUri(pending.issuerUri);
        activation.setResourceUri(pending.resourceUri);
        activation.setScopeCanonical(pending.scopeCanonical);
        activation.setScopeDigest(pending.scopeDigest.clone());
        activation.setPrincipalSubjectDigest(
                pending.principalSubjectDigest.clone());
        activation.setConsentIntent(pending.consentIntent);
        activation.setBindingId(proof.context().bindingId());
        activation.setBindingVersion(proof.context().bindingVersion());
        activation.setActivatedAt(copy(lease.transitionAt()));
        if (pending.version == null || Objects.equals(pending.version, Long.MAX_VALUE)
                || oauthMapper.activateTokenFamilyIfVersion(
                                activation, pending.version)
                        != 1) {
            throw new ServiceException("BOARD_OAUTH_PENDING_FAMILY_CONFLICT", 409);
        }
    }

    private void insertLockedOAuthFamilyActivationReceipt(
            BoardConnectorBindingActivationLease lease) {
        requireBoundLease(
                lease,
                BoardConnectorBindingActivationLease.Phase.W4A_RECEIPT_WRITTEN);
        BoardOAuthFamilyActivationProof proof = requireActivationProof(lease);
        if (!proof.hasPresentedBearerProvenance()) {
            throw new ServiceException("BOARD_OAUTH_PRESENTED_BEARER_PROOF_REQUIRED", 500);
        }
        BoardOAuthFamilyActivationContext context = proof.context();
        BoardOAuthReceipt receipt = new BoardOAuthReceipt();
        receipt.setReceiptId(context.receiptId());
        receipt.setAction(context.action());
        receipt.setClientId(context.clientId());
        receipt.setAuthorizationRequestId(null);
        receipt.setAuthorizationCodeId(null);
        receipt.setFamilyId(context.familyId());
        receipt.setTokenId(null);
        receipt.setBindingId(context.bindingId());
        receipt.setTenantId(context.tenantId());
        receipt.setMemberId(context.memberId());
        receipt.setUserId(context.userId());
        receipt.setPrincipalSubjectDigest(context.principalSubjectDigest());
        receipt.setActorType(context.actorType());
        receipt.setActorUserId(context.actorUserId());
        receipt.setActorSubjectDigest(context.actorSubjectDigest());
        receipt.setCorrelationId(context.correlationId());
        receipt.setPayloadDigest(context.payloadDigest());
        receipt.setEvidenceLevel(context.evidenceLevel());
        receipt.setCreatedAt(context.createdAt());
        try {
            if (oauthMapper.insertReceipt(receipt) != 1
                    || receipt.getId() == null
                    || receipt.getId() <= 0L) {
                throw new ServiceException(
                        "BOARD_OAUTH_ACTIVATION_RECEIPT_WRITE_FAILED", 500);
            }
        } catch (DuplicateKeyException conflict) {
            throw new ServiceException(
                    "BOARD_OAUTH_ACTIVATION_RECEIPT_CONFLICT", 409);
        } catch (DataAccessException persistenceFailure) {
            throw new ServiceException(
                    "BOARD_OAUTH_ACTIVATION_RECEIPT_WRITE_FAILED", 503);
        }
        verifyW4bActivationReceipt(lease);
    }

    private BoardConnectorBinding applyBindingMutation(
            BoardConnectorBindingActivationLease lease,
            BoardConnectorProtectedRequestAttestation attestation) {
        if (!attestation.validUntil().after(lease.transitionAt())) {
            throw new ServiceException("CONNECTOR_CREDENTIAL_EXPIRED", 403);
        }
        if (lease.mode() == BoardConnectorBindingActivationLease.Mode.FIRST_ACTIVATION) {
            if (lease.existingBinding(oauthActivationOwnerToken) != null
                    || lease.expectedVersion() != null) {
                throw new ServiceException("BOARD_CONNECTOR_ACTIVATION_LEASE_INVALID", 500);
            }
            BoardConnectorBinding binding = newBinding(attestation, lease.transitionAt());
            insertBindingAndScopes(binding, lease.transitionAt());
            return binding;
        }

        BoardConnectorBinding binding = lease.existingBinding(oauthActivationOwnerToken);
        Long expectedVersion = lease.expectedVersion();
        if (binding == null || expectedVersion == null || expectedVersion <= 0L
                || Objects.equals(expectedVersion, Long.MAX_VALUE)) {
            throw new ServiceException("BOARD_CONNECTOR_ACTIVATION_LEASE_INVALID", 500);
        }
        binding.setIssuerUri(attestation.issuerUri());
        binding.setResourceUri(attestation.resourceUri());
        binding.setClientId(attestation.clientId());
        binding.setPrincipalSubjectDigest(attestation.principalSubjectDigest());
        binding.setStatus(STATUS_ACTIVE);
        binding.setVerificationMethod(attestation.verificationMethod());
        binding.setEvidenceDigest(attestation.evidenceDigest());
        binding.setVerifiedAt(copy(lease.transitionAt()));
        binding.setLastSeenAt(copy(lease.transitionAt()));
        binding.setValidUntil(copy(attestation.validUntil()));
        binding.setRevokedAt(null);
        binding.setVersion(expectedVersion + 1L);
        if (mapper.reauthorizeConnectorBindingIfVersion(binding, expectedVersion) != 1) {
            throw new ServiceException("BOARD_CONNECTOR_BINDING_CONFLICT", 409);
        }
        return binding;
    }

    private void requirePresentedTokenLocatorMatchesLease(
            IndependentBoardOAuthMapper.TokenContextLocator locator,
            BoardConnectorBindingActivationLease lease,
            byte[] presentedAccessTokenDigest) {
        BoardConnectorBindingKey key = lease.key();
        Date locatorObservedAt = lease.provisionalTransitionAt(
                oauthActivationOwnerToken);
        if (locator == null
                || presentedAccessTokenDigest == null
                || presentedAccessTokenDigest.length != BoardOAuthCrypto.SHA256_BYTES
                || locator.getTokenId() == null || locator.getTokenId() <= 0L
                || locator.getFamilyId() == null
                || !FAMILY_ID.matcher(locator.getFamilyId()).matches()
                || !Objects.equals(locator.getTokenType(), "ACCESS")
                || !Objects.equals(locator.getTokenGeneration(), 0L)
                || !Objects.equals(locator.getTokenStatus(), STATUS_ACTIVE)
                || locator.getTokenVersion() == null || locator.getTokenVersion() < 0L
                || locator.getTokenIssuedAt() == null
                || locator.getTokenIssuedAt().after(locatorObservedAt)
                || locator.getTokenExpiresAt() == null
                || !locator.getTokenExpiresAt().after(locatorObservedAt)
                || locator.getClientId() == null
                || !OAUTH_CLIENT_ID.matcher(locator.getClientId()).matches()
                || !Objects.equals(locator.getTenantId(), key.tenantId())
                || !Objects.equals(locator.getMemberId(), key.memberId())
                || !Objects.equals(locator.getUserId(), key.userId())
                || !Objects.equals(locator.getProductCode(), key.productCode())
                || !Objects.equals(locator.getSourceCode(), SOURCE_CODE)
                || !Objects.equals(locator.getConnectorCode(), CONNECTOR_CODE)
                || !Objects.equals(locator.getIssuerUri(), BoardOAuthProfile.ISSUER)
                || !Objects.equals(locator.getResourceUri(), BoardOAuthProfile.RESOURCE)
                || !Objects.equals(
                        locator.getScopeCanonical(), BoardOAuthProfile.CANONICAL_SCOPE)
                || !sameBytes(locator.getScopeDigest(), OAUTH_SCOPE_DIGEST)
                || locator.getPrincipalSubjectDigest() == null
                || locator.getPrincipalSubjectDigest().length
                        != BoardOAuthCrypto.SHA256_BYTES
                || locator.getConsentIntent() == null
                || locator.getBindingId() != null
                || locator.getBindingVersion() != null
                || !Objects.equals(locator.getFamilyStatus(), "PENDING_BINDING")
                || !Objects.equals(locator.getCurrentRefreshGeneration(), 0L)
                || !Objects.equals(locator.getFamilyVersion(), 0L)
                || locator.getFamilyIssuedAt() == null
                || locator.getFamilyIssuedAt().after(locatorObservedAt)
                || locator.getFamilyActivatedAt() != null
                || locator.getFamilyExpiresAt() == null
                || !locator.getFamilyExpiresAt().after(locatorObservedAt)) {
            throw new ServiceException(
                    IndependentBoardOAuthFirstProtectedRequestTransactionRunner.ACCESS_TOKEN_INVALID,
                    401);
        }
    }

    private void requireFamilyLocatorForPresentedToken(
            BoardOAuthTokenFamily family,
            IndependentBoardOAuthMapper.TokenContextLocator tokenLocator) {
        if (family == null
                || family.getId() == null || family.getId() <= 0L
                || !Objects.equals(family.getFamilyId(), tokenLocator.getFamilyId())
                || !Objects.equals(family.getClientId(), tokenLocator.getClientId())
                || !Objects.equals(
                        family.getConsentIntent(), tokenLocator.getConsentIntent())
                || family.getOriginAuthorizationCodeId() == null
                || family.getOriginAuthorizationCodeId() <= 0L) {
            throw new ServiceException(
                    IndependentBoardOAuthFirstProtectedRequestTransactionRunner.ACCESS_TOKEN_INVALID,
                    401);
        }
    }

    private void requireCodeLocatorForPresentedToken(
            BoardOAuthAuthorizationCode code,
            BoardOAuthTokenFamily family,
            IndependentBoardOAuthMapper.TokenContextLocator tokenLocator) {
        if (code == null
                || code.getId() == null || code.getId() <= 0L
                || !Objects.equals(code.getId(), family.getOriginAuthorizationCodeId())
                || !Objects.equals(code.getClientId(), tokenLocator.getClientId())
                || !Objects.equals(code.getConsentIntent(), family.getConsentIntent())
                || !Objects.equals(code.getConsentIntent(), tokenLocator.getConsentIntent())
                || code.getAuthorizationRequestId() == null
                || code.getAuthorizationRequestId() <= 0L) {
            throw new ServiceException(
                    IndependentBoardOAuthFirstProtectedRequestTransactionRunner.ACCESS_TOKEN_INVALID,
                    401);
        }
    }

    private BoardOAuthConsentIntent requireConsentIntentLineage(
            BoardOAuthAuthorizationRequest request,
            BoardOAuthAuthorizationCode code,
            BoardOAuthTokenFamily family,
            IndependentBoardOAuthMapper.TokenContextLocator tokenLocator,
            BoardConnectorBindingActivationLease lease) {
        BoardOAuthConsentIntent intent = request == null
                ? null
                : request.getConsentIntent();
        if (intent == null
                || code == null
                || family == null
                || tokenLocator == null
                || !Objects.equals(code.getConsentIntent(), intent)
                || !Objects.equals(family.getConsentIntent(), intent)
                || !Objects.equals(tokenLocator.getConsentIntent(), intent)) {
            throw new ServiceException("BOARD_OAUTH_CONSENT_INTENT_LINEAGE_INVALID", 409);
        }
        requireIntentMatchesBindingTopology(intent, lease);
        return intent;
    }

    private void requireIntentMatchesBindingTopology(
            BoardOAuthConsentIntent intent,
            BoardConnectorBindingActivationLease lease) {
        if (intent == null || lease == null) {
            throw new ServiceException("BOARD_OAUTH_CONSENT_INTENT_LINEAGE_INVALID", 409);
        }
        boolean topologyMatches =
                (intent == BoardOAuthConsentIntent.FIRST_CONNECT
                                && lease.mode()
                                        == BoardConnectorBindingActivationLease.Mode.FIRST_ACTIVATION)
                        || (intent == BoardOAuthConsentIntent.EXPLICIT_REAUTHORIZATION
                                && lease.mode()
                                        == BoardConnectorBindingActivationLease.Mode
                                                .EXPLICIT_REAUTHORIZATION);
        if (!topologyMatches) {
            throw new ServiceException("BOARD_OAUTH_CONSENT_INTENT_TOPOLOGY_CONFLICT", 409);
        }
    }

    private BoardConnectorProtectedRequestAttestation authoritativeAttestation(
            BoardConnectorBindingActivationLease lease,
            BoardOAuthClient client,
            BoardOAuthTokenFamily pendingFamily,
            List<BoardOAuthToken> pendingTokens,
            IndependentBoardOAuthMapper.TokenContextLocator tokenLocator,
            byte[] presentedAccessTokenDigest,
            String verificationMethod) {
        BoardOAuthToken presented = findPresentedAccessToken(
                pendingTokens,
                tokenLocator == null ? null : tokenLocator.getTokenId(),
                presentedAccessTokenDigest);
        if (client == null || pendingFamily == null || presented == null
                || pendingFamily.getPrincipalSubjectDigest() == null
                || pendingFamily.getPrincipalSubjectDigest().length
                        != BoardOAuthCrypto.SHA256_BYTES
                || pendingFamily.getScopeDigest() == null
                || pendingFamily.getScopeDigest().length != BoardOAuthCrypto.SHA256_BYTES
                || pendingFamily.getExpiresAt() == null
                || client.getExpiresAt() == null) {
            throw new ServiceException(
                    IndependentBoardOAuthFirstProtectedRequestTransactionRunner.ACCESS_TOKEN_INVALID,
                    401);
        }
        String evidenceCanonical = String.join("\n",
                "FBSIR:OAUTH:FIRST_PROTECTED:v2",
                String.valueOf(presented.getId()),
                HexFormat.of().formatHex(presentedAccessTokenDigest),
                pendingFamily.getFamilyId(),
                pendingFamily.getClientId(),
                String.valueOf(pendingFamily.getTenantId()),
                String.valueOf(pendingFamily.getMemberId()),
                String.valueOf(pendingFamily.getUserId()),
                HexFormat.of().formatHex(
                        pendingFamily.getPrincipalSubjectDigest()),
                pendingFamily.getResourceUri(),
                pendingFamily.getScopeCanonical(),
                HexFormat.of().formatHex(pendingFamily.getScopeDigest()),
                String.valueOf(presented.getExpiresAt().getTime()),
                String.valueOf(pendingFamily.getExpiresAt().getTime()),
                String.valueOf(client.getExpiresAt().getTime()),
                pendingFamily.getConsentIntent().name(),
                lease.mode().name(),
                verificationMethod,
                String.valueOf(lease.transitionAt().getTime()));
        String evidenceDigest = HexFormat.of().formatHex(
                BoardOAuthCrypto.sha256(
                        evidenceCanonical.getBytes(StandardCharsets.US_ASCII)));
        return new BoardConnectorProtectedRequestAttestation(
                pendingFamily.getTenantId(),
                pendingFamily.getMemberId(),
                pendingFamily.getUserId(),
                pendingFamily.getIssuerUri(),
                pendingFamily.getResourceUri(),
                pendingFamily.getClientId(),
                HexFormat.of().formatHex(
                        pendingFamily.getPrincipalSubjectDigest()),
                BoardOAuthProfile.REQUIRED_SCOPES,
                verificationMethod,
                evidenceDigest,
                pendingFamily.getExpiresAt());
    }

    private void requirePresentedAccessTokenCurrentRead(
            IndependentBoardOAuthMapper.TokenContextLocator locator,
            BoardOAuthTokenFamily pendingFamily,
            List<BoardOAuthToken> pendingTokens,
            byte[] presentedAccessTokenDigest,
            Date transitionAt) {
        BoardOAuthToken token = findPresentedAccessToken(
                pendingTokens,
                locator == null ? null : locator.getTokenId(),
                presentedAccessTokenDigest);
        if (token == null
                || pendingFamily == null
                || !Objects.equals(token.getFamilyId(), pendingFamily.getFamilyId())
                || !Objects.equals(token.getTokenType(), "ACCESS")
                || !Objects.equals(token.getGeneration(), 0L)
                || !Objects.equals(token.getStatus(), STATUS_ACTIVE)
                || token.getUsedAt() != null || token.getRevokedAt() != null
                || token.getIssuedAt() == null || token.getIssuedAt().after(transitionAt)
                || token.getExpiresAt() == null || !token.getExpiresAt().after(transitionAt)
                || !Objects.equals(token.getResourceUri(), BoardOAuthProfile.RESOURCE)
                || !Objects.equals(
                        token.getScopeCanonical(), BoardOAuthProfile.CANONICAL_SCOPE)
                || !sameBytes(token.getScopeDigest(), OAUTH_SCOPE_DIGEST)) {
            throw new ServiceException(
                    IndependentBoardOAuthFirstProtectedRequestTransactionRunner.ACCESS_TOKEN_INVALID,
                    401);
        }
    }

    private BoardOAuthToken findPresentedAccessToken(
            List<BoardOAuthToken> tokens,
            Long expectedTokenId,
            byte[] presentedAccessTokenDigest) {
        if (tokens == null || expectedTokenId == null
                || presentedAccessTokenDigest == null
                || presentedAccessTokenDigest.length != BoardOAuthCrypto.SHA256_BYTES) {
            return null;
        }
        BoardOAuthToken match = null;
        for (BoardOAuthToken token : tokens) {
            if (token != null
                    && Objects.equals(token.getId(), expectedTokenId)
                    && sameBytes(token.getTokenDigest(), presentedAccessTokenDigest)) {
                if (match != null) {
                    return null;
                }
                match = token;
            }
        }
        return match;
    }

    private BoardOAuthReceipt requireTokenFamilyCreationProvenance(
            BoardOAuthAuthorizationRequest request,
            BoardOAuthAuthorizationCode code,
            BoardOAuthTokenFamily pendingFamily,
            List<BoardOAuthToken> pendingTokens,
            List<BoardOAuthReceipt> candidates) {
        if (candidates == null || candidates.size() != 1) {
            throw new ServiceException(
                    "BOARD_OAUTH_TOKEN_FAMILY_CREATED_RECEIPT_CARDINALITY_INVALID",
                    500);
        }
        BoardOAuthReceipt receipt = candidates.get(0);
        try {
            BoardOAuthTokenFamilyCreatedReceiptFactory.validate(
                    receipt, request, code, pendingFamily, pendingTokens);
        } catch (RuntimeException invalidProvenance) {
            throw new ServiceException(
                    "BOARD_OAUTH_TOKEN_FAMILY_CREATED_RECEIPT_INVALID", 500);
        }
        return receipt;
    }

    private void requireCurrentOAuthClientForBearer(
            BoardOAuthClient client,
            BoardConnectorProtectedRequestAttestation attestation,
            Date transitionAt) {
        requireCurrentOAuthClient(client, attestation, transitionAt);
        if (!OAUTH_CLIENT_ID.matcher(client.getClientId()).matches()
                || !Objects.equals(
                        client.getClientName(),
                        IndependentBoardOAuthClientRegistrationService
                                .NEUTRAL_CLIENT_NAME)
                || client.getRegisteredAt() == null
                || client.getExpiresAt() == null
                || client.getExpiresAt().getTime()
                        != client.getRegisteredAt().getTime()
                                + 31L * 24L * 60L * 60L * 1000L
                || client.getCreatedAt() == null
                || client.getUpdatedAt() == null
                || client.getUpdatedAt().before(client.getCreatedAt())) {
            throw new ServiceException("BOARD_OAUTH_CLIENT_PROFILE_DRIFT", 409);
        }
    }

    private void requireCurrentOAuthClient(
            BoardOAuthClient client,
            BoardConnectorProtectedRequestAttestation attestation,
            Date transitionAt) {
        if (client == null
                || client.getId() == null || client.getId() <= 0L
                || !Objects.equals(client.getClientId(), attestation.clientId())
                || !Objects.equals(client.getIssuerUri(), BoardOAuthProfile.ISSUER)
                || !Objects.equals(client.getResourceUri(), BoardOAuthProfile.RESOURCE)
                || !Objects.equals(client.getProductCode(), IndependentBoardEntitlementService.PRODUCT_CODE)
                || !Objects.equals(client.getSourceCode(), SOURCE_CODE)
                || !Objects.equals(client.getConnectorCode(), CONNECTOR_CODE)
                || !Objects.equals(client.getTokenEndpointAuthMethod(), "none")
                || !Objects.equals(client.getGrantTypesCanonical(), "authorization_code refresh_token")
                || !Objects.equals(client.getResponseTypesCanonical(), "code")
                || !Objects.equals(client.getScopeCanonical(), BoardOAuthProfile.CANONICAL_SCOPE)
                || !sameBytes(client.getScopeDigest(), OAUTH_SCOPE_DIGEST)
                || client.getMetadataDigest() == null || client.getMetadataDigest().length != 32
                || client.getRegistrationSourceDigest() == null
                || client.getRegistrationSourceDigest().length != 32
                || !Objects.equals(client.getStatus(), STATUS_ACTIVE)
                || client.getRegisteredAt() == null || client.getRegisteredAt().after(transitionAt)
                || client.getExpiresAt() == null || !client.getExpiresAt().after(transitionAt)
                || client.getExpiresAt().before(attestation.validUntil())
                || client.getTerminatedAt() != null
                || client.getVersion() == null || client.getVersion() < 0L
                || !BoardOAuthProfile.isAllowedLoopbackRedirect(client.getRedirectUri())
                || !Objects.equals(
                        client.getRedirectPort(),
                        BoardOAuthProfile.requireLoopbackPort(client.getRedirectUri()))) {
            throw new ServiceException("BOARD_OAUTH_CLIENT_NOT_CURRENT", 403);
        }
    }

    private BoardConnectorBinding requireAppliedBindingMutationCurrentRead(
            BoardConnectorBinding expected) {
        BoardConnectorBinding current = expected == null
                ? null
                : mapper.selectConnectorBindingForUpdate(
                        expected.getTenantId(), expected.getMemberId(), expected.getUserId(),
                        expected.getProductCode(), expected.getSourceCode(), expected.getConnectorCode());
        List<String> scopes = current == null
                ? null
                : mapper.selectConnectorBindingScopesForUpdate(current.getBindingId());
        List<String> sortedCurrentScopes = scopes == null ? null : sortedScopes(scopes);
        List<String> sortedExpectedScopes = expected == null
                ? null
                : sortedScopes(expected.getScopes());
        if (current == null || expected == null || sortedCurrentScopes == null
                || current.getId() == null || current.getId() <= 0L
                || !Objects.equals(current.getId(), expected.getId())
                || !Objects.equals(current.getBindingId(), expected.getBindingId())
                || !Objects.equals(current.getTenantId(), expected.getTenantId())
                || !Objects.equals(current.getMemberId(), expected.getMemberId())
                || !Objects.equals(current.getUserId(), expected.getUserId())
                || !Objects.equals(current.getProductCode(), expected.getProductCode())
                || !Objects.equals(current.getSourceCode(), expected.getSourceCode())
                || !Objects.equals(current.getConnectorCode(), expected.getConnectorCode())
                || !Objects.equals(current.getIssuerUri(), expected.getIssuerUri())
                || !Objects.equals(current.getResourceUri(), expected.getResourceUri())
                || !Objects.equals(current.getClientId(), expected.getClientId())
                || !Objects.equals(current.getPrincipalSubjectDigest(), expected.getPrincipalSubjectDigest())
                || !Objects.equals(current.getStatus(), STATUS_ACTIVE)
                || !Objects.equals(current.getVerificationMethod(), expected.getVerificationMethod())
                || !Objects.equals(current.getEvidenceDigest(), expected.getEvidenceDigest())
                || !sameDate(current.getVerifiedAt(), expected.getVerifiedAt())
                || !sameDate(current.getLastSeenAt(), expected.getLastSeenAt())
                || !sameDate(current.getValidUntil(), expected.getValidUntil())
                || current.getRevokedAt() != null
                || !Objects.equals(current.getVersion(), expected.getVersion())
                || current.getCreatedAt() == null || current.getUpdatedAt() == null
                || current.getUpdatedAt().before(current.getCreatedAt())
                || !sameDate(current.getUpdatedAt(), current.getVerifiedAt())
                || !Objects.equals(sortedCurrentScopes, sortedExpectedScopes)
                || !Objects.equals(new HashSet<>(sortedCurrentScopes), REQUIRED_SCOPES)) {
            throw new ServiceException("BOARD_CONNECTOR_BINDING_WRITE_PROOF_INVALID", 500);
        }
        current.setScopes(sortedCurrentScopes);
        return current;
    }

    private void requirePendingFamilyBeforeImage(
            BoardOAuthTokenFamily family,
            List<BoardOAuthToken> tokens,
            String expectedFamilyId,
            BoardConnectorProtectedRequestAttestation attestation,
            BoardConnectorBindingKey key,
            byte[] principalDigest,
            Date transitionAt) {
        if (family == null
                || family.getId() == null || family.getId() <= 0L
                || !Objects.equals(family.getFamilyId(), expectedFamilyId)
                || family.getOriginAuthorizationCodeId() == null
                || family.getOriginAuthorizationCodeId() <= 0L
                || !Objects.equals(family.getClientId(), attestation.clientId())
                || !familyMatchesKeyAndProfile(family, key, principalDigest)
                || family.getConsentIntent() == null
                || family.getBindingId() != null || family.getBindingVersion() != null
                || !Objects.equals(family.getStatus(), "PENDING_BINDING")
                || !Objects.equals(family.getCurrentRefreshGeneration(), 0L)
                || family.getIssuedAt() == null || family.getIssuedAt().after(transitionAt)
                || family.getActivatedAt() != null || family.getTerminatedAt() != null
                || family.getExpiresAt() == null || !family.getExpiresAt().after(transitionAt)
                || !sameDate(family.getExpiresAt(), attestation.validUntil())
                || !Objects.equals(family.getVersion(), 0L)) {
            throw new ServiceException("BOARD_OAUTH_PENDING_FAMILY_STATE_INVALID", 409);
        }
        requirePendingGenerationZeroTokens(family, tokens, transitionAt);
    }

    private void requireOldActiveFamilyBeforeImage(
            BoardConnectorBindingActivationLease lease,
            BoardOAuthTokenFamily family,
            List<BoardOAuthToken> tokens,
            BoardConnectorBindingKey key,
            Date transitionAt) {
        BoardConnectorBinding existing = lease.existingBinding(oauthActivationOwnerToken);
        if (lease.mode() == BoardConnectorBindingActivationLease.Mode.FIRST_ACTIVATION) {
            if (existing != null || family != null || !tokens.isEmpty()) {
                throw new ServiceException("BOARD_OAUTH_BINDING_FAMILY_DRIFT", 409);
            }
            return;
        }
        if (existing == null) {
            throw new ServiceException("BOARD_CONNECTOR_ACTIVATION_LEASE_INVALID", 500);
        }
        boolean activeBinding = Objects.equals(existing.getStatus(), STATUS_ACTIVE);
        if (activeBinding != (family != null)) {
            throw new ServiceException("BOARD_OAUTH_BINDING_FAMILY_DRIFT", 409);
        }
        if (!activeBinding) {
            if (!tokens.isEmpty()) {
                throw new ServiceException("BOARD_OAUTH_BINDING_FAMILY_DRIFT", 409);
            }
            return;
        }
        byte[] expectedPrincipal = parseSha256(existing.getPrincipalSubjectDigest());
        if (family.getId() == null || family.getId() <= 0L
                || !familyMatchesKeyAndProfile(family, key, expectedPrincipal)
                || !Objects.equals(family.getClientId(), existing.getClientId())
                || !Objects.equals(family.getBindingId(), existing.getBindingId())
                || !Objects.equals(family.getBindingVersion(), existing.getVersion())
                || !Objects.equals(family.getStatus(), STATUS_ACTIVE)
                || family.getCurrentRefreshGeneration() == null
                || family.getCurrentRefreshGeneration() < 0L
                || family.getIssuedAt() == null || family.getIssuedAt().after(transitionAt)
                || family.getActivatedAt() == null || family.getActivatedAt().after(transitionAt)
                || family.getExpiresAt() == null || !family.getExpiresAt().after(transitionAt)
                || family.getTerminatedAt() != null
                || family.getVersion() == null || family.getVersion() <= 0L) {
            throw new ServiceException("BOARD_OAUTH_ACTIVE_FAMILY_STATE_INVALID", 409);
        }
        requireOldFamilyTokens(family, tokens, transitionAt);
    }

    private boolean familyMatchesKeyAndProfile(
            BoardOAuthTokenFamily family,
            BoardConnectorBindingKey key,
            byte[] principalDigest) {
        return Objects.equals(family.getTenantId(), key.tenantId())
                && Objects.equals(family.getMemberId(), key.memberId())
                && Objects.equals(family.getUserId(), key.userId())
                && Objects.equals(family.getProductCode(), key.productCode())
                && Objects.equals(family.getSourceCode(), SOURCE_CODE)
                && Objects.equals(family.getConnectorCode(), CONNECTOR_CODE)
                && Objects.equals(family.getIssuerUri(), BoardOAuthProfile.ISSUER)
                && Objects.equals(family.getResourceUri(), BoardOAuthProfile.RESOURCE)
                && Objects.equals(family.getScopeCanonical(), BoardOAuthProfile.CANONICAL_SCOPE)
                && sameBytes(family.getScopeDigest(), OAUTH_SCOPE_DIGEST)
                && sameBytes(family.getPrincipalSubjectDigest(), principalDigest);
    }

    private void requirePendingGenerationZeroTokens(
            BoardOAuthTokenFamily family,
            List<BoardOAuthToken> tokens,
            Date transitionAt) {
        if (tokens == null || tokens.size() != 2) {
            throw new ServiceException("BOARD_OAUTH_PENDING_TOKEN_SET_INVALID", 409);
        }
        Set<String> types = new HashSet<>();
        Set<Long> ids = new HashSet<>();
        for (BoardOAuthToken token : tokens) {
            if (token == null
                    || token.getId() == null || token.getId() <= 0L || !ids.add(token.getId())
                    || !types.add(token.getTokenType())
                    || !Set.of("ACCESS", "REFRESH").contains(token.getTokenType())
                    || !Objects.equals(token.getFamilyId(), family.getFamilyId())
                    || !Objects.equals(token.getGeneration(), 0L)
                    || !Objects.equals(token.getResourceUri(), BoardOAuthProfile.RESOURCE)
                    || !Objects.equals(token.getScopeCanonical(), BoardOAuthProfile.CANONICAL_SCOPE)
                    || !sameBytes(token.getScopeDigest(), OAUTH_SCOPE_DIGEST)
                    || token.getTokenDigest() == null || token.getTokenDigest().length != 32
                    || !Objects.equals(token.getStatus(), STATUS_ACTIVE)
                    || token.getIssuedAt() == null || token.getIssuedAt().before(family.getIssuedAt())
                    || token.getIssuedAt().after(transitionAt)
                    || token.getUsedAt() != null || token.getRevokedAt() != null
                    || token.getExpiresAt() == null || !token.getExpiresAt().after(transitionAt)
                    || token.getExpiresAt().after(family.getExpiresAt())
                    || !Objects.equals(token.getVersion(), 0L)) {
                throw new ServiceException("BOARD_OAUTH_PENDING_TOKEN_SET_INVALID", 409);
            }
            if (Objects.equals(token.getTokenType(), "ACCESS")
                    && token.getExpiresAt().getTime() != token.getIssuedAt().getTime() + 600_000L) {
                throw new ServiceException("BOARD_OAUTH_PENDING_TOKEN_SET_INVALID", 409);
            }
            if (Objects.equals(token.getTokenType(), "REFRESH")
                    && !sameDate(token.getExpiresAt(), family.getExpiresAt())) {
                throw new ServiceException("BOARD_OAUTH_PENDING_TOKEN_SET_INVALID", 409);
            }
        }
    }

    private void requireOldFamilyTokens(
            BoardOAuthTokenFamily family,
            List<BoardOAuthToken> tokens,
            Date transitionAt) {
        if (tokens == null
                || tokens.isEmpty()
                || tokens.size() > MAX_OAUTH_FAMILY_TOKEN_HISTORY
                || family.getCurrentRefreshGeneration() == null
                || family.getCurrentRefreshGeneration() < 0L
                || Objects.equals(
                        family.getCurrentRefreshGeneration(), Long.MAX_VALUE)) {
            throw new ServiceException("BOARD_OAUTH_ACTIVE_TOKEN_SET_INVALID", 409);
        }
        Set<Long> ids = new HashSet<>();
        int currentActiveRefresh = 0;
        for (BoardOAuthToken token : tokens) {
            if (token == null
                    || token.getId() == null || token.getId() <= 0L || !ids.add(token.getId())
                    || !Objects.equals(token.getFamilyId(), family.getFamilyId())
                    || !Set.of("ACCESS", "REFRESH").contains(token.getTokenType())
                    || token.getGeneration() == null || token.getGeneration() < 0L
                    || token.getGeneration() > family.getCurrentRefreshGeneration()
                    || !Objects.equals(token.getResourceUri(), BoardOAuthProfile.RESOURCE)
                    || !Objects.equals(token.getScopeCanonical(), BoardOAuthProfile.CANONICAL_SCOPE)
                    || !sameBytes(token.getScopeDigest(), OAUTH_SCOPE_DIGEST)
                    || token.getTokenDigest() == null || token.getTokenDigest().length != 32
                    || token.getIssuedAt() == null || token.getIssuedAt().after(transitionAt)
                    || token.getExpiresAt() == null || token.getExpiresAt().after(family.getExpiresAt())
                    || token.getVersion() == null || token.getVersion() < 0L
                    || Objects.equals(token.getVersion(), Long.MAX_VALUE)
                    || !Set.of(STATUS_ACTIVE, "USED", STATUS_REVOKED, "EXPIRED")
                            .contains(token.getStatus())) {
                throw new ServiceException("BOARD_OAUTH_ACTIVE_TOKEN_SET_INVALID", 409);
            }
            if (Objects.equals(token.getTokenType(), "REFRESH")
                    && Objects.equals(token.getGeneration(), family.getCurrentRefreshGeneration())
                    && Objects.equals(token.getStatus(), STATUS_ACTIVE)) {
                currentActiveRefresh++;
            }
        }
        if (currentActiveRefresh != 1) {
            throw new ServiceException("BOARD_OAUTH_ACTIVE_TOKEN_SET_INVALID", 409);
        }
    }

    private List<BoardOAuthToken> requireTokenCurrentRead(String familyId) {
        List<BoardOAuthToken> tokens = oauthMapper.selectFamilyTokensForUpdate(familyId);
        if (tokens == null) {
            throw new ServiceException("BOARD_OAUTH_TOKEN_CURRENT_READ_FAILED", 500);
        }
        return tokens;
    }

    private BoardConnectorBinding verifyOAuthFamilyActivationFinalState(
            BoardConnectorBindingActivationLease lease) {
        BoardOAuthFamilyActivationProof proof = requireActivationProof(lease);
        BoardOAuthFamilyActivationContext context = proof.context();
        if (context.bindingTopology() != lease.mode()) {
            throw new ServiceException("BOARD_OAUTH_CONSENT_INTENT_TOPOLOGY_CONFLICT", 409);
        }
        requireIntentMatchesBindingTopology(context.consentIntent(), lease);
        Date verificationNow = Date.from(clock.instant());
        verifyActivationAuthorityCurrentRead(lease, verificationNow);
        BoardConnectorBinding verifiedBinding = verifyAppliedBindingCurrentRead(lease);
        if (verifiedBinding.getValidUntil() == null
                || !verifiedBinding.getValidUntil().after(verificationNow)) {
            throw new ServiceException("BOARD_OAUTH_ACTIVATION_AUTHORITY_EXPIRED", 409);
        }
        BoardOAuthClient currentClient = oauthMapper.selectClientForUpdate(context.clientId());
        requireOAuthClientMatchesProof(proof.client(), currentClient);
        if (currentClient.getExpiresAt() == null
                || !currentClient.getExpiresAt().after(verificationNow)) {
            throw new ServiceException("BOARD_OAUTH_ACTIVATION_AUTHORITY_EXPIRED", 409);
        }
        verifyAuthorizationLineageCurrentRead(proof);
        BoardOAuthTokenFamily activeSlot = oauthMapper.selectActiveTokenFamilySlotForUpdate(
                context.tenantId(), context.memberId(),
                IndependentBoardEntitlementService.PRODUCT_CODE, SOURCE_CODE, CONNECTOR_CODE);
        BoardOAuthTokenFamily pendingSlot = oauthMapper.selectPendingTokenFamilySlotForUpdate(
                context.tenantId(), context.memberId(),
                IndependentBoardEntitlementService.PRODUCT_CODE, SOURCE_CODE, CONNECTOR_CODE);
        if (activeSlot == null
                || !Objects.equals(activeSlot.getFamilyId(), context.familyId())
                || pendingSlot != null) {
            throw new ServiceException("BOARD_OAUTH_ACTIVATION_FINAL_STATE_INVALID", 500);
        }
        BoardOAuthTokenFamily currentNew = oauthMapper.selectTokenFamilyForUpdate(
                context.familyId(), context.clientId());
        List<BoardOAuthToken> currentNewTokens = requireTokenCurrentRead(context.familyId());
        requireActivatedFamilyMatchesProof(
                proof, currentNew, currentNewTokens, verificationNow);

        BoardOAuthFamilyActivationProof.FamilyImage old = proof.oldActiveFamily();
        if (old != null) {
            BoardOAuthTokenFamily currentOld = oauthMapper.selectTokenFamilyForUpdate(
                    old.familyId, old.clientId);
            List<BoardOAuthToken> currentOldTokens = requireTokenCurrentRead(old.familyId);
            requireRevokedOldFamilyMatchesProof(
                    proof, currentOld, currentOldTokens, lease.transitionAt());
        }
        verifyTokenFamilyCreationReceiptCurrentRead(proof);
        return verifiedBinding;
    }

    private void verifyActivationAuthorityCurrentRead(
            BoardConnectorBindingActivationLease lease,
            Date verificationNow) {
        BoardConnectorBindingKey key = lease.key();
        BoardEnterpriseMemberScope currentMember = mapper.selectExactActiveMemberForUpdate(
                key.tenantId(), key.memberId(), key.userId());
        BoardConnectorBindingActivationLease.MemberImage member =
                lease.memberImage(oauthActivationOwnerToken);
        if (currentMember == null
                || !Objects.equals(currentMember.getMemberId(), member.memberId)
                || !Objects.equals(currentMember.getTenantId(), member.tenantId)
                || !Objects.equals(currentMember.getTenantName(), member.tenantName)
                || !Objects.equals(currentMember.getUserId(), member.userId)
                || !Objects.equals(currentMember.getMemberRole(), member.memberRole)
                || !Objects.equals(currentMember.getStatus(), member.status)
                || !Objects.equals(currentMember.getDelFlag(), member.delFlag)) {
            throw new ServiceException("BOARD_OAUTH_ACTIVATION_AUTHORITY_DRIFT", 409);
        }
        requireExactMember(
                currentMember, key.tenantId(), key.memberId(), key.userId());

        BoardProductEntitlement currentEntitlement = mapper.selectEntitlementForUpdate(
                key.tenantId(), key.memberId(), key.productCode());
        BoardConnectorBindingActivationLease.EntitlementImage entitlement =
                lease.entitlementImage(oauthActivationOwnerToken);
        if (currentEntitlement == null
                || !Objects.equals(currentEntitlement.getId(), entitlement.id)
                || !Objects.equals(currentEntitlement.getTenantId(), entitlement.tenantId)
                || !Objects.equals(currentEntitlement.getMemberId(), entitlement.memberId)
                || !Objects.equals(currentEntitlement.getUserId(), entitlement.userId)
                || !Objects.equals(currentEntitlement.getProductCode(), entitlement.productCode)
                || !Objects.equals(currentEntitlement.getPlanCode(), entitlement.planCode)
                || !Objects.equals(currentEntitlement.getStatus(), entitlement.status)
                || !Objects.equals(
                        currentEntitlement.getConnectorBindingId(), entitlement.connectorBindingId)
                || !sameDate(
                        currentEntitlement.getConnectorVerifiedAt(), entitlement.connectorVerifiedAt)
                || !sameDate(currentEntitlement.getValidFrom(), entitlement.validFrom)
                || !sameDate(currentEntitlement.getValidUntil(), entitlement.validUntil)
                || !Objects.equals(currentEntitlement.getVersion(), entitlement.version)
                || !sameDate(currentEntitlement.getCreatedAt(), entitlement.createdAt)
                || !sameDate(currentEntitlement.getUpdatedAt(), entitlement.updatedAt)) {
            throw new ServiceException("BOARD_OAUTH_ACTIVATION_AUTHORITY_DRIFT", 409);
        }
        requireCurrentVipEntitlementState(
                currentEntitlement,
                key.tenantId(), key.memberId(), key.userId(), key.productCode(), verificationNow);

        BoardProductPlan currentPlan = mapper.selectActivePlanForUpdate(
                key.productCode(), IndependentBoardEntitlementService.VIP_PLAN);
        BoardConnectorBindingActivationLease.PlanImage plan =
                lease.planImage(oauthActivationOwnerToken);
        if (currentPlan == null
                || !Objects.equals(currentPlan.getProductCode(), plan.productCode)
                || !Objects.equals(currentPlan.getPlanCode(), plan.planCode)
                || !Objects.equals(currentPlan.getVip(), plan.vip)
                || !Objects.equals(currentPlan.getConnectorRequired(), plan.connectorRequired)
                || !Objects.equals(currentPlan.getStatus(), plan.status)) {
            throw new ServiceException("BOARD_OAUTH_ACTIVATION_AUTHORITY_DRIFT", 409);
        }
        requireCurrentVipPlan(currentPlan);
    }

    private void requireOAuthClientMatchesProof(
            BoardOAuthFamilyActivationProof.ClientImage before,
            BoardOAuthClient current) {
        if (current == null
                || !Objects.equals(before.id, current.getId())
                || !Objects.equals(before.clientId, current.getClientId())
                || !Objects.equals(before.clientName, current.getClientName())
                || !Objects.equals(before.issuerUri, current.getIssuerUri())
                || !Objects.equals(before.resourceUri, current.getResourceUri())
                || !Objects.equals(before.productCode, current.getProductCode())
                || !Objects.equals(before.sourceCode, current.getSourceCode())
                || !Objects.equals(before.connectorCode, current.getConnectorCode())
                || !Objects.equals(before.redirectPort, current.getRedirectPort())
                || !Objects.equals(before.redirectUri, current.getRedirectUri())
                || !Objects.equals(before.tokenEndpointAuthMethod, current.getTokenEndpointAuthMethod())
                || !Objects.equals(before.grantTypesCanonical, current.getGrantTypesCanonical())
                || !Objects.equals(before.responseTypesCanonical, current.getResponseTypesCanonical())
                || !Objects.equals(before.scopeCanonical, current.getScopeCanonical())
                || !sameBytes(before.scopeDigest, current.getScopeDigest())
                || !sameBytes(before.metadataDigest, current.getMetadataDigest())
                || !sameBytes(before.registrationSourceDigest, current.getRegistrationSourceDigest())
                || !Objects.equals(before.status, current.getStatus())
                || !sameDate(before.registeredAt, current.getRegisteredAt())
                || !sameDate(before.expiresAt, current.getExpiresAt())
                || !sameDate(before.terminatedAt, current.getTerminatedAt())
                || !Objects.equals(before.version, current.getVersion())
                || !sameDate(before.createdAt, current.getCreatedAt())
                || !sameDate(before.updatedAt, current.getUpdatedAt())) {
            throw new ServiceException("BOARD_OAUTH_CLIENT_FINAL_STATE_INVALID", 500);
        }
    }

    private void verifyAuthorizationLineageCurrentRead(
            BoardOAuthFamilyActivationProof proof) {
        if (!proof.hasPresentedBearerProvenance()) {
            return;
        }
        BoardOAuthFamilyActivationProof.RequestImage request =
                proof.authorizationRequest();
        BoardOAuthFamilyActivationProof.CodeImage code =
                proof.authorizationCode();
        BoardOAuthAuthorizationRequest currentRequest =
                oauthMapper.selectAuthorizationRequestByIdAndClientForUpdate(
                        request.id, request.clientId);
        BoardOAuthAuthorizationCode currentCode =
                oauthMapper.selectAuthorizationCodeByIdAndClientForUpdate(
                        code.id, code.clientId);
        if (!sameAuthorizationRequest(request, currentRequest)
                || !sameAuthorizationCode(code, currentCode)) {
            throw new ServiceException(
                    "BOARD_OAUTH_AUTHORIZATION_LINEAGE_DRIFT", 409);
        }
    }

    private void verifyTokenFamilyCreationReceiptCurrentRead(
            BoardOAuthFamilyActivationProof proof) {
        if (!proof.hasPresentedBearerProvenance()) {
            return;
        }
        BoardOAuthFamilyActivationProof.ReceiptImage expected =
                proof.tokenFamilyCreatedReceipt();
        BoardOAuthFamilyActivationProof.FamilyImage family =
                proof.pendingFamily();
        List<BoardOAuthReceipt> candidates =
                oauthMapper.selectTokenFamilyCreatedReceiptCandidatesForUpdate(
                        family.familyId,
                        family.clientId,
                        family.originAuthorizationCodeId);
        if (candidates == null
                || candidates.size() != 1
                || !sameTokenFamilyCreationReceipt(
                        expected, candidates.get(0))) {
            throw new ServiceException(
                    "BOARD_OAUTH_TOKEN_FAMILY_CREATED_RECEIPT_DRIFT", 409);
        }
    }

    private boolean sameAuthorizationRequest(
            BoardOAuthFamilyActivationProof.RequestImage before,
            BoardOAuthAuthorizationRequest current) {
        return current != null
                && Objects.equals(before.id, current.getId())
                && sameOptionalBytes(
                        before.requestHandleDigest, current.getRequestHandleDigest())
                && Objects.equals(before.clientId, current.getClientId())
                && Objects.equals(before.redirectUri, current.getRedirectUri())
                && Objects.equals(before.codeChallenge, current.getCodeChallenge())
                && Objects.equals(
                        before.codeChallengeMethod, current.getCodeChallengeMethod())
                && sameOptionalBytes(before.stateDigest, current.getStateDigest())
                && Objects.equals(before.stateKeyRef, current.getStateKeyRef())
                && sameOptionalBytes(before.stateNonce, current.getStateNonce())
                && sameOptionalBytes(
                        before.stateCiphertext, current.getStateCiphertext())
                && Objects.equals(before.issuerUri, current.getIssuerUri())
                && Objects.equals(before.resourceUri, current.getResourceUri())
                && Objects.equals(before.productCode, current.getProductCode())
                && Objects.equals(before.sourceCode, current.getSourceCode())
                && Objects.equals(before.connectorCode, current.getConnectorCode())
                && Objects.equals(before.scopeCanonical, current.getScopeCanonical())
                && sameOptionalBytes(before.scopeDigest, current.getScopeDigest())
                && Objects.equals(before.tenantId, current.getTenantId())
                && Objects.equals(before.memberId, current.getMemberId())
                && Objects.equals(before.userId, current.getUserId())
                && sameOptionalBytes(
                        before.principalSubjectDigest,
                        current.getPrincipalSubjectDigest())
                && Objects.equals(before.consentIntent, current.getConsentIntent())
                && Objects.equals(before.status, current.getStatus())
                && sameDate(before.requestedAt, current.getRequestedAt())
                && sameDate(before.expiresAt, current.getExpiresAt())
                && sameDate(before.approvedAt, current.getApprovedAt())
                && sameDate(before.deniedAt, current.getDeniedAt())
                && sameDate(before.consumedAt, current.getConsumedAt())
                && Objects.equals(before.version, current.getVersion())
                && sameDate(before.createdAt, current.getCreatedAt())
                && sameDate(before.updatedAt, current.getUpdatedAt());
    }

    private boolean sameAuthorizationCode(
            BoardOAuthFamilyActivationProof.CodeImage before,
            BoardOAuthAuthorizationCode current) {
        return current != null
                && Objects.equals(before.id, current.getId())
                && sameOptionalBytes(before.codeDigest, current.getCodeDigest())
                && Objects.equals(
                        before.authorizationRequestId,
                        current.getAuthorizationRequestId())
                && Objects.equals(before.clientId, current.getClientId())
                && Objects.equals(before.redirectUri, current.getRedirectUri())
                && Objects.equals(before.codeChallenge, current.getCodeChallenge())
                && Objects.equals(
                        before.codeChallengeMethod, current.getCodeChallengeMethod())
                && Objects.equals(before.issuerUri, current.getIssuerUri())
                && Objects.equals(before.resourceUri, current.getResourceUri())
                && Objects.equals(before.productCode, current.getProductCode())
                && Objects.equals(before.sourceCode, current.getSourceCode())
                && Objects.equals(before.connectorCode, current.getConnectorCode())
                && Objects.equals(before.scopeCanonical, current.getScopeCanonical())
                && sameOptionalBytes(before.scopeDigest, current.getScopeDigest())
                && Objects.equals(before.tenantId, current.getTenantId())
                && Objects.equals(before.memberId, current.getMemberId())
                && Objects.equals(before.userId, current.getUserId())
                && sameOptionalBytes(
                        before.principalSubjectDigest,
                        current.getPrincipalSubjectDigest())
                && Objects.equals(before.consentIntent, current.getConsentIntent())
                && Objects.equals(before.status, current.getStatus())
                && sameDate(before.issuedAt, current.getIssuedAt())
                && sameDate(before.expiresAt, current.getExpiresAt())
                && sameDate(before.usedAt, current.getUsedAt())
                && sameDate(before.revokedAt, current.getRevokedAt())
                && Objects.equals(before.version, current.getVersion())
                && sameDate(before.createdAt, current.getCreatedAt())
                && sameDate(before.updatedAt, current.getUpdatedAt());
    }

    private boolean sameTokenFamilyCreationReceipt(
            BoardOAuthFamilyActivationProof.ReceiptImage before,
            BoardOAuthReceipt current) {
        return current != null
                && Objects.equals(before.id, current.getId())
                && before.id != null && before.id > 0L
                && Objects.equals(before.receiptId, current.getReceiptId())
                && Objects.equals(before.action, current.getAction())
                && Objects.equals(before.clientId, current.getClientId())
                && Objects.equals(
                        before.authorizationRequestId,
                        current.getAuthorizationRequestId())
                && Objects.equals(
                        before.authorizationCodeId,
                        current.getAuthorizationCodeId())
                && Objects.equals(before.familyId, current.getFamilyId())
                && Objects.equals(before.tokenId, current.getTokenId())
                && Objects.equals(before.bindingId, current.getBindingId())
                && Objects.equals(before.tenantId, current.getTenantId())
                && Objects.equals(before.memberId, current.getMemberId())
                && Objects.equals(before.userId, current.getUserId())
                && sameOptionalBytes(
                        before.principalSubjectDigest,
                        current.getPrincipalSubjectDigest())
                && Objects.equals(before.actorType, current.getActorType())
                && Objects.equals(before.actorUserId, current.getActorUserId())
                && sameOptionalBytes(
                        before.actorSubjectDigest,
                        current.getActorSubjectDigest())
                && Objects.equals(before.correlationId, current.getCorrelationId())
                && sameOptionalBytes(before.payloadDigest, current.getPayloadDigest())
                && Objects.equals(before.evidenceLevel, current.getEvidenceLevel())
                && sameDate(before.createdAt, current.getCreatedAt());
    }

    private void requireActivatedFamilyMatchesProof(
            BoardOAuthFamilyActivationProof proof,
            BoardOAuthTokenFamily current,
            List<BoardOAuthToken> currentTokens,
            Date verificationNow) {
        BoardOAuthFamilyActivationProof.FamilyImage before = proof.pendingFamily();
        BoardOAuthFamilyActivationContext context = proof.context();
        if (proof.hasPresentedBearerProvenance()
                && (!KNOWN_VERIFICATION_METHODS.contains(proof.verificationMethod())
                        || !Objects.equals(
                                proof.binding().verificationMethod,
                                proof.verificationMethod())
                        || currentTokens == null
                        || currentTokens.stream().filter(token ->
                                Objects.equals(token.getId(), proof.presentedTokenId())
                                        && sameBytes(
                                                token.getTokenDigest(),
                                                proof.presentedTokenDigest())
                                        && Objects.equals(token.getTokenType(), "ACCESS")
                                        && Objects.equals(token.getGeneration(), 0L)
                                        && Objects.equals(token.getStatus(), STATUS_ACTIVE))
                                .count() != 1L)) {
            throw new ServiceException(
                    "BOARD_OAUTH_PRESENTED_BEARER_PROOF_INVALID", 500);
        }
        if (current != null
                && (current.getExpiresAt() == null
                        || !current.getExpiresAt().after(verificationNow)
                        || currentTokens == null
                        || currentTokens.stream().anyMatch(token -> token.getExpiresAt() == null
                                || !token.getExpiresAt().after(verificationNow)))) {
            throw new ServiceException("BOARD_OAUTH_ACTIVATION_AUTHORITY_EXPIRED", 409);
        }
        if (current == null
                || !sameFamilyImmutable(before, current)
                || !Objects.equals(current.getBindingId(), context.bindingId())
                || !Objects.equals(current.getBindingVersion(), context.bindingVersion())
                || !Objects.equals(before.consentIntent, context.consentIntent())
                || !Objects.equals(current.getStatus(), STATUS_ACTIVE)
                || (proof.hasPresentedBearerProvenance()
                        && (!Objects.equals(current.getLifecycleSlot(), STATUS_ACTIVE)
                                || !sameDate(before.createdAt, current.getCreatedAt())
                                || !hasTransitionUpdate(
                                        before.updatedAt,
                                        current.getUpdatedAt(),
                                        context.createdAt())))
                || !sameDate(current.getActivatedAt(), context.createdAt())
                || current.getTerminatedAt() != null
                || before.version == null || Objects.equals(before.version, Long.MAX_VALUE)
                || !Objects.equals(current.getVersion(), before.version + 1L)
                || !sameTokenSetUnchanged(proof.pendingTokens(), currentTokens)) {
            throw new ServiceException("BOARD_OAUTH_ACTIVATION_FINAL_STATE_INVALID", 500);
        }
    }

    private void requireRevokedOldFamilyMatchesProof(
            BoardOAuthFamilyActivationProof proof,
            BoardOAuthTokenFamily current,
            List<BoardOAuthToken> currentTokens,
            Date transitionAt) {
        BoardOAuthFamilyActivationProof.FamilyImage before = proof.oldActiveFamily();
        if (current == null
                || !sameFamilyImmutable(before, current)
                || !Objects.equals(current.getBindingId(), before.bindingId)
                || !Objects.equals(current.getBindingVersion(), before.bindingVersion)
                || !Objects.equals(current.getStatus(), STATUS_REVOKED)
                || (proof.hasPresentedBearerProvenance()
                        && (current.getLifecycleSlot() != null
                                || !sameDate(before.createdAt, current.getCreatedAt())
                                || !hasTransitionUpdate(
                                        before.updatedAt,
                                        current.getUpdatedAt(),
                                        transitionAt)))
                || !sameDate(current.getActivatedAt(), before.activatedAt)
                || !sameDate(current.getTerminatedAt(), transitionAt)
                || before.version == null || Objects.equals(before.version, Long.MAX_VALUE)
                || !Objects.equals(current.getVersion(), before.version + 1L)
                || !sameOldTokenSetAfterRevocation(
                        proof.oldActiveTokens(), currentTokens, transitionAt)) {
            throw new ServiceException("BOARD_OAUTH_REAUTHORIZATION_FINAL_STATE_INVALID", 500);
        }
    }

    private boolean sameFamilyImmutable(
            BoardOAuthFamilyActivationProof.FamilyImage before,
            BoardOAuthTokenFamily current) {
        return Objects.equals(before.id, current.getId())
                && Objects.equals(before.familyId, current.getFamilyId())
                && Objects.equals(before.originAuthorizationCodeId, current.getOriginAuthorizationCodeId())
                && Objects.equals(before.clientId, current.getClientId())
                && Objects.equals(before.tenantId, current.getTenantId())
                && Objects.equals(before.memberId, current.getMemberId())
                && Objects.equals(before.userId, current.getUserId())
                && Objects.equals(before.productCode, current.getProductCode())
                && Objects.equals(before.sourceCode, current.getSourceCode())
                && Objects.equals(before.connectorCode, current.getConnectorCode())
                && Objects.equals(before.issuerUri, current.getIssuerUri())
                && Objects.equals(before.resourceUri, current.getResourceUri())
                && Objects.equals(before.scopeCanonical, current.getScopeCanonical())
                && sameBytes(before.scopeDigest, current.getScopeDigest())
                && sameBytes(before.principalSubjectDigest, current.getPrincipalSubjectDigest())
                && Objects.equals(before.consentIntent, current.getConsentIntent())
                && Objects.equals(before.currentRefreshGeneration, current.getCurrentRefreshGeneration())
                && sameDate(before.issuedAt, current.getIssuedAt())
                && sameDate(before.expiresAt, current.getExpiresAt())
                && sameDate(before.createdAt, current.getCreatedAt());
    }

    private boolean sameTokenSetUnchanged(
            List<BoardOAuthFamilyActivationProof.TokenImage> before,
            List<BoardOAuthToken> current) {
        if (current == null || before.size() != current.size()) {
            return false;
        }
        for (int index = 0; index < before.size(); index++) {
            if (!sameToken(before.get(index), current.get(index), false, null)) {
                return false;
            }
        }
        return true;
    }

    private boolean sameOldTokenSetAfterRevocation(
            List<BoardOAuthFamilyActivationProof.TokenImage> before,
            List<BoardOAuthToken> current,
            Date transitionAt) {
        if (current == null || before.size() != current.size()) {
            return false;
        }
        for (int index = 0; index < before.size(); index++) {
            if (!sameToken(before.get(index), current.get(index), true, transitionAt)) {
                return false;
            }
        }
        return true;
    }

    private boolean sameToken(
            BoardOAuthFamilyActivationProof.TokenImage before,
            BoardOAuthToken current,
            boolean expectActiveRevocation,
            Date transitionAt) {
        if (current == null
                || !Objects.equals(before.id, current.getId())
                || !sameBytes(before.tokenDigest, current.getTokenDigest())
                || !Objects.equals(before.familyId, current.getFamilyId())
                || !Objects.equals(before.tokenType, current.getTokenType())
                || !Objects.equals(before.generation, current.getGeneration())
                || !Objects.equals(before.resourceUri, current.getResourceUri())
                || !Objects.equals(before.scopeCanonical, current.getScopeCanonical())
                || !sameBytes(before.scopeDigest, current.getScopeDigest())
                || !sameDate(before.issuedAt, current.getIssuedAt())
                || !sameDate(before.expiresAt, current.getExpiresAt())
                || !sameDate(before.createdAt, current.getCreatedAt())) {
            return false;
        }
        if (!expectActiveRevocation || !Objects.equals(before.status, STATUS_ACTIVE)) {
            return Objects.equals(before.status, current.getStatus())
                    && Objects.equals(
                            before.activeRefreshSlot,
                            current.getActiveRefreshSlot())
                    && sameDate(before.usedAt, current.getUsedAt())
                    && sameDate(before.revokedAt, current.getRevokedAt())
                    && Objects.equals(before.version, current.getVersion())
                    && sameDate(before.updatedAt, current.getUpdatedAt());
        }
        String expectedStatus = !before.expiresAt.after(transitionAt) ? "EXPIRED" : STATUS_REVOKED;
        Date expectedRevokedAt = Objects.equals(expectedStatus, STATUS_REVOKED) ? transitionAt : null;
        return Objects.equals(current.getStatus(), expectedStatus)
                && current.getActiveRefreshSlot() == null
                && sameDate(before.usedAt, current.getUsedAt())
                && sameDate(current.getRevokedAt(), expectedRevokedAt)
                && before.version != null
                && !Objects.equals(before.version, Long.MAX_VALUE)
                && Objects.equals(current.getVersion(), before.version + 1L)
                && hasTransitionUpdate(
                        before.updatedAt,
                        current.getUpdatedAt(),
                        transitionAt);
    }

    private BoardConnectorBinding verifyAppliedBindingCurrentRead(
            BoardConnectorBindingActivationLease lease) {
        BoardOAuthFamilyActivationProof proof = requireActivationProof(lease);
        BoardOAuthFamilyActivationContext context = proof.context();
        BoardOAuthFamilyActivationProof.BindingImage expected = proof.binding();
        BoardConnectorBinding current = mapper.selectConnectorBindingForUpdate(
                context.tenantId(), context.memberId(), context.userId(),
                IndependentBoardEntitlementService.PRODUCT_CODE, SOURCE_CODE, CONNECTOR_CODE);
        List<String> scopes = current == null
                ? null
                : mapper.selectConnectorBindingScopesForUpdate(current.getBindingId());
        List<String> sortedCurrentScopes = scopes == null ? null : sortedScopes(scopes);
        if (current == null || sortedCurrentScopes == null
                || !Objects.equals(current.getId(), expected.id)
                || !Objects.equals(current.getBindingId(), expected.bindingId)
                || !Objects.equals(current.getTenantId(), expected.tenantId)
                || !Objects.equals(current.getMemberId(), expected.memberId)
                || !Objects.equals(current.getUserId(), expected.userId)
                || !Objects.equals(current.getProductCode(), expected.productCode)
                || !Objects.equals(current.getSourceCode(), expected.sourceCode)
                || !Objects.equals(current.getConnectorCode(), expected.connectorCode)
                || !Objects.equals(current.getIssuerUri(), expected.issuerUri)
                || !Objects.equals(current.getResourceUri(), expected.resourceUri)
                || !Objects.equals(current.getClientId(), expected.clientId)
                || !Objects.equals(current.getPrincipalSubjectDigest(), expected.principalSubjectDigest)
                || !Objects.equals(current.getStatus(), expected.status)
                || !Objects.equals(current.getVerificationMethod(), expected.verificationMethod)
                || !Objects.equals(current.getEvidenceDigest(), expected.evidenceDigest)
                || !sameDate(current.getVerifiedAt(), expected.verifiedAt)
                || !sameDate(current.getLastSeenAt(), expected.lastSeenAt)
                || !sameDate(current.getValidUntil(), expected.validUntil)
                || !sameDate(current.getRevokedAt(), expected.revokedAt)
                || !Objects.equals(current.getVersion(), expected.version)
                || !sameDate(current.getCreatedAt(), expected.createdAt)
                || !sameDate(current.getUpdatedAt(), expected.updatedAt)
                || !Objects.equals(sortedCurrentScopes, expected.scopes)) {
            throw new ServiceException("BOARD_CONNECTOR_ACTIVATION_FINAL_STATE_INVALID", 500);
        }
        current.setScopes(sortedCurrentScopes);
        return current;
    }

    private void verifyW4aReceipt(BoardConnectorBindingActivationLease lease) {
        BoardOAuthFamilyActivationProof proof = requireActivationProof(lease);
        BoardConnectorBinding currentBinding = verifyAppliedBindingCurrentRead(lease);
        String recomputedPayloadDigest = bindingDigest(
                currentBinding, lease.actorUserId(), "CONNECTOR_BINDING_VERIFIED");
        BoardConnectorBindingReceipt receipt = proof.w4aReceiptId() == null
                ? null
                : mapper.selectConnectorBindingReceiptForUpdate(proof.w4aReceiptId());
        BoardOAuthFamilyActivationContext context = proof.context();
        if (receipt == null
                || !Objects.equals(receipt.getReceiptId(), proof.w4aReceiptId())
                || !Objects.equals(receipt.getBindingId(), context.bindingId())
                || !Objects.equals(receipt.getTenantId(), context.tenantId())
                || !Objects.equals(receipt.getMemberId(), context.memberId())
                || !Objects.equals(receipt.getUserId(), context.userId())
                || !Objects.equals(receipt.getActorUserId(), context.actorUserId())
                || !Objects.equals(receipt.getAction(), "CONNECTOR_BINDING_VERIFIED")
                || !BoardDigest.equal(
                        receipt.getPayloadDigest(), proof.w4aReceiptPayloadDigest())
                || !BoardDigest.equal(receipt.getPayloadDigest(), recomputedPayloadDigest)
                || !Objects.equals(receipt.getEvidenceLevel(), "ACTION_COMPLETED")
                || !sameDate(receipt.getCreatedAt(), context.createdAt())) {
            throw new ServiceException("BOARD_CONNECTOR_BINDING_RECEIPT_PROOF_INVALID", 500);
        }
    }

    private void verifyW4bActivationReceipt(BoardConnectorBindingActivationLease lease) {
        BoardOAuthFamilyActivationContext context = requireActivationProof(lease).context();
        BoardOAuthReceipt receipt = oauthMapper.selectReceiptByReceiptId(context.receiptId());
        if (receipt == null
                || receipt.getId() == null || receipt.getId() <= 0L
                || !Objects.equals(receipt.getReceiptId(), context.receiptId())
                || !Objects.equals(receipt.getAction(), context.action())
                || !Objects.equals(receipt.getClientId(), context.clientId())
                || receipt.getAuthorizationRequestId() != null
                || receipt.getAuthorizationCodeId() != null
                || !Objects.equals(receipt.getFamilyId(), context.familyId())
                || receipt.getTokenId() != null
                || !Objects.equals(receipt.getBindingId(), context.bindingId())
                || !Objects.equals(receipt.getTenantId(), context.tenantId())
                || !Objects.equals(receipt.getMemberId(), context.memberId())
                || !Objects.equals(receipt.getUserId(), context.userId())
                || !sameBytes(receipt.getPrincipalSubjectDigest(), context.principalSubjectDigest())
                || !Objects.equals(receipt.getActorType(), context.actorType())
                || !Objects.equals(receipt.getActorUserId(), context.actorUserId())
                || !sameBytes(receipt.getActorSubjectDigest(), context.actorSubjectDigest())
                || !Objects.equals(receipt.getCorrelationId(), context.correlationId())
                || !sameBytes(receipt.getPayloadDigest(), context.payloadDigest())
                || !Objects.equals(receipt.getEvidenceLevel(), context.evidenceLevel())
                || !sameDate(receipt.getCreatedAt(), context.createdAt())) {
            throw new ServiceException("BOARD_OAUTH_ACTIVATION_RECEIPT_PROOF_INVALID", 500);
        }
    }

    private BoardOAuthFamilyActivationProof requireActivationProof(
            BoardConnectorBindingActivationLease lease) {
        BoardOAuthFamilyActivationProof proof = lease == null
                ? null
                : lease.activationProof(oauthActivationOwnerToken);
        if (proof == null) {
            throw new ServiceException("BOARD_CONNECTOR_ACTIVATION_PROOF_REQUIRED", 500);
        }
        return proof;
    }

    private byte[] oauthActivationReceiptDigest(
            String receiptId,
            String action,
            String correlationId,
            BoardConnectorBinding binding,
            BoardOAuthTokenFamily pendingFamily,
            List<BoardOAuthToken> pendingTokens,
            BoardOAuthTokenFamily oldActiveFamily,
            List<BoardOAuthToken> oldActiveTokens,
            byte[] principalDigest,
            Long actorUserId,
            Date transitionAt,
            BoardOAuthClient client,
            BoardOAuthAuthorizationRequest authorizationRequest,
            BoardOAuthAuthorizationCode authorizationCode,
            BoardOAuthReceipt tokenFamilyCreatedReceipt,
            Long presentedTokenId,
            byte[] presentedTokenDigest,
            String verificationMethod,
            BoardOAuthConsentIntent consentIntent,
            BoardConnectorBindingActivationLease.Mode bindingTopology) {
        List<String> tokenImages = new ArrayList<>();
        for (BoardOAuthToken token : pendingTokens) {
            tokenImages.add(canonicalField("new") + tokenDigestCanonical(token));
        }
        for (BoardOAuthToken token : oldActiveTokens) {
            tokenImages.add(canonicalField("old") + tokenDigestCanonical(token));
        }
        Collections.sort(tokenImages);
        List<String> fields = new ArrayList<>();
        Collections.addAll(fields,
                "FBSIR:OAUTH:FAMILY_ACTIVATION_RECEIPT:v3",
                receiptId,
                action,
                correlationId,
                pendingFamily.getClientId(),
                pendingFamily.getFamilyId(),
                binding.getBindingId(),
                String.valueOf(binding.getVersion()),
                String.valueOf(binding.getTenantId()),
                String.valueOf(binding.getMemberId()),
                String.valueOf(binding.getUserId()),
                binding.getIssuerUri(),
                binding.getResourceUri(),
                binding.getClientId(),
                binding.getStatus(),
                binding.getVerificationMethod(),
                binding.getEvidenceDigest(),
                String.join(" ", sortedScopes(binding.getScopes())),
                HexFormat.of().formatHex(principalDigest),
                String.valueOf(actorUserId),
                String.valueOf(transitionAt.getTime()),
                String.valueOf(binding.getValidUntil().getTime()),
                oldActiveFamily == null ? null : oldActiveFamily.getFamilyId(),
                oldActiveFamily == null
                        ? null
                        : String.valueOf(oldActiveFamily.getVersion()),
                client == null ? null : client.getClientId(),
                client == null ? null : String.valueOf(client.getVersion()),
                client == null ? null : dateMillis(client.getExpiresAt()),
                client == null ? null : digestHex(client.getMetadataDigest()),
                client == null
                        ? null
                        : digestHex(client.getRegistrationSourceDigest()),
                authorizationRequest == null
                        ? null
                        : String.valueOf(authorizationRequest.getId()),
                authorizationRequest == null
                        ? null
                        : String.valueOf(authorizationRequest.getVersion()),
                authorizationRequest == null
                        ? null
                        : digestHex(authorizationRequest.getRequestHandleDigest()),
                authorizationRequest == null
                        ? null
                        : dateMillis(authorizationRequest.getConsumedAt()),
                authorizationCode == null
                        ? null
                        : String.valueOf(authorizationCode.getId()),
                authorizationCode == null
                        ? null
                        : String.valueOf(authorizationCode.getVersion()),
                authorizationCode == null
                        ? null
                        : digestHex(authorizationCode.getCodeDigest()),
                authorizationCode == null
                        ? null
                        : dateMillis(authorizationCode.getUsedAt()),
                tokenFamilyCreatedReceipt == null
                        ? null
                        : tokenFamilyCreatedReceipt.getReceiptId(),
                tokenFamilyCreatedReceipt == null
                        ? null
                        : digestHex(tokenFamilyCreatedReceipt.getPayloadDigest()),
                presentedTokenId == null ? null : String.valueOf(presentedTokenId),
                digestHex(presentedTokenDigest),
                consentIntent == null ? null : consentIntent.name(),
                bindingTopology == null ? null : bindingTopology.name(),
                verificationMethod);
        fields.addAll(tokenImages);
        String canonical = fields.stream()
                .map(this::canonicalField)
                .collect(java.util.stream.Collectors.joining("\n"));
        return BoardOAuthCrypto.sha256(canonical.getBytes(StandardCharsets.US_ASCII));
    }

    private String tokenDigestCanonical(BoardOAuthToken token) {
        return java.util.Arrays.asList(
                        String.valueOf(token.getId()),
                        digestHex(token.getTokenDigest()),
                        token.getFamilyId(),
                        token.getTokenType(),
                        String.valueOf(token.getGeneration()),
                        token.getStatus(),
                        String.valueOf(token.getActiveRefreshSlot()),
                        String.valueOf(token.getVersion()),
                        dateMillis(token.getIssuedAt()),
                        dateMillis(token.getUsedAt()),
                        dateMillis(token.getRevokedAt()),
                        dateMillis(token.getExpiresAt()),
                        dateMillis(token.getCreatedAt()),
                        dateMillis(token.getUpdatedAt()))
                .stream()
                .map(this::canonicalField)
                .collect(java.util.stream.Collectors.joining());
    }

    private String canonicalField(String value) {
        return value == null ? "-1:" : value.length() + ":" + value;
    }

    private String digestHex(byte[] value) {
        return value == null ? null : HexFormat.of().formatHex(value);
    }

    private String dateMillis(Date value) {
        return value == null ? null : String.valueOf(value.getTime());
    }

    private void requireFamilyId(String familyId) {
        if (familyId == null || !FAMILY_ID.matcher(familyId).matches()) {
            throw new ServiceException("BOARD_OAUTH_FAMILY_ID_INVALID", 400);
        }
    }

    private byte[] parseSha256(String value) {
        try {
            return HexFormat.of().parseHex(value);
        } catch (IllegalArgumentException invalid) {
            throw new ServiceException("CONNECTOR_SUBJECT_DIGEST_INVALID", 400);
        }
    }

    private boolean sameBytes(byte[] left, byte[] right) {
        return left != null
                && right != null
                && left.length == right.length
                && BoardOAuthCrypto.constantTimeEquals(left, right);
    }

    private boolean sameOptionalBytes(byte[] left, byte[] right) {
        return (left == null && right == null) || sameBytes(left, right);
    }

    private boolean sameDate(Date left, Date right) {
        return Objects.equals(left, right);
    }

    private Date earlierDate(Date left, Date right) {
        if (left == null) {
            return copy(right);
        }
        if (right == null) {
            return copy(left);
        }
        return copy(left.before(right) ? left : right);
    }

    private boolean hasTransitionUpdate(
            Date before,
            Date current,
            Date transitionAt) {
        if (before == null && current == null) {
            return true;
        }
        return before != null
                && current != null
                && transitionAt != null
                && !current.before(before)
                && Math.abs(current.getTime() - transitionAt.getTime()) <= 5_000L;
    }

    private void requireFreshOAuthActivationTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new ServiceException("BOARD_CONNECTOR_ACTIVATION_TRANSACTION_REQUIRED", 500);
        }
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new ServiceException("BOARD_CONNECTOR_ACTIVATION_READ_ONLY_TRANSACTION", 500);
        }
        try {
            if (TransactionAspectSupport.currentTransactionStatus().hasSavepoint()) {
                throw new ServiceException("BOARD_CONNECTOR_ACTIVATION_NESTED_TRANSACTION", 500);
            }
        } catch (NoTransactionException noProxyTransactionStatus) {
            // The explicit synchronization checks above remain authoritative for
            // programmatic transaction owners and the unit transaction harness.
        }
        if (TransactionSynchronizationManager.hasResource(
                OAuthActivationTransactionResource.KEY)) {
            throw new ServiceException("BOARD_CONNECTOR_ACTIVATION_LEASE_ALREADY_BOUND", 500);
        }
    }

    private BoardTransactionBoundary captureBoardTransactionBoundary() {
        Object resource = boardDataSource == null
                ? null
                : TransactionSynchronizationManager.getResource(boardDataSource);
        if (!(resource instanceof ConnectionHolder holder)
                || !holder.isSynchronizedWithTransaction()) {
            throw new ServiceException(
                    "BOARD_CONNECTOR_ACTIVATION_BOARD_TRANSACTION_REQUIRED", 500);
        }
        Connection connection;
        try {
            connection = holder.getConnection();
            if (connection == null
                    || connection.isClosed()
                    || connection.getAutoCommit()
                    || connection.getTransactionIsolation()
                            != Connection.TRANSACTION_REPEATABLE_READ) {
                throw new ServiceException(
                        "BOARD_CONNECTOR_ACTIVATION_BOARD_TRANSACTION_REQUIRED", 500);
            }
        } catch (SQLException | IllegalStateException invalidConnection) {
            throw new ServiceException(
                    "BOARD_CONNECTOR_ACTIVATION_BOARD_TRANSACTION_REQUIRED", 500);
        }
        return new BoardTransactionBoundary(resource, connection);
    }

    private BoardTransactionBoundary captureRefreshBoardTransactionBoundary() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new ServiceException("BOARD_OAUTH_REFRESH_TRANSACTION_REQUIRED", 500);
        }
        try {
            if (TransactionAspectSupport.currentTransactionStatus().hasSavepoint()) {
                throw new ServiceException("BOARD_OAUTH_REFRESH_ROOT_TRANSACTION_REQUIRED", 500);
            }
        } catch (NoTransactionException noProxyTransactionStatus) {
            // Programmatic transaction owners are covered by synchronization and
            // the physical connection checks below.
        }
        Object resource = boardDataSource == null
                ? null
                : TransactionSynchronizationManager.getResource(boardDataSource);
        if (!(resource instanceof ConnectionHolder holder)
                || !holder.isSynchronizedWithTransaction()) {
            throw new ServiceException("BOARD_OAUTH_REFRESH_TRANSACTION_REQUIRED", 500);
        }
        try {
            Connection connection = holder.getConnection();
            if (connection == null
                    || connection.isClosed()
                    || connection.getAutoCommit()
                    || connection.getTransactionIsolation()
                            != Connection.TRANSACTION_REPEATABLE_READ) {
                throw new ServiceException("BOARD_OAUTH_REFRESH_TRANSACTION_REQUIRED", 500);
            }
            return new BoardTransactionBoundary(resource, connection);
        } catch (SQLException | IllegalStateException invalidConnection) {
            throw new ServiceException("BOARD_OAUTH_REFRESH_TRANSACTION_REQUIRED", 500);
        }
    }

    private void requireRefreshLease(BoardOAuthRefreshAuthorityLease lease) {
        if (lease == null
                || !lease.isOwnedBy(oauthRefreshOwnerToken)
                || !lease.isOwnedByCurrentThread()
                || lease.replayRevoked(oauthRefreshOwnerToken)
                || !TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || boardDataSource == null
                || !TransactionSynchronizationManager.hasResource(boardDataSource)) {
            throw new ServiceException("BOARD_OAUTH_REFRESH_AUTHORITY_LEASE_INVALID", 500);
        }
        Object resource = TransactionSynchronizationManager.getResource(boardDataSource);
        if (resource != lease.boardTransactionResource(oauthRefreshOwnerToken)
                || !(resource instanceof ConnectionHolder holder)
                || !holder.isSynchronizedWithTransaction()) {
            throw new ServiceException("BOARD_OAUTH_REFRESH_AUTHORITY_LEASE_INVALID", 500);
        }
        try {
            Connection connection = holder.getConnection();
            if (connection != lease.boardPhysicalConnection(oauthRefreshOwnerToken)
                    || connection.isClosed()
                    || connection.getAutoCommit()
                    || connection.getTransactionIsolation()
                            != Connection.TRANSACTION_REPEATABLE_READ) {
                throw new ServiceException("BOARD_OAUTH_REFRESH_AUTHORITY_LEASE_INVALID", 500);
            }
        } catch (SQLException | IllegalStateException invalidConnection) {
            throw new ServiceException("BOARD_OAUTH_REFRESH_AUTHORITY_LEASE_INVALID", 500);
        }
    }

    private boolean hasSameBoardTransactionBoundary(
            BoardConnectorBindingActivationLease lease) {
        if (boardDataSource == null
                || !TransactionSynchronizationManager.hasResource(boardDataSource)) {
            return false;
        }
        Object currentResource = TransactionSynchronizationManager.getResource(boardDataSource);
        if (currentResource != lease.boardTransactionResource(oauthActivationOwnerToken)
                || !(currentResource instanceof ConnectionHolder holder)
                || !holder.isSynchronizedWithTransaction()) {
            return false;
        }
        try {
            Connection connection = holder.getConnection();
            return connection == lease.boardPhysicalConnection(oauthActivationOwnerToken)
                    && !connection.isClosed()
                    && !connection.getAutoCommit()
                    && connection.getTransactionIsolation()
                            == Connection.TRANSACTION_REPEATABLE_READ;
        } catch (SQLException | IllegalStateException invalidConnection) {
            return false;
        }
    }

    private void bindActivationLease(BoardConnectorBindingActivationLease lease) {
        Object resourceKey = OAuthActivationTransactionResource.KEY;
        List<TransactionSynchronization> trustedBaseline = List.copyOf(
                TransactionSynchronizationManager.getSynchronizations());
        if (trustedBaseline.stream().anyMatch(synchronization ->
                !TRUSTED_TRANSACTION_SYNCHRONIZATIONS.contains(
                        synchronization.getClass().getName()))) {
            lease.invalidate(oauthActivationOwnerToken);
            throw new ServiceException(
                    "BOARD_CONNECTOR_ACTIVATION_UNTRUSTED_SYNCHRONIZATION", 500);
        }
        TransactionSynchronization synchronization =
                new OAuthActivationIncompleteGuard(lease);
        try {
            lease.attachTrustedBaselineSynchronizations(
                    oauthActivationOwnerToken, trustedBaseline);
            lease.attachTransactionSynchronization(
                    oauthActivationOwnerToken, synchronization);
            TransactionSynchronizationManager.bindResource(
                    resourceKey, lease.transactionMarker(oauthActivationOwnerToken));
            TransactionSynchronizationManager.registerSynchronization(synchronization);
        } catch (RuntimeException bindingFailure) {
            lease.invalidate(oauthActivationOwnerToken);
            if (TransactionSynchronizationManager.hasResource(resourceKey)
                    && TransactionSynchronizationManager.getResource(resourceKey)
                    == lease.transactionMarker(oauthActivationOwnerToken)) {
                TransactionSynchronizationManager.unbindResource(resourceKey);
            }
            if (bindingFailure instanceof ServiceException known) {
                throw known;
            }
            throw new ServiceException(
                    "BOARD_CONNECTOR_ACTIVATION_TRANSACTION_BIND_FAILED", 500);
        }
    }

    private void bindActivationFinalizer(BoardConnectorBindingActivationLease lease) {
        TransactionSynchronization finalizer = new OAuthActivationFinalizer(lease);
        try {
            lease.attachFinalizerSynchronization(oauthActivationOwnerToken, finalizer);
            TransactionSynchronizationManager.registerSynchronization(finalizer);
        } catch (RuntimeException registrationFailure) {
            lease.invalidate(oauthActivationOwnerToken);
            if (registrationFailure instanceof ServiceException known) {
                throw known;
            }
            throw new ServiceException(
                    "BOARD_CONNECTOR_ACTIVATION_FINALIZER_BIND_FAILED", 500);
        }
    }

    private void verifyActivationSynchronizationClosure(
            BoardConnectorBindingActivationLease lease,
            TransactionSynchronization finalizer) {
        List<TransactionSynchronization> current =
                TransactionSynchronizationManager.getSynchronizations();
        List<Object> expected = new ArrayList<>(
                lease.trustedBaselineSynchronizations(oauthActivationOwnerToken));
        expected.add(lease.transactionSynchronization(oauthActivationOwnerToken));
        expected.add(finalizer);
        boolean exactIdentitySet = current.size() == expected.size();
        if (exactIdentitySet) {
            for (Object candidate : expected) {
                long identityMatches = current.stream()
                        .filter(value -> value == candidate)
                        .count();
                if (identityMatches != 1L) {
                    exactIdentitySet = false;
                    break;
                }
            }
        }
        if (!exactIdentitySet
                || current.isEmpty()
                || current.get(current.size() - 1) != finalizer
                || lease.finalizerSynchronization(oauthActivationOwnerToken) != finalizer) {
            throw new ServiceException(
                    "BOARD_CONNECTOR_ACTIVATION_SYNCHRONIZATION_DRIFT", 500);
        }
    }

    private void requireBoundLease(
            BoardConnectorBindingActivationLease lease,
            BoardConnectorBindingActivationLease.Phase expectedPhase) {
        boolean synchronizationPresent = false;
        if (lease != null
                && TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            synchronizationPresent = TransactionSynchronizationManager.getSynchronizations()
                    .stream()
                    .anyMatch(candidate -> candidate
                            == lease.transactionSynchronization(oauthActivationOwnerToken));
        }
        if (lease == null
                || !lease.isOwnedBy(oauthActivationOwnerToken)
                || !lease.isOwnedByCurrentThread()
                || !TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()
                || !hasSameBoardTransactionBoundary(lease)
                || !TransactionSynchronizationManager.hasResource(
                        OAuthActivationTransactionResource.KEY)
                || TransactionSynchronizationManager.getResource(
                        OAuthActivationTransactionResource.KEY)
                        != lease.transactionMarker(oauthActivationOwnerToken)
                || !synchronizationPresent
                || lease.phase() != expectedPhase) {
            throw new ServiceException("BOARD_CONNECTOR_ACTIVATION_LEASE_INVALID", 500);
        }
    }

    private void advanceLease(
            BoardConnectorBindingActivationLease lease,
            BoardConnectorBindingActivationLease.Phase expected,
            BoardConnectorBindingActivationLease.Phase next) {
        try {
            lease.advance(oauthActivationOwnerToken, expected, next);
        } catch (IllegalStateException invalidPhase) {
            throw new ServiceException("BOARD_CONNECTOR_ACTIVATION_LEASE_INVALID", 500);
        }
    }

    private BoardConnectorBinding newBinding(
            BoardConnectorProtectedRequestAttestation attestation,
            Date verifiedAt) {
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
        binding.setVerifiedAt(copy(verifiedAt));
        binding.setLastSeenAt(copy(verifiedAt));
        binding.setValidUntil(copy(attestation.validUntil()));
        binding.setVersion(1L);
        binding.setScopes(sortedScopes(attestation.scopes()));
        return binding;
    }

    private void insertBindingAndScopes(BoardConnectorBinding binding, Date createdAt) {
        try {
            if (mapper.insertConnectorBinding(binding) != 1) {
                throw new ServiceException("BOARD_CONNECTOR_BINDING_WRITE_FAILED", 500);
            }
            for (String scope : binding.getScopes()) {
                if (mapper.insertConnectorBindingScope(
                        binding.getBindingId(), scope, createdAt) != 1) {
                    throw new ServiceException("BOARD_CONNECTOR_BINDING_SCOPE_WRITE_FAILED", 500);
                }
            }
        } catch (DuplicateKeyException | PessimisticLockingFailureException conflict) {
            throw new ServiceException("BOARD_CONNECTOR_BINDING_CONFLICT", 409);
        }
    }

    private void validateActivationKey(BoardConnectorBindingKey key, Long actorUserId) {
        requirePositive(actorUserId, "AUTHENTICATED_PRINCIPAL_REQUIRED");
        if (key == null) {
            throw new ServiceException("BOARD_CONNECTOR_ACTIVATION_KEY_REQUIRED", 400);
        }
        requirePositive(key.tenantId(), "TENANT_REQUIRED");
        requirePositive(key.memberId(), "MEMBER_REQUIRED");
        requirePositive(key.userId(), "USER_REQUIRED");
        requireProduct(key.productCode());
        if (!Objects.equals(key.userId(), actorUserId)) {
            throw new ServiceException("CONNECTOR_ATTESTATION_ACTOR_MISMATCH", 403);
        }
    }

    private void requireAttestationMatchesKey(
            BoardConnectorProtectedRequestAttestation attestation,
            BoardConnectorBindingKey key) {
        if (!Objects.equals(attestation.tenantId(), key.tenantId())
                || !Objects.equals(attestation.memberId(), key.memberId())
                || !Objects.equals(attestation.userId(), key.userId())) {
            throw new ServiceException("BOARD_CONNECTOR_ACTIVATION_SCOPE_MISMATCH", 403);
        }
    }

    private void requireExactRequiredScopes(List<String> scopes) {
        if (scopes == null
                || scopes.size() != REQUIRED_SCOPES.size()
                || new HashSet<>(scopes).size() != scopes.size()
                || !new HashSet<>(scopes).equals(REQUIRED_SCOPES)) {
            throw new ServiceException("BOARD_CONNECTOR_BINDING_SCOPE_INVALID", 500);
        }
    }

    private Date activationTransitionAt(BoardConnectorBinding binding, Date wallClockNow) {
        long transitionMillis = wallClockNow.getTime();
        transitionMillis = Math.max(transitionMillis, binding.getVerifiedAt().getTime());
        transitionMillis = Math.max(transitionMillis, binding.getLastSeenAt().getTime());
        if (binding.getRevokedAt() != null) {
            transitionMillis = Math.max(transitionMillis, binding.getRevokedAt().getTime());
        }
        return new Date(transitionMillis);
    }

    private void finalizeOAuthActivationTransitionAt(
            BoardConnectorBindingActivationLease lease) {
        Date transitionAt;
        try {
            transitionAt = lease.finalizeTransitionAt(
                    oauthActivationOwnerToken,
                    Date.from(clock.instant()));
        } catch (IllegalStateException invalidLease) {
            throw new ServiceException("BOARD_CONNECTOR_ACTIVATION_LEASE_INVALID", 500);
        }
        BoardConnectorBindingActivationLease.EntitlementImage entitlement =
                lease.entitlementImage(oauthActivationOwnerToken);
        if (entitlement.validFrom == null
                || entitlement.validFrom.after(transitionAt)
                || (entitlement.validUntil != null
                        && !entitlement.validUntil.after(transitionAt))) {
            throw new ServiceException("CONNECTOR_VIP_ENTITLEMENT_NOT_CURRENT", 403);
        }
        BoardConnectorBinding existing = lease.existingBinding(oauthActivationOwnerToken);
        if (existing != null) {
            requireBindingTransitionTimes(existing, transitionAt);
        }
    }

    private void requireBindingTransitionTimes(
            BoardConnectorBinding binding,
            Date wallClockNow) {
        if (binding.getVerifiedAt() == null
                || binding.getVerifiedAt().after(wallClockNow)
                || binding.getLastSeenAt() == null
                || binding.getLastSeenAt().after(wallClockNow)
                || (binding.getRevokedAt() != null
                        && binding.getRevokedAt().after(wallClockNow))) {
            throw new ServiceException(
                    "BOARD_CONNECTOR_BINDING_TIME_DRIFT", 409);
        }
    }

    private final class OAuthActivationIncompleteGuard
            implements TransactionSynchronization, Ordered {
        private final BoardConnectorBindingActivationLease lease;

        private OAuthActivationIncompleteGuard(BoardConnectorBindingActivationLease lease) {
            this.lease = lease;
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }

        @Override
        public void suspend() {
            lease.invalidate(oauthActivationOwnerToken);
            throw new ServiceException(
                    "BOARD_CONNECTOR_ACTIVATION_TRANSACTION_SUSPENDED", 500);
        }

        @Override
        public void resume() {
            lease.invalidate(oauthActivationOwnerToken);
            throw new ServiceException(
                    "BOARD_CONNECTOR_ACTIVATION_TRANSACTION_RESUME_INVALID", 500);
        }

        @Override
        public void savepoint(Object savepoint) {
            lease.invalidate(oauthActivationOwnerToken);
        }

        @Override
        public void savepointRollback(Object savepoint) {
            lease.invalidate(oauthActivationOwnerToken);
        }

        @Override
        public void beforeCommit(boolean readOnly) {
            try {
                requireBoundLease(
                        lease, BoardConnectorBindingActivationLease.Phase.COMPLETE);
                Object finalizer = lease.finalizerSynchronization(oauthActivationOwnerToken);
                if (finalizer == null
                        || TransactionSynchronizationManager.getSynchronizations().stream()
                                .noneMatch(candidate -> candidate == finalizer)) {
                    throw new ServiceException(
                            "BOARD_CONNECTOR_ACTIVATION_FINALIZER_REQUIRED", 500);
                }
            } catch (RuntimeException incomplete) {
                lease.invalidate(oauthActivationOwnerToken);
                throw incomplete;
            }
        }

        @Override
        public void afterCompletion(int status) {
            lease.invalidate(oauthActivationOwnerToken);
            Object resourceKey = OAuthActivationTransactionResource.KEY;
            if (TransactionSynchronizationManager.hasResource(resourceKey)) {
                Object value = TransactionSynchronizationManager.getResource(resourceKey);
                if (value == lease.transactionMarker(oauthActivationOwnerToken)) {
                    TransactionSynchronizationManager.unbindResource(resourceKey);
                }
            }
        }
    }

    private final class OAuthActivationFinalizer
            implements TransactionSynchronization, Ordered {
        private final BoardConnectorBindingActivationLease lease;

        private OAuthActivationFinalizer(BoardConnectorBindingActivationLease lease) {
            this.lease = lease;
        }

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;
        }

        @Override
        public void beforeCommit(boolean readOnly) {
            try {
                requireBoundLease(
                        lease, BoardConnectorBindingActivationLease.Phase.COMPLETE);
                verifyActivationSynchronizationClosure(lease, this);
                verifyOAuthFamilyActivationFinalState(lease);
                verifyW4aReceipt(lease);
                verifyW4bActivationReceipt(lease);
            } catch (RuntimeException incomplete) {
                lease.invalidate(oauthActivationOwnerToken);
                throw incomplete;
            }
            lease.invalidate(oauthActivationOwnerToken);
        }
    }

    private record BoardTransactionBoundary(Object resource, Connection connection) {
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
        requireCurrentVipEntitlementState(entitlement, attestation, now);
        BoardProductPlan plan = mapper.selectActivePlanForUpdate(
                IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.VIP_PLAN);
        if (plan == null) {
            throw new ServiceException("BOARD_PLAN_CONTRACT_DRIFT", 500);
        }
        requireCurrentVipPlan(plan);
    }

    private void requireCurrentVipEntitlementState(
            BoardProductEntitlement entitlement,
            BoardConnectorProtectedRequestAttestation attestation,
            Date now) {
        requireCurrentVipEntitlementState(
                entitlement,
                attestation.tenantId(),
                attestation.memberId(),
                attestation.userId(),
                IndependentBoardEntitlementService.PRODUCT_CODE,
                now);
    }

    private void requireCurrentVipEntitlementState(
            BoardProductEntitlement entitlement,
            Long tenantId,
            Long memberId,
            Long userId,
            String productCode,
            Date now) {
        if (entitlement == null
                || !Objects.equals(entitlement.getTenantId(), tenantId)
                || !Objects.equals(entitlement.getMemberId(), memberId)
                || !Objects.equals(entitlement.getUserId(), userId)
                || !Objects.equals(entitlement.getProductCode(), productCode)
                || !Objects.equals(entitlement.getPlanCode(), IndependentBoardEntitlementService.VIP_PLAN)
                || !Objects.equals(entitlement.getStatus(), STATUS_ACTIVE)
                || entitlement.getValidFrom() == null
                || entitlement.getValidFrom().after(now)
                || (entitlement.getValidUntil() != null && !entitlement.getValidUntil().after(now))) {
            throw new ServiceException("CONNECTOR_VIP_ENTITLEMENT_NOT_CURRENT", 403);
        }
    }

    private void requireCurrentVipPlan(BoardProductPlan plan) {
        if (!Objects.equals(plan.getProductCode(), IndependentBoardEntitlementService.PRODUCT_CODE)
                || !Objects.equals(plan.getPlanCode(), IndependentBoardEntitlementService.VIP_PLAN)
                || !Boolean.TRUE.equals(plan.getVip())
                || !Boolean.TRUE.equals(plan.getConnectorRequired())
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

    private void requireCurrentEnterprise(
            BoardEnterpriseAuthority enterprise,
            Long tenantId) {
        if (!isTokenExchangeEnterpriseCurrent(enterprise, tenantId)) {
            throw new ServiceException("TENANT_ENTERPRISE_SCOPE_INVALID", 403);
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

    private BoardConnectorBindingReceipt insertReceipt(
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
        return receipt;
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

    private boolean isTokenExchangeEnterpriseCurrent(
            BoardEnterpriseAuthority enterprise,
            Long tenantId) {
        return enterprise != null
                && Objects.equals(enterprise.getTenantId(), tenantId)
                && Objects.equals(enterprise.getStatus(), 1)
                && Objects.equals(enterprise.getDelFlag(), "0");
    }

    private boolean isTokenExchangeMemberCurrent(
            BoardEnterpriseMemberScope member,
            Long tenantId,
            Long memberId,
            Long userId) {
        return member != null
                && Objects.equals(member.getTenantId(), tenantId)
                && Objects.equals(member.getMemberId(), memberId)
                && Objects.equals(member.getUserId(), userId)
                && Objects.equals(member.getStatus(), 1)
                && Objects.equals(member.getDelFlag(), "0");
    }

    private boolean isTokenExchangeEntitlementCurrent(
            BoardProductEntitlement entitlement,
            Long tenantId,
            Long memberId,
            Long userId,
            String productCode,
            Date now) {
        return entitlement != null
                && entitlement.getId() != null
                && entitlement.getId() > 0L
                && Objects.equals(entitlement.getTenantId(), tenantId)
                && Objects.equals(entitlement.getMemberId(), memberId)
                && Objects.equals(entitlement.getUserId(), userId)
                && Objects.equals(entitlement.getProductCode(), productCode)
                && Objects.equals(
                        entitlement.getPlanCode(),
                        IndependentBoardEntitlementService.VIP_PLAN)
                && Objects.equals(entitlement.getStatus(), STATUS_ACTIVE)
                && entitlement.getValidFrom() != null
                && !entitlement.getValidFrom().after(now)
                && (entitlement.getValidUntil() == null
                        || entitlement.getValidUntil().after(now))
                && entitlement.getVersion() != null
                && entitlement.getVersion() >= 0L;
    }

    private boolean isTokenExchangePlanCurrent(
            BoardProductPlan plan,
            String productCode) {
        return plan != null
                && Objects.equals(plan.getProductCode(), productCode)
                && Objects.equals(
                        plan.getPlanCode(),
                        IndependentBoardEntitlementService.VIP_PLAN)
                && Boolean.TRUE.equals(plan.getVip())
                && Boolean.TRUE.equals(plan.getConnectorRequired())
                && Objects.equals(plan.getStatus(), STATUS_ACTIVE);
    }

    private boolean isTokenExchangeBindingShapeCurrent(
            BoardConnectorBinding binding,
            List<String> scopes,
            Long tenantId,
            Long memberId,
            Long userId,
            String productCode,
            String sourceCode,
            String connectorCode,
            Date now) {
        return binding.getId() != null
                && binding.getId() > 0L
                && isUuid(binding.getBindingId())
                && Objects.equals(binding.getTenantId(), tenantId)
                && Objects.equals(binding.getMemberId(), memberId)
                && Objects.equals(binding.getUserId(), userId)
                && Objects.equals(binding.getProductCode(), productCode)
                && Objects.equals(binding.getSourceCode(), sourceCode)
                && Objects.equals(binding.getConnectorCode(), connectorCode)
                && Objects.equals(binding.getIssuerUri(), BoardOAuthProfile.ISSUER)
                && Objects.equals(binding.getResourceUri(), BoardOAuthProfile.RESOURCE)
                && binding.getClientId() != null
                && OAUTH_CLIENT_ID.matcher(binding.getClientId()).matches()
                && isSha256(binding.getPrincipalSubjectDigest())
                && isSha256(binding.getEvidenceDigest())
                && KNOWN_STATUSES.contains(binding.getStatus())
                && KNOWN_VERIFICATION_METHODS.contains(binding.getVerificationMethod())
                && binding.getVerifiedAt() != null
                && !binding.getVerifiedAt().after(now)
                && binding.getLastSeenAt() != null
                && !binding.getLastSeenAt().before(binding.getVerifiedAt())
                && !binding.getLastSeenAt().after(now)
                && binding.getValidUntil() != null
                && binding.getValidUntil().after(binding.getLastSeenAt())
                && binding.getVersion() != null
                && binding.getVersion() > 0L
                && ((STATUS_ACTIVE.equals(binding.getStatus())
                                && binding.getRevokedAt() == null)
                        || (!STATUS_ACTIVE.equals(binding.getStatus())
                                && binding.getRevokedAt() != null
                                && !binding.getRevokedAt().before(
                                        binding.getLastSeenAt())
                                && !binding.getRevokedAt().after(now)))
                && scopes != null
                && scopes.size() == REQUIRED_SCOPES.size()
                && new HashSet<>(scopes).size() == scopes.size()
                && new HashSet<>(scopes).equals(REQUIRED_SCOPES);
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
