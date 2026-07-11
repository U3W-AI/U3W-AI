package com.wx.fbsir.business.fbs.dto.self;

import java.io.Serializable;
import java.util.Date;

/**
 * 可领取场景包列表项DTO
 *
 * @author FBSir
 * @date 2026-04-10
 */
public class MyScenePackItemDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 场景包ID */
    private Long id;

    /** 场景包编码 */
    private String packCode;

    /** 场景包名称 */
    private String packName;

    /** 当前版本号 */
    private String currentVersion;

    /** 描述 */
    private String description;

    /** 积分规则编码（null=免费） */
    private String pointsRuleCode;

    /** 创建时间 */
    private Date createTime;

    /** 是否已领取 */
    private Boolean claimed;

    // ========== getter / setter ==========

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPackCode() { return packCode; }
    public void setPackCode(String packCode) { this.packCode = packCode; }

    public String getPackName() { return packName; }
    public void setPackName(String packName) { this.packName = packName; }

    public String getCurrentVersion() { return currentVersion; }
    public void setCurrentVersion(String currentVersion) { this.currentVersion = currentVersion; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getPointsRuleCode() { return pointsRuleCode; }
    public void setPointsRuleCode(String pointsRuleCode) { this.pointsRuleCode = pointsRuleCode; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Boolean getClaimed() { return claimed; }
    public void setClaimed(Boolean claimed) { this.claimed = claimed; }
}
