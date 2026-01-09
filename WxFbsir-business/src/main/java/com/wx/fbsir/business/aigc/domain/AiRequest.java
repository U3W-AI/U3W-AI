package com.wx.fbsir.business.aigc.domain;

import com.wx.fbsir.common.core.domain.BaseEntity;

import java.util.Map;

/**
 * AI请求对象
 * 
 * @author wxfbsir
 * @date 2026-01-07
 */
public class AiRequest extends BaseEntity {
    
    private static final long serialVersionUID = 1L;

    /** 请求ID（系统生成，全链路跟踪） */
    private String requestId;

    /** 请求类型（如：AI_DEEPSEEK_QUERY、AI_DEEPSEEK_CHECK_LOGIN等） */
    private String type;

    /** 用户ID */
    private String userId;

    /** 用户名 */
    private String username;

    /** 主机ID */
    private String hostId;

    /** 用户提问内容 */
    private String userPrompt;

    /** 会话ID（用于继续对话，多轮对话共享） */
    private String chatId;

    /** 会话请求ID（每次请求唯一，作为数据库主键） */
    private String sessionId;

    /** 是否启用深度思考（DeepSeek专用） */
    private Boolean enableDeepThinking = false;

    /** 是否启用联网搜索（DeepSeek专用） */
    private Boolean enableWebSearch = false;

    /** 扩展参数 */
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

    public Boolean getEnableDeepThinking() {
        return enableDeepThinking;
    }

    public void setEnableDeepThinking(Boolean enableDeepThinking) {
        this.enableDeepThinking = enableDeepThinking;
    }

    public Boolean getEnableWebSearch() {
        return enableWebSearch;
    }

    public void setEnableWebSearch(Boolean enableWebSearch) {
        this.enableWebSearch = enableWebSearch;
    }

    public Map<String, Object> getExtraParams() {
        return extraParams;
    }

    public void setExtraParams(Map<String, Object> extraParams) {
        this.extraParams = extraParams;
    }

    @Override
    public String toString() {
        return "AiRequest{" +
                "requestId='" + requestId + '\'' +
                ", type='" + type + '\'' +
                ", userId='" + userId + '\'' +
                ", username='" + username + '\'' +
                ", hostId='" + hostId + '\'' +
                ", userPrompt='" + userPrompt + '\'' +
                ", chatId='" + chatId + '\'' +
                ", enableDeepThinking=" + enableDeepThinking +
                ", enableWebSearch=" + enableWebSearch +
                ", extraParams=" + extraParams +
                '}';
    }
}
