package com.wx.fbsir.business.fbs.dto.business.auth_code;

import java.util.Date;

/**
 * 授权码详情响应
 *
 * @author FBSir
 * @date 2026-04-08
 */
public class AuthCodeDetailResponse {

    private Long id;
    private String authCode;
    private Integer codeType;
    private String targetType;
    private Long targetId;
    private String targetName;   // 场景包名称（JOIN获取）
    private Integer issuerType;
    private Long issuerId;
    private Integer available;      // 0=禁用, 1=启用
    private String availableDesc;
    private Integer status;         // 0=未激活, 1=已激活, 2=已用尽, 3=已过期, 4=已撤销
    private String statusDesc;
    private Date deadline;
    private Integer maxActivations;
    private Integer activatedCount;
    private String description;
    private String createdBy;
    private String createTime;

    // ===== getter / setter =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAuthCode() { return authCode; }
    public void setAuthCode(String authCode) { this.authCode = authCode; }

    public Integer getCodeType() { return codeType; }
    public void setCodeType(Integer codeType) { this.codeType = codeType; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }

    public String getTargetName() { return targetName; }
    public void setTargetName(String targetName) { this.targetName = targetName; }

    public Integer getIssuerType() { return issuerType; }
    public void setIssuerType(Integer issuerType) { this.issuerType = issuerType; }

    public Long getIssuerId() { return issuerId; }
    public void setIssuerId(Long issuerId) { this.issuerId = issuerId; }

    public Integer getAvailable() { return available; }
    public void setAvailable(Integer available) { this.available = available; }

    public String getAvailableDesc() { return availableDesc; }
    public void setAvailableDesc(String availableDesc) { this.availableDesc = availableDesc; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public String getStatusDesc() { return statusDesc; }
    public void setStatusDesc(String statusDesc) { this.statusDesc = statusDesc; }

    public Date getDeadline() { return deadline; }
    public void setDeadline(Date deadline) { this.deadline = deadline; }

    public Integer getMaxActivations() { return maxActivations; }
    public void setMaxActivations(Integer maxActivations) { this.maxActivations = maxActivations; }

    public Integer getActivatedCount() { return activatedCount; }
    public void setActivatedCount(Integer activatedCount) { this.activatedCount = activatedCount; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
}
