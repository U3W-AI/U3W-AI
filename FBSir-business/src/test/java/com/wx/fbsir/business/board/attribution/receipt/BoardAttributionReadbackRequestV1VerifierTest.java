package com.wx.fbsir.business.board.attribution.receipt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.fbsir.business.board.attribution.config.IndependentBoardAttributionProperties;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardAttributionReadbackRequestV1VerifierTest {
    private static final String KEY_ID = "readback-k1";
    private static final String PREVIOUS_KEY_ID = "readback-k0";
    private static final String SECRET =
            "0123456789abcdef0123456789abcdef";
    private static final String PREVIOUS_SECRET =
            "abcdef0123456789abcdef0123456789";
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-23T08:30:30Z"), ZoneOffset.UTC);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final IndependentBoardAttributionProperties properties =
            properties();
    private final BoardAttributionReadbackRequestV1Verifier verifier =
            new BoardAttributionReadbackRequestV1Verifier(
                    Map.of(
                            KEY_ID, "utf8:" + SECRET,
                            PREVIOUS_KEY_ID, "utf8:" + PREVIOUS_SECRET),
                    CLOCK);

    @Test
    void verifiesExactTupleWithoutReturningRawNonce() {
        BoardAttributionReadbackRequestV1 request = signed(valid(), SECRET);

        VerifiedBoardAttributionReadbackRequest verified =
                verifier.verify(request, properties);

        assertEquals(request.getEventId(), verified.eventId());
        assertEquals(request.getReceiptId(), verified.receiptId());
        assertEquals(request.getEventDigest(), verified.eventDigest());
        assertEquals(KEY_ID, verified.signerKeyId());
        assertEquals(64, verified.nonceHash().length());
    }

    @Test
    void canonicalPayloadUsesTheContractFieldOrderAndDomain() {
        BoardAttributionReadbackRequestV1 request = valid();

        assertEquals(
                "FBSIR_INDEPENDENT_BOARD_READBACK_V1\n"
                        + "{\"method\":\"POST\","
                        + "\"path\":\"/internal/independent-board/attribution/events/readback\","
                        + "\"schemaVersion\":\"fbsir.independentBoardAttributionReadbackRequest.v1\","
                        + "\"eventId\":\"" + "1".repeat(64) + "\","
                        + "\"receiptId\":\"" + "2".repeat(64) + "\","
                        + "\"eventDigest\":\"" + "3".repeat(64) + "\","
                        + "\"issuedAt\":\"2026-08-23T08:30:00Z\","
                        + "\"expiresAt\":\"2026-08-23T08:31:00Z\","
                        + "\"nonce\":\"readback-nonce-0001\","
                        + "\"keyId\":\"readback-k1\"}",
                BoardAttributionReadbackRequestV1Verifier
                        .canonicalSigningPayload(request));
    }

    @Test
    void signsEveryDecisionAgainstRequestAndActualHttpStatus()
            throws Exception {
        BoardAttributionReadbackRequestV1 wire = signed(valid(), SECRET);
        VerifiedBoardAttributionReadbackRequest request =
                verifier.verify(wire, properties);
        IndependentBoardAttributionProperties responseProperties =
                properties();
        responseProperties.setActiveEventKeyId(KEY_ID);
        responseProperties.setAuthoritativeReadbackReceiverReleaseId(
                "w05e-readback-test");
        responseProperties.setAuthoritativeReadbackReceiverJarSha256(
                "a".repeat(64));
        BoardAttributionReadbackResponseV1 unsigned = unsigned(
                request, "NOT_FOUND_AUTHORITATIVE", true);

        BoardAttributionReadbackResponseV1 signed = verifier.signResponse(
                unsigned, request, 404, responseProperties);

        assertEquals(404, signed.httpStatus());
        assertEquals(request.requestDigest(), signed.requestDigest());
        assertEquals(request.nonceHash(), signed.requestNonceHash());
        assertEquals(KEY_ID, signed.keyId());
        assertEquals(64, signed.signature().length());
        assertTrue(JSON.writeValueAsBytes(signed).length <= 16 * 1024);
        assertEquals(
                signed.signature(),
                HexFormat.of().formatHex(hmac(
                        SECRET,
                        BoardAttributionReadbackRequestV1Verifier
                                .canonicalResponseSigningPayload(signed))));

        assertThrows(
                IllegalStateException.class,
                () -> verifier.signResponse(
                        unsigned, request, 200, responseProperties));
        assertThrows(
                IllegalStateException.class,
                () -> verifier.signResponse(
                        unsigned(
                                request,
                                "READBACK_UNAVAILABLE",
                                true),
                        request,
                        503,
                        responseProperties));
    }

    @Test
    void verifiesTheSharedNodeToJavaReadbackGoldenVector() throws Exception {
        JsonNode vector;
        try (var input = getClass().getClassLoader().getResourceAsStream(
                "independent-board-attribution-readback-v1-golden-vector.json")) {
            if (input == null) {
                throw new IllegalStateException("readback golden vector missing");
            }
            vector = JSON.readTree(input);
        }
        BoardAttributionReadbackRequestV1 request = JSON.treeToValue(
                vector.get("request"),
                BoardAttributionReadbackRequestV1.class);
        Clock clock = Clock.fixed(
                Instant.parse(vector.get("verificationClock").asText()),
                ZoneOffset.UTC);
        BoardAttributionReadbackRequestV1Verifier vectorVerifier =
                new BoardAttributionReadbackRequestV1Verifier(
                        Map.of(
                                request.getKeyId(),
                                vector.get("encodedSecret").asText()),
                        clock);
        IndependentBoardAttributionProperties responseProperties =
                properties();
        responseProperties.setActiveEventKeyId(request.getKeyId());
        responseProperties.setActiveEventKey(
                vector.get("encodedSecret").asText());
        JsonNode expected = vector.get("response");
        responseProperties.setAuthoritativeReadbackReceiverReleaseId(
                expected.get("receiverReleaseId").asText());
        responseProperties.setAuthoritativeReadbackReceiverJarSha256(
                expected.get("receiverJarSha256").asText());

        VerifiedBoardAttributionReadbackRequest verified =
                vectorVerifier.verify(request, responseProperties);
        BoardAttributionReadbackResponseV1 signed = vectorVerifier.signResponse(
                unsigned(verified, "COMMITTED_EXACT", true),
                verified,
                200,
                responseProperties);

        assertEquals(expected.get("requestDigest").asText(),
                verified.requestDigest());
        assertEquals(expected.get("signature").asText(),
                signed.signature());
        assertEquals(expected.get("requestNonceHash").asText(),
                signed.requestNonceHash());
    }

    @Test
    void acceptsPreviousKeyDuringBoundedRotation() {
        BoardAttributionReadbackRequestV1 request = valid();
        request.setKeyId(PREVIOUS_KEY_ID);

        VerifiedBoardAttributionReadbackRequest verified = verifier.verify(
                signed(request, PREVIOUS_SECRET), properties);

        assertEquals(PREVIOUS_KEY_ID, verified.signerKeyId());
    }

    @Test
    void rejectsTamperingUnknownKeyAndNoncanonicalTextGenerically() {
        BoardAttributionReadbackRequestV1 tampered = signed(valid(), SECRET);
        tampered.setEventDigest("4".repeat(64));
        assertInvalid(tampered);

        BoardAttributionReadbackRequestV1 unknownKey = valid();
        unknownKey.setKeyId("unknown-k1");
        assertInvalid(signed(unknownKey, SECRET));

        BoardAttributionReadbackRequestV1 padded = signed(valid(), SECRET);
        padded.setEventId(" " + padded.getEventId());
        assertInvalid(padded);

        BoardAttributionReadbackRequestV1 uppercase = valid();
        uppercase.setEventDigest("A".repeat(64));
        assertInvalid(signed(uppercase, SECRET));
    }

    @Test
    void rejectsExpiredOverlongAndFarFutureReceipts() {
        BoardAttributionReadbackRequestV1 expired = valid();
        expired.setExpiresAt("2026-08-23T08:30:29Z");
        assertInvalid(signed(expired, SECRET));

        BoardAttributionReadbackRequestV1 overlong = valid();
        overlong.setExpiresAt("2026-08-23T08:31:01Z");
        assertInvalid(signed(overlong, SECRET));

        BoardAttributionReadbackRequestV1 future = valid();
        future.setIssuedAt("2026-08-23T08:31:01Z");
        future.setExpiresAt("2026-08-23T08:31:30Z");
        assertInvalid(signed(future, SECRET));

        BoardAttributionReadbackRequestV1 offset = valid();
        offset.setIssuedAt("2026-08-23T08:30:00+00:00");
        assertInvalid(signed(offset, SECRET));

        BoardAttributionReadbackRequestV1 overprecise = valid();
        overprecise.setIssuedAt("2026-08-23T08:30:00.1234Z");
        assertInvalid(signed(overprecise, SECRET));

        properties.setAuthoritativeReadbackTtlSeconds(30);
        BoardAttributionReadbackRequestV1 propertyBound = valid();
        assertInvalid(signed(propertyBound, SECRET));
    }

    @Test
    void rejectsMalformedSchemaTupleNonceAndSignature() {
        BoardAttributionReadbackRequestV1 schema = valid();
        schema.setSchemaVersion("wrong");
        assertInvalid(signed(schema, SECRET));

        BoardAttributionReadbackRequestV1 id = valid();
        id.setEventId("1".repeat(63));
        assertInvalid(signed(id, SECRET));

        BoardAttributionReadbackRequestV1 nonce = valid();
        nonce.setNonce("short");
        assertInvalid(signed(nonce, SECRET));

        BoardAttributionReadbackRequestV1 signature = valid();
        signature.setSignature("f".repeat(63));
        assertInvalid(signature);
    }

    private void assertInvalid(BoardAttributionReadbackRequestV1 request) {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> verifier.verify(request, properties));
        assertEquals("readback_request_invalid", error.getMessage());
    }

    private IndependentBoardAttributionProperties properties() {
        IndependentBoardAttributionProperties value =
                new IndependentBoardAttributionProperties();
        value.setAuthoritativeReadbackTtlSeconds(60);
        return value;
    }

    private BoardAttributionReadbackRequestV1 valid() {
        BoardAttributionReadbackRequestV1 request =
                new BoardAttributionReadbackRequestV1();
        request.setSchemaVersion(
                BoardAttributionReadbackRequestV1Verifier.SCHEMA_VERSION);
        request.setEventId("1".repeat(64));
        request.setReceiptId("2".repeat(64));
        request.setEventDigest("3".repeat(64));
        request.setIssuedAt("2026-08-23T08:30:00Z");
        request.setExpiresAt("2026-08-23T08:31:00Z");
        request.setNonce("readback-nonce-0001");
        request.setKeyId(KEY_ID);
        request.setSignature("");
        return request;
    }

    private BoardAttributionReadbackRequestV1 signed(
            BoardAttributionReadbackRequestV1 request,
            String secret) {
        request.setSignature(HexFormat.of().formatHex(hmac(
                secret,
                BoardAttributionReadbackRequestV1Verifier
                        .canonicalSigningPayload(request))));
        return request;
    }

    private BoardAttributionReadbackResponseV1 unsigned(
            VerifiedBoardAttributionReadbackRequest request,
            String status,
            boolean authoritative) {
        return new BoardAttributionReadbackResponseV1(
                BoardAttributionReadbackResponseV1.SCHEMA_VERSION,
                status,
                request.eventId(),
                request.receiptId(),
                request.eventDigest(),
                authoritative,
                0,
                "",
                request.nonceHash(),
                "",
                "",
                "w05e-readback-test",
                "a".repeat(64),
                false,
                "",
                "");
    }

    private byte[] hmac(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }
}
