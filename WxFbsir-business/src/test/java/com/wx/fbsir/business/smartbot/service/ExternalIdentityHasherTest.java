package com.wx.fbsir.business.smartbot.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExternalIdentityHasherTest {

    @Test
    void hashesAreDeterministicAndScopedByBotAndPurpose() {
        ExternalIdentityHasher hasher = new ExternalIdentityHasher(testKey());

        String first = hasher.hashUser(1L, "external-user");
        assertEquals(first, hasher.hashUser(1L, "external-user"));
        assertEquals(64, first.length());
        assertNotEquals(first, hasher.hashUser(2L, "external-user"));
        assertNotEquals(first, hasher.hashChat(1L, "external-user"));
        assertNotEquals(first, hasher.hashMessage(1L, "external-user"));
    }

    @Test
    void missingRuntimeKeyFailsOnlyWhenFeatureIsUsed() {
        ExternalIdentityHasher hasher = new ExternalIdentityHasher("");
        assertThrows(IllegalStateException.class, () -> hasher.hashUser(1L, "user"));
    }

    @Test
    void rejectsWeakOrMalformedRuntimeKeys() {
        assertThrows(IllegalArgumentException.class,
            () -> new ExternalIdentityHasher(Base64.getEncoder().encodeToString("short".getBytes())));
        assertThrows(IllegalArgumentException.class,
            () -> new ExternalIdentityHasher("not-base64"));
    }

    private String testKey() {
        return Base64.getEncoder().encodeToString(
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
    }
}
