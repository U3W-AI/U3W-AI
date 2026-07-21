package com.wx.fbsir.business.board.oauth;

import com.wx.fbsir.business.board.oauth.domain.BoardOAuthAuthorizationCode;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthAuthorizationRequest;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthReceipt;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Creates schema-valid, digest-only OAuth authorization receipts. */
@Component
public final class BoardOAuthReceiptFactory {
    public static final String RECEIPT_INVALID = "OAUTH_AUTHORIZATION_RECEIPT_INVALID";
    public static final String RECEIPT_ID_GENERATION_FAILED =
            "OAUTH_AUTHORIZATION_RECEIPT_ID_GENERATION_FAILED";

    public static final String CANONICAL_DOMAIN_V2 =
            "FBSIR:OAUTH:AUTHORIZATION_RECEIPT:v2";
    private static final String ACTOR_USER = "USER";
    private static final String EVIDENCE_ACTION_COMPLETED = "ACTION_COMPLETED";
    private static final Pattern OPAQUE_ID = Pattern.compile("[A-Za-z0-9_-]{1,128}");
    private static final Pattern KEY_REF = Pattern.compile("[A-Za-z0-9._:-]{1,191}");

    private final Supplier<String> idSupplier;

    @Autowired
    public BoardOAuthReceiptFactory() {
        this(() -> UUID.randomUUID().toString());
    }

    BoardOAuthReceiptFactory(Supplier<String> idSupplier) {
        this.idSupplier = Objects.requireNonNull(idSupplier, "idSupplier");
    }

    public ApprovalReceipts approval(
            BoardOAuthAuthorizationRequest request,
            BoardOAuthAuthorizationCode code,
            Long actorUserId,
            Instant createdAt) {
        requireApprovedContext(request, code, actorUserId, createdAt);
        String correlationId = nextId();
        BoardOAuthReceipt approved = receipt(
                nextId(),
                "AUTHORIZATION_APPROVED",
                correlationId,
                request,
                null,
                actorUserId,
                createdAt);
        BoardOAuthReceipt codeIssued = receipt(
                nextId(),
                "AUTHORIZATION_CODE_ISSUED",
                correlationId,
                request,
                code,
                actorUserId,
                createdAt);
        return new ApprovalReceipts(approved, codeIssued);
    }

    public BoardOAuthReceipt denial(
            BoardOAuthAuthorizationRequest request,
            Long actorUserId,
            Instant createdAt) {
        requireDeniedContext(request, actorUserId, createdAt);
        String correlationId = nextId();
        return receipt(
                nextId(),
                "AUTHORIZATION_DENIED",
                correlationId,
                request,
                null,
                actorUserId,
                createdAt);
    }

    private BoardOAuthReceipt receipt(
            String receiptId,
            String action,
            String correlationId,
            BoardOAuthAuthorizationRequest request,
            BoardOAuthAuthorizationCode code,
            Long actorUserId,
            Instant createdAt) {
        BoardOAuthReceipt receipt = new BoardOAuthReceipt();
        receipt.setReceiptId(receiptId);
        receipt.setAction(action);
        receipt.setClientId(request.getClientId());
        receipt.setAuthorizationRequestId(request.getId());
        receipt.setAuthorizationCodeId(code == null ? null : code.getId());
        receipt.setFamilyId(null);
        receipt.setTokenId(null);
        receipt.setBindingId(null);
        receipt.setTenantId(request.getTenantId());
        receipt.setMemberId(request.getMemberId());
        receipt.setUserId(request.getUserId());
        receipt.setPrincipalSubjectDigest(request.getPrincipalSubjectDigest().clone());
        receipt.setActorType(ACTOR_USER);
        receipt.setActorUserId(actorUserId);
        receipt.setActorSubjectDigest(request.getPrincipalSubjectDigest().clone());
        receipt.setCorrelationId(correlationId);
        receipt.setPayloadDigest(payloadDigest(
                receiptId, action, correlationId, request, code, actorUserId, createdAt));
        receipt.setEvidenceLevel(EVIDENCE_ACTION_COMPLETED);
        receipt.setCreatedAt(Date.from(createdAt));
        return receipt;
    }

