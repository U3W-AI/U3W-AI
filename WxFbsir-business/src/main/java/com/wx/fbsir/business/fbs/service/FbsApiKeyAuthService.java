package com.wx.fbsir.business.fbs.service;

import com.wx.fbsir.business.fbs.domain.entity.FbsApiKey;
import com.wx.fbsir.business.fbs.mapper.FbsApiKeyMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * API Key 认证与限流服务
 *
 * 职责：
 * 1. 校验 API Key 有效性（存在 + 启用状态）
 * 2. 内存级速率限制（MVP：ConcurrentHashMap + 滑动窗口）
 * 3. 后续迭代：Redis + 滑动窗口
 *
 * @author wxfbsir
 * @date 2026-04-11
 */
@Service
public class FbsApiKeyAuthService {

    private static final Logger log = LoggerFactory.getLogger(FbsApiKeyAuthService.class);

    @Autowired
    private FbsApiKeyMapper apiKeyMapper;

    /** 速率限制计数器：apiKey → RateLimitEntry */
    private final ConcurrentHashMap<String, RateLimitEntry> rateLimitMap = new ConcurrentHashMap<>();

    /**
     * 校验 API Key 并检查速率限制
     *
     * @param apiKey API Key 字符串
     * @return 校验结果（null=通过，非null=错误消息）
     */
    public ApiKeyCheckResult checkApiKey(String apiKey) {
        // 1. Header 缺失
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return ApiKeyCheckResult.fail(401, "SKILL_API_KEY_INVALID", "API Key 无效");
        }

        // 2. 查询 Key（只查启用状态）
        FbsApiKey keyEntity = apiKeyMapper.selectActiveByKey(apiKey);

        // 3. Key 不存在 → 401
        if (keyEntity == null) {
            // 再查一次不限状态的，区分是"不存在"还是"已禁用"
            FbsApiKey anyStatusKey = apiKeyMapper.selectByApiKey(apiKey);
            if (anyStatusKey != null && anyStatusKey.getStatus() != null && anyStatusKey.getStatus() == 0) {
                return ApiKeyCheckResult.fail(403, "SKILL_API_KEY_DISABLED", "API Key 已禁用");
            }
            return ApiKeyCheckResult.fail(401, "SKILL_API_KEY_INVALID", "API Key 无效");
        }

        // 4. 速率限制检查
        if (!checkRateLimit(keyEntity)) {
            return ApiKeyCheckResult.fail(429, "SKILL_API_RATE_LIMITED", "API 调用频率超限，请稍后重试");
        }

        // 5. 更新最后使用时间
        try {
            apiKeyMapper.updateLastUsedAt(keyEntity.getApiKey());
        } catch (Exception e) {
            // 更新失败不影响主流程，仅记录日志
            log.warn("更新 API Key 最后使用时间失败 key={}", keyEntity.getMaskedApiKey(), e);
        }

