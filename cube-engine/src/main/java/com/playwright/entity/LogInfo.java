package com.playwright.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * dateStart 2024/8/4 9:34
 * dateNow   2025/8/8 13:53
 */
@Data
@Schema(name = "LogInfo", description = "日志信息")
public class LogInfo {
    @Schema(description = "日志 ID")
    private String id;

    @Schema(description = "用户 ID")
    private String userId;

    @Schema(description = "方法名称")
    private String methodName;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "方法参数")
    private String methodParams;

    @Schema(description = "执行时间")
    @JsonIgnore
    private LocalDateTime executionTime;

    @Schema(description = "执行结果")
    private String executionResult;

    @Schema(description = "执行时间（毫秒）")
    private Long executionTimeMillis;

    @Schema(description = "是否成功(1:成功，0:失败)")
    private Integer isSuccess;
}
