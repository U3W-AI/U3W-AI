package com.wx.fbsir.business.smartbot.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Minimal, schema-projected untrusted user content. It never represents the
 * raw WeCom callback envelope and is cleared by the callback adapter after
 * the ingress transaction completes.
 */
public final class SmartBotContentArtifactPayload {

    private final String kind;
    private final byte[] contentBytes;
    private final String contentHash;

    private SmartBotContentArtifactPayload(String kind, byte[] contentBytes) {
        if (kind == null || kind.isBlank()) {
            throw new IllegalArgumentException("内容工件类型不能为空");
        }
        if (contentBytes == null || contentBytes.length == 0) {
            throw new IllegalArgumentException("内容工件不能为空");
        }
        this.kind = kind;
        this.contentBytes = Arrays.copyOf(contentBytes, contentBytes.length);
        this.contentHash = sha256(this.contentBytes);
    }

    public static SmartBotContentArtifactPayload ofUtf8Json(String kind, String json) {
        if (json == null) {
            throw new IllegalArgumentException("内容工件不能为空");
        }
        return new SmartBotContentArtifactPayload(kind, json.getBytes(StandardCharsets.UTF_8));
    }

    public String getKind() {
        return kind;
    }

    public String getContentHash() {
        return contentHash;
    }

    /** Returns a short-lived defensive copy for encryption only. */
    public byte[] copyContentBytes() {
        return Arrays.copyOf(contentBytes, contentBytes.length);
    }

    public int getContentSize() {
        return contentBytes.length;
    }

    /** Best-effort in-memory minimisation; this object must never be logged. */
    public void clear() {
        Arrays.fill(contentBytes, (byte) 0);
    }

    @Override
    public String toString() {
        return "SmartBotContentArtifactPayload[kind=" + kind + ", contentSize=" + contentBytes.length + "]";
    }

    private static String sha256(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
