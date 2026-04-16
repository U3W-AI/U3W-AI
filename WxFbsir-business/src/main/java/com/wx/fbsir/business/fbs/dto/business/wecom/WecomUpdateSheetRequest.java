package com.wx.fbsir.business.fbs.dto.business.wecom;

import lombok.Data;

/**
 * 更新子表请求
 *
 * @author wxfbsir
 * @date 2026-04-15
 */
@Data
public class WecomUpdateSheetRequest {

    /**
     * 文档 ID
     */
    private String docid;

    /**
     * 子表 ID
     */
    private String sheetId;

    /**
     * 新的子表标题
     */
    private String title;
}
