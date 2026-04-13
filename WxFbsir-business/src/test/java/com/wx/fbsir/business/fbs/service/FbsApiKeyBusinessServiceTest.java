package com.wx.fbsir.business.fbs.service;

import com.wx.fbsir.business.fbs.domain.entity.FbsApiKey;
import com.wx.fbsir.business.fbs.mapper.FbsApiKeyMapper;
import com.wx.fbsir.business.fbs.service.business.impl.FbsApiKeyBusinessServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FbsApiKeyBusinessService 单元测试
 *
 * @author wxfbsir
 * @date 2026-04-11
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("API Key 运营管理服务测试")
class FbsApiKeyBusinessServiceTest {

    @Mock
    private FbsApiKeyMapper apiKeyMapper;

    @InjectMocks
    private FbsApiKeyBusinessServiceImpl service;

    // ---- 常量 ----
    private static final Long KEY_ID = 1L;
    private static final String KEY_NAME = "测试Key";

    // ---- Fixture ----
    private FbsApiKey buildActiveKey() {
        FbsApiKey key = new FbsApiKey();
        key.setId(KEY_ID);
        key.setApiKey("fbs_abc123def456ghi789jkl012mno345pqr");
        key.setName(KEY_NAME);
        key.setPackCode("pack_bookwriter_v2");
        key.setRateLimitPerMin(60);
        key.setStatus(1); // 启用
        return key;
    }

    private FbsApiKey buildDisabledKey() {
        FbsApiKey key = new FbsApiKey();
        key.setId(2L);
        key.setApiKey("fbs_disabled_key_1234567890abcdefghij");
        key.setName("已禁用Key");
        key.setStatus(0); // 禁用
        return key;
    }

    // ========================================================================
    // §6.1.3.1 生成 API Key 测试
    // ========================================================================

    @Nested
    @DisplayName("§6.1.3.1 生成 API Key")
    class GenerateApiKeyTests {

        @Test
        @DisplayName("§6.1.3.1.1 生成成功 — 返回完整 Key（仅此一次）")
        void generateSuccess() {
            when(apiKeyMapper.selectByApiKey(anyString())).thenReturn(null); // 不冲突
            doAnswer(invocation -> {
                FbsApiKey k = invocation.getArgument(0);
                k.setId(KEY_ID); // 模拟自增ID回写
                return 1;
            }).when(apiKeyMapper).insertApiKey(any(FbsApiKey.class));

            FbsApiKey result = service.generateApiKey(KEY_NAME, "pack_bookwriter_v2", 60, "备注");

            assertNotNull(result);
            assertNotNull(result.getApiKey());
            assertTrue(result.getApiKey().startsWith("fbs_"), "API Key 应以 fbs_ 前缀开头");
            assertEquals(1, result.getStatus(), "新生成的 Key 默认启用");
            assertEquals(KEY_NAME, result.getName());

            // 验证 insertApiKey 被调用
            ArgumentCaptor<FbsApiKey> captor = ArgumentCaptor.forClass(FbsApiKey.class);
            verify(apiKeyMapper).insertApiKey(captor.capture());
            assertTrue(captor.getValue().getApiKey().startsWith("fbs_"));
        }

        @Test
        @DisplayName("§6.1.3.1.2 rateLimitPerMin 为 null — 默认 60")
        void generateNullRateLimit() {
            when(apiKeyMapper.selectByApiKey(anyString())).thenReturn(null);
            doAnswer(invocation -> {
                FbsApiKey k = invocation.getArgument(0);
                k.setId(KEY_ID);
                return 1;
            }).when(apiKeyMapper).insertApiKey(any(FbsApiKey.class));

            FbsApiKey result = service.generateApiKey(KEY_NAME, null, null, null);

            assertNotNull(result);
            assertEquals(60, result.getRateLimitPerMin());
        }

        @Test
        @DisplayName("§6.1.3.1.3 packCode 为 null — 生成全局 Key")
        void generateGlobalKey() {
            when(apiKeyMapper.selectByApiKey(anyString())).thenReturn(null);
            doAnswer(invocation -> {
                FbsApiKey k = invocation.getArgument(0);
                k.setId(KEY_ID);
                return 1;
            }).when(apiKeyMapper).insertApiKey(any(FbsApiKey.class));

            FbsApiKey result = service.generateApiKey(KEY_NAME, null, 60, null);

            assertNotNull(result);
            assertNull(result.getPackCode());
        }
    }

    // ========================================================================
    // §6.1.3.2 禁用 API Key 测试
    // ========================================================================

    @Nested
    @DisplayName("§6.1.3.2 禁用 API Key")
    class DisableApiKeyTests {

        @Test
        @DisplayName("§6.1.3.2.1 禁用成功")
        void disableSuccess() {
            when(apiKeyMapper.selectById(KEY_ID)).thenReturn(buildActiveKey());
            when(apiKeyMapper.updateApiKey(any(FbsApiKey.class))).thenReturn(1);

            assertDoesNotThrow(() -> service.disableApiKey(KEY_ID));

            ArgumentCaptor<FbsApiKey> captor = ArgumentCaptor.forClass(FbsApiKey.class);
            verify(apiKeyMapper).updateApiKey(captor.capture());
            assertEquals(0, captor.getValue().getStatus());
        }

        @Test
        @DisplayName("§6.1.3.2.2 Key 不存在 — 抛出异常")
        void disableNotFound() {
            when(apiKeyMapper.selectById(KEY_ID)).thenReturn(null);

            assertThrows(RuntimeException.class, () -> service.disableApiKey(KEY_ID));
            verify(apiKeyMapper, never()).updateApiKey(any());
        }
    }

