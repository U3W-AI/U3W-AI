package com.wx.fbsir.business.fbs.dto.business.wecom;

import lombok.Data;

import java.util.List;

/**
 * 删除记录请求
 *
 * @author wxfbsir
 * @date 2026-04-15
 */
@Data
public class WecomDeleteRecordsRequest {

    /**
     * 文档 ID
     */
    private String docid;

    /**
     * 子表 ID
     */
    private String sheetId;

    /**
     * 待删除的记录 ID 列表
     */
    private List<String> recordIds;
}
