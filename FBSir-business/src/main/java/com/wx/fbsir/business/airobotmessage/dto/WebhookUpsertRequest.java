package com.wx.fbsir.business.airobotmessage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request used to create or update an enterprise-scoped WeCom Webhook. */
public record WebhookUpsertRequest(
        Long id,
        @NotNull Long enterpriseId,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 512) String webhookUrl,
        @Size(max = 255) String description,
        Boolean status,
        Integer version) {
}
