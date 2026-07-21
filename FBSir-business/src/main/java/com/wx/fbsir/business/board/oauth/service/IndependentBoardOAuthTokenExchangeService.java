package com.wx.fbsir.business.board.oauth.service;

import com.wx.fbsir.business.board.oauth.BoardOAuthConsentIntent;
import com.wx.fbsir.business.board.oauth.BoardOAuthCrypto;
import com.wx.fbsir.business.board.oauth.BoardOAuthProfile;
import com.wx.fbsir.business.board.oauth.BoardOAuthPrincipalSubject;
import com.wx.fbsir.business.board.oauth.BoardOAuthProtocolException;
import com.wx.fbsir.business.board.oauth.BoardOAuthTokenFamilyCreatedReceiptFactory;
import com.wx.fbsir.business.board.oauth.BoardOAuthTokenMaterialGenerator;
import com.wx.fbsir.business.board.oauth.BoardOAuthTokenSecurityReceiptFactory;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthAuthorizationCode;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthAuthorizationRequest;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthClient;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthReceipt;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthToken;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthTokenFamily;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthTokenExchangeCommand;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthTokenExchangeResult;
import com.wx.fbsir.business.board.oauth.mapper.IndependentBoardOAuthMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/** Internal, transaction-bound implementation of the locked authorization-code exchange. */
@Service
class IndependentBoardOAuthTokenExchangeService {
    public static final String INPUT_INVALID = "OAUTH_TOKEN_EXCHANGE_INPUT_INVALID";
    public static final String CODE_INVALID = "OAUTH_TOKEN_EXCHANGE_CODE_INVALID";
    public static final String CLIENT_INVALID = "OAUTH_TOKEN_EXCHANGE_CLIENT_INVALID";
    public static final String RESOURCE_INVALID =
            "OAUTH_TOKEN_EXCHANGE_RESOURCE_INVALID";
    public static final String PROFILE_DRIFT = "OAUTH_TOKEN_EXCHANGE_PROFILE_DRIFT";
    public static final String LINEAGE_DRIFT = "OAUTH_TOKEN_EXCHANGE_LINEAGE_DRIFT";
    public static final String CONSENT_INTENT_DRIFT =
            "OAUTH_TOKEN_EXCHANGE_CONSENT_INTENT_DRIFT";
    public static final String FAMILY_EXISTS = "OAUTH_TOKEN_EXCHANGE_FAMILY_EXISTS";
    public static final String CONFLICT = "OAUTH_TOKEN_EXCHANGE_CONFLICT";
    public static final String WRITE_FAILED = "OAUTH_TOKEN_EXCHANGE_WRITE_FAILED";
    public static final String RECEIPT_WRITE_FAILED =
            "OAUTH_TOKEN_EXCHANGE_RECEIPT_WRITE_FAILED";
    public static final String RECEIPT_CARDINALITY_INVALID =
            "OAUTH_TOKEN_EXCHANGE_RECEIPT_CARDINALITY_INVALID";
    public static final String PERSISTENCE_UNAVAILABLE =
            "OAUTH_TOKEN_EXCHANGE_PERSISTENCE_UNAVAILABLE";
    public static final String OUTCOME_INVALID =
            "OAUTH_TOKEN_EXCHANGE_OUTCOME_INVALID";
    public static final String SECURITY_RECEIPT_CARDINALITY_INVALID =
            "OAUTH_TOKEN_EXCHANGE_SECURITY_RECEIPT_CARDINALITY_INVALID";
    public static final String PENDING_FAMILY_DRIFT =
            "OAUTH_TOKEN_EXCHANGE_PENDING_FAMILY_DRIFT";
    public static final String REPLAY_LINEAGE_DRIFT =
            "OAUTH_TOKEN_EXCHANGE_REPLAY_LINEAGE_DRIFT";
    public static final String AUTHORITY_NOT_CURRENT =
            "OAUTH_TOKEN_EXCHANGE_AUTHORITY_NOT_CURRENT";

    static final Duration ACCESS_TOKEN_LIFETIME = Duration.ofMinutes(10);
    static final Duration MAX_FAMILY_LIFETIME = Duration.ofDays(30);

    private static final Duration CLIENT_LIFETIME = Duration.ofDays(31);
    private static final Duration REQUEST_LIFETIME = Duration.ofMinutes(5);
    private static final Duration CODE_LIFETIME = Duration.ofSeconds(60);
    private static final Duration MAX_DATABASE_CLOCK_SKEW = Duration.ofSeconds(5);
    private static final String PRODUCT_CODE = "FBSIR_INDEPENDENT_BOARD";
    private static final String SOURCE_CODE = "WORKBUDDY";
    private static final String CONNECTOR_CODE = "fbs-connector";
    private static final String NEUTRAL_CLIENT_NAME = "未验证的本地公共客户端";
    private static final String TOKEN_ENDPOINT_AUTH_METHOD = "none";
    private static final String GRANT_TYPES_CANONICAL =
            "authorization_code refresh_token";
    private static final String RESPONSE_TYPES_CANONICAL = "code";
    private static final String TOKEN_TYPE_BEARER = "Bearer";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_USED = "USED";
    private static final String STATUS_CONSUMED = "CONSUMED";
    private static final String STATUS_PENDING_BINDING = "PENDING_BINDING";
    private static final String TOKEN_ACCESS = "ACCESS";
    private static final String TOKEN_REFRESH = "REFRESH";
    private static final Pattern OPAQUE_256 = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Pattern CLIENT_ID = Pattern.compile("[A-Za-z0-9_-]{43,191}");

    private final IndependentBoardOAuthMapper mapper;
    private final BoardOAuthTokenExchangeAuthorityPort authorityPort;
    private final BoardOAuthTokenMaterialGenerator materialGenerator;
    private final Clock clock;

    @Autowired
    public IndependentBoardOAuthTokenExchangeService(
            IndependentBoardOAuthMapper mapper,
            BoardOAuthTokenExchangeAuthorityPort authorityPort,
            BoardOAuthTokenMaterialGenerator materialGenerator) {
        this(mapper, authorityPort, materialGenerator, Clock.systemUTC());
    }

    IndependentBoardOAuthTokenExchangeService(
            IndependentBoardOAuthMapper mapper,
            BoardOAuthTokenExchangeAuthorityPort authorityPort,
            BoardOAuthTokenMaterialGenerator materialGenerator,
            Clock clock) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.authorityPort = Objects.requireNonNull(
                authorityPort, "authorityPort");
        this.materialGenerator = Objects.requireNonNull(
                materialGenerator, "materialGenerator");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Returns replay rejection as data so the proxied runner can commit the
     * containment state before the public facade raises {@code invalid_grant}.
     */
    BoardOAuthTokenExchangeOutcome exchangeForCommit(
            BoardOAuthTokenExchangeCommand command) {
        validateCommand(command);
        try {
            return exchangeLocked(command);
        } catch (BoardOAuthProtocolException known) {
            throw known;
        } catch (DuplicateKeyException conflict) {
            throw BoardOAuthProtocolException.conflict(CONFLICT);
        } catch (DataAccessException unavailable) {
            throw BoardOAuthProtocolException.temporarilyUnavailable(
                    PERSISTENCE_UNAVAILABLE);
        } catch (RuntimeException failure) {
            throw BoardOAuthProtocolException.serverError(WRITE_FAILED);
        }
    }

