package com.wx.fbsir.business.fbs.dto.business.scene_pack;

/**
 * 场景包详情响应（含积分规则名、用户数统计）
 *
 * @author wxfbsir
 * @date 2026-04-08
 */
public class ScenePackDetailResponse {

    private Long id;
    private String packCode;
    private String packName;
    private Integer packType;
    private String packTypeDesc;
    private Integer ownerType;
    private Long ownerId;
    private String description;
    private Integer status;
    private String statusDesc;
    private String visibleScope;
    private String pointsRuleCode;
    private String pointsRuleName;
    private String contentSnapshot;
    private String currentVersion;
    private Integer userCount;   // 关联用户数（本期暂不查，后端留字段）
    private String createdBy;
    private String createTime;

    // ===== getter / setter =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPackCode() { return packCode; }
    public void setPackCode(String packCode) { this.packCode = packCode; }

    public String getPackName() { return packName; }
    public void setPackName(String packName) { this.packName = packName; }

    public Integer getPackType() { return packType; }
    public void setPackType(Integer packType) { this.packType = packType; }

    public String getPackTypeDesc() { return packTypeDesc; }
    public void setPackTypeDesc(String packTypeDesc) { this.packTypeDesc = packTypeDesc; }

    public Integer getOwnerType() { return ownerType; }
    public void setOwnerType(Integer ownerType) { this.ownerType = ownerType; }

    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public String getStatusDesc() { return statusDesc; }
    public void setStatusDesc(String statusDesc) { this.statusDesc = statusDesc; }

    public String getVisibleScope() { return visibleScope; }
    public void setVisibleScope(String visibleScope) { this.visibleScope = visibleScope; }

    public String getPointsRuleCode() { return pointsRuleCode; }
    public void setPointsRuleCode(String pointsRuleCode) { this.pointsRuleCode = pointsRuleCode; }

    public String getPointsRuleName() { return pointsRuleName; }
    public void setPointsRuleName(String pointsRuleName) { this.pointsRuleName = pointsRuleName; }

    public String getContentSnapshot() { return contentSnapshot; }
    public void setContentSnapshot(String contentSnapshot) { this.contentSnapshot = contentSnapshot; }

    public String getCurrentVersion() { return currentVersion; }
    public void setCurrentVersion(String currentVersion) { this.currentVersion = currentVersion; }

    public Integer getUserCount() { return userCount; }
    public void setUserCount(Integer userCount) { this.userCount = userCount; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
}
