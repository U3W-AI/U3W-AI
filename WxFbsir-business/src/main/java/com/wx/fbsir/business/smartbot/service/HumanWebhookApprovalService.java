package com.wx.fbsir.business.smartbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.fbsir.business.airobotmessage.service.WebhookScopeGuard;
import com.wx.fbsir.business.airobotmessage.service.MessageService;
import com.wx.fbsir.business.airobotmessage.dto.WebhookMetadataResponse;
import com.wx.fbsir.business.smartbot.domain.DeliveryOutbox;
import com.wx.fbsir.business.smartbot.domain.OrchestrationRun;
import com.wx.fbsir.business.smartbot.domain.OrchestrationReceipt;
import com.wx.fbsir.business.smartbot.domain.OrchestrationStep;
import com.wx.fbsir.business.smartbot.domain.SmartBotInputArtifact;
import com.wx.fbsir.business.smartbot.dto.HumanWebhookApprovalResult;
import com.wx.fbsir.business.smartbot.dto.WebhookPreviewResult;
import com.wx.fbsir.business.smartbot.mapper.DeliveryOutboxMapper;
import com.wx.fbsir.business.smartbot.mapper.OrchestrationRunMapper;
import com.wx.fbsir.business.smartbot.mapper.OrchestrationReceiptMapper;
import com.wx.fbsir.business.smartbot.mapper.OrchestrationStepMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Durable human gate: approval and the outbound command are committed atomically. */
@Service
public class HumanWebhookApprovalService {

    static final String APPROVAL_STEP = "human.webhook.approved";
    static final String EVENT_TYPE = "WEBHOOK_SEND_REQUESTED";
    static final String DESTINATION_TYPE = "WEBHOOK_HUB";

    private final OrchestrationRunMapper runMapper;
    private final OrchestrationStepMapper stepMapper;
    private final OrchestrationReceiptMapper receiptMapper;
    private final DeliveryOutboxMapper outboxMapper;
    private final SmartBotInputArtifactService inputArtifactService;
    private final WebhookScopeGuard scopeGuard;
    private final MessageService messageService;
    private final SmartBotWebhookPayloadRenderer renderer;
    private final ObjectMapper objectMapper;

