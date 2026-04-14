package com.wx.fbsir.business.fbs.dto.business.wecom;

/**
 * 企微 CLI 连通性检测响应
 *
 * @author wxfbsir
 * @date 2026-04-13
 */
public class WecomCheckResponse {

    /** CLI 文件是否存在 */
    private boolean cliAvailable;

    /** 当前配置的 CLI 路径 */
    private String cliPath;

    /** 最后一次错误信息（无错误时为 null） */
    private String lastError;

    // ---- 工厂方法 ----

    public static WecomCheckResponse available(String cliPath) {
        WecomCheckResponse r = new WecomCheckResponse();
        r.cliAvailable = true;
        r.cliPath = cliPath;
        return r;
    }

    public static WecomCheckResponse unavailable(String cliPath, String lastError) {
        WecomCheckResponse r = new WecomCheckResponse();
        r.cliAvailable = false;
        r.cliPath = cliPath;
        r.lastError = lastError;
        return r;
    }

    // ---- Getter / Setter ----

    public boolean isCliAvailable() { return cliAvailable; }
    public void setCliAvailable(boolean cliAvailable) { this.cliAvailable = cliAvailable; }

    public String getCliPath() { return cliPath; }
    public void setCliPath(String cliPath) { this.cliPath = cliPath; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
}
