package com.wx.fbsir.business.board.attribution.receipt;

/**
 * Server-authoritative values used to bind an API2 receipt to U3W state.
 * Callers must derive this from the locked challenge, product registration,
 * and runtime state; never copy it from the untrusted acknowledgement. U3W's
 * independent-board evidence policy requires every accepted receipt to bind a
 * covered event, even though API2 v2 defines that field as generally optional.
 */
public record BoardApi2ReceiptExpectedContext(
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
        String nonceHash,
        String tenantSubjectDigest
) { }
