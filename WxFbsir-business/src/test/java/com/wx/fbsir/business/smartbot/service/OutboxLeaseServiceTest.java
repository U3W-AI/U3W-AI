package com.wx.fbsir.business.smartbot.service;

import com.wx.fbsir.business.smartbot.domain.DeliveryOutbox;
import com.wx.fbsir.business.smartbot.mapper.DeliveryOutboxMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxLeaseServiceTest {

    private DeliveryOutboxMapper mapper;
    private OutboxLeaseService service;

    @BeforeEach
    void setUp() {
        mapper = mock(DeliveryOutboxMapper.class);
        service = new OutboxLeaseService(mapper);
    }

    @Test
    void claimsOneRowAndReturnsItsFencingToken() {
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        when(mapper.selectClaimCandidateForUpdate("INTERNAL_DISPATCHER")).thenReturn(7L);
        when(mapper.claimById(eq(7L), eq("INTERNAL_DISPATCHER"), eq("worker-01"),
            token.capture(), eq(30L))).thenReturn(1);
        DeliveryOutbox outbox = new DeliveryOutbox();
        outbox.setId(7L);
        when(mapper.selectByLeaseToken(any())).thenAnswer(invocation -> {
            outbox.setLeaseToken(invocation.getArgument(0));
            return outbox;
        });

        Optional<DeliveryOutbox> claimed = service.claimNext("worker-01", Duration.ofSeconds(30));

        assertTrue(claimed.isPresent());
        assertEquals(36, token.getValue().length());
        assertEquals(token.getValue(), claimed.orElseThrow().getLeaseToken());
    }

    @Test
    void noCandidateReturnsEmptyWithoutLeaseLookup() {
        when(mapper.selectClaimCandidateForUpdate("INTERNAL_DISPATCHER")).thenReturn(null);

        assertFalse(service.claimNext("worker-01", Duration.ofSeconds(30)).isPresent());

        verify(mapper, never()).selectByLeaseToken(any());
        verify(mapper, never()).claimById(anyLong(), any(), any(), any(), anyLong());
    }

    @Test
    void staleWorkerCannotCompleteOrRetry() {
        when(mapper.markDispatched(7L, "00000000-0000-0000-0000-000000000001")).thenReturn(0);
        when(mapper.scheduleRetry(eq(7L), eq("00000000-0000-0000-0000-000000000001"),
            any(Date.class), any())).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> service.markDispatched(
            7L, "00000000-0000-0000-0000-000000000001"));
        assertThrows(IllegalStateException.class, () -> service.scheduleRetry(
            7L, "00000000-0000-0000-0000-000000000001", new Date(), "retry"));
    }

    @Test
    void validatesLeaseBoundsAndSanitizesStoredError() {
        assertThrows(IllegalArgumentException.class,
            () -> service.claimNext("bad owner/", Duration.ofSeconds(30)));
        assertThrows(IllegalArgumentException.class,
            () -> service.claimNext("worker-01", Duration.ofMillis(500)));
        assertThrows(IllegalArgumentException.class,
            () -> service.claimNext("worker-01", Duration.ofSeconds(901)));
        when(mapper.markDead(eq(7L), eq("00000000-0000-0000-0000-000000000001"), any()))
            .thenReturn(1);

        service.markDead(7L, "00000000-0000-0000-0000-000000000001",
            "line1\nline2 token=abc123 Authorization: Bearer-value");

        verify(mapper).markDead(7L, "00000000-0000-0000-0000-000000000001",
            "line1 line2 token=[REDACTED] Authorization=[REDACTED]");
    }
}
