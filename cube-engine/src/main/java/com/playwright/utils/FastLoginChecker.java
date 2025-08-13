package com.playwright.utils;

import com.microsoft.playwright.*;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 高性能登录状态检测器
 * 专门针对状态检测场景进行极致优化，减少CPU和内存占用
 */
@Component
public class FastLoginChecker {
    
    // 轻量级浏览器实例缓存（专用于状态检测）
    private static final ConcurrentHashMap<String, BrowserContext> quickCheckContexts = new ConcurrentHashMap<>();
    private static Playwright lightweightPlaywright = null;
    
    /**
     * 获取专用于状态检测的轻量级浏览器配置
     */
    public static String[] getUltraLightArgs() {
        return new String[]{
            // 核心性能优化
            "--headless=new",                        // 使用新版headless模式，更轻量
            "--no-sandbox",                          // 禁用沙箱
            "--disable-dev-shm-usage",              // 禁用共享内存
            "--single-process",                      // 单进程模式，降低CPU占用
            "--no-zygote",                          // 禁用zygote进程
            
            // 渲染优化 - 保留基本布局
            "--disable-gpu",                         // 禁用GPU加速
            "--disable-software-rasterizer",        // 禁用软件光栅化
            "--disable-2d-canvas-image-chromium",   // 禁用2D画布图像
            "--disable-accelerated-2d-canvas",      // 禁用加速2D画布
            "--disable-gpu-sandbox",                 // 禁用GPU沙箱
            "--virtual-time-budget=5000",           // 限制虚拟时间预算
            
            // 内存优化
            "--memory-pressure-off",                 // 关闭内存压力监控
            "--max_old_space_size=128",             // 极限V8堆内存128MB
            "--aggressive-cache-discard",            // 积极丢弃缓存
            "--disable-background-timer-throttling", // 禁用后台定时器
            
            // 网络和资源优化
            "--disable-background-networking",       // 禁用后台网络
            "--disable-default-apps",               // 禁用默认应用
            "--disable-extensions",                  // 禁用扩展
            "--disable-plugins",                     // 禁用插件
            "--disable-component-extensions-with-background-pages", // 禁用后台扩展
            
            // 最小渲染 - 但保留布局结构
            "--blink-settings=imagesEnabled=false", // 禁用图片但保留占位
            "--disable-remote-fonts",               // 禁用远程字体
            "--disable-web-security",               // 禁用web安全检查
            "--disable-features=TranslateUI,BlinkGenPropertyTrees", // 禁用翻译UI和属性树
            
            // 跨平台优化
            "--disable-logging",                     // 禁用日志记录
            "--silent",                             // 静默模式
            "--disable-background-mode",            // 禁用后台模式
            "--disable-background-timer-throttling", // 禁用定时器限制
            "--disable-renderer-backgrounding",     // 禁用渲染器后台化
            "--disable-backgrounding-occluded-windows", // 禁用遮挡窗口后台化
            
            // 系统特定优化
            "--disable-ipc-flooding-protection",    // 禁用IPC洪水保护
            "--disable-hang-monitor",               // 禁用挂起监控
            "--disable-prompt-on-repost",           // 禁用重复提交提示
            "--disable-client-side-phishing-detection", // 禁用客户端钓鱼检测
            "--disable-sync",                       // 禁用同步
            "--metrics-recording-only",             // 仅记录指标
            "--no-first-run",                       // 跳过首次运行
            "--safebrowsing-disable-auto-update"    // 禁用安全浏览更新
        };
    }
    
    /**
     * 获取轻量级Playwright实例
     */
    private static synchronized Playwright getLightweightPlaywright() {
        if (lightweightPlaywright == null) {
            lightweightPlaywright = Playwright.create();
        }
        return lightweightPlaywright;
    }
    
