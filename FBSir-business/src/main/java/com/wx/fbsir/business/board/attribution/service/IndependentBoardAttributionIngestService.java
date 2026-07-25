package com.wx.fbsir.business.board.attribution.service;

import com.wx.fbsir.business.board.attribution.binding.BoardSameBindingKeyDeriver;
import com.wx.fbsir.business.board.attribution.config.IndependentBoardAttributionProperties;
import com.wx.fbsir.business.board.attribution.domain.BoardAttributionJourneyHead;
import com.wx.fbsir.business.board.attribution.domain.BoardAttributionLedgerEvent;
import com.wx.fbsir.business.board.attribution.mapper.IndependentBoardAttributionV1Mapper;
import com.wx.fbsir.business.board.attribution.receipt.BoardAttributionEventV1;
import com.wx.fbsir.business.board.attribution.receipt.BoardAttributionEventVerifier;
import com.wx.fbsir.business.board.attribution.receipt.VerifiedBoardAttributionEvent;
import com.wx.fbsir.business.board.attribution.traffic.BoardTrafficClassificationResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.Instant;
import java.util.Date;
import java.util.Objects;

/**
 * Transactional Wave 1 ingest spine. The user-facing WorkBuddy path never
 * depends on this service; API2 can buffer and retry observations independently.
 */
@Service
@ConditionalOnProperty(
        prefix = "fbsir.independent-board.attribution",
        name = "observation-writer-enabled",
        havingValue = "true",
        matchIfMissing = false)
public class IndependentBoardAttributionIngestService {
    private final IndependentBoardAttributionV1Mapper mapper;
    private final IndependentBoardAttributionProperties properties;
    private final BoardAttributionEventVerifier verifier;
    private final BoardSameBindingKeyDeriver bindingKeyDeriver;
    private final BoardTrafficClassificationResolver trafficResolver;

    public IndependentBoardAttributionIngestService(
            IndependentBoardAttributionV1Mapper mapper,
            IndependentBoardAttributionProperties properties,
            BoardAttributionEventVerifier verifier,
            BoardSameBindingKeyDeriver bindingKeyDeriver,
            BoardTrafficClassificationResolver trafficResolver) {
        this.mapper = mapper;
        this.properties = properties;
        this.verifier = verifier;
        this.bindingKeyDeriver = bindingKeyDeriver;
        this.trafficResolver = trafficResolver;
    }

    @Transactional(rollbackFor = Exception.class)
    public IngestResult ingest(BoardAttributionEventV1 event) {
        if (!properties.isObservationWriterEnabled()
                || !properties.isIntentClassifierEnabled()) {
            throw new IllegalStateException("attribution_writer_disabled");
        }
        VerifiedBoardAttributionEvent verified =
                verifier.verify(event, properties);
        String sameBindingKey = bindingKeyDeriver.derive(
                event.getContractId(),
                event.getTenantSubjectDigest(),
                event.getServerBindingId(),
                event.getJourneyId(),
                event.getProductId(),
                event.getListedManifestVersion());

        String trafficClass = trafficResolver.resolve(event.getTrafficClass());
        BoardAttributionLedgerEvent existing =
                mapper.selectEventByEventId(event.getEventId());
        if (existing == null) {
            existing = mapper.selectEventByReceiptId(event.getReceiptId());
        }
        if (existing != null) {
            return replayOrReject(existing, verified, sameBindingKey);
        }

        if (event.getSequenceNo() == 1) {
            mapper.insertJourneyHeadIfAbsent(
                    newHead(event, trafficClass, sameBindingKey));
        }
        BoardAttributionJourneyHead head =
                mapper.selectJourneyHeadForUpdate(sameBindingKey);
        if (head == null) {
            throw new IllegalStateException("journey_head_missing");
        }

        // The initial idempotency lookup happens before the journey-head
        // lock.  A concurrent retry can therefore miss the first transaction
        // and arrive here after that transaction has committed.  Re-read
        // under the lock so an exact duplicate is a 200 replay rather than a
        // false out-of-order conflict (the API2 outbox relies on this).
        BoardAttributionLedgerEvent committed =
                mapper.selectEventByEventId(event.getEventId());
        if (committed == null) {
            committed = mapper.selectEventByReceiptId(event.getReceiptId());
        }
        if (committed != null) {
            return replayOrReject(committed, verified, sameBindingKey);
        }
        verifyHead(head, verified, trafficClass, sameBindingKey);

        String intentFamily = nextIntent(head, verified);
        boolean naturalClosure = event.getSequenceNo() == 3
                && "NATURAL".equals(trafficClass)
                && "SUCCESS".equals(event.getOutcome());
        boolean productCredit = properties.isProductCreditEnabled()
                && naturalClosure;
        BoardAttributionLedgerEvent row = ledgerRow(
                verified, trafficClass, intentFamily, productCredit,
                sameBindingKey);
        if (mapper.insertLedgerEvent(row) != 1) {
            throw new IllegalStateException("ledger_insert_failed");
        }
        if (mapper.advanceJourneyHead(
                sameBindingKey,
                head.getHeadVersion(),
                head.getLastSequenceNo(),
                event.getSequenceNo(),
                verified.eventDigest(),
                intentFamily) != 1) {
            throw new IllegalStateException("journey_head_conflict");
        }
        return new IngestResult(
                event.getEventId(),
                event.getReceiptId(),
                verified.eventDigest(),
                "APPENDED_REPORT_ONLY",
                false,
                trafficClass,
                intentFamily,
                naturalClosure,
                productCredit);
    }

