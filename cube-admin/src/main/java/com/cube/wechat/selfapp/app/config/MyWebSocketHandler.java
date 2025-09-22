package com.cube.wechat.selfapp.app.config;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2025年01月06日 11:34
 */
import com.alibaba.fastjson.JSONObject;
import com.cube.common.core.redis.RedisCache;
import com.cube.common.entity.UserInfoRequest;
import com.cube.common.utils.StringUtils;
import com.cube.openAI.utils.SpringContextUtils;
import com.cube.wechat.selfapp.app.mapper.UserInfoMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class MyWebSocketHandler extends TextWebSocketHandler {

    private static final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, CompletableFuture<String>> FUTURE_MAP = new ConcurrentHashMap<>();


    @Autowired
    private UserInfoMapper userInfoMapper;
    @Autowired
    public MyWebSocketHandler(UserInfoMapper userInfoMapper) {
        this.userInfoMapper = userInfoMapper;
    }
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 获取客户端 ID
        String clientId = (String) session.getAttributes().get("clientId");
        if (clientId != null) {
            // 保存客户端 ID 和会话的映射
            sessions.put(clientId, session);
            JSONObject res = new JSONObject();
            res.put("message","online");
            // 1.0
//            sendMessageToClient(clientId,res.toJSONString(),null,null,null);

            //2.0
            sendMsgToClient(clientId,res.toJSONString(),new JSONObject());
            System.out.println("客户端连接成功，ID: " + clientId);
        } else {
            session.close(CloseStatus.BAD_DATA);
            System.out.println("客户端连接失败，未提供有效的客户端 ID");
        }
    }
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        // 移除断开的客户端会话
        String clientId = (String) session.getAttributes().get("clientId");
        if (clientId != null) {
            sessions.remove(clientId);
            System.out.println("客户端连接关闭，ID: " + clientId);
            JSONObject res = new JSONObject();
            res.put("message","offline");

            // 1.0
//            sendMessageToClient(clientId,res.toJSONString(),null,null,null);

            sendMsgToClient(clientId,res.toJSONString(),new JSONObject());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 从 session 中获取 clientId
        String clientId = (String) session.getAttributes().get("clientId");
        String payload = message.getPayload();

        System.out.println("收到来自客户端 " + clientId + " 的消息: " + payload);

        // 判断是否为心跳消息
        if (payload.contains("heartbeat")) {
            // 减少心跳日志输出，只在DEBUG模式下显示
            // System.out.println("心跳检查：" + clientId);
            return;
        }
        Map map = JSONObject.parseObject(payload, Map.class);
        Object o = map.get("type");
        if(o != null && "openAI".equals(o.toString())) {
            System.out.println("收到openAI调用结果：" + payload);
            String userId = map.get("userId").toString();
            String aiName = map.get("aiName").toString();
            String content = map.get("message").toString();
            String taskId = map.get("taskId").toString();
            saveAiResponse("openAI:" + userId + ":" + aiName + ":" + taskId, content);
            log.info("保存openAI结果：" + payload);
            return;
        }

        // 1.0
//        sendMessageToClient(clientId,payload,null,null,null);
        sendMsgToClient(clientId,payload,new JSONObject());
    }

    public void saveAiResponse(String type, String message) {
//        消息保存60秒
        RedisCache redisCache = SpringContextUtils.getBean(RedisCache.class);
        redisCache.setCacheObject(type, message, 60, TimeUnit.SECONDS);
    }
    /**
     * openAI与MCP规范专用方法
     */
    public String sendMsgToAI(String clientId, UserInfoRequest userInfoRequest) {
        if(userInfoRequest == null) {
            return "false";
        }
        clientId = "play-" + clientId;
        System.out.println("请求主机" + clientId);
        try {
            WebSocketSession webSocketSession = sessions.get(clientId);
            if(webSocketSession == null) {
                System.out.println("未查询到客户端，ID: " + clientId);
                return "false";
            }
            if (!webSocketSession.isOpen()) {
                System.out.println("客户端连接已关闭，ID: " + clientId);
                return "false";
            }
            userInfoRequest.setRoles("使用F8S:" + userInfoRequest.getRoles());
            log.info("发送给openAI：" + JSONObject.toJSONString(userInfoRequest));
            webSocketSession.sendMessage(new TextMessage(JSONObject.toJSONString(userInfoRequest)));
        } catch (IOException e) {
            return "false";
        }
        return "true";
    }


    public String sendMessageToClient(String clientId, String message, String taskId,String companyId,String username) throws Exception {
        System.out.println("客户端："+clientId);
        System.out.println("消息："+message);

        JSONObject res = new JSONObject();
        // 确定实际的客户端 ID
        if(clientId.contains("mini") && message.contains("playWright")){
            //小程序发给playwright
            String corpId = userInfoMapper.getCorpIdByUserId(clientId.substring(5));
            // 获取 WebSocketSession
            WebSocketSession session = sessions.get("play-"+corpId);
            // 判断 session 是否存在且在线
            if (session == null || !session.isOpen()) {
                System.out.println("playWright-" + corpId + " 不在线或连接已关闭");
                res.put("message","offline");
                return res.toJSONString();
            }
            JSONObject jsonObject = JSONObject.parseObject(message);
            jsonObject.put("userId",clientId.substring(5));
            session.sendMessage(new TextMessage(jsonObject.toJSONString()));
        }

        if(StringUtils.isNotEmpty(taskId)){
            WebSocketSession session = sessions.get("play-"+companyId);
            // 判断 session 是否存在且在线
            if (session == null || !session.isOpen()) {
                System.out.println("play-" + companyId + " 不在线或连接已关闭");
                res.put("message","offline");
                return res.toJSONString();
            }
            // 构造消息内容
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("keyword", message);
            jsonObject.put("type", "AICHAT");
            jsonObject.put("taskId", taskId);
            jsonObject.put("corpId", companyId);
            jsonObject.put("username", username);
            jsonObject.put("userId",clientId);
            // 发送消息
            session.sendMessage(new TextMessage(jsonObject.toJSONString()));
        }

        if(clientId.contains("play")&& !message.contains("plugin")){
            System.out.println("play消息："+message);
            JSONObject jsonObject = JSONObject.parseObject(message);
            if(message.contains("checkYB") || message.contains("offline") || message.contains("online")){
                List<String> userIds = userInfoMapper.getUserIdsByCorpId(clientId.substring(5));
                for (String userId : userIds) {
                    //小程序发给playwright
                    WebSocketSession session = sessions.get("mini-"+userId);
                    // 判断 session 是否存在且在线
                    if (session == null || !session.isOpen()) {
                        System.out.println("小程序" + "mini-"+userId + " 不在线或连接已关闭");
                        continue;
                    }
                    // 构造消息内容
                    jsonObject.put("type", "mini");
                    // 发送消息
                    session.sendMessage(new TextMessage(jsonObject.toJSONString()));
                }
            }else{
                String userId = jsonObject.get("userId")+"";
                if(StringUtils.isNotEmpty(userId)){
                    // 获取 WebSocketSession
                    WebSocketSession session = sessions.get("mini-"+userId);
                    // 判断 session 是否存在且在线
                    if (session == null || !session.isOpen()) {
                        System.out.println("小程序" + "mini-"+userId + " 不在线或连接已关闭");
                    }
                    // 构造消息内容
                    jsonObject.put("type", "mini");
                    // 发送消息
                    session.sendMessage(new TextMessage(jsonObject.toJSONString()));
                }

            }
        }

        if(clientId.contains("play") && message.contains("plugin")){
            //小程序发给playwright
            // 获取 WebSocketSession
            WebSocketSession session = sessions.get(clientId.substring(5));
            // 判断 session 是否存在且在线
            if (session == null || !session.isOpen()) {
                System.out.println("插件" + clientId.substring(5)+ " 不在线或连接已关闭");
            }
            // 发送消息
            session.sendMessage(new TextMessage(message));
        }
        res.put("message","online");
        return res.toJSONString();
    }


    public String sendMsgToClient(String clientId,String message,JSONObject jsonObject) throws Exception {

        JSONObject res = new JSONObject();

        if(jsonObject.get("taskId")!=null && jsonObject.get("taskId") != ""){
            WebSocketSession session = sessions.get("play-"+jsonObject.get("corpId"));
            // 判断 session 是否存在且在线
            if (session == null || !session.isOpen()) {
                System.out.println("play-" + jsonObject.get("corpId") + " 不在线或连接已关闭");
                res.put("message","offline");
                return res.toJSONString();
            }
            // 构造消息内容
//            JSONObject jsonObject = new JSONObject();
//            jsonObject.put("keyword", userInfo.getKeyword());
//            jsonObject.put("userPrompt", message);
//            jsonObject.put("type", userInfo.getType());
//            jsonObject.put("taskId", userInfo.getTaskId());
//            jsonObject.put("corpId", userInfo.getCorpId());
//            jsonObject.put("username", userInfo.getUsername());
//            jsonObject.put("userId",clientId);
//            jsonObject.put("roles",userInfo.getRoles());

            // 发送消息
            session.sendMessage(new TextMessage(jsonObject.toJSONString()));
        }


        if(clientId.contains("mypc") && message.contains("PLAY")){
            //web发给playwright
            JSONObject jsonObjectMsg = JSONObject.parseObject(message);
            // 获取 WebSocketSession
            WebSocketSession session = sessions.get("play-"+jsonObjectMsg.get("corpId"));
            // 判断 session 是否存在且在线
            if (session == null || !session.isOpen()) {
                System.out.println("playWright-" + jsonObjectMsg.get("corpId") + " 不在线或连接已关闭");
                res.put("message","offline");
                return res.toJSONString();
            }

            jsonObjectMsg.put("userId",clientId.substring(5));
            session.sendMessage(new TextMessage(message));
        }


        if(clientId.contains("play")){
            System.out.println("play消息："+message);
            JSONObject jsonObjectMsg = JSONObject.parseObject(message);
            if(message.contains("CHECK") || message.contains("offline") || message.contains("online")){
                List<String> userIds = userInfoMapper.getUserIdsByCorpId(clientId.substring(5));
                for (String userId : userIds) {
                    //小程序发给playwright
                    WebSocketSession session = sessions.get("mini-"+userId);
                    // 判断 session 是否存在且在线
                    if (session == null || !session.isOpen()) {
//                        System.out.println("web" + "web-"+userId + " 不在线或连接已关闭");
                        continue;
                    }
                    // 发送消息
                    session.sendMessage(new TextMessage(jsonObjectMsg.toJSONString()));
                }
            }else if(message.contains("PC")){
                String userId = jsonObjectMsg.get("userId")+"";
                if(StringUtils.isNotEmpty(userId)){
                    // 获取 WebSocketSession
                    WebSocketSession session = sessions.get("mypc-"+userId);
                    // 判断 session 是否存在且在线
                    if (session == null || !session.isOpen()) {
                        System.out.println( "mypc-"+userId + " 不在线或连接已关闭");
                    } else {
                        // 发送消息
                        session.sendMessage(new TextMessage(jsonObjectMsg.toJSONString()));
                    }
                }
            }else if(message.contains("MEDIA")){
                String userId = jsonObjectMsg.get("userId")+"";
                if(StringUtils.isNotEmpty(userId)){
                    // 获取 WebSocketSession
                    WebSocketSession session = sessions.get("mypc-"+userId);
                    // 判断 session 是否存在且在线
                    if (session == null || !session.isOpen()) {
                        System.out.println( "mypc-"+userId + " 不在线或连接已关闭");
                    }else
                        // 发送消息
                        session.sendMessage(new TextMessage(jsonObjectMsg.toJSONString()));
                    // 获取 WebSocketSession
                    WebSocketSession session1 = sessions.get("mini-"+userId);
                    // 判断 session 是否存在且在线
                    if (session1 == null || !session1.isOpen()) {
                        System.out.println( "mini-"+userId + " 不在线或连接已关闭");
                    } else {
                        // 发送消息
                        session1.sendMessage(new TextMessage(jsonObjectMsg.toJSONString()));
                    }
                }
            }else if(message.contains("HTTP")){
                String requestId = jsonObjectMsg.get("requestId")+"";
                CompletableFuture<String> future = FUTURE_MAP.remove(requestId);
                if (future != null) {
                    future.complete(jsonObjectMsg.get("res")+"");
                }
            }else{
                String userId = jsonObjectMsg.get("userId")+"";
                if(StringUtils.isNotEmpty(userId)){
                    // 获取 WebSocketSession
                    WebSocketSession session = sessions.get("mini-"+userId);
                    // 判断 session 是否存在且在线
                    if (session == null || !session.isOpen()) {
                        System.out.println("mini" + "mini-"+userId + " 不在线或连接已关闭");
                    }else{
                        session.sendMessage(new TextMessage(jsonObjectMsg.toJSONString()));
                    }
                    WebSocketSession sessionpc = sessions.get("mypc-"+userId);

                    // 判断 session 是否存在且在线
                    if (sessionpc == null || !sessionpc.isOpen()) {
                        System.out.println("mini" + "mini-"+userId + " 不在线或连接已关闭");
                    }else{
                        // 发送消息
                        sessionpc.sendMessage(new TextMessage(jsonObjectMsg.toJSONString()));
                    }

                }
            }
        }

        res.put("message","online");
        return res.toJSONString();
    }
    public static void registerFuture(String requestId, CompletableFuture<String> future) {
        FUTURE_MAP.put(requestId, future);
    }


}
