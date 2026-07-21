package com.wx.fbsir.business.board.portal.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.List;

public record BoardPortalConnectorBindingView(
        String bindingRef,
        long tenantId,
        String tenantLabel,
        String memberLabel,
        String userLabel,
        String productCode,
        String sourceCode,
        String connectorCode,
        String status,
        List<String> scopes,
        String verificationMethod,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
        Instant verifiedAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
        Instant lastSeenAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
        Instant validUntil,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
        Instant revokedAt,
        String clientRef,
        String subjectDigestRef,
        long version,
        boolean entitlementActive,
        boolean familyActive,
        boolean vipEffective) {

    public BoardPortalConnectorBindingView {
        scopes = List.copyOf(scopes);
    }
}
