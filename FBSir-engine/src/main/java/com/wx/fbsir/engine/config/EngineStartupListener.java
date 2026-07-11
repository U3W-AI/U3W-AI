package com.wx.fbsir.engine.config;

import com.wx.fbsir.engine.capability.EngineCapabilityManager;
import com.wx.fbsir.engine.playwright.config.PlaywrightProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Engine 启动监听器
 * 
 * 在应用启动时执行初始化检查
 *
 * @author FBSir
 * @date 2025-12-18
 */
@Component
@Order(1)
public class EngineStartupListener implements ApplicationListener<ApplicationStartedEvent> {

    private static final Logger log = LoggerFactory.getLogger(EngineStartupListener.class);

    @Autowired(required = false)
    private PlaywrightProperties playwrightProperties;

    @Autowired(required = false)
    private EngineCapabilityManager capabilityManager;

    @Autowired
    private EngineProperties engineProperties;

    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        // 打印简洁启动信息
        printStartupInfo();
    }

    private void printStartupInfo() {
        int capCount = capabilityManager != null ? capabilityManager.getCapabilityCount() : 0;
        String browserVersion = "已安装";
        
        log.info("┌─────────────────────────────────────────────┐");
        log.info("│           FBSir Engine 启动完成            │");
        log.info("├─────────────────────────────────────────────┤");
        log.info("│  主机ID: {}", padRight(engineProperties.getHostId(), 33) + "│");
        log.info("│  版本: {}", padRight(engineProperties.getVersion(), 35) + "│");
        log.info("│  能力数: {}", padRight(String.valueOf(capCount), 33) + "│");
        log.info("│  浏览器: Chromium {}", padRight(browserVersion, 22) + "│");
        log.info("│  主节点: {}", padRight(truncate(engineProperties.getWsUrl(), 32), 33) + "│");
        log.info("└─────────────────────────────────────────────┘");
    }

    private String padRight(String s, int n) {
        if (s == null) s = "";
        if (s.length() >= n) return s.substring(0, n);
        return s + " ".repeat(n - s.length());
    }

    private String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() > n ? s.substring(0, n - 3) + "..." : s;
    }
}
