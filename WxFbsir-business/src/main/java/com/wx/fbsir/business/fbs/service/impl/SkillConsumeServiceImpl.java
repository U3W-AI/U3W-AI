package com.wx.fbsir.business.fbs.service.impl;

import com.wx.fbsir.business.fbs.domain.entity.FbsScenePack;
import com.wx.fbsir.business.fbs.domain.entity.FbsSkillUsageRecord;
import com.wx.fbsir.business.fbs.domain.enums.UsageStatus;
import com.wx.fbsir.business.fbs.dto.ComprehensiveRightsResult;
import com.wx.fbsir.business.fbs.dto.ConsumeResult;
import com.wx.fbsir.business.fbs.mapper.FbsScenePackMapper;
import com.wx.fbsir.business.fbs.mapper.FbsSkillUsageRecordMapper;
import com.wx.fbsir.business.fbs.service.RightsCheckService;
import com.wx.fbsir.business.fbs.service.SkillConsumeService;
import com.wx.fbsir.business.point.domain.PointsRule;
import com.wx.fbsir.business.point.mapper.PointsRuleMapper;
import com.wx.fbsir.business.point.service.IPointsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;

/**
 * Skill 消费统一入口服务实现
 *
 * 内部执行流程（design.md §4.4）：
 *  1. 查 fbs_scene_pack（packCode → packId, points_rule_code）
 *  2. 从 wx_points_rule 读取 amount（points_rule_code=NULL 则 免费包）
 *  3. comprehensiveCheck（全部 Fail-Closed）
 *  4. 幂等写入 fbs_skill_usage_record（status=0）
 *  5. 调用 IPointsService.changePoints 轻量重载（非免费包）
 *  6. 更新 fbs_skill_usage_record（status=1）
 *  7. 失败时更新 status=2，返回 failReason
 *
 * @author wxfbsir
 * @date 2026-04-08
 */
@Service
public class SkillConsumeServiceImpl implements SkillConsumeService {

    private static final Logger log = LoggerFactory.getLogger(SkillConsumeServiceImpl.class);

    @Autowired
    private FbsScenePackMapper scenePackMapper;

    @Autowired
    private FbsSkillUsageRecordMapper usageRecordMapper;

    @Autowired
    private PointsRuleMapper pointsRuleMapper;

    @Autowired
    private RightsCheckService rightsCheckService;

    @Autowired
    private IPointsService pointsService;

