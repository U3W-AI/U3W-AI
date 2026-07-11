package com.wx.fbsir.business.smartbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.fbsir.business.airobotmessage.service.WebhookScopeGuard;
import com.wx.fbsir.business.airobotmessage.service.MessageService;
import com.wx.fbsir.business.airobotmessage.dto.WebhookMetadataResponse;
import com.wx.fbsir.business.smartbot.domain.DeliveryOutbox;
import com.wx.fbsir.business.smartbot.domain.OrchestrationReceipt;
import com.wx.fbsir.business.smartbot.domain.OrchestrationRun;
import com.wx.fbsir.business.smartbot.domain.OrchestrationStep;
import com.wx.fbsir.business.smartbot.domain.SmartBotInputArtifact;
import com.wx.fbsir.business.smartbot.dto.HumanWebhookApprovalResult;
import com.wx.fbsir.business.smartbot.mapper.DeliveryOutboxMapper;
import com.wx.fbsir.business.smartbot.mapper.OrchestrationReceiptMapper;
import com.wx.fbsir.business.smartbot.mapper.OrchestrationRunMapper;
import com.wx.fbsir.business.smartbot.mapper.OrchestrationStepMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HumanWebhookApprovalServiceTest {
    private OrchestrationRunMapper runMapper;
    private OrchestrationStepMapper stepMapper;
    private OrchestrationReceiptMapper receiptMapper;
    private DeliveryOutboxMapper outboxMapper;
    private SmartBotInputArtifactService artifactService;
    private WebhookScopeGuard scopeGuard;
    private MessageService messageService;
    private SmartBotWebhookPayloadRenderer renderer;
    private HumanWebhookApprovalService service;

    @BeforeEach
    void setUp() {
        runMapper = mock(OrchestrationRunMapper.class);
        stepMapper = mock(OrchestrationStepMapper.class);
        receiptMapper = mock(OrchestrationReceiptMapper.class);
        outboxMapper = mock(DeliveryOutboxMapper.class);
        artifactService = mock(SmartBotInputArtifactService.class);
        scopeGuard = mock(WebhookScopeGuard.class);
        messageService = mock(MessageService.class);
        renderer = mock(SmartBotWebhookPayloadRenderer.class);
        service = new HumanWebhookApprovalService(runMapper, stepMapper, receiptMapper,
            outboxMapper, artifactService, scopeGuard, messageService, renderer, new ObjectMapper());
        when(messageService.get(any(), any(), any())).thenReturn(new WebhookMetadataResponse(
            7L, 11L, "test", "masked", null, true, 1, null, null));
        when(renderer.render(any(), any(), any())).thenReturn("approved message");
    }

    @Test
    void approvalAtomicallyCreatesHumanActionAndWebhookCommand() {
        OrchestrationRun run = run("READY", 1);
        OrchestrationStep activation = activation();
        SmartBotInputArtifact artifact = artifact();
        when(runMapper.selectByRunIdForUpdate(run.getRunId())).thenReturn(run);
        when(stepMapper.selectByRunStepAttemptForUpdate(run.getRunId(),
            HumanWebhookApprovalService.APPROVAL_STEP, 1)).thenReturn(null);
        when(outboxMapper.selectByEventKeyForUpdate(HumanWebhookApprovalService.eventKey(run.getRunId())))
            .thenReturn(null);
        when(stepMapper.selectByRunStepAttemptForUpdate(run.getRunId(),
            InternalRunActivationService.ACTIVATION_STEP, 1)).thenReturn(activation);
        when(artifactService.requireAvailableForActivation(run, activation.getInputRef(), activation.getInputHash()))
            .thenReturn(artifact);
        when(stepMapper.insertStep(any())).thenReturn(1);
        when(receiptMapper.insertReceipt(any())).thenReturn(1);
        when(outboxMapper.insertOutbox(any())).thenAnswer(invocation -> {
            ((DeliveryOutbox) invocation.getArgument(0)).setId(99L);
            return 1;
        });
        when(runMapper.markWebhookQueued(run.getRunId(), 1)).thenReturn(1);

        HumanWebhookApprovalResult result = service.approve(
            run.getRunId(), 7L, 1, 31L, approvedHash());

        assertEquals("WEBHOOK_QUEUED", result.status());
        assertEquals(99L, result.outboxId());
        ArgumentCaptor<OrchestrationStep> gate = ArgumentCaptor.forClass(OrchestrationStep.class);
        verify(stepMapper).insertStep(gate.capture());
        assertEquals("HUMAN_GATE", gate.getValue().getKind());
        assertEquals("HUMAN", gate.getValue().getExecutorType());
        assertEquals("user:31", gate.getValue().getEvidenceRef());
        ArgumentCaptor<OrchestrationReceipt> action = ArgumentCaptor.forClass(OrchestrationReceipt.class);
        verify(receiptMapper).insertReceipt(action.capture());
        assertEquals("ACTION", action.getValue().getReceiptType());
        assertEquals("APPROVED", action.getValue().getStatus());
        ArgumentCaptor<DeliveryOutbox> outbox = ArgumentCaptor.forClass(DeliveryOutbox.class);
        verify(outboxMapper).insertOutbox(outbox.capture());
        assertEquals("WEBHOOK_HUB", outbox.getValue().getDestinationType());
        assertFalse(outbox.getValue().getPayloadJson().contains("messageContent"));
        assertFalse(outbox.getValue().getPayloadJson().contains("webhookUrl"));
    }

    @Test
    void staleVersionFailsBeforeAnyGateOrOutboundCommand() {
        OrchestrationRun run = run("READY", 2);
        when(runMapper.selectByRunIdForUpdate(run.getRunId())).thenReturn(run);

        assertThrows(IllegalStateException.class,
            () -> service.approve(run.getRunId(), 7L, 1, 31L, approvedHash()));

        verify(stepMapper, never()).insertStep(any());
        verify(receiptMapper, never()).insertReceipt(any());
        verify(outboxMapper, never()).insertOutbox(any());
        verify(runMapper, never()).markWebhookQueued(any(), any());
    }

    @Test
    void disabledWebhookFailsClosedBeforeAnyGateOrOutboundCommand() {
        OrchestrationRun run = run("READY", 1);
        when(runMapper.selectByRunIdForUpdate(run.getRunId())).thenReturn(run);
        when(messageService.get(7L, 11L, 31L)).thenReturn(new WebhookMetadataResponse(
            7L, 11L, "disabled", "masked", null, false, 1, null, null));

        assertThrows(IllegalStateException.class,
            () -> service.approve(run.getRunId(), 7L, 1, 31L, approvedHash()));

        verify(stepMapper, never()).insertStep(any());
        verify(receiptMapper, never()).insertReceipt(any());
        verify(outboxMapper, never()).insertOutbox(any());
        verify(runMapper, never()).markWebhookQueued(any(), any());
    }

    @Test
    void changedPreviewFingerprintFailsBeforeAnyGateOrOutboundCommand() {
        OrchestrationRun run = run("READY", 1);
        OrchestrationStep activation = activation();
        when(runMapper.selectByRunIdForUpdate(run.getRunId())).thenReturn(run);
        when(stepMapper.selectByRunStepAttemptForUpdate(run.getRunId(),
            HumanWebhookApprovalService.APPROVAL_STEP, 1)).thenReturn(null);
        when(outboxMapper.selectByEventKeyForUpdate(HumanWebhookApprovalService.eventKey(run.getRunId())))
            .thenReturn(null);
        when(stepMapper.selectByRunStepAttemptForUpdate(run.getRunId(),
            InternalRunActivationService.ACTIVATION_STEP, 1)).thenReturn(activation);
        when(artifactService.requireAvailableForActivation(run, activation.getInputRef(), activation.getInputHash()))
            .thenReturn(artifact());

        assertThrows(IllegalStateException.class,
            () -> service.approve(run.getRunId(), 7L, 1, 31L, "0".repeat(64)));

        verify(stepMapper, never()).insertStep(any());
        verify(receiptMapper, never()).insertReceipt(any());
        verify(outboxMapper, never()).insertOutbox(any());
        verify(runMapper, never()).markWebhookQueued(any(), any());
    }

    private OrchestrationRun run(String status, int version) {
        OrchestrationRun run = new OrchestrationRun();
        run.setRunId("11111111-1111-1111-1111-111111111111");
        run.setInboundEventId(101L);
        run.setBotBindingId(7L);
        run.setEnterpriseId(11L);
        run.setEnterpriseMemberId(21L);
        run.setUserId(31L);
        run.setStatus(status);
        run.setVersion(version);
        return run;
    }

    private OrchestrationStep activation() {
        OrchestrationStep step = new OrchestrationStep();
        step.setStatus("SUCCEEDED");
        step.setInputRef(artifact().getInputRef());
        step.setInputHash(artifact().getContentHash());
        return step;
    }

    private SmartBotInputArtifact artifact() {
        SmartBotInputArtifact artifact = new SmartBotInputArtifact();
        artifact.setInputRef("vault:v1:00000000-0000-0000-0000-000000000001");
        artifact.setContentHash("a".repeat(64));
        return artifact;
    }

    private String approvedHash() {
        return sha256(WebhookOutboxDispatcherService.outboundMarkdown("approved message"));
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
