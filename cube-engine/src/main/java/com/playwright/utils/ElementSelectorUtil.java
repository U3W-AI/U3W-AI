package com.playwright.utils;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 元素选择器工具类
 * 提供多种选择器策略和重试机制
 */
@Component
public class ElementSelectorUtil {

    /**
     * 使用多种选择器策略查找元素
     * @param page 页面对象
     * @param selectors 选择器数组，按优先级排序
     * @param timeout 超时时间（毫秒）
     * @return 找到的Locator对象，如果没找到返回null
     */
    public Locator findElementWithMultipleSelectors(Page page, String[] selectors, int timeout) {
        for (String selector : selectors) {
            try {
                Locator locator = page.locator(selector);
                // 等待元素出现，但不抛出异常
                try {
                    locator.waitFor(new Locator.WaitForOptions().setTimeout(timeout));
                    if (locator.count() > 0) {
                        // 如果找到多个元素，返回第一个以避免严格模式违反
                        return locator.count() > 1 ? locator.first() : locator;
                    }
                } catch (TimeoutError e) {
                    // 超时就尝试下一个选择器
                    continue;
                }
            } catch (Exception e) {
                // 如果是严格模式违反错误，尝试使用.first()
                if (e.getMessage().contains("strict mode violation")) {
                    try {
                        Locator locator = page.locator(selector).first();
                        locator.waitFor(new Locator.WaitForOptions().setTimeout(timeout));
                        if (locator.count() > 0) {
                            return locator;
                        }
                    } catch (Exception ex) {
                    }
                }
                continue;
            }
        }
        return null;
    }

