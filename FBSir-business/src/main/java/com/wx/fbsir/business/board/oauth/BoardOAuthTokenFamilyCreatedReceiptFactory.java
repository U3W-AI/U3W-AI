package com.wx.fbsir.business.board.oauth;

import com.wx.fbsir.business.board.oauth.domain.BoardOAuthAuthorizationCode;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthAuthorizationRequest;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthReceipt;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthToken;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthTokenFamily;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Builds and verifies the canonical digest-only {@code TOKEN_FAMILY_CREATED}
 * receipt.
 *
 * <p>The caller must supply authoritative, current-read terminal rows. This
 * class never accepts a raw authorization code, access token, refresh token or
 * state value. It accepts only the digest-only persistence models and rejects
 * an authorization request whose encrypted state has not been cleared.</p>
 */
public final class BoardOAuthTokenFamilyCreatedReceiptFactory {
    public static final String RECEIPT_INVALID =
            "OAUTH_TOKEN_FAMILY_CREATED_RECEIPT_INVALID";
    public static final String CANONICAL_DOMAIN_V2 =
            "FBSIR:OAUTH:TOKEN_FAMILY_CREATED_RECEIPT:v2";

    private static final String ACTION = "TOKEN_FAMILY_CREATED";
    private static final String ACTOR_CLIENT = "CLIENT";
    private static final String EVIDENCE_ACTION_COMPLETED = "ACTION_COMPLETED";
    private static final String PRODUCT_CODE = "FBSIR_INDEPENDENT_BOARD";
    private static final String SOURCE_CODE = "WORKBUDDY";
    private static final String CONNECTOR_CODE = "fbs-connector";
    private static final Duration REQUEST_LIFETIME = Duration.ofMinutes(5);
    private static final Duration CODE_LIFETIME = Duration.ofSeconds(60);
    private static final Duration ACCESS_TOKEN_LIFETIME = Duration.ofMinutes(10);
    private static final Duration MAX_FAMILY_LIFETIME = Duration.ofDays(30);
    private static final Duration MAX_DATABASE_CLOCK_SKEW = Duration.ofSeconds(5);
    private static final Pattern RECEIPT_IDENTIFIER =
            Pattern.compile("[A-Za-z0-9_-]{1,128}");
    private static final Pattern CLIENT_IDENTIFIER =
            Pattern.compile("[A-Za-z0-9_-]{43,191}");
    private static final Pattern FAMILY_IDENTIFIER =
            Pattern.compile("[A-Za-z0-9_-]{1,128}");

    private BoardOAuthTokenFamilyCreatedReceiptFactory() {
    }

    /**
     * Creates a schema-valid receipt from already-consumed lineage rows.
     * Token order is deliberately caller-independent and is canonicalized by
     * ascending database token id.
     */
    public static BoardOAuthReceipt create(
            String receiptId,
            String correlationId,
            Instant createdAt,
            BoardOAuthAuthorizationRequest request,
            BoardOAuthAuthorizationCode code,
            BoardOAuthTokenFamily family,
            List<BoardOAuthToken> unsortedTokens) {
        try {
            CanonicalContext context = requireContext(
                    receiptId,
                    correlationId,
                    createdAt,
                    request,
                    code,
                    family,
                    unsortedTokens);
            BoardOAuthReceipt receipt = new BoardOAuthReceipt();
            receipt.setReceiptId(receiptId);
            receipt.setAction(ACTION);
            receipt.setClientId(request.getClientId());
            receipt.setAuthorizationRequestId(null);
            receipt.setAuthorizationCodeId(code.getId());
            receipt.setFamilyId(family.getFamilyId());
            receipt.setTokenId(null);
            receipt.setBindingId(null);
            receipt.setTenantId(family.getTenantId());
            receipt.setMemberId(family.getMemberId());
            receipt.setUserId(family.getUserId());
            receipt.setPrincipalSubjectDigest(
                    family.getPrincipalSubjectDigest().clone());
            receipt.setActorType(ACTOR_CLIENT);
            receipt.setActorUserId(null);
            receipt.setActorSubjectDigest(
                    BoardOAuthCrypto.sha256Ascii(request.getClientId()));
            receipt.setCorrelationId(correlationId);
            receipt.setEvidenceLevel(EVIDENCE_ACTION_COMPLETED);
            receipt.setCreatedAt(Date.from(createdAt));
            receipt.setPayloadDigest(canonicalPayloadDigest(receipt, context));
            return receipt;
        } catch (BoardOAuthProtocolException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw invalidReceipt();
        }
    }

