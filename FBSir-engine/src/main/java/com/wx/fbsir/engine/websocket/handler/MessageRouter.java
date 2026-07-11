package com.wx.fbsir.engine.websocket.handler;

import com.wx.fbsir.engine.capability.EngineCapabilityManager;
import com.wx.fbsir.engine.websocket.message.EngineMessage;
import com.wx.fbsir.engine.websocket.message.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息路由器
 * 
 * 替代老项目的 500+ 行 if-else 字符串匹配
 * 使用 Handler 模式，支持动态注册处理器
 * 
 * 【单向通信原则】
 * - 只接收 Admin 的请求并返回结果
 * - 不主动向 Admin 发起请求并等待回复
 * - 所有响应通过 TASK_RESULT 或 ERROR 消息回传
 *
 * @author FBSir
 * @date 2025-12-15
 */
@Component
public class MessageRouter {

    private static final Logger log = LoggerFactory.getLogger(MessageRouter.class);

    private final Map<MessageType, MessageHandler> handlers = new ConcurrentHashMap<>();
    private final ThreadPoolTaskExecutor messageExecutor;
    
    /**
     * 能力管理器（延迟注入避免循环依赖）
     */
    @Autowired
    @Lazy
    private EngineCapabilityManager capabilityManager;

    public MessageRouter(@Qualifier("messageExecutor") ThreadPoolTaskExecutor messageExecutor) {
        this.messageExecutor = messageExecutor;
    }

    @PostConstruct
    public void init() {
        log.info("[消息路由] 初始化完成 - 系统处理器已就绪，业务能力由 CapabilityManager 管理");
    }

    /**
     * 注册消息处理器
     *
     * @param type    消息类型
     * @param handler 处理器
     */
    public void registerHandler(MessageType type, MessageHandler handler) {
        handlers.put(type, handler);
        log.debug("[消息路由] 注册处理器 - 类型: {}, 处理器: {}", 
            type.getCode(), handler.getClass().getSimpleName());
    }

    /**
     * 路由消息到对应处理器
     * 
     * 路由优先级：
     * 1. 系统消息（心跳、注册等）- 由 WebSocketClient 处理
     * 2. 已注册的 MessageHandler
     * 3. CapabilityManager 管理的能力
     * 4. 未知消息 - 返回错误
     *
     * @param message 消息对象
     */
    public void route(EngineMessage message) {
        if (message == null) {
            log.warn("[消息路由] 收到空消息，已忽略");
            return;
        }

        String messageType = message.getType();
        MessageType type = message.getMessageType();

        // 1. 检查是否有注册的 Handler
        MessageHandler handler = handlers.get(type);
        if (handler != null) {
            executeHandler(message, type, handler);
            return;
        }

        // 2. 委托给能力管理器处理
        if (capabilityManager != null) {
            // 检查是否是已注册的能力
            if (capabilityManager.hasCapability(messageType)) {
                log.debug("[消息路由] 委托给能力管理器 - 能力: {}", messageType);
                capabilityManager.handleCapabilityRequest(message);
                return;
            }
            
            // 未知能力 - 返回错误
            log.warn("[消息路由] 未知能力: {} - 当前主机不支持此能力", messageType);
            capabilityManager.handleCapabilityRequest(message); // 会返回 CAPABILITY_NOT_FOUND 错误
            return;
        }

        // 3. 没有能力管理器，记录警告
        log.warn("[消息路由] 未找到处理器且能力管理器不可用 - 消息类型: {}", messageType);
    }

    /**
     * 执行消息处理器（带排队支持）
     */
    private void executeHandler(EngineMessage message, MessageType type, MessageHandler handler) {
        try {
            messageExecutor.execute(() -> {
                long startTime = System.currentTimeMillis();
                try {
                    log.debug("[消息路由] 开始处理消息 - 类型: {}, 用户: {}", 
                        type.getCode(), message.getUserId());
                    
                    handler.handle(message);
                    
                    long costTime = System.currentTimeMillis() - startTime;
                    log.debug("[消息路由] 消息处理完成 - 类型: {}, 耗时: {}ms", 
                        type.getCode(), costTime);
                    
                } catch (Exception e) {
                    log.error("[消息路由] 消息处理异常 - 类型: {}, 用户: {}, 错误: {}", 
                        type.getCode(), message.getUserId(), e.getMessage(), e);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // 线程池拒绝（已在EngineCapabilityManager中捕获并发送友好提示）
            log.warn("[消息路由] 线程池繁忙 - 类型: {}, 用户: {}", 
                type.getCode(), message.getUserId());
        }
    }

    /**
     * 消息处理器接口
     */
    public interface MessageHandler {
        /**
         * 处理消息
         *
         * @param message 消息对象
         */
        void handle(EngineMessage message);
    }

    /**
     * 获取已注册的 Handler 数量
     */
    public int getHandlerCount() {
        return handlers.size();
    }

    /**
     * 获取能力数量（来自 CapabilityManager）
     */
    public int getCapabilityCount() {
        return capabilityManager != null ? capabilityManager.getCapabilityCount() : 0;
    }
}
