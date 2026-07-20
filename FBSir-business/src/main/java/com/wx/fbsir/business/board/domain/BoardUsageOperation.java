package com.wx.fbsir.business.board.domain;

import java.time.LocalDate;
import java.util.Date;

/** Idempotent meeting-quota operation and admin audit projection. */
public class BoardUsageOperation {
    private Long id;
    private String operationId;
    private String requestDigest;
    private Long tenantId;
    private Long memberId;
    private Long userId;
    private String productCode;
    private String metricCode;
    private LocalDate bucketDate;
    private Integer units;
    private String status;
    private String effectivePlanCode;
    private Integer agendaCount;
    private Integer seatCount;
    private Integer remainingCount;
    private Date createTime;
    private Date updateTime;
    private Date completedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOperationId() { return operationId; }
    public void setOperationId(String operationId) { this.operationId = operationId; }
    public String getRequestDigest() { return requestDigest; }
    public void setRequestDigest(String requestDigest) { this.requestDigest = requestDigest; }
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
    public LocalDate getBucketDate() { return bucketDate; }
    public void setBucketDate(LocalDate bucketDate) { this.bucketDate = bucketDate; }
    public Integer getUnits() { return units; }
    public void setUnits(Integer units) { this.units = units; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getEffectivePlanCode() { return effectivePlanCode; }
    public void setEffectivePlanCode(String effectivePlanCode) { this.effectivePlanCode = effectivePlanCode; }
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
}
