package com.wx.fbsir.business.board.oauth.dto;

import java.time.Instant;

/** Raw OAuth values that may be published only after the approving proxy commits. */
public final class BoardOAuthAuthorizationApprovedResult {
    private final String redirectUri;
    private final String rawAuthorizationCode;
    private final String rawState;
    private final String issuerUri;
    private final Instant codeExpiresAt;

    public BoardOAuthAuthorizationApprovedResult(
            String redirectUri,
            String rawAuthorizationCode,
            String rawState,
            String issuerUri,
            Instant codeExpiresAt) {
        this.redirectUri = redirectUri;
        this.rawAuthorizationCode = rawAuthorizationCode;
        this.rawState = rawState;
        this.issuerUri = issuerUri;
        this.codeExpiresAt = codeExpiresAt;
    }

    public String redirectUri() {
        return redirectUri;
    }

    public String rawAuthorizationCode() {
        return rawAuthorizationCode;
    }

    public String rawState() {
        return rawState;
    }

    public String issuerUri() {
        return issuerUri;
    }

    public Instant codeExpiresAt() {
        return codeExpiresAt;
    }

    @Override
    public String toString() {
        return "BoardOAuthAuthorizationApprovedResult[REDACTED]";
    }
}
