package com.wx.fbsir.business.board.attribution.service;

import com.wx.fbsir.business.board.attribution.domain.BoardAttributionEvidenceEvent;
import com.wx.fbsir.business.board.attribution.domain.BoardAttributionSnapshot;
import com.wx.fbsir.business.board.attribution.domain.BoardHostForwardingChallenge;

import java.util.Date;

/** Internal-only port. It intentionally exposes no HTTP, menu, OAuth, or credit operation. */
public interface BoardAttributionEvidencePort {
    BoardHostForwardingChallenge issueChallenge(IssueChallenge command);

    AppendResult appendEvent(BoardAttributionEvidenceEvent event);

    BoardAttributionSnapshot sealSnapshot(BoardAttributionSnapshot snapshot);

    record IssueChallenge(String challengeId, String contractId, String serverBindingId, String nonceHash,
                          Date issuedAt, Date expiresAt, Date retentionUntil) { }

    record AppendResult(String eventId, String receiptId, String status, long eventWatermark,
                        boolean productCreditEligible, boolean businessClosureEligible) { }
}
