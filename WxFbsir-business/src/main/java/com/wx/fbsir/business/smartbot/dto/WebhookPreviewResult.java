package com.wx.fbsir.business.smartbot.dto;

/** Exact final Markdown and fingerprint that the human must approve. */
public record WebhookPreviewResult(
    String runId,
    Integer runVersion,
    String messageContent,
    String contentHash,
    Integer utf8Bytes
) {
}
