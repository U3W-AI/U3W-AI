package com.wx.fbsir.business.smartbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wx.fbsir.business.interviewbot.utils.WXBizJsonMsgCrypt;
import com.wx.fbsir.business.smartbot.dto.ResolvedBotBinding;
import com.wx.fbsir.business.smartbot.dto.SmartBotInboundEnvelope;
import com.wx.fbsir.business.smartbot.dto.SmartBotIngressResult;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Short-connection protocol adapter. It only verifies/decrypts, normalizes
 * metadata, performs the durable ingress transaction, and returns a final ACK.
 * LLM, HTTP, MCP, Engine, and Playwright work must stay behind the outbox.
 */
@Service
public class SmartBotCallbackAdapter {

    private static final String RECEIVE_ID = "";
    private static final String ACCEPTED_MESSAGE = "已接收，任务已进入福帮手编排队列。";

    private final BotBindingResolver bindingResolver;
    private final SmartBotIngressService ingressService;
    private final ObjectMapper objectMapper;

    public SmartBotCallbackAdapter(BotBindingResolver bindingResolver,
                                   SmartBotIngressService ingressService,
                                   ObjectMapper objectMapper) {
        this.bindingResolver = bindingResolver;
        this.ingressService = ingressService;
        this.objectMapper = objectMapper;
    }

    public String verifyUrl(String callbackKey, String msgSignature, String timestamp,
                            String nonce, String echoStr) throws Exception {
        ResolvedBotBinding binding = bindingResolver.resolve(callbackKey);
        return crypt(binding).VerifyURL(msgSignature, timestamp, nonce, echoStr);
    }

    public String acceptCallback(String callbackKey, String msgSignature, String timestamp,
                                 String nonce, String encryptedBody) throws Exception {
        ResolvedBotBinding binding = bindingResolver.resolve(callbackKey);
        WXBizJsonMsgCrypt crypt = crypt(binding);
        String decrypted = crypt.DecryptMsg(msgSignature, timestamp, nonce, encryptedBody);
        byte[] decryptedBytes = decrypted.getBytes(StandardCharsets.UTF_8);
        JsonNode root = objectMapper.readTree(decryptedBytes);

        SmartBotInboundEnvelope envelope = SmartBotInboundEnvelope.builder()
            .msgId(text(root, "msgid"))
            .aibotId(text(root, "aibotid"))
            .opaqueSenderId(text(root.path("from"), "userid"))
            .chatType(optionalText(root, "chattype"))
            .chatId(optionalText(root, "chatid"))
            .msgType(text(root, "msgtype"))
            .eventType(optionalText(root.path("event"), "eventtype"))
            .payloadHash(sha256(decryptedBytes))
            .build();

        SmartBotIngressResult ingress = ingressService.accept(binding, envelope);
        ObjectNode stream = objectMapper.createObjectNode();
        stream.put("id", ingress.streamId());
        stream.put("finish", true);
        stream.put("content", ACCEPTED_MESSAGE);
        ObjectNode reply = objectMapper.createObjectNode();
        reply.put("msgtype", "stream");
        reply.set("stream", stream);
        return crypt.EncryptMsg(objectMapper.writeValueAsString(reply), timestamp, nonce);
    }

    private WXBizJsonMsgCrypt crypt(ResolvedBotBinding binding) throws Exception {
        return new WXBizJsonMsgCrypt(binding.token(), binding.encodingAesKey(), RECEIVE_ID);
    }

    private String text(JsonNode node, String field) {
        String value = optionalText(node, field);
        return value == null ? "" : value;
    }

    private String optionalText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
    }

    private String sha256(byte[] value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            hex.append(String.format("%02x", b & 0xff));
        }
        return hex.toString();
    }
}