    public HumanWebhookApprovalService(OrchestrationRunMapper runMapper,
                                       OrchestrationStepMapper stepMapper,
                                       OrchestrationReceiptMapper receiptMapper,
                                       DeliveryOutboxMapper outboxMapper,
                                       SmartBotInputArtifactService inputArtifactService,
                                       WebhookScopeGuard scopeGuard,
                                       MessageService messageService,
                                       SmartBotWebhookPayloadRenderer renderer,
                                       ObjectMapper objectMapper) {
        this.runMapper = runMapper;
        this.stepMapper = stepMapper;
        this.receiptMapper = receiptMapper;
        this.outboxMapper = outboxMapper;
        this.inputArtifactService = inputArtifactService;
        this.scopeGuard = scopeGuard;
        this.messageService = messageService;
        this.renderer = renderer;
        this.objectMapper = objectMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public WebhookPreviewResult preview(String runId, Long actorUserId) {
        if (runId == null || !runId.matches("[0-9a-fA-F-]{36}") || actorUserId == null) {
            throw new IllegalArgumentException("preview command is invalid");
        }
        OrchestrationRun run = runMapper.selectByRunIdForUpdate(runId);
        if (run == null || !"READY".equals(run.getStatus())) {
            throw new IllegalStateException("run is not ready for Webhook preview");
        }
        scopeGuard.requireActiveMember(run.getEnterpriseId(), actorUserId);
        OrchestrationStep activation = requireActivation(run);
        SmartBotInputArtifact artifact = inputArtifactService.requireAvailableForActivation(
            run, activation.getInputRef(), activation.getInputHash());
        String finalMessage = WebhookOutboxDispatcherService.outboundMarkdown(
            renderer.render(run, artifact.getInputRef(), artifact.getContentHash()));
        return new WebhookPreviewResult(runId, run.getVersion(), finalMessage,
            sha256(finalMessage), finalMessage.getBytes(StandardCharsets.UTF_8).length);
    }

    @Transactional(rollbackFor = Exception.class)
    public HumanWebhookApprovalResult approve(String runId, Long webhookId,
                                               Integer expectedRunVersion, Long actorUserId,
                                               String approvedContentHash) {
        if (runId == null || !runId.matches("[0-9a-fA-F-]{36}") || webhookId == null || webhookId <= 0
                || expectedRunVersion == null || expectedRunVersion < 0 || actorUserId == null
                || approvedContentHash == null || !approvedContentHash.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("approval command is invalid");
        }
        OrchestrationRun run = runMapper.selectByRunIdForUpdate(runId);
        if (run == null) {
            throw new IllegalArgumentException("orchestration run does not exist");
        }
        scopeGuard.requireActiveMember(run.getEnterpriseId(), actorUserId);
        WebhookMetadataResponse target = messageService.get(webhookId, run.getEnterpriseId(), actorUserId);
        if (target == null || !Boolean.TRUE.equals(target.status())) {
            throw new IllegalStateException("approval target Webhook is disabled or unavailable");
        }
        String eventKey = eventKey(runId);
        OrchestrationStep existingStep = stepMapper.selectByRunStepAttemptForUpdate(runId, APPROVAL_STEP, 1);
        DeliveryOutbox existingOutbox = outboxMapper.selectByEventKeyForUpdate(eventKey);
        if ("WEBHOOK_QUEUED".equals(run.getStatus())) {
            verifyReplay(existingStep, existingOutbox, run, webhookId, actorUserId);
            return new HumanWebhookApprovalResult(runId, "ALREADY_APPROVED", existingOutbox.getId());
        }
        if (!"READY".equals(run.getStatus()) || !Objects.equals(run.getVersion(), expectedRunVersion)
                || existingStep != null || existingOutbox != null) {
            throw new IllegalStateException("run is not ready for this approval version");
        }
        OrchestrationStep activation = requireActivation(run);
        SmartBotInputArtifact artifact = inputArtifactService.requireAvailableForActivation(
            run, activation.getInputRef(), activation.getInputHash());
        String finalMessage = WebhookOutboxDispatcherService.outboundMarkdown(
            renderer.render(run, artifact.getInputRef(), artifact.getContentHash()));
        if (!sha256(finalMessage).equalsIgnoreCase(approvedContentHash)) {
            throw new IllegalStateException("approved Webhook content has changed");
        }

        OrchestrationStep gate = new OrchestrationStep();
        Date now = new Date();
        gate.setStepId(UUID.randomUUID().toString());
        gate.setRunId(runId);
        gate.setStepKey(APPROVAL_STEP);
        gate.setAttempt(1);
        gate.setKind("HUMAN_GATE");
        gate.setExecutorType("HUMAN");
        gate.setExecutorRef("u3w.webhook-approval");
        gate.setStatus("SUCCEEDED");
        gate.setInputRef(artifact.getInputRef());
        gate.setInputHash(artifact.getContentHash());
        gate.setOutputRef("webhook:" + webhookId);
        gate.setEvidenceRef("user:" + actorUserId);
        gate.setVersion(0);
        gate.setStartedAt(now);
        gate.setFinishedAt(now);
        if (stepMapper.insertStep(gate) != 1) {
            throw new IllegalStateException("human approval step insert failed");
        }

        OrchestrationReceipt actionReceipt = new OrchestrationReceipt();
        actionReceipt.setReceiptId(UUID.randomUUID().toString());
        actionReceipt.setRunId(runId);
        actionReceipt.setStepId(gate.getStepId());
        actionReceipt.setReceiptType("ACTION");
        actionReceipt.setSource("U3W_AUTHENTICATED_HUMAN_GATE");
        actionReceipt.setExternalRefHash(sha256(runId + ":" + gate.getStepId() + ":" + actorUserId
            + ":" + approvedContentHash.toLowerCase()));
        actionReceipt.setStatus("APPROVED");
        actionReceipt.setEvidenceRef("user:" + actorUserId);
        actionReceipt.setVerifiedAt(now);
        if (receiptMapper.insertReceipt(actionReceipt) != 1) {
            throw new IllegalStateException("human action receipt insert failed");
        }

        DeliveryOutbox outbox = new DeliveryOutbox();
        outbox.setEventKey(eventKey);
        outbox.setRunId(runId);
        outbox.setEventType(EVENT_TYPE);
        outbox.setDestinationType(DESTINATION_TYPE);
        outbox.setDestinationRef("webhook:" + webhookId);
        outbox.setPayloadJson(payload(run, artifact, webhookId, actorUserId, approvedContentHash));
        outbox.setStatus("PENDING");
        outbox.setAttemptCount(0);
        if (outboxMapper.insertOutbox(outbox) != 1) {
            throw new IllegalStateException("webhook outbox insert failed");
        }
        if (runMapper.markWebhookQueued(runId, expectedRunVersion) != 1) {
            throw new IllegalStateException("webhook queue CAS failed");
        }
        return new HumanWebhookApprovalResult(runId, "WEBHOOK_QUEUED", outbox.getId());
    }

    static String eventKey(String runId) {
        return "run:" + runId + ":webhook-send:v1";
    }

    private String payload(OrchestrationRun run, SmartBotInputArtifact artifact,
                           Long webhookId, Long actorUserId, String approvedContentHash) {
        try {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("runId", run.getRunId());
            value.put("enterpriseId", run.getEnterpriseId());
            value.put("webhookId", webhookId);
            value.put("actorUserId", actorUserId);
            value.put("inputArtifactRef", artifact.getInputRef());
            value.put("contentHash", artifact.getContentHash());
            value.put("approvedContentHash", approvedContentHash.toLowerCase());
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("webhook command serialization failed", e);
        }
    }

    private OrchestrationStep requireActivation(OrchestrationRun run) {
        OrchestrationStep activation = stepMapper.selectByRunStepAttemptForUpdate(
            run.getRunId(), InternalRunActivationService.ACTIVATION_STEP, 1);
        if (activation == null || !"SUCCEEDED".equals(activation.getStatus())) {
            throw new IllegalStateException("run has no completed activation step");
        }
        return activation;
    }

    private void verifyReplay(OrchestrationStep step, DeliveryOutbox outbox,
                              OrchestrationRun run, Long webhookId, Long actorUserId) {
        if (step == null || outbox == null || !"SUCCEEDED".equals(step.getStatus())
                || !Objects.equals("webhook:" + webhookId, step.getOutputRef())
                || !Objects.equals("user:" + actorUserId, step.getEvidenceRef())
                || !Objects.equals(run.getRunId(), outbox.getRunId())
                || !EVENT_TYPE.equals(outbox.getEventType())
                || !DESTINATION_TYPE.equals(outbox.getDestinationType())
                || !Objects.equals("webhook:" + webhookId, outbox.getDestinationRef())) {
            throw new IllegalStateException("approved run invariant mismatch");
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("approval evidence hash failed", e);
        }
    }
}
