package com.wx.fbsir.business.truthspine.domain;

import lombok.Data;

import java.util.Date;

/**
 * 已验签的 Truth Spine 测试态批次摘要。
 *
 * 不保存原始会话内容、签名私钥或用户正文；它也不代表自然流量、产品信用或业务闭环。
 */
@Data
public class TruthSpineReceiptBatch {
    private String batchId;
    private String workloadId;
    private String idempotencyKey;
    private String invocationId;
    private String productId;
    private String bindingHash;
    private String hostReceiptHash;
    private String signerKeyId;
    private String payloadSha256;
    private String stateClass;
    private String verificationStatus;
    private String status;
    private boolean productCreditEligible;
    private boolean businessClosureEligible;
    private Date verifiedAt;
    private Date createTime;
}
