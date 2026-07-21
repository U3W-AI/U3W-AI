package com.wx.fbsir.business.board.oauth.service;

import com.wx.fbsir.business.board.oauth.BoardOAuthCrypto;
import com.wx.fbsir.business.board.oauth.BoardOAuthPrincipalSubject;
import com.wx.fbsir.business.board.oauth.BoardOAuthProfile;
import com.wx.fbsir.business.board.oauth.BoardOAuthProtocolException;
import com.wx.fbsir.business.board.oauth.BoardOAuthRefreshReceiptFactory;
import com.wx.fbsir.business.board.oauth.BoardOAuthRefreshStateDigest;
import com.wx.fbsir.business.board.oauth.BoardOAuthTokenMaterialGenerator;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthClient;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthReceipt;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthToken;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthTokenFamily;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthRefreshCommand;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthRefreshResult;
import com.wx.fbsir.business.board.oauth.mapper.IndependentBoardOAuthMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/** Internal, transaction-bound refresh rotation and replay-containment service. */
@Service
class IndependentBoardOAuthRefreshService {
    static final String INPUT_INVALID = "OAUTH_REFRESH_INPUT_INVALID";
    static final String TOKEN_INVALID = "OAUTH_REFRESH_TOKEN_INVALID";
    static final String CLIENT_INVALID = "OAUTH_REFRESH_CLIENT_INVALID";
    static final String RESOURCE_INVALID = "OAUTH_REFRESH_RESOURCE_INVALID";
    static final String SCOPE_INVALID = "OAUTH_REFRESH_SCOPE_INVALID";
    static final String PROFILE_DRIFT = "OAUTH_REFRESH_PROFILE_DRIFT";
    static final String LINEAGE_DRIFT = "OAUTH_REFRESH_LINEAGE_DRIFT";
    static final String RECEIPT_DRIFT = "OAUTH_REFRESH_RECEIPT_DRIFT";
    static final String AUTHORITY_NOT_CURRENT = "OAUTH_REFRESH_AUTHORITY_NOT_CURRENT";
    static final String REPLAY_DETECTED = "OAUTH_REFRESH_REPLAY_DETECTED";
    static final String CONFLICT = "OAUTH_REFRESH_CONFLICT";
    static final String WRITE_FAILED = "OAUTH_REFRESH_WRITE_FAILED";
    static final String RECEIPT_WRITE_FAILED = "OAUTH_REFRESH_RECEIPT_WRITE_FAILED";
    static final String PERSISTENCE_UNAVAILABLE = "OAUTH_REFRESH_PERSISTENCE_UNAVAILABLE";
    static final String OUTCOME_INVALID = "OAUTH_REFRESH_OUTCOME_INVALID";

    static final Duration ACCESS_TOKEN_LIFETIME = Duration.ofMinutes(10);

    private static final String PRODUCT_CODE = "FBSIR_INDEPENDENT_BOARD";
    private static final String SOURCE_CODE = "WORKBUDDY";
    private static final String CONNECTOR_CODE = "fbs-connector";
    private static final String TOKEN_ENDPOINT_AUTH_METHOD = "none";
    private static final String GRANT_TYPES_CANONICAL =
            "authorization_code refresh_token";
    private static final String RESPONSE_TYPES_CANONICAL = "code";
    private static final String TOKEN_TYPE_BEARER = "Bearer";
    private static final String TOKEN_ACCESS = "ACCESS";
    private static final String TOKEN_REFRESH = "REFRESH";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_USED = "USED";
    private static final String STATUS_REVOKED = "REVOKED";
    private static final String STATUS_EXPIRED = "EXPIRED";
    private static final String STATUS_COMPROMISED = "COMPROMISED";
    private static final String TOKEN_FAMILY_CREATED = "TOKEN_FAMILY_CREATED";
    private static final long MAX_UNSIGNED_INT = 4_294_967_295L;
    private static final int MAX_LOCKED_TOKEN_ROWS = 10_000;
    private static final int TOKEN_ROW_SENTINEL_LIMIT = MAX_LOCKED_TOKEN_ROWS + 1;
    private static final int MAX_LOCKED_RECEIPT_ROWS = 6_000;
    private static final int RECEIPT_ROW_SENTINEL_LIMIT =
            MAX_LOCKED_RECEIPT_ROWS + 1;
    private static final Pattern OPAQUE_256 = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Pattern CLIENT_ID = Pattern.compile("[A-Za-z0-9_-]{43,191}");

    private final IndependentBoardOAuthMapper mapper;
    private final BoardOAuthRefreshAuthorityPort authorityPort;
    private final BoardOAuthTokenMaterialGenerator materialGenerator;
    private final Clock clock;

    @Autowired
    IndependentBoardOAuthRefreshService(
            IndependentBoardOAuthMapper mapper,
            BoardOAuthRefreshAuthorityPort authorityPort,
            BoardOAuthTokenMaterialGenerator materialGenerator) {
        this(mapper, authorityPort, materialGenerator, Clock.systemUTC());
    }

