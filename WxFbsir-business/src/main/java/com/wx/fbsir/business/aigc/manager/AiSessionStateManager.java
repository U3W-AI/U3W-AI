package com.wx.fbsir.business.aigc.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * AI会话状态管理器
 * 
 * 职责：
 * 1. 跟踪每轮对话中各AI的完成状态
 * 2. 判断某轮对话是否全部完成
 * 3. 确保各AI任务相互独立，互不影响
 * 
 * 设计思路：
 * - 每个sessionId代表一轮对话
 * - 每轮对话可能包含多个AI任务（DeepSeek、元宝等）
 * - 每个AI任务独立处理，完成后标记状态
 * - 当所有AI任务完成后，标记整轮对话完成
 * 
 * @author wxfbsir
 * @date 2026-01-08
 */
@Component
public class AiSessionStateManager {

    private static final Logger log = LoggerFactory.getLogger(AiSessionStateManager.class);

    /**
     * 会话状态存储
     * Key: sessionId
     * Value: AiSessionState
     */
    private final Map<String, AiSessionState> sessions = new ConcurrentHashMap<>();

    /**
     * 会话超时时间（毫秒）- 2小时
     */
    private static final long SESSION_TIMEOUT = 2 * 60 * 60 * 1000;

    /**
     * 创建新的AI会话
     * 
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @param aiTypes 参与的AI类型列表
     */
    public void createSession(String sessionId, String userId, Set<String> aiTypes) {
        AiSessionState state = new AiSessionState(sessionId, userId, aiTypes);
        sessions.put(sessionId, state);
        log.info("[会话管理] 创建AI会话 - 会话: {}, 用户: {}, AI数量: {}, AI列表: {}", 
            sessionId, userId, aiTypes.size(), aiTypes);
    }

    /**
     * 标记某个AI任务开始
     * 
     * @param sessionId 会话ID
     * @param aiType AI类型
     */
    public void markAiStarted(String sessionId, String aiType) {
        AiSessionState state = sessions.get(sessionId);
        if (state != null) {
            state.markAiStarted(aiType);
            log.debug("[会话管理] AI任务开始 - 会话: {}, AI: {}", sessionId, aiType);
        }
    }

    /**
     * 标记某个AI任务完成
     * 
     * @param sessionId 会话ID
     * @param aiType AI类型
     * @return 是否所有AI任务都已完成
     */
    public boolean markAiCompleted(String sessionId, String aiType) {
        AiSessionState state = sessions.get(sessionId);
        if (state != null) {
            state.markAiCompleted(aiType);
            boolean allCompleted = state.isAllCompleted();
            
            log.info("[会话管理] ✅ AI任务完成 - 会话: {}, AI: {}, 进度: {}/{}, 整轮完成: {}", 
                sessionId, aiType, state.getCompletedCount(), state.getTotalCount(), allCompleted);
            
            if (allCompleted) {
                log.info("[会话管理] 🎉 整轮对话完成 - 会话: {}, 共{}个AI全部完成", sessionId, state.getTotalCount());
            }
            
            return allCompleted;
        }
        return false;
    }

    /**
     * 标记某个AI任务失败
     * 
     * @param sessionId 会话ID
     * @param aiType AI类型
     * @param errorMessage 错误消息
     */
    public void markAiFailed(String sessionId, String aiType, String errorMessage) {
        AiSessionState state = sessions.get(sessionId);
        if (state != null) {
            state.markAiFailed(aiType, errorMessage);
            log.warn("[会话管理] ❌ AI任务失败 - 会话: {}, AI: {}, 错误: {}", sessionId, aiType, errorMessage);
        }
    }

    /**
     * 获取会话状态
     * 
     * @param sessionId 会话ID
     * @return 会话状态
     */
    public AiSessionState getSessionState(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * 清理过期会话
     */
    public void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        int cleaned = 0;
        
        for (Map.Entry<String, AiSessionState> entry : sessions.entrySet()) {
            AiSessionState state = entry.getValue();
            if (now - state.getCreateTime() > SESSION_TIMEOUT) {
                sessions.remove(entry.getKey());
                cleaned++;
            }
        }
        
        if (cleaned > 0) {
            log.info("[会话管理] 清理过期会话 - 清理数量: {}, 剩余: {}", cleaned, sessions.size());
        }
    }

    /**
     * 移除会话
     * 
     * @param sessionId 会话ID
     */
    public void removeSession(String sessionId) {
        sessions.remove(sessionId);
        log.debug("[会话管理] 移除会话 - 会话: {}", sessionId);
    }

    /**
     * AI会话状态类
     */
    public static class AiSessionState {
        private final String sessionId;
        private final String userId;
        private final Set<String> aiTypes;
        private final Set<String> startedAis;
        private final Set<String> completedAis;
        private final Set<String> failedAis;
        private final Map<String, String> errorMessages;
        private final long createTime;

        public AiSessionState(String sessionId, String userId, Set<String> aiTypes) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.aiTypes = new CopyOnWriteArraySet<>(aiTypes);
            this.startedAis = new CopyOnWriteArraySet<>();
            this.completedAis = new CopyOnWriteArraySet<>();
            this.failedAis = new CopyOnWriteArraySet<>();
            this.errorMessages = new ConcurrentHashMap<>();
            this.createTime = System.currentTimeMillis();
        }

        public void markAiStarted(String aiType) {
            startedAis.add(aiType);
        }

        public void markAiCompleted(String aiType) {
            completedAis.add(aiType);
        }

        public void markAiFailed(String aiType, String errorMessage) {
            failedAis.add(aiType);
            if (errorMessage != null) {
                errorMessages.put(aiType, errorMessage);
            }
        }

        public boolean isAllCompleted() {
            // 所有AI要么完成要么失败，才算整轮结束
            return (completedAis.size() + failedAis.size()) >= aiTypes.size();
        }

        public int getCompletedCount() {
            return completedAis.size();
        }

        public int getTotalCount() {
            return aiTypes.size();
        }

        // Getters
        public String getSessionId() { return sessionId; }
        public String getUserId() { return userId; }
        public Set<String> getAiTypes() { return aiTypes; }
        public Set<String> getStartedAis() { return startedAis; }
        public Set<String> getCompletedAis() { return completedAis; }
        public Set<String> getFailedAis() { return failedAis; }
        public Map<String, String> getErrorMessages() { return errorMessages; }
        public long getCreateTime() { return createTime; }
    }
}
