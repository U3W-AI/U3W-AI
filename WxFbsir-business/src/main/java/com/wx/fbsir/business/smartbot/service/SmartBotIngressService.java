package com.wx.fbsir.business.smartbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wx.fbsir.business.fbs.domain.entity.FbsEnterprise;
import com.wx.fbsir.business.fbs.domain.entity.FbsEnterpriseMember;
import com.wx.fbsir.business.fbs.mapper.FbsEnterpriseMapper;
import com.wx.fbsir.business.fbs.mapper.FbsEnterpriseMemberMapper;
import com.wx.fbsir.business.smartbot.domain.DeliveryOutbox;
import com.wx.fbsir.business.smartbot.domain.OrchestrationRun;
import com.wx.fbsir.business.smartbot.domain.OrchestrationStep;
import com.wx.fbsir.business.smartbot.domain.WecomBotMemberBinding;
import com.wx.fbsir.business.smartbot.domain.WecomInboundEvent;
import com.wx.fbsir.business.smartbot.dto.ResolvedBotBinding;
import com.wx.fbsir.business.smartbot.dto.SmartBotInboundEnvelope;
import com.wx.fbsir.business.smartbot.dto.SmartBotIngressResult;
import com.wx.fbsir.business.smartbot.mapper.DeliveryOutboxMapper;
import com.wx.fbsir.business.smartbot.mapper.OrchestrationRunMapper;
import com.wx.fbsir.business.smartbot.mapper.OrchestrationStepMapper;
import com.wx.fbsir.business.smartbot.mapper.WecomBotMemberBindingMapper;
import com.wx.fbsir.business.smartbot.mapper.WecomBotBindingMapper;
import com.wx.fbsir.business.smartbot.mapper.WecomInboundEventMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Durable ingress spine. The database unique key claims a provider event and
 * the winning transaction creates exactly one run, ingress step, and outbox row.
 */
@Service
public class SmartBotIngressService {

    private static final Pattern SHA256 = Pattern.compile("[a-fA-F0-9]{64}");
    private static final String DEFAULT_DEFINITION = "u3w.smartbot.default";

    private final WecomBotBindingMapper botBindingMapper;
    private final WecomBotMemberBindingMapper memberBindingMapper;
    private final FbsEnterpriseMapper enterpriseMapper;
    private final FbsEnterpriseMemberMapper enterpriseMemberMapper;
    private final WecomInboundEventMapper inboundEventMapper;
    private final OrchestrationRunMapper runMapper;
    private final OrchestrationStepMapper stepMapper;
    private final DeliveryOutboxMapper outboxMapper;
    private final ExternalIdentityHasher identityHasher;
    private final ObjectMapper objectMapper;

