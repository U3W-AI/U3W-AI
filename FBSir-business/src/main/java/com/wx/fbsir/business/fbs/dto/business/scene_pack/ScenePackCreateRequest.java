package com.wx.fbsir.business.fbs.dto.business.scene_pack;

/**
 * 场景包创建请求
 *
 * @author FBSir
 * @date 2026-04-08
 */
public class ScenePackCreateRequest {

    /** 场景包编码（业务唯一键） */
    private String packCode;

    /** 场景包名称 */
    private String packName;

    /** 场景包类型：1=平台包, 2=企业包, 3=自定义包 */
    private Integer packType;

    /** 所属者类型：1=平台, 2=企业, 3=个人 */
    private Integer ownerType;

    /** 所属者ID（ownerType=1时为NULL） */
    private Long ownerId;

    /** 场景包描述 */
    private String description;

    /** 可见范围：ALL/PRIVATE */
    private String visibleScope;

    /** 关联积分规则编码（NULL=免费包） */
    private String pointsRuleCode;

    /** 内容快照JSON */
    private String contentSnapshot;

    // ===== getter / setter =====

    public String getPackCode() { return packCode; }
    public void setPackCode(String packCode) { this.packCode = packCode; }

    public String getPackName() { return packName; }
    public void setPackName(String packName) { this.packName = packName; }

    public Integer getPackType() { return packType; }
    public void setPackType(Integer packType) { this.packType = packType; }

    public Integer getOwnerType() { return ownerType; }
    public void setOwnerType(Integer ownerType) { this.ownerType = ownerType; }

    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getVisibleScope() { return visibleScope; }
    public void setVisibleScope(String visibleScope) { this.visibleScope = visibleScope; }

    public String getPointsRuleCode() { return pointsRuleCode; }
    public void setPointsRuleCode(String pointsRuleCode) { this.pointsRuleCode = pointsRuleCode; }

    public String getContentSnapshot() { return contentSnapshot; }
    public void setContentSnapshot(String contentSnapshot) { this.contentSnapshot = contentSnapshot; }
}
