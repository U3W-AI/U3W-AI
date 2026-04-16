package com.wx.fbsir.business.fbs.controller.business;

import com.wx.fbsir.business.fbs.dto.business.wecom.*;
import com.wx.fbsir.business.fbs.service.WecomSchemaService;
import com.wx.fbsir.business.fbs.service.WecomSyncService;
import com.wx.fbsir.business.fbs.service.WecomWriteService;
import com.wx.fbsir.common.core.controller.BaseController;
import com.wx.fbsir.common.core.domain.AjaxResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 企微同步 Controller
 *
 * <p>MVP：手动触发读取 + CLI 可用性检测，需要 JWT 认证 + 功能权限。</p>
 * <p>权限前缀：business:fbs:wecom（与运营侧 business:fbs: 体系一致）</p>
 *
 * @author wxfbsir
 * @date 2026-04-13
 */
@RestController
@RequestMapping("/fbs/business/wecom")
public class WecomSyncController extends BaseController {

    @Autowired
    private WecomSyncService wecomSyncService;

    @Autowired
    private WecomWriteService wecomWriteService;

    @Autowired
    private WecomSchemaService wecomSchemaService;

    // ===================== 已有端点 =====================

    /**
     * POST /fbs/business/wecom/sync/read
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:wecom:sync:read')")
    @PostMapping("/sync/read")
    public AjaxResult read(@RequestBody(required = false) WecomSyncReadRequest request) {
        String sheetName = (request != null) ? request.getSheetName() : null;
        WecomSyncReadResponse response = wecomSyncService.readSheet(sheetName);
        return AjaxResult.success(response);
    }

    /**
     * POST /fbs/business/wecom/sync/check
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:wecom:sync:check')")
    @PostMapping("/sync/check")
    public AjaxResult check() {
        WecomCheckResponse response = wecomSyncService.check();
        return AjaxResult.success(response);
    }

    /**
     * POST /fbs/business/wecom/sync/write
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:wecom:sync:write')")
    @PostMapping("/sync/write")
    public AjaxResult write(@RequestBody(required = false) WecomSyncWriteRequest request) {
        String sheetName = (request != null) ? request.getSheetName() : null;
        WecomSyncWriteResponse response = wecomWriteService.writeRecords(sheetName,
                request != null ? request.getRecords() : null);
        return AjaxResult.success(response);
    }

    // ===================== 子表管理 =====================

    /**
     * GET /fbs/business/wecom/schema/sheets
     * 查询子表列表
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:wecom:schema:read')")
    @GetMapping("/schema/sheets")
    public AjaxResult getSheets(@RequestParam String docid) {
        WecomGetSheetsResponse response = wecomSchemaService.getSheets(docid);
        return AjaxResult.success(response);
    }

    /**
     * POST /fbs/business/wecom/schema/sheet
     * 添加子表
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:wecom:schema:write')")
    @PostMapping("/schema/sheet")
    public AjaxResult addSheet(@RequestBody WecomAddSheetRequest request) {
        WecomAddSheetResponse response = wecomSchemaService.addSheet(request.getDocid(), request.getTitle());
        return AjaxResult.success(response);
    }

    /**
     * PUT /fbs/business/wecom/schema/sheet
     * 更新子表标题
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:wecom:schema:write')")
    @PutMapping("/schema/sheet")
    public AjaxResult updateSheet(@RequestBody WecomUpdateSheetRequest request) {
        WecomAddSheetResponse response = wecomSchemaService.updateSheet(
                request.getDocid(), request.getSheetId(), request.getTitle());
        return AjaxResult.success(response);
    }

    /**
     * DELETE /fbs/business/wecom/schema/sheet
     * 删除子表
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:wecom:schema:delete')")
    @DeleteMapping("/schema/sheet")
    public AjaxResult deleteSheet(@RequestParam String docid, @RequestParam String sheetId) {
        boolean success = wecomSchemaService.deleteSheet(docid, sheetId);
        return AjaxResult.success(success);
    }

    // ===================== 字段管理 =====================

    /**
     * GET /fbs/business/wecom/schema/fields
     * 查询字段列表
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:wecom:schema:read')")
    @GetMapping("/schema/fields")
    public AjaxResult getFields(@RequestParam String docid, @RequestParam String sheetId) {
        WecomGetFieldsResponse response = wecomSchemaService.getFields(docid, sheetId);
        return AjaxResult.success(response);
    }

    /**
     * POST /fbs/business/wecom/schema/fields
     * 添加字段
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:wecom:schema:write')")
    @PostMapping("/schema/fields")
    public AjaxResult addFields(@RequestBody WecomAddFieldsRequest request) {
        WecomGetFieldsResponse response = wecomSchemaService.addFields(
                request.getDocid(), request.getSheetId(),
                request.getFields() != null ? request.getFields().toArray(new WecomAddFieldsRequest.FieldItem[0]) : null);
        return AjaxResult.success(response);
    }

    /**
     * PUT /fbs/business/wecom/schema/fields
     * 更新字段
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:wecom:schema:write')")
    @PutMapping("/schema/fields")
    public AjaxResult updateFields(@RequestBody WecomUpdateFieldsRequest request) {
        WecomGetFieldsResponse response = wecomSchemaService.updateFields(
                request.getDocid(), request.getSheetId(),
                request.getFields() != null ? request.getFields().toArray(new WecomUpdateFieldsRequest.FieldItem[0]) : null);
        return AjaxResult.success(response);
    }

    /**
     * DELETE /fbs/business/wecom/schema/fields
     * 删除字段
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:wecom:schema:delete')")
    @DeleteMapping("/schema/fields")
    public AjaxResult deleteFields(@RequestParam String docid, @RequestParam String sheetId,
                                   @RequestParam List<String> fieldIds) {
        int deletedCount = wecomSchemaService.deleteFields(docid, sheetId,
                fieldIds.toArray(new String[0]));
        return AjaxResult.success(deletedCount);
    }

    // ===================== 记录更新/删除 =====================

    /**
     * PUT /fbs/business/wecom/records
     * 更新记录
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:wecom:records:write')")
    @PutMapping("/records")
    public AjaxResult updateRecords(@RequestBody(required = false) WecomUpdateRecordsRequest request) {
        if (request == null || request.getRecords() == null) {
            throw new IllegalArgumentException("records 不能为空");
        }
        List<Map<String, Object>> records = new java.util.ArrayList<>();
        if (request.getRecords() != null) {
            for (WecomUpdateRecordsRequest.RecordItem item : request.getRecords()) {
                Map<String, Object> map = new java.util.HashMap<>();
                map.put("recordId", item.getRecordId());
                map.put("values", item.getValues());
                records.add(map);
            }
        }
        WecomSyncWriteResponse response = wecomWriteService.updateRecords(request.getSheetId(),
                records);
        return AjaxResult.success(response);
    }

    /**
     * DELETE /fbs/business/wecom/records
     * 删除记录
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:wecom:records:delete')")
    @DeleteMapping("/records")
    public AjaxResult deleteRecords(@RequestBody(required = false) WecomDeleteRecordsRequest request) {
        if (request == null || request.getRecordIds() == null) {
            throw new IllegalArgumentException("recordIds 不能为空");
        }
        WecomSyncWriteResponse response = wecomWriteService.deleteRecords(request.getSheetId(),
                request.getRecordIds());
        return AjaxResult.success(response);
    }
}
