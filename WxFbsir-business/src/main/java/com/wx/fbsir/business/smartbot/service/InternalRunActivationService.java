package com.wx.fbsir.business.smartbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.fbsir.business.smartbot.domain.DeliveryOutbox;
import com.wx.fbsir.business.smartbot.domain.OrchestrationRun;
import com.wx.fbsir.business.smartbot.domain.OrchestrationStep;
import com.wx.fbsir.business.smartbot.mapper.DeliveryOutboxMapper;
import com.wx.fbsir.business.smartbot.mapper.OrchestrationRunMapper;
import com.wx.fbsir.business.smartbot.mapper.OrchestrationStepMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.Objects;
import java.util.UUID;

/** Applies a leased RUN_CREATED event using only local control-plane tables. */
@Service
public class InternalRunActivationService {

    static final String RUN_CREATED = "RUN_CREATED";
    static final String ACTIVATION_STEP = "internal.dispatch.accepted";

    private final DeliveryOutboxMapper outboxMapper;
    private final OrchestrationRunMapper runMapper;
    private final OrchestrationStepMapper stepMapper;
    private final ObjectMapper objectMapper;

    public InternalRunActivationService(DeliveryOutboxMapper outboxMapper,
                                        OrchestrationRunMapper runMapper,
                                        OrchestrationStepMapper stepMapper,
                                        ObjectMapper objectMapper) {
        this.outboxMapper = outboxMapper;
        this.runMapper = runMapper;
        this.stepMapper = stepMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public InternalOutboxDispatcherService.DispatchOutcome apply(DeliveryOutbox claimed) {
        requireLeaseIdentity(claimed);
        DeliveryOutbox outbox = outboxMapper.selectActiveLeaseForUpdate(
            claimed.getId(), claimed.getLeaseToken());
        if (outbox == null) {
            throw new IllegalStateException("outbox lease expired, lost, or already consumed");
        }
        validateInternalEvent(outbox);

        OrchestrationRun run = runMapper.selectByRunIdForUpdate(outbox.getRunId());
        if (run == null) {
            throw new IllegalStateException("leased outbox run does not exist");
        }
        OrchestrationStep existing = stepMapper.selectByRunStepAttemptForUpdate(
            run.getRunId(), ACTIVATION_STEP, 1);

        String outcome;
        if ("PENDING".equals(run.getStatus())) {
            if (existing != null) {
                throw new IllegalStateException("pending run already has an activation step");
            }
            insertActivationStep(run);
            if (run.getVersion() == null || runMapper.markReady(run.getRunId(), run.getVersion()) != 1) {
                throw new IllegalStateException("run readiness CAS failed");
            }
            outcome = "READY";
        } else if ("READY".equals(run.getStatus())) {
            verifyActivationStep(existing, run);
            outcome = "ALREADY_READY";
        } else {
            throw new IllegalStateException("run cannot accept RUN_CREATED from state " + run.getStatus());
        }

        if (outboxMapper.markConsumed(outbox.getId(), outbox.getLeaseToken()) != 1) {
            throw new IllegalStateException("active outbox could not be consumed");
        }
        return new InternalOutboxDispatcherService.DispatchOutcome(run.getRunId(), outcome);
    }

    private void validateInternalEvent(DeliveryOutbox outbox) {
        if (!RUN_CREATED.equals(outbox.getEventType()) || !StringUtils.hasText(outbox.getRunId())
                || !Objects.equals("run:" + outbox.getRunId() + ":created", outbox.getEventKey())) {
            throw new IllegalStateException("unsupported or inconsistent internal outbox event");
        }
        try {
            JsonNode payload = objectMapper.readTree(outbox.getPayloadJson());
            if (payload == null || !Objects.equals(outbox.getRunId(), payload.path("runId").asText(null))) {
                throw new IllegalStateException("outbox payload runId mismatch");
            }
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("outbox payload is invalid", ex);
        }
    }

    private void insertActivationStep(OrchestrationRun run) {
        Date now = new Date();
        OrchestrationStep step = new OrchestrationStep();
        step.setStepId(UUID.randomUUID().toString());
        step.setRunId(run.getRunId());
        step.setStepKey(ACTIVATION_STEP);
        step.setAttempt(1);
        step.setKind("SYSTEM");
        step.setExecutorType("JAVA");
        step.setExecutorRef("smartbot.internal-dispatcher");
        step.setStatus("SUCCEEDED");
        step.setVersion(0);
        step.setStartedAt(now);
        step.setFinishedAt(now);
        if (stepMapper.insertStep(step) != 1) {
            throw new IllegalStateException("activation step insert failed");
        }
    }

    private void verifyActivationStep(OrchestrationStep step, OrchestrationRun run) {
        if (step == null || !Objects.equals(run.getRunId(), step.getRunId())
                || !ACTIVATION_STEP.equals(step.getStepKey()) || !Objects.equals(1, step.getAttempt())
                || !"SYSTEM".equals(step.getKind()) || !"JAVA".equals(step.getExecutorType())
                || !"smartbot.internal-dispatcher".equals(step.getExecutorRef())
                || !"SUCCEEDED".equals(step.getStatus())) {
            throw new IllegalStateException("ready run lacks a matching activation step");
        }
    }

    private void requireLeaseIdentity(DeliveryOutbox claimed) {
        if (claimed == null || claimed.getId() == null || claimed.getId() <= 0
                || !StringUtils.hasText(claimed.getLeaseToken())) {
            throw new IllegalArgumentException("claimed outbox lease identity is invalid");
        }
    }
}
