package com.wx.fbsir.business.board.oauth.dto;

/** Raw OAuth state that may be published only after the denying proxy commits. */
public final class BoardOAuthAuthorizationDeniedResult {
    private final String redirectUri;
    private final String rawState;
    private final String issuerUri;
    private final String oauthError;

    public BoardOAuthAuthorizationDeniedResult(
            String redirectUri,
            String rawState,
            String issuerUri,
            String oauthError) {
        this.redirectUri = redirectUri;
        this.rawState = rawState;
        this.issuerUri = issuerUri;
        this.oauthError = oauthError;
    }

    public String redirectUri() {
        return redirectUri;
    }

    public String rawState() {
        return rawState;
    }

    public String issuerUri() {
        return issuerUri;
    }

    public String oauthError() {
        return oauthError;
    }

    @Override
    public String toString() {
        return "BoardOAuthAuthorizationDeniedResult[REDACTED]";
    }
}
