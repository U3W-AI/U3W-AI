package com.wx.fbsir.business.board.oauth;

import com.wx.fbsir.business.board.oauth.domain.BoardOAuthAuthorizationCode;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthAuthorizationRequest;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthReceipt;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthToken;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthTokenFamily;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Canonical, digest-only receipts for authorization-code replay containment. */
public final class BoardOAuthTokenSecurityReceiptFactory {
    public static final String RECEIPT_INVALID =
            "OAUTH_TOKEN_SECURITY_RECEIPT_INVALID";

    public static final String AUTHORIZATION_CODE_REPLAY_DETECTED =
            "AUTHORIZATION_CODE_REPLAY_DETECTED";
    public static final String TOKEN_FAMILY_COMPROMISED =
            "TOKEN_FAMILY_COMPROMISED";
    public static final String TOKEN_FAMILY_REVOKED = "TOKEN_FAMILY_REVOKED";
    private static final String TOKEN_FAMILY_CREATED = "TOKEN_FAMILY_CREATED";

    private static final String REPLAY_DOMAIN =
            "FBSIR:OAUTH:AUTHORIZATION_CODE_REPLAY_RECEIPT:v1";
    private static final String COMPROMISED_DOMAIN =
            "FBSIR:OAUTH:TOKEN_FAMILY_COMPROMISED_RECEIPT:v1";
    private static final String REVOKED_DOMAIN =
            "FBSIR:OAUTH:TOKEN_FAMILY_REVOKED_RECEIPT:v2";
    private static final String SUPERSESSION_CAUSE =
            "SUPERSEDED_BY_AUTHORIZATION_CODE_EXCHANGE";
    private static final String ACTOR_CLIENT = "CLIENT";
    private static final String EVIDENCE_ACTION_COMPLETED = "ACTION_COMPLETED";
    private static final String PRODUCT_CODE = "FBSIR_INDEPENDENT_BOARD";
    private static final String SOURCE_CODE = "WORKBUDDY";
    private static final String CONNECTOR_CODE = "fbs-connector";
    private static final Set<String> TERMINAL_TOKEN_STATUSES =
            Set.of("USED", "REVOKED", "EXPIRED");
    private static final Set<String> REPLAY_FAMILY_STATUSES =
            Set.of("REVOKED", "COMPROMISED", "EXPIRED");
    private static final Pattern IDENTIFIER =
            Pattern.compile("[A-Za-z0-9_-]{1,191}");

    private BoardOAuthTokenSecurityReceiptFactory() {
    }

    public static BoardOAuthReceipt authorizationCodeReplayDetected(
            String receiptId,
            String correlationId,
            Instant createdAt,
            BoardOAuthAuthorizationRequest request,
            BoardOAuthAuthorizationCode code,
            BoardOAuthTokenFamily family,
            List<BoardOAuthToken> tokens,
            BoardOAuthReceipt creationReceipt) {
        return create(
                REPLAY_DOMAIN,
                AUTHORIZATION_CODE_REPLAY_DETECTED,
                receiptId,
                correlationId,
                createdAt,
                request,
                code,
                family,
                tokens,
                creationReceipt,
                null);
    }

    public static BoardOAuthReceipt tokenFamilyCompromised(
            String receiptId,
            String correlationId,
            Instant createdAt,
            BoardOAuthAuthorizationRequest request,
            BoardOAuthAuthorizationCode code,
            BoardOAuthTokenFamily family,
            List<BoardOAuthToken> tokens,
            BoardOAuthReceipt creationReceipt) {
        return create(
                COMPROMISED_DOMAIN,
                TOKEN_FAMILY_COMPROMISED,
                receiptId,
                correlationId,
                createdAt,
                request,
                code,
                family,
                tokens,
                creationReceipt,
                null);
    }

