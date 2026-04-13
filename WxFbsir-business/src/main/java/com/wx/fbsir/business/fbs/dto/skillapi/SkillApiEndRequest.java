package com.wx.fbsir.business.fbs.dto.skillapi;

/**
 * Skill API 两阶段使用记录 - end 请求
 * ⚠️ 与 consume 互斥：end 只负责更新记录状态，不扣减积分/配额
 */
public class SkillApiEndRequest {

    /** 状态：1=成功, 2=失败 */
    private Integer status;

    /** 失败原因（成功时传NULL） */
    private String errorMessage;

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
