package com.wx.fbsir.engine.controller.demo;

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
import java.util.HashMap;
import java.util.Map;

/**
 * 简单健康检查演示Controller（单次输出完整示例）
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 演示内容
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 本Controller演示单次返回任务的完整实现，包括：
 * 
 * 1. ✅ 单次返回 - 不继承StreamTaskHelper，直接使用WebSocketClientManager
 * 2. ✅ 参数提取 - 从EngineMessage中提取payload参数
 * 3. ✅ 业务处理 - 收集系统性能数据
 * 4. ✅ 数据封装 - 构建结构化的返回数据
 * 5. ✅ 消息发送 - 使用EngineMessage.builder()构建响应
 * 6. ✅ 异常处理 - 完整的错误处理和错误响应
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 单次返回 vs 流式返回
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 单次返回（本Controller）：
 * - ✅ 不继承StreamTaskHelper
 * - ✅ 直接注入WebSocketClientManager
 * - ✅ 使用@OnceCapability注解
 * - ✅ 只发送一次TASK_RESULT
 * - ✅ 适合快速返回的任务（如数据查询、状态检查）
 * 
 * 流式返回（BaiduHotSearchDemoController）：
 * - ✅ 继承StreamTaskHelper
 * - ✅ 使用@StreamCapability注解
 * - ✅ 可发送多次TASK_LOG、TASK_SCREENSHOT、TASK_PROGRESS
 * - ✅ 最后发送TASK_RESULT
 * - ✅ 适合长时间运行的任务（如爬虫、AI对话、文件处理）
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 客户端调用示例
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * ```json
 * {
 *   "type": "SIMPLE_HEALTH_CHECK_DEMO",
 *   "engineId": "engine-001",
 *   "payload": {
 *     "includeDetails": true
 *   }
 * }
 * ```
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 返回数据格式
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 成功响应：
 * ```json
 * {
 *   "type": "TASK_RESULT",
 *   "userId": "1",
 *   "payload": {
 *     "requestId": "xxx",
 *     "success": true,
 *     "data": {
 *       "status": "healthy",
 *       "hardware": {
 *         "cpuModel": "Apple M1",
 *         "cpuCores": 8,
 *         "totalMemoryGB": 16
 *       },
 *       "performance": {
 *         "cpuUsage": 0.35,
 *         "memoryUsage": 0.68,
 *         "jvmMemoryUsageMB": 512,
 *         "jvmMaxMemoryMB": 2048
 *       },
 *       "components": {
 *         "websocket": {
 *           "connected": true,
 *           "status": "CONNECTED"
 *         },
 *         "browserPool": {
 *           "available": 5,
 *           "total": 10
 *         }
 *       },
 *       "timestamp": 1234567890
 *     },
 *     "timestamp": 1234567890
 *   }
 * }
 * ```
 * 
 * 错误响应：
 * ```json
 * {
 *   "type": "TASK_RESULT",
 *   "userId": "1",
 *   "payload": {
 *     "requestId": "xxx",
 *     "success": false,
 *     "errorCode": "TASK_ERROR",
 *     "errorMessage": "系统异常: xxx",
 *     "timestamp": 1234567890
 *   }
 * }
 * ```
 *
 * @author FBSir
 * @date 2025-12-29
 */
@Controller
public class SimpleHealthCheckDemoController {

    private static final Logger log = LoggerFactory.getLogger(SimpleHealthCheckDemoController.class);

