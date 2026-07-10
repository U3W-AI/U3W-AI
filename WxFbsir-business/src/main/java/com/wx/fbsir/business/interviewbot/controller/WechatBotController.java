package com.wx.fbsir.business.interviewbot.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wx.fbsir.business.interviewbot.service.InterviewService;
import com.wx.fbsir.business.interviewbot.utils.WXBizJsonMsgCrypt;
import com.wx.fbsir.common.annotation.Anonymous;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

/**
 * 企业微信机器人回调控制器
 * 处理微信验证与消息交互
 * * @author WxFbsir Team
 * @date 2026-01-22
 */
@Anonymous
@RestController
@RequestMapping("/api/wechat")
public class WechatBotController {

    private static final Logger log = LoggerFactory.getLogger(WechatBotController.class);
    // Enterprise-created WeCom smart bots require an empty receiveId for the
    // official JSON callback crypto envelope. CorpID applies to a different
    // application callback contract and must not be appended here.
    private static final String WE_COM_SMART_BOT_RECEIVE_ID = "";

    @Value("${wechat.token}")
    private String sToken;

    @Value("${wechat.aes-key}")
    private String sEncodingAESKey;

    @Autowired
    private InterviewService interviewService;

    private final ObjectMapper jsonMapper = new ObjectMapper();

    /**
     * URL 验证接口 (GET)
     * 用于企业微信后台配置回调地址时的验证
     */
    @GetMapping
    public String verify(@RequestParam("msg_signature") String msgSignature,
                         @RequestParam("timestamp") String timestamp,
                         @RequestParam("nonce") String nonce,
                         @RequestParam("echostr") String echostr) {
        try {
            WXBizJsonMsgCrypt wxcpt = new WXBizJsonMsgCrypt(sToken, sEncodingAESKey, WE_COM_SMART_BOT_RECEIVE_ID);
            return wxcpt.VerifyURL(msgSignature, timestamp, nonce, echostr);
        } catch (Exception e) {
            log.error("[微信回调] URL验证失败 - signature: {}, timestamp: {}", msgSignature, timestamp, e);
            return "verify fail";
        }
    }

    /**
     * 消息接收与回复接口 (POST)
     * 接收用户消息并返回加密回复
     */
    @PostMapping
    public String handleMessage(@RequestParam(name = "msg_signature") String msgSignature,
                                @RequestParam(name = "timestamp") String timestamp,
                                @RequestParam(name = "nonce") String nonce,
                                @RequestBody String postData) {
        
        try {
            // 初始化加解密类
            WXBizJsonMsgCrypt wxcpt = new WXBizJsonMsgCrypt(sToken, sEncodingAESKey, WE_COM_SMART_BOT_RECEIVE_ID);

            // 解密
            String decryptedMsg = wxcpt.DecryptMsg(msgSignature, timestamp, nonce, postData);
            // 解析 JSON 业务数据
            JsonNode root = jsonMapper.readTree(decryptedMsg);
            String msgType = root.path("msgtype").asText();
            log.debug("[微信回调] 已解析消息 - msgId: {}, botId: {}, type: {}",
                root.path("msgid").asText(), root.path("aibotid").asText(), msgType);
            String replyContent;

            // 业务逻辑处理
            if ("text".equals(msgType)) {
                String content = root.path("text").path("content").asText().trim();
                log.debug("[业务处理] 收到文本指令，长度: {}", content.length());
                replyContent = handleTextCommand(content);
            } else if ("image".equals(msgType)) {
                replyContent = "收到图片，但我是文本机器人哦。";
            } else {
                log.warn("[业务处理] 不支持的消息类型: {}", msgType);
                replyContent = "收到其他消息类型：" + msgType + "，当前暂不支持";
            }

            // 构造回复 (流式/一次性)
            ObjectNode streamNode = jsonMapper.createObjectNode();
            streamNode.put("id", String.valueOf(System.currentTimeMillis()));
            streamNode.put("finish", true);
            streamNode.put("content", replyContent);

            // 构造外层 JSON
            ObjectNode replyRoot = jsonMapper.createObjectNode();
            replyRoot.put("msgtype", "stream");
            replyRoot.set("stream", streamNode);

            String replyJson = jsonMapper.writeValueAsString(replyRoot);
            
            // 加密并返回
            return wxcpt.EncryptMsg(replyJson, timestamp, nonce);

        } catch (Exception e) {
            log.error("[微信回调] 消息处理异常", e);
            return "";
        }
    }

    /**
     * 处理文本指令
     */
    private String handleTextCommand(String content) {
        // 清洗 @ (保留之前的正则逻辑)
        String cleanContent = content.replaceAll("^@\\S+\\s*", "").trim();
        log.debug("[指令清洗] 已完成，原始长度: {}，清洗后长度: {}", content.length(), cleanContent.length());

        // 匹配 "今日面试"
        if ("今日面试".equals(cleanContent)) {
            return interviewService.getTodaySummary();
        }

        // 匹配 "查看最近 N"
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^查看最近\\s*(\\d+)$");
        java.util.regex.Matcher matcher = pattern.matcher(cleanContent);

        if (matcher.find()) {
            String numStr = matcher.group(1);
            try {
                int limit = Integer.parseInt(numStr);
                return interviewService.getRecentRecords(limit);
            } catch (NumberFormatException e) {
                log.warn("[指令处理] 数字解析失败: {}", numStr);
                return "目前最多仅支持查看5条信息";
            }
        }

        return "指令菜单：\n1. 今日面试\n2. 查看最近 N（1~5） (例如: \"查看最近 3\")";
    }
}
