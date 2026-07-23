package com.wx.fbsir.business.point.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.wx.fbsir.common.utils.StringUtils;
import com.wx.fbsir.common.core.domain.entity.SysUser;
import com.wx.fbsir.system.service.ISysUserService;
import com.wx.fbsir.business.point.domain.PointsResult;
import com.wx.fbsir.business.point.domain.PointsRule;
import com.wx.fbsir.business.point.service.IPointsRuleService;
import com.wx.fbsir.business.point.service.IPointsService;

/**
 * 积分前置校验与调用模板
 * 
 * 适用场景：在"日更助手文章生成"等耗积分的业务入口前，先统一校验并封装异常，
 * 若校验失败直接返回，不进入核心业务。
 * 
 * 核心要点：
 * - 仅使用 ruleCode（规则编码，唯一）做业务标识
 * - 先校验用户/规则/限频/累计上限/余额，再决定是否继续
 * - 统一封装错误码，业务方只需在调用前执行本方法
 * 
 * @author FBSir
 * @date 2025-12-08
 */
@Service
public class PointsPrecheckService {

    @Autowired
    private IPointsRuleService pointsRuleService;
    
    @Autowired
    private IPointsService pointsService;
    
    @Autowired
    private ISysUserService userService;

    /**
     * 积分变更前置校验与执行
     * 
     * @param userId 用户ID
     * @param ruleCode 规则编码（唯一标识）
     * @param changeAmount 积分变动值（可选，不传则使用规则默认值）
     * @return 积分操作结果
     */
    public PointsResult tryChangePoints(Long userId, String ruleCode, Integer changeAmount) {
        // 0. 基础校验
        if (userId == null) {
            return PointsResult.fail("USER_EMPTY", "用户ID为空");
        }
        if (StringUtils.isEmpty(ruleCode)) {
            return PointsResult.fail("RULE_EMPTY", "规则编码为空");
        }

        // 1. 用户校验
        SysUser user = userService.selectUserById(userId);
        if (user == null) {
            return PointsResult.fail("USER_NOT_FOUND", "用户不存在");
        }
        if (!"0".equals(user.getStatus())) {
            return PointsResult.fail("USER_DISABLED", "用户不可用");
        }

        // 2. 规则校验
        PointsRule rule = pointsRuleService.getRuleByCode(ruleCode);
        if (rule == null) {
            return PointsResult.fail("RULE_NOT_FOUND", "积分规则不存在");
        }
        if (!"0".equals(rule.getStatus())) {
            return PointsResult.fail("RULE_DISABLED", "积分规则已停用");
        }

        // 3. 计算本次变动值：优先用入参，否则用规则默认
        Integer actualChange = (changeAmount != null) ? changeAmount : rule.getPointsValue();
        if (actualChange == null || actualChange == 0) {
            return PointsResult.fail("POINTS_ZERO", "积分变动值无效");
        }

        // 4. 限频校验
        if (!pointsRuleService.checkLimit(userId, ruleCode, rule)) {
            return PointsResult.fail("LIMIT_REACHED", "已达到领取/扣减限频");
        }

        // 5. 累计上限校验（仅正向积分）
        if (!pointsRuleService.checkMaxAmount(userId, ruleCode, actualChange, rule)) {
            return PointsResult.fail("MAX_AMOUNT_REACHED", "已达到累计上限");
        }

        // 6. 余额校验（扣减场景）
        Integer current = pointsService.getUserPoints(userId);
        if (current == null) {
            current = 0;
        }
        long projectedBalance = (long) current + actualChange;
        if (projectedBalance > Integer.MAX_VALUE || projectedBalance < Integer.MIN_VALUE) {
            return PointsResult.fail("POINTS_BALANCE_OUT_OF_RANGE", "积分余额超出可表示范围");
        }
        if (projectedBalance < 0) {
            return PointsResult.fail("INSUFFICIENT_BALANCE", "积分余额不足");
        }

        // 7. 执行积分变更（可改为异步/事务）
        try {
            com.wx.fbsir.common.core.domain.AjaxResult result = pointsService.changePoints(userId, ruleCode, actualChange);
            if (result.isSuccess()) {
                Object balanceAfter = result.get("data");
                if (!(balanceAfter instanceof Integer)) {
                    return PointsResult.fail("POINTS_ERROR", "POINTS_COMMITTED_BALANCE_MISSING");
                }
                return PointsResult.ok(actualChange, (Integer) balanceAfter);
            } else {
                return PointsResult.fail("POINTS_ERROR", (String) result.get("msg"));
            }
        } catch (Exception ex) {
            return PointsResult.fail("POINTS_ERROR", "积分处理失败：" + ex.getMessage());
        }
    }

    /**
     * 业务方使用示例：
     * 
     * PointsResult r = pointsPrecheckService.tryChangePoints(userId, "USE_DAILY_ASSISTANT", null);
     * if (!r.isSuccess()) {
     *     return AjaxResult.error(r.getCode(), r.getMsg()); // 直接拦截
     * }
     * // 通过校验后再执行核心业务（如日更助手生成）
     */
}

