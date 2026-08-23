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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IndependentBoardAttributionIngestServiceTest {
    private static final String BINDING_SECRET =
            "utf8:abcdef0123456789abcdef0123456789";
    private final IndependentBoardAttributionV1Mapper mapper =
            mock(IndependentBoardAttributionV1Mapper.class);
    private final BoardAttributionEventVerifier verifier =
            mock(BoardAttributionEventVerifier.class);
    private final IndependentBoardAttributionProperties properties = properties();
    private final BoardSameBindingKeyDeriver binding =
            new BoardSameBindingKeyDeriver(BINDING_SECRET);
    private final IndependentBoardAttributionIngestService service =
            new IndependentBoardAttributionIngestService(
                    mapper, properties, verifier, binding,
                    new BoardTrafficClassificationResolver());

    @BeforeEach
    void resetMocks() {
        reset(mapper, verifier);
    }

    @Test
    void appendsTheFirstOfficialEntryWithoutConnectorOrProductCredit() {
        BoardAttributionEventV1 event = event(1, "ENTRY_OBSERVED", "", "unknown");
        String sameBindingKey = serverKey(event);
        when(verifier.verify(event, properties)).thenReturn(verified(event, "UNKNOWN"));
        when(mapper.insertJourneyHeadIfAbsent(any())).thenReturn(1);
        when(mapper.selectJourneyHeadForUpdate(sameBindingKey))
                .thenReturn(head(0, "", "UNKNOWN", 0));
        when(mapper.insertLedgerEvent(any())).thenReturn(1);
        when(mapper.advanceJourneyHead(
                eq(sameBindingKey), eq(0L), eq(0L), eq(1L),
                eq("a".repeat(64)), eq("UNKNOWN"))).thenReturn(1);

        IndependentBoardAttributionIngestService.IngestResult result =
                service.ingest(event);

        assertEquals("APPENDED_REPORT_ONLY", result.status());
        assertEquals("NATURAL", result.trafficClass());
        assertEquals("UNKNOWN", result.intentFamily());
        assertFalse(result.idempotentReplay());
        assertFalse(result.naturalClosureEligible());
        assertFalse(result.productCreditEligible());
        ArgumentCaptor<BoardAttributionLedgerEvent> inserted =
                ArgumentCaptor.forClass(BoardAttributionLedgerEvent.class);
        verify(mapper).insertLedgerEvent(inserted.capture());
        assertEquals("26.7.21", inserted.getValue().getListedManifestVersion());
        assertEquals("26.7.20", inserted.getValue().getEmbeddedContractVersion());
        assertEquals(sameBindingKey, inserted.getValue().getSameBindingKey());
        assertFalse(inserted.getValue().isProductCreditEligible());
    }

    @Test
    void returnsExactReplayWithoutLockingOrWriting() {
        BoardAttributionEventV1 event = event(1, "ENTRY_OBSERVED", "", "unknown");
        when(verifier.verify(event, properties)).thenReturn(verified(event, "UNKNOWN"));
        when(mapper.selectEventByEventId(event.getEventId()))
                .thenReturn(persisted(event, "a".repeat(64), "UNKNOWN"));

        IndependentBoardAttributionIngestService.IngestResult result =
                service.ingest(event);

        assertEquals("IDEMPOTENT_REPLAY", result.status());
        assertEquals("a".repeat(64), result.eventDigest());
        assertFalse(result.productCreditEligible());
        verify(mapper, never()).selectJourneyHeadForUpdate(any());
        verify(mapper, never()).insertLedgerEvent(any());
    }

    @Test
    void rechecksForExactReplayAfterTheJourneyHeadLock() {
        BoardAttributionEventV1 event = event(1, "ENTRY_OBSERVED", "", "unknown");
        String sameBindingKey = serverKey(event);
        BoardAttributionLedgerEvent persisted =
                persisted(event, "a".repeat(64), "UNKNOWN");
        when(verifier.verify(event, properties)).thenReturn(verified(event, "UNKNOWN"));
        when(mapper.selectEventByEventId(event.getEventId()))
                .thenReturn(null);
        when(mapper.selectEventByEventIdForUpdate(event.getEventId()))
                .thenReturn(persisted);
        when(mapper.insertJourneyHeadIfAbsent(any())).thenReturn(1);
        when(mapper.selectJourneyHeadForUpdate(sameBindingKey))
                .thenReturn(head(1, "a".repeat(64), "UNKNOWN", 1));

        IndependentBoardAttributionIngestService.IngestResult result =
                service.ingest(event);

        assertEquals("IDEMPOTENT_REPLAY", result.status());
        assertFalse(result.productCreditEligible());
        verify(mapper, never()).insertLedgerEvent(any());
    }

    @Test
    void derivesBindingInternallyAndRejectsOutOfOrderStageWithZeroWrites() {
        BoardAttributionEventV1 chosen = event(
                1, "ENTRY_OBSERVED", "", "unknown");
        chosen.setSameBindingKey("0".repeat(64));
        when(verifier.verify(chosen, properties)).thenReturn(verified(chosen, "UNKNOWN"));
        when(mapper.insertJourneyHeadIfAbsent(any())).thenReturn(1);
        when(mapper.selectJourneyHeadForUpdate(serverKey(chosen)))
                .thenReturn(head(0, "", "UNKNOWN", 0));
        when(mapper.insertLedgerEvent(any())).thenReturn(1);
        when(mapper.advanceJourneyHead(
                eq(serverKey(chosen)), eq(0L), eq(0L), eq(1L),
                eq("a".repeat(64)), eq("UNKNOWN"))).thenReturn(1);
        service.ingest(chosen);
        ArgumentCaptor<BoardAttributionLedgerEvent> inserted =
                ArgumentCaptor.forClass(BoardAttributionLedgerEvent.class);
        verify(mapper).insertLedgerEvent(inserted.capture());
        assertEquals(serverKey(chosen), inserted.getValue().getSameBindingKey());

        reset(mapper, verifier);
        BoardAttributionEventV1 second = event(
                2, "INTENT_CLASSIFIED", "b".repeat(64), "operating_diagnosis");
        when(verifier.verify(second, properties))
                .thenReturn(verified(second, "OPERATING_DIAGNOSIS"));

        assertThrows(IllegalStateException.class, () -> service.ingest(second));
        verify(mapper, never()).insertLedgerEvent(any());
    }

    @Test
    void advancesIntentAtStageTwoAndClosesNaturalValueWithoutCreditAtStageThree() {
        BoardAttributionEventV1 second = event(
                2, "INTENT_CLASSIFIED", "b".repeat(64), "operating_diagnosis");
        String secondBindingKey = serverKey(second);
        when(verifier.verify(second, properties))
                .thenReturn(verified(second, "OPERATING_DIAGNOSIS"));
        when(mapper.selectJourneyHeadForUpdate(secondBindingKey))
                .thenReturn(head(1, "b".repeat(64), "UNKNOWN", 1));
        when(mapper.insertLedgerEvent(any())).thenReturn(1);
        when(mapper.advanceJourneyHead(
                eq(secondBindingKey), eq(1L), eq(1L), eq(2L),
                eq("a".repeat(64)), eq("OPERATING_DIAGNOSIS"))).thenReturn(1);

        IndependentBoardAttributionIngestService.IngestResult classified =
                service.ingest(second);
        assertEquals("OPERATING_DIAGNOSIS", classified.intentFamily());
        assertFalse(classified.naturalClosureEligible());

        reset(mapper, verifier);
        BoardAttributionEventV1 third = event(
                3, "FIRST_VALUE_COMPLETED", "b".repeat(64),
                "operating_diagnosis");
        String thirdBindingKey = serverKey(third);
        when(verifier.verify(third, properties))
                .thenReturn(verified(third, "OPERATING_DIAGNOSIS"));
        when(mapper.selectJourneyHeadForUpdate(thirdBindingKey))
                .thenReturn(head(2, "b".repeat(64), "OPERATING_DIAGNOSIS", 2));
        when(mapper.insertLedgerEvent(any())).thenReturn(1);
        when(mapper.advanceJourneyHead(
                eq(thirdBindingKey), eq(2L), eq(2L), eq(3L),
                eq("a".repeat(64)), eq("OPERATING_DIAGNOSIS"))).thenReturn(1);

        IndependentBoardAttributionIngestService.IngestResult closed =
                service.ingest(third);
        assertEquals("NATURAL", closed.trafficClass());
        assertEquals("OPERATING_DIAGNOSIS", closed.intentFamily());
        assertEquals(true, closed.naturalClosureEligible());
        assertFalse(closed.productCreditEligible());
    }

    private IndependentBoardAttributionProperties properties() {
        IndependentBoardAttributionProperties value =
                new IndependentBoardAttributionProperties();
        value.setObservationWriterEnabled(true);
        value.setIntentClassifierEnabled(true);
        value.setProductCreditEnabled(false);
        return value;
    }

    private BoardAttributionEventV1 event(
            long sequence, String type, String previousDigest, String intentSignal) {
        BoardAttributionEventV1 event = new BoardAttributionEventV1();
        event.setSchemaVersion("fbsir.independentBoardAttributionEvent.v1");
        event.setEventId(Integer.toString((int) sequence).repeat(64));
        event.setReceiptId(Integer.toString((int) sequence + 3).repeat(64));
        event.setContractId("FBSIR_INDEPENDENT_BOARD_W1A_V1");
        event.setEventType(type);
        event.setSequenceNo(sequence);
        event.setOccurredAt("2026-07-23T10:00:00Z");
        event.setProductId("fbsir-eight-seat-board");
        event.setPackageId("fbsir-eight-seat-board");
        event.setAgentName("board-convener");
        event.setMarketplace("experts");
        event.setListedSurface("listed_runtime_state");
        event.setListedManifestVersion("26.7.21");
        event.setEmbeddedContractVersion("26.7.20");
        event.setHostClientFamily("WORKBUDDY");
        event.setHostVersion("5.3.3.0");
        event.setTerminal("WORKBUDDY_WINDOWS");
        event.setChannel("OFFICIAL_EXPERTS");
        event.setRequestSource("WORKBUDDY_OFFICIAL_ENTRY");
        event.setIntentSignal(intentSignal);
        event.setClassificationSource("PACKAGE_SCENE_ROUTER");
        event.setClassifierVersion("scene-lexicon-26.7.20-core.1");
        event.setConfidenceBucket(sequence == 1 ? "UNKNOWN" : "HIGH");
        event.setReviewMode("STANDARD_REVIEW");
        event.setJourneyId("3".repeat(64));
        event.setServerBindingId("srv_wave1Binding01");
        event.setTenantSubjectDigest("5".repeat(64));
        event.setSameBindingKey("");
        event.setTrafficClass("NATURAL");
        event.setTrafficAuthority("API2_SERVER_CLASSIFIER_V1");
        event.setOutcome("SUCCESS");
        event.setPreviousEventDigest(previousDigest);
        event.setTraceparent("");
        event.setRawContentStored(false);
        event.setIssuedAt("2026-07-23T10:00:00Z");
        event.setExpiresAt("2026-07-23T10:02:00Z");
        event.setNonce("nonce-" + sequence);
        event.setKeyId("wave1-k1");
        event.setSignatureAlgorithm("hmac-sha256-v1");
        event.setSignature("v1=" + "9".repeat(64));
        return event;
    }

    private VerifiedBoardAttributionEvent verified(
            BoardAttributionEventV1 event, String intent) {
        return new VerifiedBoardAttributionEvent(
                event,
                Instant.parse(event.getIssuedAt()),
                Instant.parse(event.getExpiresAt()),
                "c".repeat(64),
                "9".repeat(64),
                "d".repeat(64),
                "a".repeat(64),
                intent);
    }

    private BoardAttributionJourneyHead head(
            long sequence, String digest, String intent, long version) {
        BoardAttributionJourneyHead head = new BoardAttributionJourneyHead();
        BoardAttributionEventV1 event = event(
                Math.max(1, sequence), "ENTRY_OBSERVED", "", "unknown");
        head.setSameBindingKey(serverKey(event));
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
        head.setTrafficClass(event.getTrafficClass());
        head.setIntentFamily(intent);
        head.setLastSequenceNo(sequence);
        head.setLastEventDigest(digest);
        head.setHeadVersion(version);
        return head;
    }

    private BoardAttributionLedgerEvent persisted(
            BoardAttributionEventV1 event, String eventDigest, String intent) {
        BoardAttributionLedgerEvent persisted =
                new BoardAttributionLedgerEvent();
        persisted.setEventId(event.getEventId());
        persisted.setReceiptId(event.getReceiptId());
        persisted.setCanonicalDigest("c".repeat(64));
        persisted.setEventDigest(eventDigest);
        persisted.setSameBindingKey(serverKey(event));
        persisted.setSequenceNo(event.getSequenceNo());
        persisted.setTrafficClass(event.getTrafficClass());
        persisted.setIntentFamily(intent);
        persisted.setOutcome(event.getOutcome());
        persisted.setProductCreditEligible(false);
        return persisted;
    }

    private String serverKey(BoardAttributionEventV1 event) {
        return binding.derive(
                event.getContractId(), event.getTenantSubjectDigest(),
                event.getServerBindingId(), event.getJourneyId(),
                event.getProductId(), event.getListedManifestVersion());
    }
}
