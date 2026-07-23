package com.wx.fbsir.business.fbs.service;

import com.wx.fbsir.business.board.credit.service.SkillConsumeCreditWriter;
import com.wx.fbsir.business.fbs.domain.entity.*;
import com.wx.fbsir.business.fbs.domain.enums.UsageStatus;
import com.wx.fbsir.business.fbs.dto.ComprehensiveRightsResult;
import com.wx.fbsir.business.fbs.dto.ConsumeResult;
import com.wx.fbsir.business.fbs.mapper.*;
import com.wx.fbsir.business.fbs.service.impl.SkillConsumeServiceImpl;
import com.wx.fbsir.business.fbs.service.impl.SkillConsumeLegacyTransactionExecutor;
import com.wx.fbsir.business.fbs.service.RightsCheckService;
import com.wx.fbsir.business.point.domain.PointsRule;
import com.wx.fbsir.business.point.mapper.PointsRuleMapper;
import com.wx.fbsir.business.point.service.IPointsService;
import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SkillConsumeService 单元测试
 *
 * 覆盖 tasks.md §6.3 全部用例（含企业配额路径 §6.4）
 *
 * @author FBSir
 * @date 2026-04-08
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Skill消费服务测试")
class SkillConsumeServiceTest {

    @Mock private FbsScenePackMapper scenePackMapper;
    @Mock private FbsSkillUsageRecordMapper usageRecordMapper;
    @Mock private PointsRuleMapper pointsRuleMapper;
    @Mock private RightsCheckService rightsCheckService;
    @Mock private IPointsService pointsService;
    @Mock private SkillConsumeCreditWriter skillConsumeCreditWriter;
    @Mock private WecomBusinessSyncService wecomBusinessSyncService;
    @Mock private SkillConsumeLegacyTransactionExecutor legacyTransactionExecutor;
    // 企业 Mapper（required=false，无表时为 null）
    @Mock private FbsEnterpriseMapper enterpriseMapper;
    @Mock private FbsEnterpriseMemberMapper enterpriseMemberMapper;
    @Mock private FbsEnterprisePackMapper enterprisePackMapper;
    @Mock private FbsMemberPackMapper memberPackMapper;

    @InjectMocks
    private SkillConsumeServiceImpl skillConsumeService;

    // ---- 常量 ----
    private static final Long USER_ID = 1001L;
    private static final Long ENT_ID = 5001L;
    private static final Long MEMBER_ID = 6001L;
    private static final Long EPACK_ID = 7001L;
    private static final Long PACK_ID = 2001L;
    private static final String PACK_CODE = "pack_vip_monthly";
    private static final String PACK_VERSION = "1.0.0";
    private static final String SKILL_CODE = "fbs-bookwriter";
    private static final String USAGE_RECORD_ID = "task-001-uuid";
    private static final String HOST_TYPE_WB = "WORKBUDDY";
    private static final String HOST_TYPE_ENT = "ENTERPRISE";
    private static final String HOST_SESSION_ID = "session-001";
    private static final String RULE_CODE = "rule_pack_vip";

    @BeforeEach
    void defaultUsageTerminalCompareAndSetSucceeds() {
        lenient().when(legacyTransactionExecutor.execute(any())).thenAnswer(invocation ->
                ((Supplier<ConsumeResult>) invocation.getArgument(0)).get());
        lenient().when(usageRecordMapper.updateStatusByRecordId(anyString(), anyInt(), nullable(String.class)))
                .thenReturn(1);
        ReflectionTestUtils.setField(skillConsumeService, "creditLedgerCandidateEnabled", false);
        ReflectionTestUtils.setField(skillConsumeService, "skillConsumeCreditWriterEnabled", false);
    }

    @Test
    void w4b5eKeepsTheDispatcherNonTransactionalAndTheLegacyExecutorTransactional()
            throws NoSuchMethodException {
        assertNull(SkillConsumeServiceImpl.class.getMethod(
                "consume", Long.class, String.class, String.class, String.class,
                String.class, String.class, String.class).getAnnotation(Transactional.class));
        assertNotNull(SkillConsumeLegacyTransactionExecutor.class.getMethod(
                "execute", Supplier.class).getAnnotation(Transactional.class));
    }

    @Test
    void w4b5eLegacyRouteDriftFailsClosedInsideTheTransactionBeforeWrites() {
        FbsScenePack prepared = buildPack(RULE_CODE);
        FbsScenePack revoked = buildPack(RULE_CODE);
        revoked.setStatus(0);
        when(scenePackMapper.selectByPackCode(PACK_CODE)).thenReturn(prepared, revoked);
        when(pointsRuleMapper.selectPointsRuleByRuleCode(RULE_CODE))
                .thenReturn(buildRule("0", 10));
        when(rightsCheckService.comprehensiveCheck(
                USER_ID, PACK_CODE, null, HOST_TYPE_WB, USAGE_RECORD_ID))
                .thenReturn(ComprehensiveRightsResult.pass(PACK_ID, RULE_CODE, 10));

        ConsumeResult result = skillConsumeService.consume(
                USER_ID, PACK_CODE, SKILL_CODE, USAGE_RECORD_ID,
                HOST_TYPE_WB, HOST_SESSION_ID, null);

        assertFalse(result.isSuccess());
        assertEquals("SKILL_CONSUME_LEGACY_ROUTE_DRIFT", result.getFailReason());
        verify(usageRecordMapper, never()).insertUsageRecord(any());
        verifyNoInteractions(pointsService);
    }

    // ---- Fixture ----
    private FbsScenePack buildPack(String pointsRuleCode) {
        FbsScenePack pack = new FbsScenePack();
        pack.setId(PACK_ID);
        pack.setPackCode(PACK_CODE);
        pack.setStatus(1);
        pack.setCurrentVersion(PACK_VERSION);
        pack.setPointsRuleCode(pointsRuleCode);
        return pack;
    }

    private FbsSkillUsageRecord buildRecord(int status) {
        return buildRecord(status, HOST_TYPE_WB);
    }

    private FbsSkillUsageRecord buildRecord(int status, String hostType) {
        FbsSkillUsageRecord rec = new FbsSkillUsageRecord();
        rec.setUsageRecordId(USAGE_RECORD_ID);
        rec.setUserId(USER_ID);
        rec.setPackId(PACK_ID);
        rec.setSkillCode(SKILL_CODE);
        rec.setHostType(hostType);
        rec.setHostSessionId(HOST_SESSION_ID);
        rec.setStatus(status);
        return rec;
    }

    private PointsRule buildRule(String status, Integer pointsValue) {
        PointsRule rule = new PointsRule();
        rule.setRuleCode(RULE_CODE);
        rule.setStatus(status);
        rule.setPointsValue(pointsValue);
        return rule;
    }

    private void arrangeAuthorizedPaidPersonalConsumption() {
        when(scenePackMapper.selectByPackCode(PACK_CODE)).thenReturn(buildPack(RULE_CODE));
        when(pointsRuleMapper.selectPointsRuleByRuleCode(RULE_CODE)).thenReturn(buildRule("0", 10));
        when(rightsCheckService.comprehensiveCheck(eq(USER_ID), eq(PACK_CODE), isNull(),
                eq(HOST_TYPE_WB), eq(USAGE_RECORD_ID)))
                .thenReturn(ComprehensiveRightsResult.pass(PACK_ID, RULE_CODE, 10));
    }

    private void arrangePaidPersonalConsumption() {
        arrangeAuthorizedPaidPersonalConsumption();
        when(usageRecordMapper.selectByRecordId(USAGE_RECORD_ID)).thenReturn(null);
        when(pointsService.changePoints(USER_ID, RULE_CODE, -10, PACK_ID, USAGE_RECORD_ID))
                .thenReturn(AjaxResult.success("积分扣减成功", 90));
        when(pointsService.getUserPoints(USER_ID)).thenReturn(90);
    }

