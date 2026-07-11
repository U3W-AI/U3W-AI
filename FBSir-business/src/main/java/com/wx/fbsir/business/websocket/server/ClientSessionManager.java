package com.wx.fbsir.business.websocket.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Client 会话管理器
 * 
 * 管理前端/小程序的 WebSocket 连接
 *
 * @author FBSir
 * @date 2025-12-18
 */
@Component
public class ClientSessionManager {

    private static final Logger log = LoggerFactory.getLogger(ClientSessionManager.class);

    /**
     * 客户端连接池: clientId -> session
     */
    private final ConcurrentHashMap<String, WebSocketSession> clientSessions = new ConcurrentHashMap<>();

    // ━━━━━━━━━━━━━━━━ 连接管理 ━━━━━━━━━━━━━━━━

    /**
     * 注册客户端连接
     */
    public void registerClient(String clientId, WebSocketSession session) {
        // 关闭旧连接
        WebSocketSession oldSession = clientSessions.get(clientId);
        if (oldSession != null && oldSession.isOpen()) {
            try {
                oldSession.close();
            } catch (IOException e) {
                log.warn("[ClientSession] 关闭旧连接失败: {}", clientId);
            }
        }
        
        clientSessions.put(clientId, session);
        log.info("[ClientSession] 客户端连接: {} (当前在线: {})", clientId, clientSessions.size());
    }

    /**
     * 移除客户端连接
     */
    public void removeClient(String clientId) {
        clientSessions.remove(clientId);
        log.info("[ClientSession] 客户端断开: {} (当前在线: {})", clientId, clientSessions.size());
    }

    /**
     * 仅在映射仍指向当前会话时移除，避免旧连接关闭回调误删新连接。
     */
    public void removeClient(String clientId, WebSocketSession session) {
        if (clientSessions.remove(clientId, session)) {
            log.info("[ClientSession] 客户端断开: {} (当前在线: {})", clientId, clientSessions.size());
        }
    }

    // ━━━━━━━━━━━━━━━━ 消息发送 ━━━━━━━━━━━━━━━━

    /**
     * 发送消息给指定用户（同时发送给 web 和 mini 端）
     */
    public void sendToUser(String userId, String message) {
        sendToClientFamily("web-" + userId, message);
        sendToClientFamily("mypc-" + userId, message);
        sendToClientFamily("mini-" + userId, message);
    }

    /**
     * 发送给同一用户同一客户端类型下的所有标签页/设备实例，同时兼容旧客户端ID。
     */
    public void sendToClientFamily(String baseClientId, String message) {
        clientSessions.forEach((clientId, session) -> {
            if (clientId.equals(baseClientId) || clientId.startsWith(baseClientId + "-")) {
                sendToClient(clientId, message);
            }
        });
    }

    /**
     * 发送消息给指定客户端
     */
    public void sendToClient(String clientId, String message) {
        WebSocketSession session = clientSessions.get(clientId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                log.error("[ClientSession] 发送消息失败: {}", clientId, e);
            }
        }
    }

    // ━━━━━━━━━━━━━━━━ 状态查询 ━━━━━━━━━━━━━━━━

    /**
     * 检查客户端是否在线
     */
    public boolean isClientOnline(String clientId) {
        WebSocketSession session = clientSessions.get(clientId);
        return session != null && session.isOpen();
    }

    /**
     * 获取所有在线客户端
     */
    public List<String> getOnlineClients() {
        return clientSessions.entrySet().stream()
                .filter(e -> e.getValue().isOpen())
                .map(e -> e.getKey())
                .collect(Collectors.toList());
    }

    /**
     * 获取在线客户端数量
     */
    public int getOnlineCount() {
        return clientSessions.size();
    }
}
