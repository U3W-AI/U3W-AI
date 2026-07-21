package com.wx.fbsir.business.board.oauth.dto;

/** Transport-neutral input for one authorization-code exchange. */
public final class BoardOAuthTokenExchangeCommand {
    private final String rawAuthorizationCode;
    private final String pkceVerifier;
    private final String clientId;
    private final String redirectUri;
    private final String resource;

    public BoardOAuthTokenExchangeCommand(
            String rawAuthorizationCode,
            String pkceVerifier,
            String clientId,
            String redirectUri,
            String resource) {
        this.rawAuthorizationCode = rawAuthorizationCode;
        this.pkceVerifier = pkceVerifier;
        this.clientId = clientId;
        this.redirectUri = redirectUri;
        this.resource = resource;
    }

    public String rawAuthorizationCode() {
        return rawAuthorizationCode;
    }

    public String pkceVerifier() {
        return pkceVerifier;
    }

    public String clientId() {
        return clientId;
    }

    public String redirectUri() {
        return redirectUri;
    }

    public String resource() {
        return resource;
    }

    @Override
    public String toString() {
        return "BoardOAuthTokenExchangeCommand[REDACTED]";
    }
}
