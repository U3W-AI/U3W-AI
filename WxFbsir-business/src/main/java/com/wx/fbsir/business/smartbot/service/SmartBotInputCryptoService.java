package com.wx.fbsir.business.smartbot.service;

import com.wx.fbsir.business.smartbot.domain.SmartBotInputArtifact;
import com.wx.fbsir.business.smartbot.dto.SmartBotInputArtifactScope;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/** AES-256-GCM encryption for a scoped SmartBot content artifact. */
@Service
public class SmartBotInputCryptoService {

    public static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    public static final String KEY_REF = "env:SMARTBOT_INPUT_ENCRYPTION_KEY_V1";
    public static final int KEY_VERSION = 1;
    private static final int GCM_TAG_BITS = 128;
    private static final int NONCE_BYTES = 12;

    private final SecretReferenceResolver secretResolver;
    private final SecureRandom secureRandom;

    public SmartBotInputCryptoService(SecretReferenceResolver secretResolver) {
        this(secretResolver, new SecureRandom());
    }

    SmartBotInputCryptoService(SecretReferenceResolver secretResolver, SecureRandom secureRandom) {
        this.secretResolver = secretResolver;
        this.secureRandom = secureRandom;
    }

    public void encryptInto(SmartBotInputArtifact artifact, byte[] plaintext) {
        if (artifact == null || plaintext == null || plaintext.length == 0) {
            throw new IllegalArgumentException("输入内容不能为空");
        }
        artifact.setCipherAlgorithm(CIPHER_ALGORITHM);
        artifact.setKeyRef(KEY_REF);
        artifact.setKeyVersion(KEY_VERSION);
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        byte[] aad = encodeAad(artifact);
        byte[] keyBytes = null;
        try {
            keyBytes = decodeKey(secretResolver.resolve(KEY_REF));
            SecretKey key = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(aad);
            artifact.setNonce(nonce);
            artifact.setCiphertext(cipher.doFinal(plaintext));
            artifact.setAadHash(sha256(aad));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("输入内容加密失败", e);
        } finally {
            Arrays.fill(aad, (byte) 0);
            if (keyBytes != null) {
                Arrays.fill(keyBytes, (byte) 0);
            }
        }
    }

    /**
     * Decryption has no ref-only entry point: every caller must provide the
     * expected run, tenant, member, bot, and hashes before plaintext is released.
     */
    public byte[] decrypt(SmartBotInputArtifact artifact, SmartBotInputArtifactScope expectedScope) {
        validateScope(artifact, expectedScope);
        if (!"AVAILABLE".equals(artifact.getStatus()) || artifact.getExpiresAt() == null
                || !artifact.getExpiresAt().toInstant().isAfter(Instant.now())) {
            throw new IllegalStateException("输入内容不可用或已过期");
        }
        byte[] aad = encodeAad(artifact);
        byte[] keyBytes = null;
        try {
            if (!MessageDigest.isEqual(hexToBytes(artifact.getAadHash()), sha256Bytes(aad))) {
                throw new SecurityException("输入内容认证信息不匹配");
            }
            keyBytes = decodeKey(secretResolver.resolve(KEY_REF));
            SecretKey key = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key,
                new GCMParameterSpec(GCM_TAG_BITS, artifact.getNonce()));
            cipher.updateAAD(aad);
            return cipher.doFinal(artifact.getCiphertext());
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new SecurityException("输入内容认证失败", e);
        } finally {
            Arrays.fill(aad, (byte) 0);
            if (keyBytes != null) {
                Arrays.fill(keyBytes, (byte) 0);
            }
        }
    }

    private void validateScope(SmartBotInputArtifact artifact, SmartBotInputArtifactScope scope) {
        if (artifact == null || scope == null
                || !CIPHER_ALGORITHM.equals(artifact.getCipherAlgorithm())
                || !KEY_REF.equals(artifact.getKeyRef()) || !Objects.equals(KEY_VERSION, artifact.getKeyVersion())
                || artifact.getNonce() == null || artifact.getNonce().length != NONCE_BYTES
                || artifact.getCiphertext() == null || artifact.getCiphertext().length <= GCM_TAG_BITS / 8
                || !StringUtils.hasText(artifact.getAadHash())) {
            throw new SecurityException("输入内容元数据无效");
        }
        if (!Objects.equals(artifact.getInputRef(), scope.inputRef())
                || !Objects.equals(artifact.getRunId(), scope.runId())
                || !Objects.equals(artifact.getInboundEventId(), scope.inboundEventId())
                || !Objects.equals(artifact.getBotBindingId(), scope.botBindingId())
                || !Objects.equals(artifact.getEnterpriseId(), scope.enterpriseId())
                || !Objects.equals(artifact.getEnterpriseMemberId(), scope.enterpriseMemberId())
                || !Objects.equals(artifact.getUserId(), scope.userId())
                || !Objects.equals(artifact.getMsgType(), scope.msgType())
                || !sameHash(artifact.getSourcePayloadHash(), scope.sourcePayloadHash())
                || !sameHash(artifact.getContentHash(), scope.contentHash())) {
            throw new SecurityException("输入内容作用域不匹配");
        }
    }

    private byte[] encodeAad(SmartBotInputArtifact artifact) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(bytes)) {
            writeString(out, artifact.getPurpose());
            writeString(out, artifact.getInputRef());
            out.writeLong(requireLong(artifact.getInboundEventId(), "inboundEventId"));
            writeString(out, artifact.getRunId());
            out.writeLong(requireLong(artifact.getBotBindingId(), "botBindingId"));
            out.writeLong(requireLong(artifact.getEnterpriseId(), "enterpriseId"));
            out.writeLong(requireLong(artifact.getEnterpriseMemberId(), "enterpriseMemberId"));
            out.writeLong(requireLong(artifact.getUserId(), "userId"));
            writeString(out, artifact.getMsgType());
            writeString(out, artifact.getSourcePayloadHash());
            writeString(out, artifact.getContentHash());
            out.writeInt(requireInt(artifact.getKeyVersion(), "keyVersion"));
            out.flush();
            return bytes.toByteArray();
        } catch (Exception e) {
            throw new IllegalArgumentException("输入内容 AAD 不完整", e);
        }
    }

    private void writeString(DataOutputStream out, String value) throws Exception {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("AAD 字段不能为空");
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private long requireLong(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(field + " 无效");
        }
        return value;
    }

    private int requireInt(Integer value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(field + " 无效");
        }
        return value;
    }

    private byte[] decodeKey(String encoded) {
        byte[] decoded = Base64.getDecoder().decode(encoded);
        if (decoded.length != 32) {
            Arrays.fill(decoded, (byte) 0);
            throw new IllegalArgumentException("输入加密密钥长度无效");
        }
        return decoded;
    }

    private String sha256(byte[] value) {
        byte[] digest = sha256Bytes(value);
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            hex.append(String.format("%02x", b & 0xff));
        }
        return hex.toString();
    }

    private byte[] sha256Bytes(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private byte[] hexToBytes(String hex) {
        if (hex == null || !hex.matches("[a-fA-F0-9]{64}")) {
            throw new SecurityException("输入内容认证信息无效");
        }
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            bytes[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return bytes;
    }

    private boolean sameHash(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }
}
