package com.wx.fbsir.business.smartbot.service;

import com.wx.fbsir.business.airobotmessage.dto.WebhookDeliveryReceipt;
import com.wx.fbsir.business.airobotmessage.dto.WebhookSendRequest;
import com.wx.fbsir.business.airobotmessage.service.MessageService;
import com.wx.fbsir.business.smartbot.domain.DeliveryOutbox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookOutboxDispatcherServiceTest {
    private OutboxLeaseService leaseService;
    private WebhookDispatchTransactionService transactionService;
    private MessageService messageService;
    private WebhookOutboxDispatcherService service;

    @BeforeEach
    void setUp() {
        leaseService = mock(OutboxLeaseService.class);
        transactionService = mock(WebhookDispatchTransactionService.class);
        messageService = mock(MessageService.class);
        service = new WebhookOutboxDispatcherService(leaseService, transactionService, messageService);
    }

    @Test
    void providerAcceptedIsFinalizedOnceWithStableIdempotencyKey() {
        DeliveryOutbox outbox = new DeliveryOutbox();
        outbox.setId(9L);
        outbox.setRunId("11111111-1111-1111-1111-111111111111");
        outbox.setLeaseToken("00000000-0000-0000-0000-000000000009");
        var item = new WebhookDispatchTransactionService.WebhookWorkItem(
            9L, outbox.getLeaseToken(), "11111111-1111-1111-1111-111111111111",
            11L, 7L, 31L, "approved message", "smartbot:stable:key");
        var receipt = new WebhookDeliveryReceipt(88L,
            "22222222-2222-2222-2222-222222222222", "PROVIDER_ACCEPTED", 200, 0, "ok");
        var result = new WebhookDispatchTransactionService.DispatchResult(
            item.runId(), "WEBHOOK_ACCEPTED_AWAIT_READBACK", 88L);
        when(leaseService.claimNext(eq("WEBHOOK_HUB"), eq("worker-1"), any(Duration.class)))
            .thenReturn(Optional.of(outbox));
        when(transactionService.prepare(outbox)).thenReturn(item);
        when(messageService.send(any(), eq(31L))).thenReturn(receipt);
        when(transactionService.finalizeDelivery(item, receipt)).thenReturn(result);

        assertEquals("WEBHOOK_ACCEPTED_AWAIT_READBACK",
            service.dispatchOne("worker-1", Duration.ofSeconds(30)).orElseThrow().status());
        ArgumentCaptor<WebhookSendRequest> request = ArgumentCaptor.forClass(WebhookSendRequest.class);
        verify(messageService).send(request.capture(), eq(31L));
        assertEquals("smartbot:stable:key", request.getValue().idempotencyKey());
        assertEquals("approved message", request.getValue().messageContent());
        assertEquals("福帮手智能机器人消息", request.getValue().title());
        verify(transactionService).finalizeDelivery(item, receipt);
        var order = inOrder(transactionService, leaseService, messageService);
        order.verify(transactionService).prepare(outbox);
        order.verify(leaseService).extendLease(9L, outbox.getLeaseToken(), Duration.ofSeconds(60));
        order.verify(messageService).send(any(), eq(31L));
    }

    @Test
    void fifthFailedAttemptMovesCommandToDeadLetter() {
        DeliveryOutbox outbox = new DeliveryOutbox();
        outbox.setId(9L);
        outbox.setRunId("11111111-1111-1111-1111-111111111111");
        outbox.setAttemptCount(5);
        outbox.setLeaseToken("00000000-0000-0000-0000-000000000009");
        RuntimeException failure = new IllegalStateException("provider unavailable");
        when(leaseService.claimNext(eq("WEBHOOK_HUB"), eq("worker-1"), any(Duration.class)))
            .thenReturn(Optional.of(outbox));
        when(transactionService.prepare(outbox)).thenThrow(failure);

        assertThrows(IllegalStateException.class,
            () -> service.dispatchOne("worker-1", Duration.ofSeconds(30)));

        verify(transactionService).deadLetter(outbox);
        verify(leaseService, never()).markDead(any(), any(), any());
        verify(leaseService, never()).scheduleRetry(eq(9L), eq(outbox.getLeaseToken()), any(), any());
    }
}
