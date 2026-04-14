package com.wx.fbsir.business.fbs.domain;

/**
 * wecom-cli 执行结果
 *
 * @author wxfbsir
 * @date 2026-04-13
 */
public class WecomCliResult {

    /** 是否成功 */
    private boolean success;

    /** 错误码（成功时为 null） */
    private String errorCode;

    /** 错误信息 */
    private String errorMessage;

    /** stdout 原始输出 */
    private String rawOutput;

    /** 解析后的数据（由上层 WecomSyncService 解析） */
    private Object parsedData;

    /** 进程退出码 */
    private int exitCode;

    /** 执行耗时（毫秒） */
    private long durationMs;

    // ---- 私有构造器 + 静态工厂 ----

    private WecomCliResult() {}

    public static WecomCliResult success(String rawOutput, long durationMs) {
        WecomCliResult r = new WecomCliResult();
        r.success = true;
        r.rawOutput = rawOutput;
        r.exitCode = 0;
        r.durationMs = durationMs;
        return r;
    }

    public static WecomCliResult fail(String errorCode, String errorMessage, int exitCode, long durationMs) {
        WecomCliResult r = new WecomCliResult();
        r.success = false;
        r.errorCode = errorCode;
        r.errorMessage = errorMessage;
        r.exitCode = exitCode;
        r.durationMs = durationMs;
        return r;
    }

    // ---- Getter / Setter ----

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getRawOutput() { return rawOutput; }
    public void setRawOutput(String rawOutput) { this.rawOutput = rawOutput; }

    public Object getParsedData() { return parsedData; }
    public void setParsedData(Object parsedData) { this.parsedData = parsedData; }

    public int getExitCode() { return exitCode; }
    public void setExitCode(int exitCode) { this.exitCode = exitCode; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
}
