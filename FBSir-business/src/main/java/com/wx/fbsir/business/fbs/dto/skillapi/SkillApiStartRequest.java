package com.wx.fbsir.business.fbs.dto.skillapi;

/**
 * Skill API 两阶段使用记录 - start 请求
 * ⚠️ 与 consume 互斥：同一 usageRecordId 只能走 start/end 或 consume 一种模式
 */
public class SkillApiStartRequest {

    /** 用户ID */
    private Long userId;

    /** 场景包编码 */
    private String packCode;

    /** Skill 编码 */
    private String skillCode;

    /** 幂等键（String，UUID 或 taskId） */
    private String usageRecordId;

    /** 宿主类型 */
    private String hostType;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getPackCode() { return packCode; }
    public void setPackCode(String packCode) { this.packCode = packCode; }

    public String getSkillCode() { return skillCode; }
    public void setSkillCode(String skillCode) { this.skillCode = skillCode; }

    public String getUsageRecordId() { return usageRecordId; }
    public void setUsageRecordId(String usageRecordId) { this.usageRecordId = usageRecordId; }

    public String getHostType() { return hostType; }
    public void setHostType(String hostType) { this.hostType = hostType; }
}