    public static BoardOAuthReceipt tokenFamilyRevoked(
            String receiptId,
            String correlationId,
            Instant createdAt,
            BoardOAuthTokenFamily family,
            List<BoardOAuthToken> tokens,
            BoardOAuthReceipt creationReceipt,
            BoardOAuthAuthorizationRequest successorRequest,
            BoardOAuthAuthorizationCode successorCode,
            String successorFamilyId,
            String successorCreationReceiptId) {
        return create(
                REVOKED_DOMAIN,
                TOKEN_FAMILY_REVOKED,
                receiptId,
                correlationId,
                createdAt,
                null,
                null,
                family,
                tokens,
                creationReceipt,
                new Supersession(
                        successorRequest,
                        successorCode,
                        successorFamilyId,
                        successorCreationReceiptId));
    }

    public static void validateAuthorizationCodeReplayDetected(
            BoardOAuthReceipt receipt,
            BoardOAuthAuthorizationRequest request,
            BoardOAuthAuthorizationCode code,
            BoardOAuthTokenFamily family,
            List<BoardOAuthToken> tokens,
            BoardOAuthReceipt creationReceipt) {
        validate(
                REPLAY_DOMAIN,
                AUTHORIZATION_CODE_REPLAY_DETECTED,
                receipt,
                request,
                code,
                family,
                tokens,
                creationReceipt,
                null);
    }

    public static void validateTokenFamilyCompromised(
            BoardOAuthReceipt receipt,
            BoardOAuthAuthorizationRequest request,
            BoardOAuthAuthorizationCode code,
            BoardOAuthTokenFamily family,
            List<BoardOAuthToken> tokens,
            BoardOAuthReceipt creationReceipt) {
        validate(
                COMPROMISED_DOMAIN,
                TOKEN_FAMILY_COMPROMISED,
                receipt,
                request,
                code,
                family,
                tokens,
                creationReceipt,
                null);
    }

    public static void validateTokenFamilyRevoked(
            BoardOAuthReceipt receipt,
            BoardOAuthTokenFamily family,
            List<BoardOAuthToken> tokens,
            BoardOAuthReceipt creationReceipt,
            BoardOAuthAuthorizationRequest successorRequest,
            BoardOAuthAuthorizationCode successorCode,
            String successorFamilyId,
            String successorCreationReceiptId) {
        validate(
                REVOKED_DOMAIN,
                TOKEN_FAMILY_REVOKED,
                receipt,
                null,
                null,
                family,
                tokens,
                creationReceipt,
                new Supersession(
                        successorRequest,
                        successorCode,
                        successorFamilyId,
                        successorCreationReceiptId));
    }

    private static BoardOAuthReceipt create(
            String domain,
            String action,
            String receiptId,
            String correlationId,
            Instant createdAt,
            BoardOAuthAuthorizationRequest request,
            BoardOAuthAuthorizationCode code,
            BoardOAuthTokenFamily family,
            List<BoardOAuthToken> tokens,
            BoardOAuthReceipt creationReceipt,
            Supersession supersession) {
        try {
            CanonicalContext context = requireContext(
                    action,
                    receiptId,
                    correlationId,
                    createdAt,
                    request,
                    code,
                    family,
                    tokens,
                    creationReceipt,
                    supersession);
            BoardOAuthReceipt receipt = new BoardOAuthReceipt();
            receipt.setReceiptId(receiptId);
            receipt.setAction(action);
            receipt.setClientId(family.getClientId());
            receipt.setAuthorizationRequestId(null);
            receipt.setAuthorizationCodeId(
                    AUTHORIZATION_CODE_REPLAY_DETECTED.equals(action)
                            ? code.getId() : null);
            receipt.setFamilyId(family.getFamilyId());
            receipt.setTokenId(null);
            receipt.setBindingId(family.getBindingId());
            receipt.setTenantId(family.getTenantId());
            receipt.setMemberId(family.getMemberId());
            receipt.setUserId(family.getUserId());
            receipt.setPrincipalSubjectDigest(
                    family.getPrincipalSubjectDigest().clone());
            receipt.setActorType(ACTOR_CLIENT);
            receipt.setActorUserId(null);
            receipt.setActorSubjectDigest(
                    BoardOAuthCrypto.sha256Ascii(
                            supersession == null
                                    ? family.getClientId()
                                    : supersession.code.getClientId()));
            receipt.setCorrelationId(correlationId);
            receipt.setEvidenceLevel(EVIDENCE_ACTION_COMPLETED);
            receipt.setCreatedAt(Date.from(createdAt));
            receipt.setPayloadDigest(canonicalDigest(domain, receipt, context));
            return receipt;
        } catch (BoardOAuthProtocolException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw invalidReceipt();
        }
    }

