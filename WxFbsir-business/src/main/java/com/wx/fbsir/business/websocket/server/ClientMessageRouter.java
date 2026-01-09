package com.wx.fbsir.business.websocket.server;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import com.wx.fbsir.business.aigc.domain.AiRequest;
import com.wx.fbsir.business.aigc.service.IAigcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Client 消息路由器
 * 
 * 负责在前端和 Engine 之间路由消息
 * 
 * 消息流向：
 *   前端 → Admin → Engine（请求）
 *   Engine → Admin → 前端（响应，可能多次）
 *
 * @author wxfbsir
 * @date 2025-12-18
 */
@Component
public class ClientMessageRouter {

    private static final Logger log = LoggerFactory.getLogger(ClientMessageRouter.class);
    
    // 🔥 sessionId → chatId 缓存（供 EngineMessageRouter 使用）
    private static final ConcurrentHashMap<String, String> SESSION_CHAT_ID_CACHE = new ConcurrentHashMap<>();

    private final ClientSessionManager clientSessionManager;
    private final EngineSessionManager engineSessionManager;
    private final IAigcService aigcService;
    
    /**
     * 缓存 sessionId → chatId 映射
     */
    public static void cacheChatId(String sessionId, String chatId) {
        if (sessionId != null && chatId != null && !chatId.isEmpty()) {
            SESSION_CHAT_ID_CACHE.put(sessionId, chatId);
            log.debug("[ChatId缓存] 已缓存: {} -> {}", sessionId, chatId);
        }
    }
    
    /**
     * 获取缓存的 chatId
     */
    public static String getCachedChatId(String sessionId) {
        return sessionId != null ? SESSION_CHAT_ID_CACHE.get(sessionId) : null;
    }
    
    /**
     * 清除缓存（可选，防止内存泄漏）
     */
    public static void removeCachedChatId(String sessionId) {
        if (sessionId != null) {
            SESSION_CHAT_ID_CACHE.remove(sessionId);
        }
    }

    public ClientMessageRouter(ClientSessionManager clientSessionManager,
                                EngineSessionManager engineSessionManager,
                                IAigcService aigcService) {
        this.clientSessionManager = clientSessionManager;
        this.engineSessionManager = engineSessionManager;
        this.aigcService = aigcService;
    }

