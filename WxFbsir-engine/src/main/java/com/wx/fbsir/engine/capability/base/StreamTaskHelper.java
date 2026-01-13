package com.wx.fbsir.engine.capability.base;

import com.wx.fbsir.engine.websocket.client.WebSocketClientManager;
import com.wx.fbsir.engine.websocket.message.EngineMessage;
import com.wx.fbsir.engine.websocket.message.MessageType;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 流式任务辅助工具类
 * <p>
 * 为流式任务提供进度推送、日志发送、截图发送等功能
 * 支持两种消息格式：AI_TASK_* (AI业务) 和 TASK_* (通用业务)
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 使用场景
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 【AI业务】使用 startAiStreamTask() - 发送 AI_TASK_* 消息
 * - AI咨询（DeepSeek、通义千问等）
 * - Admin会自动存储到 wc_chat_history.data 字段
 * - 前端自动显示在任务流程区、截图轮播区
 * 
 * 【通用业务】使用 startStreamTask() - 发送 TASK_* 消息
 * - 登录检测、扫码登录
 * - 元器等非AI工具
 * - 其他流式任务
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 💡 使用示例
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * // AI业务示例
 * StreamTask task = startAiStreamTask(userId, sessionId, "deepseek", 6000);
 * task.sendLog("正在连接DeepSeek...");     // → AI_TASK_LOG
 * task.sendScreenshot(screenshotUrl);       // → AI_TASK_SCREENSHOT
 * task.sendSuccess("完成", resultData);     // → AI_TASK_RESULT
 * 
 * // 通用业务示例
 * StreamTask task = startStreamTask(userId, sessionId, 5000);
 * task.sendLog("页面加载中...");           // → TASK_LOG
 * task.sendScreenshot(screenshotUrl);       // → TASK_SCREENSHOT
 * task.sendSuccess("完成", resultData);     // → TASK_RESULT
 *
 * @author wxfbsir
 * @date 2025-12-23
 * @version 2.0 (支持AI_TASK_*和TASK_*双格式)
 */
public abstract class StreamTaskHelper {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * WebSocket客户端管理器（延迟注入避免循环依赖）
     */
    @Autowired
    @Lazy
    protected WebSocketClientManager webSocketClientManager;


    // ==========================================================================
    // 🔧 通用业务流式任务（发送 TASK_* 消息）
    // ==========================================================================
    
    /**
     * 开始通用流式任务（使用默认间隔5秒）
     * 
     * 【使用场景】登录检测、扫码登录、元器等非AI业务
     * 【发送消息】TASK_LOG、TASK_SCREENSHOT、TASK_RESULT
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID（与系统的requestId区分）
     * @return 流式任务对象
     */
    protected StreamTask startStreamTask(String userId, String sessionId) {
        return new StreamTask(userId, sessionId, "unknown", 5000, false);
    }

    /**
     * 开始通用流式任务（自定义推送间隔）
     * 
     * 【使用场景】登录检测、扫码登录、元器等非AI业务
     * 【发送消息】TASK_LOG、TASK_SCREENSHOT、TASK_RESULT
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID（与系统的requestId区分）
     * @param intervalMillis 进度推送间隔（毫秒）
     * @return 流式任务对象
     */
    protected StreamTask startStreamTask(String userId, String sessionId, long intervalMillis) {
        return new StreamTask(userId, sessionId, "unknown", intervalMillis, false);
    }
    
    // ==========================================================================
    // 🤖 AI业务流式任务（发送 AI_TASK_* 消息）
    // ==========================================================================
    
    /**
     * 开始AI流式任务（自动发送 AI_TASK_* 格式消息）
     * 
     * 【使用场景】AI咨询业务（DeepSeek、通义千问等）
     * 【发送消息】AI_TASK_LOG、AI_TASK_SCREENSHOT、AI_TASK_RESULT、AI_TASK_ERROR
     * 【Admin处理】自动存储到 wc_chat_history.data.progressLogs、screenshots
     * 【前端显示】任务流程区、截图轮播区、AI响应结果区
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID（前端生成，全链路追踪）
     * @param aiType AI类型（如"deepseek"、"tongyi"等）
     * @param intervalMillis 进度推送间隔（毫秒）
     * @return AI流式任务对象
     */
    protected StreamTask startAiStreamTask(String userId, String sessionId, String aiType, long intervalMillis) {
        return new StreamTask(userId, sessionId, aiType, intervalMillis, true);
    }

