package com.wx.fbsir.business.board.oauth;

import com.wx.fbsir.business.board.oauth.domain.BoardOAuthReceipt;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Builds digest-only v2 receipts for refresh-token rotation and replay
 * containment. The API intentionally accepts only immutable authority snapshots;
 * raw bearer material is neither accepted nor projected into a receipt.
 */
public final class BoardOAuthRefreshReceiptFactory {
    public static final int RECEIPT_FORMAT_V2 = 2;
    public static final String TOKEN_FAMILY_ROTATED = "TOKEN_FAMILY_ROTATED";
    public static final String REFRESH_REPLAY_DETECTED = "REFRESH_REPLAY_DETECTED";
    public static final String RECEIPT_INVALID = "OAUTH_REFRESH_RECEIPT_INVALID";

    static final String ROTATION_DOMAIN =
            "FBSIR:OAUTH:TOKEN_FAMILY_ROTATED_RECEIPT:v2";
    static final String REPLAY_DOMAIN =
            "FBSIR:OAUTH:REFRESH_REPLAY_DETECTED_RECEIPT:v2";

    private static final String TOKEN_FAMILY_CREATED = "TOKEN_FAMILY_CREATED";
    private static final String SUBJECT_TOKEN_TYPE = "REFRESH";
    private static final String ACTOR_CLIENT = "CLIENT";
    private static final String EVIDENCE_ACTION_COMPLETED = "ACTION_COMPLETED";
    private static final String ACTIVE = "ACTIVE";
    private static final String COMPROMISED = "COMPROMISED";
    private static final String USED = "USED";
    private static final long MAX_UNSIGNED_INT = 4_294_967_295L;
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_-]{1,128}");

    private BoardOAuthRefreshReceiptFactory() {
    }

    public static BoardOAuthReceipt createRotation(
            String receiptId,
            String correlationId,
            Instant createdAt,
            AuthoritySnapshot authority,
            CausationSnapshot causation) {
        return create(
                ROTATION_DOMAIN,
                TOKEN_FAMILY_ROTATED,
                receiptId,
                correlationId,
                createdAt,
                authority,
                causation);
    }

    public static BoardOAuthReceipt createReplay(
            String receiptId,
            String correlationId,
            Instant createdAt,
            AuthoritySnapshot authority,
            CausationSnapshot causation) {
        return create(
                REPLAY_DOMAIN,
                REFRESH_REPLAY_DETECTED,
                receiptId,
                correlationId,
                createdAt,
                authority,
                causation);
    }

    public static void validateRotation(
            BoardOAuthReceipt receipt,
            AuthoritySnapshot authority,
            CausationSnapshot causation) {
        validate(ROTATION_DOMAIN, TOKEN_FAMILY_ROTATED, receipt, authority, causation);
    }

    public static void validateReplay(
            BoardOAuthReceipt receipt,
            AuthoritySnapshot authority,
            CausationSnapshot causation) {
        validate(REPLAY_DOMAIN, REFRESH_REPLAY_DETECTED, receipt, authority, causation);
    }

    private static BoardOAuthReceipt create(
            String domain,
            String action,
            String receiptId,
            String correlationId,
            Instant createdAt,
            AuthoritySnapshot authority,
            CausationSnapshot causation) {
        try {
            CanonicalContext context = requireContext(
                    action,
                    receiptId,
                    correlationId,
                    createdAt,
                    authority,
                    causation);
            BoardOAuthReceipt receipt = new BoardOAuthReceipt();
            receipt.setReceiptId(receiptId);
            receipt.setAction(action);
            receipt.setReceiptFormatVersion(RECEIPT_FORMAT_V2);
            receipt.setClientId(authority.clientId());
            receipt.setAuthorizationRequestId(null);
            receipt.setAuthorizationCodeId(null);
            receipt.setFamilyId(authority.familyId());
            receipt.setTokenId(authority.tokenId());
            receipt.setBindingId(authority.bindingId());
            receipt.setTenantId(authority.tenantId());
            receipt.setMemberId(authority.memberId());
            receipt.setUserId(authority.userId());
            receipt.setPrincipalSubjectDigest(authority.principalSubjectDigest());
            receipt.setActorType(ACTOR_CLIENT);
            receipt.setActorUserId(null);
            receipt.setActorSubjectDigest(
                    BoardOAuthCrypto.sha256Ascii(authority.clientId()));
            receipt.setCorrelationId(correlationId);
            receipt.setEvidenceLevel(EVIDENCE_ACTION_COMPLETED);
            receipt.setSubjectGeneration(authority.subjectGeneration());
            receipt.setResultGeneration(context.resultGeneration);
            receipt.setCausationReceiptId(causation.receiptId());
            receipt.setBeforeStateDigest(authority.beforeStateDigest());
            receipt.setAfterStateDigest(authority.afterStateDigest());
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
            AuthoritySnapshot authority,
            CausationSnapshot causation) {
        try {
            if (receipt == null || receipt.getCreatedAt() == null) {
                throw invalidReceipt();
            }
            CanonicalContext context = requireContext(
                    action,
                    receipt.getReceiptId(),
                    receipt.getCorrelationId(),
                    receipt.getCreatedAt().toInstant(),
                    authority,
                    causation);
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
            Instant createdAt,
            AuthoritySnapshot authority,
            CausationSnapshot causation) {
        if (!isIdentifier(receiptId)
                || !isIdentifier(correlationId)
                || (causation != null
                        && Objects.equals(receiptId, causation.receiptId()))
                || createdAt == null
                || createdAt.getNano() % 1_000_000 != 0
                || authority == null
                || causation == null) {
            throw invalidReceipt();
        }
        requireAuthority(action, createdAt, authority);
        requireCausation(action, createdAt, authority.subjectGeneration(), causation);
        Long resultGeneration = TOKEN_FAMILY_ROTATED.equals(action)
                ? authority.subjectGeneration() + 1L : null;
        return new CanonicalContext(authority, causation, resultGeneration);
    }

    private static void requireAuthority(
            String action,
            Instant createdAt,
            AuthoritySnapshot authority) {
        if (!isIdentifier(authority.clientId())
                || !isIdentifier(authority.familyId())
                || !isIdentifier(authority.bindingId())
                || !isPositive(authority.tokenId())
                || !hasIdentity(
                        authority.tenantId(),
                        authority.memberId(),
                        authority.userId(),
                        authority.principalSubjectDigest())
                || !hasDigest(authority.refreshTokenDigest())
                || !hasDigest(authority.beforeStateDigest())
                || !hasDigest(authority.afterStateDigest())
                || sameDigest(
                        authority.beforeStateDigest(), authority.afterStateDigest())
                || !isUnsignedInt(authority.subjectGeneration())
                || !isUnsignedInt(authority.currentFamilyGeneration())
                || authority.familyVersion() == null
                || authority.familyVersion() <= 0L
                || authority.tokenVersion() == null
                || authority.tokenVersion() <= 0L
                || !USED.equals(authority.refreshTokenStatus())
                || authority.refreshTokenUsedAt() == null
                || authority.refreshTokenUsedAt().isAfter(createdAt)) {
            throw invalidReceipt();
        }
        if (TOKEN_FAMILY_ROTATED.equals(action)) {
            if (authority.subjectGeneration() == MAX_UNSIGNED_INT
                    || !Objects.equals(
                            authority.currentFamilyGeneration(),
                            authority.subjectGeneration() + 1L)
                    || !ACTIVE.equals(authority.familyStatus())
                    || !createdAt.equals(authority.refreshTokenUsedAt())
                    || authority.familyTerminatedAt() != null) {
                throw invalidReceipt();
            }
        } else if (REFRESH_REPLAY_DETECTED.equals(action)) {
            if (authority.subjectGeneration() == MAX_UNSIGNED_INT
                    || authority.currentFamilyGeneration()
                            <= authority.subjectGeneration()
                    || !COMPROMISED.equals(authority.familyStatus())
                    || !createdAt.equals(authority.familyTerminatedAt())) {
                throw invalidReceipt();
            }
        } else {
            throw invalidReceipt();
        }
    }

    private static void requireCausation(
            String action,
            Instant createdAt,
            Long subjectGeneration,
            CausationSnapshot causation) {
        if (!isIdentifier(causation.receiptId())
                || causation.receiptFormatVersion() == null
                || !hasDigest(causation.payloadDigest())
                || causation.createdAt() == null
                || causation.createdAt().isAfter(createdAt)) {
            throw invalidReceipt();
        }
        if (TOKEN_FAMILY_ROTATED.equals(action)) {
            if (subjectGeneration == 0L) {
                if (!TOKEN_FAMILY_CREATED.equals(causation.action())
                        || causation.receiptFormatVersion() != 1
                        || causation.resultGeneration() != null) {
                    throw invalidReceipt();
                }
            } else if (!TOKEN_FAMILY_ROTATED.equals(causation.action())
                    || causation.receiptFormatVersion() != RECEIPT_FORMAT_V2
                    || !Objects.equals(causation.resultGeneration(), subjectGeneration)) {
                throw invalidReceipt();
            }
        } else if (!TOKEN_FAMILY_ROTATED.equals(causation.action())
                || causation.receiptFormatVersion() != RECEIPT_FORMAT_V2
                || !Objects.equals(
                        causation.resultGeneration(), subjectGeneration + 1L)) {
            throw invalidReceipt();
        }
    }

    private static void requireReceiptShape(
            String action,
            BoardOAuthReceipt receipt,
            CanonicalContext context) {
        AuthoritySnapshot authority = context.authority;
        String expectedSlot = TOKEN_FAMILY_ROTATED.equals(action)
                ? "R:" + authority.familyId() + ":"
                        + String.format(Locale.ROOT, "%010d", context.resultGeneration)
                : "P:" + authority.familyId();
        if (!isPositive(receipt.getId())
                || !Objects.equals(receipt.getAction(), action)
                || !Objects.equals(
                        receipt.getReceiptFormatVersion(), RECEIPT_FORMAT_V2)
                || !Objects.equals(receipt.getClientId(), authority.clientId())
                || receipt.getAuthorizationRequestId() != null
                || receipt.getAuthorizationCodeId() != null
                || !Objects.equals(receipt.getFamilyId(), authority.familyId())
                || !Objects.equals(receipt.getTokenId(), authority.tokenId())
                || !Objects.equals(receipt.getBindingId(), authority.bindingId())
                || !Objects.equals(receipt.getTenantId(), authority.tenantId())
                || !Objects.equals(receipt.getMemberId(), authority.memberId())
                || !Objects.equals(receipt.getUserId(), authority.userId())
                || !sameDigest(
                        receipt.getPrincipalSubjectDigest(),
                        authority.principalSubjectDigest())
                || !ACTOR_CLIENT.equals(receipt.getActorType())
                || receipt.getActorUserId() != null
                || !sameDigest(
                        receipt.getActorSubjectDigest(),
                        BoardOAuthCrypto.sha256Ascii(authority.clientId()))
                || !EVIDENCE_ACTION_COMPLETED.equals(receipt.getEvidenceLevel())
                || !Objects.equals(
                        receipt.getSubjectGeneration(), authority.subjectGeneration())
                || !Objects.equals(receipt.getResultGeneration(), context.resultGeneration)
                || !Objects.equals(
                        receipt.getCausationReceiptId(), context.causation.receiptId())
                || !sameDigest(
                        receipt.getBeforeStateDigest(), authority.beforeStateDigest())
                || !sameDigest(
                        receipt.getAfterStateDigest(), authority.afterStateDigest())
                || (receipt.getSubjectTokenType() != null
                        && !SUBJECT_TOKEN_TYPE.equals(receipt.getSubjectTokenType()))
                || (receipt.getSecurityEventSlot() != null
                        && !expectedSlot.equals(receipt.getSecurityEventSlot()))) {
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
        writer.number("receipt.format", receipt.getReceiptFormatVersion().longValue());
        writer.text("receipt.client", receipt.getClientId());
        writer.text("receipt.family", receipt.getFamilyId());
        writer.number("receipt.token", receipt.getTokenId());
        writer.text("receipt.binding", receipt.getBindingId());
        writer.number("receipt.tenant", receipt.getTenantId());
        writer.number("receipt.member", receipt.getMemberId());
        writer.number("receipt.user", receipt.getUserId());
        writer.digest("receipt.principal", receipt.getPrincipalSubjectDigest());
        writer.text("receipt.actor_type", receipt.getActorType());
        writer.digest("receipt.actor", receipt.getActorSubjectDigest());
        writer.text("receipt.correlation", receipt.getCorrelationId());
        writer.number("receipt.subject_generation", receipt.getSubjectGeneration());
        writer.number("receipt.result_generation", receipt.getResultGeneration());
        writer.text("receipt.causation", receipt.getCausationReceiptId());
        writer.digest("receipt.before_state", receipt.getBeforeStateDigest());
        writer.digest("receipt.after_state", receipt.getAfterStateDigest());
        writer.text("receipt.subject_token_type", SUBJECT_TOKEN_TYPE);
        writer.time("receipt.created", receipt.getCreatedAt().toInstant());

        AuthoritySnapshot authority = context.authority;
        writer.digest("authority.refresh_digest", authority.refreshTokenDigest());
        writer.number("authority.family_generation", authority.currentFamilyGeneration());
        writer.number("authority.family_version", authority.familyVersion());
        writer.number("authority.token_version", authority.tokenVersion());
        writer.text("authority.family_status", authority.familyStatus());
        writer.text("authority.token_status", authority.refreshTokenStatus());
        writer.time("authority.token_used", authority.refreshTokenUsedAt());
        writer.time("authority.family_terminated", authority.familyTerminatedAt());

        CausationSnapshot causation = context.causation;
        writer.text("causation.id", causation.receiptId());
        writer.text("causation.action", causation.action());
        writer.number("causation.format", causation.receiptFormatVersion().longValue());
        writer.number("causation.result_generation", causation.resultGeneration());
        writer.digest("causation.payload", causation.payloadDigest());
        writer.time("causation.created", causation.createdAt());
        return BoardOAuthCrypto.sha256Ascii(writer.value());
    }

    private static boolean hasIdentity(
            Long tenantId,
            Long memberId,
            Long userId,
            byte[] principalDigest) {
        return isPositive(tenantId)
                && isPositive(memberId)
                && isPositive(userId)
                && sameDigest(
                        principalDigest,
                        BoardOAuthPrincipalSubject.digest(
                                tenantId, memberId, userId));
    }

    private static boolean isUnsignedInt(Long value) {
        return value != null && value >= 0L && value <= MAX_UNSIGNED_INT;
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

    private static BoardOAuthProtocolException invalidReceipt() {
        return BoardOAuthProtocolException.serverError(RECEIPT_INVALID);
    }

    /**
     * Post-mutation current-read snapshot. Byte arrays are defensively copied and
     * the textual representation is deliberately redacted.
     */
    public record AuthoritySnapshot(
            String clientId,
            String familyId,
            Long tokenId,
            String bindingId,
            Long tenantId,
            Long memberId,
            Long userId,
            byte[] principalSubjectDigest,
            byte[] refreshTokenDigest,
            Long subjectGeneration,
            Long currentFamilyGeneration,
            Long familyVersion,
            Long tokenVersion,
            String familyStatus,
            String refreshTokenStatus,
            Instant refreshTokenUsedAt,
            Instant familyTerminatedAt,
            byte[] beforeStateDigest,
            byte[] afterStateDigest) {
        public AuthoritySnapshot {
            principalSubjectDigest = copy(principalSubjectDigest);
            refreshTokenDigest = copy(refreshTokenDigest);
            beforeStateDigest = copy(beforeStateDigest);
            afterStateDigest = copy(afterStateDigest);
        }

        @Override
        public byte[] principalSubjectDigest() {
            return copy(principalSubjectDigest);
        }

        @Override
        public byte[] refreshTokenDigest() {
            return copy(refreshTokenDigest);
        }

        @Override
        public byte[] beforeStateDigest() {
            return copy(beforeStateDigest);
        }

        @Override
        public byte[] afterStateDigest() {
            return copy(afterStateDigest);
        }

        @Override
        public String toString() {
            return "AuthoritySnapshot[REDACTED]";
        }
    }

    /** Direct immutable cause locked in the same family/client scope. */
    public record CausationSnapshot(
            String receiptId,
            String action,
            Integer receiptFormatVersion,
            Long resultGeneration,
            byte[] payloadDigest,
            Instant createdAt) {
        public CausationSnapshot {
            payloadDigest = copy(payloadDigest);
        }

        @Override
        public byte[] payloadDigest() {
            return copy(payloadDigest);
        }

        @Override
        public String toString() {
            return "CausationSnapshot[REDACTED]";
        }
    }

    private record CanonicalContext(
            AuthoritySnapshot authority,
            CausationSnapshot causation,
            Long resultGeneration) {
    }

    private static byte[] copy(byte[] value) {
        return value == null ? null : value.clone();
    }

    private static final class CanonicalWriter {
        private final StringBuilder value = new StringBuilder();

        private void text(String name, String field) {
            append(name, field);
        }

        private void number(String name, Long field) {
            append(name, field == null ? null : Long.toString(field));
        }

        private void time(String name, Instant field) {
            append(name, field == null ? null : Long.toString(field.toEpochMilli()));
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
