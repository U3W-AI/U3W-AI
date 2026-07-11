package com.wx.fbsir.business.websocket.server;

import com.wx.fbsir.common.constant.CacheConstants;
import com.wx.fbsir.common.constant.Constants;
import com.wx.fbsir.common.core.domain.model.LoginUser;
import com.wx.fbsir.common.core.redis.RedisCache;
import com.wx.fbsir.common.utils.StringUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Client WebSocket 握手拦截器
 * 
 * 处理前端/小程序的 WebSocket 连接握手
 * 
 * 认证方式：
 *   - Token 通过 Authorization Header 传递（和其他 API 一致）
 *   - 格式: Authorization: Bearer {token}
 * 
 * 连接参数：
 *   - clientType: 客户端类型（web/mini），必填
 *   - userId 从 Token 自动解析，无需传递
 *
 * @author FBSir
 * @date 2025-12-18
 */
@Component
public class ClientWebSocketInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ClientWebSocketInterceptor.class);
    private static final Set<String> SUPPORTED_CLIENT_TYPES = Set.of("web", "mypc", "mini");
    private static final Pattern CLIENT_INSTANCE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    @Value("${token.secret}")
    private String secret;

    @Value("${token.header:Authorization}")
    private String tokenHeader;

    private final RedisCache redisCache;

    public ClientWebSocketInterceptor(RedisCache redisCache) {
        this.redisCache = redisCache;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        
        if (request instanceof ServletServerHttpRequest) {
            ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
            
            // 获取客户端类型（web/mini）
            String clientType = servletRequest.getServletRequest().getParameter("clientType");
            if (clientType == null || clientType.isEmpty()) {
                // 兼容旧格式：从 clientId 解析类型
                String clientId = servletRequest.getServletRequest().getParameter("clientId");
                if (clientId != null) {
                    clientType = parseClientType(clientId);
                }
            }
            
            if (clientType == null || clientType.isEmpty()) {
                log.warn("[Client WebSocket] 握手失败: 缺少 clientType 参数");
                return false;
            }
            if (!SUPPORTED_CLIENT_TYPES.contains(clientType)) {
                log.warn("[Client WebSocket] 握手失败: 不支持的 clientType={}", clientType);
                return false;
            }
            
            // 从 Authorization Header 获取 Token（和其他 API 一致）
            String token = getTokenFromHeader(servletRequest);
            
            // 验证 Token
            LoginUser loginUser = getLoginUserFromToken(token);
            if (loginUser == null) {
                log.warn("[Client WebSocket] 握手失败: Token 无效");
                return false;
            }
            
            // 标签页实例标识允许同一用户并行打开多个受控 WebSocket 页面。
            String clientInstanceId = servletRequest.getServletRequest().getParameter("clientInstanceId");
            String clientId = buildClientId(clientType, loginUser.getUserId(), clientInstanceId);
            if (clientId == null) {
                log.warn("[Client WebSocket] 握手失败: clientInstanceId 格式非法");
                return false;
            }
            
            // 存入 session attributes
            attributes.put("clientId", clientId);
            attributes.put("clientType", clientType);
            attributes.put("userId", loginUser.getUserId());
            attributes.put("username", loginUser.getUsername());
            
            log.info("[Client WebSocket] 握手成功: clientId={}, username={}", clientId, loginUser.getUsername());
            return true;
        }
        
        return false;
    }

    static String buildClientId(String clientType, Object userId, String clientInstanceId) {
        String baseClientId = clientType + "-" + userId;
        if (StringUtils.isEmpty(clientInstanceId)) {
            return baseClientId;
        }
        if (!CLIENT_INSTANCE_PATTERN.matcher(clientInstanceId).matches()) {
            return null;
        }
        return baseClientId + "-" + clientInstanceId;
    }

    /**
     * 从 Authorization Header 或 URL 参数获取 Token
     */
    private String getTokenFromHeader(ServletServerHttpRequest request) {
        // 优先从 Header 获取
        String token = request.getServletRequest().getHeader(tokenHeader);
        
        // 兼容：支持从 URL 参数获取（方便测试）
        if (StringUtils.isEmpty(token)) {
            // 先尝试获取 token 参数
            token = request.getServletRequest().getParameter("token");
            
            // 再尝试获取 key 参数（兼容前端调试工具的命名）
            if (StringUtils.isEmpty(token)) {
                token = request.getServletRequest().getParameter("key");
            }
        }
        
        return token;
    }

    /**
     * 从 Token 获取登录用户信息
     */
    private LoginUser getLoginUserFromToken(String token) {
        if (StringUtils.isEmpty(token)) {
            log.warn("[Client WebSocket] 缺少 Token");
            return null;
        }
        
        try {
            // 去除 Bearer 前缀
            String processedToken = token;
            if (processedToken.startsWith(Constants.TOKEN_PREFIX)) {
                processedToken = processedToken.replace(Constants.TOKEN_PREFIX, "");
            }
            
            // 解析 Token
            Claims claims = Jwts.parser()
                    .setSigningKey(secret)
                    .parseClaimsJws(processedToken)
                    .getBody();
            
            // 获取 uuid
            String uuid = (String) claims.get(Constants.LOGIN_USER_KEY);
            if (StringUtils.isEmpty(uuid)) {
                log.warn("[Client WebSocket] Token 中缺少用户标识");
                return null;
            }
            
            // 从 Redis 获取用户信息
            String userKey = CacheConstants.LOGIN_TOKEN_KEY + uuid;
            LoginUser loginUser = redisCache.getCacheObject(userKey);
            
            if (loginUser == null) {
                log.warn("[Client WebSocket] 用户信息已过期或不存在: uuid={}", uuid);
                return null;
            }
            
            log.debug("[Client WebSocket] Token 验证成功: uuid={}, username={}", uuid, loginUser.getUsername());
            return loginUser;
            
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            log.error("[Client WebSocket] Token 已过期: {}", e.getMessage());
            return null;
        } catch (io.jsonwebtoken.MalformedJwtException e) {
            log.error("[Client WebSocket] Token 格式错误: {}", e.getMessage());
            return null;
        } catch (io.jsonwebtoken.SignatureException e) {
            log.error("[Client WebSocket] Token 签名错误: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("[Client WebSocket] Token 验证失败: {}", e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 从旧格式 clientId 解析客户端类型（兼容）
     */
    private String parseClientType(String clientId) {
        if (clientId.startsWith("web-") || clientId.startsWith("mypc-")) {
            return "web";
        } else if (clientId.startsWith("mini-")) {
            return "mini";
        }
        return null;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 握手后处理（可选）
    }
}
