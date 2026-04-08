package com.wx.fbsir.business.fbs.dto;

/**
 * 场景包数据传输对象（供 Controller 层输入/输出使用）
 *
 * @author wxfbsir
 * @date 2026-04-08
 */
public class ScenePackDTO {

    // ===== 创建时输入字段 =====

    /** 场景包编码（全局唯一） */
    private String packCode;

    /** 场景包名称 */
    private String packName;

    /** 场景包类型：1=平台包, 2=企业包, 3=自定义包 */
    private Integer packType;

    /** 所属者类型：1=平台, 2=企业, 3=个人 */
    private Integer ownerType;

    /** 所属者ID */
    private Long ownerId;

    /** 描述 */
    private String description;

    /** 关联积分规则编码（null=免费包） */
    private String pointsRuleCode;

    /** 可见范围：ALL/PRIVATE */
    private String visibleScope;

    // ===== 查询时输出字段 =====

    /** 主键 */
    private Long id;

    /** 状态：0=草稿, 1=已发布, 2=已下架 */
    private Integer status;

    /** 当前版本号 */
    private String currentVersion;

    // ========== getter / setter ==========

    public String getPackCode()          { return packCode; }
    public void setPackCode(String v)    { this.packCode = v; }

    public String getPackName()          { return packName; }
    public void setPackName(String v)    { this.packName = v; }

    public Integer getPackType()         { return packType; }
    public void setPackType(Integer v)   { this.packType = v; }

    public Integer getOwnerType()        { return ownerType; }
    public void setOwnerType(Integer v)  { this.ownerType = v; }

    public Long getOwnerId()             { return ownerId; }
    public void setOwnerId(Long v)       { this.ownerId = v; }

    public String getDescription()       { return description; }
    public void setDescription(String v) { this.description = v; }

    public String getPointsRuleCode()           { return pointsRuleCode; }
    public void setPointsRuleCode(String v)     { this.pointsRuleCode = v; }

    public String getVisibleScope()             { return visibleScope; }
    public void setVisibleScope(String v)       { this.visibleScope = v; }

    public Long getId()                  { return id; }
    public void setId(Long v)            { this.id = v; }

    public Integer getStatus()           { return status; }
    public void setStatus(Integer v)     { this.status = v; }

    public String getCurrentVersion()          { return currentVersion; }
    public void setCurrentVersion(String v)    { this.currentVersion = v; }
}