    @Test
    @DisplayName("W4B5D: two candidate flags route paid personal consumption to v2 without legacy writes")
    void candidateWriterPairRoutesPaidPersonalConsumptionToV2WithoutLegacyWrites() {
        ReflectionTestUtils.setField(skillConsumeService, "creditLedgerCandidateEnabled", true);
        ReflectionTestUtils.setField(skillConsumeService, "skillConsumeCreditWriterEnabled", true);
        arrangeAuthorizedPaidPersonalConsumption();
        when(skillConsumeCreditWriter.consume(
                USER_ID, USAGE_RECORD_ID, PACK_ID, PACK_VERSION, SKILL_CODE, RULE_CODE,
                10, HOST_TYPE_WB, HOST_SESSION_ID))
                .thenReturn(ConsumeResult.success(USAGE_RECORD_ID, 90));

        ConsumeResult result = skillConsumeService.consume(USER_ID, PACK_CODE, SKILL_CODE,
                USAGE_RECORD_ID, HOST_TYPE_WB, HOST_SESSION_ID, null);

        assertTrue(result.isSuccess());
        assertEquals(90, result.getRemainPoints());
        verify(scenePackMapper).selectByPackCode(PACK_CODE);
        verify(pointsRuleMapper).selectPointsRuleByRuleCode(RULE_CODE);
        verify(rightsCheckService).comprehensiveCheck(USER_ID, PACK_CODE, null,
                HOST_TYPE_WB, USAGE_RECORD_ID);
        verify(skillConsumeCreditWriter).consume(
                USER_ID, USAGE_RECORD_ID, PACK_ID, PACK_VERSION, SKILL_CODE, RULE_CODE,
                10, HOST_TYPE_WB, HOST_SESSION_ID);
        verify(usageRecordMapper, never()).selectByRecordId(anyString());
        verify(usageRecordMapper, never()).insertUsageRecord(any());
        verify(usageRecordMapper, never()).updateStatusByRecordId(anyString(), anyInt(), nullable(String.class));
        verifyNoInteractions(pointsService);
        verifyNoInteractions(wecomBusinessSyncService);
    }

    @Test
    @DisplayName("W4B5D: v2 writer failure is returned without falling back to legacy writes")
    void candidateWriterFailureDoesNotFallBackToLegacyWrites() {
        ReflectionTestUtils.setField(skillConsumeService, "creditLedgerCandidateEnabled", true);
        ReflectionTestUtils.setField(skillConsumeService, "skillConsumeCreditWriterEnabled", true);
        arrangeAuthorizedPaidPersonalConsumption();
        when(skillConsumeCreditWriter.consume(
                USER_ID, USAGE_RECORD_ID, PACK_ID, PACK_VERSION, SKILL_CODE, RULE_CODE,
                10, HOST_TYPE_WB, HOST_SESSION_ID))
                .thenReturn(ConsumeResult.fail(
                        USAGE_RECORD_ID, "SKILL_CREDIT_LEDGER_INSUFFICIENT_BALANCE"));

        ConsumeResult result = skillConsumeService.consume(USER_ID, PACK_CODE, SKILL_CODE,
                USAGE_RECORD_ID, HOST_TYPE_WB, HOST_SESSION_ID, null);

        assertFalse(result.isSuccess());
        assertEquals("SKILL_CREDIT_LEDGER_INSUFFICIENT_BALANCE", result.getFailReason());
        verify(skillConsumeCreditWriter).consume(
                USER_ID, USAGE_RECORD_ID, PACK_ID, PACK_VERSION, SKILL_CODE, RULE_CODE,
                10, HOST_TYPE_WB, HOST_SESSION_ID);
        verify(usageRecordMapper, never()).selectByRecordId(anyString());
        verify(usageRecordMapper, never()).insertUsageRecord(any());
        verify(usageRecordMapper, never()).updateStatusByRecordId(anyString(), anyInt(), nullable(String.class));
        verifyNoInteractions(pointsService);
        verifyNoInteractions(wecomBusinessSyncService);
    }

    @Test
    @DisplayName("W4B5D: two flags fail closed when the optional v2 writer bean is unavailable")
    void candidateWriterPairWithoutWriterBeanFailsClosedWithoutLegacyFallback() {
        ReflectionTestUtils.setField(skillConsumeService, "creditLedgerCandidateEnabled", true);
        ReflectionTestUtils.setField(skillConsumeService, "skillConsumeCreditWriterEnabled", true);
        ReflectionTestUtils.setField(skillConsumeService, "skillConsumeCreditWriter", null);
        arrangeAuthorizedPaidPersonalConsumption();

        ConsumeResult result = skillConsumeService.consume(USER_ID, PACK_CODE, SKILL_CODE,
                USAGE_RECORD_ID, HOST_TYPE_WB, HOST_SESSION_ID, null);

        assertFalse(result.isSuccess());
        assertEquals("SKILL_CONSUME_CREDIT_WRITER_NOT_READY", result.getFailReason());
        verify(usageRecordMapper, never()).selectByRecordId(anyString());
        verify(usageRecordMapper, never()).insertUsageRecord(any());
        verifyNoInteractions(pointsService);
        verifyNoInteractions(wecomBusinessSyncService);
    }

    @Test
    @DisplayName("W4B5D: paid candidate normalizes the legacy-compatible host type before v2")
    void candidateWriterPairNormalizesPersonalHostTypeBeforeV2Delegation() {
        ReflectionTestUtils.setField(skillConsumeService, "creditLedgerCandidateEnabled", true);
        ReflectionTestUtils.setField(skillConsumeService, "skillConsumeCreditWriterEnabled", true);
        when(scenePackMapper.selectByPackCode(PACK_CODE)).thenReturn(buildPack(RULE_CODE));
        when(pointsRuleMapper.selectPointsRuleByRuleCode(RULE_CODE)).thenReturn(buildRule("0", 10));
        when(rightsCheckService.comprehensiveCheck(
                USER_ID, PACK_CODE, null, " workbuddy ", USAGE_RECORD_ID))
                .thenReturn(ComprehensiveRightsResult.pass(PACK_ID, RULE_CODE, 10));
        when(skillConsumeCreditWriter.consume(
                USER_ID, USAGE_RECORD_ID, PACK_ID, PACK_VERSION, SKILL_CODE, RULE_CODE,
                10, HOST_TYPE_WB, HOST_SESSION_ID))
                .thenReturn(ConsumeResult.success(USAGE_RECORD_ID, 90));

        ConsumeResult result = skillConsumeService.consume(USER_ID, PACK_CODE, SKILL_CODE,
                USAGE_RECORD_ID, " workbuddy ", HOST_SESSION_ID, null);

        assertTrue(result.isSuccess());
        verify(skillConsumeCreditWriter).consume(
                USER_ID, USAGE_RECORD_ID, PACK_ID, PACK_VERSION, SKILL_CODE, RULE_CODE,
                10, HOST_TYPE_WB, HOST_SESSION_ID);
        verifyNoInteractions(pointsService);
        verifyNoInteractions(wecomBusinessSyncService);
    }

    @Test
    @DisplayName("W4B5D: Integer.MIN_VALUE points rule fails closed instead of becoming free")
    void minimumIntegerPointsRuleFailsClosedBeforeRightsOrWriters() {
        when(scenePackMapper.selectByPackCode(PACK_CODE)).thenReturn(buildPack(RULE_CODE));
        when(pointsRuleMapper.selectPointsRuleByRuleCode(RULE_CODE))
                .thenReturn(buildRule("0", Integer.MIN_VALUE));

        ConsumeResult result = skillConsumeService.consume(USER_ID, PACK_CODE, SKILL_CODE,
                USAGE_RECORD_ID, HOST_TYPE_WB, HOST_SESSION_ID, null);

        assertFalse(result.isSuccess());
        assertEquals("SKILL_POINTS_RULE_AMOUNT_INVALID", result.getFailReason());
        verifyNoInteractions(rightsCheckService);
        verifyNoInteractions(skillConsumeCreditWriter);
        verifyNoInteractions(pointsService);
        verifyNoInteractions(wecomBusinessSyncService);
    }

    @Test
    @DisplayName("W4B4: either candidate flag alone preserves the legacy paid-consumption path")
    void oneCandidateFlagAloneDoesNotActivateOrBlockTheLegacyWriter() {
        ReflectionTestUtils.setField(skillConsumeService, "creditLedgerCandidateEnabled", true);
        ReflectionTestUtils.setField(skillConsumeService, "skillConsumeCreditWriterEnabled", false);
        arrangePaidPersonalConsumption();

        ConsumeResult result = skillConsumeService.consume(USER_ID, PACK_CODE, SKILL_CODE,
                USAGE_RECORD_ID, HOST_TYPE_WB, HOST_SESSION_ID, null);

        assertTrue(result.isSuccess());
        assertEquals(90, result.getRemainPoints());
        verify(pointsService).changePoints(USER_ID, RULE_CODE, -10, PACK_ID, USAGE_RECORD_ID);
        verifyNoInteractions(skillConsumeCreditWriter);
    }

