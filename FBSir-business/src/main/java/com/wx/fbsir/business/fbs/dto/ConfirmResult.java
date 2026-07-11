package com.wx.fbsir.business.fbs.dto;

/**
 * 积分确认结果（正式扣减）
 *
 * TODO (OpenSpec #add-fbs-rights-foundation): 冻结/确认/回滚延期至后续 OpenSpec
 *
 * @author FBSir
 * @date 2026-04-08
 */
public class ConfirmResult {
    private boolean success;
    private String failReason;

    public static ConfirmResult success() {
        ConfirmResult r = new ConfirmResult();
        r.success = true;
        return r;
    }

    public static ConfirmResult fail(String reason) {
        ConfirmResult r = new ConfirmResult();
        r.success = false;
        r.failReason = reason;
        return r;
    }

    public boolean isSuccess()    { return success; }
    public String getFailReason() { return failReason; }
}
