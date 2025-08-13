package com.playwright.utils;

/**
 * @author 优立方
 * @version JDK 17
 * @date 2025年01月14日 10:57
 */

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class BrowserUtil {

    @Value("${cube.datadir}")
    private String userDataDir;

    // 🔥 优化：增强的资源管理和重试策略
    private static final int MAX_RETRIES = 12; // 增加重试次数到12次，给更多机会
    private static final long BASE_WAIT_TIME = 3000; // 基础等待时间增加到3秒
    private static final long MAX_WAIT_TIME = 30000; // 最大等待时间增加到30秒
    private static final int CONTEXT_TIMEOUT = 90000; // 🔥 关键：上下文创建超时增加到90秒
    
    // 🔥 新增：并发控制计数器，防止过多同时创建上下文
    private static final AtomicInteger CONCURRENT_CONTEXT_COUNT = new AtomicInteger(0);
    private static final int MAX_CONCURRENT_CONTEXTS = 3; // 最多同时创建3个上下文



    /**
     * 启动持久化浏览器上下文
     * 🔥 优化：增强的重试机制、资源管理和并发控制
     *
     * @return BrowserContext 持久化浏览器上下文
     */
    public BrowserContext createPersistentBrowserContext(boolean isHead, String userId, String name) {
        Exception lastException = null;
        
        // 🔥 并发控制：如果当前创建的上下文过多，等待
        while (CONCURRENT_CONTEXT_COUNT.get() >= MAX_CONCURRENT_CONTEXTS) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("等待并发控制时被中断", e);
            }
        }
        
        // 增加并发计数
        CONCURRENT_CONTEXT_COUNT.incrementAndGet();
        
        try {
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                Playwright playwright = null;
                try {
                    
                    // 🔥 优化：更保守的退避策略，线性增长而非指数增长
                    if (attempt > 1) {
                        long waitTime = Math.min(BASE_WAIT_TIME + (attempt - 1) * 2000, MAX_WAIT_TIME);
                        Thread.sleep(waitTime);
                        
                        // 🔥 增强：强制垃圾回收和更长的资源释放时间
                        System.gc();
                        Thread.sleep(1500); // 增加到1.5秒给系统更多时间清理
                        
                        // 🔥 新增：特殊情况处理，第3次重试后使用更保守的配置
                        if (attempt >= 3) {
                            Thread.sleep(3000); // 额外等待3秒
                        }
                    }
                    
                    // 创建 Playwright 实例
                    playwright = Playwright.create();
        BrowserType browserType = playwright.chromium();

                    // 🔥 关键优化：根据重试次数调整启动参数
                    BrowserType.LaunchPersistentContextOptions options = createOptimizedBrowserOptions(isHead, attempt);
                    
                    
                    // 🔥 核心修复：启动持久化上下文，使用更长的超时时间
                    BrowserContext context = browserType.launchPersistentContext(
                        Paths.get(userDataDir + "/" + name + "/" + userId), 
                        options
                    );
                    
                    // 验证上下文是否有效
                    if (context == null) {
                        throw new RuntimeException("Browser context creation returned null");
                    }
                    
                    
                    // 🔥 优化：更安全的权限授予
                    try {
                        context.grantPermissions(Arrays.asList("clipboard-read", "clipboard-write"));
                    } catch (Exception permissionError) {
                    }
                    
                    return context;
                    
                } catch (com.microsoft.playwright.impl.TargetClosedError e) {
                    lastException = e;
                    
                    // 强制清理资源
                    cleanupPlaywrightResources(playwright);
                    
                    if (attempt < MAX_RETRIES) {
                        
                        // 🔥 新增：TargetClosedError 特殊处理，额外等待时间
                        if (attempt >= 2) {
                            try {
                                Thread.sleep(5000); // 额外等待5秒
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException("线程在TargetClosedError恢复等待时被中断", ie);
                            }
                        }
                    } else {
                    }
                } catch (com.microsoft.playwright.TimeoutError e) {
                    lastException = e;
                    
                    // 强制清理资源
                    cleanupPlaywrightResources(playwright);
                    
                    if (attempt < MAX_RETRIES) {
                        
                        // 🔥 新增：TimeoutError 特殊处理，更长的等待时间
                        if (attempt >= 3) {
                            try {
                                Thread.sleep(8000); // 额外等待8秒
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException("线程在TimeoutError恢复等待时被中断", ie);
                            }
                        }
                    } else {
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    cleanupPlaywrightResources(playwright);
                    throw new RuntimeException("浏览器上下文创建被中断", e);
                } catch (Exception e) {
                    lastException = e;
                    
                    // 对于某些特定错误，不需要重试
                    if (isNonRetryableError(e)) {
                        cleanupPlaywrightResources(playwright);
                        throw new RuntimeException("浏览器上下文创建失败（不可重试错误）", e);
                    }
                    
                    cleanupPlaywrightResources(playwright);
                    
                    if (attempt < MAX_RETRIES) {
                    } else {
                    }
                }
            }
            
            String errorMessage = lastException != null ? lastException.getMessage() : "未知错误";
            throw new RuntimeException("创建持久化浏览器上下文失败，经过 " + MAX_RETRIES + " 次重试。最后错误: " + errorMessage, lastException);
            
        } finally {
            // 🔥 新增：确保释放并发计数
            CONCURRENT_CONTEXT_COUNT.decrementAndGet();
        }
    }
    
    /**
     * 🔥 新增：根据重试次数创建优化的浏览器选项
     */
    private BrowserType.LaunchPersistentContextOptions createOptimizedBrowserOptions(boolean isHead, int attempt) {
        BrowserType.LaunchPersistentContextOptions options = new BrowserType.LaunchPersistentContextOptions()
                .setHeadless(isHead)
                .setViewportSize(1280, 760)
                .setTimeout(CONTEXT_TIMEOUT); // 🔥 使用更长的超时时间
        
        // 🔥 根据重试次数调整启动参数：重试次数越多，配置越保守
        if (attempt <= 2) {
            // 前两次重试：标准配置
            options.setArgs(Arrays.asList(
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu",
                "--disable-web-security",
                "--disable-extensions",
                "--disable-plugins",
                "--disable-images",
                "--disable-background-timer-throttling",
                "--disable-backgrounding-occluded-windows",
                "--disable-renderer-backgrounding",
                "--memory-pressure-off",
                "--max_old_space_size=512",
                "--aggressive-cache-discard"
            ));
        } else if (attempt <= 5) {
            // 第3-5次重试：保守配置
            options.setArgs(Arrays.asList(
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu",
                "--disable-web-security",
                "--disable-extensions",
                "--disable-plugins",
                "--disable-images",
                "--single-process",
                "--no-zygote",
                "--disable-setuid-sandbox",
                "--disable-background-networking",
                "--disable-default-apps",
                "--disable-sync",
                "--memory-pressure-off",
                "--max_old_space_size=256" // 减少内存使用
            ));
        } else {
            // 第6次及以后：超保守配置
            options.setArgs(Arrays.asList(
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu",
                "--disable-web-security",
                "--disable-extensions",
                "--disable-plugins",
                "--disable-images",
                "--single-process",
                "--no-zygote",
                "--disable-setuid-sandbox",
                "--disable-background-networking",
                "--disable-default-apps",
                "--disable-sync",
                "--disable-translate",
                "--disable-ipc-flooding-protection",
                "--disable-hang-monitor",
                "--disable-prompt-on-repost",
                "--disable-client-side-phishing-detection",
                "--disable-component-update",
                "--disable-domain-reliability",
                "--disable-features=VizDisplayCompositor",
                "--memory-pressure-off",
                "--max_old_space_size=128" // 最小内存使用
            ));
        }
        
        return options;
    }
    
    /**
     * 清理 Playwright 资源
     */
    private void cleanupPlaywrightResources(Playwright playwright) {
        if (playwright != null) {
            try {
                playwright.close();
            } catch (Exception cleanupE) {
            }
        }
    }
    
    /**
     * 判断是否为不可重试的错误
     */
    private boolean isNonRetryableError(Exception e) {
        String errorMessage = e.getMessage();
        if (errorMessage == null) return false;
        
        // 配置相关错误通常不需要重试
        return errorMessage.contains("Invalid argument") ||
               errorMessage.contains("Permission denied") ||
               errorMessage.contains("Access denied") ||
               errorMessage.contains("File not found") ||
               e instanceof IllegalArgumentException ||
               e instanceof SecurityException;
    }

    /**
     * 获取或重用现有页面，避免创建多余的空白页面
     * 优先重用已存在的空白页面；如果没有合适的页面，则创建新页面
     *
     * @param context 浏览器上下文
     * @return Page 页面对象
     */
    public Page getOrCreatePage(BrowserContext context) {
        try {
            // 查找可重用的空白页面
            for (Page page : context.pages()) {
                try {
                    String url = page.url();
                    // 重用空白页面或初始页面
                    if (url.equals("about:blank") || url.equals("chrome://newtab/") || url.equals("")) {
                        return page;
                    }
                } catch (Exception e) {
                    // 如果获取URL失败，跳过这个页面
                    continue;
                }
            }
            
            // 如果没有找到空白页面，但有其他页面，重用第一个页面
            if (context.pages().size() > 0) {
                return context.pages().get(0);
            }
            
            // 如果没有任何页面，创建新页面
            return context.newPage();
        } catch (Exception e) {
            throw new RuntimeException("无法获取或创建页面", e);
        }
    }

    /**
     * 创建一个自动关闭的BrowserContext包装器
     * 
     * @param context 浏览器上下文
     * @param userId 用户ID
     * @param contextName 上下文名称
     * @return AutoCloseable包装器
     */
    public static AutoCloseable createAutoCloseableContext(BrowserContext context, String userId, String contextName) {
        return () -> gracefullyCloseBrowserContext(context, userId, contextName);
    }

    /**
     * 优雅关闭浏览器上下文，避免 Playwright 内部错误
     * 
     * @param context 要关闭的浏览器上下文
     * @param userId 用户ID（用于日志）
     * @param contextName 上下文名称（用于日志）
     */
    public static void gracefullyCloseBrowserContext(BrowserContext context, String userId, String contextName) {
        if (context == null) {
            return;
        }
        
        try {
            
            // 1. 首先关闭所有页面
            try {
                for (Page page : context.pages()) {
                    try {
                        if (!page.isClosed()) {
                            page.close();
                        }
                    } catch (Exception e) {
                        // 忽略页面关闭错误，继续处理其他页面
                    }
                }
            } catch (Exception e) {
            }
            
            // 2. 等待一小段时间让页面完全关闭
            Thread.sleep(500);
            
            // 3. 关闭上下文，捕获并忽略 Playwright 内部错误
            try {
                if (!context.browser().isConnected()) {
                    return;
                }
                
                context.close();
                
            } catch (Exception e) {
                String errorMsg = e.getMessage();
                
                // 检查是否是已知的 Playwright 内部错误
                if (errorMsg != null && (
                    errorMsg.contains("Cannot find module") ||
                    errorMsg.contains("package.json") ||
                    errorMsg.contains("userAgent.js") ||
                    errorMsg.contains("harTracer.js") ||
                    errorMsg.contains("Target page, context or browser has been closed")
                )) {
                } else {
                    // 其他类型的错误仍然记录
                }
            }
            
        } catch (Exception e) {
        }
    }

    /**
     * 优雅关闭 Playwright 实例，避免资源泄露
     * 
     * @param playwright Playwright 实例
     */
    public static void gracefullyClosePlaywright(Playwright playwright) {
        if (playwright == null) {
            return;
        }
        
        try {
            
            // 等待一小段时间确保所有操作完成
            Thread.sleep(200);
            
            playwright.close();
            
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            
            // 检查是否是已知的内部错误
            if (errorMsg != null && (
                errorMsg.contains("Cannot find module") ||
                errorMsg.contains("package.json") ||
                errorMsg.contains("userAgent.js")
            )) {
            } else {
            }
        }
    }
}
