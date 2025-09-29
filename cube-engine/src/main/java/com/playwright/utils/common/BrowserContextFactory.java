package com.playwright.utils.common;

import com.microsoft.playwright.*;
import com.playwright.controller.ai.BrowserController;
import com.playwright.entity.UnPersisBrowserContextInfo;

import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ConcurrentHashMap;

/**
 * dateStart 2024/8/4 9:34
 * dateNow   2025/8/4 9:27
 */
public class BrowserContextFactory {
    public static final Map<String, UnPersisBrowserContextInfo> map = new HashMap<>();
    private static final Playwright playwright = Playwright.create();
    
    // 🔥 优化：动态调整上下文数量限制，基于CPU核心数
    private static final int CPU_CORES = Runtime.getRuntime().availableProcessors();
    private static Integer CONTEXT_COUNT = 0;
    private static final Integer MAX_CONTEXT_COUNT = Math.max(20, CPU_CORES * 3); // 至少20个或3倍CPU核心数

    // 🔥 优化：并发控制基于CPU核心数，提高并发创建能力
    private static final Semaphore CREATION_SEMAPHORE = new Semaphore(Math.max(4, CPU_CORES / 2)); // 至少4个或CPU核心数一半
    private static final Object CREATION_LOCK = new Object();
    
    // 🔥 新增：任务运行状态追踪，用于动态延长浏览器实例时间
    private static final Map<String, Long> TASK_START_TIME = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> TASK_RUNNING_STATUS = new ConcurrentHashMap<>();

