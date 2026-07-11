package com.wx.fbsir.business.websocket.server;

import com.wx.fbsir.business.websocket.config.WebSocketProperties;
import com.wx.fbsir.business.websocket.security.EngineCredentialVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class EngineWebSocketInterceptorTest {

    private static final String TOKEN = "engine-token-at-least-32-characters-long";
    private EngineWebSocketInterceptor interceptor;

    @BeforeEach
    void setUp() {
        WebSocketProperties properties = new WebSocketProperties();
        properties.setEngineToken(TOKEN);
        interceptor = new EngineWebSocketInterceptor(new EngineCredentialVerifier(properties));
    }

    @Test
    void acceptsHeaderCredentialAndIgnoresSpoofedForwardingHeaders() throws Exception {
        MockHttpServletRequest servletRequest = request(TOKEN);
        servletRequest.setRemoteAddr("10.0.0.8");
        servletRequest.addHeader("X-Forwarded-For", "203.0.113.99");
        servletRequest.addHeader("X-Real-IP", "203.0.113.100");
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(
            new ServletServerHttpRequest(servletRequest),
            new ServletServerHttpResponse(new MockHttpServletResponse()),
            mock(WebSocketHandler.class), attributes);

        assertTrue(accepted);
        assertEquals("10.0.0.8", attributes.get("remoteAddress"));
        assertEquals(Boolean.TRUE, attributes.get("engineCredentialVerified"));
    }

    @Test
    void rejectsMissingOrWrongCredential() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        ServletServerHttpResponse serverResponse = new ServletServerHttpResponse(response);

        boolean accepted = interceptor.beforeHandshake(
            new ServletServerHttpRequest(request("wrong-token")),
            serverResponse,
            mock(WebSocketHandler.class), new HashMap<>());

        assertFalse(accepted);
        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatus());
        assertEquals("ENGINE_CREDENTIAL", serverResponse.getHeaders().getFirst("X-WS-Reject-Reason"));
    }

    private MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/engine");
        if (token != null) {
            request.addHeader(EngineCredentialVerifier.HEADER_NAME, token);
        }
        return request;
    }
}
