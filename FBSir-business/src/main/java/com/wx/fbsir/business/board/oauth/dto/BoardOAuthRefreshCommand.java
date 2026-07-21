package com.wx.fbsir.business.board.oauth.dto;

/** Transport-neutral input for one refresh-token rotation attempt. */
public final class BoardOAuthRefreshCommand {
    private final String rawRefreshToken;
    private final String clientId;
    private final String resource;
    private final String requestedScope;

    public BoardOAuthRefreshCommand(
            String rawRefreshToken,
            String clientId,
            String resource,
            String requestedScope) {
        this.rawRefreshToken = rawRefreshToken;
        this.clientId = clientId;
        this.resource = resource;
        this.requestedScope = requestedScope;
    }

    public String rawRefreshToken() {
        return rawRefreshToken;
    }

    public String clientId() {
        return clientId;
    }

    public String resource() {
        return resource;
    }

    public String requestedScope() {
        return requestedScope;
    }

    @Override
    public String toString() {
        return "BoardOAuthRefreshCommand[REDACTED]";
    }
}
