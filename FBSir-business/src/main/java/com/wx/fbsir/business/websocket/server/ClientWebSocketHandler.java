package com.wx.fbsir.business.websocket.server;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Client WebSocket 处理器
 * 
 * 处理前端/小程序的 WebSocket 连接和消息
 * 
 * 职责：
 *   1. 管理前端连接的生命周期
 *   2. 将前端消息路由到 Engine
 *   3. 将 Engine 响应转发给前端
 *
 * @author FBSir
 * @date 2025-12-18
 */
@Component
public class ClientWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ClientWebSocketHandler.class);

    private final ClientSessionManager sessionManager;
    private final ClientMessageRouter messageRouter;

    public ClientWebSocketHandler(ClientSessionManager sessionManager,
                                   ClientMessageRouter messageRouter) {
        this.sessionManager = sessionManager;
        this.messageRouter = messageRouter;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String clientId = (String) session.getAttributes().get("clientId");
        String clientType = (String) session.getAttributes().get("clientType");

        if (clientId == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        // 注册连接
        sessionManager.registerClient(clientId, session);

        // 发送连接确认
        JSONObject response = new JSONObject();
        response.put("type", "CONNECTED");
        response.put("message", "online");
        response.put("clientId", clientId);
        session.sendMessage(new TextMessage(response.toJSONString()));
        
        log.info("✅ Client 连接: {} ({})", clientId, clientType);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String clientId = (String) session.getAttributes().get("clientId");
        if (clientId != null) {
            sessionManager.removeClient(clientId, session);
            log.info("❌ Client 断开: {}", clientId);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String clientId = (String) session.getAttributes().get("clientId");
        String payload = message.getPayload();

        // 心跳消息静默处理
        if (payload.contains("heartbeat") || payload.contains("HEARTBEAT")) {
            handleHeartbeat(session, payload);
            return;
        }

        // 简化日志输出
        if (payload.length() > 100) {
            log.debug("📨 Client [{}]: {}...", clientId, payload.substring(0, 50));
        } else {
            log.debug("📨 Client [{}]: {}", clientId, payload);
        }

        // 路由消息到 Engine
        messageRouter.routeToEngine(clientId, payload);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String clientId = (String) session.getAttributes().get("clientId");
        log.error("⚠️ Client 传输错误 [{}]: {}", clientId, exception.getMessage());
    }

    /**
     * 处理心跳消息
     */
    private void handleHeartbeat(WebSocketSession session, String payload) throws Exception {
        JSONObject response = new JSONObject();
        response.put("type", "HEARTBEAT_PONG");
        response.put("timestamp", System.currentTimeMillis());
        session.sendMessage(new TextMessage(response.toJSONString()));
    }
}
