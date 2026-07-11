package com.wx.fbsir.business.fbs.service;

import com.wx.fbsir.business.fbs.dto.ComprehensiveRightsResult;
import com.wx.fbsir.business.fbs.dto.RightsCheckResult;

/**
 * 权益校验服务接口
 *
 * MVP 策略：全部 Fail-Closed，任意一项校验失败立即拒绝，不做 fail-open。
 *
 * TODO (OpenSpec #add-fbs-rights-foundation): 延期不做以下内容：
 *   - PointsRule 扩展字段（ruleScope、isFailOpen 等）→ 延期至后续 OpenSpec
 *   - fbs_enterprise / fbs_enterprise_user 表 → 延期至 OpenSpec #3（企业侧分发）
 *   - fbs_points_freeze 冻结/确认/回滚 → 延期至后续 OpenSpec
 *
 * @author FBSir
 * @date 2026-04-08
 */
public interface RightsCheckService {

    /**
     * 校验用户是否有权使用指定场景包
     *
     * @param userId 用户ID
     * @param packId 场景包ID
     * @return 校验结果
     */
    RightsCheckResult checkScenePack(Long userId, Long packId);

    /**
     * 校验授权码有效性
     * 有效条件：存在 + available=1 + status IN(0,1) + 未过期 + activated_count < max_activations
     *
     * @param authCode 授权码字符串
     * @return 校验结果
     */
    RightsCheckResult checkAuthCode(String authCode);

    /**
     * 校验用户积分是否充足（一次性扣减前预检）
     * amount 来源：wx_points_rule.points_value（通过 ruleCode 查询）
     * ⚠️ wx_points_rule.status="0" 为启用（与惯例相反），实现时使用 !"0".equals(rule.getStatus())
     *
     * @param userId   用户ID
     * @param ruleCode 积分规则编码
     * @param amount   需要积分数
     * @return 校验结果
     */
    RightsCheckResult checkPoints(Long userId, String ruleCode, Integer amount);

    /**
     * 综合校验（场景包 + 授权码 + 积分）
     * 流程：checkScenePack → checkAuthCode(如有) → checkPoints(如有规则)
     * 任意失败 → Fail-Closed，返回失败及原因
     *
     * @param userId    用户ID
     * @param packCode  场景包编码
     * @param authCode  授权码（可为 null）
     * @param hostType  宿主类型
     * @param taskId    任务幂等键
     * @return 综合校验结果（含 packId、pointsRuleCode、pointsAmount，供消费服务使用）
     */
    ComprehensiveRightsResult comprehensiveCheck(
            Long userId, String packCode, String authCode,
            String hostType, String taskId);

    /**
     * Read-only, fail-closed enterprise entitlement snapshot for an already
     * resolved tenant/member/run scope. Unlike {@link #comprehensiveCheck}, it
     * never infers a tenant by selecting a user's first enterprise membership.
     * It does not consume quota, points, authorizations, or create receipts.
     */
    ComprehensiveRightsResult checkEnterpriseScoped(
            Long enterpriseId, Long enterpriseMemberId, Long userId, String packCode);
}
