package com.wx.fbsir.business.websocket.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EngineAdminControllerAuthorizationTest {

    @Test
    void disconnectUsesThePermissionDeclaredByTheMenuAndUi() throws Exception {
        Method method = EngineAdminController.class.getMethod("disconnectEngine", String.class);
        PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);

        assertNotNull(authorization);
        assertEquals("@ss.hasPermi('business:host:online:offline')", authorization.value());
    }
}
