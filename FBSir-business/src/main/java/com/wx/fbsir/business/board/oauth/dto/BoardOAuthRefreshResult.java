package com.wx.fbsir.business.board.oauth.dto;

import java.time.Instant;

/** Raw rotated tokens that may leave the internal boundary only after commit. */
public final class BoardOAuthRefreshResult {
    private final String rawAccessToken;
    private final String rawRefreshToken;
    private final String tokenType;
    private final long expiresInSeconds;
    private final String scopeCanonical;
    private final Instant accessTokenExpiresAt;
    private final Instant refreshTokenExpiresAt;
    private final String receiptId;
    private final String correlationId;

    public BoardOAuthRefreshResult(
            String rawAccessToken,
            String rawRefreshToken,
            String tokenType,
            long expiresInSeconds,
            String scopeCanonical,
            Instant accessTokenExpiresAt,
            Instant refreshTokenExpiresAt,
            String receiptId,
            String correlationId) {
        this.rawAccessToken = rawAccessToken;
        this.rawRefreshToken = rawRefreshToken;
        this.tokenType = tokenType;
        this.expiresInSeconds = expiresInSeconds;
        this.scopeCanonical = scopeCanonical;
        this.accessTokenExpiresAt = accessTokenExpiresAt;
        this.refreshTokenExpiresAt = refreshTokenExpiresAt;
        this.receiptId = receiptId;
        this.correlationId = correlationId;
    }

    public String rawAccessToken() { return rawAccessToken; }
    public String rawRefreshToken() { return rawRefreshToken; }
    public String tokenType() { return tokenType; }
    public long expiresInSeconds() { return expiresInSeconds; }
    public String scopeCanonical() { return scopeCanonical; }
    public Instant accessTokenExpiresAt() { return accessTokenExpiresAt; }
    public Instant refreshTokenExpiresAt() { return refreshTokenExpiresAt; }
    public String receiptId() { return receiptId; }
    public String correlationId() { return correlationId; }

    @Override
    public String toString() {
        return "BoardOAuthRefreshResult[REDACTED]";
    }
}
