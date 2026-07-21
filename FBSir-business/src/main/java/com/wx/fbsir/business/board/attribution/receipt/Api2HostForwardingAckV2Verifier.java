package com.wx.fbsir.business.board.attribution.receipt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.fbsir.business.board.attribution.config.IndependentBoardAttributionProperties;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Pure, side-effect-free verifier for API2 fbss.hostForwardingAck.v2 receipts.
 *
 * U3W deliberately requires an explicit wire keyId, a covered event, and ISO
 * Instant timestamps even though the current API2 verifier accepts a few wider
 * forms. This verifier does not reserve nonces: callers must lock and consume
 * the matching durable challenge in the same transaction that accepts the
 * projected evidence. Do not register it against the legacy lossy event seam.
 */
public final class Api2HostForwardingAckV2Verifier {
    public static final String SCHEMA_VERSION = "fbss.hostForwardingAck.v2";
    public static final String SIGNATURE_ALGORITHM = "hmac-sha256-v1";
    public static final int MAX_TTL_SECONDS = 120;
    private static final int MIN_SECRET_BYTES = 32;
    private static final int MAX_SIGNED_FIELD_CHARS = 4_096;
    private static final int MAX_CANONICAL_CHARS = 65_536;
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Pattern HEX_64 = Pattern.compile("[0-9a-fA-F]{64}");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> REQUIRED_FIELDS = List.of(
            "schemaVersion", "ackId", "ackStage", "releaseId", "serverBindingId",
            "anonymousUserCodeHash", "chainFingerprint", "productId", "expertEntryId",
            "entrySurface", "entryPromptCode", "channelTrack", "packCode", "scenePackId",
            "nextTool", "actionEnvelopeId", "actionEnvelopeDigest", "toolArgumentsDigest",
            "challengeId", "requestDigest", "issuedAt", "expiresAt", "nonce", "keyId");

    private final Map<String, byte[]> keyring;
    private final Clock clock;

