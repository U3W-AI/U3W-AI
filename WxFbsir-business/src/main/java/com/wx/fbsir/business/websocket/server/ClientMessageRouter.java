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
import java.util.concurrent.TimeUnit;

/**
 * Client 消息路由器
 * 
 * 负责在前端和 Engine 之间路由消息
 * 
 * 消息流向：
 *   前端 → Admin → Engine（请求）
 *   Engine → Admin → 前端（响应，可能多次）
 * 
 * 🤖 AIGC消息说明：
 *   - AI_前缀的消息类型由AIGC模块独立处理
 *   - Admin对payload完全透传，不做解析和验证
 *   - Engine端负责解析AI平台特定参数
 *
 * @author wxfbsir
 * @date 2025-12-18
 */
@Component
public class ClientMessageRouter {

    private static final Logger log = LoggerFactory.getLogger(ClientMessageRouter.class);
    
    // 🔥 sessionId → chatId 缓存（供 EngineMessageRouter 使用）
    private static final ConcurrentHashMap<String, String> SESSION_CHAT_ID_CACHE = new ConcurrentHashMap<>();

    /**
     * 请求来源登记表。Engine 的业务结果通常只回传 requestId，
     * 因此由主节点保留来源，避免结果回到路由器时退化为兼容双路由。
     */
    private static final long REQUEST_SOURCE_TTL_MILLIS = TimeUnit.MINUTES.toMillis(10);
    private static final ConcurrentHashMap<String, RequestSourceRegistration> REQUEST_SOURCE_CACHE =
        new ConcurrentHashMap<>();

    private record RequestSourceRegistration(String sourceType, long registeredAtMillis) {
    }

    public static void registerRequestSource(String requestId, String sourceType) {
        registerRequestSource(requestId, sourceType, System.currentTimeMillis());
    }

    static void registerRequestSource(String requestId, String sourceType, long registeredAtMillis) {
        if (requestId != null && !requestId.isEmpty() && sourceType != null && !sourceType.isEmpty()) {
            REQUEST_SOURCE_CACHE.put(requestId, new RequestSourceRegistration(sourceType, registeredAtMillis));
            cleanupExpiredRequestSources(registeredAtMillis);
        }
    }

    public static String getRequestSource(String requestId) {
        return getRequestSource(requestId, System.currentTimeMillis());
    }

    static String getRequestSource(String requestId, long currentTimeMillis) {
        if (requestId == null) {
            return null;
        }
        RequestSourceRegistration registration = REQUEST_SOURCE_CACHE.get(requestId);
        if (registration == null) {
            return null;
        }
        if (isExpired(registration, currentTimeMillis)) {
            REQUEST_SOURCE_CACHE.remove(requestId, registration);
            return null;
        }
        return registration.sourceType();
    }

    public static void removeRequestSource(String requestId) {
        if (requestId != null) {
            REQUEST_SOURCE_CACHE.remove(requestId);
        }
    }

    static void clearRequestSourceCache() {
        REQUEST_SOURCE_CACHE.clear();
    }

    static int getRequestSourceCacheSize() {
        return REQUEST_SOURCE_CACHE.size();
    }

    static void cleanupExpiredRequestSources(long currentTimeMillis) {
        REQUEST_SOURCE_CACHE.entrySet().removeIf(entry -> isExpired(entry.getValue(), currentTimeMillis));
    }

    private static boolean isExpired(RequestSourceRegistration registration, long currentTimeMillis) {
        return currentTimeMillis - registration.registeredAtMillis() > REQUEST_SOURCE_TTL_MILLIS;
    }

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
        String requestId = null;
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
            
            // 🔥 检查 Engine 是否在线（AI请求必须先验证）
            if (!engineSessionManager.isEngineOnline(engineId)) {
                // 发送友好的AI任务错误消息
                sendAiTaskError(clientId, userId, type, engineId, 
                    "主机不在线", 
                    "您配置的AI主机 [" + engineId + "] 当前不在线，请检查：\n" +
                    "1. 确认主机ID是否正确\n" +
                    "2. 确认Engine服务是否已启动\n" +
                    "3. 确认网络连接是否正常\n\n" +
                    "如需帮助，请联系管理员");
                log.warn("[Router] ❌ Engine 不在线: {} - 用户: {}, 类型: {}", engineId, userId, type);
                return;
            }
            
            // 🔥 检查 Engine 是否具有请求的能力
            EngineSession engineSession = engineSessionManager.getSessionByEngineId(engineId);
            if (engineSession != null && !engineSession.hasCapability(type)) {
                sendAiTaskError(clientId, userId, type, engineId,
                    "主机不支持此功能",
                    "Engine [" + engineId + "] 不支持 [" + type + "] 功能\n\n" +
                    "请确认：\n" +
                    "1. Engine版本是否支持此功能\n" +
                    "2. 是否需要更新Engine服务");
                log.warn("[Router] ❌ Engine 无此能力: {} - Engine: {}, 用户: {}", type, engineId, userId);
                return;
            }
            