    /**
     * Recomputes the canonical payload digest from a persisted receipt and
     * authoritative current-read lineage rows. The stored payload digest is
     * intentionally ignored by this method.
     */
    public static byte[] recomputePayloadDigest(
            BoardOAuthReceipt receipt,
            BoardOAuthAuthorizationRequest request,
            BoardOAuthAuthorizationCode code,
            BoardOAuthTokenFamily family,
            List<BoardOAuthToken> unsortedTokens) {
        try {
            if (receipt == null || receipt.getCreatedAt() == null) {
                throw invalidReceipt();
            }
            CanonicalContext context = requireContext(
                    receipt.getReceiptId(),
                    receipt.getCorrelationId(),
                    receipt.getCreatedAt().toInstant(),
                    request,
                    code,
                    family,
                    unsortedTokens);
            requireReceiptShape(receipt, context);
            return canonicalPayloadDigest(receipt, context);
        } catch (BoardOAuthProtocolException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw invalidReceipt();
        }
    }

    /**
     * Fails closed unless every receipt field and its canonical payload digest
     * match the supplied authoritative lineage rows.
     */
    public static void validate(
            BoardOAuthReceipt receipt,
            BoardOAuthAuthorizationRequest request,
            BoardOAuthAuthorizationCode code,
            BoardOAuthTokenFamily family,
            List<BoardOAuthToken> unsortedTokens) {
        byte[] expected = recomputePayloadDigest(
                receipt, request, code, family, unsortedTokens);
        if (!hasDigest(receipt.getPayloadDigest())
                || !BoardOAuthCrypto.constantTimeEquals(
                        expected, receipt.getPayloadDigest())) {
            throw invalidReceipt();
        }
    }

    private static CanonicalContext requireContext(
            String receiptId,
            String correlationId,
            Instant createdAt,
            BoardOAuthAuthorizationRequest request,
            BoardOAuthAuthorizationCode code,
            BoardOAuthTokenFamily family,
            List<BoardOAuthToken> unsortedTokens) {
        if (!isReceiptIdentifier(receiptId)
                || !isReceiptIdentifier(correlationId)
                || !isMillisecondInstant(createdAt)
                || request == null
                || code == null
                || family == null
                || unsortedTokens == null
                || unsortedTokens.size() != 2) {
            throw invalidReceipt();
        }

        requireRequest(request, createdAt);
        requireCode(request, code, createdAt);
        requireFamily(code, family, createdAt);

        List<BoardOAuthToken> sortedTokens = new ArrayList<>(unsortedTokens);
        if (sortedTokens.get(0) == null || sortedTokens.get(1) == null) {
            throw invalidReceipt();
        }
        sortedTokens.sort((left, right) -> Long.compare(
                requirePositive(left.getId()), requirePositive(right.getId())));
        BoardOAuthToken first = sortedTokens.get(0);
        BoardOAuthToken second = sortedTokens.get(1);
        if (Objects.equals(first.getId(), second.getId())
                || sameDigest(first.getTokenDigest(), second.getTokenDigest())) {
            throw invalidReceipt();
        }
        requireToken(first, family, createdAt);
        requireToken(second, family, createdAt);
        if (Objects.equals(first.getTokenType(), second.getTokenType())) {
            throw invalidReceipt();
        }

        return new CanonicalContext(
                request, code, family, List.copyOf(sortedTokens), createdAt);
    }