    private static void validate(
            String domain,
            String action,
            BoardOAuthReceipt receipt,
            BoardOAuthAuthorizationRequest request,
            BoardOAuthAuthorizationCode code,
            BoardOAuthTokenFamily family,
            List<BoardOAuthToken> tokens,
            BoardOAuthReceipt creationReceipt,
            Supersession supersession) {
        try {
            if (receipt == null || receipt.getCreatedAt() == null) {
                throw invalidReceipt();
            }
            CanonicalContext context = requireContext(
                    action,
                    receipt.getReceiptId(),
                    receipt.getCorrelationId(),
                    receipt.getCreatedAt().toInstant(),
                    request,
                    code,
                    family,
                    tokens,
                    creationReceipt,
                    supersession);
            requireReceiptShape(action, receipt, context);
            byte[] expected = canonicalDigest(domain, receipt, context);
            if (!sameDigest(receipt.getPayloadDigest(), expected)) {
                throw invalidReceipt();
            }
        } catch (BoardOAuthProtocolException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw invalidReceipt();
        }
    }

    private static CanonicalContext requireContext(
            String action,
            String receiptId,
            String correlationId,
            Instant eventAt,
            BoardOAuthAuthorizationRequest request,
            BoardOAuthAuthorizationCode code,
            BoardOAuthTokenFamily family,
            List<BoardOAuthToken> tokens,
            BoardOAuthReceipt creationReceipt,
            Supersession supersession) {
        if (!isIdentifier(receiptId)
                || !isIdentifier(correlationId)
                || eventAt == null
                || eventAt.getNano() % 1_000_000 != 0
                || family == null
                || tokens == null
                || creationReceipt == null) {
            throw invalidReceipt();
        }
        requireFamily(action, family, eventAt);
        List<BoardOAuthToken> sortedTokens = requireTerminalTokens(
                action, family, tokens, eventAt);
        requireCreationReceiptReference(family, creationReceipt);
        if (TOKEN_FAMILY_REVOKED.equals(action)) {
            if (request != null || code != null || supersession == null) {
                throw invalidReceipt();
            }
            requireSupersession(family, supersession);
        } else {
            if (supersession != null) {
                throw invalidReceipt();
            }
            requireConsumedLineage(request, code, family);
        }
        return new CanonicalContext(
                request,
                code,
                family,
                sortedTokens,
                creationReceipt,
                supersession);
    }

    private static void requireSupersession(
            BoardOAuthTokenFamily oldFamily,
            Supersession supersession) {
        BoardOAuthAuthorizationRequest request = supersession.request;
        BoardOAuthAuthorizationCode code = supersession.code;
        if (request == null
                || code == null
                || !isPositive(request.getId())
                || !isPositive(code.getId())
                || !Objects.equals(code.getAuthorizationRequestId(), request.getId())
                || Objects.equals(
                        code.getId(), oldFamily.getOriginAuthorizationCodeId())
                || !isIdentifier(request.getClientId())
                || !Objects.equals(code.getClientId(), request.getClientId())
                || !isIdentifier(supersession.familyId)
                || !isIdentifier(supersession.creationReceiptId)
                || Objects.equals(supersession.familyId, oldFamily.getFamilyId())
                || !Set.of("APPROVED", "CONSUMED").contains(request.getStatus())
                || !Set.of("ACTIVE", "USED").contains(code.getStatus())
                || !hasDigest(code.getCodeDigest())
                || request.getConsentIntent() == null
                || !Objects.equals(request.getConsentIntent(), code.getConsentIntent())
                || !Objects.equals(request.getConsentIntent(), oldFamily.getConsentIntent())
                || !sameIdentity(request, code, oldFamily)
                || !hasFixedProfile(request, code, oldFamily)) {
            throw invalidReceipt();
        }
    }

