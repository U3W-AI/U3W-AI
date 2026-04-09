package com.wx.fbsir.business.fbs.controller.business;

import com.wx.fbsir.business.fbs.domain.entity.FbsAuthCode;
import com.wx.fbsir.business.fbs.dto.business.auth_code.AuthCodeGenerateRequest;
import com.wx.fbsir.business.fbs.dto.business.auth_code.AuthCodePageRequest;
import com.wx.fbsir.business.fbs.service.business.IFbsAuthCodeBusinessService;
import com.wx.fbsir.common.annotation.Log;
import com.wx.fbsir.common.core.controller.BaseController;
import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.core.page.TableDataInfo;
import com.wx.fbsir.common.enums.BusinessType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 授权码运营Controller
 * 路径: /business/fbs/auth-code/*
 *
 * @author wxfbsir
 * @date 2026-04-08
 */
@RestController
@RequestMapping("/business/fbs/auth-code")
public class FbsAuthCodeBusinessController extends BaseController {

    @Autowired
    private IFbsAuthCodeBusinessService authCodeBusinessService;

    /**
     * GET /business/fbs/auth-code/list
     * 授权码分页列表
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:authCode:list')")
    @GetMapping("/list")
    public TableDataInfo list(AuthCodePageRequest request) {
        startPage();
        List<FbsAuthCode> list = authCodeBusinessService.getAuthCodePage(request);
        return getDataTable(list);
    }

    /**
     * POST /business/fbs/auth-code/generate
     * 批量生成授权码
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:authCode:generate')")
    @Log(title = "FBS授权码-生成", businessType = BusinessType.INSERT)
    @PostMapping("/generate")
    public AjaxResult generate(@RequestBody AuthCodeGenerateRequest request) {
        if (request.getTargetId() == null) {
            return AjaxResult.error("targetId不能为空");
        }
        if (request.getCount() == null || request.getCount() < 1) {
            request.setCount(1);
        }
        if (request.getCount() > 1000) {
            return AjaxResult.error("单次最多生成1000个授权码");
        }
        Map<Long, String> result = authCodeBusinessService.generateAuthCodeBatch(request, getUsername());
        return AjaxResult.success("生成成功", result);
    }

    /**
     * PUT /business/fbs/auth-code/disable
     * 禁用授权码
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:authCode:disable')")
    @Log(title = "FBS授权码-禁用", businessType = BusinessType.UPDATE)
    @PutMapping("/disable")
    public AjaxResult disable(@RequestBody IdRequest request) {
        if (request.getId() == null) {
            return AjaxResult.error("id不能为空");
        }
        boolean ok = authCodeBusinessService.disableAuthCode(request.getId());
        if (!ok) {
            return AjaxResult.error("禁用失败（仅启用状态可禁用）");
        }
        return AjaxResult.success("禁用成功");
    }

    /**
     * PUT /business/fbs/auth-code/enable
     * 启用授权码
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:authCode:enable')")
    @Log(title = "FBS授权码-启用", businessType = BusinessType.UPDATE)
    @PutMapping("/enable")
    public AjaxResult enable(@RequestBody IdRequest request) {
        if (request.getId() == null) {
            return AjaxResult.error("id不能为空");
        }
        boolean ok = authCodeBusinessService.enableAuthCode(request.getId());
        if (!ok) {
            return AjaxResult.error("启用失败（仅禁用状态且未用尽/未过期/未撤销的授权码可启用）");
        }
        return AjaxResult.success("启用成功");
    }

    /**
     * PUT /business/fbs/auth-code/revoke
     * 撤销授权码
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:authCode:revoke')")
    @Log(title = "FBS授权码-撤销", businessType = BusinessType.UPDATE)
    @PutMapping("/revoke")
    public AjaxResult revoke(@RequestBody IdRequest request) {
        if (request.getId() == null) {
            return AjaxResult.error("id不能为空");
        }
        boolean ok = authCodeBusinessService.revokeAuthCode(request.getId());
        if (!ok) {
            return AjaxResult.error("撤销失败（已撤销的授权码不可重复撤销）");
        }
        return AjaxResult.success("撤销成功");
    }

    /** 通用ID请求体 */
    public static class IdRequest {
        private Long id;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
    }
}
