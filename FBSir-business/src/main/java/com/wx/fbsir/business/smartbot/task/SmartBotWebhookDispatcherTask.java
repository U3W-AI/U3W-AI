package com.wx.fbsir.business.smartbot.task;

import com.wx.fbsir.business.smartbot.service.WebhookOutboxDispatcherService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/** Bounded Quartz-callable worker for the WEBHOOK_HUB destination. */
@Component("smartBotWebhookDispatcherTask")
public class SmartBotWebhookDispatcherTask {
    private final WebhookOutboxDispatcherService dispatcherService;
    private final int batchSize;
    private final Duration leaseDuration;
    private final String workerId = "smartbot-webhook-" + UUID.randomUUID().toString().substring(0, 8);

    public SmartBotWebhookDispatcherTask(WebhookOutboxDispatcherService dispatcherService,
                                         @Value("${smartbot.webhook-dispatcher.batch-size:10}") int batchSize,
                                         @Value("${smartbot.webhook-dispatcher.lease-seconds:30}") long leaseSeconds) {
        if (batchSize < 1 || batchSize > 100 || leaseSeconds < 30 || leaseSeconds > 300) {
            throw new IllegalArgumentException("Webhook dispatcher configuration is invalid");
        }
        this.dispatcherService = dispatcherService;
        this.batchSize = batchSize;
        this.leaseDuration = Duration.ofSeconds(leaseSeconds);
    }

    /** Quartz invoke target: smartBotWebhookDispatcherTask.dispatchAvailable() */
    public int dispatchAvailable() {
        int consumed = 0;
        for (int i = 0; i < batchSize; i++) {
            if (dispatcherService.dispatchOne(workerId, leaseDuration).isEmpty()) {
                break;
            }
            consumed++;
        }
        return consumed;
    }
}
