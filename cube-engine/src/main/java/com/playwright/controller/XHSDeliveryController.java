package com.playwright.controller;

import com.alibaba.fastjson.JSONObject;
import com.playwright.entity.ImageTextRequest;
import com.playwright.entity.UserInfoRequest;
import com.playwright.utils.LogMsgUtil;
import com.playwright.websocket.WebSocketClientService;
import okio.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 小红书投递控制器
 * 统一处理小红书内容的排版和投递功能
 * @author Moyesan
 * @version JDK 17
 * @date 2025年 8月 8日 19:01
 */
@RestController
@RequestMapping("/api/browser")
@Tag(name = "小红书投递控制器", description = "统一处理小红书内容的排版和投递功能")
public class XHSDeliveryController {

    // 依赖注入
    private final WebSocketClientService webSocketClientService;

    @Autowired
    private AIGCController aigcController;

    @Autowired
    private MediaController mediaController;

    @Autowired
    private LogMsgUtil logInfo;

    @Value("${cube.url}")
    private String url;

    @Value("${cube.imgfile}")
    private String uploadurl;


    // 构造器注入WebSocket服务
    public XHSDeliveryController(WebSocketClientService webSocketClientService) {
        this.webSocketClientService = webSocketClientService;
    }

    /**
     * 统一的小红书投递处理方法
     * 包含智能排版和小红书投递的完整流程
     * @param userInfoRequest 用户信息请求体
     * @return 处理结果
     */
    @PostMapping("/deliverToXHS")
    @Operation(summary = "投递内容到小红书", description = "处理小红书内容的排版和投递")
    public String deliverToXHS(@RequestBody UserInfoRequest userInfoRequest) {
        String userId = userInfoRequest.getUserId();
        String aiName = userInfoRequest.getAiName();

        try {
            // 1. 发送开始消息
            sendTaskLog("投递到小红书", "开始小红书内容排版...", userId);

            // 2. 内部调用智能排版
            logInfo.sendMediaTaskLog("正在调用豆包进行智能排版...", userId, "投递到小红书");
            String layoutResult = callInternalLayout(userInfoRequest);

            if (layoutResult == null || layoutResult.trim().isEmpty()) {
                logInfo.sendMediaTaskLog("排版失败，无法继续投递", userId, "投递到小红书");
                sendDeliveryResult("投递到小红书", "error", "排版失败", userId);
                return "error";
            }

            logInfo.sendMediaTaskLog("内容排版完成，准备投递...", userId, "投递到小红书");

            // 3. 构建小红书文章标题
            String title = buildXHSTitle(layoutResult);
            logInfo.sendMediaTaskLog("标题构建完成：" + title, userId, "投递到小红书");

            // 4.生成小红书笔记图片

            //设置图文格式和排版，二期进行用户自定义图文格式开发
            ImageTextRequest imageTextRequest = new ImageTextRequest();


            imageTextRequest.setImagePath(getClass().getClassLoader().getResource("file/xiaohongshu_text_bg.jpg").getPath());
            System.out.println(getClass().getClassLoader()
                    .getResource("file/xiaohongshu_text_bg.jpg")
                    .getPath());
            imageTextRequest.setOutputPath(uploadurl + "/xhs_img/" + (int)(Math.random() * 1000000)+ ".jpg");
            //上传路径如果不存在则创建
            if(!Files.isDirectory(Paths.get(uploadurl + "/xhs_img/"))){
                Files.createDirectories(Paths.get(uploadurl + "/xhs_img/"));
            }
            imageTextRequest.setText(layoutResult);
            imageTextRequest.setFontSize(25);
            imageTextRequest.setFontStyle("BOLD");
            imageTextRequest.setX1(150);
            imageTextRequest.setY1(210);
            imageTextRequest.setX2(920);
            imageTextRequest.setY2(1220);
            imageTextRequest.setCharSpacing(1.0f);
            imageTextRequest.setLineSpacing(1.6f);

            List<String> imgs = mediaController.putTextToImage(imageTextRequest);

            // 4. 内部调用小红书投递
            logInfo.sendMediaTaskLog("正在投递内容到小红书平台...", userId, "投递到小红书");
            String deliveryResult = mediaController.sendToXHS(userId, title, layoutResult,imgs);


            if ("true".equals(deliveryResult)) {
                logInfo.sendMediaTaskLog("小红书投递完成！", userId, "投递到小红书");
                sendDeliveryResult("投递到小红书", "success", "小红书投递任务完成", userId);
            } else {
                logInfo.sendMediaTaskLog("小红书投递失败", userId, "投递到小红书");
                sendDeliveryResult("投递到小红书", "error", "小红书投递失败", userId);
            }

            return deliveryResult;
        } catch (Exception e) {
            String errorMsg = "投递失败：" + e.getMessage();
            logInfo.sendMediaTaskLog(errorMsg, userId, "投递到小红书");
            sendDeliveryResult("投递到小红书", "error", errorMsg, userId);
            return "error";
        }
    }

    /**
     * 内部调用智能排版功能
     * @param userInfoRequest 用户请求信息
     * @return 排版后的内容
     */
    private String callInternalLayout(UserInfoRequest userInfoRequest) {
        try {
            // 设置角色为豆包排版
            userInfoRequest.setRoles("db");

            // 调用AIGCController的内部排版方法
            String layoutResult = aigcController.startDBInternal(userInfoRequest);

            return layoutResult;
        } catch (Exception e) {
            logInfo.sendTaskLog("智能排版调用失败：" + e.getMessage(), userInfoRequest.getUserId(), "投递到小红书");
            return null;
        }
    }

    /**
     * 构建小红书文章标题
     * 格式：排版后的第一句
     * @param text 排版后的文字内容
     * @return 构建的标题
     */
    private String buildXHSTitle(String text) {
        try {
            String[] strings = text.split("\n");
            for(int i = 0;i < strings.length;i++){
                if(strings[i] != null && strings[i].length() <= 20){
                    return strings[i];
                }
            }
            return "小红书文章";
        } catch (Exception e) {
            // 如果标题构建失败，使用默认标题
            return "小红书文章";
        }
    }

    /**
     * 发送任务日志消息
     * @param aiName AI名称
     * @param content 日志内容
     * @param userId 用户ID
     */
    private void sendTaskLog(String aiName, String content, String userId) {
        try {
            JSONObject message = new JSONObject();
            message.put("type", "RETURN_PC_TASK_LOG");
            message.put("aiName", aiName);
            message.put("content", content);
            message.put("userId", userId);
            message.put("timestamp", System.currentTimeMillis());

            webSocketClientService.sendMessage(message.toJSONString());
        } catch (Exception e) {
            System.err.println("发送任务日志失败: " + e.getMessage());
        }
    }

    /**
     * 发送投递完成结果消息
     * @param aiName AI名称
     * @param status 状态（success/error）
     * @param message 结果消息
     * @param userId 用户ID
     */
    private void sendDeliveryResult(String aiName, String status, String message, String userId) {
        try {
            JSONObject resultMessage = new JSONObject();
            resultMessage.put("type", "RETURN_XHS_DELIVERY_RES");
            resultMessage.put("aiName", aiName);
            resultMessage.put("status", status);
            resultMessage.put("message", message);
            resultMessage.put("userId", userId);
            resultMessage.put("timestamp", System.currentTimeMillis());

            webSocketClientService.sendMessage(resultMessage.toJSONString());
        } catch (Exception e) {
            System.err.println("发送投递结果失败: " + e.getMessage());
        }
    }
}