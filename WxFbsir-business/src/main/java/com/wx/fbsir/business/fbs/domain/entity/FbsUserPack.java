package com.wx.fbsir.business.fbs.domain.entity;

import com.wx.fbsir.common.core.domain.BaseEntity;

import java.util.Date;

/**
 * 用户场景包关系表实体 fbs_user_pack
 *
 * 代表用户已获得某场景包的使用权益。
 * source_type 说明权益来源：1=平台分发, 2=企业分发, 3=用户激活（通过授权码）
 *
 * @author wxfbsir
 * @date 2026-04-08
 */
public class FbsUserPack extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 场景包ID（关联 fbs_scene_pack.id） */
    private Long packId;

    /** 激活时的版本号（快照，对应 fbs_scene_pack.current_version） */
    private String packVersion;

    /** 激活时使用的授权码ID（source_type=3 时有值） */
    private Long authCodeId;

    /** 激活时间 */
    private Date activatedAt;

    /** 过期时间，NULL=永不过期 */
    private Date expiresAt;

    /**
     * 权益状态：1=有效, 2=已过期, 3=已撤销
     */
    private Integer status;

    /**
     * 来源类型：1=平台分发, 2=企业分发, 3=用户激活
     */
    private Integer sourceType;

    /** 创建者 */
    private String createdBy;

    /** 更新者 */
    private String updatedBy;

    /** 删除标志（0=存在, 2=删除） */
    private String delFlag;

    // ========== getter / setter ==========

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getPackId() { return packId; }
    public void setPackId(Long packId) { this.packId = packId; }

    public String getPackVersion() { return packVersion; }
    public void setPackVersion(String packVersion) { this.packVersion = packVersion; }

    public Long getAuthCodeId() { return authCodeId; }
    public void setAuthCodeId(Long authCodeId) { this.authCodeId = authCodeId; }

    public Date getActivatedAt() { return activatedAt; }
    public void setActivatedAt(Date activatedAt) { this.activatedAt = activatedAt; }

    public Date getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Date expiresAt) { this.expiresAt = expiresAt; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public Integer getSourceType() { return sourceType; }
    public void setSourceType(Integer sourceType) { this.sourceType = sourceType; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
}
