package com.wx.fbsir.business.fbs.controller.business;

import com.wx.fbsir.business.fbs.domain.entity.FbsEnterpriseMember;
import com.wx.fbsir.business.fbs.dto.business.enterprise.EnterpriseMemberAddRequest;
import com.wx.fbsir.business.fbs.dto.business.enterprise.EnterpriseMemberDetailResponse;
import com.wx.fbsir.business.fbs.service.business.IFbsEnterpriseMemberBusinessService;
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
 * 企业成员管理Controller
 * 路径: /business/fbs/enterprise/member/*
 *
 * @author FBSir
 * @date 2026-04-09
 */
@RestController
@RequestMapping("/business/fbs/enterprise/member")
public class FbsEnterpriseMemberBusinessController extends BaseController {

    @Autowired
    private IFbsEnterpriseMemberBusinessService memberBusinessService;

    /**
     * GET /business/fbs/enterprise/member/list
     * 查询企业成员列表
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:enterpriseMember:list')")
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam Long enterpriseId) {
        if (enterpriseId == null) {
            return getDataTable(List.of());
        }
        startPage();
        List<FbsEnterpriseMember> list = memberBusinessService.getMemberPage(enterpriseId);
        return getDataTable(list);
    }

    /**
     * GET /business/fbs/enterprise/member/{id}
     * 成员详情
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:enterpriseMember:query')")
    @GetMapping("/{id}")
    public AjaxResult getDetail(@PathVariable Long id) {
        EnterpriseMemberDetailResponse detail = memberBusinessService.getMemberDetail(id);
        if (detail == null) {
            return AjaxResult.error("成员不存在");
        }
        return AjaxResult.success(detail);
    }

    /**
     * POST /business/fbs/enterprise/member
     * 添加企业成员
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:enterpriseMember:add')")
    @Log(title = "FBS企业成员-添加", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody EnterpriseMemberAddRequest request) {
        if (request.getEnterpriseId() == null) {
            return AjaxResult.error("enterpriseId不能为空");
        }
        if (request.getUserId() == null) {
            return AjaxResult.error("userId不能为空");
        }
        try {
            Long id = memberBusinessService.addMember(request, getUsername());
            return AjaxResult.success("添加成功", id);
        } catch (IllegalArgumentException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * DELETE /business/fbs/enterprise/member/{id}
     * 移除企业成员
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:enterpriseMember:remove')")
    @Log(title = "FBS企业成员-移除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id) {
        if (id == null) {
            return AjaxResult.error("id不能为空");
        }
        boolean ok = memberBusinessService.removeMember(id, getUsername());
        if (!ok) {
            return AjaxResult.error("移除失败（仅正常状态成员可移除）");
        }
        return AjaxResult.success("移除成功");
    }
}
