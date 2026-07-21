package com.wx.fbsir.business.board.portal.dto;

public record BoardPortalTenantView(
        long tenantId,
        String tenantLabel,
        String status) {
}