    private IngestResult replayOrReject(
            BoardAttributionLedgerEvent existing,
            VerifiedBoardAttributionEvent verified,
            String sameBindingKey) {
        BoardAttributionEventV1 event = verified.rawEvent();
        boolean exact = Objects.equals(existing.getEventId(), event.getEventId())
                && Objects.equals(existing.getReceiptId(), event.getReceiptId())
                && Objects.equals(existing.getCanonicalDigest(),
                verified.canonicalDigest())
                && Objects.equals(existing.getSameBindingKey(),
                sameBindingKey)
                && existing.getSequenceNo() == event.getSequenceNo();
        if (!exact) {
            throw new IllegalStateException("event_or_receipt_collision");
        }
        boolean naturalClosure = existing.getSequenceNo() == 3
                && "NATURAL".equals(existing.getTrafficClass())
                && "SUCCESS".equals(existing.getOutcome());
        return new IngestResult(
                existing.getEventId(),
                existing.getReceiptId(),
                existing.getEventDigest(),
                "IDEMPOTENT_REPLAY",
                true,
                existing.getTrafficClass(),
                existing.getIntentFamily(),
                naturalClosure,
                existing.isProductCreditEligible());
    }

    private BoardAttributionJourneyHead newHead(
            BoardAttributionEventV1 event,
            String trafficClass,
            String sameBindingKey) {
        BoardAttributionJourneyHead head = new BoardAttributionJourneyHead();
        head.setSameBindingKey(sameBindingKey);
        head.setContractId(event.getContractId());
        head.setTenantSubjectDigest(event.getTenantSubjectDigest());
        head.setServerBindingId(event.getServerBindingId());
        head.setJourneyId(event.getJourneyId());
        head.setProductId(event.getProductId());
        head.setListedManifestVersion(event.getListedManifestVersion());
        head.setEmbeddedContractVersion(event.getEmbeddedContractVersion());
        head.setChannel(event.getChannel());
        head.setTerminal(event.getTerminal());
        head.setHostVersion(event.getHostVersion());
        head.setTrafficClass(trafficClass);
        head.setIntentFamily("UNKNOWN");
        head.setLastSequenceNo(0);
        head.setLastEventDigest("");
        head.setHeadVersion(0);
        return head;
    }

