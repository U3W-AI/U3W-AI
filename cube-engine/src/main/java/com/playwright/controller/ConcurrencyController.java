package com.playwright.controller;

import com.playwright.utils.BrowserConcurrencyManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 并发监控控制器
 * 提供浏览器并发状态查询接口
 */
@RestController
@RequestMapping("/api/concurrency")
@Tag(name = "并发监控", description = "浏览器并发状态监控接口")
public class ConcurrencyController {
    
    @Autowired
    private BrowserConcurrencyManager concurrencyManager;
    
    @GetMapping("/status")
    @Operation(summary = "获取当前并发状态", description = "返回当前浏览器任务的并发状态信息")
    public BrowserConcurrencyManager.ConcurrencyStatus getStatus() {
        return concurrencyManager.getStatus();
    }
    
    @GetMapping("/canExecute")
    @Operation(summary = "检查是否可以立即执行", description = "检查当前是否可以立即执行新任务而不需要等待")
    public boolean canExecuteImmediately() {
        return concurrencyManager.canExecuteImmediately();
    }
} 