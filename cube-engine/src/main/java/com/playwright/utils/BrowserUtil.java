package com.playwright.utils;

/**
 * @author 优立方
 * @version JDK 17
 * @date 2025年01月14日 10:57
 */

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;
import java.util.Arrays;

@Component
public class BrowserUtil {

    @Value("${cube.datadir}")
    private String userDataDir;


    /**
     * 启动持久化浏览器上下文
     *
     * @return BrowserContext 持久化浏览器上下文
     */
    public BrowserContext createPersistentBrowserContext(boolean isHead, String userId, String name) {
        Playwright playwright = Playwright.create();
        BrowserType browserType = playwright.chromium();

        // 配置启动选项
        BrowserType.LaunchPersistentContextOptions options = new BrowserType.LaunchPersistentContextOptions()
                .setHeadless(isHead)
                .setViewportSize(null);
        // 启动持久化上下文
        BrowserContext context = browserType.launchPersistentContext(Paths.get(userDataDir + "/" + name + "/" + userId), options);
        // 授予剪贴板读写权限
        context.grantPermissions(Arrays.asList("clipboard-read", "clipboard-write"));
        return context;
    }
}
