package com.wx.fbsir.business.fbs.service;

import com.wx.fbsir.business.fbs.domain.entity.FbsScenePack;

/**
 * 企微业务同步服务
 *
 * 负责 MySQL 业务数据同步到企微智能表格
 * OpenSpec #9: business-sync-to-wecom
 */
public interface WecomBusinessSyncService {

    /**
     * 同步积分消费到 commercial_hub Sheet
     *
     * @param userId        用户ID
     * @param packCode      场景包编码
     * @param pointsAmount  消费积分数量（正数）
     * @param remainPoints  剩余积分
     */
    void syncCommercialHub(Long userId, String packCode, int pointsAmount, int remainPoints);

    /**
     * 同步场景包积分规则到 entitlement Sheet
     *
     * @param scenePack     场景包实体
     * @param creditsRequired 所需积分（从 pointsRuleCode 推导）
     */
    void syncEntitlement(FbsScenePack scenePack, int creditsRequired);
}