    private BoardOAuthTokenExchangeOutcome exchangeLocked(
            BoardOAuthTokenExchangeCommand command) {
        byte[] codeDigest = BoardOAuthCrypto.sha256Ascii(
                command.rawAuthorizationCode());

        // Locator data is never authorization evidence; only its lock keys are used.
        IndependentBoardOAuthMapper.TokenExchangeLockLocator locator =
                mapper.selectTokenExchangeLockLocatorByDigest(codeDigest);
        requireLocator(locator, command);

        Instant lockRequestedAt = millisecondNow();
        BoardOAuthTokenExchangeAuthorityPort.LockResult authority =
                authorityPort.lockForTokenExchange(
                        locator.getTenantId(),
                        locator.getMemberId(),
                        locator.getUserId(),
                         locator.getProductCode(),
                         locator.getSourceCode(),
                         locator.getConnectorCode(),
                         lockRequestedAt);

        TreeSet<String> clientIds = new TreeSet<>();
        clientIds.add(command.clientId());
        if (locator.getPendingClientId() != null) {
            clientIds.add(locator.getPendingClientId());
        }
        Map<String, BoardOAuthClient> lockedClients = requireLockedClients(
                clientIds,
                mapper.selectClientsForUpdate(List.copyOf(clientIds)),
                command.clientId());
        BoardOAuthClient client = lockedClients.get(command.clientId());

        BoardOAuthAuthorizationRequest request =
                mapper.selectAuthorizationRequestByIdAndClientForUpdate(
                        locator.getAuthorizationRequestId(), command.clientId());
        BoardOAuthAuthorizationCode code = mapper.selectAuthorizationCodeForUpdate(
                locator.getCodeId(), command.clientId(), codeDigest);
        requireCanonicalFamilyLockKeys(request, code, locator, client, codeDigest);
        // Canonical family-slot order is shared with first-protected activation.
        BoardOAuthTokenFamily activeSlot =
                mapper.selectActiveTokenFamilySlotForUpdate(
                        request.getTenantId(), request.getMemberId(),
                        request.getProductCode(), request.getSourceCode(),
                        request.getConnectorCode());
        BoardOAuthTokenFamily pendingSlot =
                mapper.selectPendingTokenFamilySlotForUpdate(
                        request.getTenantId(), request.getMemberId(),
                        request.getProductCode(), request.getSourceCode(),
                        request.getConnectorCode());
        requirePendingClientPrelocked(pendingSlot, lockedClients);
        BoardOAuthTokenFamily existingFamily =
                mapper.selectTokenFamilyByOriginAuthorizationCodeForUpdate(
                        locator.getCodeId(), command.clientId());

        if (isConsumedCodeReplay(request, code)) {
            return containConsumedCodeReplay(
                    command,
                    request,
                    code,
                    locator,
                    client,
                    codeDigest,
                    existingFamily,
                    activeSlot,
                    pendingSlot,
                    authority.observedAt());
        }

        PendingSupersessionBeforeImage supersessionBeforeImage =
                existingFamily == null
                        ? lockSupersededPendingFamily(pendingSlot, activeSlot)
                        : null;

        // Fresh issuance uses a branch-final instant captured only after every
        // mutable before-image row for this branch is locked. This prevents a
        // lock wait from carrying an otherwise-valid request across an expiry.
        Instant now = decisionAtNotBefore(authority.observedAt());
        // Mutable client lifecycle may reject fresh issuance, but it must not
        // suppress containment of a proven replay of an already-consumed code.
        validateClient(client, command, now);
        validateLockedLineage(request, code, locator, client, codeDigest, command, now);
        requireIssuanceAuthority(authority, request.getConsentIntent(), now);
        if (existingFamily != null) {
            throw BoardOAuthProtocolException.conflict(FAMILY_EXISTS);
        }

        GeneratedMaterial generated = generateMaterial();
        SupersessionEvidence supersession = retireSupersededPendingFamily(
                supersessionBeforeImage, request, code, generated, now);

        Instant accessExpiresAt = now.plus(ACCESS_TOKEN_LIFETIME);
        Instant familyExpiresAt = earlier(
                now.plus(MAX_FAMILY_LIFETIME), client.getExpiresAt().toInstant());
        if (familyExpiresAt.isBefore(accessExpiresAt)) {
            throw BoardOAuthProtocolException.invalidClient(CLIENT_INVALID);
        }

        Long codeVersion = code.getVersion();
        Long requestVersion = request.getVersion();
        if (mapper.consumeAuthorizationCodeForClientIfVersion(
                code.getId(), code.getClientId(), codeVersion, Date.from(now)) != 1) {
            throw BoardOAuthProtocolException.conflict(CONFLICT);
        }
        request.setConsumedAt(Date.from(now));
        if (mapper.consumeAuthorizationRequestIfVersion(request, requestVersion) != 1) {
            throw BoardOAuthProtocolException.conflict(CONFLICT);
        }

        BoardOAuthTokenFamily family = newFamily(
                generated.familyId(), code, now, familyExpiresAt);
        insertFamily(family);
        BoardOAuthToken accessToken = newToken(
                generated.rawAccessToken(), generated.familyId(), TOKEN_ACCESS,
                now, accessExpiresAt, code);
        BoardOAuthToken refreshToken = newToken(
                generated.rawRefreshToken(), generated.familyId(), TOKEN_REFRESH,
                now, familyExpiresAt, code);
        insertToken(accessToken);
        insertToken(refreshToken);

        BoardOAuthAuthorizationRequest consumedRequest =
                mapper.selectAuthorizationRequestByIdAndClientForUpdate(
                        request.getId(), request.getClientId());
        BoardOAuthAuthorizationCode usedCode = mapper.selectAuthorizationCodeForUpdate(
                code.getId(), code.getClientId(), codeDigest);
        BoardOAuthTokenFamily persistedFamily = mapper.selectTokenFamilyForUpdate(
                generated.familyId(), code.getClientId());
        List<BoardOAuthToken> persistedTokens =
                mapper.selectFamilyTokensForUpdate(generated.familyId());
        validateCurrentRows(
                consumedRequest,
                usedCode,
                persistedFamily,
                persistedTokens,
                request,
                code,
                generated,
                now,
                familyExpiresAt,
                accessExpiresAt);

        List<BoardOAuthReceipt> existingCreationReceipts =
                mapper.selectTokenFamilyCreatedReceiptCandidatesForUpdate(
                        persistedFamily.getFamilyId(),
                        persistedFamily.getClientId(),
                        usedCode.getId());
        requireNoCreationReceipt(existingCreationReceipts);

        BoardOAuthReceipt receipt = BoardOAuthTokenFamilyCreatedReceiptFactory.create(
                generated.receiptId(),
                generated.correlationId(),
                now,
                consumedRequest,
                usedCode,
                persistedFamily,
                persistedTokens);
        if (mapper.insertReceipt(receipt) != 1) {
            throw BoardOAuthProtocolException.serverError(RECEIPT_WRITE_FAILED);
        }
        BoardOAuthReceipt persistedReceipt =
                mapper.selectReceiptByReceiptId(generated.receiptId());
        BoardOAuthTokenFamilyCreatedReceiptFactory.validate(
                persistedReceipt,
                consumedRequest,
                usedCode,
                persistedFamily,
                persistedTokens);
        validateSupersessionPair(
                supersession,
                persistedReceipt,
                consumedRequest,
                usedCode,
                generated);

        return BoardOAuthTokenExchangeOutcome.completed(
                new BoardOAuthTokenExchangeResult(
                        generated.rawAccessToken(),
                        generated.rawRefreshToken(),
                        TOKEN_TYPE_BEARER,
                        ACCESS_TOKEN_LIFETIME.toSeconds(),
                        BoardOAuthProfile.CANONICAL_SCOPE,
                        accessExpiresAt,
                        familyExpiresAt,
                        generated.receiptId(),
                        generated.correlationId()));
    }

    private static boolean isConsumedCodeReplay(
            BoardOAuthAuthorizationRequest request,
            BoardOAuthAuthorizationCode code) {
        return request != null
                && code != null
                && STATUS_CONSUMED.equals(request.getStatus())
                && STATUS_USED.equals(code.getStatus());
    }

    private static void requireLocator(
            IndependentBoardOAuthMapper.TokenExchangeLockLocator locator,
            BoardOAuthTokenExchangeCommand command) {
        if (locator == null
                || !isPositive(locator.getCodeId())
                || !isPositive(locator.getAuthorizationRequestId())
                || !isPositive(locator.getTenantId())
                || !isPositive(locator.getMemberId())
                || !isPositive(locator.getUserId())
                || !PRODUCT_CODE.equals(locator.getProductCode())
                || !SOURCE_CODE.equals(locator.getSourceCode())
                || !CONNECTOR_CODE.equals(locator.getConnectorCode())
                || !Objects.equals(command.clientId(), locator.getClientId())
                || (locator.getPendingClientId() != null
                        && !CLIENT_ID.matcher(locator.getPendingClientId()).matches())) {
            throw BoardOAuthProtocolException.invalidGrant(CODE_INVALID);
        }
    }

    private static Map<String, BoardOAuthClient> requireLockedClients(
            TreeSet<String> requestedIds,
            List<BoardOAuthClient> lockedClients,
            String commandClientId) {
        if (lockedClients == null) {
            throw BoardOAuthProtocolException.serverError(LINEAGE_DRIFT);
        }
        Map<String, BoardOAuthClient> byId = new LinkedHashMap<>();
        String prior = null;
        for (BoardOAuthClient locked : lockedClients) {
            String clientId = locked == null ? null : locked.getClientId();
            if (locked == null
                    || !isPositive(locked.getId())
                    || clientId == null
                    || !CLIENT_ID.matcher(clientId).matches()
                    || !requestedIds.contains(clientId)
                    || !hasLockedClientProvenance(locked)
                    || (prior != null && prior.compareTo(clientId) >= 0)
                    || byId.put(clientId, locked) != null) {
                throw BoardOAuthProtocolException.serverError(LINEAGE_DRIFT);
            }
            prior = clientId;
        }
        if (!byId.containsKey(commandClientId)) {
            throw BoardOAuthProtocolException.invalidClient(CLIENT_INVALID);
        }
        if (byId.size() != requestedIds.size()) {
            throw BoardOAuthProtocolException.serverError(LINEAGE_DRIFT);
        }
        return Map.copyOf(byId);
    }

    private static boolean hasLockedClientProvenance(BoardOAuthClient client) {
        return BoardOAuthProfile.ISSUER.equals(client.getIssuerUri())
                && BoardOAuthProfile.RESOURCE.equals(client.getResourceUri())
                && PRODUCT_CODE.equals(client.getProductCode())
                && SOURCE_CODE.equals(client.getSourceCode())
                && CONNECTOR_CODE.equals(client.getConnectorCode());
    }

    private static void requirePendingClientPrelocked(
            BoardOAuthTokenFamily pendingFamily,
            Map<String, BoardOAuthClient> lockedClients) {
        if (pendingFamily != null
                && (pendingFamily.getClientId() == null
                        || !lockedClients.containsKey(pendingFamily.getClientId()))) {
            throw BoardOAuthProtocolException.conflict(CONFLICT);
        }
    }

    private static void requireIssuanceAuthority(
            BoardOAuthTokenExchangeAuthorityPort.LockResult authority,
            BoardOAuthConsentIntent intent,
            Instant decisionAt) {
        if (authority == null || !authority.issuanceAuthorityCurrentAt(decisionAt)) {
            throw BoardOAuthProtocolException.accessDenied(AUTHORITY_NOT_CURRENT);
        }
        if (!authority.permits(intent, decisionAt)) {
            throw BoardOAuthProtocolException.conflict(CONSENT_INTENT_DRIFT);
        }
    }

    private static void requireCanonicalFamilyLockKeys(
            BoardOAuthAuthorizationRequest request,
            BoardOAuthAuthorizationCode code,
            IndependentBoardOAuthMapper.TokenExchangeLockLocator locator,
            BoardOAuthClient client,
            byte[] codeDigest) {
        if (request == null || code == null) {
            throw BoardOAuthProtocolException.invalidGrant(CODE_INVALID);
        }
        if (!isPositive(request.getId())
                || !isPositive(request.getTenantId())
                || !isPositive(request.getMemberId())
                || !isPositive(request.getUserId())
                || !PRODUCT_CODE.equals(request.getProductCode())
                || !SOURCE_CODE.equals(request.getSourceCode())
                || !CONNECTOR_CODE.equals(request.getConnectorCode())
                || !Objects.equals(request.getClientId(), client.getClientId())
                || !Objects.equals(request.getId(), locator.getAuthorizationRequestId())
                || !Objects.equals(request.getTenantId(), locator.getTenantId())
                || !Objects.equals(request.getMemberId(), locator.getMemberId())
                || !Objects.equals(request.getUserId(), locator.getUserId())
                || !Objects.equals(request.getProductCode(), locator.getProductCode())
                || !Objects.equals(request.getSourceCode(), locator.getSourceCode())
                || !Objects.equals(request.getConnectorCode(), locator.getConnectorCode())
                || !Objects.equals(request.getClientId(), locator.getClientId())
                || !Objects.equals(code.getId(), locator.getCodeId())
                || !sameDigest(code.getCodeDigest(), codeDigest)
                || !Objects.equals(code.getAuthorizationRequestId(), request.getId())
                || !Objects.equals(code.getClientId(), request.getClientId())
                || !Objects.equals(code.getTenantId(), request.getTenantId())
                || !Objects.equals(code.getMemberId(), request.getMemberId())
                || !Objects.equals(code.getUserId(), request.getUserId())
                || !Objects.equals(code.getProductCode(), request.getProductCode())
                || !Objects.equals(code.getSourceCode(), request.getSourceCode())
                || !Objects.equals(code.getConnectorCode(), request.getConnectorCode())) {
            throw BoardOAuthProtocolException.serverError(LINEAGE_DRIFT);
        }
    }