    public Api2HostForwardingAckV2Verifier(Map<String, String> encodedKeys, Clock clock) {
        this.keyring = decodeKeyring(encodedKeys);
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public boolean isConfigured() {
        return !keyring.isEmpty();
    }

    public VerifiedBoardApi2Receipt verify(
            BoardApi2HostForwardingAckV2 ack,
            BoardApi2ReceiptExpectedContext expected,
            IndependentBoardAttributionProperties properties) {
        if (ack == null) {
            reject("ack_required");
        }
        if (expected == null) {
            reject("expected_context_required");
        }
        if (properties == null) {
            reject("properties_required");
        }

        Map<String, String> signed = signedFields(ack);
        for (String field : REQUIRED_FIELDS) {
            if (text(signed.get(field)).isEmpty()) {
                reject("required_field_missing:" + field);
            }
        }
        for (Map.Entry<String, String> entry : signed.entrySet()) {
            if (entry.getValue().length() > MAX_SIGNED_FIELD_CHARS) {
                reject("field_too_long:" + entry.getKey());
            }
        }
        if (!SCHEMA_VERSION.equals(text(ack.schemaVersion()))) {
            reject("schema_unsupported");
        }
        if (!SIGNATURE_ALGORITHM.equals(text(ack.signatureAlgorithm()))) {
            reject("signature_algorithm_unsupported");
        }
        if (!isConfigured()) {
            reject("server_signature_keyring_unconfigured");
        }

        String keyId = text(ack.keyId());
        byte[] secret = keyring.get(keyId);
        if (secret == null) {
            reject("server_signature_key_unknown");
        }

        Instant issuedAt = parseInstant(ack.issuedAt());
        Instant expiresAt = parseInstant(ack.expiresAt());
        Instant now = clock.instant();
        long ttlMillis;
        try {
            ttlMillis = expiresAt.toEpochMilli() - issuedAt.toEpochMilli();
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException("timestamp_malformed", error);
        }
        long configuredTtlMillis = Math.max(1, Math.min(properties.getReceiptTtlSeconds(), MAX_TTL_SECONDS)) * 1_000L;
        if (ttlMillis <= 0 || ttlMillis > configuredTtlMillis || now.isBefore(issuedAt) || now.isAfter(expiresAt)) {
            reject("ack_expired_or_ttl_invalid");
        }

        verifyExpectedContext(ack, expected);
        String nonceHash = sha256Hex(text(ack.nonce()).getBytes(StandardCharsets.UTF_8));
        if (!MessageDigest.isEqual(
                nonceHash.getBytes(StandardCharsets.US_ASCII),
                text(expected.nonceHash()).getBytes(StandardCharsets.US_ASCII))) {
            reject("expected_nonceHash_mismatch");
        }

        String suppliedHex = signatureHex(ack.signature());
        if (suppliedHex.isEmpty()) {
            reject("signature_malformed");
        }
        String canonical = canonicalJson(signed);
        if (canonical.length() > MAX_CANONICAL_CHARS) {
            reject("receipt_too_large");
        }
        byte[] computed = hmacSha256(secret, canonical.getBytes(StandardCharsets.UTF_8));
        byte[] supplied = hexBytes(suppliedHex);
        if (!MessageDigest.isEqual(computed, supplied)) {
            reject("signature_mismatch");
        }

        return new VerifiedBoardApi2Receipt(
                ack, text(expected.tenantSubjectDigest()),
                nonceHash, suppliedHex,
                sha256Hex(canonical.getBytes(StandardCharsets.UTF_8)),
                productSignatureDigest(ack), issuedAt, expiresAt, text(ack.coveredEventId()));
    }

    private void verifyExpectedContext(BoardApi2HostForwardingAckV2 ack, BoardApi2ReceiptExpectedContext expected) {
        Map<String, String> expectedValues = new LinkedHashMap<>();
        expectedValues.put("ackStage", expected.ackStage());
        expectedValues.put("releaseId", expected.releaseId());
        expectedValues.put("serverBindingId", expected.serverBindingId());
        expectedValues.put("anonymousUserCodeHash", expected.anonymousUserCodeHash());
        expectedValues.put("chainFingerprint", expected.chainFingerprint());
        expectedValues.put("productId", expected.productId());
        expectedValues.put("expertEntryId", expected.expertEntryId());
        expectedValues.put("entrySurface", expected.entrySurface());
        expectedValues.put("entryPromptCode", expected.entryPromptCode());
        expectedValues.put("channelTrack", expected.channelTrack());
        expectedValues.put("packCode", expected.packCode());
        expectedValues.put("scenePackId", expected.scenePackId());
        expectedValues.put("nextTool", expected.nextTool());
        expectedValues.put("actionEnvelopeId", expected.actionEnvelopeId());
        expectedValues.put("actionEnvelopeDigest", expected.actionEnvelopeDigest());
        expectedValues.put("toolArgumentsDigest", expected.toolArgumentsDigest());
        expectedValues.put("challengeId", expected.challengeId());
        expectedValues.put("requestDigest", expected.requestDigest());
        expectedValues.put("coveredEventId", expected.coveredEventId());
        expectedValues.put("tenantSubjectDigest", expected.tenantSubjectDigest());
        expectedValues.put("nonceHash", expected.nonceHash());

        Map<String, String> actualValues = new LinkedHashMap<>();
        actualValues.put("ackStage", ack.ackStage());
        actualValues.put("releaseId", ack.releaseId());
        actualValues.put("serverBindingId", ack.serverBindingId());
        actualValues.put("anonymousUserCodeHash", ack.anonymousUserCodeHash());
        actualValues.put("chainFingerprint", ack.chainFingerprint());
        actualValues.put("productId", ack.productId());
        actualValues.put("expertEntryId", ack.expertEntryId());
        actualValues.put("entrySurface", ack.entrySurface());
        actualValues.put("entryPromptCode", ack.entryPromptCode());
        actualValues.put("channelTrack", ack.channelTrack());
        actualValues.put("packCode", ack.packCode());
        actualValues.put("scenePackId", ack.scenePackId());
        actualValues.put("nextTool", ack.nextTool());
        actualValues.put("actionEnvelopeId", ack.actionEnvelopeId());
        actualValues.put("actionEnvelopeDigest", ack.actionEnvelopeDigest());
        actualValues.put("toolArgumentsDigest", ack.toolArgumentsDigest());
        actualValues.put("challengeId", ack.challengeId());
        actualValues.put("requestDigest", ack.requestDigest());
        actualValues.put("coveredEventId", ack.coveredEventId());

        for (Map.Entry<String, String> entry : expectedValues.entrySet()) {
            String field = entry.getKey();
            String expectedValue = text(entry.getValue());
            if (expectedValue.isEmpty()) {
                reject("expected_" + field + "_missing");
            }
            if (expectedValue.length() > MAX_SIGNED_FIELD_CHARS) {
                reject("expected_" + field + "_too_long");
            }
            if (actualValues.containsKey(field) && !expectedValue.equals(text(actualValues.get(field)))) {
                reject("expected_" + field + "_mismatch");
            }
        }
    }

    private Map<String, String> signedFields(BoardApi2HostForwardingAckV2 ack) {
        Map<String, String> values = new TreeMap<>();
        values.put("schemaVersion", text(ack.schemaVersion()));
        values.put("ackId", text(ack.ackId()));
        values.put("ackStage", text(ack.ackStage()));
        values.put("releaseId", text(ack.releaseId()));
        values.put("serverBindingId", text(ack.serverBindingId()));
        values.put("anonymousUserCodeHash", text(ack.anonymousUserCodeHash()));
        values.put("chainFingerprint", text(ack.chainFingerprint()));
        values.put("productId", text(ack.productId()));
        values.put("expertEntryId", text(ack.expertEntryId()));
        values.put("entrySurface", text(ack.entrySurface()));
        values.put("entryPromptCode", text(ack.entryPromptCode()));
        values.put("channelTrack", text(ack.channelTrack()));
        values.put("packCode", text(ack.packCode()));
        values.put("scenePackId", text(ack.scenePackId()));
        values.put("nextTool", text(ack.nextTool()));
        values.put("actionEnvelopeId", text(ack.actionEnvelopeId()));
        values.put("actionEnvelopeDigest", text(ack.actionEnvelopeDigest()));
        values.put("toolArgumentsDigest", text(ack.toolArgumentsDigest()));
        values.put("challengeId", text(ack.challengeId()));
        values.put("requestDigest", text(ack.requestDigest()));
        putOptional(values, "coveredEventId", ack.coveredEventId());
        putOptional(values, "previousServiceEventId", ack.previousServiceEventId());
        values.put("issuedAt", text(ack.issuedAt()));
        values.put("expiresAt", text(ack.expiresAt()));
        values.put("nonce", text(ack.nonce()));
        values.put("keyId", text(ack.keyId()));
        values.put("signatureAlgorithm", SIGNATURE_ALGORITHM);
        return values;
    }

    private String productSignatureDigest(BoardApi2HostForwardingAckV2 ack) {
        Map<String, String> values = new TreeMap<>();
        values.put("productId", text(ack.productId()));
        values.put("expertEntryId", text(ack.expertEntryId()));
        values.put("entrySurface", text(ack.entrySurface()));
        values.put("entryPromptCode", text(ack.entryPromptCode()));
        values.put("channelTrack", text(ack.channelTrack()));
        values.put("packCode", text(ack.packCode()));
        values.put("scenePackId", text(ack.scenePackId()));
        return sha256Hex(canonicalJson(values).getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, byte[]> decodeKeyring(Map<String, String> encodedKeys) {
        Map<String, byte[]> decoded = new LinkedHashMap<>();
        if (encodedKeys == null) {
            return Map.of();
        }
        for (Map.Entry<String, String> entry : encodedKeys.entrySet()) {
            String keyId = text(entry.getKey());
            byte[] secret = decodeSecret(entry.getValue());
            if (!keyId.isEmpty() && secret != null && secret.length >= MIN_SECRET_BYTES) {
                decoded.put(keyId, secret.clone());
            }
        }
        return Map.copyOf(decoded);
    }

    private static byte[] decodeSecret(String encoded) {
        String value = text(encoded);
        if (value.isEmpty()) {
            return null;
        }
        try {
            if (value.startsWith("base64:")) {
                return Base64.getDecoder().decode(value.substring(7));
            }
            if (value.startsWith("hex:")) {
                String hex = value.substring(4);
                if (hex.length() % 2 != 0 || !hex.matches("[0-9a-fA-F]+")) {
                    return null;
                }
                return hexBytes(hex);
            }
            String raw = value.startsWith("utf8:") ? value.substring(5) : value;
            return raw.getBytes(StandardCharsets.UTF_8);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private static Instant parseInstant(String value) {
        try {
            return Instant.parse(text(value));
        } catch (DateTimeException error) {
            throw new IllegalArgumentException("timestamp_malformed", error);
        }
    }

    private static String signatureHex(String signature) {
        String value = text(signature).toLowerCase(Locale.ROOT);
        if (value.length() > 80) {
            return "";
        }
        if (value.startsWith("v1=")) {
            value = value.substring(3);
        } else if (value.startsWith("sha256=")) {
            value = value.substring(7);
        }
        return HEX_64.matcher(value).matches() ? value : "";
    }

    private static byte[] hmacSha256(byte[] secret, byte[] message) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(message);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("HmacSHA256 is unavailable", error);
        }
    }

    private static String canonicalJson(Map<String, String> values) {
        try {
            return JSON.writeValueAsString(new TreeMap<>(values));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Unable to canonicalize API2 receipt", error);
        }
    }

    private static String sha256Hex(byte[] input) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(input));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static byte[] hexBytes(String hex) {
        byte[] result = new byte[hex.length() / 2];
        for (int index = 0; index < hex.length(); index += 2) {
            result[index / 2] = (byte) Integer.parseInt(hex.substring(index, index + 2), 16);
        }
        return result;
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value & 0xff));
        }
        return result.toString();
    }

    private static void putOptional(Map<String, String> values, String key, String value) {
        String normalized = text(value);
        if (!normalized.isEmpty()) {
            values.put(key, normalized);
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static void reject(String reason) {
        throw new IllegalArgumentException(reason);
    }
}
