package com.wx.fbsir.business.point.domain;

import com.wx.fbsir.common.core.domain.BaseEntity;

/**
 * 积分规则对象 wx_points_rule
 * 
 * @author FBSir
 * @date 2025-12-08
 */
public class PointsRule extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 规则ID */
    private Long ruleId;

    /** 规则编码（唯一标识，用于业务索引） */
    private String ruleCode;

    /** 规则名称（用于显示，可修改） */
    private String ruleName;

    /** 积分值（正数为奖励，负数为扣减） */
    private Integer pointsValue;

    /** 限频类型：DAILY/WEEKLY/MONTHLY/TOTAL */
    private String limitType;

    /** 限频次数 */
    private Integer limitValue;

    /** 累计上限 */
    private Integer maxAmount;

    /** 状态（0正常 1停用） */
    private String status;

    /** 排序 */
    private Integer sortOrder;

    public void setRuleId(Long ruleId) {
        this.ruleId = ruleId;
    }

    public Long getRuleId() {
        return ruleId;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setPointsValue(Integer pointsValue) {
        this.pointsValue = pointsValue;
    }

    public Integer getPointsValue() {
        return pointsValue;
    }

    public void setLimitType(String limitType) {
        this.limitType = limitType;
    }

    public String getLimitType() {
        return limitType;
    }

    public void setLimitValue(Integer limitValue) {
        this.limitValue = limitValue;
    }

    public Integer getLimitValue() {
        return limitValue;
    }

    public void setMaxAmount(Integer maxAmount) {
        this.maxAmount = maxAmount;
    }

    public Integer getMaxAmount() {
        return maxAmount;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }
}

