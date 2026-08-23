package com.wx.fbsir.business.board.attribution.service;

import com.wx.fbsir.business.board.attribution.config.IndependentBoardAttributionProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

/** Process-local amplification fence; it stores no identifier beyond key id. */
@Component
@ConditionalOnProperty(
        prefix = "fbsir.independent-board.attribution",
        name = "authoritative-readback-enabled",
        havingValue = "true",
        matchIfMissing = false)
public class IndependentBoardAttributionReadbackAdmission {
    private final IndependentBoardAttributionProperties properties;
    private final Clock clock;
    private final Semaphore concurrent;
    private final Map<String, MinuteBudget> budgets = new HashMap<>();

    public IndependentBoardAttributionReadbackAdmission(
            IndependentBoardAttributionProperties properties) {
        this(properties, Clock.systemUTC());
    }

    IndependentBoardAttributionReadbackAdmission(
            IndependentBoardAttributionProperties properties,
            Clock clock) {
        this.properties = properties;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.concurrent = new Semaphore(
                properties.getAuthoritativeReadbackMaximumConcurrent(), true);
    }

    public Lease acquire(String signerKeyId) {
        if (!withinMinuteBudget(signerKeyId)
                || !concurrent.tryAcquire()) {
            throw new IndependentBoardAttributionReadbackUnavailableException(
                    "readback_admission_budget_exhausted");
        }
        return new Lease(concurrent);
    }

    private synchronized boolean withinMinuteBudget(String signerKeyId) {
        long minute = Math.floorDiv(clock.millis(), 60_000L);
        MinuteBudget current = budgets.get(signerKeyId);
        if (current == null || current.minute() != minute) {
            budgets.put(signerKeyId, new MinuteBudget(minute, 1));
            return true;
        }
        if (current.count() >= properties
                .getAuthoritativeReadbackMaximumRequestsPerMinute()) {
            return false;
        }
        budgets.put(signerKeyId,
                new MinuteBudget(minute, current.count() + 1));
        return true;
    }

    private record MinuteBudget(long minute, int count) {
    }

    public static final class Lease implements AutoCloseable {
        private final Semaphore semaphore;
        private boolean closed;

        private Lease(Semaphore semaphore) {
            this.semaphore = semaphore;
        }

        @Override
        public synchronized void close() {
            if (!closed) {
                closed = true;
                semaphore.release();
            }
        }
    }
}
