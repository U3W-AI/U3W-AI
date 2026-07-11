package com.wx.fbsir.engine.playwright.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;

/**
 * 系统性能检测器
 * 
 * 根据当前系统的 CPU、内存等资源情况，动态计算推荐的配置参数。
 * 确保框架在不同性能的机器上都能稳定运行。
 *
 * @author FBSir
 * @date 2025-12-16
 */
public final class SystemCapabilityDetector {

    private static final Logger log = LoggerFactory.getLogger(SystemCapabilityDetector.class);

    /**
     * 每个浏览器实例预估占用内存（MB）
     * 无头模式约 150-200MB，有头模式约 250-350MB
     */
    private static final long BROWSER_MEMORY_MB = 250;

    /**
     * 系统预留内存（MB）- 留给系统和其他应用
     */
    private static final long SYSTEM_RESERVED_MEMORY_MB = 512;

    /**
     * 最小浏览器池大小（确保即使低配置也能运行）
     */
    private static final int MIN_POOL_SIZE = 1;

    /**
     * 最大浏览器池大小
     */
    private static final int MAX_POOL_SIZE = 50;
    
    /**
     * 最小线程数
     */
    private static final int MIN_THREADS = 1;
    
    /**
     * 最小队列大小
     */
    private static final int MIN_QUEUE_SIZE = 10;

    private SystemCapabilityDetector() {
        // 工具类禁止实例化
    }

    /**
     * 获取 CPU 核心数
     *
     * @return CPU 核心数
     */
    public static int getCpuCores() {
        return Runtime.getRuntime().availableProcessors();
    }

    /**
     * 获取 JVM 最大可用内存（MB）
     *
     * @return 最大内存 MB
     */
    public static long getMaxMemoryMb() {
        return Runtime.getRuntime().maxMemory() / (1024 * 1024);
    }