    private static byte[] payloadDigest(
            String receiptId,
            String action,
            String correlationId,
            BoardOAuthAuthorizationRequest request,
            BoardOAuthAuthorizationCode code,
            Long actorUserId,
            Instant createdAt) {
        try {
            CanonicalWriter writer = new CanonicalWriter();
            writer.text("receipt.action", action);
            writer.text("receipt.receipt_id", receiptId);
            writer.text("receipt.correlation_id", correlationId);
            writer.number("receipt.actor_user_id", actorUserId);
            writer.time("receipt.created_at", Date.from(createdAt));

            writer.number("request.id", request.getId());
            writer.text("request.client_id", request.getClientId());
            writer.text("request.redirect_uri", request.getRedirectUri());
            writer.text("request.code_challenge", request.getCodeChallenge());
            writer.text("request.code_challenge_method",
                    request.getCodeChallengeMethod());
            writer.digest("request.state_digest", request.getStateDigest());
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
            writer.number("request.version", request.getVersion());
            writer.time("request.requested_at", request.getRequestedAt());

            writer.number("code.id", code == null ? null : code.getId());
            writer.digest("code.code_digest",
                    code == null ? null : code.getCodeDigest());
            writer.text("code.consent_intent",
                    code == null ? null : code.getConsentIntent().name());
            writer.time("code.expires_at",
                    code == null ? null : code.getExpiresAt());
            return writer.digest();
        } catch (RuntimeException failure) {
            throw BoardOAuthProtocolException.serverError(RECEIPT_INVALID);
        }
    }

    private static void requireApprovedContext(
            BoardOAuthAuthorizationRequest request,
            BoardOAuthAuthorizationCode code,
            Long actorUserId,
            Instant createdAt) {
        requireCommonContext(request, actorUserId, createdAt);
        if (!"APPROVED".equals(request.getStatus())
                || request.getApprovedAt() == null
                || !request.getApprovedAt().toInstant().equals(createdAt)
                || request.getDeniedAt() != null
                || request.getConsumedAt() != null
                || request.getStateKeyRef() == null
                || !KEY_REF.matcher(request.getStateKeyRef()).matches()
                || request.getStateNonce() == null
                || request.getStateNonce().length != 12
                || request.getStateCiphertext() == null
                || request.getStateCiphertext().length < 32
                || request.getStateCiphertext().length > 528
                || code == null
                || !isPositive(code.getId())
                || !Objects.equals(code.getAuthorizationRequestId(), request.getId())
                || !Objects.equals(code.getClientId(), request.getClientId())
                || !Objects.equals(code.getRedirectUri(), request.getRedirectUri())
                || !Objects.equals(code.getCodeChallenge(), request.getCodeChallenge())
                || !Objects.equals(
                        code.getCodeChallengeMethod(), request.getCodeChallengeMethod())
                || !Objects.equals(code.getIssuerUri(), request.getIssuerUri())
                || !Objects.equals(code.getResourceUri(), request.getResourceUri())
                || !Objects.equals(code.getProductCode(), request.getProductCode())
                || !Objects.equals(code.getSourceCode(), request.getSourceCode())
                || !Objects.equals(code.getConnectorCode(), request.getConnectorCode())
                || !Objects.equals(code.getScopeCanonical(), request.getScopeCanonical())
                || !Objects.equals(code.getTenantId(), request.getTenantId())
                || !Objects.equals(code.getMemberId(), request.getMemberId())
                || !Objects.equals(code.getUserId(), request.getUserId())
                || !Objects.equals(
                        code.getConsentIntent(), request.getConsentIntent())
                || !sameDigest(code.getPrincipalSubjectDigest(),
                        request.getPrincipalSubjectDigest())
                || !sameDigest(code.getScopeDigest(), request.getScopeDigest())
                || !hasDigest(code.getCodeDigest())
                || !"ACTIVE".equals(code.getStatus())
                || code.getIssuedAt() == null
                || !code.getIssuedAt().toInstant().equals(createdAt)
                || code.getExpiresAt() == null
                || !code.getExpiresAt().toInstant().equals(createdAt.plusSeconds(60))
                || !request.getExpiresAt().after(code.getExpiresAt())
                || code.getUsedAt() != null
                || code.getRevokedAt() != null
                || !Objects.equals(code.getVersion(), 0L)) {
            throw BoardOAuthProtocolException.serverError(RECEIPT_INVALID);
        }
    }

    private static void requireDeniedContext(
            BoardOAuthAuthorizationRequest request,
            Long actorUserId,
            Instant createdAt) {
        requireCommonContext(request, actorUserId, createdAt);
        if (!"DENIED".equals(request.getStatus())
                || request.getDeniedAt() == null
                || !request.getDeniedAt().toInstant().equals(createdAt)
                || request.getApprovedAt() != null
                || request.getConsumedAt() != null
                || request.getStateKeyRef() != null
                || request.getStateNonce() != null
                || request.getStateCiphertext() != null) {
            throw BoardOAuthProtocolException.serverError(RECEIPT_INVALID);
        }
    }

