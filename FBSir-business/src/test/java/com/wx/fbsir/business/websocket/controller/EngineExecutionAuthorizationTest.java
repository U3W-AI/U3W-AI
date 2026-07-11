package com.wx.fbsir.business.websocket.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EngineExecutionAuthorizationTest {

    @Test
    void requestRequiresOnlineHostQueryPermission() throws Exception {
        assertPermission(EngineRequestController.class.getMethod("sendRequest", Map.class),
            "@ss.hasPermi('business:host:online:query')");
    }

    @Test
    void engineReadEndpointsRequireOnlineHostQueryPermission() throws Exception {
        String queryPermission = "@ss.hasPermi('business:host:online:query')";
        assertPermission(EngineAdminController.class.getMethod("getStats"), queryPermission);
        assertPermission(EngineAdminController.class.getMethod("getEngines"), queryPermission);
        assertPermission(EngineAdminController.class.getMethod("getEngine", String.class), queryPermission);
        assertPermission(EngineAdminController.class.getMethod("getConfig"), queryPermission);
        assertPermission(EngineRequestController.class.getMethod("listEngines"), queryPermission);
    }

    @Test
    void directTaskAndBroadcastRequireDebugSendPermission() throws Exception {
        assertPermission(EngineAdminController.class.getMethod("sendTask", String.class, Map.class),
            "@ss.hasPermi('business:debug:send')");
        assertPermission(EngineAdminController.class.getMethod("broadcast", Map.class),
            "@ss.hasPermi('business:debug:send')");
    }

    private void assertPermission(Method method, String expected) {
        PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
        assertNotNull(authorization);
        assertEquals(expected, authorization.value());
    }
}
