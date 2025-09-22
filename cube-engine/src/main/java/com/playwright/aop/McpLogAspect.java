package com.playwright.aop;

import com.playwright.entity.LogInfo;
import com.playwright.entity.UserInfoRequest;
import com.playwright.entity.mcp.McpResult;
import com.playwright.utils.RestUtils;
import com.playwright.utils.UserInfoUtil;
import com.playwright.utils.UserLogUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * MCP日志切面处理类，负责记录MCP相关方法的执行日志和异常信息
 * dateStart 2024/8/4 9:34
 * dateNow   2025/8/23 10:23
 */
@Component
@Aspect
@Slf4j
@RequiredArgsConstructor
public class McpLogAspect {
    @Value("${cube.url}")
    private String url;
    private final UserInfoUtil userInfoUtil;
    
    @Pointcut("execution(* com.playwright.mcp.*.*(..))")
    public void logPointCut() {
    }

    @Around("logPointCut()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        LogInfo logInfo = new LogInfo();
        logInfo.setUserId("");
        String description = "无";
        String methodName = "";
        UserInfoRequest userInfoRequest = null;
        
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            // 方法名
            Method method = signature.getMethod();
            methodName = method.getName();
            log.info("进入MCP方法：{}", methodName);
            logInfo.setMethodName(methodName);
            
            // 获取Tool注解上的name
            description = "";
            if (method.isAnnotationPresent(Tool.class)) {
                Tool tool = method.getAnnotation(Tool.class);
                description = tool.name();
                logInfo.setDescription(description);
            }
            
            // 参数处理，增加空指针检查
            Object[] args = joinPoint.getArgs();
            if (args.length > 0) {
                Object arg = args[0];
                if (arg instanceof UserInfoRequest) {
                    userInfoRequest = (UserInfoRequest) arg;
                    
                    // 安全获取unionId
                    String unionId = userInfoRequest.getUnionId();
                    if (StringUtils.hasText(unionId)) {
                        try {
                            String userId = userInfoUtil.getUserIdByUnionId(unionId);
                            if (StringUtils.hasText(userId)) {
                                logInfo.setUserId(userId);
                            } else {
                                log.warn("通过unionId未找到对应的userId，unionId：{}", unionId);
                                logInfo.setUserId("未找到用户");
                            }
                        } catch (Exception e) {
                            log.error("获取用户ID异常，unionId：{}", unionId, e);
                            logInfo.setUserId("用户ID获取异常");
                            // 记录获取用户ID的异常
                            UserLogUtil.sendExceptionLog("未知", "获取用户ID异常-" + unionId, methodName, e, url + "/saveLogInfo");
                        }
                    } else {
                        log.warn("UserInfoRequest中的unionId为空，方法：{}", methodName);
                        logInfo.setUserId("unionId为空");
                    }
                } else {
                    log.warn("MCP方法{}的第一个参数不是UserInfoRequest类型，实际类型：{}", methodName, arg.getClass().getName());
                    logInfo.setUserId("参数类型异常");
                }
            }
            logInfo.setMethodParams(Arrays.toString(args));
            
        } catch (Exception e) {
            log.error("MCP AOP参数解析异常，方法：{}", methodName, e);
            UserLogUtil.sendExceptionLog(logInfo.getUserId(), "MCP AOP参数解析异常-" + methodName, "logAround", e, url + "/saveLogInfo");
        }
        
