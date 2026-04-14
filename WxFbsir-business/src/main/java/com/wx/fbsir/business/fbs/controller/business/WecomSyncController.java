package com.wx.fbsir.business.fbs.controller.business;

import com.wx.fbsir.business.fbs.dto.business.wecom.WecomCheckResponse;
import com.wx.fbsir.business.fbs.dto.business.wecom.WecomSyncReadRequest;
import com.wx.fbsir.business.fbs.dto.business.wecom.WecomSyncReadResponse;
import com.wx.fbsir.business.fbs.service.WecomSyncService;
import com.wx.fbsir.common.core.controller.BaseController;
import com.wx.fbsir.common.core.domain.AjaxResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    /**
     * POST /fbs/business/wecom/sync/read
     * 手动触发读取指定 Sheet 数据（仅支持 meta / commercial_hub）
     *
     * @param request 读取请求（可选 sheetName，不传默认 "meta"）
     * @return 读取响应（含前 10 条预览）
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
     * 检测 CLI 可用性（只检文件存在，不检认证态，不写日志）
     *
     * @return 检测响应
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:wecom:sync:check')")
    @PostMapping("/sync/check")
    public AjaxResult check() {
        WecomCheckResponse response = wecomSyncService.check();
        return AjaxResult.success(response);
    }
}
