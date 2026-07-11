package com.wx.fbsir.engine.exception;

import com.wx.fbsir.engine.websocket.client.WebSocketClientManager;
import com.wx.fbsir.engine.websocket.message.EngineMessage;
import com.wx.fbsir.engine.websocket.message.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * 全局异常处理器
 * 
 * 统一处理Controller中的异常，避免每个Controller重复编写异常处理代码
 * 
 * 核心功能：
 * 1. 捕获并记录异常日志
 * 2. 自动发送错误响应给前端
 * 3. 统一错误格式
 * 4. 防止异常导致任务卡死
 * 
 * 使用方式：
 * <pre>
 * @Controller
 * public class MyController {
 *     
 *     @Autowired
 *     private GlobalExceptionHandler exceptionHandler;
 *     
 *     public void handleMyTask(EngineMessage message) {
 *         try {
 *             // 业务逻辑
 *         } catch (Exception e) {
 *             // 统一异常处理
 *             exceptionHandler.handleException(message, e);
 *         }
 *     }
 * }
 * </pre>
 * 
 * 或者使用BaseStreamController（已集成异常处理）：
 * <pre>
 * public class MyController extends BaseStreamController {
 *     public void handleMyTask(EngineMessage message) {
 *         StreamTask task = startStreamTask(userId, requestId);
 *         try {
 *             // 业务逻辑
 *         } catch (Exception e) {
 *             task.sendError(e.getMessage()); // 自动调用全局异常处理
 *         }
 *     }
 * }
 * </pre>
 *
 * @author FBSir
 * @date 2025-12-23
 */
@Component
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * WebSocket客户端管理器（延迟注入避免循环依赖）
     */
    @Autowired
    @Lazy
    private WebSocketClientManager webSocketClientManager;

    /**
     * 处理异常（完整版）
     * 
     * @param message 原始消息对象
     * @param exception 捕获的异常
     */
    public void handleException(EngineMessage message, Exception exception) {
        String userId = message.getUserId();
        String requestId = extractRequestId(message);
        String messageType = message.getType();
        
        handleException(userId, requestId, messageType, exception);
    }

    /**
     * 处理异常（简化版）
     * 
     * @param userId 用户ID
     * @param requestId 请求ID
     * @param messageType 消息类型
     * @param exception 捕获的异常
     */
    public void handleException(String userId, String requestId, String messageType, Exception exception) {
        // 1. 记录详细错误日志
        log.error("[全局异常处理] 用户: {}, 请求ID: {}, 消息类型: {}, 错误: {}", 
            userId, requestId, messageType, exception.getMessage(), exception);
        
        // 2. 构建友好的错误消息
        String userMessage = buildUserFriendlyMessage(exception);
        String errorCode = determineErrorCode(exception);
        
        // 3. 发送错误响应给前端
        sendErrorResponse(userId, requestId, userMessage, errorCode, exception);
    }

    /**
     * 提取requestId
     */
    private String extractRequestId(EngineMessage message) {
        Object requestId = message.getPayloadValue("requestId");
        return requestId != null ? requestId.toString() : "unknown";
    }

    /**
     * 构建用户友好的错误消息
     * 
     * 隐藏技术细节，返回可读的错误描述
     */
    private String buildUserFriendlyMessage(Exception exception) {
        String message = exception.getMessage();
        
        // 处理常见异常类型
        if (exception instanceof NullPointerException) {
            return "系统内部错误：空指针异常";
        } else if (exception instanceof IllegalArgumentException) {
            return "参数错误：" + message;
        } else if (exception instanceof IllegalStateException) {
            return "状态错误：" + message;
        } else if (exception instanceof InterruptedException) {
            return "任务被中断";
        } else if (exception.getClass().getName().contains("Timeout")) {
            return "请求超时，请稍后重试";
        }
        
        // 默认返回异常消息（已经足够友好的情况）
        if (message != null && !message.isEmpty()) {
            return "执行失败：" + message;
        }
        
        return "系统异常：" + exception.getClass().getSimpleName();
    }

    /**
     * 确定错误码
     * 
     * 根据异常类型返回对应的错误码，便于前端处理
     */
    private String determineErrorCode(Exception exception) {
        if (exception instanceof IllegalArgumentException) {
            return "INVALID_PARAMETER";
        } else if (exception instanceof IllegalStateException) {
            return "INVALID_STATE";
        } else if (exception instanceof InterruptedException) {
            return "TASK_INTERRUPTED";
        } else if (exception.getClass().getName().contains("Timeout")) {
            return "REQUEST_TIMEOUT";
        } else if (exception instanceof NullPointerException) {
            return "NULL_POINTER";
        }
        
        return "SYSTEM_ERROR";
    }

    /**
     * 发送错误响应给前端
     */
    private void sendErrorResponse(String userId, String requestId, String errorMessage, String errorCode, Exception exception) {
        // 检查WebSocket是否连接
        if (webSocketClientManager == null || !webSocketClientManager.isConnected()) {
            log.warn("[全局异常处理] WebSocket未连接，无法发送错误响应 - 用户: {}, 请求ID: {}", userId, requestId);
            return;
        }
        
        // 构建错误响应消息
        EngineMessage errorMsg = EngineMessage.builder()
            .type(MessageType.TASK_RESULT.getCode())
            .userId(userId)
            .payload("requestId", requestId)
            .payload("success", false)
            .payload("errorCode", errorCode)
            .payload("errorMessage", errorMessage)
            .payload("timestamp", System.currentTimeMillis())
            .build();
        
        try {
            // 发送错误响应
            webSocketClientManager.sendMessage(errorMsg);
            log.debug("[全局异常处理] 已发送错误响应 - 用户: {}, 请求ID: {}, 错误码: {}", 
                userId, requestId, errorCode);
        } catch (Exception e) {
            log.error("[全局异常处理] 发送错误响应失败 - 用户: {}, 请求ID: {}, 错误: {}", 
                userId, requestId, e.getMessage());
        }
    }

    /**
     * 包装异常处理（用于函数式编程）
     * 
     * 示例：
     * <pre>
     * exceptionHandler.wrap(userId, requestId, "MY_TASK", () -> {
     *     // 可能抛出异常的业务逻辑
     *     doSomething();
     * });
     * </pre>
     */
    public void wrap(String userId, String requestId, String messageType, Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            handleException(userId, requestId, messageType, e);
        }
    }

    /**
     * 包装异常处理（带返回值）
     * 
     * 示例：
     * <pre>
     * String result = exceptionHandler.wrapWithResult(userId, requestId, "MY_TASK", () -> {
     *     return calculateResult();
     * });
     * </pre>
     */
    public <T> T wrapWithResult(String userId, String requestId, String messageType, java.util.function.Supplier<T> task) {
        try {
            return task.get();
        } catch (Exception e) {
            handleException(userId, requestId, messageType, e);
            return null;
        }
    }
}
