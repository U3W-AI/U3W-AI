package com.wx.fbsir.business.board.attribution.binding;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Derives the binding key inside U3W using a secret that is independent from
 * the API2 event-signing key. Upstream supplied binding keys are never trusted.
 */
public final class BoardSameBindingKeyDeriver {
    private static final int MIN_SECRET_BYTES = 32;
    private static final Pattern HEX_64 = Pattern.compile("[0-9a-f]{64}");
    private final byte[] secret;

    public BoardSameBindingKeyDeriver(String encodedSecret) {
        byte[] decoded = decode(encodedSecret);
        if (decoded == null || decoded.length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "same_binding_secret_must_be_at_least_32_bytes");
        }
        this.secret = decoded.clone();
    }

    public String derive(
            String contractId,
            String tenantSubjectDigest,
            String serverBindingId,
            String journeyId,
            String productId,
            String listedManifestVersion) {
        String canonical = String.join("|",
                component(contractId),
                component(tenantSubjectDigest),
                component(serverBindingId),
                component(journeyId),
                component(productId),
                component(listedManifestVersion));
        return HexFormat.of().formatHex(hmac(
                secret, canonical.getBytes(StandardCharsets.UTF_8)));
    }

    public boolean matches(
            String supplied,
            String contractId,
            String tenantSubjectDigest,
            String serverBindingId,
            String journeyId,
            String productId,
            String listedManifestVersion) {
        if (supplied == null || !HEX_64.matcher(supplied).matches()) {
            return false;
        }
        byte[] expected = HexFormat.of().parseHex(derive(
                contractId, tenantSubjectDigest, serverBindingId, journeyId,
                productId, listedManifestVersion));
        return MessageDigest.isEqual(
                expected, HexFormat.of().parseHex(supplied));
    }

    private static String component(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 256
                || normalized.contains("|")
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                    "same_binding_component_invalid");
        }
        return normalized;
    }

    private static byte[] decode(String encoded) {
        String value = encoded == null ? "" : encoded.trim();
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

    private static byte[] hmac(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("HmacSHA256 unavailable", error);
        }
    }
}
