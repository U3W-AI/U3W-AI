package com.wx.fbsir.business.smartbot.controller;

import com.wx.fbsir.business.smartbot.dto.HumanWebhookApprovalRequest;
import com.wx.fbsir.business.smartbot.service.HumanWebhookApprovalService;
import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.utils.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/smartbot/runs")
public class HumanWebhookApprovalController {
    private final HumanWebhookApprovalService approvalService;

    public HumanWebhookApprovalController(HumanWebhookApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @PostMapping("/{runId}/webhook-approval")
    @PreAuthorize("@ss.hasPermi('business:wecom:send')")
    public AjaxResult approve(@PathVariable String runId,
                              @Valid @RequestBody HumanWebhookApprovalRequest request) {
        return AjaxResult.success(approvalService.approve(
            runId, request.webhookId(), request.expectedRunVersion(), SecurityUtils.getUserId(),
            request.approvedContentHash()));
    }
}
