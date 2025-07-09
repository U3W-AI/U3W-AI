package com.playwright.utils;

import com.alibaba.fastjson.JSONObject;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import com.playwright.entity.UserInfoRequest;
import com.playwright.websocket.WebSocketClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * DeepSeek AI平台工具类
 * @author 优立方
 * @version JDK 17
 * &#064;date  2025年06月15日 10:33
 */
@Component
public class DeepSeekUtil {

    @Autowired
    private LogMsgUtil logInfo;
    
    @Autowired
    private WebSocketClientService webSocketClientService;
    
    @Autowired
    private ClipboardLockManager clipboardLockManager;
    
    @Value("${cube.url}")
    private String url;
    
    @Autowired
    private ScreenshotUtil screenshotUtil;

    /**
     * 检查DeepSeek登录状态
     * @param page Playwright页面对象
     * @param navigate 是否需要先导航到DeepSeek页面
     * @return 登录状态，如果已登录则返回用户名，否则返回"false"
     */
    public String checkLoginStatus(Page page, boolean navigate) {
        try {
            if (navigate) {
                page.navigate("https://chat.deepseek.com/");
                page.waitForLoadState();
                page.waitForTimeout(2000); // 增加等待时间确保页面完全加载
            }
            
            // 检查是否有登录按钮，如果有则表示未登录
            try {
                Locator loginBtn = page.locator("button:has-text('登录'), button:has-text('Login')").first();
                if (loginBtn.count() > 0 && loginBtn.isVisible()) {
                    return "false";
                }
            } catch (Exception e) {
                // 忽略检查错误
            }
            
            // 特别针对用户昵称"Obvious"的检测
            try {
                // 点击头像显示下拉菜单
                Locator avatarLocator = page.locator("img.fdf01f38").first();
                if (avatarLocator.count() > 0 && avatarLocator.isVisible()) {
                    avatarLocator.click();
                    page.waitForTimeout(1500); // 增加等待时间确保下拉菜单显示
                    
                    // 优先检查pending选项，这通常包含用户昵称
                    Locator pendingOption = page.locator(".ds-dropdown-menu-option--pending").first();
                    if (pendingOption.count() > 0 && pendingOption.isVisible()) {
                        Locator nameLabel = pendingOption.locator(".ds-dropdown-menu-option__label").first();
                        if (nameLabel.count() > 0) {
                            String name = nameLabel.textContent();
                            if (name != null && !name.trim().isEmpty() && 
                                !name.trim().equals("登录") && !name.trim().equals("Login")) {
                                System.out.println("从pending选项找到用户昵称: " + name.trim());
                                return name.trim();
                            }
                        }
                    }
                    
                    // 检查所有可能的标签，找出用户昵称
                    Locator allLabels = page.locator(".ds-dropdown-menu-option__label");
                    for (int i = 0; i < allLabels.count(); i++) {
                        String labelText = allLabels.nth(i).textContent();
                        if (labelText != null && !labelText.trim().isEmpty() && 
                            !labelText.trim().equals("登录") && !labelText.trim().equals("Login") &&
                            !labelText.trim().equals("系统设置") && !labelText.trim().equals("联系我们") && 
                            !labelText.trim().equals("退出登录")) {
                            System.out.println("从所有标签中找到用户昵称: " + labelText.trim());
                            return labelText.trim();
                        }
                    }
                    
                    // 使用JavaScript进行更深入的检测
                    Object userName = page.evaluate("""
                        () => {
                            try {
                                // 特别针对"Obvious"用户名的检测
                                const allElements = document.querySelectorAll('*');
                                for (const el of allElements) {
                                    if (el.textContent && el.textContent.trim() === 'Obvious') {
                                        return 'Obvious';
                                    }
                                }
                                
                                // 检查pending选项
                                const pendingOption = document.querySelector('.ds-dropdown-menu-option--pending');
                                if (pendingOption) {
                                    const nameLabel = pendingOption.querySelector('.ds-dropdown-menu-option__label');
                                    if (nameLabel && nameLabel.textContent) {
                                        return nameLabel.textContent.trim();
                                    }
                                }
                                
                                // 检查所有可能的标签
                                const allLabels = document.querySelectorAll('.ds-dropdown-menu-option__label');
                                for (const label of allLabels) {
                                    if (label && label.textContent && 
                                        label.textContent.trim() !== '登录' && 
                                        label.textContent.trim() !== 'Login' &&
                                        label.textContent.trim() !== '系统设置' &&
                                        label.textContent.trim() !== '联系我们' &&
                                        label.textContent.trim() !== '退出登录') {
                                        return label.textContent.trim();
                                    }
                                }
                                
                                // 检查是否有退出登录选项，如果有则表示已登录
                                const logoutOption = Array.from(allLabels).find(label => 
                                    label.textContent && 
                                    (label.textContent.trim() === '退出登录' || 
                                     label.textContent.trim() === 'Logout'));
                                     
                                if (logoutOption) {
                                    return '已登录用户';
                                }
                                
                                return null;
                            } catch (e) {
                                console.error('JS检测用户名出错:', e);
                                return null;
                            }
                        }
                    """);
                    
                    if (userName != null && !userName.toString().equals("null")) {
                        System.out.println("通过JS找到用户昵称: " + userName.toString());
                        return userName.toString();
                    }
                    
                    // 即使未找到昵称，也已确认已登录
                    return "已登录用户";
                }
            } catch (Exception e) {
                System.out.println("检测用户昵称失败: " + e.getMessage());
            }
            
            // 最后尝试使用通用方法检测登录状态
            try {
                // 检查是否有新建聊天按钮或其他已登录状态的标志
                Locator newChatBtn = page.locator("button:has-text('新建聊天'), button:has-text('New Chat')").first();
                if (newChatBtn.count() > 0 && newChatBtn.isVisible()) {
                    return "已登录用户";
                }
                
                // 检查是否有聊天历史记录
                Locator chatHistory = page.locator(".conversation-list, .chat-history").first();
                if (chatHistory.count() > 0 && chatHistory.isVisible()) {
                    return "已登录用户";
                }
            } catch (Exception e) {
                // 忽略检查错误
            }
            
            // 默认返回未登录状态
            return "false";
        } catch (Exception e) {
            System.out.println("检查DeepSeek登录状态出错: " + e.getMessage());
            return "false";
        }
    }

    /**
     * 等待并获取DeepSeek二维码
     * @param page Playwright页面对象
     * @param userId 用户ID
     * @param screenshotUtil 截图工具
     * @return 二维码截图URL
     */
    public String waitAndGetQRCode(Page page, String userId, ScreenshotUtil screenshotUtil) {
        try {
            logInfo.sendTaskLog("正在获取DeepSeek登录二维码", userId, "DeepSeek");
            
            // 导航到DeepSeek登录页面
            page.navigate("https://chat.deepseek.com/");
            page.waitForLoadState();
            
            // 直接截图当前页面（包含登录按钮）
            String url = screenshotUtil.screenshotAndUpload(page, "checkDeepSeekLogin.png");
            
            // 尝试点击登录按钮
            try {
                // 简单的选择器，尝试点击登录按钮
                String[] loginSelectors = {
                    "button:has-text('登录')",
                    "button:has-text('Login')",
                    "a:has-text('登录')",
                    "a:has-text('Login')"
                };
                
                boolean clicked = false;
                for (String selector : loginSelectors) {
                    try {
                        Locator loginBtn = page.locator(selector).first();
                        if (loginBtn.count() > 0 && loginBtn.isVisible()) {
                            loginBtn.click();
                            clicked = true;
                            Thread.sleep(500); // 从1000ms减少到500ms
                            break;
                        }
                    } catch (Exception e) {
                        // 忽略点击错误
                    }
                }
                
                // 如果没有找到登录按钮，尝试使用JavaScript点击
                if (!clicked) {
                    page.evaluate("""
                            () => {\s
                              const buttons = document.querySelectorAll('button, a');
                              for (const btn of buttons) {
                                if (btn.innerText && (btn.innerText.includes('登录') || btn.innerText.includes('Login'))) {
                                  btn.click();
                                  return true;
                                }
                              }
                              return false;
                            }""");
                    Thread.sleep(500); // 从1000ms减少到500ms
                }
                
                // 再次截图（可能已经显示二维码）
                url = screenshotUtil.screenshotAndUpload(page, "checkDeepSeekLogin.png");
            } catch (Exception e) {
                // 出错也返回第一次的截图
            }
            
            logInfo.sendTaskLog("DeepSeek二维码获取成功", userId, "DeepSeek");
            return url;
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return "false";
        }
    }

