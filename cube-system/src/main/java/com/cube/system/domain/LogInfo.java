package com.cube.system.domain;

import java.time.LocalDateTime;
import java.util.Date;

import com.cube.common.core.domain.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.cube.common.annotation.Excel;

/**
 * 日志信息（记录方法执行日志）对象 wc_log_info
 *
 * @author cube
 * @date 2025-08-08
 */
public class LogInfo extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 日志ID（自增） */
    private Long id;

    /** 用户 ID */
    @Excel(name = "用户 ID")
    private String userId;

    /** 方法名称 */
    @Excel(name = "方法名称")
    private String methodName;

    /** 描述（对应接口注解的summary等信息） */
    @Excel(name = "描述", readConverterExp = "对=应接口注解的summary等信息")
    private String description;

    /** 方法参数（存储JSON格式或字符串化参数） */
    @Excel(name = "方法参数", readConverterExp = "存=储JSON格式或字符串化参数")
    private String methodParams;

    /** 执行时间 精确到时分秒*/
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "执行时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime executionTime;

    /** 执行结果 */
    @Excel(name = "执行结果")
    private String executionResult;

    /** 执行时间（毫秒） */
    @Excel(name = "执行时间", readConverterExp = "毫=秒")
    private Long executionTimeMillis;

    /** 是否成功(1:成功，0:失败) */
    @Excel(name = "是否成功(1:成功，0:失败)")
    private Long isSuccess;

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getId()
    {
        return id;
    }
    public void setUserId(String userId)
    {
        this.userId = userId;
    }

    public String getUserId()
    {
        return userId;
    }
    public void setMethodName(String methodName)
    {
        this.methodName = methodName;
    }

    public String getMethodName()
    {
        return methodName;
    }
    public void setDescription(String description)
    {
        this.description = description;
    }

    public String getDescription()
    {
        return description;
    }
    public void setMethodParams(String methodParams)
    {
        this.methodParams = methodParams;
    }

    public String getMethodParams()
    {
        return methodParams;
    }
    public void setExecutionTime(LocalDateTime executionTime)
    {
        this.executionTime = executionTime;
    }

    public LocalDateTime getExecutionTime()
    {
        return executionTime;
    }
    public void setExecutionResult(String executionResult)
    {
        this.executionResult = executionResult;
    }

    public String getExecutionResult()
    {
        return executionResult;
    }
    public void setExecutionTimeMillis(Long executionTimeMillis)
    {
        this.executionTimeMillis = executionTimeMillis;
    }

    public Long getExecutionTimeMillis()
    {
        return executionTimeMillis;
    }
    public void setIsSuccess(Long isSuccess)
    {
        this.isSuccess = isSuccess;
    }

    public Long getIsSuccess()
    {
        return isSuccess;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("userId", getUserId())
            .append("methodName", getMethodName())
            .append("description", getDescription())
            .append("methodParams", getMethodParams())
            .append("executionTime", getExecutionTime())
            .append("executionResult", getExecutionResult())
            .append("executionTimeMillis", getExecutionTimeMillis())
            .append("isSuccess", getIsSuccess())
            .toString();
    }
}