    @Test
    @DisplayName("W4B4: writer flag alone does not activate or block the legacy paid-consumption path")
    void writerFlagAloneDoesNotActivateOrBlockTheLegacyWriter() {
        ReflectionTestUtils.setField(skillConsumeService, "creditLedgerCandidateEnabled", false);
        ReflectionTestUtils.setField(skillConsumeService, "skillConsumeCreditWriterEnabled", true);
        arrangePaidPersonalConsumption();

        ConsumeResult result = skillConsumeService.consume(USER_ID, PACK_CODE, SKILL_CODE,
                USAGE_RECORD_ID, HOST_TYPE_WB, HOST_SESSION_ID, null);

        assertTrue(result.isSuccess());
        assertEquals(90, result.getRemainPoints());
        verify(pointsService).changePoints(USER_ID, RULE_CODE, -10, PACK_ID, USAGE_RECORD_ID);
        verifyNoInteractions(skillConsumeCreditWriter);
    }

    @Test
    @DisplayName("W4B4: two candidate flags do not block a free personal consumption")
    void candidateWriterPairDoesNotBlockFreePersonalConsumption() {
        ReflectionTestUtils.setField(skillConsumeService, "creditLedgerCandidateEnabled", true);
        ReflectionTestUtils.setField(skillConsumeService, "skillConsumeCreditWriterEnabled", true);
        when(scenePackMapper.selectByPackCode(PACK_CODE)).thenReturn(buildPack(null));
        when(rightsCheckService.comprehensiveCheck(USER_ID, PACK_CODE, null,
                HOST_TYPE_WB, USAGE_RECORD_ID))
                .thenReturn(ComprehensiveRightsResult.pass(PACK_ID, null, 0));
        when(usageRecordMapper.selectByRecordId(USAGE_RECORD_ID)).thenReturn(null);
        when(pointsService.getUserPoints(USER_ID)).thenReturn(100);

        ConsumeResult result = skillConsumeService.consume(USER_ID, PACK_CODE, SKILL_CODE,
                USAGE_RECORD_ID, HOST_TYPE_WB, HOST_SESSION_ID, null);

        assertTrue(result.isSuccess());
        assertEquals(100, result.getRemainPoints());
        verify(pointsService, never()).changePoints(anyLong(), anyString(), anyInt(), anyLong(), anyString());
        verifyNoInteractions(skillConsumeCreditWriter);
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
        mp.setPackId(PACK_ID);
        mp.setStatus(status);
        return mp;
    }

    // ========================================================================
    // §6.3 WORKBUDDY 个人路径消费测试
    // ========================================================================

    @Nested
    @DisplayName("§6.3.1-6.3.5 consume（WORKBUDDY 个人积分路径）")
    class ConsumePersonalTests {

        @Test
        @DisplayName("§6.3.1 有积分场景包，扣减成功")
        void consumeWithPointsSuccess() {
            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(RULE_CODE));
            when(pointsRuleMapper.selectPointsRuleByRuleCode(RULE_CODE))
                    .thenReturn(buildRule("0", 10));
            when(rightsCheckService.comprehensiveCheck(eq(USER_ID), eq(PACK_CODE),
                    isNull(), eq(HOST_TYPE_WB), eq(USAGE_RECORD_ID)))
                    .thenReturn(ComprehensiveRightsResult.pass(PACK_ID, RULE_CODE, 10));
            when(usageRecordMapper.selectByRecordId(USAGE_RECORD_ID)).thenReturn(null);
            when(pointsService.changePoints(eq(USER_ID), eq(RULE_CODE), eq(-10),
                    eq(PACK_ID), eq(USAGE_RECORD_ID)))
                    .thenReturn(AjaxResult.success("积分扣减成功"));
            when(pointsService.getUserPoints(USER_ID)).thenReturn(990);

            ConsumeResult result = skillConsumeService.consume(
                    USER_ID, PACK_CODE, SKILL_CODE,
                    USAGE_RECORD_ID, HOST_TYPE_WB, HOST_SESSION_ID, null);

            assertTrue(result.isSuccess());
            assertEquals(USAGE_RECORD_ID, result.getUsageRecordId());
            assertEquals(990, result.getRemainPoints());

            verify(usageRecordMapper).updateStatusByRecordId(
                    eq(USAGE_RECORD_ID), eq(UsageStatus.SUCCESS.getCode()), isNull());
        }

        @Test
        @DisplayName("§6.3.2 免费包(ruleCode=NULL)，跳过积分扣减")
        void consumeFreePack() {
            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(null));
            when(rightsCheckService.comprehensiveCheck(eq(USER_ID), eq(PACK_CODE),
                    isNull(), eq(HOST_TYPE_WB), eq(USAGE_RECORD_ID)))
                    .thenReturn(ComprehensiveRightsResult.pass(PACK_ID, null, 0));
            when(usageRecordMapper.selectByRecordId(USAGE_RECORD_ID)).thenReturn(null);
            when(pointsService.getUserPoints(USER_ID)).thenReturn(1000);

            ConsumeResult result = skillConsumeService.consume(
                    USER_ID, PACK_CODE, SKILL_CODE,
                    USAGE_RECORD_ID, HOST_TYPE_WB, HOST_SESSION_ID, null);

            assertTrue(result.isSuccess());
            assertEquals(1000, result.getRemainPoints());

