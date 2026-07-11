package com.wx.fbsir.business.smartbot.service;

import com.wx.fbsir.business.airobotmessage.dto.WebhookDeliveryReceipt;
import com.wx.fbsir.business.airobotmessage.dto.WebhookSendRequest;
import com.wx.fbsir.business.airobotmessage.service.MessageService;
import com.wx.fbsir.business.smartbot.domain.DeliveryOutbox;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Date;
import java.util.Optional;

/** Orchestrates one human-approved Webhook command without holding a DB transaction over HTTP. */
@Service
public class WebhookOutboxDispatcherService {
    private static final Duration NETWORK_LEASE = Duration.ofSeconds(60);
    static final String MESSAGE_TITLE = "福帮手智能机器人消息";
    private final OutboxLeaseService leaseService;
    private final WebhookDispatchTransactionService transactionService;
    private final MessageService messageService;

    public WebhookOutboxDispatcherService(OutboxLeaseService leaseService,
                                          WebhookDispatchTransactionService transactionService,
                                          MessageService messageService) {
        this.leaseService = leaseService;
        this.transactionService = transactionService;
        this.messageService = messageService;
    }

    public Optional<WebhookDispatchTransactionService.DispatchResult> dispatchOne(
            String workerId, Duration leaseDuration) {
        Optional<DeliveryOutbox> claimed = leaseService.claimNext(
            HumanWebhookApprovalService.DESTINATION_TYPE, workerId, leaseDuration);
        if (claimed.isEmpty()) {
            return Optional.empty();
        }
        DeliveryOutbox outbox = claimed.orElseThrow();
        try {
            WebhookDispatchTransactionService.WebhookWorkItem item = transactionService.prepare(outbox);
            // Keep the fencing token valid beyond the transport's bounded HTTP timeout.
            leaseService.extendLease(outbox.getId(), outbox.getLeaseToken(), NETWORK_LEASE);
            WebhookDeliveryReceipt delivery = messageService.send(new WebhookSendRequest(
                item.enterpriseId(), item.webhookId(), item.idempotencyKey(),
                MESSAGE_TITLE, item.messageContent(), null), item.actorUserId());
            return Optional.of(transactionService.finalizeDelivery(item, delivery));
        } catch (RuntimeException e) {
            if (outbox.getAttemptCount() != null && outbox.getAttemptCount() >= 5) {
                transactionService.deadLetter(outbox);
            } else {
                leaseService.scheduleRetry(outbox.getId(), outbox.getLeaseToken(),
                    new Date(System.currentTimeMillis() + 30_000L), e.getMessage());
            }
            throw e;
        }
    }

    static String outboundMarkdown(String messageContent) {
        return "**" + MESSAGE_TITLE + "**\n" + messageContent;
    }
}
