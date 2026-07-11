package com.wx.fbsir.business.smartbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.fbsir.business.smartbot.domain.OrchestrationRun;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** Whitelist-only projection from the encrypted callback artifact to a Webhook message. */
@Component
public class SmartBotWebhookPayloadRenderer {
    private static final int WECOM_MARKDOWN_UTF8_LIMIT_BYTES = 4096;
    private static final int RESERVED_WRAPPER_UTF8_BYTES = 256;
    static final int MAX_MESSAGE_UTF8_BYTES =
        WECOM_MARKDOWN_UTF8_LIMIT_BYTES - RESERVED_WRAPPER_UTF8_BYTES;
    private final SmartBotInputArtifactService artifactService;
    private final ObjectMapper objectMapper;

    public SmartBotWebhookPayloadRenderer(SmartBotInputArtifactService artifactService,
                                          ObjectMapper objectMapper) {
        this.artifactService = artifactService;
        this.objectMapper = objectMapper;
    }

    public String render(OrchestrationRun run, String inputRef, String contentHash) {
        String json = artifactService.readDecryptedForWebhook(run, inputRef, contentHash);
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || root.path("schemaVersion").asInt(-1) != 1
                    || !SmartBotInputArtifactService.PURPOSE.equals(root.path("kind").asText(null))) {
                throw new IllegalStateException("smartbot artifact schema is not supported");
            }
            String msgType = root.path("msgType").asText(null);
            JsonNode content = root.path("content");
            JsonNode value = "text".equals(msgType) ? content.path("text")
                : "voice".equals(msgType) ? content.path("voiceTranscript") : null;
            if (value == null || !value.isTextual() || value.asText().isBlank()) {
                throw new IllegalStateException("smartbot artifact has no supported message content");
            }
            String message = value.asText();
            if (message.getBytes(StandardCharsets.UTF_8).length > MAX_MESSAGE_UTF8_BYTES) {
                throw new IllegalStateException("smartbot message exceeds Webhook UTF-8 byte limit");
            }
            return message;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("smartbot artifact cannot be rendered", e);
        }
    }
}