        // 6. 通过
        return ApiKeyCheckResult.success(keyEntity);
    }

    // ========== #15 安全加固：时间戳校验 + 签名校验 ==========

    /** 时间戳有效窗口：5 分钟（毫秒） */
    private static final long TIMESTAMP_WINDOW_MS = 5 * 60 * 1000;

    /**
     * 校验时间戳
     *
     * @param timestamp X-FBS-Timestamp Header 值（Unix 毫秒时间戳字符串）
     * @return 校验结果
     */
    public TimestampCheckResult verifyTimestamp(String timestamp) {
        // 1. 缺失
        if (timestamp == null || timestamp.trim().isEmpty()) {
            return TimestampCheckResult.fail(401, "SKILL_API_TIMESTAMP_MISSING", "时间戳缺失");
        }

        // 2. 解析
        long ts;
        try {
            ts = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            return TimestampCheckResult.fail(401, "SKILL_API_TIMESTAMP_MISSING", "时间戳格式无效");
        }

        // 3. 过期检查
        long now = System.currentTimeMillis();
        if (Math.abs(now - ts) > TIMESTAMP_WINDOW_MS) {
            return TimestampCheckResult.fail(401, "SKILL_API_TIMESTAMP_EXPIRED", "时间戳已过期");
        }

        return TimestampCheckResult.success();
    }

    /**
     * 校验 HMAC-SHA256 签名
     *
     * @param apiKey    API Key 原文（从数据库读取）
     * @param timestamp 时间戳字符串
     * @param body      原始 request body 字符串（不做反序列化再序列化）
     * @param signature 客户端提供的签名（X-FBS-Signature Header）
     * @return 校验结果
     */
    public SignatureCheckResult verifySignature(String apiKey, String timestamp, String body, String signature) {
        // 1. 签名缺失
        if (signature == null || signature.trim().isEmpty()) {
            return SignatureCheckResult.fail(401, "SKILL_API_SIGNATURE_INVALID", "签名缺失");
        }

        // 2. 计算 HMAC-SHA256
        try {
            String message = timestamp + "\n" + body;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(apiKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));

            // 转为 hex 字符串
            StringBuilder sb = new StringBuilder(computed.length * 2);
            for (byte b : computed) {
                sb.append(String.format("%02x", b));
            }
            String computedHex = sb.toString();

            // 3. 比对签名（使用 MessageDigest.isEqual 防时序攻击）
            boolean valid = MessageDigest.isEqual(
                    computedHex.getBytes(StandardCharsets.UTF_8),
                    signature.trim().getBytes(StandardCharsets.UTF_8)
            );

            if (!valid) {
                return SignatureCheckResult.fail(401, "SKILL_API_SIGNATURE_INVALID", "签名不匹配");
            }

            return SignatureCheckResult.success();
        } catch (Exception e) {
            log.error("签名校验异常", e);
            return SignatureCheckResult.fail(401, "SKILL_API_SIGNATURE_INVALID", "签名校验异常");
        }
    }

    /**
     * 速率限制检查（内存级，滑动窗口）
     */
    private boolean checkRateLimit(FbsApiKey keyEntity) {
        int limit = keyEntity.getRateLimitPerMin() != null ? keyEntity.getRateLimitPerMin() : 60;
        String key = keyEntity.getApiKey();

        long now = System.currentTimeMillis();
        long windowStart = (now / 60000) * 60000; // 当前分钟起始毫秒

        RateLimitEntry entry = rateLimitMap.compute(key, (k, existing) -> {
            if (existing == null || existing.windowStart != windowStart) {
                // 新窗口
                return new RateLimitEntry(new AtomicLong(1), windowStart);
            }
            // 同一窗口，计数+1
            existing.count.incrementAndGet();
            return existing;
        });

        long currentCount = entry.count.get();
        if (currentCount > limit) {
            log.warn("API Key 速率超限 key={}, count={}, limit={}", keyEntity.getMaskedApiKey(), currentCount, limit);
            return false;
        }
        return true;
    }

    /**
     * 速率限制条目
     */
    static class RateLimitEntry {
        final AtomicLong count;
        final long windowStart;

        RateLimitEntry(AtomicLong count, long windowStart) {
            this.count = count;
            this.windowStart = windowStart;
        }
    }

    /**
     * 校验结果
     */
    public static class ApiKeyCheckResult {
        private final boolean success;
        private final int httpStatus;
        private final String errorCode;
        private final String errorMessage;
        private final FbsApiKey keyEntity;

        private ApiKeyCheckResult(boolean success, int httpStatus, String errorCode, String errorMessage, FbsApiKey keyEntity) {
            this.success = success;
            this.httpStatus = httpStatus;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
            this.keyEntity = keyEntity;
        }

        public static ApiKeyCheckResult success(FbsApiKey keyEntity) {
            return new ApiKeyCheckResult(true, 200, null, null, keyEntity);
        }

        public static ApiKeyCheckResult fail(int httpStatus, String errorCode, String errorMessage) {
            return new ApiKeyCheckResult(false, httpStatus, errorCode, errorMessage, null);
        }

        public boolean isSuccess() { return success; }
        public int getHttpStatus() { return httpStatus; }
        public String getErrorCode() { return errorCode; }
        public String getErrorMessage() { return errorMessage; }
        public FbsApiKey getKeyEntity() { return keyEntity; }
    }

    /**
     * 时间戳校验结果（#15 安全加固）
     */
    public static class TimestampCheckResult {
        private final boolean success;
        private final int httpStatus;
        private final String errorCode;
        private final String errorMessage;

        private TimestampCheckResult(boolean success, int httpStatus, String errorCode, String errorMessage) {
            this.success = success;
            this.httpStatus = httpStatus;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }

        public static TimestampCheckResult success() {
            return new TimestampCheckResult(true, 200, null, null);
        }

        public static TimestampCheckResult fail(int httpStatus, String errorCode, String errorMessage) {
            return new TimestampCheckResult(false, httpStatus, errorCode, errorMessage);
        }

        public boolean isSuccess() { return success; }
        public int getHttpStatus() { return httpStatus; }
        public String getErrorCode() { return errorCode; }
        public String getErrorMessage() { return errorMessage; }
    }

    /**
     * 签名校验结果（#15 安全加固）
     */
    public static class SignatureCheckResult {
        private final boolean success;
        private final int httpStatus;
        private final String errorCode;
        private final String errorMessage;

        private SignatureCheckResult(boolean success, int httpStatus, String errorCode, String errorMessage) {
            this.success = success;
            this.httpStatus = httpStatus;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }

        public static SignatureCheckResult success() {
            return new SignatureCheckResult(true, 200, null, null);
        }

        public static SignatureCheckResult fail(int httpStatus, String errorCode, String errorMessage) {
            return new SignatureCheckResult(false, httpStatus, errorCode, errorMessage);
        }

        public boolean isSuccess() { return success; }
        public int getHttpStatus() { return httpStatus; }
        public String getErrorCode() { return errorCode; }
        public String getErrorMessage() { return errorMessage; }
    }
}
