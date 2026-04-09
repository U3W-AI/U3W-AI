package com.wx.fbsir.business.fbs.dto.business.scene_pack;

/**
 * 场景包编辑请求
 *
 * @author wxfbsir
 * @date 2026-04-08
 */
public class ScenePackUpdateRequest {

    /** 主键 */
    private Long id;

    /** 场景包名称 */
    private String packName;

    /** 场景包描述 */
    private String description;

    /** 可见范围：ALL/PRIVATE */
    private String visibleScope;

    /** 关联积分规则编码（NULL=免费包） */
    private String pointsRuleCode;

    /** 内容快照JSON */
    private String contentSnapshot;

    // ===== getter / setter =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPackName() { return packName; }
    public void setPackName(String packName) { this.packName = packName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getVisibleScope() { return visibleScope; }
    public void setVisibleScope(String visibleScope) { this.visibleScope = visibleScope; }

    public String getPointsRuleCode() { return pointsRuleCode; }
    public void setPointsRuleCode(String pointsRuleCode) { this.pointsRuleCode = pointsRuleCode; }

    public String getContentSnapshot() { return contentSnapshot; }
    public void setContentSnapshot(String contentSnapshot) { this.contentSnapshot = contentSnapshot; }
}
