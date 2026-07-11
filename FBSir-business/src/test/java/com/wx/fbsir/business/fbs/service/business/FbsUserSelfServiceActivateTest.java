package com.wx.fbsir.business.fbs.service.business;

import com.wx.fbsir.business.fbs.domain.entity.FbsAuthCode;
import com.wx.fbsir.business.fbs.domain.entity.FbsScenePack;
import com.wx.fbsir.business.fbs.dto.self.AuthCodeActivateRequestDTO;
import com.wx.fbsir.business.fbs.dto.self.AuthCodeActivateResponseDTO;
import com.wx.fbsir.business.fbs.mapper.FbsAuthCodeMapper;
import com.wx.fbsir.business.fbs.mapper.FbsScenePackMapper;
import com.wx.fbsir.business.fbs.mapper.FbsUserPackMapper;
import com.wx.fbsir.business.fbs.service.AuthCodeService;
import com.wx.fbsir.business.fbs.service.business.impl.FbsUserSelfServiceBusinessServiceImpl;
import com.wx.fbsir.common.core.domain.model.LoginUser;
import com.wx.fbsir.common.exception.ServiceException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * FbsUserSelfService — activateAuthCode 完整单元测试
 *
 * 覆盖：tasks.md §7.2 + 补充边界用例
 * - §7.2.1 正常激活成功返回 HTTP 200，msg=激活成功
 * - §7.2.2 重复激活（用户已有该包权益）返回 409 + "您已拥有该场景包权益，无法使用其他授权码重复激活"
 * - §7.2.3 授权码不存在返回 400 + 中文消息
 * - §7.2.4 授权码已禁用返回 400 + "授权码已禁用"
 * - §7.2.5 授权码已撤销返回 400 + "授权码已撤销"
 * - §7.2.6 授权码已过期返回 400 + "授权码已过期"
 * - §7.2.7 授权码次数用尽返回 400 + "授权码激活次数已用尽"
 * - §7.2.8 未登录抛 ServiceException(401)
 * - §7.2.9 authCode 为空字符串返回 400
 * - 补充1: authCode 为 null 返回 400
 * - 补充2: targetType 非 SCENE_PACK 返回 400
 * - 补充3: targetId 为 null 返回 400
 * - 补充4: failReason 兜底映射
 * - 补充5: 激活成功时 expiresAt 为 null（永不过期）
 * - 补充6: 禁用码+用户已有权益 → 仍应返回"已禁用"而非幂等成功
 * - 补充7: 撤销码+用户已有权益 → 仍应返回"已撤销"而非幂等成功
 *
 * @author FBSir
 * @date 2026-04-11
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("用户自助 — activateAuthCode")
class FbsUserSelfServiceActivateTest {

    @Mock
    private FbsUserPackMapper userPackMapper;
    @Mock
    private FbsAuthCodeMapper authCodeMapper;
    @Mock
    private FbsScenePackMapper scenePackMapper;
    @Mock
    private AuthCodeService authCodeService;

    @InjectMocks
    private FbsUserSelfServiceBusinessServiceImpl service;

