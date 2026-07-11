package com.wx.fbsir.business.smartbot.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Human gate command. The target is an existing scoped Webhook, never a raw URL. */
public record HumanWebhookApprovalRequest(
    @NotNull Long webhookId,
    @NotNull Integer expectedRunVersion,
    @NotBlank @Pattern(regexp = "[0-9a-fA-F]{64}") String approvedContentHash
) {
}