    private static void requireConsumedLineage(
            BoardOAuthAuthorizationRequest request,
            BoardOAuthAuthorizationCode code,
            BoardOAuthTokenFamily family) {
        if (request == null
                || code == null
                || !isPositive(request.getId())
                || !isPositive(code.getId())
                || !Objects.equals(code.getAuthorizationRequestId(), request.getId())
                || !Objects.equals(family.getOriginAuthorizationCodeId(), code.getId())
                || !Objects.equals(request.getClientId(), family.getClientId())
                || !Objects.equals(code.getClientId(), family.getClientId())
                || !"CONSUMED".equals(request.getStatus())
                || !Objects.equals(request.getVersion(), 2L)
                || request.getConsumedAt() == null
                || request.getStateKeyRef() != null
                || request.getStateNonce() != null
                || request.getStateCiphertext() != null
                || !"USED".equals(code.getStatus())
                || !Objects.equals(code.getVersion(), 1L)
                || code.getUsedAt() == null
                || !sameDate(request.getConsumedAt(), code.getUsedAt())
                || !Objects.equals(request.getRedirectUri(), code.getRedirectUri())
                || !Objects.equals(request.getCodeChallenge(), code.getCodeChallenge())
                || !Objects.equals(
                        request.getCodeChallengeMethod(), code.getCodeChallengeMethod())
                || request.getConsentIntent() == null
                || !Objects.equals(request.getConsentIntent(), code.getConsentIntent())
                || !Objects.equals(code.getConsentIntent(), family.getConsentIntent())
                || !sameIdentity(request, code, family)
                || !hasFixedProfile(request, code, family)) {
            throw invalidReceipt();
        }
    }

    private static void requireFamily(
            String action,
            BoardOAuthTokenFamily family,
            Instant eventAt) {
        String expectedStatus = TOKEN_FAMILY_COMPROMISED.equals(action)
                ? "COMPROMISED"
                : TOKEN_FAMILY_REVOKED.equals(action) ? "REVOKED" : null;
        if (!isPositive(family.getId())
                || !isIdentifier(family.getFamilyId())
                || !isPositive(family.getOriginAuthorizationCodeId())
                || !isIdentifier(family.getClientId())
                || !hasIdentity(
                        family.getTenantId(),
                        family.getMemberId(),
                        family.getUserId(),
                        family.getPrincipalSubjectDigest())
                || !PRODUCT_CODE.equals(family.getProductCode())
                || !SOURCE_CODE.equals(family.getSourceCode())
                || !CONNECTOR_CODE.equals(family.getConnectorCode())
                || !BoardOAuthProfile.ISSUER.equals(family.getIssuerUri())
                || !BoardOAuthProfile.RESOURCE.equals(family.getResourceUri())
                || !BoardOAuthProfile.CANONICAL_SCOPE.equals(
                        family.getScopeCanonical())
                || !sameDigest(
                        family.getScopeDigest(),
                        BoardOAuthCrypto.sha256Ascii(
                                BoardOAuthProfile.CANONICAL_SCOPE))
                || family.getConsentIntent() == null
                || family.getIssuedAt() == null
                || family.getExpiresAt() == null
                || family.getTerminatedAt() == null
                || family.getIssuedAt().after(family.getTerminatedAt())
                || !family.getExpiresAt().after(family.getIssuedAt())
                || family.getVersion() == null
                || family.getVersion() <= 0L) {
            throw invalidReceipt();
        }
        if (AUTHORIZATION_CODE_REPLAY_DETECTED.equals(action)) {
            if (family.getTerminatedAt().after(Date.from(eventAt))) {
                throw invalidReceipt();
            }
        } else if (!sameDate(family.getTerminatedAt(), Date.from(eventAt))) {
            throw invalidReceipt();
        }
        if (expectedStatus != null && !expectedStatus.equals(family.getStatus())) {
            throw invalidReceipt();
        }
        if (AUTHORIZATION_CODE_REPLAY_DETECTED.equals(action)
                && !REPLAY_FAMILY_STATUSES.contains(family.getStatus())) {
            throw invalidReceipt();
        }
        if ((family.getBindingId() == null) != (family.getBindingVersion() == null)) {
            throw invalidReceipt();
        }
    }

