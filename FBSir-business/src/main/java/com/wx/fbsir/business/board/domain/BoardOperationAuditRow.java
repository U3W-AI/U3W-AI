package com.wx.fbsir.business.board.domain;

import java.time.LocalDate;
import java.util.Date;

/** Internal-only immutable-policy lineage row for the bounded admin operation audit read. */
public class BoardOperationAuditRow {
    private String operationId;
    private Long tenantId;
    private Long memberId;
    private Long userId;
    private String productCode;
    private String metricCode;
    private String status;
    private String effectivePlanCode;
    private LocalDate bucketDate;
    private Integer agendaCount;
    private Integer seatCount;
    private Integer remainingCount;
    private Date createTime;
    private Date updateTime;
    private Date completedAt;
    private Long lineageTenantId;
    private String lineageOperationId;
    private String lineageProductCode;
    private String lineagePlanCode;
    private String policyReceiptId;
    private Long policyVersion;
    private String policyDigest;
    private String policyReceiptReceiptId;
    private String policyReceiptProductCode;
    private String policyReceiptPlanCode;
    private Long policyReceiptPolicyVersion;
    private String policyReceiptPolicyDigest;
    private String policyPlanName;

    public String getOperationId() { return operationId; }
    public void setOperationId(String operationId) { this.operationId = operationId; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getMetricCode() { return metricCode; }
    public void setMetricCode(String metricCode) { this.metricCode = metricCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getEffectivePlanCode() { return effectivePlanCode; }
    public void setEffectivePlanCode(String effectivePlanCode) { this.effectivePlanCode = effectivePlanCode; }
    public LocalDate getBucketDate() { return bucketDate; }
    public void setBucketDate(LocalDate bucketDate) { this.bucketDate = bucketDate; }
    public Integer getAgendaCount() { return agendaCount; }
    public void setAgendaCount(Integer agendaCount) { this.agendaCount = agendaCount; }
    public Integer getSeatCount() { return seatCount; }
    public void setSeatCount(Integer seatCount) { this.seatCount = seatCount; }
    public Integer getRemainingCount() { return remainingCount; }
    public void setRemainingCount(Integer remainingCount) { this.remainingCount = remainingCount; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
    public Date getCompletedAt() { return completedAt; }
    public void setCompletedAt(Date completedAt) { this.completedAt = completedAt; }
    public Long getLineageTenantId() { return lineageTenantId; }
    public void setLineageTenantId(Long lineageTenantId) { this.lineageTenantId = lineageTenantId; }
    public String getLineageOperationId() { return lineageOperationId; }
    public void setLineageOperationId(String lineageOperationId) { this.lineageOperationId = lineageOperationId; }
    public String getLineageProductCode() { return lineageProductCode; }
    public void setLineageProductCode(String lineageProductCode) { this.lineageProductCode = lineageProductCode; }
    public String getLineagePlanCode() { return lineagePlanCode; }
    public void setLineagePlanCode(String lineagePlanCode) { this.lineagePlanCode = lineagePlanCode; }
    public String getPolicyReceiptId() { return policyReceiptId; }
    public void setPolicyReceiptId(String policyReceiptId) { this.policyReceiptId = policyReceiptId; }
    public Long getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(Long policyVersion) { this.policyVersion = policyVersion; }
    public String getPolicyDigest() { return policyDigest; }
    public void setPolicyDigest(String policyDigest) { this.policyDigest = policyDigest; }
    public String getPolicyReceiptReceiptId() { return policyReceiptReceiptId; }
    public void setPolicyReceiptReceiptId(String policyReceiptReceiptId) { this.policyReceiptReceiptId = policyReceiptReceiptId; }
    public String getPolicyReceiptProductCode() { return policyReceiptProductCode; }
    public void setPolicyReceiptProductCode(String policyReceiptProductCode) { this.policyReceiptProductCode = policyReceiptProductCode; }
    public String getPolicyReceiptPlanCode() { return policyReceiptPlanCode; }
    public void setPolicyReceiptPlanCode(String policyReceiptPlanCode) { this.policyReceiptPlanCode = policyReceiptPlanCode; }
    public Long getPolicyReceiptPolicyVersion() { return policyReceiptPolicyVersion; }
    public void setPolicyReceiptPolicyVersion(Long policyReceiptPolicyVersion) { this.policyReceiptPolicyVersion = policyReceiptPolicyVersion; }
    public String getPolicyReceiptPolicyDigest() { return policyReceiptPolicyDigest; }
    public void setPolicyReceiptPolicyDigest(String policyReceiptPolicyDigest) { this.policyReceiptPolicyDigest = policyReceiptPolicyDigest; }
    public String getPolicyPlanName() { return policyPlanName; }
    public void setPolicyPlanName(String policyPlanName) { this.policyPlanName = policyPlanName; }
}
