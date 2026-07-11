package com.wx.fbsir.business.smartbot.service;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/** Short database transactions around a network call; no plaintext is persisted. */
@Service
public class WebhookDispatchTransactionService {
    static final String RESULT_STEP = "webhook.send.result";

    private final DeliveryOutboxMapper outboxMapper;
    private final OrchestrationRunMapper runMapper;
    private final OrchestrationStepMapper stepMapper;
    private final OrchestrationReceiptMapper receiptMapper;
    private final SmartBotWebhookPayloadRenderer renderer;
    private final ObjectMapper objectMapper;

    public WebhookDispatchTransactionService(DeliveryOutboxMapper outboxMapper,
                                             OrchestrationRunMapper runMapper,
                                             OrchestrationStepMapper stepMapper,
                                             OrchestrationReceiptMapper receiptMapper,
                                             SmartBotWebhookPayloadRenderer renderer,
                                             ObjectMapper objectMapper) {
        this.outboxMapper = outboxMapper;
        this.runMapper = runMapper;
        this.stepMapper = stepMapper;
        this.receiptMapper = receiptMapper;
        this.renderer = renderer;
        this.objectMapper = objectMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public WebhookWorkItem prepare(DeliveryOutbox claimed) {
        requireClaim(claimed);
        OrchestrationRun run = runMapper.selectByRunIdForUpdate(claimed.getRunId());
        DeliveryOutbox outbox = outboxMapper.selectActiveLeaseForUpdateByDestination(
            claimed.getId(), claimed.getLeaseToken(), HumanWebhookApprovalService.DESTINATION_TYPE);
        if (outbox == null || !Objects.equals(claimed.getRunId(), outbox.getRunId())) {
            throw new IllegalStateException("webhook outbox lease expired or was lost");
        }
        Command command = parse(outbox);
        if (run == null || !"WEBHOOK_QUEUED".equals(run.getStatus())
                || !Objects.equals(run.getRunId(), command.runId())
                || !Objects.equals(run.getEnterpriseId(), command.enterpriseId())) {
            throw new IllegalStateException("webhook command run is not dispatchable");
        }
        OrchestrationStep gate = stepMapper.selectByRunStepAttemptForUpdate(
            run.getRunId(), HumanWebhookApprovalService.APPROVAL_STEP, 1);
        if (gate == null || !"SUCCEEDED".equals(gate.getStatus())
                || !Objects.equals("webhook:" + command.webhookId(), gate.getOutputRef())
                || !Objects.equals("user:" + command.actorUserId(), gate.getEvidenceRef())) {
            throw new IllegalStateException("webhook command lacks its human approval gate");
        }
        String message = renderer.render(run, command.inputArtifactRef(), command.contentHash());
        if (!sha256(WebhookOutboxDispatcherService.outboundMarkdown(message))
                .equalsIgnoreCase(command.approvedContentHash())) {
            throw new IllegalStateException("approved Webhook content fingerprint mismatch");
        }
        return new WebhookWorkItem(outbox.getId(), outbox.getLeaseToken(), run.getRunId(),
            run.getEnterpriseId(), command.webhookId(), command.actorUserId(), message,
            "smartbot:" + run.getRunId() + ":webhook:" + command.webhookId() + ":v1");
    }

    @Transactional(rollbackFor = Exception.class)
    public DispatchResult finalizeDelivery(WebhookWorkItem item, WebhookDeliveryReceipt delivery) {
        if (item == null || delivery == null || delivery.deliveryId() == null
                || !StringUtils.hasText(delivery.traceId()) || !StringUtils.hasText(delivery.status())) {
            throw new IllegalArgumentException("webhook delivery receipt is incomplete");
        }
        OrchestrationRun run = runMapper.selectByRunIdForUpdate(item.runId());
        if (run == null || !"WEBHOOK_QUEUED".equals(run.getStatus())) {
            throw new IllegalStateException("webhook run is not awaiting a result");
        }
        DeliveryOutbox outbox = outboxMapper.selectActiveLeaseForUpdateByDestination(
            item.outboxId(), item.leaseToken(), HumanWebhookApprovalService.DESTINATION_TYPE);
        if (outbox == null || !Objects.equals(item.runId(), outbox.getRunId())) {
            throw new IllegalStateException("webhook outbox lease expired before finalization");
        }
        OrchestrationStep existing = stepMapper.selectByRunStepAttemptForUpdate(
            run.getRunId(), RESULT_STEP, 1);
        if (existing != null) {
            throw new IllegalStateException("webhook result step already exists");
        }
        Date now = new Date();
        OrchestrationStep step = new OrchestrationStep();
        step.setStepId(UUID.randomUUID().toString());
        step.setRunId(run.getRunId());
        step.setStepKey(RESULT_STEP);
        step.setAttempt(1);
        step.setKind("CAPABILITY");
        step.setExecutorType("HTTP");
        step.setExecutorRef("wecom.group-webhook");
        step.setStatus(switch (delivery.status()) {
            case "PROVIDER_ACCEPTED" -> "SUCCEEDED";
            case "REJECTED" -> "FAILED";
            default -> "RECONCILIATION_REQUIRED";
        });
        step.setInputRef("webhook:" + item.webhookId());
        step.setOutputRef("wc_webhook_delivery:" + delivery.deliveryId());
        step.setEvidenceRef("trace:" + delivery.traceId());
        step.setVersion(0);
        step.setStartedAt(now);
        step.setFinishedAt(now);
        if (stepMapper.insertStep(step) != 1) {
            throw new IllegalStateException("webhook result step insert failed");
        }

        OrchestrationReceipt receipt = new OrchestrationReceipt();
        receipt.setReceiptId(UUID.randomUUID().toString());
        receipt.setRunId(run.getRunId());
        receipt.setStepId(step.getStepId());
        receipt.setReceiptType("DELIVERY");
        receipt.setSource("WECOM_GROUP_WEBHOOK");
        receipt.setExternalRefHash(sha256(delivery.deliveryId() + ":" + delivery.traceId()));
        receipt.setStatus(delivery.status());
        receipt.setEvidenceRef("wc_webhook_delivery:" + delivery.deliveryId());
        receipt.setVerifiedAt(now);
        if (receiptMapper.insertReceipt(receipt) != 1) {
            throw new IllegalStateException("webhook delivery receipt insert failed");
        }

        String runStatus = switch (delivery.status()) {
            case "PROVIDER_ACCEPTED" -> "WEBHOOK_ACCEPTED_AWAIT_READBACK";
            case "REJECTED" -> "WEBHOOK_REJECTED";
            default -> "MANUAL_RECONCILIATION";
        };
        if (runMapper.markWebhookResult(run.getRunId(), run.getVersion(), runStatus) != 1) {
            throw new IllegalStateException("webhook result run CAS failed");
        }
        if (outboxMapper.markConsumed(outbox.getId(), outbox.getLeaseToken()) != 1) {
            throw new IllegalStateException("webhook outbox consume failed");
        }
        return new DispatchResult(run.getRunId(), runStatus, delivery.deliveryId());
    }

    /** Exhausted commands become an explicit manual-reconciliation run, never a silent queued zombie. */
    @Transactional(rollbackFor = Exception.class)
    public DispatchResult deadLetter(DeliveryOutbox claimed) {
        requireClaim(claimed);
        OrchestrationRun run = runMapper.selectByRunIdForUpdate(claimed.getRunId());
        if (run == null || !"WEBHOOK_QUEUED".equals(run.getStatus())) {
            throw new IllegalStateException("webhook run is not awaiting dead-letter reconciliation");
        }
        DeliveryOutbox outbox = outboxMapper.selectActiveLeaseForUpdateByDestination(
            claimed.getId(), claimed.getLeaseToken(), HumanWebhookApprovalService.DESTINATION_TYPE);
        if (outbox == null || !Objects.equals(run.getRunId(), outbox.getRunId())) {
            throw new IllegalStateException("webhook outbox lease expired before dead-lettering");
        }
        if (runMapper.markWebhookResult(run.getRunId(), run.getVersion(), "MANUAL_RECONCILIATION") != 1) {
            throw new IllegalStateException("webhook dead-letter run CAS failed");
        }
        if (outboxMapper.markDead(outbox.getId(), outbox.getLeaseToken(),
                "webhook dispatch retry limit exhausted") != 1) {
            throw new IllegalStateException("webhook outbox dead-letter failed");
        }
        return new DispatchResult(run.getRunId(), "MANUAL_RECONCILIATION", null);
    }

    private Command parse(DeliveryOutbox outbox) {
        if (!HumanWebhookApprovalService.EVENT_TYPE.equals(outbox.getEventType())
                || !HumanWebhookApprovalService.DESTINATION_TYPE.equals(outbox.getDestinationType())
                || !Objects.equals(HumanWebhookApprovalService.eventKey(outbox.getRunId()), outbox.getEventKey())) {
            throw new IllegalStateException("unsupported Webhook outbox command");
        }
        try {
            JsonNode root = objectMapper.readTree(outbox.getPayloadJson());
            Command command = new Command(root.path("runId").asText(null),
                root.path("enterpriseId").isIntegralNumber() ? root.path("enterpriseId").asLong() : null,
                root.path("webhookId").isIntegralNumber() ? root.path("webhookId").asLong() : null,
                root.path("actorUserId").isIntegralNumber() ? root.path("actorUserId").asLong() : null,
                root.path("inputArtifactRef").asText(null), root.path("contentHash").asText(null),
                root.path("approvedContentHash").asText(null));
            if (!StringUtils.hasText(command.runId()) || command.enterpriseId() == null
                    || command.webhookId() == null || command.actorUserId() == null
                    || !StringUtils.hasText(command.inputArtifactRef())
                    || !StringUtils.hasText(command.contentHash())
                    || !StringUtils.hasText(command.approvedContentHash())
                    || !command.approvedContentHash().matches("[0-9a-fA-F]{64}")) {
                throw new IllegalStateException("webhook outbox payload is incomplete");
            }
            return command;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("webhook outbox payload is invalid", e);
        }
    }

    private void requireClaim(DeliveryOutbox claimed) {
        if (claimed == null || claimed.getId() == null || claimed.getId() <= 0
                || !StringUtils.hasText(claimed.getLeaseToken())
                || !StringUtils.hasText(claimed.getRunId())) {
            throw new IllegalArgumentException("claimed Webhook outbox is invalid");
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("delivery evidence hash failed", e);
        }
    }

    private record Command(String runId, Long enterpriseId, Long webhookId, Long actorUserId,
                           String inputArtifactRef, String contentHash, String approvedContentHash) {
    }

    public record WebhookWorkItem(Long outboxId, String leaseToken, String runId, Long enterpriseId,
                                  Long webhookId, Long actorUserId, String messageContent,
                                  String idempotencyKey) {
    }

    public record DispatchResult(String runId, String status, Long deliveryId) {
    }
}
