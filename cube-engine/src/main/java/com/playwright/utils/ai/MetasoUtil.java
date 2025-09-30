package com.playwright.utils.ai;

import com.alibaba.fastjson.JSONObject;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.playwright.entity.UserInfoRequest;
import com.playwright.entity.mcp.McpResult;
import com.playwright.utils.common.ClipboardLockManager;
import com.playwright.utils.common.LogMsgUtil;
import com.playwright.websocket.WebSocketClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.WaitForSelectorState;

@Component
public class MetasoUtil {

    @Autowired
    private LogMsgUtil logInfo;

    @Autowired
    private ClipboardLockManager clipboardLockManager;

    @Autowired
    private WebSocketClientService webSocketClientService;

    //    检查登录
    public String checkLogin(Page page, String userId) throws Exception {
        // 检查页面是否已关闭
        if (page.isClosed()) {
            throw new RuntimeException("页面已关闭，无法检查登录状态");
        }

        try {
        Locator loginLocator = page.locator("//button[contains(text(),'登录/注册')]");
            if (!loginLocator.isVisible()) {
            String userName = page.locator("(//span[@class='MuiTypography-root MuiTypography-body1 css-1tyjpe7'])[1]").textContent();
            JSONObject jsonObjectTwo = new JSONObject();
                jsonObjectTwo.put("status", userName);
                jsonObjectTwo.put("userId", userId);
                jsonObjectTwo.put("type", "RETURN_METASO_STATUS");
            webSocketClientService.sendMessage(jsonObjectTwo.toJSONString());
            return userName;
        }
        return null;
        } catch (com.microsoft.playwright.impl.TargetClosedError e) {
            throw new RuntimeException("页面目标已关闭", e);
        } catch (TimeoutError e) {
            throw new RuntimeException("检查登录状态超时", e);
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * 监控Metaso回答并提取HTML内容
     * @param page Playwright页面实例
     * @param userId 用户ID
     * @param aiName AI名称
     * @return 提取的HTML内容
     */
    public String waitMetasoHtmlDom(Page page, String userId, String aiName, UserInfoRequest userInfoRequest) throws Exception {
        // 检查页面是否已关闭
        if (page.isClosed()) {
            throw new RuntimeException("页面已关闭，无法等待Metaso回答");
        }

        try {
            String currentContent = "";
            String lastContent = "";
            String textContent = "";
            long timeout = 60000 * 3; //  3分钟超时设置
            long startTime = System.currentTimeMillis();

            while (true) {
                // 检查页面是否已关闭
                if (page.isClosed()) {
                    throw new RuntimeException("页面在等待回答过程中已关闭");
                }

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
                // 设置 20 分钟超时时间获取 innerHTML
                currentContent = contentLocator.innerHTML(new Locator.InnerHTMLOptions()
                        .setTimeout(1200000) // 20分钟 = 1200000毫秒
                );
                textContent = contentLocator.textContent();
                
                // 内容稳定且已完成回答时退出循环
                if (userInfoRequest.getAiName() != null && userInfoRequest.getAiName().contains("stream")) {
                    webSocketClientService.sendMessage(userInfoRequest, McpResult.success(textContent, ""), userInfoRequest.getAiName());
                }
                
                if (!currentContent.isEmpty() && currentContent.equals(lastContent)) {
                    logInfo.sendTaskLog(aiName + "回答完成，正在提取内容", userId, aiName);
                    break;
                }
                lastContent = currentContent;
                page.waitForTimeout(2000); // 2秒检查一次
            }
            
            logInfo.sendTaskLog(aiName + "内容已提取完成", userId, aiName);
            
            if (userInfoRequest.getAiName() != null && userInfoRequest.getAiName().contains("stream")) {
                webSocketClientService.sendMessage(userInfoRequest, McpResult.success("END", ""), userInfoRequest.getAiName());
            }
            
            return currentContent;
            
        } catch (com.microsoft.playwright.impl.TargetClosedError e) {
            throw new RuntimeException("页面目标在等待Metaso回答时已关闭", e);
        } catch (TimeoutError e) {
            throw new RuntimeException("等待Metaso回答超时", e);
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * 安全获取秘塔分享链接
     * @param page Playwright页面实例
     * @param userId 用户ID
     * @param aiName AI名称
     * @return 分享链接
     */
    public String getMetasoShareUrlSafely(Page page, String userId, String aiName) {
        try {
            // 检查页面是否已关闭
            if (page.isClosed()) {
                logInfo.sendTaskLog("页面已关闭，无法获取分享链接", userId, aiName);
                return null;
            }

            // 🔥 多策略复制链接按钮选择器
            String[] shareButtonSelectors = {
                // 基于角色和文本的选择器（最稳定）
                "button:has-text('复制链接')",
                "[role='button']:has-text('复制链接')", 
                "//button[contains(text(),'复制')]",
                
                // 基于SVG图标的选择器
                "//svg[contains(@class,'share') or contains(@class,'copy')]//ancestor::button",
                "//use[contains(@xlink:href,'share') or contains(@xlink:href,'copy')]//ancestor::*[@role='button' or local-name()='button']",
                
                // 基于位置的选择器（作为备用）
                "(//*[name()='svg'])[26]",
                "(//button[@type='button'])[24]",
                
                // 通过DOM结构定位
                "//div[contains(@class,'toolbar') or contains(@class,'action')]//button[last()]",
                "//div[contains(@class,'option') or contains(@class,'menu')]//button[contains(@class,'copy') or contains(text(),'复制')]"
            };

            String shareUrl = null;
            boolean clickSuccess = false;

            // 策略1：尝试所有选择器进行复制链接操作
            for (int i = 0; i < shareButtonSelectors.length && !clickSuccess; i++) {
                try {
                    String selector = shareButtonSelectors[i];
                    Locator shareButton = page.locator(selector);
                    
                    if (shareButton.count() > 0) {
                        // 等待按钮可见并点击
                        shareButton.waitFor(new Locator.WaitForOptions()
                            .setTimeout(5000)
                            .setState(WaitForSelectorState.VISIBLE));
                            
                        shareButton.click();
                        Thread.sleep(1000);
                        
                        // 尝试从剪贴板读取链接
                        try {
                            shareUrl = (String) page.evaluate("navigator.clipboard.readText()");
                            if (shareUrl != null && shareUrl.contains("http")) {
                                clickSuccess = true;
                                logInfo.sendTaskLog("通过选择器 " + (i+1) + " 成功获取分享链接: " + shareUrl, userId, aiName);
                                break;
                            }
                        } catch (Exception clipEx) {
                            logInfo.sendTaskLog("第 " + (i+1) + " 个选择器点击成功但读取剪贴板失败", userId, aiName);
                        }
                    }
                } catch (Exception e) {
                    // 继续尝试下一个选择器
                    if (i == shareButtonSelectors.length - 1) {
                        logInfo.sendTaskLog("所有复制链接选择器都失败", userId, aiName);
                    }
                }
            }

            // 策略2：如果复制链接失败，尝试从URL或页面中直接提取
            if (!clickSuccess || shareUrl == null || !shareUrl.contains("http")) {
                logInfo.sendTaskLog("复制链接失败，尝试从页面直接获取链接", userId, aiName);
                
                // 方法2.1：从当前页面URL中提取或构建分享链接
                String currentUrl = page.url();
                if (currentUrl.contains("metaso.cn")) {
                    // 构建标准的秘塔分享链接格式
                    if (currentUrl.contains("/search/")) {
                        shareUrl = currentUrl;
                        logInfo.sendTaskLog("从页面URL获取分享链接: " + shareUrl, userId, aiName);
                    }
                }
                
                // 方法2.2：搜索页面中是否有分享链接元素
                if (shareUrl == null || !shareUrl.contains("http")) {
                    try {
                        Locator linkElements = page.locator("a[href*='metaso.cn'], input[value*='metaso.cn'], span:has-text('http')");
                        if (linkElements.count() > 0) {
                            for (int i = 0; i < linkElements.count(); i++) {
                                try {
                                    String linkText = linkElements.nth(i).textContent();
                                    String linkHref = linkElements.nth(i).getAttribute("href");
                                    String linkValue = linkElements.nth(i).getAttribute("value");
                                    
                                    String potentialUrl = linkHref != null ? linkHref : 
                                                         linkValue != null ? linkValue : linkText;
                                    
                                    if (potentialUrl != null && potentialUrl.contains("http") && potentialUrl.contains("metaso")) {
                                        shareUrl = potentialUrl;
                                        logInfo.sendTaskLog("从页面元素中找到分享链接: " + shareUrl, userId, aiName);
                                        break;
                                    }
                                } catch (Exception ex) {
                                    continue;
                                }
                            }
                        }
                    } catch (Exception e) {
                        logInfo.sendTaskLog("搜索页面链接元素失败: " + e.getMessage(), userId, aiName);
                    }
                }
                
                // 方法2.3：最后备用方案 - 使用当前URL作为分享链接
                if (shareUrl == null || !shareUrl.contains("http")) {
                    shareUrl = currentUrl;
                    logInfo.sendTaskLog("使用当前页面URL作为分享链接: " + shareUrl, userId, aiName);
                }
            }

            // 🔥 新增：清理URL，只保留数字ID部分（支持 /search/ 和 /search-v2/）
            if (shareUrl != null && (shareUrl.contains("metaso.cn/search/") || shareUrl.contains("metaso.cn/search-v2/"))) {
                shareUrl = cleanMetasoUrl(shareUrl);
                logInfo.sendTaskLog("已清理秘塔URL，保留数字ID: " + shareUrl, userId, aiName);
            }
            
            return shareUrl;

        } catch (Exception e) {
            logInfo.sendTaskLog("获取秘塔分享链接时发生异常: " + e.getMessage(), userId, aiName);
            // 返回当前页面URL作为备用
            try {
                String backupUrl = page.url();
                if (backupUrl != null && (backupUrl.contains("metaso.cn/search/") || backupUrl.contains("metaso.cn/search-v2/"))) {
                    backupUrl = cleanMetasoUrl(backupUrl);
                }
                return backupUrl;
            } catch (Exception urlEx) {
                return null;
            }
        }
    }

    /**
     * 清理秘塔URL，只保留数字ID部分
     * 例如：https://metaso.cn/search-v2/8646763915575853056?q=xxx -> https://metaso.cn/search-v2/8646763915575853056
     * @param url 原始URL
     * @return 清理后的URL
     */
    private String cleanMetasoUrl(String url) {
        if (url == null || (!url.contains("metaso.cn/search/") && !url.contains("metaso.cn/search-v2/"))) {
            return url;
        }
        
        try {
            // 查找数字ID的位置（支持 /search/ 和 /search-v2/）
            int searchIndex = url.indexOf("metaso.cn/search-v2/");
            String searchPath = "metaso.cn/search-v2/";
            
            if (searchIndex == -1) {
                searchIndex = url.indexOf("metaso.cn/search/");
                searchPath = "metaso.cn/search/";
            }
            
            if (searchIndex == -1) {
                return url;
            }
            
            // 提取基础路径
            String basePath = url.substring(0, searchIndex + searchPath.length());
            
            // 提取数字ID部分
            String remaining = url.substring(searchIndex + searchPath.length());
            
            // 查找第一个非数字字符的位置（通常是?或#）
            int endIndex = 0;
            for (int i = 0; i < remaining.length(); i++) {
                char c = remaining.charAt(i);
                if (!Character.isDigit(c)) {
                    endIndex = i;
                    break;
                }
            }
            
            // 如果全部都是数字，则保留全部
            if (endIndex == 0) {
                endIndex = remaining.length();
            }
            
            String numberId = remaining.substring(0, endIndex);
            return basePath + numberId;
            
        } catch (Exception e) {
            // 如果解析失败，返回原URL
            return url;
        }
    }
}