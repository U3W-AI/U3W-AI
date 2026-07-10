package com.wx.fbsir.engine.controller.monitor;

import com.wx.fbsir.engine.capability.TaskExecutionTracker;
import com.wx.fbsir.engine.playwright.core.PlaywrightInstancePool;
import com.wx.fbsir.engine.playwright.pool.BrowserPoolManager;
import com.wx.fbsir.engine.websocket.client.WebSocketClientManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.Map;

/**
 * 资源监控控制器
 * 
 * 提供系统资源使用情况的实时监控接口
 * 
 * @author wxfbsir
 * @date 2026-01-20
 */
@RestController
@RequestMapping("/api/monitor")
public class ResourceMonitorController {
    
    private final TaskExecutionTracker taskTracker;
    private final PlaywrightInstancePool instancePool;
    private final BrowserPoolManager browserPool;
    private final WebSocketClientManager webSocketClientManager;
    
    public ResourceMonitorController(TaskExecutionTracker taskTracker,
                                       PlaywrightInstancePool instancePool,
                                       BrowserPoolManager browserPool,
                                       WebSocketClientManager webSocketClientManager) {
        this.taskTracker = taskTracker;
        this.instancePool = instancePool;
        this.browserPool = browserPool;
        this.webSocketClientManager = webSocketClientManager;
    }
    
    /**
     * 获取系统健康状态
     * 
     * GET /api/monitor/health
     */
    @GetMapping("/health")
    public Map<String, Object> getHealth() {
        Map<String, Object> health = new HashMap<>();
        
        // liveness 只说明进程可响应；readiness 反映是否可以执行和回传任务。
        health.put("liveness", "UP");
        health.put("timestamp", System.currentTimeMillis());
        
        // 任务执行状态
        int executing = taskTracker.getExecutingCount();
        int capacity = instancePool.getPoolSize();
        double utilizationRate = capacity > 0 ? (executing * 100.0 / capacity) : 0;
        
        Map<String, Object> taskStatus = new HashMap<>();
        taskStatus.put("executing", executing);
        taskStatus.put("capacity", capacity);
        taskStatus.put("utilizationRate", String.format("%.1f%%", utilizationRate));
        taskStatus.put("stats", taskTracker.getStats());
        health.put("taskStatus", taskStatus);
        
        // 浏览器池状态
        Map<String, Object> browserStatus = new HashMap<>();
        browserStatus.put("activeSessions", browserPool.getActiveCount());
        browserStatus.put("persistentSessions", browserPool.getPersistentCount());
        browserStatus.put("temporarySessions", browserPool.getTemporaryCount());
        health.put("browserStatus", browserStatus);

        java.util.List<String> readinessReasons = new java.util.ArrayList<>();
        String readiness = "UP";
        if (capacity <= 0) {
            readiness = "DOWN";
            readinessReasons.add("Playwright 实例池容量为 0");
        }
        if (browserPool.getAvailableSlots() <= 0 && "UP".equals(readiness)) {
            readiness = "DEGRADED";
            readinessReasons.add("浏览器会话池没有可用槽位");
        }
        if (webSocketClientManager == null || !webSocketClientManager.isConnected()) {
            if ("UP".equals(readiness)) {
                readiness = "DEGRADED";
            }
            readinessReasons.add("未连接 Admin WebSocket，任务回执无法可靠回传");
        }
        health.put("status", readiness);
        health.put("readinessReasons", readinessReasons);
        
        // JVM状态
        Map<String, Object> jvmStatus = getJvmStatus();
        health.put("jvmStatus", jvmStatus);
        
        return health;
    }
    
    /**
     * 获取详细的任务统计
     * 
     * GET /api/monitor/tasks
     */
    @GetMapping("/tasks")
    public Map<String, Object> getTaskStats() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("timestamp", System.currentTimeMillis());
        stats.put("executing", taskTracker.getExecutingCount());
        stats.put("capacity", instancePool.getPoolSize());
        stats.put("summary", taskTracker.getStats());
        
        // Playwright实例池状态
        Map<String, Object> instanceStats = new HashMap<>();
        instanceStats.put("poolSize", instancePool.getPoolSize());
        instanceStats.put("available", instancePool.getPoolSize() - taskTracker.getExecutingCount());
        stats.put("instancePool", instanceStats);
        
        return stats;
    }
    
    /**
     * 获取浏览器池统计
     * 
     * GET /api/monitor/browsers
     */
    @GetMapping("/browsers")
    public Map<String, Object> getBrowserStats() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("timestamp", System.currentTimeMillis());
        stats.put("activeSessions", browserPool.getActiveCount());
        stats.put("persistentSessions", browserPool.getPersistentCount());
        stats.put("temporarySessions", browserPool.getTemporaryCount());
        stats.put("maxSessions", browserPool.getStatus().get("maxSize"));
        
        return stats;
    }
    
    /**
     * 获取JVM状态
     */
    private Map<String, Object> getJvmStatus() {
        Map<String, Object> jvm = new HashMap<>();
        
        // 内存信息
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed() / 1024 / 1024;
        long heapMax = memoryBean.getHeapMemoryUsage().getMax() / 1024 / 1024;
        double memoryUsageRate = heapMax > 0 ? (heapUsed * 100.0 / heapMax) : 0;
        
        Map<String, Object> memory = new HashMap<>();
        memory.put("heapUsed", heapUsed + "MB");
        memory.put("heapMax", heapMax + "MB");
        memory.put("usageRate", String.format("%.1f%%", memoryUsageRate));
        jvm.put("memory", memory);
        
        // 线程信息
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        Map<String, Object> threads = new HashMap<>();
        threads.put("count", threadBean.getThreadCount());
        threads.put("peak", threadBean.getPeakThreadCount());
        threads.put("daemon", threadBean.getDaemonThreadCount());
        jvm.put("threads", threads);
        
        // CPU信息
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> cpu = new HashMap<>();
        cpu.put("processors", runtime.availableProcessors());
        jvm.put("cpu", cpu);
        
        return jvm;
    }
    
    /**
     * 获取完整的系统状态报告
     * 
     * GET /api/monitor/report
     */
    @GetMapping("/report")
    public Map<String, Object> getFullReport() {
        Map<String, Object> report = new HashMap<>();
        
        report.put("timestamp", System.currentTimeMillis());
        report.put("health", getHealth());
        report.put("tasks", getTaskStats());
        report.put("browsers", getBrowserStats());
        
        return report;
    }
}
