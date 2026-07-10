package com.wx.fbsir.business.smartbot.controller;

import com.wx.fbsir.business.smartbot.service.SmartBotCallbackAdapter;
import com.wx.fbsir.common.annotation.Anonymous;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Dedicated multi-binding entry for WeCom smart-bot callback mode. */
@Anonymous
@RestController
@RequestMapping("/api/smartbot/wecom/{callbackKey}")
public class SmartBotCallbackController {

    private static final Logger log = LoggerFactory.getLogger(SmartBotCallbackController.class);
    private final SmartBotCallbackAdapter callbackAdapter;

    public SmartBotCallbackController(SmartBotCallbackAdapter callbackAdapter) {
        this.callbackAdapter = callbackAdapter;
    }

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public String verify(@PathVariable String callbackKey,
                         @RequestParam("msg_signature") String msgSignature,
                         @RequestParam("timestamp") String timestamp,
                         @RequestParam("nonce") String nonce,
                         @RequestParam("echostr") String echoStr) {
        try {
            return callbackAdapter.verifyUrl(callbackKey, msgSignature, timestamp, nonce, echoStr);
        } catch (Exception e) {
            log.warn("[智能机器人控制面] URL 验证失败，错误类型: {}", e.getClass().getSimpleName());
            return "";
        }
    }

    @PostMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public String accept(@PathVariable String callbackKey,
                         @RequestParam("msg_signature") String msgSignature,
                         @RequestParam("timestamp") String timestamp,
                         @RequestParam("nonce") String nonce,
                         @RequestBody String encryptedBody) {
        try {
            return callbackAdapter.acceptCallback(callbackKey, msgSignature, timestamp, nonce, encryptedBody);
        } catch (Exception e) {
            log.warn("[智能机器人控制面] 回调拒绝，错误类型: {}", e.getClass().getSimpleName());
            return "";
        }
    }
}
