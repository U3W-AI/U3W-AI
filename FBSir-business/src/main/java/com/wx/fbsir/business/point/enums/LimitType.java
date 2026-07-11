package com.wx.fbsir.business.point.enums;

/**
 * 限频类型枚举
 * 
 * @author FBSir
 * @date 2025-12-08
 */
public enum LimitType {
    /** 每日限频 */
    DAILY("DAILY", "每日"),
    /** 每周限频 */
    WEEKLY("WEEKLY", "每周"),
    /** 每月限频 */
    MONTHLY("MONTHLY", "每月"),
    /** 总计限频 */
    TOTAL("TOTAL", "总计");
    
    private final String code;
    private final String name;
    
    LimitType(String code, String name) {
        this.code = code;
        this.name = name;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getName() {
        return name;
    }
    
    public static LimitType getByCode(String code) {
        for (LimitType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}

