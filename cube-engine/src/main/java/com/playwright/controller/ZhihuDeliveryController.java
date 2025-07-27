package com.playwright.controller;

import com.alibaba.fastjson.JSONObject;
import com.playwright.entity.UserInfoRequest;
import com.playwright.utils.LogMsgUtil;
import com.playwright.websocket.WebSocketClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 知乎投递控制器
 * 统一处理知乎内容的排版和投递功能
 * @author 优立方
 * @version JDK 17
 * @date 2025年01月21日 15:30
 */
@RestController
@RequestMapping("/api/browser")
@Tag(name = "知乎投递控制器", description = "统一处理知乎内容的排版和投递功能")
public class ZhihuDeliveryController {

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

    // 构造器注入WebSocket服务
    public ZhihuDeliveryController(WebSocketClientService webSocketClientService) {
        this.webSocketClientService = webSocketClientService;
    }

    /**
     * 统一的知乎投递处理方法
     * 包含智能排版和知乎投递的完整流程
     * @param userInfoRequest 用户信息请求体
     * @return 处理结果
     */
    @PostMapping("/deliverToZhihu")
    @Operation(summary = "投递内容到知乎", description = "统一处理知乎内容的排版和投递")
    public String deliverToZhihu(@RequestBody UserInfoRequest userInfoRequest) {
        String userId = userInfoRequest.getUserId();
        String aiName = userInfoRequest.getAiName();
        
        try {
            // 1. 发送开始消息
            sendTaskLog("投递到知乎", "开始知乎内容排版...", userId);
            
            // 2. 内部调用智能排版
            logInfo.sendMediaTaskLog("正在调用豆包进行智能排版...", userId, "投递到知乎");
            String layoutResult = callInternalLayout(userInfoRequest);
            
            if (layoutResult == null || layoutResult.trim().isEmpty()) {
                logInfo.sendMediaTaskLog("排版失败，无法继续投递", userId, "投递到知乎");
                sendDeliveryResult("投递到知乎", "error", "排版失败", userId);
                return "error";
            }
            
            logInfo.sendMediaTaskLog("内容排版完成，准备投递...", userId, "投递到知乎");
            
            // 3. 构建知乎文章标题
            String title = buildZhihuTitle(aiName, userId);
            logInfo.sendMediaTaskLog("标题构建完成：" + title, userId, "投递到知乎");
            
            // 4. 内部调用知乎投递
            logInfo.sendMediaTaskLog("正在投递内容到知乎平台...", userId, "投递到知乎");
            String deliveryResult = mediaController.sendToZhihu(userId, title, layoutResult);
            
            if ("true".equals(deliveryResult)) {
                logInfo.sendMediaTaskLog("知乎投递完成！", userId, "投递到知乎");
                sendDeliveryResult("投递到知乎", "success", "知乎投递任务完成", userId);
            } else {
                logInfo.sendMediaTaskLog("知乎投递失败", userId, "投递到知乎");
                sendDeliveryResult("投递到知乎", "error", "知乎投递失败", userId);
            }
            
            return deliveryResult;
        } catch (Exception e) {
            String errorMsg = "投递失败：" + e.getMessage();
            logInfo.sendMediaTaskLog(errorMsg, userId, "投递到知乎");
            sendDeliveryResult("投递到知乎", "error", errorMsg, userId);
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
            logInfo.sendTaskLog("智能排版调用失败：" + e.getMessage(), userInfoRequest.getUserId(), "投递到知乎");
            return null;
        }
    }
    
    /**
     * 构建知乎文章标题
     * 格式：<aiName>-<userId>-<formattedDate>
     * @param aiName AI名称
     * @param userId 用户ID
     * @return 构建的标题
     */
    private String buildZhihuTitle(String aiName, String userId) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
            String formattedDate = sdf.format(new Date());
            
            return aiName + "-" + userId + "-" + formattedDate;
        } catch (Exception e) {
            // 如果标题构建失败，使用默认标题
            return "知乎文章-" + userId + "-" + System.currentTimeMillis();
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
            resultMessage.put("type", "RETURN_ZHIHU_DELIVERY_RES");
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