            // ━━━━━━━━━━ 强制生成requestId（安全核心）━━━━━━━━━━
            json.remove("requestId");
            requestId = com.wx.fbsir.business.websocket.util.RequestIdGenerator.generate(userId, type);
            json.put("requestId", requestId);
            registerRequestSource(requestId, "WEBSOCKET");

            // ━━━━━━━━━━ 添加路由必要字段，完整保留payload ━━━━━━━━━━
            json.put("userId", userId);
            json.put("sourceClientId", clientId);
            json.put("sourceType", "WEBSOCKET");

            // Engine控制器从payload读取关联字段；同时保留顶层字段兼容旧版本
            JSONObject payload = json.getJSONObject("payload");
            if (payload == null) {
                payload = new JSONObject();
                json.put("payload", payload);
            }
            payload.put("requestId", requestId);
            payload.put("sourceType", "WEBSOCKET");

            // 🔥 缓存 sessionId → chatId 映射（用于 Engine 返回时查找）
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
            
            // ==========================================================================
            // 🤖 AIGC请求预处理（先存后发架构）
            // 说明：AI_前缀的消息需要预保存到数据库，payload完全透传给Engine
            // ==========================================================================
            if (sessionId != null && type != null && type.startsWith("AI_")) {
                try {
                    AiRequest aiRequest = new AiRequest();
                    aiRequest.setSessionId(sessionId);
                    aiRequest.setChatId(chatId);
                    aiRequest.setUserId(userId);
                    aiRequest.setUserPrompt(userPrompt);
                    aiRequest.setType(type);
                    
                    // 🤖 提取aiType用于数据库存储区分
                    String aiType = payload != null ? (String) payload.get("aiType") : null;
                    aiRequest.setAiType(aiType);
                    
                    // 🤖 保存完整payload（Admin透传，Engine解析）
                    if (payload != null) {
                        java.util.Map<String, Object> payloadMap = new java.util.HashMap<>();
                        payload.forEach((k, v) -> payloadMap.put(k, v));
                        aiRequest.setPayload(payloadMap);
                    }
                    
                    // 🤖 保存任务流程元数据到extraParams（用于Admin端业务逻辑）
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
                    log.info("[AIGC路由] 🔥 预保存请求 - sessionId={}, aiType={}, userPrompt={}", 
                        sessionId, aiType, userPrompt);
                } catch (Exception e) {
                    log.warn("[AIGC路由] 预保存失败，继续透传: {}", e.getMessage());
                }
            }
            // ==========================================================================
            // 🤖 AIGC请求预处理结束
            // ==========================================================================
            
            // 直接发送修改后的JSON字符串给Engine
            String messageToSend = json.toJSONString();
            
            // 发送消息到Engine
            boolean sent = engineSessionManager.sendRawMessage(engineId, messageToSend);
            if (!sent) {
                removeRequestSource(requestId);
                sendError(clientId, type, "SEND_FAILED", "消息发送失败，Engine可能已离线");
                log.error("[Router] 发送失败: {} -> {} (用户: {}, 请求ID: {})", type, engineId, userId, requestId);
            } else {
                log.debug("[Router] 转发到 Engine: {} -> {} (用户: {}, 请求ID: {})", type, engineId, userId, requestId);
            }
            
        } catch (JSONException e) {
            removeRequestSource(requestId);
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
            removeRequestSource(requestId);
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
     * 发送AI任务错误消息给客户端（友好提示）
     * 使用AI_TASK_ERROR格式，前端可以在AI任务流程中展示
     */
    private void sendAiTaskError(String clientId, String userId, String originalType, String engineId, 
                                  String errorTitle, String errorMessage) {
        JSONObject error = new JSONObject();
        error.put("type", "AI_TASK_ERROR");
        error.put("success", false);
        
        // payload中包含详细错误信息
        JSONObject payload = new JSONObject();
        payload.put("errorCode", "ENGINE_OFFLINE");
        payload.put("errorTitle", errorTitle);
        payload.put("errorMessage", errorMessage);
        payload.put("engineId", engineId);
        payload.put("originalType", originalType);
        payload.put("timestamp", System.currentTimeMillis());
        
        error.put("payload", payload);
        
        // 发送给客户端
        clientSessionManager.sendToClient(clientId, error.toJSONString());
        
        // 同时发送给用户的所有端（如果有多个设备登录）
        clientSessionManager.sendToUser(userId, error.toJSONString());
        
        log.info("[Router] 已发送AI任务错误提示: {} - 用户: {}, Engine: {}", errorTitle, userId, engineId);
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