    private BoardOAuthTokenExchangeOutcome containConsumedCodeReplay(
            BoardOAuthTokenExchangeCommand command,
            BoardOAuthAuthorizationRequest request,
            BoardOAuthAuthorizationCode code,
            IndependentBoardOAuthMapper.TokenExchangeLockLocator locator,
            BoardOAuthClient client,
            byte[] codeDigest,
            BoardOAuthTokenFamily family,
            BoardOAuthTokenFamily activeSlot,
            BoardOAuthTokenFamily pendingSlot,
            Instant notBefore) {
        if (family == null) {
            throw BoardOAuthProtocolException.serverError(REPLAY_LINEAGE_DRIFT);
        }
        List<BoardOAuthToken> tokens =
                mapper.selectFamilyTokensForUpdate(family.getFamilyId());
        List<BoardOAuthReceipt> creationCandidates =
                mapper.selectTokenFamilyCreatedReceiptCandidatesForUpdate(
                        family.getFamilyId(), family.getClientId(), code.getId());
        BoardOAuthReceipt creationReceipt = requireSingleCreationReceipt(
                creationCandidates, family, code.getId());
        List<BoardOAuthReceipt> replayCandidates =
                mapper.selectAuthorizationCodeReplayReceiptCandidatesForUpdate(
                        family.getFamilyId(), family.getClientId(), code.getId());
        List<BoardOAuthReceipt> compromiseCandidates =
                mapper.selectTokenFamilyCompromisedReceiptCandidatesForUpdate(
                        family.getFamilyId(), family.getClientId());
        requireAtMostOneSecurityReceipt(replayCandidates);
        requireAtMostOneSecurityReceipt(compromiseCandidates);

        // Replay containment also uses a branch-final event time. Token and
        // receipt locks can wait independently of the family slot, so capturing
        // this value before those locks would produce stale terminal timestamps.
        Instant now = decisionAtNotBefore(notBefore);

        validateConsumedReplayLineage(
                command,
                request,
                code,
                locator,
                client,
                codeDigest,
                family,
                activeSlot,
                pendingSlot,
                tokens,
                creationReceipt,
                now);

        boolean liveFamily = STATUS_PENDING_BINDING.equals(family.getStatus())
                || STATUS_ACTIVE.equals(family.getStatus());
        BoardOAuthTokenFamily terminalFamily = family;
        List<BoardOAuthToken> terminalTokens = tokens;
        if (liveFamily) {
            if (!replayCandidates.isEmpty() || !compromiseCandidates.isEmpty()) {
                throw BoardOAuthProtocolException.serverError(
                        SECURITY_RECEIPT_CARDINALITY_INVALID);
            }
            int activeTokenCount = countTokensWithStatus(tokens, STATUS_ACTIVE);
            if (activeTokenCount <= 0
                    || mapper.revokeActiveFamilyTokensAtLogicalTime(
                            family.getFamilyId(), Date.from(now))
                            != activeTokenCount) {
                throw BoardOAuthProtocolException.conflict(CONFLICT);
            }
            if (mapper.compromiseTokenFamilyIfVersion(
                    family.getFamilyId(),
                    family.getClientId(),
                    family.getVersion(),
                    Date.from(now)) != 1) {
                throw BoardOAuthProtocolException.conflict(CONFLICT);
            }
            terminalFamily = mapper.selectTokenFamilyForUpdate(
                    family.getFamilyId(), family.getClientId());
            terminalTokens = mapper.selectFamilyTokensForUpdate(family.getFamilyId());
            requireCompromisedCurrentRead(
                    family, tokens, terminalFamily, terminalTokens, now);

            String correlationId = requireOpaqueIdentifier(
                    materialGenerator.generateCorrelationId());
            String familyReceiptId = requireOpaqueIdentifier(
                    materialGenerator.generateReceiptId());
            String replayReceiptId = requireOpaqueIdentifier(
                    materialGenerator.generateReceiptId());
            if (familyReceiptId.equals(replayReceiptId)) {
                throw BoardOAuthProtocolException.serverError(WRITE_FAILED);
            }
            BoardOAuthReceipt compromisedReceipt =
                    BoardOAuthTokenSecurityReceiptFactory.tokenFamilyCompromised(
                            familyReceiptId,
                            correlationId,
                            now,
                            request,
                            code,
                            terminalFamily,
                            terminalTokens,
                            creationReceipt);
            BoardOAuthReceipt persistedCompromised =
                    insertAndCurrentReadSecurityReceipt(compromisedReceipt);
            BoardOAuthTokenSecurityReceiptFactory.validateTokenFamilyCompromised(
                    persistedCompromised,
                    request,
                    code,
                    terminalFamily,
                    terminalTokens,
                    creationReceipt);

            BoardOAuthReceipt replayReceipt =
                    BoardOAuthTokenSecurityReceiptFactory
                            .authorizationCodeReplayDetected(
                                    replayReceiptId,
                                    correlationId,
                                    now,
                                    request,
                                    code,
                                    terminalFamily,
                                    terminalTokens,
                                    creationReceipt);
            BoardOAuthReceipt persistedReplay =
                    insertAndCurrentReadSecurityReceipt(replayReceipt);
            if (!Objects.equals(
                    persistedCompromised.getCorrelationId(),
                    persistedReplay.getCorrelationId())
                    || !Objects.equals(
                            persistedCompromised.getCreatedAt(),
                            persistedReplay.getCreatedAt())) {
                throw BoardOAuthProtocolException.serverError(
                        BoardOAuthTokenSecurityReceiptFactory.RECEIPT_INVALID);
            }
            BoardOAuthTokenSecurityReceiptFactory
                    .validateAuthorizationCodeReplayDetected(
                            persistedReplay,
                            request,
                            code,
                            terminalFamily,
                            terminalTokens,
                            creationReceipt);
        } else {
            requireTerminalReplayCurrentRead(family, tokens, now);
            requireTerminalCompromiseReceipt(
                    compromiseCandidates,
                    request,
                    code,
                    terminalFamily,
                    terminalTokens,
                    creationReceipt);
            if (replayCandidates.isEmpty()) {
                String correlationId = requireOpaqueIdentifier(
                        materialGenerator.generateCorrelationId());
                String replayReceiptId = requireOpaqueIdentifier(
                        materialGenerator.generateReceiptId());
                BoardOAuthReceipt replayReceipt =
                        BoardOAuthTokenSecurityReceiptFactory
                                .authorizationCodeReplayDetected(
                                        replayReceiptId,
                                        correlationId,
                                        now,
                                        request,
                                        code,
                                        terminalFamily,
                                        terminalTokens,
                                        creationReceipt);
                BoardOAuthReceipt persistedReplay =
                        insertAndCurrentReadSecurityReceipt(replayReceipt);
                BoardOAuthTokenSecurityReceiptFactory
                        .validateAuthorizationCodeReplayDetected(
                                persistedReplay,
                                request,
                                code,
                                terminalFamily,
                                terminalTokens,
                                creationReceipt);
            } else {
                BoardOAuthTokenSecurityReceiptFactory
                        .validateAuthorizationCodeReplayDetected(
                                replayCandidates.get(0),
                                request,
                                code,
                                terminalFamily,
                                terminalTokens,
                                creationReceipt);
            }
        }
        return BoardOAuthTokenExchangeOutcome.invalidGrantAfterCommit(CODE_INVALID);
    }

    private PendingSupersessionBeforeImage lockSupersededPendingFamily(
            BoardOAuthTokenFamily pendingFamily,
            BoardOAuthTokenFamily activeSlot) {
        if (pendingFamily == null) {
            return null;
        }
        if (activeSlot != null
                && Objects.equals(activeSlot.getFamilyId(), pendingFamily.getFamilyId())) {
            throw BoardOAuthProtocolException.serverError(PENDING_FAMILY_DRIFT);
        }
        List<BoardOAuthToken> tokens =
                mapper.selectFamilyTokensForUpdate(pendingFamily.getFamilyId());
        List<BoardOAuthReceipt> creationCandidates =
                mapper.selectTokenFamilyCreatedReceiptCandidatesForUpdate(
                        pendingFamily.getFamilyId(),
                        pendingFamily.getClientId(),
                        pendingFamily.getOriginAuthorizationCodeId());
        BoardOAuthReceipt creationReceipt = requireSingleCreationReceipt(
                creationCandidates,
                pendingFamily,
                pendingFamily.getOriginAuthorizationCodeId());
        List<BoardOAuthReceipt> revokedCandidates =
                mapper.selectTokenFamilyRevokedReceiptCandidatesForUpdate(
                        pendingFamily.getFamilyId(), pendingFamily.getClientId());
        if (revokedCandidates == null || !revokedCandidates.isEmpty()) {
            throw BoardOAuthProtocolException.serverError(
                    SECURITY_RECEIPT_CARDINALITY_INVALID);
        }
        return new PendingSupersessionBeforeImage(
                pendingFamily, List.copyOf(tokens), creationReceipt);
    }

    private SupersessionEvidence retireSupersededPendingFamily(
            PendingSupersessionBeforeImage beforeImage,
            BoardOAuthAuthorizationRequest request,
            BoardOAuthAuthorizationCode code,
            GeneratedMaterial generated,
            Instant now) {
        if (beforeImage == null) {
            return null;
        }
        BoardOAuthTokenFamily pendingFamily = beforeImage.pendingFamily;
        List<BoardOAuthToken> tokens = beforeImage.tokens;
        BoardOAuthReceipt creationReceipt = beforeImage.creationReceipt;
        requireSupersededPendingBeforeImage(
                pendingFamily, tokens, request, code, now);
        if (generated.familyId().equals(pendingFamily.getFamilyId())
                || generated.receiptId().equals(creationReceipt.getReceiptId())
                || generated.correlationId().equals(
                        creationReceipt.getCorrelationId())) {
            throw BoardOAuthProtocolException.serverError(WRITE_FAILED);
        }

        int activeTokenCount = countTokensWithStatus(tokens, STATUS_ACTIVE);
        if (activeTokenCount != tokens.size()
                || mapper.revokeActiveFamilyTokensAtLogicalTime(
                        pendingFamily.getFamilyId(), Date.from(now))
                        != activeTokenCount) {
            throw BoardOAuthProtocolException.conflict(CONFLICT);
        }
        if (mapper.revokeTokenFamilyIfVersion(
                pendingFamily.getFamilyId(),
                pendingFamily.getClientId(),
                pendingFamily.getVersion(),
                Date.from(now)) != 1) {
            throw BoardOAuthProtocolException.conflict(CONFLICT);
        }
        BoardOAuthTokenFamily revokedFamily = mapper.selectTokenFamilyForUpdate(
                pendingFamily.getFamilyId(), pendingFamily.getClientId());
        List<BoardOAuthToken> revokedTokens =
                mapper.selectFamilyTokensForUpdate(pendingFamily.getFamilyId());
        requireRevokedPendingCurrentRead(
                pendingFamily, tokens, revokedFamily, revokedTokens, now);

        String receiptId = requireOpaqueIdentifier(
                materialGenerator.generateReceiptId());
        if (generated.collidesWith(receiptId)
                || receiptId.equals(creationReceipt.getReceiptId())) {
            throw BoardOAuthProtocolException.serverError(WRITE_FAILED);
        }
        BoardOAuthReceipt receipt =
                BoardOAuthTokenSecurityReceiptFactory.tokenFamilyRevoked(
                        receiptId,
                        generated.correlationId(),
                        now,
                        revokedFamily,
                        revokedTokens,
                        creationReceipt,
                        request,
                        code,
                        generated.familyId(),
                        generated.receiptId());
        BoardOAuthReceipt persisted = insertAndCurrentReadSecurityReceipt(receipt);
        BoardOAuthTokenSecurityReceiptFactory.validateTokenFamilyRevoked(
                persisted,
                revokedFamily,
                revokedTokens,
                creationReceipt,
                request,
                code,
                generated.familyId(),
                generated.receiptId());
        return new SupersessionEvidence(
                persisted,
                revokedFamily,
                List.copyOf(revokedTokens),
                creationReceipt);
    }