    private static void requireRequest(
            BoardOAuthAuthorizationRequest request,
            Instant eventAt) {
        if (!isPositive(request.getId())
                || !hasDigest(request.getRequestHandleDigest())
                || !isClientIdentifier(request.getClientId())
                || !BoardOAuthProfile.isAllowedLoopbackRedirect(
                        request.getRedirectUri())
                || !BoardOAuthCrypto.isValidPkceS256Challenge(
                        request.getCodeChallenge())
                || !BoardOAuthProfile.PKCE_METHOD.equals(
                        request.getCodeChallengeMethod())
                || !hasDigest(request.getStateDigest())
                || request.getStateKeyRef() != null
                || request.getStateNonce() != null
                || request.getStateCiphertext() != null
                || !hasFixedProfile(
                        request.getIssuerUri(),
                        request.getResourceUri(),
                        request.getProductCode(),
                        request.getSourceCode(),
                        request.getConnectorCode(),
                        request.getScopeCanonical(),
                        request.getScopeDigest())
                || !hasIdentity(
                        request.getTenantId(),
                        request.getMemberId(),
                        request.getUserId(),
                        request.getPrincipalSubjectDigest())
                || request.getConsentIntent() == null
                || !"CONSUMED".equals(request.getStatus())
                || request.getRequestedAt() == null
                || request.getExpiresAt() == null
                || request.getApprovedAt() == null
                || request.getDeniedAt() != null
                || request.getConsumedAt() == null
                || !Objects.equals(request.getVersion(), 2L)
                || !hasOrderedRowTimes(
                        request.getCreatedAt(), request.getUpdatedAt())) {
            throw invalidReceipt();
        }

        Instant requestedAt = request.getRequestedAt().toInstant();
        Instant expiresAt = request.getExpiresAt().toInstant();
        Instant approvedAt = request.getApprovedAt().toInstant();
        Instant consumedAt = request.getConsumedAt().toInstant();
        if (!expiresAt.equals(requestedAt.plus(REQUEST_LIFETIME))
                || approvedAt.isBefore(requestedAt)
                || !approvedAt.isBefore(expiresAt)
                || consumedAt.isBefore(approvedAt)
                || !consumedAt.isBefore(expiresAt)
                || !consumedAt.equals(eventAt)
                || !isWithinDatabaseClockSkew(
                        request.getCreatedAt(), requestedAt)
                || !isWithinDatabaseClockSkew(
                        request.getUpdatedAt(), eventAt)) {
            throw invalidReceipt();
        }
    }

    private static void requireCode(
            BoardOAuthAuthorizationRequest request,
            BoardOAuthAuthorizationCode code,
            Instant eventAt) {
        if (!isPositive(code.getId())
                || !hasDigest(code.getCodeDigest())
                || !Objects.equals(code.getAuthorizationRequestId(), request.getId())
                || !sameString(code.getClientId(), request.getClientId())
                || !sameString(code.getRedirectUri(), request.getRedirectUri())
                || !sameString(code.getCodeChallenge(), request.getCodeChallenge())
                || !sameString(
                        code.getCodeChallengeMethod(),
                        request.getCodeChallengeMethod())
                || !hasFixedProfile(
                        code.getIssuerUri(),
                        code.getResourceUri(),
                        code.getProductCode(),
                        code.getSourceCode(),
                        code.getConnectorCode(),
                        code.getScopeCanonical(),
                        code.getScopeDigest())
                || !sameIdentity(request, code)
                || !Objects.equals(
                        code.getConsentIntent(), request.getConsentIntent())
                || !"USED".equals(code.getStatus())
                || code.getIssuedAt() == null
                || code.getExpiresAt() == null
                || code.getUsedAt() == null
                || code.getRevokedAt() != null
                || !Objects.equals(code.getVersion(), 1L)
                || !hasOrderedRowTimes(code.getCreatedAt(), code.getUpdatedAt())) {
            throw invalidReceipt();
        }

        Instant issuedAt = code.getIssuedAt().toInstant();
        Instant expiresAt = code.getExpiresAt().toInstant();
        Instant usedAt = code.getUsedAt().toInstant();
        if (!issuedAt.equals(request.getApprovedAt().toInstant())
                || !expiresAt.equals(issuedAt.plus(CODE_LIFETIME))
                || usedAt.isBefore(issuedAt)
                || !usedAt.isBefore(expiresAt)
                || !usedAt.equals(eventAt)
                || !isWithinDatabaseClockSkew(code.getCreatedAt(), issuedAt)
                || !isWithinDatabaseClockSkew(code.getUpdatedAt(), eventAt)) {
            throw invalidReceipt();
        }
    }