            verify(pointsService, never())
                    .changePoints(anyLong(), anyString(), anyInt(), anyLong(), anyString());
            verify(usageRecordMapper).updateStatusByRecordId(
                    eq(USAGE_RECORD_ID), eq(UsageStatus.SUCCESS.getCode()), isNull());
        }

        @Test
        @DisplayName("personal paid terminal CAS conflict rejects success")
        void paidConsumeRejectsSuccessWhenUsageTerminalCompareAndSetLost() {
            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(RULE_CODE));
            when(pointsRuleMapper.selectPointsRuleByRuleCode(RULE_CODE))
                    .thenReturn(buildRule("0", 10));
            when(rightsCheckService.comprehensiveCheck(eq(USER_ID), eq(PACK_CODE),
                    isNull(), eq(HOST_TYPE_WB), eq(USAGE_RECORD_ID)))
                    .thenReturn(ComprehensiveRightsResult.pass(PACK_ID, RULE_CODE, 10));
            when(usageRecordMapper.selectByRecordId(USAGE_RECORD_ID)).thenReturn(null);
            when(pointsService.changePoints(eq(USER_ID), eq(RULE_CODE), eq(-10),
                    eq(PACK_ID), eq(USAGE_RECORD_ID)))
                    .thenReturn(AjaxResult.success("points deducted"));
            when(pointsService.getUserPoints(USER_ID)).thenReturn(990);
            when(usageRecordMapper.updateStatusByRecordId(
                    eq(USAGE_RECORD_ID), eq(UsageStatus.SUCCESS.getCode()), isNull()))
                    .thenReturn(0);

            ServiceException exception = assertThrows(ServiceException.class, () ->
                    skillConsumeService.consume(USER_ID, PACK_CODE, SKILL_CODE,
                            USAGE_RECORD_ID, HOST_TYPE_WB, HOST_SESSION_ID, null));

            assertEquals("SKILL_USAGE_RECORD_TERMINAL_CAS_CONFLICT", exception.getMessage());
            assertEquals(409, exception.getCode());
            verify(pointsService).changePoints(USER_ID, RULE_CODE, -10, PACK_ID, USAGE_RECORD_ID);
        }

        @Test
        @DisplayName("free personal terminal CAS conflict rejects success")
        void freeConsumeRejectsSuccessWhenUsageTerminalCompareAndSetLost() {
            when(scenePackMapper.selectByPackCode(PACK_CODE)).thenReturn(buildPack(null));
            when(rightsCheckService.comprehensiveCheck(eq(USER_ID), eq(PACK_CODE),
                    isNull(), eq(HOST_TYPE_WB), eq(USAGE_RECORD_ID)))
                    .thenReturn(ComprehensiveRightsResult.pass(PACK_ID, null, 0));
            when(usageRecordMapper.selectByRecordId(USAGE_RECORD_ID)).thenReturn(null);
            when(pointsService.getUserPoints(USER_ID)).thenReturn(1000);
            when(usageRecordMapper.updateStatusByRecordId(
                    eq(USAGE_RECORD_ID), eq(UsageStatus.SUCCESS.getCode()), isNull()))
                    .thenReturn(0);

            ServiceException exception = assertThrows(ServiceException.class, () ->
                    skillConsumeService.consume(USER_ID, PACK_CODE, SKILL_CODE,
                            USAGE_RECORD_ID, HOST_TYPE_WB, HOST_SESSION_ID, null));

            assertEquals("SKILL_USAGE_RECORD_TERMINAL_CAS_CONFLICT", exception.getMessage());
            assertEquals(409, exception.getCode());
            verify(pointsService, never())
                    .changePoints(anyLong(), anyString(), anyInt(), anyLong(), anyString());
            verifyNoInteractions(skillConsumeCreditWriter);
        }

        @Test
        @DisplayName("§6.3.3 积分不足 → Fail-Closed")
        void consumePointsInsufficient() {
            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(RULE_CODE));
            when(pointsRuleMapper.selectPointsRuleByRuleCode(RULE_CODE))
                    .thenReturn(buildRule("0", 10));
            when(rightsCheckService.comprehensiveCheck(eq(USER_ID), eq(PACK_CODE),
                    isNull(), eq(HOST_TYPE_WB), eq(USAGE_RECORD_ID)))
                    .thenReturn(ComprehensiveRightsResult.fail("用户积分不足"));

            ConsumeResult result = skillConsumeService.consume(
                    USER_ID, PACK_CODE, SKILL_CODE,
                    USAGE_RECORD_ID, HOST_TYPE_WB, HOST_SESSION_ID, null);

            assertFalse(result.isSuccess());
            assertTrue(result.getFailReason().contains("积分不足"));

            verify(pointsService, never())
                    .changePoints(anyLong(), anyString(), anyInt(), anyLong(), anyString());
        }

        @Test
        @DisplayName("§6.3.4 幂等：usageRecordId 重复提交（status=0 → 拒绝）")
        void idempotentDuplicateSubmitInProgress() {
            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(RULE_CODE));
            when(pointsRuleMapper.selectPointsRuleByRuleCode(RULE_CODE))
                    .thenReturn(buildRule("0", 10));
            when(rightsCheckService.comprehensiveCheck(eq(USER_ID), eq(PACK_CODE),
                    isNull(), eq(HOST_TYPE_WB), eq(USAGE_RECORD_ID)))
                    .thenReturn(ComprehensiveRightsResult.pass(PACK_ID, RULE_CODE, 10));
            when(usageRecordMapper.selectByRecordId(USAGE_RECORD_ID))
                    .thenReturn(buildRecord(UsageStatus.IN_PROGRESS.getCode()));

            ConsumeResult result = skillConsumeService.consume(
                    USER_ID, PACK_CODE, SKILL_CODE,
                    USAGE_RECORD_ID, HOST_TYPE_WB, HOST_SESSION_ID, null);

            assertFalse(result.isSuccess());
            assertTrue(result.getFailReason().contains("正在处理中"));

            verify(pointsService, never())
                    .changePoints(anyLong(), anyString(), anyInt(), anyLong(), anyString());
        }

        @Test
        @DisplayName("§6.3.5 幂等：usageRecordId 已成功（直接返回，不重复扣）")
        void idempotentAlreadySuccess() {
            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(RULE_CODE));
            when(pointsRuleMapper.selectPointsRuleByRuleCode(RULE_CODE))
                    .thenReturn(buildRule("0", 10));
            when(rightsCheckService.comprehensiveCheck(eq(USER_ID), eq(PACK_CODE),
                    isNull(), eq(HOST_TYPE_WB), eq(USAGE_RECORD_ID)))
                    .thenReturn(ComprehensiveRightsResult.pass(PACK_ID, RULE_CODE, 10));
            when(usageRecordMapper.selectByRecordId(USAGE_RECORD_ID))
                    .thenReturn(buildRecord(UsageStatus.SUCCESS.getCode()));
            when(pointsService.getUserPoints(USER_ID)).thenReturn(990);

            ConsumeResult result = skillConsumeService.consume(
                    USER_ID, PACK_CODE, SKILL_CODE,
                    USAGE_RECORD_ID, HOST_TYPE_WB, HOST_SESSION_ID, null);

            assertTrue(result.isSuccess());
            assertEquals(990, result.getRemainPoints());

            verify(pointsService, never())
                    .changePoints(anyLong(), anyString(), anyInt(), anyLong(), anyString());
            verify(usageRecordMapper, never())
                    .updateStatusByRecordId(anyString(), anyInt(), any());
        }

        @Test
        @DisplayName("P0：他人 usageRecordId 不得作为本用户幂等成功重放")
        void rejectCrossUserUsageRecordReplay() {
            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(RULE_CODE));
            when(pointsRuleMapper.selectPointsRuleByRuleCode(RULE_CODE))
                    .thenReturn(buildRule("0", 10));
            when(rightsCheckService.comprehensiveCheck(eq(USER_ID), eq(PACK_CODE),
                    isNull(), eq(HOST_TYPE_WB), eq(USAGE_RECORD_ID)))
                    .thenReturn(ComprehensiveRightsResult.pass(PACK_ID, RULE_CODE, 10));
            FbsSkillUsageRecord otherUserRecord = buildRecord(UsageStatus.SUCCESS.getCode());
            otherUserRecord.setUserId(USER_ID + 1);
            when(usageRecordMapper.selectByRecordId(USAGE_RECORD_ID)).thenReturn(otherUserRecord);

            ConsumeResult result = skillConsumeService.consume(
                    USER_ID, PACK_CODE, SKILL_CODE,
                    USAGE_RECORD_ID, HOST_TYPE_WB, HOST_SESSION_ID, null);

            assertFalse(result.isSuccess());
            assertEquals("SKILL_USAGE_RECORD_SCOPE_MISMATCH", result.getFailReason());
            verify(pointsService, never()).getUserPoints(anyLong());
            verify(pointsService, never())
                    .changePoints(anyLong(), anyString(), anyInt(), anyLong(), anyString());
            verify(usageRecordMapper, never())
                    .updateStatusByRecordId(anyString(), anyInt(), any());
        }

        @Test
        @DisplayName("P0：同用户但不同技能范围不得复用 usageRecordId")
        void rejectDifferentSkillUsageRecordReplay() {
            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(RULE_CODE));
            when(pointsRuleMapper.selectPointsRuleByRuleCode(RULE_CODE))
                    .thenReturn(buildRule("0", 10));
            when(rightsCheckService.comprehensiveCheck(eq(USER_ID), eq(PACK_CODE),
                    isNull(), eq(HOST_TYPE_WB), eq(USAGE_RECORD_ID)))
                    .thenReturn(ComprehensiveRightsResult.pass(PACK_ID, RULE_CODE, 10));
            FbsSkillUsageRecord otherSkillRecord = buildRecord(UsageStatus.SUCCESS.getCode());
            otherSkillRecord.setSkillCode("another-skill");
            when(usageRecordMapper.selectByRecordId(USAGE_RECORD_ID)).thenReturn(otherSkillRecord);

            ConsumeResult result = skillConsumeService.consume(
                    USER_ID, PACK_CODE, SKILL_CODE,
                    USAGE_RECORD_ID, HOST_TYPE_WB, HOST_SESSION_ID, null);

            assertFalse(result.isSuccess());
            assertEquals("SKILL_USAGE_RECORD_SCOPE_MISMATCH", result.getFailReason());
            verify(pointsService, never()).getUserPoints(anyLong());
            verify(pointsService, never())
                    .changePoints(anyLong(), anyString(), anyInt(), anyLong(), anyString());
        }

        @Test
        @DisplayName("P1：不同宿主会话不得复用 usageRecordId")
        void rejectDifferentHostSessionUsageRecordReplay() {
            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(RULE_CODE));
            when(pointsRuleMapper.selectPointsRuleByRuleCode(RULE_CODE))
                    .thenReturn(buildRule("0", 10));
            when(rightsCheckService.comprehensiveCheck(eq(USER_ID), eq(PACK_CODE),
                    isNull(), eq(HOST_TYPE_WB), eq(USAGE_RECORD_ID)))
                    .thenReturn(ComprehensiveRightsResult.pass(PACK_ID, RULE_CODE, 10));
            FbsSkillUsageRecord otherSessionRecord = buildRecord(UsageStatus.SUCCESS.getCode());
            otherSessionRecord.setHostSessionId("another-session");
            when(usageRecordMapper.selectByRecordId(USAGE_RECORD_ID)).thenReturn(otherSessionRecord);

            ConsumeResult result = skillConsumeService.consume(
                    USER_ID, PACK_CODE, SKILL_CODE,
                    USAGE_RECORD_ID, HOST_TYPE_WB, HOST_SESSION_ID, null);

            assertFalse(result.isSuccess());
            assertEquals("SKILL_USAGE_RECORD_SCOPE_MISMATCH", result.getFailReason());
            verify(pointsService, never()).getUserPoints(anyLong());
            verify(pointsService, never())
                    .changePoints(anyLong(), anyString(), anyInt(), anyLong(), anyString());
        }
    }

    // ========================================================================
    // §6.4 ENTERPRISE 企业配额路径消费测试
    // ========================================================================

    @Nested
    @DisplayName("§6.4.1-6.4.5 consume（ENTERPRISE 企业配额路径）")
    class ConsumeEnterpriseTests {

        @Test
        @DisplayName("§6.4.1 企业配额不受双开关候选围栏影响 → usedQuota++，remainQuota = packQuota - usedQuota - 1")
        void enterpriseQuotaSufficient() {
            ReflectionTestUtils.setField(skillConsumeService, "creditLedgerCandidateEnabled", true);
            ReflectionTestUtils.setField(skillConsumeService, "skillConsumeCreditWriterEnabled", true);
            // 构建企业包实体（用于验证 incrementUsedQuota 后实体同步更新）
            FbsEnterprisePack ep = buildEnterprisePack(1, 100, 5);
            // P1-1 修复：增量后重新查询的实体（usedQuota 已被 DB 更新为 6）
            FbsEnterprisePack epAfterIncrement = buildEnterprisePack(1, 100, 6);

            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(RULE_CODE));
            when(enterpriseMemberMapper.selectActiveByUserId(USER_ID))
                    .thenReturn(Arrays.asList(buildMember(1)));
            when(enterpriseMapper.selectById(ENT_ID))
                    .thenReturn(buildEnterprise(1));
            when(enterprisePackMapper.selectByEnterpriseAndPack(ENT_ID, PACK_ID))
                    .thenReturn(ep);
            // P0-1：成员授权凭证必须存在
            when(memberPackMapper.selectActiveByMemberIdAndPackId(MEMBER_ID, PACK_ID))
                    .thenReturn(buildMemberPack(1));
            when(usageRecordMapper.selectByRecordId(USAGE_RECORD_ID)).thenReturn(null);
            // 原子 usedQuota++（P0-2：WHERE used_quota < pack_quota 保证不超扣）
            doAnswer(invocation -> {
                ep.setUsedQuota(ep.getUsedQuota() + 1);
                return 1;
            }).when(enterprisePackMapper).incrementUsedQuota(EPACK_ID);
            // P1-1 修复：增量后重新查询，返回增量后的实体
            when(enterprisePackMapper.selectById(EPACK_ID)).thenReturn(epAfterIncrement);

            ConsumeResult result = skillConsumeService.consume(
                    USER_ID, PACK_CODE, SKILL_CODE,
                    USAGE_RECORD_ID, HOST_TYPE_ENT, HOST_SESSION_ID, null);

            assertTrue(result.isSuccess());
            assertEquals(USAGE_RECORD_ID, result.getUsageRecordId());
            assertEquals(94, result.getRemainPoints()); // 100 - 6 = 94（P1-1 修复后准确）

            // 验证 usedQuota 原子++
            verify(enterprisePackMapper).incrementUsedQuota(EPACK_ID);
            // 验证增量后重新查询（P1-1 修复）
            verify(enterprisePackMapper).selectById(EPACK_ID);
            // 验证 usage_record status=1
            verify(usageRecordMapper).updateStatusByRecordId(
                    eq(USAGE_RECORD_ID), eq(UsageStatus.SUCCESS.getCode()), isNull());
            // 验证不扣个人积分
            verify(pointsService, never())
                    .changePoints(anyLong(), anyString(), anyInt(), anyLong(), anyString());
            verifyNoInteractions(skillConsumeCreditWriter);
        }

        @Test
        @DisplayName("§6.4.2 企业配额刚好用尽（usedQuota >= packQuota） → Fail-Closed，写失败记录")
        void enterpriseQuotaExhausted() {
            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(RULE_CODE));
            when(enterpriseMemberMapper.selectActiveByUserId(USER_ID))
                    .thenReturn(Arrays.asList(buildMember(1)));
            when(enterpriseMapper.selectById(ENT_ID))
                    .thenReturn(buildEnterprise(1));
            when(enterprisePackMapper.selectByEnterpriseAndPack(ENT_ID, PACK_ID))
                    .thenReturn(buildEnterprisePack(1, 100, 100)); // 已用尽
            // P0-1：成员授权凭证检查通过
            when(memberPackMapper.selectActiveByMemberIdAndPackId(MEMBER_ID, PACK_ID))
                    .thenReturn(buildMemberPack(1));

            ConsumeResult result = skillConsumeService.consume(
                    USER_ID, PACK_CODE, SKILL_CODE,
                    USAGE_RECORD_ID, HOST_TYPE_ENT, HOST_SESSION_ID, null);

            assertFalse(result.isSuccess());
            assertTrue(result.getFailReason().contains("配额已用尽"));

            // 验证写过失败记录
            verify(usageRecordMapper, atLeastOnce()).insertUsageRecord(any(FbsSkillUsageRecord.class));
            // 验证不调用 incrementUsedQuota（提前失败）
            verify(enterprisePackMapper, never()).incrementUsedQuota(anyLong());
        }

        @Test
        @DisplayName("§6.4.3 用户不是企业成员 → Fail-Closed（不走个人路径 fallback）")
        void userNotEnterpriseMember() {
            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(RULE_CODE));
            when(enterpriseMemberMapper.selectActiveByUserId(USER_ID))
                    .thenReturn(Arrays.asList()); // 空 → 非成员

            ConsumeResult result = skillConsumeService.consume(
                    USER_ID, PACK_CODE, SKILL_CODE,
                    USAGE_RECORD_ID, HOST_TYPE_ENT, HOST_SESSION_ID, null);

            assertFalse(result.isSuccess());
            assertTrue(result.getFailReason().contains("不是企业成员"));

            // 不走个人积分路径
            verify(pointsService, never())
                    .changePoints(anyLong(), anyString(), anyInt(), anyLong(), anyString());
            verify(enterprisePackMapper, never()).incrementUsedQuota(anyLong());
        }

        @Test
        @DisplayName("§6.4.4 企业已禁用（status=2） → Fail-Closed")
        void enterpriseDisabled() {
            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(RULE_CODE));
            when(enterpriseMemberMapper.selectActiveByUserId(USER_ID))
                    .thenReturn(Arrays.asList(buildMember(1)));
            when(enterpriseMapper.selectById(ENT_ID))
                    .thenReturn(buildEnterprise(2)); // 已禁用

            ConsumeResult result = skillConsumeService.consume(
                    USER_ID, PACK_CODE, SKILL_CODE,
                    USAGE_RECORD_ID, HOST_TYPE_ENT, HOST_SESSION_ID, null);

            assertFalse(result.isSuccess());
            assertTrue(result.getFailReason().contains("已禁用"));

            verify(enterprisePackMapper, never()).incrementUsedQuota(anyLong());
        }

        @Test
        @DisplayName("§6.4.5 企业未获此场景包授权 → Fail-Closed")
        void enterprisePackNotAuthorized() {
            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(RULE_CODE));
            when(enterpriseMemberMapper.selectActiveByUserId(USER_ID))
                    .thenReturn(Arrays.asList(buildMember(1)));
            when(enterpriseMapper.selectById(ENT_ID))
                    .thenReturn(buildEnterprise(1));
            when(enterprisePackMapper.selectByEnterpriseAndPack(ENT_ID, PACK_ID))
                    .thenReturn(null); // 未获授权

            ConsumeResult result = skillConsumeService.consume(
                    USER_ID, PACK_CODE, SKILL_CODE,
                    USAGE_RECORD_ID, HOST_TYPE_ENT, HOST_SESSION_ID, null);

            assertFalse(result.isSuccess());
            assertTrue(result.getFailReason().contains("未获"));

            verify(enterprisePackMapper, never()).incrementUsedQuota(anyLong());
        }

        @Test
        @DisplayName("§6.4.6 企业旧记录未持久化租户范围 → 重放失败关闭")
        void enterpriseReplayFailsClosedUntilTenantScopeIsPersisted() {
            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(RULE_CODE));
            when(usageRecordMapper.selectByRecordId(USAGE_RECORD_ID))
                    .thenReturn(buildRecord(UsageStatus.SUCCESS.getCode(), HOST_TYPE_ENT));

            ConsumeResult result = skillConsumeService.consume(
                    USER_ID, PACK_CODE, SKILL_CODE,
                    USAGE_RECORD_ID, HOST_TYPE_ENT, HOST_SESSION_ID, null);

            assertFalse(result.isSuccess());
            assertEquals("SKILL_ENTERPRISE_USAGE_REPLAY_SCOPE_UNVERIFIED", result.getFailReason());

            verifyNoInteractions(enterpriseMemberMapper, enterpriseMapper, memberPackMapper);
            verify(enterprisePackMapper, never()).incrementUsedQuota(anyLong());
            verify(usageRecordMapper, never())
                    .updateStatusByRecordId(anyString(), anyInt(), any());
        }

        @Test
        @DisplayName("P1：同用户存在多个企业成员身份时不选择任意第一个企业")
        void enterpriseMultipleMembershipsFailClosed() {
            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(RULE_CODE));
            FbsEnterpriseMember first = buildMember(1);
            FbsEnterpriseMember second = buildMember(1);
            second.setId(MEMBER_ID + 1);
            second.setEnterpriseId(ENT_ID + 1);
            when(enterpriseMemberMapper.selectActiveByUserId(USER_ID))
                    .thenReturn(Arrays.asList(first, second));

            ConsumeResult result = skillConsumeService.consume(
                    USER_ID, PACK_CODE, SKILL_CODE,
                    USAGE_RECORD_ID, HOST_TYPE_ENT, HOST_SESSION_ID, null);

            assertFalse(result.isSuccess());
            assertEquals("SKILL_ENTERPRISE_MEMBERSHIP_SCOPE_AMBIGUOUS", result.getFailReason());
            verifyNoInteractions(enterpriseMapper, enterprisePackMapper, memberPackMapper);
            verify(usageRecordMapper).selectByRecordId(USAGE_RECORD_ID);
            verify(usageRecordMapper, never()).insertUsageRecord(any());
            verify(usageRecordMapper, never())
                    .updateStatusByRecordId(anyString(), anyInt(), any());
            verifyNoInteractions(pointsService);
        }

        @Test
        @DisplayName("P0：企业配额路径拒绝他人 usageRecordId 重放")
        void enterpriseRejectCrossUserUsageRecordReplay() {
            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(RULE_CODE));
            FbsSkillUsageRecord otherUserRecord =
                    buildRecord(UsageStatus.SUCCESS.getCode(), HOST_TYPE_ENT);
            otherUserRecord.setUserId(USER_ID + 1);
            when(usageRecordMapper.selectByRecordId(USAGE_RECORD_ID)).thenReturn(otherUserRecord);

            ConsumeResult result = skillConsumeService.consume(
                    USER_ID, PACK_CODE, SKILL_CODE,
                    USAGE_RECORD_ID, HOST_TYPE_ENT, HOST_SESSION_ID, null);

            assertFalse(result.isSuccess());
            assertEquals("SKILL_USAGE_RECORD_SCOPE_MISMATCH", result.getFailReason());
            verifyNoInteractions(enterpriseMemberMapper, enterpriseMapper, memberPackMapper);
            verify(enterprisePackMapper, never()).incrementUsedQuota(anyLong());
            verify(usageRecordMapper, never())
                    .updateStatusByRecordId(anyString(), anyInt(), any());
        }

        @Test
        @DisplayName("§6.4.7 并发安全：incrementUsedQuota 返回 0 → 回滚并失败")
        void enterpriseConcurrentExhaustion() {
            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(RULE_CODE));
            when(enterpriseMemberMapper.selectActiveByUserId(USER_ID))
                    .thenReturn(Arrays.asList(buildMember(1)));
            when(enterpriseMapper.selectById(ENT_ID))
                    .thenReturn(buildEnterprise(1));
            when(enterprisePackMapper.selectByEnterpriseAndPack(ENT_ID, PACK_ID))
                    .thenReturn(buildEnterprisePack(1, 100, 9)); // 配额只剩1
            // P0-1：成员授权凭证检查通过
            when(memberPackMapper.selectActiveByMemberIdAndPackId(MEMBER_ID, PACK_ID))
                    .thenReturn(buildMemberPack(1));
            when(usageRecordMapper.selectByRecordId(USAGE_RECORD_ID)).thenReturn(null);
            // P0-2：并发情况下 incrementUsedQuota 返回 0（WHERE used_quota < pack_quota 条件失败）
            when(enterprisePackMapper.incrementUsedQuota(EPACK_ID)).thenReturn(0);

            ConsumeResult result = skillConsumeService.consume(
                    USER_ID, PACK_CODE, SKILL_CODE,
                    USAGE_RECORD_ID, HOST_TYPE_ENT, HOST_SESSION_ID, null);

            assertFalse(result.isSuccess());
            assertTrue(result.getFailReason().contains("配额已用尽"));

            // usage_record 应被更新为 FAILED
            verify(usageRecordMapper).updateStatusByRecordId(
                    eq(USAGE_RECORD_ID), eq(UsageStatus.FAILED.getCode()), anyString());
        }

        @Test
        @DisplayName("§6.4.8 企业配额充足但成员授权凭证缺失 → Fail-Closed（P0-1 修复）")
        void enterpriseMemberPackNotFound() {
            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(RULE_CODE));
            when(enterpriseMemberMapper.selectActiveByUserId(USER_ID))
                    .thenReturn(Arrays.asList(buildMember(1)));
            when(enterpriseMapper.selectById(ENT_ID))
                    .thenReturn(buildEnterprise(1));
            when(enterprisePackMapper.selectByEnterpriseAndPack(ENT_ID, PACK_ID))
                    .thenReturn(buildEnterprisePack(1, 100, 5));
            // P0-1：成员授权凭证不存在（未 grant 或已被撤销）
            when(memberPackMapper.selectActiveByMemberIdAndPackId(MEMBER_ID, PACK_ID))
                    .thenReturn(null);

            ConsumeResult result = skillConsumeService.consume(
                    USER_ID, PACK_CODE, SKILL_CODE,
                    USAGE_RECORD_ID, HOST_TYPE_ENT, HOST_SESSION_ID, null);

            assertFalse(result.isSuccess());
            assertTrue(result.getFailReason().contains("成员授权"));

            // 不应调用配额扣减
            verify(enterprisePackMapper, never()).incrementUsedQuota(anyLong());
        }

        @Test
        @DisplayName("§6.4.9 企业配额充足但成员授权已失效（status=3） → Fail-Closed（P0-1 修复）")
        void enterpriseMemberPackRevoked() {
            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(RULE_CODE));
            when(enterpriseMemberMapper.selectActiveByUserId(USER_ID))
                    .thenReturn(Arrays.asList(buildMember(1)));
            when(enterpriseMapper.selectById(ENT_ID))
                    .thenReturn(buildEnterprise(1));
            when(enterprisePackMapper.selectByEnterpriseAndPack(ENT_ID, PACK_ID))
                    .thenReturn(buildEnterprisePack(1, 100, 5));
            // P0-1：成员授权已被级联撤销（status=3）
            when(memberPackMapper.selectActiveByMemberIdAndPackId(MEMBER_ID, PACK_ID))
                    .thenReturn(buildMemberPack(3)); // status=3 已撤销

            ConsumeResult result = skillConsumeService.consume(
                    USER_ID, PACK_CODE, SKILL_CODE,
                    USAGE_RECORD_ID, HOST_TYPE_ENT, HOST_SESSION_ID, null);

            assertFalse(result.isSuccess());
            assertTrue(result.getFailReason().contains("失效"));

            // 不应调用配额扣减
            verify(enterprisePackMapper, never()).incrementUsedQuota(anyLong());
        }

        // ---- 新增 §E4 ----
        @Test
        @DisplayName("§E4.1 consume ENTERPRISE — usedQuota 递增后 remainPoints = packQuota - newUsedQuota")
        void enterpriseConsumeRemainPoints() {
            // GIVEN usedQuota=5, packQuota=100，增量后应为 6
            FbsEnterprisePack ep = buildEnterprisePack(1, 100, 5);
            // 原子增量后 selectById 返回新的 usedQuota=6
            FbsEnterprisePack epAfter = buildEnterprisePack(1, 100, 6);

            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(RULE_CODE));
            when(enterpriseMemberMapper.selectActiveByUserId(USER_ID))
                    .thenReturn(Arrays.asList(buildMember(1)));
            when(enterpriseMapper.selectById(ENT_ID))
                    .thenReturn(buildEnterprise(1));
            when(enterprisePackMapper.selectByEnterpriseAndPack(ENT_ID, PACK_ID))
                    .thenReturn(ep);
            when(memberPackMapper.selectActiveByMemberIdAndPackId(MEMBER_ID, PACK_ID))
                    .thenReturn(buildMemberPack(1));
            when(usageRecordMapper.selectByRecordId(USAGE_RECORD_ID)).thenReturn(null);
            // incrementUsedQuota 原子++（doAnswer 同步 ep 对象）
            doAnswer(inv -> {
                ep.setUsedQuota(ep.getUsedQuota() + 1);
                return 1;
            }).when(enterprisePackMapper).incrementUsedQuota(EPACK_ID);
            // P1-1 修复：增量后重新查询
            when(enterprisePackMapper.selectById(EPACK_ID)).thenReturn(epAfter);

            ConsumeResult result = skillConsumeService.consume(
                    USER_ID, PACK_CODE, SKILL_CODE,
                    USAGE_RECORD_ID, HOST_TYPE_ENT, HOST_SESSION_ID, null);

            assertTrue(result.isSuccess());
            // remainPoints = 100 - 6 = 94（不是 95！）
            assertEquals(94, result.getRemainPoints());
        }

        @Test
        @DisplayName("enterprise terminal CAS conflict rejects success")
        void enterpriseConsumeRejectsSuccessWhenUsageTerminalCompareAndSetLost() {
            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(RULE_CODE));
            when(enterpriseMemberMapper.selectActiveByUserId(USER_ID))
                    .thenReturn(Arrays.asList(buildMember(1)));
            when(enterpriseMapper.selectById(ENT_ID))
                    .thenReturn(buildEnterprise(1));
            when(enterprisePackMapper.selectByEnterpriseAndPack(ENT_ID, PACK_ID))
                    .thenReturn(buildEnterprisePack(1, 100, 5));
            when(memberPackMapper.selectActiveByMemberIdAndPackId(MEMBER_ID, PACK_ID))
                    .thenReturn(buildMemberPack(1));
            when(usageRecordMapper.selectByRecordId(USAGE_RECORD_ID)).thenReturn(null);
            when(enterprisePackMapper.incrementUsedQuota(EPACK_ID)).thenReturn(1);
            when(enterprisePackMapper.selectById(EPACK_ID))
                    .thenReturn(buildEnterprisePack(1, 100, 6));
            when(usageRecordMapper.updateStatusByRecordId(
                    eq(USAGE_RECORD_ID), eq(UsageStatus.SUCCESS.getCode()), isNull()))
                    .thenReturn(0);

            ServiceException exception = assertThrows(ServiceException.class, () ->
                    skillConsumeService.consume(USER_ID, PACK_CODE, SKILL_CODE,
                            USAGE_RECORD_ID, HOST_TYPE_ENT, HOST_SESSION_ID, null));

            assertEquals("SKILL_USAGE_RECORD_TERMINAL_CAS_CONFLICT", exception.getMessage());
            assertEquals(409, exception.getCode());
            verify(enterprisePackMapper).incrementUsedQuota(EPACK_ID);
        }

        @Test
        @DisplayName("§E4.1a consume ENTERPRISE — 配额递增后回读缺失时使用已捕获企业包ID并保守返回0")
        void enterpriseConsumeMissingRefreshRowUsesCapturedPackId() {
            FbsEnterprisePack enterprisePack = buildEnterprisePack(1, 100, 5);

            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(RULE_CODE));
            when(enterpriseMemberMapper.selectActiveByUserId(USER_ID))
                    .thenReturn(Arrays.asList(buildMember(1)));
            when(enterpriseMapper.selectById(ENT_ID))
                    .thenReturn(buildEnterprise(1));
            when(enterprisePackMapper.selectByEnterpriseAndPack(ENT_ID, PACK_ID))
                    .thenReturn(enterprisePack);
            when(memberPackMapper.selectActiveByMemberIdAndPackId(MEMBER_ID, PACK_ID))
                    .thenReturn(buildMemberPack(1));
            when(usageRecordMapper.selectByRecordId(USAGE_RECORD_ID)).thenReturn(null);
            when(enterprisePackMapper.incrementUsedQuota(EPACK_ID)).thenReturn(1);
            when(enterprisePackMapper.selectById(EPACK_ID)).thenReturn(null);

            ConsumeResult result = skillConsumeService.consume(
                    USER_ID, PACK_CODE, SKILL_CODE,
                    USAGE_RECORD_ID, HOST_TYPE_ENT, HOST_SESSION_ID, null);

            assertTrue(result.isSuccess());
            assertEquals(0, result.getRemainPoints());
            verify(enterprisePackMapper).selectById(EPACK_ID);
            verify(usageRecordMapper).updateStatusByRecordId(
                    eq(USAGE_RECORD_ID), eq(UsageStatus.SUCCESS.getCode()), isNull());
        }

        @Test
        @DisplayName("enterprise missing refresh row terminal CAS conflict rejects success")
        void enterpriseMissingRefreshRowRejectsSuccessWhenUsageTerminalCompareAndSetLost() {
            FbsEnterprisePack enterprisePack = buildEnterprisePack(1, 100, 5);
            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(RULE_CODE));
            when(enterpriseMemberMapper.selectActiveByUserId(USER_ID))
                    .thenReturn(Arrays.asList(buildMember(1)));
            when(enterpriseMapper.selectById(ENT_ID)).thenReturn(buildEnterprise(1));
            when(enterprisePackMapper.selectByEnterpriseAndPack(ENT_ID, PACK_ID))
                    .thenReturn(enterprisePack);
            when(memberPackMapper.selectActiveByMemberIdAndPackId(MEMBER_ID, PACK_ID))
                    .thenReturn(buildMemberPack(1));
            when(usageRecordMapper.selectByRecordId(USAGE_RECORD_ID)).thenReturn(null);
            when(enterprisePackMapper.incrementUsedQuota(EPACK_ID)).thenReturn(1);
            when(enterprisePackMapper.selectById(EPACK_ID)).thenReturn(null);
            when(usageRecordMapper.updateStatusByRecordId(
                    eq(USAGE_RECORD_ID), eq(UsageStatus.SUCCESS.getCode()), isNull()))
                    .thenReturn(0);

            ServiceException exception = assertThrows(ServiceException.class, () ->
                    skillConsumeService.consume(USER_ID, PACK_CODE, SKILL_CODE,
                            USAGE_RECORD_ID, HOST_TYPE_ENT, HOST_SESSION_ID, null));

            assertEquals("SKILL_USAGE_RECORD_TERMINAL_CAS_CONFLICT", exception.getMessage());
            assertEquals(409, exception.getCode());
            verify(enterprisePackMapper).incrementUsedQuota(EPACK_ID);
        }

        @Test
        @DisplayName("§E4.2 consume ENTERPRISE — 配额用尽 failReason='企业配额已用尽'，记录 status=2")
        void enterpriseQuotaExhaustedFailReason() {
            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(RULE_CODE));
            when(enterpriseMemberMapper.selectActiveByUserId(USER_ID))
                    .thenReturn(Arrays.asList(buildMember(1)));
            when(enterpriseMapper.selectById(ENT_ID))
                    .thenReturn(buildEnterprise(1));
            when(enterprisePackMapper.selectByEnterpriseAndPack(ENT_ID, PACK_ID))
                    .thenReturn(buildEnterprisePack(1, 100, 100));
            when(memberPackMapper.selectActiveByMemberIdAndPackId(MEMBER_ID, PACK_ID))
                    .thenReturn(buildMemberPack(1));

            ConsumeResult result = skillConsumeService.consume(
                    USER_ID, PACK_CODE, SKILL_CODE,
                    USAGE_RECORD_ID, HOST_TYPE_ENT, HOST_SESSION_ID, null);

            assertFalse(result.isSuccess());
            assertTrue(result.getFailReason().contains("企业配额已用尽"));
            verify(enterprisePackMapper, never()).incrementUsedQuota(anyLong());
            verify(enterprisePackMapper, never()).selectById(anyLong());
        }

        @Test
        @DisplayName("§E4.3 consume ENTERPRISE — 企业包 status=3（已撤销），Fail-Closed")
        void enterprisePackRevoked() {
            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(RULE_CODE));
            when(enterpriseMemberMapper.selectActiveByUserId(USER_ID))
                    .thenReturn(Arrays.asList(buildMember(1)));
            when(enterpriseMapper.selectById(ENT_ID))
                    .thenReturn(buildEnterprise(1));
            when(enterprisePackMapper.selectByEnterpriseAndPack(ENT_ID, PACK_ID))
                    .thenReturn(buildEnterprisePack(3, 100, 50)); // status=3 已撤销

            ConsumeResult result = skillConsumeService.consume(
                    USER_ID, PACK_CODE, SKILL_CODE,
                    USAGE_RECORD_ID, HOST_TYPE_ENT, HOST_SESSION_ID, null);

            assertFalse(result.isSuccess());
            verify(enterprisePackMapper, never()).incrementUsedQuota(anyLong());
        }

        @Test
        @DisplayName("§E4.4 consume WORKBUDDY — 用户是企业成员但走个人路径，不受企业配额限制")
        void workbuddyPathIgnoresEnterpriseQuota() {
            // GIVEN 用户是企业成员（但调用方用 WORKBUDDY）
            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(RULE_CODE));
            when(pointsRuleMapper.selectPointsRuleByRuleCode(RULE_CODE))
                    .thenReturn(buildRule("0", 10));
            when(rightsCheckService.comprehensiveCheck(eq(USER_ID), eq(PACK_CODE),
                    isNull(), eq(HOST_TYPE_WB), eq(USAGE_RECORD_ID)))
                    .thenReturn(ComprehensiveRightsResult.pass(PACK_ID, RULE_CODE, 10));
            when(usageRecordMapper.selectByRecordId(USAGE_RECORD_ID)).thenReturn(null);
            // 按实际方法签名 mock（位置参数，不用 eq）
            when(pointsService.changePoints(USER_ID, RULE_CODE, -10, PACK_ID, USAGE_RECORD_ID))
                    .thenReturn(AjaxResult.success("积分扣减成功"));
            when(pointsService.getUserPoints(USER_ID)).thenReturn(990);

            ConsumeResult result = skillConsumeService.consume(
                    USER_ID, PACK_CODE, SKILL_CODE,
                    USAGE_RECORD_ID, HOST_TYPE_WB, HOST_SESSION_ID, null);

            assertTrue(result.isSuccess());
            // 走个人路径，扣积分，不动企业配额
            verify(enterprisePackMapper, never()).incrementUsedQuota(anyLong());
            verify(pointsService).changePoints(USER_ID, RULE_CODE, -10, PACK_ID, USAGE_RECORD_ID);
        }

        @Test
        @DisplayName("§E4.5 consume ENTERPRISE — 成员无 memberPack 授权，Fail-Closed")
        void enterpriseMemberNoPackAuth() {
            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(RULE_CODE));
            when(enterpriseMemberMapper.selectActiveByUserId(USER_ID))
                    .thenReturn(Arrays.asList(buildMember(1)));
            when(enterpriseMapper.selectById(ENT_ID))
                    .thenReturn(buildEnterprise(1));
            when(enterprisePackMapper.selectByEnterpriseAndPack(ENT_ID, PACK_ID))
                    .thenReturn(buildEnterprisePack(1, 100, 10));
            // 成员无此包授权
            when(memberPackMapper.selectActiveByMemberIdAndPackId(MEMBER_ID, PACK_ID))
                    .thenReturn(null);

            ConsumeResult result = skillConsumeService.consume(
                    USER_ID, PACK_CODE, SKILL_CODE,
                    USAGE_RECORD_ID, HOST_TYPE_ENT, HOST_SESSION_ID, null);

            assertFalse(result.isSuccess());
            assertTrue(result.getFailReason().contains("成员授权"));
            verify(enterprisePackMapper, never()).incrementUsedQuota(anyLong());
        }

        @Test
        @DisplayName("§E4.7 consume ENTERPRISE — usedQuota 递增，sys_user.points 不变，不走 wx_points_rule")
        void enterpriseConsumeNoPointsDeduction() {
            FbsEnterprisePack ep = buildEnterprisePack(1, 100, 5);
            FbsEnterprisePack epAfter = buildEnterprisePack(1, 100, 6);

            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(RULE_CODE));
            when(enterpriseMemberMapper.selectActiveByUserId(USER_ID))
                    .thenReturn(Arrays.asList(buildMember(1)));
            when(enterpriseMapper.selectById(ENT_ID))
                    .thenReturn(buildEnterprise(1));
            when(enterprisePackMapper.selectByEnterpriseAndPack(ENT_ID, PACK_ID))
                    .thenReturn(ep);
            when(memberPackMapper.selectActiveByMemberIdAndPackId(MEMBER_ID, PACK_ID))
                    .thenReturn(buildMemberPack(1));
            when(usageRecordMapper.selectByRecordId(USAGE_RECORD_ID)).thenReturn(null);
            doAnswer(inv -> {
                ep.setUsedQuota(ep.getUsedQuota() + 1);
                return 1;
            }).when(enterprisePackMapper).incrementUsedQuota(EPACK_ID);
            when(enterprisePackMapper.selectById(EPACK_ID)).thenReturn(epAfter);

            ConsumeResult result = skillConsumeService.consume(
                    USER_ID, PACK_CODE, SKILL_CODE,
                    USAGE_RECORD_ID, HOST_TYPE_ENT, HOST_SESSION_ID, null);

            assertTrue(result.isSuccess());
            // 核心断言：不动个人积分
            verify(pointsService, never()).changePoints(anyLong(), anyString(), anyInt(), anyLong(), anyString());
            // 写入的 usage_record host_type = ENTERPRISE
            verify(usageRecordMapper).updateStatusByRecordId(
                    eq(USAGE_RECORD_ID), eq(UsageStatus.SUCCESS.getCode()), isNull());
        }

        // §E4.8: selectActiveByUserId 不返回 status=2 的成员（该方法只筛选 status=1）
        // 测试场景：成员不在企业成员列表中（已移除/不存在）→ 服务返回"不是企业成员"
        @Test
        @DisplayName("§E4.8 consume ENTERPRISE — 用户不是企业成员，Fail-Closed")
        void enterpriseMemberNotFound() {
            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(RULE_CODE));
            // selectActiveByUserId 只返回 status=1 的成员，成员不存在时返回 null
            when(enterpriseMemberMapper.selectActiveByUserId(USER_ID))
                    .thenReturn(null);

            ConsumeResult result = skillConsumeService.consume(
                    USER_ID, PACK_CODE, SKILL_CODE,
                    USAGE_RECORD_ID, HOST_TYPE_ENT, HOST_SESSION_ID, null);

            assertFalse(result.isSuccess());
            assertTrue(result.getFailReason().contains("不是企业成员"));
            verify(enterprisePackMapper, never()).incrementUsedQuota(anyLong());
        }
    }
}
