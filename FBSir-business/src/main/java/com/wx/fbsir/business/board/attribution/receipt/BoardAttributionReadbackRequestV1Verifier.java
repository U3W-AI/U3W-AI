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
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** HMAC verifier for the internal, default-off authoritative readback route. */
public class BoardAttributionReadbackRequestV1Verifier
        implements BoardAttributionReadbackRequestVerifier {
    public static final String SCHEMA_VERSION =
            "fbsir.independentBoardAttributionReadbackRequest.v1";
    public static final String DOMAIN_TAG =
            "FBSIR_INDEPENDENT_BOARD_READBACK_V1";
    public static final String RESPONSE_DOMAIN_TAG =
            "FBSIR_INDEPENDENT_BOARD_READBACK_RESPONSE_V1";
    public static final String METHOD = "POST";
    public static final String PATH =
            "/internal/independent-board/attribution/events/readback";
    private static final int MIN_SECRET_BYTES = 32;
    private static final int MAX_TTL_SECONDS = 60;
    private static final int MAX_CLOCK_SKEW_SECONDS = 30;
    private static final int MAX_CANONICAL_CHARS = 2048;
    private static final Pattern HEX_64 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern KEY_ID = Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9._-]{0,95}");
    private static final Pattern NONCE = Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9._:-]{15,127}");
    private static final Pattern UTC_INSTANT = Pattern.compile(
            "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}"
                    + "(?:\\.[0-9]{1,3})?Z");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Map<String, byte[]> keyring;
    private final Clock clock;

    public BoardAttributionReadbackRequestV1Verifier(
            Map<String, String> encodedKeys,
            Clock clock) {
        this.keyring = decodeKeyring(encodedKeys);
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public boolean isConfigured() {
        return !keyring.isEmpty();
    }

    @Override
    public VerifiedBoardAttributionReadbackRequest verify(
            BoardAttributionReadbackRequestV1 request,
            IndependentBoardAttributionProperties properties) {
        if (request == null || properties == null) {
            reject();
        }
        verifyCanonicalWireText(request);
        if (!SCHEMA_VERSION.equals(request.getSchemaVersion())
                || !HEX_64.matcher(request.getEventId()).matches()
                || !HEX_64.matcher(request.getReceiptId()).matches()
                || !HEX_64.matcher(request.getEventDigest()).matches()
                || !KEY_ID.matcher(request.getKeyId()).matches()
                || !NONCE.matcher(request.getNonce()).matches()
                || !UTC_INSTANT.matcher(request.getIssuedAt()).matches()
                || !UTC_INSTANT.matcher(request.getExpiresAt()).matches()
                || !HEX_64.matcher(request.getSignature()).matches()) {
            reject();
        }

        Instant issuedAt = instant(request.getIssuedAt());
        Instant expiresAt = instant(request.getExpiresAt());
        Instant now = clock.instant();
        int configuredTtl = Math.max(1, Math.min(
                properties.getAuthoritativeReadbackTtlSeconds(),
                MAX_TTL_SECONDS));
        Duration ttl = Duration.between(issuedAt, expiresAt);
        if (ttl.isZero() || ttl.isNegative()
                || ttl.compareTo(Duration.ofSeconds(configuredTtl)) > 0
                || now.isBefore(issuedAt.minusSeconds(
                    MAX_CLOCK_SKEW_SECONDS))
                || now.isAfter(expiresAt)) {
            reject();
        }
        byte[] secret = keyring.get(request.getKeyId());
        if (secret == null) {
            reject();
        }
        String canonical = canonicalSigningPayload(request);
        if (canonical.length() > MAX_CANONICAL_CHARS) {
            reject();
        }
        byte[] expected = hmacSha256(
                secret, canonical.getBytes(StandardCharsets.UTF_8));
        byte[] supplied;
        try {
            supplied = HexFormat.of().parseHex(request.getSignature());
        } catch (IllegalArgumentException error) {
            reject();
            return null;
        }
        if (!MessageDigest.isEqual(expected, supplied)) {
            reject();
        }
        return new VerifiedBoardAttributionReadbackRequest(
                request.getEventId(),
                request.getReceiptId(),
                request.getEventDigest(),
                issuedAt,
                expiresAt,
                sha256Hex(canonical.getBytes(StandardCharsets.UTF_8)),
                sha256Hex(request.getNonce().getBytes(StandardCharsets.UTF_8)),
                request.getKeyId());
    }

    @Override
    public BoardAttributionReadbackResponseV1 signResponse(
            BoardAttributionReadbackResponseV1 response,
            VerifiedBoardAttributionReadbackRequest request,
            int httpStatus,
            IndependentBoardAttributionProperties properties) {
        if (response == null || request == null || properties == null
                || !validStatus(response.status(), httpStatus)
                || !response.eventId().equals(request.eventId())
                || !response.receiptId().equals(request.receiptId())
                || !response.eventDigest().equals(request.eventDigest())
                || response.authoritativeRead() != (httpStatus != 503)
                || response.productCreditEligible()) {
            throw new IllegalStateException(
                    "readback_response_contract_invalid");
        }
        String activeKeyId = text(properties.getActiveEventKeyId());
        byte[] secret = keyring.get(activeKeyId);
        String releaseId = text(
                properties.getAuthoritativeReadbackReceiverReleaseId());
        String jarSha256 = text(
                properties.getAuthoritativeReadbackReceiverJarSha256());
        if (secret == null
                || !KEY_ID.matcher(activeKeyId).matches()
                || !releaseId.matches(
                    "[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")
                || !HEX_64.matcher(jarSha256).matches()) {
            throw new IllegalStateException(
                    "readback_response_signing_unavailable");
        }
        Instant readAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        int ttl = Math.max(1, Math.min(
                properties.getAuthoritativeReadbackTtlSeconds(),
                MAX_TTL_SECONDS));
        BoardAttributionReadbackResponseV1 unsigned =
                new BoardAttributionReadbackResponseV1(
                        BoardAttributionReadbackResponseV1.SCHEMA_VERSION,
                        response.status(),
                        request.eventId(),
                        request.receiptId(),
                        request.eventDigest(),
                        response.authoritativeRead(),
                        httpStatus,
                        request.requestDigest(),
                        request.nonceHash(),
                        readAt.toString(),
                        readAt.plusSeconds(ttl).toString(),
                        releaseId,
                        jarSha256,
                        false,
                        activeKeyId,
                        "");
        String signature = HexFormat.of().formatHex(hmacSha256(
                secret,
                canonicalResponseSigningPayload(unsigned)
                        .getBytes(StandardCharsets.UTF_8)));
        return new BoardAttributionReadbackResponseV1(
                unsigned.schemaVersion(),
                unsigned.status(),
                unsigned.eventId(),
                unsigned.receiptId(),
                unsigned.eventDigest(),
                unsigned.authoritativeRead(),
                unsigned.httpStatus(),
                unsigned.requestDigest(),
                unsigned.requestNonceHash(),
                unsigned.readAt(),
                unsigned.expiresAt(),
                unsigned.receiverReleaseId(),
                unsigned.receiverJarSha256(),
                false,
                unsigned.keyId(),
                signature);
    }

    /** Stable cross-language signing payload used by the future Node client. */
    public static String canonicalSigningPayload(
            BoardAttributionReadbackRequestV1 request) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put("method", METHOD);
        values.put("path", PATH);
        values.put("schemaVersion", text(request.getSchemaVersion()));
        values.put("eventId", text(request.getEventId()));
        values.put("receiptId", text(request.getReceiptId()));
        values.put("eventDigest", text(request.getEventDigest()));
        values.put("issuedAt", text(request.getIssuedAt()));
        values.put("expiresAt", text(request.getExpiresAt()));
        values.put("nonce", text(request.getNonce()));
        values.put("keyId", text(request.getKeyId()));
        try {
            return DOMAIN_TAG + "\n" + JSON.writeValueAsString(values);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException(
                    "Unable to canonicalize readback request", error);
        }
    }

    /** Stable signed response payload for Node verification. */
    public static String canonicalResponseSigningPayload(
            BoardAttributionReadbackResponseV1 response) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("schemaVersion", text(response.schemaVersion()));
        values.put("status", text(response.status()));
        values.put("httpStatus", response.httpStatus());
        values.put("eventId", text(response.eventId()));
        values.put("receiptId", text(response.receiptId()));
        values.put("eventDigest", text(response.eventDigest()));
        values.put("authoritativeRead", response.authoritativeRead());
        values.put("requestDigest", text(response.requestDigest()));
        values.put("requestNonceHash", text(response.requestNonceHash()));
        values.put("readAt", text(response.readAt()));
        values.put("expiresAt", text(response.expiresAt()));
        values.put("receiverReleaseId", text(response.receiverReleaseId()));
        values.put("receiverJarSha256", text(response.receiverJarSha256()));
        values.put("productCreditEligible",
                response.productCreditEligible());
        values.put("keyId", text(response.keyId()));
        try {
            return RESPONSE_DOMAIN_TAG + "\n"
                    + JSON.writeValueAsString(values);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException(
                    "Unable to canonicalize readback response", error);
        }
    }

    private static boolean validStatus(String status, int httpStatus) {
        return ("COMMITTED_EXACT".equals(status) && httpStatus == 200)
                || ("NOT_FOUND_AUTHORITATIVE".equals(status)
                    && httpStatus == 404)
                || ("IDENTITY_COLLISION".equals(status)
                    && httpStatus == 409)
                || ("READBACK_UNAVAILABLE".equals(status)
                    && httpStatus == 503);
    }

    private static void verifyCanonicalWireText(
            BoardAttributionReadbackRequestV1 request) {
        String[] values = {
                request.getSchemaVersion(), request.getEventId(),
                request.getReceiptId(), request.getEventDigest(),
                request.getIssuedAt(), request.getExpiresAt(),
                request.getNonce(), request.getKeyId(), request.getSignature()
        };
        for (String value : values) {
            if (value == null || !value.equals(value.trim())) {
                reject();
            }
        }
    }

    private static Map<String, byte[]> decodeKeyring(
            Map<String, String> encodedKeys) {
        if (encodedKeys == null || encodedKeys.isEmpty()) {
            return Map.of();
        }
        Map<String, byte[]> decoded = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : encodedKeys.entrySet()) {
            String keyId = text(entry.getKey());
            byte[] secret = decodeSecret(entry.getValue());
            if (KEY_ID.matcher(keyId).matches()
                    && secret != null && secret.length >= MIN_SECRET_BYTES) {
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
                if (hex.length() % 2 != 0
                        || !hex.matches("[0-9a-fA-F]+")) {
                    return null;
                }
                return HexFormat.of().parseHex(hex);
            }
            String raw = value.startsWith("utf8:")
                    ? value.substring(5) : value;
            return raw.getBytes(StandardCharsets.UTF_8);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private static Instant instant(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeException error) {
            reject();
            return null;
        }
    }

    private static byte[] hmacSha256(byte[] secret, byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(payload);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("HmacSHA256 unavailable", error);
        }
    }

    private static String sha256Hex(byte[] payload) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static void reject() {
        throw new IllegalArgumentException("readback_request_invalid");
    }
}