    private void verifyHead(
            BoardAttributionJourneyHead head,
            VerifiedBoardAttributionEvent verified,
            String trafficClass,
            String sameBindingKey) {
        BoardAttributionEventV1 event = verified.rawEvent();
        boolean identityMatches =
                Objects.equals(head.getSameBindingKey(), sameBindingKey)
                && Objects.equals(head.getContractId(), event.getContractId())
                && Objects.equals(head.getTenantSubjectDigest(),
                event.getTenantSubjectDigest())
                && Objects.equals(head.getServerBindingId(),
                event.getServerBindingId())
                && Objects.equals(head.getJourneyId(), event.getJourneyId())
                && Objects.equals(head.getProductId(), event.getProductId())
                && Objects.equals(head.getListedManifestVersion(),
                event.getListedManifestVersion())
                && Objects.equals(head.getEmbeddedContractVersion(),
                event.getEmbeddedContractVersion())
                && Objects.equals(head.getChannel(), event.getChannel())
                && Objects.equals(head.getTerminal(), event.getTerminal())
                && Objects.equals(head.getHostVersion(), event.getHostVersion())
                && Objects.equals(head.getTrafficClass(), trafficClass);
        if (!identityMatches) {
            throw new IllegalStateException("journey_identity_conflict");
        }
        if (event.getSequenceNo() != head.getLastSequenceNo() + 1
                || !Objects.equals(
                normalized(head.getLastEventDigest()),
                normalized(event.getPreviousEventDigest()))) {
            throw new IllegalStateException("event_out_of_order");
        }
    }

    private String nextIntent(
            BoardAttributionJourneyHead head,
            VerifiedBoardAttributionEvent verified) {
        long sequence = verified.rawEvent().getSequenceNo();
        if (sequence == 1) {
            return "UNKNOWN";
        }
        if (sequence == 2) {
            return verified.intentFamily();
        }
        if (!Objects.equals(head.getIntentFamily(), verified.intentFamily())) {
            throw new IllegalStateException("intent_family_conflict");
        }
        return head.getIntentFamily();
    }

    private BoardAttributionLedgerEvent ledgerRow(
            VerifiedBoardAttributionEvent verified,
            String trafficClass,
            String intentFamily,
            boolean productCredit,
            String sameBindingKey) {
        BoardAttributionEventV1 event = verified.rawEvent();
        BoardAttributionLedgerEvent row = new BoardAttributionLedgerEvent();
        row.setEventId(event.getEventId());
        row.setReceiptId(event.getReceiptId());
        row.setSameBindingKey(sameBindingKey);
        row.setContractId(event.getContractId());
        row.setJourneyId(event.getJourneyId());
        row.setServerBindingId(event.getServerBindingId());
        row.setTenantSubjectDigest(event.getTenantSubjectDigest());
        row.setEventType(event.getEventType());
        row.setSequenceNo(event.getSequenceNo());
        row.setOccurredAt(date(event.getOccurredAt()));
        row.setProductId(event.getProductId());
        row.setPackageId(event.getPackageId());
        row.setAgentName(event.getAgentName());
        row.setMarketplace(event.getMarketplace());
        row.setListedSurface(event.getListedSurface());
        row.setListedManifestVersion(event.getListedManifestVersion());
        row.setEmbeddedContractVersion(event.getEmbeddedContractVersion());
        row.setHostClientFamily(event.getHostClientFamily());
        row.setHostVersion(event.getHostVersion());
        row.setTerminal(event.getTerminal());
        row.setChannel(event.getChannel());
        row.setRequestSource(event.getRequestSource());
        row.setIntentSignal(event.getIntentSignal());
        row.setIntentFamily(intentFamily);
        row.setClassificationSource(event.getClassificationSource());
        row.setClassifierVersion(event.getClassifierVersion());
        row.setConfidenceBucket(event.getConfidenceBucket());
        row.setReviewMode(event.getReviewMode());
        row.setTrafficClass(trafficClass);
        row.setTrafficAuthority(event.getTrafficAuthority());
        row.setOutcome(event.getOutcome());
        row.setPreviousEventDigest(event.getPreviousEventDigest());
        row.setTraceparent(event.getTraceparent());
        row.setCanonicalDigest(verified.canonicalDigest());
        row.setEventDigest(verified.eventDigest());
        row.setSignerKeyId(event.getKeyId());
        row.setSignatureHex(verified.signatureHex());
        row.setNonceHash(verified.nonceHash());
        row.setIssuedAt(Date.from(verified.issuedAt()));
        row.setExpiresAt(Date.from(verified.expiresAt()));
        row.setProductCreditEligible(productCredit);
        return row;
    }

    private Date date(String value) {
        return Date.from(Instant.parse(value));
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    public record IngestResult(
            String eventId,
            String receiptId,
            String eventDigest,
            String status,
            boolean idempotentReplay,
            String trafficClass,
            String intentFamily,
            boolean naturalClosureEligible,
            boolean productCreditEligible) {
    }
}
