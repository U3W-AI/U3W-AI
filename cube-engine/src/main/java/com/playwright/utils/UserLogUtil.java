package com.playwright.utils;

import com.playwright.entity.LogInfo;

import java.time.LocalDateTime;

/**
 * @author 孔德权
 * description:  TODO
 * dateStart 2024/8/4 9:34
 * dateNow   2025/8/9 11:12
 */
public class UserLogUtil {
    public static void sendExceptionLog(String userId, String description, String methodName, Exception e, String url) {
        LogInfo logInfo = new LogInfo();
        logInfo.setUserId(userId);
        logInfo.setMethodName(methodName);
        logInfo.setDescription(description);
        logInfo.setExecutionResult(e.getMessage());
        logInfo.setExecutionTimeMillis(0L);
        logInfo.setExecutionTime(LocalDateTime.now());
        logInfo.setMethodParams("无");
        logInfo.setIsSuccess(0);
        RestUtils.post(url, logInfo);
        System.out.println(methodName + "方法出现异常,详情:" + description + "用户id" + userId);
    }
}
