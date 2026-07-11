package com.wx.fbsir.business.smartbot.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.wx.fbsir.business.interviewbot.utils.AesException;
import com.wx.fbsir.business.smartbot.service.SmartBotCallbackAdapter;
import com.wx.fbsir.common.annotation.Anonymous;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<String> verify(@PathVariable String callbackKey,
                                         @RequestParam("msg_signature") String msgSignature,
                                         @RequestParam("timestamp") String timestamp,
                                         @RequestParam("nonce") String nonce,
                                         @RequestParam("echostr") String echoStr) {
        try {
            return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(callbackAdapter.verifyUrl(callbackKey, msgSignature, timestamp, nonce, echoStr));
        } catch (SecurityException e) {
            return rejected(HttpStatus.FORBIDDEN, "URL 验证绑定拒绝", e);
        } catch (IllegalArgumentException | JsonProcessingException e) {
            return rejected(HttpStatus.BAD_REQUEST, "URL 验证请求无效", e);
        } catch (AesException e) {
            return rejected(aesStatus(e), "URL 验证加解密失败", e);
        } catch (Exception e) {
            return rejected(HttpStatus.SERVICE_UNAVAILABLE, "URL 验证暂时不可用", e);
        }
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> accept(@PathVariable String callbackKey,
                                         @RequestParam("msg_signature") String msgSignature,
                                         @RequestParam("timestamp") String timestamp,
                                         @RequestParam("nonce") String nonce,
                                         @RequestBody String encryptedBody) {
        try {
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(callbackAdapter.acceptCallback(callbackKey, msgSignature, timestamp, nonce, encryptedBody));
        } catch (SecurityException e) {
            return rejected(HttpStatus.FORBIDDEN, "回调绑定拒绝", e);
        } catch (IllegalArgumentException | JsonProcessingException e) {
            return rejected(HttpStatus.BAD_REQUEST, "回调协议无效", e);
        } catch (AesException e) {
            return rejected(aesStatus(e), "回调加解密失败", e);
        } catch (Exception e) {
            return rejected(HttpStatus.SERVICE_UNAVAILABLE, "回调暂时不可用", e);
        }
    }

    private HttpStatus aesStatus(AesException exception) {
        return switch (exception.getCode()) {
            case AesException.ValidateSignatureError,
                 AesException.ValidateCorpidError,
                 AesException.DecryptAESError,
                 AesException.IllegalBuffer -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }

    private ResponseEntity<String> rejected(HttpStatus status, String operation, Exception exception) {
        log.warn("[智能机器人控制面] {}，状态: {}，错误类型: {}",
            operation, status.value(), exception.getClass().getSimpleName());
        return ResponseEntity.status(status).body("");
    }
}
