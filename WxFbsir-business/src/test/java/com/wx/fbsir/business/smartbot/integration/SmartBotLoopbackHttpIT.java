package com.wx.fbsir.business.smartbot.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.fbsir.business.interviewbot.utils.WXBizJsonMsgCrypt;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Explicit opt-in protocol test against an already running loopback Admin. */
class SmartBotLoopbackHttpIT {
    @Test
    void encryptedCallbackReachesDurableIngressAndReturnsDecryptableFinalStream() throws Exception {
        String baseUrl = required("SMARTBOT_LOOPBACK_URL");
        if (!baseUrl.matches("^http://(127\\.0\\.0\\.1|localhost):\\d+$")) {
            throw new IllegalStateException("loopback test only accepts a local HTTP Admin URL");
        }
        String callbackKey = required("SMARTBOT_LOOPBACK_CALLBACK_KEY");
        String token = required("SMARTBOT_LOOPBACK_TOKEN");
        String aesKey = required("SMARTBOT_LOOPBACK_AES_KEY");
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String msgId = "loopback-" + UUID.randomUUID();
        String marker = "U3W闭环实测-" + UUID.randomUUID().toString().substring(0, 8);
        String payload = "{\"msgid\":\"" + msgId + "\",\"aibotid\":\"AIBOT-U3W-LOCAL\","
            + "\"from\":{\"userid\":\"opaque-user-local\"},\"chattype\":\"single\","
            + "\"msgtype\":\"text\",\"text\":{\"content\":\"" + marker + "\"}}";
        WXBizJsonMsgCrypt crypt = new WXBizJsonMsgCrypt(token, aesKey, "");
        String encrypted = crypt.EncryptMsg(payload, timestamp, nonce);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode requestEnvelope = mapper.readTree(encrypted);
        URI uri = URI.create(baseUrl + "/api/smartbot/wecom/" + callbackKey
            + "?msg_signature=" + requestEnvelope.path("msgsignature").asText()
            + "&timestamp=" + timestamp + "&nonce=" + nonce);
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(encrypted))
            .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(
            request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        JsonNode responseEnvelope = mapper.readTree(response.body());
        String plaintext = crypt.DecryptMsg(responseEnvelope.path("msgsignature").asText(),
            responseEnvelope.path("timestamp").asText(), responseEnvelope.path("nonce").asText(),
            response.body());
        JsonNode stream = mapper.readTree(plaintext);
        assertEquals("stream", stream.path("msgtype").asText());
        assertTrue(stream.path("stream").path("finish").asBoolean());
        assertTrue(stream.path("stream").path("id").asText().length() > 10);
        System.out.println("SMARTBOT_LOOPBACK_MARKER=" + marker);
    }

    private String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for the explicit loopback test");
        }
        return value;
    }
}
