package com.wx.fbsir.business.fbs.service.impl;

import com.wx.fbsir.business.board.credit.service.SkillConsumeCreditWriter;
import com.wx.fbsir.business.fbs.domain.entity.*;
import com.wx.fbsir.business.fbs.domain.enums.UsageStatus;
import com.wx.fbsir.business.fbs.dto.ComprehensiveRightsResult;
import com.wx.fbsir.business.fbs.dto.ConsumeResult;
import com.wx.fbsir.business.fbs.dto.business.wecom.CommercialHubSyncContext;
import com.wx.fbsir.business.fbs.mapper.*;
import com.wx.fbsir.business.fbs.service.RightsCheckService;
import com.wx.fbsir.business.fbs.service.SkillConsumeService;
import com.wx.fbsir.business.fbs.service.WecomBusinessSyncService;
import com.wx.fbsir.business.point.domain.PointsRule;
import com.wx.fbsir.business.point.mapper.PointsRuleMapper;
import com.wx.fbsir.business.point.service.IPointsService;
import com.wx.fbsir.common.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Skill 消费统一入口服务实现
 *
 * 内部执行流程（design.md §4.4）：
 *  1. 查 fbs_scene_pack（packCode → packId, points_rule_code）
 *  2. 从 wx_points_rule 读取 amount（points_rule_code=NULL 则 免费包）
 *  3. comprehensiveCheck（全部 Fail-Closed）
 *  4. 双候选开关均开启时调用 042 v2 writer；否则幂等写入 legacy usage（status=0）
 *  5. legacy 路径调用 IPointsService.changePoints 轻量重载（非免费包）
 *  6. 更新 fbs_skill_usage_record（status=1）
 *  7. 失败时更新 status=2，返回 failReason
 *
 * @author FBSir
 * @date 2026-04-08
 */
@Service
public class SkillConsumeServiceImpl implements SkillConsumeService {

    private static final Logger log = LoggerFactory.getLogger(SkillConsumeServiceImpl.class);
    private static final String USAGE_SCOPE_MISMATCH = "SKILL_USAGE_RECORD_SCOPE_MISMATCH";
    private static final String ENTERPRISE_USAGE_SCOPE_UNVERIFIED =
            "SKILL_ENTERPRISE_USAGE_REPLAY_SCOPE_UNVERIFIED";
    private static final String ENTERPRISE_MEMBERSHIP_SCOPE_AMBIGUOUS =
            "SKILL_ENTERPRISE_MEMBERSHIP_SCOPE_AMBIGUOUS";
    private static final String USAGE_RECORD_TERMINAL_CAS_CONFLICT =
            "SKILL_USAGE_RECORD_TERMINAL_CAS_CONFLICT";
    private static final String SKILL_CONSUME_CREDIT_WRITER_NOT_READY =
            "SKILL_CONSUME_CREDIT_WRITER_NOT_READY";
    private static final String SKILL_POINTS_RULE_AMOUNT_INVALID =
            "SKILL_POINTS_RULE_AMOUNT_INVALID";
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

    @Autowired(required = false)
    private SkillConsumeCreditWriter skillConsumeCreditWriter;

    @Autowired(required = false)
    private FbsEnterpriseMapper enterpriseMapper;

    @Autowired(required = false)
    private FbsEnterpriseMemberMapper enterpriseMemberMapper;

    @Autowired(required = false)
    private FbsEnterprisePackMapper enterprisePackMapper;

    @Autowired(required = false)
    private FbsMemberPackMapper memberPackMapper;

    @Autowired(required = false)
    private WecomBusinessSyncService wecomBusinessSyncService;

    /**
     * The two flags deliberately form an AND gate.  The existing credit-candidate
     * admin/read surface can be enabled alone without activating a different
     * personal-consume writer.
     */
    @Value("${fbsir.independent-board.credit-ledger-candidate.enabled:false}")
    private boolean creditLedgerCandidateEnabled;

    @Value("${fbsir.independent-board.skill-consume-credit-writer.enabled:false}")
    private boolean skillConsumeCreditWriterEnabled;

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

