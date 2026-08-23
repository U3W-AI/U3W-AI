package com.wx.fbsir.business.board.attribution.receipt;

/** Minimal authoritative result. It never contains tenant or binding fields. */
public record BoardAttributionReadbackResponseV1(
        String schemaVersion,
        String status,
        String eventId,
        String receiptId,
        String eventDigest,
        boolean authoritativeRead,
        int httpStatus,
        String requestDigest,
        String requestNonceHash,
        String readAt,
        String expiresAt,
        String receiverReleaseId,
        String receiverJarSha256,
        boolean productCreditEligible,
        String keyId,
        String signature) {
    public static final String SCHEMA_VERSION =
            "fbsir.independentBoardAttributionReadbackResponse.v1";
}
