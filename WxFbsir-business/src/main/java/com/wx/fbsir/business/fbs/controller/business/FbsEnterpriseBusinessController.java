package com.wx.fbsir.business.fbs.controller.business;

import com.wx.fbsir.business.fbs.domain.entity.FbsEnterprise;
import com.wx.fbsir.business.fbs.dto.business.enterprise.*;
import com.wx.fbsir.business.fbs.service.business.IFbsEnterpriseBusinessService;
import com.wx.fbsir.common.annotation.Log;
import com.wx.fbsir.common.core.controller.BaseController;
import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.core.page.TableDataInfo;
import com.wx.fbsir.common.enums.BusinessType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 企业组织管理Controller
 * 路径: /business/fbs/enterprise/*
 *
 * @author wxfbsir
 * @date 2026-04-09
 */
@RestController
@RequestMapping("/business/fbs/enterprise")
public class FbsEnterpriseBusinessController extends BaseController {

    @Autowired
    private IFbsEnterpriseBusinessService enterpriseBusinessService;

    /**
     * GET /business/fbs/enterprise/list
     * 企业分页列表
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:enterprise:list')")
    @GetMapping("/list")
    public TableDataInfo list(EnterprisePageRequest request) {
        startPage();
        List<FbsEnterprise> list = enterpriseBusinessService.getEnterprisePage(request);
        return getDataTable(list);
    }

    /**
     * GET /business/fbs/enterprise/{id}
     * 企业详情
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:enterprise:query')")
    @GetMapping("/{id}")
    public AjaxResult getDetail(@PathVariable Long id) {
        EnterpriseDetailResponse detail = enterpriseBusinessService.getEnterpriseDetail(id);
        if (detail == null) {
            return AjaxResult.error("企业不存在");
        }
        return AjaxResult.success(detail);
    }

    /**
     * POST /business/fbs/enterprise
     * 创建企业
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:enterprise:add')")
    @Log(title = "FBS企业", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult create(@RequestBody EnterpriseCreateRequest request) {
        if (request.getEnterpriseName() == null || request.getEnterpriseName().trim().isEmpty()) {
            return AjaxResult.error("企业名称不能为空");
        }
        try {
            Long id = enterpriseBusinessService.createEnterprise(request, getUsername());
            return AjaxResult.success("创建成功", id);
        } catch (IllegalArgumentException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * PUT /business/fbs/enterprise
     * 编辑企业
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:enterprise:edit')")
    @Log(title = "FBS企业", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult update(@RequestBody EnterpriseUpdateRequest request) {
        if (request.getId() == null) {
            return AjaxResult.error("id不能为空");
        }
        try {
            boolean ok = enterpriseBusinessService.updateEnterprise(request.getId(), request, getUsername());
            if (!ok) {
                return AjaxResult.error("编辑失败（企业不存在）");
            }
            return AjaxResult.success("编辑成功");
        } catch (IllegalArgumentException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * PUT /business/fbs/enterprise/disable
     * 禁用企业
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:enterprise:disable')")
    @Log(title = "FBS企业-禁用", businessType = BusinessType.UPDATE)
    @PutMapping("/disable")
    public AjaxResult disable(@RequestBody IdRequest request) {
        if (request.getId() == null) {
            return AjaxResult.error("id不能为空");
        }
        boolean ok = enterpriseBusinessService.disableEnterprise(request.getId(), getUsername());
        if (!ok) {
            return AjaxResult.error("禁用失败（仅正常状态企业可禁用）");
        }
        return AjaxResult.success("禁用成功");
    }

    /** 通用ID请求体 */
    public static class IdRequest {
        private Long id;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
    }
}
