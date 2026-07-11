package com.wx.fbsir.business.websocket.controller;

import com.wx.fbsir.business.websocket.server.EngineSessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EngineUploadControllerTest {

    @TempDir
    Path tempDir;

    private EngineUploadController controller;
    private EngineSessionManager sessionManager;

    @BeforeEach
    void setUp() throws Exception {
        sessionManager = mock(EngineSessionManager.class);
        controller = new EngineUploadController(sessionManager);
        setField("uploadPath", tempDir.toString());
        setField("resourcePrefix", "http://localhost:8080");
        when(sessionManager.isEngineOnline("engine-1")).thenReturn(true);
    }

    @Test
    void rejectsTraversalUserId() throws Exception {
        Map<String, Object> body = controller.uploadScreenshot(
            "engine-1", "..\\outside", "ignored", png()).getBody();

        assertNotNull(body);
        assertEquals(false, body.get("success"));
        assertFalse(Files.exists(tempDir.getParent().resolve("outside")));
    }

    @Test
    void rejectsActiveOrFakeImageContent() {
        MockMultipartFile html = new MockMultipartFile(
            "file", "payload.html", "text/html", "<script>alert(1)</script>".getBytes());

        Map<String, Object> body = controller.uploadScreenshot(
            "engine-1", "user-1", "payload", html).getBody();

        assertNotNull(body);
        assertEquals(false, body.get("success"));
    }

    @Test
    void reencodesValidImageToServerGeneratedPng() throws Exception {
        Map<String, Object> body = controller.uploadScreenshot(
            "engine-1", "user-1", "..\\ignored", png()).getBody();

        assertNotNull(body);
        assertEquals(true, body.get("success"));
        String fileName = body.get("fileName").toString();
        assertTrue(fileName.matches("[0-9a-f]{32}\\.png"));
        Path stored;
        try (var files = Files.walk(tempDir.resolve("engine/user-1"))) {
            stored = files.filter(Files::isRegularFile).findFirst().orElseThrow();
        }
        assertNotNull(ImageIO.read(stored.toFile()));
    }

    @Test
    void batchAllFailedNeverReturnsSuccess() {
        MockMultipartFile fake = new MockMultipartFile(
            "files", "fake.svg", "image/svg+xml", "<svg/>".getBytes());

        Map<String, Object> body = controller.batchUploadScreenshots(
            "engine-1", "user-1", new MockMultipartFile[]{fake}).getBody();

        assertNotNull(body);
        assertEquals(false, body.get("success"));
        assertEquals("ALL_UPLOADS_FAILED", body.get("code"));
        assertEquals(1, body.get("failedCount"));
    }

    @Test
    void defaultUploadPathPreservesHistoricalEngineDataDirectory() throws Exception {
        Field field = EngineUploadController.class.getDeclaredField("uploadPath");

        assertEquals("${fbsir.profile:/data/wxfbsir/uploadPath}",
            field.getAnnotation(Value.class).value());
    }

    private MockMultipartFile png() throws Exception {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, Color.BLUE.getRGB());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return new MockMultipartFile("file", "source.png", "image/png", output.toByteArray());
    }

    private void setField(String name, Object value) throws Exception {
        Field field = EngineUploadController.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(controller, value);
    }
}
