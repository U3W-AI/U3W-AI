package com.cube.wechat.selfapp.app.util;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.List;
import java.util.Map;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2024年11月13日 17:07
 */
public class AssistantUtil {
    private static final String API_URL = "https://open.hunyuan.tencent.com/openapi/v1/agent/chat/completions";

    public static String callApi(String text,String assistantId,String token) throws Exception {
        // 构建请求体
        JSONObject requestBody = new JSONObject();
        //智能体ID
        requestBody.put("assistant_id", assistantId);
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
        Map<String, Object> resultMap = RestUtils.aiPost(API_URL, requestBody,token);

        // 解析返回的 JSON 数据

        List<Map> choices = (List<Map>) resultMap.get("choices");
        Map usage = (Map) resultMap.get("usage");
        String totalToken = usage.get("total_tokens")+"";
        if (choices != null && choices.size() > 0) {
            Map echoicMap = (Map) choices.get(0).get("message");
            String messageContent = echoicMap.get("content")+"";
            return messageContent;
        }

        return null;
    }

}
