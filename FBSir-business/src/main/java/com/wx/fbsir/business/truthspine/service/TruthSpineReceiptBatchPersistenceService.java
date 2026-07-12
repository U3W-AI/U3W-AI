package com.wx.fbsir.business.truthspine.service;

import com.wx.fbsir.business.truthspine.domain.TruthSpineReceiptBatch;
import com.wx.fbsir.business.truthspine.mapper.TruthSpineReceiptBatchMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.Objects;

/**
 * Persists only an already-verified, locally quarantined Truth Spine batch.
 * Signature verification, trusted key management and production admission remain outside this
 * service. This boundary keeps a test receipt from accidentally becoming product credit.
 */
@Service
public class TruthSpineReceiptBatchPersistenceService {
    static final String TEST_STATE_CLASS = "TEST_QUARANTINE";
    static final String VERIFIED_STATUS = "VERIFIED";
    static final String ACCEPTED_STATUS = "ACCEPTED_TEST_QUARANTINE";

    private final TruthSpineReceiptBatchMapper mapper;

    public TruthSpineReceiptBatchPersistenceService(TruthSpineReceiptBatchMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public PersistResult persist(VerifiedTestStateBatch command) {
        validate(command);
        TruthSpineReceiptBatch batch = toEntity(command);
        mapper.insertIfAbsent(batch);
        // MySQL/JDBC may report 0, 1 or 2 for a no-op upsert depending on connection flags.
        // Never infer whether it was new from that value. The unique-key upsert claims the row
        // before the lock read, avoiding a missing-row FOR UPDATE gap-lock race under concurrency.
        TruthSpineReceiptBatch persisted = mapper.selectByWorkloadAndIdempotencyKeyForUpdate(
            command.workloadId(), command.idempotencyKey());
        if (persisted == null) {
            throw new IllegalStateException("Truth Spine test-state batch cannot be re-read after insert");
        }
        return existingResult(persisted, command);
    }

    private PersistResult existingResult(TruthSpineReceiptBatch existing, VerifiedTestStateBatch command) {
        if (!Objects.equals(existing.getBatchId(), command.batchId())
                || !Objects.equals(existing.getWorkloadId(), command.workloadId())
                || !Objects.equals(existing.getPayloadSha256(), command.payloadSha256())
                || !Objects.equals(existing.getInvocationId(), command.invocationId())
                || !Objects.equals(existing.getProductId(), command.productId())
                || !Objects.equals(existing.getBindingHash(), command.bindingHash())
                || !Objects.equals(existing.getHostReceiptHash(), command.hostReceiptHash())
                || !Objects.equals(existing.getSignerKeyId(), command.signerKeyId())
                || existing.isProductCreditEligible() || existing.isBusinessClosureEligible()
                || !TEST_STATE_CLASS.equals(existing.getStateClass())
                || !VERIFIED_STATUS.equals(existing.getVerificationStatus())
                || !ACCEPTED_STATUS.equals(existing.getStatus())) {
            throw new IllegalStateException("Truth Spine test-state idempotency key was reused for different evidence");
        }
        return result(existing.getBatchId());
    }

    private TruthSpineReceiptBatch toEntity(VerifiedTestStateBatch command) {
        TruthSpineReceiptBatch batch = new TruthSpineReceiptBatch();
        batch.setBatchId(command.batchId());
        batch.setWorkloadId(command.workloadId());
        batch.setIdempotencyKey(command.idempotencyKey());
        batch.setInvocationId(command.invocationId());
        batch.setProductId(command.productId());
        batch.setBindingHash(command.bindingHash());
        batch.setHostReceiptHash(command.hostReceiptHash());
        batch.setSignerKeyId(command.signerKeyId());
        batch.setPayloadSha256(command.payloadSha256());
        batch.setStateClass(TEST_STATE_CLASS);
        batch.setVerificationStatus(VERIFIED_STATUS);
        batch.setStatus(ACCEPTED_STATUS);
        batch.setProductCreditEligible(false);
        batch.setBusinessClosureEligible(false);
        batch.setVerifiedAt(command.verifiedAt());
        return batch;
    }

    private PersistResult result(String batchId) {
        return new PersistResult(batchId, ACCEPTED_STATUS, false, false);
    }

    private void validate(VerifiedTestStateBatch command) {
        if (command == null || !StringUtils.hasText(command.batchId())
                || !StringUtils.hasText(command.workloadId())
                || !StringUtils.hasText(command.idempotencyKey())
                || !StringUtils.hasText(command.invocationId())
                || !StringUtils.hasText(command.productId())
                || !StringUtils.hasText(command.signerKeyId())
                || command.verifiedAt() == null
                || !sha256(command.bindingHash()) || !sha256(command.hostReceiptHash())
                || !sha256(command.payloadSha256())) {
            throw new IllegalArgumentException("Truth Spine test-state receipt batch is incomplete");
        }
        if (command.productCreditEligible() || command.businessClosureEligible()) {
            throw new IllegalArgumentException("Truth Spine test-state batch cannot carry product or business credit");
        }
    }

    private boolean sha256(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    public record VerifiedTestStateBatch(String batchId, String workloadId, String idempotencyKey, String invocationId,
                                         String productId, String bindingHash, String hostReceiptHash,
                                         String signerKeyId, String payloadSha256,
                                         boolean productCreditEligible,
                                         boolean businessClosureEligible, Date verifiedAt) {
    }

    public record PersistResult(String batchId, String status, boolean productCreditEligible,
                                boolean businessClosureEligible) {
    }
}
