package com.playwright.aop;

import com.playwright.entity.LogInfo;
import com.playwright.entity.UserInfoRequest;
import com.playwright.entity.mcp.McpResult;
import com.playwright.utils.common.LogMsgUtil;
import com.playwright.utils.common.RestUtils;
import com.playwright.utils.common.UserLogUtil;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

/**
 * dateStart 2024/8/4 9:34
 * dateNow   2025/8/8 10:11
 */
@Component
@Aspect
@Slf4j
public class LogAspect {
    @Value("${cube.url}")
    private String url;
    @Autowired
    private LogMsgUtil logMsgUtil;

    @Pointcut("execution(* com.playwright.controller..*(..))")
    public void logPointCut() {
    }

    @Around("logPointCut()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = 0;
        LogInfo logInfo = new LogInfo();
        logInfo.setUserId("");
        String description = "无";
        try {
            log.info("进入方法：{}", joinPoint.getSignature().getName());
            start = System.currentTimeMillis();
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
//        方法名
            Method method = signature.getMethod();
            String methodName = method.getName();
            logInfo.setMethodName(methodName);
//        获取Operation注解上的summary
            description = "";
            if (method.isAnnotationPresent(Operation.class)) {
                Operation operation = method.getAnnotation(Operation.class);
                description = operation.summary();
                logInfo.setDescription(description);
            }
//        参数
            Object[] args = joinPoint.getArgs();
            if (args.length > 0) {
                Object arg = args[0];
                if (arg instanceof UserInfoRequest userInfoRequest) {
                    logInfo.setUserId(userInfoRequest.getUserId());
                } else if(arg instanceof Map map) {
                    logInfo.setUserId(map.get("userId").toString());
                }
                else {
                    String str = (String) arg;
                    logInfo.setUserId(str);
                }
            }
            logInfo.setMethodParams(Arrays.toString(args));
        } catch (Exception e) {
            UserLogUtil.sendExceptionLog("无", "aop异常", "logAround", e, url + "/saveLogInfo");
        }
        Object result = null;
        // 最多重试1次（i=0首次执行，i=1重试1次）
        for(int i = 0; i <= 1; i++) {
            try {
                result = joinPoint.proceed();
//            执行成功
                if(result == null) {
                    return null;
                }
                McpResult mcpResult = null;
                String resultStr = "";
                if(result instanceof McpResult) {
                    mcpResult = (McpResult) result;
                } else {
                    resultStr = result.toString();
                }
                if(resultStr.contains("false") || mcpResult != null && mcpResult.getCode() == 204) {
//            如果是登录方法，跳过检测
                    if (logInfo.getMethodName().contains("check")) {
                        log.info(logInfo.getMethodName() + "为登录方法,不再检测");
                        break;
                    }

                    if(i < 1) {
                        // 记录详细的错误原因
                        String errorDetail = mcpResult != null ? mcpResult.getResult() : resultStr;
                        log.warn("{}执行失败，准备重试 [第{}次失败，错误详情: {}]", logInfo.getMethodName(), i + 1, errorDetail);
                        
                        // 发送详细的错误日志
                        UserLogUtil.sendAIBusinessLog(
                            logInfo.getUserId(), 
                            extractAIName(description), 
                            logInfo.getMethodName(), 
                            "执行失败，准备重试 - 错误详情: " + errorDetail, 
                            System.currentTimeMillis(), 
                            url + "/saveLogInfo"
                        );
                        
                        sendTaskLog(description, logInfo.getUserId(), ",尝试重试");
                        continue;
                    }
                }
                
                // 如果是登录检查方法，不记录成功日志
                if (logInfo.getMethodName().contains("check") && (logInfo.getMethodName().contains("Login") || logInfo.getMethodName().contains("login"))) {
                    log.info(logInfo.getMethodName() + "为登录检查方法，不记录成功日志");
                    break;
                }
                
                if(mcpResult != null) {
                    logInfo.setExecutionResult(mcpResult.getResult());
                } else {
                    logInfo.setExecutionResult(resultStr);
                }
                logInfo.setIsSuccess(1);
                logInfo.setExecutionTimeMillis(System.currentTimeMillis() - start);
                RestUtils.post(url + "/saveLogInfo", logInfo);
                break;
            } catch (Throwable e) {
                if(i < 1) {
//              如果是登录方法，跳过检测
                    if (logInfo.getMethodName().contains("check")) {
                        log.info(logInfo.getMethodName() + "为登录方法,不再检测");
                        // 根据方法返回类型返回相应的结果
                        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
                        Class<?> returnType = signature.getReturnType();
                        
                        if (returnType == String.class) {
                            return "false";
                        } else if (McpResult.class.isAssignableFrom(returnType)) {
                            return McpResult.fail("用户id:" + logInfo.getUserId() + description + "未登录", "");
                        } else {
                            return null;
                        }
                    }
                    
                    // 记录详细的异常信息
                    String exceptionDetail = e.getClass().getSimpleName() + " | " + e.getMessage();
                    log.warn("{}执行异常，准备重试 [第{}次失败，异常类型: {}, 异常信息: {}]", 
                        logInfo.getMethodName(), i + 1, e.getClass().getSimpleName(), e.getMessage());
                    
                    // 发送详细的异常日志
                    Exception exception = (e instanceof Exception) ? (Exception) e : new RuntimeException(e);
                    UserLogUtil.sendAIExceptionLog(
                        logInfo.getUserId(), 
                        extractAIName(description), 
                        logInfo.getMethodName(), 
                        exception,
                        System.currentTimeMillis(),
                        "执行失败，准备重试（第" + (i + 1) + "次失败）",
                        url + "/saveLogInfo"
                    );
                    
                    sendTaskLog(description, logInfo.getUserId(), ",尝试重试");
                    continue;
                }
//            执行失败（重试后仍然失败）
                logInfo.setExecutionResult(e.getMessage());
                logInfo.setIsSuccess(0);
                logInfo.setExecutionTimeMillis(System.currentTimeMillis() - start);
                log.error("{}方法执行失败（已重试），详情: {}, 用户ID: {}, 异常: {}", 
                    logInfo.getMethodName(), logInfo.getDescription(), logInfo.getUserId(), e.getMessage());
                
                // 记录最终失败的日志
                RestUtils.post(url + "/saveLogInfo", logInfo);
                
                // 发送最终失败的异常日志
                Exception finalException = (e instanceof Exception) ? (Exception) e : new RuntimeException(e);
                UserLogUtil.sendAIExceptionLog(
                    logInfo.getUserId(), 
                    extractAIName(description), 
                    logInfo.getMethodName(), 
                    finalException,
                    System.currentTimeMillis(),
                    "执行最终失败（已重试1次仍失败）",
                    url + "/saveLogInfo"
                );
                
                //             传递不同ai的错误信息
                sendTaskLog(description, logInfo.getUserId(), "");
                
                // 根据方法返回类型返回相应的结果
                MethodSignature signature = (MethodSignature) joinPoint.getSignature();
                Class<?> returnType = signature.getReturnType();
                log.info("最终失败处理：方法名={}，返回类型={}", logInfo.getMethodName(), returnType.getName());
                
                if (returnType == String.class) {
                    // 如果方法返回String类型，返回"false"
                    log.info("方法返回String类型，返回false");
                    return "false"; 
                } else if (McpResult.class.isAssignableFrom(returnType)) {
                    // 如果方法返回McpResult类型，返回McpResult.fail()
                    log.info("方法返回McpResult类型，返回McpResult.fail()");
                    if (description.contains("检查")) {
                        return McpResult.fail("用户id:" + logInfo.getUserId()  + description + "未登录", "");
                    } else if (description.contains("投递")) {
                        return McpResult.fail("用户id:" + logInfo.getUserId() + description + "失败", "");
                    } else {
                        return McpResult.fail("用户id:" + logInfo.getUserId() + description + "失败", "");
                    } 
                } else {
                    // 对于其他返回类型，返回null
                    log.info("方法返回其他类型，返回null");
                    return null;
                }
            }
        }
        // 简化AI回复日志输出，避免终端被大量文本刷屏
        if(result == null) {
            return null;
        }
        String resultStr = result.toString();
        if (resultStr.length() > 200) {
            // 如果内容过长，只显示前100字符和后100字符
            String prefix = resultStr.substring(0, 100);
            String suffix = resultStr.substring(resultStr.length() - 100);
            log.info("返回结果：{}...{} [总长度:{}字符]", prefix, suffix, resultStr.length());
        } else {
            log.info("返回结果：{}", result);
        }
        
        // 确保返回结果与方法返回类型一致
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Class<?> returnType = signature.getReturnType();
        
        if (returnType == String.class) {
            if (result instanceof McpResult) {
                McpResult mcpResult = (McpResult) result;
                return mcpResult.getResult() != null ? mcpResult.getResult() : "false";
            } else if (result != null) {
                return result.toString();
            } else {
                return "false";
            }
        } else if (McpResult.class.isAssignableFrom(returnType)) {
            if (result instanceof String) {
                return McpResult.fail(result, "");
            } else if (result != null) {
                return result;
            } else {
                return McpResult.fail("执行失败", "");
            }
        } else {
            return result;
        }
    }

