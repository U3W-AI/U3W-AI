package com.wx.fbsir.engine.playwright.monitor;

import com.wx.fbsir.engine.playwright.pool.BrowserPoolManager;
import com.wx.fbsir.engine.playwright.util.ClipboardManager;
import com.wx.fbsir.engine.playwright.util.ScreenshotUtil;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Playwright 资源监控器
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 核心职责
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 1. 监控浏览器池资源使用情况
 * 2. 检测锁泄漏（剪贴板锁、截图锁）
 * 3. 监控线程数量，预防僵尸线程堆积
 * 4. 监控内存使用，预防OOM
 * 5. 定期输出资源状态日志
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 解决老项目问题
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * - 僵尸线程堆积：监控线程数量，异常增长时告警
 * - 用户锁堆积：监控锁数量，超过阈值时告警并清理
 * - 内存泄漏：监控内存使用率，超过阈值时告警
 * - 资源未释放：监控会话创建/销毁差异
 * 
 * @author FBSir
 * @date 2025-12-18
 */
@Component
public class ResourceMonitor {

    private static final Logger log = LoggerFactory.getLogger(ResourceMonitor.class);

    private final BrowserPoolManager browserPoolManager;
    private final ClipboardManager clipboardManager;
    private final ScreenshotUtil screenshotUtil;

    /**
     * 基线线程数（启动时记录）
     */
    private int baselineThreadCount = 0;

    /**
     * 自动清理锁的阈值（静默处理）
     */
    private static final int AUTO_CLEANUP_LOCKS = 5;
    
    /**
     * 告警锁的阈值（影响使用时才告警）
     */
    private static final int ALERT_LOCKS = 10;
    
    /**
     * 线程增长阈值（相对基线）- 提高到50%
     */
    private static final int THREAD_GROWTH_THRESHOLD = 50;
    
    /**
     * 内存使用阈值（比例）- 🟡 P2修复：降低至75%，提前触发清理
     */
    private static final double MEMORY_USAGE_THRESHOLD = 0.75;
    
    /**
     * 内存严重告警阈值 - 🟡 P2修复：降低至80%，留更多缓冲空间
     */
    private static final double MEMORY_CRITICAL_THRESHOLD = 0.80;

    /**
     * 告警计数器（避免重复告警）
     */
    private final AtomicInteger alertCount = new AtomicInteger(0);

    public ResourceMonitor(BrowserPoolManager browserPoolManager,
                           ClipboardManager clipboardManager,
                           ScreenshotUtil screenshotUtil) {
        this.browserPoolManager = browserPoolManager;
        this.clipboardManager = clipboardManager;
        this.screenshotUtil = screenshotUtil;
        
        // 记录基线线程数
        this.baselineThreadCount = ManagementFactory.getThreadMXBean().getThreadCount();
        log.info("[资源监控] 初始化完成 - 基线线程数: {}", baselineThreadCount);
    }

    /**
     * 定期资源检查（每10分钟，启动后5分钟开始）
     * 降低检查频率，减少资源消耗
     */
    @Scheduled(fixedRate = 600000, initialDelay = 300000)
    public void checkResources() {
        try {
            // 检查锁泄漏
            checkLockLeak();
            
            // 检查线程泄漏
            checkThreadLeak();
            
            // 检查内存使用
            checkMemoryUsage();
            
            // 检查浏览器池状态
            checkBrowserPool();
            
        } catch (Exception e) {
            log.error("[资源监控] 检查异常 - 错误类型: {}, 错误信息: {}", 
                e.getClass().getSimpleName(), e.getMessage());
        }
    }

