package com.wx.fbsir.business.board.attribution.receipt;

/**
 * Lossless wire representation of fbss.hostForwardingAck.v2.
 *
 * Timestamps intentionally remain strings because their exact wire text is
 * covered by the API2 HMAC. Parse them only after signature canonicalization.
 */
public record BoardApi2HostForwardingAckV2(
        String schemaVersion,
        String ackId,
        String ackStage,
        String releaseId,
        String serverBindingId,
        String anonymousUserCodeHash,
        String chainFingerprint,
        String productId,
        String expertEntryId,
        String entrySurface,
        String entryPromptCode,
        String channelTrack,
        String packCode,
        String scenePackId,
        String nextTool,
        String actionEnvelopeId,
        String actionEnvelopeDigest,
        String toolArgumentsDigest,
        String challengeId,
        String requestDigest,
        String coveredEventId,
        String previousServiceEventId,
        String issuedAt,
        String expiresAt,
        String nonce,
        String keyId,
        String signatureAlgorithm,
        String signature
) { }