    /**
     * 创建极速状态检测浏览器上下文
     */
    public BrowserContext createFastCheckContext(String userId, String platform) {
        String contextKey = userId + "_" + platform + "_check";
        
        // 检查是否已有可用的上下文
        BrowserContext existingContext = quickCheckContexts.get(contextKey);
        if (existingContext != null) {
            try {
                // 验证上下文是否仍然有效
                existingContext.pages().get(0).title();
                return existingContext;
            } catch (Exception e) {
                // 上下文已失效，移除并重新创建
                quickCheckContexts.remove(contextKey);
                try {
                    existingContext.close();
                } catch (Exception ignored) {}
            }
        }
        
        try {
            Playwright playwright = getLightweightPlaywright();
            
            // 创建极轻量级浏览器
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(true)  // 状态检测使用headless模式
                .setTimeout(15000)  // 15秒超时
                .setArgs(Arrays.asList(getUltraLightArgs()))
            );
            
            // 创建上下文
            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 FastChecker/1.0")
                .setViewportSize(1024, 768)  // 固定小尺寸视口
                .setJavaScriptEnabled(true)  // 保留JS以确保正确检测登录状态
                .setIgnoreHTTPSErrors(true)
            );
            
            // 缓存上下文以重复使用
            quickCheckContexts.put(contextKey, context);
            
            return context;
            
        } catch (Exception e) {
            throw new RuntimeException("无法创建状态检测浏览器", e);
        }
    }
    
    /**
     * 为页面配置极速检测优化
     */
    public void optimizePageForFastCheck(Page page) {
        try {
            // 设置超短超时
            page.setDefaultTimeout(10000); // 10秒
            page.setDefaultNavigationTimeout(15000); // 15秒导航超时
            
            // 阻止所有非关键资源，但保留基本CSS以维持布局
            page.route("**/*.{png,jpg,jpeg,gif,svg,ico,webp,bmp,tiff}", route -> route.abort());
            page.route("**/fonts/**", route -> route.abort());
            page.route("**/*.{woff,woff2,ttf,otf,eot}", route -> route.abort());
            page.route("**/analytics/**", route -> route.abort());
            page.route("**/tracking/**", route -> route.abort());
            page.route("**/ads/**", route -> route.abort());
            page.route("**/advertisement/**", route -> route.abort());
            page.route("**/*google-analytics*", route -> route.abort());
            page.route("**/*gtag*", route -> route.abort());
            page.route("**/*facebook*", route -> route.abort());
            page.route("**/*twitter*", route -> route.abort());
            page.route("**/*doubleclick*", route -> route.abort());
            page.route("**/*googlesyndication*", route -> route.abort());
            
            // 注入快速检测脚本
            page.addInitScript("""
                // 禁用动画和过渡效果
                const style = document.createElement('style');
                style.textContent = `
                    *, *::before, *::after {
                        animation-duration: 0.001ms !important;
                        animation-delay: 0s !important;
                        transition-duration: 0.001ms !important;
                        transition-delay: 0s !important;
                    }
                `;
                document.head.appendChild(style);
                
                // 快速DOM就绪检测
                window._fastCheckReady = false;
                const checkReady = () => {
                    if (document.readyState === 'interactive' || document.readyState === 'complete') {
                        window._fastCheckReady = true;
                    }
                };
                checkReady();
                document.addEventListener('readystatechange', checkReady);
                
                // 禁用控制台输出
                console.log = console.warn = console.error = () => {};
            """);
            
        } catch (Exception e) {
        }
    }
    
    /**
     * 等待页面快速就绪（专用于状态检测）
     */
    public void waitForFastReady(Page page, int maxWaitMs) {
        try {
            // 等待DOM基本就绪或超时
            page.waitForFunction("() => window._fastCheckReady === true", 
                new Page.WaitForFunctionOptions().setTimeout(maxWaitMs));
        } catch (Exception e) {
            // 超时也继续，因为主要元素可能已加载
        }
    }
    
    /**
     * 清理快速检测上下文（定期调用）
     */
    public static void cleanupFastCheckContexts() {
        quickCheckContexts.entrySet().removeIf(entry -> {
            try {
                entry.getValue().pages().get(0).title();
                return false; // 上下文有效，保留
            } catch (Exception e) {
                try {
                    entry.getValue().close();
                } catch (Exception ignored) {}
                return true; // 上下文无效，移除
            }
        });
    }
    
    /**
     * 关闭所有快速检测资源
     */
    public static void shutdown() {
        quickCheckContexts.values().forEach(context -> {
            try {
                context.close();
            } catch (Exception ignored) {}
        });
        quickCheckContexts.clear();
        
        if (lightweightPlaywright != null) {
            try {
                lightweightPlaywright.close();
                lightweightPlaywright = null;
            } catch (Exception ignored) {}
        }
    }
} 