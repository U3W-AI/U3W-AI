package com.wx.fbsir.business.board.portal;

public final class BoardPortalDataDriftException extends RuntimeException {

    private final String errorCode;

    public BoardPortalDataDriftException(String errorCode) {
        super("independent-board portal read contract drifted");
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
