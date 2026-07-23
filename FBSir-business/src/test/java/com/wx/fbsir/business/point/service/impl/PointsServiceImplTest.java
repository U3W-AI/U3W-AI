package com.wx.fbsir.business.point.service.impl;

import com.wx.fbsir.business.point.domain.PointsRecord;
import com.wx.fbsir.business.point.domain.PointsRule;
import com.wx.fbsir.business.point.mapper.PointsMapper;
import com.wx.fbsir.business.point.mapper.PointsRecordMapper;
import com.wx.fbsir.business.point.service.IPointsRuleService;
import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.core.domain.entity.SysUser;
import com.wx.fbsir.system.mapper.SysUserMapper;
import com.wx.fbsir.system.service.ISysUserService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
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
 * PointsService 单元测试
 *
 * OpenSpec #10: 积分模型适配
 * 覆盖幂等逻辑测试
 *
 * @author FBSir
 * @date 2026-04-16
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("积分服务测试")
class PointsServiceImplTest {

    @Mock
    private PointsMapper pointsMapper;

    @Mock
    private PointsRecordMapper pointsRecordMapper;

    @Mock
    private IPointsRuleService pointsRuleService;

    @Mock
    private ISysUserService userService;

    @Mock
    private SysUserMapper sysUserMapper;

    @InjectMocks
    private PointsServiceImpl pointsService;

    @BeforeEach
    void allowConditionalBalanceUpdateByDefault() {
        lenient().when(pointsMapper.updateUserPointsIfBalance(anyLong(), anyInt(), anyInt())).thenReturn(1);
    }

    @Test
    @DisplayName("P0: 粉丝查询经数据权限服务，客户端 dataScope 不得触达原始 Mapper")
    void getPointsFansListDelegatesToDataScopedUserServiceInsteadOfRawMapper() {
        SysUser query = new SysUser();
        query.getParams().put("dataScope", " OR 1 = 1 -- client supplied");
        List<SysUser> expected = List.of(new SysUser(7L));
        when(userService.selectUserList(query)).thenReturn(expected);

        assertSame(expected, pointsService.getPointsFansList(query));

        verify(userService).selectUserList(query);
        verifyNoInteractions(sysUserMapper);
    }

    @Nested
    @DisplayName("changePoints(eventId) 幂等测试")
    class ChangePointsWithEventId {

        @Test
        @DisplayName("P0: 幂等 - eventId 已存在时返回既有余额")
        void testChangePoints_Idempotent_WhenEventIdExists() {
            // Given: eventId 已存在
            String eventId = "first_install_1";
            PointsRecord existingRecord = new PointsRecord();
            existingRecord.setBalanceAfter(1000);
            when(pointsRecordMapper.selectByEventId(eventId)).thenReturn(existingRecord);

            // When: 调用 changePoints
            AjaxResult result = pointsService.changePoints(
                1L, "FIRST_INSTALL", 100, null, null, eventId
            );

            // Then: 返回成功，但不执行积分变动
            assertTrue(result.isSuccess());
            assertEquals(1000, result.get("data")); // 返回既有余额

            // Then: 不调用 updatePoints 和 insertPointsRecord
            verify(pointsMapper, never()).updateUserPoints(anyLong(), anyInt());
            verify(pointsMapper, never()).updateUserPointsIfBalance(anyLong(), anyInt(), anyInt());
            verify(pointsRecordMapper, never()).insertPointsRecord(any());
        }

        @Test
        @DisplayName("P1: 正常 - eventId 不存在时执行积分变动")
        void testChangePoints_Normal_WhenEventIdNotExists() {
            // Given: eventId 不存在
            String eventId = "daily_login_1_2026-04-16";
            when(pointsRecordMapper.selectByEventId(eventId)).thenReturn(null);

            // Given: 规则配置
            PointsRule rule = new PointsRule();
            rule.setRuleCode("DAILY_LOGIN");
            rule.setPointsValue(10);
            rule.setStatus("0");
            when(pointsRuleService.getRuleByCode("DAILY_LOGIN")).thenReturn(rule);
            when(pointsRuleService.checkLimit(anyLong(), anyString(), any())).thenReturn(true);
            when(pointsRuleService.checkMaxAmount(anyLong(), anyString(), anyInt(), any())).thenReturn(true);

            // Given: 当前余额
            when(pointsMapper.getUserPoints(1L)).thenReturn(100);

            // When: 调用 changePoints
            AjaxResult result = pointsService.changePoints(
                1L, "DAILY_LOGIN", 10, null, null, eventId
            );

            // Then: 返回成功
            assertTrue(result.isSuccess());
            assertEquals(110, result.get("data")); // 新余额

            // Then: 调用 updatePoints 和 insertPointsRecord
            verify(pointsMapper).updateUserPointsIfBalance(1L, 100, 110);
            verify(pointsMapper, never()).updateUserPoints(anyLong(), anyInt());
            verify(pointsRecordMapper).insertPointsRecord(argThat(record ->
                record.getEventId().equals(eventId) &&
                record.getChangeAmount() == 10 &&
                record.getBalanceBefore() == 100 &&
                record.getBalanceAfter() == 110
            ));
        }

