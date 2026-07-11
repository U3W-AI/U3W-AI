package com.wx.fbsir.engine.capability.annotation;

import java.lang.annotation.*;

/**
 * 流式能力注解
 * 
 * 标记在Controller方法上，表示该方法处理流式消息（支持多次进度返回）
 * 
 * 使用示例：
 * <pre>
 * @Controller
 * public class MyController extends BaseStreamController {
 *     
 *     @StreamCapability(
 *         type = "MY_TASK",
 *         description = "我的任务",
 *         progressInterval = 5000
 *     )
 *     public void handleMyTask(EngineMessage message) {
 *         // 处理流式任务
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
public @interface StreamCapability {
    
    /**
     * 消息类型（必填）
     * 
     * 对应MessageType枚举中的code
     * 例如："PLAYWRIGHT_TEST"
     */
    String type();
    
    /**
     * 能力描述（必填）
     * 
     * 用于注册时展示给前端
     * 例如："Playwright自动化测试"
     */
    String description();
    
    /**
     * 进度推送间隔（可选，默认5000毫秒）
     * 
     * 当使用StreamTask的自动进度推送功能时生效
     */
    long progressInterval() default 5000L;
    
    /**
     * 是否需要认证（可选，默认true）
     */
    boolean requireAuth() default true;
    
    /**
     * 最大并发数（可选，默认10）
     * 
     * 同一用户同时执行此任务的最大数量
     */
    int maxConcurrency() default 10;
}
