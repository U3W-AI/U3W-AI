package com.wx.fbsir.business.fbs.service;

import com.wx.fbsir.business.fbs.dto.business.wecom.WecomSyncWriteResponse;

import java.util.List;
import java.util.Map;

/**
 * 企微智能表格写入服务接口
 *
 * @author wxfbsir
 * @date 2026-04-14
 */
public interface WecomWriteService {

    /**
     * 向企微智能表格写入记录
     *
     * @param sheetName 目标 Sheet 名称（null/空串默认 "meta"）
     * @param records   待写入记录数组（CLI 格式，每条须包含 values 字段）
     * @return 写入响应
     */
    WecomSyncWriteResponse writeRecords(String sheetName, List<Map<String, Object>> records);

    /**
     * 更新企微智能表格中的记录
     *
     * @param sheetName 目标 Sheet 名称
     * @param records   待更新记录数组（每条须包含 recordId + values）
     * @return 更新结果
     */
    WecomSyncWriteResponse updateRecords(String sheetName, List<Map<String, Object>> records);

    /**
     * 删除企微智能表格中的记录
     *
     * @param sheetName 目标 Sheet 名称
     * @param recordIds 待删除记录 ID 数组
     * @return 删除结果
     */
    WecomSyncWriteResponse deleteRecords(String sheetName, List<String> recordIds);
}