    private static List<BoardOAuthToken> requireTerminalTokens(
            String action,
            BoardOAuthTokenFamily family,
            List<BoardOAuthToken> tokens,
            Instant eventAt) {
        if (tokens.size() < 2) {
            throw invalidReceipt();
        }
        List<BoardOAuthToken> sorted = new ArrayList<>(tokens);
        sorted.sort(Comparator.comparing(BoardOAuthToken::getId,
                Comparator.nullsFirst(Long::compareTo)));
        Set<Long> ids = new HashSet<>();
        Set<String> digests = new HashSet<>();
        for (BoardOAuthToken token : sorted) {
            if (token == null
                    || !isPositive(token.getId())
                    || !ids.add(token.getId())
                    || !hasDigest(token.getTokenDigest())
                    || !digests.add(HexFormat.of().formatHex(
                            token.getTokenDigest()))
                    || !Objects.equals(token.getFamilyId(), family.getFamilyId())
                    || !("ACCESS".equals(token.getTokenType())
                            || "REFRESH".equals(token.getTokenType()))
                    || token.getGeneration() == null
                    || token.getGeneration() < 0L
                    || !BoardOAuthProfile.RESOURCE.equals(token.getResourceUri())
                    || !BoardOAuthProfile.CANONICAL_SCOPE.equals(
                            token.getScopeCanonical())
                    || !sameDigest(token.getScopeDigest(), family.getScopeDigest())
                    || !TERMINAL_TOKEN_STATUSES.contains(token.getStatus())
                    || token.getIssuedAt() == null
                    || token.getExpiresAt() == null
                    || token.getIssuedAt().after(token.getExpiresAt())
                    || token.getVersion() == null
                    || token.getVersion() < 0L) {
                throw invalidReceipt();
            }
            if ("REVOKED".equals(token.getStatus())) {
                if (token.getRevokedAt() == null
                        || token.getRevokedAt().after(Date.from(eventAt))) {
                    throw invalidReceipt();
                }
            } else if (token.getRevokedAt() != null) {
                throw invalidReceipt();
            }
            if ("USED".equals(token.getStatus())
                    && (!"REFRESH".equals(token.getTokenType())
                            || token.getUsedAt() == null)) {
                throw invalidReceipt();
            }
            if (!"USED".equals(token.getStatus()) && token.getUsedAt() != null) {
                throw invalidReceipt();
            }
        }
        return List.copyOf(sorted);
    }

    private static void requireCreationReceiptReference(
            BoardOAuthTokenFamily family,
            BoardOAuthReceipt receipt) {
        if (!isPositive(receipt.getId())
                || !isIdentifier(receipt.getReceiptId())
                || !TOKEN_FAMILY_CREATED.equals(receipt.getAction())
                || !Objects.equals(receipt.getClientId(), family.getClientId())
                || receipt.getAuthorizationRequestId() != null
                || !Objects.equals(
                        receipt.getAuthorizationCodeId(),
                        family.getOriginAuthorizationCodeId())
                || !Objects.equals(receipt.getFamilyId(), family.getFamilyId())
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
                || !sameDigest(
                        receipt.getActorSubjectDigest(),
                        BoardOAuthCrypto.sha256Ascii(family.getClientId()))
                || !isIdentifier(receipt.getCorrelationId())
                || !hasDigest(receipt.getPayloadDigest())
                || !EVIDENCE_ACTION_COMPLETED.equals(receipt.getEvidenceLevel())
                || receipt.getCreatedAt() == null
                || !sameDate(receipt.getCreatedAt(), family.getIssuedAt())) {
            throw invalidReceipt();
        }
    }

