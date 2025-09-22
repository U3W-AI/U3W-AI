package com.playwright.utils;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.playwright.entity.UserInfoRequest;
import com.playwright.entity.mcp.McpResult;
import com.playwright.websocket.WebSocketClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MetasoUtil {

    @Autowired
    private LogMsgUtil logInfo;

    @Autowired
    private ClipboardLockManager clipboardLockManager;

    @Autowired
    private WebSocketClientService webSocketClientService;

    /**
     * 监控Metaso回答并提取HTML内容
     * @param page Playwright页面实例
     * @param userId 用户ID
     * @param aiName AI名称
     * @return 提取的HTML内容
     */
    public String waitMetasoHtmlDom(Page page, String userId, String aiName, UserInfoRequest userInfoRequest) {
        try {
            String currentContent = "";
            String lastContent = "";
            String textContent = "";
            long timeout = 60000 * 3; //  20分钟超时设置
            long startTime = System.currentTimeMillis();

            while (true) {
                // 检查超时
                if (System.currentTimeMillis() - startTime > timeout) {
                    break;
                }

                // 搜索额度用尽弹窗判断
                if (page.getByText("今日搜索额度已用尽").isVisible()) {
                    return "今日搜索额度已用尽";
                }

                // 获取最新回答内容
                Locator contentLocator = page.locator("div.MuiBox-root .markdown-body").last();
                // 设置 10 分钟超时时间获取 innerHTML
                currentContent = contentLocator.innerHTML(new Locator.InnerHTMLOptions()
                        .setTimeout(1200000) // 20分钟 = 1200000毫秒
                );
                textContent = contentLocator.textContent();
                // 内容稳定且已完成回答时退出循环
                if(userInfoRequest.getAiName() != null && userInfoRequest.getAiName().contains("stream")) {
                    webSocketClientService.sendMessage(userInfoRequest, McpResult.success(textContent, ""), userInfoRequest.getAiName());
                }
                if(!currentContent.isEmpty() && currentContent.equals(lastContent)) {
                    logInfo.sendTaskLog(aiName + "回答完成，正在提取内容", userId, aiName);
                    break;
                }
                lastContent = currentContent;
                page.waitForTimeout(2000); // 5秒检查一次
            }
            logInfo.sendTaskLog(aiName + "内容已提取完成", userId, aiName);
            if(userInfoRequest.getAiName() != null && userInfoRequest.getAiName().contains("stream")) {
                webSocketClientService.sendMessage(userInfoRequest, McpResult.success("END", ""), userInfoRequest.getAiName());
            }
            return currentContent;
        } catch (Exception e) {
            throw e;
        }
    }
}