package com.wx.fbsir.business.fbs.service;

import com.wx.fbsir.business.fbs.service.impl.RightsCheckServiceImpl;
import com.wx.fbsir.business.fbs.domain.entity.FbsAuthCode;
import com.wx.fbsir.business.fbs.domain.entity.FbsScenePack;
import com.wx.fbsir.business.fbs.domain.entity.FbsUserPack;
import com.wx.fbsir.business.fbs.dto.ComprehensiveRightsResult;
import com.wx.fbsir.business.fbs.dto.RightsCheckResult;
import com.wx.fbsir.business.fbs.mapper.FbsAuthCodeMapper;
import com.wx.fbsir.business.fbs.mapper.FbsScenePackMapper;
import com.wx.fbsir.business.fbs.mapper.FbsUserPackMapper;
import com.wx.fbsir.business.point.domain.PointsRule;
import com.wx.fbsir.business.point.mapper.PointsRuleMapper;
import com.wx.fbsir.business.point.service.IPointsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * RightsCheckService 单元测试
 *
 * 覆盖 tasks.md §6.1 全部用例（共10个）
 *
 * @author wxfbsir
 * @date 2026-04-08
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("权益校验服务测试")
class RightsCheckServiceTest {

    @Mock
    private FbsScenePackMapper scenePackMapper;

    @Mock
    private FbsUserPackMapper userPackMapper;

    @Mock
    private FbsAuthCodeMapper authCodeMapper;

    @Mock
    private PointsRuleMapper pointsRuleMapper;

    @Mock
    private IPointsService pointsService;

    @InjectMocks
    private RightsCheckServiceImpl rightsCheckService;

    // ---- 常量 ----
    private static final Long USER_ID = 1001L;
    private static final Long PACK_ID = 2001L;
    private static final String PACK_CODE = "pack_vip_monthly";
    private static final String AUTH_CODE = "AUTH_TEST_001";
    private static final String RULE_CODE = "rule_pack_vip";

    // ---- Fixture ----
    private FbsScenePack buildPack(int status) {
        FbsScenePack pack = new FbsScenePack();
        pack.setId(PACK_ID);
        pack.setPackCode(PACK_CODE);
        pack.setStatus(status);
        pack.setPointsRuleCode(RULE_CODE);
        return pack;
    }

    private FbsUserPack buildUserPack(int status, Date expiresAt) {
        FbsUserPack up = new FbsUserPack();
        up.setUserId(USER_ID);
        up.setPackId(PACK_ID);
        up.setStatus(status);
        up.setExpiresAt(expiresAt);
        return up;
    }

    private FbsAuthCode buildAuthCode(int available, int status, int activated, int max,
                                      Date deadline) {
        FbsAuthCode code = new FbsAuthCode();
        code.setAuthCode(AUTH_CODE);
        code.setAvailable(available);
        code.setStatus(status);
        code.setActivatedCount(activated);
        code.setMaxActivations(max);
        code.setDeadline(deadline);
        return code;
    }

    private PointsRule buildRule(String status, Integer pointsValue) {
        PointsRule rule = new PointsRule();
        rule.setRuleCode(RULE_CODE);
        rule.setStatus(status);
        rule.setPointsValue(pointsValue);
        return rule;
    }

    // ========================================================================
    // §6.1 checkScenePack 测试
    // ========================================================================

    @Nested
    @DisplayName("§6.1.1-6.1.3 checkScenePack")
    class CheckScenePackTests {

        @Test
        @DisplayName("用户有有效权益 → 通过")
        void userHasValidRights() {
            when(scenePackMapper.selectById(PACK_ID)).thenReturn(buildPack(1));
            when(userPackMapper.selectActiveByUserIdAndPackId(USER_ID, PACK_ID))
                    .thenReturn(buildUserPack(1, null));

            RightsCheckResult result = rightsCheckService.checkScenePack(USER_ID, PACK_ID);

            assertTrue(result.isAllowed());
            assertNull(result.getReason());
        }

        @Test
        @DisplayName("场景包不存在 → Fail-Closed")
        void scenePackNotFound() {
            when(scenePackMapper.selectById(PACK_ID)).thenReturn(null);

            RightsCheckResult result = rightsCheckService.checkScenePack(USER_ID, PACK_ID);

            assertFalse(result.isAllowed());
            assertNotNull(result.getReason());
            assertTrue(result.getReason().contains("不存在"));
        }

        @Test
        @DisplayName("用户无权益 → Fail-Closed")
        void userHasNoRights() {
            when(scenePackMapper.selectById(PACK_ID)).thenReturn(buildPack(1));
            when(userPackMapper.selectActiveByUserIdAndPackId(USER_ID, PACK_ID))
                    .thenReturn(null);

            RightsCheckResult result = rightsCheckService.checkScenePack(USER_ID, PACK_ID);

            assertFalse(result.isAllowed());
            assertNotNull(result.getReason());
        }
    }

