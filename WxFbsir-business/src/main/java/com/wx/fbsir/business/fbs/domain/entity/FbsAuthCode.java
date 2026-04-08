package com.wx.fbsir.business.fbs.domain.entity;

import com.wx.fbsir.common.core.domain.BaseEntity;

import java.util.Date;

/**
 * 授权码表实体 fbs_auth_code
 *
 * 注意：available（启用状态）与 status（使用状态）是两个独立维度：
 *   - available：管理层面开关，0=禁用（任何人无法使用），1=启用
 *   - status：使用状态，详见 AuthCodeStatus 枚举
 *
 * 激活状态机：
 *   - 可激活：available=1 AND status IN(0,1) AND activated_count < max_activations AND 未过期
 *   - 首次激活后：status → 1，activated_count + 1
 *   - activated_count >= max_activations：status → 2
 *
 * @author wxfbsir
 * @date 2026-04-08
 */
public class FbsAuthCode extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 授权码字符串（唯一） */
    private String authCode;

    /** 授权码类型：1=场景包权益码, 2=通用授权码 */
    private Integer codeType;

    /** 关联目标类型：SCENE_PACK/GENERIC */
    private String targetType;

    /** 关联目标ID（如场景包ID） */
    private Long targetId;

    /** 发放者类型：1=平台, 2=企业, 3=用户 */
    private Integer issuerType;

    /** 发放者ID */
    private Long issuerId;

    /**
     * 启用状态（管理层面开关，独立于 status）：
     * 0=禁用（任何人无法使用），1=启用
     */
    private Integer available;

    /**
     * 使用状态：0=未激活, 1=已激活, 2=已用尽, 3=已过期, 4=已撤销
     * @see com.wx.fbsir.business.fbs.domain.enums.AuthCodeStatus
     */
    private Integer status;

    /** 截止时间，NULL=不限 */
    private Date deadline;

    /** 最大激活次数（默认1） */
    private Integer maxActivations;

    /** 已激活次数（默认0） */
    private Integer activatedCount;

    /** 说明/备注 */
    private String description;

    /** 创建者 */
    private String createdBy;

    /** 更新者 */
    private String updatedBy;

    /** 删除标志（0=存在, 2=删除） */
    private String delFlag;

    // ========== getter / setter ==========

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

    public Integer getIssuerType() { return issuerType; }
    public void setIssuerType(Integer issuerType) { this.issuerType = issuerType; }

    public Long getIssuerId() { return issuerId; }
    public void setIssuerId(Long issuerId) { this.issuerId = issuerId; }

    public Integer getAvailable() { return available; }
    public void setAvailable(Integer available) { this.available = available; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

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

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
}
