package com.wx.fbsir.business.board.plan.domain;

import java.util.Date;

/** Complete typed state and provenance for one immutable plan-policy revision. */
public class BoardPlanPolicyReceipt {
    private Long id;
    private String receiptId;
    private String productCode;
    private String planCode;
    private Long policyVersion;
    private String previousReceiptId;
    private String rollbackOfReceiptId;
    private String action;
    private String actorType;
    private Long actorUserId;
    private String idempotencyKeyDigest;
    private String commandDigest;
    private String previousPolicyDigest;
    private String policyDigest;
    private String planName;
    private Boolean vip;
    private Boolean connectorRequired;
    private Integer dailyMeetingLimit;
    private Integer agendaLimit;
    private Integer seatLimit;
    private Boolean secretaryEnabled;
    private String status;
    private String evidenceLevel;
    private Date createdAt;

    public void copyFrom(BoardPlanPolicyReceipt source) {
        this.id = source.id;
        this.receiptId = source.receiptId;
        this.productCode = source.productCode;
        this.planCode = source.planCode;
        this.policyVersion = source.policyVersion;
        this.previousReceiptId = source.previousReceiptId;
        this.rollbackOfReceiptId = source.rollbackOfReceiptId;
        this.action = source.action;
        this.actorType = source.actorType;
        this.actorUserId = source.actorUserId;
        this.idempotencyKeyDigest = source.idempotencyKeyDigest;
        this.commandDigest = source.commandDigest;
        this.previousPolicyDigest = source.previousPolicyDigest;
        this.policyDigest = source.policyDigest;
        this.planName = source.planName;
        this.vip = source.vip;
        this.connectorRequired = source.connectorRequired;
        this.dailyMeetingLimit = source.dailyMeetingLimit;
        this.agendaLimit = source.agendaLimit;
        this.seatLimit = source.seatLimit;
        this.secretaryEnabled = source.secretaryEnabled;
        this.status = source.status;
        this.evidenceLevel = source.evidenceLevel;
        this.createdAt = source.createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getReceiptId() { return receiptId; }
    public void setReceiptId(String receiptId) { this.receiptId = receiptId; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getPlanCode() { return planCode; }
    public void setPlanCode(String planCode) { this.planCode = planCode; }
    public Long getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(Long policyVersion) { this.policyVersion = policyVersion; }
    public String getPreviousReceiptId() { return previousReceiptId; }
    public void setPreviousReceiptId(String previousReceiptId) { this.previousReceiptId = previousReceiptId; }
    public String getRollbackOfReceiptId() { return rollbackOfReceiptId; }
    public void setRollbackOfReceiptId(String rollbackOfReceiptId) { this.rollbackOfReceiptId = rollbackOfReceiptId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getActorType() { return actorType; }
    public void setActorType(String actorType) { this.actorType = actorType; }
    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long actorUserId) { this.actorUserId = actorUserId; }
    public String getIdempotencyKeyDigest() { return idempotencyKeyDigest; }
    public void setIdempotencyKeyDigest(String idempotencyKeyDigest) { this.idempotencyKeyDigest = idempotencyKeyDigest; }
    public String getCommandDigest() { return commandDigest; }
    public void setCommandDigest(String commandDigest) { this.commandDigest = commandDigest; }
    public String getPreviousPolicyDigest() { return previousPolicyDigest; }
    public void setPreviousPolicyDigest(String previousPolicyDigest) { this.previousPolicyDigest = previousPolicyDigest; }
    public String getPolicyDigest() { return policyDigest; }
    public void setPolicyDigest(String policyDigest) { this.policyDigest = policyDigest; }
    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }
    public Boolean getVip() { return vip; }
    public void setVip(Boolean vip) { this.vip = vip; }
    public Boolean getConnectorRequired() { return connectorRequired; }
    public void setConnectorRequired(Boolean connectorRequired) { this.connectorRequired = connectorRequired; }
    public Integer getDailyMeetingLimit() { return dailyMeetingLimit; }
    public void setDailyMeetingLimit(Integer dailyMeetingLimit) { this.dailyMeetingLimit = dailyMeetingLimit; }
    public Integer getAgendaLimit() { return agendaLimit; }
    public void setAgendaLimit(Integer agendaLimit) { this.agendaLimit = agendaLimit; }
    public Integer getSeatLimit() { return seatLimit; }
    public void setSeatLimit(Integer seatLimit) { this.seatLimit = seatLimit; }
    public Boolean getSecretaryEnabled() { return secretaryEnabled; }
    public void setSecretaryEnabled(Boolean secretaryEnabled) { this.secretaryEnabled = secretaryEnabled; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getEvidenceLevel() { return evidenceLevel; }
    public void setEvidenceLevel(String evidenceLevel) { this.evidenceLevel = evidenceLevel; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
