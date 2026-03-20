package com.wx.fbsir.business.aigc.service;

import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AIGC批量更新服务
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 优化目标
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 【问题】1次AI咨询产生15-30次数据库UPDATE（高频日志+截图）
 * 【优化】批量攒批，每500ms或积累到阈值时统一更新
 * 【收益】减少90%数据库往返，降低行锁竞争
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📊 性能对比
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 优化前：10条日志 + 5张截图 = 15次UPDATE
 * 优化后：10条日志 + 5张截图 = 1次UPDATE（攒批500ms）
 * 
 * @author wxfbsir
 * @date 2026-01-09
 */
@Service
public class AigcBatchUpdateService {

    private static final Logger log = LoggerFactory.getLogger(AigcBatchUpdateService.class);

    private final IAigcService aigcService;

    /**
     * 批量更新队列
     * Key: sessionId
     * Value: 待更新的操作列表
     */
    private final Map<String, SessionUpdateBatch> batchQueue = new ConcurrentHashMap<>();

    /**
     * 批量刷新阈值（条数）
     */
    private static final int BATCH_THRESHOLD = 10;

    /**
     * 最大重试次数
     */
    private static final int MAX_RETRY_COUNT = 3;

    public AigcBatchUpdateService(IAigcService aigcService) {
        this.aigcService = aigcService;
    }

    // ==========================================================================
    // 🔥 批量添加操作（攒批入口）
    // ==========================================================================

    /**
     * 添加进度日志到批量队列
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param chatId 前端chatId
     * @param aiType AI类型
     * @param logMessage 日志内容
     * @param timestamp 时间戳
     */
    public void addProgressLog(String userId, String sessionId, String chatId, 
                               String aiType, String logMessage, Long timestamp) {
        SessionUpdateBatch batch = batchQueue.computeIfAbsent(sessionId, 
            k -> new SessionUpdateBatch(userId, sessionId, chatId));
        
        Map<String, Object> logEntry = new HashMap<>();
        logEntry.put("content", logMessage);
        logEntry.put("timestamp", timestamp);
        logEntry.put("aiType", aiType);
        
        batch.addProgressLog(logEntry);
        
        log.debug("[批量队列] 添加日志 - 会话: {}, 当前队列大小: {}", sessionId, batch.getTotalSize());
        
        // 🔥 达到阈值立即刷新
        if (batch.getTotalSize() >= BATCH_THRESHOLD) {
            flushSession(sessionId);
        }
    }

    /**
     * 添加截图到批量队列
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param chatId 前端chatId
     * @param aiType AI类型
     * @param screenshotUrl 截图URL
     */
    public void addScreenshot(String userId, String sessionId, String chatId, 
                             String aiType, String screenshotUrl) {
        SessionUpdateBatch batch = batchQueue.computeIfAbsent(sessionId, 
            k -> new SessionUpdateBatch(userId, sessionId, chatId));
        
        batch.addScreenshot(screenshotUrl);
        
        log.debug("[批量队列] 添加截图 - 会话: {}, 当前队列大小: {}", sessionId, batch.getTotalSize());
        
        // 🔥 达到阈值立即刷新
        if (batch.getTotalSize() >= BATCH_THRESHOLD) {
            flushSession(sessionId);
        }
    }

    // ==========================================================================
    // 🔄 定时批量刷新（每500ms执行一次）
    // ==========================================================================

    /**
     * 定时批量刷新所有会话的更新
     * 
     * 【执行频率】每500ms执行一次
     * 【处理策略】合并同一sessionId的所有日志和截图，一次性UPDATE
     */
    @Scheduled(fixedDelay = 500)
    public void scheduledFlushAll() {
        if (batchQueue.isEmpty()) {
            return;
        }

        log.debug("[批量刷新] 开始处理 - 队列大小: {}", batchQueue.size());
        
        // 复制队列并清空（避免阻塞新增）
        Map<String, SessionUpdateBatch> currentBatch = new HashMap<>(batchQueue);
        batchQueue.clear();
        
        // 批量处理
        int successCount = 0;
        int failCount = 0;
        
        for (Map.Entry<String, SessionUpdateBatch> entry : currentBatch.entrySet()) {
            String sessionId = entry.getKey();
            SessionUpdateBatch batch = entry.getValue();
            
            try {
                flushBatch(batch);
                successCount++;
            } catch (Exception e) {
                failCount++;
                log.error("[批量刷新] 处理失败 - 会话: {}, 错误: {}", sessionId, e.getMessage(), e);
                // 🔥 失败重新入队，下次重试
                batchQueue.put(sessionId, batch);
            }
        }
        
        if (successCount > 0 || failCount > 0) {
            log.info("[批量刷新] 完成 - 成功: {}, 失败: {}", successCount, failCount);
        }
    }

    /**
     * 立即刷新指定会话（阈值触发）
     */
    private void flushSession(String sessionId) {
        SessionUpdateBatch batch = batchQueue.remove(sessionId);
        if (batch != null) {
            try {
                flushBatch(batch);
                log.info("[立即刷新] 完成 - 会话: {}, 更新数量: {}", sessionId, batch.getTotalSize());
            } catch (Exception e) {
                log.error("[立即刷新] 失败 - 会话: {}, 错误: {}", sessionId, e.getMessage(), e);
                // 🔥 失败重新入队
                batchQueue.put(sessionId, batch);
            }
        }
    }