    /**
     * @param key   userId
     * @param count 页面数量
     * @return 浏览器上下文信息
     */
    public static UnPersisBrowserContextInfo getBrowserContext(String key, int count) {
        UnPersisBrowserContextInfo unPersisBrowserContextInfo = map.get(key);
        if (unPersisBrowserContextInfo == null) {
            Browser browser = null;
            BrowserContext browserContext = null;

            // 获取创建许可，避免过多并发创建
            boolean acquired = false;
            try {
                acquired = CREATION_SEMAPHORE.tryAcquire(10, java.util.concurrent.TimeUnit.SECONDS);
                if (!acquired) {
                    return null;
                }

                synchronized (CREATION_LOCK) {
                    // 再次检查，可能在等待期间已经创建了
                    unPersisBrowserContextInfo = map.get(key);
                    if (unPersisBrowserContextInfo != null) {
                        return unPersisBrowserContextInfo;
                    }

                    if (CONTEXT_COUNT >= MAX_CONTEXT_COUNT) {
                        //关闭最久的上下文
                        closeLongestUsed();
                    }

                    // 启动 Chromium 浏览器（优化资源消耗和稳定性）
                    browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                            .setHeadless(false)
                            .setTimeout(30000) // 设置30秒超时
                            .setArgs(Arrays.asList(
                                    "--no-sandbox",                    // 禁用沙箱模式，减少CPU占用
                                    "--disable-dev-shm-usage",        // 禁用/dev/shm，避免内存不足
                                    "--disable-gpu",                   // 禁用GPU加速，减少GPU占用
                                    "--disable-extensions",            // 禁用扩展
                                    "--disable-plugins",               // 禁用插件
                                    "--disable-images",                // 禁用图片加载，大幅减少网络和内存占用
                                    "--disable-background-timer-throttling",  // 禁用后台定时器限制
                                    "--disable-backgrounding-occluded-windows", // 禁用被遮挡窗口的后台化
                                    "--disable-renderer-backgrounding", // 禁用渲染器后台化
                                    "--memory-pressure-off",           // 关闭内存压力监控
                                    "--max_old_space_size=192",        // 优化V8堆内存为192MB
                                    "--aggressive-cache-discard",     // 积极丢弃缓存
                                    "--disable-background-networking", // 禁用后台网络
                                    "--disable-default-apps",         // 禁用默认应用
                                    "--disable-sync",                  // 禁用同步
                                    "--disable-web-security",         // 禁用web安全检查（减少CPU检查）
                                    "--disable-features=VizDisplayCompositor", // 禁用合成器
                                    "--disable-logging",               // 禁用日志记录
                                    "--silent",                        // 静默模式
                                    "--disable-hang-monitor",          // 禁用挂起监控
                                    "--disable-prompt-on-repost",      // 禁用重复提交提示
                                    "--disable-ipc-flooding-protection", // 禁用IPC洪水保护
                                    "--disable-client-side-phishing-detection", // 禁用钓鱼检测
                                    "--no-first-run",                  // 跳过首次运行
                                    "--metrics-recording-only",        // 仅记录指标
                                    "--safebrowsing-disable-auto-update" // 禁用安全浏览更新
                            ))
                    );

                    // 创建浏览器上下文（相当于新的隐身窗口）
                    browserContext = browser.newContext(new Browser.NewContextOptions()
                            .setPermissions(Arrays.asList(
                                    "clipboard-read",  // 允许读取剪贴板
                                    "clipboard-write"  // 允许写入剪贴板
                            )));

                    // 确保有足够的页面 - 修复页面创建逻辑
                    int currentPageCount = browserContext.pages().size();
                    int needToCreate = count - currentPageCount;

                    if (needToCreate > 0) {
                        for (int i = 0; i < needToCreate; i++) {
                            try {
                                Page page = browserContext.newPage();
                            } catch (Exception pageE) {
                                // 即使某个页面创建失败，也继续尝试创建其他页面
                                throw pageE;
                            }
                        }
                    }

                    int finalPageCount = browserContext.pages().size();

                    // 如果页面数量仍然不足，记录警告但不抛出异常
                    if (finalPageCount < count) {
                        System.err.println("WARNING: 页面数量不足，期望: " + count + ", 实际: " + finalPageCount);
                    }

                    // 存入信息
                    unPersisBrowserContextInfo = new UnPersisBrowserContextInfo();
                    unPersisBrowserContextInfo.setUserId(key);
                    unPersisBrowserContextInfo.setBrowserContext(browserContext);
                    
                    // 🔥 核心优化：动态设置过期时间，考虑任务运行状态
                    long expireTime = calculateDynamicExpireTime(key);
                    unPersisBrowserContextInfo.setExpireTime(expireTime);
                    
                    map.put(key, unPersisBrowserContextInfo);
                    CONTEXT_COUNT++;
                    
                    // 🔥 新增：标记任务开始时间
                    markTaskStart(key);
                }
                return unPersisBrowserContextInfo;
            } catch (InterruptedException e) {
                // 处理中断异常
                Thread.currentThread().interrupt(); // 恢复中断状态

                // 清理资源
                if (browserContext != null) {
                    try {
                        browserContext.close();
                    } catch (Exception cleanupE) {
                        // 静默处理
                    }
                }
                if (browser != null) {
                    try {
                        browser.close();
                    } catch (Exception cleanupE) {
                        // 静默处理
                    }
                }

                return null; // 中断时返回null
            } catch (Exception e) {
                // 清理资源
                if (browserContext != null) {
                    try {
                        browserContext.close();
                    } catch (Exception cleanupE) {
                        // 静默处理
                    }
                }
                if (browser != null) {
                    try {
                        browser.close();
                    } catch (Exception cleanupE) {
                        // 静默处理
                    }
                }

                throw e;
            } finally {
                // 释放创建许可
                if (acquired) {
                    CREATION_SEMAPHORE.release();
                }
            }
        }
        return unPersisBrowserContextInfo;
    }

    // 🔥 新增：标记任务开始
    public static void markTaskStart(String userId) {
        TASK_START_TIME.put(userId, System.currentTimeMillis());
        TASK_RUNNING_STATUS.put(userId, true);
    }
    
    // 🔥 新增：标记任务完成
    public static void markTaskComplete(String userId) {
        TASK_RUNNING_STATUS.put(userId, false);
    }
    
    // 🔥 新增：检查任务是否正在运行
    public static boolean isTaskRunning(String userId) {
        return TASK_RUNNING_STATUS.getOrDefault(userId, false);
    }
    
    // 🔥 新增：延长浏览器实例运行时间（当任务还在运行时）
    public static void extendContextIfTaskRunning(String key) {
        UnPersisBrowserContextInfo contextInfo = map.get(key);
        if (contextInfo != null && isTaskRunning(key)) {
            long newExpireTime = calculateDynamicExpireTime(key);
            contextInfo.setExpireTime(newExpireTime);
        }
    }
    
    // 🔥 新增：检查是否为腾讯元宝用户（最小改动）
    private static boolean isTencentUser(String userId) {
        UnPersisBrowserContextInfo contextInfo = map.get(userId);
        if (contextInfo != null && contextInfo.getBrowserContext() != null) {
            try {
                // 检查是否有腾讯元宝的页面
                return contextInfo.getBrowserContext().pages().stream()
                    .anyMatch(page -> {
                        try {
                            String url = page.url();
                            return url != null && url.contains("yuanbao.tencent.com");
                        } catch (Exception e) {
                            return false;
                        }
                    });
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    // 🔥 新增：动态计算过期时间
    private static long calculateDynamicExpireTime(String userId) {
        long currentTime = System.currentTimeMillis();
        
        // 🔥 关键：腾讯元宝用户永不过期（最小改动）
        if (isTencentUser(userId)) {
            return Long.MAX_VALUE; // 永不过期
        }
        
        // 基础过期时间：2小时
        long baseExpireTime = currentTime + (2 * 60 * 60 * 1000);
        
        // 如果任务正在运行，延长到4小时
        if (isTaskRunning(userId)) {
            Long taskStartTime = TASK_START_TIME.get(userId);
            if (taskStartTime != null) {
                long taskRunningTime = currentTime - taskStartTime;
                
                // 任务已运行超过1小时，再给4小时
                if (taskRunningTime > (60 * 60 * 1000)) {
                    return currentTime + (4 * 60 * 60 * 1000);
                }
                // 任务运行不到1小时，给3小时
                else {
                    return currentTime + (3 * 60 * 60 * 1000);
                }
            }
        }
        
        return baseExpireTime;
    }

    // 🔥 优化：检查过期时间时考虑任务运行状态
    public static void closeExpireData(String key) {
        UnPersisBrowserContextInfo unPersisBrowserContextInfo = map.get(key);
        if (unPersisBrowserContextInfo != null) {
            BrowserContext browserContext = unPersisBrowserContextInfo.getBrowserContext();
            if (browserContext == null) {
                map.remove(key);
                BrowserController.loginMap.remove(key);
                // 🔥 新增：清理任务状态
                cleanupTaskStatus(key);
                return;
            }
            
            // 🔥 优化：在关闭前检查任务是否还在运行，如果在运行则延长时间
            if (System.currentTimeMillis() > unPersisBrowserContextInfo.getExpireTime()) {
                // 🔥 关键：腾讯元宝用户永不关闭（最小改动）
                if (isTencentUser(key)) {
                    return; // 腾讯元宝用户不关闭
                }
                
                if (isTaskRunning(key)) {
                    // 任务还在运行，延长过期时间
                    extendContextIfTaskRunning(key);
                } else {
                    // 任务已完成，可以关闭
                browserContext.close();
                BrowserController.loginMap.remove(key);
                map.remove(key);
                    cleanupTaskStatus(key);
                }
            }
        }
    }
    
    // 🔥 新增：清理任务状态
    private static void cleanupTaskStatus(String userId) {
        TASK_START_TIME.remove(userId);
        TASK_RUNNING_STATUS.remove(userId);
    }

    // 关闭存活最久的上下文
    public static void closeLongestUsed() {
        Set<String> set = map.keySet();
        String suvMinKey = "";
        long suvMinTime = Long.MAX_VALUE;
        for (String key : set) {
            UnPersisBrowserContextInfo unPersisBrowserContextInfo = map.get(key);
            if (unPersisBrowserContextInfo != null) {
                // 🔥 关键：腾讯元宝用户不参与最久关闭逻辑（最小改动）
                if (isTencentUser(key)) {
                    continue;
                }
                
                // 🔥 优化：如果任务还在运行，跳过该上下文
                if (isTaskRunning(key)) {
                    continue;
                }
                
                Long expireTime = unPersisBrowserContextInfo.getExpireTime() - System.currentTimeMillis();
                if (expireTime < suvMinTime) {
                    suvMinTime = expireTime;
                    suvMinKey = key;
                }
            }
        }
        if (!Objects.equals(suvMinKey, "")) {
            UnPersisBrowserContextInfo unPersisBrowserContextInfo = map.get(suvMinKey);
            BrowserContext browserContext = unPersisBrowserContextInfo.getBrowserContext();
            if (browserContext != null) {
                browserContext.close();
            }
            map.remove(suvMinKey);
            cleanupTaskStatus(suvMinKey);
        }
    }
}
