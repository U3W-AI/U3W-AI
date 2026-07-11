package com.wx.fbsir.business.airobotmessage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Instant;
import java.security.SecureRandom;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in action test for a dedicated WeCom test group.
 *
 * <p>The Webhook URL is supplied only through the process environment and is never logged or
 * persisted. Provider acceptance is deliberately not described as delivery, visibility or read.
 */
@EnabledIfEnvironmentVariable(named = "U3W_WECOM_LIVE_TEST", matches = "true")
class WecomWebhookLiveIT {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void dedicatedGroupAcceptsMessageThroughU3wTransport() throws Exception {
        String webhookUrl = System.getenv("U3W_WECOM_WEBHOOK_URL");
        String runIdPrefix = System.getenv().getOrDefault("U3W_WECOM_RUN_ID", Instant.now().toString());
        int startIndex = Integer.parseInt(System.getenv().getOrDefault("U3W_WECOM_START_INDEX", "1"));
        int messageCount = Integer.parseInt(System.getenv().getOrDefault("U3W_WECOM_MESSAGE_COUNT", "1"));
        assertTrue(webhookUrl != null && !webhookUrl.isBlank(), "live test URL is required");
        assertTrue(messageCount >= 1 && messageCount <= 10, "live test is capped at ten messages");
        new WebhookSecretCodec("v1", Map.of("v1", "live-test-validation-key-is-never-persisted"),
                new SecureRandom()).validate(webhookUrl);

        WecomWebhookTransport transport = new JdkWecomWebhookTransport();
        for (int offset = 0; offset < messageCount; offset++) {
            String runId = messageCount == 1 ? runIdPrefix
                    : runIdPrefix + "-" + String.format("%03d", startIndex + offset);
            JsonNode payload = objectMapper.createObjectNode()
                    .put("msgtype", "markdown")
                    .set("markdown", objectMapper.createObjectNode().put("content",
                            "**U3W Webhook 受控联测**\n"
                                    + "> 阶段：ACTION / PROVIDER_ACCEPTED 验证\n"
                                    + "> 标识：`" + runId + "`\n"
                                    + "> 说明：本消息仅投放到专用测试群，不代表已读或业务归因完成。"));

            WecomWebhookTransport.TransportResult result = transport
                    .send(webhookUrl, objectMapper.writeValueAsString(payload));
            JsonNode response = objectMapper.readTree(result.responseBody());

            assertEquals(200, result.httpStatus());
            assertEquals(0, response.path("errcode").asInt(Integer.MIN_VALUE));
            if (offset + 1 < messageCount) Thread.sleep(350L);
        }
    }
}
