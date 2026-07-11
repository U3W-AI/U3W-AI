package com.wx.fbsir.business.fbs.dto;

/**
 * 综合权益校验结果（场景包 + 授权码 + 积分 三项合并）
 *
 * MVP 策略：全部 Fail-Closed，任意一项失败返回 pass=false
 *
 * @author FBSir
 * @date 2026-04-08
 */
public class ComprehensiveRightsResult {

    /** 是否全部通过 */
    private boolean pass;

    /** 失败原因（pass=true 时为 null） */
    private String failReason;

    /** 关联场景包ID（通过后返回，供后续扣减使用） */
    private Long packId;

    /** 关联积分规则编码（null=免费包，供后续扣减使用） */
    private String pointsRuleCode;

    /** 积分金额（免费包为0，供后续扣减使用） */
    private Integer pointsAmount;

    private ComprehensiveRightsResult() {}

    public static ComprehensiveRightsResult pass(Long packId, String pointsRuleCode, Integer pointsAmount) {
        ComprehensiveRightsResult r = new ComprehensiveRightsResult();
        r.pass            = true;
        r.packId          = packId;
        r.pointsRuleCode  = pointsRuleCode;
        r.pointsAmount    = pointsAmount;
        return r;
    }

    public static ComprehensiveRightsResult fail(String failReason) {
        ComprehensiveRightsResult r = new ComprehensiveRightsResult();
        r.pass       = false;
        r.failReason = failReason;
        return r;
    }

    public boolean isPass()          { return pass; }
    public String getFailReason()    { return failReason; }
    public Long getPackId()          { return packId; }
    public String getPointsRuleCode(){ return pointsRuleCode; }
    public Integer getPointsAmount() { return pointsAmount; }
}