        @Test
        @DisplayName("P2: 幂等 - eventId 为 null 时不检查幂等")
        void testChangePoints_NoIdempotent_WhenEventIdIsNull() {
            // Given: 规则配置
            PointsRule rule = new PointsRule();
            rule.setRuleCode("NORMAL_TASK");
            rule.setPointsValue(5);
            rule.setStatus("0");
            when(pointsRuleService.getRuleByCode("NORMAL_TASK")).thenReturn(rule);
            when(pointsRuleService.checkLimit(anyLong(), anyString(), any())).thenReturn(true);
            when(pointsRuleService.checkMaxAmount(anyLong(), anyString(), anyInt(), any())).thenReturn(true);

            // Given: 当前余额
            when(pointsMapper.getUserPoints(1L)).thenReturn(100);

            // When: 调用 changePoints（eventId=null）
            AjaxResult result = pointsService.changePoints(
                1L, "NORMAL_TASK", 5, null, null, null
            );

            // Then: 返回成功
            assertTrue(result.isSuccess());

            // Then: 不调用 selectByEventId
            verify(pointsRecordMapper, never()).selectByEventId(any());
        }

        @Test
        @DisplayName("P2: 幂等 - eventId 为空字符串时不检查幂等")
        void testChangePoints_NoIdempotent_WhenEventIdIsEmpty() {
            // Given: 规则配置
            PointsRule rule = new PointsRule();
            rule.setRuleCode("NORMAL_TASK");
            rule.setPointsValue(5);
            rule.setStatus("0");
            when(pointsRuleService.getRuleByCode("NORMAL_TASK")).thenReturn(rule);
            when(pointsRuleService.checkLimit(anyLong(), anyString(), any())).thenReturn(true);
            when(pointsRuleService.checkMaxAmount(anyLong(), anyString(), anyInt(), any())).thenReturn(true);

            // Given: 当前余额
            when(pointsMapper.getUserPoints(1L)).thenReturn(100);

            // When: 调用 changePoints（eventId=""）
            AjaxResult result = pointsService.changePoints(
                1L, "NORMAL_TASK", 5, null, null, ""
            );

            // Then: 返回成功
            assertTrue(result.isSuccess());

            // Then: 不调用 selectByEventId
            verify(pointsRecordMapper, never()).selectByEventId(any());
        }

        @Test
        @DisplayName("P0: event entry returns a stable conflict without ledger or limit writes when CAS misses")
        void changePointsWithEventIdStopsBeforeSideEffectsWhenBalanceCasMisses() {
            String eventId = "cas_conflict_event";
            PointsRule rule = activeRule("DAILY_LOGIN", 10);
            when(pointsRecordMapper.selectByEventId(eventId)).thenReturn(null);
            when(pointsRuleService.getRuleByCode("DAILY_LOGIN")).thenReturn(rule);
            when(pointsRuleService.checkLimit(1L, "DAILY_LOGIN", rule)).thenReturn(true);
            when(pointsRuleService.checkMaxAmount(1L, "DAILY_LOGIN", 10, rule)).thenReturn(true);
            when(pointsMapper.getUserPoints(1L)).thenReturn(100);
            when(pointsMapper.updateUserPointsIfBalance(1L, 100, 110)).thenReturn(0);

            AjaxResult result = pointsService.changePoints(1L, "DAILY_LOGIN", 10, null, null, eventId);

            assertFalse(result.isSuccess());
            assertEquals("POINTS_BALANCE_CONCURRENT_CONFLICT", result.get("msg"));
            verify(pointsMapper).updateUserPointsIfBalance(1L, 100, 110);
            verify(pointsMapper, never()).updateUserPoints(anyLong(), anyInt());
            verify(pointsRecordMapper, never()).insertPointsRecord(any());
            verify(pointsRuleService, never()).markLimit(anyLong(), anyString(), any());
        }

