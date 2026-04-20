package com.wx.fbsir.business.fbs.service.impl;

import com.wx.fbsir.business.fbs.domain.entity.FbsAuthCode;
import com.wx.fbsir.business.fbs.domain.entity.FbsEnterprise;
import com.wx.fbsir.business.fbs.domain.entity.FbsEnterpriseMember;
import com.wx.fbsir.business.fbs.domain.entity.FbsScenePack;
import com.wx.fbsir.business.fbs.dto.business.wecom.CommercialHubSyncContext;
import com.wx.fbsir.business.fbs.dto.business.wecom.WecomSyncWriteResponse;
import com.wx.fbsir.business.fbs.mapper.FbsAuthCodeMapper;
import com.wx.fbsir.business.fbs.mapper.FbsEnterpriseMapper;
import com.wx.fbsir.business.fbs.mapper.FbsEnterpriseMemberMapper;
import com.wx.fbsir.business.fbs.service.WecomBusinessSyncService;
import com.wx.fbsir.business.fbs.service.WecomWriteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 企微业务同步服务实现
 *
 * OpenSpec #9: business-sync-to-wecom
 * OpenSpec #13: commercial_hub 字段补全（14 个空字段 + user_id 格式修复）
 *
 * 同步范围：commercial_hub（积分消费流水）+ entitlement（场景包积分规则）
 */
@Service
public class WecomBusinessSyncServiceImpl implements WecomBusinessSyncService {

    private static final Logger log = LoggerFactory.getLogger(WecomBusinessSyncServiceImpl.class);

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private WecomWriteService wecomWriteService;

    @Autowired(required = false)
    private FbsEnterpriseMemberMapper enterpriseMemberMapper;

    @Autowired(required = false)
    private FbsEnterpriseMapper enterpriseMapper;

    @Autowired(required = false)
    private FbsAuthCodeMapper authCodeMapper;

    // =====================================================================
    // syncCommercialHub（旧签名，兼容保留）
    // =====================================================================

    @Override
    @Deprecated
    public void syncCommercialHub(Long userId, String packCode, int pointsAmount, int remainPoints) {
        // 旧签名转调：构建最小 context，扩展字段为默认值
        CommercialHubSyncContext context = CommercialHubSyncContext.builder()
                .userId(userId)
                .packCode(packCode)
                .pointsAmount(pointsAmount)
                .remainPoints(remainPoints)
                .hostType("WORKBUDDY")
                .usageRecordId("")
                .packId(null)
                .authCode(null)
                .pointsRuleCode(null)
                .packType(0)
                .build();
        syncCommercialHub(context);
    }

    // =====================================================================
    // syncCommercialHub（新签名，完整上下文）
    // =====================================================================

    @Override
    public void syncCommercialHub(CommercialHubSyncContext context) {
        try {
            // 1. 构建记录
            Map<String, Object> record = buildCommercialHubRecord(context);

            // 2. 调用写入服务
            List<Map<String, Object>> records = Collections.singletonList(record);
            WecomSyncWriteResponse response = wecomWriteService.writeRecords("commercial_hub", records);

            // 3. 日志记录
            if (response.isSuccess()) {
                log.info("同步积分消费成功 userId={}, packCode={}, writtenRecords={}",
                        context.getUserId(), context.getPackCode(), response.getWrittenRecords());
            } else {
                log.warn("同步积分消费失败 userId={}, packCode={}, errorCode={}, errorMsg={}",
                        context.getUserId(), context.getPackCode(),
                        response.getErrorCode(), response.getErrorMessage());
            }
        } catch (Exception e) {
            // 同步失败不影响业务
            log.error("同步积分消费异常 userId={}, packCode={}",
                    context.getUserId(), context.getPackCode(), e);
        }
    }

    // =====================================================================
    // syncEntitlement
    // =====================================================================

    @Override
    public void syncEntitlement(FbsScenePack scenePack, int creditsRequired) {
        if (scenePack == null || scenePack.getPackCode() == null) {
            log.warn("同步 entitlement 失败：场景包或 packCode 为空");
            return;
        }

        try {
            // 1. 构建记录
            Map<String, Object> record = buildEntitlementRecord(scenePack, creditsRequired);

            // 2. 调用写入服务
            List<Map<String, Object>> records = Collections.singletonList(record);
            WecomSyncWriteResponse response = wecomWriteService.writeRecords("entitlement", records);

            // 3. 日志记录
            if (response.isSuccess()) {
                log.info("同步场景包规则成功 packCode={}, creditsRequired={}, writtenRecords={}",
                        scenePack.getPackCode(), creditsRequired, response.getWrittenRecords());
            } else {
                log.warn("同步场景包规则失败 packCode={}, errorCode={}, errorMsg={}",
                        scenePack.getPackCode(), response.getErrorCode(), response.getErrorMessage());
            }
        } catch (Exception e) {
            // 同步失败不影响业务
            log.error("同步场景包规则异常 packCode={}", scenePack.getPackCode(), e);
        }
    }

