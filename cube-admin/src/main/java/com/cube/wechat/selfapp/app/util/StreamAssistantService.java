package com.cube.wechat.selfapp.app.util;


import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.cube.point.controller.PointsSystem;
import com.cube.wechat.selfapp.app.domain.AIParam;
import com.cube.wechat.selfapp.app.mapper.UserInfoMapper;
import com.cube.wechat.selfapp.wecom.util.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2024年11月19日 09:29
 */


@Service
public class StreamAssistantService {

    private final WebClient webClient;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private UserInfoMapper userInfoMapper;

    @Autowired
    private PointsSystem pointsSystem;

    private String content;
    private String conversationId;
    private String text;
    private String assistantId;
    private Integer chatType;
    private String fileUrl;
    private String userId;

    public StreamAssistantService(WebClient.Builder webClientBuilder) {
        // 配置超时设置
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(120)); // 设置响应超时时间

        this.webClient = webClientBuilder
                .baseUrl("https://yuanqi.tencent.com")
                .clientConnector(new ReactorClientHttpConnector(httpClient)) // 使用自定义的 HttpClient
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer ->
                                configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024)) // 增加内存缓冲区
                        .build())
                .build();
    }


    public Flux<String> fetchStreamData(AIParam aiParam) {

        Map agentData = userInfoMapper.getAgentTokenByUserId(aiParam.getUserId());
        pointsSystem.setUserPoint(aiParam.getUserId(),"调用公众号智能体API",null,"0x3f4413a0e863903147172b1e7672d7a23025e084","824af41abf2ca18335f5547ae293a4e250ed7e80a78f985fd01d551e0a0d3552");

        aiParam.setAssistantId(agentData.get("agent_id")+"");
        aiParam.setToken(agentData.get("agent_token")+"");

        this.conversationId = aiParam.getConversationId();
        this.text = aiParam.getUserPrompt();
        this.assistantId = aiParam.getAssistantId();
        this.userId = aiParam.getUserId();
        this.content="";
        this.chatType = aiParam.getChatType();


        System.out.println("参数："+aiParam.toString());
        // 构建请求体
        JSONObject messageObject = new JSONObject();
        messageObject.put("role", "user");
        JSONArray contentArray = new JSONArray();
        JSONObject textObject = new JSONObject();
        textObject.put("type", "text");
        textObject.put("text", text);
        contentArray.add(textObject);
        if(aiParam.getChatType().equals(2)){
            this.fileUrl = aiParam.getFileUrl();
            JSONObject fileObject = new JSONObject();
            fileObject.put("type", "file_url");
            JSONObject fileUrlObject = new JSONObject();
            fileUrlObject.put("type","");
            fileUrlObject.put("url",aiParam.getFileUrl());
            fileObject.put("file_url",fileUrlObject);
            contentArray.add(fileObject);
        }
        messageObject.put("content", contentArray);


        JSONObject requestBody = new JSONObject();
        requestBody.put("assistant_id", assistantId);
        requestBody.put("user_id", this.userId);
        requestBody.put("stream", true); // 确保流式响应开启

        JSONArray messagesArray = new JSONArray();



        messagesArray.add(messageObject);

        Object chatHistory = redisUtil.get("dudu."+conversationId);
        JSONArray chatMessagesArray;
        if(chatHistory!=null){
            chatMessagesArray = JSONArray.parseArray(chatHistory.toString());
            chatMessagesArray.addAll(messagesArray);
            requestBody.put("messages", chatMessagesArray);
        }else{
            requestBody.put("messages", messagesArray);
        }


        // 调用远程接口
        Flux<String> flux = webClient.post()
                .uri("/openapi/v1/agent/chat/completions")
                .header("Authorization", "Bearer " + aiParam.getToken()) // 设置 Authorization 请求头
                .header("Content-Type", "application/json")
                .bodyValue(requestBody) // 设置请求体
                .retrieve()
                .bodyToFlux(String.class) // 将响应流转换为字符串流
                .onErrorResume(e -> Flux.just("Error occurred: " + e.getMessage()));
        return flux.doOnNext(this::processContentAsync); // 在这里处理流数据的副作用;
    }

    private void processContentAsync(String jsonString) {
        // 异步处理逻辑
        new Thread(() -> {
            try {
                extractContent(jsonString);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // 提取 content 字段

    private String extractContent(String jsonString) {
        try {
            System.out.println("结果："+jsonString);
            if(!jsonString.equals("[DONE]")){
                JSONObject jsonObject = JSONObject.parseObject(jsonString);
                List<Map> choices = (List<Map>) jsonObject.get("choices");
                Map delta = (Map) choices.get(0).get("delta");
                if(delta.get("role").equals("assistant")){
                    content = content + delta.get("content");
                    return delta.get("content").toString().trim();
                }
                return "";
            }else{
                saveUserChatHistory();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Error parsing JSON.";
        }
        return "";
    }

    private void saveUserChatHistory() {
        JSONArray messagesArray = new JSONArray();

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        JSONArray userContents = new JSONArray();
        JSONObject userText = new JSONObject();
        userText.put("type", "text");
        userText.put("text", text);
        userContents.add(userText);
        if(this.chatType.equals(2)){
            JSONObject fileObject = new JSONObject();
            fileObject.put("type", "file_url");
            JSONObject fileUrlObject = new JSONObject();
            fileUrlObject.put("type","");
            fileUrlObject.put("url",this.fileUrl);
            fileObject.put("file_url",fileUrlObject);
            userContents.add(fileObject);
        }
        userMsg.put("content", userContents);

        messagesArray.add(userMsg);


        JSONObject AIMsg = new JSONObject();
        AIMsg.put("role", "assistant");
        JSONArray AIContents = new JSONArray();
        JSONObject AIText = new JSONObject();
        AIText.put("type", "text");
        AIText.put("text", this.content);
        AIContents.add(AIText);
        AIMsg.put("content", AIContents);
        messagesArray.add(AIMsg);


        System.out.println("准备入库");
        Object chatHistory = redisUtil.get("dudu."+conversationId);
        JSONArray contentArray;
        if(chatHistory!=null){
            System.out.println("更新会话");
            contentArray = JSONArray.parseArray(chatHistory.toString());
            contentArray.addAll(messagesArray);
            redisUtil.set("dudu."+this.conversationId,contentArray);
            Map map = new HashMap();
            map.put("userId",this.userId);
            map.put("conversationId",this.conversationId);
            map.put("chatHistory",contentArray.toString());
            userInfoMapper.updateUserChat(map);
        }else{
            System.out.println("新建会话");
            //用户新会话
            redisUtil.set("dudu."+this.conversationId,messagesArray);
            Map map = new HashMap();
            map.put("userId",this.userId);
            map.put("conversationId",this.conversationId);
            map.put("title",this.text);
            map.put("chatHistory",messagesArray.toString());
            userInfoMapper.saveUserChat(map);
        }
    }
}
