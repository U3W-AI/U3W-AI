package com.wx.fbsir.business.fbs.service;

import com.wx.fbsir.business.fbs.dto.ConsumeResult;

/**
 * Skill 消费统一入口服务接口
 *
 * 对应 POST /fbs/internal/usage/consume
 * 调用方只需一次调用，系统内部完成"综合校验 → 积分扣减 → 写使用记录"。
 *
 * @author wxfbsir
 * @date 2026-04-08
 */
public interface SkillConsumeService {

    /**
     * Skill 消费统一入口：综合校验 → 积分扣减 → 写使用记录
     *
     * 内部执行流程（见 design.md §4.4）：
     *  1. 查 fbs_scene_pack（packCode → packId, points_rule_code）
     *  2. 从 wx_points_rule 读取 amount（points_rule_code=NULL 则 amount=0，免费包）
     *  3. comprehensiveCheck（全部 Fail-Closed）
     *  4. 幂等写入 fbs_skill_usage_record（status=0）
     *  5. 调用 IPointsService.changePoints 轻量重载（非免费包）
     *  6. 更新 fbs_skill_usage_record（status=1）
     *  7. 失败时更新 status=2，返回 failReason
     *
     * @param userId          用户ID
     * @param packCode        场景包编码
     * @param skillCode       技能编码
     * @param usageRecordId   使用记录幂等键（调用方生成，建议 UUID 或 taskId）
     * @param hostType        宿主类型（WORKBUDDY/STANDALONE/API）
     * @param hostSessionId   宿主会话ID（可空）
     * @param authCode        授权码（可空）
     * @return 消费结果
     */
    ConsumeResult consume(Long userId, String packCode, String skillCode,
                          String usageRecordId, String hostType,
                          String hostSessionId, String authCode);
}
