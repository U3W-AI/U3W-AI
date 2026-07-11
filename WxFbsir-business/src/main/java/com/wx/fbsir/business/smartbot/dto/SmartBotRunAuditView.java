package com.wx.fbsir.business.smartbot.dto;

import java.util.Date;
import java.util.List;

/** Safe, metadata-only run view. It never returns message plaintext or provider credentials. */
public record SmartBotRunAuditView(
    String runId,
    String traceId,
    String status,
    Integer version,
    Date createTime,
    Date updateTime,
    List<StepView> steps,
    List<ReceiptView> receipts
) {
    public record StepView(String stepKey, String kind, String executorType, String status,
                           String outputRef, String evidenceRef, Date finishedAt) {
    }

    public record ReceiptView(String receiptType, String source, String status,
                              String evidenceRef, Date verifiedAt) {
    }
}