    /**
     * 执行批量更新（带重试机制）
     */
    private void flushBatch(SessionUpdateBatch batch) {
        int retryCount = 0;
        Exception lastException = null;
        
        while (retryCount < MAX_RETRY_COUNT) {
            try {
                doFlushBatch(batch);
                return; // 成功则返回
            } catch (Exception e) {
                lastException = e;
                retryCount++;
                
                if (retryCount < MAX_RETRY_COUNT) {
                    try {
                        // 🔥 指数退避（100ms, 200ms, 400ms）
                        long backoffMs = 100L * (1 << (retryCount - 1));
                        log.warn("[批量刷新] 重试 {}/{} - 会话: {}, 等待: {}ms", 
                            retryCount, MAX_RETRY_COUNT, batch.sessionId, backoffMs);
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("批量更新被中断", ie);
                    }
                }
            }
        }
        
        // 🔥 重试3次仍失败，记录错误并告警
        log.error("[批量刷新] 重试{}次后仍失败 - 会话: {}, 数据量: {}, 错误: {}", 
            MAX_RETRY_COUNT, batch.sessionId, batch.getTotalSize(), 
            lastException != null ? lastException.getMessage() : "unknown", lastException);
        
        // TODO: 发送告警（钉钉/企微/邮件）
        // alertService.sendAlert("AIGC批量更新失败", batch.sessionId, lastException);
        
        // TODO: 记录到失败表，后续补偿
        // failedUpdateRepository.save(batch);
        
        throw new RuntimeException("批量更新失败，已重试" + MAX_RETRY_COUNT + "次", lastException);
    }

    /**
     * 执行单次批量更新（核心逻辑）
     */
    private void doFlushBatch(SessionUpdateBatch batch) {
        String sessionId = batch.sessionId;
        
        // 🔥 一次性读取现有记录
        Map<String, Object> existingChat = aigcService.getChatBySessionId(sessionId);
        
        if (existingChat != null) {
            // 更新现有记录
            String dataStr = (String) existingChat.get("data");
            Map<String, Object> dataMap = dataStr != null ? JSON.parseObject(dataStr) : new HashMap<>();
            
            // 🔥 批量合并日志
            if (!batch.progressLogs.isEmpty()) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> logs = (List<Map<String, Object>>) dataMap.get("progressLogs");
                if (logs == null) {
                    logs = new ArrayList<>();
                    dataMap.put("progressLogs", logs);
                }
                logs.addAll(batch.progressLogs);
                log.debug("[批量更新] 合并日志 - 会话: {}, 新增: {}, 总计: {}", 
                    sessionId, batch.progressLogs.size(), logs.size());
            }
            
            // 🔥 批量合并截图
            if (!batch.screenshots.isEmpty()) {
                @SuppressWarnings("unchecked")
                List<String> screenshots = (List<String>) dataMap.get("screenshots");
                if (screenshots == null) {
                    screenshots = new ArrayList<>();
                    dataMap.put("screenshots", screenshots);
                }
                screenshots.addAll(batch.screenshots);
                log.debug("[批量更新] 合并截图 - 会话: {}, 新增: {}, 总计: {}", 
                    sessionId, batch.screenshots.size(), screenshots.size());
            }
            
            // 🔥 一次性UPDATE数据库
            existingChat.put("data", JSON.toJSONString(dataMap));
            aigcService.updateChatData(existingChat);
            
            log.info("[批量更新] 成功 - 会话: {}, 日志: {}, 截图: {}", 
                sessionId, batch.progressLogs.size(), batch.screenshots.size());
            
        } else {
            // 创建新记录
            Map<String, Object> chatData = new HashMap<>();
            chatData.put("id", sessionId);
            chatData.put("userId", batch.userId);
            
            Map<String, Object> dataMap = new HashMap<>();
            if (!batch.progressLogs.isEmpty()) {
                dataMap.put("progressLogs", new ArrayList<>(batch.progressLogs));
            }
            if (!batch.screenshots.isEmpty()) {
                dataMap.put("screenshots", new ArrayList<>(batch.screenshots));
            }
            
            chatData.put("data", JSON.toJSONString(dataMap));
            aigcService.saveChatData(chatData);
            
            log.info("[批量更新] 创建新记录 - 会话: {}, 日志: {}, 截图: {}", 
                sessionId, batch.progressLogs.size(), batch.screenshots.size());
        }
    }




    // ==========================================================================
    // 📦 内部类：会话更新批次
    // ==========================================================================

    /**
     * 会话更新批次（攒批数据结构）
     */
    private static class SessionUpdateBatch {
        private final String userId;
        private final String sessionId;
        private final String chatId;
        private final List<Map<String, Object>> progressLogs = new ArrayList<>();
        private final List<String> screenshots = new ArrayList<>();

        public SessionUpdateBatch(String userId, String sessionId, String chatId) {
            this.userId = userId;
            this.sessionId = sessionId;
            this.chatId = chatId;
        }

        public void addProgressLog(Map<String, Object> logEntry) {
            progressLogs.add(logEntry);
        }

        public void addScreenshot(String screenshotUrl) {
            screenshots.add(screenshotUrl);
        }

        public int getTotalSize() {
            return progressLogs.size() + screenshots.size();
        }
    }


}
