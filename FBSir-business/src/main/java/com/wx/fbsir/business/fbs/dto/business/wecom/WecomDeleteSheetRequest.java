package com.wx.fbsir.business.fbs.dto.business.wecom;

import lombok.Data;

/**
 * 删除子表请求
 *
 * @author FBSir
 * @date 2026-04-15
 */
@Data
public class WecomDeleteSheetRequest {

    /**
     * 文档 ID
     */
    private String docid;

    /**
     * 子表 ID
     */
    private String sheetId;
}
