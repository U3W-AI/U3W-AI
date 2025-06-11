package com.cube.wechat.selfapp.app.config;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2025年05月19日 15:31
 */
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Configuration;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;

@Configuration
public class WebSocketBufferConfig implements ServletContextInitializer {

    @Override
    public void onStartup(ServletContext servletContext) throws ServletException {
        // 设置最大文本消息大小为 1MB（可调）
        servletContext.setInitParameter("org.apache.tomcat.websocket.textBufferSize", "1048576");
        servletContext.setInitParameter("org.apache.tomcat.websocket.binaryBufferSize", "1048576");
    }
}
