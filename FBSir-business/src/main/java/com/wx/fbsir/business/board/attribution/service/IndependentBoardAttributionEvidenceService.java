package com.wx.fbsir.business.board.attribution.service;

import com.wx.fbsir.business.board.attribution.config.IndependentBoardAttributionProperties;
import com.wx.fbsir.business.board.attribution.domain.BoardAttributionEvidenceEvent;
import com.wx.fbsir.business.board.attribution.domain.BoardAttributionProductContract;
import com.wx.fbsir.business.board.attribution.domain.BoardAttributionSnapshot;
import com.wx.fbsir.business.board.attribution.domain.BoardHostForwardingChallenge;
import com.wx.fbsir.business.board.attribution.mapper.IndependentBoardAttributionMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Objects;

@Service
public class IndependentBoardAttributionEvidenceService implements BoardAttributionEvidencePort {
    private static final String PRODUCT_ID = "fbsir-eight-seat-board";
    private static final String PRODUCT_VERSION = "26.7.20";
    private static final String CONTRACT_ID = "FBSIR_INDEPENDENT_BOARD_W4B2D";
    private static final long CLOCK_SKEW_MILLIS = 30_000L;
    private final IndependentBoardAttributionMapper mapper;
    private final IndependentBoardAttributionProperties properties;
    private final BoardAttributionEnvelopeVerifier verifier;
    private final ObjectProvider<BoardAttributionApi2ReceiptVerifier> api2ReceiptVerifier;

