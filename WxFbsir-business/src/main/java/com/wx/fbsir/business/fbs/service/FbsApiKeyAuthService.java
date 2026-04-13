package com.wx.fbsir.business.fbs.service;

import com.wx.fbsir.business.fbs.domain.entity.FbsApiKey;
import com.wx.fbsir.business.fbs.mapper.FbsApiKeyMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

        // 5. 通过
        return ApiKeyCheckResult.success(keyEntity);
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
}
