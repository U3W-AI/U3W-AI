package com.wx.fbsir.business.fbs.dto;

/**
 * 单项权益校验结果（场景包 / 授权码 / 积分）
 *
 * @author wxfbsir
 * @date 2026-04-08
 */
public class RightsCheckResult {

    /** 是否通过 */
    private boolean allowed;

    /** 失败原因（allowed=true 时为 null） */
    private String reason;

    private RightsCheckResult() {}

    public static RightsCheckResult pass() {
        RightsCheckResult r = new RightsCheckResult();
        r.allowed = true;
        return r;
    }

    public static RightsCheckResult fail(String reason) {
        RightsCheckResult r = new RightsCheckResult();
        r.allowed = false;
        r.reason  = reason;
        return r;
    }

    public boolean isAllowed() { return allowed; }
    public String getReason()  { return reason; }
}
