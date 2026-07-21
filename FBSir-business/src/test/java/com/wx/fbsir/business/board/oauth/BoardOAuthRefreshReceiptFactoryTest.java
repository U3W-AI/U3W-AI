package com.wx.fbsir.business.board.oauth;

import com.wx.fbsir.business.board.oauth.BoardOAuthRefreshReceiptFactory.AuthoritySnapshot;
import com.wx.fbsir.business.board.oauth.BoardOAuthRefreshReceiptFactory.CausationSnapshot;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthReceipt;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardOAuthRefreshReceiptFactoryTest {
    private static final Instant EVENT_AT =
            Instant.parse("2026-07-21T06:07:08.901Z");

    @Test
    void createsCanonicalRotationWithDirectCreationCause() {
        AuthoritySnapshot authority = rotationAuthority(0L);
        CausationSnapshot cause = creationCause();

        BoardOAuthReceipt receipt = BoardOAuthRefreshReceiptFactory.createRotation(
                "rotation_receipt_1", "refresh_operation_1", EVENT_AT,
                authority, cause);

        assertEquals(2, receipt.getReceiptFormatVersion());
        assertEquals("TOKEN_FAMILY_ROTATED", receipt.getAction());
        assertEquals(401L, receipt.getTokenId());
        assertEquals(0L, receipt.getSubjectGeneration());
        assertEquals(1L, receipt.getResultGeneration());
        assertEquals(cause.receiptId(), receipt.getCausationReceiptId());
        assertArrayEquals(authority.beforeStateDigest(), receipt.getBeforeStateDigest());
        assertArrayEquals(authority.afterStateDigest(), receipt.getAfterStateDigest());
        assertNull(receipt.getSubjectTokenType());
        assertNull(receipt.getSecurityEventSlot());
        assertEquals(Date.from(EVENT_AT), receipt.getCreatedAt());
        assertEquals(BoardOAuthCrypto.SHA256_BYTES, receipt.getPayloadDigest().length);

        receipt.setId(9001L);
        assertDoesNotThrow(() -> BoardOAuthRefreshReceiptFactory.validateRotation(
                receipt, authority, cause));
    }

    @Test
    void createsReplayWithNullResultAndDirectRotationCause() {
        AuthoritySnapshot authority = replayAuthority(0L, 1L);
        CausationSnapshot cause = rotationCause(1L, EVENT_AT.minusSeconds(10));

        BoardOAuthReceipt receipt = BoardOAuthRefreshReceiptFactory.createReplay(
                "replay_receipt_1", "refresh_operation_2", EVENT_AT,
                authority, cause);

        assertEquals("REFRESH_REPLAY_DETECTED", receipt.getAction());
        assertEquals(0L, receipt.getSubjectGeneration());
        assertNull(receipt.getResultGeneration());
        assertEquals(cause.receiptId(), receipt.getCausationReceiptId());
        receipt.setId(9002L);
        assertDoesNotThrow(() -> BoardOAuthRefreshReceiptFactory.validateReplay(
                receipt, authority, cause));
    }

    @Test
    void laterRotationRequiresImmediatelyPrecedingRotationGeneration() {
        AuthoritySnapshot authority = rotationAuthority(1L);
        CausationSnapshot valid = rotationCause(1L, EVENT_AT.minusSeconds(10));
        assertDoesNotThrow(() -> BoardOAuthRefreshReceiptFactory.createRotation(
                "rotation_receipt_2", "refresh_operation_3", EVENT_AT,
                authority, valid));

        CausationSnapshot stale = rotationCause(0L, EVENT_AT.minusSeconds(10));
        assertInvalid(() -> BoardOAuthRefreshReceiptFactory.createRotation(
                "rotation_receipt_3", "refresh_operation_4", EVENT_AT,
                authority, stale));
    }

    @Test
    void rejectsGenerationLifecycleAndReceiptDigestDrift() {
        assertInvalid(() -> BoardOAuthRefreshReceiptFactory.createRotation(
                "rotation_receipt_4", "refresh_operation_5", EVENT_AT,
                replayAuthority(0L, 1L), creationCause()));

        AuthoritySnapshot authority = replayAuthority(0L, 1L);
        CausationSnapshot cause = rotationCause(1L, EVENT_AT.minusSeconds(10));
        BoardOAuthReceipt receipt = BoardOAuthRefreshReceiptFactory.createReplay(
                "replay_receipt_2", "refresh_operation_6", EVENT_AT,
                authority, cause);
        receipt.setId(9003L);
        receipt.setAfterStateDigest(digest("tampered-after-state"));
        assertInvalid(() -> BoardOAuthRefreshReceiptFactory.validateReplay(
                receipt, authority, cause));
    }

    @Test
    void rejectsAZeroChangeSecurityReceipt() {
        byte[] unchanged = digest("unchanged-state");
        AuthoritySnapshot authority = new AuthoritySnapshot(
                "c".repeat(43), "family_1", 401L, "binding_1",
                11L, 22L, 33L,
                BoardOAuthPrincipalSubject.digest(11L, 22L, 33L),
                digest("refresh-token-digest"),
                0L, 1L, 1L, 1L, "ACTIVE", "USED",
                EVENT_AT, null, unchanged, unchanged);

        assertInvalid(() -> BoardOAuthRefreshReceiptFactory.createRotation(
                "rotation_receipt_5", "refresh_operation_7", EVENT_AT,
                authority, creationCause()));
    }

    @Test
    void rejectsSelfCausation() {
        CausationSnapshot self = new CausationSnapshot(
                "rotation_receipt_self", "TOKEN_FAMILY_CREATED", 1,
                null, digest("creation-payload"), EVENT_AT.minusSeconds(1));

        assertInvalid(() -> BoardOAuthRefreshReceiptFactory.createRotation(
                "rotation_receipt_self", "refresh_operation_8", EVENT_AT,
                rotationAuthority(0L), self));
    }

    @Test
    void rejectsReplayAtUnsignedGenerationLimit() {
        long maximum = 4_294_967_295L;
        AuthoritySnapshot authority = new AuthoritySnapshot(
                "c".repeat(43), "family_1", 401L, "binding_1",
                11L, 22L, 33L,
                BoardOAuthPrincipalSubject.digest(11L, 22L, 33L),
                digest("refresh-token-digest"),
                maximum, maximum, 2L, 2L, "COMPROMISED", "USED",
                EVENT_AT.minusSeconds(10), EVENT_AT,
                digest("before-max-replay"), digest("after-max-replay"));
        CausationSnapshot cause = rotationCause(
                maximum + 1L, EVENT_AT.minusSeconds(10));

        assertInvalid(() -> BoardOAuthRefreshReceiptFactory.createReplay(
                "replay_receipt_max", "refresh_operation_9", EVENT_AT,
                authority, cause));
    }

    @Test
    void snapshotsDefensivelyCopyDigestsAndRedactTheirTextForm() {
        byte[] principal = BoardOAuthPrincipalSubject.digest(11L, 22L, 33L);
        byte[] tokenDigest = digest("opaque-refresh-token-material");
        byte[] before = digest("before-state");
        byte[] after = digest("after-state");
        AuthoritySnapshot authority = new AuthoritySnapshot(
                "c".repeat(43), "family_1", 401L, "binding_1",
                11L, 22L, 33L, principal, tokenDigest, 0L, 1L,
                1L, 1L, "ACTIVE", "USED", EVENT_AT, null, before, after);

        Arrays.fill(tokenDigest, (byte) 0);
        assertFalse(Arrays.equals(tokenDigest, authority.refreshTokenDigest()));
        byte[] exposed = authority.refreshTokenDigest();
        Arrays.fill(exposed, (byte) 1);
        assertArrayEquals(digest("opaque-refresh-token-material"),
                authority.refreshTokenDigest());
        assertEquals("AuthoritySnapshot[REDACTED]", authority.toString());
        assertFalse(authority.toString().contains(
                HexFormat.of().formatHex(authority.refreshTokenDigest())));
    }

    @Test
    void legacyReceiptDefaultsToV1AndGeneratedColumnsHaveNoPublicSetter() {
        assertEquals(1, new BoardOAuthReceipt().getReceiptFormatVersion());
        assertFalse(hasMethod("setSubjectTokenType"));
        assertFalse(hasMethod("setSecurityEventSlot"));
    }

    private static boolean hasMethod(String name) {
        return Arrays.stream(BoardOAuthReceipt.class.getMethods())
                .map(Method::getName)
                .anyMatch(name::equals);
    }

    private static AuthoritySnapshot rotationAuthority(long subjectGeneration) {
        return new AuthoritySnapshot(
                "c".repeat(43), "family_1", 401L, "binding_1",
                11L, 22L, 33L,
                BoardOAuthPrincipalSubject.digest(11L, 22L, 33L),
                digest("refresh-token-digest"),
                subjectGeneration, subjectGeneration + 1L,
                subjectGeneration + 1L, 1L,
                "ACTIVE", "USED", EVENT_AT, null,
                digest("before-state-" + subjectGeneration),
                digest("after-state-" + subjectGeneration));
    }

    private static AuthoritySnapshot replayAuthority(
            long subjectGeneration,
            long currentGeneration) {
        return new AuthoritySnapshot(
                "c".repeat(43), "family_1", 401L, "binding_1",
                11L, 22L, 33L,
                BoardOAuthPrincipalSubject.digest(11L, 22L, 33L),
                digest("refresh-token-digest"),
                subjectGeneration, currentGeneration,
                currentGeneration + 1L, 2L,
                "COMPROMISED", "USED", EVENT_AT.minusSeconds(10), EVENT_AT,
                digest("before-replay-state"), digest("after-replay-state"));
    }

    private static CausationSnapshot creationCause() {
        return new CausationSnapshot(
                "family_created_receipt_1", "TOKEN_FAMILY_CREATED", 1,
                null, digest("creation-receipt-payload"), EVENT_AT.minusSeconds(20));
    }

    private static CausationSnapshot rotationCause(long resultGeneration, Instant createdAt) {
        return new CausationSnapshot(
                "rotation_cause_" + resultGeneration, "TOKEN_FAMILY_ROTATED", 2,
                resultGeneration, digest("rotation-cause-" + resultGeneration), createdAt);
    }

    private static byte[] digest(String value) {
        return BoardOAuthCrypto.sha256Ascii(value);
    }

    private static void assertInvalid(Runnable invocation) {
        BoardOAuthProtocolException failure = assertThrows(
                BoardOAuthProtocolException.class, invocation::run);
        assertEquals(BoardOAuthRefreshReceiptFactory.RECEIPT_INVALID,
                failure.reasonCode());
        assertEquals("server_error", failure.oauthError());
        assertEquals(500, failure.httpStatus());
    }
}
