package com.wx.fbsir.business.fbs.dto.skillapi;

/**
 * Skill API 一次性消费请求
 * ⚠️ 不传 pointsAmount，由后端按规则自动计算
 */
public class SkillApiConsumeRequest {

    /** 用户ID */
    private Long userId;

    /** 场景包编码 */
    private String packCode;

    /** 授权码（可空） */
    private String authCode;

    /** Skill 编码（如 "bookwriter"） */
    private String skillCode;

    /** 幂等键（调用方生成，UUID 或 taskId） */
    private String usageRecordId;

    /** 宿主类型（默认 WORKBUDDY） */
    private String hostType;

    /** 宿主会话ID（可空；作为签名调用方声明的幂等范围，不等同于服务端宿主认证） */
    private String hostSessionId;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getPackCode() { return packCode; }
    public void setPackCode(String packCode) { this.packCode = packCode; }

    public String getAuthCode() { return authCode; }
    public void setAuthCode(String authCode) { this.authCode = authCode; }

    public String getSkillCode() { return skillCode; }
    public void setSkillCode(String skillCode) { this.skillCode = skillCode; }

    public String getUsageRecordId() { return usageRecordId; }
    public void setUsageRecordId(String usageRecordId) { this.usageRecordId = usageRecordId; }

    public String getHostType() { return hostType; }
    public void setHostType(String hostType) { this.hostType = hostType; }

    public String getHostSessionId() { return hostSessionId; }
    public void setHostSessionId(String hostSessionId) { this.hostSessionId = hostSessionId; }
}