        // ---- hostType=ENTERPRISE：走企业配额路径 ----
        if ("ENTERPRISE".equalsIgnoreCase(hostType)) {
            return consumeEnterprise(userId, packCode, skillCode, usageRecordId, hostType, hostSessionId);
        }

        // ---- WORKBUDDY 路径（原 OpenSpec #1 个人授权路径）----

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
            long absoluteAmount = rule.getPointsValue() == null
                    ? 0L : Math.abs(rule.getPointsValue().longValue());
            if (absoluteAmount > Integer.MAX_VALUE) {
                return ConsumeResult.fail(usageRecordId, SKILL_POINTS_RULE_AMOUNT_INVALID);
            }
            pointsAmount = Math.toIntExact(absoluteAmount);
        }
        // pointsRuleCode = NULL → 免费包，pointsAmount = 0

        // ---- 步骤 3：comprehensiveCheck（全部 Fail-Closed）----
        ComprehensiveRightsResult checkResult = rightsCheckService.comprehensiveCheck(
                userId, packCode, authCode, hostType, usageRecordId);
        if (!checkResult.isPass()) {
            return ConsumeResult.fail(usageRecordId, checkResult.getFailReason());
        }

        // The v2 writer is reachable only through the explicit two-flag AND
        // gate.  It owns usage idempotency, projection mutation and terminal
        // status in one isolated transaction.  Never fall back to the legacy
        // writer after this branch has been selected.
        if (pointsAmount > 0 && creditLedgerCandidateEnabled && skillConsumeCreditWriterEnabled) {
            if (skillConsumeCreditWriter == null) {
                return ConsumeResult.fail(usageRecordId, SKILL_CONSUME_CREDIT_WRITER_NOT_READY);
            }
            return skillConsumeCreditWriter.consume(
                    userId, usageRecordId, packId, pack.getCurrentVersion(), skillCode,
                    pointsRuleCode, pointsAmount, normalizeHostType(hostType), hostSessionId);
        }

        // ---- 步骤 4：幂等写入 fbs_skill_usage_record（status=0）----
        // 幂等：若 usageRecordId 已存在且 status=0 → 继续（视为重试）
        //       若 usageRecordId 已存在且 status=1/2 → 直接返回（已处理）
        FbsSkillUsageRecord existingRecord = usageRecordMapper.selectByRecordId(usageRecordId);
        if (existingRecord != null) {
            if (!isSameUsageScope(existingRecord, userId, packId, skillCode, hostType, hostSessionId)) {
                log.warn("拒绝跨范围 usageRecordId 重放 usageRecordId={}, requestedUserId={}, existingUserId={}",
                        usageRecordId, userId, existingRecord.getUserId());
                return ConsumeResult.fail(usageRecordId, USAGE_SCOPE_MISMATCH);
            }
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
        markUsageSucceededOrThrow(usageRecordId);

        log.info("Skill消费成功 userId={}, packCode={}, usageRecordId={}, pointsAmount={}, remainPoints={}",
                userId, packCode, usageRecordId, pointsAmount, remainPoints);

        // ---- 步骤 7：同步到企微智能表格（commercial_hub）----
        syncPersonalConsumptionBestEffort(
                userId, packCode, pointsAmount, remainPoints, hostType,
                usageRecordId, pack, authCode, pointsRuleCode);

        return ConsumeResult.success(usageRecordId, remainPoints);
    }

    private void syncPersonalConsumptionBestEffort(
            Long userId, String packCode, int pointsAmount, Integer remainPoints,
            String hostType, String usageRecordId, FbsScenePack pack,
            String authCode, String pointsRuleCode) {
        try {
            if (wecomBusinessSyncService != null) {
                CommercialHubSyncContext syncCtx = CommercialHubSyncContext.builder()
                        .userId(userId)
                        .packCode(packCode)
                        .pointsAmount(pointsAmount)
                        .remainPoints(remainPoints)
                        .hostType(hostType != null ? hostType : "WORKBUDDY")
                        .usageRecordId(usageRecordId)
                        .packId(pack.getId())
                        .authCode(authCode)
                        .pointsRuleCode(pointsRuleCode)
                        .packType(pack.getPackType() != null ? pack.getPackType() : 0)
                        .build();
                wecomBusinessSyncService.syncCommercialHub(syncCtx);
            }
        } catch (Exception e) {
            // 同步失败不影响业务
            log.warn("同步积分消费到企微失败 userId={}, packCode={}", userId, packCode, e);
        }
    }

    /**
     * 将使用记录从处理中原子推进到成功。
     *
     * <p>成功消费的业务写入与 usage 终态必须同属 {@link #consume(Long, String, String,
     * String, String, String, String)} 的事务；若 CAS 未命中，抛出运行时异常以回滚扣积分或配额增量，
     * 绝不能把未完成的终态伪装为成功。</p>
     */
    private void markUsageSucceededOrThrow(String usageRecordId) {
        int affected = usageRecordMapper.updateStatusByRecordId(usageRecordId,
                UsageStatus.SUCCESS.getCode(), null);
        if (affected != 1) {
            log.error("Skill消费终态CAS失败 usageRecordId={}, affected={}", usageRecordId, affected);
            throw new ServiceException(USAGE_RECORD_TERMINAL_CAS_CONFLICT, 409);
        }
    }

    // =====================================================================
    // consumeEnterprise：企业配额路径（hostType=ENTERPRISE）
    // 配额扣减统一在 fbs_enterprise_pack.usedQuota，不扣个人积分
    // =====================================================================

    /**
     * 企业成员消费场景包（走企业配额路径）
     * hostType=ENTERPRISE 时强制走此路径，不 fallback 到个人授权
     *
     * 步骤：
     *  1. 查场景包
     *  2. 查企业成员
     *  3. 校验企业状态（Fail-Closed）
     *  4. 校验企业包存在且状态正常
     *  5. 校验企业配额充足（usedQuota < packQuota）
     *  6. 幂等写入 usage_record（status=0）
     *  7. usedQuota++（企业级原子操作）
     *  8. 更新 usage_record（status=1）
     */
    private ConsumeResult consumeEnterprise(Long userId, String packCode, String skillCode,
                                            String usageRecordId, String hostType,
                                            String hostSessionId) {
        // ---- 步骤 1：查场景包 ----
        FbsScenePack pack = scenePackMapper.selectByPackCode(packCode);
        if (pack == null) {
            return ConsumeResult.fail(usageRecordId, "场景包不存在: " + packCode);
        }
        if (pack.getStatus() == null || pack.getStatus() != 1) {
            return ConsumeResult.fail(usageRecordId, "场景包未发布或已下架: " + packCode);
        }

        // 旧表未持久化 enterprise/member 快照，任何企业旧记录都不能证明原租户范围。
        // 先在零写状态下拒绝重放，避免先写配额耗尽失败记录或误报幂等成功。
        FbsSkillUsageRecord existingRecord = usageRecordMapper.selectByRecordId(usageRecordId);
        if (existingRecord != null) {
            if (!isSameUsageScope(existingRecord, userId, pack.getId(), skillCode,
                    hostType, hostSessionId)) {
                log.warn("拒绝企业路径跨范围 usageRecordId 重放 usageRecordId={}, requestedUserId={}, existingUserId={}",
                        usageRecordId, userId, existingRecord.getUserId());
                return ConsumeResult.fail(usageRecordId, USAGE_SCOPE_MISMATCH);
            }
            return ConsumeResult.fail(usageRecordId, ENTERPRISE_USAGE_SCOPE_UNVERIFIED);
        }

        // ---- 步骤 2：查企业成员 ----
        if (enterpriseMemberMapper == null || enterpriseMapper == null
                || enterprisePackMapper == null || memberPackMapper == null) {
            return ConsumeResult.fail(usageRecordId, "企业模块未完整初始化");
        }
        List<FbsEnterpriseMember> members = enterpriseMemberMapper.selectActiveByUserId(userId);
        if (members == null || members.isEmpty()) {
            return ConsumeResult.fail(usageRecordId, "用户不是企业成员");
        }
        if (members.size() != 1) {
            return ConsumeResult.fail(usageRecordId, ENTERPRISE_MEMBERSHIP_SCOPE_AMBIGUOUS);
        }
        // 只有唯一活动企业成员身份时才允许继续，禁止无序选择任意企业。
        FbsEnterpriseMember member = members.get(0);

        // ---- 步骤 3：校验企业状态 ----
        FbsEnterprise enterprise = enterpriseMapper.selectById(member.getEnterpriseId());
        if (enterprise == null) {
            return ConsumeResult.fail(usageRecordId, "企业不存在");
        }
        if (enterprise.getStatus() != null && enterprise.getStatus() == 2) {
            return ConsumeResult.fail(usageRecordId, "企业账户已禁用");
        }

        // ---- 步骤 4：校验企业包 ----
        FbsEnterprisePack enterprisePack = enterprisePackMapper
            .selectByEnterpriseAndPack(member.getEnterpriseId(), pack.getId());
        if (enterprisePack == null) {
            return ConsumeResult.fail(usageRecordId, "企业未获此场景包授权");
        }
        if (enterprisePack.getStatus() == null || enterprisePack.getStatus() != 1) {
            return ConsumeResult.fail(usageRecordId, "企业包状态不可用");
        }
        Long enterprisePackId = enterprisePack.getId();

        // ---- 步骤 4.5：校验成员授权凭证（fbs_member_pack）----
        // 成员授权凭证：用户必须在此场景包上有一个有效的 fbs_member_pack 记录
        // 若企业包被撤销（status=3），memberPack 也应已被级联撤销（status=3）
        FbsMemberPack memberPack = memberPackMapper
                .selectActiveByMemberIdAndPackId(member.getId(), pack.getId());
        if (memberPack == null) {
            return ConsumeResult.fail(usageRecordId, "用户未获此场景包成员授权");
        }
        if (memberPack.getStatus() == null || memberPack.getStatus() != 1) {
            return ConsumeResult.fail(usageRecordId, "成员授权已失效");
        }

        // ---- 步骤 5：校验配额 ----
        int packQuota = enterprisePack.getPackQuota() != null ? enterprisePack.getPackQuota() : 0;
        int usedQuota = enterprisePack.getUsedQuota() != null ? enterprisePack.getUsedQuota() : 0;
        if (usedQuota >= packQuota) {
            String reason = "企业配额已用尽，请联系管理员";
            // 写入失败记录
            writeFailedUsageRecord(userId, pack, usageRecordId, hostType, hostSessionId, skillCode, reason);
            return ConsumeResult.fail(usageRecordId, reason);
        }

        // ---- 步骤 6：首次写入 usage_record（status=0）----
        FbsSkillUsageRecord record = new FbsSkillUsageRecord();
        record.setUsageRecordId(usageRecordId);
        record.setUserId(userId);
        record.setHostType(hostType);
        record.setHostSessionId(hostSessionId);
        record.setSkillCode(skillCode);
        record.setPackId(pack.getId());
        record.setPackVersion(pack.getCurrentVersion());
        record.setPointsAmount(0); // 企业路径不扣积分
        record.setStatus(UsageStatus.IN_PROGRESS.getCode());
        record.setStartTime(new Date());
        usageRecordMapper.insertUsageRecord(record);

        // ---- 步骤 7：扣减企业配额（usedQuota++，原子操作）----
        int updated = enterprisePackMapper.incrementUsedQuota(enterprisePackId);
        if (updated <= 0) {
            // 并发情况下配额可能刚好用尽，回滚
            usageRecordMapper.updateStatusByRecordId(usageRecordId,
                    UsageStatus.FAILED.getCode(), "企业配额已用尽（并发）");
            return ConsumeResult.fail(usageRecordId, "企业配额已用尽，请联系管理员");
        }

        // ---- 步骤 8：重新查询企业包，获取增量后的最新 usedQuota ----
        // P1-1 修复：不能用旧的 enterprisePack 对象计算剩余额度（该对象 usedQuota 未更新）
        enterprisePack = enterprisePackMapper.selectById(enterprisePackId);
        if (enterprisePack == null) {
            // 理论上不应该发生，保守处理
            log.warn("企业配额消费成功但无法重新查询企业包记录 enterprisePackId={}", enterprisePackId);
            markUsageSucceededOrThrow(usageRecordId);
            return ConsumeResult.success(usageRecordId, 0);
        }

        // ---- 步骤 9：更新 usage_record（status=1）----
        markUsageSucceededOrThrow(usageRecordId);

        int remain = computeRemainQuota(enterprisePack);
        log.info("企业配额消费成功 userId={}, enterpriseId={}, packCode={}, usageRecordId={}, remainQuota={}",
                userId, member.getEnterpriseId(), packCode, usageRecordId, remain);

        // ---- 步骤 10：同步到企微智能表格（commercial_hub）----
        // ⚠️ OpenSpec #13：新增企业消费路径同步（之前完全没有调用！）
        try {
            if (wecomBusinessSyncService != null) {
                CommercialHubSyncContext syncCtx = CommercialHubSyncContext.builder()
                        .userId(userId)
                        .packCode(packCode)
                        .pointsAmount(0)              // 企业路径不扣个人积分
                        .remainPoints(remain)         // 剩余企业配额
                        .hostType("ENTERPRISE")
                        .usageRecordId(usageRecordId)
                        .packId(pack.getId())
                        .authCode(null)               // 企业路径无授权码
                        .pointsRuleCode(pack.getPointsRuleCode())
                        .packType(pack.getPackType() != null ? pack.getPackType() : 0)
                        .build();
                wecomBusinessSyncService.syncCommercialHub(syncCtx);
            }
        } catch (Exception e) {
            // 同步失败不影响配额扣减
            log.warn("同步企业消费到企微失败 userId={}, packCode={}", userId, packCode, e);
        }

        return ConsumeResult.success(usageRecordId, remain);
    }

    /** 计算企业包剩余配额 */
    private int computeRemainQuota(FbsEnterprisePack ep) {
        int packQuota = ep.getPackQuota() != null ? ep.getPackQuota() : 0;
        int usedQuota = ep.getUsedQuota() != null ? ep.getUsedQuota() : 0;
        return Math.max(0, packQuota - usedQuota);
    }

    private boolean isSameUsageScope(FbsSkillUsageRecord record, Long userId, Long packId,
                                     String skillCode, String hostType, String hostSessionId) {
        return Objects.equals(record.getUserId(), userId)
                && Objects.equals(record.getPackId(), packId)
                && Objects.equals(record.getSkillCode(), skillCode)
                && Objects.equals(normalizeHostType(record.getHostType()), normalizeHostType(hostType))
                && Objects.equals(record.getHostSessionId(), hostSessionId);
    }

    private String normalizeHostType(String hostType) {
        return StringUtils.hasText(hostType)
                ? hostType.trim().toUpperCase(Locale.ROOT)
                : "WORKBUDDY";
    }

    /** 写入失败的使用记录 */
    private void writeFailedUsageRecord(Long userId, FbsScenePack pack, String usageRecordId,
                                       String hostType, String hostSessionId,
                                       String skillCode, String reason) {
        FbsSkillUsageRecord record = new FbsSkillUsageRecord();
        record.setUsageRecordId(usageRecordId);
        record.setUserId(userId);
        record.setHostType(hostType);
        record.setHostSessionId(hostSessionId);
        record.setSkillCode(skillCode);
        record.setPackId(pack.getId());
        record.setPackVersion(pack.getCurrentVersion());
        record.setPointsAmount(0);
        record.setStatus(UsageStatus.FAILED.getCode());
        record.setStartTime(new Date());
        record.setErrorMessage(reason);
        try {
            usageRecordMapper.insertUsageRecord(record);
        } catch (Exception e) {
            log.warn("写入企业配额失败记录异常 usageRecordId={}, reason={}", usageRecordId, reason);
        }
    }
}
