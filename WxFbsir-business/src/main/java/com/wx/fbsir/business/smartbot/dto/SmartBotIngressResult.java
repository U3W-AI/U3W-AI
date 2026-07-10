package com.wx.fbsir.business.smartbot.dto;

/** Stable ingress result returned for both the winning and duplicate deliveries. */
public record SmartBotIngressResult(
        boolean firstDelivery,
        Long inboundEventId,
        String traceId,
        String runId,
        String streamId,
        Long enterpriseId,
        Long enterpriseMemberId,
        Long userId) {
}
