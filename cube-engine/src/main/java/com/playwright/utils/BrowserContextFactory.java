package com.playwright.utils;

import com.microsoft.playwright.*;
import com.playwright.entity.UnPersisBrowserContextInfo;

import java.util.*;

/**
 * dateStart 2024/8/4 9:34
 * dateNow   2025/8/4 9:27
 */
public class BrowserContextFactory {
    public static final Map<String, UnPersisBrowserContextInfo> map = new HashMap<>();
    private static final Playwright playwright = Playwright.create();
    private static Integer CONTEXT_COUNT = 0;
    private static final Integer MAX_CONTEXT_COUNT = 20;

    /**
     * @param key   userId
     * @param count 页面数量
     * @return 浏览器上下文信息
     */
    public static UnPersisBrowserContextInfo getBrowserContext(String key, int count) {
        UnPersisBrowserContextInfo unPersisBrowserContextInfo = map.get(key);
        if (unPersisBrowserContextInfo == null) {
            try {
                synchronized (BrowserContextFactory.class) {
                    if(CONTEXT_COUNT >= MAX_CONTEXT_COUNT) {
                        //关闭最久的上下文
                        closeLongestUsed();
                    }
                    // 启动 Chromium 浏览器（headless=false 显示窗口，方便调试）
                    Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                            .setHeadless(false)
                    );
                    // 创建浏览器上下文（相当于新的隐身窗口）
                    BrowserContext browserContext = browser.newContext(new Browser.NewContextOptions()
                            .setPermissions(Arrays.asList(
                                    "clipboard-read",  // 允许读取剪贴板
                                    "clipboard-write"  // 允许写入剪贴板
                            )));
                    // 打开新页面
                    for (int i = 0; i < count; i++) {
                        Page page = browserContext.newPage();
                    }
                    // 存入信息
                    unPersisBrowserContextInfo = new UnPersisBrowserContextInfo();
                    unPersisBrowserContextInfo.setUserId(key);
                    unPersisBrowserContextInfo.setBrowserContext(browserContext);
                    unPersisBrowserContextInfo.setExpireTime(System.currentTimeMillis() + UnPersisBrowserContextInfo.ExpireTime.DAY_EXPIRE_TIME);
                    map.put(key, unPersisBrowserContextInfo);
                    CONTEXT_COUNT++;
                }
                return unPersisBrowserContextInfo;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return unPersisBrowserContextInfo;
    }
    // 检查过期时间
    public static void closeExpireData(String key) {
        UnPersisBrowserContextInfo unPersisBrowserContextInfo = map.get(key);
        if (unPersisBrowserContextInfo != null) {
            BrowserContext browserContext = unPersisBrowserContextInfo.getBrowserContext();
            if (browserContext == null) {
                map.remove(key);
                return;
            }
//            处理过期上下文
            if (System.currentTimeMillis() > unPersisBrowserContextInfo.getExpireTime()) {
                System.out.println(key + "浏览器上下文已过期，重新创建");
                browserContext.close();
                map.remove(key);
            }
            System.out.println(key + "浏览器上下文未过期");
        }
    }
    // 关闭存活最久的上下文
    public static void closeLongestUsed() {
        Set<String> set = map.keySet();
        String suvMinKey = "";
        long suvMinTime = Long.MAX_VALUE;
        for (String key : set) {
            UnPersisBrowserContextInfo unPersisBrowserContextInfo = map.get(key);
            if (unPersisBrowserContextInfo != null) {
                Long expireTime = unPersisBrowserContextInfo.getExpireTime() - System.currentTimeMillis();
                if(expireTime < suvMinTime) {
                    suvMinTime = expireTime;
                    suvMinKey = key;
                }
            }
        }
        if(!Objects.equals(suvMinKey, "")) {
            UnPersisBrowserContextInfo unPersisBrowserContextInfo = map.get(suvMinKey);
            BrowserContext browserContext = unPersisBrowserContextInfo.getBrowserContext();
            if(browserContext != null) {
                browserContext.close();
            }
            map.remove(suvMinKey);
        }
    }
}