    private static void validateSupersessionPair(
            SupersessionEvidence supersession,
            BoardOAuthReceipt successorCreationReceipt,
            BoardOAuthAuthorizationRequest successorRequest,
            BoardOAuthAuthorizationCode successorCode,
            GeneratedMaterial generated) {
        if (supersession == null) {
            return;
        }
        BoardOAuthReceipt revokedReceipt = supersession.revokedReceipt;
        if (revokedReceipt == null
                || successorCreationReceipt == null
                || Objects.equals(
                        revokedReceipt.getReceiptId(),
                        successorCreationReceipt.getReceiptId())
                || !Objects.equals(
                        successorCreationReceipt.getReceiptId(),
                        generated.receiptId())
                || !Objects.equals(
                        successorCreationReceipt.getFamilyId(),
                        generated.familyId())
                || !Objects.equals(
                        revokedReceipt.getCorrelationId(),
                        successorCreationReceipt.getCorrelationId())
                || !Objects.equals(
                        successorCreationReceipt.getCorrelationId(),
                        generated.correlationId())
                || !Objects.equals(
                        revokedReceipt.getCreatedAt(),
                        successorCreationReceipt.getCreatedAt())
                || !sameDigest(
                        revokedReceipt.getActorSubjectDigest(),
                        successorCreationReceipt.getActorSubjectDigest())) {
            throw BoardOAuthProtocolException.serverError(
                    BoardOAuthTokenSecurityReceiptFactory.RECEIPT_INVALID);
        }
        BoardOAuthTokenSecurityReceiptFactory.validateTokenFamilyRevoked(
                revokedReceipt,
                supersession.revokedFamily,
                supersession.revokedTokens,
                supersession.originalCreationReceipt,
                successorRequest,
                successorCode,
                generated.familyId(),
                generated.receiptId());
    }

    private static void validateConsumedReplayLineage(
            BoardOAuthTokenExchangeCommand command,
            BoardOAuthAuthorizationRequest request,
            BoardOAuthAuthorizationCode code,
            IndependentBoardOAuthMapper.TokenExchangeLockLocator locator,
            BoardOAuthClient client,
            byte[] expectedCodeDigest,
            BoardOAuthTokenFamily family,
            BoardOAuthTokenFamily activeSlot,
            BoardOAuthTokenFamily pendingSlot,
            List<BoardOAuthToken> tokens,
            BoardOAuthReceipt creationReceipt,
            Instant now) {
        boolean requestTemporal = request.getRequestedAt() != null
                && request.getExpiresAt() != null
                && request.getApprovedAt() != null
                && request.getConsumedAt() != null
                && request.getRequestedAt().toInstant().plus(REQUEST_LIFETIME)
                        .equals(request.getExpiresAt().toInstant())
                && !request.getApprovedAt().before(request.getRequestedAt())
                && request.getApprovedAt().before(request.getExpiresAt())
                && !request.getConsumedAt().before(request.getApprovedAt())
                && request.getConsumedAt().before(request.getExpiresAt())
                && !request.getConsumedAt().toInstant().isAfter(now);
        boolean requestAuditTemporal = hasOrderedRowTimes(
                        request.getCreatedAt(), request.getUpdatedAt())
                && isWithinDatabaseClockSkew(
                        request.getCreatedAt(),
                        request.getRequestedAt() == null
                                ? null : request.getRequestedAt().toInstant())
                && isWithinDatabaseClockSkew(
                        request.getUpdatedAt(),
                        request.getConsumedAt() == null
                                ? null : request.getConsumedAt().toInstant());
        boolean codeTemporal = code.getIssuedAt() != null
                && code.getExpiresAt() != null
                && code.getUsedAt() != null
                && code.getIssuedAt().toInstant().plus(CODE_LIFETIME)
                        .equals(code.getExpiresAt().toInstant())
                && code.getIssuedAt().equals(request.getApprovedAt())
                && code.getUsedAt().equals(request.getConsumedAt())
                && !code.getUsedAt().before(code.getIssuedAt())
                && code.getUsedAt().before(code.getExpiresAt())
                && !code.getUsedAt().toInstant().isAfter(now);
        boolean codeAuditTemporal = hasOrderedRowTimes(
                        code.getCreatedAt(), code.getUpdatedAt())
                && isWithinDatabaseClockSkew(
                        code.getCreatedAt(),
                        code.getIssuedAt() == null
                                ? null : code.getIssuedAt().toInstant())
                && isWithinDatabaseClockSkew(
                        code.getUpdatedAt(),
                        code.getUsedAt() == null
                                ? null : code.getUsedAt().toInstant());
        if (!requestTemporal
                || !requestAuditTemporal
                || !codeTemporal
                || !codeAuditTemporal
                || !isPositive(request.getId())
                || !hasDigest(request.getRequestHandleDigest())
                || !Objects.equals(request.getClientId(), client.getClientId())
                || !Objects.equals(request.getRedirectUri(), client.getRedirectUri())
                || !Objects.equals(request.getRedirectUri(), command.redirectUri())
                || !BoardOAuthProfile.isAllowedLoopbackRedirect(
                        request.getRedirectUri())
                || !BoardOAuthCrypto.isValidPkceS256Challenge(
                        request.getCodeChallenge())
                || !BoardOAuthProfile.PKCE_METHOD.equals(
                        request.getCodeChallengeMethod())
                || !hasDigest(request.getStateDigest())
                || !BoardOAuthCrypto.matchesPkceS256(
                        command.pkceVerifier(), code.getCodeChallenge())
                || !STATUS_CONSUMED.equals(request.getStatus())
                || !Objects.equals(request.getVersion(), 2L)
                || request.getStateKeyRef() != null
                || request.getStateNonce() != null
                || request.getStateCiphertext() != null
                || request.getDeniedAt() != null
                || request.getConsentIntent() == null
                || !hasIdentity(
                        request.getTenantId(),
                        request.getMemberId(),
                        request.getUserId(),
                        request.getPrincipalSubjectDigest())
                || !Objects.equals(code.getId(), locator.getCodeId())
                || !sameDigest(code.getCodeDigest(), expectedCodeDigest)
                || !Objects.equals(code.getAuthorizationRequestId(), request.getId())
                || !Objects.equals(code.getClientId(), request.getClientId())
                || !Objects.equals(code.getRedirectUri(), request.getRedirectUri())
                || !BoardOAuthCrypto.isValidPkceS256Challenge(
                        code.getCodeChallenge())
                || !Objects.equals(
                        code.getCodeChallenge(), request.getCodeChallenge())
                || !Objects.equals(
                        code.getCodeChallengeMethod(),
                        request.getCodeChallengeMethod())
                || !BoardOAuthProfile.PKCE_METHOD.equals(
                        code.getCodeChallengeMethod())
                || !STATUS_USED.equals(code.getStatus())
                || !Objects.equals(code.getVersion(), 1L)
                || code.getRevokedAt() != null
                || !hasIdentity(
                        code.getTenantId(),
                        code.getMemberId(),
                        code.getUserId(),
                        code.getPrincipalSubjectDigest())
                || code.getConsentIntent() == null
                || !Objects.equals(code.getConsentIntent(), request.getConsentIntent())
                || !sameIdentity(request, code)
                || !hasFixedProfile(
                        request.getIssuerUri(), request.getResourceUri(),
                        request.getProductCode(), request.getSourceCode(),
                        request.getConnectorCode(), request.getScopeCanonical(),
                        request.getScopeDigest())
                || !hasFixedProfile(
                        code.getIssuerUri(), code.getResourceUri(),
                        code.getProductCode(), code.getSourceCode(),
                        code.getConnectorCode(), code.getScopeCanonical(),
                        code.getScopeDigest())
                || !familyMatchesLineage(family, code)
                || tokens == null
                || tokens.size() < 2
                || creationReceipt == null
                || now.isBefore(code.getUsedAt().toInstant())) {
            throw BoardOAuthProtocolException.serverError(REPLAY_LINEAGE_DRIFT);
        }
        if (STATUS_ACTIVE.equals(family.getStatus())
                && !sameFamily(activeSlot, family)) {
            throw BoardOAuthProtocolException.serverError(REPLAY_LINEAGE_DRIFT);
        }
        if (STATUS_PENDING_BINDING.equals(family.getStatus())
                && !sameFamily(pendingSlot, family)) {
            throw BoardOAuthProtocolException.serverError(REPLAY_LINEAGE_DRIFT);
        }
        requireReplayTokenBeforeImage(family, tokens, now);
    }