    private static void requireCommonContext(
            BoardOAuthAuthorizationRequest request,
            Long actorUserId,
            Instant createdAt) {
        if (request == null
                || createdAt == null
                || !isPositive(request.getId())
                || request.getClientId() == null
                || request.getClientId().isEmpty()
                || !isPositive(request.getTenantId())
                || !isPositive(request.getMemberId())
                || !isPositive(request.getUserId())
                || !Objects.equals(actorUserId, request.getUserId())
                || !BoardOAuthProfile.isAllowedLoopbackRedirect(request.getRedirectUri())
                || !BoardOAuthCrypto.isValidPkceS256Challenge(request.getCodeChallenge())
                || !BoardOAuthProfile.PKCE_METHOD.equals(request.getCodeChallengeMethod())
                || !BoardOAuthProfile.ISSUER.equals(request.getIssuerUri())
                || !BoardOAuthProfile.RESOURCE.equals(request.getResourceUri())
                || !"FBSIR_INDEPENDENT_BOARD".equals(request.getProductCode())
                || !"WORKBUDDY".equals(request.getSourceCode())
                || !"fbs-connector".equals(request.getConnectorCode())
                || !BoardOAuthProfile.CANONICAL_SCOPE.equals(request.getScopeCanonical())
                || !hasDigest(request.getRequestHandleDigest())
                || !hasDigest(request.getStateDigest())
                || !hasDigest(request.getPrincipalSubjectDigest())
                || request.getConsentIntent() == null
                || !hasDigest(request.getScopeDigest())
                || !BoardOAuthCrypto.constantTimeEquals(
                        BoardOAuthCrypto.sha256Ascii(BoardOAuthProfile.CANONICAL_SCOPE),
                        request.getScopeDigest())
                || request.getRequestedAt() == null
                || request.getExpiresAt() == null
                || !request.getExpiresAt().toInstant().equals(
                        request.getRequestedAt().toInstant().plusSeconds(300))
                || request.getRequestedAt().toInstant().isAfter(createdAt)
                || !request.getExpiresAt().toInstant().isAfter(createdAt)
                || !Objects.equals(request.getVersion(), 1L)) {
            throw BoardOAuthProtocolException.serverError(RECEIPT_INVALID);
        }
    }

    private String nextId() {
        String value;
        try {
            value = idSupplier.get();
        } catch (RuntimeException failure) {
            throw BoardOAuthProtocolException.serverError(RECEIPT_ID_GENERATION_FAILED);
        }
        if (value == null || !OPAQUE_ID.matcher(value).matches()) {
            throw BoardOAuthProtocolException.serverError(RECEIPT_ID_GENERATION_FAILED);
        }
        return value;
    }

    private static boolean isPositive(Long value) {
        return value != null && value > 0L;
    }

    private static boolean hasDigest(byte[] value) {
        return value != null && value.length == BoardOAuthCrypto.SHA256_BYTES;
    }

    private static boolean sameDigest(byte[] left, byte[] right) {
        return hasDigest(left)
                && hasDigest(right)
                && BoardOAuthCrypto.constantTimeEquals(left, right);
    }

    /** Length-prefixed ASCII encoding prevents delimiter and null ambiguity. */
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
            field(name, fieldValue == null ? null : Long.toString(fieldValue.getTime()));
        }

        private void digest(String name, byte[] fieldValue) {
            if (fieldValue != null && !hasDigest(fieldValue)) {
                throw BoardOAuthProtocolException.serverError(RECEIPT_INVALID);
            }
            field(name, fieldValue == null ? null : HexFormat.of().formatHex(fieldValue));
        }

        private void field(String name, String fieldValue) {
            if (!isAscii(name) || (fieldValue != null && !isAscii(fieldValue))) {
                throw BoardOAuthProtocolException.serverError(RECEIPT_INVALID);
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

    /** Two immutable receipt shapes emitted by one approval transaction. */
    public static final class ApprovalReceipts {
        private final BoardOAuthReceipt authorizationApproved;
        private final BoardOAuthReceipt authorizationCodeIssued;

        private ApprovalReceipts(
                BoardOAuthReceipt authorizationApproved,
                BoardOAuthReceipt authorizationCodeIssued) {
            this.authorizationApproved = authorizationApproved;
            this.authorizationCodeIssued = authorizationCodeIssued;
        }

        public BoardOAuthReceipt authorizationApproved() {
            return authorizationApproved;
        }

        public BoardOAuthReceipt authorizationCodeIssued() {
            return authorizationCodeIssued;
        }

        @Override
        public String toString() {
            return "ApprovalReceipts[REDACTED]";
        }
    }
}
