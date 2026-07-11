package com.wx.fbsir.engine.util;

import org.slf4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 采样日志工具（P1-3优化：减少高频日志性能损耗）
 * 
 * 【问题】
 * - 生产环境DEBUG日志过多，严重影响性能（降低20%+）
 * - 高频操作每次都记录日志，IO阻塞
 * 
 * 【优化】
 * - 采样记录：每N次记录1次
 * - 时间窗口：每N秒最多记录1次
 * - 自动降级：错误日志不采样，DEBUG日志采样
 * 
 * 【使用】
 * ```java
 * // 每100次记录一次
 * SamplingLogger.debug(log, "websocket-receive", 100, "[WebSocket] 收到消息");
 * 
 * // 每5秒最多记录一次
 * SamplingLogger.debugWithTime(log, "heartbeat", 5000, "[WebSocket] 发送心跳");
 * ```
 * 
 * @author FBSir - Senior Architect
 * @date 2025-12-22
 */
public final class SamplingLogger {

    private SamplingLogger() {}

    private static final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicLong> lastLogTimes = new ConcurrentHashMap<>();

    /**
     * 采样DEBUG日志（每N次记录一次）
     * 
     * @param logger 日志器
     * @param key 采样键（唯一标识）
     * @param sampleRate 采样率（每N次记录1次）
     * @param message 日志消息
     * @param args 日志参数
     */
    public static void debug(Logger logger, String key, int sampleRate, String message, Object... args) {
        if (!logger.isDebugEnabled()) {
            return;
        }

        AtomicLong counter = counters.computeIfAbsent(key, k -> new AtomicLong(0));
        long count = counter.incrementAndGet();

        if (count % sampleRate == 0) {
            logger.debug(message + " [采样: {}/{}]", appendArgs(args, count, sampleRate));
        }
    }

    /**
     * 基于时间窗口的采样日志（每N毫秒最多记录1次）
     * 
     * @param logger 日志器
     * @param key 采样键
     * @param intervalMs 时间间隔（毫秒）
     * @param message 日志消息
     * @param args 日志参数
     */
    public static void debugWithTime(Logger logger, String key, long intervalMs, String message, Object... args) {
        if (!logger.isDebugEnabled()) {
            return;
        }

        AtomicLong lastTime = lastLogTimes.computeIfAbsent(key, k -> new AtomicLong(0));
        long now = System.currentTimeMillis();
        long last = lastTime.get();

        if (now - last >= intervalMs) {
            if (lastTime.compareAndSet(last, now)) {
                logger.debug(message, args);
            }
        }
    }

    /**
     * INFO级别采样（生产环境也启用）
     */
    public static void info(Logger logger, String key, int sampleRate, String message, Object... args) {
        if (!logger.isInfoEnabled()) {
            return;
        }

        AtomicLong counter = counters.computeIfAbsent(key, k -> new AtomicLong(0));
        long count = counter.incrementAndGet();

        if (count % sampleRate == 0) {
            logger.info(message + " [采样: {}/{}]", appendArgs(args, count, sampleRate));
        }
    }

    /**
     * 重置计数器（用于测试或重启）
     */
    public static void reset(String key) {
        counters.remove(key);
        lastLogTimes.remove(key);
    }

    /**
     * 清空所有计数器
     */
    public static void resetAll() {
        counters.clear();
        lastLogTimes.clear();
    }

    /**
     * 追加采样统计参数
     */
    private static Object[] appendArgs(Object[] args, long count, int sampleRate) {
        Object[] newArgs = new Object[args.length + 2];
        System.arraycopy(args, 0, newArgs, 0, args.length);
        newArgs[args.length] = count;
        newArgs[args.length + 1] = sampleRate;
        return newArgs;
    }
}
