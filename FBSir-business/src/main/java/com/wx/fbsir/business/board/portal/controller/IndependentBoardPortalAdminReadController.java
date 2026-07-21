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
@RequestMapping("/business/independent-board")
@ConditionalOnProperty(
        prefix = "fbsir.independent-board.portal-candidate",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
public class IndependentBoardPortalAdminReadController extends BaseController {

    private static final Set<String> ADMIN_PAGE_OPTIONAL = Set.of("status", "cursor");
    private static final Set<String> TENANT_PAGE_OPTIONAL =
            Set.of("query", "status", "cursor");
    private static final Set<String> TENANT_PAGE_REQUIRED = Set.of("tenantId");

    private final IndependentBoardPortalReadService readService;

    public IndependentBoardPortalAdminReadController(IndependentBoardPortalReadService readService) {
        this.readService = readService;
    }

    @GetMapping("/oauth/clients")
    @PreAuthorize("@ss.hasRole('admin') and @ss.hasPermi('board:oauth:client:query')")
    public ResponseEntity<AjaxResult> oauthClients(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cursor,
            HttpServletRequest request) {
        BoardPortalHttpRequestGuard.requireExactParameters(
                request, Set.of(), ADMIN_PAGE_OPTIONAL);
        return BoardPortalHttpResponses.success(
                readService.listOAuthClients(getUserId(), status, cursor));
    }

    @GetMapping("/tenants")
    @PreAuthorize("@ss.hasRole('admin') and @ss.hasPermi('board:tenant:query')")
    public ResponseEntity<AjaxResult> tenants(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cursor,
            HttpServletRequest request) {
        BoardPortalHttpRequestGuard.requireExactParameters(
                request, Set.of(), TENANT_PAGE_OPTIONAL);
        return BoardPortalHttpResponses.success(
                readService.listTenants(getUserId(), query, status, cursor));
    }

    @GetMapping("/oauth/families")
    @PreAuthorize("@ss.hasRole('admin') and @ss.hasPermi('board:oauth:family:query')")
    public ResponseEntity<AjaxResult> oauthFamilies(
            @RequestParam @Min(1) Long tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cursor,
            HttpServletRequest request) {
        BoardPortalHttpRequestGuard.requireExactParameters(
                request, TENANT_PAGE_REQUIRED, ADMIN_PAGE_OPTIONAL);
        return BoardPortalHttpResponses.success(
                readService.listOAuthFamilies(getUserId(), tenantId, status, cursor));
    }

    @GetMapping("/connector-bindings")
    @PreAuthorize("@ss.hasRole('admin') and @ss.hasPermi('board:connector:query')")
    public ResponseEntity<AjaxResult> connectorBindings(
            @RequestParam @Min(1) Long tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cursor,
            HttpServletRequest request) {
        BoardPortalHttpRequestGuard.requireExactParameters(
                request, TENANT_PAGE_REQUIRED, ADMIN_PAGE_OPTIONAL);
        return BoardPortalHttpResponses.success(
                readService.listConnectorBindings(getUserId(), tenantId, status, cursor));
    }
}
