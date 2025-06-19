package com.playwright.utils;

import com.alibaba.fastjson.JSONObject;
import com.microsoft.playwright.Page;
import com.playwright.websocket.WebSocketClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WebSocket日志消息工具类，用于发送不同业务场景的日志数据
 * @author 优立方
 * @version JDK 17
 * @date 2025年05月27日 10:21
 */
@Component
public class LogMsgUtil {

    @Autowired
    private ScreenshotUtil screenshotUtil;


    // WebSocket客户端服务，用于实际的消息发送
    private final WebSocketClientService webSocketClientService;

    /**
     * 构造函数注入WebSocket客户端服务
     * @param webSocketClientService WebSocket客户端服务实例
     */
    public LogMsgUtil(WebSocketClientService webSocketClientService) {
        this.webSocketClientService = webSocketClientService;
    }

    /**
     * 发送图片数据消息
     * @param page Playwright页面对象
     * @param imageName 图片名称（自动添加.png后缀）
     * @param userId 用户ID
     */
    public void sendImgData(Page page, String imageName, String userId){
        // 截图并上传到指定存储服务
        String url = screenshotUtil.screenshotAndUpload(page,imageName+".png");

        JSONObject imgData = new JSONObject();
        imgData.put("url",url);
        imgData.put("userId",userId);
        imgData.put("type","RETURN_PC_TASK_IMG");
        webSocketClientService.sendMessage(imgData.toJSONString());
    }


    /**
     * 发送任务日志消息
     * @param taskNode 任务节点描述信息
     * @param userId 用户ID
     * @param aiName AI服务名称
     */
    public void sendTaskLog(String taskNode,String userId,String aiName){

        JSONObject logData = new JSONObject();
        logData.put("content",taskNode);
        logData.put("userId",userId);
        logData.put("type","RETURN_PC_TASK_LOG");
        logData.put("aiName",aiName);
        webSocketClientService.sendMessage(logData.toJSONString());
    }

    /**
     * 发送结果数据消息
     * @param copiedText 文本内容（如剪贴板内容）
     * @param userId 用户ID
     * @param aiName AI服务名称
     * @param type 消息类型标识
     */
    public void sendResData(String copiedText,String userId,String aiName,String type,String shareUrl,String shareImgUrl){

        JSONObject resData = new JSONObject();
        resData.put("draftContent",copiedText);
        resData.put("shareUrl",shareUrl);
        resData.put("shareImgUrl",shareImgUrl);
        resData.put("aiName",aiName);
        resData.put("type", type);
        resData.put("userId",userId);
        webSocketClientService.sendMessage(resData.toJSONString());
    }


    /**
     * 发送聊天数据消息（从页面URL提取参数）
     * @param page Playwright页面对象
     * @param filterCode URL参数匹配正则表达式
     * @param userId 用户ID
     * @param type 消息类型标识
     * @param count 正则匹配组序号（从1开始）
     */
    public void sendChatData(Page page,String filterCode,String userId,String type,int count){
        String currentUrl = page.url();
        Pattern pattern = Pattern.compile(filterCode);
        Matcher matcher = pattern.matcher(currentUrl);
        JSONObject chatData = new JSONObject();
        chatData.put("type", type);
        if (matcher.find()) {
            String param = matcher.group(count);
            chatData.put("chatId", param);
            chatData.put("userId", userId);
            webSocketClientService.sendMessage(chatData.toJSONString());
        }
    }
}
