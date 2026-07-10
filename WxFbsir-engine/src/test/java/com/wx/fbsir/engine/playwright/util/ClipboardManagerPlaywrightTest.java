package com.wx.fbsir.engine.playwright.util;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClipboardManagerPlaywrightTest {

    private static Playwright playwright;
    private static Browser browser;
    private static HttpServer server;
    private static String baseUrl;

    @BeforeAll
    static void startBrowserAndFixtureServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = "<html><body><textarea id='target'></textarea></body></html>"
                .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        playwright = Playwright.create();
        browser = playwright.chromium().launch(
            new com.microsoft.playwright.BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    static void stopBrowserAndFixtureServer() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void writesReadsAndPastesThroughRealBrowserClipboard() {
        BrowserContext context = browser.newContext();
        context.grantPermissions(List.of("clipboard-read", "clipboard-write"),
            new BrowserContext.GrantPermissionsOptions().setOrigin(baseUrl));
        Page page = context.newPage();
        page.navigate(baseUrl);
        ClipboardManager clipboard = new ClipboardManager();
        String expected = "const answer = '企微自动化';\n{" + "\"ok\":true}";

        assertTrue(clipboard.write(page, expected));
        assertEquals(expected, clipboard.read(page));
        assertTrue(clipboard.pasteToElement(page, "#target", expected));
        assertEquals(expected, page.locator("#target").inputValue());

        context.close();
    }
}
