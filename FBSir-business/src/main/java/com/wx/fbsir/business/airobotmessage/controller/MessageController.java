package com.wx.fbsir.business.airobotmessage.controller;

import com.wx.fbsir.business.airobotmessage.dto.WebhookSendRequest;
import com.wx.fbsir.business.airobotmessage.dto.WebhookUpsertRequest;
import com.wx.fbsir.business.airobotmessage.service.MessageService;
import com.wx.fbsir.common.annotation.Log;
import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.enums.BusinessType;
import com.wx.fbsir.common.utils.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/business/message")
public class MessageController {
    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping("/list")
    @PreAuthorize("@ss.hasAnyPermi('business:wecom:list,business:output:pushWebhook')")
    public AjaxResult list(@RequestParam Long enterpriseId) {
        return AjaxResult.success(messageService.list(enterpriseId, SecurityUtils.getUserId()));
    }

    @GetMapping("/enterprises")
    @PreAuthorize("@ss.hasAnyPermi('business:wecom:list,business:output:pushWebhook')")
    public AjaxResult enterprises() {
        return AjaxResult.success(messageService.enterprises(SecurityUtils.getUserId()));
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermi('business:wecom:query')")
    public AjaxResult get(@RequestParam Long id, @RequestParam Long enterpriseId) {
        return AjaxResult.success(messageService.get(id, enterpriseId, SecurityUtils.getUserId()));
    }

    @PostMapping("/insert")
    @PreAuthorize("@ss.hasPermi('business:wecom:add')")
    @Log(title = "企业微信Webhook", businessType = BusinessType.INSERT, isSaveRequestData = false)
    public AjaxResult insert(@Valid @RequestBody WebhookUpsertRequest request) {
        return AjaxResult.success(messageService.create(request, SecurityUtils.getUserId()));
    }

    @PostMapping("/update")
    @PreAuthorize("@ss.hasPermi('business:wecom:edit')")
    @Log(title = "企业微信Webhook", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    public AjaxResult update(@Valid @RequestBody WebhookUpsertRequest request) {
        return AjaxResult.success(messageService.update(request, SecurityUtils.getUserId()));
    }

    @PostMapping("/delete")
    @PreAuthorize("@ss.hasPermi('business:wecom:remove')")
    @Log(title = "企业微信Webhook", businessType = BusinessType.DELETE)
    public AjaxResult delete(@RequestParam Long id,
                             @RequestParam Long enterpriseId,
                             @RequestParam Integer version) {
        messageService.delete(id, enterpriseId, version, SecurityUtils.getUserId());
        return AjaxResult.success();
    }

    @PostMapping("/send")
    @PreAuthorize("@ss.hasPermi('business:wecom:send')")
    @Log(title = "企业微信Webhook投递", businessType = BusinessType.OTHER, isSaveRequestData = false)
    public AjaxResult send(@Valid @RequestBody WebhookSendRequest request) {
        return AjaxResult.success(messageService.send(request, SecurityUtils.getUserId()));
    }
}