    private static void requireSupersededPendingBeforeImage(
            BoardOAuthTokenFamily family,
            List<BoardOAuthToken> tokens,
            BoardOAuthAuthorizationRequest request,
            BoardOAuthAuthorizationCode code,
            Instant now) {
        if (family == null
                || tokens == null
                || tokens.size() != 2
                || !STATUS_PENDING_BINDING.equals(family.getStatus())
                || family.getBindingId() != null
                || family.getBindingVersion() != null
                || family.getActivatedAt() != null
                || family.getTerminatedAt() != null
                || !Objects.equals(family.getVersion(), 0L)
                || !Objects.equals(family.getCurrentRefreshGeneration(), 0L)
                || Objects.equals(
                        family.getOriginAuthorizationCodeId(), code.getId())
                || !Objects.equals(family.getTenantId(), request.getTenantId())
                || !Objects.equals(family.getMemberId(), request.getMemberId())
                || !Objects.equals(family.getUserId(), request.getUserId())
                || !sameDigest(
                        family.getPrincipalSubjectDigest(),
                        request.getPrincipalSubjectDigest())
                || !Objects.equals(
                        family.getConsentIntent(), request.getConsentIntent())
                || !hasFixedProfile(
                        family.getIssuerUri(), family.getResourceUri(),
                        family.getProductCode(), family.getSourceCode(),
                        family.getConnectorCode(), family.getScopeCanonical(),
                        family.getScopeDigest())
                || family.getIssuedAt() == null
                || family.getIssuedAt().toInstant().isAfter(now)
                || family.getExpiresAt() == null
                || !family.getExpiresAt().after(family.getIssuedAt())) {
            throw BoardOAuthProtocolException.conflict(PENDING_FAMILY_DRIFT);
        }
        boolean access = false;
        boolean refresh = false;
        for (BoardOAuthToken token : tokens) {
            requirePendingTokenBeforeImage(token, family, now);
            access |= TOKEN_ACCESS.equals(token.getTokenType());
            refresh |= TOKEN_REFRESH.equals(token.getTokenType());
        }
        if (!access || !refresh) {
            throw BoardOAuthProtocolException.conflict(PENDING_FAMILY_DRIFT);
        }
    }

    private static void requirePendingTokenBeforeImage(
            BoardOAuthToken token,
            BoardOAuthTokenFamily family,
            Instant now) {
        if (token == null
                || !isPositive(token.getId())
                || !hasDigest(token.getTokenDigest())
                || !Objects.equals(token.getFamilyId(), family.getFamilyId())
                || !(TOKEN_ACCESS.equals(token.getTokenType())
                        || TOKEN_REFRESH.equals(token.getTokenType()))
                || !Objects.equals(token.getGeneration(), 0L)
                || !BoardOAuthProfile.RESOURCE.equals(token.getResourceUri())
                || !BoardOAuthProfile.CANONICAL_SCOPE.equals(
                        token.getScopeCanonical())
                || !sameDigest(token.getScopeDigest(), family.getScopeDigest())
                || !STATUS_ACTIVE.equals(token.getStatus())
                || token.getUsedAt() != null
                || token.getRevokedAt() != null
                || !Objects.equals(token.getVersion(), 0L)
                || token.getIssuedAt() == null
                || token.getIssuedAt().toInstant().isAfter(now)
                || token.getExpiresAt() == null) {
            throw BoardOAuthProtocolException.conflict(PENDING_FAMILY_DRIFT);
        }
    }

    private static void requireReplayTokenBeforeImage(
            BoardOAuthTokenFamily family,
            List<BoardOAuthToken> tokens,
            Instant now) {
        int activeCount = 0;
        for (BoardOAuthToken token : tokens) {
            if (token == null
                    || !isPositive(token.getId())
                    || !hasDigest(token.getTokenDigest())
                    || !Objects.equals(token.getFamilyId(), family.getFamilyId())
                    || !(TOKEN_ACCESS.equals(token.getTokenType())
                            || TOKEN_REFRESH.equals(token.getTokenType()))
                    || token.getGeneration() == null
                    || token.getGeneration() < 0L
                    || !BoardOAuthProfile.RESOURCE.equals(token.getResourceUri())
                    || !BoardOAuthProfile.CANONICAL_SCOPE.equals(
                            token.getScopeCanonical())
                    || !sameDigest(token.getScopeDigest(), family.getScopeDigest())
                    || token.getIssuedAt() == null
                    || token.getIssuedAt().toInstant().isAfter(now)
                    || token.getExpiresAt() == null
                    || token.getVersion() == null
                    || token.getVersion() < 0L) {
                throw BoardOAuthProtocolException.serverError(REPLAY_LINEAGE_DRIFT);
            }
            if (STATUS_ACTIVE.equals(token.getStatus())) {
                activeCount++;
            } else if (!("USED".equals(token.getStatus())
                    || "REVOKED".equals(token.getStatus())
                    || "EXPIRED".equals(token.getStatus()))) {
                throw BoardOAuthProtocolException.serverError(REPLAY_LINEAGE_DRIFT);
            }
        }
        boolean live = STATUS_PENDING_BINDING.equals(family.getStatus())
                || STATUS_ACTIVE.equals(family.getStatus());
        if (STATUS_PENDING_BINDING.equals(family.getStatus())) {
            boolean access = false;
            boolean refresh = false;
            for (BoardOAuthToken token : tokens) {
                if (!Objects.equals(token.getGeneration(), 0L)
                        || !STATUS_ACTIVE.equals(token.getStatus())) {
                    throw BoardOAuthProtocolException.serverError(
                            REPLAY_LINEAGE_DRIFT);
                }
                access |= TOKEN_ACCESS.equals(token.getTokenType());
                refresh |= TOKEN_REFRESH.equals(token.getTokenType());
            }
            if (tokens.size() != 2 || !access || !refresh) {
                throw BoardOAuthProtocolException.serverError(
                        REPLAY_LINEAGE_DRIFT);
            }
        }
        if (live != (activeCount > 0)) {
            throw BoardOAuthProtocolException.serverError(REPLAY_LINEAGE_DRIFT);
        }
    }

    private static boolean familyMatchesLineage(
            BoardOAuthTokenFamily family,
            BoardOAuthAuthorizationCode code) {
        if (family == null
                || !isPositive(family.getId())
                || !Objects.equals(
                        family.getOriginAuthorizationCodeId(), code.getId())
                || !Objects.equals(family.getClientId(), code.getClientId())
                || !Objects.equals(family.getTenantId(), code.getTenantId())
                || !Objects.equals(family.getMemberId(), code.getMemberId())
                || !Objects.equals(family.getUserId(), code.getUserId())
                || !sameDigest(
                        family.getPrincipalSubjectDigest(),
                        code.getPrincipalSubjectDigest())
                || !Objects.equals(family.getConsentIntent(), code.getConsentIntent())
                || !hasFixedProfile(
                        family.getIssuerUri(), family.getResourceUri(),
                        family.getProductCode(), family.getSourceCode(),
                        family.getConnectorCode(), family.getScopeCanonical(),
                        family.getScopeDigest())
                || family.getIssuedAt() == null
                || family.getExpiresAt() == null
                || !family.getExpiresAt().after(family.getIssuedAt())
                || family.getVersion() == null
                || family.getCurrentRefreshGeneration() == null) {
            return false;
        }
        if (STATUS_PENDING_BINDING.equals(family.getStatus())) {
            return family.getBindingId() == null
                    && family.getBindingVersion() == null
                    && family.getActivatedAt() == null
                    && family.getTerminatedAt() == null
                    && Objects.equals(family.getVersion(), 0L);
        }
        if (STATUS_ACTIVE.equals(family.getStatus())) {
            return family.getBindingId() != null
                    && family.getBindingVersion() != null
                    && family.getActivatedAt() != null
                    && family.getTerminatedAt() == null
                    && family.getVersion() > 0L;
        }
        return ("REVOKED".equals(family.getStatus())
                        || "COMPROMISED".equals(family.getStatus())
                        || "EXPIRED".equals(family.getStatus()))
                && family.getTerminatedAt() != null
                && family.getVersion() > 0L;
    }

    private static BoardOAuthReceipt requireSingleCreationReceipt(
            List<BoardOAuthReceipt> candidates,
            BoardOAuthTokenFamily family,
            Long authorizationCodeId) {
        if (candidates == null || candidates.size() != 1) {
            throw BoardOAuthProtocolException.serverError(
                    RECEIPT_CARDINALITY_INVALID);
        }
        BoardOAuthReceipt receipt = candidates.get(0);
        if (receipt == null
                || !isPositive(receipt.getId())
                || receipt.getReceiptId() == null
                || receipt.getReceiptId().isBlank()
                || !"TOKEN_FAMILY_CREATED".equals(receipt.getAction())
                || !Objects.equals(receipt.getClientId(), family.getClientId())
                || receipt.getAuthorizationRequestId() != null
                || !Objects.equals(
                        receipt.getAuthorizationCodeId(), authorizationCodeId)
                || !Objects.equals(receipt.getFamilyId(), family.getFamilyId())
                || receipt.getTokenId() != null
                || receipt.getBindingId() != null
                || !Objects.equals(receipt.getTenantId(), family.getTenantId())
                || !Objects.equals(receipt.getMemberId(), family.getMemberId())
                || !Objects.equals(receipt.getUserId(), family.getUserId())
                || !sameDigest(
                        receipt.getPrincipalSubjectDigest(),
                        family.getPrincipalSubjectDigest())
                || !"CLIENT".equals(receipt.getActorType())
                || receipt.getActorUserId() != null
                || !sameDigest(
                        receipt.getActorSubjectDigest(),
                        BoardOAuthCrypto.sha256Ascii(family.getClientId()))
                || receipt.getCorrelationId() == null
                || receipt.getCorrelationId().isBlank()
                || !hasDigest(receipt.getPayloadDigest())
                || !"ACTION_COMPLETED".equals(receipt.getEvidenceLevel())
                || receipt.getCreatedAt() == null
                || family.getIssuedAt() == null
                || !receipt.getCreatedAt().equals(family.getIssuedAt())) {
            throw BoardOAuthProtocolException.serverError(
                    RECEIPT_CARDINALITY_INVALID);
        }
        return receipt;
    }

    private static void requireAtMostOneSecurityReceipt(
            List<BoardOAuthReceipt> candidates) {
        if (candidates == null
                || candidates.size() > 1
                || (candidates.size() == 1 && candidates.get(0) == null)) {
            throw BoardOAuthProtocolException.serverError(
                    SECURITY_RECEIPT_CARDINALITY_INVALID);
        }
    }

