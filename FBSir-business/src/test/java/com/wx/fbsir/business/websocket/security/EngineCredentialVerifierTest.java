package com.wx.fbsir.business.websocket.security;

import com.wx.fbsir.business.websocket.config.WebSocketProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineCredentialVerifierTest {

    @Test
    void requiresStrongConfiguredCredentialAndMatchesExactly() {
        WebSocketProperties properties = new WebSocketProperties();
        properties.setEngineToken("engine-token-at-least-32-characters-long");
        EngineCredentialVerifier verifier = new EngineCredentialVerifier(properties);

        assertTrue(verifier.matches("engine-token-at-least-32-characters-long"));
        assertFalse(verifier.matches("engine-token-at-least-32-characters-lonh"));
        assertFalse(verifier.matches(null));
    }

    @Test
    void failsClosedWhenCredentialIsMissingOrShort() {
        WebSocketProperties missing = new WebSocketProperties();
        WebSocketProperties shortToken = new WebSocketProperties();
        shortToken.setEngineToken("too-short");

        assertThrows(IllegalStateException.class, () -> new EngineCredentialVerifier(missing));
        assertThrows(IllegalStateException.class, () -> new EngineCredentialVerifier(shortToken));
    }

    @Test
    void websocketOriginsDefaultToSameOriginAndSupportExplicitList() {
        WebSocketProperties properties = new WebSocketProperties();
        assertArrayEquals(new String[0], properties.getAllowedOrigins());

        properties.setAllowedOrigins("https://admin.example.com, https://ops.example.com");
        assertArrayEquals(new String[]{"https://admin.example.com", "https://ops.example.com"},
            properties.getAllowedOrigins());
    }
}
