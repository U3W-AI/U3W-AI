package com.cube.mcp.config;

import com.cube.mcp.CubeMcp;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author muyou
 * description:  TODO
 * dateStart 2024/8/4 9:34
 * dateNow   2025/8/20 10:16
 */
@Configuration
@RequiredArgsConstructor
public class McpConfig {
    private final CubeMcp cubeMcp;
    @Bean
    public ToolCallbackProvider toolCallbackProvider() {
        return MethodToolCallbackProvider.builder()
                .toolObjects(cubeMcp)
                .build();
    }
}
