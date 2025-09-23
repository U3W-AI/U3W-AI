package com.playwright.utils;

import com.alibaba.fastjson.JSONObject;
import com.microsoft.playwright.Keyboard;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.playwright.entity.UserInfoRequest;
import com.playwright.entity.mcp.McpResult;
import com.playwright.websocket.WebSocketClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通义千问AI工具类
 * 提供与通义千问AI交互的自动化操作功能
 * @author 优立方
 * @version JDK 17
 * @date 2025年05月27日 10:33
 */
@Component
public class TongYiUtil {

    @Autowired
    private LogMsgUtil logInfo;

    @Autowired
    private WebSocketClientService webSocketClientService;
    
    @Autowired
    private ElementSelectorUtil elementSelectorUtil;
    
    @Value("${cube.url}")
    private String url;

//    检查登录
    public String  checkLogin(Page page, String userId) {
        Locator loginLocator = page.locator("//button[contains(text(),'登录/注册')]");
        if(!loginLocator.isVisible()) {
            String userName = page.locator("//span[@class='MuiTypography-root MuiTypography-body1 css-15xijen']").textContent();
            JSONObject jsonObjectTwo = new JSONObject();
            jsonObjectTwo.put("status",userName);
            jsonObjectTwo.put("userId",userId);
            jsonObjectTwo.put("type","RETURN_METASO_STATUS");
            webSocketClientService.sendMessage(jsonObjectTwo.toJSONString());
            return userName;
        }
        return null;
    }

    /**
     * 处理通义千问的特殊模式切换（深度思考/联网搜索）
     * @param page   Playwright页面实例
     * @param roles  用户选择的角色字符串
     * @param userId 用户ID
     * @param aiName AI名称
     */
    private void handleCapabilitySwitch(Page page, String roles, String userId, String aiName) {
        long startTime = System.currentTimeMillis();
        try {
            String desiredMode = "";
            if (roles.contains("ty-qw-sdsk")) {
                desiredMode = "深度思考";
            }/* else if (roles.contains("ty-qw-lwss")) {
                desiredMode = "联网搜索";
            }*/

            // 🔥 优化：检查当前是否已有激活的模式，增加超时控制
            try {
            Locator closeButton = page.locator("span[class*='closeIcon--']");
                closeButton.waitFor(new Locator.WaitForOptions().setTimeout(10000));
                
            if (closeButton.isVisible()) {
                // 获取当前激活模式的文本，以判断是否需要切换
                Locator activeModeTag = page.locator("span[class*='tipBtn--']");
                String activeModeText = activeModeTag.textContent().trim();

                // 如果模式不同则先关闭当前模式
                if (!activeModeText.contains(desiredMode)) {
                        closeButton.click(new Locator.ClickOptions().setTimeout(15000));
                        page.waitForTimeout(2000); // 增加等待时间
                } else {
                    // 记录模式已正确
                    UserLogUtil.sendAISuccessLog(userId, aiName, "模式切换", "模式已正确设置为：" + desiredMode, startTime, url + "/saveLogInfo");
                    return;
                }
                }
            } catch (TimeoutError e) {
                // 如果没有找到关闭按钮，说明没有激活模式，继续处理
            }

            // 🔥 优化：开启目标模式，增加超时控制和重试机制
            if (!desiredMode.isEmpty()) {
                try {
                Locator buttonContainer = page.locator(".operateLine--gpbLU2Fi");
                    buttonContainer.waitFor(new Locator.WaitForOptions().setTimeout(20000));
                    
                    Locator modeButton = buttonContainer.getByText(desiredMode);
                    modeButton.click(new Locator.ClickOptions().setTimeout(15000));
                    page.waitForTimeout(2000); // 增加等待时间
                
                // 记录模式切换成功
                UserLogUtil.sendAISuccessLog(userId, aiName, "模式切换", "成功切换到：" + desiredMode, startTime, url + "/saveLogInfo");
                } catch (TimeoutError e) {
                    // 如果找不到模式按钮，记录警告但不抛出异常
                    UserLogUtil.sendAIBusinessLog(userId, aiName, "模式切换", "未找到模式按钮：" + desiredMode + "，继续处理", startTime, url + "/saveLogInfo");
                }
            }
        } catch (TimeoutError e) {
            // 记录模式切换超时
            UserLogUtil.sendAITimeoutLog(userId, aiName, "模式切换", e, "等待模式按钮或切换操作", url + "/saveLogInfo");
            logInfo.sendTaskLog("切换特殊模式时发生超时", userId, aiName);
            throw e;
        } catch (Exception e) {
            // 记录模式切换异常
            UserLogUtil.sendAIBusinessLog(userId, aiName, "模式切换", "切换特殊模式时发生错误：" + e.getMessage(), startTime, url + "/saveLogInfo");
            logInfo.sendTaskLog("切换特殊模式时发生严重错误", userId, aiName);
            throw e;
        }
    }