    /**
     * 路由消息到 Engine
     * 
     * 核心职责：
     * 1. 强制生成requestId（确保全链路追踪）
     * 2. 验证Engine可用性和能力
     * 3. 【透明转发】完整保留payload字段，Admin不做任何处理
     * 
     * ⚠️ 必须在请求中指定 engineId，不支持自动选择
     * ⚠️ requestId由后端强制生成，前端传递的requestId会被忽略
     * ⚠️ payload字段完全透传，Admin不解析、不修改、不验证
     */
    public void routeToEngine(String clientId, String rawMessage) {
        try {
            JSONObject json = JSON.parseObject(rawMessage);
            String type = json.getString("type");
            String userId = extractUserId(clientId);
            
            // ━━━━━━━━━━ 获取 engineId（必须指定）━━━━━━━━━━
            String engineId = json.getString("engineId");
            
            // 检查是否指定了 engineId
            if (engineId == null || engineId.isEmpty()) {
                sendError(clientId, type, "ENGINE_NOT_SPECIFIED", "必须指定 engineId 参数");
                log.warn("[Router] 未指定 engineId - 用户: {}, 类型: {}", userId, type);
                return;
            }
            
            // 检查 Engine 是否在线
            if (!engineSessionManager.isEngineOnline(engineId)) {
                sendError(clientId, type, "ENGINE_OFFLINE", "指定的 Engine [" + engineId + "] 不在线");
                log.warn("[Router] Engine 不在线: {} - 用户: {}, 类型: {}", engineId, userId, type);
                return;
            }
            
            // 检查 Engine 是否具有请求的能力
            EngineSession engineSession = engineSessionManager.getSessionByEngineId(engineId);
            if (engineSession != null && !engineSession.hasCapability(type)) {
                sendError(clientId, type, "CAPABILITY_NOT_FOUND", 
                    "Engine [" + engineId + "] 没有 [" + type + "] 能力，请确认后再次尝试");
                log.warn("[Router] Engine 无此能力: {} - Engine: {}, 用户: {}", type, engineId, userId);
                return;
            }
            
            // ━━━━━━━━━━ 强制生成requestId（安全核心）━━━━━━━━━━
            json.remove("requestId");
            String requestId = com.wx.fbsir.business.websocket.util.RequestIdGenerator.generate(userId, type);
            json.put("requestId", requestId);
            
            // ━━━━━━━━━━ 添加路由必要字段，完整保留payload ━━━━━━━━━━
            json.put("userId", userId);
            json.put("sourceClientId", clientId);
            json.put("sourceType", "WEBSOCKET");
            
            // 🔥 缓存 sessionId → chatId 映射（用于 Engine 返回时查找）
            JSONObject payload = json.getJSONObject("payload");
            String sessionId = null;
            String chatId = null;
            String userPrompt = null;
            
            if (payload != null) {
                sessionId = payload.getString("sessionId");
                userPrompt = payload.getString("userPrompt");
                chatId = json.getString("chatId");  // 顶层chatId
                if (chatId == null || chatId.isEmpty()) {
                    chatId = payload.getString("chatId");  // payload中的chatId
                }
                if (sessionId != null && chatId != null && !chatId.isEmpty()) {
                    cacheChatId(sessionId, chatId);
                    log.info("[Router] 🔥 缓存chatId: sessionId={} -> chatId={}", sessionId, chatId);
                }
            }
            
            // 🔥🔥🔥 先存后发：预保存请求记录到数据库（核心改进）
            if (sessionId != null && type != null && type.startsWith("AI_")) {
                try {
                    AiRequest aiRequest = new AiRequest();
                    aiRequest.setSessionId(sessionId);
                    aiRequest.setChatId(chatId);
                    aiRequest.setUserId(userId);
                    aiRequest.setUserPrompt(userPrompt);
                    aiRequest.setType(type);
                    
                    // 🔥 保存任务流程和进度日志到extraParams
                    java.util.Map<String, Object> extraParams = new java.util.HashMap<>();
                    if (payload != null) {
                        Object enabledAIs = payload.get("enabledAIs");
                        Object progressLogs = payload.get("progressLogs");
                        if (enabledAIs != null) {
                            extraParams.put("enabledAIs", enabledAIs);
                        }
                        if (progressLogs != null) {
                            extraParams.put("progressLogs", progressLogs);
                        }
                    }
                    if (!extraParams.isEmpty()) {
                        aiRequest.setExtraParams(extraParams);
                    }
                    
                    aigcService.saveInitialRequest(aiRequest);
                    log.info("[Router] 🔥 预保存请求记录 - sessionId={}, chatId={}, userPrompt={}, 扩展字段数={}", 
                        sessionId, chatId, userPrompt, extraParams.size());
                } catch (Exception e) {
                    log.warn("[Router] 预保存请求失败，继续转发: {}", e.getMessage());
                }
            }
            
            // 直接发送修改后的JSON字符串给Engine
            String messageToSend = json.toJSONString();
            
            // 发送消息到Engine
            boolean sent = engineSessionManager.sendRawMessage(engineId, messageToSend);
            if (!sent) {
                sendError(clientId, type, "SEND_FAILED", "消息发送失败，Engine可能已离线");
                log.error("[Router] 发送失败: {} -> {} (用户: {}, 请求ID: {})", type, engineId, userId, requestId);
            } else {
                log.debug("[Router] 转发到 Engine: {} -> {} (用户: {}, 请求ID: {})", type, engineId, userId, requestId);
            }
            
        } catch (JSONException e) {
            // JSON解析错误 - 友好提示
            log.error("========================================");
            log.error("[Admin路由] ❌ JSON解析失败");
            log.error("[原始消息] {}", rawMessage);
            log.error("[错误原因] {}", e.getMessage());
            log.error("========================================");
            
            // 构建友好的错误消息
            String friendlyMessage = "JSON格式错误，请检查：\n" +
                "1. 是否有多余的换行符或空格\n" +
                "2. 是否有未转义的特殊字符\n" +
                "3. 是否缺少引号或逗号\n" +
                "原始错误: " + e.getMessage();
            
            sendError(clientId, "ERROR", "JSON_PARSE_ERROR", friendlyMessage);
            
        } catch (Exception e) {
            // 其他错误
            log.error("========================================");
            log.error("[Admin路由] ❌ 处理客户端消息失败");
            log.error("[原始消息] {}", rawMessage);
            log.error("[错误详情]", e);
            log.error("========================================");
            
            sendError(clientId, "ERROR", "PROCESS_ERROR", "消息处理失败: " + e.getMessage());
        }
    }

    /**
     * 路由 Engine 响应到前端
     */
    public void routeToClient(String userId, String message) {
        try {
            JSONObject json = JSON.parseObject(message);
            String type = json.getString("type");
            
            // 根据消息类型前缀决定发送目标
            if (type != null) {
                if (type.contains("PC_") || type.startsWith("RETURN_PC_")) {
                    // PC 专用消息
                    clientSessionManager.sendToClient("web-" + userId, message);
                    clientSessionManager.sendToClient("mypc-" + userId, message);
                } else if (type.contains("MINI_")) {
                    // 小程序专用消息
                    clientSessionManager.sendToClient("mini-" + userId, message);
                } else {
                    // 通用消息，发送给所有端
                    clientSessionManager.sendToUser(userId, message);
                }
            } else {
                // 无类型，发送给所有端
                clientSessionManager.sendToUser(userId, message);
            }
            
            log.debug("[Router] 转发到用户: {} -> {}", type, userId);
            
        } catch (Exception e) {
            log.error("[Router] 处理 Engine 响应失败", e);
        }
    }

    /**
     * 发送错误消息给客户端
     */
    private void sendError(String clientId, String type, String errorCode, String errorMessage) {
        JSONObject error = new JSONObject();
        error.put("type", type != null ? type + "_ERROR" : "ERROR");
        error.put("success", false);
        error.put("errorCode", errorCode);
        error.put("errorMessage", errorMessage);
        error.put("timestamp", System.currentTimeMillis());
        clientSessionManager.sendToClient(clientId, error.toJSONString());
    }

    /**
     * 从 clientId 提取 userId
     */
    private String extractUserId(String clientId) {
        if (clientId.startsWith("web-")) {
            return clientId.substring(4);
        } else if (clientId.startsWith("mypc-")) {
            return clientId.substring(5);
        } else if (clientId.startsWith("mini-")) {
            return clientId.substring(5);
        }
        return clientId;
    }
}
