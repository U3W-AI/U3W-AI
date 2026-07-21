package com.wx.fbsir.business.board.oauth.service;

import com.wx.fbsir.business.board.domain.BoardConnectorBinding;
import com.wx.fbsir.business.board.domain.BoardEnterpriseAuthority;
import com.wx.fbsir.business.board.domain.BoardEnterpriseMemberScope;
import com.wx.fbsir.business.board.domain.BoardProductEntitlement;
import com.wx.fbsir.business.board.domain.BoardProductPlan;
import com.wx.fbsir.business.board.mapper.IndependentBoardMapper;
import com.wx.fbsir.business.board.oauth.AesGcmBoardOAuthStateCipher;
import com.wx.fbsir.business.board.oauth.BoardOAuthAuthorizationCodeGenerator;
import com.wx.fbsir.business.board.oauth.BoardOAuthAuthorizationStateAad;
import com.wx.fbsir.business.board.oauth.BoardOAuthConsentIntent;
import com.wx.fbsir.business.board.oauth.BoardOAuthCrypto;
import com.wx.fbsir.business.board.oauth.BoardOAuthPrincipalSubject;
import com.wx.fbsir.business.board.oauth.BoardOAuthProfile;
import com.wx.fbsir.business.board.oauth.BoardOAuthProtocolException;
import com.wx.fbsir.business.board.oauth.BoardOAuthReceiptFactory;
import com.wx.fbsir.business.board.oauth.BoardOAuthRequestHandleGenerator;
import com.wx.fbsir.business.board.oauth.BoardOAuthStateCipher;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthAuthorizationCode;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthAuthorizationRequest;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthClient;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthReceipt;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthAuthorizationApprovalCommand;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthAuthorizationApprovedResult;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthAuthorizationDenialCommand;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthAuthorizationDeniedResult;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthAuthorizationStartCommand;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthAuthorizationStartResult;
import com.wx.fbsir.business.board.oauth.mapper.IndependentBoardOAuthMapper;
import com.wx.fbsir.common.exception.ServiceException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Internal authorization request and consent service for the locked WorkBuddy profile.
 *
 * <p>This class deliberately publishes no HTTP route. Browser input cannot
 * supply authoritative identity, scope, resource, client or redirect values.</p>
 */
@Service
class IndependentBoardOAuthAuthorizationService {
    public static final String REQUEST_INVALID = "OAUTH_AUTHORIZATION_REQUEST_INVALID";
    public static final String CLIENT_INVALID = "OAUTH_AUTHORIZATION_CLIENT_INVALID";
    public static final String CLIENT_PROFILE_DRIFT =
            "OAUTH_AUTHORIZATION_CLIENT_PROFILE_DRIFT";
    public static final String STATE_PROTECTION_UNAVAILABLE =
            "OAUTH_AUTHORIZATION_STATE_PROTECTION_UNAVAILABLE";
    public static final String STATE_PROTECTION_FAILED =
            "OAUTH_AUTHORIZATION_STATE_PROTECTION_FAILED";
    public static final String REQUEST_CONFLICT = "OAUTH_AUTHORIZATION_REQUEST_CONFLICT";
    public static final String REQUEST_WRITE_FAILED =
            "OAUTH_AUTHORIZATION_REQUEST_WRITE_FAILED";
    public static final String CONSENT_INVALID = "OAUTH_AUTHORIZATION_CONSENT_INVALID";
    public static final String CONSENT_REQUEST_NOT_FOUND =
            "OAUTH_AUTHORIZATION_CONSENT_REQUEST_NOT_FOUND";
    public static final String CONSENT_MEMBER_NOT_ACTIVE =
            "OAUTH_AUTHORIZATION_CONSENT_MEMBER_NOT_ACTIVE";
    public static final String CONSENT_ENTERPRISE_NOT_ACTIVE =
            "OAUTH_AUTHORIZATION_CONSENT_ENTERPRISE_NOT_ACTIVE";
    public static final String CONSENT_VIP_NOT_CURRENT =
            "OAUTH_AUTHORIZATION_CONSENT_VIP_NOT_CURRENT";
    public static final String CONSENT_PLAN_DRIFT =
            "OAUTH_AUTHORIZATION_CONSENT_PLAN_DRIFT";
    public static final String CONSENT_INTENT_CONFLICT =
            "OAUTH_AUTHORIZATION_CONSENT_INTENT_CONFLICT";
    public static final String CONSENT_BINDING_DRIFT =
            "OAUTH_AUTHORIZATION_CONSENT_BINDING_DRIFT";
    public static final String CONSENT_REQUEST_EXPIRED =
            "OAUTH_AUTHORIZATION_CONSENT_REQUEST_EXPIRED";
    public static final String CONSENT_REQUEST_DRIFT =
            "OAUTH_AUTHORIZATION_CONSENT_REQUEST_DRIFT";
    public static final String CONSENT_STATE_TAMPERED =
            "OAUTH_AUTHORIZATION_CONSENT_STATE_TAMPERED";
    public static final String CONSENT_PERSISTENCE_UNAVAILABLE =
            "OAUTH_AUTHORIZATION_CONSENT_PERSISTENCE_UNAVAILABLE";
    public static final String AUTHORIZATION_CODE_WRITE_FAILED =
            "OAUTH_AUTHORIZATION_CODE_WRITE_FAILED";
    public static final String AUTHORIZATION_RECEIPT_WRITE_FAILED =
            "OAUTH_AUTHORIZATION_RECEIPT_WRITE_FAILED";

