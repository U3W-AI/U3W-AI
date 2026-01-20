package com.wx.fbsir.engine.capability;

import com.wx.fbsir.engine.websocket.client.WebSocketClientManager;
import com.wx.fbsir.engine.websocket.message.EngineMessage;
import com.wx.fbsir.engine.websocket.message.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;

/**
 * 消息处理管理器
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 核心职责（参考 cube-engine 设计）
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 调用链路（简化版）：
 *   WebSocket → EngineCapabilityManager → CapabilityRegistry → Controller → Utils
 * 
 * ⚠️ 重要：只支持精准匹配消息类型，避免误调用
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 *
 * @author wxfbsir
 * @date 2025-12-18
 */
@Component
public class EngineCapabilityManager {

    private static final Logger log = LoggerFactory.getLogger(EngineCapabilityManager.class);

    private final CapabilityRegistry registry;
    private final ThreadPoolTaskExecutor taskExecutor;
    private final TaskExecutionTracker taskTracker;

    private WebSocketClientManager webSocketClientManager;

    public EngineCapabilityManager(CapabilityRegistry registry,
                                    @Qualifier("messageExecutor") ThreadPoolTaskExecutor taskExecutor,
                                    TaskExecutionTracker taskTracker) {
        this.registry = registry;
        this.taskExecutor = taskExecutor;
        this.taskTracker = taskTracker;
        log.info("[消息管理] EngineCapabilityManager 初始化完成");
    }

    public void setWebSocketClientManager(WebSocketClientManager manager) {
        this.webSocketClientManager = manager;
    }

    @PostConstruct
    public void init() {
        // CapabilityRegistry 通过 @PostConstruct 自动注册，无需手动调用
        log.info("[消息管理] 初始化完成 - 当前注册能力数: {}", registry.size());
    }

    /**
     * 处理消息请求（只支持精准匹配，避免误调用）
     */
    public void handleMessage(EngineMessage message) {
        String type = message.getType();
        String userId = message.getUserId();
        
        // 只支持精准匹配，避免 yb_deepseek 误匹配 deepseek 等问题
        CapabilityRegistry.MessageHandler handler = registry.getHandler(type);
        
        if (handler == null) {
            log.warn("[消息] 无处理器（精准匹配）: {}", type);
            sendNotFoundError(message, type);
            return;
        }

        // 🔥 检查是否可以执行（全局并发、用户并发、重复提交）
        TaskExecutionTracker.TaskStartResult startResult = taskTracker.tryStart(userId, type);
        if (!startResult.success) {
            log.warn("[{}] 拒绝执行 - 用户: {}, 原因: {}", type, userId, startResult.errorCode);
            sendTaskRejectedError(message, type, startResult.errorCode, startResult.errorMessage);
            return;
        }

        // 异步执行（捕获拒绝异常）
        try {
            taskExecutor.execute(() -> executeHandler(handler, message));
        } catch (RejectedExecutionException e) {
            // 线程池繁忙，释放任务追踪
            taskTracker.finish(userId, type);
            log.warn("[{}] 任务被拒绝 - 系统繁忙，请稍后重试", type);
            sendBusyError(message, type);
        }
    }

    private void executeHandler(CapabilityRegistry.MessageHandler handler, EngineMessage message) {
        long startTime = System.currentTimeMillis();
        String type = handler.type();
        String userId = message.getUserId();

        try {
            handler.handle(message);
            long costTime = System.currentTimeMillis() - startTime;
            log.debug("[{}] 完成 - 耗时: {}ms", type, costTime);

        } catch (Exception e) {
            log.error("[{}] 异常: {}", type, e.getMessage(), e);
            sendErrorResult(message, type, e.getMessage());
        } finally {
            // 🔥 无论成功或失败，都释放任务追踪
            taskTracker.finish(userId, type);
        }
    }

    private void sendNotFoundError(EngineMessage message, String type) {
        if (webSocketClientManager == null || !webSocketClientManager.isConnected()) {
            return;
        }

        EngineMessage response = EngineMessage.builder()
            .type(MessageType.TASK_RESULT.getCode())
            .userId(message.getUserId())
            .payload("requestId", message.getPayloadValue("requestId"))
            .payload("success", false)
            .payload("errorCode", "HANDLER_NOT_FOUND")
            .payload("errorMessage", "当前主机没有 [" + type + "] 消息处理能力，需要更新主机或联系管理员处理")
            .build();

        webSocketClientManager.sendMessage(response);
    }

    private void sendErrorResult(EngineMessage message, String type, String errorMsg) {
        if (webSocketClientManager == null || !webSocketClientManager.isConnected()) {
            return;
        }

        EngineMessage response = EngineMessage.builder()
            .type(MessageType.TASK_RESULT.getCode())
            .userId(message.getUserId())
            .payload("requestId", message.getPayloadValue("requestId"))
            .payload("success", false)
            .payload("errorCode", "EXECUTION_ERROR")
            .payload("errorMessage", "处理 [" + type + "] 时发生错误: " + errorMsg)
            .build();

        webSocketClientManager.sendMessage(response);
    }

    private void sendBusyError(EngineMessage message, String type) {
        if (webSocketClientManager == null || !webSocketClientManager.isConnected()) {
            return;
        }

        EngineMessage response = EngineMessage.builder()
            .type(MessageType.TASK_RESULT.getCode())
            .userId(message.getUserId())
            .payload("requestId", message.getPayloadValue("requestId"))
            .payload("success", false)
            .payload("errorCode", "SYSTEM_BUSY")
            .payload("errorMessage", "系统繁忙，请稍后再试。当前任务队列已满，建议等待1-2分钟后重新尝试。")
            .build();

        webSocketClientManager.sendMessage(response);
    }

    private void sendTaskRejectedError(EngineMessage message, String type, String errorCode, String errorMessage) {
        if (webSocketClientManager == null || !webSocketClientManager.isConnected()) {
            return;
        }

        EngineMessage response = EngineMessage.builder()
            .type(MessageType.TASK_RESULT.getCode())
            .userId(message.getUserId())
            .payload("requestId", message.getPayloadValue("requestId"))
            .payload("success", false)
            .payload("errorCode", errorCode)
            .payload("errorMessage", errorMessage)
            .build();

        webSocketClientManager.sendMessage(response);
    }

    public List<Map<String, Object>> getCapabilityList() {
        return registry.getCapabilityList();
    }

    public boolean hasCapability(String code) {
        return registry.hasHandler(code);
    }

    public int getCapabilityCount() {
        return registry.size();
    }

    /**
     * 处理能力请求（供 MessageRouter 调用）
     */
    public void handleCapabilityRequest(EngineMessage message) {
        handleMessage(message);
    }
    
}
