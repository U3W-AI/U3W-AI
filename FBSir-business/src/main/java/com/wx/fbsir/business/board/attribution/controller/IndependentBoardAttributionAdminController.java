package com.wx.fbsir.business.board.attribution.controller;

import com.wx.fbsir.business.board.attribution.service.IndependentBoardAttributionAdminReadService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/business/independent-board/attribution")
@ConditionalOnProperty(
        prefix = "fbsir.independent-board.attribution",
        name = "observation-admin-read-enabled",
        havingValue = "true",
        matchIfMissing = false)
public class IndependentBoardAttributionAdminController {
    private final IndependentBoardAttributionAdminReadService service;

    public IndependentBoardAttributionAdminController(
            IndependentBoardAttributionAdminReadService service) {
        this.service = service;
    }

    @PreAuthorize(
            "@ss.hasRole('admin') and @ss.hasPermi('board:attribution:query')")
    @GetMapping(path = "/summary", produces = "application/json")
    public ResponseEntity<IndependentBoardAttributionAdminReadService.Summary>
            summary(
                    @RequestParam String windowStart,
                    @RequestParam String windowEnd,
                    @RequestParam(defaultValue = "ALL") String mode) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .body(service.summary(windowStart, windowEnd, mode));
    }
}
