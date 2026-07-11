package com.wx.fbsir.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Engine 副节点启动类
 * 
 * 独立部署的浏览器自动化服务，通过 WebSocket 与主节点通信
 * 功能：执行浏览器自动化任务、AI对话、媒体发布等
 * 
 * 【安全策略】禁止从环境变量读取配置参数，所有配置必须在 application.yml 中明确指定
 *
 * @author FBSir
 * @date 2025-12-15
 */
@SpringBootApplication
@EnableScheduling
public class FBSirEngineApplication {

    private static final Logger log = LoggerFactory.getLogger(FBSirEngineApplication.class);

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        
        try {
            SpringApplication app = new SpringApplication(FBSirEngineApplication.class);
            
            // 【安全策略】禁止从环境变量和系统属性读取配置
            app.setEnvironment(new NoEnvEnvironment());
            // 禁止命令行参数覆盖配置
            app.setAddCommandLineProperties(false);
            
            app.run(args);
            long endTime = System.currentTimeMillis();
            log.info("[Engine] 服务启动成功 - 耗时: {}ms", endTime - startTime);
        } catch (Exception e) {
            log.error("[Engine] 服务启动失败 - 错误: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    /**
     * 自定义环境类 - 禁止从环境变量和系统属性读取配置
     */
    static class NoEnvEnvironment extends StandardEnvironment {
        @Override
        protected void customizePropertySources(org.springframework.core.env.MutablePropertySources propertySources) {
            super.customizePropertySources(propertySources);
            // 移除系统环境变量属性源
            propertySources.remove(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
            // 移除系统属性属性源（如 -Dserver.port=8080）
            propertySources.remove(SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        }
    }
}
