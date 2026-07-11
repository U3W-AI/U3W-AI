package com.wx.fbsir.engine.playwright.pool;

import com.wx.fbsir.engine.playwright.config.PlaywrightProperties;
import com.wx.fbsir.engine.playwright.core.PlaywrightManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class GlobalBrowserPoolTest
{
    @Test
    void zeroMinIdleDoesNotLaunchBrowsersDuringEngineStartup()
    {
        PlaywrightProperties properties = new PlaywrightProperties();
        properties.setEnabled(true);
        properties.getPool().setMaxSize(4);
        properties.getPool().setMinIdle(0);
        PlaywrightManager manager = mock(PlaywrightManager.class);

        GlobalBrowserPool pool = new GlobalBrowserPool(manager, properties);
        pool.init();

        assertEquals(0, pool.getAvailableCount());
        assertEquals(0, pool.getTotalBrowsers());
        verifyNoInteractions(manager);
        pool.destroy();
    }
}
