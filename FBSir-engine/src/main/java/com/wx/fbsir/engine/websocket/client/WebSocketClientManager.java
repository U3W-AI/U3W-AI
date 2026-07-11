package com.wx.fbsir.engine.websocket.client;

import com.wx.fbsir.engine.capability.EngineCapabilityManager;
import com.wx.fbsir.engine.config.EngineProperties;
import com.wx.fbsir.engine.websocket.handler.MessageRouter;
import com.wx.fbsir.engine.websocket.message.EngineMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebSocket 客户端管理器
 * 
 * 负责 WebSocket 客户端的生命周期管理
 * 解决老项目的资源管理问题
 *
 * @author FBSir
 * @date 2025-12-15
 */
@Component
public class WebSocketClientManager {

    private static final Logger log = LoggerFactory.getLogger(WebSocketClientManager.class);

    private final EngineProperties properties;
    private final ThreadPoolTaskScheduler scheduler;
    private final MessageRouter messageRouter;

    /**
     * 能力管理器（延迟注入避免循环依赖）
     */
    @Autowired
    @Lazy
    private EngineCapabilityManager capabilityManager;

    private EngineWebSocketClient client;
    private volatile boolean initialized = false;
    
    /**
     * 待发送消息队列（重连期间消息暂存）- 使用BlockingQueue实现背压控制
     */
    private final java.util.concurrent.BlockingQueue<EngineMessage> pendingMessages = 
        new java.util.concurrent.LinkedBlockingQueue<>(WebSocketConstants.MAX_PENDING_MESSAGES);
    
    /**
     * 丢弃消息计数（队列满时丢弃）
     */
    private final AtomicInteger droppedMessageCount = new AtomicInteger(0);

    public WebSocketClientManager(EngineProperties properties,
                                   @Qualifier("taskScheduler") ThreadPoolTaskScheduler scheduler,
                                   MessageRouter messageRouter) {
        this.properties = properties;
        this.scheduler = scheduler;
        this.messageRouter = messageRouter;
    }

    /**
     * 应用启动完成后自动连接主节点
     * 延迟执行，确保 Playwright 初始化完成
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("[ClientManager] 应用启动完成，准备连接主节点");
        
        // 注入 WebSocketClientManager 到 CapabilityManager（解决循环依赖）
        if (capabilityManager != null) {
            capabilityManager.setWebSocketClientManager(this);
        }
        
        // 延迟2秒连接，确保 Playwright 初始化完成
        scheduler.schedule(() -> {
            log.info("[ClientManager] Playwright 初始化完成，开始连接主节点");
            connect();
        }, new java.util.Date(System.currentTimeMillis() + 2000));
    }

    /**
     * 连接主节点
     */
    public synchronized void connect() {
        if (client != null && client.isOpen()) {
            log.warn("[ClientManager] 已存在活跃连接，请先断开");
            return;
        }

        String wsUrl = properties.getWsUrl();
        log.info("[ClientManager] 开始连接主节点 - 地址: {}", wsUrl);

        try {
            URI serverUri = new URI(wsUrl);
            ScheduledExecutorService schedulerService = scheduler.getScheduledExecutor();

            client = new EngineWebSocketClient(
                serverUri,
                properties,
                schedulerService,
                this::handleMessage
            );

            // 设置能力列表（连接时上报给 Admin）
            if (capabilityManager != null) {
                List<Map<String, Object>> capabilities = capabilityManager.getCapabilityList();
                client.setCapabilities(capabilities);
                log.info("[ClientManager] 已加载 {} 个能力", capabilities.size());
            }

            // 设置最大消息大小（支持大文本传输）
            int maxMessageSize = properties.getConnection().getMaxMessageSize();
            client.setConnectionLostTimeout(properties.getConnection().getHeartbeatTimeout());
            
            // 异步连接
            client.connect();
            
            log.info("[ClientManager] 最大消息大小: {}MB", maxMessageSize / 1024 / 1024);
            initialized = true;
            
            log.info("[ClientManager] 连接请求已发送，等待建立连接...");

        } catch (Exception e) {
            log.error("[ClientManager] 连接初始化失败 - 错误类型: {}, 错误信息: {}",
                e.getClass().getSimpleName(), e.getMessage());
        }
    }

