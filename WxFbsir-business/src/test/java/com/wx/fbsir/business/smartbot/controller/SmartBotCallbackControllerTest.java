package com.wx.fbsir.business.smartbot.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.fbsir.business.interviewbot.utils.AesException;
import com.wx.fbsir.business.interviewbot.utils.WXBizJsonMsgCrypt;
import com.wx.fbsir.business.smartbot.service.SmartBotCallbackAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SmartBotCallbackControllerTest {

    private SmartBotCallbackAdapter adapter;
    private SmartBotCallbackController controller;

    @BeforeEach
    void setUp() {
        adapter = mock(SmartBotCallbackAdapter.class);
        controller = new SmartBotCallbackController(adapter);
    }

    @Test
    void successfulPostReturnsEncryptedJsonContentType() throws Exception {
        when(adapter.acceptCallback("callback_key_it_01", "signature", "timestamp", "nonce", "body"))
            .thenReturn("{\"encrypt\":\"ciphertext\"}");

        ResponseEntity<String> response = controller.accept(
            "callback_key_it_01", "signature", "timestamp", "nonce", "body");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
        assertEquals("{\"encrypt\":\"ciphertext\"}", response.getBody());
    }

    @Test
    void protocolAndBindingFailuresAreNotAcknowledgedAsSuccess() throws Exception {
        when(adapter.acceptCallback("bad_protocol_key", "signature", "timestamp", "nonce", "body"))
            .thenThrow(new IllegalArgumentException("invalid protocol"));
        when(adapter.acceptCallback("forbidden_key_01", "signature", "timestamp", "nonce", "body"))
            .thenThrow(new SecurityException("unknown binding"));

        assertEquals(HttpStatus.BAD_REQUEST, controller.accept(
            "bad_protocol_key", "signature", "timestamp", "nonce", "body").getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, controller.accept(
            "forbidden_key_01", "signature", "timestamp", "nonce", "body").getStatusCode());
    }

    @Test
    void transientInternalFailureReturnsRetryableStatus() throws Exception {
        when(adapter.acceptCallback("callback_key_it_01", "signature", "timestamp", "nonce", "body"))
            .thenThrow(new IllegalStateException("database unavailable"));

        ResponseEntity<String> response = controller.accept(
            "callback_key_it_01", "signature", "timestamp", "nonce", "body");

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("", response.getBody());
    }

    @Test
    void invalidSignatureIsRejectedInsteadOfAcknowledged() throws Exception {
        WXBizJsonMsgCrypt crypt = new WXBizJsonMsgCrypt(
            "test-token", "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG", "");
        String encrypted = crypt.EncryptMsg("{\"msgtype\":\"text\"}", "timestamp", "nonce");
        JsonNode envelope = new ObjectMapper().readTree(encrypted);
        AesException invalidSignature = assertThrows(AesException.class,
            () -> crypt.DecryptMsg("invalid-signature", "timestamp", "nonce", encrypted));
        when(adapter.acceptCallback("callback_key_it_01", "invalid-signature", "timestamp", "nonce", encrypted))
            .thenThrow(invalidSignature);

        ResponseEntity<String> response = controller.accept(
            "callback_key_it_01", "invalid-signature", "timestamp", "nonce", encrypted);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("", response.getBody());
        assertEquals("timestamp", envelope.path("timestamp").asText());
    }
}
