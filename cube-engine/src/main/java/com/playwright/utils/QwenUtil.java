package com.playwright.utils;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author 优立方
 * @version JDK 17
 * @date 2025年05月27日 10:33
 */
@Component
public class QwenUtil {

    @Autowired
    private LogMsgUtil logInfo;

    @Autowired
    private ClipboardLockManager clipboardLockManager;

    /**
     * html片段获取（核心监控方法）
     * @param page Playwright页面实例
     */
    public String waitQWHtmlDom(Page page,String userId)  {
        try {
            // 等待聊天框的内容稳定
            String currentContent = "";
            String lastContent = "";
            boolean isRight =false;
            // 设置最大等待时间（单位：毫秒），比如 10 分钟
            long timeout = 600000; // 10 分钟
            long startTime = System.currentTimeMillis();  // 获取当前时间戳
            AtomicReference<String> textRef = new AtomicReference<>();

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
                Locator outputLocator = page.locator(".tongyi-markdown").last();
                currentContent = outputLocator.innerHTML();


                System.out.println(currentContent);

                // 如果当前内容和上次内容相同，认为 AI 已经完成回答，退出循环
                if (currentContent.equals(lastContent)) {
                    logInfo.sendTaskLog( "通义千问回答完成，正在自动提取内容",userId,"通义千问");


                    clipboardLockManager.runWithClipboardLock(() -> {
                        try {
                            // 获取所有复制按钮的 SVG 元素（通过 xlink:href 属性定位）
                            Locator copyBtn = page.locator("xpath=//use[@xlink:href='#tongyi-copy-line']/ancestor::div[contains(@class, 'btn--YtZqkWMA')]");
                            // 点击按钮
                            copyBtn.click();

                            String text = (String) page.evaluate("navigator.clipboard.readText()");
                            textRef.set(text);
                            System.out.println("剪贴板内容：" + text);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });

                    break;
                }

                // 更新上次内容为当前内容
                lastContent = currentContent;
                page.waitForTimeout(10000);  // 等待10秒再次检查
            }
            logInfo.sendTaskLog( "通义千问包内容已自动提取完成",userId,"通义千问");
            currentContent = textRef.get();
            return currentContent;

        } catch (Exception e) {
            e.printStackTrace();
        }
        return "获取内容失败";
    }




}
