package com.wx.fbsir.business.board.portal.controller;

import com.wx.fbsir.common.core.domain.AjaxResult;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;

final class BoardPortalHttpResponses {

    private BoardPortalHttpResponses() {
    }

    static ResponseEntity<AjaxResult> success(Object data) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .body(AjaxResult.success(data));
    }
}
