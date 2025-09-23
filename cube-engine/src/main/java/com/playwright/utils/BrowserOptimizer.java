package com.playwright.utils;

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
            page.setDefaultTimeout(60000); // 增加到60秒超时，给AI响应更多时间
            
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
            "--max_old_space_size=512",         // 限制V8堆内存
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
            "--disable-ipc-flooding-protection"   // 禁用IPC洪水保护
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
                
        } catch (Exception e) {
        }
    }
} 