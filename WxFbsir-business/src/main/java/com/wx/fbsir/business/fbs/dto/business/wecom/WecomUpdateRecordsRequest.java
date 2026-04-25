package com.wx.fbsir.business.fbs.dto.business.wecom;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 更新记录请求
 *
 * @author wxfbsir
 * @date 2026-04-15
 */
@Data
public class WecomUpdateRecordsRequest {

    /**
     * 文档 ID
     */
    private String docid;

    /**
     * 子表 ID
     */
    private String sheetId;

    /**
     * 待更新的记录列表
     */
    private List<RecordItem> records;

    /**
     * 记录项
     */
    @Data
    public static class RecordItem {
        /**
         * 记录 ID（必填）
         */
        private String recordId;

        /**
         * 字段值映射（fieldId → value）
         */
        private Map<String, Object> values;
    }
}
