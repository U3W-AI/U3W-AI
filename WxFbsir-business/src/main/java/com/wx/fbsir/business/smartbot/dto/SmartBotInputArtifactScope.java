package com.wx.fbsir.business.smartbot.dto;

/** Required caller scope for any future decrypted content read. */
public record SmartBotInputArtifactScope(
    String inputRef,
    String runId,
    Long inboundEventId,
    Long botBindingId,
    Long enterpriseId,
    Long enterpriseMemberId,
    Long userId,
    String msgType,
    String sourcePayloadHash,
    String contentHash
) {
}