    private static void requireFamily(
            BoardOAuthAuthorizationCode code,
            BoardOAuthTokenFamily family,
            Instant eventAt) {
        if (!isPositive(family.getId())
                || !isFamilyIdentifier(family.getFamilyId())
                || !Objects.equals(
                        family.getOriginAuthorizationCodeId(), code.getId())
                || !sameString(family.getClientId(), code.getClientId())
                || !sameIdentity(code, family)
                || !Objects.equals(
                        family.getConsentIntent(), code.getConsentIntent())
                || !hasFixedProfile(
                        family.getIssuerUri(),
                        family.getResourceUri(),
                        family.getProductCode(),
                        family.getSourceCode(),
                        family.getConnectorCode(),
                        family.getScopeCanonical(),
                        family.getScopeDigest())
                || family.getBindingId() != null
                || family.getBindingVersion() != null
                || !"PENDING_BINDING".equals(family.getStatus())
                || !"PENDING_BINDING".equals(family.getLifecycleSlot())
                || !Objects.equals(family.getCurrentRefreshGeneration(), 0L)
                || family.getIssuedAt() == null
                || family.getActivatedAt() != null
                || family.getExpiresAt() == null
                || family.getTerminatedAt() != null
                || !Objects.equals(family.getVersion(), 0L)
                || !hasOrderedRowTimes(
                        family.getCreatedAt(), family.getUpdatedAt())) {
            throw invalidReceipt();
        }

        Instant issuedAt = family.getIssuedAt().toInstant();
        Instant expiresAt = family.getExpiresAt().toInstant();
        if (!issuedAt.equals(eventAt)
                || !expiresAt.isAfter(issuedAt)
                || expiresAt.isAfter(issuedAt.plus(MAX_FAMILY_LIFETIME))
                || !isWithinDatabaseClockSkew(family.getCreatedAt(), eventAt)
                || !isWithinDatabaseClockSkew(family.getUpdatedAt(), eventAt)) {
            throw invalidReceipt();
        }
    }

    private static void requireToken(
            BoardOAuthToken token,
            BoardOAuthTokenFamily family,
            Instant eventAt) {
        boolean access = "ACCESS".equals(token.getTokenType());
        boolean refresh = "REFRESH".equals(token.getTokenType());
        if (!isPositive(token.getId())
                || !hasDigest(token.getTokenDigest())
                || !sameString(token.getFamilyId(), family.getFamilyId())
                || (!access && !refresh)
                || !Objects.equals(token.getGeneration(), 0L)
                || !sameString(token.getResourceUri(), family.getResourceUri())
                || !sameString(
                        token.getScopeCanonical(), family.getScopeCanonical())
                || !sameDigest(token.getScopeDigest(), family.getScopeDigest())
                || !"ACTIVE".equals(token.getStatus())
                || (access && token.getActiveRefreshSlot() != null)
                || (refresh && !Objects.equals(token.getActiveRefreshSlot(), 1))
                || token.getIssuedAt() == null
                || token.getUsedAt() != null
                || token.getRevokedAt() != null
                || token.getExpiresAt() == null
                || !Objects.equals(token.getVersion(), 0L)
                || !hasOrderedRowTimes(token.getCreatedAt(), token.getUpdatedAt())) {
            throw invalidReceipt();
        }

        Instant issuedAt = token.getIssuedAt().toInstant();
        Instant expiresAt = token.getExpiresAt().toInstant();
        if (!issuedAt.equals(eventAt)
                || !expiresAt.isAfter(issuedAt)
                || (access && !expiresAt.equals(
                        issuedAt.plus(ACCESS_TOKEN_LIFETIME)))
                || (refresh && !expiresAt.equals(
                        family.getExpiresAt().toInstant()))
                || !isWithinDatabaseClockSkew(token.getCreatedAt(), eventAt)
                || !isWithinDatabaseClockSkew(token.getUpdatedAt(), eventAt)) {
            throw invalidReceipt();
        }
    }

