package com.wx.fbsir.business.fbs.dto.business.wecom;

/**
 * 企微同步读取请求
 *
 * @author wxfbsir
 * @date 2026-04-13
 */
public class WecomSyncReadRequest {

    /** Sheet 名称（不传默认 "meta"） */
    private String sheetName;

    public String getSheetName() {
        return sheetName;
    }

    public void setSheetName(String sheetName) {
        this.sheetName = sheetName;
    }
}