    // =====================================================================
    // buildCommercialHubRecord（OpenSpec #13：补全 14 个空字段 + user_id 修复）
    // =====================================================================

    /**
     * 构建 commercial_hub 全字段记录
     *
     * 智能表格共 24 个字段，写入格式：
     * - TEXT 字段：[{"type":"text","text":"内容"}]
     * - NUMBER 字段：直接用数值
     * - SINGLE_SELECT 字段：[{"type":"text","text":"选项值"}]（MVP 策略，待确认）
     */
    private Map<String, Object> buildCommercialHubRecord(CommercialHubSyncContext ctx) {
        Map<String, Object> values = new LinkedHashMap<>();
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);

        // ---- 1. record_id (TEXT) ----
        values.put("record_id", textVal(UUID.randomUUID().toString()));

        // ---- 2. record_type (TEXT) ----
        values.put("record_type", textVal("SKILL_USAGE"));

        // ---- 3. corp_id (TEXT) ---- 从企业成员→企业编码关联查
        values.put("corp_id", textVal(resolveCorpId(ctx.getUserId())));

        // ---- 4. user_id (TEXT) ---- ⚠️ 修复：必须文本包装
        values.put("user_id", textVal(String.valueOf(ctx.getUserId())));

        // ---- 5. genre (TEXT) ----
        values.put("genre", textVal(ctx.getPackCode()));

        // ---- 6. event (TEXT) ----
        values.put("event", textVal("CONSUME"));

        // ---- 7. delta (NUMBER) ----
        values.put("delta", -ctx.getPointsAmount());

        // ---- 8. balance_after (NUMBER) ----
        values.put("balance_after", ctx.getRemainPoints() != null ? ctx.getRemainPoints() : 0);

        // ---- 9. credits_required (NUMBER) ----
        values.put("credits_required", ctx.getPointsAmount());

        // ---- 10. code_prefix (TEXT) ---- ⚠️ 无数据源，写空
        values.put("code_prefix", textVal(""));

        // ---- 11. code_hash (TEXT) ---- ⚠️ 无数据源，写空
        values.put("code_hash", textVal(""));

        // ---- 12. code_type (TEXT) ---- 从 FbsAuthCode 查
        values.put("code_type", textVal(resolveCodeType(ctx.getAuthCode())));

        // ---- 13. redeem_target (TEXT) ----
        values.put("redeem_target", textVal(ctx.getPackCode()));

        // ---- 14. request_id (TEXT) ----
        values.put("request_id", textVal(ctx.getUsageRecordId()));

        // ---- 15. order_id (TEXT) ---- MVP 同 request_id
        values.put("order_id", textVal(ctx.getUsageRecordId()));

        // ---- 16. status (TEXT) ----
        values.put("status", textVal("SUCCESS"));

        // ---- 17. trial_allowed (SINGLE_SELECT) ---- MVP 固定 false
        values.put("trial_allowed", textVal("false"));

        // ---- 18. enterprise_only (SINGLE_SELECT) ---- packType=2 → true
        values.put("enterprise_only", textVal(ctx.getPackType() == 2 ? "true" : "false"));

        // ---- 19. source (TEXT) ---- hostType 映射
        values.put("source", textVal(resolveSource(ctx.getHostType())));

        // ---- 20. operator (TEXT) ----
        values.put("operator", textVal(String.valueOf(ctx.getUserId())));

        // ---- 21. risk_flag (TEXT) ---- P3 延期，固定空
        values.put("risk_flag", textVal(""));

        // ---- 22. payload_json (TEXT) ----
        values.put("payload_json", textVal(buildPayloadJson(ctx)));

        // ---- 23. created_at (TEXT) ----
        values.put("created_at", textVal(timestamp));

        // ---- 24. updated_at (TEXT) ----
        values.put("updated_at", textVal(timestamp));

