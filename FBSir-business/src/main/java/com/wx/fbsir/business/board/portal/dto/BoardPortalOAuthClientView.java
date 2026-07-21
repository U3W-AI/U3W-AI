package com.wx.fbsir.business.board.portal.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.List;

public record BoardPortalOAuthClientView(
        String clientRef,
        String displayName,
        String status,
        String redirectUri,
        List<String> grantTypes,
        List<String> responseTypes,
        List<String> scopes,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
        Instant registeredAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
        Instant expiresAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
        Instant terminatedAt,
        long version,
        String metadataDigestRef,
        String registrationSourceDigestRef) {

    public BoardPortalOAuthClientView {
        grantTypes = List.copyOf(grantTypes);
        responseTypes = List.copyOf(responseTypes);
        scopes = List.copyOf(scopes);
    }
}
