package com.wx.fbsir.business.board.oauth.dto;

import java.time.Instant;

/** One-time handle returned only after the PENDING request transaction commits. */
public final class BoardOAuthAuthorizationStartResult {
    private final String requestHandle;
    private final Instant expiresAt;

    public BoardOAuthAuthorizationStartResult(String requestHandle, Instant expiresAt) {
        this.requestHandle = requestHandle;
        this.expiresAt = expiresAt;
    }

    public String requestHandle() {
        return requestHandle;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    @Override
    public String toString() {
        return "BoardOAuthAuthorizationStartResult[REDACTED]";
    }
}
