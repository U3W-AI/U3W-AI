package com.wx.fbsir.business.fbs.service;

import com.wx.fbsir.business.fbs.dto.business.wecom.*;

/**
 * 企微智能表格结构管理服务接口
 *
 * @author wxfbsir
 * @date 2026-04-15
 */
public interface WecomSchemaService {

    // ===================== 子表管理 =====================

    /**
     * 查询子表列表
     *
     * @param docid 文档 ID
     * @return 子表列表响应
     */
    WecomGetSheetsResponse getSheets(String docid);

    /**
     * 添加子表
     *
     * @param docid 文档 ID
     * @param title 子表标题
     * @return 添加结果
     */
    WecomAddSheetResponse addSheet(String docid, String title);

    /**
     * 更新子表标题
     *
     * @param docid   文档 ID
     * @param sheetId 子表 ID
     * @param title   新的标题
     * @return 更新后的子表信息
     */
    WecomAddSheetResponse updateSheet(String docid, String sheetId, String title);

    /**
     * 删除子表
     *
     * @param docid   文档 ID
     * @param sheetId 子表 ID
     * @return 是否成功
     */
    boolean deleteSheet(String docid, String sheetId);

    // ===================== 字段管理 =====================

    /**
     * 查询字段列表
     *
     * @param docid   文档 ID
     * @param sheetId 子表 ID
     * @return 字段列表响应
     */
    WecomGetFieldsResponse getFields(String docid, String sheetId);

    /**
     * 添加字段
     *
     * @param docid   文档 ID
     * @param sheetId 子表 ID
     * @param fields  字段列表
     * @return 添加结果
     */
    WecomGetFieldsResponse addFields(String docid, String sheetId, WecomAddFieldsRequest.FieldItem[] fields);

    /**
     * 更新字段
     *
     * @param docid   文档 ID
     * @param sheetId 子表 ID
     * @param fields  字段列表
     * @return 更新结果
     */
    WecomGetFieldsResponse updateFields(String docid, String sheetId, WecomUpdateFieldsRequest.FieldItem[] fields);

    /**
     * 删除字段
     *
     * @param docid    文档 ID
     * @param sheetId  子表 ID
     * @param fieldIds 字段 ID 列表
     * @return 删除的字段数量
     */
    int deleteFields(String docid, String sheetId, String[] fieldIds);
}