        try {
            // 执行方法
            Object result = joinPoint.proceed();
            long end = System.currentTimeMillis();
            
            McpResult mcpResult = null;
            if(result instanceof McpResult) {
                mcpResult = (McpResult) result;
            }
            
            if(mcpResult == null) {
                log.error("MCP方法{}返回结果不是McpResult类型或为null", methodName);
                
                // 记录异常到数据库
                logInfo.setExecutionTimeMillis(end - start);
                logInfo.setExecutionResult("方法返回结果异常：结果为null或类型不正确");
                logInfo.setIsSuccess(0);
                logInfo.setExecutionTime(LocalDateTime.now());
                
                try {
                    RestUtils.post(url + "/saveLogInfo", logInfo);
                } catch (Exception saveException) {
                    log.error("保存MCP异常日志失败，方法：{}", methodName, saveException);
                }
                
                // 使用UserLogUtil记录详细异常
                Exception nullResultException = new Exception("MCP方法返回结果为null或类型错误");
                UserLogUtil.sendExceptionLog(logInfo.getUserId(), "MCP方法返回异常-" + description, methodName, nullResultException, start, url + "/saveLogInfo");
                
                return McpResult.fail("MCP方法执行异常：返回结果为null", "");
            }
            
            // 记录成功日志
            logInfo.setExecutionTimeMillis(end - start);
            String resultContent = "";
            if (StringUtils.hasText(mcpResult.getResult())) {
                resultContent = mcpResult.getResult();
            }
            if (StringUtils.hasText(mcpResult.getShareUrl())) {
                resultContent += " | ShareUrl:" + mcpResult.getShareUrl();
            }
            logInfo.setExecutionResult(resultContent);
            logInfo.setIsSuccess(mcpResult.getCode() == 200 ? 1 : 0);
            logInfo.setExecutionTime(LocalDateTime.now());
            
            try {
                RestUtils.post(url + "/saveLogInfo", logInfo);
            } catch (Exception e) {
                log.error("保存MCP成功日志失败，方法：{}", methodName, e);
            }
            
            // 如果执行失败，使用UserLogUtil记录详细信息
            if (mcpResult.getCode() != 200) {
                Exception businessException = new Exception("MCP业务执行失败，错误码：" + mcpResult.getCode() + "，错误信息：" + mcpResult.getResult());
                UserLogUtil.sendExceptionLog(logInfo.getUserId(), "MCP业务执行失败-" + description, methodName, businessException, start, url + "/saveLogInfo");
                log.warn("MCP方法{}执行失败，用户：{}, 错误码：{}, 错误信息：{}", methodName, logInfo.getUserId(), mcpResult.getCode(), mcpResult.getResult());
            } else {
                // 记录成功日志
                UserLogUtil.sendNormalLog(logInfo.getUserId(), "MCP方法执行成功-" + description, methodName, start, resultContent, url + "/saveLogInfo");
                log.info("MCP方法{}执行成功，用户：{}", methodName, logInfo.getUserId());
            }
            
            return result;
            
        } catch (Throwable e) {
            long end = System.currentTimeMillis();
            
            String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.error("MCP方法{}执行异常，用户：{}, 错误：{}", methodName, logInfo.getUserId(), errorMessage, e);
            
            // 记录详细异常信息到数据库
            logInfo.setExecutionTimeMillis(end - start);
            logInfo.setExecutionResult(errorMessage + " | 异常类型：" + e.getClass().getSimpleName() + " | 堆栈：" + getStackTrace(e));
            logInfo.setIsSuccess(0);
            logInfo.setExecutionTime(LocalDateTime.now());
            
            try {
                RestUtils.post(url + "/saveLogInfo", logInfo);
            } catch (Exception saveException) {
                log.error("保存MCP异常日志失败，方法：{}", methodName, saveException);
            }
            
            // 使用UserLogUtil记录详细异常信息
            Exception exception = (e instanceof Exception) ? (Exception) e : new Exception(e);
            UserLogUtil.sendExceptionLog(logInfo.getUserId(), "MCP方法执行异常-" + description, methodName, exception, start, url + "/saveLogInfo");
            
            // 针对特定异常类型提供更详细的错误信息
            String detailedErrorMsg;
            if (e instanceof java.util.concurrent.TimeoutException || errorMessage.contains("Timeout")) {
                detailedErrorMsg = String.format("MCP方法超时异常 | 方法：%s | 用户：%s | 超时时间：%dms | 详情：%s", 
                    methodName, logInfo.getUserId(), (end - start), errorMessage);
            } else if (e instanceof java.lang.NullPointerException) {
                detailedErrorMsg = String.format("MCP方法空指针异常 | 方法：%s | 用户：%s | 详情：%s", 
                    methodName, logInfo.getUserId(), errorMessage);
            } else if (e instanceof java.lang.IllegalArgumentException) {
                detailedErrorMsg = String.format("MCP方法参数异常 | 方法：%s | 用户：%s | 详情：%s", 
                    methodName, logInfo.getUserId(), errorMessage);
            } else {
                detailedErrorMsg = String.format("MCP方法执行异常 | 方法：%s | 用户：%s | 异常类型：%s | 详情：%s", 
                    methodName, logInfo.getUserId(), e.getClass().getSimpleName(), errorMessage);
            }
            
            return McpResult.fail(detailedErrorMsg, "");
        }
    }
    
    /**
     * 获取异常堆栈信息的前300个字符
     */
    private String getStackTrace(Throwable e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        e.printStackTrace(pw);
        String stackTrace = sw.toString();
        // 限制堆栈信息长度，避免数据库字段溢出
        return stackTrace.length() > 300 ? stackTrace.substring(0, 300) : stackTrace;
    }
}