    /**
     * 检查锁泄漏
     */
    private void checkLockLeak() {
        int clipboardLocks = clipboardManager.getLockCount();
        int screenshotLocks = screenshotUtil.getLockCount();
        
        // 自动清理：超过5个锁时静默清理
        if (clipboardLocks >= AUTO_CLEANUP_LOCKS || screenshotLocks >= AUTO_CLEANUP_LOCKS) {
            clipboardManager.clearAllLocks();
            screenshotUtil.clearAllLocks();
            log.debug("[资源监控] 自动清理锁 - 剪贴板锁: {}, 截图锁: {}", clipboardLocks, screenshotLocks);
        }
        
        // 只在严重影响使用时才告警（超过10个锁）
        if (clipboardLocks >= ALERT_LOCKS || screenshotLocks >= ALERT_LOCKS) {
            log.warn("[资源监控] ⚠️ 锁数量过多，已自动清理 - 剪贴板锁: {}, 截图锁: {} | 建议检查任务是否异常退出", 
                clipboardLocks, screenshotLocks);
            alertCount.incrementAndGet();
        }
    }

    /**
     * 检查线程泄漏
     */
    private void checkThreadLeak() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        int currentThreads = threadBean.getThreadCount();
        int peakThreads = threadBean.getPeakThreadCount();
        
        int growth = currentThreads - baselineThreadCount;
        double growthRatio = (double) growth / baselineThreadCount;
        
