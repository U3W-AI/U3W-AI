package com.wx.fbsir.business.fbs.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.fbsir.business.fbs.domain.WecomCliResult;
import com.wx.fbsir.business.fbs.domain.entity.FbsWecomSyncLog;
import com.wx.fbsir.business.fbs.dto.business.wecom.WecomSyncWriteResponse;
import com.wx.fbsir.business.fbs.mapper.FbsWecomSyncLogMapper;
import com.wx.fbsir.business.fbs.service.WecomCliService;
import com.wx.fbsir.business.fbs.service.WecomWriteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 企微智能表格写入服务实现
 *
 * <p>核心职责：分片写入编排 + 日志记录。一次 write 写一条 sync_log。</p>
 * <p>重试由底层 WecomCliService.execute() 承担（NET_ ×1），本层不加重试。</p>
 *
 * @author wxfbsir
 * @date 2026-04-14
 */
@Service
public class WecomWriteServiceImpl implements WecomWriteService {

    private static final Logger logger = LoggerFactory.getLogger(WecomWriteServiceImpl.class);

    @Autowired
    private WecomCliService wecomCliService;

    @Autowired
    private FbsWecomSyncLogMapper syncLogMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 默认 Sheet */
    private static final String DEFAULT_SHEET = "meta";

    /** 最大 payload 上限（字节），单条超过此值直接拒绝 */
    @Value("${wecom.write.max-payload-bytes:20480}")
    private int maxPayloadBytes;

    /** 企微智能表格 URL（CLI 支持 URL 或 docid 二选一） */
    @Value("${wecom.sheet.doc-url:}")
    private String docUrl;

    /** meta sheet_id */
    @Value("${wecom.sheet.sheet-ids.meta:}")
    private String metaSheetId;

    /** commercial_hub sheet_id */
    @Value("${wecom.sheet.sheet-ids.commercial-hub:}")
    private String commercialHubSheetId;

    /** entitlement sheet_id */
    @Value("${wecom.sheet.sheet-ids.entitlement:}")
    private String entitlementSheetId;

