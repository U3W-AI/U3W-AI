package com.wx.fbsir.engine.playwright.config;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 统一构造 Chromium 启动参数和上下文超时，避免持久会话与临时会话配置漂移。
 */
public final class BrowserLaunchOptionsFactory {

    private BrowserLaunchOptionsFactory() {
    }

    public static List<String> buildArgs(PlaywrightProperties.BrowserConfig config) {
        List<String> args = new ArrayList<>();

        if (config.isNoSandbox()) {
            args.add("--no-sandbox");
        }

        args.add("--disable-dev-shm-usage");
        args.add("--disable-extensions");
        args.add("--disable-plugins");

        if (config.isDisableGpu()) {
            args.add("--disable-gpu");
        }
        if (config.isDisableImages()) {
            args.add("--blink-settings=imagesEnabled=false");
        }

        args.add("--disable-background-timer-throttling");
        args.add("--disable-backgrounding-occluded-windows");
        args.add("--disable-renderer-backgrounding");
        args.add("--disable-background-networking");
        args.add("--disable-sync");
        args.add("--no-first-run");
        args.add("--disable-default-apps");
        args.add("--memory-pressure-off");
        args.add("--js-flags=--max-old-space-size=256");

        return Collections.unmodifiableList(args);
    }

    public static BrowserType.LaunchOptions createLaunchOptions(
            PlaywrightProperties properties, boolean headless) {
        PlaywrightProperties.BrowserConfig config = properties.getBrowser();
        return new BrowserType.LaunchOptions()
            .setHeadless(headless)
            .setTimeout(config.getLaunchTimeout())
            .setArgs(buildArgs(config));
    }

    public static BrowserType.LaunchPersistentContextOptions createPersistentContextOptions(
            PlaywrightProperties properties, boolean headless) {
        PlaywrightProperties.BrowserConfig config = properties.getBrowser();
        return new BrowserType.LaunchPersistentContextOptions()
            .setHeadless(headless)
            .setTimeout(config.getLaunchTimeout())
            .setViewportSize(config.getViewportWidth(), config.getViewportHeight())
            .setArgs(buildArgs(config));
    }

    public static void configureContextTimeouts(
            BrowserContext context, PlaywrightProperties properties) {
        PlaywrightProperties.BrowserConfig config = properties.getBrowser();
        context.setDefaultTimeout(config.getActionTimeout());
        context.setDefaultNavigationTimeout(config.getNavigationTimeout());
    }
}
