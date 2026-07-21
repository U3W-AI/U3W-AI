package com.wx.fbsir.business.board.attribution.receipt;

import com.wx.fbsir.business.board.attribution.config.IndependentBoardAttributionProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Api2HostForwardingAckV2VerifierTest {
    private static final String SECRET = "utf8:0123456789abcdef0123456789abcdef";
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-21T19:01:00.000Z"), ZoneOffset.UTC);

    private final IndependentBoardAttributionProperties properties = properties();
    private final Api2HostForwardingAckV2Verifier verifier =
            new Api2HostForwardingAckV2Verifier(Map.of("k1", SECRET), FIXED_CLOCK);

    @Test
    void verifiesTheDeterministicApi2NodeVectorWithoutRewritingRawTimestamps() {
        BoardApi2HostForwardingAckV2 ack = validAck();

        VerifiedBoardApi2Receipt verified = verifier.verify(ack, expected(), properties);

        assertEquals("ack-001", verified.ackId());
        assertEquals("ch-001", verified.challengeId());
        assertEquals("k1", verified.keyId());
        assertEquals("4da13ed0a7c65a67b266b91d52b8dde643f704c054ca08c7a34a4b162216086c", verified.signatureHex());
        assertEquals("4476a8759bb306f225f744a08379d50fbb90f46f475e7ef3b1391f55d722c35a", verified.canonicalDigest());
        assertEquals("f693ed56f0f02f9ce8a3a36232381a4daa36abca793a46ebbeec583099288bb2", verified.productSignatureDigest());
        assertEquals(Instant.parse("2026-07-21T19:00:00.000Z"), verified.issuedAt());
        assertEquals(Instant.parse("2026-07-21T19:02:00.000Z"), verified.expiresAt());
        assertEquals("2026-07-21T19:00:00.000Z", ack.issuedAt());
        assertEquals("2026-07-21T19:02:00.000Z", ack.expiresAt());
        assertEquals("40dacd2a55eef0b881adc81fd97caa4ef424761205f65154a1d1677925084a4f", verified.rawAck().requestDigest());
        assertEquals("prev-001", verified.rawAck().previousServiceEventId());
    }

    @Test
    void rejectsTamperedExpectedContextAndNonceBinding() {
        BoardApi2ReceiptExpectedContext tamperedProduct = expected("other-product", validNonceHash());
        assertReason("expected_productId_mismatch", () -> verifier.verify(validAck(), tamperedProduct, properties));

        BoardApi2ReceiptExpectedContext tamperedNonce = expected("fbsir-board-secretary-assistant", "0".repeat(64));
        assertReason("expected_nonceHash_mismatch", () -> verifier.verify(validAck(), tamperedNonce, properties));

        BoardApi2ReceiptExpectedContext tamperedRequest = expected(
                "fbsir-board-secretary-assistant", validNonceHash(), "0".repeat(64), "event-001");
        assertReason("expected_requestDigest_mismatch", () -> verifier.verify(validAck(), tamperedRequest, properties));

        BoardApi2ReceiptExpectedContext missingCoveredEvent = expected(
                "fbsir-board-secretary-assistant", validNonceHash(),
                "40dacd2a55eef0b881adc81fd97caa4ef424761205f65154a1d1677925084a4f", "");
        assertReason("expected_coveredEventId_missing", () -> verifier.verify(validAck(), missingCoveredEvent, properties));
    }

    @Test
    void rejectsMissingWireKeyIdInsteadOfSilentlySelectingTheOnlyKey() {
        BoardApi2HostForwardingAckV2 ack = ack("", "2026-07-21T19:02:00.000Z");
        assertReason("required_field_missing:keyId", () -> verifier.verify(ack, expected(), properties));
    }

    @Test
    void rejectsExpiredOrOverlongReceiptsAndMalformedKeys() {
        BoardApi2HostForwardingAckV2 expired = ack("k1", "2026-07-21T19:00:30.000Z");
        assertReason("ack_expired_or_ttl_invalid", () -> verifier.verify(expired, expected(), properties));

        BoardApi2HostForwardingAckV2 overlong = ack("k1", "2026-07-21T19:02:00.001Z");
        assertReason("ack_expired_or_ttl_invalid", () -> verifier.verify(overlong, expected(), properties));

        Api2HostForwardingAckV2Verifier shortKeyVerifier =
                new Api2HostForwardingAckV2Verifier(Map.of("k1", "utf8:too-short"), FIXED_CLOCK);
        assertReason("server_signature_keyring_unconfigured", () -> shortKeyVerifier.verify(validAck(), expected(), properties));
    }

    @Test
    void rejectsMalformedTimestampsSignaturesAndOversizedFields() {
        assertReason("timestamp_malformed", () -> verifier.verify(
                ack("ack-001", "k1", "not-an-instant", "2026-07-21T19:02:00.000Z", validSignature()), expected(), properties));

        assertReason("signature_malformed", () -> verifier.verify(
                ack("ack-001", "k1", "2026-07-21T19:00:00.000Z", "2026-07-21T19:02:00.000Z", "nope"), expected(), properties));

        assertReason("signature_mismatch", () -> verifier.verify(
                ack("ack-001", "k1", "2026-07-21T19:00:00.000Z", "2026-07-21T19:02:00.000Z", "v1=" + "0".repeat(64)), expected(), properties));

        assertReason("field_too_long:ackId", () -> verifier.verify(
                ack("a".repeat(4_097), "k1", "2026-07-21T19:00:00.000Z", "2026-07-21T19:02:00.000Z", validSignature()), expected(), properties));
    }

    private void assertReason(String reason, Runnable action) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, action::run);
        assertEquals(reason, error.getMessage());
    }

    private IndependentBoardAttributionProperties properties() {
        IndependentBoardAttributionProperties value = new IndependentBoardAttributionProperties();
        value.setReceiptTtlSeconds(120);
        return value;
    }

    private BoardApi2ReceiptExpectedContext expected() {
        return expected(
                "fbsir-board-secretary-assistant", validNonceHash(),
                "40dacd2a55eef0b881adc81fd97caa4ef424761205f65154a1d1677925084a4f", "event-001");
    }

    private BoardApi2ReceiptExpectedContext expected(String productId, String nonceHash) {
        return expected(
                productId, nonceHash,
                "40dacd2a55eef0b881adc81fd97caa4ef424761205f65154a1d1677925084a4f", "event-001");
    }

    private BoardApi2ReceiptExpectedContext expected(
            String productId, String nonceHash, String requestDigest, String coveredEventId) {
        return new BoardApi2ReceiptExpectedContext(
                "post_tool",
                "202607152006-p1-004-runtime-state-14e20a6d.staged",
                "bind-001",
                "sha256:user",
                "sha256:chain",
                productId,
                "independent-board",
                "workbuddy",
                "ddh",
                "natural",
                "ddh-independent-board",
                "ddh-independent-board",
                "fbs_scene_pack_query",
                "ae-001",
                "0be6936cd0f19b1a457b0a9636b1b5334b3cc60f7ab8e8b2f9fa105bcda77455",
                "73a9548bd88c226e0b896b011a94b71ac8233112b5b0223b9ab8ade7a54a03a2",
                "ch-001",
                requestDigest,
                coveredEventId,
                nonceHash,
                "tenant-subject-digest"
        );
    }

    private BoardApi2HostForwardingAckV2 validAck() {
        return ack("ack-001", "k1", "2026-07-21T19:00:00.000Z", "2026-07-21T19:02:00.000Z", validSignature());
    }

    private BoardApi2HostForwardingAckV2 ack(String keyId, String expiresAt) {
        return ack("ack-001", keyId, "2026-07-21T19:00:00.000Z", expiresAt, validSignature());
    }

    private BoardApi2HostForwardingAckV2 ack(
            String ackId, String keyId, String issuedAt, String expiresAt, String signature) {
        return new BoardApi2HostForwardingAckV2(
                "fbss.hostForwardingAck.v2",
                ackId,
                "post_tool",
                "202607152006-p1-004-runtime-state-14e20a6d.staged",
                "bind-001",
                "sha256:user",
                "sha256:chain",
                "fbsir-board-secretary-assistant",
                "independent-board",
                "workbuddy",
                "ddh",
                "natural",
                "ddh-independent-board",
                "ddh-independent-board",
                "fbs_scene_pack_query",
                "ae-001",
                "0be6936cd0f19b1a457b0a9636b1b5334b3cc60f7ab8e8b2f9fa105bcda77455",
                "73a9548bd88c226e0b896b011a94b71ac8233112b5b0223b9ab8ade7a54a03a2",
                "ch-001",
                "40dacd2a55eef0b881adc81fd97caa4ef424761205f65154a1d1677925084a4f",
                "event-001",
                "prev-001",
                issuedAt,
                expiresAt,
                "nonce-001",
                keyId,
                "hmac-sha256-v1",
                signature
        );
    }

    private String validNonceHash() {
        return "70862daf9aaf88d10db6bfc0ff845ad50aff6f29a3f5c623e9bd9390559393c9";
    }

    private String validSignature() {
        return "v1=4da13ed0a7c65a67b266b91d52b8dde643f704c054ca08c7a34a4b162216086c";
    }
}
