package com.playwright.utils;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeoutException;

@Component
public class MetasoUtil {

    @Autowired
    private LogMsgUtil logInfo;

    @Autowired
    private ClipboardLockManager clipboardLockManager;

    /**
     * 监控Metaso回答并提取HTML内容
     * @param page Playwright页面实例
     * @param userId 用户ID
     * @param aiName AI名称
     * @return 提取的HTML内容
     */
    public String waitMetasoHtmlDom(Page page, String userId, String aiName) {
        try {
            String currentContent = "";
            String lastContent = "";
            long timeout = 1200000; //  20分钟超时设置
            long startTime = System.currentTimeMillis();

            while (true) {
                // 检查超时
                if (System.currentTimeMillis() - startTime > timeout) {
                    System.out.println("超时，" + aiName + "未完成回答！");
                    break;
                }

                // 搜索额度用尽弹窗判断
                if (page.getByText("今日搜索额度已用尽").isVisible()) {
                    return "今日搜索额度已用尽";
                }

                // 检测 flex-container 是否出现（出现即表示 AI 说完了）
                boolean isDone = page.locator("[id^='search-content-container-'] > .flex-container").count() > 0;

                // 获取最新回答内容
                Locator contentLocator = page.locator("div.MuiBox-root .markdown-body").last();
                // 设置 10 分钟超时时间获取 innerHTML
                currentContent = contentLocator.innerHTML(new Locator.InnerHTMLOptions()
                        .setTimeout(1200000) // 20分钟 = 1200000毫秒
                );

                // 内容稳定且已完成回答时退出循环
                if (isDone && currentContent.equals(lastContent)) {
                    logInfo.sendTaskLog(aiName + "回答完成，正在提取内容", userId, aiName);
                    break;
                }
                lastContent = currentContent;
                page.waitForTimeout(5000); // 5秒检查一次
            }
            logInfo.sendTaskLog(aiName + "内容已提取完成", userId, aiName);
            return currentContent;
        } catch (Exception e) {
            e.printStackTrace();
            return "获取内容失败";
        }
    }
}