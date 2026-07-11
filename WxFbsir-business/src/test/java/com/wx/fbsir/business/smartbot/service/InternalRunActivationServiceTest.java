package com.wx.fbsir.business.smartbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.fbsir.business.smartbot.domain.DeliveryOutbox;
import com.wx.fbsir.business.smartbot.domain.OrchestrationRun;
import com.wx.fbsir.business.smartbot.domain.OrchestrationStep;
import com.wx.fbsir.business.smartbot.mapper.DeliveryOutboxMapper;
import com.wx.fbsir.business.smartbot.mapper.OrchestrationRunMapper;
import com.wx.fbsir.business.smartbot.mapper.OrchestrationStepMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternalRunActivationServiceTest {

    private DeliveryOutboxMapper outboxMapper;
    private OrchestrationRunMapper runMapper;
    private OrchestrationStepMapper stepMapper;
    private InternalRunActivationService service;

    @BeforeEach
    void setUp() {
        outboxMapper = mock(DeliveryOutboxMapper.class);
        runMapper = mock(OrchestrationRunMapper.class);
        stepMapper = mock(OrchestrationStepMapper.class);
        service = new InternalRunActivationService(outboxMapper, runMapper, stepMapper,
            new ObjectMapper());
    }

    @Test
    void pendingRunBecomesReadyWithOneInternalAuditStep() {
        DeliveryOutbox outbox = outbox();
        OrchestrationRun run = run("PENDING");
        when(outboxMapper.selectActiveLeaseForUpdate(7L, outbox.getLeaseToken())).thenReturn(outbox);
        when(runMapper.selectByRunIdForUpdate(outbox.getRunId())).thenReturn(run);
        when(stepMapper.selectByRunStepAttemptForUpdate(
            outbox.getRunId(), InternalRunActivationService.ACTIVATION_STEP, 1)).thenReturn(null);
        when(stepMapper.insertStep(org.mockito.ArgumentMatchers.any())).thenReturn(1);
        when(runMapper.markReady(outbox.getRunId(), 0)).thenReturn(1);
        when(outboxMapper.markConsumed(7L, outbox.getLeaseToken())).thenReturn(1);

        InternalOutboxDispatcherService.DispatchOutcome outcome = service.apply(outbox);

        assertEquals("READY", outcome.status());
        ArgumentCaptor<OrchestrationStep> step = ArgumentCaptor.forClass(OrchestrationStep.class);
        verify(stepMapper).insertStep(step.capture());
        assertEquals("internal.dispatch.accepted", step.getValue().getStepKey());
        assertEquals("SYSTEM", step.getValue().getKind());
        assertEquals("SUCCEEDED", step.getValue().getStatus());
        verify(outboxMapper).markConsumed(7L, outbox.getLeaseToken());
    }

    @Test
    void readyRunIsOnlyAcceptedWhenItsAuditStepMatches() {
        DeliveryOutbox outbox = outbox();
        OrchestrationRun run = run("READY");
        OrchestrationStep step = new OrchestrationStep();
        step.setRunId(run.getRunId());
        step.setStepKey(InternalRunActivationService.ACTIVATION_STEP);
        step.setAttempt(1);
        step.setKind("SYSTEM");
        step.setExecutorType("JAVA");
        step.setExecutorRef("smartbot.internal-dispatcher");
        step.setStatus("SUCCEEDED");
        when(outboxMapper.selectActiveLeaseForUpdate(7L, outbox.getLeaseToken())).thenReturn(outbox);
        when(runMapper.selectByRunIdForUpdate(outbox.getRunId())).thenReturn(run);
        when(stepMapper.selectByRunStepAttemptForUpdate(run.getRunId(),
            InternalRunActivationService.ACTIVATION_STEP, 1)).thenReturn(step);
        when(outboxMapper.markConsumed(7L, outbox.getLeaseToken())).thenReturn(1);

        assertEquals("ALREADY_READY", service.apply(outbox).status());
        verify(stepMapper, never()).insertStep(org.mockito.ArgumentMatchers.any());
        verify(runMapper, never()).markReady(org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
    }

    @Test
    void leaseLossAndPayloadMismatchFailClosed() {
        DeliveryOutbox outbox = outbox();
        when(outboxMapper.selectActiveLeaseForUpdate(7L, outbox.getLeaseToken())).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> service.apply(outbox));
        verify(runMapper, never()).selectByRunIdForUpdate(org.mockito.ArgumentMatchers.any());

        DeliveryOutbox mismatch = outbox();
        mismatch.setPayloadJson("{\"runId\":\"different\"}");
        when(outboxMapper.selectActiveLeaseForUpdate(7L, mismatch.getLeaseToken())).thenReturn(mismatch);
        assertThrows(IllegalStateException.class, () -> service.apply(mismatch));
        verify(runMapper, never()).selectByRunIdForUpdate(mismatch.getRunId());
    }

    private DeliveryOutbox outbox() {
        DeliveryOutbox outbox = new DeliveryOutbox();
        outbox.setId(7L);
        outbox.setRunId("11111111-1111-1111-1111-111111111111");
        outbox.setEventKey("run:" + outbox.getRunId() + ":created");
        outbox.setEventType("RUN_CREATED");
        outbox.setDestinationType("INTERNAL_DISPATCHER");
        outbox.setPayloadJson("{\"runId\":\"" + outbox.getRunId() + "\"}");
        outbox.setStatus("LEASED");
        outbox.setLeaseToken("00000000-0000-0000-0000-000000000001");
        return outbox;
    }

    private OrchestrationRun run(String status) {
        OrchestrationRun run = new OrchestrationRun();
        run.setRunId("11111111-1111-1111-1111-111111111111");
        run.setStatus(status);
        run.setVersion(0);
        return run;
    }
}