    /**
     * 等待DeepSeek AI回答完成并提取内容
     * @param page Playwright页面实例
     * @param userId 用户ID
     * @param aiName AI名称
     * @param roles 角色信息，用于判断是否为深度思考模式
     * @return 获取的回答内容
     */
    public String waitDeepSeekResponse(Page page, String userId, String aiName, String roles) {
        try {
            // 等待页面内容稳定
            String currentContent = "";
            String lastContent = "";
            boolean isContentStable = false;
            int stableCount = 0;
            
            // 设置最大等待时间（单位：毫秒），比如 10 分钟
            long timeout = 600000; // 10 分钟
            long startTime = System.currentTimeMillis();
            long lastScreenshotTime = 0; // 记录上次截图时间
            int screenshotCount = 0; // 截图计数
            
            // 添加初始延迟，确保页面完全加载，避免检测到上次会话的复制按钮
            // 设置初始等待时间为2000ms，确保页面基本加载
            page.waitForTimeout(2000);  // 初始等待2秒，确保页面基本加载
            
            // 重置消息发送时间戳，避免历史值干扰
            page.evaluate("() => { window._deepseekMessageSentTime = Date.now(); console.log('设置消息发送时间戳: ' + window._deepseekMessageSentTime); }");
            
            // 记录开始时间，用于强制等待最小时间
            long messageStartTime = System.currentTimeMillis();
            
            // 记录初始状态，用于后续比较
            boolean initialPageLoaded = false;
            int initialCopyButtonCount = 0;
            
            try {
                // 记录初始复制按钮数量
                Object initialCounts = page.evaluate("""
                    () => {
                        const regularButtons = document.querySelectorAll('.ds-icon-button, [data-testid="copy-button"], .copy-button');
                        const specificButtons = document.querySelectorAll('div.ds-flex .ds-icon-button');
                        return {
                            regularCount: regularButtons.length,
                            specificCount: specificButtons.length
                        };
                    }
                """);
                
                if (initialCounts instanceof Map) {
                    Map<String, Object> counts = (Map<String, Object>) initialCounts;
                    int regularCount = ((Number) counts.getOrDefault("regularCount", 0)).intValue();
                    int specificCount = ((Number) counts.getOrDefault("specificCount", 0)).intValue();
                    initialCopyButtonCount = regularCount + specificCount;
                    
                    System.out.println("页面初始状态 - 复制按钮数量: " + initialCopyButtonCount);
                    initialPageLoaded = true;
                }
            } catch (Exception e) {
                System.out.println("获取初始页面状态时出错: " + e.getMessage());
            }
            
            // 进入循环，直到内容不再变化或者超时
            while (true) {
                // 获取当前时间戳
                long elapsedTime = System.currentTimeMillis() - startTime;
                
                // 如果超时，退出循环
                // 深度思考模式下延长超时时间
                boolean isDeepThinkingMode = roles != null && roles.contains("ds-sdsk");
                long maxTimeout = isDeepThinkingMode ? 1200000 : 600000; // 深度思考模式20分钟，普通模式10分钟
                
                if (elapsedTime > maxTimeout) {
                    logInfo.sendTaskLog("超时，AI未完成回答！", userId, aiName);
                    break;
                }
                
                // 尝试获取最新内容 - 优化DOM处理逻辑
                try {
                    // 使用JavaScript精确定位最新回答内容，避免获取历史回复
                    Object jsContent = page.evaluate("""
                        () => {
                            try {
                                // 1. 首先尝试找到当前会话的最后一个AI回复消息容器
                                const allMessages = document.querySelectorAll('.ds-chat-message-group');
                                const latestAiMessage = Array.from(allMessages)
                                    .filter(msg => msg.classList.contains('ds-chat-message-group--ai'))
                                    .pop();
                                
                                if (latestAiMessage) {
                                    // 找到最新AI消息中的Markdown内容容器
                                    const markdownContent = latestAiMessage.querySelector('.ds-markdown, .flow-markdown-body, .ds-markdown--block');
                                    if (markdownContent) {
                                        return {
                                            content: markdownContent.innerHTML,
                                            source: 'latest-ai-message',
                                            timestamp: Date.now()
                                        };
                        }
                    }
                    
                                // 2. 尝试通过时间戳或位置找到最新回复
                                const messageContainers = document.querySelectorAll('.ds-chat-message-group--ai, .ai-message-container');
                                if (messageContainers.length > 0) {
                                    // 获取最后一个AI消息
                                    const lastContainer = messageContainers[messageContainers.length - 1];
                                    const contentElement = lastContainer.querySelector('.ds-markdown, .flow-markdown-body, .message-content');
                                    
                                    if (contentElement) {
                                        return {
                                            content: contentElement.innerHTML,
                                            source: 'last-container',
                                            timestamp: Date.now()
                                        };
                                    }
                                }
                                
                                // 3. 回退方案：查找所有可能的内容元素，并尝试确定哪个是最新的
                                const allContentElements = document.querySelectorAll('.ds-markdown, .flow-markdown-body, .ds-markdown--block, .message-content, .ai-message-content');
                                if (allContentElements.length > 0) {
                                    // 获取最后一个内容元素
                                    const lastElement = allContentElements[allContentElements.length - 1];
                                    
                                    // 检查是否在用户输入区域上方（即在聊天流中）
                                    const inputArea = document.querySelector('#chat-input, .chat-input-container');
                                    const isAboveInput = inputArea && 
                                        lastElement.getBoundingClientRect().bottom < inputArea.getBoundingClientRect().top;
                                    
                                    if (isAboveInput) {
                                        return {
                                            content: lastElement.innerHTML,
                                            source: 'content-element',
                                            timestamp: Date.now()
                                        };
                                    }
                                }
                                
                                // 4. 最后尝试：查找具有特定类的最新消息
                                const specificClassMessages = document.querySelectorAll('div._4f9bf79, div.d7dc56a8, div._43c05b5');
                                if (specificClassMessages.length > 0) {
                                    const lastSpecificMessage = specificClassMessages[specificClassMessages.length - 1];
                                    return {
                                        content: lastSpecificMessage.innerHTML,
                                        source: 'specific-class',
                                        timestamp: Date.now()
                                    };
                                }
                                
                                return {
                                    content: '',
                                    source: 'not-found',
                                    timestamp: Date.now()
                                };
                            } catch (e) {
                                return {
                                    content: '',
                                    source: 'error',
                                    error: e.toString(),
                                    timestamp: Date.now()
                                };
                            }
                            }
                        """);
                        
                    if (jsContent instanceof Map) {
                        Map<String, Object> contentData = (Map<String, Object>) jsContent;
                        String content = (String) contentData.getOrDefault("content", "");
                        String source = (String) contentData.getOrDefault("source", "unknown");
                        
                        if (content != null && !content.isEmpty()) {
                            currentContent = content;
                            // 移除监听中的日志输出
                        }
                    }
                } catch (Exception e) {
                    // 忽略内容提取错误
                    System.out.println("提取内容时出错: " + e.getMessage());
                }
                
                // 检查是否仍在生成内容
                boolean isGenerating = false;
                try {
                    Locator generatingIndicator = page.locator(".generating-indicator, .loading-indicator, .thinking-indicator").first();
                    isGenerating = generatingIndicator.count() > 0 && generatingIndicator.isVisible();
                    
                    // 深度思考模式下的特殊检测
                    if (!isGenerating) {
                        // 检查是否有"思考中"或"正在搜索"等指示器
                        Object thinkingStatus = page.evaluate("""
                            () => {
                                // 检查思考中指示器
                                const thinkingIndicators = document.querySelectorAll('.ds-typing-container, .ds-loading-dots, .loading-container');
                                let isThinking = false;
                                
                                for (const indicator of thinkingIndicators) {
                                    if (indicator && window.getComputedStyle(indicator).display !== 'none') {
                                        isThinking = true;
                                        break;
                                    }
                                }
                                
                                // 检查网络搜索指示器
                                const searchingIndicators = document.querySelectorAll('.search-status, .network-loading');
                                let isSearching = false;
                                
                                for (const indicator of searchingIndicators) {
                                    if (indicator && window.getComputedStyle(indicator).display !== 'none') {
                                        isSearching = true;
                                        break;
                                    }
                                }
                                
                                // 检查停止生成按钮
                                const stopButtons = document.querySelectorAll('button:contains("停止生成"), [title="停止生成"], .stop-generating-button');
                                let hasStopButton = false;
                                
                                for (const btn of stopButtons) {
                                    if (btn && window.getComputedStyle(btn).display !== 'none' && window.getComputedStyle(btn).visibility !== 'hidden') {
                                        hasStopButton = true;
                                        break;
                                    }
                                }
                                
                                return { isThinking, isSearching, hasStopButton };
                            }
                        """);
                        
                        if (thinkingStatus instanceof Map) {
                            Map<String, Object> status = (Map<String, Object>) thinkingStatus;
                            boolean isThinking = (boolean) status.getOrDefault("isThinking", false);
                            boolean isSearching = (boolean) status.getOrDefault("isSearching", false);
                            boolean hasStopButton = (boolean) status.getOrDefault("hasStopButton", false);
                            
                            if (isThinking || isSearching || hasStopButton) {
                                isGenerating = true;
                                if (isThinking) {
                        System.out.println("检测到AI仍在思考中...");
                                }
                                if (isSearching) {
                        System.out.println("检测到AI正在进行联网搜索...");
                                }
                                if (hasStopButton) {
                        System.out.println("检测到停止生成按钮，AI仍在生成回答...");
                                }
                            } else {
                                // 所有指示器都不存在，可能表示生成已完成
                                long currentElapsedTime = System.currentTimeMillis() - startTime;
                                if (currentElapsedTime > 20000) { // 确保至少20秒后再做此判断
                        System.out.println("所有生成指示器均已消失，AI可能已完成回答");
                                    // 这里不立即结束，让下面的稳定性检查和复制按钮检查来确认
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // 忽略指示器检查错误
                }
                
                // 如果当前内容和上次内容相同，并且不为空，可能表示AI已经完成回答
                if (currentContent.equals(lastContent) && !currentContent.isEmpty()) {
                    stableCount++;
                    
                    // 计算从开始检测到现在的时间（毫秒）
                    long buttonDetectionElapsedTime = System.currentTimeMillis() - startTime;
                    
                    // 如果内容连续3次保持稳定，或者明确检测到生成已完成
                    // 增强深度思考模式的支持，提高稳定计数要求
                    int requiredStableCount = isDeepThinkingMode ? 7 : 3; // 深度思考模式需要更长的稳定时间
                    long requiredMinTime = isDeepThinkingMode ? 30000 : 15000; // 深度思考模式至少等待30秒，普通模式至少等待15秒
                    
                    // 内容长度检查，对于深度思考模式，要求内容更丰富才认为可能完成
                    int minContentLength = isDeepThinkingMode ? 1000 : 200;
                    boolean hasRichContent = currentContent.length() >= minContentLength;
                    
                    // 如果深度思考模式下内容不够丰富，增加等待时间
                    if (isDeepThinkingMode && !hasRichContent && buttonDetectionElapsedTime < 60000) {
                            // 删除冗余日志输出
                        stableCount = Math.min(stableCount, 3); // 限制稳定计数增长
                    }
                    
                    // 检查是否有"停止生成"按钮存在，如果存在则表示仍在生成中
                    boolean stopGenerationButtonExists = false;
                    try {
                        Object stopButtonExists = page.evaluate("""
                            () => {
                                // 查找停止生成按钮
                                const stopButtons = document.querySelectorAll('[title="停止生成"], button:contains("停止生成"), .stop-generating-button');
                                return stopButtons.length > 0 && Array.from(stopButtons).some(btn => 
                                    window.getComputedStyle(btn).display !== 'none' && 
                                    window.getComputedStyle(btn).visibility !== 'hidden'
                                );
                            }
                        """);
                        
                        if (stopButtonExists instanceof Boolean) {
                            stopGenerationButtonExists = (Boolean) stopButtonExists;
                            if (stopGenerationButtonExists) {
                                    System.out.println("检测到停止生成按钮，AI仍在生成回答中...");
                                isGenerating = true; // 强制认为仍在生成
                                stableCount = 0; // 重置稳定计数
                            }
                        }
                    } catch (Exception e) {
                        // 忽略检测停止按钮时的错误
                    }
                    
                    // 只有当达到所需稳定计数、经过最小时间，且没有检测到"停止生成"按钮时，才继续检查复制按钮
                    if ((stableCount >= requiredStableCount && buttonDetectionElapsedTime > requiredMinTime && !stopGenerationButtonExists) || !isGenerating) {
                        // 检查是否出现了复制按钮或其他表示完成的指标
                        try {
                            // 使用JavaScript检测复制按钮，包括特定CSS类的检测和区分历史回答与当前回答
                            Object copyButtonDetails = page.evaluate("""
                                (initialButtonCount) => {
                                    // 检查常规复制按钮
                                    const regularCopyButtons = document.querySelectorAll('.ds-icon-button, [data-testid="copy-button"], .copy-button');
                                    
                                    // 检查特定CSS类的复制按钮和回答区域
                                    const specificClassButtons = document.querySelectorAll('div.ds-flex .ds-icon-button');
                                    const specificClassAreas = document.querySelectorAll('div._4f9bf79.d7dc56a8._43c05b5');
                                    
                                    // 找出最后一个回答区域
                                    let lastAnswer = null;
                                    if (specificClassAreas.length > 0) {
                                        lastAnswer = specificClassAreas[specificClassAreas.length - 1];
                                    } else {
                                        // 尝试其他可能的回答区域选择器
                                        const otherAnswerSelectors = [
                                            'div._4f9bf79', 
                                            'div.d7dc56a8', 
                                            'div._43c05b5',
                                            '.ds-markdown--block',
                                            '.message-content',
                                            '.ai-message-content'
                                        ];
                                        
                                        for (const selector of otherAnswerSelectors) {
                                            const elements = document.querySelectorAll(selector);
                                            if (elements.length > 0) {
                                                lastAnswer = elements[elements.length - 1];
                                break;
                                            }
                                        }
                                    }
                                    
                                    // 检查当前按钮总数是否大于初始按钮数
                                    const currentButtonCount = regularCopyButtons.length + specificClassButtons.length;
                                    const hasNewButtons = currentButtonCount > initialButtonCount;
                                    
                                    // 检查该区域是否包含复制按钮
                                    let foundCopyButton = false;
                                    let isInLatestAnswer = false;
                                    let buttonInfo = { type: 'none', position: null };
                                    
                                    // 检查是否有复制按钮在最后一个回答区域内
                                    if (lastAnswer) {
                                        // 检查是否为当前对话的最新回答，避免获取历史回答
                                        const isLatestAnswer = Array.from(document.querySelectorAll('div._4f9bf79, div.d7dc56a8, div._43c05b5'))
                                            .indexOf(lastAnswer) === document.querySelectorAll('div._4f9bf79, div.d7dc56a8, div._43c05b5').length - 1;
                                        
                                        // 获取消息发送时间戳，用于判断是否为新回答
                                        const messageTimestamp = window._deepseekMessageSentTime || 0;
                                        const currentTime = Date.now();
                                        const timeSinceMessage = currentTime - messageTimestamp;
                                        
                                        console.log('消息时间戳信息: 发送时间=' + messageTimestamp + ', 当前时间=' + currentTime + ', 时间差=' + timeSinceMessage + 'ms');
                                        
                                        // 如果不是最新回答或者消息发送后时间太短（小于15秒），可能是历史回答
                                        const isTooEarly = messageTimestamp > 0 && timeSinceMessage < 15000;
                                        
                                        // 如果时间太短且按钮数量没有增加，不认为找到了有效的复制按钮
                                        if (isTooEarly && !hasNewButtons) {
                                            console.log('检测到复制按钮，但时间太短且无新增按钮，可能是上次会话的按钮，继续等待');
                                            foundCopyButton = false;
                                            return { 
                                                found: false, 
                                                isInLatestAnswer: false,
                                                timeSinceMessage: timeSinceMessage,
                                                isTooEarly: true
                                            };
                                        }
                                        
                                        // 获取最后回答区域创建时间（如果有）
                                        let answerCreationTime = null;
                                        try {
                                            if (lastAnswer.dataset && lastAnswer.dataset.timestamp) {
                                                answerCreationTime = parseInt(lastAnswer.dataset.timestamp);
                                            }
                                        } catch (e) {}
                                        
                                        // 辅助函数：检查按钮是否在代码块或Mermaid图表内
                                        function isInCodeOrDiagram(button) {
                                            // 检查祖先元素是否包含代码块或Mermaid相关类名
                                            let parent = button;
                                            while (parent && parent !== document.body) {
                                                const classNames = parent.className || '';
                                                if (typeof classNames === 'string') {
                                                    if (classNames.includes('code-block') || 
                                                        classNames.includes('mermaid') || 
                                                        classNames.includes('md-code-block') || 
                                                        classNames.includes('flowchart') || 
                                                        classNames.includes('kvfysmfp') ||
                                                        classNames.includes('ds-code')) {
                                                        return true;
                                                    }
                                                }
                                                parent = parent.parentElement;
                                            }
                                            return false;
                                        }
                                        
                                        // 首先检查特定类的复制按钮
                                        for (let i = specificClassButtons.length - 1; i >= 0; i--) {
                                            const button = specificClassButtons[i];
                                            // 排除代码块或图表中的复制按钮
                                            if ((lastAnswer.contains(button) || isNearElement(button, lastAnswer)) && !isInCodeOrDiagram(button)) {
                                                foundCopyButton = true;
                                                isInLatestAnswer = true;
                                                buttonInfo = { 
                                                    type: 'specific', 
                                                    position: button.getBoundingClientRect(),
                                                    answerPosition: lastAnswer.getBoundingClientRect()
                                                };
                                                break;
                                            }
                                        }
                                        
                                        // 如果没找到，检查常规复制按钮
                                        if (!foundCopyButton) {
                                            for (let i = regularCopyButtons.length - 1; i >= 0; i--) {
                                                const button = regularCopyButtons[i];
                                                // 排除代码块或图表中的复制按钮
                                                if ((lastAnswer.contains(button) || isNearElement(button, lastAnswer)) && !isInCodeOrDiagram(button)) {
                                                    foundCopyButton = true;
                                                    isInLatestAnswer = true;
                                                    buttonInfo = { 
                                                        type: 'regular', 
                                                        position: button.getBoundingClientRect(),
                                                        answerPosition: lastAnswer.getBoundingClientRect()
                                                    };
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                    
                                    // 如果在最后一个回答区域内没找到，检查是否有任何复制按钮
                                    if (!foundCopyButton) {
                                            // 获取消息发送时间戳，用于判断是否为新回答
                                            const messageTimestamp = window._deepseekMessageSentTime || 0;
                                            const currentTime = Date.now();
                                            const timeSinceMessage = currentTime - messageTimestamp;
                                            
                                            // 如果消息发送后时间太短（小于10秒），可能是历史按钮
                                            const isTooEarly = messageTimestamp > 0 && timeSinceMessage < 10000;
                                            
                                            // 如果时间太短，不认为找到了有效的复制按钮
                                            if (isTooEarly) {
                                                console.log('发送消息后时间太短，跳过按钮检测。时间差: ' + timeSinceMessage + 'ms');
                                                foundCopyButton = false;
                                                return { 
                                                    found: false, 
                                                    isInLatestAnswer: false,
                                                    timeSinceMessage: timeSinceMessage,
                                                    isTooEarly: true
                                                };
                                            }
                                            
                                        if (specificClassButtons.length > 0) {
                                            foundCopyButton = true;
                                            buttonInfo = { 
                                                type: 'specific-outside', 
                                                    position: specificClassButtons[specificClassButtons.length - 1].getBoundingClientRect(),
                                                    timeSinceMessage: timeSinceMessage,
                                                    isTooEarly: false
                                            };
                                        } else if (regularCopyButtons.length > 0) {
                                            foundCopyButton = true;
                                            buttonInfo = { 
                                                type: 'regular-outside', 
                                                    position: regularCopyButtons[regularCopyButtons.length - 1].getBoundingClientRect(),
                                                    timeSinceMessage: timeSinceMessage,
                                                    isTooEarly: false
                                            };
                                        }
                                    }
                                    
                                    // 判断元素是否在另一个元素附近的辅助函数
                                    function isNearElement(element1, element2) {
                                        const rect1 = element1.getBoundingClientRect();
                                        const rect2 = element2.getBoundingClientRect();
                                        
                                        // 检查两个元素是否在垂直方向上接近
                                        const verticalOverlap = (rect1.bottom >= rect2.top && rect1.top <= rect2.bottom);
                                        
                                        // 检查两个元素是否在水平方向上接近
                                        const horizontalOverlap = (rect1.right >= rect2.left && rect1.left <= rect2.right);
                                        
                                        // 如果两个元素在垂直和水平方向上都接近，或者一个元素在另一个元素的下方不远处
                                        return (verticalOverlap && horizontalOverlap) || 
                                               (Math.abs(rect1.bottom - rect2.bottom) < 100 && horizontalOverlap) ||
                                               (Math.abs(rect1.top - rect2.bottom) < 50 && horizontalOverlap);
                                    }
                                    
                                    return {
                                        found: foundCopyButton,
                                        isInLatestAnswer: isInLatestAnswer,
                                        regularButtonsCount: regularCopyButtons.length,
                                        specificButtonsCount: specificClassButtons.length,
                                        answersCount: specificClassAreas.length,
                                        buttonInfo: buttonInfo,
                                        hasLastAnswer: lastAnswer !== null,
                                        hasNewButtons: hasNewButtons,
                                        currentButtonCount: currentButtonCount,
                                        initialButtonCount: initialButtonCount
                                    };
                                }
                            """, initialCopyButtonCount);
                            
                            if (copyButtonDetails instanceof Map) {
                                Map<String, Object> details = (Map<String, Object>) copyButtonDetails;
                                boolean found = (boolean) details.getOrDefault("found", false);
                                boolean isInLatestAnswer = (boolean) details.getOrDefault("isInLatestAnswer", false);
                                int regularButtonsCount = ((Number) details.getOrDefault("regularButtonsCount", 0)).intValue();
                                int specificButtonsCount = ((Number) details.getOrDefault("specificButtonsCount", 0)).intValue();
                                int answersCount = ((Number) details.getOrDefault("answersCount", 0)).intValue();
                                boolean hasLastAnswer = (boolean) details.getOrDefault("hasLastAnswer", false);
                                boolean hasNewButtons = (boolean) details.getOrDefault("hasNewButtons", false);
                                int currentButtonCount = ((Number) details.getOrDefault("currentButtonCount", 0)).intValue();
                                int initialButtonCount = ((Number) details.getOrDefault("initialButtonCount", 0)).intValue();
                                Map<String, Object> buttonInfo = (Map<String, Object>) details.getOrDefault("buttonInfo", null);
                                
                                // 删除冗余日志输出，保留关键信息
                                if (found && isInLatestAnswer) {
                                    System.out.println("检测到复制按钮，准备提取内容");
                                }
                                
                                // 直接使用前面已经定义的buttonDetectionElapsedTime和tooEarlyForDetection变量
                                
                                // 如果时间太短（小于8秒）且没有新增按钮，可能是检测到了上次会话的按钮
                                boolean tooEarlyForDetection = buttonDetectionElapsedTime < 8000;
                                
                                if (found && isInLatestAnswer) {
                                    if (tooEarlyForDetection && !hasNewButtons) {
                                        // 检测太早，可能是上次会话的按钮，继续等待
                                        System.out.println("检测到复制按钮，但时间太短且无新增按钮，可能是上次会话的按钮，继续等待");
                                        
                                        // 获取消息发送时间戳信息，用于调试
                                        try {
                                            Object timestampInfo = page.evaluate("() => { return { sentTime: window._deepseekMessageSentTime || 0, currentTime: Date.now(), diff: Date.now() - (window._deepseekMessageSentTime || 0) }; }");
                                            if (timestampInfo instanceof Map) {
                                                Map<String, Object> info = (Map<String, Object>) timestampInfo;
                                                System.out.println("消息时间戳信息: 发送时间=" + info.get("sentTime") + 
                                                                  ", 当前时间=" + info.get("currentTime") + 
                                                                  ", 时间差=" + info.get("diff") + "ms");
                                            }
                                        } catch (Exception e) {
                                            // 忽略时间戳检查错误
                                        }
                                    } else {
                                        // 添加额外检查，确认特殊内容已渲染完成
                                        try {
                                            Object specialContentStatus = page.evaluate("""
                                                () => {
                                                    // 检查页面上的特殊内容是否已渲染完成
                                                    const mermaidDiagrams = document.querySelectorAll('.mermaid, .flowchart, .kvfysmfp');
                                                    const codeBlocks = document.querySelectorAll('.md-code-block, .ds-code');
                                                    const tables = document.querySelectorAll('.markdown-table-wrapper');
                                                    
                                                    // 检查特殊元素数量
                                                    const specialElements = {
                                                        mermaidCount: mermaidDiagrams.length,
                                                        codeBlockCount: codeBlocks.length,
                                                        tableCount: tables.length
                                                    };
                                                    
                                                    // 检查是否还有加载中的元素
                                                    const loadingElements = document.querySelectorAll('.loading, .generating, .thinking');
                                                    const hasLoadingElements = loadingElements.length > 0;
                                                    
                                                    // 检查最近5秒内是否有DOM变化
                                                    const hasRecentChanges = window._lastDomChange && 
                                                                            (Date.now() - window._lastDomChange < 5000);
                                                    
                                                    // 设置DOM变化监听器（如果尚未设置）
                                                    if (!window._domChangeListenerSet) {
                                                        window._lastDomChange = Date.now();
                                                        const observer = new MutationObserver(() => {
                                                            window._lastDomChange = Date.now();
                                                        });
                                                        observer.observe(document.body, { 
                                                            childList: true, 
                                                            subtree: true, 
                                                            attributes: true 
                                                        });
                                                        window._domChangeListenerSet = true;
                                                    }
                                                    
                                                    return {
                                                        specialElements,
                                                        hasLoadingElements,
                                                        hasRecentChanges,
                                                        renderingComplete: !hasLoadingElements && !hasRecentChanges
                                                    };
                                                }
                                            """);
                                            
                                            if (specialContentStatus instanceof Map) {
                                                Map<String, Object> status = (Map<String, Object>) specialContentStatus;
                                                boolean renderingComplete = (boolean) status.getOrDefault("renderingComplete", true);
                                                
                                                if (!renderingComplete) {
                                                    // 特殊内容仍在渲染中，继续等待
                                                    System.out.println("检测到复制按钮，但特殊内容（图表/代码块）仍在渲染中，继续等待");
                                                    continue;
                                                }
                                                
                                                // 记录特殊内容信息
                                                Map<String, Object> elements = (Map<String, Object>) status.getOrDefault("specialElements", new HashMap<>());
                                                // 删除冗余日志输出
                                            }
                                        } catch (Exception e) {
                                            // 忽略特殊内容检查错误
                                        }
                                        
                                        // 检查是否满足最小等待时间要求（至少5秒）
                                        long currentWaitTime = System.currentTimeMillis() - messageStartTime;
                                        if (currentWaitTime < 5000) {
                                            // 如果等待时间不足5秒，继续等待
                                            long remainingWaitTime = 5000 - currentWaitTime;
                                            System.out.println("检测到回答可能已完成，但距离发送消息仅" + currentWaitTime + "ms，继续等待" + remainingWaitTime + "ms");
                                            Thread.sleep(remainingWaitTime);
                                        }
                                        
                                        // 条件满足，确认完成
                                        logInfo.sendTaskLog("DeepSeek回答完成，正在自动提取内容", userId, aiName);
                                        break;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // 忽略按钮检测错误
                            System.out.println("检测复制按钮时出错: " + e.getMessage());
                        }
                        
                                                    // 如果没有明确的完成指标，但内容已经稳定一段时间，也认为完成
                        if (stableCount >= 5) {
                            // 深度思考模式下，需要更严格的稳定性要求
                            // 使用前面已经定义的isDeepThinkingMode变量
                            if (isDeepThinkingMode) {
                                // 深度思考模式下，如果内容已经稳定超过5次检查，进一步确认
                                boolean isReallyComplete = false;
                                
                                // 1. 检查是否已经过了合理的思考时间
                                long currentElapsedTime = System.currentTimeMillis() - startTime;
                                boolean hasEnoughTime = currentElapsedTime > 40000; // 至少40秒
                                
                                // 2. 检查内容是否足够丰富（长度判断）
                                boolean hasEnoughContent = currentContent.length() > 500;
                                
                                // 3. 检查停止按钮是否已消失
                                boolean stopButtonGone = false;
                                try {
                                    Object checkStopButton = page.evaluate("""
                                        () => {
                                            const stopButtons = document.querySelectorAll('button:contains("停止生成"), [title="停止生成"], .stop-generating-button');
                                            return stopButtons.length === 0 || Array.from(stopButtons).every(btn => 
                                                window.getComputedStyle(btn).display === 'none' || 
                                                window.getComputedStyle(btn).visibility === 'hidden'
                                            );
                                        }
                                    """);
                                    
                                    if (checkStopButton instanceof Boolean) {
                                        stopButtonGone = (Boolean) checkStopButton;
                                    }
                                } catch (Exception e) {
                                    // 忽略检测错误
                                }
                                
                                isReallyComplete = hasEnoughTime && hasEnoughContent && stopButtonGone;
                                
                                if (isReallyComplete) {
                                    // 检查是否满足最小等待时间要求（至少5秒）
                                    long currentWaitTime = System.currentTimeMillis() - messageStartTime;
                                    if (currentWaitTime < 5000) {
                                        // 如果等待时间不足5秒，继续等待
                                        long remainingWaitTime = 5000 - currentWaitTime;
                                        System.out.println("检测到回答可能已完成，但距离发送消息仅" + currentWaitTime + "ms，继续等待" + remainingWaitTime + "ms");
                                        Thread.sleep(remainingWaitTime);
                                    }
                                    
                                    System.out.println(aiName + "深度思考模式回答可能已完成，内容已稳定且符合完成条件");
                                    break;
                                } else {
                                    System.out.println("深度思考模式下内容已暂时稳定，但仍在等待更多确认信号");
                                    // 重置稳定计数，给更多时间继续生成
                                    if (stableCount == 5) {
                                        stableCount = 3;
                                    }
                                }
                            } else {
                                // 非深度思考模式，使用原有的判断逻辑
                                // 检查是否满足最小等待时间要求（至少5秒）
                                long currentWaitTime = System.currentTimeMillis() - messageStartTime;
                                if (currentWaitTime < 5000) {
                                    // 如果等待时间不足5秒，继续等待
                                    long remainingWaitTime = 5000 - currentWaitTime;
                                    System.out.println("检测到回答可能已完成，但距离发送消息仅" + currentWaitTime + "ms，继续等待" + remainingWaitTime + "ms");
                                    Thread.sleep(remainingWaitTime);
                                }
                                
                                System.out.println(aiName + "回答可能已完成，内容已稳定");
                                break;
                            }
                        }
                    }
                } else {
                    // 内容发生变化，重置稳定计数
                    stableCount = 0;
                }
                
                // 更新上次内容为当前内容
                lastContent = currentContent;
                
                // 不输出定期的监控日志，与豆包保持一致
                
                // 等待一段时间再次检查
                page.waitForTimeout(1000); // 从3000ms减少到1000ms，更快地检测变化
            }
            
            logInfo.sendTaskLog("DeepSeek内容已自动提取完成", userId, aiName);
            
            // 清理HTML内容，去除不必要的标记，保持原始格式
            if (currentContent != null && !currentContent.isEmpty()) {
                // 简单清理：移除行号和不必要的标签
                currentContent = currentContent.replaceAll("<span>\\s*<span[^>]*?>\\d+</span>\\s*</span>", "");
                
                // 使用JavaScript清理交互元素，但保留原始格式和结构
                try {
                    Object cleanedContent = page.evaluate("""
                        (content) => {
                            try {
                                // 创建临时DOM元素来处理HTML
                                const tempDiv = document.createElement('div');
                                tempDiv.innerHTML = content;
                                    
                                // 移除所有交互元素和控制元素
                                const elementsToRemove = tempDiv.querySelectorAll(
                                    'button, .ds-button, [role="button"], .md-code-block-banner, ' +
                                    '.md-code-block-banner-wrap, .ds-icon, .code-info-button-text, ' +
                                    '.copy-button, .code-block-header, .code-block-header-container'
                                );
                                
                                    elementsToRemove.forEach(el => {
                                    if (el && el.parentNode) {
                                        el.parentNode.removeChild(el);
                                    }
                                    });
                                    
                                // 移除空的控制容器
                                const emptyContainers = tempDiv.querySelectorAll('.efa13877, .d2a24f03, ._121d384');
                                emptyContainers.forEach(container => {
                                    if (container && container.children.length === 0) {
                                        container.parentNode?.removeChild(container);
                                    }
                                    });
                                    
                                // 保留所有原始格式，只返回清理后的HTML
                                return tempDiv.innerHTML;
                            } catch (e) {
                                console.error('清理内容时出错:', e);
                                return content; // 出错时返回原始内容
                            }
                        }
                    """, currentContent);
                    
                    if (cleanedContent != null && !cleanedContent.toString().isEmpty()) {
                        currentContent = cleanedContent.toString();
                        logInfo.sendTaskLog("已清理HTML内容中的交互元素，保留原始格式", userId, aiName);
                    }
                } catch (Exception e) {
                    logInfo.sendTaskLog("清理HTML内容时出错: " + e.getMessage(), userId, aiName);
                }
                
                // 如果内容很短或看起来不完整，尝试精确定位最新回答的纯文本
                if (currentContent.length() < 50) {
                    try {
                        Object latestContent = page.evaluate("""
                            () => {
                                // 精确定位最新的AI回答
                                const aiMessages = document.querySelectorAll('.ds-chat-message-group--ai');
                                if (aiMessages.length > 0) {
                                    const latestMessage = aiMessages[aiMessages.length - 1];
                                    const contentElement = latestMessage.querySelector('.ds-markdown, .flow-markdown-body');
                                    if (contentElement) {
                                        return contentElement.innerText || contentElement.textContent;
                                    }
                                }
                                return '';
                            }
                        """);
                        
                        if (latestContent != null && !latestContent.toString().isEmpty()) {
                            currentContent = latestContent.toString();
                            logInfo.sendTaskLog("使用纯文本提取方式获取内容", userId, aiName);
                        }
                    } catch (Exception e) {
                        // 忽略JavaScript提取错误
                    }
                }
            }
            
            return currentContent;
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "获取内容失败";
    }

    /**
     * 发送消息到DeepSeek并等待回复
     * @param page Playwright页面实例
     * @param userPrompt 用户提示文本
     * @param userId 用户ID
     * @param roles 角色标识
     * @param chatId 会话ID，如果不为空则使用此会话继续对话
     * @return 处理完成后的结果
     */
    public String handleDeepSeekAI(Page page, String userPrompt, String userId, String roles, String chatId) {
        try {
            long startProcessTime = System.currentTimeMillis(); // 记录开始处理时间
            
            // 设置页面错误处理
            page.onPageError(error -> {
                System.out.println("页面错误: " + error);
            });
            
            // 监听请求失败
            page.onRequestFailed(request -> {
                System.out.println("请求失败: " + request.url());
            });
            
            boolean navigationSucceeded = false;
            int retries = 0;
            final int MAX_RETRIES = 3; // 增加重试次数
            
            // 如果有会话ID，则直接导航到该会话
            if (chatId != null && !chatId.isEmpty()) {
                // 这个日志保留，与豆包一致
                
                while (!navigationSucceeded && retries < MAX_RETRIES) {
                    try {
                        // 增加导航选项，提高稳定性
                        page.navigate("https://chat.deepseek.com/a/chat/s/" + chatId, 
                            new Page.NavigateOptions()
                            .setTimeout(10000) // 增加超时时间
                            .setWaitUntil(WaitUntilState.LOAD)); // 使用LOAD而不是DOMCONTENTLOADED，确保页面完全加载
                        
                        // 等待页面稳定 - 使用更可靠的方式
                        try {
                            // 首先等待页面加载完成
                            page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(15000));
                            
                            // 使用JavaScript检查页面是否已准备好，而不是依赖选择器
                            boolean pageReady = false;
                            for (int attempt = 0; attempt < 10 && !pageReady; attempt++) {
                                try {
                                    Object result = page.evaluate("() => { return document.readyState === 'complete' || document.readyState === 'interactive'; }");
                                    if (result instanceof Boolean && (Boolean) result) {
                                        pageReady = true;
                                    } else {
                                        Thread.sleep(500); // 等待500毫秒再次检查
                                    }
                                } catch (Exception evalEx) {
                                    // 忽略评估错误，继续尝试
                                    Thread.sleep(500);
                                }
                            }
                            
                            // 如果页面已准备好，尝试等待网络空闲，但不强制要求
                            if (pageReady) {
                                try {
                                    page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
                                } catch (Exception networkEx) {
                                    // 忽略网络空闲等待错误
                                }
                            }
                        } catch (Exception e) {
                            // 忽略等待错误，继续执行
                            System.out.println("等待页面稳定时出错，将继续执行: " + e.getMessage());
                        }
                        
                        navigationSucceeded = true;
                    } catch (Exception e) {
                        retries++;
                        System.out.println("导航到已有会话时出错(尝试 " + retries + "/" + MAX_RETRIES + "): " + e.getMessage());
            
                        if (retries >= MAX_RETRIES) {
                            System.out.println("多次尝试后仍无法导航到已有会话，将尝试打开主页");
                            try {
                                page.navigate("https://chat.deepseek.com/");
                                Thread.sleep(1000); // 给页面充足的加载时间
                            } catch (Exception ex) {
                                System.out.println("导航到主页也失败: " + ex.getMessage());
                            }
                        }
                        
                        // 短暂等待后重试
                        Thread.sleep(2000); // 增加等待时间
                    }
                }
            } else {
                try {
                    page.navigate("https://chat.deepseek.com/", 
                        new Page.NavigateOptions()
                        .setTimeout(10000)
                        .setWaitUntil(WaitUntilState.LOAD)); // 使用LOAD而不是DOMCONTENTLOADED
                    
                    // 等待页面稳定 - 使用更可靠的方式
                    try {
                        // 首先等待页面加载完成
                        page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(15000));
                        
                        // 使用JavaScript检查页面是否已准备好，而不是依赖选择器
                        boolean pageReady = false;
                        for (int attempt = 0; attempt < 10 && !pageReady; attempt++) {
                            try {
                                Object result = page.evaluate("() => { return document.readyState === 'complete' || document.readyState === 'interactive'; }");
                                if (result instanceof Boolean && (Boolean) result) {
                                    pageReady = true;
                                } else {
                                    Thread.sleep(500); // 等待500毫秒再次检查
                                }
                            } catch (Exception evalEx) {
                                // 忽略评估错误，继续尝试
                                Thread.sleep(500);
                            }
                        }
                        
                        // 如果页面已准备好，尝试等待网络空闲，但不强制要求
                        if (pageReady) {
                            try {
                                page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
                            } catch (Exception networkEx) {
                                // 忽略网络空闲等待错误
                            }
                        }
                    } catch (Exception e) {
                        // 忽略等待错误，继续执行
                        System.out.println("等待页面稳定时出错，将继续执行: " + e.getMessage());
                    }
                } catch (Exception e) {
                    System.out.println("导航到主页时出错: " + e.getMessage());
                }
            }
            
            // 等待页面加载完成
            try {
                // 使用更可靠的等待方式，但缩短超时时间
                Thread.sleep(1000); // 给页面充足的渲染时间
                logInfo.sendTaskLog("DeepSeek页面打开完成", userId, "DeepSeek");
            } catch (Exception e) {
                System.out.println("等待页面加载时出错: " + e.getMessage() + "，将继续执行");
            }
            
            // 先处理深度思考和联网搜索按钮的状态
            boolean needDeepThink = roles.contains("ds-sdsk");
            boolean needWebSearch = roles.contains("ds-lwss");
            // 只要有一个没选中就点亮，否则如果都没选则全部关闭
            if (needDeepThink || needWebSearch) {
                if (needDeepThink) {
                    toggleButtonIfNeeded(page, userId, "深度思考", true, logInfo);
                    // 保留深度思考模式的日志，与豆包一致
                    logInfo.sendTaskLog("已启动深度思考模式", userId, "DeepSeek");
                } else {
                    toggleButtonIfNeeded(page, userId, "深度思考", false, logInfo);
                }
                if (needWebSearch) {
                    toggleButtonIfNeeded(page, userId, "联网搜索", true, logInfo);
                } else {
                    toggleButtonIfNeeded(page, userId, "联网搜索", false, logInfo);
                }
            } else {
                // 如果都不需要，全部关闭
                toggleButtonIfNeeded(page, userId, "深度思考", false, logInfo);
                toggleButtonIfNeeded(page, userId, "联网搜索", false, logInfo);
            }
            
            // 定位并填充输入框
            try {
                Locator inputBox = page.locator("#chat-input");
                // 优化等待逻辑：循环检测输入框可用，最多等3秒
                int inputWait = 0;
                while ((inputBox.count() == 0 || !inputBox.isVisible()) && inputWait < 30) {
                    Thread.sleep(100);
                    inputBox = page.locator("#chat-input");
                    inputWait++;
                }
                if (inputBox.count() > 0 && inputBox.isVisible()) {
                    inputBox.click();
                    // 等待输入框获得焦点（最多500ms）
                    int focusWait = 0;
                    while (!inputBox.evaluate("el => document.activeElement === el").equals(Boolean.TRUE) && focusWait < 5) {
                        Thread.sleep(100);
                        focusWait++;
                    }
                    inputBox.fill(userPrompt);
                    logInfo.sendTaskLog("用户指令已自动输入完成", userId, "DeepSeek");
                    // 立即尝试点击发送按钮，无需多余等待
                    try {
                        // 使用用户提供的特定选择器
                        String sendButtonSelector = "._7436101";
                        boolean clicked = false;
                        Locator sendButton = page.locator(sendButtonSelector);
                        // 循环检测发送按钮可用，最多等2秒
                        int sendWait = 0;
                        while ((sendButton.count() == 0 || !sendButton.isVisible()) && sendWait < 20) {
                            Thread.sleep(100);
                            sendButton = page.locator(sendButtonSelector);
                            sendWait++;
                        }
                        if (sendButton.count() > 0 && sendButton.isVisible()) {
                            try {
                                sendButton.scrollIntoViewIfNeeded();
                                sendButton.click(new Locator.ClickOptions().setForce(true).setTimeout(5000));
                                clicked = true;
                                logInfo.sendTaskLog("指令已自动发送成功", userId, "DeepSeek");
                            } catch (Exception e) {
                                System.out.println("点击特定按钮失败，尝试备用方法: " + e.getMessage());
                            }
                        } else {
                            System.out.println("未找到特定发送按钮，尝试备用方法");
                        }
                        
                        // 如果特定按钮点击失败，尝试其他选择器
                        if (!clicked) {
                            try {
                                // 尝试常见的发送按钮选择器
                                String[] alternativeSelectors = {
                                    "button.send-button", 
                                    "button[aria-label='发送']",
                                    "button[aria-label='Send']",
                                    "button.ds-button--primary",
                                    ".send-message-button"
                                };
                                
                                for (String selector : alternativeSelectors) {
                                    try {
                                        Locator altButton = page.locator(selector).first();
                                        if (altButton.count() > 0 && altButton.isVisible()) {
                                            altButton.click(new Locator.ClickOptions().setForce(true).setTimeout(3000));
                                            clicked = true;
                                            logInfo.sendTaskLog("指令已自动发送成功", userId, "DeepSeek");
                                            break;
                                        }
                                    } catch (Exception e) {
                                        // 继续尝试下一个选择器
                                    }
                                }
                            } catch (Exception e) {
                                System.out.println("备用选择器点击失败: " + e.getMessage());
                            }
                        }
                        
                        // 如果所有按钮选择器都失败，尝试使用JavaScript点击
                        if (!clicked) {
                            System.out.println("尝试使用JavaScript方法点击发送按钮");
                            try {
                                Object result = page.evaluate("""
                                    () => {
                                        try {
                                            // 记录消息发送时间戳，用于后续判断回答是否为当前会话的新回答
                                            window._deepseekMessageSentTime = Date.now();
                                            console.log('设置消息发送时间戳:', window._deepseekMessageSentTime);
                                            
                                            // 尝试多种可能的按钮
                                            const selectors = [
                                                '._7436101', 
                                                'button.send-button', 
                                                'button[aria-label="发送"]',
                                                'button[aria-label="Send"]',
                                                'button.ds-button--primary',
                                                '.send-message-button'
                                            ];
                                            
                                            // 遍历所有选择器
                                            for (const selector of selectors) {
                                                const button = document.querySelector(selector);
                                                if (button && button.offsetParent !== null) {
                                                    button.click();
                                                    return { method: selector, success: true };
                                                }
                                            }
                                            
                                            // 尝试找到任何看起来像发送按钮的元素
                                            const allButtons = document.querySelectorAll('button');
                                            for (const btn of allButtons) {
                                                if (btn.innerText && (btn.innerText.includes('发送') || btn.innerText.includes('Send'))) {
                                                    btn.click();
                                                    return { method: '文本匹配', success: true };
                                                }
                                                
                                                // 检查按钮样式是否暗示它是发送按钮
                                                const style = window.getComputedStyle(btn);
                                                if (style.backgroundColor && 
                                                    (style.backgroundColor.includes('rgb(77, 107, 254)') || 
                                                     style.backgroundColor.includes('#4D6BFE'))) {
                                                    btn.click();
                                                    return { method: '样式匹配', success: true };
                                                }
                                            }
                                            
                                            // 如果仍然找不到，尝试按回车键
                                            const inputElement = document.querySelector('#chat-input');
                                            if (inputElement) {
                                                const event = new KeyboardEvent('keydown', {
                                                    key: 'Enter',
                                                    code: 'Enter',
                                                    keyCode: 13,
                                                    bubbles: true
                                                });
                                                inputElement.dispatchEvent(event);
                                                return { method: 'Enter键', success: true };
                                            }
                                            
                                            return { method: '所有方法', success: false };
                                        } catch (e) {
                                            return { method: '出错', success: false, error: e.toString() };
                                        }
                                    }
                                """);
                                System.out.println("JavaScript点击结果: " + result);
                                
                                // 最后一招：尝试按下Enter键
                                try {
                                    // 设置消息发送时间戳
                                    page.evaluate("() => { window._deepseekMessageSentTime = Date.now(); console.log('设置消息发送时间戳(Enter):', window._deepseekMessageSentTime); }");
                                    
                                    inputBox.press("Enter");
                                    logInfo.sendTaskLog("指令已自动发送成功", userId, "DeepSeek");
                                } catch (Exception e) {
                                    System.out.println("按下Enter键失败: " + e.getMessage());
                                }
                            } catch (Exception e) {
                                System.out.println("JavaScript点击失败: " + e.getMessage());
                            }
                        }

                        // 等待一段时间，确保消息已发送
                        Thread.sleep(1000); // 给予充足的时间确保消息发送
                    } catch (Exception e) {
                        System.out.println("发送消息失败: " + e.getMessage());
                        return "获取内容失败：发送消息出错";
                    }
                } else {
                    System.out.println("未找到输入框");
                    return "获取内容失败：未找到输入框";
                }
            } catch (Exception e) {
                System.out.println("发送消息失败: " + e.getMessage());
                return "获取内容失败：发送消息出错";
            }
            
            // 等待回答完成并获取内容
            logInfo.sendTaskLog("开启自动监听任务，持续监听DeepSeek回答中", userId, "DeepSeek");
            String content = waitDeepSeekResponse(page, userId, "DeepSeek", roles);
            
            // 返回内容
            return content;
            
        } catch (Exception e) {
            e.printStackTrace();
            return "获取内容失败：" + e.getMessage();
        }
    }

    /**
     * 处理DeepSeek内容并保存到稿库
     * 只保存AI回答的内容，不以问答形式展现
     * @param page Playwright页面实例
     * @param userInfoRequest 用户信息请求
     * @param roleType 角色类型
     * @param userId 用户ID
     * @param content 已获取的内容
     * @return 处理后的内容
     */
    public String saveDeepSeekContent(Page page, UserInfoRequest userInfoRequest, String roleType, String userId, String content) {
        try {
            long startTime = System.currentTimeMillis(); // 记录开始时间
            
            // 1. 从URL提取会话ID和分享链接
            String shareUrl = "";
            String chatId = "";
            
                        try {
                            String currentUrl = page.url();
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("/chat/s/([^/]+)");
                java.util.regex.Matcher matcher = pattern.matcher(currentUrl);
                
                if (matcher.find()) {
                    chatId = matcher.group(1);
                    shareUrl = "https://chat.deepseek.com/a/chat/s/" + chatId;
                    
                    // 将会话ID保存到请求对象中
                    userInfoRequest.setYbDsChatId(chatId);
                    
                    // 发送会话ID给前端
                    JSONObject chatData = new JSONObject();
                    chatData.put("type", "RETURN_YBDS_CHATID");
                    chatData.put("chatId", chatId);
                    chatData.put("userId", userId);
                    webSocketClientService.sendMessage(chatData.toJSONString());
                }
            } catch (Exception e) {
                // 忽略错误
            }
            
            // 2. 处理内容，为后台显示创建对话形式，但稿库仍只存储AI回答
            boolean isHtml = content != null && content.trim().startsWith("<") && content.contains("</");
            
            // 创建后台显示用的对话格式内容，但添加明显的标记，方便豆包处理
            StringBuilder displayBuilder = new StringBuilder();
            
            // 添加用户问题部分（添加明显标记，方便豆包识别并删除）
            displayBuilder.append("<!-- 用户提问开始 - 豆包处理时请删除此部分 -->\n");
            displayBuilder.append("<div style=\"background-color: #f5f5f5; border-left: 4px solid rgb(0, 213, 255); margin: 0px auto; padding: 20px; color: #666; max-width: 800px; width: 100%;\">\n");
            displayBuilder.append("<h3 style=\"color:rgb(0, 213, 255); margin-top: 0;\">用户提问：</h3>\n");
            displayBuilder.append("<p>").append(userInfoRequest.getUserPrompt().replace("\n", "<br>")).append("</p>\n");
            displayBuilder.append("</div>\n");
            displayBuilder.append("<!-- 用户提问结束 - 豆包处理时请删除此部分 -->\n\n");
            
            // 添加AI回答部分（原始内容，但清理DeepSeek图标和其他不需要的元素）
            displayBuilder.append("<!-- AI回答开始 - 保留此部分 -->\n");
            try {
                // 无论是否为HTML，都尝试清理内容
                String processedContent = content;
                
                // 清理DeepSeek图标和其他不需要的元素
                processedContent = cleanDeepSeekContent(processedContent, userId);
                
                // 如果不是HTML格式，处理换行符
                if (!isHtml && processedContent != null) {
                    processedContent = processedContent.replace("\n", "<br>");
                }
                
                // 检查处理后的内容是否为空
                if (processedContent == null || processedContent.trim().isEmpty()) {
                    // 如果清理后内容为空，使用原始内容
            if (isHtml) {
                        displayBuilder.append(content);
            } else {
                        displayBuilder.append(content.replace("\n", "<br>"));
            }
                } else {
                    displayBuilder.append(processedContent);
                }
            } catch (Exception e) {
                // 发生异常时使用原始内容
                System.out.println("处理AI回答内容时出错: " + e.getMessage());
                if (isHtml) {
                    displayBuilder.append(content);
                } else {
                    displayBuilder.append(content.replace("\n", "<br>"));
                }
            }
            displayBuilder.append("\n<!-- AI回答结束 -->\n");
            
            // 用于显示的内容
            String displayContent = displayBuilder.toString();
            
            // 3. 设置AI名称
            String aiName = "DeepSeek";
            if (roleType != null) {
                boolean hasDeepThinking = roleType.contains("ds-sdsk");
                boolean hasWebSearch = roleType.contains("ds-lwss");
                
                if (hasDeepThinking && hasWebSearch) {
                    aiName = "DeepSeek-思考联网";
                } else if (hasDeepThinking) {
                    aiName = "DeepSeek-深度思考";
                } else if (hasWebSearch) {
                    aiName = "DeepSeek-联网搜索";
                }
            }
            
            // 4. 发送内容到前端 - 使用对话形式的美化内容
            logInfo.sendResData(displayContent, userId, "DeepSeek", "RETURN_DEEPSEEK_RES", shareUrl, null);
            
            // 5. 保存内容到稿库 - 保存清理后的AI回复
            try {
                // 清理DeepSeek图标和其他不需要的元素
                String cleanedContent = cleanDeepSeekContent(content, userId);
                
                // 如果清理后内容为空，使用原始内容
                if (cleanedContent == null || cleanedContent.trim().isEmpty()) {
                    userInfoRequest.setDraftContent(content);
                } else {
                    userInfoRequest.setDraftContent(cleanedContent);
                }
            } catch (Exception e) {
                // 发生异常时使用原始内容
                System.out.println("处理稿库内容时出错: " + e.getMessage());
                userInfoRequest.setDraftContent(content);
            }
            userInfoRequest.setAiName(aiName);
            userInfoRequest.setShareUrl(shareUrl);
            userInfoRequest.setShareImgUrl(null);
            
            // 6. 发送稿库存储请求
                Object response = RestUtils.post(url + "/saveDraftContent", userInfoRequest);
                
            logInfo.sendTaskLog("执行完成", userId, "DeepSeek");
            return displayContent; // 返回对话形式的美化内容用于显示
            
        } catch (Exception e) {
            logInfo.sendTaskLog("DeepSeek内容保存过程发生异常: " + e.getMessage(), userId, "DeepSeek");
            return content; // 出错时返回原始内容
        }
    }

    /**
     * 通用方法：根据目标激活状态切换按钮（深度思考/联网搜索）
     * @param page Playwright页面
     * @param userId 用户ID
     * @param buttonText 按钮文本（如"深度思考"、"联网搜索"）
     * @param shouldActive 期望激活(true)还是关闭(false)
     * @param logInfo 日志工具
     * @throws InterruptedException
     */
    private void toggleButtonIfNeeded(Page page, String userId, String buttonText, boolean shouldActive, LogMsgUtil logInfo) throws InterruptedException {
        try {
            long startTime = System.currentTimeMillis(); // 记录开始时间
            
            // 查找按钮
            Locator buttonLocator = page.locator("div[role=\"button\"] span:has-text(\"" + buttonText + "\")").first();
            if (buttonLocator.count() > 0 && buttonLocator.isVisible()) {
                String buttonHtml = buttonLocator.evaluate("button => button.outerHTML").toString();
                logInfo.sendTaskLog(buttonText + "按钮HTML: " + buttonHtml, userId, "DeepSeek");

                // 检查按钮当前状态
                Object buttonStateDetails = page.evaluate("""
                    (btnText) => {
                        const buttons = document.querySelectorAll('div[role="button"]');
                        let button = null;
                        for (const btn of buttons) {
                            if (btn.textContent.includes(btnText)) {
                                button = btn;
                                break;
                            }
                        }
                        if (!button) return { found: false, message: '未找到' + btnText + '按钮' };
                        const style = window.getComputedStyle(button);
                        const bgColor = style.backgroundColor;
                        const textColor = style.color;
                        const buttonStyle = button.getAttribute('style') || '';
                        const isActive = 
                            buttonStyle.includes('#DBEAFE') || 
                            buttonStyle.includes('#4D6BFE') ||
                            textColor === 'rgb(77, 107, 254)' || 
                            bgColor === 'rgb(219, 234, 254)';
                        return {
                            found: true,
                            isActive: isActive,
                            bgColor: bgColor,
                            textColor: textColor,
                            buttonStyle: buttonStyle,
                            outerHTML: button.outerHTML
                        };
                    }
                """, buttonText);
                logInfo.sendTaskLog(buttonText + "按钮状态: " + buttonStateDetails, userId, "DeepSeek");
                boolean isActive = false;
                if (buttonStateDetails instanceof Map) {
                    Map<String, Object> details = (Map<String, Object>) buttonStateDetails;
                    if (details.containsKey("found") && (boolean) details.get("found")) {
                        isActive = details.containsKey("isActive") ? (boolean) details.get("isActive") : false;
                    }
                }
                // 只在状态不符时点击
                if (isActive != shouldActive) {
                    try {
                        Locator buttonParent = page.locator("div[role=\"button\"]:has(span:has-text(\"" + buttonText + "\"))").first();
                        buttonParent.click();
                        logInfo.sendTaskLog((shouldActive ? "已启动" : "已关闭") + buttonText + "模式(直接点击父元素)", userId, "DeepSeek");
                        // 缩短截图等待，仅在切换后快速截图
                        try {
                            Thread.sleep(50); // 从100ms减少到50ms
                            page.screenshot(new Page.ScreenshotOptions()
                                .setPath(Paths.get(System.getProperty("java.io.tmpdir"), "deepseek_buttons_after_toggle_" + buttonText + "_" + System.currentTimeMillis() + ".png"))
                                .setFullPage(false));
                            logInfo.sendTaskLog("已保存点击" + buttonText + "后的按钮状态截图", userId, "DeepSeek");
                        } catch (Exception e) {
                            logInfo.sendTaskLog("保存点击后按钮状态截图失败: " + e.getMessage(), userId, "DeepSeek");
                        }
                    } catch (Exception e) {
                        logInfo.sendTaskLog("直接点击" + buttonText + "按钮失败，尝试JS点击: " + e.getMessage(), userId, "DeepSeek");
                        Object jsClickResult = page.evaluate("""
                            (btnText) => {
                                const buttons = document.querySelectorAll('div[role="button"]');
                                for (const btn of buttons) {
                                    if (btn.textContent.includes(btnText)) {
                                        btn.click();
                                        return { success: true, message: '已点击' + btnText + '按钮' };
                                    }
                                }
                                return { success: false, message: '未找到可点击的' + btnText + '按钮' };
                            }
                        """, buttonText);
                        logInfo.sendTaskLog("JS点击" + buttonText + "按钮结果: " + jsClickResult, userId, "DeepSeek");
                    }
                } else {
                    logInfo.sendTaskLog(buttonText + "模式已处于" + (shouldActive ? "激活" : "关闭") + "状态，无需重复点击", userId, "DeepSeek");
                }
            }
            
            // 记录操作耗时
            logInfo.sendTaskLog("切换" + buttonText + "按钮操作耗时: " + (System.currentTimeMillis() - startTime) + "ms", userId, "DeepSeek");
        } catch (Exception e) {
            logInfo.sendTaskLog("切换" + buttonText + "模式时出错: " + e.getMessage(), userId, "DeepSeek");
        }
    }

    /**
     * 清理DeepSeek内容中的图标和其他不需要的元素
     * @param content 原始内容
     * @param userId 用户ID，用于记录日志
     * @return 清理后的内容
     */
    private String cleanDeepSeekContent(String content, String userId) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        
        try {
            // 只清理DeepSeek图标，保留其他内容
            
            // 1. 移除SVG图标及其容器 - 使用更通用的模式
            String cleaned = content;
            
            // 清理特定的DeepSeek图标容器
            cleaned = cleaned.replaceAll("<div class=\"[^\"]*_7eb2358[^\"]*\">.*?</svg></div>", "");
            cleaned = cleaned.replaceAll("<div class=\"[^\"]*_58dfa60[^\"]*\">.*?</svg></div>", "");
            
            // 如果内容被完全清空，返回原始内容
            if (cleaned.trim().isEmpty() || !cleaned.contains("<")) {
                return content;
            }
            
            logInfo.sendTaskLog("已清理HTML内容中的交互元素，保留原始格式", userId, "DeepSeek");
            return cleaned;
        } catch (Exception e) {
            // 出现异常时记录日志并返回原始内容
            System.out.println("清理DeepSeek内容时出错: " + e.getMessage());
            return content;
        }
    }
} 