    public SmartBotIngressService(WecomBotBindingMapper botBindingMapper,
                                  WecomBotMemberBindingMapper memberBindingMapper,
                                  FbsEnterpriseMapper enterpriseMapper,
                                  FbsEnterpriseMemberMapper enterpriseMemberMapper,
                                  WecomInboundEventMapper inboundEventMapper,
                                  OrchestrationRunMapper runMapper,
                                  OrchestrationStepMapper stepMapper,
                                  DeliveryOutboxMapper outboxMapper,
                                  ExternalIdentityHasher identityHasher,
                                  ObjectMapper objectMapper) {
        this.botBindingMapper = botBindingMapper;
        this.memberBindingMapper = memberBindingMapper;
        this.enterpriseMapper = enterpriseMapper;
        this.enterpriseMemberMapper = enterpriseMemberMapper;
        this.inboundEventMapper = inboundEventMapper;
        this.runMapper = runMapper;
        this.stepMapper = stepMapper;
        this.outboxMapper = outboxMapper;
        this.identityHasher = identityHasher;
        this.objectMapper = objectMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public SmartBotIngressResult accept(ResolvedBotBinding binding,
                                        SmartBotInboundEnvelope envelope) {
        validate(binding, envelope);
        if (!Objects.equals(binding.aibotId(), envelope.getAibotId())) {
            throw new SecurityException("回调机器人与绑定不一致");
        }
        validateControlPlaneState(binding);

        String userHash = identityHasher.hashUser(binding.bindingId(), envelope.getOpaqueSenderId());
        String msgIdHash = identityHasher.hashMessage(binding.bindingId(), envelope.getMsgId());
        WecomBotMemberBinding memberBinding = memberBindingMapper
            .selectActiveByExternalHash(binding.bindingId(), userHash);
        FbsEnterpriseMember member = validateMemberBinding(binding, memberBinding);

        String traceId = UUID.randomUUID().toString();
        String runId = UUID.randomUUID().toString();
        String streamId = "u3w-" + UUID.randomUUID().toString().replace("-", "");

        WecomInboundEvent event = new WecomInboundEvent();
        event.setBotBindingId(binding.bindingId());
        event.setMsgIdHash(msgIdHash);
        event.setAibotId(envelope.getAibotId());
        event.setTraceId(traceId);
        event.setRunId(runId);
        event.setStreamId(streamId);
        event.setFromUserHash(userHash);
        event.setChatType(blankToNull(envelope.getChatType()));
        event.setChatIdHash(StringUtils.hasText(envelope.getChatId())
            ? identityHasher.hashChat(binding.bindingId(), envelope.getChatId()) : null);
        event.setMsgType(envelope.getMsgType());
        event.setEventType(blankToNull(envelope.getEventType()));
        event.setPayloadHash(normalizeHash(envelope.getPayloadHash()));
        event.setStatus("RECEIVED");

        int claimResult = inboundEventMapper.claimInboundEvent(event);
        if (claimResult <= 0 || event.getId() == null) {
            throw new IllegalStateException("入站事件原子 claim 失败");
        }
        if (claimResult != 1) {
            return duplicateResult(event, userHash);
        }

        Date now = new Date();
        OrchestrationRun run = buildRun(binding, memberBinding, event, now);
        ensureInserted(runMapper.insertRun(run), "编排运行创建失败");

        OrchestrationStep step = buildIngressStep(envelope, event, now);
        ensureInserted(stepMapper.insertStep(step), "入站步骤创建失败");

        DeliveryOutbox outbox = buildRunCreatedOutbox(binding, event, now);
        ensureInserted(outboxMapper.insertOutbox(outbox), "事务 Outbox 创建失败");

        return new SmartBotIngressResult(true, event.getId(), event.getTraceId(),
            event.getRunId(), event.getStreamId(), binding.enterpriseId(),
            member.getId(), member.getUserId());
    }

    private SmartBotIngressResult duplicateResult(WecomInboundEvent claimed,
                                                   String currentUserHash) {
        WecomInboundEvent stored = inboundEventMapper.selectById(claimed.getId());
        if (stored == null || !Objects.equals(stored.getBotBindingId(), claimed.getBotBindingId())
                || !Objects.equals(stored.getMsgIdHash(), claimed.getMsgIdHash())
                || !Objects.equals(stored.getAibotId(), claimed.getAibotId())
                || !Objects.equals(stored.getFromUserHash(), currentUserHash)
                || !sameOptionalHash(stored.getPayloadHash(), claimed.getPayloadHash())) {
            throw new SecurityException("重复回调元数据不一致");
        }
        OrchestrationRun run = runMapper.selectByRunId(stored.getRunId());
        if (run == null) {
            throw new IllegalStateException("重复回调对应运行不存在");
        }
        return new SmartBotIngressResult(false, stored.getId(), stored.getTraceId(),
            stored.getRunId(), stored.getStreamId(), run.getEnterpriseId(),
            run.getEnterpriseMemberId(), run.getUserId());
    }

    private FbsEnterpriseMember validateMemberBinding(ResolvedBotBinding binding,
                                                       WecomBotMemberBinding memberBinding) {
        if (memberBinding == null || memberBinding.getEnterpriseMemberId() == null
                || memberBinding.getUserId() == null
                || !Objects.equals(binding.enterpriseId(), memberBinding.getEnterpriseId())) {
            throw new SecurityException("企微用户未绑定到当前企业成员");
        }
        FbsEnterpriseMember member = enterpriseMemberMapper.selectById(memberBinding.getEnterpriseMemberId());
        if (member == null || member.getStatus() == null || member.getStatus() != 1
                || !"0".equals(member.getDelFlag())
                || !Objects.equals(member.getEnterpriseId(), binding.enterpriseId())
                || !Objects.equals(member.getUserId(), memberBinding.getUserId())) {
            throw new SecurityException("U3W 企业成员绑定已失效");
        }
        return member;
    }

    private OrchestrationRun buildRun(ResolvedBotBinding binding,
                                      WecomBotMemberBinding memberBinding,
                                      WecomInboundEvent event,
                                      Date now) {
        OrchestrationRun run = new OrchestrationRun();
        run.setRunId(event.getRunId());
        run.setInboundEventId(event.getId());
        run.setBotBindingId(binding.bindingId());
        run.setEnterpriseId(binding.enterpriseId());
        run.setEnterpriseMemberId(memberBinding.getEnterpriseMemberId());
        run.setUserId(memberBinding.getUserId());
        run.setDefinitionCode(DEFAULT_DEFINITION);
        run.setDefinitionVersion(1);
        run.setTraceId(event.getTraceId());
        run.setStreamId(event.getStreamId());
        run.setStatus("PENDING");
        run.setVersion(0);
        run.setNextWakeupAt(now);
        return run;
    }

    private OrchestrationStep buildIngressStep(SmartBotInboundEnvelope envelope,
                                               WecomInboundEvent event,
                                               Date now) {
        OrchestrationStep step = new OrchestrationStep();
        step.setStepId(UUID.randomUUID().toString());
        step.setRunId(event.getRunId());
        step.setStepKey("ingress.accepted");
        step.setAttempt(1);
        step.setKind("SYSTEM");
        step.setExecutorType("JAVA");
        step.setExecutorRef("smartbot.ingress");
        step.setStatus("SUCCEEDED");
        step.setInputHash(normalizeHash(envelope.getPayloadHash()));
        step.setVersion(0);
        step.setStartedAt(now);
        step.setFinishedAt(now);
        return step;
    }

    private DeliveryOutbox buildRunCreatedOutbox(ResolvedBotBinding binding,
                                                 WecomInboundEvent event,
                                                 Date now) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("runId", event.getRunId());
        payload.put("traceId", event.getTraceId());
        payload.put("botBindingId", binding.bindingId());
        payload.put("definitionCode", DEFAULT_DEFINITION);

        DeliveryOutbox outbox = new DeliveryOutbox();
        outbox.setEventKey("run:" + event.getRunId() + ":created");
        outbox.setRunId(event.getRunId());
        outbox.setEventType("RUN_CREATED");
        outbox.setDestinationType("INTERNAL_DISPATCHER");
        outbox.setPayloadJson(payload.toString());
        outbox.setStatus("PENDING");
        outbox.setAttemptCount(0);
        outbox.setNextAttemptAt(now);
        return outbox;
    }

