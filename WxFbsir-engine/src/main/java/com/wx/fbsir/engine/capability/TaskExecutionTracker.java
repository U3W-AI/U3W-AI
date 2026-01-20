package com.wx.fbsir.engine.capability;

import com.wx.fbsir.engine.playwright.core.PlaywrightInstancePool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 企业级任务执行追踪器
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 设计原则
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 1. 实例级并发控制：有N个Playwright实例，就支持N个任务并发
 * 2. 资源最大化利用：只要有空闲实例，立即执行任务
 * 3. 任务防重复：同一用户+能力组合不允许重复提交
 * 4. 自动超时清理：长时间运行的任务自动释放资源
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 并发模型
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 全局并发限制：8个Playwright实例 → 最多8个任务同时执行
 * 防重复提交：同一用户+能力组合，同一时间只能有1个任务
 * 
 * 示例场景：
 * - 用户A执行 DeepSeek登录 + YuanQi登录 + 节点编辑等多个任务 → 完全并发 ✅
 * - 用户A连续点击2次 DeepSeek登录 → 第2次被拒绝（防重复）❌
 * - 8个不同任务同时执行 → 实例利用率100%
 * 
 * @author wxfbsir
 * @date 2026-01-20
 */
@Component
public class TaskExecutionTracker {
    
    private static final Logger log = LoggerFactory.getLogger(TaskExecutionTracker.class);
    
    private final PlaywrightInstancePool instancePool;
    
    /**
     * 全局并发控制信号量（基于Playwright实例数量）
     */
    private Semaphore globalSemaphore;
    
    /**
     * 任务超时时间（毫秒）- 超过此时间视为任务异常，自动清理
     */
    private static final long TASK_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(5);
    
    /**
     * 正在执行的任务详情
     * key = taskId (UUID), value = TaskInfo
     */
    private final ConcurrentHashMap<String, TaskInfo> executingTasks = new ConcurrentHashMap<>();
    
    
    /**
     * 防重复提交映射
     * key = userId:capabilityType, value = taskId
     */
    private final ConcurrentHashMap<String, String> duplicateCheck = new ConcurrentHashMap<>();
    
    /**
     * 全局任务计数器
     */
    private final AtomicInteger totalTaskCount = new AtomicInteger(0);
    private final AtomicInteger completedTaskCount = new AtomicInteger(0);
    private final AtomicInteger rejectedTaskCount = new AtomicInteger(0);
    
    public TaskExecutionTracker(PlaywrightInstancePool instancePool) {
        this.instancePool = instancePool;
        this.globalSemaphore = new Semaphore(instancePool.getPoolSize(), true);
        log.info("[任务追踪] 初始化完成 - 全局并发限制: {} (基于Playwright实例池)", 
            instancePool.getPoolSize());
    }
    
    /**
     * 尝试开始执行任务
     * 
     * @param userId 用户ID
     * @param capabilityType 能力类型
     * @return TaskStartResult
     */
    public TaskStartResult tryStart(String userId, String capabilityType) {
        // 1. 检查是否重复提交（同一用户+能力）
        String duplicateKey = buildDuplicateKey(userId, capabilityType);
        if (duplicateCheck.containsKey(duplicateKey)) {
            rejectedTaskCount.incrementAndGet();
            log.warn("[任务追踪] 拒绝重复提交 - 用户: {}, 能力: {}", userId, capabilityType);
            return TaskStartResult.duplicate();
        }
        
        // 2. 尝试获取全局信号量（非阻塞）
        if (!globalSemaphore.tryAcquire()) {
            rejectedTaskCount.incrementAndGet();
            log.warn("[任务追踪] 拒绝执行，全局并发已满 - 当前执行: {}/{}", 
                executingTasks.size(), instancePool.getPoolSize());
            return TaskStartResult.globalLimitExceeded(executingTasks.size());
        }
        
        // 4. 创建任务并开始执行
        String taskId = java.util.UUID.randomUUID().toString();
        TaskInfo taskInfo = new TaskInfo(taskId, userId, capabilityType);
        
        executingTasks.put(taskId, taskInfo);
        duplicateCheck.put(duplicateKey, taskId);
        totalTaskCount.incrementAndGet();
        
        log.info("[任务追踪] 任务已开始 - 用户: {}, 能力: {}, 任务ID: {}, 全局并发: {}/{}", 
            userId, capabilityType, taskId.substring(0, 8), 
            executingTasks.size(), instancePool.getPoolSize());
        
        return TaskStartResult.success(taskId);
    }
    
