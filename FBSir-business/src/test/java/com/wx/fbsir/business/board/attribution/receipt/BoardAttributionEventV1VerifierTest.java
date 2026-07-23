package com.wx.fbsir.business.board.attribution.receipt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.fbsir.business.board.attribution.config.IndependentBoardAttributionProperties;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoardAttributionEventV1VerifierTest {
    private static final String KEY_ID = "wave1-k1";
    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-07-23T10:00:30Z"), ZoneOffset.UTC);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final IndependentBoardAttributionProperties properties = properties();
    private final BoardAttributionEventV1Verifier verifier =
            new BoardAttributionEventV1Verifier(
                    Map.of(KEY_ID, "utf8:" + SECRET), CLOCK);

    @Test
    void verifiesCurrentOfficialDualVersionIdentityAndReturnsPrivacySafeDigests() {
        BoardAttributionEventV1 event = signed(validEvent());

        VerifiedBoardAttributionEvent verified = verifier.verify(event, properties);

        assertEquals("fbsir-eight-seat-board", verified.rawEvent().getProductId());
        assertEquals("26.7.21", verified.rawEvent().getListedManifestVersion());
        assertEquals("26.7.20", verified.rawEvent().getEmbeddedContractVersion());
        assertEquals("OPERATING_DIAGNOSIS", verified.intentFamily());
        assertEquals(64, verified.eventDigest().length());
        assertEquals(64, verified.nonceHash().length());
        assertFalse(verified.rawEvent().isRawContentStored());
    }

    @Test
    void rejectsTamperingWrongOfficialIdentityAndUntrustedNaturalAuthority() {
        BoardAttributionEventV1 tampered = signed(validEvent());
        tampered.setTerminal("WORKBUDDYAI");
        assertReason("signature_mismatch", () -> verifier.verify(tampered, properties));

        BoardAttributionEventV1 wrongVersion = validEvent();
        wrongVersion.setListedManifestVersion("26.7.20");
        assertReason("listed_identity_mismatch",
                () -> verifier.verify(signed(wrongVersion), properties));

        BoardAttributionEventV1 untrustedNatural = validEvent();
        untrustedNatural.setTrafficAuthority("CLIENT_CLAIM");
        assertReason("traffic_authority_invalid",
                () -> verifier.verify(signed(untrustedNatural), properties));
    }

    @Test
    void rejectsRawContentInvalidTraceContextAndOverlongOrExpiredReceipts() {
        BoardAttributionEventV1 raw = validEvent();
        raw.setRawContentStored(true);
        assertReason("raw_content_forbidden", () -> verifier.verify(signed(raw), properties));

        BoardAttributionEventV1 trace = validEvent();
        trace.setTraceparent("not-a-traceparent");
        assertReason("traceparent_invalid", () -> verifier.verify(signed(trace), properties));

        BoardAttributionEventV1 expired = validEvent();
        expired.setExpiresAt("2026-07-23T10:00:29Z");
        assertReason("event_expired_or_ttl_invalid",
                () -> verifier.verify(signed(expired), properties));

        BoardAttributionEventV1 overlong = validEvent();
        overlong.setExpiresAt("2026-07-23T10:02:01Z");
        assertReason("event_expired_or_ttl_invalid",
                () -> verifier.verify(signed(overlong), properties));
    }

    @Test
    void rejectsOutOfOrderStageAndMissingPreviousDigest() {
        BoardAttributionEventV1 wrongSequence = validEvent();
        wrongSequence.setSequenceNo(2);
        assertReason("event_sequence_invalid",
                () -> verifier.verify(signed(wrongSequence), properties));

        BoardAttributionEventV1 missingPrevious = validEvent();
        missingPrevious.setEventType("INTENT_CLASSIFIED");
        missingPrevious.setSequenceNo(2);
        assertReason("previous_event_digest_invalid",
                () -> verifier.verify(signed(missingPrevious), properties));
    }

    private IndependentBoardAttributionProperties properties() {
        IndependentBoardAttributionProperties value =
                new IndependentBoardAttributionProperties();
        value.setReceiptTtlSeconds(120);
        return value;
    }

    private BoardAttributionEventV1 validEvent() {
        BoardAttributionEventV1 event = new BoardAttributionEventV1();
        event.setSchemaVersion("fbsir.independentBoardAttributionEvent.v1");
        event.setEventId("1".repeat(64));
        event.setReceiptId("2".repeat(64));
        event.setContractId("FBSIR_INDEPENDENT_BOARD_W1A_V1");
        event.setEventType("ENTRY_OBSERVED");
        event.setSequenceNo(1);
        event.setOccurredAt("2026-07-23T10:00:00Z");
        event.setProductId("fbsir-eight-seat-board");
        event.setPackageId("fbsir-eight-seat-board");
        event.setAgentName("board-convener");
        event.setMarketplace("experts");
        event.setListedSurface("listed_runtime_state");
        event.setListedManifestVersion("26.7.21");
        event.setEmbeddedContractVersion("26.7.20");
        event.setHostClientFamily("WORKBUDDY");
        event.setHostVersion("5.3.3.0");
        event.setTerminal("WORKBUDDY_WINDOWS");
        event.setChannel("OFFICIAL_EXPERTS");
        event.setRequestSource("WORKBUDDY_OFFICIAL_ENTRY");
        event.setIntentSignal("operating_diagnosis");
        event.setClassificationSource("PACKAGE_SCENE_ROUTER");
        event.setClassifierVersion("scene-lexicon-26.7.20-core.1");
        event.setConfidenceBucket("HIGH");
        event.setReviewMode("UNKNOWN");
        event.setJourneyId("3".repeat(64));
        event.setServerBindingId("srv_wave1Binding01");
        event.setSameBindingKey("4".repeat(64));
        event.setTenantSubjectDigest("5".repeat(64));
        event.setTrafficClass("NATURAL");
        event.setTrafficAuthority("API2_SERVER_CLASSIFIER_V1");
        event.setOutcome("SUCCESS");
        event.setPreviousEventDigest("");
        event.setTraceparent("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        event.setRawContentStored(false);
        event.setIssuedAt("2026-07-23T10:00:00Z");
        event.setExpiresAt("2026-07-23T10:02:00Z");
        event.setNonce("wave1-nonce-001");
        event.setKeyId(KEY_ID);
        event.setSignatureAlgorithm("hmac-sha256-v1");
        return event;
    }

    private BoardAttributionEventV1 signed(BoardAttributionEventV1 event) {
        event.setSignature("v1=" + hmac(canonical(event)));
        return event;
    }

    private String canonical(BoardAttributionEventV1 event) {
        try {
            Map<String, String> values = new TreeMap<>();
            values.put("agentName", event.getAgentName());
            values.put("channel", event.getChannel());
            values.put("classificationSource", event.getClassificationSource());
            values.put("classifierVersion", event.getClassifierVersion());
            values.put("confidenceBucket", event.getConfidenceBucket());
            values.put("contractId", event.getContractId());
            values.put("embeddedContractVersion", event.getEmbeddedContractVersion());
            values.put("eventId", event.getEventId());
            values.put("eventType", event.getEventType());
            values.put("expiresAt", event.getExpiresAt());
            values.put("hostClientFamily", event.getHostClientFamily());
            values.put("hostVersion", event.getHostVersion());
            values.put("intentSignal", event.getIntentSignal());
            values.put("issuedAt", event.getIssuedAt());
            values.put("journeyId", event.getJourneyId());
            values.put("keyId", event.getKeyId());
            values.put("listedManifestVersion", event.getListedManifestVersion());
            values.put("listedSurface", event.getListedSurface());
            values.put("marketplace", event.getMarketplace());
            values.put("nonce", event.getNonce());
            values.put("occurredAt", event.getOccurredAt());
            values.put("outcome", event.getOutcome());
            values.put("packageId", event.getPackageId());
            values.put("previousEventDigest", event.getPreviousEventDigest());
            values.put("productId", event.getProductId());
            values.put("rawContentStored", Boolean.toString(event.isRawContentStored()));
            values.put("receiptId", event.getReceiptId());
            values.put("requestSource", event.getRequestSource());
            values.put("reviewMode", event.getReviewMode());
            values.put("sameBindingKey", event.getSameBindingKey());
            values.put("schemaVersion", event.getSchemaVersion());
            values.put("sequenceNo", Long.toString(event.getSequenceNo()));
            values.put("serverBindingId", event.getServerBindingId());
            values.put("signatureAlgorithm", event.getSignatureAlgorithm());
            values.put("tenantSubjectDigest", event.getTenantSubjectDigest());
            values.put("terminal", event.getTerminal());
            values.put("traceparent", event.getTraceparent());
            values.put("trafficAuthority", event.getTrafficAuthority());
            values.put("trafficClass", event.getTrafficClass());
            return JSON.writeValueAsString(values);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(
                    mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private void assertReason(String reason, Runnable action) {
        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, action::run);
        assertEquals(reason, error.getMessage());
    }
}
