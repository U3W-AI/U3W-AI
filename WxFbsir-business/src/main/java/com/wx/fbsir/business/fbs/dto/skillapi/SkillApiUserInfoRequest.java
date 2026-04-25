package com.wx.fbsir.business.fbs.dto.skillapi;

/**
 * Skill API 用户信息查询请求
 * 不返回 T0-T3 用户层级（当前仓库无此模型）
 */
public class SkillApiUserInfoRequest {

    /** 用户ID */
    private Long userId;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
}
