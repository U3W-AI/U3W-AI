package com.wx.fbsir.business.board.domain;

import java.util.Date;

/** Immutable lineage from one committed meeting operation to its consumed policy receipt. */
public class BoardUsageOperationPolicyReceipt {
    private Long id;
    private Long tenantId;
    private String operationId;
    private String productCode;
    private String planCode;
    private String policyReceiptId;
    private Long policyVersion;
    private String policyDigest;
    private Date createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getOperationId() { return operationId; }
    public void setOperationId(String operationId) { this.operationId = operationId; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getPlanCode() { return planCode; }
    public void setPlanCode(String planCode) { this.planCode = planCode; }
    public String getPolicyReceiptId() { return policyReceiptId; }
    public void setPolicyReceiptId(String policyReceiptId) { this.policyReceiptId = policyReceiptId; }
    public Long getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(Long policyVersion) { this.policyVersion = policyVersion; }
    public String getPolicyDigest() { return policyDigest; }
    public void setPolicyDigest(String policyDigest) { this.policyDigest = policyDigest; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
