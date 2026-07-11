package com.wx.fbsir.engine.playwright.util;

import com.wx.fbsir.engine.config.EngineProperties;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

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

    private ScreenshotUploadClient.UploadResult parse(ScreenshotUploadClient client, String json) throws Exception {
        Method method = ScreenshotUploadClient.class.getDeclaredMethod("parseResponse", String.class);
        method.setAccessible(true);
        return (ScreenshotUploadClient.UploadResult) method.invoke(client, json);
    }
}
