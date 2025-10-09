package com.playwright.utils.common;

import org.springframework.stereotype.Component;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

/**
 * 浏览器并发管理器
 * 基于CPU核心数控制同时运行的浏览器自动化任务数量
 * 
 * @author cube-engine
 * @date 2025/8/11
 */
@Component
public class BrowserConcurrencyManager {
    
    // CPU核心数
    private final int CPU_CORES = Runtime.getRuntime().availableProcessors();

    // 🔥 优化：并发限制调整为两倍CPU核心数，提高并发处理能力
    private final int MAX_CONCURRENT_BROWSERS;
    
    // 线程池执行器
    private final ThreadPoolExecutor executor;
    
    // 当前运行的任务数量
    private final AtomicInteger runningTasks = new AtomicInteger(0);
    
    // 🔥 优化：增大队列大小，提高队列稳定性和并发处理能力
    private final int QUEUE_SIZE = Math.max(200, CPU_CORES * 10); // 队列大小至少200或CPU核心数*10
    
    // 任务执行状态跟踪 - 防止重复执行
    private final Set<String> executingTasks = ConcurrentHashMap.newKeySet();
    
    /**
     * 优先级任务包装器
     * 用于支持任务优先级排序
     */
    public static class PriorityTask implements Runnable, Comparable<PriorityTask> {
        private final Runnable task;
        private final String taskName;
        private final String userId;
        private final int priority;
        private final long submissionTime;
        
        // 优先级常量
        public static final int PRIORITY_HIGH = 1;      // 高优先级（百家号状态检测）
        public static final int PRIORITY_NORMAL = 5;    // 普通优先级
        public static final int PRIORITY_LOW = 10;      // 低优先级
        
        public PriorityTask(Runnable task, String taskName, String userId, int priority) {
            this.task = task;
            this.taskName = taskName;
            this.userId = userId;
            this.priority = priority;
            this.submissionTime = System.nanoTime();
        }
        
        @Override
        public void run() {
            task.run();
        }
        
        @Override
        public int compareTo(PriorityTask other) {
            // 首先按优先级排序（数字越小优先级越高）
            int priorityComparison = Integer.compare(this.priority, other.priority);
            if (priorityComparison != 0) {
                return priorityComparison;
            }
            // 优先级相同时按提交时间排序（FIFO）
            return Long.compare(this.submissionTime, other.submissionTime);
        }
        
        public String getTaskName() { return taskName; }
        public String getUserId() { return userId; }
        public int getPriority() { return priority; }
    }
    
    public BrowserConcurrencyManager() {
        // 🔥 核心优化：计算最大并发数为两倍CPU核心数，大幅提升并发处理能力
        this.MAX_CONCURRENT_BROWSERS = CPU_CORES * 2;
        
        // 🔥 优化：创建线程池，增强并发能力和稳定性
        this.executor = new ThreadPoolExecutor(
            MAX_CONCURRENT_BROWSERS,           // 核心线程数：两倍CPU核心数
            MAX_CONCURRENT_BROWSERS + Math.max(2, CPU_CORES / 2), // 最大线程数：核心线程数 + 额外缓冲
            300L,                              // 🔥 优化：增加线程空闲存活时间到300秒，减少线程频繁创建销毁
            TimeUnit.SECONDS,                  // 时间单位
            new PriorityBlockingQueue<>(QUEUE_SIZE), // 使用优先级队列，容量大幅提升
            new ThreadFactory() {              // 线程工厂
                private final AtomicInteger threadNumber = new AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "BrowserTask-" + threadNumber.getAndIncrement());
                    thread.setDaemon(false);
                    // 设置线程优先级为正常，避免影响系统其他任务
                    thread.setPriority(Thread.NORM_PRIORITY);
                    return thread;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略：调用者运行，确保任务不丢失
        );
        
        // 预启动核心线程，减少首次任务执行延迟
        executor.prestartAllCoreThreads();
        
    }
    