    private static void requireReceiptShape(
            BoardOAuthReceipt receipt,
            CanonicalContext context) {
        BoardOAuthAuthorizationRequest request = context.request;
        BoardOAuthAuthorizationCode code = context.code;
        BoardOAuthTokenFamily family = context.family;
        byte[] expectedActor = BoardOAuthCrypto.sha256Ascii(request.getClientId());
        if (!isPositive(receipt.getId())
                || !isReceiptIdentifier(receipt.getReceiptId())
                || !ACTION.equals(receipt.getAction())
                || !sameString(receipt.getClientId(), request.getClientId())
                || receipt.getAuthorizationRequestId() != null
                || !Objects.equals(receipt.getAuthorizationCodeId(), code.getId())
                || !sameString(receipt.getFamilyId(), family.getFamilyId())
                || receipt.getTokenId() != null
                || receipt.getBindingId() != null
                || !Objects.equals(receipt.getTenantId(), family.getTenantId())
                || !Objects.equals(receipt.getMemberId(), family.getMemberId())
                || !Objects.equals(receipt.getUserId(), family.getUserId())
                || !sameDigest(
                        receipt.getPrincipalSubjectDigest(),
                        family.getPrincipalSubjectDigest())
                || !ACTOR_CLIENT.equals(receipt.getActorType())
                || receipt.getActorUserId() != null
                || !sameDigest(receipt.getActorSubjectDigest(), expectedActor)
                || !isReceiptIdentifier(receipt.getCorrelationId())
                || !EVIDENCE_ACTION_COMPLETED.equals(receipt.getEvidenceLevel())
                || receipt.getCreatedAt() == null
                || !receipt.getCreatedAt().toInstant().equals(context.createdAt)) {
            throw invalidReceipt();
        }
    }

    private static byte[] canonicalPayloadDigest(
            BoardOAuthReceipt receipt,
            CanonicalContext context) {
        CanonicalWriter writer = new CanonicalWriter();
        writer.text("receipt.receipt_id", receipt.getReceiptId());
        writer.text("receipt.action", receipt.getAction());
        writer.text("receipt.client_id", receipt.getClientId());
        writer.number("receipt.authorization_request_id",
                receipt.getAuthorizationRequestId());
        writer.number("receipt.authorization_code_id",
                receipt.getAuthorizationCodeId());
        writer.text("receipt.family_id", receipt.getFamilyId());
        writer.number("receipt.token_id", receipt.getTokenId());
        writer.text("receipt.binding_id", receipt.getBindingId());
        writer.number("receipt.enterprise_id", receipt.getTenantId());
        writer.number("receipt.member_id", receipt.getMemberId());
        writer.number("receipt.user_id", receipt.getUserId());
        writer.digest("receipt.principal_subject_digest",
                receipt.getPrincipalSubjectDigest());
        writer.text("receipt.actor_type", receipt.getActorType());
        writer.number("receipt.actor_user_id", receipt.getActorUserId());
        writer.digest("receipt.actor_subject_digest",
                receipt.getActorSubjectDigest());
        writer.text("receipt.correlation_id", receipt.getCorrelationId());
        writer.text("receipt.evidence_level", receipt.getEvidenceLevel());
        writer.time("receipt.created_at", receipt.getCreatedAt());

        appendRequest(writer, context.request);
        appendCode(writer, context.code);
        appendFamily(writer, context.family);
        appendToken(writer, "token.0", context.sortedTokens.get(0));
        appendToken(writer, "token.1", context.sortedTokens.get(1));
        return writer.digest();
    }

