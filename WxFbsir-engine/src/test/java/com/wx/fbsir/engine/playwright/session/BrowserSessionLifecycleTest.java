package com.wx.fbsir.engine.playwright.session;

import com.microsoft.playwright.BrowserContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BrowserSessionLifecycleTest {

    @Test
    void closeReleasesLeaseWithoutInvalidatingPersistentSession() {
        BrowserContext context = mock(BrowserContext.class);
        BrowserSession session = new BrowserSession(
            "user-1", "wecom", context, true, true, 60_000);
        AtomicInteger releases = new AtomicInteger();
        session.setOnClose(() -> {
            if (session.releaseIfAcquired()) {
                releases.incrementAndGet();
            }
        });

        assertTrue(session.acquire("task-1"));
        session.close();
        session.close();

        assertFalse(session.isInUse());
        assertTrue(session.isValid());
        assertTrue(session.acquire("task-2"));
        session.close();
        assertTrue(releases.get() == 2);
    }

    @Test
    void destroyIsIdempotentAndClosesContextOnce() {
        BrowserContext context = mock(BrowserContext.class);
        when(context.pages()).thenReturn(List.of());
        BrowserSession session = new BrowserSession(
            "user-1", "temp", context, false, true, 60_000);

        session.destroy();
        session.destroy();

        assertFalse(session.isValid());
        verify(context).close();
    }

    @Test
    void absentInstanceIdRemainsAbsentForPoolKeyReconstruction() {
        BrowserContext context = mock(BrowserContext.class);
        BrowserSession session = new BrowserSession(
            "user-1", "wecom", context, true, true, 60_000);

        assertTrue(session.getInstanceId() == null);
    }
}
