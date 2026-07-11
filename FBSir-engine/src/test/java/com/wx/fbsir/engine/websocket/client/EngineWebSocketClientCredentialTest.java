package com.wx.fbsir.engine.websocket.client;

import com.wx.fbsir.engine.config.EngineProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EngineWebSocketClientCredentialTest {

    @Test
    void sendsCredentialInHandshakeHeaderInsteadOfUrl() {
        EngineProperties properties = new EngineProperties();
        properties.setEngineToken("engine-token-at-least-32-characters-long");

        assertEquals("engine-token-at-least-32-characters-long",
            EngineWebSocketClient.buildHandshakeHeaders(properties).get("X-FBSir-Engine-Token"));
    }
}
