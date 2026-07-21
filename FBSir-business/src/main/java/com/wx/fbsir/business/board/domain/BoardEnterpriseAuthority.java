package com.wx.fbsir.business.board.domain;

/** Raw enterprise authority row locked before board member and entitlement rows. */
public class BoardEnterpriseAuthority {
    private Long tenantId;
    private String tenantName;
    private Integer status;
    private String delFlag;

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getTenantName() { return tenantName; }
    public void setTenantName(String tenantName) { this.tenantName = tenantName; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
}
