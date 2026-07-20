package com.wx.fbsir.business.board.domain;

/** Exact server-side enterprise membership scope for an authenticated user. */
public class BoardEnterpriseMemberScope {
    private Long memberId;
    private Long tenantId;
    private Long userId;
    private Integer status;
    private String delFlag;

    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
}
