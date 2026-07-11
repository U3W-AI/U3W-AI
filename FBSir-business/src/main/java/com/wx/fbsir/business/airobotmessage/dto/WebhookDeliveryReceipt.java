package com.wx.fbsir.business.airobotmessage.dto;

/** Durable receipt for one idempotent Webhook delivery attempt. */
public record WebhookDeliveryReceipt(
        Long deliveryId,
        String traceId,
        String status,
        Integer providerHttpStatus,
        Integer providerErrcode,
        String providerErrmsg) {
}
