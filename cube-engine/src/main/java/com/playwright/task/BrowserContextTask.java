package com.playwright.task;

import com.playwright.entity.UnPersisBrowserContextInfo;
import com.playwright.utils.BrowserContextFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * dateStart 2024/8/4 9:34
 * dateNow   2025/8/5 11:50
 */
@Component
public class BrowserContextTask {
//    @Scheduled(cron = "0/10 * * * * ?") // 十秒钟一次，测试
    @Scheduled(cron = "0 0 * * * ?") // 一小时执行一次检查
    public void closeBrowserContext() {
        // 定时关闭浏览器上下文
        Map<String, UnPersisBrowserContextInfo> map = BrowserContextFactory.map;
        Set<String> set = map.keySet();
        for (String key : set) {
            BrowserContextFactory.closeExpireData(key + "-yb");
        }
    }
}
