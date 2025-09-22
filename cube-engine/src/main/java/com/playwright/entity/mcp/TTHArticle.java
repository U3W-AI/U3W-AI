package com.playwright.entity.mcp;

import lombok.Data;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * dateStart 2024/8/4 9:34
 * dateNow   2025/8/20 13:14
 */
@Data
public class TTHArticle {
    @ToolParam(description = "标题")
    private String title;
    @ToolParam(description = "内容")
    private String content;
    @ToolParam(description = "用户id")
    private String userId;
}
