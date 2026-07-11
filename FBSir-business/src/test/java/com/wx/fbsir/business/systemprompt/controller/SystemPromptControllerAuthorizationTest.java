package com.wx.fbsir.business.systemprompt.controller;

import com.wx.fbsir.business.systemprompt.domain.SystemPrompt;
import com.wx.fbsir.common.annotation.Anonymous;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SystemPromptControllerAuthorizationTest {

    @Test
    void everyPromptEndpointRequiresItsSqlDeclaredPermission() throws Exception {
        assertPermission("listSystemPrompt", "business:prompt:list");
        assertPermission("getPrompt", "business:prompt:query", Long.class);
        assertPermission("getSystemPrompt", "business:prompt:query", Long.class);
        assertPermission("insertSystemPrompt", "business:prompt:add", SystemPrompt.class);
        assertPermission("updateSystemPrompt", "business:prompt:edit", SystemPrompt.class);
        assertPermission("deleteSystemPrompt", "business:prompt:remove", Long.class);
    }

    private void assertPermission(String methodName, String permission, Class<?>... parameterTypes)
        throws Exception {
        Method method = SystemPromptController.class.getMethod(methodName, parameterTypes);
        PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);

        assertNotNull(authorization, methodName + " must be protected");
        assertEquals("@ss.hasPermi('" + permission + "')", authorization.value());
        assertNull(method.getAnnotation(Anonymous.class), methodName + " must not allow anonymous access");
    }
}
