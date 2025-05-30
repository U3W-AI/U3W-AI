package com.playwright.utils;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @author 优立方
 * @version JDK 17
 * @date 2025年05月27日 10:33
 */
@Component
public class DouBaoUtil {

    @Autowired
    private LogMsgUtil logInfo;

    public void waitAndClickDBScoreCopyButton(Page page, String userId)  {
        try {
            Locator locator = page.locator("//*[@id=\"root\"]/div[1]/div/div[3]/div[1]/div[1]/div/div/div[2]/div/div[2]/div/div/div");
            locator.waitFor(new Locator.WaitForOptions().setTimeout(20000));
            locator.click();
            // 等待复制按钮出现
            page.waitForSelector("[data-testid='message_action_copy']",
                    new Page.WaitForSelectorOptions()
                            .setState(WaitForSelectorState.VISIBLE)
                            .setTimeout(600000));  // 600秒超时
            logInfo.sendTaskLog( "评分完成，正在自动获取评分内容",userId,"智能评分");
            Thread.sleep(2000);  // 额外等待确保按钮可点击

            // 点击复制按钮
            page.locator("[data-testid='message_action_copy']")
                    .last()  // 获取最后一个复制按钮
                    .click();
            logInfo.sendTaskLog( "评分结果已自动提取完成",userId,"豆包");
            System.out.println("复制成功");

            // 确保点击操作完成
            Thread.sleep(1000);

        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    public String waitAndClickDBCopyButton(Page page,String userId,String roles)  {
        try {
            String copiedText = "";
            // 等待复制按钮出现
            Locator locator = page.locator("//*[@id=\"root\"]/div[1]/div/div[3]/div[1]/div[1]/div/div/div[2]/div/div[2]/div/div/div");

            if (locator.count() > 0 && locator.isVisible()) {
                locator.click(new Locator.ClickOptions().setForce(true));
                System.out.println("元素已点击");
            } else {
                System.out.println("元素未出现，跳过点击");
            }


            page.waitForSelector("[data-testid='message_action_copy']",
                    new Page.WaitForSelectorOptions()
                            .setState(WaitForSelectorState.VISIBLE)
                            .setTimeout(600000));  // 600秒超时
            logInfo.sendTaskLog( "豆包回答完成，正在自动提取内容",userId,"豆包");
            // 点击复制按钮
            page.locator("[data-testid='message_action_copy']")
                    .last()  // 获取最后一个复制按钮
                    .click();
            Thread.sleep(2000);
            copiedText = (String) page.evaluate("navigator.clipboard.readText()");
            logInfo.sendTaskLog( "豆包内容已自动提取完成",userId,"豆包");
            return copiedText;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "获取内容失败";
    }

    /**
     * html片段获取（核心监控方法）
     * @param page Playwright页面实例
     */
    public String waitDBHtmlDom(Page page,String userId)  {
        try {
            // 等待聊天框的内容稳定
            String currentContent = "";
            String lastContent = "";
            // 设置最大等待时间（单位：毫秒），比如 10 分钟
            long timeout = 600000; // 10 分钟
            long startTime = System.currentTimeMillis();  // 获取当前时间戳

            // 进入循环，直到内容不再变化或者超时
            while (true) {
                // 获取当前时间戳
                long elapsedTime = System.currentTimeMillis() - startTime;

                // 如果超时，退出循环
                if (elapsedTime > timeout) {
                    System.out.println("超时，AI未完成回答！");
                    break;
                }
                // 获取最新内容
                Locator outputLocator = page.locator(".flow-markdown-body").last();
                currentContent = outputLocator.innerHTML();

                System.out.println(currentContent);
                // 如果当前内容和上次内容相同，认为 AI 已经完成回答，退出循环
                if (currentContent.equals(lastContent)) {
                    logInfo.sendTaskLog( "豆包回答完成，正在自动提取内容",userId,"豆包");
                    break;
                }

                // 更新上次内容为当前内容
                lastContent = currentContent;
                page.waitForTimeout(10000);  // 等待10秒再次检查
            }
            logInfo.sendTaskLog( "豆包内容已自动提取完成",userId,"豆包");
            return currentContent;

        } catch (Exception e) {
            e.printStackTrace();
        }
        return "获取内容失败";
    }




}
