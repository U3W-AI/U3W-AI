package com.wx.fbsir.business.fbs.dto.business.wecom;

import lombok.Data;

import java.util.List;

/**
 * 添加字段请求
 *
 * @author wxfbsir
 * @date 2026-04-15
 */
@Data
public class WecomAddFieldsRequest {

    /**
     * 文档 ID
     */
    private String docid;

    /**
     * 子表 ID
     */
    private String sheetId;

    /**
     * 待添加的字段列表
     */
    private List<FieldItem> fields;

    /**
     * 字段项
     */
    @Data
    public static class FieldItem {
        /**
         * 字段标题
         */
        private String fieldTitle;

        /**
         * 字段类型
         */
        private String fieldType;
    }
}
