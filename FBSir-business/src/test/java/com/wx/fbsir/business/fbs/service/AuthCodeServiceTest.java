package com.wx.fbsir.business.fbs.service;

import com.wx.fbsir.business.fbs.service.impl.AuthCodeServiceImpl;
import com.wx.fbsir.business.fbs.domain.entity.FbsAuthCode;
import com.wx.fbsir.business.fbs.domain.entity.FbsScenePack;
import com.wx.fbsir.business.fbs.domain.entity.FbsUserPack;
import com.wx.fbsir.business.fbs.domain.enums.PackStatus;
import com.wx.fbsir.business.fbs.dto.RightsCheckResult;
import com.wx.fbsir.business.fbs.mapper.FbsAuthCodeMapper;
import com.wx.fbsir.business.fbs.mapper.FbsScenePackMapper;
import com.wx.fbsir.business.fbs.mapper.FbsUserPackMapper;
import com.wx.fbsir.business.fbs.service.AuthCodeService.ActivateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AuthCodeService 单元测试
 *
 * 覆盖 tasks.md §6.2 全部用例（共5个）
 *
 * @author FBSir
 * @date 2026-04-08
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("授权码激活服务测试")
class AuthCodeServiceTest {

    @Mock
    private RightsCheckService rightsCheckService;

    @Mock
    private FbsAuthCodeMapper authCodeMapper;

    @Mock
    private FbsScenePackMapper scenePackMapper;

    @Mock
    private FbsUserPackMapper userPackMapper;

    @InjectMocks
    private AuthCodeServiceImpl authCodeService;

    // ---- 常量 ----
    private static final Long USER_ID = 1001L;
    private static final Long PACK_ID = 2001L;
    private static final Long CODE_ID = 3001L;
    private static final Long USER_PACK_ID = 4001L;
    private static final String AUTH_CODE_STR = "AUTH_TEST_001";
    private static final String PACK_CODE = "pack_vip_monthly";

    // ---- Fixture ----
    private FbsScenePack buildPack(int status) {
        FbsScenePack pack = new FbsScenePack();
        pack.setId(PACK_ID);
        pack.setPackCode(PACK_CODE);
        pack.setStatus(status);
        pack.setCurrentVersion("1.0.0");
        return pack;
    }

    private FbsAuthCode buildCode(int available, int status, int activated, int max) {
        FbsAuthCode code = new FbsAuthCode();
        code.setId(CODE_ID);
        code.setAuthCode(AUTH_CODE_STR);
        code.setTargetType("SCENE_PACK");
        code.setTargetId(PACK_ID);
        code.setAvailable(available);
        code.setStatus(status);
        code.setActivatedCount(activated);
        code.setMaxActivations(max);
        code.setDeadline(null);
        return code;
    }

    // ========================================================================
    // §6.2 授权码激活测试
    // ========================================================================

    @Nested
    @DisplayName("§6.2.1-6.2.5 activateAuthCode")
    class ActivateAuthCodeTests {

        @Test
        @DisplayName("§6.2.1 正常激活 → 创建 fbs_user_pack")
        void normalActivation() {
            // 预校验通过
            when(rightsCheckService.checkAuthCode(AUTH_CODE_STR))
                    .thenReturn(RightsCheckResult.pass());
            // FOR UPDATE 查询
            when(authCodeMapper.selectByAuthCodeForUpdate(AUTH_CODE_STR))
                    .thenReturn(buildCode(1, 0, 0, 10));
            // 场景包存在
            when(scenePackMapper.selectById(PACK_ID)).thenReturn(buildPack(1));
            when(authCodeMapper.updateActivated(anyLong(), anyInt(), anyInt())).thenReturn(1);
            when(userPackMapper.insertUserPack(any(FbsUserPack.class))).thenReturn(1);

            ActivateResult result = authCodeService.activateAuthCode(AUTH_CODE_STR, USER_ID);

            assertTrue(result.isSuccess());
            assertEquals(PACK_ID, result.getPackId());
            assertEquals(PACK_CODE, result.getPackCode());

            // 验证 fbs_user_pack 创建参数
            ArgumentCaptor<FbsUserPack> captor = ArgumentCaptor.forClass(FbsUserPack.class);
            verify(userPackMapper).insertUserPack(captor.capture());
            FbsUserPack savedPack = captor.getValue();
            assertEquals(USER_ID, savedPack.getUserId());
            assertEquals(PACK_ID, savedPack.getPackId());
            assertEquals(3, savedPack.getSourceType()); // source_type=3 用户激活
        }

        @Test
        @DisplayName("§6.2.2 最后一次激活 → status 更新为已用尽(2)")
        void lastActivationExhausted() {
            when(rightsCheckService.checkAuthCode(AUTH_CODE_STR))
                    .thenReturn(RightsCheckResult.pass());
            // 当前已激活1次，本次是最后一次
            when(authCodeMapper.selectByAuthCodeForUpdate(AUTH_CODE_STR))
                    .thenReturn(buildCode(1, 1, 1, 2)); // activated=1, max=2, 本次后=2=已用尽
            when(scenePackMapper.selectById(PACK_ID)).thenReturn(buildPack(1));
            when(authCodeMapper.updateActivated(anyLong(), anyInt(), anyInt())).thenReturn(1);
            when(userPackMapper.insertUserPack(any(FbsUserPack.class))).thenReturn(1);

            ActivateResult result = authCodeService.activateAuthCode(AUTH_CODE_STR, USER_ID);

            assertTrue(result.isSuccess());

            // 验证 status=2（已用尽）
            ArgumentCaptor<Integer> statusCaptor = ArgumentCaptor.forClass(Integer.class);
            verify(authCodeMapper).updateActivated(eq(CODE_ID), statusCaptor.capture(), eq(2));
            assertEquals(2, statusCaptor.getValue());
        }

        @Test
        @DisplayName("§6.2.3 已禁用(available=0) → Fail-Closed")
        void disabledAuthCode() {
            when(rightsCheckService.checkAuthCode(AUTH_CODE_STR))
                    .thenReturn(RightsCheckResult.fail("授权码已禁用"));

            ActivateResult result = authCodeService.activateAuthCode(AUTH_CODE_STR, USER_ID);

            assertFalse(result.isSuccess());
            assertTrue(result.getFailReason().contains("禁用"));
            verify(authCodeMapper, never()).updateActivated(anyLong(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("§6.2.4 过期 → Fail-Closed")
        void expiredAuthCode() {
            when(rightsCheckService.checkAuthCode(AUTH_CODE_STR))
                    .thenReturn(RightsCheckResult.fail("授权码已过期"));

            ActivateResult result = authCodeService.activateAuthCode(AUTH_CODE_STR, USER_ID);

            assertFalse(result.isSuccess());
            assertTrue(result.getFailReason().contains("过期"));
            verify(authCodeMapper, never()).updateActivated(anyLong(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("§6.2.5 次数超限 → Fail-Closed")
        void exceededMaxActivations() {
            when(rightsCheckService.checkAuthCode(AUTH_CODE_STR))
                    .thenReturn(RightsCheckResult.fail("授权码已达激活次数上限"));

            ActivateResult result = authCodeService.activateAuthCode(AUTH_CODE_STR, USER_ID);

            assertFalse(result.isSuccess());
            assertTrue(result.getFailReason().contains("上限"));
            verify(authCodeMapper, never()).updateActivated(anyLong(), anyInt(), anyInt());
        }
    }
}
