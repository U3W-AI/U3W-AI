package com.wx.fbsir.business.fbs.service;

import com.wx.fbsir.business.fbs.service.impl.SkillConsumeServiceImpl;
import com.wx.fbsir.business.fbs.domain.entity.FbsScenePack;
import com.wx.fbsir.business.fbs.domain.entity.FbsSkillUsageRecord;
import com.wx.fbsir.business.fbs.domain.enums.UsageStatus;
import com.wx.fbsir.business.fbs.dto.ComprehensiveRightsResult;
import com.wx.fbsir.business.fbs.dto.ConsumeResult;
import com.wx.fbsir.business.fbs.mapper.FbsScenePackMapper;
import com.wx.fbsir.business.fbs.mapper.FbsSkillUsageRecordMapper;
import com.wx.fbsir.business.point.domain.PointsRule;
import com.wx.fbsir.business.point.mapper.PointsRuleMapper;
import com.wx.fbsir.business.point.service.IPointsService;
import com.wx.fbsir.common.core.domain.AjaxResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SkillConsumeService 单元测试
 *
 * 覆盖 tasks.md §6.3 全部用例（共5个）
 *
 * @author wxfbsir
 * @date 2026-04-08
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Skill消费服务测试")
class SkillConsumeServiceTest {

    @Mock
    private FbsScenePackMapper scenePackMapper;

    @Mock
    private FbsSkillUsageRecordMapper usageRecordMapper;

    @Mock
    private PointsRuleMapper pointsRuleMapper;

    @Mock
    private RightsCheckService rightsCheckService;

    @Mock
    private IPointsService pointsService;

    @InjectMocks
    private SkillConsumeServiceImpl skillConsumeService;

    // ---- 常量 ----
    private static final Long USER_ID = 1001L;
    private static final Long PACK_ID = 2001L;
    private static final String PACK_CODE = "pack_vip_monthly";
    private static final String PACK_VERSION = "1.0.0";
    private static final String SKILL_CODE = "fbs-bookwriter";
    private static final String USAGE_RECORD_ID = "task-001-uuid";
    private static final String HOST_TYPE = "WORKBUDDY";
    private static final String HOST_SESSION_ID = "session-001";
    private static final String RULE_CODE = "rule_pack_vip";

    // ---- Fixture ----
    private FbsScenePack buildPack(String pointsRuleCode) {
        FbsScenePack pack = new FbsScenePack();
        pack.setId(PACK_ID);
        pack.setPackCode(PACK_CODE);
        pack.setStatus(1); // 已发布
        pack.setCurrentVersion(PACK_VERSION);
        pack.setPointsRuleCode(pointsRuleCode);
        return pack;
    }

    private FbsSkillUsageRecord buildRecord(int status) {
        FbsSkillUsageRecord rec = new FbsSkillUsageRecord();
        rec.setUsageRecordId(USAGE_RECORD_ID);
        rec.setUserId(USER_ID);
        rec.setPackId(PACK_ID);
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

    // ========================================================================
    // §6.3 Skill消费测试
    // ========================================================================

    @Nested
    @DisplayName("§6.3.1-6.3.5 consume")
    class ConsumeTests {

        @Test
        @DisplayName("§6.3.1 有积分场景包，扣减成功")
        void consumeWithPointsSuccess() {
            // 1. 场景包存在且已发布
            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(RULE_CODE));
            // 2. 积分规则存在且启用
            when(pointsRuleMapper.selectPointsRuleByRuleCode(RULE_CODE))
                    .thenReturn(buildRule("0", 10));
            // 3. 综合校验通过
            when(rightsCheckService.comprehensiveCheck(eq(USER_ID), eq(PACK_CODE),
                    isNull(), eq(HOST_TYPE), eq(USAGE_RECORD_ID)))
                    .thenReturn(ComprehensiveRightsResult.pass(PACK_ID, RULE_CODE, 10));
            // 4. 幂等：首次请求，无 existing record
            when(usageRecordMapper.selectByRecordId(USAGE_RECORD_ID)).thenReturn(null);
            // 5. 积分扣减成功
            when(pointsService.changePoints(eq(USER_ID), eq(RULE_CODE), eq(-10),
                    eq(PACK_ID), eq(USAGE_RECORD_ID)))
                    .thenReturn(AjaxResult.success("积分扣减成功"));
            when(pointsService.getUserPoints(USER_ID)).thenReturn(990);

            ConsumeResult result = skillConsumeService.consume(
                    USER_ID, PACK_CODE, SKILL_CODE,
                    USAGE_RECORD_ID, HOST_TYPE, HOST_SESSION_ID, null);

            assertTrue(result.isSuccess());
            assertEquals(USAGE_RECORD_ID, result.getUsageRecordId());
            assertEquals(990, result.getRemainPoints());

            // 验证 usage_record status=1
            verify(usageRecordMapper).updateStatusByRecordId(
                    eq(USAGE_RECORD_ID), eq(UsageStatus.SUCCESS.getCode()), isNull());
        }

        @Test
        @DisplayName("§6.3.2 免费包(ruleCode=NULL)，跳过积分扣减")
        void consumeFreePack() {
            // 1. 场景包存在且已发布，ruleCode=NULL
            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(null)); // ruleCode = NULL
            // 2. 跳过积分规则查询（因为 ruleCode 为 null）
            // 3. 综合校验通过
            when(rightsCheckService.comprehensiveCheck(eq(USER_ID), eq(PACK_CODE),
                    isNull(), eq(HOST_TYPE), eq(USAGE_RECORD_ID)))
                    .thenReturn(ComprehensiveRightsResult.pass(PACK_ID, null, 0));
            // 4. 幂等：首次请求
            when(usageRecordMapper.selectByRecordId(USAGE_RECORD_ID)).thenReturn(null);
            // 5. 积分余额查询
            when(pointsService.getUserPoints(USER_ID)).thenReturn(1000);

            ConsumeResult result = skillConsumeService.consume(
                    USER_ID, PACK_CODE, SKILL_CODE,
                    USAGE_RECORD_ID, HOST_TYPE, HOST_SESSION_ID, null);

            assertTrue(result.isSuccess());
            assertEquals(1000, result.getRemainPoints());

            // 验证未调用 changePoints（因为免费）
            verify(pointsService, never())
                    .changePoints(anyLong(), anyString(), anyInt(), anyLong(), anyString());
            // 验证 usage_record status=1
            verify(usageRecordMapper).updateStatusByRecordId(
                    eq(USAGE_RECORD_ID), eq(UsageStatus.SUCCESS.getCode()), isNull());
        }

