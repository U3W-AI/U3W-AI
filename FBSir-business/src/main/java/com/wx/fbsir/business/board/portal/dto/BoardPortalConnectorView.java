package com.wx.fbsir.business.board.portal.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.List;

public record BoardPortalConnectorView(
        long tenantId,
        long memberId,
        String uiState,
        String effectivePlanCode,
        String clientRef,
        String familyRef,
        String bindingRef,
        List<String> scopes,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
        Instant issuedAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
        Instant expiresAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
        Instant lastSeenAt,
        long version,
        String evidenceLevel) {

    public BoardPortalConnectorView {
        scopes = List.copyOf(scopes);
    }
}