    private static void appendRequest(
            CanonicalWriter writer,
            BoardOAuthAuthorizationRequest request) {
        writer.number("request.id", request.getId());
        writer.digest("request.request_handle_digest",
                request.getRequestHandleDigest());
        writer.text("request.client_id", request.getClientId());
        writer.text("request.redirect_uri", request.getRedirectUri());
        writer.text("request.code_challenge", request.getCodeChallenge());
        writer.text("request.code_challenge_method",
                request.getCodeChallengeMethod());
        writer.digest("request.state_digest", request.getStateDigest());
        writer.text("request.state_key_ref", request.getStateKeyRef());
        writer.bytes("request.state_nonce", request.getStateNonce());
        writer.bytes("request.state_ciphertext", request.getStateCiphertext());
        writer.text("request.issuer_uri", request.getIssuerUri());
        writer.text("request.resource_uri", request.getResourceUri());
        writer.text("request.product_code", request.getProductCode());
        writer.text("request.source_code", request.getSourceCode());
        writer.text("request.connector_code", request.getConnectorCode());
        writer.text("request.scope_canonical", request.getScopeCanonical());
        writer.digest("request.scope_digest", request.getScopeDigest());
        writer.number("request.enterprise_id", request.getTenantId());
        writer.number("request.member_id", request.getMemberId());
        writer.number("request.user_id", request.getUserId());
        writer.digest("request.principal_subject_digest",
                request.getPrincipalSubjectDigest());
        writer.text("request.consent_intent", request.getConsentIntent().name());
        writer.text("request.status", request.getStatus());
        writer.time("request.requested_at", request.getRequestedAt());
        writer.time("request.expires_at", request.getExpiresAt());
        writer.time("request.approved_at", request.getApprovedAt());
        writer.time("request.denied_at", request.getDeniedAt());
        writer.time("request.consumed_at", request.getConsumedAt());
        writer.number("request.version", request.getVersion());
        writer.time("request.created_at", request.getCreatedAt());
        writer.time("request.updated_at", request.getUpdatedAt());
    }

    private static void appendCode(
            CanonicalWriter writer,
            BoardOAuthAuthorizationCode code) {
        writer.number("code.id", code.getId());
        writer.digest("code.code_digest", code.getCodeDigest());
        writer.number("code.authorization_request_id",
                code.getAuthorizationRequestId());
        writer.text("code.client_id", code.getClientId());
        writer.text("code.redirect_uri", code.getRedirectUri());
        writer.text("code.code_challenge", code.getCodeChallenge());
        writer.text("code.code_challenge_method", code.getCodeChallengeMethod());
        writer.text("code.issuer_uri", code.getIssuerUri());
        writer.text("code.resource_uri", code.getResourceUri());
        writer.text("code.product_code", code.getProductCode());
        writer.text("code.source_code", code.getSourceCode());
        writer.text("code.connector_code", code.getConnectorCode());
        writer.text("code.scope_canonical", code.getScopeCanonical());
        writer.digest("code.scope_digest", code.getScopeDigest());
        writer.number("code.enterprise_id", code.getTenantId());
        writer.number("code.member_id", code.getMemberId());
        writer.number("code.user_id", code.getUserId());
        writer.digest("code.principal_subject_digest",
                code.getPrincipalSubjectDigest());
        writer.text("code.consent_intent", code.getConsentIntent().name());
        writer.text("code.status", code.getStatus());
        writer.time("code.issued_at", code.getIssuedAt());
        writer.time("code.expires_at", code.getExpiresAt());
        writer.time("code.used_at", code.getUsedAt());
        writer.time("code.revoked_at", code.getRevokedAt());
        writer.number("code.version", code.getVersion());
        writer.time("code.created_at", code.getCreatedAt());
        writer.time("code.updated_at", code.getUpdatedAt());
    }

