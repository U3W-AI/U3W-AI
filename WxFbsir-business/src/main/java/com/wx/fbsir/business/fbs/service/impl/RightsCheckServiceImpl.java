package com.wx.fbsir.business.fbs.service.impl;

import com.wx.fbsir.business.fbs.domain.entity.FbsAuthCode;
import com.wx.fbsir.business.fbs.domain.entity.FbsScenePack;
import com.wx.fbsir.business.fbs.domain.entity.FbsUserPack;
import com.wx.fbsir.business.fbs.dto.ComprehensiveRightsResult;
import com.wx.fbsir.business.fbs.dto.RightsCheckResult;
import com.wx.fbsir.business.fbs.mapper.FbsAuthCodeMapper;
import com.wx.fbsir.business.fbs.mapper.FbsScenePackMapper;
import com.wx.fbsir.business.fbs.mapper.FbsUserPackMapper;
import com.wx.fbsir.business.fbs.service.RightsCheckService;
import com.wx.fbsir.business.point.domain.PointsRule;
import com.wx.fbsir.business.point.mapper.PointsRuleMapper;
import com.wx.fbsir.business.point.service.IPointsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;

/**
 * 权益校验服务实现
 *
 * MVP 策略：全部 Fail-Closed，任意校验失败立即返回拒绝。
 *
 * @author wxfbsir
 * @date 2026-04-08
 */
@Service
public class RightsCheckServiceImpl implements RightsCheckService {

    @Autowired
    private FbsScenePackMapper scenePackMapper;

    @Autowired
    private FbsUserPackMapper userPackMapper;

    @Autowired
    private FbsAuthCodeMapper authCodeMapper;

    @Autowired
    private PointsRuleMapper pointsRuleMapper;

    @Autowired
    private IPointsService pointsService;

    // =====================================================================
    // checkScenePack
    // =====================================================================

    @Override
    public RightsCheckResult checkScenePack(Long userId, Long packId) {
        if (userId == null || packId == null) {
            return RightsCheckResult.fail("参数不能为空");
        }

        // 1. 场景包是否存在且已发布
        FbsScenePack pack = scenePackMapper.selectById(packId);
        if (pack == null) {
            return RightsCheckResult.fail("场景包不存在");
        }
        if (pack.getStatus() == null || pack.getStatus() != 1) {
            return RightsCheckResult.fail("场景包未发布或已下架");
        }

        // 2. 用户是否有有效权益（fbs_user_pack status=1 且未过期）
        FbsUserPack userPack = userPackMapper.selectActiveByUserIdAndPackId(userId, packId);
        if (userPack == null) {
            return RightsCheckResult.fail("用户无该场景包权益");
        }

        return RightsCheckResult.pass();
    }

    // =====================================================================
    // checkAuthCode
    // =====================================================================

    @Override
    public RightsCheckResult checkAuthCode(String authCode) {
        if (!StringUtils.hasText(authCode)) {
            return RightsCheckResult.fail("授权码不能为空");
        }

        FbsAuthCode code = authCodeMapper.selectByAuthCode(authCode);
        if (code == null) {
            return RightsCheckResult.fail("授权码不存在");
        }

        // available=0：管理员禁用
        if (code.getAvailable() == null || code.getAvailable() != 1) {
            return RightsCheckResult.fail("授权码已禁用");
        }

        // status 必须在 {0,1}（未激活或已激活）才可通过；2/3/4 均拒绝
        int status = code.getStatus() == null ? -1 : code.getStatus();
        if (status != 0 && status != 1) {
            String desc = getAuthCodeStatusDesc(status);
            return RightsCheckResult.fail("授权码" + desc);
        }

        // deadline 校验
        if (code.getDeadline() != null && new Date().after(code.getDeadline())) {
            return RightsCheckResult.fail("授权码已过期");
        }

        // 激活次数校验
        int activatedCount = code.getActivatedCount() == null ? 0 : code.getActivatedCount();
        int maxActivations = code.getMaxActivations() == null ? 1 : code.getMaxActivations();
        if (activatedCount >= maxActivations) {
            return RightsCheckResult.fail("授权码已达激活次数上限");
        }

        return RightsCheckResult.pass();
    }

