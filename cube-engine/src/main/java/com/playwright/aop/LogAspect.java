package com.playwright.aop;

import com.playwright.entity.LogInfo;
import com.playwright.entity.UserInfoRequest;
import com.playwright.utils.RestUtils;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;

/**
 * @author 孔德权
 * description:  TODO
 * dateStart 2024/8/4 9:34
 * dateNow   2025/8/8 10:11
 */
@Component
@Aspect
@Slf4j
public class LogAspect {
    @Value("${cube.url}")
    private String url;

    @Pointcut("execution(* com.playwright.controller.*.*(..))")
    public void logPointCut() {
    }

    @Around("logPointCut()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        log.info("进入方法：{}", joinPoint.getSignature().getName());
        long start = System.currentTimeMillis();
        LogInfo logInfo = new LogInfo();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
//        方法名
        Method method = signature.getMethod();
        String methodName = method.getName();
        logInfo.setMethodName(methodName);
//        获取Operation注解上的summary
        String description = "";
        if (method.isAnnotationPresent(Operation.class)) {
            Operation operation = method.getAnnotation(Operation.class);
            description = operation.summary();
            logInfo.setDescription(description);
        } else {
            logInfo.setDescription("无");
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
            RestUtils.post(url + "/saveLogInfo", logInfo);
            if (description.contains("检查")) {
                return "false";
            } else {
                return "内容获取失败";
            }
        }
        log.info("返回结果：{}", result);
        return result;
    }

}
