package com.playwright.utils;

import com.playwright.entity.LogInfo;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 用户日志工具类，提供详细的日志记录功能
 * @author 孔德权
 * description: 增强版日志记录工具，支持智能体错误信息记录
 * dateStart 2024/8/4 9:34
 * dateNow   2025/8/9 11:12
 */
public class UserLogUtil {
    
    /**
     * 发送日志的核心方法
     */
    private static void sendLog(String userId, String description, String methodName, Exception e, Integer isSuccess, Long startTime, String result, String url) {
        try {
            LogInfo logInfo = new LogInfo();
            logInfo.setUserId(StringUtils.hasText(userId) ? userId : "未知用户");
            logInfo.setMethodName(StringUtils.hasText(methodName) ? methodName : "未知方法");
            logInfo.setDescription(StringUtils.hasText(description) ? description : "无描述");
            
            if(e != null) {
                String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                // 添加更详细的错误信息
                String detailedError = String.format("错误类型：%s | 错误信息：%s | 发生时间：%s", 
                    e.getClass().getSimpleName(), errorMessage, LocalDateTime.now());
                logInfo.setExecutionResult(detailedError);
            } else {
                logInfo.setExecutionResult(StringUtils.hasText(result) ? result : "执行成功");
            }
            
            long executionTime = startTime != null ? System.currentTimeMillis() - startTime : 0;
            logInfo.setExecutionTimeMillis(executionTime);
            logInfo.setExecutionTime(LocalDateTime.now());
            logInfo.setMethodParams("通过UserLogUtil记录");
            logInfo.setIsSuccess(isSuccess);
            
            RestUtils.post(url, logInfo);
        } catch (Exception ex) {
            // 避免日志记录本身出现异常影响主流程
            System.err.println("UserLogUtil记录日志失败: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
    
    /**
     * 记录异常日志（不包含开始时间）
     */
    public static void sendExceptionLog(String userId, String description, String methodName, Exception e, String url) {
        sendLog(userId, description, methodName, e, 0, System.currentTimeMillis(), null, url);
    }
    
    /**
     * 记录异常日志（包含开始时间）
     */
    public static void sendExceptionLog(String userId, String description, String methodName, Exception e, Long startTime, String url) {
        sendLog(userId, description, methodName, e, 0, startTime, null, url);
    }

    /**
     * 记录正常执行日志
     */
    public static void sendNormalLog(String userId, String description, String methodName, Long startTime, String result, String url) {
        sendLog(userId, description, methodName, null, 1, startTime, result, url);
    }
    
    /**
     * 记录智能体特定的异常日志
     */
    public static void sendAIExceptionLog(String userId, String aiName, String methodName, Exception e, Long startTime, String additionalInfo, String url) {
        String description = String.format("智能体异常 | AI：%s | 附加信息：%s", 
            StringUtils.hasText(aiName) ? aiName : "未知AI", 
            StringUtils.hasText(additionalInfo) ? additionalInfo : "无");
        sendLog(userId, description, methodName, e, 0, startTime, null, url);
    }
    
    /**
     * 记录智能体操作超时异常
     */
    public static void sendAITimeoutLog(String userId, String aiName, String operation, Exception e, String elementInfo, String url) {
        String description = String.format("智能体操作超时 | AI：%s | 操作：%s", aiName, operation);
        sendLog(userId, description, "AI操作", e, 0, System.currentTimeMillis(), null, url);
    }
    
    /**
     * 记录智能体元素不可见异常
     */
    public static void sendElementNotVisibleLog(String userId, String aiName, String selector, String pageUrl, String url) {
        Exception elementException = new Exception(String.format(
            "元素不可见 | AI：%s | 选择器：%s | 页面URL：%s", 
            aiName, selector, pageUrl));
        String description = String.format("智能体元素异常 | AI：%s | 元素不可见", aiName);
        sendLog(userId, description, "元素操作", elementException, 0, System.currentTimeMillis(), null, url);
    }
    
    /**
     * 记录智能体登录状态异常
     */
    public static void sendLoginStatusLog(String userId, String aiName, String statusInfo, String url) {
        Exception loginException = new Exception(String.format(
            "登录状态异常 | AI：%s | 状态信息：%s", 
            aiName, statusInfo));
        String description = String.format("智能体登录异常 | AI：%s", aiName);
        sendLog(userId, description, "登录检查", loginException, 0, System.currentTimeMillis(), null, url);
    }
    
    /**
     * 记录智能体业务执行异常
     */
    public static void sendAIBusinessLog(String userId, String aiName, String businessType, String errorInfo, Long startTime, String url) {
        Exception businessException = new Exception(String.format(
            "业务执行异常 | AI：%s | 业务类型：%s | 错误信息：%s", 
            aiName, businessType, errorInfo));
        String description = String.format("智能体业务异常 | AI：%s | 业务：%s", aiName, businessType);
        sendLog(userId, description, businessType, businessException, 0, startTime, null, url);
    }
    
    /**
     * 记录智能体成功执行日志
     */
    public static void sendAISuccessLog(String userId, String aiName, String operation, String result, Long startTime, String url) {
        String description = String.format("智能体执行成功 | AI：%s | 操作：%s", aiName, operation);
        String detailedResult = String.format("成功执行 | AI：%s | 操作：%s | 结果：%s", aiName, operation, result);
        sendLog(userId, description, operation, null, 1, startTime, detailedResult, url);
    }
}