    /**
     * 发送单次进度通知（无需创建StreamTask）
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param aiType AI类型
     * @param message 进度消息
     * @param current 当前步骤
     * @param total 总步骤数
     */
    protected void sendProgress(String userId, String sessionId, String aiType, String message, int current, int total) {
        if (!isConnected()) {
            return;
        }

        EngineMessage progressMsg = EngineMessage.builder()
            .type(MessageType.TASK_LOG.getCode())
            .userId(userId)
            .payload("sessionId", sessionId)   // 会话ID
            .payload("aiType", aiType)         // AI类型
            .payload("message", message)
            .payload("current", current)
            .payload("total", total)
            .payload("timestamp", System.currentTimeMillis())
            .build();

        webSocketClientManager.sendMessage(progressMsg);
    }

    /**
     * 发送成功结果
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param aiType AI类型
     * @param message 结果消息
     * @param data 结果数据
     */
    protected void sendSuccess(String userId, String sessionId, String aiType, String message, Object data) {
        if (!isConnected()) {
            return;
        }

        EngineMessage.Builder builder = EngineMessage.builder()
            .type(MessageType.TASK_RESULT.getCode())
            .userId(userId)
            .payload("sessionId", sessionId)   // 会话ID
            .payload("aiType", aiType)         // AI类型
            .payload("success", true)
            .payload("message", message)
            .payload("timestamp", System.currentTimeMillis());

        if (data != null) {
            builder.payload("data", data);
        }

        webSocketClientManager.sendMessage(builder.build());
    }

    /**
     * 发送错误结果
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param aiType AI类型
     * @param errorMessage 错误消息
     */
    protected void sendError(String userId, String sessionId, String aiType, String errorMessage) {
        if (!isConnected()) {
            return;
        }

        EngineMessage errorMsg = EngineMessage.builder()
            .type(MessageType.TASK_RESULT.getCode())
            .userId(userId)
            .payload("sessionId", sessionId)   // 会话ID
            .payload("aiType", aiType)         // AI类型
            .payload("success", false)
            .payload("errorCode", "TASK_ERROR")
            .payload("errorMessage", errorMessage)
            .payload("timestamp", System.currentTimeMillis())
            .build();

        webSocketClientManager.sendMessage(errorMsg);
    }

    /**
     * 检查WebSocket是否已连接
     */
    protected boolean isConnected() {
        return webSocketClientManager != null && webSocketClientManager.isConnected();
    }

    /**
     * 流式任务包装类
     * <p>
     * 提供自动化的进度推送和消息发送功能
     * 根据 isAiTask 标识自动选择发送 AI_TASK_* 或 TASK_* 消息
     */
    public class StreamTask {
        /**
         * -- GETTER --
         *  获取用户ID
         */
        @Getter
        private final String userId;
        /**
         * -- GETTER --
         *  获取会话ID
         */
        @Getter
        private final String sessionId;  // 会话ID（与系统的requestId区分）
        /**
         * -- GETTER --
         *  获取AI类型
         */
        @Getter
        private final String aiType;     // AI类型
        /**
         * -- GETTER --
         *  是否为AI任务
         */
        @Getter
        private final boolean isAiTask;  // 是否为AI任务（true=AI_TASK_*, false=TASK_*）
        private final long intervalMillis;
        private final AtomicInteger progressCount = new AtomicInteger(0);
        private final AtomicBoolean stopped = new AtomicBoolean(false);
        private ScheduledExecutorService scheduler;
        private ScheduledFuture<?> progressFuture;

        /**
         * 构造函数
         * 
         * @param userId 用户ID
         * @param sessionId 会话ID（全链路唯一标识）
         * @param aiType AI类型（AI任务必填，通用任务可为"unknown"）
         * @param intervalMillis 进度推送间隔（毫秒）
         * @param isAiTask 是否为AI任务（true=发送AI_TASK_*，false=发送TASK_*）
         */
        public StreamTask(String userId, String sessionId, String aiType, long intervalMillis, boolean isAiTask) {
            this.userId = userId;
            this.sessionId = sessionId;
            this.aiType = aiType;
            this.intervalMillis = intervalMillis;
            this.isAiTask = isAiTask;
        }

