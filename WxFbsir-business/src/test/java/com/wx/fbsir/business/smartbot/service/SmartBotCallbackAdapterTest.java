package com.wx.fbsir.business.smartbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.fbsir.business.interviewbot.utils.WXBizJsonMsgCrypt;
import com.wx.fbsir.business.smartbot.dto.ResolvedBotBinding;
import com.wx.fbsir.business.smartbot.dto.SmartBotInboundEnvelope;
import com.wx.fbsir.business.smartbot.dto.SmartBotIngressResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmartBotCallbackAdapterTest {

    private static final String TOKEN = "test-token";
    private static final String AES_KEY = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";
    private static final String TIMESTAMP = "1783700000";
    private static final String NONCE = "nonce-01";

    private BotBindingResolver bindingResolver;
    private SmartBotIngressService ingressService;
    private ObjectMapper objectMapper;
    private SmartBotCallbackAdapter adapter;

    @BeforeEach
    void setUp() {
        bindingResolver = mock(BotBindingResolver.class);
        ingressService = mock(SmartBotIngressService.class);
        objectMapper = new ObjectMapper();
        adapter = new SmartBotCallbackAdapter(bindingResolver, ingressService, objectMapper);
    }

    @Test
    void decryptsNormalizesPersistsAndReturnsStableFinalStream() throws Exception {
        ResolvedBotBinding binding = binding();
        when(bindingResolver.resolve("bot_callback_key_01")).thenReturn(binding);
        when(ingressService.accept(eq(binding), any())).thenReturn(new SmartBotIngressResult(
            true, 101L, "trace-01", "run-01", "stable-stream-01", 11L, 21L, 31L));

        String decryptedPayload = "{\"msgid\":\"msg-01\",\"aibotid\":\"AIBOT-01\","
            + "\"from\":{\"userid\":\"opaque-user-01\"},\"chattype\":\"single\","
            + "\"chatid\":\"chat-01\",\"msgtype\":\"text\","
            + "\"text\":{\"content\":\"message body must not persist\"}}";
        WXBizJsonMsgCrypt crypt = new WXBizJsonMsgCrypt(TOKEN, AES_KEY, "");
        String encryptedRequest = crypt.EncryptMsg(decryptedPayload, TIMESTAMP, NONCE);
        JsonNode requestEnvelope = objectMapper.readTree(encryptedRequest);

        String encryptedResponse = adapter.acceptCallback("bot_callback_key_01",
            requestEnvelope.path("msgsignature").asText(), TIMESTAMP, NONCE, encryptedRequest);

        ArgumentCaptor<SmartBotInboundEnvelope> captor =
            ArgumentCaptor.forClass(SmartBotInboundEnvelope.class);
        verify(ingressService).accept(eq(binding), captor.capture());
        SmartBotInboundEnvelope normalized = captor.getValue();
        assertEquals("msg-01", normalized.getMsgId());
        assertEquals("opaque-user-01", normalized.getOpaqueSenderId());
        assertEquals(64, normalized.getPayloadHash().length());
        assertFalse(normalized.toString().contains("opaque-user-01"));
        assertFalse(normalized.toString().contains("chat-01"));
        assertFalse(normalized.toString().contains("msg-01"));

        JsonNode responseEnvelope = objectMapper.readTree(encryptedResponse);
        String responsePlaintext = crypt.DecryptMsg(
            responseEnvelope.path("msgsignature").asText(),
            responseEnvelope.path("timestamp").asText(),
            responseEnvelope.path("nonce").asText(), encryptedResponse);
        JsonNode response = objectMapper.readTree(responsePlaintext);
        assertEquals("stream", response.path("msgtype").asText());
        assertEquals("stable-stream-01", response.path("stream").path("id").asText());
        assertTrue(response.path("stream").path("finish").asBoolean());
        assertTrue(response.path("stream").path("content").asText().contains("编排队列"));
    }

    private ResolvedBotBinding binding() {
        return new ResolvedBotBinding(7L, "bot_callback_key_01", "AIBOT-01", 11L,
            "CALLBACK", 1, TOKEN, AES_KEY);
    }
}
