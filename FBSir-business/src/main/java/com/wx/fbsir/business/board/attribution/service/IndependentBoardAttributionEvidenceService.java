package com.wx.fbsir.business.board.attribution.service;

import com.wx.fbsir.business.board.attribution.config.IndependentBoardAttributionProperties;
import com.wx.fbsir.business.board.attribution.domain.BoardAttributionEvidenceEvent;
import com.wx.fbsir.business.board.attribution.domain.BoardAttributionProductContract;
import com.wx.fbsir.business.board.attribution.domain.BoardAttributionSnapshot;
import com.wx.fbsir.business.board.attribution.domain.BoardHostForwardingChallenge;
import com.wx.fbsir.business.board.attribution.mapper.IndependentBoardAttributionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Objects;

@Service
public class IndependentBoardAttributionEvidenceService implements BoardAttributionEvidencePort {
    private static final String PRODUCT_ID = "fbsir-eight-seat-board";
    private static final String PRODUCT_VERSION = "26.7.20";
    private static final String CONTRACT_ID = "FBSIR_INDEPENDENT_BOARD_W4B2D";
    private final IndependentBoardAttributionMapper mapper;
    private final IndependentBoardAttributionProperties properties;
    private final BoardAttributionEnvelopeVerifier verifier;

    public IndependentBoardAttributionEvidenceService(IndependentBoardAttributionMapper mapper,
                                                        IndependentBoardAttributionProperties properties,
                                                        BoardAttributionEnvelopeVerifier verifier) {
        this.mapper = mapper;
        this.properties = properties;
        this.verifier = verifier;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BoardHostForwardingChallenge issueChallenge(IssueChallenge command) {
        requireEnabled();
        if (command == null || !CONTRACT_ID.equals(command.contractId()) || !StringUtilsExt.binding(command.serverBindingId())
                || !StringUtilsExt.sha256(command.challengeId()) || !StringUtilsExt.sha256(command.nonceHash())
                || command.issuedAt() == null || command.expiresAt() == null || command.retentionUntil() == null
                || command.expiresAt().before(command.issuedAt())
                || command.retentionUntil().getTime() < command.expiresAt().getTime() + properties.getRetentionHours() * 3_600_000L) {
            throw new IllegalArgumentException("Independent Board challenge is invalid");
        }
        exactPendingContract();
        BoardHostForwardingChallenge challenge = new BoardHostForwardingChallenge();
        challenge.setChallengeId(command.challengeId()); challenge.setContractId(command.contractId());
        challenge.setServerBindingId(command.serverBindingId()); challenge.setNonceHash(command.nonceHash());
        challenge.setIssuedAt(command.issuedAt()); challenge.setExpiresAt(command.expiresAt());
        challenge.setRetentionUntil(command.retentionUntil()); challenge.setStatus("ISSUED");
        mapper.insertChallenge(challenge);
        return challenge;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppendResult appendEvent(BoardAttributionEvidenceEvent event) {
        requireEnabled(); verifier.verify(event, properties); exactPendingContract();
        BoardHostForwardingChallenge challenge = mapper.selectChallengeForUpdate(event.getChallengeId(), event.getServerBindingId(), event.getContractId());
        if (challenge == null || !"ISSUED".equals(challenge.getStatus()) || challenge.getExpiresAt().before(new Date())) {
            throw new IllegalStateException("Independent Board challenge is not active");
        }
        mapper.insertEventIfAbsent(event);
        BoardAttributionEvidenceEvent persisted = mapper.selectEventByReceiptForUpdate(event.getReceiptId());
        if (persisted == null || !Objects.equals(persisted.getEventId(), event.getEventId())
                || !Objects.equals(persisted.getCanonicalDigest(), event.getCanonicalDigest())
                || !Objects.equals(persisted.getServerBindingId(), event.getServerBindingId())) {
            throw new IllegalStateException("Independent Board receipt replay or identity collision");
        }
        if ("closure".equals(event.getStage())) mapper.updateChallengeStatus(event.getChallengeId(), "CONSUMED");
        return new AppendResult(persisted.getEventId(), persisted.getReceiptId(), "SEALED_REPORT_ONLY", persisted.getEventWatermark(), false, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BoardAttributionSnapshot sealSnapshot(BoardAttributionSnapshot snapshot) {
        requireEnabled(); verifier.verifySnapshot(snapshot, properties); exactPendingContract();
        mapper.insertSnapshot(snapshot);
        BoardAttributionSnapshot persisted = mapper.selectSnapshotByWindowForUpdate(snapshot.getContractId(), snapshot.getWindowStart(), snapshot.getWindowEnd());
        if (persisted == null || !Objects.equals(persisted.getSnapshotId(), snapshot.getSnapshotId())) throw new IllegalStateException("Independent Board snapshot cannot be re-read");
        return persisted;
    }

    private BoardAttributionProductContract exactPendingContract() {
        BoardAttributionProductContract contract = mapper.selectExactProductForUpdate(PRODUCT_ID, PRODUCT_VERSION);
        if (contract == null || !CONTRACT_ID.equals(contract.getContractId()) || !PRODUCT_ID.equals(contract.getProductId())
                || !PRODUCT_VERSION.equals(contract.getProductVersion()) || !"PENDING_HOST_REGISTRATION".equals(contract.getRegistrationStatus())
                || contract.isCandidateEnabled() || contract.isPublicRouteEnabled() || contract.isAuthoritativeCreditEnabled()) {
            throw new IllegalStateException("Independent Board exact product contract is not default-off");
        }
        return contract;
    }

    private void requireEnabled() {
        if (!properties.isEnabled() || properties.isCandidateEnabled() || properties.isPublicRouteEnabled() || properties.isAuthoritativeCreditEnabled()) {
            throw new IllegalStateException("Independent Board attribution writer is disabled or unsafe");
        }
    }

    private static final class StringUtilsExt {
        private static boolean sha256(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
        private static boolean binding(String value) { return value != null && (value.matches("[0-9a-f]{64}") || value.matches("srv_[A-Za-z0-9_-]{12}")); }
    }
}
