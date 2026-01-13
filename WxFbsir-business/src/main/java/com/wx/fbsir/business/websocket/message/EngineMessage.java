package com.wx.fbsir.business.websocket.message;

import com.alibaba.fastjson2.JSON;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * WebSocket 统一消息格式
 * 
 * 主节点与副节点共用同一套消息格式定义
 * 替代老项目的字符串拼接，提供类型安全的消息封装
 *
 * @author wxfbsir
 * @date 2025-12-15
 */
public class EngineMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息唯一ID（用于幂等和追踪）
     */
    private String messageId;

    /**
     * 消息类型
     */
    private String type;

    /**
     * 来源 Engine ID
     */
    private String engineId;

    /**
     * Engine 版本号
     */
    private String version;

    /**
     * 用户ID（可选）
     */
    private String userId;

    /**
     * 链路追踪ID
     */
    private String traceId;

    /**
     * 时间戳
     */
    private long timestamp;

    /**
     * 业务数据
     */
    private Map<String, Object> payload;

    /**
     * 会话ID（多轮对话共享）
     */
    private String chatId;

    /**
     * 元数据
     */
    private Map<String, String> metadata;

    /**
     * 默认构造器
     */
    public EngineMessage() {
        this.messageId = UUID.randomUUID().toString().replace("-", "");
        this.timestamp = System.currentTimeMillis();
        this.payload = new HashMap<>();
        this.metadata = new HashMap<>();
    }

    /**
     * 创建消息构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 从 JSON 字符串解析消息
     *
     * @param json JSON 字符串
     * @return EngineMessage 对象，解析失败返回 null
     */
    public static EngineMessage fromJson(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return JSON.parseObject(json, EngineMessage.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 转换为 JSON 字符串
     */
    public String toJson() {
        return JSON.toJSONString(this);
    }

    /**
     * 获取消息类型枚举
     */
    public MessageType getMessageType() {
        return MessageType.fromCode(this.type);
    }

    /**
     * 获取 payload 中的值
     */
    @SuppressWarnings("unchecked")
    public <T> T getPayloadValue(String key) {
        if (payload == null) {
            return null;
        }
        return (T) payload.get(key);
    }

    /**
     * 设置 payload 值
     */
    public EngineMessage setPayloadValue(String key, Object value) {
        if (this.payload == null) {
            this.payload = new HashMap<>();
        }
        this.payload.put(key, value);
        return this;
    }

    // ========== Getter/Setter ==========

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getEngineId() {
        return engineId;
    }

    public void setEngineId(String engineId) {
        this.engineId = engineId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public String getChatId() {
        return chatId;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    @Override
    public String toString() {
        return "EngineMessage{" +
                "messageId='" + messageId + '\'' +
                ", type='" + type + '\'' +
                ", engineId='" + engineId + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }

    /**
     * 消息构建器
     */
    public static class Builder {
        private final EngineMessage message;

        public Builder() {
            this.message = new EngineMessage();
        }

        public Builder type(MessageType type) {
            this.message.setType(type.getCode());
            return this;
        }

        public Builder type(String type) {
            this.message.setType(type);
            return this;
        }

        public Builder engineId(String engineId) {
            this.message.setEngineId(engineId);
            return this;
        }

        public Builder version(String version) {
            this.message.setVersion(version);
            return this;
        }

        public Builder userId(String userId) {
            this.message.setUserId(userId);
            return this;
        }

        public Builder traceId(String traceId) {
            this.message.setTraceId(traceId);
            return this;
        }

        public Builder payload(String key, Object value) {
            this.message.setPayloadValue(key, value);
            return this;
        }

        public Builder payload(Map<String, Object> payload) {
            this.message.setPayload(payload);
            return this;
        }

        public Builder metadata(String key, String value) {
            if (this.message.getMetadata() == null) {
                this.message.setMetadata(new HashMap<>());
            }
            this.message.getMetadata().put(key, value);
            return this;
        }

        public EngineMessage build() {
            return this.message;
        }
    }
}