    private static void requireTerminalCompromiseReceipt(
            List<BoardOAuthReceipt> candidates,
            BoardOAuthAuthorizationRequest request,
            BoardOAuthAuthorizationCode code,
            BoardOAuthTokenFamily family,
            List<BoardOAuthToken> tokens,
            BoardOAuthReceipt creationReceipt) {
        if ("COMPROMISED".equals(family.getStatus())) {
            // A COMPROMISED row without its canonical terminalization receipt is
            // an evidence gap, so it cannot be promoted into a new replay fact.
            if (candidates == null
                    || candidates.size() != 1
                    || candidates.get(0) == null) {
                throw BoardOAuthProtocolException.serverError(
                        SECURITY_RECEIPT_CARDINALITY_INVALID);
            }
            BoardOAuthTokenSecurityReceiptFactory.validateTokenFamilyCompromised(
                    candidates.get(0),
                    request,
                    code,
                    family,
                    tokens,
                    creationReceipt);
            return;
        }
        // REVOKED/EXPIRED families are already safely terminal. They may receive
        // a first replay receipt, but must not carry false compromise evidence.
        if (candidates == null || !candidates.isEmpty()) {
            throw BoardOAuthProtocolException.serverError(
                    SECURITY_RECEIPT_CARDINALITY_INVALID);
        }
    }

    private BoardOAuthReceipt insertAndCurrentReadSecurityReceipt(
            BoardOAuthReceipt receipt) {
        if (mapper.insertReceipt(receipt) != 1) {
            throw BoardOAuthProtocolException.serverError(RECEIPT_WRITE_FAILED);
        }
        BoardOAuthReceipt persisted =
                mapper.selectReceiptByReceiptId(receipt.getReceiptId());
        if (persisted == null) {
            throw BoardOAuthProtocolException.serverError(RECEIPT_WRITE_FAILED);
        }
        return persisted;
    }

    private static void requireCompromisedCurrentRead(
            BoardOAuthTokenFamily before,
            List<BoardOAuthToken> beforeTokens,
            BoardOAuthTokenFamily current,
            List<BoardOAuthToken> tokens,
            Instant eventAt) {
        if (current == null
                || !Objects.equals(current.getFamilyId(), before.getFamilyId())
                || !sameFamilyImmutable(before, current)
                || !"COMPROMISED".equals(current.getStatus())
                || current.getTerminatedAt() == null
                || !current.getTerminatedAt().equals(Date.from(eventAt))
                || before.getVersion() == null
                || !Objects.equals(current.getVersion(), before.getVersion() + 1L)) {
            throw BoardOAuthProtocolException.serverError(REPLAY_LINEAGE_DRIFT);
        }
        requireTerminalTokenCurrentRead(
                beforeTokens, tokens, eventAt, REPLAY_LINEAGE_DRIFT);
    }

    private static void requireRevokedPendingCurrentRead(
            BoardOAuthTokenFamily before,
            List<BoardOAuthToken> beforeTokens,
            BoardOAuthTokenFamily current,
            List<BoardOAuthToken> tokens,
            Instant eventAt) {
        if (current == null
                || !Objects.equals(current.getFamilyId(), before.getFamilyId())
                || !sameFamilyImmutable(before, current)
                || !"REVOKED".equals(current.getStatus())
                || current.getTerminatedAt() == null
                || !current.getTerminatedAt().equals(Date.from(eventAt))
                || before.getVersion() == null
                || !Objects.equals(current.getVersion(), before.getVersion() + 1L)
                || current.getBindingId() != null
                || current.getBindingVersion() != null) {
            throw BoardOAuthProtocolException.serverError(PENDING_FAMILY_DRIFT);
        }
        requireTerminalTokenCurrentRead(
                beforeTokens, tokens, eventAt, PENDING_FAMILY_DRIFT);
    }

    private static void requireTerminalReplayCurrentRead(
            BoardOAuthTokenFamily family,
            List<BoardOAuthToken> tokens,
            Instant now) {
        if (!("REVOKED".equals(family.getStatus())
                || "COMPROMISED".equals(family.getStatus())
                || "EXPIRED".equals(family.getStatus()))
                || family.getTerminatedAt() == null
                || family.getTerminatedAt().toInstant().isAfter(now)) {
            throw BoardOAuthProtocolException.serverError(REPLAY_LINEAGE_DRIFT);
        }
        requireNoActiveTokens(tokens, REPLAY_LINEAGE_DRIFT);
    }

    private static void requireNoActiveTokens(
            List<BoardOAuthToken> tokens,
            String reasonCode) {
        if (tokens == null || tokens.size() < 2) {
            throw BoardOAuthProtocolException.serverError(reasonCode);
        }
        for (BoardOAuthToken token : tokens) {
            if (token == null || STATUS_ACTIVE.equals(token.getStatus())) {
                throw BoardOAuthProtocolException.serverError(reasonCode);
            }
        }
    }

    private static void requireTerminalTokenCurrentRead(
            List<BoardOAuthToken> before,
            List<BoardOAuthToken> current,
            Instant eventAt,
            String reasonCode) {
        if (before == null
                || current == null
                || before.size() != current.size()
                || current.size() < 2) {
            throw BoardOAuthProtocolException.serverError(reasonCode);
        }
        for (BoardOAuthToken prior : before) {
            BoardOAuthToken after = null;
            for (BoardOAuthToken candidate : current) {
                if (prior != null
                        && candidate != null
                        && Objects.equals(prior.getId(), candidate.getId())) {
                    if (after != null) {
                        throw BoardOAuthProtocolException.serverError(reasonCode);
                    }
                    after = candidate;
                }
            }
            if (prior == null
                    || after == null
                    || !sameDigest(prior.getTokenDigest(), after.getTokenDigest())
                    || !Objects.equals(prior.getFamilyId(), after.getFamilyId())
                    || !Objects.equals(prior.getTokenType(), after.getTokenType())
                    || !Objects.equals(prior.getGeneration(), after.getGeneration())
                    || !Objects.equals(prior.getResourceUri(), after.getResourceUri())
                    || !Objects.equals(
                            prior.getScopeCanonical(), after.getScopeCanonical())
                    || !sameDigest(prior.getScopeDigest(), after.getScopeDigest())
                    || !Objects.equals(prior.getIssuedAt(), after.getIssuedAt())
                    || !Objects.equals(prior.getExpiresAt(), after.getExpiresAt())
                    || !Objects.equals(prior.getCreatedAt(), after.getCreatedAt())) {
                throw BoardOAuthProtocolException.serverError(reasonCode);
            }
            if (!STATUS_ACTIVE.equals(prior.getStatus())) {
                if (!Objects.equals(prior.getStatus(), after.getStatus())
                        || !Objects.equals(prior.getUsedAt(), after.getUsedAt())
                        || !Objects.equals(prior.getRevokedAt(), after.getRevokedAt())
                        || !Objects.equals(prior.getVersion(), after.getVersion())) {
                    throw BoardOAuthProtocolException.serverError(reasonCode);
                }
                continue;
            }
            boolean expired = !prior.getExpiresAt().after(Date.from(eventAt));
            String expectedStatus = expired ? "EXPIRED" : "REVOKED";
            Date expectedRevokedAt = expired ? null : Date.from(eventAt);
            if (!expectedStatus.equals(after.getStatus())
                    || !Objects.equals(prior.getUsedAt(), after.getUsedAt())
                    || !Objects.equals(after.getRevokedAt(), expectedRevokedAt)
                    || prior.getVersion() == null
                    || Objects.equals(prior.getVersion(), Long.MAX_VALUE)
                    || !Objects.equals(after.getVersion(), prior.getVersion() + 1L)
                    || after.getActiveRefreshSlot() != null) {
                throw BoardOAuthProtocolException.serverError(reasonCode);
            }
        }
    }

    private static int countTokensWithStatus(
            List<BoardOAuthToken> tokens,
            String status) {
        int count = 0;
        for (BoardOAuthToken token : tokens) {
            if (token != null && status.equals(token.getStatus())) {
                count++;
            }
        }
        return count;
    }

    private static boolean sameFamily(
            BoardOAuthTokenFamily left,
            BoardOAuthTokenFamily right) {
        return left != null
                && right != null
                && Objects.equals(left.getId(), right.getId())
                && Objects.equals(left.getFamilyId(), right.getFamilyId());
    }

    private static boolean sameFamilyImmutable(
            BoardOAuthTokenFamily before,
            BoardOAuthTokenFamily current) {
        return Objects.equals(before.getId(), current.getId())
                && Objects.equals(
                        before.getOriginAuthorizationCodeId(),
                        current.getOriginAuthorizationCodeId())
                && Objects.equals(before.getClientId(), current.getClientId())
                && Objects.equals(before.getTenantId(), current.getTenantId())
                && Objects.equals(before.getMemberId(), current.getMemberId())
                && Objects.equals(before.getUserId(), current.getUserId())
                && Objects.equals(before.getProductCode(), current.getProductCode())
                && Objects.equals(before.getSourceCode(), current.getSourceCode())
                && Objects.equals(before.getConnectorCode(), current.getConnectorCode())
                && Objects.equals(before.getIssuerUri(), current.getIssuerUri())
                && Objects.equals(before.getResourceUri(), current.getResourceUri())
                && Objects.equals(
                        before.getScopeCanonical(), current.getScopeCanonical())
                && sameDigest(before.getScopeDigest(), current.getScopeDigest())
                && sameDigest(
                        before.getPrincipalSubjectDigest(),
                        current.getPrincipalSubjectDigest())
                && Objects.equals(before.getConsentIntent(), current.getConsentIntent())
                && Objects.equals(before.getBindingId(), current.getBindingId())
                && Objects.equals(before.getBindingVersion(), current.getBindingVersion())
                && Objects.equals(
                        before.getCurrentRefreshGeneration(),
                        current.getCurrentRefreshGeneration())
                && Objects.equals(before.getIssuedAt(), current.getIssuedAt())
                && Objects.equals(before.getActivatedAt(), current.getActivatedAt())
                && Objects.equals(before.getExpiresAt(), current.getExpiresAt())
                && Objects.equals(before.getCreatedAt(), current.getCreatedAt());
    }

    private static String requireOpaqueIdentifier(String value) {
        if (value == null || !OPAQUE_256.matcher(value).matches()) {
            throw BoardOAuthProtocolException.serverError(WRITE_FAILED);
        }
        return value;
    }

    private static void validateCommand(BoardOAuthTokenExchangeCommand command) {
        if (command == null) {
            throw BoardOAuthProtocolException.invalidRequest(INPUT_INVALID);
        }
        if (!BoardOAuthProfile.RESOURCE.equals(command.resource())) {
            throw BoardOAuthProtocolException.invalidTarget(RESOURCE_INVALID);
        }
        if (command.clientId() == null
                || !CLIENT_ID.matcher(command.clientId()).matches()) {
            throw BoardOAuthProtocolException.invalidClient(CLIENT_INVALID);
        }
        if (command.rawAuthorizationCode() == null
                || !OPAQUE_256.matcher(command.rawAuthorizationCode()).matches()
                || !BoardOAuthCrypto.isValidPkceVerifier(command.pkceVerifier())
                || !BoardOAuthProfile.isAllowedLoopbackRedirect(
                        command.redirectUri())) {
            throw BoardOAuthProtocolException.invalidGrant(CODE_INVALID);
        }
    }

