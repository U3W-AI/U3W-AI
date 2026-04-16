package com.wx.fbsir.business.fbs.dto.business.wecom;

import lombok.Data;

import java.util.List;

/**
 * 更新字段请求
 *
 * @author wxfbsir
 * @date 2026-04-15
 */
@Data
public class WecomUpdateFieldsRequest {

    /**
     * 文档 ID
     */
    private String docid;

    /**
     * 子表 ID
     */
    private String sheetId;

    /**
     * 待更新的字段列表
     */
    private List<FieldItem> fields;

    /**
     * 字段项
     */
    @Data
    public static class FieldItem {
        /**
         * 字段 ID（必填）
         */
        private String fieldId;

        /**
         * 字段标题（可选）
         */
        private String fieldTitle;

        /**
         * 字段类型（可选）
         */
        private String fieldType;
    }
}
