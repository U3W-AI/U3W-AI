package com.wx.fbsir.business.board.attribution.receipt;

import com.wx.fbsir.business.board.attribution.config.IndependentBoardAttributionProperties;

/**
 * Boundary used by the ingest transaction. Production uses the exact v1
 * verifier; tests can isolate ordering and persistence semantics.
 */
public interface BoardAttributionEventVerifier {
    boolean isConfigured();

    VerifiedBoardAttributionEvent verify(
            BoardAttributionEventV1 event,
            IndependentBoardAttributionProperties properties);
}
