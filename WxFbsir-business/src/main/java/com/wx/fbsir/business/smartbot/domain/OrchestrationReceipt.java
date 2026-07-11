package com.wx.fbsir.business.smartbot.domain;

import lombok.Data;

import java.util.Date;

/** Evidence-linked receipt. ACTION is deliberately distinct from business/read receipts. */
@Data
public class OrchestrationReceipt {
    private String receiptId;
    private String runId;
    private String stepId;
    private String receiptType;
    private String source;
    private String externalRefHash;
    private String status;
    private String evidenceRef;
    private Date verifiedAt;
    private Date createTime;
}
