package com.wx.fbsir.business.fbs.service;

import com.wx.fbsir.business.fbs.domain.entity.FbsApiKey;
import com.wx.fbsir.business.fbs.mapper.FbsApiKeyMapper;
import com.wx.fbsir.business.fbs.service.FbsApiKeyAuthService.ApiKeyCheckResult;
import com.wx.fbsir.business.fbs.service.FbsApiKeyAuthService.TimestampCheckResult;
import com.wx.fbsir.business.fbs.service.FbsApiKeyAuthService.SignatureCheckResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

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
    private static final String VALID_KEY = "fbs_a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2";
    private static final String DISABLED_KEY = "fbs_0000000000000000000000000000000000000000000000000000000000000000";

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
        @DisplayName("§6.1.1.1 有效 Key — 校验通过 + 更新 last_used_at")
        void validKey_shouldPass() {
            when(apiKeyMapper.selectActiveByKey(VALID_KEY)).thenReturn(buildActiveKey());

            ApiKeyCheckResult result = service.checkApiKey(VALID_KEY);

            assertTrue(result.isSuccess());
            assertEquals(200, result.getHttpStatus());
            assertNotNull(result.getKeyEntity());
            assertEquals(VALID_KEY, result.getKeyEntity().getApiKey());
            
            // 验证更新最后使用时间被调用
            verify(apiKeyMapper).updateLastUsedAt(VALID_KEY);
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

    // ========================================================================
    // §6.3.1 时间戳校验逻辑测试（#15 安全加固）
    // ========================================================================

    @Nested
    @DisplayName("§6.3.1 时间戳校验逻辑")
    class TimestampCheckTests {

        @Test
        @DisplayName("§6.3.1.1 有效时间戳 — 校验通过")
        void validTimestamp_shouldPass() {
            String ts = String.valueOf(System.currentTimeMillis());
            TimestampCheckResult result = service.verifyTimestamp(ts);

            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("§6.3.1.2 时间戳为 null — 返回 401 MISSING")
        void nullTimestamp_shouldReturn401() {
            TimestampCheckResult result = service.verifyTimestamp(null);

            assertFalse(result.isSuccess());
            assertEquals(401, result.getHttpStatus());
            assertEquals("SKILL_API_TIMESTAMP_MISSING", result.getErrorCode());
        }

        @Test
        @DisplayName("§6.3.1.3 时间戳为空字符串 — 返回 401 MISSING")
        void emptyTimestamp_shouldReturn401() {
            TimestampCheckResult result = service.verifyTimestamp("");

            assertFalse(result.isSuccess());
            assertEquals(401, result.getHttpStatus());
            assertEquals("SKILL_API_TIMESTAMP_MISSING", result.getErrorCode());
        }

        @Test
        @DisplayName("§6.3.1.4 时间戳非数字 — 返回 401 MISSING")
        void nonNumericTimestamp_shouldReturn401() {
            TimestampCheckResult result = service.verifyTimestamp("not-a-number");

            assertFalse(result.isSuccess());
            assertEquals(401, result.getHttpStatus());
            assertEquals("SKILL_API_TIMESTAMP_MISSING", result.getErrorCode());
        }

        @Test
        @DisplayName("§6.3.1.5 时间戳已过期（超过 5 分钟）— 返回 401 EXPIRED")
        void expiredTimestamp_shouldReturn401() {
            // 10 分钟前
            String ts = String.valueOf(System.currentTimeMillis() - 10 * 60 * 1000);
            TimestampCheckResult result = service.verifyTimestamp(ts);

            assertFalse(result.isSuccess());
            assertEquals(401, result.getHttpStatus());
            assertEquals("SKILL_API_TIMESTAMP_EXPIRED", result.getErrorCode());
        }

        @Test
        @DisplayName("§6.3.1.6 时间戳在窗口边界内（4分59秒前）— 校验通过")
        void nearEdgeTimestamp_shouldPass() {
            // 4 分 50 秒前
            String ts = String.valueOf(System.currentTimeMillis() - 290 * 1000);
            TimestampCheckResult result = service.verifyTimestamp(ts);

            assertTrue(result.isSuccess());
        }
    }

    // ========================================================================
    // §6.3.2 签名校验逻辑测试（#15 安全加固）
    // ========================================================================

    @Nested
    @DisplayName("§6.3.2 签名校验逻辑")
    class SignatureCheckTests {

        /** 辅助：计算正确的 HMAC-SHA256 签名 */
        private String computeHmac(String apiKey, String timestamp, String body) throws Exception {
            String message = timestamp + "\n" + body;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(apiKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(computed.length * 2);
            for (byte b : computed) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }

        @Test
        @DisplayName("§6.3.2.1 正确签名 — 校验通过")
        void validSignature_shouldPass() throws Exception {
            String ts = String.valueOf(System.currentTimeMillis());
            String body = "{\"userId\":1,\"ruleCode\":\"daily_login\",\"changeAmount\":10}";
            String sig = computeHmac(VALID_KEY, ts, body);

            SignatureCheckResult result = service.verifySignature(VALID_KEY, ts, body, sig);

            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("§6.3.2.2 签名为 null — 返回 401 INVALID")
        void nullSignature_shouldReturn401() {
            SignatureCheckResult result = service.verifySignature(VALID_KEY, "1234567890", "{}", null);

            assertFalse(result.isSuccess());
            assertEquals(401, result.getHttpStatus());
            assertEquals("SKILL_API_SIGNATURE_INVALID", result.getErrorCode());
        }

        @Test
        @DisplayName("§6.3.2.3 签名为空字符串 — 返回 401 INVALID")
        void emptySignature_shouldReturn401() {
            SignatureCheckResult result = service.verifySignature(VALID_KEY, "1234567890", "{}", "");

            assertFalse(result.isSuccess());
            assertEquals(401, result.getHttpStatus());
            assertEquals("SKILL_API_SIGNATURE_INVALID", result.getErrorCode());
        }

        @Test
        @DisplayName("§6.3.2.4 签名不匹配 — 返回 401 INVALID")
        void mismatchedSignature_shouldReturn401() {
            SignatureCheckResult result = service.verifySignature(VALID_KEY, "1234567890", "{}", "deadbeef");

            assertFalse(result.isSuccess());
            assertEquals(401, result.getHttpStatus());
            assertEquals("SKILL_API_SIGNATURE_INVALID", result.getErrorCode());
            assertEquals("签名不匹配", result.getErrorMessage());
        }

        @Test
        @DisplayName("§6.3.2.5 body 被篡改 — 签名不匹配")
        void tamperedBody_shouldFail() throws Exception {
            String ts = String.valueOf(System.currentTimeMillis());
            String originalBody = "{\"userId\":1,\"changeAmount\":10}";
            String tamperedBody = "{\"userId\":1,\"changeAmount\":999}";
            String sig = computeHmac(VALID_KEY, ts, originalBody);

            SignatureCheckResult result = service.verifySignature(VALID_KEY, ts, tamperedBody, sig);

            assertFalse(result.isSuccess());
            assertEquals("签名不匹配", result.getErrorMessage());
        }

        @Test
        @DisplayName("§6.3.2.6 空 body — 正确签名可通过")
        void emptyBody_shouldPass() throws Exception {
            String ts = String.valueOf(System.currentTimeMillis());
            String body = "";
            String sig = computeHmac(VALID_KEY, ts, body);

            SignatureCheckResult result = service.verifySignature(VALID_KEY, ts, body, sig);

            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("§6.3.2.7 签名大小写混合 — 自动 trim 后比对失败")
        void mixedCaseSignature_shouldFail() throws Exception {
            String ts = String.valueOf(System.currentTimeMillis());
            String body = "{}";
            String sig = computeHmac(VALID_KEY, ts, body);
            // 篡改一部分字符为大写
            StringBuilder mangledBuilder = new StringBuilder(sig);
            for (int i = 0; i < mangledBuilder.length(); i++) {
                char current = mangledBuilder.charAt(i);
                if (current >= 'a' && current <= 'f') {
                    mangledBuilder.setCharAt(i, Character.toUpperCase(current));
                    break;
                }
            }
            String mangledSig = mangledBuilder.toString();

            SignatureCheckResult result = service.verifySignature(VALID_KEY, ts, body, mangledSig);

            assertFalse(result.isSuccess());
        }
    }
}
