package com.wx.fbsir.business.point.domain;

import com.wx.fbsir.common.core.domain.BaseEntity;

/**
 * 积分明细记录对象 wx_points_record
 * 
 * @author wxfbsir
 * @date 2025-12-08
 */
public class PointsRecord extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 记录ID */
    private Long recordId;

    /** 用户ID */
    private Long userId;

    /** 规则编码（关联wx_points_rule.rule_code） */
    private String ruleCode;

    /** 规则名称（展示用） */
    private String ruleName;

    /** 变动金额（正数为增加，负数为扣减） */
    private Integer changeAmount;

    /** 变动前余额 */
    private Integer balanceBefore;

    /** 变动后余额 */
    private Integer balanceAfter;

    /** 关联场景包ID（FBS场景包消费时写入，普通积分操作为NULL） */
    private Long scenePackId;

    /** 关联使用记录幂等键（FBS场景包消费时写入，普通积分操作为NULL） */
    private String usageRecordId;

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public void setChangeAmount(Integer changeAmount) {
        this.changeAmount = changeAmount;
    }

    public Integer getChangeAmount() {
        return changeAmount;
    }

    public void setBalanceBefore(Integer balanceBefore) {
        this.balanceBefore = balanceBefore;
    }

    public Integer getBalanceBefore() {
        return balanceBefore;
    }

    public void setBalanceAfter(Integer balanceAfter) {
        this.balanceAfter = balanceAfter;
    }

    public Integer getBalanceAfter() {
        return balanceAfter;
    }

    public Long getScenePackId() {
        return scenePackId;
    }

    public void setScenePackId(Long scenePackId) {
        this.scenePackId = scenePackId;
    }

    public String getUsageRecordId() {
        return usageRecordId;
    }

    public void setUsageRecordId(String usageRecordId) {
        this.usageRecordId = usageRecordId;
    }
}
