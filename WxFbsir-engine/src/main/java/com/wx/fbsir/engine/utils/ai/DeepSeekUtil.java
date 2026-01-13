package com.wx.fbsir.engine.utils.ai;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import com.wx.fbsir.engine.playwright.util.ClipboardManager;
import com.wx.fbsir.engine.playwright.util.ScreenshotUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * DeepSeek AI平台工具类
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 核心职责
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 1. 登录状态检测
 * 2. 二维码扫码登录支持
 * 3. 消息发送与响应监听
 * 4. 深度思考模式切换
 * 5. 联网搜索模式切换
 * 6. 内容提取与清理
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 使用方式
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * ```java
 * @Autowired
 * private DeepSeekUtil deepSeekUtil;
 * 
 * // 检查登录状态
 * String loginStatus = deepSeekUtil.checkLoginStatus(page, true);
 * 
 * // 导航到登录页获取二维码
 * boolean success = deepSeekUtil.navigateToLoginPage(page);
 * 
 * // 发送消息并等待回复
 * String response = deepSeekUtil.sendMessageAndWaitResponse(page, "你好", false, false);
 * ```
 * 
 * @author wxfbsir
 * @date 2025-12-25
 */
@Component
public class DeepSeekUtil {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekUtil.class);
    
    @Autowired
    private ClipboardManager clipboardManager;
    
    @Autowired
    private ScreenshotUtil screenshotUtil;
    
    /**
     * DeepSeek 主页地址
     */
    private static final String DEEPSEEK_HOME_URL = "https://chat.deepseek.com/";
    
    /**
     * DeepSeek 登录页地址
     */
    private static final String DEEPSEEK_LOGIN_URL = "https://chat.deepseek.com/sign_in";
    
    /**
     * 刷新按钮点击标志（防止重复点击）
     */
    private volatile boolean hasClickedRefreshButton = false;

    /**
     * 检查DeepSeek登录状态（快速检测版本）
     * 
     * @param page Playwright页面对象
     * @param navigate 是否需要先导航到DeepSeek页面
     * @return 登录状态，如果已登录则返回用户名，否则返回"false"
     */
    public String checkLoginStatus(Page page, boolean navigate) {
        if (navigate) {
            try {
                log.debug("[DeepSeek] 开始导航到主页");
                page.navigate(DEEPSEEK_HOME_URL, new Page.NavigateOptions().setTimeout(10000));
                page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(10000));
                page.waitForTimeout(1000);
                log.debug("[DeepSeek] 页面加载完成");
            } catch (Exception e) {
                log.warn("[DeepSeek] 导航失败: {}", e.getMessage());
                return "false";
            }
        }

        // 1. 快速检测：登录表单（未登录的明确标志）
        try {
            Locator loginForm = page.locator("div.ds-sign-up-form__main").first();
            if (loginForm.count() > 0) {
                log.info("[DeepSeek] 检测到登录表单 - 未登录");
                return "false";
            }
        } catch (Exception e) {
            log.debug("[DeepSeek] 登录表单检测异常: {}", e.getMessage());
        }

        // 2. 检测用户名（已登录标志） - 先尝试直接获取
        try {
            Locator userNameDiv = page.locator("div._2afd28d div._9d8da05").first();
            if (userNameDiv.count() > 0 && userNameDiv.isVisible()) {
                String userName = userNameDiv.textContent();
                if (userName != null && !userName.trim().isEmpty()) {
                    log.info("[DeepSeek] 检测到已登录用户: {}", userName.trim());
                    return userName.trim();
                }
            }
        } catch (Exception e) {
            log.debug("[DeepSeek] 直接获取用户名失败: {}", e.getMessage());
        }

        // 3. 尝试展开侧边栏获取用户名
        try {
            Locator sidebarToggle = page.locator("div.ca6d4be1._5a20a69 div._4f3769f.ds-icon-button").first();
            if (sidebarToggle.count() > 0 && sidebarToggle.isVisible()) {
                log.debug("[DeepSeek] 尝试展开侧边栏");
                sidebarToggle.click();
                page.waitForTimeout(1000);
                
                // 再次检测用户名
                Locator userNameDiv = page.locator("div._2afd28d div._9d8da05").first();
                if (userNameDiv.count() > 0) {
                    String userName = userNameDiv.textContent();
                    if (userName != null && !userName.trim().isEmpty()) {
                        log.info("[DeepSeek] 展开侧边栏后检测到已登录用户: {}", userName.trim());
                        return userName.trim();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[DeepSeek] 侧边栏展开检测异常: {}", e.getMessage());
        }

        // 4. 检测历史对话列表（已登录的标志）
        try {
            Locator chatHistory = page.locator("div._3098d02").first();
            if (chatHistory.count() > 0) {
                log.info("[DeepSeek] 检测到对话历史 - 已登录（未获取到用户名）");
                return "已登录用户";
            }
        } catch (Exception e) {
            log.debug("[DeepSeek] 对话历史检测异常: {}", e.getMessage());
        }

        // 默认返回未登录
        log.debug("[DeepSeek] 所有检测完成 - 判定为未登录");
        return "false";
    }

    /**
     * 导航到DeepSeek登录页面并等待二维码加载
     * 
     * @param page Playwright页面实例
     * @return 是否成功导航并加载二维码
     */
    public boolean navigateToLoginPage(Page page) {
        try {
            log.info("[DeepSeek] 开始导航到登录页面");
            page.navigate(DEEPSEEK_LOGIN_URL, new Page.NavigateOptions()
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                .setTimeout(15000));
            
            log.debug("[DeepSeek] 页面基本加载完成，等待二维码加载");
            
            // 🔥 优化：使用waitForSelector直接等待二维码元素，最多等待5秒
            try {
                page.locator(".ds-sign-in-with-wechat-block").first().waitFor(new Locator.WaitForOptions()
                    .setTimeout(5000));
                log.info("[DeepSeek] 检测到微信登录区域");
                page.waitForTimeout(1000);
                log.info("[DeepSeek] 二维码加载完成，准备截图");
                return true;
            } catch (Exception e) {
                log.debug("[DeepSeek] 微信登录区域未找到，尝试检测iframe");
                try {
                    page.locator("iframe[src*='open.weixin.qq.com']").first().waitFor(new Locator.WaitForOptions()
                        .setTimeout(3000));
                    log.info("[DeepSeek] 检测到微信二维码iframe");
                    page.waitForTimeout(1000);
                    log.info("[DeepSeek] 二维码加载完成，准备截图");
                    return true;
                } catch (Exception ex) {
                    log.warn("[DeepSeek] 二维码加载超时，尝试备用方案");
                    return tryFallbackNavigation(page);
                }
            }
            
        } catch (Exception e) {
            log.error("[DeepSeek] 直接导航失败，尝试备用方案", e);
            return tryFallbackNavigation(page);
        }
    }
    
    /**
     * 备用导航方案：从主页点击登录按钮
     */
    private boolean tryFallbackNavigation(Page page) {
        try {
            log.info("[DeepSeek] 执行备用导航方案");
            page.navigate(DEEPSEEK_HOME_URL, new Page.NavigateOptions()
                .setWaitUntil(WaitUntilState.NETWORKIDLE)
                .setTimeout(30000));
            
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.waitForTimeout(2000);
            
            Locator loginButton = page.locator("button:has-text('登录'), button:has-text('Login')").first();
            if (loginButton.count() > 0 && loginButton.isVisible()) {
                log.info("[DeepSeek] 找到登录按钮，正在点击");
                loginButton.click();
                page.waitForTimeout(3000);
                
                log.debug("[DeepSeek] 已跳转到登录页面，等待二维码加载");
                for (int j = 0; j < 8; j++) {
                    page.waitForTimeout(1000);
                    Locator wechatBlock = page.locator(".ds-sign-in-with-wechat-block");
                    Locator iframe = page.locator("iframe[src*='open.weixin.qq.com']");
                    if (wechatBlock.count() > 0 || iframe.count() > 0) {
                        log.info("[DeepSeek] 备用方案检测到二维码");
                        page.waitForTimeout(2000);
                        return true;
                    }
                }
                
                log.warn("[DeepSeek] 备用方案二维码加载超时");
                return false;
            } else {
                log.error("[DeepSeek] 未找到登录按钮");
                return false;
            }
            
        } catch (Exception fallbackException) {
            log.error("[DeepSeek] 备用方案也失败", fallbackException);
            return false;
        }
    }

    /**
     * 发送消息到DeepSeek并等待回复
     * 
     * @param page Playwright页面实例
     * @param userPrompt 用户提示文本
     * @param enableDeepThinking 是否启用深度思考
     * @param enableWebSearch 是否启用联网搜索
     * @return AI回复内容
     */
    public String sendMessageAndWaitResponse(Page page, String userPrompt, 
                                            boolean enableDeepThinking, 
                                            boolean enableWebSearch) {
        try {
            log.info("[DeepSeek] 准备发送消息");
            hasClickedRefreshButton = false;
            
            // 不再强制导航到首页，保留当前页面（可能是续问的会话页）
            String currentUrl = page.url();
            log.debug("[DeepSeek] 当前页面: {}", currentUrl);
            
            // 等待页面稳定
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.waitForTimeout(1500);
            
            toggleButtonIfNeeded(page, "深度思考", enableDeepThinking);
            toggleButtonIfNeeded(page, "联网搜索", enableWebSearch);
            
            boolean inputSuccess = fillAndSendMessage(page, userPrompt);
            if (!inputSuccess) {
                throw new RuntimeException("发送消息失败：未找到输入框或发送失败");
            }
            
            log.info("[DeepSeek] 开始监听回复");
            String content = waitForResponse(page, enableDeepThinking, enableWebSearch);
            
            log.info("[DeepSeek] 回复接收完成，内容长度: {}", content != null ? content.length() : 0);
            return content;
            
        } catch (Exception e) {
            log.error("[DeepSeek] 发送消息或接收回复失败", e);
            throw new RuntimeException("DeepSeek操作失败: " + e.getMessage(), e);
        }
    }

    /**
     * 填充并发送消息
     * 
     * @param page Playwright页面对象
     * @param userPrompt 用户消息
     * @return 是否发送成功
     */
    private boolean fillAndSendMessage(Page page, String userPrompt) {
        try {
            Locator inputBox = null;
            boolean inputFound = false;
            
            String[] inputSelectors = {
                "textarea[placeholder*='给 DeepSeek 发送消息']",
                "textarea[placeholder*='Send a message']", 
                "textarea.ds-scroll-area",
                "textarea._27c9245",
                "#chat-input",
                ".chat-input",
                "textarea[rows='2']"
            };
            
            for (String selector : inputSelectors) {
                try {
                    inputBox = page.locator(selector).first();
                    if (inputBox.count() > 0 && inputBox.isVisible()) {
                        inputFound = true;
                        log.debug("[DeepSeek] 使用选择器找到输入框: {}", selector);
                        break;
                    }
                } catch (Exception e) {
                    // 继续尝试下一个选择器
                }
            }
            
            if (!inputFound) {
                try {
                    Object jsResult = page.evaluate("""
                        () => {
                            const textareas = document.querySelectorAll('textarea');
                            for (const textarea of textareas) {
                                if (textarea.placeholder && 
                                    (textarea.placeholder.includes('DeepSeek') || 
                                     textarea.placeholder.includes('发送消息') ||
                                     textarea.placeholder.includes('Send a message'))) {
                                    textarea.setAttribute('data-ai-input', 'true');
                                    return true;
                                }
                            }
                            return false;
                        }
                    """);
                    
                    if (Boolean.TRUE.equals(jsResult)) {
                        inputBox = page.locator("textarea[data-ai-input='true']").first();
                        if (inputBox.count() > 0 && inputBox.isVisible()) {
                            inputFound = true;
                            log.debug("[DeepSeek] 通过JavaScript找到输入框");
                        }
                    }
                } catch (Exception e) {
                    log.error("[DeepSeek] JavaScript查找输入框失败", e);
                }
            }
            
            if (inputFound && inputBox != null) {
                inputBox.click();
                page.waitForTimeout(500);
                
                inputBox.fill("");
                page.waitForTimeout(200);
                
                inputBox.fill(userPrompt);
                log.info("[DeepSeek] 用户指令已自动输入完成");
                
                int times = 3;
                String inputText = inputBox.textContent();
                while (inputText != null && !inputText.isEmpty()) {
                    inputBox.press("Enter");
                    inputText = inputBox.textContent();
                    page.waitForTimeout(1000);
                    if(times-- < 0) {
                        throw new RuntimeException("指令输入失败");
                    }
                }
                
                log.info("[DeepSeek] 消息发送成功");
                return true;
            } else {
                log.error("[DeepSeek] 未找到输入框");
                return false;
            }
        } catch (Exception e) {
            log.error("[DeepSeek] 填充或发送消息失败", e);
            return false;
        }
    }

    /**
     * 等待DeepSeek AI回答完成并提取内容
     * 
     * @param page Playwright页面实例
     * @param enableDeepThinking 是否启用深度思考模式
     * @param enableWebSearch 是否启用联网搜索模式
     * @return 获取的回答内容
     */
    private String waitForResponse(Page page, boolean enableDeepThinking, boolean enableWebSearch) {
        try {
            String currentContent = "";
            String lastContent = "";
            int stableCount = 0;
            int emptyCount = 0;
            int noChangeCount = 0;
            int[] contentLengthHistory = new int[3];
            boolean hasEverHadContent = false;
  
            long startTime = System.currentTimeMillis();
            page.waitForTimeout(500);
            
            long maxTimeout = 300000; // 默认5分钟
            int requiredStableCount = 1;
            int checkInterval = 200;
            
            if (enableDeepThinking && enableWebSearch) {
                maxTimeout = 1800000; // 30分钟
                requiredStableCount = 2;
                checkInterval = 450;
                log.info("[DeepSeek] 启用深度思考+联网模式，等待时间可能较长");
            } else if (enableDeepThinking) {
                maxTimeout = 1350000; // 22.5分钟
                requiredStableCount = 2;
                checkInterval = 375;
                log.info("[DeepSeek] 启用深度思考模式，等待时间可能较长");
            } else if (enableWebSearch) {
                maxTimeout = 900000; // 15分钟
                requiredStableCount = 2;
                checkInterval = 375;
                log.info("[DeepSeek] 启用联网搜索模式");
            }

            page.waitForTimeout(4000);
            log.info("[DeepSeek] 开始检测回复完成状态");

            long lastScreenshotTime = System.currentTimeMillis();
            int screenshotInterval = 6000;

            while (true) {
                if (page.isClosed()) {
                    log.error("[DeepSeek] 页面已关闭，停止监听");
                    throw new RuntimeException("页面在监控过程中被关闭");
                }
                
                long elapsedTime = System.currentTimeMillis() - startTime;
                if (elapsedTime > maxTimeout) {
                    log.warn("[DeepSeek] 超时，AI未完成回答或回答时间过长");
                    break;
                }

                if (System.currentTimeMillis() - lastScreenshotTime >= screenshotInterval) {
                    try {
                        screenshotUtil.capture(page, "deepseek_progress_" + (elapsedTime/1000/6 + 1));
                        lastScreenshotTime = System.currentTimeMillis();
                    } catch (Exception e) {
                        log.debug("[DeepSeek] 进度截图失败", e);
                    }
                }

                try {
                    checkAndClickRefreshButton(page);
                } catch (Exception e) {
                    log.debug("[DeepSeek] 刷新按钮检测失败", e);
                }

                Map<String, Object> responseData = getLatestDeepSeekResponseWithCompletion(page);
                currentContent = (String) responseData.getOrDefault("content", "");
                Object hasActionButtonsObj = responseData.get("hasActionButtons");
                boolean hasActionButtons = hasActionButtonsObj != null ? (Boolean) hasActionButtonsObj : false;
                int contentLength = 0;
                if (responseData.containsKey("length")) {
                    contentLength = ((Number) responseData.get("length")).intValue();
                }

                if (currentContent != null && !currentContent.trim().isEmpty()) {
                    hasEverHadContent = true;
                    emptyCount = 0;
                    
                    for (int i = contentLengthHistory.length - 1; i > 0; i--) {
                        contentLengthHistory[i] = contentLengthHistory[i-1];
                    }
                    contentLengthHistory[0] = contentLength;
                    
                    if (currentContent.equals(lastContent)) {
                        stableCount++;
                        
                        boolean isThinking = checkIfGenerating(page);
                        boolean isComplete = false;
                        
                        if (hasActionButtons) {
                            log.info("[DeepSeek] 检测到完成按钮组，回复已完成");
                            isComplete = true;
                        } else if (stableCount >= requiredStableCount && !isThinking) {
                            if (contentLength > 1000) {
                                log.debug("[DeepSeek] 长内容已稳定，准备提取");
                                isComplete = true;
                            } else if (contentLength > 500) {
                                noChangeCount++;
                                if (noChangeCount >= 2) {
                                    log.debug("[DeepSeek] 内容稳定，准备提取");
                                    isComplete = true;
                                }
                            } else if (isContentGrowthStopped(contentLengthHistory) && stableCount >= requiredStableCount) {
                                log.debug("[DeepSeek] 内容增长已停止，准备提取");
                                isComplete = true;
                            } else if (stableCount >= requiredStableCount + 1) {
                                log.debug("[DeepSeek] 短内容已稳定，准备提取");
                                isComplete = true;
                            }
                        }
                        
                        if (isComplete) {
                            log.info("[DeepSeek] 回答完成，正在自动提取内容");
                            break;
                        }
                    } else {
                        stableCount = 0;
                        noChangeCount = 0;
                        lastContent = currentContent;
                    }
                } else {
                    emptyCount++;
                    
                    if (emptyCount > 8) {
                        try {
                            Object errorResult = page.evaluate("""
                                () => {
                                    const errorElements = document.querySelectorAll('.error-message, .ds-error, [class*="error"]');
                                    for (const el of errorElements) {
                                        if (el.innerText && el.innerText.trim() && 
                                            window.getComputedStyle(el).display !== 'none') {
                                            return el.innerText.trim();
                                        }
                                    }
                                    return null;
                                }
                            """);
                            
                            if (errorResult instanceof String && !((String)errorResult).isEmpty()) {
                                log.error("[DeepSeek] 返回错误: {}", errorResult);
                                return "DeepSeek错误: " + errorResult;
                            }
                        } catch (Exception e) {
                            log.debug("[DeepSeek] 错误检测失败", e);
                        }
                        
                        if (!hasEverHadContent && emptyCount > 100) {
                            log.warn("[DeepSeek] 长时间未检测到回复，但继续等待...");
                        }
                        
                        if (hasEverHadContent && emptyCount == 10) {
                            log.debug("[DeepSeek] 内容暂时为空，继续等待...");
                        }
                    }
                }

                page.waitForTimeout(checkInterval);
                
                if (elapsedTime > 30000) {
                    checkInterval = Math.min(800, checkInterval + 50);
                }
            }

            // 🔥 优化：检测到回复完成后，直接点击复制按钮获取文本
            String finalContent = clickCopyButtonAndGetAnswer(page);
            
            if (finalContent == null || finalContent.trim().isEmpty()) {
                log.error("[DeepSeek] 点击复制按钮获取内容失败");
                if (!hasEverHadContent) {
                    return "DeepSeek超时未返回内容，请检查网络或账号状态";
                }
                // 如果曾经有内容但复制失败，返回错误提示
                return "DeepSeek回复完成，但获取内容失败，请手动查看";
            }
            
            log.info("[DeepSeek] 内容已通过复制按钮提取完成");
            return finalContent;

        } catch (Exception e) {
            log.error("[DeepSeek] 等待响应失败", e);
            throw new RuntimeException("等待DeepSeek响应失败: " + e.getMessage(), e);
        }
    }

    /**
     * 检查是否仍在生成内容
     */
    private boolean checkIfGenerating(Page page) {
        try {
            Object generatingStatus = page.evaluate("""
            () => {
                try {
                    const thinkingIndicators = document.querySelectorAll(
                        '.generating-indicator, .loading-indicator, .thinking-indicator, ' +
                        '.ds-typing-container, .ds-loading-dots, .loading-container, ' +
                        '[class*="loading"], [class*="typing"], [class*="generating"]'
                    );
                    
                    for (const indicator of thinkingIndicators) {
                        if (indicator && 
                            window.getComputedStyle(indicator).display !== 'none' && 
                            window.getComputedStyle(indicator).visibility !== 'hidden') {
                            return true;
                        }
                    }
                    
                    const stopButtons = document.querySelectorAll(
                        'button:contains("停止生成"), button:contains("Stop"), ' +
                        '[title="停止生成"], [title="Stop generating"], ' +
                        '.stop-generating-button, [class*="stop"]'
                    );
                    
                    for (const btn of stopButtons) {
                        if (btn && 
                            window.getComputedStyle(btn).display !== 'none' && 
                            window.getComputedStyle(btn).visibility !== 'hidden') {
                            return true;
                        }
                    }
                    
                    return false;
                } catch (e) {
                    console.error('检查生成状态时出错:', e);
                    return false;
                }
            }
            """);

            return generatingStatus instanceof Boolean ? (Boolean) generatingStatus : false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查内容增长是否已经停止
     */
    private boolean isContentGrowthStopped(int[] contentLengthHistory) {
        if (contentLengthHistory[0] > 0 && 
            Math.abs(contentLengthHistory[0] - contentLengthHistory[1]) <= 5 && 
            Math.abs(contentLengthHistory[1] - contentLengthHistory[2]) <= 5) {
            return true;
        }
        return false;
    }

    /**
     * 检查并点击刷新按钮（当检测到网络错误时）
     */
    private void checkAndClickRefreshButton(Page page) {
        if (hasClickedRefreshButton) {
            return;
        }
        
        try {
            Object result = page.evaluate("""
                () => {
                    const buttons = document.querySelectorAll('button');
                    for (const button of buttons) {
                        if (button.textContent.includes('刷新') || button.textContent.includes('Refresh') ||
                            button.textContent.includes('重试') || button.textContent.includes('Retry')) {
                            return true;
                        }
                    }
                    return false;
                }
            """);
            
            if (Boolean.TRUE.equals(result)) {
                page.evaluate("""
                    () => {
                        const buttons = document.querySelectorAll('button');
                        for (const button of buttons) {
                            if (button.textContent.includes('刷新') || button.textContent.includes('Refresh') ||
                                button.textContent.includes('重试') || button.textContent.includes('Retry')) {
                                button.click();
                                return;
                            }
                        }
                    }
                """);
                hasClickedRefreshButton = true;
                log.info("[DeepSeek] 检测到刷新按钮并已点击");
                page.waitForTimeout(2000);
            }
        } catch (Exception e) {
            log.debug("[DeepSeek] 刷新按钮检测失败", e);
        }
    }

    /**
     * 通用方法：根据目标激活状态切换按钮（深度思考/联网搜索）
     */
    private void toggleButtonIfNeeded(Page page, String buttonText, boolean shouldActive) {
        try {
            String buttonSelector = String.format(
                "button.ds-toggle-button:has-text('%s'), div[role='button'].ds-toggle-button:has-text('%s')", 
                buttonText, buttonText);

            Locator button = page.locator(buttonSelector).first();
            button.waitFor(new Locator.WaitForOptions().setTimeout(10000));

            if (!button.isVisible()) {
                log.warn("[DeepSeek] {}按钮不可见", buttonText);
                return;
            }

            String currentClasses = (String) button.evaluate("el => el.className");
            boolean isCurrentlyActive = currentClasses.contains("ds-toggle-button--selected");

            if (isCurrentlyActive != shouldActive) {
                button.click(new Locator.ClickOptions().setTimeout(5000).setForce(true));

                boolean stateChanged = false;
                for (int i = 0; i < 10; i++) {
                    page.waitForTimeout(200);

                    String newClasses = (String) button.evaluate("el => el.className");
                    boolean isNowActive = newClasses.contains("ds-toggle-button--selected");

                    if (isNowActive == shouldActive) {
                        stateChanged = true;
                        break;
                    }
                }

                if (stateChanged) {
                    log.info("[DeepSeek] {}{}模式", shouldActive ? "已启动" : "已关闭", buttonText);
                } else {
                    log.warn("[DeepSeek] {}模式切换失败，状态未改变", buttonText);
                }
            } else {
                log.debug("[DeepSeek] {}模式已经是{}状态", buttonText, shouldActive ? "开启" : "关闭");
            }
        } catch (Exception e) {
            log.warn("[DeepSeek] 切换{}模式失败: {}", buttonText, e.getMessage());
        }
    }

    /**
     * 获取最新的DeepSeek回答内容，并检查是否包含完成按钮组
     */
    private Map<String, Object> getLatestDeepSeekResponseWithCompletion(Page page) {
        try {
            Object jsResult = page.evaluate("""
            () => {
                try {
                    const responseContainers = document.querySelectorAll('div._4f9bf79.d7dc56a8._43c05b5');
                    if (responseContainers.length === 0) {
                        return {
                            content: '',
                            textContent: '',
                            length: 0,
                            hasActionButtons: false,
                            source: 'no-response-containers',
                            timestamp: Date.now()
                        };
                    }
                    
                    const latestContainer = responseContainers[responseContainers.length - 1];
                    
                    const actionButtonsSelector = 'div.ds-flex._0a3d93b[style*="align-items: center; gap: 10px"] div.ds-flex._965abe9._54866f7';
                    const hasActionButtons = latestContainer.querySelector(actionButtonsSelector) !== null;
                    
                    let markdownElement = latestContainer.querySelector('.ds-markdown-html');
                    let isHtmlContent = true;
                    
                    if (!markdownElement) {
                        markdownElement = latestContainer.querySelector('.ds-markdown');
                        isHtmlContent = false;
                    }
                    
                    if (!markdownElement) {
                        return {
                            content: '',
                            textContent: '',
                            length: 0,
                            hasActionButtons: hasActionButtons,
                            source: 'no-markdown-in-container',
                            timestamp: Date.now()
                        };
                    }
                    
                    const contentClone = markdownElement.cloneNode(true);
                    
                    const elementsToRemove = contentClone.querySelectorAll(
                        'svg, .ds-icon, button, [role="button"], ' +
                        '[class*="loading"], [class*="typing"], [class*="cursor"], ' +
                        '.md-code-block-banner, .code-info-button-text'
                    );
                    elementsToRemove.forEach(el => el.remove());
                    
                    const textContent = contentClone.textContent || '';
                    const contentLength = textContent.trim().length;
                    
                    return {
                        content: contentClone.innerHTML,
                        textContent: textContent,
                        length: contentLength,
                        hasActionButtons: hasActionButtons,
                        source: isHtmlContent ? 'ds-markdown-html-only' : 'ds-markdown-full',
                        timestamp: Date.now()
                    };
                } catch (e) {
                    return {
                        content: '',
                        textContent: '',
                        length: 0,
                        hasActionButtons: false,
                        source: 'error',
                        error: e.toString(),
                        timestamp: Date.now()
                    };
                }
            }
            """);

            if (jsResult instanceof Map) {
                return (Map<String, Object>) jsResult;
            }
        } catch (Exception e) {
            log.error("[DeepSeek] 获取回答时出错", e);
        }

        return new HashMap<>();
    }

    /**
     * 获取最后一组对话内容
     */
    private String getLastConversationContent(Page page) {
        try {
            log.debug("[DeepSeek] 开始获取最后一组对话内容");
            
            Object jsResult = page.evaluate("""
            () => {
                try {
                    const responseContainers = document.querySelectorAll('div._4f9bf79.d7dc56a8._43c05b5');
                    if (responseContainers.length === 0) {
                        return { content: '', source: 'no-containers' };
                    }
                    
                    const latestContainer = responseContainers[responseContainers.length - 1];
                    const containerClone = latestContainer.cloneNode(true);
                    
                    const elementsToRemove = containerClone.querySelectorAll(
                        'button, [role="button"], ' +
                        '[class*="loading"], [class*="typing"], [class*="cursor"], ' +
                        '.code-info-button-text, ._17e543b'
                    );
                    elementsToRemove.forEach(el => el.remove());
                    
                    const emptyDivs = containerClone.querySelectorAll('div:empty');
                    emptyDivs.forEach(div => div.remove());
                    
                    const cleanedContent = containerClone.innerHTML;
                    
                    return {
                        content: cleanedContent,
                        source: 'last-conversation-cleaned',
                        timestamp: Date.now()
                    };
                } catch (e) {
                    return {
                        content: '',
                        source: 'error',
                        error: e.toString()
                    };
                }
            }
            """);

            if (jsResult instanceof Map) {
                Map<String, Object> result = (Map<String, Object>) jsResult;
                String content = (String) result.getOrDefault("content", "");
                if (!content.trim().isEmpty()) {
                    log.debug("[DeepSeek] 成功获取最后一组对话内容");
                    return content;
                }
            }
            
            return "";
            
        } catch (Exception e) {
            log.error("[DeepSeek] 获取对话内容失败", e);
            return "";
        }
    }

    /**
     * 点击复制按钮并获取纯回答内容（过滤思考过程）
     */
    private String clickCopyButtonAndGetAnswer(Page page) {
        try {
            log.debug("[DeepSeek] 正在点击复制按钮获取回答内容");
            
            Object result = page.evaluate("""
                () => {
                    try {
                        // 🔥 方法1：通过最新回复容器查找（优先）
                        const responseContainers = document.querySelectorAll('div._4f9bf79.d7dc56a8._43c05b5');
                        if (responseContainers.length > 0) {
                            const latestContainer = responseContainers[responseContainers.length - 1];
                            
                            // 查找外层按钮组容器（新DOM结构）
                            const outerContainer = latestContainer.querySelector('div.ds-flex._0a3d93b[style*="align-items: center"]');
                            if (outerContainer) {
                                // 在外层容器内查找所有按钮
                                const buttons = outerContainer.querySelectorAll('div.ds-icon-button[role="button"]');
                                for (let button of buttons) {
                                    // 检查是否是复制按钮（通过SVG路径识别）
                                    const copyIcon = button.querySelector('svg path[d*="M6.14"]');
                                    if (copyIcon) {
                                        button.click();
                                        return { success: true, message: 'copy-button-clicked-new-structure' };
                                    }
                                }
                            }
                            
                            // 旧DOM结构兼容
                            const actionButtonsContainer = latestContainer.querySelector('div.ds-flex._965abe9._54866f7[style*="align-items: center"]');
                            if (actionButtonsContainer) {
                                const buttons = actionButtonsContainer.querySelectorAll('div.ds-icon-button[role="button"]');
                                for (let button of buttons) {
                                    const copyIcon = button.querySelector('svg path[d*="M6.14"]');
                                    if (copyIcon) {
                                        button.click();
                                        return { success: true, message: 'copy-button-clicked-old-structure' };
                                    }
                                }
                            }
                        }
                        
                        // 🔥 方法2：全局查找最后一个复制按钮（回退方案）
                        const allContainers = document.querySelectorAll('div.ds-flex[style*="align-items: center"]');
                        for (let i = allContainers.length - 1; i >= 0; i--) {
                            const container = allContainers[i];
                            const copyButtons = container.querySelectorAll('div.ds-icon-button[role="button"]');
                            for (let button of copyButtons) {
                                const copyIcon = button.querySelector('svg path[d*="M6.14"]');
                                if (copyIcon) {
                                    button.click();
                                    return { success: true, message: 'copy-button-clicked-global-search' };
                                }
                            }
                        }
                        
                        return { success: false, error: 'copy-button-not-found' };
                    } catch (e) {
                        return { success: false, error: e.toString() };
                    }
                }
            """);
            
            if (result instanceof Map) {
                Map<String, Object> resultMap = (Map<String, Object>) result;
                Boolean success = (Boolean) resultMap.get("success");
                
                if (Boolean.TRUE.equals(resultMap.get("success"))) {
                    // 等待剪贴板操作完成
                    page.waitForTimeout(2000);
                    
                    // 读取剪贴板内容
                    String clipboardContent = clipboardManager.read(page);
                    
                    if (clipboardContent != null && !clipboardContent.trim().isEmpty()) {
                        log.debug("[DeepSeek] 成功获取剪贴板内容");
                        return clipboardContent;
                    } else {
                        log.warn("[DeepSeek] 剪贴板内容为空");
                        return "";
                    }
                } else {
                    log.warn("[DeepSeek] 复制按钮点击失败");
                    return "";
                }
            }
            
            return "";
        } catch (Exception e) {
            log.error("[DeepSeek] 点击复制按钮失败", e);
            return "";
        }
    }

    /**
     * 提取会话ID（从URL中）
     * 
     * @param page Playwright页面对象
     * @return 会话ID，如果提取失败返回null
     */
    public String extractChatId(Page page) {
        try {
            String currentUrl = page.url();
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("/chat/s/([^/?#]+)");
            java.util.regex.Matcher matcher = pattern.matcher(currentUrl);
            if (matcher.find()) {
                String chatId = matcher.group(1);
                log.info("[DeepSeek] 提取到会话ID: {}", chatId);
                return chatId;
            }
        } catch (Exception e) {
            log.error("[DeepSeek] 提取会话ID失败", e);
        }
        return null;
    }

    /**
     * 导航到指定会话
     * 
     * @param page Playwright页面对象
     * @param chatId 会话ID
     * @return 是否导航成功
     */
    public boolean navigateToChat(Page page, String chatId) {
        if (chatId == null || chatId.isEmpty()) {
            log.warn("[DeepSeek] 会话ID为空，导航到首页");
            page.navigate(DEEPSEEK_HOME_URL);
            return true;
        }
        
        try {
            log.info("[DeepSeek] 导航到会话: {}", chatId);
            page.navigate("https://chat.deepseek.com/a/chat/s/" + chatId, 
                new Page.NavigateOptions()
                .setTimeout(15000)
                .setWaitUntil(WaitUntilState.NETWORKIDLE));
            
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.waitForTimeout(2000);
            
            log.info("[DeepSeek] 成功导航到会话，等待页面稳定");
            return true;
        } catch (Exception e) {
            log.error("[DeepSeek] 导航到会话失败: {}", e.getMessage());
            return false;
        }
    }
}
