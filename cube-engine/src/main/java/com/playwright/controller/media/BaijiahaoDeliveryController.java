package com.playwright.controller.media;

import com.alibaba.fastjson.JSONObject;
import com.playwright.controller.ai.AIGCController;
import com.playwright.entity.UserInfoRequest;
import com.playwright.utils.common.LogMsgUtil;
import com.playwright.utils.common.UserLogUtil;
import com.playwright.websocket.WebSocketClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 百家号投递控制器
 * 统一处理百家号内容的排版和投递功能
 * @author 优立方
 * @version JDK 17
 */
@RestController
@RequestMapping("/api/browser")
@Tag(name = "百家号投递控制器", description = "统一处理百家号内容的排版和投递功能")
public class BaijiahaoDeliveryController {

    // 依赖注入
    private final WebSocketClientService webSocketClientService;
    @Value("${cube.url}")
    private String url;
    @Autowired
    private AIGCController aigcController;
    @Autowired
    private MediaController mediaController;
    @Autowired
    private LogMsgUtil logInfo;

    // 构造器注入WebSocket服务
    public BaijiahaoDeliveryController(WebSocketClientService webSocketClientService) {
        this.webSocketClientService = webSocketClientService;
    }

    /**
     * 统一的百家号投递处理方法
     * 包含智能排版和百家号投递的完整流程
     * @param userInfoRequest 用户信息请求体
     * @return 处理结果
     */
    @PostMapping("/deliverToBaijiahao")
    @Operation(summary = "投递内容到百家号", description = "统一处理百家号内容的排版和投递")
    public String deliverToBaijiahao(@RequestBody UserInfoRequest userInfoRequest) {
        String userId = userInfoRequest.getUserId();
        String aiName = userInfoRequest.getAiName();

        try {
            // 3. 构建百家号文章标题
            String title = buildBaijiahaoTitle(aiName, userId);
            logInfo.sendMediaTaskLog("标题构建完成：" + title, userId, "投递到百家号");

            // 4. 内部调用百家号投递
            logInfo.sendMediaTaskLog("正在投递内容到百家号平台...", userId, "投递到百家号");
            String deliveryResult = mediaController.sendToBaijiahao(userId, title, userInfoRequest.getUserPrompt());

            if ("true".equals(deliveryResult)) {
                logInfo.sendMediaTaskLog("百家号投递完成！", userId, "投递到百家号");
                sendDeliveryResult("投递到百家号", "success", "百家号投递任务完成", userId);
            } else {
                logInfo.sendMediaTaskLog("百家号投递失败", userId, "投递到百家号");
                sendDeliveryResult("投递到百家号", "error", "百家号投递失败", userId);
            }

            return deliveryResult;
        } catch (Exception e) {
            String errorMsg = "投递失败：" + e.getMessage();
            logInfo.sendMediaTaskLog(errorMsg, userId, "投递到百家号");
            sendDeliveryResult("投递到百家号", "error", errorMsg, userId);
            UserLogUtil.sendExceptionLog(userId, "百家号投递", "deliverToBaijiahao", e, url + "/saveLogInfo");
            return "error";
        }
    }

    /**
     * 构建百家号文章标题
     * 格式：<aiName>-<userId>-<formattedDate>
     * @param aiName AI名称
     * @param userId 用户ID
     * @return 构建的标题
     */
    private String buildBaijiahaoTitle(String aiName, String userId) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
            String formattedDate = sdf.format(new Date());

            return aiName + "-" + userId + "-" + formattedDate;
        } catch (Exception e) {
            UserLogUtil.sendExceptionLog(userId, "百家号投递", "buildBaijiahaoTitle", e, url + "/saveLogInfo");
            // 如果标题构建失败，使用默认标题
            return "百家号文章-" + userId + "-" + System.currentTimeMillis();
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
            System.err.println("发送任务日志失败");
            UserLogUtil.sendExceptionLog(userId, "百家号投递", "sendTaskLog", e, url + "/saveLogInfo");
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
            resultMessage.put("type", "RETURN_BAIJIAHAO_DELIVERY_RES");
            resultMessage.put("aiName", aiName);
            resultMessage.put("status", status);
            resultMessage.put("message", message);
            resultMessage.put("userId", userId);
            resultMessage.put("timestamp", System.currentTimeMillis());

            webSocketClientService.sendMessage(resultMessage.toJSONString());
        } catch (Exception e) {
            System.err.println("发送投递结果失败");
            UserLogUtil.sendExceptionLog(userId, "百家号投递", "sendDeliveryResult", e, url + "/saveLogInfo");
        }
    }
}