    private static final Long TEST_USER_ID = 1001L;
    private static final Long PACK_ID = 2001L;
    private static final Long USER_PACK_ID = 3001L;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    private void mockSecurityContext(Long userId) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(userId);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(loginUser);
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(context);
    }

    private FbsAuthCode buildAuthCode(String authCodeStr, Long targetId, int available, int status) {
        FbsAuthCode code = new FbsAuthCode();
        code.setId(1L);
        code.setAuthCode(authCodeStr);
        code.setTargetType("SCENE_PACK");
        code.setTargetId(targetId);
        code.setAvailable(available);
        code.setStatus(status);
        return code;
    }

    private FbsAuthCode buildAuthCode(String authCodeStr, Long targetId, int available, int status, int activatedCount) {
        FbsAuthCode code = buildAuthCode(authCodeStr, targetId, available, status);
        code.setActivatedCount(activatedCount);
        return code;
    }

    private FbsScenePack buildOnlinePack(Long packId, String packName) {
        FbsScenePack pack = new FbsScenePack();
        pack.setId(packId);
        pack.setPackName(packName);
        pack.setStatus(1); // 已发布
        return pack;
    }

    private FbsScenePack buildOfflinePack(Long packId, String packName) {
        FbsScenePack pack = new FbsScenePack();
        pack.setId(packId);
        pack.setPackName(packName);
        pack.setStatus(2); // 已下架
        return pack;
    }

    // ========================================================================
    // §7.2 activateAuthCode
    // ========================================================================

    @Nested
    @DisplayName("§7.2 activateAuthCode")
    class ActivateTests {

        @Test
        @DisplayName("§7.2.1 正常激活成功返回 HTTP 200，msg=激活成功，字段完整")
        void activateSuccess() {
            mockSecurityContext(TEST_USER_ID);
            String code = "AUTH123456";
            Date expiresAt = new Date(System.currentTimeMillis() + 86400000L);

            when(authCodeMapper.selectByAuthCode(code))
                    .thenReturn(buildAuthCode(code, PACK_ID, 1, 0, 0));
            when(scenePackMapper.selectById(PACK_ID)).thenReturn(buildOnlinePack(PACK_ID, "标准版"));
            when(userPackMapper.selectExistsByUserIdAndPackId(TEST_USER_ID, PACK_ID)).thenReturn(0);
            when(authCodeService.activateAuthCode(eq(code), eq(TEST_USER_ID)))
                    .thenReturn(AuthCodeService.ActivateResult.success(USER_PACK_ID, PACK_ID, "PKG001", expiresAt));

            AuthCodeActivateRequestDTO req = new AuthCodeActivateRequestDTO();
            req.setAuthCode(code);
            AuthCodeActivateResponseDTO resp = service.activateAuthCode(req);

            assertNotNull(resp);
            assertEquals("激活成功", resp.getMsg());
            assertEquals(PACK_ID, resp.getPackId());
            assertEquals("标准版", resp.getPackName());
            assertNotNull(resp.getExpiresAt());
        }

        @Test
        @DisplayName("§7.2.2 重复激活（用户已有该包权益）返回 409，不消耗授权码")
        void idempotentActivate_shouldReject() {
            mockSecurityContext(TEST_USER_ID);
            String code = "AUTH123456";
            when(authCodeMapper.selectByAuthCode(code))
                    .thenReturn(buildAuthCode(code, PACK_ID, 1, 0, 0));
            when(scenePackMapper.selectById(PACK_ID)).thenReturn(buildOnlinePack(PACK_ID, "标准版"));
            when(userPackMapper.selectExistsByUserIdAndPackId(TEST_USER_ID, PACK_ID)).thenReturn(1);

            AuthCodeActivateRequestDTO req = new AuthCodeActivateRequestDTO();
            req.setAuthCode(code);
            ServiceException ex = assertThrows(ServiceException.class,
                    () -> service.activateAuthCode(req));
            assertEquals(409, ex.getCode());
            assertEquals("您已拥有该场景包权益，无法使用其他授权码重复激活", ex.getMessage());
            verify(authCodeService, never()).activateAuthCode(anyString(), anyLong());
        }

        @Test
        @DisplayName("§7.2.3 授权码不存在返回 400")
        void authCodeNotFound() {
            mockSecurityContext(TEST_USER_ID);
            when(authCodeMapper.selectByAuthCode("INVALID")).thenReturn(null);

            AuthCodeActivateRequestDTO req = new AuthCodeActivateRequestDTO();
            req.setAuthCode("INVALID");
            ServiceException ex = assertThrows(ServiceException.class,
                    () -> service.activateAuthCode(req));
            assertEquals(400, ex.getCode());
            assertEquals("授权码不存在", ex.getMessage());
        }

        @Test
        @DisplayName("§7.2.4 授权码已禁用返回 400 + '授权码已禁用'（幂等前拦截）")
        void authCodeDisabled() {
            mockSecurityContext(TEST_USER_ID);
            String code = "DISABLED123";
            when(authCodeMapper.selectByAuthCode(code))
                    .thenReturn(buildAuthCode(code, PACK_ID, 0, 0)); // available=0

            AuthCodeActivateRequestDTO req = new AuthCodeActivateRequestDTO();
            req.setAuthCode(code);
            ServiceException ex = assertThrows(ServiceException.class,
                    () -> service.activateAuthCode(req));
            assertEquals(400, ex.getCode());
            assertEquals("授权码已禁用", ex.getMessage());
            // 不应调用 authCodeService
            verify(authCodeService, never()).activateAuthCode(anyString(), anyLong());
        }

        @Test
        @DisplayName("§7.2.5 授权码已撤销返回 400 + '授权码已撤销'（幂等前拦截）")
        void authCodeRevoked() {
            mockSecurityContext(TEST_USER_ID);
            String code = "REVOKED123";
            when(authCodeMapper.selectByAuthCode(code))
                    .thenReturn(buildAuthCode(code, PACK_ID, 1, 4)); // status=4

            AuthCodeActivateRequestDTO req = new AuthCodeActivateRequestDTO();
            req.setAuthCode(code);
            ServiceException ex = assertThrows(ServiceException.class,
                    () -> service.activateAuthCode(req));
            assertEquals(400, ex.getCode());
            assertEquals("授权码已撤销", ex.getMessage());
            verify(authCodeService, never()).activateAuthCode(anyString(), anyLong());
        }

        @Test
        @DisplayName("§7.2.6 授权码已过期(status=3)返回 400 + '授权码已过期'（幂等前拦截）")
        void authCodeExpired_status3() {
            mockSecurityContext(TEST_USER_ID);
            String code = "EXPIRED123";
            when(authCodeMapper.selectByAuthCode(code))
                    .thenReturn(buildAuthCode(code, PACK_ID, 1, 3)); // status=3

            AuthCodeActivateRequestDTO req = new AuthCodeActivateRequestDTO();
            req.setAuthCode(code);
            ServiceException ex = assertThrows(ServiceException.class,
                    () -> service.activateAuthCode(req));
            assertEquals(400, ex.getCode());
            assertEquals("授权码已过期", ex.getMessage());
            verify(authCodeService, never()).activateAuthCode(anyString(), anyLong());
        }

        @Test
        @DisplayName("§7.2.6b 授权码deadline过期(status=0)外层直接拦截返回'授权码已过期'")
        void authCodeExpired_deadline() {
            mockSecurityContext(TEST_USER_ID);
            String code = "EXPIRED_DEADLINE";
            // status=0 但 deadline 已过 → 外层直接拦截
            FbsAuthCode expiredCode = buildAuthCode(code, PACK_ID, 1, 0, 0);
            expiredCode.setDeadline(new Date(System.currentTimeMillis() - 86400000L)); // 昨天
            when(authCodeMapper.selectByAuthCode(code)).thenReturn(expiredCode);

            AuthCodeActivateRequestDTO req = new AuthCodeActivateRequestDTO();
            req.setAuthCode(code);
            ServiceException ex = assertThrows(ServiceException.class,
                    () -> service.activateAuthCode(req));
            assertEquals(400, ex.getCode());
            assertEquals("授权码已过期", ex.getMessage());
            // 外层拦截，不应走到 authCodeService
            verify(authCodeService, never()).activateAuthCode(anyString(), anyLong());
        }

        @Test
        @DisplayName("§7.2.7 授权码次数用尽(status=2)返回 400 + '授权码激活次数已用尽'（幂等前拦截）")
        void authCodeExhausted() {
            mockSecurityContext(TEST_USER_ID);
            String code = "EXHAUSTED123";
            when(authCodeMapper.selectByAuthCode(code))
                    .thenReturn(buildAuthCode(code, PACK_ID, 1, 2)); // status=2

            AuthCodeActivateRequestDTO req = new AuthCodeActivateRequestDTO();
            req.setAuthCode(code);
            ServiceException ex = assertThrows(ServiceException.class,
                    () -> service.activateAuthCode(req));
            assertEquals(400, ex.getCode());
            assertEquals("授权码激活次数已用尽", ex.getMessage());
            verify(authCodeService, never()).activateAuthCode(anyString(), anyLong());
        }

        @Test
        @DisplayName("§7.2.7b 授权码激活次数上限(status=0但authCodeService拒绝)")
        void authCodeExhausted_viaService() {
            mockSecurityContext(TEST_USER_ID);
            String code = "EXHAUSTED_VIA_SVC";
            when(authCodeMapper.selectByAuthCode(code))
                    .thenReturn(buildAuthCode(code, PACK_ID, 1, 0, 0));
            when(scenePackMapper.selectById(PACK_ID)).thenReturn(buildOnlinePack(PACK_ID, "标准版"));
            when(userPackMapper.selectExistsByUserIdAndPackId(TEST_USER_ID, PACK_ID)).thenReturn(0);
            when(authCodeService.activateAuthCode(eq(code), eq(TEST_USER_ID)))
                    .thenReturn(AuthCodeService.ActivateResult.fail("授权码已达激活次数上限"));

            AuthCodeActivateRequestDTO req = new AuthCodeActivateRequestDTO();
            req.setAuthCode(code);
            ServiceException ex = assertThrows(ServiceException.class,
                    () -> service.activateAuthCode(req));
            assertEquals(400, ex.getCode());
            assertEquals("授权码激活次数已用尽", ex.getMessage());
        }

        @Test
        @DisplayName("§7.2.8 未登录抛 ServiceException(401)")
        void notLoggedIn() {
            AuthCodeActivateRequestDTO req = new AuthCodeActivateRequestDTO();
            req.setAuthCode("AUTH123");

            ServiceException ex = assertThrows(ServiceException.class,
                    () -> service.activateAuthCode(req));
            assertEquals(401, ex.getCode());
        }

        @Test
        @DisplayName("§7.2.9 authCode 为空字符串返回 400")
        void emptyAuthCode() {
            mockSecurityContext(TEST_USER_ID);
            AuthCodeActivateRequestDTO req = new AuthCodeActivateRequestDTO();
            req.setAuthCode("   ");

            ServiceException ex = assertThrows(ServiceException.class,
                    () -> service.activateAuthCode(req));
            assertEquals(400, ex.getCode());
            assertTrue(ex.getMessage().contains("授权码") || ex.getMessage().contains("不能为空"),
                    "actual: " + ex.getMessage());
        }

        @Test
        @DisplayName("补充: authCode 为 null 返回 400")
        void nullAuthCode() {
            mockSecurityContext(TEST_USER_ID);
            AuthCodeActivateRequestDTO req = new AuthCodeActivateRequestDTO();
            req.setAuthCode(null);

            ServiceException ex = assertThrows(ServiceException.class,
                    () -> service.activateAuthCode(req));
            assertEquals(400, ex.getCode());
            assertTrue(ex.getMessage().contains("授权码") || ex.getMessage().contains("不能为空"),
                    "actual: " + ex.getMessage());
        }

        @Test
        @DisplayName("补充: targetType 非 SCENE_PACK 返回 400")
        void targetTypeNotScenePack() {
            mockSecurityContext(TEST_USER_ID);
            String code = "GEN123";
            FbsAuthCode genericCode = new FbsAuthCode();
            genericCode.setId(2L);
            genericCode.setAuthCode(code);
            genericCode.setTargetType("GENERIC");
            genericCode.setTargetId(PACK_ID);
            genericCode.setAvailable(1);
            genericCode.setStatus(0);
            when(authCodeMapper.selectByAuthCode(code)).thenReturn(genericCode);

            AuthCodeActivateRequestDTO req = new AuthCodeActivateRequestDTO();
            req.setAuthCode(code);
            ServiceException ex = assertThrows(ServiceException.class,
                    () -> service.activateAuthCode(req));
            assertEquals(400, ex.getCode());
            assertTrue(ex.getMessage().contains("授权码") || ex.getMessage().contains("无效"),
                    "actual: " + ex.getMessage());
        }

        @Test
        @DisplayName("补充: targetId 为 null 返回 400")
        void targetIdNull() {
            mockSecurityContext(TEST_USER_ID);
            String code = "NOTARGET";
            FbsAuthCode noTargetCode = new FbsAuthCode();
            noTargetCode.setId(3L);
            noTargetCode.setAuthCode(code);
            noTargetCode.setTargetType("SCENE_PACK");
            noTargetCode.setTargetId(null);
            noTargetCode.setAvailable(1);
            noTargetCode.setStatus(0);
            when(authCodeMapper.selectByAuthCode(code)).thenReturn(noTargetCode);

            AuthCodeActivateRequestDTO req = new AuthCodeActivateRequestDTO();
            req.setAuthCode(code);
            ServiceException ex = assertThrows(ServiceException.class,
                    () -> service.activateAuthCode(req));
            assertEquals(400, ex.getCode());
            assertTrue(ex.getMessage().contains("授权码") || ex.getMessage().contains("无效"),
                    "actual: " + ex.getMessage());
        }

        @Test
        @DisplayName("补充: failReason 不在已知关键词 → 兜底返回 400")
        void failReasonFallback() {
            mockSecurityContext(TEST_USER_ID);
            String code = "UNKNOWN123";
            when(authCodeMapper.selectByAuthCode(code))
                    .thenReturn(buildAuthCode(code, PACK_ID, 1, 0, 0));
            when(scenePackMapper.selectById(PACK_ID)).thenReturn(buildOnlinePack(PACK_ID, "标准版"));
            when(userPackMapper.selectExistsByUserIdAndPackId(TEST_USER_ID, PACK_ID)).thenReturn(0);
            when(authCodeService.activateAuthCode(eq(code), eq(TEST_USER_ID)))
                    .thenReturn(AuthCodeService.ActivateResult.fail("未知错误"));

            AuthCodeActivateRequestDTO req = new AuthCodeActivateRequestDTO();
            req.setAuthCode(code);
            ServiceException ex = assertThrows(ServiceException.class,
                    () -> service.activateAuthCode(req));
            assertEquals(400, ex.getCode());
            assertTrue(ex.getMessage().contains("授权码") || ex.getMessage().contains("未知错误"),
                    "actual: " + ex.getMessage());
        }

        @Test
        @DisplayName("补充: 激活成功时 expiresAt 为 null（永不过期场景）")
        void activateSuccess_neverExpires() {
            mockSecurityContext(TEST_USER_ID);
            String code = "NOEXPIRE";

            when(authCodeMapper.selectByAuthCode(code))
                    .thenReturn(buildAuthCode(code, PACK_ID, 1, 0, 0));
            when(scenePackMapper.selectById(PACK_ID)).thenReturn(buildOnlinePack(PACK_ID, "永不过期版"));
            when(userPackMapper.selectExistsByUserIdAndPackId(TEST_USER_ID, PACK_ID)).thenReturn(0);
            when(authCodeService.activateAuthCode(eq(code), eq(TEST_USER_ID)))
                    .thenReturn(AuthCodeService.ActivateResult.success(USER_PACK_ID, PACK_ID, "PKG001", null));

            AuthCodeActivateRequestDTO req = new AuthCodeActivateRequestDTO();
            req.setAuthCode(code);
            AuthCodeActivateResponseDTO resp = service.activateAuthCode(req);

            assertEquals("激活成功", resp.getMsg());
            assertNull(resp.getExpiresAt());
        }

        @Test
        @DisplayName("补充: 禁用码+用户已有权益 → 仍返回'已禁用'而非幂等成功")
        void disabledCode_withExistingPack_shouldNotIdempotent() {
            mockSecurityContext(TEST_USER_ID);
            String code = "DISABLED_BUT_HAS_PACK";
            when(authCodeMapper.selectByAuthCode(code))
                    .thenReturn(buildAuthCode(code, PACK_ID, 0, 0)); // available=0

            // 即使有权益也不应走到幂等路径
            AuthCodeActivateRequestDTO req = new AuthCodeActivateRequestDTO();
            req.setAuthCode(code);
            ServiceException ex = assertThrows(ServiceException.class,
                    () -> service.activateAuthCode(req));
            assertEquals(400, ex.getCode());
            assertEquals("授权码已禁用", ex.getMessage());
            // 确认没有查用户权益
            verify(userPackMapper, never()).selectExistsByUserIdAndPackId(anyLong(), anyLong());
        }

        @Test
        @DisplayName("补充: 撤销码+用户已有权益 → 仍返回'已撤销'而非幂等成功")
        void revokedCode_withExistingPack_shouldNotIdempotent() {
            mockSecurityContext(TEST_USER_ID);
            String code = "REVOKED_BUT_HAS_PACK";
            when(authCodeMapper.selectByAuthCode(code))
                    .thenReturn(buildAuthCode(code, PACK_ID, 1, 4)); // status=4

            AuthCodeActivateRequestDTO req = new AuthCodeActivateRequestDTO();
            req.setAuthCode(code);
            ServiceException ex = assertThrows(ServiceException.class,
                    () -> service.activateAuthCode(req));
            assertEquals(400, ex.getCode());
            assertEquals("授权码已撤销", ex.getMessage());
            verify(userPackMapper, never()).selectExistsByUserIdAndPackId(anyLong(), anyLong());
        }

        @Test
        @DisplayName("补充: 已绑定用户码(status=1, activatedCount>0) → '该授权码已被其他用户绑定'")
        void authCodeAlreadyBound() {
            mockSecurityContext(TEST_USER_ID);
            String code = "ALREADY_BOUND";
            when(authCodeMapper.selectByAuthCode(code))
                    .thenReturn(buildAuthCode(code, PACK_ID, 1, 1, 1)); // status=1, activatedCount=1

            AuthCodeActivateRequestDTO req = new AuthCodeActivateRequestDTO();
            req.setAuthCode(code);
            ServiceException ex = assertThrows(ServiceException.class,
                    () -> service.activateAuthCode(req));
            assertEquals(400, ex.getCode());
            assertEquals("该授权码已被其他用户绑定", ex.getMessage());
            verify(authCodeService, never()).activateAuthCode(anyString(), anyLong());
        }

        @Test
        @DisplayName("补充: 场景包已下架(pack.status=2) → '场景包已下架，授权码无法激活'")
        void scenePackOffline() {
            mockSecurityContext(TEST_USER_ID);
            String code = "PACK_OFFLINE";
            when(authCodeMapper.selectByAuthCode(code))
                    .thenReturn(buildAuthCode(code, PACK_ID, 1, 0, 0));
            when(scenePackMapper.selectById(PACK_ID)).thenReturn(buildOfflinePack(PACK_ID, "已下架包"));

            AuthCodeActivateRequestDTO req = new AuthCodeActivateRequestDTO();
            req.setAuthCode(code);
            ServiceException ex = assertThrows(ServiceException.class,
                    () -> service.activateAuthCode(req));
            assertEquals(400, ex.getCode());
            assertEquals("场景包已下架，授权码无法激活", ex.getMessage());
            verify(authCodeService, never()).activateAuthCode(anyString(), anyLong());
        }

        @Test
        @DisplayName("补充: 场景包不存在(pack=null) → '授权码关联的场景包不存在'")
        void scenePackNotFound() {
            mockSecurityContext(TEST_USER_ID);
            String code = "PACK_DELETED";
            when(authCodeMapper.selectByAuthCode(code))
                    .thenReturn(buildAuthCode(code, PACK_ID, 1, 0, 0));
            when(scenePackMapper.selectById(PACK_ID)).thenReturn(null);

            AuthCodeActivateRequestDTO req = new AuthCodeActivateRequestDTO();
            req.setAuthCode(code);
            ServiceException ex = assertThrows(ServiceException.class,
                    () -> service.activateAuthCode(req));
            assertEquals(400, ex.getCode());
            assertEquals("授权码关联的场景包不存在", ex.getMessage());
            verify(authCodeService, never()).activateAuthCode(anyString(), anyLong());
        }

        @Test
        @DisplayName("补充: 场景包草稿(pack.status=0) → '场景包已下架，授权码无法激活'")
        void scenePackDraft() {
            mockSecurityContext(TEST_USER_ID);
            String code = "PACK_DRAFT";
            when(authCodeMapper.selectByAuthCode(code))
                    .thenReturn(buildAuthCode(code, PACK_ID, 1, 0, 0));
            FbsScenePack draftPack = new FbsScenePack();
            draftPack.setId(PACK_ID);
            draftPack.setPackName("草稿包");
            draftPack.setStatus(0); // 草稿
            when(scenePackMapper.selectById(PACK_ID)).thenReturn(draftPack);

            AuthCodeActivateRequestDTO req = new AuthCodeActivateRequestDTO();
            req.setAuthCode(code);
            ServiceException ex = assertThrows(ServiceException.class,
                    () -> service.activateAuthCode(req));
            assertEquals(400, ex.getCode());
            assertEquals("场景包已下架，授权码无法激活", ex.getMessage());
            verify(authCodeService, never()).activateAuthCode(anyString(), anyLong());
        }
    }
}
