package com.wx.fbsir.business.board.controller;

import com.wx.fbsir.business.board.dto.BoardMeetingReservationRequest;
import com.wx.fbsir.business.board.service.IndependentBoardDashboardService;
import com.wx.fbsir.business.board.service.IndependentBoardEntitlementService;
import com.wx.fbsir.business.board.service.IndependentBoardMeetingService;
import com.wx.fbsir.common.core.controller.BaseController;
import com.wx.fbsir.common.core.domain.AjaxResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/my/independent-board")
public class IndependentBoardMeController extends BaseController {
    private final IndependentBoardDashboardService dashboardService;
    private final IndependentBoardEntitlementService entitlementService;
    private final IndependentBoardMeetingService meetingService;

    public IndependentBoardMeController(
            IndependentBoardDashboardService dashboardService,
            IndependentBoardEntitlementService entitlementService,
            IndependentBoardMeetingService meetingService) {
        this.dashboardService = dashboardService;
        this.entitlementService = entitlementService;
        this.meetingService = meetingService;
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/contexts")
    public AjaxResult contexts() {
        return AjaxResult.success(dashboardService.listContexts(getUserId()));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/dashboard")
    public AjaxResult dashboard(@RequestParam @Min(1) Long tenantId) {
        return AjaxResult.success(dashboardService.getDashboard(tenantId, getUserId()));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/meeting-reservations/{operationId}")
    public AjaxResult meetingReservation(
            @PathVariable
            @Pattern(regexp = BoardMeetingReservationRequest.OPERATION_ID_PATTERN)
            String operationId,
            @RequestParam @Min(1) Long tenantId) {
        return AjaxResult.success(dashboardService.getMeetingReservation(
                tenantId, operationId, getUserId()));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/entitlement")
    public AjaxResult entitlement(@RequestParam @Min(1) Long tenantId) {
        return AjaxResult.success(entitlementService.getSnapshot(tenantId, getUserId()));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/meeting-reservations")
    public AjaxResult reserve(@Valid @RequestBody BoardMeetingReservationRequest request) {
        return AjaxResult.success(meetingService.reserve(request, getUserId()));
    }
}
