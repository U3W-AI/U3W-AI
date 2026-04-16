package com.wx.fbsir.business.fbs.dto.business.wecom;

import lombok.Data;

import java.util.List;

/**
 * 查询子表列表响应
 *
 * @author wxfbsir
 * @date 2026-04-15
 */
@Data
public class WecomGetSheetsResponse {

    /**
     * 子表列表
     */
    private List<SheetInfo> sheets;

    /**
     * 子表信息
     */
    @Data
    public static class SheetInfo {
        /**
         * 子表 ID
         */
        private String sheetId;

        /**
         * 子表标题
         */
        private String title;

        /**
         * 行数
         */
        private Integer rowCount;
    }
}
