package com.wx.fbsir.engine.playwright.util;

import com.wx.fbsir.engine.config.EngineProperties;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenshotUploadClientTest {

    @Test
    void onlyAcceptsTopLevelSuccessWithNonBlankUrl() throws Exception {
        ScreenshotUploadClient client = new ScreenshotUploadClient(new EngineProperties());

        ScreenshotUploadClient.UploadResult nestedSuccess = parse(client,
            "{\"success\":false,\"detail\":{\"success\":true},\"message\":\"拒绝\"}");
        ScreenshotUploadClient.UploadResult missingUrl = parse(client,
            "{\"success\":true,\"fileName\":\"a.png\"}");
        ScreenshotUploadClient.UploadResult valid = parse(client,
            "{\"success\":true,\"url\":\"https://example.test/a.png\",\"fileName\":\"a.png\"}");

        assertFalse(nestedSuccess.isSuccess());
        assertFalse(missingUrl.isSuccess());
        assertTrue(valid.isSuccess());
    }

    @Test
    void sendsCredentialAndHostBindingHeaders() throws Exception {
        AtomicReference<String> token = new AtomicReference<>();
        AtomicReference<String> hostId = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/engine/screenshot/upload", exchange -> {
            token.set(exchange.getRequestHeaders().getFirst("X-FBSir-Engine-Token"));
            hostId.set(exchange.getRequestHeaders().getFirst("X-FBSir-Engine-Id"));
            exchange.getRequestBody().readAllBytes();
            byte[] body = "{\"success\":true,\"url\":\"http://example.test/a.png\",\"fileName\":\"a.png\"}"
                .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            EngineProperties properties = new EngineProperties();
            properties.setHostId("engine-1");
            properties.setEngineToken("engine-token-at-least-32-characters-long");
            ScreenshotUploadClient client = new ScreenshotUploadClient(properties);
            Field baseUrl = ScreenshotUploadClient.class.getDeclaredField("adminBaseUrl");
            baseUrl.setAccessible(true);
            baseUrl.set(client, "http://127.0.0.1:" + server.getAddress().getPort());

            ScreenshotUploadClient.UploadResult result =
                client.uploadScreenshot("user-1", "capture", new byte[]{1, 2, 3});

            assertTrue(result.isSuccess());
            assertTrue("engine-token-at-least-32-characters-long".equals(token.get()));
            assertTrue("engine-1".equals(hostId.get()));
        } finally {
            server.stop(0);
        }
    }

    private ScreenshotUploadClient.UploadResult parse(ScreenshotUploadClient client, String json) throws Exception {
        Method method = ScreenshotUploadClient.class.getDeclaredMethod("parseResponse", String.class);
        method.setAccessible(true);
        return (ScreenshotUploadClient.UploadResult) method.invoke(client, json);
    }
}
