package com.wx.fbsir.business.websocket.server;

import com.wx.fbsir.business.websocket.service.IpAccessControlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手拦截器
 * 
 * 在 WebSocket 握手前后进行拦截处理
 * 用于：
 * - 连接限流（防DDoS）
 * - IP黑名单快速检查
 * - 连接鉴权（可扩展）
 * - 请求日志记录
 * - 请求参数提取
 *
 * @author FBSir
 * @date 2025-12-15
 */
@Component
public class EngineWebSocketInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(EngineWebSocketInterceptor.class);

    @Autowired(required = false)
    private IpAccessControlService ipAccessControl;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, 
                                    ServerHttpResponse response,
                                    WebSocketHandler wsHandler, 
                                    Map<String, Object> attributes) throws Exception {
        
        String remoteAddress = getRemoteAddress(request);
        String uri = request.getURI().toString();
        
        // 1. 限流检查（最早拦截，防止DDoS）
        if (ipAccessControl != null && !ipAccessControl.checkRateLimit(remoteAddress)) {
            log.warn("[拦截] 限流拒绝 - IP: {}", remoteAddress);
            response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            // 添加自定义头，让副节点能识别拒绝原因
            response.getHeaders().add("X-WS-Reject-Reason", "RATE_LIMIT");
            response.getHeaders().add("X-WS-Reject-Code", "4291");
            return false;
        }
        
        // 2. IP黑名单快速检查（在握手阶段就拦截）
        if (ipAccessControl != null) {
            var blacklist = ipAccessControl.checkBlacklistWithCache(remoteAddress);
            if (blacklist != null && blacklist.getBlockType() != null && blacklist.getBlockType() >= 1) {
                log.warn("[拦截] 黑名单拒绝 - IP: {}", remoteAddress);
                ipAccessControl.updateHitCount(blacklist.getId());
                response.setStatusCode(HttpStatus.FORBIDDEN);
                // 添加自定义头，让副节点能识别拒绝原因
                response.getHeaders().add("X-WS-Reject-Reason", "BLACKLIST");
                response.getHeaders().add("X-WS-Reject-Code", "4031");
                return false;
            }
        }
        
        // 3. 提取请求参数
        if (request instanceof ServletServerHttpRequest) {
            ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
            
            // 提取 engineId 参数（可选，用于预注册）
            String engineId = servletRequest.getServletRequest().getParameter("engineId");
            if (engineId != null && !engineId.isEmpty()) {
                attributes.put("engineId", engineId);
            }
            
            // 提取 token 参数（用于鉴权，可扩展）
            String token = servletRequest.getServletRequest().getParameter("token");
            if (token != null && !token.isEmpty()) {
                attributes.put("token", token);
                // TODO: 实现 token 验证逻辑
            }
        }
        
        // 记录来源 IP
        attributes.put("remoteAddress", remoteAddress);
        attributes.put("connectTime", System.currentTimeMillis());
        
        // 返回 true 允许握手，false 拒绝
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, 
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler, 
                               Exception exception) {
        
        if (exception != null) {
            log.error("[拦截] 握手失败: {}", exception.getMessage());
        }
    }

    /**
     * 获取客户端真实 IP
     */
    private String getRemoteAddress(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest) {
            ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
            
            // 尝试从代理头获取真实 IP
            String xForwardedFor = servletRequest.getServletRequest().getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                // X-Forwarded-For 可能包含多个 IP，取第一个
                return xForwardedFor.split(",")[0].trim();
            }
            
            String xRealIp = servletRequest.getServletRequest().getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isEmpty()) {
                return xRealIp;
            }
            
            return servletRequest.getServletRequest().getRemoteAddr();
        }
        
        return request.getRemoteAddress() != null ? 
            request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
    }
}
