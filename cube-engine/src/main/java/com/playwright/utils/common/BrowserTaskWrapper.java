package com.playwright.utils.common;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.concurrent.Future;

/**
 * 浏览器任务包装器
 * 提供便捷方法来提交浏览器自动化任务，集成任务状态管理
 */
@Component
public class BrowserTaskWrapper {
    
    @Autowired
    private BrowserConcurrencyManager concurrencyManager;
    
    /**
     * 包装现有的Runnable任务，使其使用并发管理器（普通优先级）
     * @param task 原始任务
     * @param taskName 任务名称
     * @param userId 用户ID
     */
    public Future<?> submitTask(Runnable task, String taskName, String userId) {
        return submitTaskWithPriority(task, taskName, userId, BrowserConcurrencyManager.PriorityTask.PRIORITY_NORMAL);
    }
    
    /**
     * 🔥 新增：包装任务并集成状态管理，确保任务完成后正确更新浏览器实例状态
     * @param task 原始任务
     * @param taskName 任务名称
     * @param userId 用户ID
     * @param priority 任务优先级
     */
    public Future<?> submitTaskWithPriority(Runnable task, String taskName, String userId, int priority) {
        // 包装任务，添加状态管理
        Runnable wrappedTask = () -> {
            try {
                // 执行原始任务
                task.run();
            } finally {
                // 🔥 关键：确保任务完成后标记状态，允许浏览器实例正确管理
                BrowserContextFactory.markTaskComplete(userId);
            }
        };
        
        return concurrencyManager.submitBrowserTaskWithPriority(wrappedTask, taskName, userId, priority);
    }
    
    /**
     * 🔥 新增：提交高优先级任务（AI状态检测专用）
     */
    public Future<?> submitHighPriorityTask(Runnable task, String taskName, String userId) {
        return submitTaskWithPriority(task, taskName, userId, BrowserConcurrencyManager.PriorityTask.PRIORITY_HIGH);
    }
    
    /**
     * 🔥 新增：提交低优先级任务
     */
    public Future<?> submitLowPriorityTask(Runnable task, String taskName, String userId) {
        return submitTaskWithPriority(task, taskName, userId, BrowserConcurrencyManager.PriorityTask.PRIORITY_LOW);
    }
    
    /**
     * 🔥 新增：提交带去重的任务
     */
    public Future<?> submitTaskWithDeduplication(Runnable task, String taskName, String userId, String userPrompt) {
        // 包装任务，添加状态管理
        Runnable wrappedTask = () -> {
            try {
                task.run();
            } finally {
                BrowserContextFactory.markTaskComplete(userId);
            }
        };
        
        return concurrencyManager.submitBrowserTaskWithDeduplication(
            wrappedTask, taskName, userId, BrowserConcurrencyManager.PriorityTask.PRIORITY_NORMAL, userPrompt);
    }
    
    /**
     * 🔥 新增：延长用户的浏览器实例时间（当任务还在运行时）
     */
    public void extendBrowserInstanceIfNeeded(String userId) {
        BrowserContextFactory.extendContextIfTaskRunning(userId);
    }
    
    /**
     * 🔥 新增：检查任务是否可以立即执行
     */
    public boolean canExecuteImmediately() {
        return concurrencyManager.canExecuteImmediately();
    }
    
    /**
     * 🔥 新增：获取系统当前负载情况
     */
    public double getSystemLoad() {
        return concurrencyManager.getSystemLoad();
    }
    
    /**
     * 获取并发管理器状态
     */
    public BrowserConcurrencyManager.ConcurrencyStatus getStatus() {
        return concurrencyManager.getStatus();
    }
    
    /**
     * 快速获取管理器状态并打印（一行显示）
     */
    public void printStatus() {
        BrowserConcurrencyManager.ConcurrencyStatus status = getStatus();
        System.out.println(String.format(
            "[浏览器并发状态] CPU核心数:%d | 最大并发数:%d | 当前运行任务:%d | 队列中等待任务:%d | 活跃线程数:%d | 已完成任务总数:%d | 系统负载:%.2f%% | 可立即执行:%s",
            status.getCpuCores(),
            status.getMaxConcurrent(),
            status.getCurrentRunning(),
            status.getQueueSize(),
            status.getActiveThreads(),
            status.getCompletedTasks(),
            getSystemLoad() * 100,
            canExecuteImmediately() ? "是" : "否"
        ));
    }
} 