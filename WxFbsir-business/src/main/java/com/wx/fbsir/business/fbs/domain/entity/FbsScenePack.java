package com.wx.fbsir.business.fbs.domain.entity;

import com.wx.fbsir.common.core.domain.BaseEntity;

import java.util.Date;

/**
 * 场景包主表实体 fbs_scene_pack
 *
 * 版本管理简化：MVP 阶段不建 fbs_pack_version，用 currentVersion + contentSnapshot 代替。
 * points_rule_code = NULL 表示免费包，跳过积分校验和扣减。
 *
 * @author wxfbsir
 * @date 2026-04-08
 */
public class FbsScenePack extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 场景包编码（全局唯一业务键） */
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

    /**
     * 状态：0=草稿, 1=已发布, 2=已下架
     * @see com.wx.fbsir.business.fbs.domain.enums.PackStatus
     */
    private Integer status;

    /** 可见范围：ALL/PRIVATE */
    private String visibleScope;

    /**
     * 关联积分规则编码（NULL=免费包，跳过积分校验和扣减）
     * 对应 wx_points_rule.rule_code
     */
    private String pointsRuleCode;

    /** 内容快照JSON（MVP 版本管理简化方案） */
    private String contentSnapshot;

    /** 当前版本号（默认1.0.0） */
    private String currentVersion;

    /** 创建者 */
    private String createdBy;

    /** 更新者 */
    private String updatedBy;

    /** 更新时间 */
    private Date updateTime;

    /** 删除标志（0=存在, 2=删除） */
    private String delFlag;

    // ========== getter / setter ==========

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public String getVisibleScope() { return visibleScope; }
    public void setVisibleScope(String visibleScope) { this.visibleScope = visibleScope; }

    public String getPointsRuleCode() { return pointsRuleCode; }
    public void setPointsRuleCode(String pointsRuleCode) { this.pointsRuleCode = pointsRuleCode; }

    public String getContentSnapshot() { return contentSnapshot; }
    public void setContentSnapshot(String contentSnapshot) { this.contentSnapshot = contentSnapshot; }

    public String getCurrentVersion() { return currentVersion; }
    public void setCurrentVersion(String currentVersion) { this.currentVersion = currentVersion; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }

    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
}
