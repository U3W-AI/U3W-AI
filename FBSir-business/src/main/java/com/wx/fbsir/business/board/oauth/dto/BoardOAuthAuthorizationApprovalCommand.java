package com.wx.fbsir.business.board.oauth.dto;

import com.wx.fbsir.business.board.oauth.BoardOAuthConsentIntent;

/** Trusted, transport-neutral command for approving one consent request. */
public final class BoardOAuthAuthorizationApprovalCommand {
    private final String rawRequestHandle;
    private final Long serverAuthenticatedUserId;
    private final Long serverValidatedTenantId;
    private final BoardOAuthConsentIntent intent;

    public BoardOAuthAuthorizationApprovalCommand(
            String rawRequestHandle,
            Long serverAuthenticatedUserId,
            Long serverValidatedTenantId,
            BoardOAuthConsentIntent intent) {
        this.rawRequestHandle = rawRequestHandle;
        this.serverAuthenticatedUserId = serverAuthenticatedUserId;
        this.serverValidatedTenantId = serverValidatedTenantId;
        this.intent = intent;
    }

    public String rawRequestHandle() {
        return rawRequestHandle;
    }

    public Long serverAuthenticatedUserId() {
        return serverAuthenticatedUserId;
    }

    public Long serverValidatedTenantId() {
        return serverValidatedTenantId;
    }

    public BoardOAuthConsentIntent intent() {
        return intent;
    }

    @Override
    public String toString() {
        return "BoardOAuthAuthorizationApprovalCommand[REDACTED]";
    }
}
