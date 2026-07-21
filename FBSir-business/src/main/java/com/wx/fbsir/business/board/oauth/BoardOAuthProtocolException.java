package com.wx.fbsir.business.board.oauth;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Transport-neutral OAuth failure with a stable, non-secret reason code.
 *
 * <p>The future HTTP adapter may map {@link #oauthError()} and
 * {@link #httpStatus()} to a protocol response. Callers must never place raw
 * authorization handles, state, codes or tokens in the reason code.</p>
 */
public final class BoardOAuthProtocolException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private static final Pattern OAUTH_ERROR = Pattern.compile("[a-z_]{1,64}");
    private static final Pattern REASON_CODE = Pattern.compile("[A-Z0-9_]{1,128}");

    private final String oauthError;
    private final int httpStatus;
    private final String reasonCode;

    private BoardOAuthProtocolException(
            String oauthError,
            int httpStatus,
            String reasonCode) {
        super(requireReasonCode(reasonCode));
        if (!OAUTH_ERROR.matcher(Objects.requireNonNull(oauthError, "oauthError")).matches()) {
            throw new IllegalArgumentException("OAuth error name is invalid");
        }
        if (httpStatus < 400 || httpStatus > 599) {
            throw new IllegalArgumentException("OAuth HTTP status is invalid");
        }
        this.oauthError = oauthError;
        this.httpStatus = httpStatus;
        this.reasonCode = reasonCode;
    }

    public static BoardOAuthProtocolException invalidRequest(String reasonCode) {
        return new BoardOAuthProtocolException("invalid_request", 400, reasonCode);
    }

    public static BoardOAuthProtocolException invalidGrant(String reasonCode) {
        return new BoardOAuthProtocolException("invalid_grant", 400, reasonCode);
    }

    public static BoardOAuthProtocolException invalidClient(String reasonCode) {
        return new BoardOAuthProtocolException("invalid_client", 400, reasonCode);
    }

    public static BoardOAuthProtocolException invalidTarget(String reasonCode) {
        return new BoardOAuthProtocolException("invalid_target", 400, reasonCode);
    }

    public static BoardOAuthProtocolException conflict(String reasonCode) {
        return new BoardOAuthProtocolException("invalid_request", 409, reasonCode);
    }

    public static BoardOAuthProtocolException accessDenied(String reasonCode) {
        return new BoardOAuthProtocolException("access_denied", 403, reasonCode);
    }

    public static BoardOAuthProtocolException serverError(String reasonCode) {
        return new BoardOAuthProtocolException("server_error", 500, reasonCode);
    }

    public static BoardOAuthProtocolException temporarilyUnavailable(String reasonCode) {
        return new BoardOAuthProtocolException("temporarily_unavailable", 503, reasonCode);
    }

    public String oauthError() {
        return oauthError;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String reasonCode() {
        return reasonCode;
    }

    @Override
    public String toString() {
        return "BoardOAuthProtocolException[oauthError=" + oauthError
                + ", httpStatus=" + httpStatus
                + ", reasonCode=" + reasonCode + "]";
    }

    private static String requireReasonCode(String reasonCode) {
        if (reasonCode == null || !REASON_CODE.matcher(reasonCode).matches()) {
            throw new IllegalArgumentException("OAuth reason code is invalid");
        }
        return reasonCode;
    }
}
