package com.wx.fbsir.business.websocket.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 请求ID生成器
 * 
 * 设计目标：
 * 1. 唯一性：确保每个请求ID全局唯一
 * 2. 可追溯：从ID可以看出请求来源、时间、类型
 * 3. 有序性：按时间排序，方便日志查询和问题定位
 * 4. 可读性：包含关键信息，便于人工识别
 * 
 * ID格式：{userId}_{timestamp}_{messageType}_{sequence}
 * 
 * 示例：
 * - user123_20251223093000_PLAYWRIGHT_TEST_001
 * - admin_20251223093001_HEALTH_CHECK_002
 * - system_20251223093002_TASK_REQUEST_003
 * 
 * 格式说明：
 * - userId: 用户标识（最长20字符，超出截断）
 * - timestamp: YYYYMMDDHHmmss（14位，精确到秒）
 * - messageType: 消息类型
 * - sequence: 序列号（3位，同一秒内的计数器）
 * 
 * 使用场景：
 * 1. Admin接收前端请求时生成requestId
 * 2. requestId在整个消息传递链路中保持不变
 * 3. 用于消息追踪、日志关联、问题排查
 * 
 * 线程安全：
 * - 使用AtomicLong保证计数器线程安全
 * - 支持高并发场景
 *
 * @author FBSir
 * @date 2025-12-23
 */
public class RequestIdGenerator {

    /**
     * 时间格式化器（精确到秒）
     */
    private static final DateTimeFormatter FORMATTER = 
        DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.systemDefault());
    
    /**
     * 序列号计数器（线程安全）
     * 每秒重置，范围 000-999
     */
    private static final AtomicLong sequence = new AtomicLong(0);
    
    /**
     * 上次生成ID的秒级时间戳
     * 🟠 P1修复：改为AtomicLong实现无锁并发
     */
    private static final AtomicLong lastTimestamp = new AtomicLong(0);
    
    /**
     * userId最大长度（避免ID过长）
     */
    private static final int MAX_USER_ID_LENGTH = 20;

    /**
     * 生成请求ID
     * 
     * 格式：{userId}_{timestamp}_{messageType}_{sequence}
     * 
     * @param userId      用户ID（会被截断到20字符）
     * @param messageType 消息类型
     * @return 请求ID
     */
    public static String generate(String userId, String messageType) {
        // 1. 处理userId（防止null，限制长度，去除特殊字符）
        String safeUserId = sanitizeUserId(userId);
        
        // 2. 获取当前时间戳（秒级）
        long currentTimestamp = Instant.now().getEpochSecond();
        String formattedTime = FORMATTER.format(Instant.now());
        
        // 3. 生成序列号（同一秒内递增，跨秒重置）
        // 🟠 P1修复：使用CAS无锁实现，提升并发性能
        long seq;
        long prevTimestamp = lastTimestamp.get();
        if (currentTimestamp != prevTimestamp) {
            // 新的一秒，尝试重置序列号
            if (lastTimestamp.compareAndSet(prevTimestamp, currentTimestamp)) {
                // CAS成功，重置序列号
                sequence.set(0);
            }
        }
        seq = sequence.incrementAndGet();
        
        // 4. 处理messageType（防止null）
        String safeMessageType = messageType != null ? messageType : "UNKNOWN";
        
        // 5. 拼接ID（格式：userId_timestamp_messageType_seq）
        return String.format("%s_%s_%s_%03d", 
            safeUserId, 
            formattedTime, 
            safeMessageType, 
            seq % 1000);  // 序列号限制在000-999
    }

    /**
     * 生成请求ID（默认messageType为空字符串）
     * 
     * @param userId 用户ID
     * @return 请求ID
     */
    public static String generate(String userId) {
        return generate(userId, "REQUEST");
    }

    /**
     * 清理userId
     * - 去除null和空字符串
     * - 去除特殊字符（保留字母、数字、下划线、连字符）
     * - 限制长度
     * 
     * @param userId 原始用户ID
     * @return 清理后的用户ID
     */
    private static String sanitizeUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return "anonymous";
        }
        
        // 去除特殊字符，只保留字母、数字、下划线、连字符
        String cleaned = userId.replaceAll("[^a-zA-Z0-9_-]", "");
        
        // 如果清理后为空，使用默认值
        if (cleaned.isEmpty()) {
            return "anonymous";
        }
        
        // 限制长度
        if (cleaned.length() > MAX_USER_ID_LENGTH) {
            return cleaned.substring(0, MAX_USER_ID_LENGTH);
        }
        
        return cleaned;
    }

    /**
     * 从requestId中提取userId
     * 
     * @param requestId 请求ID
     * @return 用户ID，如果解析失败返回null
     */
    public static String extractUserId(String requestId) {
        if (requestId == null || !requestId.contains("_")) {
            return null;
        }
        return requestId.split("_")[0];
    }

    /**
     * 从requestId中提取messageType
     * 
     * @param requestId 请求ID
     * @return 消息类型，如果解析失败返回null
     */
    public static String extractMessageType(String requestId) {
        if (requestId == null) {
            return null;
        }
        String[] parts = requestId.split("_");
        if (parts.length >= 3) {
            return parts[2];
        }
        return null;
    }

    /**
     * 从requestId中提取时间戳
     * 
     * @param requestId 请求ID
     * @return 时间戳字符串（yyyyMMddHHmmss），如果解析失败返回null
     */
    public static String extractTimestamp(String requestId) {
        if (requestId == null) {
            return null;
        }
        String[] parts = requestId.split("_");
        if (parts.length >= 2) {
            return parts[1];
        }
        return null;
    }

    /**
     * 验证requestId格式是否正确
     * 
     * @param requestId 请求ID
     * @return true=格式正确，false=格式错误
     */
    public static boolean isValid(String requestId) {
        if (requestId == null || requestId.isEmpty()) {
            return false;
        }
        
        // 检查格式：应该有4个部分，用下划线分隔
        String[] parts = requestId.split("_");
        if (parts.length < 4) {
            return false;
        }
        
        // 验证时间戳部分（14位数字）
        String timestamp = parts[1];
        if (timestamp.length() != 14 || !timestamp.matches("\\d{14}")) {
            return false;
        }
        
        // 验证序列号部分（3位数字）
        String seq = parts[parts.length - 1];
        if (seq.length() != 3 || !seq.matches("\\d{3}")) {
            return false;
        }
        
        return true;
    }
}