        @Test
        @DisplayName("§6.3.3 积分不足 → Fail-Closed")
        void consumePointsInsufficient() {
            // 1. 场景包存在且已发布
            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(RULE_CODE));
            // 2. 积分规则存在且启用
            when(pointsRuleMapper.selectPointsRuleByRuleCode(RULE_CODE))
                    .thenReturn(buildRule("0", 10));
            // 3. 综合校验失败（积分不足）
            when(rightsCheckService.comprehensiveCheck(eq(USER_ID), eq(PACK_CODE),
                    isNull(), eq(HOST_TYPE), eq(USAGE_RECORD_ID)))
                    .thenReturn(ComprehensiveRightsResult.fail("用户积分不足"));

            ConsumeResult result = skillConsumeService.consume(
                    USER_ID, PACK_CODE, SKILL_CODE,
                    USAGE_RECORD_ID, HOST_TYPE, HOST_SESSION_ID, null);

            assertFalse(result.isSuccess());
            assertTrue(result.getFailReason().contains("积分不足"));

            // 验证未调用 changePoints
            verify(pointsService, never())
                    .changePoints(anyLong(), anyString(), anyInt(), anyLong(), anyString());
        }

        @Test
        @DisplayName("§6.3.4 幂等：usageRecordId 重复提交（status=0 → 拒绝）")
        void idempotentDuplicateSubmitInProgress() {
            // 1. 场景包存在
            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(RULE_CODE));
            when(pointsRuleMapper.selectPointsRuleByRuleCode(RULE_CODE))
                    .thenReturn(buildRule("0", 10));
            when(rightsCheckService.comprehensiveCheck(eq(USER_ID), eq(PACK_CODE),
                    isNull(), eq(HOST_TYPE), eq(USAGE_RECORD_ID)))
                    .thenReturn(ComprehensiveRightsResult.pass(PACK_ID, RULE_CODE, 10));
            // 4. 幂等：已有进行中的记录（status=0）
            when(usageRecordMapper.selectByRecordId(USAGE_RECORD_ID))
                    .thenReturn(buildRecord(UsageStatus.IN_PROGRESS.getCode()));

            ConsumeResult result = skillConsumeService.consume(
                    USER_ID, PACK_CODE, SKILL_CODE,
                    USAGE_RECORD_ID, HOST_TYPE, HOST_SESSION_ID, null);

            assertFalse(result.isSuccess());
            assertTrue(result.getFailReason().contains("正在处理中"));

            // 验证未重复扣积分
            verify(pointsService, never())
                    .changePoints(anyLong(), anyString(), anyInt(), anyLong(), anyString());
        }

        @Test
        @DisplayName("§6.3.5 幂等：usageRecordId 已成功（直接返回，不重复扣）")
        void idempotentAlreadySuccess() {
            // 1. 场景包存在
            when(scenePackMapper.selectByPackCode(PACK_CODE))
                    .thenReturn(buildPack(RULE_CODE));
            when(pointsRuleMapper.selectPointsRuleByRuleCode(RULE_CODE))
                    .thenReturn(buildRule("0", 10));
            when(rightsCheckService.comprehensiveCheck(eq(USER_ID), eq(PACK_CODE),
                    isNull(), eq(HOST_TYPE), eq(USAGE_RECORD_ID)))
                    .thenReturn(ComprehensiveRightsResult.pass(PACK_ID, RULE_CODE, 10));
            // 4. 幂等：已有成功记录（status=1）
            when(usageRecordMapper.selectByRecordId(USAGE_RECORD_ID))
                    .thenReturn(buildRecord(UsageStatus.SUCCESS.getCode()));
            when(pointsService.getUserPoints(USER_ID)).thenReturn(990);

            ConsumeResult result = skillConsumeService.consume(
                    USER_ID, PACK_CODE, SKILL_CODE,
                    USAGE_RECORD_ID, HOST_TYPE, HOST_SESSION_ID, null);

            assertTrue(result.isSuccess());
            assertEquals(990, result.getRemainPoints());

            // 验证未调用 changePoints（幂等返回）
            verify(pointsService, never())
                    .changePoints(anyLong(), anyString(), anyInt(), anyLong(), anyString());
            // 验证未更新状态
            verify(usageRecordMapper, never())
                    .updateStatusByRecordId(anyString(), anyInt(), any());
        }
    }
}
