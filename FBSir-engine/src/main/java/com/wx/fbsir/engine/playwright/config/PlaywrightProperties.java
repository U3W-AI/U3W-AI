package com.wx.fbsir.engine.playwright.config;

import com.wx.fbsir.engine.playwright.util.SystemCapabilityDetector;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Playwright 配置属性
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 配置说明
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 此类定义了 Playwright 浏览器自动化框架的所有可配置参数。
 * 配置前缀：wxfbsir.engine.playwright
 * 
 * 配置示例 (application.yml):
 * ```yaml
 * wxfbsir:
 *   engine:
 *     playwright:
 *     enabled: true
 *     data-dir: /path/to/browser/data
 *     headless: false
 *     pool:
 *       max-size: 10
 *       min-idle: 2
 *       session-timeout: 3600000
 *     browser:
 *       launch-timeout: 60000
 *       navigation-timeout: 30000
 *       viewport-width: 1280
 *       viewport-height: 720
 * ```
 * 
 * @author FBSir
 * @date 2025-12-16
 */
@Component
@ConfigurationProperties(prefix = "wxfbsir.engine.playwright")
public class PlaywrightProperties {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightProperties.class);

    /**
     * 是否启用 Playwright 功能
     */
    private boolean enabled = true;

    /**
     * 是否启用动态性能适配（根据系统性能自动调整配置）
     */
    private boolean dynamicPerformance = true;

    /**
     * 浏览器用户数据目录（用于持久化会话）
     * 默认值：系统临时目录下的 playwright-data
     */
    private String dataDir = System.getProperty("java.io.tmpdir") + "/playwright-data";

    /**
     * 默认是否使用无头模式
     * true: 无头模式（无GUI，适合服务器）
     * false: 有头模式（有GUI，适合调试）
     */
    private boolean headless = false;

    /**
     * Playwright 实例池配置
     */
    private InstancePoolConfig instancePool = new InstancePoolConfig();
    
    /**
     * 浏览器池配置
     */
    private PoolConfig pool = new PoolConfig();

    /**
     * 浏览器配置
     */
    private BrowserConfig browser = new BrowserConfig();

    /**
     * 线程池配置
     */
    private ThreadPoolConfig threadPool = new ThreadPoolConfig();

    // ==================== Getters and Setters ====================

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public boolean isHeadless() {
        return headless;
    }

    public void setHeadless(boolean headless) {
        this.headless = headless;
    }

    public InstancePoolConfig getInstancePool() {
        return instancePool;
    }

    public void setInstancePool(InstancePoolConfig instancePool) {
        this.instancePool = instancePool;
    }

    public PoolConfig getPool() {
        return pool;
    }

    public void setPool(PoolConfig pool) {
        this.pool = pool;
    }

    public BrowserConfig getBrowser() {
        return browser;
    }

    public void setBrowser(BrowserConfig browser) {
        this.browser = browser;
    }

    public ThreadPoolConfig getThreadPool() {
        return threadPool;
    }

    public void setThreadPool(ThreadPoolConfig threadPool) {
        this.threadPool = threadPool;
    }

    public boolean isDynamicPerformance() {
        return dynamicPerformance;
    }

    public void setDynamicPerformance(boolean dynamicPerformance) {
        this.dynamicPerformance = dynamicPerformance;
    }

    /**
     * 初始化后检查并应用动态配置
     * 如果启用了动态性能适配，将根据系统性能调整默认值
     */
    @PostConstruct
    public void initDynamicConfig() {
        if (!dynamicPerformance) {
            log.debug("[Playwright配置] 动态性能适配已禁用，使用配置文件中的固定值");
            return;
        }

        // 打印系统信息
        SystemCapabilityDetector.logSystemInfo();

        // 如果配置值为默认值（0或负数表示使用动态值），则使用系统推荐值
        if (pool.maxSize <= 0) {
            pool.maxSize = SystemCapabilityDetector.calculateRecommendedPoolSize();
            log.debug("[Playwright配置] 动态设置浏览器池大小: {}", pool.maxSize);
        }

        if (threadPool.coreSize <= 0) {
            threadPool.coreSize = SystemCapabilityDetector.calculateRecommendedCoreThreads();
            log.debug("[Playwright配置] 动态设置核心线程数: {}", threadPool.coreSize);
        }

        if (threadPool.maxSize <= 0) {
            threadPool.maxSize = SystemCapabilityDetector.calculateRecommendedMaxThreads();
            log.debug("[Playwright配置] 动态设置最大线程数: {}", threadPool.maxSize);
        }

        if (threadPool.queueCapacity <= 0) {
            threadPool.queueCapacity = SystemCapabilityDetector.calculateRecommendedQueueCapacity();
            log.debug("[Playwright配置] 动态设置队列容量: {}", threadPool.queueCapacity);
        }

        // 低性能系统优化
        if (SystemCapabilityDetector.isLowPerformanceSystem()) {
            log.warn("[Playwright配置] 检测到低性能系统，启用资源节约模式");
            if (!browser.disableImages) {
                browser.disableImages = true;
                log.debug("[Playwright配置] 自动禁用图片加载以节省资源");
            }
        }
    }

    // ==================== 内部配置类 ====================

    /**
     * Playwright 实例池配置
     */
    public static class InstancePoolConfig {
        /**
         * 实例池大小（Playwright 实例数量）
         * 每个实例独立加锁，支持真正的并发
         * 设置为 0 时根据 CPU 核心数自动计算：
         *   - 8核及以下：核心数
         *   - 8核以上：核心数 * 0.75
         * 建议值：
         *   - 单用户多AI：5-10个
         *   - 多用户场景：并发用户数 * 2
         *   - 最大值：32（避免资源浪费）
         */
        private int size = 0; // 0 表示使用动态计算
        
        public int getSize() {
            return size;
        }
        
        public void setSize(int size) {
            this.size = size;
        }
    }

    /**
     * 浏览器池配置
     */
    public static class PoolConfig {
        /**
         * 池最大大小（最大并发浏览器实例数）
         * 设置为 0 或负数时启用动态计算（根据系统性能自动设置）
         * 手动设置时建议值：CPU核心数 * 2
         */
        private int maxSize = 0; // 0 表示使用动态计算

        /**
         * 池最小空闲数（预热的浏览器实例数）
         */
        private int minIdle = 0;

        /**
         * 会话超时时间（毫秒），超时后自动清理
         * 默认：1小时
         */
        private long sessionTimeout = 3600000;

        /**
         * 会话清理检查间隔（毫秒）
         * 默认：5分钟
         */
        private long cleanupInterval = 300000;

        /**
         * 获取会话的等待超时时间（毫秒）
         * 默认：30秒
         */
        private long acquireTimeout = 30000;

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }

        public int getMinIdle() {
            return minIdle;
        }

        public void setMinIdle(int minIdle) {
            this.minIdle = minIdle;
        }

        public long getSessionTimeout() {
            return sessionTimeout;
        }

        public void setSessionTimeout(long sessionTimeout) {
            this.sessionTimeout = sessionTimeout;
        }

        public long getCleanupInterval() {
            return cleanupInterval;
        }

        public void setCleanupInterval(long cleanupInterval) {
            this.cleanupInterval = cleanupInterval;
        }

        public long getAcquireTimeout() {
            return acquireTimeout;
        }

        public void setAcquireTimeout(long acquireTimeout) {
            this.acquireTimeout = acquireTimeout;
        }
    }

    /**
     * 浏览器配置
     */
    public static class BrowserConfig {
        /**
         * 浏览器启动超时时间（毫秒）
         */
        private long launchTimeout = 60000;

        /**
         * 页面导航超时时间（毫秒）
         */
        private long navigationTimeout = 30000;

        /**
         * 普通页面动作默认超时时间（毫秒）
         */
        private long actionTimeout = 30000;

        /**
         * 默认视口宽度
         */
        private int viewportWidth = 1280;

        /**
         * 默认视口高度
         */
        private int viewportHeight = 720;

        /**
         * 是否禁用图片加载（减少资源消耗）
         */
        private boolean disableImages = false;

        /**
         * 是否禁用 GPU 加速
         */
        private boolean disableGpu = true;

        /**
         * 是否显式关闭 Chromium sandbox。
         * 默认关闭该选项，仅在受控容器且经过安全评审后启用。
         */
        private boolean noSandbox = false;

        /**
         * 是否允许 Engine 自动删除 Chromium 用户目录中的陈旧锁文件。
         * 默认禁用，避免误删仍被其他浏览器进程持有的锁。
         */
        private boolean cleanupStaleLocks = false;

        /**
         * 启动失败最大重试次数
         */
        private int maxRetries = 3;

        /**
         * 重试间隔基础时间（毫秒）
         */
        private long retryInterval = 2000;

        public long getLaunchTimeout() {
            return launchTimeout;
        }

        public void setLaunchTimeout(long launchTimeout) {
            this.launchTimeout = launchTimeout;
        }

        public long getNavigationTimeout() {
            return navigationTimeout;
        }

        public void setNavigationTimeout(long navigationTimeout) {
            this.navigationTimeout = navigationTimeout;
        }

        public long getActionTimeout() {
            return actionTimeout;
        }

        public void setActionTimeout(long actionTimeout) {
            this.actionTimeout = actionTimeout;
        }

        public int getViewportWidth() {
            return viewportWidth;
        }

        public void setViewportWidth(int viewportWidth) {
            this.viewportWidth = viewportWidth;
        }

        public int getViewportHeight() {
            return viewportHeight;
        }

        public void setViewportHeight(int viewportHeight) {
            this.viewportHeight = viewportHeight;
        }

        public boolean isDisableImages() {
            return disableImages;
        }

        public void setDisableImages(boolean disableImages) {
            this.disableImages = disableImages;
        }

        public boolean isDisableGpu() {
            return disableGpu;
        }

        public void setDisableGpu(boolean disableGpu) {
            this.disableGpu = disableGpu;
        }

        public boolean isNoSandbox() {
            return noSandbox;
        }

        public void setNoSandbox(boolean noSandbox) {
            this.noSandbox = noSandbox;
        }

        public boolean isCleanupStaleLocks() {
            return cleanupStaleLocks;
        }

        public void setCleanupStaleLocks(boolean cleanupStaleLocks) {
            this.cleanupStaleLocks = cleanupStaleLocks;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public long getRetryInterval() {
            return retryInterval;
        }

        public void setRetryInterval(long retryInterval) {
            this.retryInterval = retryInterval;
        }
    }

    /**
     * 线程池配置
     */
    public static class ThreadPoolConfig {
        /**
         * 核心线程数
         * 设置为 0 或负数时启用动态计算
         */
        private int coreSize = 0; // 0 表示使用动态计算

        /**
         * 最大线程数
         * 设置为 0 或负数时启用动态计算
         */
        private int maxSize = 0; // 0 表示使用动态计算

        /**
         * 线程空闲时间（秒）
         */
        private int keepAliveSeconds = 60;

        /**
         * 任务队列大小
         * 设置为 0 或负数时启用动态计算
         */
        private int queueCapacity = 0; // 0 表示使用动态计算

        public int getCoreSize() {
            return coreSize;
        }

        public void setCoreSize(int coreSize) {
            this.coreSize = coreSize;
        }

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }

        public int getKeepAliveSeconds() {
            return keepAliveSeconds;
        }

        public void setKeepAliveSeconds(int keepAliveSeconds) {
            this.keepAliveSeconds = keepAliveSeconds;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }
    }
}
