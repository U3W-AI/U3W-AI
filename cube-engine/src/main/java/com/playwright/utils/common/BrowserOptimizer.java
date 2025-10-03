package com.playwright.utils.common;

import com.microsoft.playwright.Page;
import org.springframework.stereotype.Component;

/**
 * 浏览器性能优化工具类
 * 提供统一的性能优化配置和方法
 */
@Component
public class BrowserOptimizer {
    
    /**
     * 优化页面性能
     * @param page 页面对象
     */
    public static void optimizePage(Page page) {
        try {
            // 🔥 优化：增加默认超时时间，减少频繁超时
            page.setDefaultTimeout(60000); // 保持60秒超时，避免用户长时间等待
            
            // 🔥 新增：设置导航超时
            page.setDefaultNavigationTimeout(45000); // 45秒导航超时
            
            // 阻止不必要的资源加载以减少网络和内存消耗
            page.route("**/*.{png,jpg,jpeg,gif,svg,ico,webp}", route -> route.abort());
            page.route("**/*.{css}", route -> route.abort());
            page.route("**/*.{woff,woff2,ttf,otf}", route -> route.abort());
            page.route("**/analytics/**", route -> route.abort());
            page.route("**/tracking/**", route -> route.abort());
            page.route("**/ads/**", route -> route.abort());
            page.route("**/*google-analytics*", route -> route.abort());
            page.route("**/*gtag*", route -> route.abort());
            page.route("**/*facebook*", route -> route.abort());
            page.route("**/*twitter*", route -> route.abort());
            
            // 🔥 新增：阻止更多无关资源，进一步优化性能
            page.route("**/*doubleclick*", route -> route.abort());
            page.route("**/*googletagmanager*", route -> route.abort());
            page.route("**/*googlesyndication*", route -> route.abort());
            page.route("**/*.{mp4,avi,mov,wmv,flv,webm}", route -> route.abort()); // 阻止视频加载
            
        } catch (Exception e) {
        }
    }
    
    /**
     * 🔥 新增：针对AI平台的特殊优化
     * @param page 页面对象
     */
    public static void optimizeForAIPlatform(Page page) {
        try {
            // AI平台需要更长的超时时间
            page.setDefaultTimeout(300000); // 5分钟超时，适应AI生成响应时间
            page.setDefaultNavigationTimeout(180000); // 3分钟导航超时
            
            // 保留必要的资源，但阻止干扰性内容
            page.route("**/analytics/**", route -> route.abort());
            page.route("**/tracking/**", route -> route.abort());
            page.route("**/ads/**", route -> route.abort());
            page.route("**/*.{png,jpg,jpeg,gif,webp}", route -> route.abort()); // 阻止图片但保留必要资源
            
        } catch (Exception e) {
        }
    }
    
    /**
     * 获取优化后的浏览器启动参数
     */
    public static String[] getOptimizedArgs() {
        return new String[]{
            "--no-sandbox",                     // 禁用沙箱模式
            "--disable-dev-shm-usage",         // 禁用/dev/shm
            "--disable-gpu",                    // 禁用GPU加速
            "--disable-web-security",           // 禁用web安全检查
            "--disable-extensions",             // 禁用扩展
            "--disable-plugins",                // 禁用插件
            "--disable-images",                 // 禁用图片加载
            "--disable-javascript-harmony-shipping", // 禁用实验性JS特性
            "--disable-background-timer-throttling",  // 禁用后台定时器限制
            "--disable-backgrounding-occluded-windows", // 禁用被遮挡窗口后台化
            "--disable-renderer-backgrounding", // 禁用渲染器后台化
            "--disable-feature-policy",         // 禁用特性策略
            "--memory-pressure-off",            // 关闭内存压力监控
            "--max_old_space_size=1024",        // 🔥 优化：增加V8堆内存到1GB，支持复杂AI任务
            "--aggressive-cache-discard",       // 积极丢弃缓存
            "--disable-background-networking",  // 禁用后台网络
            "--disable-background-mode",        // 禁用后台模式
            "--disable-default-apps",           // 禁用默认应用
            "--disable-hang-monitor",           // 禁用挂起监控
            "--disable-prompt-on-repost",       // 禁用重复提交提示
            "--disable-sync",                   // 禁用同步
            "--metrics-recording-only",         // 仅记录指标
            "--no-first-run",                   // 跳过首次运行
            "--safebrowsing-disable-auto-update", // 禁用安全浏览自动更新
            "--disable-ipc-flooding-protection",   // 禁用IPC洪水保护
            "--disable-blink-features=AutomationControlled", // 🔥 新增：隐藏自动化痕迹
            "--disable-component-extensions-with-background-pages", // 禁用后台扩展页面
            "--disable-default-apps",           // 禁用默认应用
            "--disable-dev-tools",             // 禁用开发者工具（在生产环境）
            "--disable-extensions-file-access-check", // 禁用扩展文件访问检查
            "--no-default-browser-check",       // 不检查默认浏览器
            "--no-pings"                        // 禁用ping
        };
    }
    
    /**
     * 🔥 新增：获取高性能配置的浏览器启动参数（用于CPU密集型任务）
     */
    public static String[] getHighPerformanceArgs() {
        return new String[]{
            "--no-sandbox",
            "--disable-dev-shm-usage",
            "--disable-gpu",
            "--disable-web-security",
            "--disable-extensions",
            "--disable-plugins",
            "--disable-images",
            "--single-process",                 // 单进程模式，减少进程间通信开销
            "--disable-background-timer-throttling",
            "--disable-backgrounding-occluded-windows",
            "--disable-renderer-backgrounding",
            "--memory-pressure-off",
            "--max_old_space_size=2048",        // 更大内存分配
            "--js-flags=--max-old-space-size=2048", // JS引擎内存
            "--aggressive-cache-discard",
            "--disable-background-networking",
            "--disable-hang-monitor",
            "--disable-ipc-flooding-protection",
            "--renderer-process-limit=1",       // 限制渲染进程数
            "--max_web_media_player_count=1"    // 限制媒体播放器数量
        };
    }
    
    /**
     * 清理页面资源
     * @param page 页面对象
     */
    public static void cleanupPage(Page page) {
        try {
            // 执行垃圾回收
            page.evaluate("() => { if (window.gc) window.gc(); }");
            
            // 清理事件监听器
            page.evaluate("() => { " +
                "const events = ['click', 'scroll', 'resize', 'mouseover', 'mouseout']; " +
                "events.forEach(event => { " +
                "  window.removeEventListener(event, function() {}); " +
                "}); " +
                "}");
            
            // 🔥 新增：清理更多资源
            page.evaluate("() => { " +
                "if (window.performance && window.performance.clearResourceTimings) {" +
                "  window.performance.clearResourceTimings();" +
                "}" +
                "if (window.performance && window.performance.clearMeasures) {" +
                "  window.performance.clearMeasures();" +
                "}" +
                "}");
                
        } catch (Exception e) {
        }
    }
} 