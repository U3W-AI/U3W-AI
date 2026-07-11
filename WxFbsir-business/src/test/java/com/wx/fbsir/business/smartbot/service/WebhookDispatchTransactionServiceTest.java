package com.wx.fbsir.business.smartbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.fbsir.business.airobotmessage.dto.WebhookDeliveryReceipt;
import com.wx.fbsir.business.smartbot.domain.DeliveryOutbox;
import com.wx.fbsir.business.smartbot.domain.OrchestrationReceipt;
import com.wx.fbsir.business.smartbot.domain.OrchestrationRun;
import com.wx.fbsir.business.smartbot.domain.OrchestrationStep;
import com.wx.fbsir.business.smartbot.mapper.DeliveryOutboxMapper;
import com.wx.fbsir.business.smartbot.mapper.OrchestrationReceiptMapper;
import com.wx.fbsir.business.smartbot.mapper.OrchestrationRunMapper;
import com.wx.fbsir.business.smartbot.mapper.OrchestrationStepMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookDispatchTransactionServiceTest {
    private DeliveryOutboxMapper outboxMapper;
    private OrchestrationRunMapper runMapper;
    private OrchestrationStepMapper stepMapper;
    private OrchestrationReceiptMapper receiptMapper;
    private WebhookDispatchTransactionService service;

    @BeforeEach
    void setUp() {
        outboxMapper = mock(DeliveryOutboxMapper.class);
        runMapper = mock(OrchestrationRunMapper.class);
        stepMapper = mock(OrchestrationStepMapper.class);
        receiptMapper = mock(OrchestrationReceiptMapper.class);
        service = new WebhookDispatchTransactionService(outboxMapper, runMapper, stepMapper,
            receiptMapper, mock(SmartBotWebhookPayloadRenderer.class), new ObjectMapper());
    }

    @Test
    void acceptedProviderResultBecomesDeliveryReceiptWithoutClaimingBusinessSuccess() {
        String runId = "11111111-1111-1111-1111-111111111111";
        String token = "00000000-0000-0000-0000-000000000009";
        DeliveryOutbox outbox = new DeliveryOutbox();
        outbox.setId(9L);
        outbox.setRunId(runId);
        outbox.setLeaseToken(token);
        OrchestrationRun run = new OrchestrationRun();
        run.setRunId(runId);
        run.setStatus("WEBHOOK_QUEUED");
        run.setVersion(2);
        when(outboxMapper.selectActiveLeaseForUpdateByDestination(9L, token, "WEBHOOK_HUB"))
            .thenReturn(outbox);
        when(runMapper.selectByRunIdForUpdate(runId)).thenReturn(run);
        when(stepMapper.selectByRunStepAttemptForUpdate(runId,
            WebhookDispatchTransactionService.RESULT_STEP, 1)).thenReturn(null);
        when(stepMapper.insertStep(any())).thenReturn(1);
        when(receiptMapper.insertReceipt(any())).thenReturn(1);
        when(runMapper.markWebhookResult(runId, 2, "WEBHOOK_ACCEPTED_AWAIT_READBACK")).thenReturn(1);
        when(outboxMapper.markConsumed(9L, token)).thenReturn(1);
        var item = new WebhookDispatchTransactionService.WebhookWorkItem(
            9L, token, runId, 11L, 7L, 31L, "content", "smartbot:stable:key");
        var delivery = new WebhookDeliveryReceipt(88L,
            "22222222-2222-2222-2222-222222222222", "PROVIDER_ACCEPTED", 200, 0, "ok");

        var result = service.finalizeDelivery(item, delivery);

        assertEquals("WEBHOOK_ACCEPTED_AWAIT_READBACK", result.status());
        ArgumentCaptor<OrchestrationReceipt> receipt = ArgumentCaptor.forClass(OrchestrationReceipt.class);
        verify(receiptMapper).insertReceipt(receipt.capture());
        assertEquals("DELIVERY", receipt.getValue().getReceiptType());
        assertEquals("PROVIDER_ACCEPTED", receipt.getValue().getStatus());
        verify(runMapper).markWebhookResult(runId, 2, "WEBHOOK_ACCEPTED_AWAIT_READBACK");
        verify(outboxMapper).markConsumed(9L, token);
    }

    @Test
    void unknownDeliveryRemainsPendingReconciliationAtEveryLayer() {
        String runId = "11111111-1111-1111-1111-111111111111";
        String token = "00000000-0000-0000-0000-000000000009";
        DeliveryOutbox outbox = new DeliveryOutbox();
        outbox.setId(9L);
        outbox.setRunId(runId);
        outbox.setLeaseToken(token);
        OrchestrationRun run = new OrchestrationRun();
        run.setRunId(runId);
        run.setStatus("WEBHOOK_QUEUED");
        run.setVersion(2);
        when(outboxMapper.selectActiveLeaseForUpdateByDestination(9L, token, "WEBHOOK_HUB"))
            .thenReturn(outbox);
        when(runMapper.selectByRunIdForUpdate(runId)).thenReturn(run);
        when(stepMapper.selectByRunStepAttemptForUpdate(runId,
            WebhookDispatchTransactionService.RESULT_STEP, 1)).thenReturn(null);
        when(stepMapper.insertStep(any())).thenReturn(1);
        when(receiptMapper.insertReceipt(any())).thenReturn(1);
        when(runMapper.markWebhookResult(runId, 2, "MANUAL_RECONCILIATION")).thenReturn(1);
        when(outboxMapper.markConsumed(9L, token)).thenReturn(1);
        var item = new WebhookDispatchTransactionService.WebhookWorkItem(
            9L, token, runId, 11L, 7L, 31L, "content", "smartbot:stable:key");
        var delivery = new WebhookDeliveryReceipt(88L,
            "22222222-2222-2222-2222-222222222222", "UNKNOWN", 503, -1,
            "temporarily unavailable");

        var result = service.finalizeDelivery(item, delivery);

        assertEquals("MANUAL_RECONCILIATION", result.status());
        ArgumentCaptor<OrchestrationStep> step = ArgumentCaptor.forClass(OrchestrationStep.class);
        verify(stepMapper).insertStep(step.capture());
        assertEquals("RECONCILIATION_REQUIRED", step.getValue().getStatus());
        ArgumentCaptor<OrchestrationReceipt> receipt = ArgumentCaptor.forClass(OrchestrationReceipt.class);
        verify(receiptMapper).insertReceipt(receipt.capture());
        assertEquals("UNKNOWN", receipt.getValue().getStatus());
        verify(runMapper).markWebhookResult(runId, 2, "MANUAL_RECONCILIATION");
    }

    @Test
    void exhaustedCommandDeadLettersOutboxAndRunTogether() {
        String runId = "11111111-1111-1111-1111-111111111111";
        String token = "00000000-0000-0000-0000-000000000009";
        DeliveryOutbox outbox = new DeliveryOutbox();
        outbox.setId(9L);
        outbox.setRunId(runId);
        outbox.setLeaseToken(token);
        OrchestrationRun run = new OrchestrationRun();
        run.setRunId(runId);
        run.setStatus("WEBHOOK_QUEUED");
        run.setVersion(2);
        when(runMapper.selectByRunIdForUpdate(runId)).thenReturn(run);
        when(outboxMapper.selectActiveLeaseForUpdateByDestination(9L, token, "WEBHOOK_HUB"))
            .thenReturn(outbox);
        when(runMapper.markWebhookResult(runId, 2, "MANUAL_RECONCILIATION")).thenReturn(1);
        when(outboxMapper.markDead(9L, token, "webhook dispatch retry limit exhausted")).thenReturn(1);

        var result = service.deadLetter(outbox);

        assertEquals("MANUAL_RECONCILIATION", result.status());
        verify(runMapper).markWebhookResult(runId, 2, "MANUAL_RECONCILIATION");
        verify(outboxMapper).markDead(9L, token, "webhook dispatch retry limit exhausted");
    }
}
