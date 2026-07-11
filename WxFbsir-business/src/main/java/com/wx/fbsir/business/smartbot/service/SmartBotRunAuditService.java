package com.wx.fbsir.business.smartbot.service;

import com.wx.fbsir.business.airobotmessage.service.WebhookScopeGuard;
import com.wx.fbsir.business.smartbot.domain.OrchestrationRun;
import com.wx.fbsir.business.smartbot.dto.SmartBotRunAuditView;
import com.wx.fbsir.business.smartbot.mapper.OrchestrationReceiptMapper;
import com.wx.fbsir.business.smartbot.mapper.OrchestrationRunMapper;
import com.wx.fbsir.business.smartbot.mapper.OrchestrationStepMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SmartBotRunAuditService {
    private final OrchestrationRunMapper runMapper;
    private final OrchestrationStepMapper stepMapper;
    private final OrchestrationReceiptMapper receiptMapper;
    private final WebhookScopeGuard scopeGuard;

    public SmartBotRunAuditService(OrchestrationRunMapper runMapper,
                                   OrchestrationStepMapper stepMapper,
                                   OrchestrationReceiptMapper receiptMapper,
                                   WebhookScopeGuard scopeGuard) {
        this.runMapper = runMapper;
        this.stepMapper = stepMapper;
        this.receiptMapper = receiptMapper;
        this.scopeGuard = scopeGuard;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SmartBotRunAuditView get(String runId, Long userId) {
        OrchestrationRun run = runMapper.selectByRunId(runId);
        if (run == null) {
            throw new IllegalArgumentException("orchestration run does not exist");
        }
        scopeGuard.requireActiveMember(run.getEnterpriseId(), userId);
        var steps = stepMapper.selectByRunId(runId).stream()
            .map(step -> new SmartBotRunAuditView.StepView(step.getStepKey(), step.getKind(),
                step.getExecutorType(), step.getStatus(), step.getOutputRef(),
                step.getEvidenceRef(), step.getFinishedAt()))
            .toList();
        var receipts = receiptMapper.selectByRunId(runId).stream()
            .map(receipt -> new SmartBotRunAuditView.ReceiptView(receipt.getReceiptType(),
                receipt.getSource(), receipt.getStatus(), receipt.getEvidenceRef(),
                receipt.getVerifiedAt()))
            .toList();
        return new SmartBotRunAuditView(run.getRunId(), run.getTraceId(), run.getStatus(),
            run.getVersion(), run.getCreateTime(), run.getUpdateTime(), steps, receipts);
    }
}
