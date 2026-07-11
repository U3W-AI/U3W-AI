package com.wx.fbsir.business.smartbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.fbsir.business.smartbot.dto.SmartBotContentArtifactPayload;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartBotContentArtifactProjectorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SmartBotContentArtifactProjector projector =
        new SmartBotContentArtifactProjector(objectMapper);

    @Test
    void textProjectionRetainsOnlyTheWhitelistedUserContent() throws Exception {
        JsonNode callback = objectMapper.readTree("""
            {"msgid":"msg-01","aibotid":"bot-01","from":{"userid":"opaque-user"},
             "chatid":"chat-01","response_url":"https://temporary.example.invalid/response",
             "msgtype":"text","text":{"content":"safe user content","ignored":"drop-me"}}
            """);

        SmartBotContentArtifactPayload payload = projector.project(callback, "text").orElseThrow();
        try {
            String projected = new String(payload.copyContentBytes(), StandardCharsets.UTF_8);
            assertTrue(projected.contains("safe user content"));
            assertTrue(projected.contains("UNTRUSTED_USER_CONTENT"));
            assertFalse(projected.contains("temporary.example.invalid"));
            assertFalse(projected.contains("opaque-user"));
            assertFalse(projected.contains("chat-01"));
            assertFalse(projected.contains("ignored"));
        } finally {
            payload.clear();
        }
    }

    @Test
    void mediaBearingMessagesRemainUnpersistedUntilASeparateIntakeExists() throws Exception {
        JsonNode callback = objectMapper.readTree("""
            {"msgtype":"file","file":{"url":"https://temporary.example.invalid/file"}}
            """);

        assertTrue(projector.project(callback, "file").isEmpty());
        assertTrue(projector.project(callback, "mixed").isEmpty());
    }
}
