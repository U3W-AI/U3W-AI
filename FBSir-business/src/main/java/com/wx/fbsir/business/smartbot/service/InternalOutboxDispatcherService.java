package com.wx.fbsir.business.smartbot.service;

import com.wx.fbsir.business.smartbot.domain.DeliveryOutbox;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/** Claims one internal event, then applies it in a separate database transaction. */
@Service
public class InternalOutboxDispatcherService {

    private final OutboxLeaseService outboxLeaseService;
    private final InternalRunActivationService activationService;

    public InternalOutboxDispatcherService(OutboxLeaseService outboxLeaseService,
                                           InternalRunActivationService activationService) {
        this.outboxLeaseService = outboxLeaseService;
        this.activationService = activationService;
    }

    public Optional<DispatchOutcome> dispatchOne(String workerId, Duration leaseDuration) {
        Optional<DeliveryOutbox> claimed = outboxLeaseService.claimNext(workerId, leaseDuration);
        if (claimed.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(activationService.apply(claimed.orElseThrow()));
    }

    public record DispatchOutcome(String runId, String status) {
    }
}
