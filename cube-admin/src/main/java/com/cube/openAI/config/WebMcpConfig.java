package com.cube.openAI.config;

import com.cube.openAI.interceptor.OpenAiApiKeyInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author muyou
 * dateStart 2024/8/4 9:34
 * dateNow   2025/9/9 13:58
 */
@Configuration
public class WebMcpConfig implements WebMvcConfigurer {
    @Autowired
    private OpenAiApiKeyInterceptor openAiApiKeyInterceptor;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(openAiApiKeyInterceptor)
                .addPathPatterns("/v1/**");
    }
}
