package com.wx.fbsir.business.board.attribution.service;

import com.wx.fbsir.business.board.attribution.config.IndependentBoardAttributionProperties;
import com.wx.fbsir.business.board.attribution.domain.BoardAttributionLedgerEvent;
import com.wx.fbsir.business.board.attribution.domain.BoardAttributionSummaryRow;
import com.wx.fbsir.business.board.attribution.mapper.IndependentBoardAttributionV1Mapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IndependentBoardAttributionAdminReadServiceTest {
    private final IndependentBoardAttributionV1Mapper mapper =
            mock(IndependentBoardAttributionV1Mapper.class);
    private final IndependentBoardAttributionProperties properties =
            properties();
    private final IndependentBoardAttributionAdminReadService service =
            new IndependentBoardAttributionAdminReadService(mapper, properties);

    @Test
    void returnsBoundedSixDimensionalRowsAndHighWatermark() {
        BoardAttributionSummaryRow row = new BoardAttributionSummaryRow();
        row.setProductId("fbsir-eight-seat-board");
        row.setListedManifestVersion("26.7.21");
        row.setChannel("OFFICIAL_EXPERTS");
        row.setTerminal("WORKBUDDY_WINDOWS");
        row.setIntentFamily("OPERATING_DIAGNOSIS");
        row.setReviewMode("STANDARD_REVIEW");
        row.setTrafficClass("NATURAL");
        row.setEntryCount(4);
        row.setClassifiedCount(3);
        row.setFirstValueCount(2);
        row.setDistinctBindingCount(4);
        row.setUnknownDebtCount(0);
        row.setEventHighWatermark(42);
        when(mapper.selectAttributionSummary(
                any(), any(), eq("NATURAL"))).thenReturn(List.of(row));

        IndependentBoardAttributionAdminReadService.Summary response =
                service.summary(
                        "2026-07-23T00:00:00Z",
                        "2026-07-23T12:00:00Z",
                        "natural");

        assertEquals(42, response.eventHighWatermark());
        assertEquals(1, response.rows().size());
        assertEquals(2, response.rows().get(0).getFirstValueCount());
        assertEquals("26.7.21",
                response.rows().get(0).getListedManifestVersion());
    }

    @Test
    void rejectsUnboundedInvalidOrDisabledReads() {
        assertThrows(IllegalArgumentException.class, () -> service.summary(
                "2026-07-22T00:00:00Z",
                "2026-07-23T00:00:00.001Z",
                "ALL"));
        assertThrows(IllegalArgumentException.class, () -> service.summary(
                "bad", "2026-07-23T00:00:00Z", "ALL"));
        assertThrows(IllegalArgumentException.class, () -> service.summary(
                "2026-07-23T00:00:00Z",
                "2026-07-23T01:00:00Z",
                "client_claimed_natural"));

        properties.setObservationAdminReadEnabled(false);
        assertThrows(IllegalStateException.class, () -> service.summary(
                Instant.EPOCH.toString(),
                Instant.EPOCH.plusSeconds(60).toString(),
                "ALL"));
    }

    @Test
    void returnsPrivacySafeSameBindingReceiptForAValidEventId() {
        BoardAttributionLedgerEvent row = new BoardAttributionLedgerEvent();
        row.setEventId("a".repeat(64));
        row.setReceiptId("b".repeat(64));
        row.setSameBindingKey("binding-secret");
        row.setEventType("ENTRY_OBSERVED");
        row.setSequenceNo(1L);
        row.setOccurredAt(java.util.Date.from(Instant.parse("2026-07-26T00:00:00Z")));
        row.setProductId("fbsir-eight-seat-board");
        row.setListedManifestVersion("26.7.21");
        row.setIntentFamily("UNKNOWN");
        row.setTrafficClass("UNKNOWN");
        row.setProductCreditEligible(false);
        row.setEventWatermark(7L);
        when(mapper.selectEventByEventId("a".repeat(64))).thenReturn(row);

        IndependentBoardAttributionAdminReadService.Receipt receipt =
                service.receipt("a".repeat(64));

        assertEquals("a".repeat(64), receipt.eventId());
        assertEquals("4a2aee9b4198a7bb5d448b5e31a80db8a2bc0df294f8e1ef9f90549a9e816c1b",
                receipt.sameBindingFingerprint());
        assertEquals("ENTRY_OBSERVED", receipt.eventType());
        assertEquals(1L, receipt.sequenceNo());
        assertEquals("2026-07-26T00:00:00Z", receipt.occurredAt());
        assertEquals(false, receipt.productCreditEligible());
    }

    @Test
    void rejectsMalformedOrUnknownReceiptLookups() {
        assertThrows(IllegalArgumentException.class,
                () -> service.receipt("not-an-event-id"));
        assertThrows(IllegalArgumentException.class,
                () -> service.receipt("A".repeat(64)));
        assertThrows(java.util.NoSuchElementException.class,
                () -> service.receipt("a".repeat(64)));
    }

    private IndependentBoardAttributionProperties properties() {
        IndependentBoardAttributionProperties value =
                new IndependentBoardAttributionProperties();
        value.setObservationAdminReadEnabled(true);
        return value;
    }
}
