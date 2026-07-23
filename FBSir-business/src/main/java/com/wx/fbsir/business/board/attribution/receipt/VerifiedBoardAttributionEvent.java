package com.wx.fbsir.business.board.attribution.receipt;

import java.time.Instant;

public record VerifiedBoardAttributionEvent(
        BoardAttributionEventV1 rawEvent,
        Instant issuedAt,
        Instant expiresAt,
        String canonicalDigest,
        String signatureHex,
        String nonceHash,
        String eventDigest,
        String intentFamily) {
}
