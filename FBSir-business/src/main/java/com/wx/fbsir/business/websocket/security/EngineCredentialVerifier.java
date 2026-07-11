package com.wx.fbsir.business.websocket.security;

import com.wx.fbsir.business.websocket.config.WebSocketProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Verifies the shared Engine credential without data-dependent comparison. */
@Component
public class EngineCredentialVerifier {

    public static final String HEADER_NAME = "X-FBSir-Engine-Token";
    public static final String ENGINE_ID_HEADER_NAME = "X-FBSir-Engine-Id";

    private final byte[] expectedToken;

    public EngineCredentialVerifier(WebSocketProperties properties) {
        String configured = properties.getEngineToken();
        if (configured == null || configured.length() < 32) {
            throw new IllegalStateException(
                "FBSIR_ENGINE_TOKEN must be configured with at least 32 characters "
                    + "(legacy WXFBSIR_ENGINE_TOKEN is also supported)");
        }
        this.expectedToken = configured.getBytes(StandardCharsets.UTF_8);
    }

    public boolean matches(String suppliedToken) {
        byte[] supplied = suppliedToken == null
            ? new byte[0]
            : suppliedToken.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedToken, supplied);
    }
}
