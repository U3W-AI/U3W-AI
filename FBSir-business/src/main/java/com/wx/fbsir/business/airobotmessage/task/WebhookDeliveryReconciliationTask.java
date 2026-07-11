package com.wx.fbsir.business.airobotmessage.task;

import com.wx.fbsir.business.airobotmessage.mapper.WebhookDeliveryMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Conservatively closes abandoned PENDING rows without attempting an unsafe automatic resend. */
@Component
@ConditionalOnProperty(name = "webhook.delivery.sweep-enabled", havingValue = "true", matchIfMissing = true)
public class WebhookDeliveryReconciliationTask {
    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryReconciliationTask.class);
    private final WebhookDeliveryMapper deliveryMapper;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "webhook-delivery-reconciler");
        thread.setDaemon(true);
        return thread;
    });

    @Value("${webhook.delivery.sweep-ms:60000}")
    private long sweepMs;

    public WebhookDeliveryReconciliationTask(WebhookDeliveryMapper deliveryMapper) {
        this.deliveryMapper = deliveryMapper;
    }

    @PostConstruct
    public void start() {
        long delay = Math.max(10_000L, sweepMs);
        scheduler.scheduleWithFixedDelay(this::markAbandonedPendingUnknownSafely,
                delay, delay, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void stop() {
        scheduler.shutdownNow();
    }

    public void markAbandonedPendingUnknown() {
        int updated = deliveryMapper.markAllStalePendingUnknown();
        if (updated > 0) {
            log.warn("[Webhook投递对账] 已将 {} 条超时PENDING记录标记为UNKNOWN，需要人工核对", updated);
        }
    }

    private void markAbandonedPendingUnknownSafely() {
        try {
            markAbandonedPendingUnknown();
        } catch (Exception e) {
            log.error("[Webhook投递对账] 清扫失败，错误类型: {}", e.getClass().getSimpleName());
        }
    }
}
