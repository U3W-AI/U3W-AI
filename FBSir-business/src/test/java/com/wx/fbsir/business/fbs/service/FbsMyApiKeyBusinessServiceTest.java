package com.wx.fbsir.business.fbs.service;

import com.wx.fbsir.business.fbs.domain.entity.FbsApiKey;
import com.wx.fbsir.business.fbs.mapper.FbsApiKeyMapper;
import com.wx.fbsir.business.fbs.service.business.impl.FbsApiKeyBusinessServiceImpl;
import com.wx.fbsir.common.core.domain.entity.SysUser;
import com.wx.fbsir.common.core.domain.model.LoginUser;
import com.wx.fbsir.common.exception.ServiceException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 用户侧 API Key 管理单元测试
 *
 * 测试范围：
 * - createByUser：用户创建 API Key（自动绑定当前用户）
 * - listMyKeys：查询当前用户的 Key 列表（脱敏）
 * - toggleStatus：禁用/启用（校验归属）
 * - deleteById：删除（校验归属）
 *
 * @author FBSir
 * @date 2026-04-17
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("用户侧 API Key 管理测试")
class FbsMyApiKeyBusinessServiceTest {

    @Mock
    private FbsApiKeyMapper apiKeyMapper;

    @InjectMocks
    private FbsApiKeyBusinessServiceImpl service;

    private static final Long USER_ID_1 = 1001L;
    private static final Long USER_ID_2 = 2002L;
    private static final String USERNAME_1 = "user1";
    private static final Long KEY_ID_1 = 1L;
    private static final Long KEY_ID_2 = 2L;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ==== Mock SecurityContext ====

    private void mockSecurityContext(Long userId, String username) {
        SysUser user = new SysUser();
        user.setUserName(username);
        
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(userId);
        loginUser.setUser(user);
        
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(loginUser);
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(context);
    }

    // ==== Fixture ====

    private FbsApiKey buildKey(Long id, Long userId, String name, Integer status) {
        FbsApiKey key = new FbsApiKey();
        key.setId(id);
        key.setApiKey("fbs_abc123def456ghi789jkl012mno345pqr");
        key.setUserId(userId);
        key.setName(name);
        key.setStatus(status);
        key.setRateLimitPerMin(60);
        return key;
    }

    // ========================================================================
    // createByUser 测试
    // ========================================================================

    @Nested
    @DisplayName("createByUser - 用户创建 API Key")
    class CreateByUserTests {

        @Test
        @DisplayName("创建成功 - 自动绑定当前用户")
        void createSuccess_autoBindUser() {
            mockSecurityContext(USER_ID_1, USERNAME_1);
            when(apiKeyMapper.selectByApiKey(anyString())).thenReturn(null); // 不冲突
            doAnswer(invocation -> {
                FbsApiKey k = invocation.getArgument(0);
                k.setId(KEY_ID_1);
                return 1;
            }).when(apiKeyMapper).insertApiKey(any(FbsApiKey.class));

            FbsApiKey result = service.createByUser("我的 Skill Key");

            assertNotNull(result);
            assertEquals(USER_ID_1, result.getUserId(), "应自动绑定当前用户");
            assertEquals("我的 Skill Key", result.getName());
            assertNotNull(result.getApiKey());
            assertTrue(result.getApiKey().startsWith("fbs_"));
            assertEquals(1, result.getStatus());

            // 验证 insertApiKey 被调用，且 userId 正确
            ArgumentCaptor<FbsApiKey> captor = ArgumentCaptor.forClass(FbsApiKey.class);
            verify(apiKeyMapper).insertApiKey(captor.capture());
            assertEquals(USER_ID_1, captor.getValue().getUserId());
        }

        @Test
        @DisplayName("返回完整密钥（仅此一次）")
        void createSuccess_returnFullKey() {
            mockSecurityContext(USER_ID_1, USERNAME_1);
            when(apiKeyMapper.selectByApiKey(anyString())).thenReturn(null);
            doAnswer(invocation -> {
                FbsApiKey k = invocation.getArgument(0);
                k.setId(KEY_ID_1);
                return 1;
            }).when(apiKeyMapper).insertApiKey(any(FbsApiKey.class));

            FbsApiKey result = service.createByUser("测试 Key");

            // 返回的 Key 是完整密钥，不是脱敏值
            assertNotNull(result.getApiKey());
            assertTrue(result.getApiKey().length() > 20);
            assertFalse(result.getApiKey().endsWith("****"));
        }
    }

    // ========================================================================
    // listMyKeys 测试
    // ========================================================================

    @Nested
    @DisplayName("listMyKeys - 查询当前用户的 Key 列表")
    class ListMyKeysTests {

        @Test
        @DisplayName("查询成功 - 返回当前用户的 Key 列表")
        void listSuccess_onlyCurrentUser() {
            mockSecurityContext(USER_ID_1, USERNAME_1);
            List<FbsApiKey> keys = Arrays.asList(
                    buildKey(KEY_ID_1, USER_ID_1, "Key1", 1),
                    buildKey(KEY_ID_2, USER_ID_1, "Key2", 0)
            );
            when(apiKeyMapper.selectByUserId(USER_ID_1)).thenReturn(keys);

            List<FbsApiKey> result = service.listMyKeys();

            assertEquals(2, result.size());
            verify(apiKeyMapper).selectByUserId(USER_ID_1);
        }

