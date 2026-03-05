package com.wx.fbsir.business.certificate.domain;

import lombok.Data;

/**
 * 认证积分规则实体类（使用现有的wx_points_rule表）
 */
@Data
public class CertPointsRule {

    /**
     * 规则ID
     */
    private Long ruleId;

    /**
     * 规则编码（唯一标识，用于业务索引）
     */
    private String ruleCode;

    /**
     * 规则名称（用于显示，可修改）
     */
    private String ruleName;

    /**
     * 积分值（正数为奖励，负数为扣减）
     */
    private Integer pointsValue;

    /**
     * 限频类型：DAILY/WEEKLY/MONTHLY/TOTAL
     */
    private String limitType;

    /**
     * 限频次数
     */
    private Integer limitValue;

    /**
     * 累计上限
     */
    private Integer maxAmount;

    /**
     * 状态（0正常 1停用）
     */
    private String status;

    /**
     * 排序
     */
    private Integer sortOrder;

    /**
     * 业务类型（用于兼容性）
     */
    private String businessType;

    /**
     * 扣除积分数（用于兼容性，等于pointsValue的绝对值）
     */
    public Integer getDeductPoints() {
        if (pointsValue != null && pointsValue < 0) {
            return Math.abs(pointsValue); // 返回负数的绝对值（扣减值）
        }
        return pointsValue; // 如果是正数，则直接返回
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }
}