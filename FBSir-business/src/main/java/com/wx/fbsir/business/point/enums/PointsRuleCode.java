package com.wx.fbsir.business.point.enums;

/**
 * 积分规则编码枚举
 * 规则编码作为唯一标识，用于业务逻辑匹配
 * 
 * @author FBSir
 * @date 2025-12-08
 */
public enum PointsRuleCode {
    /** 每日登录 */
    DAILY_LOGIN("DAILY_LOGIN", "每日登录", 10),
    /** 首次登录奖励 */
    FIRST_LOGIN_BONUS("FIRST_LOGIN_BONUS", "首次登录奖励", 5000),
    /** 使用日更助手 */
    USE_DAILY_ASSISTANT("USE_DAILY_ASSISTANT", "使用日更助手", -1),
    /** 模板上架 */
    TEMPLATE_PUBLISH("TEMPLATE_PUBLISH", "模板上架", 50),
    /** 模板购买 */
    TEMPLATE_BUY("TEMPLATE_BUY", "模板购买", -10),
    /** 模板分成 */
    TEMPLATE_REWARD("TEMPLATE_REWARD", "模板分成", 8),
    /** 充值赠送 */
    RECHARGE("RECHARGE", "充值赠送", 100),
    /** 使用Gitee AI助手 */
    USE_GITEE_AI("USE_GITEE_AI", "使用Gitee AI助手", -1);
    
    /** 规则编码（唯一标识） */
    private final String code;
    /** 规则名称（用于显示） */
    private final String name;
    /** 默认积分值 */
    private final Integer defaultPoints;
    
    PointsRuleCode(String code, String name, Integer defaultPoints) {
        this.code = code;
        this.name = name;
        this.defaultPoints = defaultPoints;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getName() {
        return name;
    }
    
    public Integer getDefaultPoints() {
        return defaultPoints;
    }
    
    /**
     * 根据编码获取枚举
     */
    public static PointsRuleCode getByCode(String code) {
        for (PointsRuleCode ruleCode : values()) {
            if (ruleCode.code.equals(code)) {
                return ruleCode;
            }
        }
        return null;
    }
}

