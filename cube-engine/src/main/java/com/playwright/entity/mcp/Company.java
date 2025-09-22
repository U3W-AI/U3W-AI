package com.playwright.entity.mcp;

import lombok.Data;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * dateStart 2024/8/4 9:34
 * dateNow   2025/8/20 15:27
 */
@Data
public class Company {
    @ToolParam(description = "排名")
    private Integer rank;
    @ToolParam(description = "公司名称")
    private String name;
    @ToolParam(description = "股票代码")
    private String symbol;
    @ToolParam(description = "市值")
    private Long marketCap;
    @ToolParam(description = "价格")
    private Double price;
    @ToolParam(description = "国家")
    private String country;
}
