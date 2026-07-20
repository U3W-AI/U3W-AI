package com.wx.fbsir.business.board.domain;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class BoardConnectorBinding {
    private Long id;
    private String bindingId;
    private Long tenantId;
    private Long memberId;
    private Long userId;
    private String productCode;
    private String sourceCode;
    private String connectorCode;
    private String issuerUri;
    private String resourceUri;
    private String clientId;
    private String principalSubjectDigest;
    private String status;
    private String verificationMethod;
    private String evidenceDigest;
    private Date verifiedAt;
    private Date lastSeenAt;
    private Date validUntil;
    private Date revokedAt;
    private Long version;
    private Date createdAt;
    private Date updatedAt;
    private List<String> scopes = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBindingId() { return bindingId; }
    public void setBindingId(String bindingId) { this.bindingId = bindingId; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    public String getConnectorCode() { return connectorCode; }
    public void setConnectorCode(String connectorCode) { this.connectorCode = connectorCode; }
    public String getIssuerUri() { return issuerUri; }
    public void setIssuerUri(String issuerUri) { this.issuerUri = issuerUri; }
    public String getResourceUri() { return resourceUri; }
    public void setResourceUri(String resourceUri) { this.resourceUri = resourceUri; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getPrincipalSubjectDigest() { return principalSubjectDigest; }
    public void setPrincipalSubjectDigest(String principalSubjectDigest) { this.principalSubjectDigest = principalSubjectDigest; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getVerificationMethod() { return verificationMethod; }
    public void setVerificationMethod(String verificationMethod) { this.verificationMethod = verificationMethod; }
    public String getEvidenceDigest() { return evidenceDigest; }
    public void setEvidenceDigest(String evidenceDigest) { this.evidenceDigest = evidenceDigest; }
    public Date getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Date verifiedAt) { this.verifiedAt = verifiedAt; }
    public Date getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Date lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public Date getValidUntil() { return validUntil; }
    public void setValidUntil(Date validUntil) { this.validUntil = validUntil; }
    public Date getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Date revokedAt) { this.revokedAt = revokedAt; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
    public List<String> getScopes() { return scopes; }
    public void setScopes(List<String> scopes) {
        this.scopes = scopes == null ? new ArrayList<>() : new ArrayList<>(scopes);
    }
}
