package com.cube.wechat.selfapp.app.config;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2025年01月06日 11:41
 */
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

public class MyHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        // 从 URL 中提取客户端 ID
        String clientId = request.getURI().getQuery(); // 假设格式为 ws://localhost:8080/websocket?clientId=123
        if (clientId != null && clientId.startsWith("clientId=")) {
            clientId = clientId.substring("clientId=".length());
            attributes.put("clientId", clientId); // 将客户端 ID 存储到 WebSocketSession 的属性中
        }
        return true; // 返回 true 表示握手成功
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 握手完成后的逻辑（如果需要）
    }
}
