package com.wx.fbsir.business.board.attribution.service;

/** Stable internal marker; its message is never exposed to the caller. */
public class IndependentBoardAttributionReadbackUnavailableException
        extends RuntimeException {
    public IndependentBoardAttributionReadbackUnavailableException(
            String reason) {
        super(reason);
    }
}
