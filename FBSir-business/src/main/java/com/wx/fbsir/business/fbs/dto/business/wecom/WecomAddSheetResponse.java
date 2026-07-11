package com.wx.fbsir.business.fbs.dto.business.wecom;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 添加子表响应
 *
 * @author FBSir
 * @date 2026-04-15
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WecomAddSheetResponse {

    /**
     * 新增子表的 sheetId
     */
    private String sheetId;

    /**
     * 新增子表的标题
     */
    private String title;
}