        @Test
        @DisplayName("查询成功 - Key 已脱敏")
        void listSuccess_keysMasked() {
            mockSecurityContext(USER_ID_1, USERNAME_1);
            FbsApiKey key = buildKey(KEY_ID_1, USER_ID_1, "Key1", 1);
            String originalKey = key.getApiKey();
            when(apiKeyMapper.selectByUserId(USER_ID_1)).thenReturn(Collections.singletonList(key));

            List<FbsApiKey> result = service.listMyKeys();

            assertEquals(1, result.size());
            // 脱敏后的 Key 不等于原始 Key
            assertNotEquals(originalKey, result.get(0).getApiKey());
            // 脱敏格式：前8位 + ****
            assertTrue(result.get(0).getApiKey().endsWith("****"));
        }

        @Test
        @DisplayName("无数据 - 返回空列表")
        void listEmpty() {
            mockSecurityContext(USER_ID_1, USERNAME_1);
            when(apiKeyMapper.selectByUserId(USER_ID_1)).thenReturn(Collections.emptyList());

            List<FbsApiKey> result = service.listMyKeys();

            assertNotNull(result);
            assertEquals(0, result.size());
        }
    }

    // ========================================================================
    // toggleStatus 测试
    // ========================================================================

    @Nested
    @DisplayName("toggleStatus - 禁用/启用 API Key")
    class ToggleStatusTests {

        @Test
        @DisplayName("禁用成功 - 操作自己的 Key")
        void toggleSuccess_ownKey() {
            mockSecurityContext(USER_ID_1, USERNAME_1);
            FbsApiKey key = buildKey(KEY_ID_1, USER_ID_1, "Key1", 1);
            when(apiKeyMapper.selectById(KEY_ID_1)).thenReturn(key);
            when(apiKeyMapper.updateApiKey(any(FbsApiKey.class))).thenReturn(1);

            assertDoesNotThrow(() -> service.toggleStatus(KEY_ID_1, 0));

            ArgumentCaptor<FbsApiKey> captor = ArgumentCaptor.forClass(FbsApiKey.class);
            verify(apiKeyMapper).updateApiKey(captor.capture());
            assertEquals(0, captor.getValue().getStatus());
        }

        @Test
        @DisplayName("启用成功 - 操作自己的 Key")
        void enableSuccess_ownKey() {
            mockSecurityContext(USER_ID_1, USERNAME_1);
            FbsApiKey key = buildKey(KEY_ID_1, USER_ID_1, "Key1", 0);
            when(apiKeyMapper.selectById(KEY_ID_1)).thenReturn(key);
            when(apiKeyMapper.updateApiKey(any(FbsApiKey.class))).thenReturn(1);

            assertDoesNotThrow(() -> service.toggleStatus(KEY_ID_1, 1));

            ArgumentCaptor<FbsApiKey> captor = ArgumentCaptor.forClass(FbsApiKey.class);
            verify(apiKeyMapper).updateApiKey(captor.capture());
            assertEquals(1, captor.getValue().getStatus());
        }

        @Test
        @DisplayName("无权操作 - 操作别人的 Key")
        void toggleFail_notOwnKey() {
            mockSecurityContext(USER_ID_1, USERNAME_1);
            FbsApiKey key = buildKey(KEY_ID_1, USER_ID_2, "Key2", 1); // 属于 user2
            when(apiKeyMapper.selectById(KEY_ID_1)).thenReturn(key);

            ServiceException ex = assertThrows(ServiceException.class, () -> {
                service.toggleStatus(KEY_ID_1, 0);
            });

            assertEquals(403, ex.getCode());
            assertTrue(ex.getMessage().contains("无权"));
            verify(apiKeyMapper, never()).updateApiKey(any());
        }

        @Test
        @DisplayName("Key 不存在 - 抛出异常")
        void toggleFail_notFound() {
            mockSecurityContext(USER_ID_1, USERNAME_1);
            when(apiKeyMapper.selectById(KEY_ID_1)).thenReturn(null);

            ServiceException ex = assertThrows(ServiceException.class, () -> {
                service.toggleStatus(KEY_ID_1, 0);
            });

            assertEquals(404, ex.getCode());
        }
    }

    // ========================================================================
    // deleteById 测试
    // ========================================================================

    @Nested
    @DisplayName("deleteById - 删除 API Key")
    class DeleteByIdTests {

        @Test
        @DisplayName("删除成功 - 删除自己的 Key")
        void deleteSuccess_ownKey() {
            mockSecurityContext(USER_ID_1, USERNAME_1);
            FbsApiKey key = buildKey(KEY_ID_1, USER_ID_1, "Key1", 1);
            when(apiKeyMapper.selectById(KEY_ID_1)).thenReturn(key);
            when(apiKeyMapper.deleteById(KEY_ID_1)).thenReturn(1);

            assertDoesNotThrow(() -> service.deleteById(KEY_ID_1));

            verify(apiKeyMapper).deleteById(KEY_ID_1);
        }

        @Test
        @DisplayName("无权删除 - 删除别人的 Key")
        void deleteFail_notOwnKey() {
            mockSecurityContext(USER_ID_1, USERNAME_1);
            FbsApiKey key = buildKey(KEY_ID_1, USER_ID_2, "Key2", 1); // 属于 user2
            when(apiKeyMapper.selectById(KEY_ID_1)).thenReturn(key);

            ServiceException ex = assertThrows(ServiceException.class, () -> {
                service.deleteById(KEY_ID_1);
            });

            assertEquals(403, ex.getCode());
            assertTrue(ex.getMessage().contains("无权"));
            verify(apiKeyMapper, never()).deleteById(any());
        }

        @Test
        @DisplayName("Key 不存在 - 抛出异常")
        void deleteFail_notFound() {
            mockSecurityContext(USER_ID_1, USERNAME_1);
            when(apiKeyMapper.selectById(KEY_ID_1)).thenReturn(null);

            ServiceException ex = assertThrows(ServiceException.class, () -> {
                service.deleteById(KEY_ID_1);
            });

            assertEquals(404, ex.getCode());
        }
    }
}
