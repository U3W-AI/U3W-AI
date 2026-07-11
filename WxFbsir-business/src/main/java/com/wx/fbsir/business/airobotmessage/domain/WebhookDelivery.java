package com.wx.fbsir.business.airobotmessage.domain;

import java.time.LocalDateTime;

/** Persistence model for the delivery idempotency ledger. */
public class WebhookDelivery {
    private Long id;
    private Long enterpriseId;
    private Long webhookId;
    private Long actorUserId;
    private String idempotencyKey;
    private String payloadHash;
    private String traceId;
    private String status;
    private Integer providerHttpStatus;
    private Integer providerErrcode;
    private String providerErrmsg;
    private Integer attemptCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getEnterpriseId() { return enterpriseId; }
    public void setEnterpriseId(Long enterpriseId) { this.enterpriseId = enterpriseId; }
    public Long getWebhookId() { return webhookId; }
    public void setWebhookId(Long webhookId) { this.webhookId = webhookId; }
    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long actorUserId) { this.actorUserId = actorUserId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getPayloadHash() { return payloadHash; }
    public void setPayloadHash(String payloadHash) { this.payloadHash = payloadHash; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getProviderHttpStatus() { return providerHttpStatus; }
    public void setProviderHttpStatus(Integer providerHttpStatus) { this.providerHttpStatus = providerHttpStatus; }
    public Integer getProviderErrcode() { return providerErrcode; }
    public void setProviderErrcode(Integer providerErrcode) { this.providerErrcode = providerErrcode; }
    public String getProviderErrmsg() { return providerErrmsg; }
    public void setProviderErrmsg(String providerErrmsg) { this.providerErrmsg = providerErrmsg; }
    public Integer getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Integer attemptCount) { this.attemptCount = attemptCount; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
