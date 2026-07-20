package com.wx.fbsir.business.board.oauth;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardOAuthCryptoTest {
    private static final String RFC_7636_VERIFIER =
            "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String RFC_7636_CHALLENGE =
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    @Test
    void generatedSecretsUseThirtyTwoCsprngBytesAndUnpaddedBase64Url() {
        BoardOAuthCrypto crypto = new BoardOAuthCrypto();
        Set<String> generated = new HashSet<>();

        for (int index = 0; index < 64; index++) {
            String secret = crypto.generateOpaqueSecret();
            assertEquals(43, secret.length());
            assertTrue(secret.matches("[A-Za-z0-9_-]{43}"));
            assertFalse(secret.contains("="));
            generated.add(secret);
        }

        assertEquals(64, generated.size());
        String verifier = crypto.generatePkceVerifier();
        assertTrue(BoardOAuthCrypto.isValidPkceVerifier(verifier));
        assertTrue(BoardOAuthCrypto.isValidPkceS256Challenge(
                BoardOAuthCrypto.pkceS256Challenge(verifier)));
    }

    @Test
    void sha256MatchesKnownVectorsAndUsesLowercaseHex() {
        assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                BoardOAuthCrypto.sha256HexAscii("abc"));
        assertArrayEquals(
                BoardOAuthCrypto.sha256("abc".getBytes(StandardCharsets.US_ASCII)),
                BoardOAuthCrypto.sha256Ascii("abc"));
        assertThrows(IllegalArgumentException.class,
                () -> BoardOAuthCrypto.sha256Ascii("令牌"));
        assertThrows(IllegalArgumentException.class,
                () -> BoardOAuthCrypto.sha256((byte[]) null));
    }

    @Test
    void pkceS256MatchesRfc7636AndUsesConstantTimeVerificationPath() {
        assertTrue(BoardOAuthCrypto.isValidPkceVerifier(RFC_7636_VERIFIER));
        assertEquals(RFC_7636_CHALLENGE,
                BoardOAuthCrypto.pkceS256Challenge(RFC_7636_VERIFIER));
        assertTrue(BoardOAuthCrypto.matchesPkceS256(
                RFC_7636_VERIFIER, RFC_7636_CHALLENGE));
        assertFalse(BoardOAuthCrypto.matchesPkceS256(
                RFC_7636_VERIFIER, "A" + RFC_7636_CHALLENGE.substring(1)));
        assertFalse(BoardOAuthCrypto.matchesPkceS256(null, RFC_7636_CHALLENGE));
        assertFalse(BoardOAuthCrypto.matchesPkceS256(RFC_7636_VERIFIER, null));
    }

    @Test
    void pkceRejectsShortLongUnicodePaddedAndNonUnreservedVerifiers() {
        for (String rejected : List.of(
                "a".repeat(42),
                "a".repeat(129),
                "a".repeat(42) + "=",
                "a".repeat(42) + "+",
                "a".repeat(42) + "/",
                "a".repeat(42) + " ",
                "a".repeat(42) + "令")) {
            assertFalse(BoardOAuthCrypto.isValidPkceVerifier(rejected), rejected);
            assertThrows(IllegalArgumentException.class,
                    () -> BoardOAuthCrypto.pkceS256Challenge(rejected));
        }
        assertFalse(BoardOAuthCrypto.isValidPkceVerifier(null));
        assertFalse(BoardOAuthCrypto.isValidPkceS256Challenge(
                RFC_7636_CHALLENGE + "="));
        assertFalse(BoardOAuthCrypto.isValidPkceS256Challenge(
                RFC_7636_CHALLENGE.substring(1)));
    }

    @Test
    void digestComparisonFailsClosedForWrongShapeNullAndNonAscii() {
        byte[] digest = BoardOAuthCrypto.sha256Ascii("opaque-token");
        assertTrue(BoardOAuthCrypto.matchesSha256Digest("opaque-token", digest));
        assertFalse(BoardOAuthCrypto.matchesSha256Digest("opaque-tokem", digest));
        assertFalse(BoardOAuthCrypto.matchesSha256Digest(null, digest));
        assertFalse(BoardOAuthCrypto.matchesSha256Digest("令牌", digest));
        assertFalse(BoardOAuthCrypto.matchesSha256Digest("opaque-token", null));
        assertFalse(BoardOAuthCrypto.matchesSha256Digest("opaque-token", new byte[31]));

        assertTrue(BoardOAuthCrypto.constantTimeEquals(digest, digest.clone()));
        assertFalse(BoardOAuthCrypto.constantTimeEquals(digest, new byte[32]));
        assertFalse(BoardOAuthCrypto.constantTimeEquals(null, digest));
        assertFalse(BoardOAuthCrypto.constantTimeEquals(digest, null));
    }
}
