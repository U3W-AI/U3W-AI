package com.cube.mcp.aop;

import com.cube.common.entity.UserLogInfo;
import com.cube.common.entity.UserSimpleInfo;
import com.cube.common.entity.UserInfoRequest;
import com.cube.common.utils.ThreadUserInfo;
import com.cube.common.utils.UserLogUtil;
import com.cube.mcp.entities.McpResult;
import com.cube.wechat.selfapp.app.controller.AIGCController;
import com.cube.wechat.selfapp.app.util.UserInfoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.ai.tool.annotation.Tool;
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
    private final UserInfoUtil userInfoUtil;
    private final AIGCController aigcController;
    @Pointcut("execution(* com.cube.mcp.CubeMcp.*(..))")
    public void logPointCut() {
    }

    @Around("logPointCut()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        UserLogInfo userLogInfo = new UserLogInfo();
        userLogInfo.setUserId("");
        String description = "";
        String methodName = "";
        String userId =  "";
        String corpId = "";
        UserInfoRequest userInfoRequest = null;
        
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            // 方法信息
            Method method = signature.getMethod();
            methodName = method.getName();
            log.info("进入MCP方法：{}", methodName);
            userLogInfo.setMethodName(methodName);
            
            // 获取Tool注解上的name
            description = "";
            if (method.isAnnotationPresent(Tool.class)) {
                Tool tool = method.getAnnotation(Tool.class);
                description = tool.name();
                userLogInfo.setDescription(description);
            }
            
            // 参数处理，增加空指针检查
            Object[] args = joinPoint.getArgs();
            if (args.length > 0) {
                Object arg = args[0];
                if (arg instanceof UserInfoRequest) {
                    userInfoRequest = (UserInfoRequest) arg;
                    // 安全获取unionId
                    String unionId = userInfoRequest.getUnionId();
//                    校验unionId
                    if(StringUtils.isEmpty(unionId)) {
                        log.warn("UserInfoRequest中的unionId为空，方法：{}", methodName);
                    }
                    userId = userInfoUtil.getUserIdByUnionId(unionId);
                    corpId = userInfoRequest.getCorpId();
                    if(userId == null || userId.isEmpty()) {
                        return McpResult.fail("您无访问权限,请联系管理员", "");
                    }
                    UserSimpleInfo userSimpleInfo = new UserSimpleInfo();
                    userSimpleInfo.setUnionId(unionId);
                    userSimpleInfo.setCropId(corpId);
                    userSimpleInfo.setUserId(userId);
                    ThreadUserInfo.setUserInfo(userSimpleInfo);
                } else {
                    log.warn("MCP方法{}的第一个参数不是UserInfoRequest类型，实际类型：{}", methodName, arg.getClass().getName());
                    userLogInfo.setUserId("参数类型异常");
//                    将实体类转换成map
                    UserLogUtil.sendExceptionLog(userLogInfo.getUserId(), "MCP方法参数解析异常-" + description, methodName, new RuntimeException("参数解析错误"), start);
                }
            }
            userLogInfo.setMethodParams(Arrays.toString(args));
            
        } catch (Exception e) {
            log.error("MCP AOP参数解析异常，方法：{}", methodName, e);
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
                userLogInfo.setExecutionTimeMillis(end - start);
                userLogInfo.setExecutionResult("方法返回结果异常：结果为null或类型不正确");
                userLogInfo.setIsSuccess(0);
                userLogInfo.setExecutionTime(LocalDateTime.now());
                
                try {
                    aigcController.saveLogInfo(userLogInfo);
                } catch (Exception saveException) {
                    log.error("保存MCP异常日志失败，方法：{}", methodName, saveException);
                }
                
                // 使用UserLogUtil记录详细异常
                Exception nullResultException = new Exception("MCP方法返回结果为null或类型错错误");
                UserLogUtil.sendExceptionLog(userLogInfo.getUserId(), "MCP方法返回异常-" + description, methodName, nullResultException, start);
                return McpResult.fail("MCP方法执行异常：返回结果为null", "");
            }
            
            // 记录成功日志
            userLogInfo.setExecutionTimeMillis(end - start);
            String resultContent = "";
            if (StringUtils.hasText(mcpResult.getResult())) {
                resultContent = mcpResult.getResult();
            }
            if (StringUtils.hasText(mcpResult.getShareUrl())) {
                resultContent += " | ShareUrl:" + mcpResult.getShareUrl();
            }
            userLogInfo.setExecutionResult(resultContent);
            userLogInfo.setIsSuccess(mcpResult.getCode() == 200 ? 1 : 0);
            userLogInfo.setExecutionTime(LocalDateTime.now());
            
            try {
                UserLogUtil.sendNormalLog(userId, "MCP方法执行成功-" + description, methodName, 0L, mcpResult.getResult());
            } catch (Exception e) {
                log.error("保存MCP成功日志失败，方法：{}", methodName, e);
            }
            
            // 如果执行失败，使用UserLogUtil记录详细信息
            if (mcpResult.getCode() != 200) {
                Exception businessException = new Exception("MCP业务执行失败");
                UserLogUtil.sendExceptionLog(userLogInfo.getUserId(), "MCP业务执行失败-" + description, methodName, businessException, start);
                log.warn("MCP方法{}执行失败，用户：{}, 错误码：{}, 错误信息：{}", methodName, userLogInfo.getUserId(), mcpResult.getCode(), mcpResult.getResult());
            } else {
                // 记录成功日志
                UserLogUtil.sendNormalLog(userLogInfo.getUserId(), "MCP方法执行成功-" + description, methodName, start, resultContent);
                log.info("MCP方法{}执行成功，用户：{}", methodName, userLogInfo.getUserId());
            }
            
            return result;
            
        } catch (Throwable e) {
            long end = System.currentTimeMillis();
            
            String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.error("MCP方法{}执行异常，用户：{}, 错误：{}", methodName, userLogInfo.getUserId(), errorMessage, e);
            
            // 记录详细异常信息到数据库
            userLogInfo.setExecutionTimeMillis(end - start);
            userLogInfo.setExecutionResult(e.getMessage());
            userLogInfo.setIsSuccess(0);
            userLogInfo.setExecutionTime(LocalDateTime.now());
            
            try {
                aigcController.saveLogInfo(userLogInfo);
            } catch (Exception saveException) {
                log.error("保存MCP异常日志失败，方法：{}", methodName, saveException);
            }
            
            // 使用UserLogUtil记录详细异常信息
            Exception exception = (e instanceof Exception) ? (Exception) e : new Exception(e);
            UserLogUtil.sendExceptionLog(userLogInfo.getUserId(), "MCP方法执行异常-" + description, methodName, exception, start);
            // 针对特定异常类型提供更详细的错误信息
            return McpResult.fail("执行异常，请联系管理员", "");
        }
    }
    
    /**
     * 获取异常堆栈信息的前300个字
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