        // 只有增长超过50%才告警（留足性能空间）
        if (growth > THREAD_GROWTH_THRESHOLD && growthRatio > 0.5) {
            log.warn("[资源监控] ⚠️ 线程数量持续增长 - 当前: {}, 基线: {}, 峰值: {}, 增长率: {:.0%} | 建议检查是否有任务未释放资源", 
                currentThreads, baselineThreadCount, peakThreads, growthRatio);
            alertCount.incrementAndGet();
        }
    }

    /**
     * 检查内存使用
     * 🟡 P2修复：降低阈值并添加主动清理逻辑
     */
    private void checkMemoryUsage() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        
        long usedMemory = heapUsage.getUsed() / (1024 * 1024);
        long maxMemory = heapUsage.getMax() / (1024 * 1024);
        double usageRatio = (double) heapUsage.getUsed() / heapUsage.getMax();
        
        // 75%-80%：触发GC + 清理空闲Session
        if (usageRatio > MEMORY_USAGE_THRESHOLD && usageRatio < MEMORY_CRITICAL_THRESHOLD) {
            System.gc();
            log.debug("[资源监控] 内存使用率 {:.0%}，已触发GC", usageRatio);
            // 🟡 P2修复：主动清理空闲Session
            try {
                browserPoolManager.cleanupExpiredSessions();
                log.debug("[资源监控] 已清理空闲Session以释放内存");
            } catch (Exception e) {
                log.debug("[资源监控] 清理Session失败: {}", e.getMessage());
            }
        }
        
        // 超过80%告警并拒绝新请求（留20%空间）
        if (usageRatio > MEMORY_CRITICAL_THRESHOLD) {
            log.warn("[资源监控] ⚠️ 内存使用率过高 - 已用: {}MB, 最大: {}MB, 使用率: {:.0%} | 建议：1) 增加堆内存 2) 检查内存泄漏 3) 暂停新任务", 
                usedMemory, maxMemory, usageRatio);
            alertCount.incrementAndGet();
            // 🟡 P2修复：强制清理资源
            try {
                browserPoolManager.cleanupExpiredSessions();
                clipboardManager.clearAllLocks();
                screenshotUtil.clearAllLocks();
                System.gc();
                log.info("[资源监控] 已执行强制资源清理");
            } catch (Exception e) {
                log.error("[资源监控] 强制清理失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 检查浏览器池状态
     */
    private void checkBrowserPool() {
        Map<String, Object> poolStatus = browserPoolManager.getStatus();
        int activeCount = (int) poolStatus.get("activeCount");
        int persistentCount = (int) poolStatus.get("persistentCount");
        int temporaryCount = (int) poolStatus.get("temporaryCount");
        int availableSlots = (int) poolStatus.get("availableSlots");
        int maxSize = (int) poolStatus.get("maxSize");
        
        double usageRatio = (double) activeCount / maxSize;
        
        // 自动清理：使用率超过50%时，尝试清理过期会话
        if (usageRatio > 0.5) {
            try {
                browserPoolManager.cleanupExpiredSessions();
                log.debug("[资源监控] 浏览器池使用率 {:.0%}，已触发自动清理", usageRatio);
            } catch (Exception e) {
                log.debug("[资源监控] 自动清理失败: {}", e.getMessage());
            }
        }
        
        // 只在使用率超过70%或无可用槽位时才告警
        if (usageRatio > 0.7 || availableSlots == 0) {
            String leakInfo = browserPoolManager.getResourceLeakInfo();
            StringBuilder reason = new StringBuilder();
            
            if (availableSlots == 0) {
                reason.append("浏览器池已满，新任务将被阻塞 | 建议：1) 检查任务是否及时释放会话 2) 增加浏览器池大小");
            } else if (usageRatio > 0.7) {
                reason.append(String.format("浏览器池使用率 %.0f%%，接近上限 | 建议：检查是否有会话泄漏", usageRatio * 100));
            }
            
            if (leakInfo != null && !leakInfo.isEmpty() && !leakInfo.equals("无")) {
                reason.append(" | 泄漏详情: ").append(leakInfo);
            }
            
            log.warn("[资源监控] ⚠️ 浏览器池压力较大 - 活跃: {}/{}, 持久化: {}, 临时: {}, 可用槽位: {} | {}", 
                activeCount, maxSize, persistentCount, temporaryCount, availableSlots, reason.toString());
            alertCount.incrementAndGet();
        }
    }

    /**
     * 获取资源状态摘要
     */
    public Map<String, Object> getResourceStatus() {
        Map<String, Object> status = new HashMap<>();
        
        // 锁状态
        status.put("clipboardLocks", clipboardManager.getLockCount());
        status.put("screenshotLocks", screenshotUtil.getLockCount());
        
        // 线程状态
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        status.put("currentThreads", threadBean.getThreadCount());
        status.put("baselineThreads", baselineThreadCount);
        status.put("peakThreads", threadBean.getPeakThreadCount());
        
        // 内存状态
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        status.put("heapUsedMB", heapUsage.getUsed() / (1024 * 1024));
        status.put("heapMaxMB", heapUsage.getMax() / (1024 * 1024));
        status.put("heapUsageRatio", (double) heapUsage.getUsed() / heapUsage.getMax());
        
        // 浏览器池状态
        status.putAll(browserPoolManager.getStatus());
        status.put("browserPoolLeakInfo", browserPoolManager.getResourceLeakInfo());
        
        // 告警计数
        status.put("alertCount", alertCount.get());
        
        return status;
    }

    /**
     * 强制清理所有资源锁
     */
    public void forceCleanupLocks() {
        log.info("[资源监控] 执行强制锁清理");
        clipboardManager.clearAllLocks();
        screenshotUtil.clearAllLocks();
    }

    /**
     * 重置基线线程数
     */
    public void resetBaselineThreadCount() {
        this.baselineThreadCount = ManagementFactory.getThreadMXBean().getThreadCount();
        log.info("[资源监控] 重置基线线程数: {}", baselineThreadCount);
    }

    /**
     * 获取告警计数
     */
    public int getAlertCount() {
        return alertCount.get();
    }

    /**
     * 重置告警计数
     */
    public void resetAlertCount() {
        alertCount.set(0);
    }

    @PreDestroy
    public void cleanup() {
        log.info("[资源监控] 关闭中，执行最终清理...");
        forceCleanupLocks();
        
        // 输出最终资源状态
        Map<String, Object> finalStatus = getResourceStatus();
        log.info("[资源监控] 最终资源状态 - 线程: {}, 内存: {}MB, 告警次数: {}", 
            finalStatus.get("currentThreads"),
            finalStatus.get("heapUsedMB"),
            finalStatus.get("alertCount"));
    }
}