    private static void requireReceiptShape(
            String action,
            BoardOAuthReceipt receipt,
            CanonicalContext context) {
        BoardOAuthTokenFamily family = context.family;
        String actorClient = context.supersession == null
                ? family.getClientId()
                : context.supersession.code.getClientId();
        Long expectedCodeId = AUTHORIZATION_CODE_REPLAY_DETECTED.equals(action)
                ? context.code.getId() : null;
        if (!isPositive(receipt.getId())
                || !Objects.equals(receipt.getAction(), action)
                || !Objects.equals(receipt.getClientId(), family.getClientId())
                || receipt.getAuthorizationRequestId() != null
                || !Objects.equals(receipt.getAuthorizationCodeId(), expectedCodeId)
                || !Objects.equals(receipt.getFamilyId(), family.getFamilyId())
                || receipt.getTokenId() != null
                || !Objects.equals(receipt.getBindingId(), family.getBindingId())
                || !Objects.equals(receipt.getTenantId(), family.getTenantId())
                || !Objects.equals(receipt.getMemberId(), family.getMemberId())
                || !Objects.equals(receipt.getUserId(), family.getUserId())
                || !sameDigest(
                        receipt.getPrincipalSubjectDigest(),
                        family.getPrincipalSubjectDigest())
                || !ACTOR_CLIENT.equals(receipt.getActorType())
                || receipt.getActorUserId() != null
                || !sameDigest(
                        receipt.getActorSubjectDigest(),
                        BoardOAuthCrypto.sha256Ascii(actorClient))
                || !EVIDENCE_ACTION_COMPLETED.equals(receipt.getEvidenceLevel())) {
            throw invalidReceipt();
        }
    }

