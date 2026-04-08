package com.wx.fbsir.business.fbs.dto;

/**
 * 积分回滚结果（释放预占）
 *
 * TODO (OpenSpec #add-fbs-rights-foundation): 冻结/确认/回滚延期至后续 OpenSpec
 *
 * @author wxfbsir
 * @date 2026-04-08
 */
public class RollbackResult {
    private boolean success;
    private String failReason;

    public static RollbackResult success() {
        RollbackResult r = new RollbackResult();
        r.success = true;
        return r;
    }

    public static RollbackResult fail(String reason) {
        RollbackResult r = new RollbackResult();
        r.success = false;
        r.failReason = reason;
        return r;
    }

    public boolean isSuccess()    { return success; }
    public String getFailReason() { return failReason; }
}
