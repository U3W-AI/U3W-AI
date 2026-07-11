package com.wx.fbsir.engine.playwright.config;

import com.microsoft.playwright.BrowserContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BrowserLaunchOptionsFactoryTest {

    @Test
    void appliesSecurityAndPerformanceFlagsFromOneSource() {
        PlaywrightProperties.BrowserConfig config = new PlaywrightProperties.BrowserConfig();
        config.setDisableGpu(true);
        config.setDisableImages(true);
        config.setNoSandbox(false);

        List<String> args = BrowserLaunchOptionsFactory.buildArgs(config);

        assertTrue(args.contains("--disable-gpu"));
        assertTrue(args.contains("--blink-settings=imagesEnabled=false"));
        assertTrue(args.contains("--js-flags=--max-old-space-size=256"));
        assertFalse(args.contains("--no-sandbox"));
        assertFalse(args.contains("--max_old_space_size=256"));
    }

    @Test
    void noSandboxRequiresExplicitOptIn() {
        PlaywrightProperties.BrowserConfig config = new PlaywrightProperties.BrowserConfig();
        config.setNoSandbox(true);

        assertTrue(BrowserLaunchOptionsFactory.buildArgs(config).contains("--no-sandbox"));
    }

    @Test
    void configuresContextTimeoutsWithoutHardCodedDrift() {
        PlaywrightProperties properties = new PlaywrightProperties();
        properties.getBrowser().setActionTimeout(4321);
        properties.getBrowser().setNavigationTimeout(8765);
        BrowserContext context = mock(BrowserContext.class);

        BrowserLaunchOptionsFactory.configureContextTimeouts(context, properties);

        verify(context).setDefaultTimeout(4321);
        verify(context).setDefaultNavigationTimeout(8765);
    }
}
