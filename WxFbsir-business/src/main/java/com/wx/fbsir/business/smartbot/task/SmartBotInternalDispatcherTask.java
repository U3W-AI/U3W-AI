package com.wx.fbsir.business.smartbot.task;

import com.wx.fbsir.business.smartbot.service.InternalOutboxDispatcherService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/** Bounded Quartz-callable wakeup adapter; all state remains in MySQL. */
@Component("smartBotInternalDispatcherTask")
public class SmartBotInternalDispatcherTask {

    private final InternalOutboxDispatcherService dispatcherService;
    private final int batchSize;
    private final Duration leaseDuration;
    private final String workerId = "smartbot-dispatcher-"
        + UUID.randomUUID().toString().substring(0, 8);

    public SmartBotInternalDispatcherTask(
            InternalOutboxDispatcherService dispatcherService,
            @Value("${smartbot.internal-dispatcher.batch-size:20}") int batchSize,
            @Value("${smartbot.internal-dispatcher.lease-seconds:30}") long leaseSeconds) {
        if (batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException("internal dispatcher batch size must be between 1 and 100");
        }
        if (leaseSeconds < 5 || leaseSeconds > 300) {
            throw new IllegalArgumentException("internal dispatcher lease must be between 5 and 300 seconds");
        }
        this.dispatcherService = dispatcherService;
        this.batchSize = batchSize;
        this.leaseDuration = Duration.ofSeconds(leaseSeconds);
    }

    /** Quartz invoke target: smartBotInternalDispatcherTask.dispatchAvailable() */
    public int dispatchAvailable() {
        int consumed = 0;
        for (int i = 0; i < batchSize; i++) {
            Optional<InternalOutboxDispatcherService.DispatchOutcome> outcome =
                dispatcherService.dispatchOne(workerId, leaseDuration);
            if (outcome.isEmpty()) {
                break;
            }
            consumed++;
        }
        return consumed;
    }
}
