package com.wx.fbsir.business.smartbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.fbsir.business.smartbot.domain.OrchestrationRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SmartBotWebhookPayloadRendererTest {
    private SmartBotInputArtifactService artifactService;
    private ObjectMapper objectMapper;
    private SmartBotWebhookPayloadRenderer renderer;
    private OrchestrationRun run;

    @BeforeEach
    void setUp() {
        artifactService = mock(SmartBotInputArtifactService.class);
        objectMapper = new ObjectMapper();
        renderer = new SmartBotWebhookPayloadRenderer(artifactService, objectMapper);
        run = new OrchestrationRun();
    }

    @Test
    void acceptsChineseAndEmojiAtUtf8SafeByteLimit() throws Exception {
        String message = "中".repeat(1278) + "😀" + "ab";
        assertEquals(SmartBotWebhookPayloadRenderer.MAX_MESSAGE_UTF8_BYTES,
            message.getBytes(StandardCharsets.UTF_8).length);
        stubArtifact(message);

        assertEquals(message, renderer.render(run, "input-ref", "content-hash"));
    }

    @Test
    void rejectsChineseAndEmojiOneByteOverUtf8SafeByteLimit() throws Exception {
        String message = "中".repeat(1278) + "😀" + "abc";
        assertEquals(SmartBotWebhookPayloadRenderer.MAX_MESSAGE_UTF8_BYTES + 1,
            message.getBytes(StandardCharsets.UTF_8).length);
        stubArtifact(message);

        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> renderer.render(run, "input-ref", "content-hash"));
        assertEquals("smartbot message exceeds Webhook UTF-8 byte limit", error.getMessage());
    }

    private void stubArtifact(String message) throws Exception {
        String json = objectMapper.writeValueAsString(Map.of(
            "schemaVersion", 1,
            "kind", SmartBotInputArtifactService.PURPOSE,
            "msgType", "text",
            "content", Map.of("text", message)));
        when(artifactService.readDecryptedForWebhook(run, "input-ref", "content-hash"))
            .thenReturn(json);
    }
}
