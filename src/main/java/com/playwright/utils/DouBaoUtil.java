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
            logInfo.sendTaskLog( "评分完成，正在自动获取评分内容",userId,"悟空评分");
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

}
