package com.playwright.aop;

import com.playwright.entity.LogInfo;
import com.playwright.entity.UserInfoRequest;
import com.playwright.utils.LogMsgUtil;
import com.playwright.utils.RestUtils;
import com.playwright.utils.UserLogUtil;
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
 * @author muyou
 * description:  用户日志处理
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

    @Pointcut("execution(* com.playwright.controller.*.*(..))")
    public void logPointCut() {
    }

    @Around("logPointCut()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = 0;
        LogInfo logInfo = logInfo = new LogInfo();
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
        try {
            result = joinPoint.proceed();
//            执行成功
            logInfo.setExecutionResult(result.toString());
            logInfo.setIsSuccess(1);
            logInfo.setExecutionTimeMillis(System.currentTimeMillis() - start);
            RestUtils.post(url + "/saveLogInfo", logInfo);
        } catch (Throwable e) {
//            执行失败
            logInfo.setExecutionResult(e.getMessage());
            logInfo.setIsSuccess(0);
            logInfo.setExecutionTimeMillis(System.currentTimeMillis() - start);
            log.info(logInfo.getMethodName()  + "方法出现错误，详情:" + logInfo.getDescription() + ",用户id" + logInfo.getUserId());
            RestUtils.post(url + "/saveLogInfo", logInfo);
//             传递不同ai的错误信息
            if(description.contains("DeepSeek")) {
                logMsgUtil.sendTaskLog(description + "执行失败", logInfo.getUserId(), "DeepSeek");
            }
            if(description.contains("豆包")) {
                logMsgUtil.sendTaskLog(description + "执行失败", logInfo.getUserId(), "豆包");
            }
            if(description.contains("MiniMax")) {
                logMsgUtil.sendTaskLog(description + "执行失败", logInfo.getUserId(), "MiniMax");
            }
            if(description.contains("秘塔")) {
                logMsgUtil.sendTaskLog(description + "执行失败", logInfo.getUserId(), "秘塔");
            }
            if(description.contains("KiMi")) {
                logMsgUtil.sendTaskLog(description + "执行失败", logInfo.getUserId(), "KiMi");
            }
            if(description.contains("通义千问")) {
                logMsgUtil.sendTaskLog(description + "执行失败", logInfo.getUserId(), "通义千问");
            }
            if(description.contains("百度AI")) {
                logMsgUtil.sendTaskLog(description + "执行失败", logInfo.getUserId(), "百度AI");
            }
            if(description.contains("腾讯元宝T1")) {
                logMsgUtil.sendTaskLog(description + "执行失败", logInfo.getUserId(), "腾讯元宝T1");
            }
            if (description.contains("腾讯元宝DS")) {
                logMsgUtil.sendTaskLog(description + "执行失败", logInfo.getUserId(), "腾讯元宝DS");
            }
            if (description.contains("知乎直答")) {
                logMsgUtil.sendTaskLog(description + "执行失败", logInfo.getUserId(), "知乎直答");
            }
            if (description.contains("检查")) {
                return "false";
            } else if(description.contains("投递")) {
                return "投递失败";
            } else {
                return "内容获取失败";
            }
        }
        log.info("返回结果：{}", result);
        return result;
    }

}
