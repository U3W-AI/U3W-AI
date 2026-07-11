package com.wx.fbsir.business.fbs.dto.business.user_pack;

import java.util.Date;

/**
 * 用户-场景包详情响应
 *
 * @author FBSir
 * @date 2026-04-08
 */
public class UserPackDetailResponse {

    private Long id;
    private Long userId;
    private Long packId;
    private String packName;       // JOIN fbs_scene_pack 获取
    private String packVersion;
    private Long authCodeId;
    private Date activatedAt;
    private Date expiresAt;
    private Integer status;         // 1=有效, 2=已过期, 3=已撤销
    private String statusDesc;
    private Integer sourceType;     // 1=平台分发, 2=企业分发, 3=用户激活
    private String sourceTypeDesc;
    private String createdBy;
    private String createTime;

    // ===== getter / setter =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getPackId() { return packId; }
    public void setPackId(Long packId) { this.packId = packId; }

    public String getPackName() { return packName; }
    public void setPackName(String packName) { this.packName = packName; }

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

    public String getStatusDesc() { return statusDesc; }
    public void setStatusDesc(String statusDesc) { this.statusDesc = statusDesc; }

    public Integer getSourceType() { return sourceType; }
    public void setSourceType(Integer sourceType) { this.sourceType = sourceType; }

    public String getSourceTypeDesc() { return sourceTypeDesc; }
    public void setSourceTypeDesc(String sourceTypeDesc) { this.sourceTypeDesc = sourceTypeDesc; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
}