    IndependentBoardOAuthRefreshService(
            IndependentBoardOAuthMapper mapper,
            BoardOAuthRefreshAuthorityPort authorityPort,
            BoardOAuthTokenMaterialGenerator materialGenerator,
            Clock clock) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.authorityPort = Objects.requireNonNull(authorityPort, "authorityPort");
        this.materialGenerator = Objects.requireNonNull(
                materialGenerator, "materialGenerator");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    BoardOAuthRefreshOutcome refreshForCommit(BoardOAuthRefreshCommand command) {
        validateCommand(command);
        try {
            return refreshLocked(command);
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

    private BoardOAuthRefreshOutcome refreshLocked(BoardOAuthRefreshCommand command) {
        byte[] presentedDigest = BoardOAuthCrypto.sha256Ascii(
                command.rawRefreshToken());
        IndependentBoardOAuthMapper.TokenContextLocator locator =
                mapper.selectTokenContextLocatorByDigest(presentedDigest);
        requireLocator(locator, command);

        BoardOAuthRefreshAuthorityPort.LockResult authority =
                authorityPort.lockForRefresh(
                        locator.getTenantId(),
                        locator.getMemberId(),
                        locator.getUserId(),
                        locator.getProductCode(),
                        locator.getSourceCode(),
                        locator.getConnectorCode(),
                        millisecondNow());

        BoardOAuthClient client = mapper.selectClientForUpdate(command.clientId());
        // Shared slot order: ACTIVE before PENDING, then exact family.
        BoardOAuthTokenFamily activeSlot = mapper.selectActiveTokenFamilySlotForUpdate(
                locator.getTenantId(), locator.getMemberId(),
                locator.getProductCode(), locator.getSourceCode(),
                locator.getConnectorCode());
        BoardOAuthTokenFamily pendingSlot = mapper.selectPendingTokenFamilySlotForUpdate(
                locator.getTenantId(), locator.getMemberId(),
                locator.getProductCode(), locator.getSourceCode(),
                locator.getConnectorCode());
        BoardOAuthTokenFamily family = mapper.selectTokenFamilyForUpdate(
                locator.getFamilyId(), command.clientId());
        List<BoardOAuthToken> tokens = mapper.selectRefreshFamilyTokensForUpdate(
                locator.getFamilyId(), TOKEN_ROW_SENTINEL_LIMIT);
        // W4a receipt prefix is deliberately after tokens and before W4b.
        authorityPort.lockReceiptsForRefresh(authority.lease());
        // One ordered family-level query is the only W4b receipt lock operation.
        List<BoardOAuthReceipt> receipts =
                mapper.selectBoundedRefreshFamilySecurityReceiptsForUpdate(
                        locator.getFamilyId(), command.clientId(),
                        RECEIPT_ROW_SENTINEL_LIMIT);

        requireClientShape(client, command.clientId());
        requireLockedTopology(locator, family, activeSlot, pendingSlot, tokens, receipts);
        BoardOAuthToken source = requirePresentedToken(
                tokens, locator.getTokenId(), presentedDigest);

        List<BoardOAuthReceipt> replayReceipts = filterReceipts(
                receipts, BoardOAuthRefreshReceiptFactory.REFRESH_REPLAY_DETECTED);
        if (replayReceipts.size() > 1) {
            throw BoardOAuthProtocolException.serverError(RECEIPT_DRIFT);
        }
        if (!replayReceipts.isEmpty() || STATUS_COMPROMISED.equals(family.getStatus())) {
            return rejectStableContainedFamily(
                    family, tokens, receipts, replayReceipts, authority);
        }
        if (!STATUS_ACTIVE.equals(family.getStatus())) {
            return BoardOAuthRefreshOutcome.invalidGrantAfterCommit(TOKEN_INVALID);
        }

        Instant decisionAt = decisionAtNotBefore(authority.observedAt());
        if (STATUS_USED.equals(source.getStatus())) {
            return containReplay(
                    family, tokens, receipts, source, client, authority,
                    command.rawRefreshToken(), decisionAt);
        }
        if (!STATUS_ACTIVE.equals(source.getStatus())) {
            return BoardOAuthRefreshOutcome.invalidGrantAfterCommit(TOKEN_INVALID);
        }
        return rotate(
                family, tokens, receipts, source, client, activeSlot,
                authority, command, decisionAt);
    }

    private BoardOAuthRefreshOutcome rotate(
            BoardOAuthTokenFamily family,
            List<BoardOAuthToken> tokens,
            List<BoardOAuthReceipt> receipts,
            BoardOAuthToken source,
            BoardOAuthClient client,
            BoardOAuthTokenFamily activeSlot,
            BoardOAuthRefreshAuthorityPort.LockResult authority,
            BoardOAuthRefreshCommand command,
            Instant now) {
        validateFreshAuthority(
                family, tokens, source, client, activeSlot, authority, now);
        requireRotationCapacity(tokens, receipts);
        BoardOAuthReceipt cause = requireRotationCause(
                receipts, family, source.getGeneration());
        requireNoRotationForSource(receipts, source);
        byte[] beforeState = BoardOAuthRefreshStateDigest.digest(
                "BEFORE", family, tokens, authority, null);

        Long sourceVersion = source.getVersion();
        if (mapper.useRefreshTokenForGenerationIfVersion(
                source.getId(), family.getFamilyId(), sourceVersion,
                source.getGeneration(), Date.from(now)) != 1) {
            throw BoardOAuthProtocolException.conflict(CONFLICT);
        }

        GeneratedMaterial generated = generateMaterial(command, family, tokens);
        long nextGeneration = source.getGeneration() + 1L;
        Instant accessExpiresAt = now.plus(ACCESS_TOKEN_LIFETIME);
        Instant refreshExpiresAt = family.getExpiresAt().toInstant();
        BoardOAuthToken access = newToken(
                generated.rawAccessToken(), family, TOKEN_ACCESS,
                nextGeneration, now, accessExpiresAt);
        BoardOAuthToken refresh = newToken(
                generated.rawRefreshToken(), family, TOKEN_REFRESH,
                nextGeneration, now, refreshExpiresAt);
        insertToken(access);
        insertToken(refresh);
        if (mapper.advanceTokenFamilyGenerationAtIfVersion(
                family.getFamilyId(), family.getClientId(), family.getBindingId(),
                family.getBindingVersion(), family.getVersion(),
                family.getCurrentRefreshGeneration(), Date.from(now)) != 1) {
            throw BoardOAuthProtocolException.conflict(CONFLICT);
        }

        BoardOAuthTokenFamily currentFamily = mapper.selectTokenFamilyForUpdate(
                family.getFamilyId(), family.getClientId());
        List<BoardOAuthToken> currentTokens = mapper.selectRefreshFamilyTokensForUpdate(
                family.getFamilyId(), TOKEN_ROW_SENTINEL_LIMIT);
        BoardOAuthToken currentSource = findTokenById(currentTokens, source.getId());
        validateRotationCurrentRead(
                family, tokens, currentFamily, currentTokens, currentSource,
                access, refresh, now, accessExpiresAt);
        byte[] afterState = BoardOAuthRefreshStateDigest.digest(
                "AFTER", currentFamily, currentTokens, authority, null);

        BoardOAuthRefreshReceiptFactory.AuthoritySnapshot snapshot = authoritySnapshot(
                currentFamily, currentSource, beforeState, afterState);
        BoardOAuthRefreshReceiptFactory.CausationSnapshot causation =
                causationSnapshot(cause);
        BoardOAuthReceipt receipt = BoardOAuthRefreshReceiptFactory.createRotation(
                generated.receiptId(), generated.correlationId(), now,
                snapshot, causation);
        insertAndValidateReceipt(receipt, snapshot, causation, true);

        return BoardOAuthRefreshOutcome.completed(new BoardOAuthRefreshResult(
                generated.rawAccessToken(), generated.rawRefreshToken(),
                TOKEN_TYPE_BEARER, ACCESS_TOKEN_LIFETIME.toSeconds(),
                BoardOAuthProfile.CANONICAL_SCOPE, accessExpiresAt,
                refreshExpiresAt, generated.receiptId(),
                generated.correlationId()));
    }

    private BoardOAuthRefreshOutcome containReplay(
            BoardOAuthTokenFamily family,
            List<BoardOAuthToken> tokens,
            List<BoardOAuthReceipt> receipts,
            BoardOAuthToken source,
            BoardOAuthClient client,
            BoardOAuthRefreshAuthorityPort.LockResult authority,
            String presentedRawRefreshToken,
            Instant now) {
        requireReplayLineage(family, tokens, source, client, authority, now);
        requireReceiptCapacity(receipts);
        BoardOAuthReceipt cause = requireExactSourceRotation(
                receipts, family, source);
        if (!filterReceipts(
                receipts,
                BoardOAuthRefreshReceiptFactory.REFRESH_REPLAY_DETECTED).isEmpty()) {
            throw BoardOAuthProtocolException.serverError(RECEIPT_DRIFT);
        }
        byte[] beforeState = BoardOAuthRefreshStateDigest.digest(
                "BEFORE", family, tokens, authority, null);
        int activeCount = countActive(tokens);
        if (mapper.compromiseTokenFamilyIfVersion(
                family.getFamilyId(), family.getClientId(), family.getVersion(),
                Date.from(now)) != 1) {
            throw BoardOAuthProtocolException.conflict(CONFLICT);
        }
        if (mapper.revokeActiveFamilyTokensAtLogicalTime(
                family.getFamilyId(), Date.from(now)) != activeCount) {
            throw BoardOAuthProtocolException.conflict(CONFLICT);
        }
        BoardOAuthRefreshAuthorityPort.BindingRevocation bindingRevocation =
                authorityPort.revokeForRefreshReplay(
                        authority.lease(), family.getBindingId(),
                        family.getBindingVersion(), now);

        BoardOAuthTokenFamily currentFamily = mapper.selectTokenFamilyForUpdate(
                family.getFamilyId(), family.getClientId());
        List<BoardOAuthToken> currentTokens = mapper.selectRefreshFamilyTokensForUpdate(
                family.getFamilyId(), TOKEN_ROW_SENTINEL_LIMIT);
        BoardOAuthToken currentSource = findTokenById(currentTokens, source.getId());
        validateReplayCurrentRead(
                family, currentFamily, currentTokens, currentSource,
                bindingRevocation, now);
        byte[] afterState = BoardOAuthRefreshStateDigest.digest(
                "AFTER", currentFamily, currentTokens, authority,
                bindingRevocation);
        GeneratedReceiptIds generated = generateReceiptIds(
                presentedRawRefreshToken, family, tokens);
        BoardOAuthRefreshReceiptFactory.AuthoritySnapshot snapshot = authoritySnapshot(
                currentFamily, currentSource, beforeState, afterState);
        BoardOAuthRefreshReceiptFactory.CausationSnapshot causation =
                causationSnapshot(cause);
        BoardOAuthReceipt receipt = BoardOAuthRefreshReceiptFactory.createReplay(
                generated.receiptId(), generated.correlationId(), now,
                snapshot, causation);
        insertAndValidateReceipt(receipt, snapshot, causation, false);
        return BoardOAuthRefreshOutcome.invalidGrantAfterCommit(REPLAY_DETECTED);
    }

    private BoardOAuthRefreshOutcome rejectStableContainedFamily(
            BoardOAuthTokenFamily family,
            List<BoardOAuthToken> tokens,
            List<BoardOAuthReceipt> receipts,
            List<BoardOAuthReceipt> replayReceipts,
            BoardOAuthRefreshAuthorityPort.LockResult authority) {
        if (!STATUS_COMPROMISED.equals(family.getStatus())
                || replayReceipts.size() != 1
                || family.getTerminatedAt() == null
                || hasActiveToken(tokens)
                || !Objects.equals(authority.bindingId(), family.getBindingId())
                || authority.bindingVersion() == null
                || authority.bindingVersion() <= family.getBindingVersion()) {
            throw BoardOAuthProtocolException.serverError(RECEIPT_DRIFT);
        }
        BoardOAuthReceipt replay = replayReceipts.get(0);
        BoardOAuthToken replayedToken = findTokenById(tokens, replay.getTokenId());
        if (replayedToken == null
                || !TOKEN_REFRESH.equals(replayedToken.getTokenType())
                || !STATUS_USED.equals(replayedToken.getStatus())
                || !Objects.equals(
                        replayedToken.getGeneration(), replay.getSubjectGeneration())) {
            throw BoardOAuthProtocolException.serverError(RECEIPT_DRIFT);
        }
        BoardOAuthReceipt cause = requireExactSourceRotation(
                receipts, family, replayedToken);
        BoardOAuthRefreshReceiptFactory.AuthoritySnapshot snapshot = authoritySnapshot(
                family, replayedToken, replay.getBeforeStateDigest(),
                replay.getAfterStateDigest());
        BoardOAuthRefreshReceiptFactory.validateReplay(
                replay, snapshot, causationSnapshot(cause));
        return BoardOAuthRefreshOutcome.invalidGrantAfterCommit(REPLAY_DETECTED);
    }

    private static void validateCommand(BoardOAuthRefreshCommand command) {
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
        if (command.rawRefreshToken() == null
                || !OPAQUE_256.matcher(command.rawRefreshToken()).matches()) {
            throw BoardOAuthProtocolException.invalidGrant(TOKEN_INVALID);
        }
        if (command.requestedScope() != null
                && !BoardOAuthProfile.CANONICAL_SCOPE.equals(
                        command.requestedScope())) {
            throw BoardOAuthProtocolException.invalidScope(SCOPE_INVALID);
        }
    }

    private static void requireLocator(
            IndependentBoardOAuthMapper.TokenContextLocator locator,
            BoardOAuthRefreshCommand command) {
        if (locator == null
                || !isPositive(locator.getTokenId())
                || !isIdentifier(locator.getFamilyId())
                || !TOKEN_REFRESH.equals(locator.getTokenType())
                || !isUnsignedInt(locator.getTokenGeneration())
                || !Objects.equals(locator.getClientId(), command.clientId())
                || !isPositive(locator.getTenantId())
                || !isPositive(locator.getMemberId())
                || !isPositive(locator.getUserId())
                || !PRODUCT_CODE.equals(locator.getProductCode())
                || !SOURCE_CODE.equals(locator.getSourceCode())
                || !CONNECTOR_CODE.equals(locator.getConnectorCode())) {
            throw BoardOAuthProtocolException.invalidGrant(TOKEN_INVALID);
        }
    }

    private static void requireLockedTopology(
            IndependentBoardOAuthMapper.TokenContextLocator locator,
            BoardOAuthTokenFamily family,
            BoardOAuthTokenFamily activeSlot,
            BoardOAuthTokenFamily pendingSlot,
            List<BoardOAuthToken> tokens,
            List<BoardOAuthReceipt> receipts) {
        if (family == null || tokens == null || receipts == null
                || tokens.isEmpty() || tokens.size() > MAX_LOCKED_TOKEN_ROWS
                || receipts.size() > MAX_LOCKED_RECEIPT_ROWS
                || !Objects.equals(family.getFamilyId(), locator.getFamilyId())
                || !Objects.equals(family.getClientId(), locator.getClientId())
                || !Objects.equals(family.getTenantId(), locator.getTenantId())
                || !Objects.equals(family.getMemberId(), locator.getMemberId())
                || !Objects.equals(family.getUserId(), locator.getUserId())
                || !PRODUCT_CODE.equals(family.getProductCode())
                || !SOURCE_CODE.equals(family.getSourceCode())
                || !CONNECTOR_CODE.equals(family.getConnectorCode())) {
            throw BoardOAuthProtocolException.serverError(LINEAGE_DRIFT);
        }
        requireFamilyShape(family);
        requireOrderedTokens(tokens, family);
        requireOrderedReceipts(receipts, family);
        if (STATUS_ACTIVE.equals(family.getStatus())) {
            if (activeSlot == null
                    || !Objects.equals(activeSlot.getFamilyId(), family.getFamilyId())
                    || !Objects.equals(activeSlot.getClientId(), family.getClientId())) {
                throw BoardOAuthProtocolException.serverError(LINEAGE_DRIFT);
            }
        } else if (activeSlot != null
                && Objects.equals(activeSlot.getFamilyId(), family.getFamilyId())) {
            throw BoardOAuthProtocolException.serverError(LINEAGE_DRIFT);
        }
        if (pendingSlot != null
                && (!Objects.equals(pendingSlot.getTenantId(), family.getTenantId())
                        || !Objects.equals(pendingSlot.getMemberId(), family.getMemberId())
                        || !Objects.equals(pendingSlot.getProductCode(),
                                family.getProductCode())
                        || !Objects.equals(pendingSlot.getSourceCode(),
                                family.getSourceCode())
                        || !Objects.equals(pendingSlot.getConnectorCode(),
                                family.getConnectorCode()))) {
            throw BoardOAuthProtocolException.serverError(LINEAGE_DRIFT);
        }
    }

    private static void requireFamilyShape(BoardOAuthTokenFamily family) {
        if (!isPositive(family.getId())
                || !isIdentifier(family.getFamilyId())
                || !isPositive(family.getOriginAuthorizationCodeId())
                || !CLIENT_ID.matcher(nullToEmpty(family.getClientId())).matches()
                || !hasIdentity(family)
                || !BoardOAuthProfile.ISSUER.equals(family.getIssuerUri())
                || !BoardOAuthProfile.RESOURCE.equals(family.getResourceUri())
                || !BoardOAuthProfile.CANONICAL_SCOPE.equals(
                        family.getScopeCanonical())
                || !sameDigest(family.getScopeDigest(), canonicalScopeDigest())
                || family.getConsentIntent() == null
                || family.getBindingId() == null
                || !isPositive(family.getBindingVersion())
                || !isUnsignedInt(family.getCurrentRefreshGeneration())
                || family.getIssuedAt() == null
                || family.getActivatedAt() == null
                || family.getExpiresAt() == null
                || family.getActivatedAt().before(family.getIssuedAt())
                || !family.getExpiresAt().after(family.getActivatedAt())
                || family.getVersion() == null
                || family.getVersion() <= 0L) {
            throw BoardOAuthProtocolException.serverError(LINEAGE_DRIFT);
        }
        boolean active = STATUS_ACTIVE.equals(family.getStatus())
                && family.getTerminatedAt() == null;
        boolean terminal = Set.of(STATUS_REVOKED, STATUS_EXPIRED, STATUS_COMPROMISED)
                .contains(family.getStatus())
                && family.getTerminatedAt() != null
                && !family.getTerminatedAt().before(family.getActivatedAt());
        if (!active && !terminal) {
            throw BoardOAuthProtocolException.serverError(LINEAGE_DRIFT);
        }
    }

    private static void requireOrderedTokens(
            List<BoardOAuthToken> tokens,
            BoardOAuthTokenFamily family) {
        Long prior = null;
        Set<String> typeGeneration = new HashSet<>();
        for (BoardOAuthToken token : tokens) {
            if (token == null || !isPositive(token.getId())
                    || (prior != null && token.getId() <= prior)
                    || !Objects.equals(token.getFamilyId(), family.getFamilyId())
                    || !(TOKEN_ACCESS.equals(token.getTokenType())
                            || TOKEN_REFRESH.equals(token.getTokenType()))
                    || !isUnsignedInt(token.getGeneration())
                    || token.getGeneration() > family.getCurrentRefreshGeneration()
                    || !typeGeneration.add(
                            token.getTokenType() + ":" + token.getGeneration())
                    || !hasDigest(token.getTokenDigest())
                    || !BoardOAuthProfile.RESOURCE.equals(token.getResourceUri())
                    || !BoardOAuthProfile.CANONICAL_SCOPE.equals(
                            token.getScopeCanonical())
                    || !sameDigest(token.getScopeDigest(), canonicalScopeDigest())
                    || token.getIssuedAt() == null
                    || token.getExpiresAt() == null
                    || !token.getExpiresAt().after(token.getIssuedAt())
                    || token.getVersion() == null || token.getVersion() < 0L
                    || !validTokenLifecycle(token)) {
                throw BoardOAuthProtocolException.serverError(LINEAGE_DRIFT);
            }
            prior = token.getId();
        }
    }

    private static boolean validTokenLifecycle(BoardOAuthToken token) {
        return (STATUS_ACTIVE.equals(token.getStatus())
                        && token.getUsedAt() == null
                        && token.getRevokedAt() == null)
                || (STATUS_USED.equals(token.getStatus())
                        && TOKEN_REFRESH.equals(token.getTokenType())
                        && token.getUsedAt() != null
                        && token.getRevokedAt() == null
                        && !token.getUsedAt().before(token.getIssuedAt()))
                || (STATUS_REVOKED.equals(token.getStatus())
                        && token.getUsedAt() == null
                        && token.getRevokedAt() != null
                        && !token.getRevokedAt().before(token.getIssuedAt()))
                || (STATUS_EXPIRED.equals(token.getStatus())
                        && token.getUsedAt() == null
                        && token.getRevokedAt() == null);
    }

    private static void requireOrderedReceipts(
            List<BoardOAuthReceipt> receipts,
            BoardOAuthTokenFamily family) {
        Long prior = null;
        for (BoardOAuthReceipt receipt : receipts) {
            if (receipt == null || !isPositive(receipt.getId())
                    || (prior != null && receipt.getId() <= prior)
                    || !Objects.equals(receipt.getFamilyId(), family.getFamilyId())
                    || !Objects.equals(receipt.getClientId(), family.getClientId())
                    || !Objects.equals(receipt.getTenantId(), family.getTenantId())
                    || !Objects.equals(receipt.getMemberId(), family.getMemberId())
                    || !Objects.equals(receipt.getUserId(), family.getUserId())
                    || !sameDigest(receipt.getPrincipalSubjectDigest(),
                            family.getPrincipalSubjectDigest())
                    || receipt.getCreatedAt() == null
                    || !hasDigest(receipt.getPayloadDigest())) {
                throw BoardOAuthProtocolException.serverError(RECEIPT_DRIFT);
            }
            prior = receipt.getId();
        }
    }

    private static BoardOAuthToken requirePresentedToken(
            List<BoardOAuthToken> tokens,
            Long tokenId,
            byte[] digest) {
        BoardOAuthToken token = findTokenById(tokens, tokenId);
        if (token == null
                || !TOKEN_REFRESH.equals(token.getTokenType())
                || !sameDigest(token.getTokenDigest(), digest)) {
            throw BoardOAuthProtocolException.invalidGrant(TOKEN_INVALID);
        }
        return token;
    }

    private static void validateFreshAuthority(
            BoardOAuthTokenFamily family,
            List<BoardOAuthToken> tokens,
            BoardOAuthToken source,
            BoardOAuthClient client,
            BoardOAuthTokenFamily activeSlot,
            BoardOAuthRefreshAuthorityPort.LockResult authority,
            Instant now) {
        requireFreshClientCurrent(client, now);
        if (!authority.rotationAuthorityCurrentAt(now)
                || authority.authorityValidUntilExclusive() == null
                || authority.authorityValidUntilExclusive().isBefore(
                        now.plus(ACCESS_TOKEN_LIFETIME))
                || !Objects.equals(authority.bindingId(), family.getBindingId())
                || !Objects.equals(authority.bindingVersion(), family.getBindingVersion())
                || !Objects.equals(authority.bindingClientId(), family.getClientId())
                || !sameDigest(authority.principalSubjectDigest(),
                        family.getPrincipalSubjectDigest())
                || !BoardOAuthProfile.hasExactScopeSet(authority.scopes())) {
            throw BoardOAuthProtocolException.invalidGrant(AUTHORITY_NOT_CURRENT);
        }
        if (activeSlot == null
                || !Objects.equals(activeSlot.getFamilyId(), family.getFamilyId())
                || !Objects.equals(source.getGeneration(),
                        family.getCurrentRefreshGeneration())
                || countActiveRefreshAtGeneration(
                        tokens, family.getCurrentRefreshGeneration()) != 1
                || source.getGeneration() == MAX_UNSIGNED_INT
                || source.getIssuedAt().toInstant().isAfter(now)
                || !now.isBefore(source.getExpiresAt().toInstant())
                || family.getActivatedAt().toInstant().isAfter(now)
                || family.getExpiresAt().toInstant().isBefore(
                        now.plus(ACCESS_TOKEN_LIFETIME))
                || family.getTerminatedAt() != null) {
            throw BoardOAuthProtocolException.invalidGrant(TOKEN_INVALID);
        }
    }

    private static void requireReplayLineage(
            BoardOAuthTokenFamily family,
            List<BoardOAuthToken> tokens,
            BoardOAuthToken source,
            BoardOAuthClient client,
            BoardOAuthRefreshAuthorityPort.LockResult authority,
            Instant now) {
        if (!STATUS_USED.equals(source.getStatus())
                || source.getUsedAt() == null
                || source.getUsedAt().toInstant().isAfter(now)
                || source.getGeneration() >= family.getCurrentRefreshGeneration()
                || !Objects.equals(authority.bindingId(), family.getBindingId())
                || !Objects.equals(authority.bindingVersion(), family.getBindingVersion())
                || !Objects.equals(authority.bindingClientId(), family.getClientId())
                || !authority.bindingShapeCurrent()
                || !authority.bindingActive()
                || !sameDigest(authority.principalSubjectDigest(),
                        family.getPrincipalSubjectDigest())
                || !BoardOAuthProfile.hasExactScopeSet(authority.scopes())
                || countActiveRefreshAtGeneration(
                        tokens, family.getCurrentRefreshGeneration()) != 1) {
            throw BoardOAuthProtocolException.serverError(LINEAGE_DRIFT);
        }
        requireClientShape(client, family.getClientId());
    }

    private static void requireFreshClientCurrent(BoardOAuthClient client, Instant now) {
        if (!STATUS_ACTIVE.equals(client.getStatus())
                || client.getTerminatedAt() != null
                || client.getRegisteredAt() == null
                || client.getExpiresAt() == null
                || client.getRegisteredAt().toInstant().isAfter(now)
                || client.getExpiresAt().toInstant().isBefore(
                        now.plus(ACCESS_TOKEN_LIFETIME))) {
            throw BoardOAuthProtocolException.invalidClient(CLIENT_INVALID);
        }
    }

    private static void requireRotationCapacity(
            List<BoardOAuthToken> tokens,
            List<BoardOAuthReceipt> receipts) {
        if (tokens.size() > MAX_LOCKED_TOKEN_ROWS - 2
                || receipts.size() >= MAX_LOCKED_RECEIPT_ROWS) {
            throw BoardOAuthProtocolException.invalidGrant(TOKEN_INVALID);
        }
    }

    private static void requireReceiptCapacity(List<BoardOAuthReceipt> receipts) {
        if (receipts.size() >= MAX_LOCKED_RECEIPT_ROWS) {
            throw BoardOAuthProtocolException.serverError(RECEIPT_DRIFT);
        }
    }

    private static void requireClientShape(BoardOAuthClient client, String clientId) {
        if (client == null || !isPositive(client.getId())
                || !Objects.equals(client.getClientId(), clientId)
                || !BoardOAuthProfile.ISSUER.equals(client.getIssuerUri())
                || !BoardOAuthProfile.RESOURCE.equals(client.getResourceUri())
                || !PRODUCT_CODE.equals(client.getProductCode())
                || !SOURCE_CODE.equals(client.getSourceCode())
                || !CONNECTOR_CODE.equals(client.getConnectorCode())
                || !TOKEN_ENDPOINT_AUTH_METHOD.equals(
                        client.getTokenEndpointAuthMethod())
                || !GRANT_TYPES_CANONICAL.equals(client.getGrantTypesCanonical())
                || !RESPONSE_TYPES_CANONICAL.equals(client.getResponseTypesCanonical())
                || !BoardOAuthProfile.CANONICAL_SCOPE.equals(
                        client.getScopeCanonical())
                || !sameDigest(client.getScopeDigest(), canonicalScopeDigest())
                || !hasDigest(client.getMetadataDigest())
                || !hasDigest(client.getRegistrationSourceDigest())
                || client.getCreatedAt() == null || client.getUpdatedAt() == null
                || client.getCreatedAt().after(client.getUpdatedAt())
                || client.getVersion() == null || client.getVersion() < 0L) {
            throw BoardOAuthProtocolException.serverError(PROFILE_DRIFT);
        }
    }

    private static BoardOAuthReceipt requireRotationCause(
            List<BoardOAuthReceipt> receipts,
            BoardOAuthTokenFamily family,
            Long subjectGeneration) {
        List<BoardOAuthReceipt> candidates = new ArrayList<>();
        if (subjectGeneration == 0L) {
            for (BoardOAuthReceipt receipt : receipts) {
                if (TOKEN_FAMILY_CREATED.equals(receipt.getAction())
                        && Objects.equals(
                                receipt.getAuthorizationCodeId(),
                                family.getOriginAuthorizationCodeId())) {
                    candidates.add(receipt);
                }
            }
        } else {
            for (BoardOAuthReceipt receipt : receipts) {
                if (BoardOAuthRefreshReceiptFactory.TOKEN_FAMILY_ROTATED.equals(
                                receipt.getAction())
                        && Objects.equals(receipt.getReceiptFormatVersion(), 2)
                        && Objects.equals(
                                receipt.getResultGeneration(), subjectGeneration)) {
                    candidates.add(receipt);
                }
            }
        }
        if (candidates.size() != 1) {
            throw BoardOAuthProtocolException.serverError(RECEIPT_DRIFT);
        }
        BoardOAuthReceipt cause = candidates.get(0);
        if (subjectGeneration == 0L
                && (!Objects.equals(cause.getReceiptFormatVersion(), 1)
                        || cause.getResultGeneration() != null
                        || cause.getAuthorizationRequestId() != null
                        || !Objects.equals(cause.getAuthorizationCodeId(),
                                family.getOriginAuthorizationCodeId())
                        || cause.getTokenId() != null
                        || cause.getBindingId() != null)) {
            throw BoardOAuthProtocolException.serverError(RECEIPT_DRIFT);
        }
        requireCauseScope(cause, family);
        return cause;
    }

    private static BoardOAuthReceipt requireExactSourceRotation(
            List<BoardOAuthReceipt> receipts,
            BoardOAuthTokenFamily family,
            BoardOAuthToken source) {
        List<BoardOAuthReceipt> candidates = new ArrayList<>();
        for (BoardOAuthReceipt receipt : receipts) {
            if (BoardOAuthRefreshReceiptFactory.TOKEN_FAMILY_ROTATED.equals(
                            receipt.getAction())
                    && Objects.equals(receipt.getReceiptFormatVersion(), 2)
                    && Objects.equals(receipt.getTokenId(), source.getId())
                    && Objects.equals(
                            receipt.getSubjectGeneration(), source.getGeneration())
                    && Objects.equals(
                            receipt.getResultGeneration(), source.getGeneration() + 1L)) {
                candidates.add(receipt);
            }
        }
        if (candidates.size() != 1) {
            throw BoardOAuthProtocolException.serverError(RECEIPT_DRIFT);
        }
        BoardOAuthReceipt cause = candidates.get(0);
        requireCauseScope(cause, family);
        return cause;
    }

    private static void requireCauseScope(
            BoardOAuthReceipt cause,
            BoardOAuthTokenFamily family) {
        if (!Objects.equals(cause.getFamilyId(), family.getFamilyId())
                || !Objects.equals(cause.getClientId(), family.getClientId())
                || !Objects.equals(cause.getTenantId(), family.getTenantId())
                || !Objects.equals(cause.getMemberId(), family.getMemberId())
                || !Objects.equals(cause.getUserId(), family.getUserId())
                || !sameDigest(cause.getPrincipalSubjectDigest(),
                        family.getPrincipalSubjectDigest())
                || !"CLIENT".equals(cause.getActorType())
                || cause.getActorUserId() != null
                || !sameDigest(cause.getActorSubjectDigest(),
                        BoardOAuthCrypto.sha256Ascii(family.getClientId()))
                || !"ACTION_COMPLETED".equals(cause.getEvidenceLevel())
                || cause.getCreatedAt() == null
                || !hasDigest(cause.getPayloadDigest())) {
            throw BoardOAuthProtocolException.serverError(RECEIPT_DRIFT);
        }
        if (BoardOAuthRefreshReceiptFactory.TOKEN_FAMILY_ROTATED.equals(
                        cause.getAction())
                && (cause.getAuthorizationRequestId() != null
                        || cause.getAuthorizationCodeId() != null
                        || cause.getTokenId() == null
                        || !Objects.equals(cause.getBindingId(),
                                family.getBindingId()))) {
            throw BoardOAuthProtocolException.serverError(RECEIPT_DRIFT);
        }
    }

    private static void requireNoRotationForSource(
            List<BoardOAuthReceipt> receipts,
            BoardOAuthToken source) {
        for (BoardOAuthReceipt receipt : receipts) {
            if (BoardOAuthRefreshReceiptFactory.TOKEN_FAMILY_ROTATED.equals(
                            receipt.getAction())
                    && (Objects.equals(receipt.getTokenId(), source.getId())
                            || Objects.equals(
                                    receipt.getResultGeneration(),
                                    source.getGeneration() + 1L))) {
                throw BoardOAuthProtocolException.serverError(RECEIPT_DRIFT);
            }
        }
    }

    private GeneratedMaterial generateMaterial(
            BoardOAuthRefreshCommand command,
            BoardOAuthTokenFamily family,
            List<BoardOAuthToken> tokens) {
        GeneratedMaterial generated = new GeneratedMaterial(
                materialGenerator.generateAccessToken(),
                materialGenerator.generateRefreshToken(),
                materialGenerator.generateReceiptId(),
                materialGenerator.generateCorrelationId());
        if (!generated.validShape()
                || generated.collidesWith(command.rawRefreshToken())
                || generated.collidesWith(family.getFamilyId())
                || generated.collidesWith(family.getClientId())) {
            throw BoardOAuthProtocolException.serverError(WRITE_FAILED);
        }
        byte[] accessDigest = BoardOAuthCrypto.sha256Ascii(
                generated.rawAccessToken());
        byte[] refreshDigest = BoardOAuthCrypto.sha256Ascii(
                generated.rawRefreshToken());
        byte[] receiptDigest = BoardOAuthCrypto.sha256Ascii(
                generated.receiptId());
        byte[] correlationDigest = BoardOAuthCrypto.sha256Ascii(
                generated.correlationId());
        for (BoardOAuthToken token : tokens) {
            if (sameDigest(token.getTokenDigest(), accessDigest)
                    || sameDigest(token.getTokenDigest(), refreshDigest)
                    || sameDigest(token.getTokenDigest(), receiptDigest)
                    || sameDigest(token.getTokenDigest(), correlationDigest)) {
                throw BoardOAuthProtocolException.serverError(WRITE_FAILED);
            }
        }
        return generated;
    }

    private GeneratedReceiptIds generateReceiptIds(
            String presentedRawRefreshToken,
            BoardOAuthTokenFamily family,
            List<BoardOAuthToken> tokens) {
        GeneratedReceiptIds generated = new GeneratedReceiptIds(
                materialGenerator.generateReceiptId(),
                materialGenerator.generateCorrelationId());
        if (!generated.validShape()
                || generated.collidesWith(presentedRawRefreshToken)
                || generated.collidesWith(family.getFamilyId())
                || generated.collidesWith(family.getClientId())) {
            throw BoardOAuthProtocolException.serverError(WRITE_FAILED);
        }
        byte[] receiptDigest = BoardOAuthCrypto.sha256Ascii(
                generated.receiptId());
        byte[] correlationDigest = BoardOAuthCrypto.sha256Ascii(
                generated.correlationId());
        for (BoardOAuthToken token : tokens) {
            if (sameDigest(token.getTokenDigest(), receiptDigest)
                    || sameDigest(token.getTokenDigest(), correlationDigest)) {
                throw BoardOAuthProtocolException.serverError(WRITE_FAILED);
            }
        }
        return generated;
    }

    private static BoardOAuthToken newToken(
            String rawToken,
            BoardOAuthTokenFamily family,
            String tokenType,
            long generation,
            Instant issuedAt,
            Instant expiresAt) {
        BoardOAuthToken token = new BoardOAuthToken();
        token.setTokenDigest(BoardOAuthCrypto.sha256Ascii(rawToken));
        token.setFamilyId(family.getFamilyId());
        token.setTokenType(tokenType);
        token.setGeneration(generation);
        token.setResourceUri(family.getResourceUri());
        token.setScopeCanonical(family.getScopeCanonical());
        token.setScopeDigest(family.getScopeDigest().clone());
        token.setStatus(STATUS_ACTIVE);
        token.setIssuedAt(Date.from(issuedAt));
        token.setUsedAt(null);
        token.setRevokedAt(null);
        token.setExpiresAt(Date.from(expiresAt));
        token.setVersion(0L);
        return token;
    }

    private void insertToken(BoardOAuthToken token) {
        if (mapper.insertToken(token) != 1 || !isPositive(token.getId())) {
            throw BoardOAuthProtocolException.serverError(WRITE_FAILED);
        }
    }

    private void insertAndValidateReceipt(
            BoardOAuthReceipt receipt,
            BoardOAuthRefreshReceiptFactory.AuthoritySnapshot authority,
            BoardOAuthRefreshReceiptFactory.CausationSnapshot causation,
            boolean rotation) {
        if (mapper.insertReceipt(receipt) != 1 || !isPositive(receipt.getId())) {
            throw BoardOAuthProtocolException.serverError(RECEIPT_WRITE_FAILED);
        }
        BoardOAuthReceipt current = mapper.selectReceiptByReceiptIdAndScopeForUpdate(
                receipt.getReceiptId(), receipt.getFamilyId(), receipt.getClientId());
        if (rotation) {
            BoardOAuthRefreshReceiptFactory.validateRotation(
                    current, authority, causation);
        } else {
            BoardOAuthRefreshReceiptFactory.validateReplay(
                    current, authority, causation);
        }
    }

    private static void validateRotationCurrentRead(
            BoardOAuthTokenFamily beforeFamily,
            List<BoardOAuthToken> beforeTokens,
            BoardOAuthTokenFamily family,
            List<BoardOAuthToken> tokens,
            BoardOAuthToken source,
            BoardOAuthToken insertedAccess,
            BoardOAuthToken insertedRefresh,
            Instant eventAt,
            Instant accessExpiresAt) {
        long expectedGeneration = beforeFamily.getCurrentRefreshGeneration() + 1L;
        if (family == null || tokens == null || source == null
                || !STATUS_ACTIVE.equals(family.getStatus())
                || !Objects.equals(family.getCurrentRefreshGeneration(),
                        expectedGeneration)
                || !Objects.equals(family.getVersion(), beforeFamily.getVersion() + 1L)
                || family.getTerminatedAt() != null
                || !STATUS_USED.equals(source.getStatus())
                || !Objects.equals(source.getVersion(),
                        findTokenById(beforeTokens, source.getId()).getVersion() + 1L)
                || source.getUsedAt() == null
                || !source.getUsedAt().toInstant().equals(eventAt)
                || tokens.size() != beforeTokens.size() + 2
                || countActiveRefreshAtGeneration(tokens, expectedGeneration) != 1
                || !containsInsertedToken(tokens, insertedAccess, TOKEN_ACCESS,
                        expectedGeneration, eventAt, accessExpiresAt)
                || !containsInsertedToken(tokens, insertedRefresh, TOKEN_REFRESH,
                        expectedGeneration, eventAt,
                        family.getExpiresAt().toInstant())) {
            throw BoardOAuthProtocolException.serverError(LINEAGE_DRIFT);
        }
        requireFamilyShape(family);
        requireOrderedTokens(tokens, family);
    }

    private static void validateReplayCurrentRead(
            BoardOAuthTokenFamily beforeFamily,
            BoardOAuthTokenFamily family,
            List<BoardOAuthToken> tokens,
            BoardOAuthToken source,
            BoardOAuthRefreshAuthorityPort.BindingRevocation revocation,
            Instant eventAt) {
        if (family == null || tokens == null || source == null || revocation == null
                || !STATUS_COMPROMISED.equals(family.getStatus())
                || !Objects.equals(family.getVersion(), beforeFamily.getVersion() + 1L)
                || family.getTerminatedAt() == null
                || !family.getTerminatedAt().toInstant().equals(eventAt)
                || !STATUS_USED.equals(source.getStatus())
                || hasActiveToken(tokens)
                || !Objects.equals(revocation.bindingId(), family.getBindingId())
                || !Objects.equals(revocation.previousVersion(),
                        family.getBindingVersion())
                || !Objects.equals(revocation.revokedVersion(),
                        family.getBindingVersion() + 1L)
                || !Objects.equals(revocation.revokedAt(), eventAt)) {
            throw BoardOAuthProtocolException.serverError(LINEAGE_DRIFT);
        }
        requireFamilyShape(family);
        requireOrderedTokens(tokens, family);
    }

    private static boolean containsInsertedToken(
            List<BoardOAuthToken> tokens,
            BoardOAuthToken inserted,
            String tokenType,
            long generation,
            Instant issuedAt,
            Instant expiresAt) {
        BoardOAuthToken current = findTokenById(tokens, inserted.getId());
        return current != null
                && tokenType.equals(current.getTokenType())
                && Objects.equals(current.getGeneration(), generation)
                && sameDigest(current.getTokenDigest(), inserted.getTokenDigest())
                && STATUS_ACTIVE.equals(current.getStatus())
                && current.getIssuedAt().toInstant().equals(issuedAt)
                && current.getExpiresAt().toInstant().equals(expiresAt)
                && Objects.equals(current.getVersion(), 0L);
    }

    private static BoardOAuthRefreshReceiptFactory.AuthoritySnapshot authoritySnapshot(
            BoardOAuthTokenFamily family,
            BoardOAuthToken source,
            byte[] beforeState,
            byte[] afterState) {
        return new BoardOAuthRefreshReceiptFactory.AuthoritySnapshot(
                family.getClientId(), family.getFamilyId(), source.getId(),
                family.getBindingId(), family.getTenantId(), family.getMemberId(),
                family.getUserId(), family.getPrincipalSubjectDigest(),
                source.getTokenDigest(), source.getGeneration(),
                family.getCurrentRefreshGeneration(), family.getVersion(),
                source.getVersion(), family.getStatus(), source.getStatus(),
                source.getUsedAt() == null ? null : source.getUsedAt().toInstant(),
                family.getTerminatedAt() == null
                        ? null : family.getTerminatedAt().toInstant(),
                beforeState, afterState);
    }

    private static BoardOAuthRefreshReceiptFactory.CausationSnapshot causationSnapshot(
            BoardOAuthReceipt receipt) {
        if (receipt == null || receipt.getCreatedAt() == null
                || !hasDigest(receipt.getPayloadDigest())) {
            throw BoardOAuthProtocolException.serverError(RECEIPT_DRIFT);
        }
        return new BoardOAuthRefreshReceiptFactory.CausationSnapshot(
                receipt.getReceiptId(), receipt.getAction(),
                receipt.getReceiptFormatVersion(), receipt.getResultGeneration(),
                receipt.getPayloadDigest(), receipt.getCreatedAt().toInstant());
    }

    private static List<BoardOAuthReceipt> filterReceipts(
            List<BoardOAuthReceipt> receipts,
            String action) {
        List<BoardOAuthReceipt> matches = new ArrayList<>();
        for (BoardOAuthReceipt receipt : receipts) {
            if (action.equals(receipt.getAction())) {
                matches.add(receipt);
            }
        }
        return matches;
    }

    private static BoardOAuthToken findTokenById(
            List<BoardOAuthToken> tokens,
            Long tokenId) {
        if (tokens == null || tokenId == null) {
            return null;
        }
        BoardOAuthToken match = null;
        for (BoardOAuthToken token : tokens) {
            if (token != null && Objects.equals(token.getId(), tokenId)) {
                if (match != null) {
                    throw BoardOAuthProtocolException.serverError(LINEAGE_DRIFT);
                }
                match = token;
            }
        }
        return match;
    }

    private static int countActive(List<BoardOAuthToken> tokens) {
        int count = 0;
        for (BoardOAuthToken token : tokens) {
            if (STATUS_ACTIVE.equals(token.getStatus())) {
                count++;
            }
        }
        return count;
    }

    private static int countActiveRefreshAtGeneration(
            List<BoardOAuthToken> tokens,
            Long generation) {
        int count = 0;
        for (BoardOAuthToken token : tokens) {
            if (TOKEN_REFRESH.equals(token.getTokenType())
                    && STATUS_ACTIVE.equals(token.getStatus())
                    && Objects.equals(token.getGeneration(), generation)) {
                count++;
            }
        }
        return count;
    }

    private static boolean hasActiveToken(List<BoardOAuthToken> tokens) {
        return countActive(tokens) != 0;
    }

    private Instant millisecondNow() {
        return Instant.ofEpochMilli(clock.instant().toEpochMilli());
    }

    private Instant decisionAtNotBefore(Instant notBefore) {
        Instant now = millisecondNow();
        return now.isBefore(notBefore) ? notBefore : now;
    }

    private static boolean hasIdentity(BoardOAuthTokenFamily family) {
        return isPositive(family.getTenantId())
                && isPositive(family.getMemberId())
                && isPositive(family.getUserId())
                && sameDigest(
                        family.getPrincipalSubjectDigest(),
                        BoardOAuthPrincipalSubject.digest(
                                family.getTenantId(), family.getMemberId(),
                                family.getUserId()));
    }

    private static byte[] canonicalScopeDigest() {
        return BoardOAuthCrypto.sha256Ascii(BoardOAuthProfile.CANONICAL_SCOPE);
    }

    private static boolean isPositive(Long value) {
        return value != null && value > 0L;
    }

    private static boolean isUnsignedInt(Long value) {
        return value != null && value >= 0L && value <= MAX_UNSIGNED_INT;
    }

    private static boolean isIdentifier(String value) {
        return value != null && OPAQUE_256.matcher(value).matches();
    }

    private static boolean hasDigest(byte[] value) {
        return value != null && value.length == BoardOAuthCrypto.SHA256_BYTES;
    }

    private static boolean sameDigest(byte[] left, byte[] right) {
        return hasDigest(left) && hasDigest(right)
                && BoardOAuthCrypto.constantTimeEquals(left, right);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static final class GeneratedMaterial {
        private final String rawAccessToken;
        private final String rawRefreshToken;
        private final String receiptId;
        private final String correlationId;

        private GeneratedMaterial(
                String rawAccessToken,
                String rawRefreshToken,
                String receiptId,
                String correlationId) {
            this.rawAccessToken = rawAccessToken;
            this.rawRefreshToken = rawRefreshToken;
            this.receiptId = receiptId;
            this.correlationId = correlationId;
        }

        private boolean validShape() {
            return isIdentifier(rawAccessToken)
                    && isIdentifier(rawRefreshToken)
                    && isIdentifier(receiptId)
                    && isIdentifier(correlationId)
                    && Set.of(rawAccessToken, rawRefreshToken, receiptId,
                            correlationId).size() == 4;
        }

        private boolean collidesWith(String value) {
            return Objects.equals(rawAccessToken, value)
                    || Objects.equals(rawRefreshToken, value)
                    || Objects.equals(receiptId, value)
                    || Objects.equals(correlationId, value);
        }

        private String rawAccessToken() { return rawAccessToken; }
        private String rawRefreshToken() { return rawRefreshToken; }
        private String receiptId() { return receiptId; }
        private String correlationId() { return correlationId; }

        @Override
        public String toString() {
            return "GeneratedMaterial[REDACTED]";
        }
    }

    private record GeneratedReceiptIds(String receiptId, String correlationId) {
        private boolean validShape() {
            return isIdentifier(receiptId)
                    && isIdentifier(correlationId)
                    && !receiptId.equals(correlationId);
        }

        private boolean collidesWith(String value) {
            return Objects.equals(receiptId, value)
                    || Objects.equals(correlationId, value);
        }

        @Override
        public String toString() {
            return "GeneratedReceiptIds[REDACTED]";
        }
    }
}
