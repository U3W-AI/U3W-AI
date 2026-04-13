package com.wx.fbsir.business.fbs.service;

import com.wx.fbsir.business.fbs.domain.entity.FbsApiKey;
import com.wx.fbsir.business.fbs.mapper.FbsApiKeyMapper;
import com.wx.fbsir.business.fbs.service.FbsApiKeyAuthService.ApiKeyCheckResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * FbsApiKeyAuthService 单元测试
 *
 * @author wxfbsir
 * @date 2026-04-11
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("API Key 认证服务测试")
class FbsApiKeyAuthServiceTest {

    @Mock
    private FbsApiKeyMapper apiKeyMapper;

    @InjectMocks
    private FbsApiKeyAuthService service;

    // ---- 常量 ----
    private static final String VALID_KEY = "fbs_abc123def456ghi789jkl012mno345pqr";
    private static final String DISABLED_KEY = "fbs_disabled_key_1234567890abcdefghij";

    // ---- Fixture ----
    private FbsApiKey buildActiveKey() {
        FbsApiKey key = new FbsApiKey();
        key.setId(1L);
        key.setApiKey(VALID_KEY);
        key.setName("测试Key");
        key.setRateLimitPerMin(60);
        key.setStatus(1); // 启用
        return key;
    }

    private FbsApiKey buildDisabledKey() {
        FbsApiKey key = new FbsApiKey();
        key.setId(2L);
        key.setApiKey(DISABLED_KEY);
        key.setName("已禁用Key");
        key.setRateLimitPerMin(60);
        key.setStatus(0); // 禁用
        return key;
    }

    // ========================================================================
    // §6.1.1 API Key 校验逻辑测试
    // ========================================================================

    @Nested
    @DisplayName("§6.1.1 API Key 校验逻辑")
    class ApiKeyCheckTests {

        @Test
        @DisplayName("§6.1.1.1 有效 Key — 校验通过")
        void validKey_shouldPass() {
            when(apiKeyMapper.selectActiveByKey(VALID_KEY)).thenReturn(buildActiveKey());

            ApiKeyCheckResult result = service.checkApiKey(VALID_KEY);

            assertTrue(result.isSuccess());
            assertEquals(200, result.getHttpStatus());
            assertNotNull(result.getKeyEntity());
            assertEquals(VALID_KEY, result.getKeyEntity().getApiKey());
        }

        @Test
        @DisplayName("§6.1.1.2 Key 为 null — 返回 401 INVALID")
        void nullKey_shouldReturn401() {
            ApiKeyCheckResult result = service.checkApiKey(null);

            assertFalse(result.isSuccess());
            assertEquals(401, result.getHttpStatus());
            assertEquals("SKILL_API_KEY_INVALID", result.getErrorCode());
        }

        @Test
        @DisplayName("§6.1.1.3 Key 为空字符串 — 返回 401 INVALID")
        void emptyKey_shouldReturn401() {
            ApiKeyCheckResult result = service.checkApiKey("");

            assertFalse(result.isSuccess());
            assertEquals(401, result.getHttpStatus());
            assertEquals("SKILL_API_KEY_INVALID", result.getErrorCode());
        }

        @Test
        @DisplayName("§6.1.1.4 Key 为纯空格 — 返回 401 INVALID")
        void blankKey_shouldReturn401() {
            ApiKeyCheckResult result = service.checkApiKey("   ");

            assertFalse(result.isSuccess());
            assertEquals(401, result.getHttpStatus());
            assertEquals("SKILL_API_KEY_INVALID", result.getErrorCode());
        }

        @Test
        @DisplayName("§6.1.1.5 Key 不存在（数据库无记录）— 返回 401 INVALID")
        void nonExistentKey_shouldReturn401() {
            when(apiKeyMapper.selectActiveByKey("fbs_nonexistent")).thenReturn(null);
            when(apiKeyMapper.selectByApiKey("fbs_nonexistent")).thenReturn(null);

            ApiKeyCheckResult result = service.checkApiKey("fbs_nonexistent");

            assertFalse(result.isSuccess());
            assertEquals(401, result.getHttpStatus());
            assertEquals("SKILL_API_KEY_INVALID", result.getErrorCode());
        }

        @Test
        @DisplayName("§6.1.1.6 Key 已禁用 — 返回 403 DISABLED")
        void disabledKey_shouldReturn403() {
            when(apiKeyMapper.selectActiveByKey(DISABLED_KEY)).thenReturn(null);
            when(apiKeyMapper.selectByApiKey(DISABLED_KEY)).thenReturn(buildDisabledKey());

            ApiKeyCheckResult result = service.checkApiKey(DISABLED_KEY);

            assertFalse(result.isSuccess());
            assertEquals(403, result.getHttpStatus());
            assertEquals("SKILL_API_KEY_DISABLED", result.getErrorCode());
        }

        @Test
        @DisplayName("§6.1.1.7 速率限制超限 — 返回 429 RATE_LIMITED")
        void rateLimitExceeded_shouldReturn429() {
            FbsApiKey key = new FbsApiKey();
            key.setId(1L);
            key.setApiKey(VALID_KEY);
            key.setRateLimitPerMin(2); // 设置极低限流
            key.setStatus(1);
            when(apiKeyMapper.selectActiveByKey(VALID_KEY)).thenReturn(key);

            // 前 2 次通过
            ApiKeyCheckResult result1 = service.checkApiKey(VALID_KEY);
            ApiKeyCheckResult result2 = service.checkApiKey(VALID_KEY);
            assertTrue(result1.isSuccess());
            assertTrue(result2.isSuccess());

            // 第 3 次超限
            ApiKeyCheckResult result3 = service.checkApiKey(VALID_KEY);
            assertFalse(result3.isSuccess());
            assertEquals(429, result3.getHttpStatus());
            assertEquals("SKILL_API_RATE_LIMITED", result3.getErrorCode());
        }

        @Test
        @DisplayName("§6.1.1.8 Key 存在但 status 非 0/1 — 返回 401 INVALID")
        void keyWithOtherStatus_shouldReturn401() {
            FbsApiKey key = new FbsApiKey();
            key.setId(3L);
            key.setApiKey("fbs_other_status_key");
            key.setStatus(99); // 异常状态
            when(apiKeyMapper.selectActiveByKey("fbs_other_status_key")).thenReturn(null);
            when(apiKeyMapper.selectByApiKey("fbs_other_status_key")).thenReturn(key);

            ApiKeyCheckResult result = service.checkApiKey("fbs_other_status_key");

            assertFalse(result.isSuccess());
            assertEquals(401, result.getHttpStatus());
            assertEquals("SKILL_API_KEY_INVALID", result.getErrorCode());
        }

        @Test
        @DisplayName("§6.1.1.9 rateLimitPerMin 为 null — 使用默认值 60")
        void nullRateLimit_shouldDefaultTo60() {
            FbsApiKey key = new FbsApiKey();
            key.setId(1L);
            key.setApiKey(VALID_KEY);
            key.setRateLimitPerMin(null); // null → 默认 60
            key.setStatus(1);
            when(apiKeyMapper.selectActiveByKey(VALID_KEY)).thenReturn(key);

            ApiKeyCheckResult result = service.checkApiKey(VALID_KEY);

            assertTrue(result.isSuccess());
        }
    }
}