    private static void appendFamily(
            CanonicalWriter writer,
            BoardOAuthTokenFamily family) {
        writer.number("family.id", family.getId());
        writer.text("family.family_id", family.getFamilyId());
        writer.number("family.origin_authorization_code_id",
                family.getOriginAuthorizationCodeId());
        writer.text("family.client_id", family.getClientId());
        writer.number("family.enterprise_id", family.getTenantId());
        writer.number("family.member_id", family.getMemberId());
        writer.number("family.user_id", family.getUserId());
        writer.text("family.product_code", family.getProductCode());
        writer.text("family.source_code", family.getSourceCode());
        writer.text("family.connector_code", family.getConnectorCode());
        writer.text("family.issuer_uri", family.getIssuerUri());
        writer.text("family.resource_uri", family.getResourceUri());
        writer.text("family.scope_canonical", family.getScopeCanonical());
        writer.digest("family.scope_digest", family.getScopeDigest());
        writer.digest("family.principal_subject_digest",
                family.getPrincipalSubjectDigest());
        writer.text("family.consent_intent", family.getConsentIntent().name());
        writer.text("family.binding_id", family.getBindingId());
        writer.number("family.binding_version", family.getBindingVersion());
        writer.text("family.status", family.getStatus());
        writer.text("family.lifecycle_slot", family.getLifecycleSlot());
        writer.number("family.current_refresh_generation",
                family.getCurrentRefreshGeneration());
        writer.time("family.issued_at", family.getIssuedAt());
        writer.time("family.activated_at", family.getActivatedAt());
        writer.time("family.expires_at", family.getExpiresAt());
        writer.time("family.terminated_at", family.getTerminatedAt());
        writer.number("family.version", family.getVersion());
        writer.time("family.created_at", family.getCreatedAt());
        writer.time("family.updated_at", family.getUpdatedAt());
    }

    private static void appendToken(
            CanonicalWriter writer,
            String prefix,
            BoardOAuthToken token) {
        writer.number(prefix + ".id", token.getId());
        writer.digest(prefix + ".token_digest", token.getTokenDigest());
        writer.text(prefix + ".family_id", token.getFamilyId());
        writer.text(prefix + ".token_type", token.getTokenType());
        writer.number(prefix + ".generation", token.getGeneration());
        writer.text(prefix + ".resource_uri", token.getResourceUri());
        writer.text(prefix + ".scope_canonical", token.getScopeCanonical());
        writer.digest(prefix + ".scope_digest", token.getScopeDigest());
        writer.text(prefix + ".status", token.getStatus());
        writer.number(prefix + ".active_refresh_slot",
                token.getActiveRefreshSlot());
        writer.time(prefix + ".issued_at", token.getIssuedAt());
        writer.time(prefix + ".used_at", token.getUsedAt());
        writer.time(prefix + ".revoked_at", token.getRevokedAt());
        writer.time(prefix + ".expires_at", token.getExpiresAt());
        writer.number(prefix + ".version", token.getVersion());
        writer.time(prefix + ".created_at", token.getCreatedAt());
        writer.time(prefix + ".updated_at", token.getUpdatedAt());
    }

    private static boolean hasFixedProfile(
            String issuerUri,
            String resourceUri,
            String productCode,
            String sourceCode,
            String connectorCode,
            String scopeCanonical,
            byte[] scopeDigest) {
        byte[] expectedScope = BoardOAuthCrypto.sha256Ascii(
                BoardOAuthProfile.CANONICAL_SCOPE);
        return BoardOAuthProfile.ISSUER.equals(issuerUri)
                && BoardOAuthProfile.RESOURCE.equals(resourceUri)
                && PRODUCT_CODE.equals(productCode)
                && SOURCE_CODE.equals(sourceCode)
                && CONNECTOR_CODE.equals(connectorCode)
                && BoardOAuthProfile.CANONICAL_SCOPE.equals(scopeCanonical)
                && sameDigest(scopeDigest, expectedScope);
    }

