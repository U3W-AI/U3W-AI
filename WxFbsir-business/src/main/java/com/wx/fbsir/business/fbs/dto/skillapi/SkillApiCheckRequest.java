package com.wx.fbsir.business.fbs.dto.skillapi;

/**
 * Skill API 权益校验请求
 */
public class SkillApiCheckRequest {

    /** 用户ID（宿主环境获取） */
    private Long userId;

    /** 场景包编码 */
    private String packCode;

    /** 授权码（个人包需要） */
    private String authCode;

    /** 宿主类型（默认 WORKBUDDY） */
    private String hostType;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getPackCode() { return packCode; }
    public void setPackCode(String packCode) { this.packCode = packCode; }

    public String getAuthCode() { return authCode; }
    public void setAuthCode(String authCode) { this.authCode = authCode; }

    public String getHostType() { return hostType; }
    public void setHostType(String hostType) { this.hostType = hostType; }
}
