package com.playwright.utils;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.playwright.entity.UserInfoRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TongYiUtil {

    @Autowired
    private LogMsgUtil logInfo;

    /**
     * 处理通义千问的特殊模式切换（深度思考/联网搜索）
     * @param page   Playwright页面实例
     * @param roles  用户选择的角色字符串
     * @param userId 用户ID
     * @param aiName AI名称
     */
    private void handleCapabilitySwitch(Page page, String roles, String userId, String aiName) {
        try {
            String desiredMode = "";
            if (roles.contains("ty-qw-sdsk")) {
                desiredMode = "深度思考";
            } else if (roles.contains("ty-qw-lwss")) {
                desiredMode = "联网搜索";
            }

            // 检查当前是否已有激活的模式
            Locator closeButton = page.locator("span[class*='closeIcon--']");
            if (closeButton.isVisible()) {

                // 获取当前激活模式的文本，以判断是否需要切换
                Locator activeModeTag = page.locator("span[class*='tipBtn--']");
                String activeModeText = activeModeTag.textContent().trim();

                // 如果模式不同则先关闭当前模式
                if (!activeModeText.contains(desiredMode)) {
                    closeButton.click();
                    page.waitForTimeout(1500);
                } else {
                    return;
                }
            }

            // 开启目标模式
            if (!desiredMode.isEmpty()) {
                Locator buttonContainer = page.locator(".operateLine--vuNKngYr");
                buttonContainer.getByText(desiredMode).click();
                page.waitForTimeout(1500);
            }
        } catch (Exception e) {
            logInfo.sendTaskLog("切换特殊模式时发生严重错误: " + e.getMessage(), userId, aiName);
            e.printStackTrace();
        }
    }

    /**
     * 提取出的通义千问请求核心处理方法
     * @param page Playwright页面实例
     * @param userInfoRequest 包含所有请求信息的对象
     * @return 包含处理结果的Map
     */
    public Map<String, String> processQianwenRequest(Page page, UserInfoRequest userInfoRequest) {
        String userId = userInfoRequest.getUserId();
        String aiName = "通义千问";
        Map<String, String> resultMap = new HashMap<>();

        try {
            // 切换特殊模式
            handleCapabilitySwitch(page, userInfoRequest.getRoles(), userId, aiName);

            Locator inputBox = page.locator("textarea[placeholder='遇事不决问通义']");
            inputBox.click();
            page.waitForTimeout(500);
            inputBox.fill(userInfoRequest.getUserPrompt());
            logInfo.sendTaskLog("用户指令已自动输入完成", userId, aiName);
            page.waitForTimeout(500);
            inputBox.press("Enter");
            logInfo.sendTaskLog("指令已自动发送成功", userId, aiName);
            logInfo.sendTaskLog("开启自动监听任务，持续监听" + aiName + "回答中", userId, aiName);

            // 获取原始回答HTML
            String rawHtmlContent = waitTongYiHtmlDom(page, userId, aiName);
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
            return resultMap;

        } catch (Exception e) {
            e.printStackTrace();
            logInfo.sendTaskLog("处理通义千问请求时发生错误: " + e.getMessage(), userId, aiName);
            resultMap.put("rawHtmlContent", "获取内容失败");
            return resultMap;
        }
    }

    /**
     * 等待通义AI的回答内容稳定，并获取HTML片段
     * @param page Playwright页面实例
     * @param userId 用户ID
     * @param aiName 智能体名称
     */
    public String waitTongYiHtmlDom(Page page, String userId, String aiName) {
        try {
            String currentContent = "";
            String lastContent = "";

            long timeout = 600000;
            long startTime = System.currentTimeMillis();

            while (true) {
                long elapsedTime = System.currentTimeMillis() - startTime;

                if (elapsedTime > timeout) {
                    System.out.println("超时，AI未完成回答！");
                    logInfo.sendTaskLog("AI回答超时，任务中断", userId, aiName);
                    break;
                }

                Locator outputLocator = page.locator(".tongyi-markdown").last();

                if (outputLocator.count() == 0) {
                    page.waitForTimeout(2000);
                    continue;
                }

                currentContent = outputLocator.innerHTML();

                System.out.println(currentContent);
                if (!currentContent.isEmpty() && currentContent.equals(lastContent)) {
                    logInfo.sendTaskLog(aiName + "回答完成，正在自动提取内容", userId, aiName);
                    break;
                }

                lastContent = currentContent;
                page.waitForTimeout(10000);
            }
            logInfo.sendTaskLog(aiName + "内容已自动提取完成", userId, aiName);

            return currentContent;

        } catch (Exception e) {
            e.printStackTrace();
        }
        return "获取内容失败";
    }
}