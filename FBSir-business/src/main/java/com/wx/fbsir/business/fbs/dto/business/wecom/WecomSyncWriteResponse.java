package com.wx.fbsir.business.fbs.dto.business.wecom;

/**
 * 企微同步写入响应
 *
 * <p>延续 #6 口径：success 字段区分业务成功/失败。</p>
 *
 * @author FBSir
 * @date 2026-04-14
 */
public class WecomSyncWriteResponse {

    private boolean success;
    private String sheetName;
    private int totalRecords;
    private int writtenRecords;
    private int shardCount;
    private long durationMs;
    private Long syncLogId;
    private String errorCode;
    private String errorMessage;

    // ---- 私有构造器 + 静态工厂 ----

    private WecomSyncWriteResponse() {}

    public static WecomSyncWriteResponse ok(String sheetName, int totalRecords, int writtenRecords,
                                            int shardCount, long durationMs, Long syncLogId) {
        WecomSyncWriteResponse r = new WecomSyncWriteResponse();
        r.success = true;
        r.sheetName = sheetName;
        r.totalRecords = totalRecords;
        r.writtenRecords = writtenRecords;
        r.shardCount = shardCount;
        r.durationMs = durationMs;
        r.syncLogId = syncLogId;
        return r;
    }

    public static WecomSyncWriteResponse noOp(String sheetName, long durationMs) {
        WecomSyncWriteResponse r = new WecomSyncWriteResponse();
        r.success = true;
        r.sheetName = sheetName;
        r.totalRecords = 0;
        r.writtenRecords = 0;
        r.shardCount = 0;
        r.durationMs = durationMs;
        return r;
    }

    public static WecomSyncWriteResponse fail(String sheetName, String errorCode, String errorMessage,
                                              long durationMs, Long syncLogId) {
        WecomSyncWriteResponse r = new WecomSyncWriteResponse();
        r.success = false;
        r.sheetName = sheetName;
        r.errorCode = errorCode;
        r.errorMessage = errorMessage;
        r.durationMs = durationMs;
        r.syncLogId = syncLogId;
        return r;
    }

    public static WecomSyncWriteResponse partialFail(String sheetName, int writtenRecords, int shardCount,
                                                      String errorCode, String errorMessage,
                                                      long durationMs, Long syncLogId) {
        WecomSyncWriteResponse r = new WecomSyncWriteResponse();
        r.success = false;
        r.sheetName = sheetName;
        r.writtenRecords = writtenRecords;
        r.shardCount = shardCount;
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

    public int getTotalRecords() { return totalRecords; }
    public void setTotalRecords(int totalRecords) { this.totalRecords = totalRecords; }

    public int getWrittenRecords() { return writtenRecords; }
    public void setWrittenRecords(int writtenRecords) { this.writtenRecords = writtenRecords; }

    public int getShardCount() { return shardCount; }
    public void setShardCount(int shardCount) { this.shardCount = shardCount; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public Long getSyncLogId() { return syncLogId; }
    public void setSyncLogId(Long syncLogId) { this.syncLogId = syncLogId; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
