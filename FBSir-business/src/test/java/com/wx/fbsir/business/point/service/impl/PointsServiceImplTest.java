package com.wx.fbsir.business.point.service.impl;

import com.wx.fbsir.business.point.domain.PointsRecord;
import com.wx.fbsir.business.point.domain.PointsRule;
import com.wx.fbsir.business.point.mapper.PointsMapper;
import com.wx.fbsir.business.point.mapper.PointsRecordMapper;
import com.wx.fbsir.business.point.service.IPointsRuleService;
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

    @InjectMocks
    private PointsServiceImpl pointsService;

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
            verify(pointsMapper).updateUserPoints(1L, 110);
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
            verify(pointsRecordMapper, never()).insertPointsRecord(any());
        }
    }
}