    // ========================================================================
    // §6.1.3.3 启用 API Key 测试
    // ========================================================================

    @Nested
    @DisplayName("§6.1.3.3 启用 API Key")
    class EnableApiKeyTests {

        @Test
        @DisplayName("§6.1.3.3.1 启用成功")
        void enableSuccess() {
            when(apiKeyMapper.selectById(KEY_ID)).thenReturn(buildDisabledKey());
            when(apiKeyMapper.updateApiKey(any(FbsApiKey.class))).thenReturn(1);

            assertDoesNotThrow(() -> service.enableApiKey(KEY_ID));

            ArgumentCaptor<FbsApiKey> captor = ArgumentCaptor.forClass(FbsApiKey.class);
            verify(apiKeyMapper).updateApiKey(captor.capture());
            assertEquals(1, captor.getValue().getStatus());
        }

        @Test
        @DisplayName("§6.1.3.3.2 Key 不存在 — 抛出异常")
        void enableNotFound() {
            when(apiKeyMapper.selectById(KEY_ID)).thenReturn(null);

            assertThrows(RuntimeException.class, () -> service.enableApiKey(KEY_ID));
            verify(apiKeyMapper, never()).updateApiKey(any());
        }
    }

    // ========================================================================
    // §6.1.3.4 删除 API Key 测试
    // ========================================================================

    @Nested
    @DisplayName("§6.1.3.4 删除 API Key")
    class DeleteApiKeyTests {

        @Test
        @DisplayName("§6.1.3.4.1 删除成功")
        void deleteSuccess() {
            when(apiKeyMapper.deleteById(KEY_ID)).thenReturn(1);

            assertDoesNotThrow(() -> service.deleteApiKey(KEY_ID));
            verify(apiKeyMapper).deleteById(KEY_ID);
        }
    }

    // ========================================================================
    // §6.1.3.5 列表查询测试
    // ========================================================================

    @Nested
    @DisplayName("§6.1.3.5 列表查询（脱敏）")
    class ListApiKeysTests {

        @Test
        @DisplayName("§6.1.3.5.1 列表查询 — Key 已脱敏（前8位+****）")
        void listKeysMasked() {
            FbsApiKey key = buildActiveKey();
            String originalApiKey = key.getApiKey(); // 保存原始值
            when(apiKeyMapper.selectApiKeyList(any(FbsApiKey.class))).thenReturn(Collections.singletonList(key));

            List<FbsApiKey> result = service.listApiKeys(new FbsApiKey());

            assertEquals(1, result.size());
            // 脱敏后的 Key 不等于原始 Key
            assertNotEquals(originalApiKey, result.get(0).getApiKey());
            // 脱敏格式：前8位 + ****
            assertTrue(result.get(0).getApiKey().endsWith("****"));
        }

        @Test
        @DisplayName("§6.1.3.5.2 空列表")
        void listEmpty() {
            when(apiKeyMapper.selectApiKeyList(any(FbsApiKey.class))).thenReturn(Collections.emptyList());

            List<FbsApiKey> result = service.listApiKeys(new FbsApiKey());

            assertTrue(result.isEmpty());
        }
    }

    // ========================================================================
    // §6.1.3.6 详情查询测试
    // ========================================================================

    @Nested
    @DisplayName("§6.1.3.6 详情查询（脱敏）")
    class GetApiKeyByIdTests {

        @Test
        @DisplayName("§6.1.3.6.1 查询成功 — Key 已脱敏")
        void getByIdMasked() {
            FbsApiKey key = buildActiveKey();
            String originalApiKey = key.getApiKey(); // 保存原始值
            when(apiKeyMapper.selectById(KEY_ID)).thenReturn(key);

            FbsApiKey result = service.getApiKeyById(KEY_ID);

            assertNotNull(result);
            assertNotEquals(originalApiKey, result.getApiKey());
            assertTrue(result.getApiKey().endsWith("****"));
        }

        @Test
        @DisplayName("§6.1.3.6.2 Key 不存在 — 返回 null")
        void getByIdNotFound() {
            when(apiKeyMapper.selectById(KEY_ID)).thenReturn(null);

            FbsApiKey result = service.getApiKeyById(KEY_ID);

            assertNull(result);
        }
    }

    // ========================================================================
    // §6.1.3.7 脱敏测试
    // ========================================================================

    @Nested
    @DisplayName("§6.1.3.7 脱敏逻辑")
    class MaskingTests {

        @Test
        @DisplayName("§6.1.3.7.1 正常 Key 脱敏 — 前8位+****")
        void maskNormalKey() {
            FbsApiKey key = new FbsApiKey();
            key.setApiKey("fbs_abc123def456ghi789jkl012mno345pqr");

            String masked = key.getMaskedApiKey();

            assertEquals("fbs_abc1****", masked);
        }

        @Test
        @DisplayName("§6.1.3.7.2 短 Key（<=8位）— 返回 ****")
        void maskShortKey() {
            FbsApiKey key = new FbsApiKey();
            key.setApiKey("fbs_abc");

            String masked = key.getMaskedApiKey();

            assertEquals("****", masked);
        }

        @Test
        @DisplayName("§6.1.3.7.3 Key 为 null — 返回 ****")
        void maskNullKey() {
            FbsApiKey key = new FbsApiKey();
            key.setApiKey(null);

            String masked = key.getMaskedApiKey();

            assertEquals("****", masked);
        }
    }
}