    /**
     * 检查元素是否处于激活状态
     * @param locator 元素定位器
     * @param activeClasses 激活状态的CSS类名数组
     * @return 是否激活
     */
    public boolean isElementActive(Locator locator, String[] activeClasses) {
        try {
            StringBuilder checkScript = new StringBuilder("element => {\n");
            checkScript.append("    return ");
            for (int i = 0; i < activeClasses.length; i++) {
                if (i > 0) {
                    checkScript.append(" && ");
                }
                checkScript.append("element.classList.contains('").append(activeClasses[i]).append("')");
            }
            checkScript.append(";\n}");
            
            return (Boolean) locator.evaluate(checkScript.toString());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 安全点击元素
     * @param locator 元素定位器
     * @param actionName 操作名称（用于日志）
     * @return 是否点击成功
     */
    public boolean safeClick(Locator locator, String actionName) {
        try {
            if (locator != null && locator.count() > 0) {
                locator.click();
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取MiniMax深度思考按钮的选择器
     * @return 选择器数组
     */
    public String[] getDeepThinkSelectors() {
        return new String[]{
            // 优先使用精确的绝对XPath定位器
            "xpath=/html/body/section/div/div/section/div/div[1]/div/div/div/div/div/div[3]/div[2]/div/div[2]/div[1]/div[2]",
            "xpath=/html/body/section/div/div/section/div/div[1]/div/div/main/div[3]/div[2]/div/div[2]/div[1]/div[2]",
            // 使用更精确的选择器，避免严格模式违反
            "div[class*='cursor-pointer']:has-text('深度思考'):first",
            "xpath=(//div[contains(@class,'cursor-pointer') and contains(text(),'深度思考')])[1]",
            "xpath=(//div[contains(text(),'深度思考')])[1]",
            // 备用选择器
            "[data-testid*='deep']",
            "[id*='deep']",
            "[class*='深度思考']"
        };
    }

    /**
     * 获取MiniMax联网按钮的选择器
     * @return 选择器数组
     */
    public String[] getInternetSelectors() {
        return new String[]{
            // 优先使用精确的绝对XPath定位器
            "xpath=/html/body/section/div/div/section/div/div[1]/div/div/div/div/div/div[3]/div[2]/div/div[2]/div[1]/div[1]",
            "xpath=/html/body/section/div/div/section/div/div[1]/div/div/main/div[3]/div[2]/div/div[2]/div[1]/div[1]",
            // 使用更精确的选择器，避免严格模式违反
            "div[class*='cursor-pointer']:has-text('联网'):first",
            "xpath=(//div[contains(@class,'cursor-pointer') and contains(text(),'联网')])[1]",
            "xpath=(//div[contains(text(),'联网')])[1]",
            // 备用选择器
            "[data-testid*='internet']",
            "[id*='internet']",
            "[class*='联网']"
        };
    }

    /**
     * 获取激活状态的CSS类名
     * @return CSS类名数组
     */
    public String[] getActiveClasses() {
        return new String[]{
            "bg-col_brand00/[0.06]",
            "text-col_brand00",
            "border-col_brand00/[0.16]"
        };
    }

    /**
     * 获取MiniMax输入框选择器
     * @return 选择器数组
     */
    public String[] getInputSelectors() {
        return new String[]{
            "#chat-input",
            "textarea[placeholder*='问']",
            "textarea[id*='input']",
            "textarea[class*='input']",
            "input[type='text'][placeholder*='问']",
            "xpath=//textarea[@placeholder]",
            "xpath=//input[@placeholder]"
        };
    }

    /**
     * 获取发送按钮选择器
     * @return 选择器数组
     */
    public String[] getSendButtonSelectors() {
        return new String[]{
            "#input-send-icon",
            "[data-input-icon='true']",
            "button:has-text('发送')",
            "[class*='send']",
            "xpath=//button[contains(@class,'send')]",
            "xpath=//div[@id='input-send-icon']"
        };
    }

    /**
     * 获取通义千问发送按钮的选择器（多重策略）
     * @return 选择器数组
     */
    public String[] getTongYiSendButtonSelectors() {
        return new String[]{
            // 原始选择器（最精确但最脆弱）
            "//div[@class='operateBtn--qMhYIdIu']//span[@role='img']//*[name()='svg']",
            // 更宽泛的备用选择器
            "//div[contains(@class,'operateBtn')]//span[@role='img']//*[name()='svg']",
            "//div[contains(@class,'operateBtn')]//span[@role='img']",
            "//div[contains(@class,'operate')]//svg",
            "//span[@role='img']//*[name()='svg'][contains(@class,'send') or contains(@class,'submit')]",
            // 基于功能的选择器
            "//button[contains(@title,'发送') or contains(@aria-label,'发送')]",
            "//div[@role='button'][contains(@class,'send') or contains(@class,'submit')]",
            "[data-testid*='send']",
            "[data-action*='send']",
            // 最后的备用选择器
            "//div[contains(@class,'btn') and contains(@class,'send')]//svg",
            "xpath=(//svg[contains(@class,'icon')])[last()]"
        };
    }

    /**
     * 安全点击通义千问发送按钮，具有重试机制
     * @param page 页面对象
     * @param actionName 操作名称（用于日志）
     * @param maxRetries 最大重试次数
     * @return 是否点击成功
     */
    public boolean safeClickTongYiSendButton(com.microsoft.playwright.Page page, String actionName, int maxRetries) {
        String[] selectors = getTongYiSendButtonSelectors();
        int baseTimeout = 45000; // 基础超时45秒
        
        for (int retry = 0; retry < maxRetries; retry++) {
            try {
                // 每次重试增加超时时间
                int currentTimeout = baseTimeout + (retry * 15000); // 每次重试增加15秒
                
                com.microsoft.playwright.Locator buttonLocator = findElementWithMultipleSelectors(page, selectors, currentTimeout);
                if (buttonLocator != null) {
                    // 确保元素可见和可点击
                    try {
                        buttonLocator.waitFor(new com.microsoft.playwright.Locator.WaitForOptions()
                            .setTimeout(currentTimeout)
                            .setState(WaitForSelectorState.VISIBLE));
                        
                        // 滚动到元素位置确保可见
                        buttonLocator.scrollIntoViewIfNeeded();
                        page.waitForTimeout(1000);
                        
                        // 点击操作
                        buttonLocator.click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(currentTimeout));
                        
                        return true;
                    } catch (Exception clickError) {
                        // 如果点击失败，尝试JavaScript点击
                        try {
                            buttonLocator.evaluate("element => element.click()");
                            return true;
                        } catch (Exception jsError) {
                            if (retry == maxRetries - 1) {
                                throw jsError;
                            }
                        }
                    }
                }
                
                // 重试前等待
                if (retry < maxRetries - 1) {
                    page.waitForTimeout(3000 + (retry * 2000)); // 渐进式等待
                }
                
            } catch (Exception e) {
                if (retry == maxRetries - 1) {
                    return false;
                }
                
                try {
                    page.waitForTimeout(2000); // 错误后等待2秒再重试
                } catch (Exception ignored) {}
            }
        }
        
        return false;
    }

    /**
     * 等待通义千问停止按钮出现，用于判断消息发送成功
     * @param page 页面对象
     * @param timeout 超时时间
     * @return 是否检测到停止按钮
     */
    public boolean waitForTongYiStopButton(com.microsoft.playwright.Page page, int timeout) {
        try {
            // 检测停止按钮的出现，说明消息已发送成功
            String[] stopButtonSelectors = {
                "//div[@class='operateBtn--qMhYIdIu stop--P_jcrPFo']",
                "//div[contains(@class,'operateBtn') and contains(@class,'stop')]",
                "//div[contains(@class,'stop')]//svg",
                "//button[contains(@class,'stop') or contains(@title,'停止')]"
            };
            
            com.microsoft.playwright.Locator stopButton = findElementWithMultipleSelectors(page, stopButtonSelectors, timeout);
            return stopButton != null && stopButton.count() > 0;
            
        } catch (Exception e) {
            return false;
        }
    }
} 