    private void sendTaskLog(String description, String userId, String isTryAgain) {
        //             传递不同ai的错误信息
        if (description.contains("DeepSeek")) {
            logMsgUtil.sendTaskLog(description + "执行失败" + isTryAgain, userId, "DeepSeek");
        }
        if (description.contains("豆包")) {
            logMsgUtil.sendTaskLog(description + "执行失败" + isTryAgain, userId, "豆包");
        }
        if (description.contains("MiniMax")) {
            logMsgUtil.sendTaskLog(description + "执行失败" + isTryAgain, userId, "MiniMax");
        }
        if (description.contains("秘塔")) {
            logMsgUtil.sendTaskLog(description + "执行失败" + isTryAgain, userId, "秘塔");
        }
        if (description.contains("KiMi")) {
            logMsgUtil.sendTaskLog(description + "执行失败" + isTryAgain, userId, "KiMi");
        }
        if (description.contains("通义千问")) {
            logMsgUtil.sendTaskLog(description + "执行失败" + isTryAgain, userId, "通义千问");
        }
        if (description.contains("百度AI")) {
            logMsgUtil.sendTaskLog(description + "执行失败" + isTryAgain, userId, "百度AI");
        }
        if (description.contains("腾讯元宝T1")) {
            logMsgUtil.sendTaskLog(description + "执行失败" + isTryAgain, userId, "腾讯元宝T1");
        }
        if (description.contains("腾讯元宝DS")) {
            logMsgUtil.sendTaskLog(description + "执行失败" + isTryAgain, userId, "腾讯元宝DS");
        }
        if (description.contains("知乎直答")) {
            logMsgUtil.sendTaskLog(description + "执行失败" + isTryAgain, userId, "知乎直答");
        }
    }

    /**
     * 从描述中提取AI名称
     * @param description 方法描述
     * @return AI名称，如果没有匹配则返回"未知"
     */
    private String extractAIName(String description) {
        if (description == null) {
            return "未知";
        }
        if (description.contains("DeepSeek")) return "DeepSeek";
        if (description.contains("豆包")) return "豆包";
        if (description.contains("MiniMax")) return "MiniMax";
        if (description.contains("秘塔")) return "秘塔";
        if (description.contains("KiMi")) return "KiMi";
        if (description.contains("通义千问")) return "通义千问";
        if (description.contains("百度AI")) return "百度AI";
        if (description.contains("腾讯元宝T1")) return "腾讯元宝T1";
        if (description.contains("腾讯元宝DS")) return "腾讯元宝DS";
        if (description.contains("知乎直答")) return "知乎直答";
        return "未知";
    }

}