    /**
     * 提取出的通义千问请求核心处理方法
     * @param page Playwright页面实例
     * @param userInfoRequest 包含所有请求信息的对象
     * @return 包含处理结果的Map
     */
    public Map<String, String> processQianwenRequest(Page page, UserInfoRequest userInfoRequest) throws InterruptedException, TimeoutException {
        String userId = userInfoRequest.getUserId();
        String aiName = "通义千问";
        Map<String, String> resultMap = new HashMap<>();
        long startTime = System.currentTimeMillis();

        try {
            // 切换特殊模式
            handleCapabilitySwitch(page, userInfoRequest.getRoles(), userId, aiName);

            //点击后placeholder变化，不可使用
//            Locator inputBox = page.locator("textarea[placeholder='遇事不决问通义']");
            Locator inputBox = page.locator("//textarea[@placeholder='遇事不决问通义']");
            if (!inputBox.isVisible()) {
                inputBox = page.locator("//textarea[@placeholder='Enter 发送，Ctrl+Enter 换行，点击放大按钮可全屏输入']");
            }
            if(userInfoRequest.getRoles().contains("ty-qw-sdsk")) {
                inputBox = page.locator("//textarea[@placeholder='基于Qwen3推理模型，支持自动联网搜索']");
            }
            inputBox.click();
            page.waitForTimeout(500);
//            模拟键盘输入
            page.keyboard().type(userInfoRequest.getUserPrompt(), new Keyboard.TypeOptions()
                    .setDelay(100)); // 每个字符之间延迟100ms，更接近真人输入
            logInfo.sendTaskLog("用户指令已自动输入完成", userId, aiName);
            page.waitForTimeout(500);
            
            // 🔥 优化：使用增强的安全点击方法，带有重试机制和多选择器策略
            boolean sendSuccess = elementSelectorUtil.safeClickTongYiSendButton(page, "发送按钮点击", 3);
            if (!sendSuccess) {
                throw new TimeoutException("发送按钮点击失败，尝试了多种选择器和重试策略仍无法成功");
            }
            
            // 🔥 增强：验证发送是否成功，等待停止按钮出现
            boolean messageSent = elementSelectorUtil.waitForTongYiStopButton(page, 15000);
            if (messageSent) {
                logInfo.sendTaskLog("指令已自动发送成功，已开始生成回答", userId, aiName);
            } else {
                logInfo.sendTaskLog("指令可能发送成功，但未检测到停止按钮，继续处理", userId, aiName);
            }
            logInfo.sendTaskLog("开启自动监听任务，持续监听" + aiName + "回答中", userId, aiName);

            // 获取原始回答HTML
            String rawHtmlContent = waitTongYiHtmlDom(page, userId, aiName, userInfoRequest);
            resultMap.put("rawHtmlContent", rawHtmlContent);

            // 捕获当前会话的 sessionId
            String currentUrl = page.url();
            Pattern pattern = Pattern.compile("sessionId=([a-zA-Z0-9\\-]+)");
            Matcher matcher = pattern.matcher(currentUrl);
            if (matcher.find()) {
                String sessionId = matcher.group(1);
                resultMap.put("sessionId", sessionId);
            } else {
                resultMap.put("sessionId", "");
                logInfo.sendTaskLog("未能在URL中捕获会话ID", userId, aiName);
            }
            
            // 记录处理成功
            UserLogUtil.sendAISuccessLog(userId, aiName, "请求处理", "成功完成通义千问请求处理", startTime, url + "/saveLogInfo");
            return resultMap;

        } catch (TimeoutError e) {
            // 记录处理超时
            UserLogUtil.sendAITimeoutLog(userId, aiName, "请求处理", e, "整个请求处理流程", url + "/saveLogInfo");
            logInfo.sendTaskLog("处理通义千问请求时发生超时", userId, aiName);
            resultMap.put("rawHtmlContent", "获取内容失败：超时");
            throw e;
        } catch (Exception e) {
            // 记录处理异常
            UserLogUtil.sendAIExceptionLog(userId, aiName, "processQianwenRequest", e, startTime, "处理通义千问请求失败", url + "/saveLogInfo");
            logInfo.sendTaskLog("处理通义千问请求时发生错误", userId, aiName);
            resultMap.put("rawHtmlContent", "获取内容失败");
            throw e;
        }
    }

