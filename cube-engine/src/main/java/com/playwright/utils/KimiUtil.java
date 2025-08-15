package com.playwright.utils;

import com.microsoft.playwright.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class KimiUtil {
    @Autowired
    private LogMsgUtil logInfo;

    public String waitKimiResponse(Page page, String userId, String userPrompt, String kimiChatId) throws InterruptedException {
        try {
            // 获取输入框并输入内容
            Thread.sleep(1000);
            page.locator("div.chat-input-editor").click();
            Thread.sleep(1000);
            page.locator("div.chat-input-editor").fill(userPrompt);
            logInfo.sendTaskLog("用户指令已自动输入完成", userId, "Kimi");
            page.locator("div.send-button").click();
            //等待页面渲染
            Thread.sleep(1000);
            logInfo.sendTaskLog("开启自动监听任务，持续监听Kimi回答中", userId, "Kimi");
            //携带会话Id
            if (kimiChatId != null && !kimiChatId.isEmpty()) {
                //等待kimi回答完成
                page.waitForSelector("span:has-text('重试')", new Page.WaitForSelectorOptions()
                        .setTimeout(300000)
                );
                page.locator("span:has-text('分享')").last().click();
                Thread.sleep(500);
                //全选
                page.locator("text='全选' >> .. >> input[type='checkbox']").click();
                Thread.sleep(500);
                page.locator("span:has-text('复制文本')").click();
                // 获取回答文本内容
                String resText = (String) page.evaluate("async () => { return await navigator.clipboard.readText(); }");
                Thread.sleep(1500);
                return resText;
            }
            //不携带会话Id
            page.waitForSelector("span:has-text('复制')", new Page.WaitForSelectorOptions()
                    .setTimeout(300000)
            );
            page.locator("span:has-text('分享')").click();
            Thread.sleep(500);
            page.locator("span:has-text('复制文本')").click();
            // 获取回答文本内容
            String resText = (String) page.evaluate("async () => { return await navigator.clipboard.readText(); }");
            Thread.sleep(1500);
            return resText;
        } catch (Exception e) {
            throw e;
        }
    }


}
