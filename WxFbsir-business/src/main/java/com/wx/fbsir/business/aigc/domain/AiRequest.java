package com.wx.fbsir.business.aigc.domain;

import com.wx.fbsir.common.core.domain.BaseEntity;

import java.util.Map;

/**
 * 🤖 AIGC通用请求对象
 * 
 * 设计原则：
 * 1. 只保留通用字段，不包含任何AI平台专属字段
 * 2. AI平台特定参数统一放入payload，Admin完全透传给Engine
 * 3. Engine端根据type类型自行解析payload中的特定参数
 * 
 * 消息流向：前端 → Admin(透传) → Engine(解析payload)
 * 
 * @author wxfbsir
 * @date 2026-01-07
 */
public class AiRequest extends BaseEntity {
    
    private static final long serialVersionUID = 1L;

    // ==========================================================================
    // 🔧 系统字段（Admin自动生成/管理）
    // ==========================================================================
    
    /** 请求ID（系统生成，全链路跟踪） */
    private String requestId;

    /** 请求类型（如：AI_DEEPSEEK_QUERY、AI_KIMI_QUERY等，AI_前缀） */
    private String type;

    // ==========================================================================
    // 👤 用户信息
    // ==========================================================================
    
    /** 用户ID */
    private String userId;

    /** 用户名 */
    private String username;

    /** 主机ID */
    private String hostId;

    // ==========================================================================
    // 💬 会话信息
    // ==========================================================================
    
    /** 用户提问内容（便于快速查询，实际内容在payload中） */
    private String userPrompt;

    /** 会话ID（用于继续对话，多轮对话共享） */
    private String chatId;

    /** 会话请求ID（每次请求唯一，作为数据库主键） */
    private String sessionId;
    
    /** AI类型标识（如：deepseek、kimi、chatgpt，用于数据库存储区分） */
    private String aiType;

    // ==========================================================================
    // 📦 通用Payload（Admin透传，Engine解析）
    // ==========================================================================
    
    /** 
     * 完整请求载荷 - Admin完全透传给Engine
     * 
     * 各AI平台专属参数示例：
     * - DeepSeek: enableDeepThinking, enableWebSearch, deepseekChatId
     * - Kimi: enableSearch, fileIds
     * - ChatGPT: model, temperature, maxTokens
     * 
     * Admin不解析、不验证、不修改此字段
     */
    private Map<String, Object> payload;

    /** 扩展参数（用于Admin端业务逻辑，如enabledAIs、progressLogs等） */
    private Map<String, Object> extraParams;

    public AiRequest() {
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getHostId() {
        return hostId;
    }

    public void setHostId(String hostId) {
        this.hostId = hostId;
    }

    public String getUserPrompt() {
        return userPrompt;
    }

    public void setUserPrompt(String userPrompt) {
        this.userPrompt = userPrompt;
    }

    public String getChatId() {
        return chatId;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    
    public String getAiType() {
        return aiType;
    }
    
    public void setAiType(String aiType) {
        this.aiType = aiType;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    public Map<String, Object> getExtraParams() {
        return extraParams;
    }

    public void setExtraParams(Map<String, Object> extraParams) {
        this.extraParams = extraParams;
    }
    
    // ==========================================================================
    // 🔧 便捷方法
    // ==========================================================================
    
    /**
     * 从payload中获取指定参数
     */
    @SuppressWarnings("unchecked")
    public <T> T getPayloadValue(String key) {
        return payload != null ? (T) payload.get(key) : null;
    }
    
    /**
     * 从payload中获取字符串参数
     */
    public String getPayloadString(String key) {
        Object value = getPayloadValue(key);
        return value != null ? value.toString() : null;
    }
    
    /**
     * 从payload中获取布尔参数
     */
    public boolean getPayloadBoolean(String key, boolean defaultValue) {
        Object value = getPayloadValue(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }

    @Override
    public String toString() {
        return "AiRequest{" +
                "requestId='" + requestId + '\'' +
                ", type='" + type + '\'' +
                ", userId='" + userId + '\'' +
                ", aiType='" + aiType + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", chatId='" + chatId + '\'' +
                ", userPrompt='" + userPrompt + '\'' +
                ", payload=" + payload +
                '}';
    }
}
