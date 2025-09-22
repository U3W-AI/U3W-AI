package com.cube.common.utils;

import com.cube.common.entity.UserLogInfo;
import com.cube.openAI.utils.SpringContextUtils;
import com.cube.wechat.selfapp.app.controller.AIGCController;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 用户日志工具类，提供详细的日志记录功?
 * @author muyou
 * description: 增强版日志记录工具，支持智能体错误信息记?
 * dateStart 2024/8/4 9:34
 * dateNow   2025/8/9 11:12
 */
public class UserLogUtil {
    
    /**
     * 发送日志的核心方法
     */
    private static void sendLog(String userId, String description, String methodName, Exception e, Integer isSuccess, Long startTime, String result) {
        try {
            UserLogInfo logInfo = new UserLogInfo();
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
            AIGCController aigcController = SpringContextUtils.getBean(AIGCController.class);
            aigcController.saveLogInfo(logInfo);
        } catch (Exception ex) {
            // 避免日志记录本身出现异常影响主流程
            System.err.println("UserLogUtil记录日志失败: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
    
    /**
     * 记录异常日志（不包含开始时间）
     */
    public static void sendExceptionLog(String userId, String description, String methodName, Exception e) {
        sendLog(userId, description, methodName, e, 0, System.currentTimeMillis(), null);
    }
    
    /**
     * 记录异常日志（包含开始时间）
     */
    public static void sendExceptionLog(String userId, String description, String methodName, Exception e, Long startTime) {
        sendLog(userId, description, methodName, e, 0, startTime, null);
    }

    /**
     * 记录正常执行日志
     */
    public static void sendNormalLog(String userId, String description, String methodName, Long startTime, String result) {
        sendLog(userId, description, methodName, null, 1, startTime, result);
    }
}
