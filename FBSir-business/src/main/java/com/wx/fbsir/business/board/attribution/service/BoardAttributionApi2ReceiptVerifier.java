package com.wx.fbsir.business.board.attribution.service;

import com.wx.fbsir.business.board.attribution.config.IndependentBoardAttributionProperties;
import com.wx.fbsir.business.board.attribution.domain.BoardAttributionEvidenceEvent;
import com.wx.fbsir.business.board.attribution.domain.BoardAttributionSnapshot;
import com.wx.fbsir.business.board.attribution.domain.BoardHostForwardingChallenge;

/**
 * Explicit seam for the API2 v2 HMAC receipt verifier.
 *
 * No default bean is supplied: until an implementation validates the complete
 * v2 ack against the U3W challenge/session context, the evidence writer stays
 * fail-closed. The v1 verification wrapper is not sufficient authority.
 */
public interface BoardAttributionApi2ReceiptVerifier {
    boolean isConfigured();

    void verifyEvent(BoardAttributionEvidenceEvent event,
                     BoardHostForwardingChallenge challenge,
                     IndependentBoardAttributionProperties properties);

    void verifySnapshot(BoardAttributionSnapshot snapshot,
                        IndependentBoardAttributionProperties properties);
}
