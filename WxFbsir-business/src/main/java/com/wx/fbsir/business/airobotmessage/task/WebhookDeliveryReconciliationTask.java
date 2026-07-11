package com.wx.fbsir.business.airobotmessage.task;

import com.wx.fbsir.business.airobotmessage.mapper.WebhookDeliveryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Conservatively closes abandoned PENDING rows without attempting an unsafe automatic resend. */
@Component
public class WebhookDeliveryReconciliationTask {
    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryReconciliationTask.class);
    private final WebhookDeliveryMapper deliveryMapper;

    public WebhookDeliveryReconciliationTask(WebhookDeliveryMapper deliveryMapper) {
        this.deliveryMapper = deliveryMapper;
    }

    @Scheduled(fixedDelayString = "${webhook.delivery.sweep-ms:60000}")
    public void markAbandonedPendingUnknown() {
        int updated = deliveryMapper.markAllStalePendingUnknown();
        if (updated > 0) {
            log.warn("[Webhook投递对账] 已将 {} 条超时PENDING记录标记为UNKNOWN，需要人工核对", updated);
        }
    }
}