    /**
     * 处理接收到的消息
     */
    private void handleMessage(EngineMessage message) {
        try {
            messageRouter.route(message);
        } catch (Exception e) {
            log.error("[ClientManager] 消息处理异常 - 消息类型: {}, 错误: {}",
                message.getType(), e.getMessage());
        }
    }

    /**
     * 发送消息（支持重连期间消息暂存）
     *
     * @param message 消息对象
     * @return true 发送成功或已加入待发送队列，false 发送失败
     */
    public boolean sendMessage(EngineMessage message) {
        if (client == null || !client.isOpen()) {
            // 连接未建立，尝试加入待发送队列
            return queuePendingMessage(message);
        }

        try {
            client.sendMessage(message);
            return true;
        } catch (Exception e) {
            log.error("[ClientManager] 消息发送失败 - 类型: {}, 用户: {}, 错误: {}", 
                message.getType(), message.getUserId(), e.getMessage(), e);
            // 发送失败，加入待发送队列
            return queuePendingMessage(message);
        }
    }
    
    /**
     * 将消息加入待发送队列（带背压控制）
     */
    private boolean queuePendingMessage(EngineMessage message) {
        boolean queued = pendingMessages.offer(message);
        if (!queued) {
            droppedMessageCount.incrementAndGet();
            log.warn("[ClientManager] 队列已满，丢弃消息 - 类型: {}, 总丢弃: {}", 
                message.getType(), droppedMessageCount.get());
            
            // 发送错误通知给用户
            if (message.getUserId() != null) {
                log.error("[ClientManager] 用户消息丢弃 - 用户: {}, 类型: {}", 
                    message.getUserId(), message.getType());
            }
            return false;
        }
        
        log.debug("[ClientManager] 消息已加入待发送队列 - 类型: {}, 队列大小: {}", 
            message.getType(), pendingMessages.size());
        return true;
    }
    
    /**
     * 重发待发送消息（连接恢复后调用）
     */
    public void flushPendingMessages() {
        if (client == null || !client.isOpen()) {
            return;
        }
        
        int sent = 0;
        int failed = 0;
        EngineMessage message;
        
        while ((message = pendingMessages.poll()) != null) {
            try {
                client.sendMessage(message);
                sent++;
            } catch (Exception e) {
                failed++;
                log.warn("[ClientManager] 重发消息失败 - 类型: {}, 错误: {}", message.getType(), e.getMessage());
            }
        }
        
        if (sent > 0 || failed > 0) {
            log.info("[ClientManager] 重发待发送消息完成 - 成功: {}, 失败: {}", sent, failed);
        }
    }
    
    /**
     * 获取待发送消息数量
     */
    public int getPendingMessageCount() {
        return pendingMessages.size();
    }
    
    /**
     * 获取丢弃消息数量
     */
    public int getDroppedMessageCount() {
        return droppedMessageCount.get();
    }

    /**
     * 断开连接
     */
    public synchronized void disconnect() {
        if (client != null) {
            client.closeGracefully();
            client = null;
        }
    }

    /**
     * 重新连接
     */
    public void reconnect() {
        log.info("[ClientManager] 重新连接主节点");
        disconnect();
        connect();
    }

    /**
     * 获取连接状态
     */
    public boolean isConnected() {
        return client != null && client.isOpen();
    }

    /**
     * 获取详细连接状态
     */
    public EngineWebSocketClient.ConnectionStatus getConnectionStatus() {
        if (client == null) {
            return new EngineWebSocketClient.ConnectionStatus(
                false, false, 0, 0, 0
            );
        }
        return client.getStatus();
    }

    /**
     * 优雅关闭
     */
    @PreDestroy
    public void shutdown() {
        disconnect();
    }
}
