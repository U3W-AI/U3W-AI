package com.wx.fbsir.business.fbs.dto.business.wecom;

import lombok.Data;

import java.util.List;

/**
 * 删除字段请求
 *
 * @author wxfbsir
 * @date 2026-04-15
 */
@Data
public class WecomDeleteFieldsRequest {

    /**
     * 文档 ID
     */
    private String docid;

    /**
     * 子表 ID
     */
    private String sheetId;

    /**
     * 待删除的字段 ID 列表
     */
    private List<String> fieldIds;
}
