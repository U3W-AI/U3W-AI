package com.wx.fbsir.business.fbs.service;

import com.wx.fbsir.business.fbs.domain.entity.*;
import com.wx.fbsir.business.fbs.dto.ComprehensiveRightsResult;
import com.wx.fbsir.business.fbs.dto.RightsCheckResult;
import com.wx.fbsir.business.fbs.mapper.*;
import com.wx.fbsir.business.fbs.service.impl.RightsCheckServiceImpl;
import com.wx.fbsir.business.point.domain.PointsRule;
import com.wx.fbsir.business.point.mapper.PointsRuleMapper;
import com.wx.fbsir.business.point.service.IPointsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RightsCheckService 单元测试
 *
 * 覆盖 tasks.md §6.1 全部用例（含企业路径扩展 §6.2）
 *
 * @author FBSir
 * @date 2026-04-08
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("权益校验服务测试")
class RightsCheckServiceTest {

    @Mock private FbsScenePackMapper scenePackMapper;
    @Mock private FbsUserPackMapper userPackMapper;
    @Mock private FbsAuthCodeMapper authCodeMapper;
    @Mock private PointsRuleMapper pointsRuleMapper;
    @Mock private IPointsService pointsService;
    // 企业 Mapper（required=false，无表时为 null）
    @Mock private FbsEnterpriseMapper enterpriseMapper;
    @Mock private FbsEnterprisePackMapper enterprisePackMapper;
    @Mock private FbsEnterpriseMemberMapper enterpriseMemberMapper;
    @Mock private FbsMemberPackMapper memberPackMapper;

    @InjectMocks
    private RightsCheckServiceImpl rightsCheckService;

    // ---- 常量 ----
    private static final Long USER_ID = 1001L;
    private static final Long ENT_ID = 5001L;
    private static final Long MEMBER_ID = 6001L;
    private static final Long EPACK_ID = 7001L;
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

    private FbsEnterprise buildEnterprise(int status) {
        FbsEnterprise e = new FbsEnterprise();
        e.setId(ENT_ID);
        e.setStatus(status);
        return e;
    }

    private FbsEnterpriseMember buildMember(int status) {
        FbsEnterpriseMember m = new FbsEnterpriseMember();
        m.setId(MEMBER_ID);
        m.setEnterpriseId(ENT_ID);
        m.setUserId(USER_ID);
        m.setStatus(status);
        return m;
    }

    private FbsEnterprisePack buildEnterprisePack(int status, int packQuota, int usedQuota) {
        FbsEnterprisePack ep = new FbsEnterprisePack();
        ep.setId(EPACK_ID);
        ep.setEnterpriseId(ENT_ID);
        ep.setPackId(PACK_ID);
        ep.setStatus(status);
        ep.setPackQuota(packQuota);
        ep.setUsedQuota(usedQuota);
        return ep;
    }

    private FbsMemberPack buildMemberPack(int status) {
        FbsMemberPack mp = new FbsMemberPack();
        mp.setId(1L);
        mp.setMemberId(MEMBER_ID);
        mp.setEnterprisePackId(EPACK_ID);
        mp.setPackId(PACK_ID);
        mp.setStatus(status);
        return mp;
    }

    // ========================================================================
    // §6.1 checkScenePack 测试
    // ========================================================================

    @Nested
    @DisplayName("§6.1.1-6.1.3 checkScenePack（个人路径）")
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
            when(authCodeMapper.selectByAuthCode(AUTH_CODE))
                    .thenReturn(buildAuthCode(1, 2, 5, 5, null));

            RightsCheckResult result = rightsCheckService.checkAuthCode(AUTH_CODE);

