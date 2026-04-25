package com.wx.fbsir.business.fbs.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.fbsir.business.fbs.domain.WecomCliResult;
import com.wx.fbsir.business.fbs.domain.entity.FbsWecomSyncLog;
import com.wx.fbsir.business.fbs.dto.business.wecom.*;
import com.wx.fbsir.business.fbs.mapper.FbsWecomSyncLogMapper;
import com.wx.fbsir.business.fbs.service.WecomCliService;
import com.wx.fbsir.business.fbs.service.WecomSchemaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 企微智能表格结构管理服务实现
 *
 * @author wxfbsir
 * @date 2026-04-15
 */
@Service
public class WecomSchemaServiceImpl implements WecomSchemaService {

    private static final Logger logger = LoggerFactory.getLogger(WecomSchemaServiceImpl.class);

    @Autowired
    private WecomCliService wecomCliService;

    @Autowired
    private FbsWecomSyncLogMapper syncLogMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 单个子表最大字段数 */
    private static final int MAX_FIELDS_PER_SHEET = 150;

    /** 允许的字段类型 */
    private static final Set<String> ALLOWED_FIELD_TYPES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "text", "number", "number自动编号", "date", "datetime",
            "checkbox", "phone", "email", "url", "attachment",
            "member", "department", "lookup", "formula", "progress", "grade"
    )));

    // ===================== 参数校验（Fail-Closed） =====================

    /** docid 最小长度（企微文档 ID 通常 60+ 字符） */
    private static final int MIN_DOCID_LENGTH = 20;

    /**
     * 校验 docid 格式
     */
    private void requireValidDocid(String docid) {
        if (docid == null || docid.trim().isEmpty()) {
            throw new IllegalArgumentException("docid 不能为空");
        }
        if (docid.trim().length() < MIN_DOCID_LENGTH) {
            throw new IllegalArgumentException("docid 格式无效（长度不足 " + MIN_DOCID_LENGTH + "）");
        }
    }

    /**
     * 校验 sheetId 格式
     */
    private void requireValidSheetId(String sheetId) {
        if (sheetId == null || sheetId.trim().isEmpty()) {
            throw new IllegalArgumentException("sheetId 不能为空");
        }
    }

    // ===================== 子表管理 =====================

    @Override
    public WecomGetSheetsResponse getSheets(String docid) {
        requireValidDocid(docid);
        long startMs = System.currentTimeMillis();
        String params = buildParams("docid", docid);

        WecomCliResult result = wecomCliService.execute("doc", "smartsheet_get_sheet", params);

        WecomGetSheetsResponse response = new WecomGetSheetsResponse();
        if (!result.isSuccess()) {
            response.setSheets(Collections.emptyList());
            return response;
        }

        // 解析 CLI 返回：{"errcode":0, "sheet_list":[{"sheet_id":"xxx","title":"xxx",...},...]}
        try {
            JsonNode root = objectMapper.readTree(result.getRawOutput());
            // 兼容两种字段名：sheet_list（MCP CLI）和 sheets（API 文档）
            JsonNode sheetsNode = root.get("sheet_list");
            if (sheetsNode == null) {
                sheetsNode = root.get("sheets");
            }
            if (sheetsNode != null && sheetsNode.isArray()) {
                List<WecomGetSheetsResponse.SheetInfo> sheets = new ArrayList<>();
                for (JsonNode sheetNode : sheetsNode) {
                    WecomGetSheetsResponse.SheetInfo info = new WecomGetSheetsResponse.SheetInfo();
                    info.setSheetId(sheetNode.has("sheet_id") ? sheetNode.get("sheet_id").asText() : null);
                    info.setTitle(sheetNode.has("title") ? sheetNode.get("title").asText() : null);
                    info.setRowCount(sheetNode.has("row_count") ? sheetNode.get("row_count").asInt() : 0);
                    sheets.add(info);
                }
                response.setSheets(sheets);
            } else {
                response.setSheets(Collections.emptyList());
            }
        } catch (Exception e) {
            logger.warn("解析 getSheets 响应失败: {}", e.getMessage());
            response.setSheets(Collections.emptyList());
        }
        return response;
    }

    @Override
    public WecomAddSheetResponse addSheet(String docid, String title) {
        requireValidDocid(docid);
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("title 不能为空");
        }
        long startMs = System.currentTimeMillis();
        String params = buildParams("docid", docid, "title", title);

        WecomCliResult result = wecomCliService.execute("doc", "smartsheet_add_sheet", params);
        long durationMs = System.currentTimeMillis() - startMs;

        // 写日志
        String requestText = "ADD_SHEET";
        writeLog(null, result.isSuccess() ? "SUCCESS" : "FAILED",
                result.isSuccess() ? 1 : 0, result.getErrorCode(), result.getErrorMessage(),
                durationMs, requestText);

        if (!result.isSuccess()) {
            return new WecomAddSheetResponse(null, null);
        }

        // 解析返回：{"errcode":0, "properties":{"sheet_id":"xxx","title":"xxx"}}
        try {
            JsonNode root = objectMapper.readTree(result.getRawOutput());
            String sheetId = null;
            String respTitle = title;
            // CLI 返回格式：properties.sheet_id
            if (root.has("properties")) {
                JsonNode props = root.get("properties");
                if (props.has("sheet_id")) {
                    sheetId = props.get("sheet_id").asText();
                }
                if (props.has("title")) {
                    respTitle = props.get("title").asText();
                }
            }
            // 兼容直接返回格式：sheet_id
            if (sheetId == null && root.has("sheet_id")) {
                sheetId = root.get("sheet_id").asText();
            }
            return new WecomAddSheetResponse(sheetId, respTitle);
        } catch (Exception e) {
            logger.warn("解析 addSheet 响应失败: {}", e.getMessage());
            return new WecomAddSheetResponse(null, title);
        }
    }

    @Override
    public WecomAddSheetResponse updateSheet(String docid, String sheetId, String title) {
        requireValidDocid(docid);
        requireValidSheetId(sheetId);
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("title 不能为空");
        }
        long startMs = System.currentTimeMillis();
        String params = buildParams("docid", docid, "sheet_id", sheetId, "title", title);

        WecomCliResult result = wecomCliService.execute("doc", "smartsheet_update_sheet", params);
        long durationMs = System.currentTimeMillis() - startMs;

        String requestText = "UPDATE_SHEET";
        writeLog(null, result.isSuccess() ? "SUCCESS" : "FAILED",
                result.isSuccess() ? 1 : 0, result.getErrorCode(), result.getErrorMessage(),
                durationMs, requestText);

        if (!result.isSuccess()) {
            return new WecomAddSheetResponse(null, null);
        }

        try {
            JsonNode root = objectMapper.readTree(result.getRawOutput());
            String respSheetId = sheetId;
            String respTitle = title;
            // CLI 返回格式：properties.sheet_id
            if (root.has("properties")) {
                JsonNode props = root.get("properties");
                if (props.has("sheet_id")) {
                    respSheetId = props.get("sheet_id").asText();
                }
                if (props.has("title")) {
                    respTitle = props.get("title").asText();
                }
            }
            // 兼容直接返回格式
            if (root.has("sheet_id")) {
                respSheetId = root.get("sheet_id").asText();
            }
            if (root.has("title")) {
                respTitle = root.get("title").asText();
            }
            return new WecomAddSheetResponse(respSheetId, respTitle);
        } catch (Exception e) {
            logger.warn("解析 updateSheet 响应失败: {}", e.getMessage());
            return new WecomAddSheetResponse(sheetId, title);
        }
    }

    @Override
    public boolean deleteSheet(String docid, String sheetId) {
        requireValidDocid(docid);
        requireValidSheetId(sheetId);
        long startMs = System.currentTimeMillis();
        String params = buildParams("docid", docid, "sheet_id", sheetId);

        WecomCliResult result = wecomCliService.execute("doc", "smartsheet_delete_sheet", params);
        long durationMs = System.currentTimeMillis() - startMs;

        String requestText = "DELETE_SHEET";
        writeLog(null, result.isSuccess() ? "SUCCESS" : "FAILED",
                result.isSuccess() ? 1 : 0, result.getErrorCode(), result.getErrorMessage(),
                durationMs, requestText);

        // CLI 对已删除的重复调用也返回成功（幂等）
        return true;
    }

    // ===================== 字段管理 =====================

    @Override
    public WecomGetFieldsResponse getFields(String docid, String sheetId) {
        requireValidDocid(docid);
        requireValidSheetId(sheetId);
        String params = buildParams("docid", docid, "sheet_id", sheetId);

        WecomCliResult result = wecomCliService.execute("doc", "smartsheet_get_fields", params);

        WecomGetFieldsResponse response = new WecomGetFieldsResponse();
        if (!result.isSuccess()) {
            response.setFields(Collections.emptyList());
            return response;
        }

        // 解析：{"errcode":0, "fields":[{"field_id":"xxx","field_title":"xxx","field_type":"text"},...]}
        try {
            JsonNode root = objectMapper.readTree(result.getRawOutput());
            JsonNode fieldsNode = root.get("fields");
            if (fieldsNode != null && fieldsNode.isArray()) {
                List<WecomGetFieldsResponse.FieldInfo> fields = new ArrayList<>();
                for (JsonNode fieldNode : fieldsNode) {
                    WecomGetFieldsResponse.FieldInfo info = new WecomGetFieldsResponse.FieldInfo();
                    info.setFieldId(fieldNode.has("field_id") ? fieldNode.get("field_id").asText() : null);
                    info.setFieldTitle(fieldNode.has("field_title") ? fieldNode.get("field_title").asText() : null);
                    info.setFieldType(fieldNode.has("field_type") ? fieldNode.get("field_type").asText() : null);
                    fields.add(info);
                }
                response.setFields(fields);
            } else {
                response.setFields(Collections.emptyList());
            }
        } catch (Exception e) {
            logger.warn("解析 getFields 响应失败: {}", e.getMessage());
            response.setFields(Collections.emptyList());
        }
        return response;
    }

    @Override
    public WecomGetFieldsResponse addFields(String docid, String sheetId, WecomAddFieldsRequest.FieldItem[] fields) {
        requireValidDocid(docid);
        requireValidSheetId(sheetId);
        if (fields == null || fields.length == 0) {
            throw new IllegalArgumentException("fields 不能为空");
        }
        long startMs = System.currentTimeMillis();

        // 前置校验：字段类型
        for (WecomAddFieldsRequest.FieldItem field : fields) {
            if (!ALLOWED_FIELD_TYPES.contains(field.getFieldType())) {
                throw new IllegalArgumentException("不支持的字段类型: " + field.getFieldType()
                        + "，允许的类型: " + ALLOWED_FIELD_TYPES);
            }
        }

        // 前置校验：字段数量（现有 + 新增 <= 150）
        int currentCount = getFields(docid, sheetId).getFields().size();
        if (currentCount + fields.length > MAX_FIELDS_PER_SHEET) {
            throw new IllegalArgumentException("字段数量超限（当前 " + currentCount
                    + " + 新增 " + fields.length + " > 上限 " + MAX_FIELDS_PER_SHEET + "）");
        }

        // 构造 CLI 参数：--fields {"fields":[{"field_title":"xxx","field_type":"text"},...]}
        String fieldsJson;
        try {
            List<Map<String, String>> fieldList = new ArrayList<>();
            for (WecomAddFieldsRequest.FieldItem item : fields) {
                Map<String, String> m = new LinkedHashMap<>();
                m.put("field_title", item.getFieldTitle());
                m.put("field_type", item.getFieldType());
                fieldList.add(m);
            }
            Map<String, Object> wrapper = new LinkedHashMap<>();
            wrapper.put("fields", fieldList);
            fieldsJson = objectMapper.writeValueAsString(wrapper);
        } catch (Exception e) {
            WecomGetFieldsResponse resp = new WecomGetFieldsResponse();
            resp.setFields(Collections.emptyList());
            return resp;
        }

        String params = buildParams("docid", docid, "sheet_id", sheetId, "fields", fieldsJson);
        WecomCliResult result = wecomCliService.execute("doc", "smartsheet_add_fields", params);
        long durationMs = System.currentTimeMillis() - startMs;

        String requestText = "ADD_FIELDS";
        writeLog(null, result.isSuccess() ? "SUCCESS" : "FAILED",
                result.isSuccess() ? fields.length : 0, result.getErrorCode(), result.getErrorMessage(),
                durationMs, requestText);

        if (!result.isSuccess()) {
            return new WecomGetFieldsResponse(Collections.emptyList());
        }

        // 解析返回：{"errcode":0,"fields":[{"field_id":"xxx","field_title":"xxx","field_type":"text"},...]}
        try {
            JsonNode root = objectMapper.readTree(result.getRawOutput());
            JsonNode fieldsNode = root.get("fields");
            List<WecomGetFieldsResponse.FieldInfo> resultFields = new ArrayList<>();
            if (fieldsNode != null && fieldsNode.isArray()) {
                for (JsonNode fn : fieldsNode) {
                    WecomGetFieldsResponse.FieldInfo info = new WecomGetFieldsResponse.FieldInfo();
                    info.setFieldId(fn.has("field_id") ? fn.get("field_id").asText() : null);
                    info.setFieldTitle(fn.has("field_title") ? fn.get("field_title").asText() : null);
                    info.setFieldType(fn.has("field_type") ? fn.get("field_type").asText() : null);
                    resultFields.add(info);
                }
            }
            return new WecomGetFieldsResponse(resultFields);
        } catch (Exception e) {
            logger.warn("解析 addFields 响应失败: {}", e.getMessage());
            return new WecomGetFieldsResponse(Collections.emptyList());
        }
    }

    @Override
    public WecomGetFieldsResponse updateFields(String docid, String sheetId, WecomUpdateFieldsRequest.FieldItem[] fields) {
        requireValidDocid(docid);
        requireValidSheetId(sheetId);
        if (fields == null || fields.length == 0) {
            throw new IllegalArgumentException("fields 不能为空");
        }
        long startMs = System.currentTimeMillis();

        // 前置校验：字段类型
        for (WecomUpdateFieldsRequest.FieldItem field : fields) {
            if (field.getFieldType() != null && !ALLOWED_FIELD_TYPES.contains(field.getFieldType())) {
                throw new IllegalArgumentException("不支持的字段类型: " + field.getFieldType()
                        + "，允许的类型: " + ALLOWED_FIELD_TYPES);
            }
        }

        // 构造 CLI 参数
        String fieldsJson;
        try {
            List<Map<String, String>> fieldList = new ArrayList<>();
            for (WecomUpdateFieldsRequest.FieldItem item : fields) {
                Map<String, String> m = new LinkedHashMap<>();
                m.put("field_id", item.getFieldId());
                if (item.getFieldTitle() != null) m.put("field_title", item.getFieldTitle());
                if (item.getFieldType() != null) m.put("field_type", item.getFieldType());
                fieldList.add(m);
            }
            Map<String, Object> wrapper = new LinkedHashMap<>();
            wrapper.put("fields", fieldList);
            fieldsJson = objectMapper.writeValueAsString(wrapper);
        } catch (Exception e) {
            return new WecomGetFieldsResponse(Collections.emptyList());
        }

        String params = buildParams("docid", docid, "sheet_id", sheetId, "fields", fieldsJson);
        WecomCliResult result = wecomCliService.execute("doc", "smartsheet_update_fields", params);
        long durationMs = System.currentTimeMillis() - startMs;

        String requestText = "UPDATE_FIELDS";
        writeLog(null, result.isSuccess() ? "SUCCESS" : "FAILED",
                result.isSuccess() ? fields.length : 0, result.getErrorCode(), result.getErrorMessage(),
                durationMs, requestText);

        if (!result.isSuccess()) {
            return new WecomGetFieldsResponse(Collections.emptyList());
        }

        try {
            JsonNode root = objectMapper.readTree(result.getRawOutput());
            JsonNode fieldsNode = root.get("fields");
            List<WecomGetFieldsResponse.FieldInfo> resultFields = new ArrayList<>();
            if (fieldsNode != null && fieldsNode.isArray()) {
                for (JsonNode fn : fieldsNode) {
                    WecomGetFieldsResponse.FieldInfo info = new WecomGetFieldsResponse.FieldInfo();
                    info.setFieldId(fn.has("field_id") ? fn.get("field_id").asText() : null);
                    info.setFieldTitle(fn.has("field_title") ? fn.get("field_title").asText() : null);
                    info.setFieldType(fn.has("field_type") ? fn.get("field_type").asText() : null);
                    resultFields.add(info);
                }
            }
            return new WecomGetFieldsResponse(resultFields);
        } catch (Exception e) {
            logger.warn("解析 updateFields 响应失败: {}", e.getMessage());
            return new WecomGetFieldsResponse(Collections.emptyList());
        }
    }

    @Override
    public int deleteFields(String docid, String sheetId, String[] fieldIds) {
        requireValidDocid(docid);
        requireValidSheetId(sheetId);
        if (fieldIds == null || fieldIds.length == 0) {
            throw new IllegalArgumentException("fieldIds 不能为空");
        }
        long startMs = System.currentTimeMillis();

        String fieldIdsJson;
        try {
            fieldIdsJson = objectMapper.writeValueAsString(Arrays.asList(fieldIds));
        } catch (Exception e) {
            return 0;
        }

        String params = buildParams("docid", docid, "sheet_id", sheetId, "field_ids", fieldIdsJson);
        WecomCliResult result = wecomCliService.execute("doc", "smartsheet_delete_fields", params);
        long durationMs = System.currentTimeMillis() - startMs;

        String requestText = "DELETE_FIELDS";
        writeLog(null, result.isSuccess() ? "SUCCESS" : "FAILED",
                result.isSuccess() ? fieldIds.length : 0, result.getErrorCode(), result.getErrorMessage(),
                durationMs, requestText);

        // CLI 幂等：重复删除也返回成功
        return result.isSuccess() ? fieldIds.length : 0;
    }

    // ---- 私有工具方法 ----

    /**
     * 构造 CLI 参数 JSON（单个键值对）
     */
    private String buildParams(String key, String value) {
        try {
            Map<String, String> m = new LinkedHashMap<>();
            m.put(key, value);
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            throw new IllegalStateException("构建CLI参数失败", e);
        }
    }

    /**
     * 构造 CLI 参数 JSON（多个键值对）
     */
    private String buildParams(String k1, String v1, String k2, String v2) {
        try {
            Map<String, String> m = new LinkedHashMap<>();
            m.put(k1, v1);
            m.put(k2, v2);
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            throw new IllegalStateException("构建CLI参数失败", e);
        }
    }

    private String buildParams(String k1, String v1, String k2, String v2, String k3, String v3) {
        try {
            Map<String, String> m = new LinkedHashMap<>();
            m.put(k1, v1);
            m.put(k2, v2);
            m.put(k3, v3);
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            throw new IllegalStateException("构建CLI参数失败", e);
        }
    }

    /**
     * 写入 sync_log
     */
    private void writeLog(String sheetName, String status, int recordCount,
                          String errorCode, String errorMessage, long durationMs, String requestText) {
        try {
            FbsWecomSyncLog log = new FbsWecomSyncLog();
            log.setSyncType("SCHEMA");
            log.setSheetName(sheetName);
            log.setStatus(status);
            log.setRecordCount(recordCount);
            log.setErrorCode(errorCode);
            log.setErrorMessage(errorMessage != null && errorMessage.length() > 2000
                    ? errorMessage.substring(0, 2000) : errorMessage);
            log.setDurationMs(durationMs);
            syncLogMapper.insertSyncLog(log);
        } catch (Exception e) {
            logger.error("写入 sync_log 失败", e);
        }
    }
}