    /**
     * 等待通义AI的回答内容稳定，并获取HTML片段
     * @param page Playwright页面实例
     * @param userId 用户ID
     * @param aiName 智能体名称
     */
    public String waitTongYiHtmlDom(Page page, String userId, String aiName, UserInfoRequest userInfoRequest) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        try {
            String currentContent = "";
            String lastContent = "";
            String textContent = "";

            long timeout = 600000;
            long operationStartTime = System.currentTimeMillis();

            Thread.sleep(3000);
            boolean isEnd = false;
            while (true) {
                long elapsedTime = System.currentTimeMillis() - operationStartTime;

                if (elapsedTime > timeout) {
                    // 记录等待超时
                    UserLogUtil.sendAITimeoutLog(userId, aiName, "内容等待", new TimeoutException("通义千问超时"), "等待AI回答完成", url + "/saveLogInfo");
                    logInfo.sendTaskLog("AI回答超时，任务中断", userId, aiName);
                    break;
                }

                Locator outputLocator = page.locator(".tongyi-markdown").last();

                if (!page.locator("//div[@class='operateBtn--qMhYIdIu stop--P_jcrPFo']").isVisible()) {
                    isEnd = true;
                }
                if (outputLocator.count() == 0) {
                    page.waitForTimeout(2000);
                    continue;
                }

//                currentContent = outputLocator.innerHTML();
                currentContent = outputLocator.innerText();
                textContent = outputLocator.textContent();
                if(userInfoRequest.getAiName() != null && userInfoRequest.getAiName().contains("stream")) {
                    webSocketClientService.sendMessage(userInfoRequest, McpResult.success(textContent, ""), userInfoRequest.getAiName());
                }
                if (isEnd && !currentContent.isEmpty() && currentContent.equals(lastContent)) {
                    logInfo.sendTaskLog(aiName + "回答完成，正在自动提取内容", userId, aiName);
                    break;
                }

                lastContent = currentContent;
                page.waitForTimeout(2000);
            }
            logInfo.sendTaskLog(aiName + "内容已自动提取完成", userId, aiName);
            if(userInfoRequest.getAiName() != null && userInfoRequest.getAiName().contains("stream")) {
                webSocketClientService.sendMessage(userInfoRequest, McpResult.success("END", ""), userInfoRequest.getAiName());
            }
            // 记录内容提取成功
            UserLogUtil.sendAISuccessLog(userId, aiName, "内容提取", "成功提取通义千问回答内容", startTime, url + "/saveLogInfo");
            return currentContent;

        } catch (TimeoutError e) {
            // 记录内容提取超时
            UserLogUtil.sendAITimeoutLog(userId, aiName, "内容提取", e, "等待内容稳定", url + "/saveLogInfo");
            throw e;
        } catch (Exception e) {
            // 记录内容提取异常
            UserLogUtil.sendAIExceptionLog(userId, aiName, "waitTongYiHtmlDom", e, startTime, "内容提取失败", url + "/saveLogInfo");
            throw e;
        }
    }
}