package com.wx.fbsir.business.smartbot.service;

import com.wx.fbsir.business.smartbot.domain.OrchestrationRun;
import com.wx.fbsir.business.smartbot.domain.SmartBotInputArtifact;
import com.wx.fbsir.business.smartbot.domain.WecomBotMemberBinding;
import com.wx.fbsir.business.smartbot.domain.WecomInboundEvent;
import com.wx.fbsir.business.smartbot.dto.ResolvedBotBinding;
import com.wx.fbsir.business.smartbot.dto.SmartBotContentArtifactPayload;
import com.wx.fbsir.business.smartbot.mapper.SmartBotInputArtifactMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Persists and validates encrypted content artifacts inside the ingress transaction. */
@Service
public class SmartBotInputArtifactService {

    public static final String PURPOSE = SmartBotContentArtifactProjector.INPUT_KIND;
    private static final Pattern INPUT_REF = Pattern.compile("vault:v1:[0-9a-f-]{36}");
    private static final long TTL_MILLIS = TimeUnit.DAYS.toMillis(7);

    private final SmartBotInputArtifactMapper artifactMapper;
    private final SmartBotInputCryptoService cryptoService;

    public SmartBotInputArtifactService(SmartBotInputArtifactMapper artifactMapper,
                                        SmartBotInputCryptoService cryptoService) {
        this.artifactMapper = artifactMapper;
        this.cryptoService = cryptoService;
    }

    public SmartBotInputArtifact create(ResolvedBotBinding binding,
                                        WecomBotMemberBinding memberBinding,
                                        WecomInboundEvent event,
                                        String msgType,
                                        SmartBotContentArtifactPayload content,
                                        Date now) {
        if (content == null || !PURPOSE.equals(content.getKind()) || event == null || event.getId() == null
                || binding == null || memberBinding == null || !StringUtils.hasText(msgType)) {
            throw new IllegalArgumentException("输入内容工件上下文不完整");
        }
        SmartBotInputArtifact artifact = new SmartBotInputArtifact();
        artifact.setInputRef("vault:v1:" + UUID.randomUUID());
        artifact.setPurpose(PURPOSE);
        artifact.setInboundEventId(event.getId());
        artifact.setRunId(event.getRunId());
        artifact.setBotBindingId(binding.bindingId());
        artifact.setEnterpriseId(binding.enterpriseId());
        artifact.setEnterpriseMemberId(memberBinding.getEnterpriseMemberId());
        artifact.setUserId(memberBinding.getUserId());
        artifact.setMsgType(msgType);
        artifact.setSourcePayloadHash(event.getPayloadHash());
        artifact.setContentHash(content.getContentHash());
        artifact.setPlaintextSize(content.getContentSize());
        artifact.setStatus("AVAILABLE");
        artifact.setExpiresAt(new Date(now.getTime() + TTL_MILLIS));

        byte[] plaintext = content.copyContentBytes();
        try {
            cryptoService.encryptInto(artifact, plaintext);
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
        if (artifactMapper.insertArtifact(artifact) != 1) {
            throw new IllegalStateException("输入内容工件创建失败");
        }
        return artifact;
    }

    /** Locks and validates the one Artifact that must authorize a RUN_CREATED activation. */
    public SmartBotInputArtifact requireAvailableForActivation(OrchestrationRun run,
                                                                String inputRef,
                                                                String contentHash) {
        if (run == null || !StringUtils.hasText(run.getRunId()) || !StringUtils.hasText(inputRef)
                || !INPUT_REF.matcher(inputRef).matches()) {
            throw new IllegalStateException("运行输入内容引用无效");
        }
        SmartBotInputArtifact artifact = artifactMapper.selectByRunIdForUpdate(run.getRunId());
        if (artifact == null || !PURPOSE.equals(artifact.getPurpose())
                || !Objects.equals(artifact.getInputRef(), inputRef)
                || !sameHash(artifact.getContentHash(), contentHash)
                || !Objects.equals(artifact.getRunId(), run.getRunId())
                || !Objects.equals(artifact.getInboundEventId(), run.getInboundEventId())
                || !Objects.equals(artifact.getBotBindingId(), run.getBotBindingId())
                || !Objects.equals(artifact.getEnterpriseId(), run.getEnterpriseId())
                || !Objects.equals(artifact.getEnterpriseMemberId(), run.getEnterpriseMemberId())
                || !Objects.equals(artifact.getUserId(), run.getUserId())
                || !"AVAILABLE".equals(artifact.getStatus()) || artifact.getExpiresAt() == null
                || !artifact.getExpiresAt().after(new Date())) {
            throw new IllegalStateException("运行输入内容不可用或作用域不一致");
        }
        return artifact;
    }

    /** Duplicate callbacks must observe the original artifact instead of creating a new one. */
    public void verifyDuplicateInvariant(WecomInboundEvent event, OrchestrationRun run) {
        SmartBotInputArtifact artifact = artifactMapper.selectByInboundEventIdForUpdate(event.getId());
        if (artifact == null || !PURPOSE.equals(artifact.getPurpose())
                || !Objects.equals(artifact.getInboundEventId(), event.getId())
                || !Objects.equals(artifact.getRunId(), event.getRunId())
                || !Objects.equals(artifact.getRunId(), run.getRunId())
                || !Objects.equals(artifact.getBotBindingId(), event.getBotBindingId())
                || !Objects.equals(artifact.getBotBindingId(), run.getBotBindingId())
                || !Objects.equals(artifact.getEnterpriseId(), run.getEnterpriseId())
                || !Objects.equals(artifact.getEnterpriseMemberId(), run.getEnterpriseMemberId())
                || !Objects.equals(artifact.getUserId(), run.getUserId())
                || !sameHash(artifact.getSourcePayloadHash(), event.getPayloadHash())
                || !StringUtils.hasText(artifact.getInputRef())
                || !INPUT_REF.matcher(artifact.getInputRef()).matches()) {
            throw new IllegalStateException("重复回调输入内容不变量损坏");
        }
    }

    private boolean sameHash(String left, String right) {
        return StringUtils.hasText(left) && StringUtils.hasText(right) && left.equalsIgnoreCase(right);
    }
}
