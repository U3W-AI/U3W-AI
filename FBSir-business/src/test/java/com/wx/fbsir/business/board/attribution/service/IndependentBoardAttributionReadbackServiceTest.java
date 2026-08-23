package com.wx.fbsir.business.board.attribution.service;

import com.wx.fbsir.business.board.attribution.config.IndependentBoardAttributionProperties;
import com.wx.fbsir.business.board.attribution.domain.BoardAttributionLedgerEvent;
import com.wx.fbsir.business.board.attribution.mapper.IndependentBoardAttributionV1Mapper;
import com.wx.fbsir.business.board.attribution.receipt.BoardAttributionReadbackResponseV1;
import com.wx.fbsir.business.board.attribution.receipt.VerifiedBoardAttributionReadbackRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IndependentBoardAttributionReadbackServiceTest {
    private static final String EVENT_ID = "1".repeat(64);
    private static final String RECEIPT_ID = "2".repeat(64);
    private static final String EVENT_DIGEST = "3".repeat(64);
    private static final String JAR_SHA = "a".repeat(64);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-23T08:40:00Z"), ZoneOffset.UTC);

    private final IndependentBoardAttributionV1Mapper mapper =
            mock(IndependentBoardAttributionV1Mapper.class);
    private final IndependentBoardAttributionProperties properties =
            properties();
    private final IndependentBoardAttributionReadbackService service =
            new IndependentBoardAttributionReadbackService(
                    mapper, properties, CLOCK);

    @BeforeEach
    void resetMapper() {
        reset(mapper);
        when(mapper.selectTransactionReadOnlyState()).thenReturn(1);
    }

    @Test
    void returnsCommittedExactOnlyWhenBothKeysResolveTheSameTuple() {
        BoardAttributionLedgerEvent row = row(
                EVENT_ID, RECEIPT_ID, EVENT_DIGEST, false);
        when(mapper.selectEventByEventId(EVENT_ID)).thenReturn(row);
        when(mapper.selectEventByReceiptId(RECEIPT_ID)).thenReturn(row);

        BoardAttributionReadbackResponseV1 response =
                service.read(request());

        assertEquals("COMMITTED_EXACT", response.status());
        assertEquals(EVENT_ID, response.eventId());
        assertEquals(RECEIPT_ID, response.receiptId());
        assertEquals(EVENT_DIGEST, response.eventDigest());
        assertEquals("w05e-readback-test", response.receiverReleaseId());
        assertEquals(JAR_SHA, response.receiverJarSha256());
        assertEquals("2026-08-23T08:40:00Z", response.readAt());
        assertFalse(response.productCreditEligible());
        assertNoWrites();
    }

    @Test
    void returnsAuthoritativeNotFoundOnlyWhenBothKeysAreAbsent() {
        BoardAttributionReadbackResponseV1 response =
                service.read(request());

        assertEquals("NOT_FOUND_AUTHORITATIVE", response.status());
        assertEquals(true, response.authoritativeRead());
        verify(mapper).selectEventByEventId(EVENT_ID);
        verify(mapper).selectEventByReceiptId(RECEIPT_ID);
        assertNoWrites();
    }

    @Test
    void returnsCollisionWithoutDisclosingWhichKeyMismatched() {
        when(mapper.selectEventByEventId(EVENT_ID)).thenReturn(row(
                EVENT_ID, "4".repeat(64), EVENT_DIGEST, false));
        when(mapper.selectEventByReceiptId(RECEIPT_ID)).thenReturn(row(
                "5".repeat(64), RECEIPT_ID, EVENT_DIGEST, false));

        BoardAttributionReadbackResponseV1 response =
                service.read(request());

        assertEquals("IDENTITY_COLLISION", response.status());
        assertEquals(EVENT_ID, response.eventId());
        assertEquals(RECEIPT_ID, response.receiptId());
        assertEquals(EVENT_DIGEST, response.eventDigest());
        assertNoWrites();
    }

    @Test
    void failsClosedIfCommittedCreditIsUnexpectedlyTrue() {
        BoardAttributionLedgerEvent row = row(
                EVENT_ID, RECEIPT_ID, EVENT_DIGEST, true);
        when(mapper.selectEventByEventId(EVENT_ID)).thenReturn(row);
        when(mapper.selectEventByReceiptId(RECEIPT_ID)).thenReturn(row);

        assertThrows(
                IndependentBoardAttributionReadbackUnavailableException.class,
                () -> service.read(request()));
        assertNoWrites();
    }

    @Test
    void failsClosedWhenReceiverBindingIsMissing() {
        properties.setAuthoritativeReadbackReceiverJarSha256("");

        assertThrows(
                IndependentBoardAttributionReadbackUnavailableException.class,
                () -> service.read(request()));
        verify(mapper, never()).selectEventByEventId(EVENT_ID);
        verify(mapper, never()).selectEventByReceiptId(RECEIPT_ID);
        assertNoWrites();
    }

    @Test
    void unavailableResponseIsNonAuthoritativeAndNeverPromotesCredit() {
        BoardAttributionReadbackResponseV1 response =
                service.unavailable(request());

        assertEquals("READBACK_UNAVAILABLE", response.status());
        assertFalse(response.authoritativeRead());
        assertFalse(response.productCreditEligible());
    }

    private void assertNoWrites() {
        verify(mapper, never()).insertJourneyHeadIfAbsent(
                org.mockito.ArgumentMatchers.any());
        verify(mapper, never()).selectJourneyHeadForUpdate(
                org.mockito.ArgumentMatchers.any());
        verify(mapper, never()).insertLedgerEvent(
                org.mockito.ArgumentMatchers.any());
        verify(mapper, never()).advanceJourneyHead(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private IndependentBoardAttributionProperties properties() {
        IndependentBoardAttributionProperties value =
                new IndependentBoardAttributionProperties();
        value.setAuthoritativeReadbackEnabled(true);
        value.setAuthoritativeReadbackReceiverReleaseId(
                "w05e-readback-test");
        value.setAuthoritativeReadbackReceiverJarSha256(JAR_SHA);
        return value;
    }

    private VerifiedBoardAttributionReadbackRequest request() {
        return new VerifiedBoardAttributionReadbackRequest(
                EVENT_ID,
                RECEIPT_ID,
                EVENT_DIGEST,
                Instant.parse("2026-08-23T08:39:30Z"),
                Instant.parse("2026-08-23T08:40:30Z"),
                "c".repeat(64),
                "b".repeat(64),
                "readback-k1");
    }

    private BoardAttributionLedgerEvent row(
            String eventId,
            String receiptId,
            String eventDigest,
            boolean credit) {
        BoardAttributionLedgerEvent row = new BoardAttributionLedgerEvent();
        row.setEventId(eventId);
        row.setReceiptId(receiptId);
        row.setEventDigest(eventDigest);
        row.setProductCreditEligible(credit);
        return row;
    }
}
