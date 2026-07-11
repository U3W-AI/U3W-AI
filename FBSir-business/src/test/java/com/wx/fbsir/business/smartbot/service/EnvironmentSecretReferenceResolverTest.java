package com.wx.fbsir.business.smartbot.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnvironmentSecretReferenceResolverTest {

    @Test
    void resolvesOnlyExplicitEnvironmentReferences() {
        EnvironmentSecretReferenceResolver resolver = new EnvironmentSecretReferenceResolver(
            name -> Map.of("WECOM_BOT_TOKEN", "secret-value").get(name));

        assertEquals("secret-value", resolver.resolve("env:WECOM_BOT_TOKEN"));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("file:C:/secret"));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("env:bad-name"));
        assertThrows(IllegalStateException.class, () -> resolver.resolve("env:MISSING_SECRET"));
    }
}
