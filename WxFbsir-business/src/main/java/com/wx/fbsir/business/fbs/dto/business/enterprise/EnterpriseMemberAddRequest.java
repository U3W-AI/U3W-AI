package com.wx.fbsir.business.fbs.dto.business.enterprise;

/**
 * 添加企业成员请求DTO
 *
 * @author wxfbsir
 * @date 2026-04-09
 */
public class EnterpriseMemberAddRequest {

    /** 企业ID（必填） */
    private Long enterpriseId;

    /** 用户ID（必填） */
    private Long userId;

    /** 成员角色（可选，默认 MEMBER） */
    private String role;

    // ===== getter / setter =====

    public Long getEnterpriseId() { return enterpriseId; }
    public void setEnterpriseId(Long enterpriseId) { this.enterpriseId = enterpriseId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
