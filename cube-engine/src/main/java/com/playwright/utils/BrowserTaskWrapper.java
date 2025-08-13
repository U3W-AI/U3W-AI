package com.playwright.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 浏览器任务包装器
 * 提供便捷方法来提交浏览器自动化任务
 */
@Component
public class BrowserTaskWrapper {
    
    @Autowired
    private BrowserConcurrencyManager concurrencyManager;
    
    /**
     * 包装现有的Runnable任务，使其使用并发管理器
     * @param task 原始任务
     * @param taskName 任务名称
     * @param userId 用户ID
     */
    public void submitTask(Runnable task, String taskName, String userId) {
        concurrencyManager.submitBrowserTask(task, taskName, userId);
    }
    
    /**
     * 快速获取管理器状态
     */
    public void printStatus() {
    }
} 