            assertFalse(result.isAllowed());
            assertTrue(result.getReason().contains("已用尽"));
        }
    }

    // ========================================================================
    // §6.1 comprehensiveCheck 测试（WORKBUDDY 路径）
    // §6.2 comprehensiveCheck 测试（ENTERPRISE 路径）
    // ========================================================================

    @Nested
    @DisplayName("§6.1.8-6.1.10 comprehensiveCheck（WORKBUDDY）")
    class ComprehensiveCheckPersonalTests {

        @Test
        @DisplayName("WORKBUDDY 全部通过 → pass=true")
        void allChecksPass() {
            // checkScenePack 内部先调 selectById(packId)
            when(scenePackMapper.selectById(PACK_ID)).thenReturn(buildPack(1));
            // comprehensiveCheck 先调 selectByPackCode
            when(scenePackMapper.selectByPackCode(PACK_CODE)).thenReturn(buildPack(1));
            // checkScenePack 调 selectActiveByUserIdAndPackId
            when(userPackMapper.selectActiveByUserIdAndPackId(USER_ID, PACK_ID))
                    .thenReturn(buildUserPack(1, null));
            // pointsRuleMapper 读规则
            when(pointsRuleMapper.selectPointsRuleByRuleCode(RULE_CODE))
                    .thenReturn(buildRule("0", 10));
            // pointsService 查余额
            when(pointsService.getUserPoints(USER_ID)).thenReturn(1000);

            ComprehensiveRightsResult result = rightsCheckService.comprehensiveCheck(
                    USER_ID, PACK_CODE, null, "WORKBUDDY", "task-001");

            assertTrue(result.isPass());
            assertEquals(PACK_ID, result.getPackId());
            assertEquals(RULE_CODE, result.getPointsRuleCode());
        }

        @Test
        @DisplayName("WORKBUDDY 场景包失败 → Fail-Closed")
        void scenePackFails() {
            when(scenePackMapper.selectByPackCode(PACK_CODE)).thenReturn(null);

            ComprehensiveRightsResult result = rightsCheckService.comprehensiveCheck(
                    USER_ID, PACK_CODE, null, "WORKBUDDY", "task-001");

            assertFalse(result.isPass());
            assertNotNull(result.getFailReason());
            assertTrue(result.getFailReason().contains("场景包"));
        }

        @Test
        @DisplayName("WORKBUDDY 积分不足 → Fail-Closed")
        void pointsInsufficient() {
            when(scenePackMapper.selectById(PACK_ID)).thenReturn(buildPack(1));
            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(1));
            when(userPackMapper.selectActiveByUserIdAndPackId(USER_ID, PACK_ID))
                    .thenReturn(buildUserPack(1, null));
            when(pointsRuleMapper.selectPointsRuleByRuleCode(RULE_CODE))
                    .thenReturn(buildRule("0", 10));
            when(pointsService.getUserPoints(USER_ID)).thenReturn(5);

            ComprehensiveRightsResult result = rightsCheckService.comprehensiveCheck(
                    USER_ID, PACK_CODE, null, "WORKBUDDY", "task-001");

            assertFalse(result.isPass());
            assertNotNull(result.getFailReason());
            assertTrue(result.getFailReason().contains("积分"));
        }
    }

    @Nested
    @DisplayName("§6.2.1-6.2.5 comprehensiveCheck（ENTERPRISE 企业配额路径）")
    class ComprehensiveCheckEnterpriseTests {

        @Test
        @DisplayName("ENTERPRISE 企业成员+配额充足 → pass=true，忽略个人积分")
        void enterpriseQuotaSufficient() {
            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(1));
            when(enterpriseMemberMapper.selectActiveByUserId(USER_ID))
                    .thenReturn(Arrays.asList(buildMember(1)));
            when(enterpriseMapper.selectById(ENT_ID))
                    .thenReturn(buildEnterprise(1));
            when(enterprisePackMapper.selectByEnterpriseAndPack(ENT_ID, PACK_ID))
                    .thenReturn(buildEnterprisePack(1, 100, 50));
            // P0-1 新增：成员授权凭证必须存在且 status=1
            when(memberPackMapper.selectActiveByMemberIdAndPackId(MEMBER_ID, PACK_ID))
                    .thenReturn(buildMemberPack(1));

            ComprehensiveRightsResult result = rightsCheckService.comprehensiveCheck(
                    USER_ID, PACK_CODE, null, "ENTERPRISE", "task-001");

            assertTrue(result.isPass());
            assertEquals(PACK_ID, result.getPackId());
            assertNull(result.getPointsRuleCode()); // 企业路径不返回积分规则
        }

        @Test
        @DisplayName("ENTERPRISE 用户不是企业成员 → Fail-Closed（不 fallback 个人路径）")
        void userNotEnterpriseMember() {
            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(1));
            when(enterpriseMemberMapper.selectActiveByUserId(USER_ID))
                    .thenReturn(Arrays.asList()); // 空列表

            ComprehensiveRightsResult result = rightsCheckService.comprehensiveCheck(
                    USER_ID, PACK_CODE, null, "ENTERPRISE", "task-001");

            assertFalse(result.isPass());
            assertNotNull(result.getFailReason());
            assertTrue(result.getFailReason().contains("企业成员"));
        }

        @Test
        @DisplayName("ENTERPRISE 企业已禁用 → Fail-Closed")
        void enterpriseDisabled() {
            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(1));
            when(enterpriseMemberMapper.selectActiveByUserId(USER_ID))
                    .thenReturn(Arrays.asList(buildMember(1)));
            when(enterpriseMapper.selectById(ENT_ID))
                    .thenReturn(buildEnterprise(2)); // status=2 已禁用

            ComprehensiveRightsResult result = rightsCheckService.comprehensiveCheck(
                    USER_ID, PACK_CODE, null, "ENTERPRISE", "task-001");

            assertFalse(result.isPass());
            assertNotNull(result.getFailReason());
            assertTrue(result.getFailReason().contains("禁用"));
        }

        @Test
        @DisplayName("ENTERPRISE 企业未获此场景包授权 → Fail-Closed")
        void enterprisePackNotFound() {
            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(1));
            when(enterpriseMemberMapper.selectActiveByUserId(USER_ID))
                    .thenReturn(Arrays.asList(buildMember(1)));
            when(enterpriseMapper.selectById(ENT_ID))
                    .thenReturn(buildEnterprise(1));
            when(enterprisePackMapper.selectByEnterpriseAndPack(ENT_ID, PACK_ID))
                    .thenReturn(null);

            ComprehensiveRightsResult result = rightsCheckService.comprehensiveCheck(
                    USER_ID, PACK_CODE, null, "ENTERPRISE", "task-001");

            assertFalse(result.isPass());
            assertNotNull(result.getFailReason());
            assertTrue(result.getFailReason().contains("授权"));
        }

        @Test
        @DisplayName("ENTERPRISE 配额已用尽（usedQuota >= packQuota） → Fail-Closed")
        void enterpriseQuotaExhausted() {
            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(1));
            when(enterpriseMemberMapper.selectActiveByUserId(USER_ID))
                    .thenReturn(Arrays.asList(buildMember(1)));
            when(enterpriseMapper.selectById(ENT_ID))
                    .thenReturn(buildEnterprise(1));
            when(enterprisePackMapper.selectByEnterpriseAndPack(ENT_ID, PACK_ID))
                    .thenReturn(buildEnterprisePack(1, 100, 100)); // usedQuota == packQuota
            when(memberPackMapper.selectActiveByMemberIdAndPackId(MEMBER_ID, PACK_ID))
                    .thenReturn(buildMemberPack(1));

            ComprehensiveRightsResult result = rightsCheckService.comprehensiveCheck(
                    USER_ID, PACK_CODE, null, "ENTERPRISE", "task-001");

            assertFalse(result.isPass());
            assertNotNull(result.getFailReason());
            assertTrue(result.getFailReason().contains("配额"));
        }

        @Test
        @DisplayName("ENTERPRISE 成员授权凭证缺失（memberPack=null） → Fail-Closed（P0-1 修复）")
        void enterpriseMemberPackNotFound() {
            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(1));
            when(enterpriseMemberMapper.selectActiveByUserId(USER_ID))
                    .thenReturn(Arrays.asList(buildMember(1)));
            when(enterpriseMapper.selectById(ENT_ID))
                    .thenReturn(buildEnterprise(1));
            when(enterprisePackMapper.selectByEnterpriseAndPack(ENT_ID, PACK_ID))
                    .thenReturn(buildEnterprisePack(1, 100, 50));
            // P0-1：成员授权凭证不存在（未 grant 或已级联撤销）
            when(memberPackMapper.selectActiveByMemberIdAndPackId(MEMBER_ID, PACK_ID))
                    .thenReturn(null);

            ComprehensiveRightsResult result = rightsCheckService.comprehensiveCheck(
                    USER_ID, PACK_CODE, null, "ENTERPRISE", "task-001");

            assertFalse(result.isPass());
            assertNotNull(result.getFailReason());
            assertTrue(result.getFailReason().contains("成员授权"));
        }

        @Test
        @DisplayName("ENTERPRISE 成员授权已失效（memberPack.status=3） → Fail-Closed（P0-1 修复）")
        void enterpriseMemberPackRevoked() {
            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(1));
            when(enterpriseMemberMapper.selectActiveByUserId(USER_ID))
                    .thenReturn(Arrays.asList(buildMember(1)));
            when(enterpriseMapper.selectById(ENT_ID))
                    .thenReturn(buildEnterprise(1));
            when(enterprisePackMapper.selectByEnterpriseAndPack(ENT_ID, PACK_ID))
                    .thenReturn(buildEnterprisePack(1, 100, 50));
            // P0-1：成员授权已被撤销（级联撤销或手动撤销）
            when(memberPackMapper.selectActiveByMemberIdAndPackId(MEMBER_ID, PACK_ID))
                    .thenReturn(buildMemberPack(3)); // status=3 已撤销

            ComprehensiveRightsResult result = rightsCheckService.comprehensiveCheck(
                    USER_ID, PACK_CODE, null, "ENTERPRISE", "task-001");

            assertFalse(result.isPass());
            assertNotNull(result.getFailReason());
            assertTrue(result.getFailReason().contains("失效"));
        }

        // §E4.8: selectActiveByUserId 不返回 status=2 的成员（该方法只筛选 status=1）
        // 测试场景：成员不在企业成员列表中（已移除/不存在）→ 服务返回"不是企业成员"
        @Test
        @DisplayName("§E4.8 comprehensiveCheck ENTERPRISE — 用户不是企业成员，Fail-Closed")
        void enterpriseMemberNotFound() {
            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(1));
            // selectActiveByUserId 只返回 status=1 的成员，成员不存在时返回 null
            when(enterpriseMemberMapper.selectActiveByUserId(USER_ID))
                    .thenReturn(null);

            ComprehensiveRightsResult result = rightsCheckService.comprehensiveCheck(
                    USER_ID, PACK_CODE, null, "ENTERPRISE", "task-001");

            assertFalse(result.isPass());
            assertTrue(result.getFailReason().contains("不是企业成员"));
        }
    }

    @Nested
    @DisplayName("智能机器人精确企业作用域权益快照")
    class SmartBotScopedEnterpriseRightsTests {

        @Test
        @DisplayName("精确企业、成员、用户和企业包一致时通过，且不消费配额")
        void exactScopePassesWithoutInferringAFirstEnterprise() {
            when(scenePackMapper.selectByPackCode(PACK_CODE)).thenReturn(buildPack(1));
            when(enterpriseMemberMapper.selectById(MEMBER_ID)).thenReturn(buildMember(1));
            when(enterpriseMapper.selectById(ENT_ID)).thenReturn(buildEnterprise(1));
            when(enterprisePackMapper.selectByEnterpriseAndPack(ENT_ID, PACK_ID))
                    .thenReturn(buildEnterprisePack(1, 10, 3));
            when(memberPackMapper.selectActiveByMemberIdAndPackId(MEMBER_ID, PACK_ID))
                    .thenReturn(buildMemberPack(1));

            ComprehensiveRightsResult result = rightsCheckService.checkEnterpriseScoped(
                    ENT_ID, MEMBER_ID, USER_ID, PACK_CODE);

            assertTrue(result.isPass());
            assertEquals(PACK_ID, result.getPackId());
            assertNull(result.getPointsRuleCode());
            verify(enterpriseMemberMapper, never()).selectActiveByUserId(anyLong());
            verify(enterprisePackMapper, never()).incrementUsedQuota(anyLong());
            verifyNoInteractions(pointsService);
        }

        @Test
        @DisplayName("同一用户的另一个企业成员不能越权通过")
        void memberMustMatchTheExactTenantAndUserScope() {
            FbsEnterpriseMember member = buildMember(1);
            member.setEnterpriseId(ENT_ID + 1);
            when(scenePackMapper.selectByPackCode(PACK_CODE)).thenReturn(buildPack(1));
            when(enterpriseMemberMapper.selectById(MEMBER_ID)).thenReturn(member);

            ComprehensiveRightsResult result = rightsCheckService.checkEnterpriseScoped(
                    ENT_ID, MEMBER_ID, USER_ID, PACK_CODE);

            assertFalse(result.isPass());
            assertTrue(result.getFailReason().contains("作用域"));
            verify(enterprisePackMapper, never()).selectByEnterpriseAndPack(anyLong(), anyLong());
        }

        @Test
        @DisplayName("成员授权必须属于当前企业包，不能仅按同一 packId 通过")
        void memberAuthorizationMustBelongToTheExactEnterprisePack() {
            FbsMemberPack crossEnterprisePack = buildMemberPack(1);
            crossEnterprisePack.setEnterprisePackId(EPACK_ID + 1);
            when(scenePackMapper.selectByPackCode(PACK_CODE)).thenReturn(buildPack(1));
            when(enterpriseMemberMapper.selectById(MEMBER_ID)).thenReturn(buildMember(1));
            when(enterpriseMapper.selectById(ENT_ID)).thenReturn(buildEnterprise(1));
            when(enterprisePackMapper.selectByEnterpriseAndPack(ENT_ID, PACK_ID))
                    .thenReturn(buildEnterprisePack(1, 10, 3));
            when(memberPackMapper.selectActiveByMemberIdAndPackId(MEMBER_ID, PACK_ID))
                    .thenReturn(crossEnterprisePack);

            ComprehensiveRightsResult result = rightsCheckService.checkEnterpriseScoped(
                    ENT_ID, MEMBER_ID, USER_ID, PACK_CODE);

            assertFalse(result.isPass());
            assertTrue(result.getFailReason().contains("当前企业包"));
        }

        @Test
        @DisplayName("已过期企业包不能作为智能机器人权益快照通过")
        void expiredEnterprisePackFailsClosed() {
            FbsEnterprisePack expired = buildEnterprisePack(1, 10, 3);
            expired.setExpiryTime(new Date(System.currentTimeMillis() - 1_000));
            when(scenePackMapper.selectByPackCode(PACK_CODE)).thenReturn(buildPack(1));
            when(enterpriseMemberMapper.selectById(MEMBER_ID)).thenReturn(buildMember(1));
            when(enterpriseMapper.selectById(ENT_ID)).thenReturn(buildEnterprise(1));
            when(enterprisePackMapper.selectByEnterpriseAndPack(ENT_ID, PACK_ID)).thenReturn(expired);

            ComprehensiveRightsResult result = rightsCheckService.checkEnterpriseScoped(
                    ENT_ID, MEMBER_ID, USER_ID, PACK_CODE);

            assertFalse(result.isPass());
            assertTrue(result.getFailReason().contains("过期"));
            verify(memberPackMapper, never()).selectActiveByMemberIdAndPackId(anyLong(), anyLong());
        }
    }
}
