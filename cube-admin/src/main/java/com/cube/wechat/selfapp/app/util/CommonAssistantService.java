package com.cube.wechat.selfapp.app.util;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.cube.wechat.selfapp.app.domain.AIParam;
import com.cube.wechat.selfapp.app.mapper.UserInfoMapper;
import com.cube.wechat.selfapp.wecom.util.RedisUtil;
import com.cube.wechat.selfapp.wecom.util.WeChatApiUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2024年11月19日 09:29
 */


@Service
public class CommonAssistantService {


    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private UserInfoMapper userInfoMapper;

    @Value("${datazone.url}")
    private String dataZoneUrl;

    @Autowired
    private WeChatApiUtils weChatApiUtils;

    private String content;
    private String conversationId;
    private String text;
    private String assistantId;
    private Integer chatType;
    private String fileUrl;
    private String userId;


    // 提取 content 字段

    public String getCommonModelRes(AIParam aiParam){
        this.conversationId = aiParam.getConversationId();
        this.text = aiParam.getUserPrompt();
        this.userId = aiParam.getUserId();
        this.content="";


        JSONObject commonJsonObject = new JSONObject();
        commonJsonObject.put("program_id", "progZ5zJMDbq4x_GfFXdNZMwnh_k6jZ1qA-t");
        commonJsonObject.put("ability_id", "invoke_create_ww_model_task");

        JSONObject commonReq = new JSONObject();

        Map varMap = new HashMap();
        varMap.put("name","keyword");
        varMap.put("value",aiParam.getUserPrompt());

        List<Map> vars = new ArrayList<>();
        vars.add(varMap);

        commonReq.put("ability_id","duduchat");
        commonReq.put("kb_id","kbsdVeYuR_VpoJIRVPPni12JpRf-QnP0Z7");
        commonReq.put("kb_retrieval_words",aiParam.getUserPrompt());
        commonReq.put("var_args",vars);
        commonJsonObject.put("request_data", commonReq.toJSONString());

        Map commonMap = RestUtils.post(dataZoneUrl+weChatApiUtils.getSelAccessToken(), commonJsonObject);

        Map<String, Object> commonRes = JSON.parseObject((String) commonMap.get("response_data"), Map.class);
        String jobId = (String) commonRes.get("jobid");

        JSONObject jobJson = new JSONObject();
        jobJson.put("program_id", "progZ5zJMDbq4x_GfFXdNZMwnh_k6jZ1qA-t");
        jobJson.put("ability_id", "invoke_get_ww_model_result");

        JSONObject jobReq = new JSONObject();
        jobReq.put("jobid",jobId);
        jobJson.put("request_data", jobReq.toJSONString());

        System.out.println("任务ID:"+jobId);
        try {
            Thread.sleep(20000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Map tabMap = RestUtils.post(dataZoneUrl+weChatApiUtils.getSelAccessToken(), jobJson);

        Map<String, Object> textData = JSON.parseObject((String) tabMap.get("response_data"), Map.class);


        if(textData.get("response_data")!=null){
            String res = textData.get("response_data")+"";
            content = res.replace("\\n\\n","<br>").replace("\n\n","<br>");
            processContentAsync();
            return content;
        }

        return "";
    }

    private void processContentAsync() {
        // 异步处理逻辑
        new Thread(() -> {
            try {
                saveUserChatHistory();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
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
