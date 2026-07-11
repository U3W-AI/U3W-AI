package com.wx.fbsir.engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Engine 副节点配置属性
 * <p>
 * 集中管理所有配置项
 * 注意：本项目禁止从环境变量读取参数，所有配置必须在 application.yml 中明确指定
 *
 * @author FBSir
 * &#064;date  2025-12-15
 */
@Component
@ConfigurationProperties(prefix = "fbsir.engine")
@Validated
public class EngineProperties {

    /**
     * 主节点 WebSocket 连接地址
     * 如果未配置，启动时会提示在终端输入
     */
    private String wsUrl;

    /**
     * 主机ID（必须向管理员申请）
     * 这是连接白名单验证的唯一凭证
     * 如果未配置，启动时会提示在终端输入
     */
    private String hostId;

    /** Shared credential for the Admin WebSocket and Engine HTTP callbacks. */
    @NotBlank(message = "Engine credential must be configured")
    @Size(min = 32, message = "Engine credential must contain at least 32 characters")
    private String engineToken;

    /**
     * 节点版本
     */
    private String version = "1.0.0";

    /**
     * 连接配置
     */
    private ConnectionConfig connection = new ConnectionConfig();

    /**
     * 重连配置
     */
    private ReconnectConfig reconnect = new ReconnectConfig();
    
    // ========== Getters and Setters ==========
    
    public String getWsUrl() {
        return wsUrl;
    }
    
    public void setWsUrl(String wsUrl) {
        this.wsUrl = wsUrl;
    }
    
    public String getHostId() {
        return hostId;
    }
    
    public void setHostId(String hostId) {
        this.hostId = hostId;
    }

    public String getEngineToken() {
        return engineToken;
    }

    public void setEngineToken(String engineToken) {
        this.engineToken = engineToken;
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public ConnectionConfig getConnection() {
        return connection;
    }
    
    public void setConnection(ConnectionConfig connection) {
        this.connection = connection;
    }
    
    public ReconnectConfig getReconnect() {
        return reconnect;
    }
    
    public void setReconnect(ReconnectConfig reconnect) {
        this.reconnect = reconnect;
    }

    /**
     * 连接配置
     */
    public static class ConnectionConfig {
        
        /**
         * 连接超时时间（秒）
         */
        @Min(value = 1, message = "连接超时时间最小为1秒")
        private int timeout = 10;

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
         * 最大消息大小（字节）
         * 默认5MB，支持大文本传输
         */
        private int maxMessageSize = 5 * 1024 * 1024;
        
        public int getTimeout() {
            return timeout;
        }
        
        public void setTimeout(int timeout) {
            this.timeout = timeout;
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
        
        public int getMaxMessageSize() {
            return maxMessageSize;
        }
        
        public void setMaxMessageSize(int maxMessageSize) {
            this.maxMessageSize = maxMessageSize;
        }
    }

    /**
     * 重连配置（指数退避策略）
     */
    public static class ReconnectConfig {
        
        /**
         * 是否启用自动重连
         */
        private boolean enabled = true;

        /**
         * 最大重试次数（-1 表示无限重试）
         */
        private int maxRetries = -1;

        /**
         * 初始重连延迟（秒）
         */
        @Min(value = 1, message = "初始重连延迟最小为1秒")
        private int initialDelay = 1;

        /**
         * 最大重连延迟（秒）
         */
        @Min(value = 1, message = "最大重连延迟最小为1秒")
        private int maxDelay = 30;

        /**
         * 退避乘数
         */
        private double backoffMultiplier = 1.5;
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
        
        public int getMaxRetries() {
            return maxRetries;
        }
        
        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }
        
        public int getInitialDelay() {
            return initialDelay;
        }
        
        public void setInitialDelay(int initialDelay) {
            this.initialDelay = initialDelay;
        }
        
        public int getMaxDelay() {
            return maxDelay;
        }
        
        public void setMaxDelay(int maxDelay) {
            this.maxDelay = maxDelay;
        }
        
        public double getBackoffMultiplier() {
            return backoffMultiplier;
        }
        
        public void setBackoffMultiplier(double backoffMultiplier) {
            this.backoffMultiplier = backoffMultiplier;
        }
    }
}