    /**
     * 提交浏览器任务（普通优先级）
     * @param task 要执行的任务
     * @param taskName 任务名称（用于日志）
     * @param userId 用户ID（用于日志）
     * @return Future对象，可用于获取执行结果或取消任务
     */
    public Future<?> submitBrowserTask(Runnable task, String taskName, String userId) {
        return submitBrowserTaskWithPriority(task, taskName, userId, PriorityTask.PRIORITY_NORMAL);
    }
    
    /**
     * 提交浏览器任务（指定优先级）
     * @param task 要执行的任务
     * @param taskName 任务名称（用于日志）
     * @param userId 用户ID（用于日志）
     * @param priority 任务优先级
     * @return Future对象，可用于获取执行结果或取消任务
     */
    public Future<?> submitBrowserTaskWithPriority(Runnable task, String taskName, String userId, int priority) {
        String priorityDesc = getPriorityDescription(priority);
        
        // 创建包装任务，添加监控和异常处理
        Runnable wrappedTask = () -> {
            int currentRunning = runningTasks.incrementAndGet();
            
            try {
                task.run();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                int remainingRunning = runningTasks.decrementAndGet();
            }
        };
        
        // 创建优先级任务并提交
        PriorityTask priorityTask = new PriorityTask(wrappedTask, taskName, userId, priority);
        executor.execute(priorityTask);
        
        // 返回一个完成的Future，因为我们已经提交了任务
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * 获取优先级描述
     */
    private String getPriorityDescription(int priority) {
        switch (priority) {
            case PriorityTask.PRIORITY_HIGH: return "高优先级";
            case PriorityTask.PRIORITY_NORMAL: return "普通优先级";
            case PriorityTask.PRIORITY_LOW: return "低优先级";
            default: return "自定义优先级(" + priority + ")";
        }
    }
    
    /**
     * 提交高优先级任务（百家号/知乎状态检测专用）
     */
    public Future<?> submitHighPriorityTask(Runnable task, String taskName, String userId) {
        return submitBrowserTaskWithPriority(task, taskName, userId, PriorityTask.PRIORITY_HIGH);
    }
    
    /**
     * 提交低优先级任务
     */
    public Future<?> submitLowPriorityTask(Runnable task, String taskName, String userId) {
        return submitBrowserTaskWithPriority(task, taskName, userId, PriorityTask.PRIORITY_LOW);
    }
    
    /**
     * 提交有返回值的浏览器任务
     * @param task 要执行的任务
     * @param taskName 任务名称
     * @param userId 用户ID
     * @return Future对象，包含任务执行结果
     */
    public <T> Future<T> submitBrowserTask(Callable<T> task, String taskName, String userId) {
        
        return executor.submit(() -> {
            int currentRunning = runningTasks.incrementAndGet();
            
            try {
                T result = task.call();
                return result;
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            } finally {
                int remainingRunning = runningTasks.decrementAndGet();
            }
        });
    }
    
    /**
     * 提交极速状态检测任务（专用于登录状态检测）
     * 使用轻量级浏览器配置，减少CPU和内存占用
     */
    public Future<?> submitFastLoginCheckTask(Runnable task, String taskName, String userId, String platform) {
        String priorityDesc = "极速检测";
        
        // 包装任务以记录性能指标
        Runnable wrappedTask = () -> {
            long startTime = System.currentTimeMillis();
            int currentRunning = runningTasks.incrementAndGet();
            
            try {
                task.run();
                long duration = System.currentTimeMillis() - startTime;
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                e.printStackTrace();
            } finally {
                int remainingRunning = runningTasks.decrementAndGet();
            }
        };
        
        // 创建优先级任务并提交
        PriorityTask priorityTask = new PriorityTask(wrappedTask, taskName + "_FAST_CHECK", userId, PriorityTask.PRIORITY_HIGH);
        executor.execute(priorityTask);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 生成任务唯一标识
     */
    private String generateTaskKey(String taskName, String userId, String userPrompt) {
        // 使用任务名称、用户ID和用户输入的前50个字符生成唯一标识
        String promptPrefix = userPrompt != null && userPrompt.length() > 50 ? 
            userPrompt.substring(0, 50) : (userPrompt != null ? userPrompt : "");
        return taskName + ":" + userId + ":" + promptPrefix.hashCode();
    }

    /**
     * 检查并标记任务为执行中
     */
    private boolean markTaskAsExecuting(String taskKey) {
        return executingTasks.add(taskKey);
    }

    /**
     * 标记任务执行完成
     */
    private void markTaskAsCompleted(String taskKey) {
        executingTasks.remove(taskKey);
    }

    /**
     * 提交浏览器任务（指定优先级）- 带重复检测
     * @param task 要执行的任务
     * @param taskName 任务名称（用于日志）
     * @param userId 用户ID（用于日志）
     * @param priority 任务优先级
     * @param userPrompt 用户输入内容（用于去重）
     * @return Future对象，可用于获取执行结果或取消任务
     */
    public Future<?> submitBrowserTaskWithDeduplication(Runnable task, String taskName, String userId, int priority, String userPrompt) {
        String taskKey = generateTaskKey(taskName, userId, userPrompt);
        
        // 检查是否已有相同任务在执行
        if (!markTaskAsExecuting(taskKey)) {
            return CompletableFuture.completedFuture(null);
        }

        String priorityDesc = getPriorityDescription(priority);
        
        // 创建包装任务，添加监控和异常处理
        Runnable wrappedTask = () -> {
            int currentRunning = runningTasks.incrementAndGet();
            
            try {
                task.run();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                // 标记任务执行完成，允许后续相同任务执行
                markTaskAsCompleted(taskKey);
                
                int remainingRunning = runningTasks.decrementAndGet();
            }
        };
        
        // 创建优先级任务并提交
        PriorityTask priorityTask = new PriorityTask(wrappedTask, taskName, userId, priority);
        executor.execute(priorityTask);
        
        // 返回一个完成的Future，因为我们已经提交了任务
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * 获取当前系统状态信息
     */
    public ConcurrencyStatus getStatus() {
        return new ConcurrencyStatus(
            CPU_CORES,
            MAX_CONCURRENT_BROWSERS,
            runningTasks.get(),
            executor.getQueue().size(),
            executor.getActiveCount(),
            executor.getCompletedTaskCount()
        );
    }
    
    /**
     * 获取是否可以立即执行任务（不需要等待）
     */
    public boolean canExecuteImmediately() {
        return runningTasks.get() < MAX_CONCURRENT_BROWSERS;
    }
    
    /**
     * 获取系统负载情况
     */
    public double getSystemLoad() {
        return (double) runningTasks.get() / MAX_CONCURRENT_BROWSERS;
    }
    
    /**
     * 关闭管理器（应用程序关闭时调用）
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                // 再次等待一段时间确保关闭
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                }
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 并发状态信息类
     */
    public static class ConcurrencyStatus {
        private final int cpuCores;
        private final int maxConcurrent;
        private final int currentRunning;
        private final int queueSize;
        private final int activeThreads;
        private final long completedTasks;
        
        public ConcurrencyStatus(int cpuCores, int maxConcurrent, int currentRunning, 
                               int queueSize, int activeThreads, long completedTasks) {
            this.cpuCores = cpuCores;
            this.maxConcurrent = maxConcurrent;
            this.currentRunning = currentRunning;
            this.queueSize = queueSize;
            this.activeThreads = activeThreads;
            this.completedTasks = completedTasks;
        }
        
        // Getters
        public int getCpuCores() { return cpuCores; }
        public int getMaxConcurrent() { return maxConcurrent; }
        public int getCurrentRunning() { return currentRunning; }
        public int getQueueSize() { return queueSize; }
        public int getActiveThreads() { return activeThreads; }
        public long getCompletedTasks() { return completedTasks; }
        
        @Override
        public String toString() {
            return String.format(
                "ConcurrencyStatus{CPU核心数=%d, 最大并发=%d, 当前运行=%d, 队列大小=%d, 活跃线程=%d, 已完成任务=%d}",
                cpuCores, maxConcurrent, currentRunning, queueSize, activeThreads, completedTasks
            );
        }
    }
} 