    // ━━━━━━━━━━ 依赖注入 ━━━━━━━━━━
    // 单次返回任务只需要注入WebSocketClientManager即可
    
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
     * 演示要点：
     * 1. 使用@OnceCapability注解标记单次返回任务
     * 2. 直接从EngineMessage中提取参数
     * 3. 直接注入WebSocketClientManager发送消息
     * 4. 使用EngineMessage.builder()构建响应
     * 5. try-catch确保异常被捕获并返回错误响应
     * 
     * @param message 消息对象，包含userId和payload参数
     */
    @OnceCapability(
        type = "SIMPLE_HEALTH_CHECK_DEMO",
        description = "简单健康检查演示（单次输出完整示例）",
        timeout = 30000L  // 任务超时时间（毫秒），可选参数
    )
    public void handleHealthCheck(EngineMessage message) {
        // ━━━━━━━━━━ 步骤1: 提取参数 ━━━━━━━━━━
        // 从EngineMessage中提取基础参数
        String userId = message.getUserId();
        String requestId = message.getPayloadValue("requestId");
        
        // 提取业务参数（带默认值）
        Boolean includeDetails = message.getPayloadValue("includeDetails");
        if (includeDetails == null) includeDetails = false;
        
        log.info("[健康检查演示] 任务开始 - 用户: {}, 请求: {}, 详细: {}", userId, requestId, includeDetails);
        
        try {
            // ━━━━━━━━━━ 步骤2: 执行业务逻辑 ━━━━━━━━━━
            
            // 2.1 获取硬件信息
            Map<String, Object> hardwareInfo = SystemPerformanceMonitor.getHardwareInfo();
            
            // 2.2 获取性能数据
            Map<String, Object> performanceData = SystemPerformanceMonitor.getPerformanceData();
            
            // 2.3 获取JVM内存信息
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
            
            Map<String, Object> jvmMemory = new HashMap<>();
            jvmMemory.put("usedMB", heapUsage.getUsed() / (1024 * 1024));
            jvmMemory.put("maxMB", heapUsage.getMax() / (1024 * 1024));
            jvmMemory.put("usageRatio", (double) heapUsage.getUsed() / heapUsage.getMax());
            
            // 2.4 检查组件状态
            Map<String, Object> components = new HashMap<>();
            
            // WebSocket连接状态
            Map<String, Object> websocketStatus = new HashMap<>();
            if (webSocketClientManager != null && webSocketClientManager.isConnected()) {
                websocketStatus.put("connected", true);
                websocketStatus.put("status", "CONNECTED");
            } else {
                websocketStatus.put("connected", false);
                websocketStatus.put("status", "DISCONNECTED");
            }
            components.put("websocket", websocketStatus);
            
            // Browser池状态
            if (browserPoolManager != null) {
                Map<String, Object> browserPoolStatus = browserPoolManager.getStatus();
                components.put("browserPool", browserPoolStatus);
            }
            
            // 全局Browser池状态
            if (globalBrowserPool != null) {
                Map<String, Object> globalPoolStatus = new HashMap<>();
                globalPoolStatus.put("available", globalBrowserPool.getAvailableCount());
                globalPoolStatus.put("total", globalBrowserPool.getTotalBrowsers());
                globalPoolStatus.put("shutdown", globalBrowserPool.isShutdown());
                components.put("globalBrowserPool", globalPoolStatus);
            }
            
            // ━━━━━━━━━━ 步骤3: 构建返回数据 ━━━━━━━━━━
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("status", "healthy");
            resultData.put("hardware", hardwareInfo);
            resultData.put("performance", performanceData);
            resultData.put("jvmMemory", jvmMemory);
            resultData.put("components", components);
            resultData.put("timestamp", System.currentTimeMillis());
            
            // 如果需要详细信息，添加更多数据
            if (includeDetails) {
                Map<String, Object> detailedInfo = new HashMap<>();
                detailedInfo.put("javaVersion", System.getProperty("java.version"));
                detailedInfo.put("osName", System.getProperty("os.name"));
                detailedInfo.put("osArch", System.getProperty("os.arch"));
                detailedInfo.put("availableProcessors", Runtime.getRuntime().availableProcessors());
                resultData.put("details", detailedInfo);
            }
            
            // ━━━━━━━━━━ 步骤4: 发送成功结果 ━━━━━━━━━━
            // 🔥 重要：使用EngineMessage.builder()构建响应
            // - type固定为"TASK_RESULT"（框架约定）
            // - userId从请求中传递
            // - payload中必须包含requestId、success、data
            sendSuccessResult(userId, requestId, resultData);
            
            log.info("[健康检查演示] 任务完成 - 用户: {}, 状态: healthy", userId);
            
        } catch (Exception e) {
            log.error("[健康检查演示] 任务失败 - 用户: {}, 请求: {}", userId, requestId, e);
            
            // ━━━━━━━━━━ 步骤5: 发送错误结果 ━━━━━━━━━━
            // 🔥 重要：异常时也要返回TASK_RESULT，但success=false
            sendErrorResult(userId, requestId, "系统异常: " + e.getMessage());
        }
    }

    /**
     * 发送成功结果
     * 
     * 数据格式说明：
     * - type: 固定为"TASK_RESULT"
     * - userId: 用户ID
     * - payload.requestId: 请求ID（必须）
     * - payload.success: true（必须）
     * - payload.data: 业务数据（可选）
     * - payload.timestamp: 时间戳（可选）
     * 
     * @param userId 用户ID
     * @param requestId 请求ID
     * @param data 业务数据
     */
    private void sendSuccessResult(String userId, String requestId, Map<String, Object> data) {
        // 🔥 重要：使用EngineMessage.builder()构建消息
        EngineMessage result = EngineMessage.builder()
            .type("TASK_RESULT")  // 固定使用TASK_RESULT
            .userId(userId)
            .payload("requestId", requestId)  // 必须包含requestId
            .payload("success", true)         // 必须包含success
            .payload("data", data)            // 业务数据
            .payload("timestamp", System.currentTimeMillis())
            .build();
        
        // 通过WebSocketClientManager发送消息
        webSocketClientManager.sendMessage(result);
        log.debug("[健康检查演示] 发送结果 - 用户: {}, 请求: {}", userId, requestId);
    }

    /**
     * 发送错误结果
     * 
     * 数据格式说明：
     * - type: 固定为"TASK_RESULT"
     * - userId: 用户ID
     * - payload.requestId: 请求ID（必须）
     * - payload.success: false（必须）
     * - payload.errorCode: 错误码（可选）
     * - payload.errorMessage: 错误信息（必须）
     * - payload.timestamp: 时间戳（可选）
     * 
     * @param userId 用户ID
     * @param requestId 请求ID
     * @param errorMessage 错误信息
     */
    private void sendErrorResult(String userId, String requestId, String errorMessage) {
        EngineMessage result = EngineMessage.builder()
            .type("TASK_RESULT")
            .userId(userId)
            .payload("requestId", requestId)
            .payload("success", false)
            .payload("errorCode", "TASK_ERROR")
            .payload("errorMessage", errorMessage)
            .payload("timestamp", System.currentTimeMillis())
            .build();
        
        webSocketClientManager.sendMessage(result);
        log.debug("[健康检查演示] 发送错误 - 用户: {}, 请求: {}, 错误: {}", userId, requestId, errorMessage);
    }
}
