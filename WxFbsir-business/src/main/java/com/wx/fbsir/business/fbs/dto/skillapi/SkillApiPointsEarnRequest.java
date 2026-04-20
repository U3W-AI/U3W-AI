package com.wx.fbsir.business.fbs.dto.skillapi;

/**
 * Skill API 行为积分上报请求
 *
 * Skill 端（credits-ledger.mjs）在检测到行为积分事件后，
 * 通过此接口向后端上报，由后端统一发放积分。
 *
 * 幂等：eventId = usageRecordId，复用 wx_points_record.uk_event_id 唯一索引
 */
public class SkillApiPointsEarnRequest {

    /** 用户ID（可选，优先从 API Key 反查） */
    private Long userId;

    /** 积分来源（映射为 ruleCode），如 first_install / daily_login / chapter_done 等 */
    private String source;

    /** 积分数量（正整数） */
    private Integer amount;

    /** 使用记录幂等键（Skill 端生成，同时映射为后端 eventId） */
    private String usageRecordId;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public Integer getAmount() { return amount; }
    public void setAmount(Integer amount) { this.amount = amount; }

    public String getUsageRecordId() { return usageRecordId; }
    public void setUsageRecordId(String usageRecordId) { this.usageRecordId = usageRecordId; }
}
