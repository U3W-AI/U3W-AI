package com.wx.fbsir.business.board.oauth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * JDK-only secret and PKCE primitives for the independent-board OAuth profile.
 * Raw generated values must never be logged or persisted by callers.
 */
public final class BoardOAuthCrypto {
    public static final int SECRET_BYTES = 32;
    public static final int SHA256_BYTES = 32;
    public static final int MIN_PKCE_VERIFIER_LENGTH = 43;
    public static final int MAX_PKCE_VERIFIER_LENGTH = 128;

    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();
    private static final Pattern PKCE_VERIFIER = Pattern.compile(
            "\\A[A-Za-z0-9\\-._~]{43,128}\\z");
    private static final Pattern PKCE_CHALLENGE = Pattern.compile(
            "\\A[A-Za-z0-9_-]{43}\\z");

    private final SecureRandom secureRandom;

    public BoardOAuthCrypto() {
        this.secureRandom = new SecureRandom();
    }

    /**
     * Generates 256 bits from the JDK CSPRNG and encodes them as unpadded
     * base64url (43 ASCII characters).
     */
    public String generateOpaqueSecret() {
        byte[] randomBytes = new byte[SECRET_BYTES];
        secureRandom.nextBytes(randomBytes);
        return BASE64_URL.encodeToString(randomBytes);
    }

    /**
     * Generates an RFC 7636 verifier with 256 bits of CSPRNG entropy.
     */
    public String generatePkceVerifier() {
        return generateOpaqueSecret();
    }

    public static boolean isValidPkceVerifier(String verifier) {
        return verifier != null && PKCE_VERIFIER.matcher(verifier).matches();
    }

    public static boolean isValidPkceS256Challenge(String challenge) {
        return challenge != null && PKCE_CHALLENGE.matcher(challenge).matches();
    }

    /**
     * Computes BASE64URL(SHA-256(ASCII(verifier))) without padding.
     */
    public static String pkceS256Challenge(String verifier) {
        if (!isValidPkceVerifier(verifier)) {
            throw new IllegalArgumentException("PKCE verifier does not satisfy RFC 7636 shape constraints");
        }
        return BASE64_URL.encodeToString(sha256Ascii(verifier));
    }

    /**
     * Verifies S256 without content-dependent String comparison.
     */
    public static boolean matchesPkceS256(String verifier, String expectedChallenge) {
        if (!isValidPkceVerifier(verifier) || !isValidPkceS256Challenge(expectedChallenge)) {
            return false;
        }
        byte[] actual = pkceS256Challenge(verifier).getBytes(StandardCharsets.US_ASCII);
        byte[] expected = expectedChallenge.getBytes(StandardCharsets.US_ASCII);
        return constantTimeEquals(actual, expected);
    }

    public static byte[] sha256Ascii(String value) {
        if (!isAscii(value)) {
            throw new IllegalArgumentException("SHA-256 protocol input must be non-null ASCII");
        }
        return sha256(value.getBytes(StandardCharsets.US_ASCII));
    }

    public static String sha256HexAscii(String value) {
        return HexFormat.of().formatHex(sha256Ascii(value));
    }

    public static byte[] sha256(byte[] value) {
        if (value == null) {
            throw new IllegalArgumentException("SHA-256 input must not be null");
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /**
     * Uses the JDK constant-time digest comparison primitive. Null values are
     * rejected before comparison; callers should compare fixed-size values.
     */
    public static boolean constantTimeEquals(byte[] trusted, byte[] candidate) {
        return trusted != null && candidate != null && MessageDigest.isEqual(trusted, candidate);
    }

    /**
     * Computes the presented ASCII value's digest and compares it against an
     * expected 32-byte digest without content-dependent equality.
     */
    public static boolean matchesSha256Digest(String presentedValue, byte[] expectedDigest) {
        if (!isAscii(presentedValue)
                || expectedDigest == null
                || expectedDigest.length != SHA256_BYTES) {
            return false;
        }
        return constantTimeEquals(expectedDigest, sha256Ascii(presentedValue));
    }

    private static boolean isAscii(String value) {
        if (value == null) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) > 0x7f) {
                return false;
            }
        }
        return true;
    }
}
