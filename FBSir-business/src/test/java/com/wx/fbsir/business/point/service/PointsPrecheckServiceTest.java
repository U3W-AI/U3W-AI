package com.wx.fbsir.business.point.service;

import com.wx.fbsir.business.point.domain.PointsResult;
import com.wx.fbsir.business.point.domain.PointsRule;
import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.core.domain.entity.SysUser;
import com.wx.fbsir.system.service.ISysUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PointsPrecheckServiceTest {

    @Mock
    private IPointsRuleService pointsRuleService;

    @Mock
    private IPointsService pointsService;

    @Mock
    private ISysUserService userService;

    @InjectMocks
    private PointsPrecheckService pointsPrecheckService;

    @Test
    void returnsTheCommittedBalanceInsteadOfTheEarlierPrecheckSnapshot() {
        SysUser user = new SysUser(1L);
        user.setStatus("0");
        PointsRule rule = new PointsRule();
        rule.setRuleCode("PRECHECK_CAS");
        rule.setStatus("0");
        rule.setPointsValue(-10);
        when(userService.selectUserById(1L)).thenReturn(user);
        when(pointsRuleService.getRuleByCode("PRECHECK_CAS")).thenReturn(rule);
        when(pointsRuleService.checkLimit(1L, "PRECHECK_CAS", rule)).thenReturn(true);
        when(pointsRuleService.checkMaxAmount(1L, "PRECHECK_CAS", -10, rule)).thenReturn(true);
        when(pointsService.getUserPoints(1L)).thenReturn(100);
        when(pointsService.changePoints(1L, "PRECHECK_CAS", -10))
                .thenReturn(AjaxResult.success("points changed", 95));

        PointsResult result = pointsPrecheckService.tryChangePoints(1L, "PRECHECK_CAS", -10);

        assertTrue(result.isSuccess());
        assertEquals(-10, result.getDelta());
        assertEquals(95, result.getBalanceAfter());
    }
}
