package com.wx.fbsir.business.airobotmessage.dto;

import java.time.LocalDateTime;

/** Safe metadata returned to the UI. Secret references and Webhook keys never leave the service. */
public record WebhookMetadataResponse(
        Long id,
        Long enterpriseId,
        String name,
        String webhookUrl,
        String description,
        Boolean status,
        Integer version,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}
