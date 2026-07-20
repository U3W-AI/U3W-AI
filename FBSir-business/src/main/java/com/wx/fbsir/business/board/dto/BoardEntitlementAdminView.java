package com.wx.fbsir.business.board.dto;

import java.util.Date;

public record BoardEntitlementAdminView(
        Long tenantId,
        Long memberId,
        Long userId,
        String planCode,
        String activationState,
        Date connectorVerifiedAt,
        Date validUntil,
        Long version) {
}
