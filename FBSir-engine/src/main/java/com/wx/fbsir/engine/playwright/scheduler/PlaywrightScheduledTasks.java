package com.wx.fbsir.engine.playwright.scheduler;

import com.wx.fbsir.engine.playwright.config.PlaywrightProperties;
import com.wx.fbsir.engine.playwright.core.PlaywrightManager;
import com.wx.fbsir.engine.playwright.util.ScreenshotUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Playwright 定时任务
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 核心职责
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 1. 定期清理过期截图文件
 * 2. 定期清理僵尸进程
 * 3. 定期清理浏览器锁文件
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 解决老项目问题
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * - 截图文件堆积：定期清理超过24小时的截图
 * - 僵尸进程：定期检测并清理
 * - 锁文件残留：定期清理
 * 
 * @author FBSir
 * @date 2025-12-18
 */
@Component
public class PlaywrightScheduledTasks {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightScheduledTasks.class);

    private final PlaywrightProperties properties;
    private final PlaywrightManager playwrightManager;
    private final ScreenshotUtil screenshotUtil;

    public PlaywrightScheduledTasks(PlaywrightProperties properties,
                                     PlaywrightManager playwrightManager,
                                     ScreenshotUtil screenshotUtil) {
        this.properties = properties;
        this.playwrightManager = playwrightManager;
        this.screenshotUtil = screenshotUtil;
    }

    /**
     * 清理过期截图（每小时执行）
     * 支持失败重试，静默处理小问题
     */
    @Scheduled(fixedRate = 3600000)
    public void cleanupOldScreenshots() {
        if (!properties.isEnabled()) {
            return;
        }
        
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            try {
                int cleaned = screenshotUtil.cleanupOldScreenshots(24);
                if (cleaned > 0) {
                    log.debug("[定时任务] 清理截图成功 - 数量: {}", cleaned);
                }
                return; // 成功，退出
            } catch (Exception e) {
                if (i == maxRetries - 1) {
                    // 最后一次失败才告警
                    log.warn("[定时任务] 清理截图失败 - 已重试 {} 次: {}", maxRetries, e.getMessage());
                } else {
                    log.debug("[定时任务] 清理截图失败，重试中 ({}/{})", i + 1, maxRetries);
                    try { Thread.sleep(1000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
        }
    }

    /**
     * 清理僵尸进程（每10分钟执行）
     * 支持失败重试，静默处理小问题
     */
    @Scheduled(fixedRate = 600000)
    public void cleanupZombieProcesses() {
        if (!properties.isEnabled()) {
            return;
        }
        
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            try {
                playwrightManager.cleanupZombieProcesses();
                return; // 成功，退出
            } catch (Exception e) {
                if (i == maxRetries - 1) {
                    log.warn("[定时任务] 清理僵尸进程失败 - 已重试 {} 次: {}", maxRetries, e.getMessage());
                } else {
                    log.debug("[定时任务] 清理僵尸进程失败，重试中 ({}/{})", i + 1, maxRetries);
                    try { Thread.sleep(1000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
        }
    }
}
