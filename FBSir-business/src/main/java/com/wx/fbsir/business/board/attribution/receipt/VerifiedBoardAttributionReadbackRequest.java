package com.wx.fbsir.business.board.attribution.receipt;

import java.time.Instant;

/** Authenticated readback tuple safe to pass to the primary read-only service. */
public record VerifiedBoardAttributionReadbackRequest(
        String eventId,
        String receiptId,
        String eventDigest,
        Instant issuedAt,
        Instant expiresAt,
        String requestDigest,
        String nonceHash,
        String signerKeyId) {
}