    private static boolean hasIdentity(
            Long tenantId,
            Long memberId,
            Long userId,
            byte[] principalSubjectDigest) {
        if (!isPositive(tenantId)
                || !isPositive(memberId)
                || !isPositive(userId)
                || !hasDigest(principalSubjectDigest)) {
            return false;
        }
        try {
            return sameDigest(
                    principalSubjectDigest,
                    BoardOAuthPrincipalSubject.digest(
                            tenantId, memberId, userId));
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private static boolean sameIdentity(
            BoardOAuthAuthorizationRequest request,
            BoardOAuthAuthorizationCode code) {
        return Objects.equals(request.getTenantId(), code.getTenantId())
                && Objects.equals(request.getMemberId(), code.getMemberId())
                && Objects.equals(request.getUserId(), code.getUserId())
                && sameDigest(
                        request.getPrincipalSubjectDigest(),
                        code.getPrincipalSubjectDigest());
    }

    private static boolean sameIdentity(
            BoardOAuthAuthorizationCode code,
            BoardOAuthTokenFamily family) {
        return Objects.equals(code.getTenantId(), family.getTenantId())
                && Objects.equals(code.getMemberId(), family.getMemberId())
                && Objects.equals(code.getUserId(), family.getUserId())
                && sameDigest(
                        code.getPrincipalSubjectDigest(),
                        family.getPrincipalSubjectDigest());
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

    private static boolean isMillisecondInstant(Instant value) {
        return value != null
                && value.equals(Instant.ofEpochMilli(value.toEpochMilli()));
    }

    private static boolean isReceiptIdentifier(String value) {
        return value != null && RECEIPT_IDENTIFIER.matcher(value).matches();
    }

    private static boolean isClientIdentifier(String value) {
        return value != null && CLIENT_IDENTIFIER.matcher(value).matches();
    }

    private static boolean isFamilyIdentifier(String value) {
        return value != null && FAMILY_IDENTIFIER.matcher(value).matches();
    }

    private static boolean isPositive(Long value) {
        return value != null && value > 0L;
    }

    private static long requirePositive(Long value) {
        if (!isPositive(value)) {
            throw invalidReceipt();
        }
        return value;
    }

    private static boolean hasDigest(byte[] value) {
        return value != null && value.length == BoardOAuthCrypto.SHA256_BYTES;
    }

    private static boolean sameDigest(byte[] left, byte[] right) {
        return hasDigest(left)
                && hasDigest(right)
                && BoardOAuthCrypto.constantTimeEquals(left, right);
    }

    private static boolean sameString(String left, String right) {
        return Objects.equals(left, right);
    }

    private static BoardOAuthProtocolException invalidReceipt() {
        return BoardOAuthProtocolException.serverError(RECEIPT_INVALID);
    }

    private static final class CanonicalContext {
        private final BoardOAuthAuthorizationRequest request;
        private final BoardOAuthAuthorizationCode code;
        private final BoardOAuthTokenFamily family;
        private final List<BoardOAuthToken> sortedTokens;
        private final Instant createdAt;

        private CanonicalContext(
                BoardOAuthAuthorizationRequest request,
                BoardOAuthAuthorizationCode code,
                BoardOAuthTokenFamily family,
                List<BoardOAuthToken> sortedTokens,
                Instant createdAt) {
            this.request = request;
            this.code = code;
            this.family = family;
            this.sortedTokens = sortedTokens;
            this.createdAt = createdAt;
        }

        @Override
        public String toString() {
            return "CanonicalContext[REDACTED]";
        }
    }

    private static final class CanonicalWriter {
        private final StringBuilder value = new StringBuilder(
                CANONICAL_DOMAIN_V2).append('\n');

        private void text(String name, String fieldValue) {
            field(name, fieldValue);
        }

        private void number(String name, Number fieldValue) {
            field(name, fieldValue == null ? null : fieldValue.toString());
        }

        private void time(String name, Date fieldValue) {
            field(name, fieldValue == null
                    ? null
                    : Long.toString(fieldValue.getTime()));
        }

        private void digest(String name, byte[] fieldValue) {
            if (fieldValue != null && !hasDigest(fieldValue)) {
                throw invalidReceipt();
            }
            bytes(name, fieldValue);
        }

        private void bytes(String name, byte[] fieldValue) {
            field(name, fieldValue == null
                    ? null
                    : HexFormat.of().formatHex(fieldValue));
        }

        private void field(String name, String fieldValue) {
            if (!isAscii(name) || (fieldValue != null && !isAscii(fieldValue))) {
                throw invalidReceipt();
            }
            value.append(name).append(':');
            if (fieldValue == null) {
                value.append("-1:");
            } else {
                value.append(fieldValue.length()).append(':').append(fieldValue);
            }
            value.append('\n');
        }

        private byte[] digest() {
            return BoardOAuthCrypto.sha256Ascii(value.toString());
        }

        private static boolean isAscii(String candidate) {
            if (candidate == null) {
                return false;
            }
            for (int index = 0; index < candidate.length(); index++) {
                if (candidate.charAt(index) > 0x7f) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public String toString() {
            return "CanonicalWriter[REDACTED]";
        }
    }
}
