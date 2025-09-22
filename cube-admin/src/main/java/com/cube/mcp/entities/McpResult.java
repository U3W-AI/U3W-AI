package com.cube.mcp.entities;

import lombok.Data;

/**
 * dateStart 2024/8/4 9:34
 * dateNow   2025/8/22 14:07
 */
@Data
public class McpResult {
    //    状态
    private Integer code;
//    执行结果
    private String result;
//    分享了链接
    private String shareUrl;
    public static McpResult success(String result, String shareUrl) {
        McpResult mcpResult = new McpResult();
        mcpResult.setResult(result);
        mcpResult.setShareUrl(shareUrl);
        mcpResult.setCode(200);
        return mcpResult;
    }
    public static McpResult fail(String result, String shareUrl) {
        McpResult mcpResult = new McpResult();
        mcpResult.setResult(result);
        mcpResult.setShareUrl(shareUrl);
        mcpResult.setCode(204);
        return mcpResult;
    }
}