        return values;
    }

    // =====================================================================
    // 关联查询方法（全部 try-catch，失败降级为空字符串）
    // =====================================================================

    /**
     * 解析企业编码：userId → FbsEnterpriseMember → FbsEnterprise.enterpriseCode
     * 失败降级：返回空字符串
     */
    private String resolveCorpId(Long userId) {
        if (userId == null || enterpriseMemberMapper == null || enterpriseMapper == null) {
            return "";
        }
        try {
            List<FbsEnterpriseMember> members = enterpriseMemberMapper.selectActiveByUserId(userId);
            if (members == null || members.isEmpty()) {
                return ""; // 非企业用户
            }
            Long enterpriseId = members.get(0).getEnterpriseId();
            FbsEnterprise enterprise = enterpriseMapper.selectById(enterpriseId);
            if (enterprise != null && StringUtils.hasText(enterprise.getEnterpriseCode())) {
                return enterprise.getEnterpriseCode();
            }
            return "";
        } catch (Exception e) {
            log.warn("resolveCorpId 查询失败 userId={}, 降级为空字符串", userId, e);
            return "";
        }
    }

    /**
     * 解析授权码类型：authCode → FbsAuthCode.codeType
     * 失败降级：返回空字符串
     */
    private String resolveCodeType(String authCode) {
        if (!StringUtils.hasText(authCode) || authCodeMapper == null) {
            return "";
        }
        try {
            FbsAuthCode code = authCodeMapper.selectByAuthCode(authCode);
            if (code != null && code.getCodeType() != null) {
                return String.valueOf(code.getCodeType());
            }
            return "";
        } catch (Exception e) {
            log.warn("resolveCodeType 查询失败 authCode={}, 降级为空字符串", authCode, e);
            return "";
        }
    }

    /**
     * hostType → source 映射
     * WORKBUDDY → SKILL_API
     * ENTERPRISE → ENTERPRISE
     * 其他 → 未知
     */
    private String resolveSource(String hostType) {
        if (hostType == null) {
            return "SKILL_API";
        }
        switch (hostType.toUpperCase()) {
            case "ENTERPRISE":
                return "ENTERPRISE";
            case "WORKBUDDY":
            default:
                return "SKILL_API";
        }
    }

    /**
     * 构建 payload_json：序列化核心消费参数
     */
    private String buildPayloadJson(CommercialHubSyncContext ctx) {
        try {
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"userId\":").append(ctx.getUserId()).append(",");
            sb.append("\"packCode\":\"").append(ctx.getPackCode() != null ? ctx.getPackCode() : "").append("\",");
            sb.append("\"pointsAmount\":").append(ctx.getPointsAmount()).append(",");
            sb.append("\"hostType\":\"").append(ctx.getHostType() != null ? ctx.getHostType() : "").append("\",");
            sb.append("\"usageRecordId\":\"").append(ctx.getUsageRecordId() != null ? ctx.getUsageRecordId() : "").append("\",");
            sb.append("\"packType\":").append(ctx.getPackType());
            if (StringUtils.hasText(ctx.getAuthCode())) {
                sb.append(",\"authCode\":\"").append(ctx.getAuthCode()).append("\"");
            }
            if (StringUtils.hasText(ctx.getPointsRuleCode())) {
                sb.append(",\"pointsRuleCode\":\"").append(ctx.getPointsRuleCode()).append("\"");
            }
            sb.append("}");
            return sb.toString();
        } catch (Exception e) {
            log.warn("buildPayloadJson 失败，降级为空字符串", e);
            return "";
        }
    }

    // =====================================================================
    // 工具方法
    // =====================================================================

    /**
     * 文本字段包装：[{"type":"text","text":"内容"}]
     */
    private List<Map<String, String>> textVal(String value) {
        return Collections.singletonList(Map.of("type", "text", "text", value != null ? value : ""));
    }

    // =====================================================================
    // buildEntitlementRecord（无变更，保留原逻辑）
    // =====================================================================

    /**
     * 构建场景包规则记录（entitlement 字段映射）
     *
     * entitlement 字段：
     * - genre: 场景包编码
     * - credits_required: 所需积分
     * - trial_allowed: 是否允许试用（MVP 固定 false）— CHECKBOX 类型
     * - enterprise_only: 是否仅限企业（packType=2 为企业包）— CHECKBOX 类型
     */
    private Map<String, Object> buildEntitlementRecord(FbsScenePack scenePack, int creditsRequired) {
        Map<String, Object> values = new LinkedHashMap<>();

        // 文本字段
        values.put("genre", textVal(scenePack.getPackCode()));

        // 数字字段
        values.put("credits_required", creditsRequired);

        // 布尔字段：直接用布尔值（企微智能表格 FIELD_TYPE_CHECKBOX 读写格式）
        // trial_allowed: MVP 固定为 false（不勾选）
        values.put("trial_allowed", false);

        // enterprise_only: 根据 packType 判断（企业包 packType=2 勾选）
        boolean isEnterpriseOnly = scenePack.getPackType() != null && scenePack.getPackType() == 2;
        values.put("enterprise_only", isEnterpriseOnly);

        return values;
    }
}
