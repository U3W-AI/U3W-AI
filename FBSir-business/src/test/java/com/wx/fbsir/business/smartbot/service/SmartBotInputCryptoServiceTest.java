package com.wx.fbsir.business.smartbot.service;

import com.wx.fbsir.business.smartbot.domain.SmartBotInputArtifact;
import com.wx.fbsir.business.smartbot.dto.SmartBotInputArtifactScope;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SmartBotInputCryptoServiceTest {

    private static final String DATA_KEY_BASE64 =
        "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Test
    void aesGcmRoundTripBindsTheArtifactToItsTenantRunAndHashes() {
        SmartBotInputCryptoService crypto = new SmartBotInputCryptoService(ref -> DATA_KEY_BASE64);
        SmartBotInputArtifact artifact = artifact();
        byte[] plaintext = "含中文与\u0000的受控内容".getBytes(StandardCharsets.UTF_8);

        crypto.encryptInto(artifact, plaintext);

        assertFalse(Arrays.equals(plaintext, artifact.getCiphertext()));
        assertFalse(artifact.toString().contains("受控内容"));
        byte[] decrypted = crypto.decrypt(artifact, scope(artifact));
        try {
            assertArrayEquals(plaintext, decrypted);
        } finally {
            Arrays.fill(decrypted, (byte) 0);
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    @Test
    void aadAndCiphertextTamperingFailClosed() {
        SmartBotInputCryptoService crypto = new SmartBotInputCryptoService(ref -> DATA_KEY_BASE64);
        SmartBotInputArtifact aadTampered = artifact();
        crypto.encryptInto(aadTampered, "tamper-test".getBytes(StandardCharsets.UTF_8));

        aadTampered.setEnterpriseId(12L);
        assertThrows(SecurityException.class, () -> crypto.decrypt(aadTampered, scope(aadTampered)));

        SmartBotInputArtifact ciphertextTampered = artifact();
        crypto.encryptInto(ciphertextTampered, "tamper-test".getBytes(StandardCharsets.UTF_8));
        byte[] ciphertext = ciphertextTampered.getCiphertext().clone();
        ciphertext[0] ^= 0x01;
        ciphertextTampered.setCiphertext(ciphertext);
        assertThrows(SecurityException.class,
            () -> crypto.decrypt(ciphertextTampered, scope(ciphertextTampered)));
    }

    private SmartBotInputArtifact artifact() {
        SmartBotInputArtifact artifact = new SmartBotInputArtifact();
        artifact.setInputRef("vault:v1:00000000-0000-0000-0000-000000000001");
        artifact.setPurpose(SmartBotInputArtifactService.PURPOSE);
        artifact.setInboundEventId(101L);
        artifact.setRunId("11111111-1111-1111-1111-111111111111");
        artifact.setBotBindingId(7L);
        artifact.setEnterpriseId(11L);
        artifact.setEnterpriseMemberId(21L);
        artifact.setUserId(31L);
        artifact.setMsgType("text");
        artifact.setSourcePayloadHash("a".repeat(64));
        artifact.setContentHash("b".repeat(64));
        artifact.setStatus("AVAILABLE");
        artifact.setExpiresAt(new Date(System.currentTimeMillis() + 60_000));
        return artifact;
    }

    private SmartBotInputArtifactScope scope(SmartBotInputArtifact artifact) {
        return new SmartBotInputArtifactScope(artifact.getInputRef(), artifact.getRunId(),
            artifact.getInboundEventId(), artifact.getBotBindingId(), artifact.getEnterpriseId(),
            artifact.getEnterpriseMemberId(), artifact.getUserId(), artifact.getMsgType(),
            artifact.getSourcePayloadHash(), artifact.getContentHash());
    }
}
