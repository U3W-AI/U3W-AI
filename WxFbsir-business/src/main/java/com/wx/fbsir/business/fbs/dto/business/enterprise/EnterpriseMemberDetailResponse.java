package com.wx.fbsir.business.fbs.dto.business.enterprise;

/**
 * 企业成员详情响应DTO
 *
 * @author wxfbsir
 * @date 2026-04-09
 */
public class EnterpriseMemberDetailResponse {

    private Long id;
    private Long enterpriseId;
    private String enterpriseName;
    private Long userId;
    private String userName;
    private String role;
    private String roleDesc;
    private String joinTime;
    private Integer status;
    private String statusDesc;
    private Integer packCount; // 成员已授权的场景包数量

    // ===== getter / setter =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEnterpriseId() { return enterpriseId; }
    public void setEnterpriseId(Long enterpriseId) { this.enterpriseId = enterpriseId; }

    public String getEnterpriseName() { return enterpriseName; }
    public void setEnterpriseName(String enterpriseName) { this.enterpriseName = enterpriseName; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getRoleDesc() { return roleDesc; }
    public void setRoleDesc(String roleDesc) { this.roleDesc = roleDesc; }

    public String getJoinTime() { return joinTime; }
    public void setJoinTime(String joinTime) { this.joinTime = joinTime; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public String getStatusDesc() { return statusDesc; }
    public void setStatusDesc(String statusDesc) { this.statusDesc = statusDesc; }

    public Integer getPackCount() { return packCount; }
    public void setPackCount(Integer packCount) { this.packCount = packCount; }
}