    private static void validateClient(
            BoardOAuthClient client,
            BoardOAuthTokenExchangeCommand command,
            Instant now) {
        if (client == null
                || !isPositive(client.getId())
                || !Objects.equals(client.getClientId(), command.clientId())
                || !STATUS_ACTIVE.equals(client.getStatus())
                || client.getTerminatedAt() != null
                || client.getRegisteredAt() == null
                || client.getExpiresAt() == null
                || client.getCreatedAt() == null
                || client.getUpdatedAt() == null
                || client.getRegisteredAt().toInstant().isAfter(now)
                || client.getCreatedAt().toInstant().isAfter(
                        client.getUpdatedAt().toInstant())
                || client.getCreatedAt().toInstant().isAfter(now)
                || client.getUpdatedAt().toInstant().isAfter(now)
                || client.getExpiresAt().toInstant().isBefore(
                        now.plus(ACCESS_TOKEN_LIFETIME))
                || !Objects.equals(client.getVersion(), 0L)) {
            throw BoardOAuthProtocolException.invalidClient(CLIENT_INVALID);
        }

        byte[] expectedScope = BoardOAuthCrypto.sha256Ascii(
                BoardOAuthProfile.CANONICAL_SCOPE);
        int redirectPort;
        try {
            redirectPort = BoardOAuthProfile.requireLoopbackPort(
                    client.getRedirectUri());
        } catch (IllegalArgumentException invalid) {
            throw BoardOAuthProtocolException.serverError(PROFILE_DRIFT);
        }
        if (!NEUTRAL_CLIENT_NAME.equals(client.getClientName())
                || !BoardOAuthProfile.ISSUER.equals(client.getIssuerUri())
                || !BoardOAuthProfile.RESOURCE.equals(client.getResourceUri())
                || !PRODUCT_CODE.equals(client.getProductCode())
                || !SOURCE_CODE.equals(client.getSourceCode())
                || !CONNECTOR_CODE.equals(client.getConnectorCode())
                || !Objects.equals(client.getRedirectPort(), redirectPort)
                || !TOKEN_ENDPOINT_AUTH_METHOD.equals(
                        client.getTokenEndpointAuthMethod())
                || !GRANT_TYPES_CANONICAL.equals(client.getGrantTypesCanonical())
                || !RESPONSE_TYPES_CANONICAL.equals(
                        client.getResponseTypesCanonical())
                || !BoardOAuthProfile.CANONICAL_SCOPE.equals(
                        client.getScopeCanonical())
                || !sameDigest(client.getScopeDigest(), expectedScope)
                || !hasDigest(client.getMetadataDigest())
                || !hasDigest(client.getRegistrationSourceDigest())
                || !client.getExpiresAt().toInstant().equals(
                        client.getRegisteredAt().toInstant().plus(CLIENT_LIFETIME))) {
            throw BoardOAuthProtocolException.serverError(PROFILE_DRIFT);
        }
    }

    private static void validateLockedLineage(
            BoardOAuthAuthorizationRequest request,
            BoardOAuthAuthorizationCode code,
            IndependentBoardOAuthMapper.TokenExchangeLockLocator locator,
            BoardOAuthClient client,
            byte[] codeDigest,
            BoardOAuthTokenExchangeCommand command,
            Instant now) {
        if (request == null || code == null) {
            throw BoardOAuthProtocolException.invalidGrant(CODE_INVALID);
        }
        validateRequest(request, client, now);
        validateCode(code, request, locator, codeDigest, now);
        if (!Objects.equals(request.getRedirectUri(), command.redirectUri())
                || !BoardOAuthCrypto.matchesPkceS256(
                        command.pkceVerifier(), code.getCodeChallenge())) {
            throw BoardOAuthProtocolException.invalidGrant(CODE_INVALID);
        }
        if (request.getConsentIntent() == null || code.getConsentIntent() == null) {
            throw BoardOAuthProtocolException.serverError(CONSENT_INTENT_DRIFT);
        }
        if (!Objects.equals(request.getConsentIntent(), code.getConsentIntent())) {
            throw BoardOAuthProtocolException.serverError(CONSENT_INTENT_DRIFT);
        }
    }

    private static void validateRequest(
            BoardOAuthAuthorizationRequest request,
            BoardOAuthClient client,
            Instant now) {
        boolean temporal = request.getRequestedAt() != null
                && request.getExpiresAt() != null
                && request.getApprovedAt() != null
                && request.getRequestedAt().toInstant().plus(REQUEST_LIFETIME)
                        .equals(request.getExpiresAt().toInstant())
                && !request.getApprovedAt().toInstant().isBefore(
                        request.getRequestedAt().toInstant())
                && request.getApprovedAt().toInstant().isBefore(
                        request.getExpiresAt().toInstant())
                && !request.getApprovedAt().toInstant().isAfter(now)
                && now.isBefore(request.getExpiresAt().toInstant());
        if (!isPositive(request.getId())
                || !hasDigest(request.getRequestHandleDigest())
                || !Objects.equals(request.getClientId(), client.getClientId())
                || !Objects.equals(request.getRedirectUri(), client.getRedirectUri())
                || !BoardOAuthProfile.isAllowedLoopbackRedirect(
                        request.getRedirectUri())
                || !BoardOAuthCrypto.isValidPkceS256Challenge(
                        request.getCodeChallenge())
                || !BoardOAuthProfile.PKCE_METHOD.equals(
                        request.getCodeChallengeMethod())
                || !hasDigest(request.getStateDigest())
                || request.getStateKeyRef() == null
                || request.getStateNonce() == null
                || request.getStateNonce().length != 12
                || request.getStateCiphertext() == null
                || request.getStateCiphertext().length < 32
                || request.getStateCiphertext().length > 528
                || !hasFixedProfile(
                        request.getIssuerUri(), request.getResourceUri(),
                        request.getProductCode(), request.getSourceCode(),
                        request.getConnectorCode(), request.getScopeCanonical(),
                        request.getScopeDigest())
                || !hasIdentity(request.getTenantId(), request.getMemberId(),
                        request.getUserId(), request.getPrincipalSubjectDigest())
                || !STATUS_APPROVED.equals(request.getStatus())
                || !temporal
                || request.getDeniedAt() != null
                || request.getConsumedAt() != null
                || !Objects.equals(request.getVersion(), 1L)
                || request.getCreatedAt() == null
                || request.getUpdatedAt() == null) {
            throw BoardOAuthProtocolException.serverError(LINEAGE_DRIFT);
        }
    }

    private static void validateCode(
            BoardOAuthAuthorizationCode code,
            BoardOAuthAuthorizationRequest request,
            IndependentBoardOAuthMapper.TokenExchangeLockLocator locator,
            byte[] expectedDigest,
            Instant now) {
        boolean temporal = code.getIssuedAt() != null
                && code.getExpiresAt() != null
                && code.getIssuedAt().toInstant().plus(CODE_LIFETIME)
                        .equals(code.getExpiresAt().toInstant())
                && code.getIssuedAt().toInstant().equals(
                        request.getApprovedAt().toInstant())
                && !code.getIssuedAt().toInstant().isAfter(now)
                && now.isBefore(code.getExpiresAt().toInstant());
        if (!Objects.equals(code.getId(), locator.getCodeId())
                || !sameDigest(code.getCodeDigest(), expectedDigest)
                || !Objects.equals(code.getAuthorizationRequestId(), request.getId())
                || !Objects.equals(code.getClientId(), request.getClientId())
                || !Objects.equals(code.getRedirectUri(), request.getRedirectUri())
                || !Objects.equals(code.getCodeChallenge(),
                        request.getCodeChallenge())
                || !Objects.equals(code.getCodeChallengeMethod(),
                        request.getCodeChallengeMethod())
                || !hasFixedProfile(
                        code.getIssuerUri(), code.getResourceUri(),
                        code.getProductCode(), code.getSourceCode(),
                        code.getConnectorCode(), code.getScopeCanonical(),
                        code.getScopeDigest())
                || !sameIdentity(request, code)
                || !STATUS_ACTIVE.equals(code.getStatus())
                || !temporal
                || code.getUsedAt() != null
                || code.getRevokedAt() != null
                || !Objects.equals(code.getVersion(), 0L)
                || code.getCreatedAt() == null
                || code.getUpdatedAt() == null) {
            throw BoardOAuthProtocolException.invalidGrant(CODE_INVALID);
        }
    }

    private GeneratedMaterial generateMaterial() {
        GeneratedMaterial generated = new GeneratedMaterial(
                materialGenerator.generateFamilyId(),
                materialGenerator.generateAccessToken(),
                materialGenerator.generateRefreshToken(),
                materialGenerator.generateReceiptId(),
                materialGenerator.generateCorrelationId());
        if (!generated.hasValidShape()) {
            throw BoardOAuthProtocolException.serverError(WRITE_FAILED);
        }
        return generated;
    }

    private static BoardOAuthTokenFamily newFamily(
            String familyId,
            BoardOAuthAuthorizationCode code,
            Instant issuedAt,
            Instant expiresAt) {
        BoardOAuthTokenFamily family = new BoardOAuthTokenFamily();
        family.setFamilyId(familyId);
        family.setOriginAuthorizationCodeId(code.getId());
        family.setClientId(code.getClientId());
        family.setTenantId(code.getTenantId());
        family.setMemberId(code.getMemberId());
        family.setUserId(code.getUserId());
        family.setProductCode(code.getProductCode());
        family.setSourceCode(code.getSourceCode());
        family.setConnectorCode(code.getConnectorCode());
        family.setIssuerUri(code.getIssuerUri());
        family.setResourceUri(code.getResourceUri());
        family.setScopeCanonical(code.getScopeCanonical());
        family.setScopeDigest(code.getScopeDigest().clone());
        family.setPrincipalSubjectDigest(code.getPrincipalSubjectDigest().clone());
        family.setConsentIntent(code.getConsentIntent());
        family.setBindingId(null);
        family.setBindingVersion(null);
        family.setStatus(STATUS_PENDING_BINDING);
        family.setCurrentRefreshGeneration(0L);
        family.setIssuedAt(Date.from(issuedAt));
        family.setActivatedAt(null);
        family.setExpiresAt(Date.from(expiresAt));
        family.setTerminatedAt(null);
        family.setVersion(0L);
        return family;
    }

