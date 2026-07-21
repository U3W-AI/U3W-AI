package com.wx.fbsir.business.board.portal.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

public record BoardPortalOAuthFamilyView(
        String familyRef,
        long tenantId,
        String tenantLabel,
        String memberLabel,
        String userLabel,
        String clientRef,
        String consentIntent,
        String status,
        long currentRefreshGeneration,
        String bindingRef,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
        Instant issuedAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
        Instant activatedAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
        Instant expiresAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
        Instant terminatedAt,
        long version) {
}