    /**
     * 标记任务完成
     * 
     * @param userId 用户ID
     * @param capabilityType 能力类型
     */
    public void finish(String userId, String capabilityType) {
        String duplicateKey = buildDuplicateKey(userId, capabilityType);
        String taskId = duplicateCheck.remove(duplicateKey);
        
        if (taskId == null) {
            log.warn("[任务追踪] 任务完成时未找到任务ID - 用户: {}, 能力: {}", userId, capabilityType);
            return;
        }
        
        TaskInfo taskInfo = executingTasks.remove(taskId);
        if (taskInfo != null) {
            long duration = System.currentTimeMillis() - taskInfo.startTime;
            completedTaskCount.incrementAndGet();
            
            // 释放全局信号量
            globalSemaphore.release();
            
            log.info("[任务追踪] 任务已完成 - 用户: {}, 能力: {}, 任务ID: {}, 耗时: {}ms, 全局并发: {}/{}", 
                userId, capabilityType, taskId.substring(0, 8), duration,
                executingTasks.size(), instancePool.getPoolSize());
        }
    }
    
    /**
     * 定时清理超时任务
     */
    @Scheduled(fixedDelay = 60000) // 每分钟执行一次
    public void cleanupTimeoutTasks() {
        long now = System.currentTimeMillis();
        int cleaned = 0;
        
        for (Map.Entry<String, TaskInfo> entry : executingTasks.entrySet()) {
            TaskInfo taskInfo = entry.getValue();
            long elapsed = now - taskInfo.startTime;
            
            if (elapsed > TASK_TIMEOUT_MS) {
                String taskId = entry.getKey();
                executingTasks.remove(taskId);
                duplicateCheck.remove(buildDuplicateKey(taskInfo.userId, taskInfo.capabilityType));
                
                // 释放全局信号量
                globalSemaphore.release();
                
                log.warn("[任务追踪] 清理超时任务 - 用户: {}, 能力: {}, 任务ID: {}, 超时: {}ms", 
                    taskInfo.userId, taskInfo.capabilityType, taskId.substring(0, 8), elapsed);
                cleaned++;
            }
        }
        
        if (cleaned > 0) {
            log.warn("[任务追踪] 清理超时任务数: {}, 当前执行: {}/{}", 
                cleaned, executingTasks.size(), instancePool.getPoolSize());
        }
    }
    
    /**
     * 获取统计信息
     */
    public String getStats() {
        return String.format("全局并发: %d/%d, 总任务: %d, 完成: %d, 拒绝: %d, 成功率: %.2f%%",
            executingTasks.size(), instancePool.getPoolSize(),
            totalTaskCount.get(), completedTaskCount.get(), rejectedTaskCount.get(),
            totalTaskCount.get() > 0 ? (completedTaskCount.get() * 100.0 / totalTaskCount.get()) : 0);
    }
    
    /**
     * 获取正在执行的任务数
     */
    public int getExecutingCount() {
        return executingTasks.size();
    }
    
    
    private String buildDuplicateKey(String userId, String capabilityType) {
        return userId + ":" + capabilityType;
    }
    
    /**
     * 任务信息
     */
    private static class TaskInfo {
        final String taskId;
        final String userId;
        final String capabilityType;
        final long startTime;
        
        TaskInfo(String taskId, String userId, String capabilityType) {
            this.taskId = taskId;
            this.userId = userId;
            this.capabilityType = capabilityType;
            this.startTime = System.currentTimeMillis();
        }
    }
    
    /**
     * 任务启动结果
     */
    public static class TaskStartResult {
        public final boolean success;
        public final String taskId;
        public final String errorCode;
        public final String errorMessage;
        
        private TaskStartResult(boolean success, String taskId, String errorCode, String errorMessage) {
            this.success = success;
            this.taskId = taskId;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }
        
        public static TaskStartResult success(String taskId) {
            return new TaskStartResult(true, taskId, null, null);
        }
        
        public static TaskStartResult duplicate() {
            return new TaskStartResult(false, null, "DUPLICATE_REQUEST", 
                "任务正在执行中，请勿重复提交。请等待当前任务完成后再试。");
        }
        
        
        public static TaskStartResult globalLimitExceeded(int currentCount) {
            return new TaskStartResult(false, null, "GLOBAL_LIMIT_EXCEEDED", 
                String.format("系统并发已满，当前执行: %d个任务。请稍后重试。", currentCount));
        }
    }
}
