package com.wx.fbsir.business.board.oauth;

/**
 * Trusted consent entry selected by the server-side me adapter.
 *
 * <p>This value must never be inferred from browser form fields or from the
 * binding row discovered during authorization.</p>
 */
public enum BoardOAuthConsentIntent {
    FIRST_CONNECT,
    EXPLICIT_REAUTHORIZATION
}
