package com.wx.fbsir.business.fbs.service.impl;

import com.wx.fbsir.business.fbs.domain.entity.FbsScenePack;
import com.wx.fbsir.business.fbs.dto.business.wecom.WecomSyncWriteResponse;
import com.wx.fbsir.business.fbs.service.WecomBusinessSyncService;
import com.wx.fbsir.business.fbs.service.WecomWriteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 企微业务同步服务实现
 *
 * OpenSpec #9: business-sync-to-wecom
 * 同步范围：commercial_hub（积分消费流水）+ entitlement（场景包积分规则）
 */
@Service
public class WecomBusinessSyncServiceImpl implements WecomBusinessSyncService {

    private static final Logger log = LoggerFactory.getLogger(WecomBusinessSyncServiceImpl.class);

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private WecomWriteService wecomWriteService;

    @Override
    public void syncCommercialHub(Long userId, String packCode, int pointsAmount, int remainPoints) {
        try {
            // 1. 构建记录
            Map<String, Object> record = buildCommercialHubRecord(userId, packCode, pointsAmount, remainPoints);

            // 2. 调用写入服务
            List<Map<String, Object>> records = Collections.singletonList(record);
            WecomSyncWriteResponse response = wecomWriteService.writeRecords("commercial_hub", records);

            // 3. 日志记录
            if (response.isSuccess()) {
                log.info("同步积分消费成功 userId={}, packCode={}, writtenRecords={}",
                        userId, packCode, response.getWrittenRecords());
            } else {
                log.warn("同步积分消费失败 userId={}, packCode={}, errorCode={}, errorMsg={}",
                        userId, packCode, response.getErrorCode(), response.getErrorMessage());
            }
        } catch (Exception e) {
            // 同步失败不影响业务
            log.error("同步积分消费异常 userId={}, packCode={}", userId, packCode, e);
        }
    }

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

    /**
     * 构建积分消费记录（commercial_hub 字段映射）
     * 注意：返回的 Map 会被 WecomWriteServiceImpl.buildWriteParams() 包装成 {"values": {...}}
     * 文本字段需要用 [{"type": "text", "text": "内容"}] 格式
     */
    private Map<String, Object> buildCommercialHubRecord(Long userId, String packCode,
                                                          int pointsAmount, int remainPoints) {
        Map<String, Object> values = new LinkedHashMap<>();

        // 文本字段：用 [{"type": "text", "text": "内容"}] 格式
        values.put("record_id", Arrays.asList(Map.of("type", "text", "text", UUID.randomUUID().toString())));
        values.put("record_type", Arrays.asList(Map.of("type", "text", "text", "SKILL_USAGE")));
        values.put("genre", Arrays.asList(Map.of("type", "text", "text", packCode)));
        values.put("event", Arrays.asList(Map.of("type", "text", "text", "CONSUME")));
        values.put("status", Arrays.asList(Map.of("type", "text", "text", "SUCCESS")));
        values.put("created_at", Arrays.asList(Map.of("type", "text", "text", LocalDateTime.now().format(TIMESTAMP_FORMATTER))));

        // 数字字段：直接用值
        values.put("user_id", userId);
        values.put("delta", -pointsAmount); // 消费是负数
        values.put("balance_after", remainPoints);
        values.put("credits_required", pointsAmount);

        // 不再包装 values，由 WecomWriteServiceImpl.buildWriteParams() 统一处理
        return values;
    }

    /**
     * 构建场景包规则记录（entitlement 字段映射）
     * 注意：返回的 Map 会被 WecomWriteServiceImpl.buildWriteParams() 包装成 {"values": {...}}
     *
     * entitlement 字段：
     * - genre: 场景包编码
     * - credits_required: 所需积分
     * - trial_allowed: 是否允许试用（MVP 固定 false）
     * - enterprise_only: 是否仅限企业（ownerType=2 为企业包）
     */
    private Map<String, Object> buildEntitlementRecord(FbsScenePack scenePack, int creditsRequired) {
        Map<String, Object> values = new LinkedHashMap<>();

        // 文本字段
        values.put("genre", Arrays.asList(Map.of("type", "text", "text", scenePack.getPackCode())));

        // 数字字段
        values.put("credits_required", creditsRequired);

        // 布尔字段：直接用布尔值（企微智能表格 FIELD_TYPE_CHECKBOX 读写格式）
        // trial_allowed: MVP 固定为 false（不勾选）
        values.put("trial_allowed", false);

        // enterprise_only: 根据 packType 判断（企业包 packType=2 勾选）
        // packType=2 为企业包，仅限企业用户使用
        boolean isEnterpriseOnly = scenePack.getPackType() != null && scenePack.getPackType() == 2;
        values.put("enterprise_only", isEnterpriseOnly);

        // 不再包装 values，由 WecomWriteServiceImpl.buildWriteParams() 统一处理
        return values;
    }
}