    private static byte[] canonicalDigest(
            String domain,
            BoardOAuthReceipt receipt,
            CanonicalContext context) {
        CanonicalWriter writer = new CanonicalWriter();
        writer.text("domain", domain);
        writer.text("receipt.id", receipt.getReceiptId());
        writer.text("receipt.action", receipt.getAction());
        writer.text("receipt.client", receipt.getClientId());
        writer.number("receipt.code", receipt.getAuthorizationCodeId());
        writer.text("receipt.family", receipt.getFamilyId());
        writer.text("receipt.binding", receipt.getBindingId());
        writer.number("receipt.tenant", receipt.getTenantId());
        writer.number("receipt.member", receipt.getMemberId());
        writer.number("receipt.user", receipt.getUserId());
        writer.digest("receipt.principal", receipt.getPrincipalSubjectDigest());
        writer.text("receipt.actor_type", receipt.getActorType());
        writer.digest("receipt.actor", receipt.getActorSubjectDigest());
        writer.text("receipt.correlation", receipt.getCorrelationId());
        writer.time("receipt.created", receipt.getCreatedAt());

        BoardOAuthTokenFamily family = context.family;
        writer.number("family.id", family.getId());
        writer.text("family.family_id", family.getFamilyId());
        writer.number("family.origin_code", family.getOriginAuthorizationCodeId());
        writer.text("family.client", family.getClientId());
        writer.text("family.status", family.getStatus());
        writer.text("family.binding", family.getBindingId());
        writer.number("family.binding_version", family.getBindingVersion());
        writer.number("family.generation", family.getCurrentRefreshGeneration());
        writer.text("family.intent", family.getConsentIntent().name());
        writer.time("family.issued", family.getIssuedAt());
        writer.time("family.activated", family.getActivatedAt());
        writer.time("family.expires", family.getExpiresAt());
        writer.time("family.terminated", family.getTerminatedAt());
        writer.number("family.version", family.getVersion());

        if (context.request != null) {
            writer.number("request.id", context.request.getId());
            writer.text("request.status", context.request.getStatus());
            writer.time("request.consumed", context.request.getConsumedAt());
            writer.number("request.version", context.request.getVersion());
            writer.digest("request.principal",
                    context.request.getPrincipalSubjectDigest());
        }
        if (context.code != null) {
            writer.number("code.id", context.code.getId());
            writer.digest("code.digest", context.code.getCodeDigest());
            writer.text("code.status", context.code.getStatus());
            writer.time("code.used", context.code.getUsedAt());
            writer.number("code.version", context.code.getVersion());
        }
        writer.text("creation.id", context.creationReceipt.getReceiptId());
        writer.text("creation.correlation",
                context.creationReceipt.getCorrelationId());
        writer.digest("creation.payload",
                context.creationReceipt.getPayloadDigest());
        writer.time("creation.created", context.creationReceipt.getCreatedAt());

        if (context.supersession != null) {
            Supersession successor = context.supersession;
            writer.text("successor.cause", SUPERSESSION_CAUSE);
            writer.number("successor.request", successor.request.getId());
            writer.number("successor.code", successor.code.getId());
            writer.digest("successor.code_digest", successor.code.getCodeDigest());
            writer.text("successor.client", successor.code.getClientId());
            writer.text("successor.family", successor.familyId);
            writer.text("successor.creation_receipt", successor.creationReceiptId);
            writer.text(
                    "successor.intent",
                    successor.code.getConsentIntent().name());
            writer.number("successor.tenant", successor.code.getTenantId());
            writer.number("successor.member", successor.code.getMemberId());
            writer.number("successor.user", successor.code.getUserId());
            writer.digest(
                    "successor.principal",
                    successor.code.getPrincipalSubjectDigest());
        }

        writer.number("tokens.count", (long) context.tokens.size());
        for (int index = 0; index < context.tokens.size(); index++) {
            BoardOAuthToken token = context.tokens.get(index);
            String prefix = "token." + index + ".";
            writer.number(prefix + "id", token.getId());
            writer.digest(prefix + "digest", token.getTokenDigest());
            writer.text(prefix + "type", token.getTokenType());
            writer.number(prefix + "generation", token.getGeneration());
            writer.text(prefix + "status", token.getStatus());
            writer.time(prefix + "issued", token.getIssuedAt());
            writer.time(prefix + "used", token.getUsedAt());
            writer.time(prefix + "revoked", token.getRevokedAt());
            writer.time(prefix + "expires", token.getExpiresAt());
            writer.number(prefix + "version", token.getVersion());
        }
        return BoardOAuthCrypto.sha256Ascii(writer.value());
    }

    private static boolean sameIdentity(
            BoardOAuthAuthorizationRequest request,
            BoardOAuthAuthorizationCode code,
            BoardOAuthTokenFamily family) {
        return Objects.equals(request.getTenantId(), family.getTenantId())
                && Objects.equals(request.getMemberId(), family.getMemberId())
                && Objects.equals(request.getUserId(), family.getUserId())
                && Objects.equals(code.getTenantId(), family.getTenantId())
                && Objects.equals(code.getMemberId(), family.getMemberId())
                && Objects.equals(code.getUserId(), family.getUserId())
                && sameDigest(
                        request.getPrincipalSubjectDigest(),
                        family.getPrincipalSubjectDigest())
                && sameDigest(
                        code.getPrincipalSubjectDigest(),
                        family.getPrincipalSubjectDigest());
    }

