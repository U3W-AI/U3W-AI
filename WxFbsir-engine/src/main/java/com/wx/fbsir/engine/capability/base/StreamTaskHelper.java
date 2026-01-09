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
 * <p>
 * 使用方式：
 * <pre>
 * &#064;Controller
 * public class MyController extends StreamTaskHelper {
 *     
 *     public void handleMyTask(EngineMessage message) {
 *         String userId = message.getUserId();
 *         String requestId = message.getPayloadValue("requestId");
 *         
 *         StreamTask task = startStreamTask(userId, requestId);
 *         
 *         try {
 *             task.sendProgress("步骤1完成", 1, 3);
 *             // 业务逻辑...
 *             task.sendSuccess("任务完成", resultData);
 *         } catch (Exception e) {
 *             task.sendError("任务失败: " + e.getMessage());
 *         } finally {
 *             task.stop();
 *         }
 *     }
 * }
 * </pre>
 *
 * @author wxfbsir
 * &#064;date  2025-12-23
 */
public abstract class StreamTaskHelper {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * WebSocket客户端管理器（延迟注入避免循环依赖）
     */
    @Autowired
    @Lazy
    protected WebSocketClientManager webSocketClientManager;


    /**
     * 开始流式任务（使用默认间隔5秒）
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID（与系统的requestId区分）
     * @return 流式任务对象
     */
    protected StreamTask startStreamTask(String userId, String sessionId) {
        return startStreamTask(userId, sessionId, "unknown", 5000);
    }

    /**
     * 开始流式任务（自定义推送间隔）
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID（与系统的requestId区分）
     * @param intervalMillis 进度推送间隔（毫秒）
     * @return 流式任务对象
     */
    protected StreamTask startStreamTask(String userId, String sessionId, long intervalMillis) {
        return new StreamTask(userId, sessionId, "unknown", intervalMillis);
    }
    
    /**
     * 开始流式任务（携带AI类型）
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID（与系统的requestId区分）
     * @param aiType AI类型（如deepseek）
     * @param intervalMillis 进度推送间隔（毫秒）
     * @return 流式任务对象
     */
    protected StreamTask startStreamTask(String userId, String sessionId, String aiType, long intervalMillis) {
        return new StreamTask(userId, sessionId, aiType, intervalMillis);
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
         * @param aiType AI类型
         * @param intervalMillis 进度推送间隔（毫秒）
         */
        public StreamTask(String userId, String sessionId, String aiType, long intervalMillis) {
            this.userId = userId;
            this.sessionId = sessionId;
            this.aiType = aiType;
            this.intervalMillis = intervalMillis;
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
         * 发送文本日志消息（参考老项目 logInfo.sendTaskLog）
         * <p>
         * 用于显示执行进度文本，如"页面加载完成"、"二维码加载中"等
         * 前端会将这些日志添加到 progressLogs 数组中显示
         * 
         * @param message 日志消息内容
         */
        public void sendLog(String message) {
            if (!isConnected()) {
                return;
            }

            EngineMessage.Builder builder = EngineMessage.builder()
                .type(MessageType.TASK_LOG.getCode())
                .userId(userId)
                .payload("sessionId", sessionId)   // 会话ID
                .payload("aiType", aiType)         // AI类型
                .payload("message", message)
                .payload("timestamp", System.currentTimeMillis());

            StreamTaskHelper.this.webSocketClientManager.sendMessage(builder.build());
            log.debug("[StreamTask] 发送日志 - 用户: {}, 会话: {}, 消息: {}", userId, sessionId, message);
        }

        /**
         * 发送截图消息
         * <p>
         * 用于发送截图URL，前端会将截图添加到 screenshots 数组中轮播显示
         * 
         * @param screenshotUrl 截图URL
         */
        public void sendScreenshot(String screenshotUrl) {
            if (!isConnected() || screenshotUrl == null || screenshotUrl.isEmpty()) {
                return;
            }

            EngineMessage.Builder builder = EngineMessage.builder()
                .type(MessageType.TASK_SCREENSHOT.getCode())
                .userId(userId)
                .payload("sessionId", sessionId)   // 会话ID
                .payload("aiType", aiType)         // AI类型
                .payload("screenshotUrl", screenshotUrl)
                .payload("timestamp", System.currentTimeMillis());

            StreamTaskHelper.this.webSocketClientManager.sendMessage(builder.build());
            log.debug("[StreamTask] 发送截图 - 用户: {}, 会话: {}, URL: {}", userId, sessionId, screenshotUrl);
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

            EngineMessage.Builder builder = EngineMessage.builder()
                .type(MessageType.TASK_LOG.getCode())
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
         * 
         * @param message 结果消息
         * @param data 结果数据
         */
        public void sendSuccess(String message, Object data) {
            stop(); // 自动停止定时任务
            StreamTaskHelper.this.sendSuccess(userId, sessionId, aiType, message, data);
        }

        /**
         * 发送错误结果
         * 
         * @param errorMessage 错误消息
         */
        public void sendError(String errorMessage) {
            stop(); // 自动停止定时任务
            StreamTaskHelper.this.sendError(userId, sessionId, aiType, errorMessage);
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
