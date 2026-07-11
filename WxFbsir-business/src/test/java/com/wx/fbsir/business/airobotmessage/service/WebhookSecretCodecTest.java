package com.wx.fbsir.business.airobotmessage.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookSecretCodecTest {
    private WebhookSecretCodec codec;

    @BeforeEach
    void setUp() {
        codec = new WebhookSecretCodec("v1",
                Map.of("v1", "unit-test-webhook-secret-key-2026-strong"), new SecureRandom());
    }

    @Test
    void encryptsAndResolvesOfficialWecomWebhook() {
        String url = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=abcdefghijklmnop";
        String reference = codec.encode(url);
        assertTrue(reference.startsWith("enc:"));
        assertTrue(reference.startsWith("enc:v1:"));
        assertEquals(url, codec.resolve(reference));
    }

    @Test
    void rejectsSsrfAndLookalikeHosts() {
        assertThrows(IllegalArgumentException.class,
                () -> codec.validate("http://127.0.0.1/internal?key=abcdefghijklmnop"));
        assertThrows(IllegalArgumentException.class,
                () -> codec.validate("https://qyapi.weixin.qq.com.attacker.example/cgi-bin/webhook/send?key=abcdefghijklmnop"));
        assertThrows(IllegalArgumentException.class,
                () -> codec.validate("https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=abcdefghijklmnop&next=http://127.0.0.1"));
    }

    @Test
    void applicationConstructorFailsClosedWhenCurrentKeyIsMissingOrWeak() {
        assertThrows(IllegalStateException.class, () -> new WebhookSecretCodec("v1", "", ""));
        assertThrows(IllegalStateException.class, () -> new WebhookSecretCodec("v1", "too-short", ""));
    }

    @Test
    void rotationReadsV1AndWritesV2WhileBothKeysArePresent() {
        String url = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=abcdefghijklmnop";
        String v1Key = "unit-test-webhook-secret-key-v1-2026-strong";
        String v2Key = "unit-test-webhook-secret-key-v2-2026-strong";
        WebhookSecretCodec v1Codec = new WebhookSecretCodec("v1", Map.of("v1", v1Key), new SecureRandom());
        String oldReference = v1Codec.encode(url);
        WebhookSecretCodec rotatingCodec = new WebhookSecretCodec("v2",
                Map.of("v1", v1Key, "v2", v2Key), new SecureRandom());

        assertEquals(url, rotatingCodec.resolve(oldReference));
        assertTrue(rotatingCodec.encode(url).startsWith("enc:v2:"));
    }

    @Test
    void missingOldKeyAndTamperedCiphertextFailClosed() {
        String url = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=abcdefghijklmnop";
        String reference = codec.encode(url);
        WebhookSecretCodec v2Only = new WebhookSecretCodec("v2",
                Map.of("v2", "unit-test-webhook-secret-key-v2-2026-strong"), new SecureRandom());

        assertThrows(IllegalStateException.class, () -> v2Only.resolve(reference));
        String tampered = reference.substring(0, reference.length() - 1)
                + (reference.endsWith("A") ? "B" : "A");
        assertThrows(IllegalStateException.class, () -> codec.resolve(tampered));
    }
}