    public IndependentBoardAttributionEvidenceService(IndependentBoardAttributionMapper mapper,
                                                        IndependentBoardAttributionProperties properties,
                                                        BoardAttributionEnvelopeVerifier verifier,
                                                        ObjectProvider<BoardAttributionApi2ReceiptVerifier> api2ReceiptVerifier) {
        this.mapper = mapper;
        this.properties = properties;
        this.verifier = verifier;
        this.api2ReceiptVerifier = api2ReceiptVerifier;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BoardHostForwardingChallenge issueChallenge(IssueChallenge command) {
        requireEnabled();
        Date now = new Date();
        long maxExpiry = now.getTime() + Math.max(1, properties.getReceiptTtlSeconds()) * 1_000L + CLOCK_SKEW_MILLIS;
        if (command == null || !CONTRACT_ID.equals(command.contractId()) || !StringUtilsExt.binding(command.serverBindingId())
                || !StringUtilsExt.sha256(command.challengeId()) || !StringUtilsExt.sha256(command.nonceHash())
                || !StringUtilsExt.sha256(command.tenantSubjectDigest())
                || command.issuedAt() == null || command.expiresAt() == null || command.retentionUntil() == null
                || command.issuedAt().getTime() > now.getTime() + CLOCK_SKEW_MILLIS || !command.expiresAt().after(now)
                || command.expiresAt().before(command.issuedAt()) || command.expiresAt().getTime() > maxExpiry
                || command.retentionUntil().getTime() < command.expiresAt().getTime() + properties.getRetentionHours() * 3_600_000L) {
            throw new IllegalArgumentException("Independent Board challenge is invalid");
        }
        exactPendingContract();
        BoardHostForwardingChallenge challenge = new BoardHostForwardingChallenge();
        challenge.setChallengeId(command.challengeId()); challenge.setContractId(command.contractId());
        challenge.setServerBindingId(command.serverBindingId()); challenge.setNonceHash(command.nonceHash());
        challenge.setTenantSubjectDigest(command.tenantSubjectDigest());
        challenge.setIssuedAt(command.issuedAt()); challenge.setExpiresAt(command.expiresAt());
        challenge.setRetentionUntil(command.retentionUntil()); challenge.setStatus("ISSUED");
        mapper.insertChallenge(challenge);
        return challenge;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppendResult appendEvent(BoardAttributionEvidenceEvent event) {
        BoardAttributionApi2ReceiptVerifier trustedVerifier = requireEnabled();
        verifier.verify(event, properties); exactPendingContract();
        BoardHostForwardingChallenge challenge = mapper.selectChallengeForUpdate(event.getChallengeId(), event.getServerBindingId(), event.getContractId());
        Date now = new Date();
        if (challenge == null || !"ISSUED".equals(challenge.getStatus()) || !challenge.getExpiresAt().after(now)
                || !Objects.equals(challenge.getTenantSubjectDigest(), event.getTenantSubjectDigest())
                || !Objects.equals(challenge.getNonceHash(), event.getReceiptNonceHash())
                || event.getIssuedAt().before(challenge.getIssuedAt()) || event.getExpiresAt().after(challenge.getExpiresAt())) {
            throw new IllegalStateException("Independent Board challenge is not active");
        }
        List<Long> priorSequenceNos = mapper.selectSuccessfulPriorSequenceNosForUpdate(
                event.getChallengeId(), event.getServerBindingId(), event.getContractId(),
                event.getTenantSubjectDigest(), event.getSequenceNo());
        for (long expectedSequence = 1; expectedSequence < event.getSequenceNo(); expectedSequence++) {
            if (!priorSequenceNos.contains(expectedSequence)) {
                throw new IllegalStateException("Independent Board attribution stage predecessor is missing");
            }
        }
        trustedVerifier.verifyEvent(event, challenge, properties);
        mapper.insertEventIfAbsent(event);
        BoardAttributionEvidenceEvent persisted = mapper.selectEventByReceiptForUpdate(event.getReceiptId());
        if (!sameEvent(persisted, event)) {
            throw new IllegalStateException("Independent Board receipt replay or identity collision");
        }
        if ("closure".equals(event.getStage())) mapper.updateChallengeStatus(event.getChallengeId(), "CONSUMED");
        return new AppendResult(persisted.getEventId(), persisted.getReceiptId(), "SEALED_REPORT_ONLY", persisted.getEventWatermark(), false, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BoardAttributionSnapshot sealSnapshot(BoardAttributionSnapshot snapshot) {
        BoardAttributionApi2ReceiptVerifier trustedVerifier = requireEnabled();
        verifier.verifySnapshot(snapshot, properties); exactPendingContract();
        trustedVerifier.verifySnapshot(snapshot, properties);
        BoardAttributionSnapshot persisted = mapper.selectSnapshotByWindowForUpdate(snapshot.getContractId(), snapshot.getWindowStart(), snapshot.getWindowEnd());
        if (persisted == null) {
            mapper.insertSnapshot(snapshot);
            persisted = mapper.selectSnapshotByWindowForUpdate(snapshot.getContractId(), snapshot.getWindowStart(), snapshot.getWindowEnd());
        }
        if (persisted == null || !sameSnapshot(persisted, snapshot)) throw new IllegalStateException("Independent Board snapshot identity collision");
        return persisted;
    }

    private BoardAttributionProductContract exactPendingContract() {
        BoardAttributionProductContract contract = mapper.selectExactProductForUpdate(PRODUCT_ID, PRODUCT_VERSION);
        if (contract == null || !CONTRACT_ID.equals(contract.getContractId()) || !PRODUCT_ID.equals(contract.getProductId())
                || !PRODUCT_VERSION.equals(contract.getProductVersion()) || !"PENDING_HOST_REGISTRATION".equals(contract.getRegistrationStatus())
                || contract.isCandidateEnabled() || contract.isPublicRouteEnabled() || contract.isAuthoritativeCreditEnabled()
                || !"".equals(contract.getPackageId()) || !"".equals(contract.getExpertEntryId())) {
            throw new IllegalStateException("Independent Board exact product contract is not default-off");
        }
        return contract;
    }

    private BoardAttributionApi2ReceiptVerifier requireEnabled() {
        if (!properties.isEnabled() || properties.isCandidateEnabled() || properties.isPublicRouteEnabled() || properties.isAuthoritativeCreditEnabled()) {
            throw new IllegalStateException("Independent Board attribution writer is disabled or unsafe");
        }
        BoardAttributionApi2ReceiptVerifier trustedVerifier = api2ReceiptVerifier.getIfAvailable();
        if (trustedVerifier == null || !trustedVerifier.isConfigured()) {
            throw new IllegalStateException("Independent Board API2 receipt verifier is not configured");
        }
        return trustedVerifier;
    }

    private static final class StringUtilsExt {
        private static boolean sha256(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
        private static boolean binding(String value) { return value != null && (value.matches("[0-9a-f]{64}") || value.matches("srv_[A-Za-z0-9_-]{12}")); }
    }

    private boolean sameEvent(BoardAttributionEvidenceEvent left, BoardAttributionEvidenceEvent right) {
        return left != null && right != null && Objects.equals(left.getEventId(), right.getEventId())
                && Objects.equals(left.getReceiptId(), right.getReceiptId()) && Objects.equals(left.getChallengeId(), right.getChallengeId())
                && Objects.equals(left.getContractId(), right.getContractId()) && Objects.equals(left.getServerBindingId(), right.getServerBindingId())
                && Objects.equals(left.getTenantSubjectDigest(), right.getTenantSubjectDigest()) && Objects.equals(left.getStage(), right.getStage())
                && Objects.equals(left.getOutcome(), right.getOutcome()) && Objects.equals(left.getEntrySurface(), right.getEntrySurface())
                && Objects.equals(left.getChannelTrack(), right.getChannelTrack()) && Objects.equals(left.getObservedAt(), right.getObservedAt())
                && left.getSequenceNo() == right.getSequenceNo() && left.getSampleCount() == right.getSampleCount()
                && Objects.equals(left.getCanonicalDigest(), right.getCanonicalDigest()) && Objects.equals(left.getSignerKeyId(), right.getSignerKeyId())
                && Objects.equals(left.getIssuer(), right.getIssuer()) && Objects.equals(left.getAudience(), right.getAudience())
                && Objects.equals(left.getReceiptNonceHash(), right.getReceiptNonceHash()) && Objects.equals(left.getReceiptSignature(), right.getReceiptSignature())
                && Objects.equals(left.getIssuedAt(), right.getIssuedAt()) && Objects.equals(left.getExpiresAt(), right.getExpiresAt());
    }

    private boolean sameSnapshot(BoardAttributionSnapshot left, BoardAttributionSnapshot right) {
        return left != null && right != null && Objects.equals(left.getSnapshotId(), right.getSnapshotId())
                && Objects.equals(left.getContractId(), right.getContractId()) && Objects.equals(left.getWindowStart(), right.getWindowStart())
                && Objects.equals(left.getWindowEnd(), right.getWindowEnd()) && Objects.equals(left.getRetentionUntil(), right.getRetentionUntil())
                && Objects.equals(left.getWatermarkAt(), right.getWatermarkAt()) && left.getEventHighWatermark() == right.getEventHighWatermark()
                && left.getRowCount() == right.getRowCount() && left.getParseErrorCount() == right.getParseErrorCount()
                && left.getGapCount() == right.getGapCount() && left.getInvalidCount() == right.getInvalidCount()
                && Objects.equals(left.getCanonicalizationVersion(), right.getCanonicalizationVersion()) && Objects.equals(left.getEventDigest(), right.getEventDigest())
                && Objects.equals(left.getRuntimeRelease(), right.getRuntimeRelease()) && Objects.equals(left.getEmbeddedRelease(), right.getEmbeddedRelease())
                && Objects.equals(left.getSignerKeyId(), right.getSignerKeyId()) && Objects.equals(left.getIssuer(), right.getIssuer())
                && Objects.equals(left.getAudience(), right.getAudience()) && Objects.equals(left.getSnapshotSignature(), right.getSnapshotSignature())
                && Objects.equals(left.getStatus(), right.getStatus());
    }
}
