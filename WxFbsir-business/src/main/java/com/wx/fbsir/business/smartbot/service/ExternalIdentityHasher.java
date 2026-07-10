package com.wx.fbsir.business.smartbot.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Bot-scoped HMAC for external user/chat identifiers; raw values are never persisted. */
@Component
public class ExternalIdentityHasher {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final byte[] hmacKey;

    public ExternalIdentityHasher(@Value("${smartbot.identity-hmac-key-base64:}") String encodedKey) {
        this.hmacKey = decodeKey(encodedKey);
    }

    public String hashUser(Long bindingId, String externalUserId) {
        return hash(bindingId, "user", externalUserId);
    }

    public String hashChat(Long bindingId, String chatId) {
        return hash(bindingId, "chat", chatId);
    }

    public String hashMessage(Long bindingId, String msgId) {
        return hash(bindingId, "message", msgId);
    }

    private String hash(Long bindingId, String purpose, String value) {
        if (bindingId == null || !StringUtils.hasText(value)) {
            throw new IllegalArgumentException("外部身份哈希参数不能为空");
        }
        if (hmacKey.length == 0) {
            throw new IllegalStateException("SMARTBOT_IDENTITY_HMAC_KEY_BASE64 未配置");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(hmacKey, HMAC_ALGORITHM));
            byte[] digest = mac.doFinal((bindingId + "\0" + purpose + "\0" + value)
                .getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b & 0xff));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("外部身份哈希失败", e);
        }
    }

    private byte[] decodeKey(String encodedKey) {
        if (!StringUtils.hasText(encodedKey)) {
            return new byte[0];
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(encodedKey);
            if (decoded.length < 32) {
                throw new IllegalArgumentException("智能机器人身份 HMAC 密钥至少需要 32 字节");
            }
            return decoded;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("SMARTBOT_IDENTITY_HMAC_KEY_BASE64 必须是至少 32 字节密钥的 Base64", e);
        }
    }
}
