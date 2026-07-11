package com.wx.fbsir.engine.controller;

import com.wx.fbsir.engine.capability.annotation.OnceCapability;
import com.wx.fbsir.engine.playwright.pool.BrowserPoolManager;
import com.wx.fbsir.engine.playwright.pool.GlobalBrowserPool;
import com.wx.fbsir.engine.util.SystemPerformanceMonitor;
import com.wx.fbsir.engine.websocket.client.WebSocketClientManager;
import com.wx.fbsir.engine.websocket.message.EngineMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查控制器
 * 
 * 功能：返回Engine的健康状态和实时性能数据
 * 
 * 返回数据包括：
 * - 基本状态：连接状态、版本号、运行时长
 * - 硬件信息：CPU型号、核心数、内存容量
 * - 性能数据：CPU使用率、内存使用率、磁盘使用率、JVM使用率
 * - 系统负载：系统平均负载
 *
 * @author FBSir
 * @date 2025-12-23
 */
@Controller
public class HealthCheckController {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckController.class);

    @Autowired
    @Lazy
    private WebSocketClientManager webSocketClientManager;
    
    @Autowired(required = false)
    private BrowserPoolManager browserPoolManager;
    
    @Autowired(required = false)
    private GlobalBrowserPool globalBrowserPool;

    /**
     * 处理健康检查请求（单次返回）
     * 
     * 返回Engine的完整健康状态和性能数据
     * 
     * @param message 健康检查消息（包含requestId）
     */
    @OnceCapability(
        type = "HEALTH_CHECK",
        description = "引擎健康检查"
    )
    public void handleHealthCheck(EngineMessage message) {
        String userId = message.getUserId();
        String requestId = message.getPayloadValue("requestId");
        
        try {
            // 1. 获取硬件信息（静态信息）
            Map<String, Object> hardwareInfo = SystemPerformanceMonitor.getHardwareInfo();
            
            // 2. 获取性能数据（动态信息）
            Map<String, Object> performanceData = SystemPerformanceMonitor.getPerformanceData();
            
            // 3. 构建响应数据（🟡 P2修复：返回详细组件状态）
            Map<String, Object> result = new HashMap<>();
            result.put("timestamp", System.currentTimeMillis());
            result.put("hardware", hardwareInfo);
            result.put("performance", performanceData);
            
            // 🟡 P2修复：WebSocket连接状态
            Map<String, Object> websocketStatus = new HashMap<>();
            if (webSocketClientManager != null && webSocketClientManager.isConnected()) {
                websocketStatus.put("connected", true);
                websocketStatus.put("status", "CONNECTED");
            } else {
                websocketStatus.put("connected", false);
                websocketStatus.put("status", "DISCONNECTED");
            }
            result.put("websocket", websocketStatus);
            
            // 🟡 P2修复：Browser池状态
            if (browserPoolManager != null) {
                Map<String, Object> browserPoolStatus = browserPoolManager.getStatus();
                result.put("browserPool", browserPoolStatus);
            }
            
            // 🟡 P2修复：全局Browser池状态
            if (globalBrowserPool != null) {
                Map<String, Object> globalPoolStatus = new HashMap<>();
                globalPoolStatus.put("available", globalBrowserPool.getAvailableCount());
                globalPoolStatus.put("total", globalBrowserPool.getTotalBrowsers());
                globalPoolStatus.put("shutdown", globalBrowserPool.isShutdown());
                result.put("globalBrowserPool", globalPoolStatus);
            }
            
            // 🟡 P2修复：内存使用率
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
            Map<String, Object> memoryStatus = new HashMap<>();
            memoryStatus.put("usedMB", heapUsage.getUsed() / (1024 * 1024));
            memoryStatus.put("maxMB", heapUsage.getMax() / (1024 * 1024));
            memoryStatus.put("usageRatio", (double) heapUsage.getUsed() / heapUsage.getMax());
            result.put("memory", memoryStatus);
            
            // 🟡 P2修复：线程数
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            Map<String, Object> threadStatus = new HashMap<>();
            threadStatus.put("current", threadBean.getThreadCount());
            threadStatus.put("peak", threadBean.getPeakThreadCount());
            threadStatus.put("daemon", threadBean.getDaemonThreadCount());
            result.put("threads", threadStatus);
            
            // 添加连接状态（兼容旧版本）
            result.put("connected", webSocketClientManager != null && webSocketClientManager.isConnected());
            
            // 4. 发送结果
            sendResult(userId, requestId, result);
            
        } catch (Exception e) {
            log.error("[健康检查] 执行失败: {}", e.getMessage(), e);
            sendErrorResult(userId, requestId, "健康检查失败: " + e.getMessage());
        }
    }

    /**
     * 发送成功结果
     */
    private void sendResult(String userId, String requestId, Map<String, Object> data) {
        EngineMessage resultMsg = EngineMessage.builder()
            .type("HEALTH_CHECK_RESULT")
            .userId(userId)
            .payload("requestId", requestId)
            .payload("success", true)
            .payload("data", data)
            .build();
        
        if (webSocketClientManager != null) {
            webSocketClientManager.sendMessage(resultMsg);
        }
    }

    /**
     * 发送错误结果
     */
    private void sendErrorResult(String userId, String requestId, String errorMessage) {
        EngineMessage errorMsg = EngineMessage.builder()
            .type("HEALTH_CHECK_RESULT")
            .userId(userId)
            .payload("requestId", requestId)
            .payload("success", false)
            .payload("errorMessage", errorMessage)
            .build();
        
        if (webSocketClientManager != null) {
            webSocketClientManager.sendMessage(errorMsg);
        }
    }

}
