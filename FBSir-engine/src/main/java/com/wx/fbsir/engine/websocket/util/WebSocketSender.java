package com.wx.fbsir.engine.websocket.util;

import com.wx.fbsir.engine.websocket.client.WebSocketClientManager;
import com.wx.fbsir.engine.websocket.message.EngineMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * WebSocket 消息发送工具 - 用于向前端发送中间状态和最终结果
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 📌 核心功能：
 *   在业务 Handler 中调用，向前端发送各种类型的消息
 *   消息会通过 Engine → Admin → 前端 的路径传递
 * 
 * 📌 常用方法：
 *   sendQrCode()   - 发送二维码图片 URL
 *   sendStatus()   - 发送状态（登录成功/失败等）
 *   sendProgress() - 发送进度（10%, 50%, 100%）
 *   sendSuccess()  - 发送成功结果
 *   sendError()    - 发送错误信息
 *   sendTimeout()  - 发送超时通知
 * 
 * 📌 使用示例（在 Handler 中）：
 * <pre>
 *   @Autowired
 *   protected WebSocketSender wsSender;
 *   
 *   public void getQrCode(EngineMessage message) {
 *       String userId = message.getUserId();
 *       
 *       // 发送二维码
 *       wsSender.sendQrCode(userId, "RETURN_PC_DB_QRURL", qrUrl);
 *       
 *       // 发送登录状态
 *       wsSender.sendStatus(userId, "RETURN_DB_STATUS", "true");
 *       
 *       // 发送进度
 *       wsSender.sendProgress(userId, "TASK_PROGRESS", 50, "处理中...");
 *       
 *       // 发送错误
 *       wsSender.sendError(userId, "TASK_ERROR", "失败原因");
 *   }
 * </pre>
 * 
 * 📌 消息流向：
 *   Handler 调用 wsSender.sendXxx()
 *      ↓
 *   WebSocketClientManager 发送给 Admin
 *      ↓
 *   Admin 的 MessageRouter 转发给对应的前端
 *      ↓
 *   前端 WebSocket 接收并处理
 *
 * @author FBSir
 * @date 2025-12-18
 */
@Component
public class WebSocketSender {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSender.class);

    @Autowired(required = false)
    private WebSocketClientManager wsManager;

    /**
     * 发送二维码
     */
    public void sendQrCode(String userId, String type, String qrUrl) {
        send(EngineMessage.builder()
            .type(type)
            .userId(userId)
            .payload("url", qrUrl)
            .payload("timestamp", System.currentTimeMillis())
            .build());
    }

    /**
     * 发送状态更新
     */
    public void sendStatus(String userId, String type, String status) {
        send(EngineMessage.builder()
            .type(type)
            .userId(userId)
            .payload("status", status)
            .payload("timestamp", System.currentTimeMillis())
            .build());
    }

    /**
     * 发送状态更新（带额外数据）
     */
    public void sendStatus(String userId, String type, String status, String key, Object value) {
        send(EngineMessage.builder()
            .type(type)
            .userId(userId)
            .payload("status", status)
            .payload(key, value)
            .payload("timestamp", System.currentTimeMillis())
            .build());
    }

    /**
     * 发送进度更新
     */
    public void sendProgress(String userId, String type, int progress, String message) {
        send(EngineMessage.builder()
            .type(type)
            .userId(userId)
            .payload("progress", progress)
            .payload("message", message)
            .payload("timestamp", System.currentTimeMillis())
            .build());
    }

    /**
     * 发送成功结果
     */
    public void sendSuccess(String userId, String type, Object data) {
        send(EngineMessage.builder()
            .type(type)
            .userId(userId)
            .payload("success", true)
            .payload("data", data)
            .payload("timestamp", System.currentTimeMillis())
            .build());
    }

    /**
     * 发送错误
     */
    public void sendError(String userId, String type, String error) {
        send(EngineMessage.builder()
            .type(type)
            .userId(userId)
            .payload("success", false)
            .payload("error", error)
            .payload("timestamp", System.currentTimeMillis())
            .build());
    }

    /**
     * 发送错误（带错误码）
     */
    public void sendError(String userId, String type, String errorCode, String errorMessage) {
        send(EngineMessage.builder()
            .type(type)
            .userId(userId)
            .payload("success", false)
            .payload("errorCode", errorCode)
            .payload("errorMessage", errorMessage)
            .payload("timestamp", System.currentTimeMillis())
            .build());
    }

    /**
     * 发送超时通知
     */
    public void sendTimeout(String userId, String type) {
        send(EngineMessage.builder()
            .type(type)
            .userId(userId)
            .payload("status", "timeout")
            .payload("timestamp", System.currentTimeMillis())
            .build());
    }

    /**
     * 发送图片/截图
     */
    public void sendImage(String userId, String type, String imageUrl) {
        send(EngineMessage.builder()
            .type(type)
            .userId(userId)
            .payload("url", imageUrl)
            .payload("imageType", "screenshot")
            .payload("timestamp", System.currentTimeMillis())
            .build());
    }

    /**
     * 发送自定义消息
     */
    public void send(EngineMessage message) {
        if (wsManager == null) {
            log.warn("[WebSocketSender] WebSocketClientManager 未初始化");
            return;
        }
        
        if (!wsManager.isConnected()) {
            log.warn("[WebSocketSender] WebSocket 未连接，消息丢弃: {}", message.getType());
            return;
        }
        
        wsManager.sendMessage(message);
        log.debug("[WebSocketSender] 发送消息: {} -> {}", message.getType(), message.getUserId());
    }

    /**
     * 检查连接状态
     */
    public boolean isConnected() {
        return wsManager != null && wsManager.isConnected();
    }
}