    /**
     * MVP 允许写入的 Sheet 白名单
     */
    private static final Set<String> ALLOWED_SHEETS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList("meta", "commercial_hub", "entitlement")));

    // ===================== writeRecords =====================

    @Override
    public WecomSyncWriteResponse writeRecords(String sheetName, List<Map<String, Object>> records) {
        String sheet = resolveSheetName(sheetName);
        long totalStartMs = System.currentTimeMillis();

        if (records == null) {
            long durationMs = System.currentTimeMillis() - totalStartMs;
            return WecomSyncWriteResponse.fail(sheet, "INVALID_REQUEST",
                    "records 不能为 null", durationMs, null);
        }

        if (records.isEmpty()) {
            long durationMs = System.currentTimeMillis() - totalStartMs;
            return WecomSyncWriteResponse.noOp(sheet, durationMs);
        }

        if (!ALLOWED_SHEETS.contains(sheet)) {
            long durationMs = System.currentTimeMillis() - totalStartMs;
            return WecomSyncWriteResponse.fail(sheet, "INVALID_SHEET",
                    "不支持的 Sheet 名称，仅允许: " + ALLOWED_SHEETS, durationMs, null);
        }

        List<byte[]> serializedRecords = new ArrayList<>(records.size());
        for (Map<String, Object> record : records) {
            byte[] bytes = serializeRecord(record);
            if (bytes.length > maxPayloadBytes) {
                long durationMs = System.currentTimeMillis() - totalStartMs;
                return WecomSyncWriteResponse.fail(sheet, "BIZ_PAYLOAD_TOO_LARGE",
                        "单条记录超过 payload 上限 (" + bytes.length + " > " + maxPayloadBytes + " bytes)",
                        durationMs, null);
            }
            serializedRecords.add(bytes);
        }

        List<List<Integer>> shards = splitRecords(serializedRecords);
        int totalWritten = 0;
        String failErrorCode = null;
        String failErrorMessage = null;

        for (int i = 0; i < shards.size(); i++) {
            List<Integer> shardIndices = shards.get(i);
            List<Map<String, Object>> shardRecords = new ArrayList<>(shardIndices.size());
            for (Integer idx : shardIndices) {
                shardRecords.add(records.get(idx));
            }

            String params = buildWriteParams(sheet, shardRecords);
            WecomCliResult cliResult = wecomCliService.execute("doc", "smartsheet_add_records", params);

            if (!cliResult.isSuccess()) {
                failErrorCode = cliResult.getErrorCode();
                failErrorMessage = cliResult.getErrorMessage();
                logger.warn("写入分片 {}/{} 失败: {} - {}",
                        i + 1, shards.size(), failErrorCode, failErrorMessage);
                break;
            }
            totalWritten += shardRecords.size();
        }

        long durationMs = System.currentTimeMillis() - totalStartMs;

        if (failErrorCode != null) {
            String errorCode = (totalWritten > 0) ? "PARTIAL_WRITE_FAILED" : failErrorCode;
            String errorMsg = (totalWritten > 0)
                    ? "部分分片写入失败: " + totalWritten + "/" + records.size() + " 条已写入"
                    : failErrorMessage;
            Long logId = writeLog(sheet, "FAILED", totalWritten, errorCode, errorMsg, durationMs, "WRITE");
            if (totalWritten > 0) {
                return WecomSyncWriteResponse.partialFail(sheet, totalWritten, shards.size(),
                        errorCode, errorMsg, durationMs, logId);
            }
            return WecomSyncWriteResponse.fail(sheet, errorCode, errorMsg, durationMs, logId);
        }

        Long logId = writeLog(sheet, "SUCCESS", totalWritten, null, null, durationMs, "WRITE");
        return WecomSyncWriteResponse.ok(sheet, records.size(), totalWritten, shards.size(),
                durationMs, logId);
    }

    // ===================== updateRecords =====================

    @Override
    public WecomSyncWriteResponse updateRecords(String sheetName, List<Map<String, Object>> records) {
        String sheet = resolveSheetName(sheetName);
        long totalStartMs = System.currentTimeMillis();

        if (records == null) {
            long durationMs = System.currentTimeMillis() - totalStartMs;
            return WecomSyncWriteResponse.fail(sheet, "INVALID_REQUEST",
                    "records 不能为 null", durationMs, null);
        }

        if (records.isEmpty()) {
            long durationMs = System.currentTimeMillis() - totalStartMs;
            return WecomSyncWriteResponse.noOp(sheet, durationMs);
        }

        if (!ALLOWED_SHEETS.contains(sheet)) {
            long durationMs = System.currentTimeMillis() - totalStartMs;
            return WecomSyncWriteResponse.fail(sheet, "INVALID_SHEET",
                    "不支持的 Sheet 名称，仅允许: " + ALLOWED_SHEETS, durationMs, null);
        }

        if (records.size() > 100) {
            long durationMs = System.currentTimeMillis() - totalStartMs;
            return WecomSyncWriteResponse.fail(sheet, "RECORD_LIMIT_EXCEEDED",
                    "单次操作记录数量超过限制（最多100条）", durationMs, null);
        }

        String params = buildRecordsParams(sheet, records);
        WecomCliResult cliResult = wecomCliService.execute("doc", "smartsheet_update_records", params);

        long durationMs = System.currentTimeMillis() - totalStartMs;
        String failErrorCode = cliResult.isSuccess() ? null : cliResult.getErrorCode();
        String failErrorMessage = cliResult.isSuccess() ? null : cliResult.getErrorMessage();
        int updatedCount = cliResult.isSuccess() ? records.size() : 0;

        if (failErrorCode != null) {
            Long logId = writeLog(sheet, "FAILED", updatedCount, failErrorCode, failErrorMessage, durationMs, "RECORD");
            return WecomSyncWriteResponse.fail(sheet, failErrorCode, failErrorMessage, durationMs, logId);
        }

        Long logId = writeLog(sheet, "SUCCESS", updatedCount, null, null, durationMs, "RECORD");
        return WecomSyncWriteResponse.ok(sheet, records.size(), updatedCount, 1, durationMs, logId);
    }

    // ===================== deleteRecords =====================

    @Override
    public WecomSyncWriteResponse deleteRecords(String sheetName, List<String> recordIds) {
        String sheet = resolveSheetName(sheetName);
        long totalStartMs = System.currentTimeMillis();

        if (recordIds == null) {
            long durationMs = System.currentTimeMillis() - totalStartMs;
            return WecomSyncWriteResponse.fail(sheet, "INVALID_REQUEST",
                    "recordIds 不能为 null", durationMs, null);
        }

        if (recordIds.isEmpty()) {
            long durationMs = System.currentTimeMillis() - totalStartMs;
            return WecomSyncWriteResponse.noOp(sheet, durationMs);
        }

        if (!ALLOWED_SHEETS.contains(sheet)) {
            long durationMs = System.currentTimeMillis() - totalStartMs;
            return WecomSyncWriteResponse.fail(sheet, "INVALID_SHEET",
                    "不支持的 Sheet 名称，仅允许: " + ALLOWED_SHEETS, durationMs, null);
        }

        if (recordIds.size() > 100) {
            long durationMs = System.currentTimeMillis() - totalStartMs;
            return WecomSyncWriteResponse.fail(sheet, "RECORD_LIMIT_EXCEEDED",
                    "单次操作记录数量超过限制（最多100条）", durationMs, null);
        }

        String params = buildDeleteParams(sheet, recordIds);
        WecomCliResult cliResult = wecomCliService.execute("doc", "smartsheet_delete_records", params);

        long durationMs = System.currentTimeMillis() - totalStartMs;
        String failErrorCode = cliResult.isSuccess() ? null : cliResult.getErrorCode();
        String failErrorMessage = cliResult.isSuccess() ? null : cliResult.getErrorMessage();
        int deletedCount = cliResult.isSuccess() ? recordIds.size() : 0;

        if (failErrorCode != null) {
            Long logId = writeLog(sheet, "FAILED", deletedCount, failErrorCode, failErrorMessage, durationMs, "RECORD");
            return WecomSyncWriteResponse.fail(sheet, failErrorCode, failErrorMessage, durationMs, logId);
        }

        Long logId = writeLog(sheet, "SUCCESS", deletedCount, null, null, durationMs, "RECORD");
        return WecomSyncWriteResponse.ok(sheet, recordIds.size(), deletedCount, 1, durationMs, logId);
    }

    // ---- 私有工具方法 ----

    private String resolveSheetName(String sheetName) {
        if (sheetName == null || sheetName.trim().isEmpty()) {
            return DEFAULT_SHEET;
        }
        return sheetName.trim();
    }

    private byte[] serializeRecord(Map<String, Object> record) {
        try {
            return objectMapper.writeValueAsBytes(record);
        } catch (Exception e) {
            return record.toString().getBytes(StandardCharsets.UTF_8);
        }
    }

    private List<List<Integer>> splitRecords(List<byte[]> serializedRecords) {
        List<List<Integer>> shards = new ArrayList<>();
        List<Integer> current = new ArrayList<>();
        int currentSize = 0;

        for (int i = 0; i < serializedRecords.size(); i++) {
            int recordSize = serializedRecords.get(i).length;
            if (currentSize + recordSize > maxPayloadBytes && !current.isEmpty()) {
                shards.add(current);
                current = new ArrayList<>();
                currentSize = 0;
            }
            current.add(i);
            currentSize += recordSize;
        }
        if (!current.isEmpty()) {
            shards.add(current);
        }
        return shards;
    }

    private String buildWriteParams(String sheetName, List<Map<String, Object>> shardRecords) {
        String sheetId = resolveSheetId(sheetName);
        try {
            // CLI smartsheet_add_records 需要 values 包装
            List<Map<String, Object>> wrappedRecords = new ArrayList<>();
            for (Map<String, Object> record : shardRecords) {
                Map<String, Object> wrappedRecord = new LinkedHashMap<>();
                wrappedRecord.put("values", record);
                wrappedRecords.add(wrappedRecord);
            }

            Map<String, Object> params = new LinkedHashMap<>();
            params.put("url", docUrl);
            params.put("sheet_id", sheetId);
            params.put("records", wrappedRecords);
            return objectMapper.writeValueAsString(params);
        } catch (Exception e) {
            throw new IllegalStateException("构建写入参数失败: " + e.getMessage(), e);
        }
    }

    private String buildRecordsParams(String sheetName, List<Map<String, Object>> records) {
        String sheetId = resolveSheetId(sheetName);
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("url", docUrl);
            params.put("sheet_id", sheetId);
            params.put("records", records);
            return objectMapper.writeValueAsString(params);
        } catch (Exception e) {
            throw new IllegalStateException("构建更新参数失败: " + e.getMessage(), e);
        }
    }

    private String buildDeleteParams(String sheetName, List<String> recordIds) {
        String sheetId = resolveSheetId(sheetName);
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("url", docUrl);
            params.put("sheet_id", sheetId);
            params.put("record_ids", recordIds);
            return objectMapper.writeValueAsString(params);
        } catch (Exception e) {
            throw new IllegalStateException("构建删除参数失败: " + e.getMessage(), e);
        }
    }

    private String resolveSheetId(String sheetName) {
        switch (sheetName) {
            case "meta":
                return metaSheetId;
            case "commercial_hub":
                return commercialHubSheetId;
            case "entitlement":
                return entitlementSheetId;
            default:
                return sheetName;
        }
    }

    private Long writeLog(String sheetName, String status, int recordCount,
                          String errorCode, String errorMessage, long durationMs, String syncType) {
        try {
            FbsWecomSyncLog log = new FbsWecomSyncLog();
            log.setSyncType(syncType);
            log.setSheetName(sheetName);
            log.setStatus(status);
            log.setRecordCount(recordCount);
            log.setErrorCode(errorCode);
            log.setErrorMessage(errorMessage != null && errorMessage.length() > 2000
                    ? errorMessage.substring(0, 2000) : errorMessage);
            log.setDurationMs(durationMs);
            syncLogMapper.insertSyncLog(log);
            return log.getId();
        } catch (Exception e) {
            logger.error("写入 sync_log 失败", e);
            return null;
        }
    }
}