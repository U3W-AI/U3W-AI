package com.wx.fbsir.engine.controller.monitor;

import com.wx.fbsir.engine.capability.TaskExecutionTracker;
import com.wx.fbsir.engine.playwright.core.PlaywrightInstancePool;
import com.wx.fbsir.engine.playwright.pool.BrowserPoolManager;
import com.wx.fbsir.engine.websocket.client.WebSocketClientManager;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResourceMonitorControllerTest {

    @Test
    void reportsDownWhenPlaywrightCapacityIsZero() {
        TaskExecutionTracker tracker = mock(TaskExecutionTracker.class);
        PlaywrightInstancePool instancePool = mock(PlaywrightInstancePool.class);
        BrowserPoolManager browserPool = mock(BrowserPoolManager.class);
        WebSocketClientManager websocket = mock(WebSocketClientManager.class);
        when(instancePool.getPoolSize()).thenReturn(0);
        when(browserPool.getAvailableSlots()).thenReturn(1);
        when(websocket.isConnected()).thenReturn(true);

        Map<String, Object> health = new ResourceMonitorController(
            tracker, instancePool, browserPool, websocket).getHealth();

        assertEquals("DOWN", health.get("status"));
    }

    @Test
    void reportsDegradedWhenReceiptChannelIsDisconnected() {
        TaskExecutionTracker tracker = mock(TaskExecutionTracker.class);
        PlaywrightInstancePool instancePool = mock(PlaywrightInstancePool.class);
        BrowserPoolManager browserPool = mock(BrowserPoolManager.class);
        WebSocketClientManager websocket = mock(WebSocketClientManager.class);
        when(instancePool.getPoolSize()).thenReturn(2);
        when(browserPool.getAvailableSlots()).thenReturn(1);
        when(websocket.isConnected()).thenReturn(false);

        Map<String, Object> health = new ResourceMonitorController(
            tracker, instancePool, browserPool, websocket).getHealth();

        assertEquals("DEGRADED", health.get("status"));
        assertTrue(health.get("readinessReasons").toString().contains("WebSocket"));
    }
}
