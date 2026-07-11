package com.wx.fbsir.business.fbs.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.fbsir.business.fbs.domain.WecomCliResult;
import com.wx.fbsir.business.fbs.domain.entity.FbsWecomSyncLog;
import com.wx.fbsir.business.fbs.dto.business.wecom.WecomCheckResponse;
import com.wx.fbsir.business.fbs.dto.business.wecom.WecomSyncReadResponse;
import com.wx.fbsir.business.fbs.mapper.FbsWecomSyncLogMapper;
import com.wx.fbsir.business.fbs.service.WecomCliService;
import com.wx.fbsir.business.fbs.service.WecomSyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 企微同步服务实现
 *
 * <p>核心职责：业务编排 + 日志写入。一次 read 只写一条 sync_log。</p>
 *
 * @author FBSir
 * @date 2026-04-13
 */
@Service
public class WecomSyncServiceImpl implements WecomSyncService {

    @Autowired
    private WecomCliService wecomCliService;

    @Autowired
    private FbsWecomSyncLogMapper syncLogMapper;

    @Value("${wecom.sheet.doc-url:}")
    private String docUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 预览条数上限 */
    private static final int PREVIEW_LIMIT = 10;

    /** 默认 Sheet */
    private static final String DEFAULT_SHEET = "meta";

