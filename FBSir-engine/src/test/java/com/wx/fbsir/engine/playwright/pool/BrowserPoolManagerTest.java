package com.wx.fbsir.engine.playwright.pool;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.wx.fbsir.engine.playwright.config.PlaywrightProperties;
import com.wx.fbsir.engine.playwright.core.PlaywrightInstancePool;
import com.wx.fbsir.engine.playwright.core.PlaywrightManager;
import com.wx.fbsir.engine.playwright.session.BrowserSession;
import com.wx.fbsir.engine.playwright.util.ClipboardManager;
import com.wx.fbsir.engine.playwright.util.ScreenshotUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BrowserPoolManagerTest {

    @TempDir
    Path tempDir;

    private BrowserPoolManager manager;

    @BeforeEach
    void createManager() {
        PlaywrightProperties properties = new PlaywrightProperties();
        properties.setDataDir(tempDir.toString());
        properties.setDynamicPerformance(false);
        properties.getPool().setMaxSize(1);
        properties.getPool().setAcquireTimeout(100);
        manager = new BrowserPoolManager(
            mock(PlaywrightManager.class),
            mock(PlaywrightInstancePool.class),
            properties,
            mock(ClipboardManager.class),
            mock(ScreenshotUtil.class),
            mock(GlobalBrowserPool.class));
        manager.init();
    }

    @Test
    void busyPersistentSessionStaysMappedInsteadOfBeingDeleted() throws Exception {
        BrowserSession busy = mock(BrowserSession.class);
        when(busy.isValid()).thenReturn(true);
        when(busy.acquire("wecom")).thenReturn(false);
        persistentSessions().put("user-1:wecom", busy);

        assertThrows(RuntimeException.class,
            () -> manager.acquire("user-1", "wecom", true, true));

        assertSame(busy, persistentSessions().get("user-1:wecom"));
    }

    @Test
    void expiredButInUsePersistentSessionIsNeverEvicted() throws Exception {
        BrowserSession busy = mock(BrowserSession.class);
        when(busy.isValid()).thenReturn(false);
        when(busy.isInUse()).thenReturn(true);
        when(busy.acquire("wecom")).thenReturn(false);
        persistentSessions().put("user-1:wecom", busy);

        assertThrows(RuntimeException.class,
            () -> manager.acquire("user-1", "wecom", true, true));

        assertSame(busy, persistentSessions().get("user-1:wecom"));
        assertEquals(1, persistentSessions().size());
    }

    @Test
    void temporaryReleaseIsIdempotentAndRestoresPoolPermit() throws Exception {
        BrowserContext context = mock(BrowserContext.class);
        when(context.pages()).thenReturn(List.of());
        Browser browser = mock(Browser.class);
        when(browser.isConnected()).thenReturn(true);
        BrowserSession session = new BrowserSession(
            "task-1", "temp", browser, context, false, true, 60_000);
        session.acquire("task");

        temporarySessions().put(session.getSessionId(), session);
        activeCount().set(1);
        semaphore().tryAcquire();

        manager.release(session);
        manager.release(session);

        assertEquals(0, activeCount().get());
        assertEquals(1, semaphore().availablePermits());
        assertEquals(0, temporarySessions().size());
        verify(context).close();
    }

    @SuppressWarnings("unchecked")
    private Map<String, BrowserSession> persistentSessions() throws Exception {
        return (ConcurrentHashMap<String, BrowserSession>) field("persistentSessions").get(manager);
    }

    @SuppressWarnings("unchecked")
    private Map<String, BrowserSession> temporarySessions() throws Exception {
        return (ConcurrentHashMap<String, BrowserSession>) field("temporarySessions").get(manager);
    }

    private AtomicInteger activeCount() throws Exception {
        return (AtomicInteger) field("activeCount").get(manager);
    }

    private Semaphore semaphore() throws Exception {
        return (Semaphore) field("semaphore").get(manager);
    }

    private Field field(String name) throws Exception {
        Field field = BrowserPoolManager.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
