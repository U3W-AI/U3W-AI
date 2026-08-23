package com.wx.fbsir.business.board.attribution.receipt;

import com.wx.fbsir.business.board.attribution.config.IndependentBoardAttributionProperties;

/** Verifies the readback envelope before any database lookup is permitted. */
public interface BoardAttributionReadbackRequestVerifier {
    boolean isConfigured();

    VerifiedBoardAttributionReadbackRequest verify(
            BoardAttributionReadbackRequestV1 request,
            IndependentBoardAttributionProperties properties);

    BoardAttributionReadbackResponseV1 signResponse(
            BoardAttributionReadbackResponseV1 response,
            VerifiedBoardAttributionReadbackRequest request,
            int httpStatus,
            IndependentBoardAttributionProperties properties);
}
