package com.wx.fbsir.business.websocket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * WebSocket 服务端配置属性
 * 
 * 主节点 WebSocket 服务配置，支持环境变量覆盖
 *
 * @author FBSir
 * @date 2025-12-15
 */
@Component
@ConfigurationProperties(prefix = "wxfbsir.websocket")
@Validated
public class WebSocketProperties {

    /**
     * 是否启用 WebSocket 服务
     */
    private boolean enabled = true;

    /**
     * Engine 端点路径（副节点连接）
     */
    private String enginePath = "/ws/engine";

    /**
     * Client 端点路径（前端/小程序连接）
     */
    private String clientPath = "/ws/client";

    /**
     * 最大连接数
     */
    @Min(value = 1, message = "最大连接数最小为1")
    @Max(value = 100000, message = "最大连接数最大为100000")
    private int maxConnections = 10000;

    /**
     * 最大消息大小（字节）
     * 默认5MB，支持大文本传输（如AI对话结果、长文章等）
     * 可通过配置调整，最大建议不超过10MB
     */
    private int maxMessageSize = 5 * 1024 * 1024;  // 5MB

    /**
     * 心跳间隔（秒）
     */
    @Min(value = 5, message = "心跳间隔最小为5秒")
    private int heartbeatInterval = 30;

    /**
     * 心跳超时时间（秒）
     */
    @Min(value = 1, message = "心跳超时时间最小为1秒")
    private int heartbeatTimeout = 10;

    /**
     * 会话清理间隔（秒）
     */
    private int sessionCleanupInterval = 60;

    /**
     * 消息队列最大大小
     */
    private int messageQueueSize = 10000;

    // ========== Getter/Setter ==========

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEnginePath() {
        return enginePath;
    }

    public void setEnginePath(String enginePath) {
        this.enginePath = enginePath;
    }

    public String getClientPath() {
        return clientPath;
    }

    public void setClientPath(String clientPath) {
        this.clientPath = clientPath;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public int getMaxMessageSize() {
        return maxMessageSize;
    }

    public void setMaxMessageSize(int maxMessageSize) {
        this.maxMessageSize = maxMessageSize;
    }

    public int getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public void setHeartbeatInterval(int heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    public int getHeartbeatTimeout() {
        return heartbeatTimeout;
    }

    public void setHeartbeatTimeout(int heartbeatTimeout) {
        this.heartbeatTimeout = heartbeatTimeout;
    }

    public int getSessionCleanupInterval() {
        return sessionCleanupInterval;
    }

    public void setSessionCleanupInterval(int sessionCleanupInterval) {
        this.sessionCleanupInterval = sessionCleanupInterval;
    }

    public int getMessageQueueSize() {
        return messageQueueSize;
    }

    public void setMessageQueueSize(int messageQueueSize) {
        this.messageQueueSize = messageQueueSize;
    }
}
