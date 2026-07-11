package com.wx.fbsir.engine.playwright.core;

import com.microsoft.playwright.Playwright;
import com.wx.fbsir.engine.playwright.config.PlaywrightProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Playwright 启动初始化器
 * 
 * 在应用启动时验证 Playwright 是否可用，如果未安装则自动下载
 * 确保 WebSocket 连接前 Playwright 已就绪
 *
 * @author FBSir
 * @date 2025-12-19
 */
@Component
@Order(1) // 确保在 WebSocket 连接前执行
public class PlaywrightInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightInitializer.class);

    private final PlaywrightManager playwrightManager;
    private final PlaywrightProperties properties;

    public PlaywrightInitializer(PlaywrightManager playwrightManager, 
                                   PlaywrightProperties properties) {
        this.playwrightManager = playwrightManager;
        this.properties = properties;
    }

    @Override
    public void run(String... args) {
        if (!properties.isEnabled()) {
            log.debug("[Playwright初始化] 功能已禁用，跳过验证");
            return;
        }

        try {
            long startTime = System.currentTimeMillis();
            
            // 尝试创建 Playwright 实例，如果浏览器未安装会自动下载
            Playwright playwright = playwrightManager.getPlaywright();
            
            long duration = System.currentTimeMillis() - startTime;
            
            log.info("[Playwright初始化] ✅ 验证成功 - 耗时: {}ms, 浏览器: Chromium, 数据目录: {}", duration, properties.getDataDir());
            
        } catch (Exception e) {
            log.error("[Playwright初始化] ❌ 验证失败 - 错误: {}", e.getMessage(), e);
            log.warn("[Playwright初始化] 应用将继续启动，但 Playwright 功能不可用");
        }
    }
}
