package com.wx.fbsir.business.board.attribution.service;

import com.wx.fbsir.business.board.attribution.config.IndependentBoardAttributionProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IndependentBoardAttributionReadbackAdmissionTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-23T08:40:00Z"), ZoneOffset.UTC);

    @Test
    void enforcesConcurrentBudgetAndReleasesExactlyOnce() {
        IndependentBoardAttributionProperties properties = properties();
        properties.setAuthoritativeReadbackMaximumConcurrent(1);
        IndependentBoardAttributionReadbackAdmission admission =
                new IndependentBoardAttributionReadbackAdmission(
                        properties, CLOCK);

        IndependentBoardAttributionReadbackAdmission.Lease first =
                admission.acquire("readback-k1");
        assertThrows(
                IndependentBoardAttributionReadbackUnavailableException.class,
                () -> admission.acquire("readback-k1"));
        first.close();
        first.close();
        assertDoesNotThrow(() -> {
            try (var ignored = admission.acquire("readback-k1")) {
                // Lease closes after the bounded read.
            }
        });
    }

    @Test
    void enforcesPerKeyMinuteBudgetWithoutStoringRequestIdentifiers() {
        IndependentBoardAttributionProperties properties = properties();
        properties.setAuthoritativeReadbackMaximumRequestsPerMinute(2);
        IndependentBoardAttributionReadbackAdmission admission =
                new IndependentBoardAttributionReadbackAdmission(
                        properties, CLOCK);

        try (var ignored = admission.acquire("readback-k1")) {
            // First request.
        }
        try (var ignored = admission.acquire("readback-k1")) {
            // Second request.
        }
        assertThrows(
                IndependentBoardAttributionReadbackUnavailableException.class,
                () -> admission.acquire("readback-k1"));
        assertDoesNotThrow(() -> {
            try (var ignored = admission.acquire("readback-k0")) {
                // Independent verified key budget.
            }
        });
    }

    private IndependentBoardAttributionProperties properties() {
        IndependentBoardAttributionProperties properties =
                new IndependentBoardAttributionProperties();
        properties.setAuthoritativeReadbackMaximumConcurrent(4);
        properties.setAuthoritativeReadbackMaximumRequestsPerMinute(120);
        return properties;
    }
}
