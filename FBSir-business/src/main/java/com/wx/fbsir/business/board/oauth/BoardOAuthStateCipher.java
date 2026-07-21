package com.wx.fbsir.business.board.oauth;

/**
 * Repository-owned AES-GCM boundary for the short-lived OAuth state value.
 * Implementations must never log or persist the raw state or raw key.
 */
public interface BoardOAuthStateCipher {
    EncryptedState encrypt(String state, byte[] associatedDataDigest);

    String decrypt(
            String keyRef,
            byte[] nonce,
            byte[] ciphertext,
            byte[] associatedDataDigest);

    record EncryptedState(String keyRef, byte[] nonce, byte[] ciphertext) {
        public EncryptedState {
            nonce = nonce == null ? null : nonce.clone();
            ciphertext = ciphertext == null ? null : ciphertext.clone();
        }

        @Override
        public byte[] nonce() {
            return nonce == null ? null : nonce.clone();
        }

        @Override
        public byte[] ciphertext() {
            return ciphertext == null ? null : ciphertext.clone();
        }
    }
}
