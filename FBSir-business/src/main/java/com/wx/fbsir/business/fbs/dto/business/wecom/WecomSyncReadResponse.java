package com.wx.fbsir.business.fbs.dto.business.wecom;

import java.util.List;
import java.util.Map;

/**
 * 企微同步读取响应
 *
 * @author FBSir
 * @date 2026-04-13
 */
public class WecomSyncReadResponse {

    private boolean success;
    private String sheetName;
    private int recordCount;
    private long durationMs;
    private Long syncLogId;
    private List<Map<String, Object>> snapshot;  // 前 10 条预览
    private String errorCode;
    private String errorMessage;

    // ---- 工厂方法 ----

    public static WecomSyncReadResponse ok(String sheetName, int recordCount, long durationMs,
                                            Long syncLogId, List<Map<String, Object>> snapshot) {
        WecomSyncReadResponse r = new WecomSyncReadResponse();
        r.success = true;
        r.sheetName = sheetName;
        r.recordCount = recordCount;
        r.durationMs = durationMs;
        r.syncLogId = syncLogId;
        r.snapshot = snapshot;
        return r;
    }

    public static WecomSyncReadResponse fail(String sheetName, String errorCode, String errorMessage,
                                              long durationMs, Long syncLogId) {
        WecomSyncReadResponse r = new WecomSyncReadResponse();
        r.success = false;
        r.sheetName = sheetName;
        r.errorCode = errorCode;
        r.errorMessage = errorMessage;
        r.durationMs = durationMs;
        r.syncLogId = syncLogId;
        return r;
    }

    // ---- Getter / Setter ----

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getSheetName() { return sheetName; }
    public void setSheetName(String sheetName) { this.sheetName = sheetName; }

    public int getRecordCount() { return recordCount; }
    public void setRecordCount(int recordCount) { this.recordCount = recordCount; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public Long getSyncLogId() { return syncLogId; }
    public void setSyncLogId(Long syncLogId) { this.syncLogId = syncLogId; }

    public List<Map<String, Object>> getSnapshot() { return snapshot; }
    public void setSnapshot(List<Map<String, Object>> snapshot) { this.snapshot = snapshot; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
