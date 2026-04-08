package com.wx.fbsir.business.fbs.dto;

/**
 * Skill 消费结果
 * 对应 POST /fbs/internal/usage/consume 响应体
 *
 * @author wxfbsir
 * @date 2026-04-08
 */
public class ConsumeResult {

    /** 是否成功 */
    private boolean success;

    /** 使用记录幂等键（调用方传入的原值回显） */
    private String usageRecordId;

    /** 扣减后剩余积分（免费包为 null） */
    private Integer remainPoints;

    /** 失败原因（success=true 时为 null） */
    private String failReason;

    private ConsumeResult() {}

    public static ConsumeResult success(String usageRecordId, Integer remainPoints) {
        ConsumeResult r = new ConsumeResult();
        r.success       = true;
        r.usageRecordId = usageRecordId;
        r.remainPoints  = remainPoints;
        return r;
    }

    public static ConsumeResult fail(String usageRecordId, String failReason) {
        ConsumeResult r = new ConsumeResult();
        r.success       = false;
        r.usageRecordId = usageRecordId;
        r.failReason    = failReason;
        return r;
    }

    public boolean isSuccess()       { return success; }
    public String getUsageRecordId() { return usageRecordId; }
    public Integer getRemainPoints() { return remainPoints; }
    public String getFailReason()    { return failReason; }
}
