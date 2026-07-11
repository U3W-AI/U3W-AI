package com.wx.fbsir.business.smartbot.controller;

import com.wx.fbsir.business.smartbot.service.HumanWebhookApprovalService;
import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.utils.SecurityUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/smartbot/runs")
public class SmartBotWebhookPreviewController {
    private final HumanWebhookApprovalService approvalService;

    public SmartBotWebhookPreviewController(HumanWebhookApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @PreAuthorize("@ss.hasPermi('business:wecom:query')")
    @GetMapping("/{runId}/webhook-preview")
    public AjaxResult preview(@PathVariable String runId) {
        return AjaxResult.success(approvalService.preview(runId, SecurityUtils.getUserId()));
    }
}