    /**
     * 获取 JVM 当前已用内存（MB）
     *
     * @return 已用内存 MB
     */
    public static long getUsedMemoryMb() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
    }

    /**
     * 获取 JVM 可用内存（MB）
     *
     * @return 可用内存 MB
     */
    public static long getAvailableMemoryMb() {
        return getMaxMemoryMb() - getUsedMemoryMb();
    }

    /**
     * 获取系统负载（如果可用）
     *
     * @return 系统负载，-1 表示不可用
     */
    public static double getSystemLoad() {
        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            return osBean.getSystemLoadAverage();
        } catch (Exception e) {
            log.debug("[系统检测] 无法获取系统负载: {}", e.getMessage());
            return -1;
        }
    }

    /**
     * 根据系统性能计算推荐的浏览器池最大大小
     * 
     * 计算规则（保守策略，确保稳定性）：
     * 1. 基于 CPU 核心数：核心数 * 1.5（保守估计）
     * 2. 基于内存：(可用内存 - 预留) / 每个浏览器占用
     * 3. 取两者较小值，确保不会资源耗尽
     * 4. 低配置系统特殊处理
     *
     * @return 推荐的池最大大小
     */
    public static int calculateRecommendedPoolSize() {
        int cpuCores = getCpuCores();
        long maxMemoryMb = getMaxMemoryMb();

        // 基于 CPU 计算（保守策略）
        int cpuBasedSize = (int) Math.ceil(cpuCores * 1.5);

        // 基于内存计算
        long availableForBrowsers = Math.max(0, maxMemoryMb - SYSTEM_RESERVED_MEMORY_MB);
        int memoryBasedSize = (int) (availableForBrowsers / BROWSER_MEMORY_MB);
        
        // 确保内存计算结果至少为1
        memoryBasedSize = Math.max(1, memoryBasedSize);

        // 取较小值
        int recommended = Math.min(cpuBasedSize, memoryBasedSize);

        // 低配置系统特殊处理：确保至少能运行1个浏览器
        if (isLowPerformanceSystem()) {
            recommended = Math.min(recommended, 3); // 低配最多3个
            log.debug("[系统检测] 低配置系统，限制浏览器池大小为: {}", recommended);
        }

        // 限制在合理范围内
        recommended = Math.max(MIN_POOL_SIZE, Math.min(MAX_POOL_SIZE, recommended));

        log.debug("[系统检测] 计算浏览器池大小 - CPU基准: {}, 内存基准: {}, 最终: {}", 
            cpuBasedSize, memoryBasedSize, recommended);

        return recommended;
    }

    /**
     * 根据系统性能计算推荐的线程池核心大小
     *
     * @return 推荐的核心线程数
     */
    public static int calculateRecommendedCoreThreads() {
        int cpuCores = getCpuCores();
        int recommended;
        
        if (isLowPerformanceSystem()) {
            // 低配置系统：最小化线程数
            recommended = Math.max(MIN_THREADS, Math.min(2, cpuCores));
        } else {
            // 正常系统：Playwright 任务是 IO 密集型
            recommended = Math.max(MIN_THREADS, cpuCores);
        }
        
        log.debug("[系统检测] 推荐核心线程数: {}", recommended);
        return recommended;
    }

    /**
     * 根据系统性能计算推荐的线程池最大大小
     *
     * @return 推荐的最大线程数
     */
    public static int calculateRecommendedMaxThreads() {
        int cpuCores = getCpuCores();
        int recommended;
        
        if (isLowPerformanceSystem()) {
            // 低配置系统：限制最大线程数
            recommended = Math.max(2, Math.min(4, cpuCores * 2));
        } else {
            // IO 密集型任务，最大线程数可以是核心数的 1.5-2 倍
            recommended = Math.max(2, (int) Math.ceil(cpuCores * 1.5));
        }
        
        log.debug("[系统检测] 推荐最大线程数: {}", recommended);
        return recommended;
    }

    /**
     * 根据系统性能计算推荐的任务队列大小
     *
     * @return 推荐的队列大小
     */
    public static int calculateRecommendedQueueCapacity() {
        int poolSize = calculateRecommendedPoolSize();
        int recommended;
        
        if (isLowPerformanceSystem()) {
            // 低配置系统：较小的队列避免内存压力
            recommended = Math.max(MIN_QUEUE_SIZE, poolSize * 5);
        } else {
            // 队列大小为池大小的 5-10 倍，允许一定的任务积压
            recommended = Math.max(MIN_QUEUE_SIZE, poolSize * 10);
        }
        
        log.debug("[系统检测] 推荐队列大小: {}", recommended);
        return recommended;
    }

    /**
     * 判断当前系统是否为低性能模式
     * 低性能：CPU <= 2 核 或 内存 <= 2GB
     *
     * @return true 如果是低性能系统
     */
    public static boolean isLowPerformanceSystem() {
        return getCpuCores() <= 2 || getMaxMemoryMb() <= 2048;
    }
    
    /**
     * 判断当前系统是否为极低性能模式
     * 极低性能：CPU = 1 核 或 内存 <= 1GB
     *
     * @return true 如果是极低性能系统
     */
    public static boolean isVeryLowPerformanceSystem() {
        return getCpuCores() == 1 || getMaxMemoryMb() <= 1024;
    }

    /**
     * 判断当前系统是否为高性能模式
     * 高性能：CPU >= 8 核 且 内存 >= 8GB
     *
     * @return true 如果是高性能系统
     */
    public static boolean isHighPerformanceSystem() {
        return getCpuCores() >= 8 && getMaxMemoryMb() >= 8192;
    }
    
    /**
     * 获取低性能系统的推荐浏览器参数
     * 用于减少资源消耗
     *
     * @return 是否应禁用图片加载
     */
    public static boolean shouldDisableImages() {
        return isLowPerformanceSystem();
    }

    /**
     * 获取系统性能等级描述
     *
     * @return 性能等级描述
     */
    public static String getPerformanceLevel() {
        if (isHighPerformanceSystem()) {
            return "高性能";
        } else if (isLowPerformanceSystem()) {
            return "低性能";
        } else {
            return "中等性能";
        }
    }

    /**
     * 打印系统信息摘要
     */
    public static void logSystemInfo() {
        int poolSize = calculateRecommendedPoolSize();
        int coreThreads = calculateRecommendedCoreThreads();
        int maxThreads = calculateRecommendedMaxThreads();
        int queueSize = calculateRecommendedQueueCapacity();
        
        log.debug("[系统检测] CPU 核心数: {}", getCpuCores());
        log.debug("[系统检测] JVM 最大内存: {} MB", getMaxMemoryMb());
        log.debug("[系统检测] 性能等级: {}", getPerformanceLevel());
        log.debug("[系统检测] 推荐浏览器池: {} 个", poolSize);
        log.debug("[系统检测] 推荐线程池: {}-{} 个", coreThreads, maxThreads);
        log.debug("[系统检测] 推荐队列大小: {} 个", queueSize);
        
        if (isLowPerformanceSystem()) {
            log.warn("[系统检测] 检测到低性能系统，已自动调整配置以确保稳定性");
            if (shouldDisableImages()) {
                log.debug("[系统检测] 建议禁用图片加载以减少资源消耗");
            }
        }
    }
}
