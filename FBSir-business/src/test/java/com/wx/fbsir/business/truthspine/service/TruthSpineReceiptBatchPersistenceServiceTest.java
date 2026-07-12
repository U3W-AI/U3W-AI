package com.wx.fbsir.business.truthspine.service;

import com.wx.fbsir.business.truthspine.domain.TruthSpineReceiptBatch;
import com.wx.fbsir.business.truthspine.mapper.TruthSpineReceiptBatchMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TruthSpineReceiptBatchPersistenceServiceTest {
    private TruthSpineReceiptBatchMapper mapper;
    private TruthSpineReceiptBatchPersistenceService service;

    @BeforeEach
    void setUp() {
        mapper = mock(TruthSpineReceiptBatchMapper.class);
        when(mapper.insertIfAbsent(any(TruthSpineReceiptBatch.class))).thenReturn(1);
        service = new TruthSpineReceiptBatchPersistenceService(mapper);
    }

    @Test
    void persistsVerifiedTestStateBatchWithPermanentZeroCredit() {
        when(mapper.selectByWorkloadAndIdempotencyKeyForUpdate("api2-test-workload-1", "idem-1"))
            .thenReturn(persisted("batch-1", hash('d')));
        TruthSpineReceiptBatchPersistenceService.PersistResult result = service.persist(command());

        assertEquals("batch-1", result.batchId());
        assertEquals("ACCEPTED_TEST_QUARANTINE", result.status());
        assertFalse(result.productCreditEligible());
        assertFalse(result.businessClosureEligible());
        org.mockito.ArgumentCaptor<TruthSpineReceiptBatch> saved = org.mockito.ArgumentCaptor.forClass(TruthSpineReceiptBatch.class);
        verify(mapper).insertIfAbsent(saved.capture());
        assertEquals("TEST_QUARANTINE", saved.getValue().getStateClass());
        assertFalse(saved.getValue().isProductCreditEligible());
        assertFalse(saved.getValue().isBusinessClosureEligible());
    }

    @Test
    void returnsExistingOnlyWhenTheIdempotencyIdentityMatches() {
        TruthSpineReceiptBatch existing = persisted("batch-1", hash('d'));
        when(mapper.selectByWorkloadAndIdempotencyKeyForUpdate("api2-test-workload-1", "idem-1")).thenReturn(existing);

        TruthSpineReceiptBatchPersistenceService.PersistResult result = service.persist(command());

        assertEquals("batch-1", result.batchId());
        assertEquals("ACCEPTED_TEST_QUARANTINE", result.status());
        verify(mapper).selectByWorkloadAndIdempotencyKeyForUpdate("api2-test-workload-1", "idem-1");
    }

    @Test
    void rejectsReuseOfIdempotencyKeyForDifferentVerifiedPayload() {
        when(mapper.selectByWorkloadAndIdempotencyKeyForUpdate("api2-test-workload-1", "idem-1")).thenReturn(persisted(hash('e')));

        assertThrows(IllegalStateException.class, () -> service.persist(command()));
    }

    @Test
    void rejectsReuseOfIdempotencyKeyForDifferentBatchIdentity() {
        when(mapper.selectByWorkloadAndIdempotencyKeyForUpdate("api2-test-workload-1", "idem-1"))
            .thenReturn(persisted("batch-from-another-request", hash('d')));

        assertThrows(IllegalStateException.class, () -> service.persist(command()));
    }

    @Test
    void reReadsConcurrentIdempotencyCollisionBeforeReturningSuccess() {
        when(mapper.selectByWorkloadAndIdempotencyKeyForUpdate("api2-test-workload-1", "idem-1"))
            .thenReturn(persisted("batch-1", hash('d')));
        when(mapper.insertIfAbsent(any(TruthSpineReceiptBatch.class))).thenReturn(1);

        TruthSpineReceiptBatchPersistenceService.PersistResult result = service.persist(command());

        assertEquals("batch-1", result.batchId());
        verify(mapper, times(1)).selectByWorkloadAndIdempotencyKeyForUpdate("api2-test-workload-1", "idem-1");
    }

    @Test
    void reReadAfterUpsertRejectsMismatchedEvidenceRegardlessOfAffectedRows() {
        when(mapper.selectByWorkloadAndIdempotencyKeyForUpdate("api2-test-workload-1", "idem-1"))
            .thenReturn(persisted("batch-existing", hash('e')));
        when(mapper.insertIfAbsent(any(TruthSpineReceiptBatch.class))).thenReturn(1);

        assertThrows(IllegalStateException.class, () -> service.persist(command()));
    }

    @Test
    void refusesAnyAttemptToPromoteTestStateIntoProductCredit() {
        TruthSpineReceiptBatchPersistenceService.VerifiedTestStateBatch promoted = new TruthSpineReceiptBatchPersistenceService.VerifiedTestStateBatch(
            "batch-1", "api2-test-workload-1", "idem-1", "invocation-1", "fbsir-super-partner-group", hash('b'), hash('c'),
            "host-test-key-1", hash('d'), true, false, new Date());

        assertThrows(IllegalArgumentException.class, () -> service.persist(promoted));
    }

    @Test
    void rejectsNonCanonicalUppercaseDigestBeforeItCanCreateASecondEvidenceIdentity() {
        TruthSpineReceiptBatchPersistenceService.VerifiedTestStateBatch uppercase = new TruthSpineReceiptBatchPersistenceService.VerifiedTestStateBatch(
            "batch-1", "api2-test-workload-1", "idem-1", "invocation-1", "fbsir-super-partner-group", hash('b'), hash('c'),
            "host-test-key-1", "A".repeat(64), false, false, new Date());

        assertThrows(IllegalArgumentException.class, () -> service.persist(uppercase));
    }

    private TruthSpineReceiptBatchPersistenceService.VerifiedTestStateBatch command() {
        return new TruthSpineReceiptBatchPersistenceService.VerifiedTestStateBatch(
            "batch-1", "api2-test-workload-1", "idem-1", "invocation-1", "fbsir-super-partner-group", hash('b'), hash('c'),
            "host-test-key-1", hash('d'), false, false, new Date());
    }

    private TruthSpineReceiptBatch persisted(String payloadSha256) {
        return persisted("batch-existing", payloadSha256);
    }

    private TruthSpineReceiptBatch persisted(String batchId, String payloadSha256) {
        TruthSpineReceiptBatch value = new TruthSpineReceiptBatch();
        value.setBatchId(batchId);
        value.setWorkloadId("api2-test-workload-1");
        value.setIdempotencyKey("idem-1");
        value.setPayloadSha256(payloadSha256);
        value.setInvocationId("invocation-1");
        value.setProductId("fbsir-super-partner-group");
        value.setBindingHash(hash('b'));
        value.setHostReceiptHash(hash('c'));
        value.setSignerKeyId("host-test-key-1");
        value.setStateClass("TEST_QUARANTINE");
        value.setVerificationStatus("VERIFIED");
        value.setProductCreditEligible(false);
        value.setBusinessClosureEligible(false);
        value.setStatus("ACCEPTED_TEST_QUARANTINE");
        return value;
    }

    private String hash(char prefix) {
        return String.valueOf(prefix).repeat(64);
    }
}
