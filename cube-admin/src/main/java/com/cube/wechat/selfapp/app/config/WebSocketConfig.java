package com.cube.wechat.selfapp.app.config;

import com.cube.wechat.selfapp.app.mapper.UserInfoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    @Autowired
    private UserInfoMapper userInfoMapper;

    @Autowired
    private HostIdInterceptor hostIdInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new MyWebSocketHandler(userInfoMapper), "/websocket")
                .addInterceptors(new MyHandshakeInterceptor()) // 添加自定义握手拦截器
                .addInterceptors(hostIdInterceptor)
                .setAllowedOrigins("*"); // 允许跨域
    }
}