    static final String PRODUCT_CODE = "FBSIR_INDEPENDENT_BOARD";
    static final String SOURCE_CODE = "WORKBUDDY";
    static final String CONNECTOR_CODE = "fbs-connector";
    static final String STATUS_PENDING = "PENDING";

    private static final String RESPONSE_TYPE_CODE = "code";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_DENIED = "DENIED";
    private static final String VIP_PLAN = "BOARD_VIP";
    private static final String TOKEN_ENDPOINT_AUTH_METHOD = "none";
    private static final String GRANT_TYPES_CANONICAL = "authorization_code refresh_token";
    private static final String RESPONSE_TYPES_CANONICAL = "code";
    private static final String NEUTRAL_CLIENT_NAME = "未验证的本地公共客户端";
    private static final String ACCESS_DENIED = "access_denied";
    private static final Duration CLIENT_LIFETIME = Duration.ofDays(31);
    private static final Duration REQUEST_LIFETIME = Duration.ofMinutes(5);
    private static final Duration CODE_LIFETIME = Duration.ofSeconds(60);
    private static final Pattern CLIENT_ID = Pattern.compile("[A-Za-z0-9_-]{43,191}");
    private static final Pattern REQUEST_HANDLE = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> BINDING_STATUSES =
            Set.of("ACTIVE", "REVOKED", "COMPROMISED");
    private static final Set<String> BINDING_VERIFICATION_METHODS =
            Set.of("MCP_INITIALIZE", "MCP_TOOLS_LIST");

    private final IndependentBoardMapper boardMapper;
    private final IndependentBoardOAuthMapper mapper;
    private final BoardOAuthStateCipher stateCipher;
    private final BoardOAuthRequestHandleGenerator handleGenerator;
    private final BoardOAuthAuthorizationCodeGenerator codeGenerator;
    private final BoardOAuthReceiptFactory receiptFactory;
    private final Clock clock;

    @Autowired
    public IndependentBoardOAuthAuthorizationService(
            IndependentBoardMapper boardMapper,
            IndependentBoardOAuthMapper mapper,
            BoardOAuthStateCipher stateCipher,
            BoardOAuthRequestHandleGenerator handleGenerator,
            BoardOAuthAuthorizationCodeGenerator codeGenerator,
            BoardOAuthReceiptFactory receiptFactory) {
        this(
                boardMapper,
                mapper,
                stateCipher,
                handleGenerator,
                codeGenerator,
                receiptFactory,
                Clock.systemUTC());
    }