    /**
     * MVP 允许读取的 Sheet 白名单（与 proposal 一致：meta + commercial_hub）
     * 传入不在此集合中的 sheetName，直接返回 INVALID_SHEET 业务失败，不发起 CLI 调用。
     */
    private static final Set<String> ALLOWED_SHEETS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList("meta", "commercial_hub")));

    /**
     * Sheet 标题 → sheet_id 映射（初始化时从配置加载，运行时通过 CLI 查询更新）
     * MVP: 静态配置在 application.yml 中，格式: wecom.sheet.sheet-ids.meta=q979lj
     */
    @Value("${wecom.sheet.sheet-ids.meta:}")
    private String metaSheetId;

    @Value("${wecom.sheet.sheet-ids.commercial-hub:}")
    private String commercialHubSheetId;

    @Override
    public WecomSyncReadResponse readSheet(String sheetName) {
        String sheet = (sheetName != null && !sheetName.trim().isEmpty())
                ? sheetName.trim() : DEFAULT_SHEET;
        long totalStartMs = System.currentTimeMillis();

        // ── P0-1: 白名单校验，不允许透传任意 sheetName ──────────────────────────
        if (!ALLOWED_SHEETS.contains(sheet)) {
            long durationMs = System.currentTimeMillis() - totalStartMs;
            // 不写日志：无效请求属于调用方错误，与 check 接口同等处理
            return WecomSyncReadResponse.fail(sheet, "INVALID_SHEET",
                    "不支持的 Sheet 名称，仅允许: " + ALLOWED_SHEETS, durationMs, null);
        }

        try {
            // 1. 检查 CLI 可用性
            if (!wecomCliService.isAvailable()) {
                long durationMs = System.currentTimeMillis() - totalStartMs;
                Long logId = writeLog(sheet, "FAILED", 0, "CLI_NOT_FOUND",
                        "wecom-cli 不可用", null, durationMs, null);
                return WecomSyncReadResponse.fail(sheet, "CLI_NOT_FOUND",
                        "wecom-cli 不可用", durationMs, logId);
            }

            // 2. 构造参数
            String params = buildParams(sheet);

            // 3. 调用 CLI
            WecomCliResult cliResult = wecomCliService.execute("doc", "smartsheet_get_records", params);

            // 4. 处理 CLI 执行失败
            if (!cliResult.isSuccess()) {
                long durationMs = System.currentTimeMillis() - totalStartMs;
                String logStatus = mapErrorCodeToStatus(cliResult.getErrorCode());
                Long logId = writeLog(sheet, logStatus, 0,
                        cliResult.getErrorCode(), cliResult.getErrorMessage(),
                        null, durationMs, null);
                return WecomSyncReadResponse.fail(sheet, cliResult.getErrorCode(),
                        cliResult.getErrorMessage(), durationMs, logId);
            }

            // 5. 解析双层 JSON
            List<Map<String, Object>> records;
            try {
                records = parseDoubleLayerJson(cliResult.getRawOutput());
            } catch (Exception e) {
                long durationMs = System.currentTimeMillis() - totalStartMs;
                Long logId = writeLog(sheet, "PARSE_ERROR", 0, "PARSE_ERROR",
                        "JSON 解析失败: " + e.getMessage(), null, durationMs, null);
                return WecomSyncReadResponse.fail(sheet, "PARSE_ERROR",
                        "JSON 解析失败: " + e.getMessage(), durationMs, logId);
            }

            // 6. 成功：写日志 + 返回预览
            long durationMs = System.currentTimeMillis() - totalStartMs;
            int recordCount = records.size();
            List<Map<String, Object>> preview = recordCount > PREVIEW_LIMIT
                    ? records.subList(0, PREVIEW_LIMIT) : records;

            String snapshotJson;
            try {
                snapshotJson = objectMapper.writeValueAsString(records);
            } catch (Exception e) {
                snapshotJson = null;
            }

            Long logId = writeLog(sheet, "SUCCESS", recordCount, null,
                    null, snapshotJson, durationMs, null);

            return WecomSyncReadResponse.ok(sheet, recordCount, durationMs, logId, preview);

        } catch (Exception e) {
            // 兜底：所有未捕获异常转为 fail response + FAILED 日志
            long durationMs = System.currentTimeMillis() - totalStartMs;
            Long logId = writeLog(sheet, "FAILED", 0, "UNKNOWN",
                    e.getMessage(), null, durationMs, null);
            return WecomSyncReadResponse.fail(sheet, "UNKNOWN",
                    e.getMessage(), durationMs, logId);
        }
    }

    @Override
    public WecomCheckResponse check() {
        if (wecomCliService.isAvailable()) {
            return WecomCheckResponse.available(getConfiguredCliPath());
        }
        return WecomCheckResponse.unavailable(getConfiguredCliPath(), "wecom-cli 文件不存在");
    }

    // ---- 私有方法 ----

    /**
     * 构造 smartsheet_get_records 参数 JSON
     * CLI 实际需要: {"url": "xxx", "sheet_id": "q979lj"}
     * 支持使用 URL 或 docid 二选一
     */
    private String buildParams(String sheetName) {
        String sheetId = resolveSheetId(sheetName);
        try {
            Map<String, String> params = Map.of("url", docUrl, "sheet_id", sheetId);
            return objectMapper.writeValueAsString(params);
        } catch (Exception e) {
            return "{\"url\":\"" + docUrl + "\",\"sheet_id\":\"" + sheetId + "\"}";
        }
    }

    /**
     * Sheet 标题 → sheet_id 解析
     * MVP: 从 application.yml 配置的静态映射获取
     */
    private String resolveSheetId(String sheetName) {
        switch (sheetName) {
            case "meta":
                return metaSheetId;
            case "commercial_hub":
                return commercialHubSheetId;
            default:
                return sheetName; // fallback（白名单校验已拦截，此处不太会到达）
        }
    }

    /**
     * 解析双层 JSON：outer.content[0].text → inner JSON → records 数组
     *
     * <p>CLI 返回格式：</p>
     * <pre>{@code
     * { "content": [{ "text": "{ \"errcode\":0, \"records\":[...] }" }] }
     * }</pre>
     *
     * <p>P0-2: content 缺失或 content[0].text 为空均视为 PARSE_ERROR，
     * 抛出异常由上层捕获，落 PARSE_ERROR 日志，不静默返回空列表。</p>
     */
    private List<Map<String, Object>> parseDoubleLayerJson(String rawOutput) throws Exception {
        JsonNode outer = objectMapper.readTree(rawOutput);
        JsonNode contentArray = outer.path("content");

        if (contentArray.isMissingNode() || !contentArray.isArray() || contentArray.isEmpty()) {
            throw new IllegalArgumentException("响应缺少 content 数组，原始输出: "
                    + truncate(rawOutput, 200));
        }

        String innerJson = contentArray.get(0).path("text").asText(null);
        if (innerJson == null || innerJson.trim().isEmpty()) {
            throw new IllegalArgumentException("content[0].text 为空，原始输出: "
                    + truncate(rawOutput, 200));
        }

        // 解析内层 JSON
        JsonNode inner = objectMapper.readTree(innerJson);

        // 检查 errcode
        int errcode = inner.path("errcode").asInt(-1);
        if (errcode != 0) {
            String errmsg = inner.path("errmsg").asText("未知错误");
            throw new IllegalArgumentException("企微接口返回错误: errcode=" + errcode
                    + ", errmsg=" + errmsg);
        }

        // 提取 records 数组
        JsonNode recordsNode = inner.path("records");
        if (recordsNode.isMissingNode() || !recordsNode.isArray()) {
            throw new IllegalArgumentException("内层 JSON 缺少 records 数组，原始输出: "
                    + truncate(innerJson, 200));
        }

        return objectMapper.readValue(recordsNode.toString(),
                new TypeReference<List<Map<String, Object>>>() {});
    }

    /** 截断过长字符串（避免错误消息超 DB 字段限制） */
    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    /**
     * 错误码 → 日志 status 大类映射
     */
    private String mapErrorCodeToStatus(String errorCode) {
        if (errorCode == null) return "FAILED";
        switch (errorCode) {
            case "EXEC_TIMEOUT":
                return "TIMEOUT";
            case "PARSE_ERROR":
                return "PARSE_ERROR";
            default:
                return "FAILED";
        }
    }

    /**
     * 写入 sync_log（统一入口，一次 read 只调用一次）
     */
    private Long writeLog(String sheetName, String status, int recordCount,
                          String errorCode, String errorMessage,
                          String snapshotJson, long durationMs, Long createdBy) {
        try {
            FbsWecomSyncLog log = new FbsWecomSyncLog();
            log.setSyncType("READ");
            log.setSheetName(sheetName);
            log.setStatus(status);
            log.setRecordCount(recordCount);
            log.setErrorCode(errorCode);
            log.setErrorMessage(errorMessage != null && errorMessage.length() > 2000
                    ? errorMessage.substring(0, 2000) : errorMessage);
            log.setSnapshotJson(snapshotJson);
            log.setDurationMs(durationMs);
            log.setCreatedBy(createdBy);
            syncLogMapper.insertSyncLog(log);
            return log.getId();
        } catch (Exception e) {
            // 日志写入失败不影响主流程
            return null;
        }
    }

    /**
     * 获取配置的 CLI 路径（用于 check 响应）
     */
    private String getConfiguredCliPath() {
        return wecomCliService.getCliPath();
    }
}
