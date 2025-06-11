package com.cube.wechat.selfapp.app.util;


import cn.felord.api.WorkWeChatApi;
import cn.felord.domain.WeComResponse;
import cn.felord.domain.webhook.WebhookBody;
import cn.felord.domain.webhook.WebhookMarkdownBody;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.List;
import java.util.Map;


/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2024年09月20日 15:50
 */
public class HunYuanApiUtil {
    private static final String API_URL = "https://open.hunyuan.tencent.com/openapi/v1/agent/chat/completions";
    private static final String BEARER_TOKEN = "RFrQc6w9AG2ammU069syM2gShXe76yfw";

    public static String callApi(String text) throws Exception {
        // 构建请求体
        JSONObject requestBody = new JSONObject();
        //智能体ID
        requestBody.put("assistant_id", "HK2SXWRYfZKC");
        requestBody.put("user_id", "AspireLife");
        requestBody.put("stream", false);

        JSONArray messagesArray = new JSONArray();
        JSONObject messageObject = new JSONObject();
        messageObject.put("role", "user");

        JSONArray contentArray = new JSONArray();
        JSONObject textObject = new JSONObject();
        textObject.put("type", "text");
        textObject.put("text", text);
        contentArray.add(textObject);
        messageObject.put("content", contentArray);
        messagesArray.add(messageObject);

        requestBody.put("messages", messagesArray);

        // 发送 POST 请求
        Map<String, Object> resultMap = RestUtils.aiPost(API_URL, requestBody,BEARER_TOKEN);

        // 解析返回的 JSON 数据

        List<Map> choices = (List<Map>) resultMap.get("choices");
        Map usage = (Map) resultMap.get("usage");
        String totalToken = usage.get("total_tokens")+"";
        if (choices != null && choices.size() > 0) {
            Map echoicMap = (Map) choices.get(0).get("message");
            String messageContent = echoicMap.get("content")+"";
            return messageContent+"\n\n（内容基于腾讯元器AI生成，仅供参考; 本次消耗token："+totalToken+"）";
        }

        return null;
    }

    public static void main(String[] args) {
        try {
//            https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=cc4d7c79-2c1e-4e49-9263-5b837012fa52
            String text = "胖东来是如何帮助永辉的？";
            String answer = HunYuanApiUtil.callApi(text);
            WebhookBody markdownBody = WebhookMarkdownBody.from(answer);
            WeComResponse weComResponse = WorkWeChatApi.webhookApi().send("cc4d7c79-2c1e-4e49-9263-5b837012fa52", markdownBody);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
