package com.wx.fbsir.business.fbs.dto.business.wecom;

import lombok.Data;

/**
 * 添加子表请求
 *
 * @author wxfbsir
 * @date 2026-04-15
 */
@Data
public class WecomAddSheetRequest {

    /**
     * 文档 ID
     */
    private String docid;

    /**
     * 子表标题
     */
    private String title;
}
