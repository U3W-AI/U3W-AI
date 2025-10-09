package com.playwright.entity;

import com.microsoft.playwright.BrowserContext;
import lombok.Data;


/**
 * dateStart 2024/8/4 9:34
 * dateNow   2025/8/5 10:30
 */
@Data
// 浏览器上下文信息
public class UnPersisBrowserContextInfo {
    // 用户唯一标识
    private String userId;
    // 过期时间
    private Long expireTime;
    // 浏览器上下文
    private BrowserContext browserContext;

    // 过期时间常量类
    public static class ExpireTime {
        public static final Long TEST = 60 * 1000L;
        // 一天
        public static final Long DAY_EXPIRE_TIME = 24 * 60 * 60 * 1000L;
        //一周
        public static final Long WEEK_EXPIRE_TIME = 7 * 24 * 60 * 60 * 1000L;
        // 一个月
        public static final Long MONTH_EXPIRE_TIME = 30 * 24 * 60 * 60 * 1000L;
    }
}