        @Test
        @DisplayName("P0: event entry rejects an out-of-range balance before conditional update or side effects")
        void changePointsWithEventIdStopsBeforeSideEffectsWhenBalanceWouldOverflow() {
            String eventId = "event_overflow";
            PointsRule rule = activeRule("EVENT_OVERFLOW", 1);
            when(pointsRecordMapper.selectByEventId(eventId)).thenReturn(null);
            when(pointsRuleService.getRuleByCode("EVENT_OVERFLOW")).thenReturn(rule);
            when(pointsRuleService.checkLimit(1L, "EVENT_OVERFLOW", rule)).thenReturn(true);
            when(pointsRuleService.checkMaxAmount(1L, "EVENT_OVERFLOW", 1, rule)).thenReturn(true);
            when(pointsMapper.getUserPoints(1L)).thenReturn(Integer.MAX_VALUE);

            AjaxResult result = pointsService.changePoints(1L, "EVENT_OVERFLOW", 1, null, null, eventId);

            assertFalse(result.isSuccess());
            assertEquals("POINTS_BALANCE_OUT_OF_RANGE", result.get("msg"));
            verify(pointsMapper, never()).updateUserPointsIfBalance(anyLong(), anyInt(), anyInt());
            verify(pointsRecordMapper, never()).insertPointsRecord(any());
            verify(pointsRuleService, never()).markLimit(anyLong(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("changePoints 免费包测试")
    class FreePackTest {

        @Test
        @DisplayName("P1: 免费包 - ruleCode 为空时直接返回成功")
        void testChangePoints_FreePack_ReturnsSuccess() {
            // When: 调用 changePoints（ruleCode=null）
            AjaxResult result = pointsService.changePoints(
                1L, null, 0, null, null, "event_123"
            );

            // Then: 返回成功
            assertTrue(result.isSuccess());

            // Then: 不调用任何 Mapper
            verify(pointsRecordMapper, never()).selectByEventId(any());
            verify(pointsMapper, never()).updateUserPoints(anyLong(), anyInt());
            verify(pointsMapper, never()).updateUserPointsIfBalance(anyLong(), anyInt(), anyInt());
            verify(pointsRecordMapper, never()).insertPointsRecord(any());
        }
    }

    @Test
    @DisplayName("P0: basic entry returns a stable conflict without ledger or limit writes when CAS misses")
    void changePointsStopsBeforeSideEffectsWhenBalanceCasMisses() {
        PointsRule rule = activeRule("BASIC_CAS", 10);
        when(pointsRuleService.getRuleByCode("BASIC_CAS")).thenReturn(rule);
        when(pointsRuleService.checkLimit(1L, "BASIC_CAS", rule)).thenReturn(true);
        when(pointsRuleService.checkMaxAmount(1L, "BASIC_CAS", 10, rule)).thenReturn(true);
        when(pointsMapper.getUserPoints(1L)).thenReturn(100);
        when(pointsMapper.updateUserPointsIfBalance(1L, 100, 110)).thenReturn(0);

        AjaxResult result = pointsService.changePoints(1L, "BASIC_CAS", 10);

        assertFalse(result.isSuccess());
        assertEquals("POINTS_BALANCE_CONCURRENT_CONFLICT", result.get("msg"));
        verify(pointsMapper).updateUserPointsIfBalance(1L, 100, 110);
        verify(pointsMapper, never()).updateUserPoints(anyLong(), anyInt());
        verify(pointsRecordMapper, never()).insertPointsRecord(any());
        verify(pointsRuleService, never()).markLimit(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("P0: basic entry returns the committed balance after a successful conditional update")
    void changePointsReturnsCommittedBalanceAfterSuccessfulConditionalUpdate() {
        PointsRule rule = activeRule("BASIC_SUCCESS", 10);
        when(pointsRuleService.getRuleByCode("BASIC_SUCCESS")).thenReturn(rule);
        when(pointsRuleService.checkLimit(1L, "BASIC_SUCCESS", rule)).thenReturn(true);
        when(pointsRuleService.checkMaxAmount(1L, "BASIC_SUCCESS", 10, rule)).thenReturn(true);
        when(pointsMapper.getUserPoints(1L)).thenReturn(100);

        AjaxResult result = pointsService.changePoints(1L, "BASIC_SUCCESS", 10);

        assertTrue(result.isSuccess());
        assertEquals(110, result.get("data"));
        verify(pointsMapper).updateUserPointsIfBalance(1L, 100, 110);
        verify(pointsRecordMapper).insertPointsRecord(any());
        verify(pointsRuleService).markLimit(1L, "BASIC_SUCCESS", rule);
    }

    @Test
    @DisplayName("P0: basic entry rejects balance overflow before conditional update or side effects")
    void changePointsStopsBeforeSideEffectsWhenBalanceWouldOverflow() {
        PointsRule rule = activeRule("BASIC_OVERFLOW", 1);
        when(pointsRuleService.getRuleByCode("BASIC_OVERFLOW")).thenReturn(rule);
        when(pointsRuleService.checkLimit(1L, "BASIC_OVERFLOW", rule)).thenReturn(true);
        when(pointsRuleService.checkMaxAmount(1L, "BASIC_OVERFLOW", 1, rule)).thenReturn(true);
        when(pointsMapper.getUserPoints(1L)).thenReturn(Integer.MAX_VALUE);

        AjaxResult result = pointsService.changePoints(1L, "BASIC_OVERFLOW", 1);

        assertFalse(result.isSuccess());
        assertEquals("POINTS_BALANCE_OUT_OF_RANGE", result.get("msg"));
        verify(pointsMapper, never()).updateUserPointsIfBalance(anyLong(), anyInt(), anyInt());
        verify(pointsMapper, never()).updateUserPoints(anyLong(), anyInt());
        verify(pointsRecordMapper, never()).insertPointsRecord(any());
        verify(pointsRuleService, never()).markLimit(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("P0: scene entry returns a stable conflict without ledger or limit writes when CAS misses")
    void changePointsWithSceneStopsBeforeSideEffectsWhenBalanceCasMisses() {
        PointsRule rule = activeRule("SCENE_CAS", 10);
        when(pointsRuleService.getRuleByCode("SCENE_CAS")).thenReturn(rule);
        when(pointsRuleService.checkLimit(1L, "SCENE_CAS", rule)).thenReturn(true);
        when(pointsRuleService.checkMaxAmount(1L, "SCENE_CAS", 10, rule)).thenReturn(true);
        when(pointsMapper.getUserPoints(1L)).thenReturn(100);
        when(pointsMapper.updateUserPointsIfBalance(1L, 100, 110)).thenReturn(0);

        AjaxResult result = pointsService.changePoints(1L, "SCENE_CAS", 10, 6L, "usage-cas");

        assertFalse(result.isSuccess());
        assertEquals("POINTS_BALANCE_CONCURRENT_CONFLICT", result.get("msg"));
        verify(pointsMapper).updateUserPointsIfBalance(1L, 100, 110);
        verify(pointsMapper, never()).updateUserPoints(anyLong(), anyInt());
        verify(pointsRecordMapper, never()).insertPointsRecord(any());
        verify(pointsRuleService, never()).markLimit(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("P0: scene entry returns the committed balance after a successful conditional update")
    void changePointsWithSceneReturnsCommittedBalanceAfterSuccessfulConditionalUpdate() {
        PointsRule rule = activeRule("SCENE_SUCCESS", 10);
        when(pointsRuleService.getRuleByCode("SCENE_SUCCESS")).thenReturn(rule);
        when(pointsRuleService.checkLimit(1L, "SCENE_SUCCESS", rule)).thenReturn(true);
        when(pointsRuleService.checkMaxAmount(1L, "SCENE_SUCCESS", 10, rule)).thenReturn(true);
        when(pointsMapper.getUserPoints(1L)).thenReturn(100);

        AjaxResult result = pointsService.changePoints(1L, "SCENE_SUCCESS", 10, 6L, "usage-success");

        assertTrue(result.isSuccess());
        assertEquals(110, result.get("data"));
        verify(pointsMapper).updateUserPointsIfBalance(1L, 100, 110);
        verify(pointsRecordMapper).insertPointsRecord(any());
        verify(pointsRuleService).markLimit(1L, "SCENE_SUCCESS", rule);
    }

    @Test
    @DisplayName("P0: scene entry rejects an out-of-range balance before conditional update or side effects")
    void changePointsWithSceneStopsBeforeSideEffectsWhenBalanceWouldOverflow() {
        PointsRule rule = activeRule("SCENE_OVERFLOW", 1);
        when(pointsRuleService.getRuleByCode("SCENE_OVERFLOW")).thenReturn(rule);
        when(pointsRuleService.checkLimit(1L, "SCENE_OVERFLOW", rule)).thenReturn(true);
        when(pointsRuleService.checkMaxAmount(1L, "SCENE_OVERFLOW", 1, rule)).thenReturn(true);
        when(pointsMapper.getUserPoints(1L)).thenReturn(Integer.MAX_VALUE);

        AjaxResult result = pointsService.changePoints(1L, "SCENE_OVERFLOW", 1, 6L, "usage-overflow");

        assertFalse(result.isSuccess());
        assertEquals("POINTS_BALANCE_OUT_OF_RANGE", result.get("msg"));
        verify(pointsMapper, never()).updateUserPointsIfBalance(anyLong(), anyInt(), anyInt());
        verify(pointsRecordMapper, never()).insertPointsRecord(any());
        verify(pointsRuleService, never()).markLimit(anyLong(), anyString(), any());
    }

    private PointsRule activeRule(String ruleCode, int pointsValue) {
        PointsRule rule = new PointsRule();
        rule.setRuleCode(ruleCode);
        rule.setPointsValue(pointsValue);
        rule.setStatus("0");
        return rule;
    }
}
