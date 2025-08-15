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
    private static void sendLog(String userId, String description, String methodName, Exception e,Integer isSuccess, Long startTime,String result, String url) {
        LogInfo logInfo = new LogInfo();
        logInfo.setUserId(userId);
        logInfo.setMethodName(methodName);
        logInfo.setDescription(description);
        if(e != null) {
            logInfo.setExecutionResult(e.getMessage());
        } else {
            logInfo.setExecutionResult(result);
        }
        logInfo.setExecutionTimeMillis(System.currentTimeMillis() - startTime);
        logInfo.setExecutionTime(LocalDateTime.now());
        logInfo.setMethodParams("无");
        logInfo.setIsSuccess(isSuccess);
        RestUtils.post(url, logInfo);
    }
    public static void sendExceptionLog(String userId, String description, String methodName, Exception e, String url) {
        sendLog(userId, description, methodName, e, 0, System.currentTimeMillis(), null, url);
    }
    public static void sendExceptionLog(String userId, String description, String methodName, Exception e, Long startTime, String url) {
        sendLog(userId, description, methodName, e, 0, startTime, null, url);
    }

    public static void sendNormalLog(String userId, String description, String methodName, Long startTime, String result, String url) {
        sendLog(userId, description, methodName, null, 1, startTime, result, url);
    }
}
