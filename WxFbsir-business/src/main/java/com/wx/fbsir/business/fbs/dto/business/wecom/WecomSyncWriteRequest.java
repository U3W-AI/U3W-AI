package com.wx.fbsir.business.fbs.dto.business.wecom;

import java.util.List;
import java.util.Map;

/**
 * 企微同步写入请求
 *
 * <p>records 格式遵循 wecom-cli smartsheet_add_records 要求：
 * 每条记录为 {"values": {"字段标题": value, ...}}</p>
 *
 * @author wxfbsir
 * @date 2026-04-14
 */
public class WecomSyncWriteRequest {

    /** 目标 Sheet 名称（null/空串默认使用 "meta"） */
    private String sheetName;

    /** 待写入记录数组（CLI 格式） */
    private List<Map<String, Object>> records;

    // ---- Getter / Setter ----

    public String getSheetName() { return sheetName; }
    public void setSheetName(String sheetName) { this.sheetName = sheetName; }

    public List<Map<String, Object>> getRecords() { return records; }
    public void setRecords(List<Map<String, Object>> records) { this.records = records; }
}