    private static BoardOAuthToken newToken(
            String rawToken,
            String familyId,
            String tokenType,
            Instant issuedAt,
            Instant expiresAt,
            BoardOAuthAuthorizationCode code) {
        BoardOAuthToken token = new BoardOAuthToken();
        token.setTokenDigest(BoardOAuthCrypto.sha256Ascii(rawToken));
        token.setFamilyId(familyId);
        token.setTokenType(tokenType);
        token.setGeneration(0L);
        token.setResourceUri(code.getResourceUri());
        token.setScopeCanonical(code.getScopeCanonical());
        token.setScopeDigest(code.getScopeDigest().clone());
        token.setStatus(STATUS_ACTIVE);
        token.setIssuedAt(Date.from(issuedAt));
        token.setUsedAt(null);
        token.setRevokedAt(null);
        token.setExpiresAt(Date.from(expiresAt));
        token.setVersion(0L);
        return token;
    }

    private void insertFamily(BoardOAuthTokenFamily family) {
        if (mapper.insertTokenFamily(family) != 1 || !isPositive(family.getId())) {
            throw BoardOAuthProtocolException.serverError(WRITE_FAILED);
        }
    }

    private void insertToken(BoardOAuthToken token) {
        if (mapper.insertToken(token) != 1 || !isPositive(token.getId())) {
            throw BoardOAuthProtocolException.serverError(WRITE_FAILED);
        }
    }

    private static void requireNoCreationReceipt(
            List<BoardOAuthReceipt> candidates) {
        if (candidates == null || !candidates.isEmpty()) {
            throw BoardOAuthProtocolException.serverError(
                    RECEIPT_CARDINALITY_INVALID);
        }
    }

    private static void validateCurrentRows(
            BoardOAuthAuthorizationRequest consumedRequest,
            BoardOAuthAuthorizationCode usedCode,
            BoardOAuthTokenFamily family,
            List<BoardOAuthToken> tokens,
            BoardOAuthAuthorizationRequest lockedRequest,
            BoardOAuthAuthorizationCode lockedCode,
            GeneratedMaterial generated,
            Instant eventAt,
            Instant familyExpiresAt,
            Instant accessExpiresAt) {
        if (consumedRequest == null
                || usedCode == null
                || family == null
                || tokens == null
                || !Objects.equals(consumedRequest.getId(), lockedRequest.getId())
                || !STATUS_CONSUMED.equals(consumedRequest.getStatus())
                || !Objects.equals(consumedRequest.getVersion(), 2L)
                || consumedRequest.getConsumedAt() == null
                || !consumedRequest.getConsumedAt().toInstant().equals(eventAt)
                || consumedRequest.getStateKeyRef() != null
                || consumedRequest.getStateNonce() != null
                || consumedRequest.getStateCiphertext() != null
                || !Objects.equals(usedCode.getId(), lockedCode.getId())
                || !STATUS_USED.equals(usedCode.getStatus())
                || !Objects.equals(usedCode.getVersion(), 1L)
                || usedCode.getUsedAt() == null
                || !usedCode.getUsedAt().toInstant().equals(eventAt)
                || !Objects.equals(family.getFamilyId(), generated.familyId())
                || !Objects.equals(family.getExpiresAt(), Date.from(familyExpiresAt))
                || !Objects.equals(family.getConsentIntent(),
                        usedCode.getConsentIntent())
                || tokens.size() != 2) {
            throw BoardOAuthProtocolException.serverError(LINEAGE_DRIFT);
        }

        byte[] accessDigest = BoardOAuthCrypto.sha256Ascii(
                generated.rawAccessToken());
        byte[] refreshDigest = BoardOAuthCrypto.sha256Ascii(
                generated.rawRefreshToken());
        boolean accessFound = false;
        boolean refreshFound = false;
        for (BoardOAuthToken token : tokens) {
            if (token != null
                    && TOKEN_ACCESS.equals(token.getTokenType())
                    && sameDigest(token.getTokenDigest(), accessDigest)
                    && Objects.equals(token.getExpiresAt(), Date.from(accessExpiresAt))) {
                accessFound = true;
            }
            if (token != null
                    && TOKEN_REFRESH.equals(token.getTokenType())
                    && sameDigest(token.getTokenDigest(), refreshDigest)
                    && Objects.equals(token.getExpiresAt(), Date.from(familyExpiresAt))) {
                refreshFound = true;
            }
        }
        if (!accessFound || !refreshFound) {
            throw BoardOAuthProtocolException.serverError(LINEAGE_DRIFT);
        }
    }

    private Instant millisecondNow() {
        return Instant.ofEpochMilli(clock.instant().toEpochMilli());
    }

    private Instant decisionAtNotBefore(Instant notBefore) {
        Instant decisionAt = millisecondNow();
        return decisionAt.isBefore(notBefore) ? notBefore : decisionAt;
    }

    private static Instant earlier(Instant left, Instant right) {
        return left.isBefore(right) ? left : right;
    }

    private static boolean hasFixedProfile(
            String issuerUri,
            String resourceUri,
            String productCode,
            String sourceCode,
            String connectorCode,
            String scopeCanonical,
            byte[] scopeDigest) {
        return BoardOAuthProfile.ISSUER.equals(issuerUri)
                && BoardOAuthProfile.RESOURCE.equals(resourceUri)
                && PRODUCT_CODE.equals(productCode)
                && SOURCE_CODE.equals(sourceCode)
                && CONNECTOR_CODE.equals(connectorCode)
                && BoardOAuthProfile.CANONICAL_SCOPE.equals(scopeCanonical)
                && sameDigest(
                        scopeDigest,
                        BoardOAuthCrypto.sha256Ascii(
                                BoardOAuthProfile.CANONICAL_SCOPE));
    }

    private static boolean sameIdentity(
            BoardOAuthAuthorizationRequest request,
            BoardOAuthAuthorizationCode code) {
        return Objects.equals(request.getTenantId(), code.getTenantId())
                && Objects.equals(request.getMemberId(), code.getMemberId())
                && Objects.equals(request.getUserId(), code.getUserId())
                && sameDigest(request.getPrincipalSubjectDigest(),
                        code.getPrincipalSubjectDigest());
    }

    private static boolean hasIdentity(
            Long tenantId,
            Long memberId,
            Long userId,
            byte[] principalDigest) {
        if (!isPositive(tenantId)
                || !isPositive(memberId)
                || !isPositive(userId)
                || !hasDigest(principalDigest)) {
            return false;
        }
        try {
            return sameDigest(
                    principalDigest,
                    BoardOAuthPrincipalSubject.digest(
                            tenantId, memberId, userId));
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private static boolean isPositive(Long value) {
        return value != null && value > 0L;
    }

    private static boolean hasDigest(byte[] value) {
        return value != null && value.length == BoardOAuthCrypto.SHA256_BYTES;
    }

    private static boolean hasOrderedRowTimes(Date createdAt, Date updatedAt) {
        return createdAt != null
                && updatedAt != null
                && !updatedAt.toInstant().isBefore(createdAt.toInstant());
    }

    private static boolean isWithinDatabaseClockSkew(
            Date databaseTime,
            Instant semanticTime) {
        if (databaseTime == null || semanticTime == null) {
            return false;
        }
        return Duration.between(semanticTime, databaseTime.toInstant())
                .abs()
                .compareTo(MAX_DATABASE_CLOCK_SKEW) <= 0;
    }

    private static boolean sameDigest(byte[] left, byte[] right) {
        return hasDigest(left)
                && hasDigest(right)
                && BoardOAuthCrypto.constantTimeEquals(left, right);
    }

    private static final class GeneratedMaterial {
        private final String familyId;
        private final String rawAccessToken;
        private final String rawRefreshToken;
        private final String receiptId;
        private final String correlationId;

        private GeneratedMaterial(
                String familyId,
                String rawAccessToken,
                String rawRefreshToken,
                String receiptId,
                String correlationId) {
            this.familyId = familyId;
            this.rawAccessToken = rawAccessToken;
            this.rawRefreshToken = rawRefreshToken;
            this.receiptId = receiptId;
            this.correlationId = correlationId;
        }

        private boolean hasValidShape() {
            return isOpaque(familyId)
                    && isOpaque(rawAccessToken)
                    && isOpaque(rawRefreshToken)
                    && isOpaque(receiptId)
                    && isOpaque(correlationId)
                    && !familyId.equals(rawAccessToken)
                    && !familyId.equals(rawRefreshToken)
                    && !familyId.equals(receiptId)
                    && !familyId.equals(correlationId)
                    && !rawAccessToken.equals(rawRefreshToken)
                    && !rawAccessToken.equals(receiptId)
                    && !rawAccessToken.equals(correlationId)
                    && !rawRefreshToken.equals(receiptId)
                    && !rawRefreshToken.equals(correlationId)
                    && !receiptId.equals(correlationId);
        }

        private String familyId() {
            return familyId;
        }

        private String rawAccessToken() {
            return rawAccessToken;
        }

        private String rawRefreshToken() {
            return rawRefreshToken;
        }

        private String receiptId() {
            return receiptId;
        }

        private String correlationId() {
            return correlationId;
        }

        private boolean collidesWith(String value) {
            return familyId.equals(value)
                    || rawAccessToken.equals(value)
                    || rawRefreshToken.equals(value)
                    || receiptId.equals(value)
                    || correlationId.equals(value);
        }

        private static boolean isOpaque(String value) {
            return value != null && OPAQUE_256.matcher(value).matches();
        }

        @Override
        public String toString() {
            return "GeneratedMaterial[REDACTED]";
        }
    }

    private static final class SupersessionEvidence {
        private final BoardOAuthReceipt revokedReceipt;
        private final BoardOAuthTokenFamily revokedFamily;
        private final List<BoardOAuthToken> revokedTokens;
        private final BoardOAuthReceipt originalCreationReceipt;

        private SupersessionEvidence(
                BoardOAuthReceipt revokedReceipt,
                BoardOAuthTokenFamily revokedFamily,
                List<BoardOAuthToken> revokedTokens,
                BoardOAuthReceipt originalCreationReceipt) {
            this.revokedReceipt = revokedReceipt;
            this.revokedFamily = revokedFamily;
            this.revokedTokens = revokedTokens;
            this.originalCreationReceipt = originalCreationReceipt;
        }
    }

    private static final class PendingSupersessionBeforeImage {
        private final BoardOAuthTokenFamily pendingFamily;
        private final List<BoardOAuthToken> tokens;
        private final BoardOAuthReceipt creationReceipt;

        private PendingSupersessionBeforeImage(
                BoardOAuthTokenFamily pendingFamily,
                List<BoardOAuthToken> tokens,
                BoardOAuthReceipt creationReceipt) {
            this.pendingFamily = pendingFamily;
            this.tokens = tokens;
            this.creationReceipt = creationReceipt;
        }
    }
}
