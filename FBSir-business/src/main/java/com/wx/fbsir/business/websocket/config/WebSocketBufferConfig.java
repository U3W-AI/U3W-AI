package com.wx.fbsir.business.websocket.config;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Configuration;

/**
 * WebSocket 缓冲区配置
 * 
 * 配置 Tomcat WebSocket 的文本和二进制消息缓冲区大小
 * 支持大消息传输（如AI对话结果、长文章等）
 *
 * @author FBSir
 * @date 2025-12-16
 */
@Configuration
public class WebSocketBufferConfig implements ServletContextInitializer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketBufferConfig.class);

    /**
     * 默认缓冲区大小：5MB
     * 与 WebSocketProperties.maxMessageSize 保持一致
     */
    private static final String BUFFER_SIZE = String.valueOf(5 * 1024 * 1024);

    @Override
    public void onStartup(ServletContext servletContext) throws ServletException {
        // 设置 Tomcat WebSocket 文本消息缓冲区大小
        servletContext.setInitParameter("org.apache.tomcat.websocket.textBufferSize", BUFFER_SIZE);
        // 设置 Tomcat WebSocket 二进制消息缓冲区大小
        servletContext.setInitParameter("org.apache.tomcat.websocket.binaryBufferSize", BUFFER_SIZE);
        
        log.info("[WebSocket] Tomcat 缓冲区配置完成 - 文本/二进制缓冲区: {}MB", 
            Integer.parseInt(BUFFER_SIZE) / 1024 / 1024);
    }
}
