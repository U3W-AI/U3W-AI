package com.wx.fbsir.business.board.portal.controller;

import com.wx.fbsir.business.board.portal.IndependentBoardPortalReadService;
import com.wx.fbsir.common.core.controller.BaseController;
import com.wx.fbsir.common.core.domain.AjaxResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Min;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/my/independent-board")
@ConditionalOnProperty(
        prefix = "fbsir.independent-board.portal-candidate",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
public class IndependentBoardPortalMeReadController extends BaseController {

    private static final Set<String> REQUIRED_PARAMETERS = Set.of("tenantId");

    private final IndependentBoardPortalReadService readService;

    public IndependentBoardPortalMeReadController(IndependentBoardPortalReadService readService) {
        this.readService = readService;
    }

    @GetMapping("/connector")
    @PreAuthorize("isAuthenticated() and @ss.hasPermi('my:independent-board:connector:view')")
    public ResponseEntity<AjaxResult> connector(
            @RequestParam @Min(1) Long tenantId,
            HttpServletRequest request) {
        BoardPortalHttpRequestGuard.requireExactParameters(
                request, REQUIRED_PARAMETERS, Set.of());
        return BoardPortalHttpResponses.success(
                readService.getConnector(getUserId(), tenantId));
    }
}
