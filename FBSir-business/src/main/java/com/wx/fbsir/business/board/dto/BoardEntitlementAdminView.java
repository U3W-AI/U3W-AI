package com.wx.fbsir.business.board.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

/** Safe global-admin entitlement projection without non-authoritative Connector fields. */
public record BoardEntitlementAdminView(
        Long tenantId,
        Long memberId,
        Long userId,
        String planCode,
        String entitlementStatus,
        String activationState,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Shanghai")
        Date validFrom,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Shanghai")
        Date validUntil,
        Long version,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Shanghai")
        Date updatedAt) {
}