    // ========================================================================
    // §6.1 checkAuthCode 测试
    // ========================================================================

    @Nested
    @DisplayName("§6.1.4-6.1.7 checkAuthCode")
    class CheckAuthCodeTests {

        @Test
        @DisplayName("有效码（available=1, status=0, 未过期）→ 通过")
        void validAuthCode() {
            when(authCodeMapper.selectByAuthCode(AUTH_CODE))
                    .thenReturn(buildAuthCode(1, 0, 0, 10, null));

            RightsCheckResult result = rightsCheckService.checkAuthCode(AUTH_CODE);

            assertTrue(result.isAllowed());
            assertNull(result.getReason());
        }

        @Test
        @DisplayName("available=0（禁用）→ Fail-Closed")
        void authCodeDisabled() {
            when(authCodeMapper.selectByAuthCode(AUTH_CODE))
                    .thenReturn(buildAuthCode(0, 0, 0, 10, null));

            RightsCheckResult result = rightsCheckService.checkAuthCode(AUTH_CODE);

            assertFalse(result.isAllowed());
            assertTrue(result.getReason().contains("禁用"));
        }

        @Test
        @DisplayName("已过期 → Fail-Closed")
        void authCodeExpired() {
            when(authCodeMapper.selectByAuthCode(AUTH_CODE))
                    .thenReturn(buildAuthCode(1, 0, 0, 10,
                            new Date(System.currentTimeMillis() - 86400000)));

            RightsCheckResult result = rightsCheckService.checkAuthCode(AUTH_CODE);

            assertFalse(result.isAllowed());
            assertTrue(result.getReason().contains("过期"));
        }

        @Test
        @DisplayName("次数已用尽 → Fail-Closed")
        void authCodeExhausted() {
            // status=2 时，实现先检查 status，返回"授权码已用尽"
            when(authCodeMapper.selectByAuthCode(AUTH_CODE))
                    .thenReturn(buildAuthCode(1, 2, 5, 5, null));

            RightsCheckResult result = rightsCheckService.checkAuthCode(AUTH_CODE);

            assertFalse(result.isAllowed());
            assertTrue(result.getReason().contains("已用尽"));
        }
    }

    // ========================================================================
    // §6.1 comprehensiveCheck 测试
    // ========================================================================

    @Nested
    @DisplayName("§6.1.8-6.1.10 comprehensiveCheck")
    class ComprehensiveCheckTests {

        @BeforeEach
        void setUp() {
            // 注意：comprehensiveCheck 内部会调用 checkPoints → pointsService.getUserPoints
            //         也会调用 checkScenePack → scenePackMapper.selectById
            lenient().when(pointsService.getUserPoints(USER_ID)).thenReturn(1000);
            lenient().when(pointsRuleMapper.selectPointsRuleByRuleCode(RULE_CODE))
                    .thenReturn(buildRule("0", 10));
            lenient().when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(1));
            lenient().when(scenePackMapper.selectById(PACK_ID))
                    .thenReturn(buildPack(1));
            lenient().when(userPackMapper.selectActiveByUserIdAndPackId(USER_ID, PACK_ID))
                    .thenReturn(buildUserPack(1, null));
        }

        @Test
        @DisplayName("全部通过 → pass=true")
        void allChecksPass() {
            ComprehensiveRightsResult result = rightsCheckService.comprehensiveCheck(
                    USER_ID, PACK_CODE, null, "WORKBUDDY", "task-001");

            assertTrue(result.isPass());
            assertEquals(PACK_ID, result.getPackId());
            assertEquals(RULE_CODE, result.getPointsRuleCode());
        }

        @Test
        @DisplayName("场景包失败 → Fail-Closed")
        void scenePackFails() {
            when(scenePackMapper.selectByPackCode(PACK_CODE)).thenReturn(null);

            ComprehensiveRightsResult result = rightsCheckService.comprehensiveCheck(
                    USER_ID, PACK_CODE, null, "WORKBUDDY", "task-001");

            assertFalse(result.isPass());
            assertNotNull(result.getFailReason());
            assertTrue(result.getFailReason().contains("场景包"));
        }

        @Test
        @DisplayName("积分不足 → Fail-ClOSED")
        void pointsInsufficient() {
            // 覆盖 setUp 的默认 mock（1000积分），改为5积分（不足10）
            when(pointsService.getUserPoints(USER_ID)).thenReturn(5);

            ComprehensiveRightsResult result = rightsCheckService.comprehensiveCheck(
                    USER_ID, PACK_CODE, null, "WORKBUDDY", "task-001");

            assertFalse(result.isPass());
            assertNotNull(result.getFailReason());
            assertTrue(result.getFailReason().contains("积分"));
        }
    }
}
