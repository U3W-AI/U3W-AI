package com.wx.fbsir.business.board.oauth;

import com.wx.fbsir.business.board.config.IndependentBoardOAuthProperties;
import com.wx.fbsir.common.exception.ServiceException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AesGcmBoardOAuthStateCipherTest {
    private static final String STATE = "workbuddy-state-0123456789abcdef";

    @Test
    void stateRoundTripsOnlyWithTheBoundContextDigest() {
        AesGcmBoardOAuthStateCipher cipher = cipher(configuredProperties());
        byte[] aad = BoardOAuthCrypto.sha256Ascii("request-context-one");

        BoardOAuthStateCipher.EncryptedState encrypted = cipher.encrypt(STATE, aad);

        assertEquals("oauth-state-k1", encrypted.keyRef());
        assertEquals(12, encrypted.nonce().length);
        assertFalse(new String(encrypted.ciphertext(), StandardCharsets.US_ASCII).contains(STATE));
        assertEquals(STATE, cipher.decrypt(
                encrypted.keyRef(), encrypted.nonce(), encrypted.ciphertext(), aad));
    }

    @Test
    void encryptedTupleDefensivelyCopiesNonceAndCiphertext() {
        AesGcmBoardOAuthStateCipher cipher = cipher(configuredProperties());
        byte[] aad = BoardOAuthCrypto.sha256Ascii("request-context-two");
        BoardOAuthStateCipher.EncryptedState encrypted = cipher.encrypt(STATE, aad);
        byte[] nonce = encrypted.nonce();
        byte[] ciphertext = encrypted.ciphertext();

        nonce[0] ^= 1;
        ciphertext[0] ^= 1;

        assertEquals(STATE, cipher.decrypt(
                encrypted.keyRef(), encrypted.nonce(), encrypted.ciphertext(), aad));
    }

    @Test
    void tamperedCiphertextOrContextFailsClosed() {
        AesGcmBoardOAuthStateCipher cipher = cipher(configuredProperties());
        byte[] aad = BoardOAuthCrypto.sha256Ascii("request-context-three");
        BoardOAuthStateCipher.EncryptedState encrypted = cipher.encrypt(STATE, aad);
        byte[] tampered = encrypted.ciphertext();
        tampered[tampered.length - 1] ^= 1;

        ServiceException ciphertextError = assertThrows(ServiceException.class,
                () -> cipher.decrypt(encrypted.keyRef(), encrypted.nonce(), tampered, aad));
        assertEquals(500, ciphertextError.getCode());
        assertEquals(AesGcmBoardOAuthStateCipher.DECRYPTION_FAILED,
                ciphertextError.getMessage());

        ServiceException contextError = assertThrows(ServiceException.class,
                () -> cipher.decrypt(
                        encrypted.keyRef(),
                        encrypted.nonce(),
                        encrypted.ciphertext(),
                        BoardOAuthCrypto.sha256Ascii("different-context")));
        assertEquals(AesGcmBoardOAuthStateCipher.DECRYPTION_FAILED,
                contextError.getMessage());
    }

    @Test
    void missingRotatedOrMalformedKeyFailsClosedWithoutEncryption() {
        IndependentBoardOAuthProperties missing = new IndependentBoardOAuthProperties();
        AesGcmBoardOAuthStateCipher missingCipher = cipher(missing);
        byte[] aad = BoardOAuthCrypto.sha256Ascii("request-context-four");

        ServiceException missingError = assertThrows(ServiceException.class,
                () -> missingCipher.encrypt(STATE, aad));
        assertEquals(503, missingError.getCode());
        assertEquals(AesGcmBoardOAuthStateCipher.KEY_NOT_CONFIGURED,
                missingError.getMessage());

        AesGcmBoardOAuthStateCipher configured = cipher(configuredProperties());
        BoardOAuthStateCipher.EncryptedState encrypted = configured.encrypt(STATE, aad);
        ServiceException rotatedError = assertThrows(ServiceException.class,
                () -> configured.decrypt(
                        "oauth-state-old",
                        encrypted.nonce(),
                        encrypted.ciphertext(),
                        aad));
        assertEquals(503, rotatedError.getCode());
        assertEquals(AesGcmBoardOAuthStateCipher.KEY_NOT_CONFIGURED,
                rotatedError.getMessage());
    }

    @Test
    void stateAndAssociatedDataShapesAreStrict() {
        AesGcmBoardOAuthStateCipher cipher = cipher(configuredProperties());
        byte[] aad = BoardOAuthCrypto.sha256Ascii("request-context-five");

        ServiceException shortState = assertThrows(ServiceException.class,
                () -> cipher.encrypt("too-short", aad));
        assertEquals(400, shortState.getCode());
        assertEquals(AesGcmBoardOAuthStateCipher.STATE_INVALID, shortState.getMessage());

        ServiceException nonPrintable = assertThrows(ServiceException.class,
                () -> cipher.encrypt("0123456789abcdef\n", aad));
        assertEquals(AesGcmBoardOAuthStateCipher.STATE_INVALID, nonPrintable.getMessage());

        ServiceException badAad = assertThrows(ServiceException.class,
                () -> cipher.encrypt(STATE, new byte[31]));
        assertEquals(AesGcmBoardOAuthStateCipher.STATE_INVALID, badAad.getMessage());
    }

    private AesGcmBoardOAuthStateCipher cipher(IndependentBoardOAuthProperties properties) {
        return new AesGcmBoardOAuthStateCipher(properties, new SecureRandom());
    }

    private IndependentBoardOAuthProperties configuredProperties() {
        IndependentBoardOAuthProperties properties = new IndependentBoardOAuthProperties();
        properties.setStateKeyRef("oauth-state-k1");
        byte[] key = new byte[32];
        for (int index = 0; index < key.length; index++) {
            key[index] = (byte) (index + 1);
        }
        properties.setStateKeyBase64(Base64.getEncoder().encodeToString(key));
        return properties;
    }
}
