package com.wx.fbsir.business.fbs.dto.business.wecom;

import lombok.Data;

import java.util.List;

/**
 * 查询字段列表响应
 *
 * @author wxfbsir
 * @date 2026-04-15
 */
@Data
public class WecomGetFieldsResponse {

    /**
     * 字段列表
     */
    private List<FieldInfo> fields;

    /**
     * 无参构造器
     */
    public WecomGetFieldsResponse() {
    }

    /**
     * 带字段列表的构造器
     */
    public WecomGetFieldsResponse(List<FieldInfo> fields) {
        this.fields = fields;
    }

    /**
     * 字段信息
     */
    @Data
    public static class FieldInfo {
        /**
         * 字段 ID
         */
        private String fieldId;

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
