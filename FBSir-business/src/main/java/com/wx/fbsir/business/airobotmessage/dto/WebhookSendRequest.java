package com.wx.fbsir.business.airobotmessage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Authenticated, idempotent message request. */
public record WebhookSendRequest(
        @NotNull Long enterpriseId,
        @NotNull Long webhookId,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._:-]{8,128}") String idempotencyKey,
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 4096) String messageContent,
        @Size(max = 1024) String actionUrl) {
}
