package com.wx.fbsir.engine.capability.annotation;

import java.lang.annotation.*;

/**
 * 单次能力注解
 * 
 * 标记在Controller方法上，表示该方法处理单次返回消息
 * 
 * 使用示例：
 * <pre>
 * @Controller
 * public class HealthCheckController {
 *     
 *     @OnceCapability(
 *         type = "HEALTH_CHECK",
 *         description = "健康检查"
 *     )
 *     public void handleHealthCheck(EngineMessage message) {
 *         // 处理单次任务，返回一次结果
 *     }
 * }
 * </pre>
 *
 * @author FBSir
 * @date 2025-12-23
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OnceCapability {
    
    /**
     * 消息类型（必填）
     * 
     * 对应MessageType枚举中的code
     * 例如："HEALTH_CHECK"
     */
    String type();
    
    /**
     * 能力描述（必填）
     * 
     * 用于注册时展示给前端
     * 例如："健康检查"
     */
    String description();
    
    /**
     * 是否需要认证（可选，默认true）
     */
    boolean requireAuth() default true;
    
    /**
     * 超时时间（可选，默认30秒）
     * 
     * 单位：毫秒
     */
    long timeout() default 30000L;
}
