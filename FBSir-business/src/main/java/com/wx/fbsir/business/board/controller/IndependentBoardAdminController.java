package com.wx.fbsir.business.board.controller;

import com.wx.fbsir.business.board.dto.BoardEntitlementGrantRequest;
import com.wx.fbsir.business.board.dto.BoardEntitlementRevokeRequest;
import com.wx.fbsir.business.board.service.IndependentBoardEntitlementService;
import com.wx.fbsir.business.board.service.IndependentBoardMeetingService;
import com.wx.fbsir.common.annotation.Log;
import com.wx.fbsir.common.core.controller.BaseController;
import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.enums.BusinessType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Global RuoYi administrator surface; tenant-delegated administration is not enabled. */
@Validated
@RestController
@RequestMapping("/business/independent-board")
public class IndependentBoardAdminController extends BaseController {
    private final IndependentBoardEntitlementService entitlementService;
    private final IndependentBoardMeetingService meetingService;

    public IndependentBoardAdminController(
            IndependentBoardEntitlementService entitlementService,
            IndependentBoardMeetingService meetingService) {
        this.entitlementService = entitlementService;
        this.meetingService = meetingService;
    }

    @PreAuthorize("@ss.hasRole('admin') and @ss.hasPermi('board:entitlement:grant')")
    @Log(title = "Independent Board entitlement", businessType = BusinessType.GRANT)
    @PostMapping("/entitlements")
    public AjaxResult grant(@Valid @RequestBody BoardEntitlementGrantRequest request) {
        return AjaxResult.success(entitlementService.grant(request, getUserId()));
    }

    @PreAuthorize("@ss.hasRole('admin') and @ss.hasPermi('board:entitlement:revoke')")
    @Log(title = "Independent Board entitlement", businessType = BusinessType.UPDATE)
    @PostMapping("/entitlements/revoke")
    public AjaxResult revoke(@Valid @RequestBody BoardEntitlementRevokeRequest request) {
        return AjaxResult.success(entitlementService.revoke(request, getUserId()));
    }

    @PreAuthorize("@ss.hasRole('admin') and @ss.hasPermi('board:entitlement:query')")
    @GetMapping("/entitlements")
    public AjaxResult entitlements(@RequestParam @Min(1) Long tenantId) {
        return AjaxResult.success(entitlementService.listEntitlements(tenantId));
    }

    @PreAuthorize("@ss.hasRole('admin') and @ss.hasPermi('board:entitlement:audit')")
    @GetMapping("/entitlement-receipts")
    public AjaxResult entitlementReceipts(@RequestParam @Min(1) Long tenantId) {
        return AjaxResult.success(entitlementService.listReceipts(tenantId));
    }

    @PreAuthorize("@ss.hasRole('admin') and @ss.hasPermi('board:operation:audit')")
    @GetMapping("/operations")
    public AjaxResult operations(@RequestParam @Min(1) Long tenantId) {
        return AjaxResult.success(meetingService.listOperations(tenantId));
    }
}
