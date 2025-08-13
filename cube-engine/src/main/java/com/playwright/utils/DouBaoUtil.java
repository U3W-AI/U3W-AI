package com.playwright.utils;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
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
public class DouBaoUtil {

    @Autowired
    private LogMsgUtil logInfo;

    @Autowired
    private ClipboardLockManager clipboardLockManager;

    public void waitAndClickDBScoreCopyButton(Page page, String userId) throws InterruptedException {
        try {
            // 等待页面内容稳定
            String currentContent = "";
            String lastContent = "";
            long timeout = 600000; // 10分钟超时
            long startTime = System.currentTimeMillis();

            while (true) {
                long elapsedTime = System.currentTimeMillis() - startTime;
                if (elapsedTime > timeout) {
                    break;
                }

                Locator outputLocator = page.locator(".flow-markdown-body").last();
                currentContent = outputLocator.innerHTML();

                if (!currentContent.isEmpty() && currentContent.equals(lastContent)) {
                    break;
                }

                lastContent = currentContent;
                page.waitForTimeout(5000); // 每5秒检查一次
            }
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

            // 确保点击操作完成
            Thread.sleep(1000);

        } catch (Exception e) {
            throw e;
        }
    }

    public String waitAndClickDBCopyButton(Page page,String userId,String roles) throws InterruptedException {
        try {
            // 等待页面内容稳定
            String currentContent = "";
            String lastContent = "";
            long timeout = 600000; // 10分钟超时
            long startTime = System.currentTimeMillis();

            while (true) {
                long elapsedTime = System.currentTimeMillis() - startTime;
                if (elapsedTime > timeout) {
                    break;
                }

                Locator outputLocator = page.locator(".flow-markdown-body").last();
                currentContent = outputLocator.innerHTML();

                if (!currentContent.isEmpty() && currentContent.equals(lastContent)) {
                    break;
                }

                lastContent = currentContent;
                page.waitForTimeout(5000); // 每5秒检查一次
            }
            String copiedText = "";
            // 等待复制按钮出现
            Locator locator = page.locator("//*[@id=\"root\"]/div[1]/div/div[3]/div[1]/div[1]/div/div/div[2]/div/div[2]/div/div/div");

            if (locator.count() > 0 && locator.isVisible()) {
                locator.click(new Locator.ClickOptions().setForce(true));
            } else {
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
            throw e;
        }
    }

    /**
     * html片段获取（核心监控方法）
     * @param page Playwright页面实例
     */
    public String waitDBHtmlDom(Page page,String userId,String aiName)  {
        try {
            // 等待聊天框的内容稳定
            String currentContent = "";
            String lastContent = "";
            boolean isRight =false;
            // 设置最大等待时间（单位：毫秒），比如 10 分钟
            long timeout = 600000; // 10 分钟
            long startTime = System.currentTimeMillis();  // 获取当前时间戳

            // 进入循环，直到内容不再变化或者超时
            while (true) {
                // 获取当前时间戳
                long elapsedTime = System.currentTimeMillis() - startTime;

                // 如果超时，退出循环
                if (elapsedTime > timeout) {
                    break;
                }
                // 获取最新内容


                if(currentContent.contains("改用对话直接回答") && !isRight){
                    page.locator("//*[@id=\"root\"]/div[1]/div/div[3]/div/main/div/div/div[2]/div/div[1]/div/div/div[2]/div[2]/div/div/div/div/div/div/div[1]/div/div/div[2]/div[1]/div/div").click();
                    isRight = true;
                }

                if(isRight){
                    Locator outputLocator = page.locator("//*[@id=\"root\"]/div[1]/div/div[3]/aside/div[2]/div/div[1]/div/div[2]/div/div/div/div/div/div/div/div/div/div/div/div[1]").last();
                    currentContent = outputLocator.innerHTML();
                }else{
                    Locator outputLocator = page.locator(".flow-markdown-body").last();
                    currentContent = outputLocator.innerHTML();
                }


                // 如果当前内容和上次内容相同，认为 AI 已经完成回答，退出循环
                if (currentContent.equals(lastContent)) {
                    logInfo.sendTaskLog( aiName+"回答完成，正在自动提取内容",userId,aiName);
                    break;
                }

                // 更新上次内容为当前内容
                lastContent = currentContent;
                page.waitForTimeout(10000);  // 等待10秒再次检查
            }
            logInfo.sendTaskLog( aiName+"内容已自动提取完成",userId,aiName);

            String regex = "<span>\\s*<span[^>]*?>\\d+</span>\\s*</span>";

            currentContent = currentContent.replaceAll(regex,"");
            currentContent = currentContent.replaceAll("撰写任何内容...","");
//            Document doc = Jsoup.parse(currentContent);
//            currentContent = doc.text();  // 提取纯文本内容
            return currentContent;

        } catch (Exception e) {
            throw e;
        }
    }



    /**
     * 排版代码获取（核心监控方法）
     * @param page Playwright页面实例
     */
    public String waitPBCopy(Page page,String userId,String aiName)  {
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
                    break;
                }

                Locator outputLocator = page.locator(".flow-markdown-body").last();
                currentContent = outputLocator.innerHTML();
                // 如果当前内容和上次内容相同，认为 AI 已经完成回答，退出循环
                if (currentContent.equals(lastContent)) {
                    logInfo.sendTaskLog( aiName+"回答完成，正在自动提取内容",userId,aiName);

                    clipboardLockManager.runWithClipboardLock(() -> {
                        try {
                            // 获取所有复制按钮的 SVG 元素（通过 xlink:href 属性定位）
                            if(page.locator("[data-testid='code-block-copy']").count()>0){
                                page.locator("[data-testid='code-block-copy']")
                                        .last()  // 获取最后一个复制按钮
                                        .click();
                            }else{
                                page.locator("[data-testid='message_action_copy']")
                                        .last()  // 获取最后一个复制按钮
                                        .click();
                            }

                            String text = (String) page.evaluate("navigator.clipboard.readText()");
                            textRef.set(text);
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
            logInfo.sendTaskLog( aiName+"内容已自动提取完成",userId,aiName);

            currentContent = textRef.get();
            return currentContent;

        } catch (Exception e) {
            throw e;
        }
    }


}
