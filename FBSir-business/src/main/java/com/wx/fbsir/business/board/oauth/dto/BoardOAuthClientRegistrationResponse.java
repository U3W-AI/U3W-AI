package com.wx.fbsir.business.board.oauth.dto;

import java.util.List;

/**
 * Internal representation of the exact metadata returned after DCR succeeds.
 * HTTP status and cache headers remain the responsibility of the future gated
 * protocol adapter.
 */
public record BoardOAuthClientRegistrationResponse(
        String clientId,
        long clientIdIssuedAt,
        long clientIdExpiresAt,
        List<String> redirectUris,
        List<String> grantTypes,
        List<String> responseTypes,
        String tokenEndpointAuthMethod,
        String clientName,
        String scope) {

    public BoardOAuthClientRegistrationResponse {
        redirectUris = List.copyOf(redirectUris);
        grantTypes = List.copyOf(grantTypes);
        responseTypes = List.copyOf(responseTypes);
    }
}
