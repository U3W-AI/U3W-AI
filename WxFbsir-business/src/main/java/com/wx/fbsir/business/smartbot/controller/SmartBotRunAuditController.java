package com.wx.fbsir.business.smartbot.controller;

import com.wx.fbsir.business.smartbot.service.SmartBotRunAuditService;
import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.utils.SecurityUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/smartbot/runs")
public class SmartBotRunAuditController {
    private final SmartBotRunAuditService auditService;

    public SmartBotRunAuditController(SmartBotRunAuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/{runId}/audit")
    @PreAuthorize("@ss.hasPermi('business:wecom:query')")
    public AjaxResult audit(@PathVariable String runId) {
        return AjaxResult.success(auditService.get(runId, SecurityUtils.getUserId()));
    }
}