    private String getAuthCodeStatusDesc(int status) {
        switch (status) {
            case 2: return "已用尽";
            case 3: return "已过期";
            case 4: return "已撤销";
            default: return "状态异常";
        }
    }

    // =====================================================================
    // checkPoints
    // =====================================================================

    @Override
    public RightsCheckResult checkPoints(Long userId, String ruleCode, Integer amount) {
        if (userId == null) {
            return RightsCheckResult.fail("用户ID不能为空");
        }
        // ruleCode = null：免费包，直接通过
        if (!StringUtils.hasText(ruleCode)) {
            return RightsCheckResult.pass();
        }

        // 查积分规则
        PointsRule rule = pointsRuleMapper.selectPointsRuleByRuleCode(ruleCode);
        if (rule == null) {
            return RightsCheckResult.fail("积分规则不存在: " + ruleCode);
        }
        // ⚠️ wx_points_rule.status = "0" 为启用（与惯例相反）
        if (!"0".equals(rule.getStatus())) {
            return RightsCheckResult.fail("积分规则已停用: " + ruleCode);
        }

        // 若 amount 未传，使用规则默认值（取绝对值，扣减场景 pointsValue 为负数）
        int required = amount != null ? amount : Math.abs(rule.getPointsValue() == null ? 0 : rule.getPointsValue());
        if (required <= 0) {
            // 免费或无效，直接通过
            return RightsCheckResult.pass();
        }

        // 查询用户积分余额
        Integer userPoints = pointsService.getUserPoints(userId);
        if (userPoints == null) {
            userPoints = 0;
        }

        if (userPoints < required) {
            return RightsCheckResult.fail("积分余额不足（需要" + required + "，现有" + userPoints + "）");
        }

        return RightsCheckResult.pass();
    }

    // =====================================================================
    // comprehensiveCheck
    // =====================================================================

    @Override
    public ComprehensiveRightsResult comprehensiveCheck(
            Long userId, String packCode, String authCode,
            String hostType, String taskId) {

        // 1. 根据 packCode 查场景包
        if (!StringUtils.hasText(packCode)) {
            return ComprehensiveRightsResult.fail("场景包编码不能为空");
        }
        FbsScenePack pack = scenePackMapper.selectByPackCode(packCode);
        if (pack == null) {
            return ComprehensiveRightsResult.fail("场景包不存在: " + packCode);
        }
        if (pack.getStatus() == null || pack.getStatus() != 1) {
            return ComprehensiveRightsResult.fail("场景包未发布或已下架");
        }

        // 2. 校验用户权益
        RightsCheckResult packCheck = checkScenePack(userId, pack.getId());
        if (!packCheck.isAllowed()) {
            return ComprehensiveRightsResult.fail(packCheck.getReason());
        }

        // 3. 校验授权码（如有）
        if (StringUtils.hasText(authCode)) {
            RightsCheckResult codeCheck = checkAuthCode(authCode);
            if (!codeCheck.isAllowed()) {
                return ComprehensiveRightsResult.fail(codeCheck.getReason());
            }
        }

        // 4. 积分校验（仅当 points_rule_code 不为空时）
        String pointsRuleCode = pack.getPointsRuleCode();
        Integer pointsAmount  = 0;

        if (StringUtils.hasText(pointsRuleCode)) {
            // 读取规则默认积分值作为 amount
            PointsRule rule = pointsRuleMapper.selectPointsRuleByRuleCode(pointsRuleCode);
            if (rule == null) {
                return ComprehensiveRightsResult.fail("积分规则不存在: " + pointsRuleCode);
            }
            if (!"0".equals(rule.getStatus())) {
                return ComprehensiveRightsResult.fail("积分规则已停用: " + pointsRuleCode);
            }
            pointsAmount = rule.getPointsValue() == null ? 0 : Math.abs(rule.getPointsValue());

            RightsCheckResult pointsCheck = checkPoints(userId, pointsRuleCode, pointsAmount);
            if (!pointsCheck.isAllowed()) {
                return ComprehensiveRightsResult.fail(pointsCheck.getReason());
            }
        }

        return ComprehensiveRightsResult.pass(pack.getId(), pointsRuleCode, pointsAmount);
    }
}
