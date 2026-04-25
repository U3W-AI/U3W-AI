package com.wx.fbsir.business.fbs.service;

import com.wx.fbsir.business.fbs.domain.entity.FbsScenePack;
import com.wx.fbsir.business.fbs.dto.business.wecom.CommercialHubSyncContext;

/**
 * 企微业务同步服务
 *
 * 负责 MySQL 业务数据同步到企微智能表格
 * OpenSpec #9: business-sync-to-wecom
 * OpenSpec #13: commercial_hub 字段补全
 */
public interface WecomBusinessSyncService {

    /**
     * 同步积分消费到 commercial_hub Sheet（旧签名，兼容保留）
     *
     * @param userId        用户ID
     * @param packCode      场景包编码
     * @param pointsAmount  消费积分数量（正数）
     * @param remainPoints  剩余积分
     * @deprecated 使用 {@link #syncCommercialHub(CommercialHubSyncContext)} 替代
     */
    @Deprecated
    void syncCommercialHub(Long userId, String packCode, int pointsAmount, int remainPoints);

    /**
     * 同步积分消费到 commercial_hub Sheet（扩展签名）
     *
     * <p>OpenSpec #13：传入完整消费上下文，使 commercial_hub 全部 24 字段均有值。</p>
     *
     * @param context 同步上下文（包含 userId, packCode, hostType, usageRecordId 等）
     */
    void syncCommercialHub(CommercialHubSyncContext context);

    /**
     * 同步场景包积分规则到 entitlement Sheet
     *
     * @param scenePack     场景包实体
     * @param creditsRequired 所需积分（从 pointsRuleCode 推导）
     */
    void syncEntitlement(FbsScenePack scenePack, int creditsRequired);
}