        /**
         * 启动定时进度推送
         * 
         * @param progressMessage 进度消息生成器（参数：当前计数）
         */
        public void startAutoProgress(java.util.function.Function<Integer, String> progressMessage) {
            if (scheduler != null) {
                log.warn("[StreamTask] 定时任务已启动，请勿重复启动 - 用户: {}, 会话ID: {}", userId, sessionId);
                return;
            }

            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r);
                thread.setName("StreamTask-" + userId + "-" + System.currentTimeMillis());
                thread.setDaemon(true);
                return thread;
            });

            progressFuture = scheduler.scheduleAtFixedRate(() -> {
                if (stopped.get()) {
                    return;
                }

                try {
                    int count = progressCount.incrementAndGet();
                    String message = progressMessage.apply(count);
                    sendProgress(message);
                } catch (Exception e) {
                    log.error("[StreamTask] 自动进度推送失败 - 用户: {}, 会话: {}, 错误: {}", 
                        userId, sessionId, e.getMessage());
                }
            }, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);

            log.debug("[StreamTask] 已启动自动进度推送 - 用户: {}, 会话: {}, 间隔: {}ms", 
                userId, sessionId, intervalMillis);
        }


        /**
         * 发送进度通知
         * 
         * @param message 进度消息
         */
        public void sendProgress(String message) {
            StreamTaskHelper.this.sendProgress(userId, sessionId, aiType, message, 0, 0);
        }

        /**
         * 发送进度通知（带进度百分比）
         * 
         * @param message 进度消息
         * @param current 当前步骤
         * @param total 总步骤数
         */
        public void sendProgress(String message, int current, int total) {
            StreamTaskHelper.this.sendProgress(userId, sessionId, aiType, message, current, total);
        }

        /**
         * 发送文本日志消息
         * <p>
         * 【AI任务】发送 AI_TASK_LOG → Admin存储到progressLogs → 前端任务流程区
         * 【通用任务】发送 TASK_LOG → 前端直接显示
         * 
         * @param message 日志消息内容
         */
        public void sendLog(String message) {
            if (!isConnected()) {
                return;
            }

            // 根据任务类型选择消息格式
            String messageType = isAiTask ? MessageType.AI_TASK_LOG.getCode() : MessageType.TASK_LOG.getCode();
            
            EngineMessage.Builder builder = EngineMessage.builder()
                .type(messageType)
                .userId(userId)
                .payload("sessionId", sessionId)   // 会话ID
                .payload("aiType", aiType)         // AI类型
                .payload("message", message)
                .payload("timestamp", System.currentTimeMillis());

            StreamTaskHelper.this.webSocketClientManager.sendMessage(builder.build());
            log.debug("[StreamTask] 发送日志[{}] - 用户: {}, 会话: {}, 消息: {}", messageType, userId, sessionId, message);
        }

        /**
         * 发送截图消息
         * <p>
         * 【AI任务】发送 AI_TASK_SCREENSHOT → Admin存储到screenshots → 前端轮播区
         * 【通用任务】发送 TASK_SCREENSHOT → 前端直接显示
         * 
         * @param screenshotUrl 截图URL
         */
        public void sendScreenshot(String screenshotUrl) {
            if (!isConnected() || screenshotUrl == null || screenshotUrl.isEmpty()) {
                return;
            }

            // 根据任务类型选择消息格式
            String messageType = isAiTask ? MessageType.AI_TASK_SCREENSHOT.getCode() : MessageType.TASK_SCREENSHOT.getCode();
            
            EngineMessage.Builder builder = EngineMessage.builder()
                .type(messageType)
                .userId(userId)
                .payload("sessionId", sessionId)   // 会话ID
                .payload("aiType", aiType)         // AI类型
                .payload("screenshotUrl", screenshotUrl)
                .payload("timestamp", System.currentTimeMillis());

            StreamTaskHelper.this.webSocketClientManager.sendMessage(builder.build());
            log.debug("[StreamTask] 发送截图[{}] - 用户: {}, 会话: {}, URL: {}", messageType, userId, sessionId, screenshotUrl);
        }

        /**
         * 发送进度通知（带额外数据）
         * 
         * @param message 进度消息
         * @param extraData 额外数据
         */
        public void sendProgress(String message, java.util.Map<String, Object> extraData) {
            if (!isConnected()) {
                return;
            }

            // 根据任务类型选择消息格式
            String messageType = isAiTask ? MessageType.AI_TASK_LOG.getCode() : MessageType.TASK_LOG.getCode();
            
            EngineMessage.Builder builder = EngineMessage.builder()
                .type(messageType)
                .userId(userId)
                .payload("sessionId", sessionId)   // 会话ID
                .payload("aiType", aiType)         // AI类型
                .payload("message", message)
                .payload("timestamp", System.currentTimeMillis());

            if (extraData != null) {
                extraData.forEach(builder::payload);
            }

            StreamTaskHelper.this.webSocketClientManager.sendMessage(builder.build());
        }

        /**
         * 发送成功结果
         * <p>
         * 【AI任务】发送 AI_TASK_RESULT → Admin合并存储到data → 前端结果区
         * 【通用任务】发送 TASK_RESULT → 前端直接显示
         * 
         * @param message 结果消息
         * @param data 结果数据
         */
        public void sendSuccess(String message, Object data) {
            stop(); // 自动停止定时任务
            
            if (!isConnected()) {
                return;
            }

            // 根据任务类型选择消息格式
            String messageType = isAiTask ? MessageType.AI_TASK_RESULT.getCode() : MessageType.TASK_RESULT.getCode();
            
            EngineMessage.Builder builder = EngineMessage.builder()
                .type(messageType)
                .userId(userId)
                .payload("sessionId", sessionId)
                .payload("aiType", aiType)
                .payload("success", true)
                .payload("message", message)
                .payload("timestamp", System.currentTimeMillis());

            if (data != null) {
                builder.payload("data", data);
            }

            StreamTaskHelper.this.webSocketClientManager.sendMessage(builder.build());
            log.debug("[StreamTask] 发送成功[{}] - 用户: {}, 会话: {}", messageType, userId, sessionId);
        }

        /**
         * 发送错误结果
         * <p>
         * 【AI任务】发送 AI_TASK_ERROR → Admin记录错误 → 前端显示错误
         * 【通用任务】发送 TASK_RESULT(success=false) → 前端显示错误
         * 
         * @param errorMessage 错误消息
         */
        public void sendError(String errorMessage) {
            stop(); // 自动停止定时任务
            
            if (!isConnected()) {
                return;
            }

            // 根据任务类型选择消息格式
            String messageType = isAiTask ? MessageType.AI_TASK_ERROR.getCode() : MessageType.TASK_RESULT.getCode();
            
            EngineMessage errorMsg = EngineMessage.builder()
                .type(messageType)
                .userId(userId)
                .payload("sessionId", sessionId)
                .payload("aiType", aiType)
                .payload("success", false)
                .payload("errorCode", "TASK_ERROR")
                .payload("errorMessage", errorMessage)
                .payload("timestamp", System.currentTimeMillis())
                .build();

            StreamTaskHelper.this.webSocketClientManager.sendMessage(errorMsg);
            log.error("[StreamTask] 发送错误[{}] - 用户: {}, 会话: {}, 错误: {}", messageType, userId, sessionId, errorMessage);
        }

        /**
         * 停止定时任务
         * 🔴 P0修复：确保异常时也能正确关闭线程池
         */
        public void stop() {
            if (stopped.getAndSet(true)) {
                return; // 已经停止
            }

            try {
                if (progressFuture != null) {
                    progressFuture.cancel(false);
                }
            } finally {
                // 🔴 P0修复：确保任何情况下都关闭线程池
                if (scheduler != null) {
                    scheduler.shutdown();
                    try {
                        if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                            scheduler.shutdownNow();
                        }
                    } catch (InterruptedException e) {
                        scheduler.shutdownNow();
                        Thread.currentThread().interrupt();
                    }
                }
            }

            log.debug("[StreamTask] 已停止 - 用户: {}, 会话: {}", userId, sessionId);
        }

    }
}
