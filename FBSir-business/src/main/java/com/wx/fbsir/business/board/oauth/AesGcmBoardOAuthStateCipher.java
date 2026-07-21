package com.wx.fbsir.business.board.oauth;

import com.wx.fbsir.business.board.config.IndependentBoardOAuthProperties;
import com.wx.fbsir.common.exception.ServiceException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM state protection with a deployment-supplied key and an explicit
 * key reference. Only a SHA-256 transaction-context digest is accepted as AAD.
 */
@Component
public class AesGcmBoardOAuthStateCipher implements BoardOAuthStateCipher {
    public static final String KEY_NOT_CONFIGURED = "OAUTH_STATE_KEY_NOT_CONFIGURED";
    public static final String STATE_INVALID = "OAUTH_STATE_INVALID";
    public static final String ENCRYPTION_FAILED = "OAUTH_STATE_ENCRYPTION_FAILED";
    public static final String DECRYPTION_FAILED = "OAUTH_STATE_DECRYPTION_FAILED";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int AES_KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int MIN_STATE_LENGTH = 16;
    private static final int MAX_STATE_LENGTH = 512;
    private static final int MIN_CIPHERTEXT_BYTES = MIN_STATE_LENGTH + 16;
    private static final int MAX_CIPHERTEXT_BYTES = MAX_STATE_LENGTH + 16;
    private static final Pattern KEY_REF = Pattern.compile("[A-Za-z0-9._:-]{1,191}");

    private final IndependentBoardOAuthProperties properties;
    private final SecureRandom secureRandom;

    @Autowired
    public AesGcmBoardOAuthStateCipher(IndependentBoardOAuthProperties properties) {
        this(properties, new SecureRandom());
    }

    AesGcmBoardOAuthStateCipher(
            IndependentBoardOAuthProperties properties,
            SecureRandom secureRandom) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    }

    @Override
    public EncryptedState encrypt(String state, byte[] associatedDataDigest) {
        validateState(state);
        validateAssociatedData(associatedDataDigest);
        KeyMaterial keyMaterial = requireKeyMaterial(null);
        byte[] nonce = new byte[NONCE_BYTES];
        byte[] plaintext = state.getBytes(StandardCharsets.US_ASCII);
        try {
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    keyMaterial.key(),
                    new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(associatedDataDigest);
            byte[] ciphertext = cipher.doFinal(plaintext);
            if (ciphertext.length < MIN_CIPHERTEXT_BYTES
                    || ciphertext.length > MAX_CIPHERTEXT_BYTES) {
                throw new ServiceException(ENCRYPTION_FAILED, 500);
            }
            return new EncryptedState(keyMaterial.keyRef(), nonce, ciphertext);
        } catch (ServiceException known) {
            throw known;
        } catch (GeneralSecurityException | RuntimeException failure) {
            throw new ServiceException(ENCRYPTION_FAILED, 500);
        } finally {
            Arrays.fill(plaintext, (byte) 0);
            keyMaterial.destroy();
        }
    }

    @Override
    public String decrypt(
            String keyRef,
            byte[] nonce,
            byte[] ciphertext,
            byte[] associatedDataDigest) {
        validateAssociatedData(associatedDataDigest);
        if (nonce == null || nonce.length != NONCE_BYTES
                || ciphertext == null
                || ciphertext.length < MIN_CIPHERTEXT_BYTES
                || ciphertext.length > MAX_CIPHERTEXT_BYTES) {
            throw new ServiceException(DECRYPTION_FAILED, 500);
        }
        KeyMaterial keyMaterial = requireKeyMaterial(keyRef);
        byte[] plaintext = null;
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    keyMaterial.key(),
                    new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(associatedDataDigest);
            plaintext = cipher.doFinal(ciphertext);
            String state = new String(plaintext, StandardCharsets.US_ASCII);
            validateState(state);
            return state;
        } catch (AEADBadTagException tampered) {
            throw new ServiceException(DECRYPTION_FAILED, 500);
        } catch (ServiceException known) {
            throw known;
        } catch (GeneralSecurityException | RuntimeException failure) {
            throw new ServiceException(DECRYPTION_FAILED, 500);
        } finally {
            if (plaintext != null) {
                Arrays.fill(plaintext, (byte) 0);
            }
            keyMaterial.destroy();
        }
    }

    private KeyMaterial requireKeyMaterial(String expectedKeyRef) {
        String keyRef = properties.getStateKeyRef();
        String encodedKey = properties.getStateKeyBase64();
        if (keyRef == null || !KEY_REF.matcher(keyRef).matches()
                || encodedKey == null || encodedKey.isBlank()
                || (expectedKeyRef != null && !Objects.equals(expectedKeyRef, keyRef))) {
            throw new ServiceException(KEY_NOT_CONFIGURED, 503);
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException invalidBase64) {
            throw new ServiceException(KEY_NOT_CONFIGURED, 503);
        }
        if (keyBytes.length != AES_KEY_BYTES) {
            Arrays.fill(keyBytes, (byte) 0);
            throw new ServiceException(KEY_NOT_CONFIGURED, 503);
        }
        return new KeyMaterial(keyRef, keyBytes);
    }

    private static void validateAssociatedData(byte[] associatedDataDigest) {
        if (associatedDataDigest == null
                || associatedDataDigest.length != BoardOAuthCrypto.SHA256_BYTES) {
            throw new ServiceException(STATE_INVALID, 400);
        }
    }

    private static void validateState(String state) {
        if (state == null
                || state.length() < MIN_STATE_LENGTH
                || state.length() > MAX_STATE_LENGTH) {
            throw new ServiceException(STATE_INVALID, 400);
        }
        for (int index = 0; index < state.length(); index++) {
            char character = state.charAt(index);
            if (character < 0x20 || character > 0x7e) {
                throw new ServiceException(STATE_INVALID, 400);
            }
        }
    }

    private record KeyMaterial(String keyRef, byte[] encoded) {
        private KeyMaterial {
            encoded = encoded.clone();
        }

        private SecretKeySpec key() {
            return new SecretKeySpec(encoded, "AES");
        }

        private void destroy() {
            Arrays.fill(encoded, (byte) 0);
        }
    }
}
