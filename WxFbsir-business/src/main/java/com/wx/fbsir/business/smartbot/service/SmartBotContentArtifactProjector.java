package com.wx.fbsir.business.smartbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wx.fbsir.business.smartbot.dto.SmartBotContentArtifactPayload;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Converts only supported, user-content fields into the internal artifact
 * schema. It deliberately does not retain raw callback JSON, identity fields,
 * response_url, or temporary media URLs.
 */
@Component
public class SmartBotContentArtifactProjector {

    public static final String INPUT_KIND = "smartbot.input.message.v1";
    private static final int MAX_CONTENT_BYTES = 256 * 1024;

    private final ObjectMapper objectMapper;

    public SmartBotContentArtifactProjector(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * The initial safe slice only accepts text and a supplied voice transcript.
     * Media-bearing formats remain deliberately unpersisted until an isolated
     * media intake pipeline can replace provider temporary URLs with internal refs.
     */
    public Optional<SmartBotContentArtifactPayload> project(JsonNode callback, String msgType) {
        String field;
        String outputField;
        if ("text".equals(msgType)) {
            field = "text";
            outputField = "text";
        } else if ("voice".equals(msgType)) {
            field = "voice";
            outputField = "voiceTranscript";
        } else {
            return Optional.empty();
        }

        JsonNode value = callback.path(field).path("content");
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("智能机器人消息内容不能为空");
        }

        ObjectNode artifact = objectMapper.createObjectNode();
        artifact.put("schemaVersion", 1);
        artifact.put("sourceType", "WECOM_SMARTBOT_CALLBACK");
        artifact.put("kind", INPUT_KIND);
        artifact.put("msgType", msgType);
        artifact.put("classification", "UNTRUSTED_USER_CONTENT");
        ObjectNode content = artifact.putObject("content");
        content.put(outputField, value.asText());

        try {
            String canonicalJson = objectMapper.writeValueAsString(artifact);
            SmartBotContentArtifactPayload payload =
                SmartBotContentArtifactPayload.ofUtf8Json(INPUT_KIND, canonicalJson);
            if (payload.getContentSize() > MAX_CONTENT_BYTES) {
                payload.clear();
                throw new IllegalArgumentException("智能机器人消息内容超限");
            }
            return Optional.of(payload);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("内容工件规范化失败", e);
        }
    }
}
