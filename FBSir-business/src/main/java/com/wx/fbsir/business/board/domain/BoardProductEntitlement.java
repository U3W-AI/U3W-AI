package com.wx.fbsir.business.board.domain;

import java.util.Date;

/** Durable Independent Review Board entitlement row. */
public class BoardProductEntitlement {
    private Long id;
    private Long tenantId;
    private Long memberId;
    private Long userId;
    private String productCode;
    private String planCode;
    private String status;
    private String connectorBindingId;
    private Date connectorVerifiedAt;
    private Date validFrom;
    private Date validUntil;
    private Long version;
    private Date createdAt;
    private Date updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getPlanCode() { return planCode; }
    public void setPlanCode(String planCode) { this.planCode = planCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getConnectorBindingId() { return connectorBindingId; }
    public void setConnectorBindingId(String connectorBindingId) { this.connectorBindingId = connectorBindingId; }
    public Date getConnectorVerifiedAt() { return connectorVerifiedAt; }
    public void setConnectorVerifiedAt(Date connectorVerifiedAt) { this.connectorVerifiedAt = connectorVerifiedAt; }
    public Date getValidFrom() { return validFrom; }
    public void setValidFrom(Date validFrom) { this.validFrom = validFrom; }
    public Date getValidUntil() { return validUntil; }
    public void setValidUntil(Date validUntil) { this.validUntil = validUntil; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
