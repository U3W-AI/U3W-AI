package com.wx.fbsir.business.fbs.service.impl;

import com.wx.fbsir.business.fbs.domain.entity.*;
import com.wx.fbsir.business.fbs.dto.ComprehensiveRightsResult;
import com.wx.fbsir.business.fbs.dto.RightsCheckResult;
import com.wx.fbsir.business.fbs.mapper.*;
import com.wx.fbsir.business.fbs.service.RightsCheckService;
import com.wx.fbsir.business.point.domain.PointsRule;
import com.wx.fbsir.business.point.mapper.PointsRuleMapper;
import com.wx.fbsir.business.point.service.IPointsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Objects;

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

    @Autowired(required = false)
    private com.wx.fbsir.business.fbs.mapper.FbsEnterpriseMapper enterpriseMapper;

    @Autowired(required = false)
    private com.wx.fbsir.business.fbs.mapper.FbsEnterpriseMemberMapper enterpriseMemberMapper;

    @Autowired(required = false)
    private com.wx.fbsir.business.fbs.mapper.FbsEnterprisePackMapper enterprisePackMapper;

    @Autowired(required = false)
    private com.wx.fbsir.business.fbs.mapper.FbsMemberPackMapper memberPackMapper;

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

        // 2. 根据 hostType 分支
        if ("ENTERPRISE".equalsIgnoreCase(hostType)) {
            return checkEnterprisePath(userId, pack);
        }

        // ---- WORKBUDDY 路径（OpenSpec #1 个人授权路径）----
        // 3. 校验用户权益
        RightsCheckResult packCheck = checkScenePack(userId, pack.getId());
        if (!packCheck.isAllowed()) {
            return ComprehensiveRightsResult.fail(packCheck.getReason());
        }

        // 4. 校验授权码（如有）
        if (StringUtils.hasText(authCode)) {
            RightsCheckResult codeCheck = checkAuthCode(authCode);
            if (!codeCheck.isAllowed()) {
                return ComprehensiveRightsResult.fail(codeCheck.getReason());
            }
        }

        // 5. 积分校验（仅当 points_rule_code 不为空时）
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

    // =====================================================================
    // 企业配额路径（hostType=ENTERPRISE）
    // hostType=ENTERPRISE 时强制走企业配额路径，互不 fallback
    // =====================================================================

    /**
     * 企业配额校验路径
     * 配额检查：remainQuota = packQuota - usedQuota，remainQuota > 0 才可通过
     */
    private ComprehensiveRightsResult checkEnterprisePath(Long userId, FbsScenePack pack) {
        // 1. 检查是否为正常企业成员
        if (enterpriseMemberMapper == null || enterprisePackMapper == null) {
            return ComprehensiveRightsResult.fail("企业模块未初始化");
        }

        // 查该用户是否属于某个正常企业
        // 注意：企业成员关系只通过 fbs_enterprise_member 查询
        // MVP 简化：取第一个 status=1 的企业成员记录
        FbsEnterpriseMember member = findActiveEnterpriseMember(userId);
        if (member == null) {
            return ComprehensiveRightsResult.fail("用户不是企业成员");
        }

        return checkEnterpriseScope(member.getEnterpriseId(), member.getId(), userId, pack, member);
    }

    @Override
    public ComprehensiveRightsResult checkEnterpriseScoped(
            Long enterpriseId, Long enterpriseMemberId, Long userId, String packCode) {
        if (enterpriseId == null || enterpriseMemberId == null || userId == null
                || !StringUtils.hasText(packCode)) {
            return ComprehensiveRightsResult.fail("企业权益作用域参数不完整");
        }
        FbsScenePack pack = scenePackMapper.selectByPackCode(packCode);
        if (pack == null || pack.getStatus() == null || pack.getStatus() != 1) {
            return ComprehensiveRightsResult.fail("场景包不存在、未发布或已下架");
        }
        return checkEnterpriseScope(enterpriseId, enterpriseMemberId, userId, pack, null);
    }

    /** Exact tenant/member/user check used by the SmartBot control plane. */
    private ComprehensiveRightsResult checkEnterpriseScope(
            Long enterpriseId, Long enterpriseMemberId, Long userId, FbsScenePack pack,
            FbsEnterpriseMember resolvedMember) {
        if (enterpriseMapper == null || enterpriseMemberMapper == null
                || enterprisePackMapper == null || memberPackMapper == null) {
            return ComprehensiveRightsResult.fail("企业权益模块未初始化");
        }

        FbsEnterpriseMember member = resolvedMember != null ? resolvedMember
                : enterpriseMemberMapper.selectById(enterpriseMemberId);
        if (member == null || !Objects.equals(enterpriseId, member.getEnterpriseId())
                || !Objects.equals(userId, member.getUserId())
                || member.getStatus() == null || member.getStatus() != 1
                || (member.getDelFlag() != null && !"0".equals(member.getDelFlag()))) {
            return ComprehensiveRightsResult.fail("企业成员作用域无效");
        }

        // 2. 检查企业是否正常
        FbsEnterprise enterprise = enterpriseMapper.selectById(enterpriseId);
        if (enterprise == null) {
            return ComprehensiveRightsResult.fail("企业不存在");
        }
        if (enterprise.getStatus() != null && enterprise.getStatus() == 2) {
            return ComprehensiveRightsResult.fail("企业账户已禁用");
        }
        if (enterprise.getStatus() == null || enterprise.getStatus() != 1
                || (enterprise.getDelFlag() != null && !"0".equals(enterprise.getDelFlag()))) {
            return ComprehensiveRightsResult.fail("企业账户不可用");
        }

        // 3. 查询企业是否已获此场景包
        FbsEnterprisePack enterprisePack = enterprisePackMapper
            .selectByEnterpriseAndPack(enterpriseId, pack.getId());
        if (enterprisePack == null) {
            return ComprehensiveRightsResult.fail("企业未获此场景包授权");
        }

        // 4. 检查企业包状态（仅 status=1 可用）
        if (enterprisePack.getStatus() == null || enterprisePack.getStatus() != 1) {
            return ComprehensiveRightsResult.fail("企业包状态不可用");
        }
        if (enterprisePack.getExpiryTime() != null && !enterprisePack.getExpiryTime().after(new Date())) {
            return ComprehensiveRightsResult.fail("企业包已过期");
        }

        // 4.5 检查成员授权凭证（fbs_member_pack）
        // 成员授权凭证：用户必须在 fbs_member_pack 上有一条 status=1 的活跃授权记录
        // 只有企业包授权（grantPack）时会自动创建此凭证；企业包撤销时会级联撤销（status=3）
        // 若凭证缺失或被撤销，即使企业包有效也拒绝消费
        FbsMemberPack memberPack = memberPackMapper
                .selectActiveByMemberIdAndPackId(enterpriseMemberId, pack.getId());
        if (memberPack == null || !Objects.equals(memberPack.getMemberId(), enterpriseMemberId)
                || !Objects.equals(memberPack.getPackId(), pack.getId())
                || !Objects.equals(memberPack.getEnterprisePackId(), enterprisePack.getId())
                || memberPack.getStatus() == null || memberPack.getStatus() != 1) {
            return ComprehensiveRightsResult.fail("成员授权已失效或不属于当前企业包");
        }

        // 5. 配额充足性检查（企业级，配额充足性检查）
        int packQuota = enterprisePack.getPackQuota() != null ? enterprisePack.getPackQuota() : 0;
        int usedQuota = enterprisePack.getUsedQuota() != null ? enterprisePack.getUsedQuota() : 0;
        if (usedQuota >= packQuota) {
            return ComprehensiveRightsResult.fail("企业配额已用尽，请联系管理员");
        }

        // 企业路径不扣积分，pointsAmount = 0
        // 通过后，consume 路径会直接 incrementUsedQuota
        return ComprehensiveRightsResult.pass(pack.getId(), null, 0);
    }

    /**
     * 查找用户所属的正常企业成员记录
     * MVP 简化：取第一条 status=1 的记录
     */
    private FbsEnterpriseMember findActiveEnterpriseMember(Long userId) {
        if (enterpriseMemberMapper == null) {
            return null;
        }
        List<FbsEnterpriseMember> members = enterpriseMemberMapper.selectActiveByUserId(userId);
        return (members != null && !members.isEmpty()) ? members.get(0) : null;
    }
}