    IndependentBoardOAuthAuthorizationService(
            IndependentBoardMapper boardMapper,
            IndependentBoardOAuthMapper mapper,
            BoardOAuthStateCipher stateCipher,
            BoardOAuthRequestHandleGenerator handleGenerator,
            BoardOAuthAuthorizationCodeGenerator codeGenerator,
            BoardOAuthReceiptFactory receiptFactory,
            Clock clock) {
        this.boardMapper = Objects.requireNonNull(boardMapper, "boardMapper");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.stateCipher = Objects.requireNonNull(stateCipher, "stateCipher");
        this.handleGenerator = Objects.requireNonNull(handleGenerator, "handleGenerator");
        this.codeGenerator = Objects.requireNonNull(codeGenerator, "codeGenerator");
        this.receiptFactory = Objects.requireNonNull(receiptFactory, "receiptFactory");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Creates the request inside the hidden runner's root transaction. */
    public BoardOAuthAuthorizationStartResult start(
            BoardOAuthAuthorizationStartCommand command) {
        validateRequestShape(command);

        BoardOAuthClient client;
        try {
            client = mapper.selectClientForUpdate(command.clientId());
        } catch (DataAccessException failure) {
            throw BoardOAuthProtocolException.temporarilyUnavailable(REQUEST_WRITE_FAILED);
        } catch (RuntimeException failure) {
            throw BoardOAuthProtocolException.serverError(REQUEST_WRITE_FAILED);
        }

        Instant requestedAt = millisecondNow();
        validateLockedClient(
                client,
                command.clientId(),
                command.redirectUri(),
                requestedAt,
                REQUEST_LIFETIME,
                false);

        String requestHandle = handleGenerator.generate();
        if (requestHandle == null || !REQUEST_HANDLE.matcher(requestHandle).matches()) {
            throw BoardOAuthProtocolException.serverError(
                    BoardOAuthRequestHandleGenerator.HANDLE_GENERATION_FAILED);
        }
        BoardOAuthAuthorizationRequest request = new BoardOAuthAuthorizationRequest();
        request.setRequestHandleDigest(BoardOAuthCrypto.sha256Ascii(requestHandle));
        request.setClientId(client.getClientId());
        request.setRedirectUri(client.getRedirectUri());
        request.setCodeChallenge(command.codeChallenge());
        request.setCodeChallengeMethod(BoardOAuthProfile.PKCE_METHOD);
        request.setStateDigest(BoardOAuthCrypto.sha256Ascii(command.state()));
        request.setIssuerUri(BoardOAuthProfile.ISSUER);
        request.setResourceUri(BoardOAuthProfile.RESOURCE);
        request.setProductCode(PRODUCT_CODE);
        request.setSourceCode(SOURCE_CODE);
        request.setConnectorCode(CONNECTOR_CODE);
        request.setScopeCanonical(BoardOAuthProfile.CANONICAL_SCOPE);
        request.setScopeDigest(BoardOAuthCrypto.sha256Ascii(BoardOAuthProfile.CANONICAL_SCOPE));
        request.setTenantId(null);
        request.setMemberId(null);
        request.setUserId(null);
        request.setPrincipalSubjectDigest(null);
        request.setConsentIntent(null);
        request.setStatus(STATUS_PENDING);
        request.setRequestedAt(Date.from(requestedAt));
        Instant expiresAt = requestedAt.plus(REQUEST_LIFETIME);
        request.setExpiresAt(Date.from(expiresAt));
        request.setApprovedAt(null);
        request.setDeniedAt(null);
        request.setConsumedAt(null);
        request.setVersion(0L);

        byte[] aadDigest = BoardOAuthAuthorizationStateAad.digest(request);
        BoardOAuthStateCipher.EncryptedState encryptedState = encryptState(
                command.state(), aadDigest);
        validateEncryptedState(encryptedState);
        request.setStateKeyRef(encryptedState.keyRef());
        request.setStateNonce(encryptedState.nonce());
        request.setStateCiphertext(encryptedState.ciphertext());

        int inserted;
        try {
            inserted = mapper.insertAuthorizationRequest(request);
        } catch (DuplicateKeyException conflict) {
            throw BoardOAuthProtocolException.conflict(REQUEST_CONFLICT);
        } catch (DataAccessException failure) {
            throw BoardOAuthProtocolException.temporarilyUnavailable(REQUEST_WRITE_FAILED);
        } catch (RuntimeException failure) {
            throw BoardOAuthProtocolException.serverError(REQUEST_WRITE_FAILED);
        }
        if (inserted != 1) {
            throw BoardOAuthProtocolException.serverError(REQUEST_WRITE_FAILED);
        }

        return new BoardOAuthAuthorizationStartResult(requestHandle, expiresAt);
    }

    /**
     * Approves one locked consent request and issues one 60-second code.
     *
     * <p>The raw code and state may leave the process only through the public
     * facade, after the hidden runner's root transaction commits.</p>
     */
    public BoardOAuthAuthorizationApprovedResult approve(
            BoardOAuthAuthorizationApprovalCommand command) {
        validateConsentShape(
                command == null ? null : command.rawRequestHandle(),
                command == null ? null : command.serverAuthenticatedUserId(),
                command == null ? null : command.serverValidatedTenantId(),
                command == null ? null : command.intent());
        try {
            ConsentContext context = lockConsentContext(
                    command.rawRequestHandle(),
                    command.serverAuthenticatedUserId(),
                    command.serverValidatedTenantId(),
                    command.intent(),
                    CODE_LIFETIME);
            Instant approvedAt = context.decisionAt();

            String rawCode = codeGenerator.generate();
            if (rawCode == null || !REQUEST_HANDLE.matcher(rawCode).matches()) {
                throw BoardOAuthProtocolException.serverError(
                        BoardOAuthAuthorizationCodeGenerator.CODE_GENERATION_FAILED);
            }

            BoardOAuthAuthorizationRequest request = context.request();
            Long expectedVersion = request.getVersion();
            applyDecisionIdentity(request, context, approvedAt, true);
            if (mapper.approveAuthorizationRequestIfVersion(request, expectedVersion) != 1) {
                throw BoardOAuthProtocolException.conflict(REQUEST_CONFLICT);
            }
            request.setStatus(STATUS_APPROVED);
            request.setVersion(expectedVersion + 1L);

            BoardOAuthAuthorizationCode code = authorizationCode(
                    request, rawCode, approvedAt);
            if (mapper.insertAuthorizationCode(code) != 1
                    || code.getId() == null
                    || code.getId() <= 0L) {
                throw BoardOAuthProtocolException.serverError(
                        AUTHORIZATION_CODE_WRITE_FAILED);
            }

            BoardOAuthReceiptFactory.ApprovalReceipts receipts = receiptFactory.approval(
                    request, code, context.userId(), approvedAt);
            insertReceipt(receipts.authorizationApproved());
            insertReceipt(receipts.authorizationCodeIssued());

            return new BoardOAuthAuthorizationApprovedResult(
                    request.getRedirectUri(),
                    rawCode,
                    context.rawState(),
                    request.getIssuerUri(),
                    approvedAt.plus(CODE_LIFETIME));
        } catch (BoardOAuthProtocolException known) {
            throw known;
        } catch (DuplicateKeyException conflict) {
            throw BoardOAuthProtocolException.conflict(REQUEST_CONFLICT);
        } catch (DataAccessException failure) {
            throw BoardOAuthProtocolException.temporarilyUnavailable(
                    CONSENT_PERSISTENCE_UNAVAILABLE);
        } catch (RuntimeException failure) {
            throw BoardOAuthProtocolException.serverError(REQUEST_WRITE_FAILED);
        }
    }

    /**
     * Denies one locked consent request and clears its encrypted state tuple.
     * Raw state may be exposed only after the same proxy-commit boundary as
     * {@link #approve(BoardOAuthAuthorizationApprovalCommand)}.
     */
    public BoardOAuthAuthorizationDeniedResult deny(
            BoardOAuthAuthorizationDenialCommand command) {
        validateConsentShape(
                command == null ? null : command.rawRequestHandle(),
                command == null ? null : command.serverAuthenticatedUserId(),
                command == null ? null : command.serverValidatedTenantId(),
                command == null ? null : command.intent());
        try {
            ConsentContext context = lockConsentContext(
                    command.rawRequestHandle(),
                    command.serverAuthenticatedUserId(),
                    command.serverValidatedTenantId(),
                    command.intent(),
                    Duration.ZERO);
            Instant deniedAt = context.decisionAt();
            BoardOAuthAuthorizationRequest request = context.request();
            Long expectedVersion = request.getVersion();
            applyDecisionIdentity(request, context, deniedAt, false);
            if (mapper.denyAuthorizationRequestIfVersion(request, expectedVersion) != 1) {
                throw BoardOAuthProtocolException.conflict(REQUEST_CONFLICT);
            }
            request.setStatus(STATUS_DENIED);
            request.setVersion(expectedVersion + 1L);
            request.setStateKeyRef(null);
            request.setStateNonce(null);
            request.setStateCiphertext(null);

            insertReceipt(receiptFactory.denial(request, context.userId(), deniedAt));
            return new BoardOAuthAuthorizationDeniedResult(
                    request.getRedirectUri(),
                    context.rawState(),
                    request.getIssuerUri(),
                    ACCESS_DENIED);
        } catch (BoardOAuthProtocolException known) {
            throw known;
        } catch (DuplicateKeyException conflict) {
            throw BoardOAuthProtocolException.conflict(REQUEST_CONFLICT);
        } catch (DataAccessException failure) {
            throw BoardOAuthProtocolException.temporarilyUnavailable(
                    CONSENT_PERSISTENCE_UNAVAILABLE);
        } catch (RuntimeException failure) {
            throw BoardOAuthProtocolException.serverError(REQUEST_WRITE_FAILED);
        }
    }

    private ConsentContext lockConsentContext(
            String rawRequestHandle,
            Long userId,
            Long tenantId,
            BoardOAuthConsentIntent intent,
            Duration minimumClientRemaining) {
        Instant lockRequestedAt = millisecondNow();
        byte[] handleDigest = BoardOAuthCrypto.sha256Ascii(rawRequestHandle);
        BoardOAuthAuthorizationRequest locator =
                mapper.selectAuthorizationRequestLocatorByHandle(handleDigest);
        if (locator == null) {
            throw BoardOAuthProtocolException.invalidRequest(CONSENT_REQUEST_NOT_FOUND);
        }
        Long requestId = locator.getId();
        String clientId = locator.getClientId();
        if (requestId == null
                || requestId <= 0L
                || clientId == null
                || !CLIENT_ID.matcher(clientId).matches()) {
            throw BoardOAuthProtocolException.serverError(CONSENT_REQUEST_DRIFT);
        }

        BoardEnterpriseAuthority enterprise =
                boardMapper.selectEnterpriseSlotForUpdate(tenantId);
        BoardEnterpriseMemberScope member =
                boardMapper.selectActiveContextForUpdate(tenantId, userId);
        validateEnterprise(enterprise, tenantId);
        validateMember(member, tenantId, userId);

        BoardProductEntitlement entitlement = boardMapper.selectEntitlementForUpdate(
                tenantId, member.getMemberId(), PRODUCT_CODE);
        // Early shape/current checks preserve fail-fast behavior. Every
        // time-sensitive predicate is repeated at the branch-final instant.
        validateEntitlement(entitlement, member, lockRequestedAt);

        BoardProductPlan plan = boardMapper.selectActivePlanForUpdate(
                PRODUCT_CODE, VIP_PLAN);
        validateVipPlan(plan);

        BoardConnectorBinding binding = boardMapper.selectConnectorBindingSlotForUpdate(
                tenantId,
                member.getMemberId(),
                PRODUCT_CODE,
                SOURCE_CODE,
                CONNECTOR_CODE);
        List<String> bindingScopes = null;
        if (binding != null) {
            bindingScopes = boardMapper.selectConnectorBindingScopesForUpdate(
                    binding.getBindingId());
            if (bindingScopes == null) {
                throw BoardOAuthProtocolException.serverError(CONSENT_BINDING_DRIFT);
            }
        }
        validateIntent(intent, binding, bindingScopes, member, lockRequestedAt);

        BoardOAuthClient client = mapper.selectClientForUpdate(clientId);
        validateLockedClient(
                client,
                clientId,
                null,
                lockRequestedAt,
                minimumClientRemaining,
                true);

        BoardOAuthAuthorizationRequest request = mapper.selectAuthorizationRequestForUpdate(
                requestId, clientId, handleDigest);
        validateLockedRequest(
                request,
                requestId,
                client,
                handleDigest,
                lockRequestedAt,
                minimumClientRemaining);

        // The decision instant is captured only after the full mutable consent
        // authority set is locked. Any lock wait that crosses entitlement,
        // client, or request expiry must therefore fail closed below.
        Instant decisionAt = decisionAtNotBefore(lockRequestedAt);
        validateEntitlement(entitlement, member, decisionAt);
        validateIntent(intent, binding, bindingScopes, member, decisionAt);
        validateLockedClient(
                client,
                clientId,
                null,
                decisionAt,
                minimumClientRemaining,
                true);
        validateLockedRequest(
                request,
                requestId,
                client,
                handleDigest,
                decisionAt,
                minimumClientRemaining);

        byte[] aadDigest = BoardOAuthAuthorizationStateAad.digest(request);
        String rawState = decryptState(request, aadDigest);
        if (!isPrintableState(rawState)
                || !BoardOAuthCrypto.matchesSha256Digest(
                        rawState, request.getStateDigest())) {
            throw BoardOAuthProtocolException.serverError(CONSENT_STATE_TAMPERED);
        }
        byte[] principalSubjectDigest = BoardOAuthPrincipalSubject.digest(
                tenantId, member.getMemberId(), userId);
        return new ConsentContext(
                request,
                tenantId,
                member.getMemberId(),
                userId,
                principalSubjectDigest,
                intent,
                rawState,
                decisionAt);
    }

    private static void validateConsentShape(
            String rawRequestHandle,
            Long userId,
            Long tenantId,
            BoardOAuthConsentIntent intent) {
        if (rawRequestHandle == null
                || !REQUEST_HANDLE.matcher(rawRequestHandle).matches()
                || userId == null
                || userId <= 0L
                || tenantId == null
                || tenantId <= 0L
                || intent == null) {
            throw BoardOAuthProtocolException.invalidRequest(CONSENT_INVALID);
        }
    }

    private static void validateEnterprise(
            BoardEnterpriseAuthority enterprise,
            Long tenantId) {
        if (enterprise == null
                || !Objects.equals(enterprise.getTenantId(), tenantId)
                || !Objects.equals(enterprise.getStatus(), 1)
                || !Objects.equals(enterprise.getDelFlag(), "0")) {
            throw BoardOAuthProtocolException.accessDenied(
                    CONSENT_ENTERPRISE_NOT_ACTIVE);
        }
    }

    private static void validateMember(
            BoardEnterpriseMemberScope member,
            Long tenantId,
            Long userId) {
        if (member == null
                || member.getMemberId() == null
                || member.getMemberId() <= 0L
                || !Objects.equals(member.getTenantId(), tenantId)
                || !Objects.equals(member.getUserId(), userId)
                || !Objects.equals(member.getStatus(), 1)
                || !Objects.equals(member.getDelFlag(), "0")) {
            throw BoardOAuthProtocolException.accessDenied(CONSENT_MEMBER_NOT_ACTIVE);
        }
    }

    private static void validateEntitlement(
            BoardProductEntitlement entitlement,
            BoardEnterpriseMemberScope member,
            Instant now) {
        if (entitlement == null
                || entitlement.getId() == null
                || entitlement.getId() <= 0L
                || !Objects.equals(entitlement.getTenantId(), member.getTenantId())
                || !Objects.equals(entitlement.getMemberId(), member.getMemberId())
                || !Objects.equals(entitlement.getUserId(), member.getUserId())
                || !Objects.equals(entitlement.getProductCode(), PRODUCT_CODE)
                || !Objects.equals(entitlement.getPlanCode(), VIP_PLAN)
                || !Objects.equals(entitlement.getStatus(), STATUS_ACTIVE)
                || entitlement.getValidFrom() == null
                || entitlement.getValidFrom().toInstant().isAfter(now)
                || (entitlement.getValidUntil() != null
                        && !entitlement.getValidUntil().toInstant().isAfter(now))
                || entitlement.getVersion() == null
                || entitlement.getVersion() < 0L) {
            throw BoardOAuthProtocolException.accessDenied(CONSENT_VIP_NOT_CURRENT);
        }
    }

    private static void validateVipPlan(BoardProductPlan plan) {
        if (plan == null
                || !Objects.equals(plan.getProductCode(), PRODUCT_CODE)
                || !Objects.equals(plan.getPlanCode(), VIP_PLAN)
                || !Boolean.TRUE.equals(plan.getVip())
                || !Boolean.TRUE.equals(plan.getConnectorRequired())
                || !Objects.equals(plan.getDailyMeetingLimit(), 5)
                || !Objects.equals(plan.getAgendaLimit(), 30)
                || plan.getSeatLimit() != null
                || !Boolean.TRUE.equals(plan.getSecretaryEnabled())
                || !Objects.equals(plan.getStatus(), STATUS_ACTIVE)) {
            throw BoardOAuthProtocolException.serverError(CONSENT_PLAN_DRIFT);
        }
    }

    private static void validateIntent(
            BoardOAuthConsentIntent intent,
            BoardConnectorBinding binding,
            List<String> scopes,
            BoardEnterpriseMemberScope member,
            Instant decisionAt) {
        if (intent == BoardOAuthConsentIntent.FIRST_CONNECT) {
            if (binding != null) {
                throw BoardOAuthProtocolException.conflict(CONSENT_INTENT_CONFLICT);
            }
            return;
        }
        if (intent != BoardOAuthConsentIntent.EXPLICIT_REAUTHORIZATION || binding == null) {
            throw BoardOAuthProtocolException.conflict(CONSENT_INTENT_CONFLICT);
        }
        if (binding.getId() == null
                || binding.getId() <= 0L
                || !isCanonicalUuid(binding.getBindingId())
                || !Objects.equals(binding.getTenantId(), member.getTenantId())
                || !Objects.equals(binding.getMemberId(), member.getMemberId())
                || !Objects.equals(binding.getUserId(), member.getUserId())
                || !Objects.equals(binding.getProductCode(), PRODUCT_CODE)
                || !Objects.equals(binding.getSourceCode(), SOURCE_CODE)
                || !Objects.equals(binding.getConnectorCode(), CONNECTOR_CODE)
                || !Objects.equals(binding.getIssuerUri(), BoardOAuthProfile.ISSUER)
                || !Objects.equals(binding.getResourceUri(), BoardOAuthProfile.RESOURCE)
                || binding.getClientId() == null
                || !CLIENT_ID.matcher(binding.getClientId()).matches()
                || binding.getPrincipalSubjectDigest() == null
                || !SHA256_HEX.matcher(binding.getPrincipalSubjectDigest()).matches()
                || !BINDING_STATUSES.contains(binding.getStatus())
                || !BINDING_VERIFICATION_METHODS.contains(binding.getVerificationMethod())
                || binding.getEvidenceDigest() == null
                || !SHA256_HEX.matcher(binding.getEvidenceDigest()).matches()
                || binding.getVerifiedAt() == null
                || binding.getVerifiedAt().toInstant().isAfter(decisionAt)
                || binding.getLastSeenAt() == null
                || binding.getLastSeenAt().before(binding.getVerifiedAt())
                || binding.getLastSeenAt().toInstant().isAfter(decisionAt)
                || binding.getValidUntil() == null
                || !binding.getValidUntil().after(binding.getLastSeenAt())
                || binding.getVersion() == null
                || binding.getVersion() <= 0L
                || ("ACTIVE".equals(binding.getStatus()) && binding.getRevokedAt() != null)
                || (!"ACTIVE".equals(binding.getStatus())
                        && (binding.getRevokedAt() == null
                                || binding.getRevokedAt().before(binding.getLastSeenAt())
                                || binding.getRevokedAt().toInstant().isAfter(decisionAt)))
                || !hasExactScopes(scopes)) {
            throw BoardOAuthProtocolException.serverError(CONSENT_BINDING_DRIFT);
        }
    }

    private static void validateLockedClient(
            BoardOAuthClient client,
            String expectedClientId,
            String expectedRedirect,
            Instant now,
            Duration minimumRemaining,
            boolean requirePersistentId) {
        if (client == null
                || !Objects.equals(expectedClientId, client.getClientId())
                || (requirePersistentId
                        && (client.getId() == null || client.getId() <= 0L))
                || !STATUS_ACTIVE.equals(client.getStatus())
                || client.getTerminatedAt() != null
                || client.getRegisteredAt() == null
                || client.getExpiresAt() == null
                || client.getRegisteredAt().toInstant().isAfter(now)
                || !client.getExpiresAt().toInstant().isAfter(
                        now.plus(minimumRemaining))) {
            throw BoardOAuthProtocolException.invalidRequest(CLIENT_INVALID);
        }

        byte[] expectedScopeDigest = BoardOAuthCrypto.sha256Ascii(
                BoardOAuthProfile.CANONICAL_SCOPE);
        int redirectPort;
        try {
            redirectPort = BoardOAuthProfile.requireLoopbackPort(client.getRedirectUri());
        } catch (IllegalArgumentException invalidRedirect) {
            throw BoardOAuthProtocolException.serverError(CLIENT_PROFILE_DRIFT);
        }
        boolean exactProfile = NEUTRAL_CLIENT_NAME.equals(client.getClientName())
                && BoardOAuthProfile.ISSUER.equals(client.getIssuerUri())
                && BoardOAuthProfile.RESOURCE.equals(client.getResourceUri())
                && PRODUCT_CODE.equals(client.getProductCode())
                && SOURCE_CODE.equals(client.getSourceCode())
                && CONNECTOR_CODE.equals(client.getConnectorCode())
                && Integer.valueOf(redirectPort).equals(client.getRedirectPort())
                && (expectedRedirect == null
                        || expectedRedirect.equals(client.getRedirectUri()))
                && TOKEN_ENDPOINT_AUTH_METHOD.equals(client.getTokenEndpointAuthMethod())
                && GRANT_TYPES_CANONICAL.equals(client.getGrantTypesCanonical())
                && RESPONSE_TYPES_CANONICAL.equals(client.getResponseTypesCanonical())
                && BoardOAuthProfile.CANONICAL_SCOPE.equals(client.getScopeCanonical())
                && BoardOAuthCrypto.constantTimeEquals(
                        expectedScopeDigest, client.getScopeDigest())
                && hasDigest(client.getMetadataDigest())
                && hasDigest(client.getRegistrationSourceDigest())
                && client.getVersion() != null
                && client.getVersion() >= 0
                && client.getExpiresAt().toInstant().equals(
                        client.getRegisteredAt().toInstant().plus(CLIENT_LIFETIME));
        if (!exactProfile) {
            throw BoardOAuthProtocolException.serverError(CLIENT_PROFILE_DRIFT);
        }
    }

    private static void validateLockedRequest(
            BoardOAuthAuthorizationRequest request,
            Long expectedRequestId,
            BoardOAuthClient client,
            byte[] expectedHandleDigest,
            Instant now,
            Duration minimumRemaining) {
        if (request == null) {
            throw BoardOAuthProtocolException.invalidRequest(CONSENT_REQUEST_NOT_FOUND);
        }
        if (!STATUS_PENDING.equals(request.getStatus())) {
            throw BoardOAuthProtocolException.conflict(REQUEST_CONFLICT);
        }
        if (request.getRequestedAt() == null
                || request.getExpiresAt() == null
                || request.getRequestedAt().toInstant().isAfter(now)
                || !request.getExpiresAt().toInstant().isAfter(
                        now.plus(minimumRemaining))) {
            throw BoardOAuthProtocolException.invalidRequest(CONSENT_REQUEST_EXPIRED);
        }
        byte[] expectedScopeDigest = BoardOAuthCrypto.sha256Ascii(
                BoardOAuthProfile.CANONICAL_SCOPE);
        boolean exact = Objects.equals(request.getId(), expectedRequestId)
                && expectedRequestId != null
                && expectedRequestId > 0L
                && BoardOAuthCrypto.constantTimeEquals(
                        expectedHandleDigest, request.getRequestHandleDigest())
                && Objects.equals(request.getClientId(), client.getClientId())
                && Objects.equals(request.getRedirectUri(), client.getRedirectUri())
                && BoardOAuthProfile.isAllowedLoopbackRedirect(request.getRedirectUri())
                && BoardOAuthCrypto.isValidPkceS256Challenge(request.getCodeChallenge())
                && BoardOAuthProfile.PKCE_METHOD.equals(request.getCodeChallengeMethod())
                && hasDigest(request.getStateDigest())
                && isSafeKeyRef(request.getStateKeyRef())
                && request.getStateNonce() != null
                && request.getStateNonce().length == 12
                && request.getStateCiphertext() != null
                && request.getStateCiphertext().length >= 32
                && request.getStateCiphertext().length <= 528
                && BoardOAuthProfile.ISSUER.equals(request.getIssuerUri())
                && BoardOAuthProfile.RESOURCE.equals(request.getResourceUri())
                && PRODUCT_CODE.equals(request.getProductCode())
                && SOURCE_CODE.equals(request.getSourceCode())
                && CONNECTOR_CODE.equals(request.getConnectorCode())
                && BoardOAuthProfile.CANONICAL_SCOPE.equals(request.getScopeCanonical())
                && BoardOAuthCrypto.constantTimeEquals(
                        expectedScopeDigest, request.getScopeDigest())
                && request.getTenantId() == null
                && request.getMemberId() == null
                && request.getUserId() == null
                && request.getPrincipalSubjectDigest() == null
                && request.getConsentIntent() == null
                && request.getApprovedAt() == null
                && request.getDeniedAt() == null
                && request.getConsumedAt() == null
                && Objects.equals(request.getVersion(), 0L)
                && request.getExpiresAt().toInstant().equals(
                        request.getRequestedAt().toInstant().plus(REQUEST_LIFETIME));
        if (!exact) {
            throw BoardOAuthProtocolException.serverError(CONSENT_REQUEST_DRIFT);
        }
    }

    private static void applyDecisionIdentity(
            BoardOAuthAuthorizationRequest request,
            ConsentContext context,
            Instant decisionAt,
            boolean approved) {
        request.setTenantId(context.tenantId());
        request.setMemberId(context.memberId());
        request.setUserId(context.userId());
        request.setPrincipalSubjectDigest(context.principalSubjectDigest());
        request.setConsentIntent(context.intent());
        if (approved) {
            request.setApprovedAt(Date.from(decisionAt));
        } else {
            request.setDeniedAt(Date.from(decisionAt));
        }
    }

    private static BoardOAuthAuthorizationCode authorizationCode(
            BoardOAuthAuthorizationRequest request,
            String rawCode,
            Instant issuedAt) {
        BoardOAuthAuthorizationCode code = new BoardOAuthAuthorizationCode();
        code.setCodeDigest(BoardOAuthCrypto.sha256Ascii(rawCode));
        code.setAuthorizationRequestId(request.getId());
        code.setClientId(request.getClientId());
        code.setRedirectUri(request.getRedirectUri());
        code.setCodeChallenge(request.getCodeChallenge());
        code.setCodeChallengeMethod(request.getCodeChallengeMethod());
        code.setIssuerUri(request.getIssuerUri());
        code.setResourceUri(request.getResourceUri());
        code.setProductCode(request.getProductCode());
        code.setSourceCode(request.getSourceCode());
        code.setConnectorCode(request.getConnectorCode());
        code.setScopeCanonical(request.getScopeCanonical());
        code.setScopeDigest(request.getScopeDigest().clone());
        code.setTenantId(request.getTenantId());
        code.setMemberId(request.getMemberId());
        code.setUserId(request.getUserId());
        code.setPrincipalSubjectDigest(request.getPrincipalSubjectDigest().clone());
        code.setConsentIntent(request.getConsentIntent());
        code.setStatus(STATUS_ACTIVE);
        code.setIssuedAt(Date.from(issuedAt));
        code.setExpiresAt(Date.from(issuedAt.plus(CODE_LIFETIME)));
        code.setUsedAt(null);
        code.setRevokedAt(null);
        code.setVersion(0L);
        return code;
    }

    private void insertReceipt(BoardOAuthReceipt receipt) {
        if (mapper.insertReceipt(receipt) != 1) {
            throw BoardOAuthProtocolException.serverError(
                    AUTHORIZATION_RECEIPT_WRITE_FAILED);
        }
    }

    private static void validateRequestShape(BoardOAuthAuthorizationStartCommand command) {
        if (command == null
                || !RESPONSE_TYPE_CODE.equals(command.responseType())
                || command.clientId() == null
                || !CLIENT_ID.matcher(command.clientId()).matches()
                || !BoardOAuthProfile.isAllowedLoopbackRedirect(command.redirectUri())
                || !BoardOAuthCrypto.isValidPkceS256Challenge(command.codeChallenge())
                || !BoardOAuthProfile.PKCE_METHOD.equals(command.codeChallengeMethod())
                || !BoardOAuthProfile.RESOURCE.equals(command.resourceUri())
                || !BoardOAuthProfile.hasExactScopeSet(command.scopes())
                || !isPrintableState(command.state())) {
            throw BoardOAuthProtocolException.invalidRequest(REQUEST_INVALID);
        }
    }

    private BoardOAuthStateCipher.EncryptedState encryptState(
            String state,
            byte[] aadDigest) {
        try {
            return stateCipher.encrypt(state, aadDigest);
        } catch (ServiceException failure) {
            if (Integer.valueOf(503).equals(failure.getCode())
                    || AesGcmBoardOAuthStateCipher.KEY_NOT_CONFIGURED.equals(
                            failure.getMessage())) {
                throw BoardOAuthProtocolException.temporarilyUnavailable(
                        STATE_PROTECTION_UNAVAILABLE);
            }
            if (Integer.valueOf(400).equals(failure.getCode())) {
                throw BoardOAuthProtocolException.invalidRequest(REQUEST_INVALID);
            }
            throw BoardOAuthProtocolException.serverError(STATE_PROTECTION_FAILED);
        } catch (BoardOAuthProtocolException known) {
            throw known;
        } catch (RuntimeException failure) {
            throw BoardOAuthProtocolException.serverError(STATE_PROTECTION_FAILED);
        }
    }

    private String decryptState(
            BoardOAuthAuthorizationRequest request,
            byte[] aadDigest) {
        try {
            return stateCipher.decrypt(
                    request.getStateKeyRef(),
                    request.getStateNonce(),
                    request.getStateCiphertext(),
                    aadDigest);
        } catch (ServiceException failure) {
            if (Integer.valueOf(503).equals(failure.getCode())
                    || AesGcmBoardOAuthStateCipher.KEY_NOT_CONFIGURED.equals(
                            failure.getMessage())) {
                throw BoardOAuthProtocolException.temporarilyUnavailable(
                        STATE_PROTECTION_UNAVAILABLE);
            }
            throw BoardOAuthProtocolException.serverError(CONSENT_STATE_TAMPERED);
        } catch (BoardOAuthProtocolException known) {
            throw known;
        } catch (RuntimeException failure) {
            throw BoardOAuthProtocolException.serverError(CONSENT_STATE_TAMPERED);
        }
    }

    private static void validateEncryptedState(BoardOAuthStateCipher.EncryptedState encrypted) {
        if (encrypted == null
                || !isSafeKeyRef(encrypted.keyRef())
                || encrypted.nonce() == null
                || encrypted.nonce().length != 12
                || encrypted.ciphertext() == null
                || encrypted.ciphertext().length < 32
                || encrypted.ciphertext().length > 528) {
            throw BoardOAuthProtocolException.serverError(STATE_PROTECTION_FAILED);
        }
    }

    private Instant millisecondNow() {
        return Instant.ofEpochMilli(clock.instant().toEpochMilli());
    }

    private Instant decisionAtNotBefore(Instant notBefore) {
        Instant decisionAt = millisecondNow();
        return decisionAt.isBefore(notBefore) ? notBefore : decisionAt;
    }

    private static boolean isPrintableState(String state) {
        if (state == null || state.length() < 16 || state.length() > 512) {
            return false;
        }
        for (int index = 0; index < state.length(); index++) {
            char character = state.charAt(index);
            if (character < 0x20 || character > 0x7e) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSafeKeyRef(String keyRef) {
        if (keyRef == null || keyRef.isEmpty() || keyRef.length() > 191) {
            return false;
        }
        for (int index = 0; index < keyRef.length(); index++) {
            char value = keyRef.charAt(index);
            if (!(value >= 'A' && value <= 'Z')
                    && !(value >= 'a' && value <= 'z')
                    && !(value >= '0' && value <= '9')
                    && value != '.' && value != '_' && value != ':' && value != '-') {
                return false;
            }
        }
        return true;
    }

    private static boolean hasDigest(byte[] value) {
        return value != null && value.length == BoardOAuthCrypto.SHA256_BYTES;
    }

    private static boolean hasExactScopes(List<String> scopes) {
        return scopes != null
                && scopes.size() == BoardOAuthProfile.REQUIRED_SCOPES.size()
                && new HashSet<>(scopes).size() == scopes.size()
                && BoardOAuthProfile.hasExactScopeSet(scopes);
    }

    private static boolean isCanonicalUuid(String value) {
        if (value == null) {
            return false;
        }
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static final class ConsentContext {
        private final BoardOAuthAuthorizationRequest request;
        private final Long tenantId;
        private final Long memberId;
        private final Long userId;
        private final byte[] principalSubjectDigest;
        private final BoardOAuthConsentIntent intent;
        private final String rawState;
        private final Instant decisionAt;

        private ConsentContext(
                BoardOAuthAuthorizationRequest request,
                Long tenantId,
                Long memberId,
                Long userId,
                byte[] principalSubjectDigest,
                BoardOAuthConsentIntent intent,
                String rawState,
                Instant decisionAt) {
            this.request = request;
            this.tenantId = tenantId;
            this.memberId = memberId;
            this.userId = userId;
            this.principalSubjectDigest = principalSubjectDigest.clone();
            this.intent = Objects.requireNonNull(intent, "intent");
            this.rawState = rawState;
            this.decisionAt = Objects.requireNonNull(decisionAt, "decisionAt");
        }

        private BoardOAuthAuthorizationRequest request() {
            return request;
        }

        private Long tenantId() {
            return tenantId;
        }

        private Long memberId() {
            return memberId;
        }

        private Long userId() {
            return userId;
        }

        private byte[] principalSubjectDigest() {
            return principalSubjectDigest.clone();
        }

        private BoardOAuthConsentIntent intent() {
            return intent;
        }

        private String rawState() {
            return rawState;
        }

        private Instant decisionAt() {
            return decisionAt;
        }

        @Override
        public String toString() {
            return "ConsentContext[REDACTED]";
        }
    }
}
