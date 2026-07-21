package com.wx.fbsir.business.board.attribution.service;

import com.wx.fbsir.business.board.attribution.config.IndependentBoardAttributionProperties;
import com.wx.fbsir.business.board.attribution.domain.BoardAttributionEvidenceEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.Date;

/** Validates the finite, already server-verified envelope; key verification stays at API2. */
@Component
public class BoardAttributionEnvelopeVerifier {
    private static final Set<String> STAGES = Set.of("whoami", "scene_pack", "consume", "closure");
    private static final long CLOCK_SKEW_MILLIS = 30_000L;

    public void verify(BoardAttributionEvidenceEvent event, IndependentBoardAttributionProperties properties) {
        if (event == null || !sha256(event.getEventId()) || !sha256(event.getReceiptId())
                || !sha256(event.getChallengeId()) || !StringUtils.hasText(event.getContractId())
                || !binding(event.getServerBindingId()) || !sha256(event.getTenantSubjectDigest())
                || !STAGES.contains(event.getStage()) || !"success".equals(event.getOutcome())
                || !"official_entry".equals(event.getEntrySurface()) || !safeDimension(event.getChannelTrack(), 64)
                || event.getObservedAt() == null || event.getSequenceNo() < 1 || event.getSequenceNo() > 4
                || event.getSampleCount() != 1 || !sha256(event.getCanonicalDigest())
                || !StringUtils.hasText(event.getSignerKeyId()) || !StringUtils.hasText(event.getIssuer())
                || !StringUtils.hasText(event.getAudience()) || !sha256(event.getReceiptNonceHash())
                || !sha256(event.getReceiptSignature()) || event.getIssuedAt() == null || event.getExpiresAt() == null
                || !"FBSIR_INDEPENDENT_BOARD_W4B2D".equals(event.getContractId())
                || !stageSequence(event.getStage(), event.getSequenceNo())) {
            throw new IllegalArgumentException("Independent Board attribution envelope is invalid");
        }
        Date now = new Date();
        if (!StringUtils.hasText(properties.getIssuer()) || !StringUtils.hasText(properties.getAudience())
                || !properties.getIssuer().equals(event.getIssuer()) || !properties.getAudience().equals(event.getAudience())
                || event.getExpiresAt().before(event.getIssuedAt()) || event.getIssuedAt().getTime() > now.getTime() + CLOCK_SKEW_MILLIS
                || !event.getExpiresAt().after(now)) {
            throw new IllegalArgumentException("Independent Board attribution authority is invalid");
        }
    }

    public void verifySnapshot(com.wx.fbsir.business.board.attribution.domain.BoardAttributionSnapshot snapshot,
                               IndependentBoardAttributionProperties properties) {
        if (snapshot == null || !sha256(snapshot.getSnapshotId()) || !"FBSIR_INDEPENDENT_BOARD_W4B2D".equals(snapshot.getContractId())
                || snapshot.getWindowStart() == null || snapshot.getWindowEnd() == null || snapshot.getRetentionUntil() == null
                || snapshot.getWatermarkAt() == null || snapshot.getWindowEnd().getTime() - snapshot.getWindowStart().getTime() != 86_400_000L
                || snapshot.getRetentionUntil().getTime() < snapshot.getWindowEnd().getTime() + properties.getRetentionHours() * 3_600_000L
                || snapshot.getEventHighWatermark() < snapshot.getRowCount() || snapshot.getRowCount() < 0
                || snapshot.getParseErrorCount() != 0 || snapshot.getGapCount() != 0 || snapshot.getInvalidCount() != 0
                || !"jcs-lite-v1".equals(snapshot.getCanonicalizationVersion()) || !sha256(snapshot.getEventDigest())
                || !StringUtils.hasText(snapshot.getRuntimeRelease()) || !StringUtils.hasText(snapshot.getEmbeddedRelease())
                || !StringUtils.hasText(snapshot.getSignerKeyId()) || !StringUtils.hasText(snapshot.getIssuer())
                || !StringUtils.hasText(snapshot.getAudience()) || !sha256(snapshot.getSnapshotSignature())
                || !"SEALED_REPORT_ONLY".equals(snapshot.getStatus())) {
            throw new IllegalArgumentException("Independent Board sealed snapshot is invalid");
        }
        if (!StringUtils.hasText(properties.getIssuer()) || !StringUtils.hasText(properties.getAudience())
                || !properties.getIssuer().equals(snapshot.getIssuer()) || !properties.getAudience().equals(snapshot.getAudience())) {
            throw new IllegalArgumentException("Independent Board snapshot authority is invalid");
        }
    }

    private boolean sha256(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
    private boolean binding(String value) { return value != null && (value.matches("[0-9a-f]{64}") || value.matches("srv_[A-Za-z0-9_-]{12}")); }
    private boolean safeDimension(String value, int max) { return StringUtils.hasText(value) && value.length() <= max && !value.matches("(?i).*(@|bearer\\s|^1[3-9]\\d{9}$).*"); }
    private boolean stageSequence(String stage, long sequence) {
        return ("whoami".equals(stage) && sequence == 1) || ("scene_pack".equals(stage) && sequence == 2)
                || ("consume".equals(stage) && sequence == 3) || ("closure".equals(stage) && sequence == 4);
    }
}
