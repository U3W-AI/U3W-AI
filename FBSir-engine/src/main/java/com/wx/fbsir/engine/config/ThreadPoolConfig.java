package com.wx.fbsir.engine.config;

import com.wx.fbsir.engine.util.SystemPerformanceDetector;
import com.wx.fbsir.engine.util.SystemPerformanceDetector.PerformanceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池配置
 * 
 * 统一管理线程池，避免线程泄漏
 * 支持优雅关闭，确保任务执行完成
 *
 * @author FBSir
 * @date 2025-12-15
 */
@Configuration
@EnableAsync
public class ThreadPoolConfig {

    private static final Logger log = LoggerFactory.getLogger(ThreadPoolConfig.class);

    private ThreadPoolTaskExecutor messageExecutor;
    private ThreadPoolTaskScheduler taskScheduler;

    /**
     * 消息处理线程池
     * 
     * 用于处理 WebSocket 接收到的消息
     */
    @Bean("messageExecutor")
    public ThreadPoolTaskExecutor messageExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 智能检测系统性能（兼容N100/N95/M系列/移动端CPU）
        PerformanceConfig perfConfig = SystemPerformanceDetector.detectPerformance();
        
        int coreSize = perfConfig.getCoreThreads();
        int maxSize = perfConfig.getMaxThreads();
        int queueCapacity = perfConfig.getQueueCapacity();
        
        log.info("[线程池] 性能检测: {} - 核心: {}, 最大: {}, 队列: {}", 
            perfConfig.getReason(), coreSize, maxSize, queueCapacity);
        
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(300);
        executor.setThreadNamePrefix("msg-handler-");
        // 使用 AbortPolicy，让任务排队管理器处理拒绝
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        
        this.messageExecutor = executor;
        
        // 性能级别提示
        switch (perfConfig.getLevel()) {
            case LOW_CPU:
                log.warn("[线程池] 检测到低频CPU（N100/N95等），已限制并发以保护CPU");
                break;
            case MOBILE:
                log.info("[线程池] 检测到移动端CPU，优化单核性能");
                break;
            case HIGH:
                log.info("[线程池] 检测到高性能CPU，发挥多核优势");
                break;
            case VERY_LOW:
                log.warn("[线程池] 检测到极低性能环境，已最大化限制并发");
                break;
            default:
                break;
        }
        
        return executor;
    }

    /**
     * 定时任务调度器
     * 
     * 用于心跳检测、重连调度等
     */
    @Bean("taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.setErrorHandler(throwable -> 
            log.error("[定时任务] 执行异常 - 错误: {}", throwable.getMessage(), throwable)
        );
        scheduler.initialize();
        
        this.taskScheduler = scheduler;
        log.debug("[线程池] 定时任务调度器初始化完成");
        
        return scheduler;
    }

    /**
     * 优雅关闭线程池
     */
    @PreDestroy
    public void shutdown() {
        if (messageExecutor != null) {
            messageExecutor.shutdown();
        }
        if (taskScheduler != null) {
            taskScheduler.shutdown();
        }
    }
}