    private static boolean hasFixedProfile(
            BoardOAuthAuthorizationRequest request,
            BoardOAuthAuthorizationCode code,
            BoardOAuthTokenFamily family) {
        return Objects.equals(request.getIssuerUri(), family.getIssuerUri())
                && Objects.equals(request.getResourceUri(), family.getResourceUri())
                && Objects.equals(request.getProductCode(), family.getProductCode())
                && Objects.equals(request.getSourceCode(), family.getSourceCode())
                && Objects.equals(request.getConnectorCode(), family.getConnectorCode())
                && Objects.equals(
                        request.getScopeCanonical(), family.getScopeCanonical())
                && sameDigest(request.getScopeDigest(), family.getScopeDigest())
                && Objects.equals(code.getIssuerUri(), family.getIssuerUri())
                && Objects.equals(code.getResourceUri(), family.getResourceUri())
                && Objects.equals(code.getProductCode(), family.getProductCode())
                && Objects.equals(code.getSourceCode(), family.getSourceCode())
                && Objects.equals(code.getConnectorCode(), family.getConnectorCode())
                && Objects.equals(code.getScopeCanonical(), family.getScopeCanonical())
                && sameDigest(code.getScopeDigest(), family.getScopeDigest());
    }

    private static boolean hasIdentity(
            Long tenantId,
            Long memberId,
            Long userId,
            byte[] digest) {
        return isPositive(tenantId)
                && isPositive(memberId)
                && isPositive(userId)
                && sameDigest(
                        digest,
                        BoardOAuthPrincipalSubject.digest(
                                tenantId, memberId, userId));
    }

    private static boolean isPositive(Long value) {
        return value != null && value > 0L;
    }

    private static boolean isIdentifier(String value) {
        return value != null && IDENTIFIER.matcher(value).matches();
    }

    private static boolean hasDigest(byte[] value) {
        return value != null && value.length == BoardOAuthCrypto.SHA256_BYTES;
    }

    private static boolean sameDigest(byte[] left, byte[] right) {
        return hasDigest(left)
                && hasDigest(right)
                && BoardOAuthCrypto.constantTimeEquals(left, right);
    }

    private static boolean sameDate(Date left, Date right) {
        return left != null && right != null && left.getTime() == right.getTime();
    }

    private static BoardOAuthProtocolException invalidReceipt() {
        return BoardOAuthProtocolException.serverError(RECEIPT_INVALID);
    }

    private static final class CanonicalContext {
        private final BoardOAuthAuthorizationRequest request;
        private final BoardOAuthAuthorizationCode code;
        private final BoardOAuthTokenFamily family;
        private final List<BoardOAuthToken> tokens;
        private final BoardOAuthReceipt creationReceipt;
        private final Supersession supersession;

        private CanonicalContext(
                BoardOAuthAuthorizationRequest request,
                BoardOAuthAuthorizationCode code,
                BoardOAuthTokenFamily family,
                List<BoardOAuthToken> tokens,
                BoardOAuthReceipt creationReceipt,
                Supersession supersession) {
            this.request = request;
            this.code = code;
            this.family = family;
            this.tokens = tokens;
            this.creationReceipt = creationReceipt;
            this.supersession = supersession;
        }
    }

    private static final class Supersession {
        private final BoardOAuthAuthorizationRequest request;
        private final BoardOAuthAuthorizationCode code;
        private final String familyId;
        private final String creationReceiptId;

        private Supersession(
                BoardOAuthAuthorizationRequest request,
                BoardOAuthAuthorizationCode code,
                String familyId,
                String creationReceiptId) {
            this.request = request;
            this.code = code;
            this.familyId = familyId;
            this.creationReceiptId = creationReceiptId;
        }
    }

    private static final class CanonicalWriter {
        private final StringBuilder value = new StringBuilder();

        private void text(String name, String field) {
            append(name, field);
        }

        private void number(String name, Long field) {
            append(name, field == null ? null : Long.toString(field));
        }

        private void time(String name, Date field) {
            append(name, field == null ? null : Long.toString(field.getTime()));
        }

        private void digest(String name, byte[] field) {
            append(name, field == null ? null : HexFormat.of().formatHex(field));
        }

        private void append(String name, String field) {
            value.append(name.length()).append(':').append(name).append('=');
            if (field == null) {
                value.append("-1:");
            } else {
                value.append(field.length()).append(':').append(field);
            }
            value.append(';');
        }

        private String value() {
            return value.toString();
        }
    }
}
