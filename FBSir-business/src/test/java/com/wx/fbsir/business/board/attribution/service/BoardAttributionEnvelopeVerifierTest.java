package com.wx.fbsir.business.board.attribution.service;

import com.wx.fbsir.business.board.attribution.config.IndependentBoardAttributionProperties;
import com.wx.fbsir.business.board.attribution.domain.BoardAttributionEvidenceEvent;
import com.wx.fbsir.business.board.attribution.domain.BoardAttributionSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoardAttributionEnvelopeVerifierTest {
    private final IndependentBoardAttributionProperties properties = new IndependentBoardAttributionProperties();
    private final BoardAttributionEnvelopeVerifier verifier = new BoardAttributionEnvelopeVerifier();

    @Test
    void acceptsExactSrvBindingButKeepsTheEnvelopeReportOnly() {
        assertDoesNotThrow(() -> verifier.verify(event("srv_AbC123_xYz90"), properties));
    }

    @Test
    void rejectsSensitiveTerminalAndFailedOrAmplifiedEvent() {
        BoardAttributionEvidenceEvent sensitive = event("srv_AbC123_xYz90");
        sensitive.setChannelTrack("alice@example.com");
        assertThrows(IllegalArgumentException.class, () -> verifier.verify(sensitive, properties));

        BoardAttributionEvidenceEvent failed = event("srv_AbC123_xYz90");
        failed.setOutcome("failed");
        assertThrows(IllegalArgumentException.class, () -> verifier.verify(failed, properties));

        BoardAttributionEvidenceEvent amplified = event("srv_AbC123_xYz90");
        amplified.setSampleCount(100);
        assertThrows(IllegalArgumentException.class, () -> verifier.verify(amplified, properties));
    }

    @Test
    void requiresExactlyTwentyFourHoursAndAtLeastTwentySixHoursRetention() {
        BoardAttributionSnapshot snapshot = snapshot();
        assertDoesNotThrow(() -> verifier.verifySnapshot(snapshot, properties));
        snapshot.setWindowEnd(new Date(snapshot.getWindowEnd().getTime() + 1));
        assertThrows(IllegalArgumentException.class, () -> verifier.verifySnapshot(snapshot, properties));
    }

    private BoardAttributionEvidenceEvent event(String binding) {
        long now = System.currentTimeMillis();
        BoardAttributionEvidenceEvent event = new BoardAttributionEvidenceEvent();
        event.setEventId("a".repeat(64)); event.setReceiptId("b".repeat(64)); event.setChallengeId("c".repeat(64));
        event.setContractId("FBSIR_INDEPENDENT_BOARD_W4B2D"); event.setServerBindingId(binding); event.setTenantSubjectDigest("d".repeat(64));
        event.setStage("whoami"); event.setOutcome("success"); event.setEntrySurface("official_entry"); event.setChannelTrack("workbuddy_official");
        event.setObservedAt(new Date(now - 1_000L)); event.setSequenceNo(1); event.setSampleCount(1); event.setCanonicalDigest("e".repeat(64));
        event.setSignerKeyId("key-1"); event.setIssuer(properties.getIssuer()); event.setAudience(properties.getAudience()); event.setReceiptNonceHash("f".repeat(64));
        event.setReceiptSignature("1".repeat(64)); event.setIssuedAt(new Date(now - 1_000L)); event.setExpiresAt(new Date(now + 300_000L));
        return event;
    }

    private BoardAttributionSnapshot snapshot() {
        BoardAttributionSnapshot snapshot = new BoardAttributionSnapshot();
        snapshot.setSnapshotId("a".repeat(64)); snapshot.setContractId("FBSIR_INDEPENDENT_BOARD_W4B2D");
        snapshot.setWindowStart(new Date(1_750_000_000_000L)); snapshot.setWindowEnd(new Date(1_750_086_400_000L));
        snapshot.setRetentionUntil(new Date(1_750_180_000_000L)); snapshot.setWatermarkAt(new Date(1_750_086_400_000L));
        snapshot.setEventHighWatermark(1); snapshot.setRowCount(1); snapshot.setParseErrorCount(0); snapshot.setGapCount(0); snapshot.setInvalidCount(0);
        snapshot.setCanonicalizationVersion("jcs-lite-v1"); snapshot.setEventDigest("b".repeat(64)); snapshot.setRuntimeRelease("runtime-1"); snapshot.setEmbeddedRelease("host-26.7.20");
        snapshot.setSignerKeyId("key-1"); snapshot.setIssuer(properties.getIssuer()); snapshot.setAudience(properties.getAudience()); snapshot.setSnapshotSignature("c".repeat(64)); snapshot.setStatus("SEALED_REPORT_ONLY");
        return snapshot;
    }
}
