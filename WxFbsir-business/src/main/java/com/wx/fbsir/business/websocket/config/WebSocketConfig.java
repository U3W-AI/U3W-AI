package com.wx.fbsir.business.websocket.config;

import com.wx.fbsir.business.websocket.server.ClientWebSocketHandler;
import com.wx.fbsir.business.websocket.server.ClientWebSocketInterceptor;
import com.wx.fbsir.business.websocket.server.EngineWebSocketHandler;
import com.wx.fbsir.business.websocket.server.EngineWebSocketInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置类
 * 
 * 配置两种 WebSocket 端点：
 *   - /ws/engine - Engine 副节点连接
 *   - /ws/client - 前端/小程序连接
 *
 * @author wxfbsir
 * @date 2025-12-15
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    private final WebSocketProperties properties;
    private final EngineWebSocketHandler engineHandler;
    private final EngineWebSocketInterceptor engineInterceptor;
    private final ClientWebSocketHandler clientHandler;
    private final ClientWebSocketInterceptor clientInterceptor;

    public WebSocketConfig(WebSocketProperties properties,
                           EngineWebSocketHandler engineHandler,
                           EngineWebSocketInterceptor engineInterceptor,
                           ClientWebSocketHandler clientHandler,
                           ClientWebSocketInterceptor clientInterceptor) {
        this.properties = properties;
        this.engineHandler = engineHandler;
        this.engineInterceptor = engineInterceptor;
        this.clientHandler = clientHandler;
        this.clientInterceptor = clientInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // ━━━━━━━━━━ Engine 端点（副节点连接）━━━━━━━━━━
        registry.addHandler(engineHandler, properties.getEnginePath())
                .addInterceptors(engineInterceptor)
                .setAllowedOrigins("*");
        
        // ━━━━━━━━━━ Client 端点（前端/小程序连接）━━━━━━━━━━
        registry.addHandler(clientHandler, properties.getClientPath())
                .addInterceptors(clientInterceptor)
                .setAllowedOrigins("*");
        
        log.info("[WebSocket] 服务端配置完成 - Engine: {}, Client: {}", 
            properties.getEnginePath(), properties.getClientPath());
    }

    /**
     * 注册RestTemplate Bean，用于HTTP健康检查
     *
     * @return RestTemplate实例
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
