package com.wx.fbsir.business.board.plan.controller;

import com.wx.fbsir.business.board.plan.dto.BoardPlanPolicyRevisionRequest;
import com.wx.fbsir.business.board.plan.service.IndependentBoardPlanPolicyService;
import com.wx.fbsir.common.annotation.Log;
import com.wx.fbsir.common.core.controller.BaseController;
import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.enums.BusinessType;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Default-off global administrator surface for immutable plan-policy revisions. */
@Validated
@RestController
@RequestMapping("/business/independent-board")
@ConditionalOnProperty(
        prefix = "fbsir.independent-board.plan-policy-candidate",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
public class IndependentBoardPlanPolicyAdminController extends BaseController {
    private final IndependentBoardPlanPolicyService planPolicyService;

    public IndependentBoardPlanPolicyAdminController(
            IndependentBoardPlanPolicyService planPolicyService) {
        this.planPolicyService = planPolicyService;
    }

    @PreAuthorize("@ss.hasRole('admin') and @ss.hasPermi('board:plan:revise')")
    @Log(
            title = "Independent Board plan policy",
            businessType = BusinessType.UPDATE,
            isSaveRequestData = false,
            isSaveResponseData = false)
    @PostMapping("/plan-policy-revisions")
    public ResponseEntity<AjaxResult> revise(
            @Valid @RequestBody BoardPlanPolicyRevisionRequest request) {
        return successResponse(planPolicyService.revise(request, getUserId()));
    }

    @PreAuthorize("@ss.hasRole('admin') and @ss.hasPermi('board:plan:audit')")
    @GetMapping("/plan-policy-receipts")
    public ResponseEntity<AjaxResult> receipts() {
        return successResponse(planPolicyService.audit());
    }

    private static ResponseEntity<AjaxResult> successResponse(Object data) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .body(AjaxResult.success(data));
    }
}
