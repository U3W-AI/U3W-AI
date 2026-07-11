package com.wx.fbsir.business.smartbot.controller;

import com.wx.fbsir.business.smartbot.dto.HumanWebhookApprovalRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SmartBotRunControllerAuthorizationTest {

    @Test
    void approvalRequiresExistingWebhookSendPermission() throws Exception {
        Method method = HumanWebhookApprovalController.class.getMethod(
            "approve", String.class, HumanWebhookApprovalRequest.class);

        PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);

        assertNotNull(authorization);
        assertEquals("@ss.hasPermi('business:wecom:send')", authorization.value());
    }

    @Test
    void auditRequiresExistingWebhookQueryPermission() throws Exception {
        Method method = SmartBotRunAuditController.class.getMethod("audit", String.class);

        PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);

        assertNotNull(authorization);
        assertEquals("@ss.hasPermi('business:wecom:query')", authorization.value());
    }
}
