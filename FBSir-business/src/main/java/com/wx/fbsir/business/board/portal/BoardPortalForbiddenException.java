package com.wx.fbsir.business.board.portal;

public final class BoardPortalForbiddenException extends RuntimeException {

    private final String errorCode;

    public BoardPortalForbiddenException(String errorCode) {
        super("independent-board portal read scope rejected");
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
