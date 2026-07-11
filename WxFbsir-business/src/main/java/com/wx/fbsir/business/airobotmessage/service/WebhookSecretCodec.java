package com.wx.fbsir.business.airobotmessage.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** Versioned AES-GCM storage for WeCom Webhook URLs with strict destination validation. */
@Component
public class WebhookSecretCodec {
    public static final String MASKED_URL = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=****";
    public static final String STORED_SENTINEL = "__SECRET_REF__";
    private static final int IV_LENGTH = 12;

    private final String currentVersion;
    private final Map<String, String> keys;
    private final SecureRandom secureRandom;

    public WebhookSecretCodec(
            @Value("${webhook.crypto.current-version:v1}") String currentVersion,
            @Value("${webhook.crypto.keys.v1:}") String v1,
            @Value("${webhook.crypto.keys.v2:}") String v2) {
        this(currentVersion, versionMap(v1, v2), new SecureRandom());
        requireKey(currentVersion);
    }

    WebhookSecretCodec(String currentVersion, Map<String, String> keys, SecureRandom secureRandom) {
        this.currentVersion = currentVersion;
        this.keys = Map.copyOf(keys);
        this.secureRandom = secureRandom;
    }

    public String encode(String webhookUrl) {
        validate(webhookUrl);
        String key = requireKey(currentVersion);
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec(key), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(webhookUrl.trim().getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted);
            return "enc:" + currentVersion + ":" + Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("Webhook密钥加密失败", e);
        }
    }

    public String resolve(String secretReference) {
        if (!StringUtils.hasText(secretReference) || !secretReference.startsWith("enc:")) {
            throw new IllegalStateException("Webhook密钥引用不可用，必须轮换旧密钥");
        }
        String[] parts = secretReference.split(":", 3);
        if (parts.length != 3 || !parts[1].matches("v[1-9][0-9]{0,2}")) {
            throw new IllegalStateException("Webhook密钥版本无效");
        }
        try {
            byte[] combined = Base64.getDecoder().decode(parts[2]);
            if (combined.length <= IV_LENGTH + 16) throw new IllegalArgumentException("ciphertext too short");
            ByteBuffer buffer = ByteBuffer.wrap(combined);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec(requireKey(parts[1])), new GCMParameterSpec(128, iv));
            String value = new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
            validate(value);
            return value;
        } catch (IllegalStateException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Webhook密钥引用损坏或格式无效", e);
        } catch (Exception e) {
            throw new IllegalStateException("Webhook密钥解密失败", e);
        }
    }

    public void validate(String webhookUrl) {
        if (!StringUtils.hasText(webhookUrl) || webhookUrl.length() > 512) {
            throw new IllegalArgumentException("Webhook地址无效");
        }
        URI uri;
        try {
            uri = URI.create(webhookUrl.trim());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Webhook地址格式无效");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !"qyapi.weixin.qq.com".equalsIgnoreCase(uri.getHost())
                || uri.getPort() != -1
                || !"/cgi-bin/webhook/send".equals(uri.getPath())
                || uri.getFragment() != null
                || !hasSingleNonEmptyKey(uri.getRawQuery())) {
            throw new IllegalArgumentException("仅支持企业微信官方消息推送Webhook");
        }
    }

    private String requireKey(String version) {
        String key = keys.get(version);
        if (!StringUtils.hasText(key) || key.length() < 32) {
            throw new IllegalStateException("Webhook加密密钥未配置或强度不足: " + version);
        }
        return key;
    }

    private SecretKeySpec keySpec(String key) throws Exception {
        return new SecretKeySpec(MessageDigest.getInstance("SHA-256")
                .digest(key.getBytes(StandardCharsets.UTF_8)), "AES");
    }

    private boolean hasSingleNonEmptyKey(String rawQuery) {
        if (!StringUtils.hasText(rawQuery)) return false;
        String[] parts = rawQuery.split("&");
        if (parts.length != 1) return false;
        String[] pair = parts[0].split("=", 2);
        if (pair.length != 2 || !"key".equals(pair[0])) return false;
        String key = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
        return key.matches("[A-Za-z0-9_-]{16,128}");
    }

    private static Map<String, String> versionMap(String v1, String v2) {
        Map<String, String> result = new LinkedHashMap<>();
        if (StringUtils.hasText(v1)) result.put("v1", v1);
        if (StringUtils.hasText(v2)) result.put("v2", v2);
        return result;
    }
}