    private void validate(ResolvedBotBinding binding, SmartBotInboundEnvelope envelope) {
        if (binding == null || binding.bindingId() == null || binding.enterpriseId() == null
                || !StringUtils.hasText(binding.aibotId())) {
            throw new IllegalArgumentException("机器人绑定不完整");
        }
        if (envelope == null || !StringUtils.hasText(envelope.getMsgId())
                || envelope.getMsgId().length() > 512
                || !StringUtils.hasText(envelope.getAibotId())
                || envelope.getAibotId().length() > 128
                || !StringUtils.hasText(envelope.getOpaqueSenderId())
                || envelope.getOpaqueSenderId().length() > 512
                || !StringUtils.hasText(envelope.getMsgType())
                || envelope.getMsgType().length() > 32
                || !StringUtils.hasText(envelope.getPayloadHash())) {
            throw new IllegalArgumentException("入站事件元数据不完整或超限");
        }
        if (StringUtils.hasText(envelope.getChatType())
                && (!Set.of("single", "group").contains(envelope.getChatType())
                    || envelope.getChatType().length() > 16)) {
            throw new IllegalArgumentException("chatType 不受支持");
        }
        if (StringUtils.hasText(envelope.getChatId()) && envelope.getChatId().length() > 512) {
            throw new IllegalArgumentException("chatId 超限");
        }
        if (StringUtils.hasText(envelope.getEventType()) && envelope.getEventType().length() > 64) {
            throw new IllegalArgumentException("eventType 超限");
        }
        normalizeHash(envelope.getPayloadHash());
    }

    private void validateControlPlaneState(ResolvedBotBinding binding) {
        var storedBinding = botBindingMapper.selectActiveById(binding.bindingId());
        if (storedBinding == null
                || !Objects.equals(storedBinding.getAibotId(), binding.aibotId())
                || !Objects.equals(storedBinding.getEnterpriseId(), binding.enterpriseId())
                || !Objects.equals(storedBinding.getCredentialVersion(), binding.credentialVersion())
                || !"CALLBACK".equals(storedBinding.getMode())) {
            throw new SecurityException("机器人绑定已变更或不可用");
        }
        FbsEnterprise enterprise = enterpriseMapper.selectById(binding.enterpriseId());
        if (enterprise == null || enterprise.getStatus() == null || enterprise.getStatus() != 1
                || !"0".equals(enterprise.getDelFlag())) {
            throw new SecurityException("企业已禁用或不存在");
        }
    }

    private String normalizeHash(String hash) {
        if (!StringUtils.hasText(hash)) {
            return null;
        }
        if (!SHA256.matcher(hash).matches()) {
            throw new IllegalArgumentException("payloadHash 必须是 SHA-256 十六进制");
        }
        return hash.toLowerCase();
    }

    private boolean sameOptionalHash(String left, String right) {
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return !StringUtils.hasText(left) && !StringUtils.hasText(right);
        }
        return left.equalsIgnoreCase(right);
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private void ensureInserted(int rows, String message) {
        if (rows != 1) {
            throw new IllegalStateException(message);
        }
    }
}
