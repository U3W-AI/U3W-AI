package com.wx.fbsir.business.websocket.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("ClientMessageRouter 请求来源缓存测试")
class ClientMessageRouterTest {

    @AfterEach
    void tearDown() {
        ClientMessageRouter.clearRequestSourceCache();
    }

    @Test
    @DisplayName("未过期的请求来源可以被查回")
    void shouldReturnSourceBeforeExpiry() {
        long now = 1_000_000L;
        ClientMessageRouter.registerRequestSource("req-1", "WEBSOCKET", now);

        assertEquals("WEBSOCKET", ClientMessageRouter.getRequestSource("req-1", now + 1));
    }

    @Test
    @DisplayName("过期的请求来源会被清理")
    void shouldExpireOldRequestSources() {
        long now = 1_000_000L;
        ClientMessageRouter.registerRequestSource("req-2", "HTTP", now);

        assertNull(ClientMessageRouter.getRequestSource("req-2", now + (10 * 60 * 1000L) + 1));
        assertEquals(0, ClientMessageRouter.getRequestSourceCacheSize());
    }
}