    // =====================================================================
    // consume
    // =====================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConsumeResult consume(Long userId, String packCode, String skillCode,
                                 String usageRecordId, String hostType,
                                 String hostSessionId, String authCode) {
        // 参数基础校验
        if (userId == null || !StringUtils.hasText(packCode) || !StringUtils.hasText(usageRecordId)) {
            return ConsumeResult.fail(usageRecordId, "参数不能为空");
        }

        // ---- 步骤 1：查 fbs_scene_pack ----
        FbsScenePack pack = scenePackMapper.selectByPackCode(packCode);
        if (pack == null) {
            return ConsumeResult.fail(usageRecordId, "场景包不存在: " + packCode);
        }
        if (pack.getStatus() == null || pack.getStatus() != 1) {
            return ConsumeResult.fail(usageRecordId, "场景包未发布或已下架: " + packCode);
        }

        Long packId = pack.getId();
        String pointsRuleCode = pack.getPointsRuleCode();
        int pointsAmount = 0;

        // ---- 步骤 2：从 wx_points_rule 读取 amount ----
        if (StringUtils.hasText(pointsRuleCode)) {
            PointsRule rule = pointsRuleMapper.selectPointsRuleByRuleCode(pointsRuleCode);
            if (rule == null) {
                return ConsumeResult.fail(usageRecordId, "积分规则不存在: " + pointsRuleCode);
            }
            // ⚠️ wx_points_rule.status = "0" 为启用（与惯例相反）
            if (!"0".equals(rule.getStatus())) {
                return ConsumeResult.fail(usageRecordId, "积分规则已停用: " + pointsRuleCode);
            }
            pointsAmount = rule.getPointsValue() == null ? 0 : Math.abs(rule.getPointsValue());
        }
        // pointsRuleCode = NULL → 免费包，pointsAmount = 0

        // ---- 步骤 3：comprehensiveCheck（全部 Fail-Closed）----
        ComprehensiveRightsResult checkResult = rightsCheckService.comprehensiveCheck(
                userId, packCode, authCode, hostType, usageRecordId);
        if (!checkResult.isPass()) {
            return ConsumeResult.fail(usageRecordId, checkResult.getFailReason());
        }

        // ---- 步骤 4：幂等写入 fbs_skill_usage_record（status=0）----
        // 幂等：若 usageRecordId 已存在且 status=0 → 继续（视为重试）
        //       若 usageRecordId 已存在且 status=1/2 → 直接返回（已处理）
        FbsSkillUsageRecord existingRecord = usageRecordMapper.selectByRecordId(usageRecordId);
        if (existingRecord != null) {
            if (existingRecord.getStatus() != null && existingRecord.getStatus() == UsageStatus.SUCCESS.getCode()) {
                // 已成功，直接返回（幂等）
                Integer remain = pointsService.getUserPoints(userId);
                return ConsumeResult.success(usageRecordId, remain);
            }
            if (existingRecord.getStatus() != null && existingRecord.getStatus() == UsageStatus.FAILED.getCode()) {
                // 已失败，不允许重复失败（需人工介入或新任务ID）
                return ConsumeResult.fail(usageRecordId, "该使用记录已失败，不允许重复提交");
            }
            // status=0 进行中：直接返回，不继续扣积分（避免重复扣款）
            return ConsumeResult.fail(usageRecordId, "该使用记录正在处理中，请勿重复提交");
        } else {
            FbsSkillUsageRecord record = new FbsSkillUsageRecord();
            record.setUsageRecordId(usageRecordId);
            record.setUserId(userId);
            record.setHostType(hostType);
            record.setHostSessionId(hostSessionId);
            record.setSkillCode(skillCode);
            record.setPackId(packId);
            record.setPackVersion(pack.getCurrentVersion());
            record.setPointsAmount(pointsAmount);
            record.setStatus(UsageStatus.IN_PROGRESS.getCode());
            record.setStartTime(new Date());
            usageRecordMapper.insertUsageRecord(record);
        }

        Integer remainPoints = null;
        if (pointsAmount > 0) {
            // 调用 IPointsService.changePoints(userId, ruleCode, -amount, scenePackId, usageRecordId)
            var pointsResult = pointsService.changePoints(userId, pointsRuleCode,
                    -pointsAmount, packId, usageRecordId);
            if (pointsResult == null || !pointsResult.isSuccess()) {
                // AjaxResult 是 HashMap 风格，无 getMsg()，通过 get(MSG_TAG) 取值
                String reason = pointsResult != null
                        ? String.valueOf(pointsResult.get(com.wx.fbsir.common.core.domain.AjaxResult.MSG_TAG))
                        : "积分扣减失败";
                log.warn("Skill消费积分扣减失败 userId={}, ruleCode={}, amount={}, reason={}",
                        userId, pointsRuleCode, pointsAmount, reason);
                // 失败：更新 usage_record status=2
                usageRecordMapper.updateStatusByRecordId(usageRecordId,
                        UsageStatus.FAILED.getCode(), reason);
                return ConsumeResult.fail(usageRecordId, reason);
            }
            remainPoints = pointsService.getUserPoints(userId);
        } else {
            // 免费包：pointsAmount = 0，直接视为成功
            remainPoints = pointsService.getUserPoints(userId);
        }

        // ---- 步骤 6：更新 fbs_skill_usage_record（status=1）----
        usageRecordMapper.updateStatusByRecordId(usageRecordId,
                UsageStatus.SUCCESS.getCode(), null);

        log.info("Skill消费成功 userId={}, packCode={}, usageRecordId={}, pointsAmount={}, remainPoints={}",
                userId, packCode, usageRecordId, pointsAmount, remainPoints);

        return ConsumeResult.success(usageRecordId, remainPoints);
    }
}
