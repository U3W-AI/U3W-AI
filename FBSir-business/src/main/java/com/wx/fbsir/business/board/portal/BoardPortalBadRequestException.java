package com.wx.fbsir.business.board.portal;

public final class BoardPortalBadRequestException extends RuntimeException {

    private final String errorCode;

    public BoardPortalBadRequestException(String errorCode) {
        super("independent-board portal read request rejected");
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
