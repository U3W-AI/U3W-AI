package com.wx.fbsir.business.websocket.server;

import com.wx.fbsir.business.websocket.config.WebSocketProperties;
import com.wx.fbsir.business.websocket.service.ConnectionLogService;
import com.wx.fbsir.business.websocket.service.ConnectionRateLimiter;
import com.wx.fbsir.business.websocket.service.WhitelistService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.WebSocketSession;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EngineWebSocketHandlerRemoteIpTest {

    @Test
    void registrationUsesTcpPeerInsteadOfForwardingHeaders() throws Exception {
        EngineWebSocketHandler handler = new EngineWebSocketHandler(
            mock(EngineSessionManager.class), mock(WebSocketProperties.class),
            mock(EngineMessageRouter.class), mock(WhitelistService.class),
            mock(ConnectionLogService.class), mock(ConnectionRateLimiter.class));
        WebSocketSession session = mock(WebSocketSession.class);
        HttpHeaders spoofed = new HttpHeaders();
        spoofed.add("X-Forwarded-For", "203.0.113.99");
        spoofed.add("X-Real-IP", "203.0.113.100");
        when(session.getHandshakeHeaders()).thenReturn(spoofed);
        when(session.getRemoteAddress()).thenReturn(new InetSocketAddress("10.0.0.8", 43210));

        Method method = EngineWebSocketHandler.class.getDeclaredMethod("getRemoteIp", WebSocketSession.class);
        method.setAccessible(true);

        assertEquals("10.0.0.8", method.invoke(handler, session));
    }
}
