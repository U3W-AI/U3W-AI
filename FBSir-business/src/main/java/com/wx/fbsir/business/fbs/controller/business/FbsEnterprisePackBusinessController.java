package com.wx.fbsir.business.fbs.controller.business;

import com.wx.fbsir.business.fbs.domain.entity.FbsEnterprisePack;
import com.wx.fbsir.business.fbs.dto.business.enterprise.EnterprisePackDetailResponse;
import com.wx.fbsir.business.fbs.dto.business.enterprise.EnterprisePackGrantRequest;
import com.wx.fbsir.business.fbs.dto.business.enterprise.EnterprisePackPageRequest;
import com.wx.fbsir.business.fbs.service.business.IFbsEnterprisePackBusinessService;
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
 * 企业场景包分发Controller
 * 路径: /business/fbs/enterprise/pack/*
 *
 * @author FBSir
 * @date 2026-04-09
 */
@RestController
@RequestMapping("/business/fbs/enterprise/pack")
public class FbsEnterprisePackBusinessController extends BaseController {

    @Autowired
    private IFbsEnterprisePackBusinessService enterprisePackBusinessService;

    /**
     * GET /business/fbs/enterprise/pack/list
     * 查询企业已获场景包列表（支持按 status 筛选）
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:enterprisePack:list')")
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam Long enterpriseId,
                              @RequestParam(required = false) Integer status) {
        if (enterpriseId == null) {
            return getDataTable(List.of());
        }
        EnterprisePackPageRequest request = new EnterprisePackPageRequest();
        request.setEnterpriseId(enterpriseId);
        request.setStatus(status);
        startPage();
        List<FbsEnterprisePack> list = enterprisePackBusinessService.getEnterprisePackPage(request);
        return getDataTable(list);
    }

    /**
     * GET /business/fbs/enterprise/pack/{id}
     * 企业包详情
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:enterprisePack:query')")
    @GetMapping("/{id}")
    public AjaxResult getDetail(@PathVariable Long id) {
        EnterprisePackDetailResponse detail = enterprisePackBusinessService.getDetail(id);
        if (detail == null) {
            return AjaxResult.error("企业包不存在");
        }
        return AjaxResult.success(detail);
    }

    /**
     * POST /business/fbs/enterprise/pack/grant
     * 平台向企业分发场景包
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:enterprisePack:grant')")
    @Log(title = "FBS企业包-分发", businessType = BusinessType.INSERT)
    @PostMapping("/grant")
    public AjaxResult grant(@RequestBody EnterprisePackGrantRequest request) {
        if (request.getEnterpriseId() == null) {
            return AjaxResult.error("enterpriseId不能为空");
        }
        if (request.getPackCode() == null || request.getPackCode().trim().isEmpty()) {
            return AjaxResult.error("packCode不能为空");
        }
        if (request.getPackQuota() == null || request.getPackQuota() <= 0) {
            return AjaxResult.error("packQuota必须大于0");
        }
        try {
            Long id = enterprisePackBusinessService.grantPackToEnterprise(request, getUsername());
            return AjaxResult.success("分发成功", id);
        } catch (IllegalArgumentException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * PUT /business/fbs/enterprise/pack/revoke
     * 平台撤销企业场景包
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:enterprisePack:revoke')")
    @Log(title = "FBS企业包-撤销", businessType = BusinessType.UPDATE)
    @PutMapping("/revoke")
    public AjaxResult revoke(@RequestBody IdRequest request) {
        if (request.getId() == null) {
            return AjaxResult.error("id不能为空");
        }
        boolean ok = enterprisePackBusinessService.revokePackFromEnterprise(request.getId(), getUsername());
        if (!ok) {
            return AjaxResult.error("撤销失败（仅已授权状态可撤销）");
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
