package com.playwright.utils;

import com.alibaba.fastjson.JSONObject;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.playwright.entity.UserInfoRequest;
import com.playwright.websocket.WebSocketClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 百度对话AI工具类
 * @author 优立方
 * @version JDK 17
 * @date 2025年07月31日 10:00
 */
@Component
public class BaiduUtil {

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
     * 检查百度对话AI登录状态
     * @param page Playwright页面对象
     * @param navigate 是否需要先导航到百度对话AI页面
     * @return 登录状态，如果已登录则返回用户名，否则返回"false"
     */
    public String checkBaiduLogin(Page page, boolean navigate) {
        try {
            if (navigate) {
                page.navigate("https://chat.baidu.com/");
                page.waitForLoadState(LoadState.LOAD);
                Thread.sleep(2000);
            }

            // 等待页面完全加载
            page.waitForLoadState(LoadState.NETWORKIDLE);
            Thread.sleep(1000);

            // 优先检查是否有登录按钮（未登录的关键标志）
            try {
                String loginButtonSelector = "//*[@id=\"app\"]/div/div[1]/div[2]/div/div";
                Locator loginButtonElement = page.locator(loginButtonSelector);

                if (loginButtonElement.count() > 0 && loginButtonElement.isVisible()) {
                    String buttonText = loginButtonElement.textContent();
                    if (buttonText != null && (buttonText.contains("登录") || buttonText.contains("登陆"))) {
                        return "false"; // 存在登录按钮，说明未登录
                    }
                }
            } catch (Exception e) {
                // 继续其他检查
            }

            // 检查用户ID元素是否存在（登录后才有的特定元素）
            try {
                String userIdSelector = "//*[@id=\"app\"]/div/div[1]/div[2]/div/div[1]/span";
                Locator userIdElement = page.locator(userIdSelector);

                if (userIdElement.count() > 0 && userIdElement.isVisible()) {
                    String userId = userIdElement.textContent();
                    if (userId != null && !userId.trim().isEmpty() && !userId.contains("登录")) {
                        // 确认已登录，现在尝试获取更友好的用户名显示
                        String[] friendlyNameSelectors = {
                            ".user-info .username", // 通用用户名选择器
                            ".user-info .nick-name", // 通用昵称选择器
                            ".header-user .user-name", // 头部用户名
                            "[data-testid='user-name']" // 测试用用户名
                        };

                        // 尝试获取更友好的用户名
                        for (String selector : friendlyNameSelectors) {
                            try {
                                Locator nameElement = page.locator(selector);
                                if (nameElement.count() > 0 && nameElement.isVisible()) {
                                    String userName = nameElement.textContent();
                                    if (userName != null && !userName.trim().isEmpty() && !userName.contains("登录")) {
                                        String cleanUserName = userName.trim();
                                        // 如果不是纯数字ID且与用户ID不同，返回这个友好的名称
                                        if (!cleanUserName.matches("\\d+") && !cleanUserName.equals(userId.trim())) {
                                            return cleanUserName;
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                // 继续尝试下一个选择器
                                continue;
                            }
                        }

                        // 如果没找到友好的用户名，返回格式化的用户ID
                        return "用户" + userId.trim();
                    }
                }
            } catch (Exception e) {
                // 用户信息检测失败，继续其他检查
            }

            // 检查是否存在登录按钮（使用安全的检查方式）
            try {
                Locator loginButton = page.locator("button:has-text('登录')");
                if (loginButton.count() > 0 && loginButton.isVisible()) {
                    return "false";
                }
            } catch (Exception e) {
                // 如果检查登录按钮时出现异常，可能是页面导航导致的，继续其他检查
            }

            // 检查输入框是否可见（登录后通常会显示聊天输入框）
            String[] inputSelectors = {
                "//*[@id=\"chat-input-box\"]", // 百度AI输入框
                "textarea[placeholder*='Shift+Enter换行']",
                ".chat-input",
                "#chat-input",
                "[data-testid='chat-input']"
            };

            for (String selector : inputSelectors) {
                try {
                    Locator inputElement = page.locator(selector);
                    if (inputElement.count() > 0 && inputElement.isVisible()) {
                        return "已登录";
                    }
                } catch (Exception e) {
                    // 继续尝试下一个选择器
                    continue;
                }
            }

            // 检查页面URL是否包含用户相关路径
            try {
                String currentUrl = page.url();
                if (currentUrl.contains("/profile") || currentUrl.contains("/user") ||
                    currentUrl.contains("/dashboard") || currentUrl.contains("/chat/")) {
                    return "已登录";
                }
            } catch (Exception e) {
                // URL检查失败，继续其他检查
            }

            // 备用检查：其他用户信息元素
            String[] userSelectors = {
                ".user-avatar",
                ".user-info",
                ".user-profile",
                ".header-user",
                ".nav-user",
                "[data-testid='user-info']",
                ".user-center"
            };

            // 检查是否存在用户头像或用户信息区域
            for (String selector : userSelectors) {
                try {
                    Locator userElement = page.locator(selector);
                    if (userElement.count() > 0 && userElement.isVisible()) {
                        // 尝试获取用户名
                        String[] usernameSelectors = {
                            ".username",
                            ".user-name",
                            ".nick-name",
                            ".display-name",
                            "[data-testid='username']"
                        };

                        for (String usernameSelector : usernameSelectors) {
                            try {
                                Locator usernameElement = page.locator(usernameSelector);
                                if (usernameElement.count() > 0 && usernameElement.isVisible()) {
                                    String username = usernameElement.first().textContent();
                                    if (username != null && !username.trim().isEmpty()) {
                                        return username.trim();
                                    }
                                }
                            } catch (Exception e) {
                                // 继续尝试下一个选择器
                            }
                        }

                        // 如果找到用户区域但无法获取用户名，返回通用登录状态
                        return "已登录";
                    }
                } catch (Exception e) {
                    // 继续尝试下一个选择器
                    continue;
                }
            }

            return "false";
        } catch (Exception e) {
            // 如果是页面导航导致的异常，可能是登录成功了
            if (e.getMessage() != null && e.getMessage().contains("navigation")) {
                try {
                    // 等待页面稳定后再次检查
                    Thread.sleep(2000);
                    page.waitForLoadState(LoadState.NETWORKIDLE);

                    // 简单检查URL是否变化（登录成功通常会跳转）
                    String currentUrl = page.url();
                    if (!currentUrl.equals("https://chat.baidu.com/") &&
                        !currentUrl.contains("login")) {
                        return "已登录";
                    }
                } catch (Exception retryException) {
                    // 如果重试也失败，返回false
                }
            }

            e.printStackTrace();
            return "false";
        }
    }

    /**
     * 处理百度对话AI页面交互
     * @param page Playwright页面对象
     * @param userPrompt 用户提示词
     * @param userId 用户ID
     * @param roles 角色配置 (支持: baidu-sdss深度搜索)
     * @param chatId 会话ID
     * @return AI生成内容
     */
    public String handleBaiduAI(Page page, String userPrompt, String userId, String roles, String chatId) {
        try {
            // 导航到百度对话AI页面
            if (chatId != null && !chatId.isEmpty()) {
                // 检查chatId是否是ori_lid格式（数字ID）
                if (chatId.matches("\\d+")) {
                    // 是ori_lid，构造原链接URL继续会话
                    String continueUrl = "https://chat.baidu.com/search?isShowHello=1&extParams=%7B%22ori_lid%22%3A%22" + chatId + "%22%2C%22subEnterType%22%3A%22his_middle%22%2C%22enter_type%22%3A%22chat_url%22%7D";
                    page.navigate(continueUrl);
                    logInfo.sendTaskLog("使用ori_lid继续会话: " + chatId, userId, "百度AI");
                } else {
                    // 传统的会话ID
                    page.navigate("https://chat.baidu.com/chat/" + chatId);
                    logInfo.sendTaskLog("使用会话ID: " + chatId, userId, "百度AI");
                }
            } else {
                page.navigate("https://chat.baidu.com/");
                logInfo.sendTaskLog("创建新会话", userId, "百度AI");
            }

            page.waitForLoadState(LoadState.LOAD);
            Thread.sleep(2000);

            // 添加页面打开截图
            logInfo.sendImgData(page, userId + "百度对话AI页面打开", userId);

            // 检查登录状态
            String loginStatus = checkBaiduLogin(page, false);
            if ("false".equals(loginStatus)) {
                logInfo.sendTaskLog("检测到需要登录，请扫码登录", userId, "百度AI");
                logInfo.sendImgData(page, userId + "百度对话AI需要登录", userId);
                // 等待用户登录
                page.waitForSelector(".chat-input, textarea[placeholder*='请输入']",
                    new Page.WaitForSelectorOptions().setTimeout(60000));
                logInfo.sendTaskLog("登录成功，继续执行", userId, "百度AI");
                logInfo.sendImgData(page, userId + "百度对话AI登录成功", userId);
            }

            // 配置百度对话AI功能模式
            configureBaiduModes(page, roles, userId);

            // 添加配置完成截图
            logInfo.sendImgData(page, userId + "百度对话AI配置完成", userId);

            // 发送提示词到百度对话AI
            sendPromptToBaidu(page, userPrompt, userId);

            // 添加发送后截图
            logInfo.sendImgData(page, userId + "百度对话AI发送提示词", userId);

            // 等待并提取回复内容
            String content = extractBaiduContent(page, userId);

            // 添加获取结果截图
            logInfo.sendImgData(page, userId + "百度对话AI生成完成", userId);

            return formatBaiduContent(content);

        } catch (Exception e) {
            logInfo.sendTaskLog("百度对话AI处理异常: " + e.getMessage(), userId, "百度AI");
            e.printStackTrace();
            return "获取内容失败: " + e.getMessage();
        }
    }

    /**
     * 配置百度对话AI功能模式
     * @param page Playwright页面对象
     * @param roles 角色配置字符串
     * @param userId 用户ID
     */
    private void configureBaiduModes(Page page, String roles, String userId) {
        try {
            // 解析角色配置，只支持深度搜索
            boolean enableInternet = roles != null && (roles.contains("baidu-sdss") || roles.contains("sdss"));

            logInfo.sendTaskLog("配置百度对话AI模式 - 深度搜索: " + enableInternet, userId, "百度AI");

            // 先切换到智能模式
            switchToSmartMode(page, userId);

            // 设置深度搜索模式状态
            toggleInternetSearchMode(page, enableInternet, userId);

            // 等待配置生效
            Thread.sleep(1000);

        } catch (Exception e) {
            logInfo.sendTaskLog("配置百度对话AI模式失败: " + e.getMessage(), userId, "百度AI");
            e.printStackTrace();
        }
    }

    /**
     * 切换到智能模式
     * @param page Playwright页面对象
     * @param userId 用户ID
     */
    private void switchToSmartMode(Page page, String userId) {
        try {
            // 模式切换按钮 XPath
            String modeSwitchSelector = "//*[@id=\"cs-bottom\"]/div/div/div[3]/div/div[2]/div[1]/div[1]/div";

            Locator modeSwitchButton = page.locator(modeSwitchSelector);
            if (modeSwitchButton.count() > 0) {
                modeSwitchButton.click();
                Thread.sleep(500);

                // 查找智能模式选项并点击
                String smartModeSelector = "//*[@id=\"cs-bottom\"]/div/div/div[3]/div/div[2]/div[1]/div[1]/div/div[1]/div";
                Locator smartModeOption = page.locator(smartModeSelector);

                if (smartModeOption.count() > 0) {
                    // 检查是否已经是智能模式（是否有勾选图标）
                    Locator checkIcon = smartModeOption.locator("i.cos-icon.cos-icon-check-circle-fill");
                    if (checkIcon.count() == 0) {
                        smartModeOption.click();
                        Thread.sleep(500);
                        logInfo.sendTaskLog("已切换到智能模式", userId, "百度AI");
                    } else {
                        logInfo.sendTaskLog("当前已是智能模式", userId, "百度AI");
                    }
                }
            }
        } catch (Exception e) {
            logInfo.sendTaskLog("切换智能模式失败: " + e.getMessage(), userId, "百度AI");
        }
    }

    /**
     * 切换联网搜索模式（深度搜索）
     * @param page Playwright页面对象
     * @param enable 是否启用
     * @param userId 用户ID
     */
    private void toggleInternetSearchMode(Page page, boolean enable, String userId) {
        try {
            // 使用CSS选择器定位深度搜索按钮（更精确）
            String deepSearchSelector = "#cs-bottom > div > div > div.input-wrap.input-wrap-multi-line > div > div.cs-input-function-wrapper > div.model-select-wrapper.result-page-module-box > div.cs-input-model-button.cos-space-ml-xxs.cs-input-model-button-inactive";

            // 同时尝试active状态的选择器
            String deepSearchActiveSelector = "#cs-bottom > div > div > div.input-wrap.input-wrap-multi-line > div > div.cs-input-function-wrapper > div.model-select-wrapper.result-page-module-box > div.cs-input-model-button.cos-space-ml-xxs.cs-input-model-button-active";

            Locator deepSearchButton = null;
            boolean isCurrentlyEnabled = false;

            // 先尝试active状态的按钮
            Locator activeButton = page.locator(deepSearchActiveSelector);
            if (activeButton.count() > 0) {
                deepSearchButton = activeButton;
                isCurrentlyEnabled = true;
            } else {
                // 再尝试inactive状态的按钮
                Locator inactiveButton = page.locator(deepSearchSelector);
                if (inactiveButton.count() > 0) {
                    deepSearchButton = inactiveButton;
                    isCurrentlyEnabled = false;
                }
            }

            if (deepSearchButton != null) {
                // 等待按钮可见并可点击
                deepSearchButton.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
                Thread.sleep(500);

                logInfo.sendTaskLog("当前深度搜索状态: " + (isCurrentlyEnabled ? "已开启" : "已关闭") + ", 目标状态: " + (enable ? "开启" : "关闭"), userId, "百度AI");

                // 如果当前状态与目标状态不同，点击按钮切换
                if (isCurrentlyEnabled != enable) {
                    // 强制点击按钮
                    try {
                        deepSearchButton.click();
                        logInfo.sendTaskLog("已点击深度搜索按钮", userId, "百度AI");
                    } catch (Exception clickException) {
                        // 如果普通点击失败，尝试JavaScript点击
                        page.evaluate("arguments[0].click()", deepSearchButton);
                        logInfo.sendTaskLog("已通过JavaScript点击深度搜索按钮", userId, "百度AI");
                    }

                    Thread.sleep(1000); // 增加等待时间

                    // 检查点击后的状态，判断是否切换成功
                    boolean newState = false;
                    Locator newActiveButton = page.locator(deepSearchActiveSelector);
                    if (newActiveButton.count() > 0) {
                        newState = true;
                    }

                    // 判断是否切换成功
                    if (newState == enable) {
                        logInfo.sendTaskLog("深度搜索模式已成功" + (newState ? "开启" : "关闭"), userId, "百度AI");
                    } else {
                        logInfo.sendTaskLog("深度搜索模式切换失败，当前状态仍为" + (newState ? "开启" : "关闭"), userId, "百度AI");

                        // 再次尝试点击
                        try {
                            // 重新定位按钮
                            String retrySelector = newState ? deepSearchActiveSelector : deepSearchSelector;
                            Locator retryButton = page.locator(retrySelector);
                            if (retryButton.count() > 0) {
                                page.evaluate("arguments[0].click()", retryButton);
                                Thread.sleep(500);

                                // 最终状态检查
                                boolean finalState = page.locator(deepSearchActiveSelector).count() > 0;

                                if (finalState == enable) {
                                    logInfo.sendTaskLog("第二次尝试成功，深度搜索模式已" + (finalState ? "开启" : "关闭"), userId, "百度AI");
                                } else {
                                    logInfo.sendTaskLog("深度搜索按钮可能无法正常切换，请手动检查", userId, "百度AI");
                                }
                            }
                        } catch (Exception retryException) {
                            logInfo.sendTaskLog("深度搜索按钮点击重试失败", userId, "百度AI");
                        }
                    }
                } else {
                    logInfo.sendTaskLog("深度搜索模式已为目标状态，无需切换", userId, "百度AI");
                }
            } else {
                logInfo.sendTaskLog("未找到深度搜索模式按钮", userId, "百度AI");
            }

        } catch (Exception e) {
            logInfo.sendTaskLog("深度搜索模式操作失败: " + e.getMessage(), userId, "百度AI");
        }
    }

    /**
     * 发送提示词到百度对话AI
     * @param page Playwright页面对象
     * @param userPrompt 用户提示词
     * @param userId 用户ID
     */
    private void sendPromptToBaidu(Page page, String userPrompt, String userId) {
        try {
            // 百度对话AI输入框 XPath
            String inputSelector = "//*[@id=\"chat-input-box\"]";

            Locator inputBox = page.locator(inputSelector);
            if (inputBox.count() == 0) {
                throw new RuntimeException("未找到输入框: " + inputSelector);
            }

            // 点击输入框并输入内容
            inputBox.click();
            Thread.sleep(500);
            inputBox.fill(userPrompt);

            logInfo.sendTaskLog("用户指令已输入完成", userId, "百度AI");

            // 百度对话AI发送按钮 XPath
            String sendButtonSelector = "//*[@id=\"cs-bottom\"]/div/div/div[3]/div/div[2]/div[2]/i";

            Locator sendButton = page.locator(sendButtonSelector);
            if (sendButton.count() > 0) {
                // 检查按钮状态
                String buttonClass = sendButton.getAttribute("class");
                if (buttonClass != null && buttonClass.contains("cos-icon-arrow-up-circle-fill send-icon")) {
                    sendButton.click();
                    logInfo.sendTaskLog("指令已发送成功", userId, "百度AI");
                } else {
                    logInfo.sendTaskLog("发送按钮未就绪，按钮状态: " + buttonClass, userId, "百度AI");
                    // 尝试按Enter键发送
                    inputBox.press("Enter");
                    logInfo.sendTaskLog("已尝试通过Enter键发送", userId, "百度AI");
                }
            } else {
                // 如果没找到发送按钮，尝试按Enter键
                inputBox.press("Enter");
                logInfo.sendTaskLog("未找到发送按钮，已尝试Enter键发送", userId, "百度AI");
            }

        } catch (Exception e) {
            logInfo.sendTaskLog("发送提示词失败: " + e.getMessage(), userId, "百度AI");
            throw new RuntimeException("发送提示词失败", e);
        }
    }

    /**
     * 提取百度对话AI回复内容
     * @param page Playwright页面对象
     * @param userId 用户ID
     * @return 提取的内容
     */
    private String extractBaiduContent(Page page, String userId) {
        try {
            logInfo.sendTaskLog("等待百度对话AI回复...", userId, "百度AI");

            // 定期截图任务
            AtomicInteger screenshotCounter = new AtomicInteger(0);
            Thread screenshotThread = new Thread(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        Thread.sleep(8000); // 每8秒截图一次
                        if (!Thread.currentThread().isInterrupted()) {
                            int count = screenshotCounter.getAndIncrement();
                            logInfo.sendImgData(page, userId + "百度对话AI生成过程" + count, userId);
                        }
                    }
                } catch (InterruptedException e) {
                    // 正常中断，不需要处理
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            screenshotThread.start();

            // 百度对话AI回复内容选择器，使用您提供的准确XPath
            String[] replySelectors = {
                "//*[@id=\"1\"]/div/div",             // 百度AI回答内容的准确XPath
                "//*[@id=\"answer_text_id\"]/div",     // 备选选择器
                ".message-item.assistant .content",
                ".chat-message.assistant",
                ".reply-content",
                "[data-role='assistant']",
                ".ai-response"
            };

            try {
                // 等待回复出现，最多等待2分钟
                boolean replyFound = false;
                Locator replyElement = null;

                for (String selector : replySelectors) {
                    try {
                        replyElement = page.locator(selector);
                        replyElement.waitFor(new Locator.WaitForOptions().setTimeout(120000));
                        replyFound = true;
                        // 移除选择器日志输出，保持任务流程简洁
                        break;
                    } catch (Exception e) {
                        // 继续尝试下一个选择器
                        continue;
                    }
                }

                if (!replyFound) {
                    logInfo.sendTaskLog("未找到百度对话AI回复内容", userId, "百度AI");
                    return "未能获取到百度对话AI的回复内容";
                }

                // 等待内容完全生成 - 监听暂停按钮消失
                logInfo.sendTaskLog("等待百度对话AI生成完成...", userId, "百度AI");

                // 监听暂停按钮，当它消失时表示生成完成 - 使用新的XPath
                String pauseButtonSelector = "//*[@id=\"cs-bottom\"]/div/div/div[3]/div/div[2]/div[2]/i";

                try {
                    // 先等待暂停按钮出现（表示开始生成）
                    Locator pauseButton = page.locator(pauseButtonSelector);

                    // 等待暂停按钮出现或发送按钮变为暂停状态
                    boolean foundPauseButton = false;
                    for (int i = 0; i < 10; i++) {
                        try {
                            String buttonClass = pauseButton.getAttribute("class");
                            if (buttonClass != null && buttonClass.contains("cos-icon-pause-dqa pause-icon")) {
                                foundPauseButton = true;
                                logInfo.sendTaskLog("检测到暂停按钮，百度对话AI开始生成内容", userId, "百度AI");
                                break;
                            }
                            Thread.sleep(1000);
                        } catch (Exception e) {
                            Thread.sleep(1000);
                        }
                    }

                    if (foundPauseButton) {
                        // 然后等待暂停按钮变回发送按钮（表示生成完成）
                        int maxWaitTime = 300; // 最多等待5分钟
                        int checkInterval = 2; // 每2秒检查一次

                        for (int i = 0; i < maxWaitTime; i += checkInterval) {
                            Thread.sleep(checkInterval * 1000);

                            try {
                                String buttonClass = pauseButton.getAttribute("class");
                                if (buttonClass != null && buttonClass.contains("cos-icon-arrow-up-circle-fill send-icon")) {
                                    logInfo.sendTaskLog("发送按钮恢复，百度对话AI生成完成", userId, "百度AI");
                                    break;
                                }
                            } catch (Exception e) {
                                // 按钮可能已经消失或变化，生成可能完成
                                logInfo.sendTaskLog("按钮状态变化，百度对话AI生成完成", userId, "百度AI");
                                break;
                            }

                            // 每30秒输出一次进度日志
                            if (i % 30 == 0 && i > 0) {
                                logInfo.sendTaskLog("百度对话AI仍在生成中，已等待" + i + "秒...", userId, "百度AI");
                            }
                        }
                    } else {
                        logInfo.sendTaskLog("未检测到暂停按钮，可能生成很快完成", userId, "百度AI");
                        Thread.sleep(3000); // 等待3秒确保内容生成
                    }

                } catch (Exception e) {
                    // 如果没有检测到暂停按钮变化，使用内容稳定性检测
                    logInfo.sendTaskLog("未检测到暂停按钮变化，使用内容稳定性检测", userId, "百度AI");

                    String lastContent = "";
                    int stableCount = 0;

                    for (int i = 0; i < 30; i++) { // 最多等待30秒
                        Thread.sleep(1000);

                        try {
                            String currentContent = replyElement.innerHTML();
                            if (currentContent != null && currentContent.equals(lastContent)) {
                                stableCount++;
                                if (stableCount >= 3) { // 连续3秒内容不变，认为生成完成
                                    logInfo.sendTaskLog("百度对话AI内容生成稳定，准备提取", userId, "百度AI");
                                    break;
                                }
                            } else {
                                stableCount = 0;
                                lastContent = currentContent;
                            }
                        } catch (Exception contentException) {
                            // 继续等待
                            continue;
                        }
                    }
                }

                logInfo.sendTaskLog("AI回复生成完成，正在提取内容", userId, "百度AI");
            } finally {
                // 停止截图任务
                screenshotThread.interrupt();
            }

            // 提取最终内容
            String content = "";
            for (String selector : replySelectors) {
                try {
                    Locator elements = page.locator(selector);
                    if (elements.count() > 0) {
                        // 获取最新的回复（通常是最后一个）
                        content = elements.last().innerHTML();
                        if (content != null && !content.trim().isEmpty()) {
                            logInfo.sendTaskLog("成功提取内容，长度: " + content.length(), userId, "百度AI");
                            break;
                        }
                    }
                } catch (Exception e) {
                    // 继续尝试下一个选择器
                    continue;
                }
            }

            // 如果还是没有内容，尝试通用提取
            if (content == null || content.trim().isEmpty()) {
                content = (String) page.evaluate("""
                    () => {
                        // 尝试查找包含AI回复的元素
                        const possibleElements = document.querySelectorAll('div, p, span');
                        let longestText = '';
                        
                        for (let element of possibleElements) {
                            const text = element.innerHTML;
                            if (text && text.length > longestText.length && text.length > 100) {
                                longestText = text;
                            }
                        }
                        
                        return longestText || '未能提取到内容';
                    }
                """);
            }

            logInfo.sendTaskLog("内容提取完成", userId, "百度AI");
            return content;

        } catch (Exception e) {
            logInfo.sendTaskLog("内容提取失败: " + e.getMessage(), userId, "百度AI");
            throw new RuntimeException("内容提取失败", e);
        }
    }

    /**
     * 获取百度AI原链接（从历史记录中提取ori_lid）
     * @param page Playwright页面对象
     * @param userId 用户ID
     * @return 百度AI原链接
     */
    public String getBaiduOriginalUrl(Page page, String userId) {
        try {
            logInfo.sendTaskLog("正在获取百度AI原链接...", userId, "百度AI");
            System.out.println("DEBUG: 当前页面URL: " + page.url());
            
            // 历史记录列表的选择器
            String historyListSelector = "//*[@id=\"app\"]/div/div[2]/div[1]/div/div/div[2]/div/div[2]/div[2]/div/div[1]/div";
            
            System.out.println("DEBUG: 尝试定位历史记录列表，选择器: " + historyListSelector);
            
            // 等待历史记录列表加载
            Locator historyList = page.locator("xpath=" + historyListSelector);
            int listCount = historyList.count();
            System.out.println("DEBUG: 历史记录列表元素数量: " + listCount);
            
            if (listCount == 0) {
                // 尝试其他可能的选择器
                String[] alternativeSelectors = {
                    "//*[@id=\"app\"]//div[contains(@class, 'history')]",
                    "//*[@id=\"app\"]//div[contains(@class, 'sidebar')]",
                    "//*[@id=\"app\"]/div/div[2]/div[1]//div"
                };
                
                for (String altSelector : alternativeSelectors) {
                    Locator altList = page.locator("xpath=" + altSelector);
                    int altCount = altList.count();
                    System.out.println("DEBUG: 备用选择器 " + altSelector + " 元素数量: " + altCount);
                }
                
                logInfo.sendTaskLog("未找到历史记录列表", userId, "百度AI");
                return "";
            }
            
            // 遍历历史记录项
            Locator historyItems = historyList.locator("xpath=./div"); // 直接子div
            int itemCount = historyItems.count();
            
            System.out.println("DEBUG: 找到 " + itemCount + " 个历史记录项");
            
            // 如果没有直接子div，尝试其他方式
            if (itemCount == 0) {
                historyItems = historyList.locator("div");
                itemCount = historyItems.count();
                System.out.println("DEBUG: 尝试所有子div，找到 " + itemCount + " 个项目");
            }
            
            for (int i = 0; i < itemCount && i < 10; i++) { // 限制最多检查10个项目
                try {
                    Locator item = historyItems.nth(i);
                    System.out.println("DEBUG: 检查第 " + (i + 1) + " 个历史记录项");
                    
                    // 获取项目的HTML结构用于调试
                    String itemHtml = item.innerHTML();
                    System.out.println("DEBUG: 项目 " + (i + 1) + " HTML长度: " + itemHtml.length());
                    
                    // 查找包含history-item-content类的元素
                    Locator contentElement = item.locator("xpath=.//*[contains(@class, 'history-item-content')]");
                    int contentCount = contentElement.count();
                    System.out.println("DEBUG: 项目 " + (i + 1) + " 中找到 " + contentCount + " 个 history-item-content 元素");
                    
                    if (contentCount == 0) {
                        // 尝试查找其他可能的类名
                        String[] possibleClasses = {"history", "item", "content", "chat", "conversation"};
                        for (String className : possibleClasses) {
                            Locator altElement = item.locator("xpath=.//*[contains(@class, '" + className + "')]");
                            int altCount = altElement.count();
                            if (altCount > 0) {
                                System.out.println("DEBUG: 项目 " + (i + 1) + " 中找到 " + altCount + " 个包含 '" + className + "' 类的元素");
                            }
                        }
                        continue;
                    }
                    
                    // 检查每个content元素
                    for (int j = 0; j < contentCount; j++) {
                        Locator singleContent = contentElement.nth(j);
                        
                        // 获取所有属性
                        String attributes = (String) singleContent.evaluate("element => {" +
                            "const attrs = {};" +
                            "for (let attr of element.attributes) {" +
                                "attrs[attr.name] = attr.value;" +
                            "}" +
                            "return JSON.stringify(attrs);" +
                        "}");
                        
                        System.out.println("DEBUG: 项目 " + (i + 1) + " content " + (j + 1) + " 属性: " + attributes);
                        
                        // 获取data-show-ext属性
                        String dataShowExt = singleContent.getAttribute("data-show-ext");
                        
                        if (dataShowExt != null && !dataShowExt.isEmpty()) {
                            System.out.println("DEBUG: 找到 data-show-ext: " + dataShowExt);
                            
                            // 从data-show-ext中提取ori_lid
                            String oriLid = extractOriLidFromDataShowExt(dataShowExt);
                            
                            if (oriLid != null && !oriLid.isEmpty()) {
                                // 构造原链接
                                String originalUrl = "https://chat.baidu.com/search?isShowHello=1&extParams=%7B%22ori_lid%22%3A%22" + oriLid + "%22%2C%22subEnterType%22%3A%22his_middle%22%2C%22enter_type%22%3A%22chat_url%22%7D";
                                
                                logInfo.sendTaskLog("成功获取百度AI原链接，ori_lid: " + oriLid, userId, "百度AI");
                                System.out.println("DEBUG: 构造的原链接: " + originalUrl);
                                return originalUrl;
                            } else {
                                System.out.println("DEBUG: 无法从 data-show-ext 中提取 ori_lid");
                            }
                        } else {
                            System.out.println("DEBUG: 项目 " + (i + 1) + " content " + (j + 1) + " 没有 data-show-ext 属性");
                        }
                    }
                } catch (Exception e) {
                    System.out.println("DEBUG: 处理第 " + (i + 1) + " 个项目时出错: " + e.getMessage());
                    continue;
                }
            }
            
            logInfo.sendTaskLog("未能从历史记录中提取到ori_lid", userId, "百度AI");
            return "";
            
        } catch (Exception e) {
            logInfo.sendTaskLog("获取百度AI原链接失败: " + e.getMessage(), userId, "百度AI");
            System.out.println("DEBUG: 获取百度AI原链接失败: " + e.getMessage());
            e.printStackTrace();
            return "";
        }
    }
    
    /**
     * 从data-show-ext属性中提取ori_lid
     * @param dataShowExt data-show-ext属性值
     * @return ori_lid值
     */
    private String extractOriLidFromDataShowExt(String dataShowExt) {
        try {
            System.out.println("DEBUG: 尝试提取ori_lid，data-show-ext内容: " + dataShowExt);
            
            // data-show-ext可能是JSON格式，尝试解析
            if (dataShowExt.contains("ori_lid")) {
                System.out.println("DEBUG: data-show-ext包含ori_lid字段");
                
                // 使用正则表达式提取ori_lid的值
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"ori_lid\"\\s*:\\s*\"([^\"]+)\"");
                java.util.regex.Matcher matcher = pattern.matcher(dataShowExt);
                
                if (matcher.find()) {
                    String oriLid = matcher.group(1);
                    System.out.println("DEBUG: 成功提取ori_lid: " + oriLid);
                    return oriLid;
                } else {
                    System.out.println("DEBUG: 正则表达式未匹配到ori_lid");
                    
                    // 尝试其他可能的格式
                    String[] patterns = {
                        "ori_lid[\"']?\\s*[:=]\\s*[\"']([^\"']+)[\"']",
                        "ori_lid[\"']?\\s*[:=]\\s*([0-9]+)",
                        "\"ori_lid\"\\s*:\\s*([0-9]+)"
                    };
                    
                    for (String patternStr : patterns) {
                        pattern = java.util.regex.Pattern.compile(patternStr);
                        matcher = pattern.matcher(dataShowExt);
                        if (matcher.find()) {
                            String oriLid = matcher.group(1);
                            System.out.println("DEBUG: 备用正则表达式成功提取ori_lid: " + oriLid);
                            return oriLid;
                        }
                    }
                }
            } else {
                System.out.println("DEBUG: data-show-ext不包含ori_lid字段");
            }
            return null;
        } catch (Exception e) {
            System.out.println("DEBUG: 提取ori_lid时出错: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 从百度AI原链接URL中提取ori_lid
     * @param originalUrl 原链接URL
     * @return ori_lid值
     */
    private String extractOriLidFromUrl(String originalUrl) {
        try {
            if (originalUrl == null || originalUrl.trim().isEmpty()) {
                System.out.println("DEBUG: 原链接为空，无法提取ori_lid");
                return null;
            }
            
            System.out.println("DEBUG: 尝试从URL提取ori_lid: " + originalUrl);
            
            // 从URL中提取ori_lid，格式：%22ori_lid%22%3A%22...%22
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("%22ori_lid%22%3A%22([^%\"]+)%22");
            java.util.regex.Matcher matcher = pattern.matcher(originalUrl);
            
            if (matcher.find()) {
                String oriLid = matcher.group(1);
                System.out.println("DEBUG: 从URL成功提取ori_lid: " + oriLid);
                return oriLid;
            } else {
                System.out.println("DEBUG: URL中未找到ori_lid");
                return null;
            }
        } catch (Exception e) {
            System.out.println("DEBUG: 从URL提取ori_lid时出错: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 获取百度对话AI分享链接
     * @param page Playwright页面对象
     * @param userId 用户ID
     * @return 分享链接
     */
    public String getBaiduShareUrl(Page page, String userId) {
        AtomicReference<String> shareUrlRef = new AtomicReference<>();

        clipboardLockManager.runWithClipboardLock(() -> {
            try {
                // 不输出"正在获取分享链接..."，用户不需要看到这个过程

                // 百度对话AI分享按钮选择器，使用通用的XPath模式
                String[] shareSelectors = {
                    "//*[starts-with(@id,'chat-id-')]/div/div/div/div/div[2]/div/div[6]", // 通用的分享按钮XPath
                    "//*[@id=\"chat-id-20872991305\"]/div/div/div/div/div[2]/div/div[6]", // 具体的分享按钮
                    ".share-button",
                    "button:has-text('分享')",
                    "[data-testid='share-button']",
                    ".action-share"
                };

                Locator shareButton = null;
                for (String selector : shareSelectors) {
                    Locator temp = page.locator(selector);
                    if (temp.count() > 0) {
                        shareButton = temp.last();
                        break;
                    }
                }

                if (shareButton != null) {
                    shareButton.click();
                    Thread.sleep(1000);

                    // 优先从输入框获取分享链接
                    try {
                        String linkInputSelector = "/html/body/div[8]/div[2]/div[2]/div/div[1]/div/input";
                        Locator linkInput = page.locator(linkInputSelector);
                        linkInput.waitFor(new Locator.WaitForOptions().setTimeout(10000));

                        String shareUrl = linkInput.inputValue();
                        if (shareUrl != null && !shareUrl.trim().isEmpty()) {
                            shareUrlRef.set(shareUrl);
                            logInfo.sendTaskLog("分享链接获取成功", userId, "百度AI");
                            return;
                        }
                    } catch (Exception inputException) {
                        // 静默处理，不输出调试信息
                    }

                    // 降级方案：查找复制链接按钮
                    String[] copySelectors = {
                        "button:has-text('复制链接')",
                        ".copy-link",
                        "[data-testid='copy-link']"
                    };

                    Locator copyButton = null;
                    for (String selector : copySelectors) {
                        Locator temp = page.locator(selector);
                        if (temp.count() > 0) {
                            copyButton = temp.first();
                            break;
                        }
                    }

                    if (copyButton != null) {
                        copyButton.click();
                        Thread.sleep(1000);

                        // 读取剪贴板内容
                        String shareUrl = (String) page.evaluate("navigator.clipboard.readText()");
                        shareUrlRef.set(shareUrl);

                        logInfo.sendTaskLog("分享链接获取成功", userId, "百度AI");
                    }
                    // 如果没找到按钮，不输出"未找到"信息
                }
                // 如果没找到分享按钮，不输出"未找到分享按钮"信息

            } catch (Exception e) {
                // 静默处理分享链接获取失败，不影响主流程
            }
        });

        return shareUrlRef.get();
    }

    /**
     * 格式化百度对话AI内容
     * @param content 原始内容
     * @return 格式化后的HTML内容
     */
    private String formatBaiduContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "未能获取到有效内容";
        }

        // 创建统一的HTML格式
        return "<div class='baidu-response' style='max-width: 800px; margin: 0 auto; background-color: #fff; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); padding: 20px; font-family: Arial, sans-serif; line-height: 1.6; color: #333;'>"
                + content + "</div>";
    }



    /**
     * 等待并获取百度AI登录二维码
     * @param page Playwright页面对象
     * @param userId 用户ID
     * @return 二维码截图URL，失败返回null
     */
    public String waitAndGetQRCode(Page page, String userId) {
        try {
            // 导航到百度AI登录页面
            page.navigate("https://chat.baidu.com/");
            page.waitForLoadState(LoadState.LOAD);
            Thread.sleep(2000);

            // 检查是否已经登录
            String loginStatus = checkBaiduLogin(page, false);
            if (!"false".equals(loginStatus)) {
                // 如果已经登录，返回当前页面截图
                logInfo.sendTaskLog("百度AI已登录，用户: " + loginStatus, userId, "百度AI");
                return screenshotUtil.screenshotAndUpload(page, "getBaiduLoggedIn.png");
            }

            // 查找并点击登录按钮
            String loginButtonSelector = "//*[@id=\"app\"]/div/div[1]/div[2]/div/div";
            Locator loginButton = page.locator(loginButtonSelector);
            
            if (loginButton.count() > 0 && loginButton.isVisible()) {
                String buttonText = loginButton.textContent();
                logInfo.sendTaskLog("找到登录按钮，文本内容: " + buttonText, userId, "百度AI");
                
                if (buttonText != null && (buttonText.contains("登录") || buttonText.contains("登陆"))) {
                    // 点击登录按钮
                    loginButton.click();
                    logInfo.sendTaskLog("已点击登录按钮，等待登录页面加载", userId, "百度AI");
                    Thread.sleep(3000); // 等待登录页面加载
                    
                    // 等待QR码出现或登录界面稳定
                    page.waitForLoadState(LoadState.NETWORKIDLE);
                    Thread.sleep(2000);
                    
                    // 截图并返回
                    logInfo.sendTaskLog("准备截图二维码", userId, "百度AI");
                    return screenshotUtil.screenshotAndUpload(page, "getBaiduQrCode.png");
                } else {
                    logInfo.sendTaskLog("登录按钮文本不匹配: " + buttonText, userId, "百度AI");
                }
            } else {
                logInfo.sendTaskLog("未找到登录按钮，按钮数量: " + loginButton.count(), userId, "百度AI");
            }

            // 如果没有找到登录按钮，直接截图当前页面
            logInfo.sendTaskLog("截图当前页面", userId, "百度AI");
            return screenshotUtil.screenshotAndUpload(page, "getBaiduLoginPage.png");

        } catch (Exception e) {
            e.printStackTrace();
            logInfo.sendTaskLog("获取二维码异常: " + e.getMessage(), userId, "百度AI");
            try {
                // 出现异常时也返回当前页面截图
                return screenshotUtil.screenshotAndUpload(page, "getBaiduLoginError.png");
            } catch (Exception screenshotException) {
                screenshotException.printStackTrace();
                logInfo.sendTaskLog("截图异常: " + screenshotException.getMessage(), userId, "百度AI");
                return null;
            }
        }
    }

    /**
     * 解析百度对话AI角色配置
     * @param roles 角色配置字符串
     * @return 解析结果描述
     */
    public String parseBaiduRoles(String roles) {
        if (roles == null || roles.isEmpty()) {
            return "百度对话AI";
        }

        StringBuilder result = new StringBuilder("百度对话AI");
        
        // 附加功能：只支持深度搜索
        if (roles.contains("baidu-sdss") || roles.contains("sdss")) {
            result.append("+深度搜索");
        }

        return result.toString();
    }

    /**
     * 保存百度对话AI内容到稿库
     * @param page Playwright页面对象
     * @param userInfoRequest 用户请求信息
     * @param roles 角色配置 (支持: baidu-sdss深度搜索模式)
     * @param userId 用户ID
     * @param content 内容
     * @return 格式化后的内容
     */
    public String saveBaiduContent(Page page, UserInfoRequest userInfoRequest, String roles, 
                                  String userId, String content) {
        try {
            // 获取会话ID
            String sessionId = extractSessionId(page);
            
            // 获取分享链接
            String shareUrl = getBaiduShareUrl(page, userId);
            
            // 获取原链接并提取ori_lid
            String originalUrl = getBaiduOriginalUrl(page, userId);
            String oriLid = extractOriLidFromUrl(originalUrl);
            
            // 如果无法获取分享链接，使用当前页面URL作为默认值
            if (shareUrl == null || shareUrl.trim().isEmpty()) {
                shareUrl = page.url();
                // 不输出具体URL，只记录获取方式
                logInfo.sendTaskLog("已获取页面链接", userId, "百度AI");
            }

            // 设置请求参数 - 将ori_lid保存到baiduChatId字段用于会话连续性
            String chatIdToSave = (oriLid != null && !oriLid.trim().isEmpty()) ? oriLid : sessionId;
            userInfoRequest.setBaiduChatId(chatIdToSave);
            userInfoRequest.setDraftContent(content);
            userInfoRequest.setAiName(parseBaiduRoles(roles));
            userInfoRequest.setShareUrl(shareUrl != null ? shareUrl : "");
            userInfoRequest.setShareImgUrl(""); // 百度对话AI暂不支持分享图片

            // 保存到数据库
            RestUtils.post(url + "/saveDraftContent", userInfoRequest);

            // 发送ori_lid到前端更新baiduChatId，使用直接发送方法
            if (oriLid != null && !oriLid.trim().isEmpty()) {
                logInfo.sendChatDataDirect(oriLid, userId, "RETURN_BAIDU_CHATID");
                logInfo.sendTaskLog("百度AI会话ID已发送到前端: " + oriLid, userId, "百度AI");
            } else {
                // 如果没有ori_lid，发送传统的sessionId
                logInfo.sendChatData(page, "/chat/([^/?#]+)", userId, "RETURN_BAIDU_CHATID", 1);
            }
            
            // 发送结果数据，投递到媒体功能由前端处理
            String formattedContent = formatBaiduContent(content);
            
            // 使用原链接作为分享链接，如果获取不到原链接则使用传统分享链接
            String finalShareUrl = (originalUrl != null && !originalUrl.trim().isEmpty()) ? originalUrl : shareUrl;
            logInfo.sendResData(formattedContent, userId, "百度AI", "RETURN_BAIDU_RES", finalShareUrl, "");

            if (oriLid != null && !oriLid.trim().isEmpty()) {
                logInfo.sendTaskLog("百度AI会话ID已保存: " + oriLid, userId, "百度AI");
            }
            logInfo.sendTaskLog("百度对话AI内容已保存到稿库", userId, "百度AI");

            return content;

        } catch (Exception e) {
            logInfo.sendTaskLog("保存百度对话AI内容失败: " + e.getMessage(), userId, "百度AI");
            e.printStackTrace();
            return content;
        }
    }

    /**
     * 从页面URL提取会话ID
     * @param page Playwright页面对象
     * @return 会话ID
     */
    private String extractSessionId(Page page) {
        try {
            String url = page.url();
            
            // 百度AI的URL格式多样，尝试多种提取方式
            if (url.contains("/chat/")) {
                // 格式1: https://chat.baidu.com/chat/sessionId
                String[] parts = url.split("/chat/");
                if (parts.length > 1) {
                    String sessionPart = parts[1];
                    // 移除可能的查询参数
                    if (sessionPart.contains("?")) {
                        sessionPart = sessionPart.split("\\?")[0];
                    }
                    if (!sessionPart.trim().isEmpty()) {
                        return sessionPart;
                    }
                }
            }
            
            // 格式2: 从URL参数中提取会话ID
            if (url.contains("sessionId=")) {
                String[] parts = url.split("sessionId=");
                if (parts.length > 1) {
                    String sessionPart = parts[1];
                    if (sessionPart.contains("&")) {
                        sessionPart = sessionPart.split("&")[0];
                    }
                    if (!sessionPart.trim().isEmpty()) {
                        return sessionPart;
                    }
                }
            }
            
            // 格式3: 从URL中提取任何像会话ID的字符串
            // 尝试提取包含数字和字母的长ID
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("[a-zA-Z0-9]{10,}");
            java.util.regex.Matcher matcher = pattern.matcher(url);
            if (matcher.find()) {
                return matcher.group();
            }
            
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 等待百度对话AI HTML DOM内容完成
     * @param page Playwright页面对象
     * @param userId 用户ID
     * @param aiName AI名称
     * @return 提取的HTML内容
     */
    public String waitBaiduHtmlDom(Page page, String userId, String aiName) {
        try {
            logInfo.sendTaskLog("等待" + aiName + "回复完成...", userId, aiName);

            // 等待页面稳定
            Thread.sleep(5000);

            // 使用具体的百度对话AI回复选择器等待内容
            String[] replySelectors = {
                "//*[@id=\"1\"]/div/div",             // 百度AI回答内容的准确XPath  
                "//*[@id=\"answer_text_id\"]/div",   // 百度对话AI具体回复内容选择器
                ".message-item.assistant .content",
                ".chat-message.assistant",
                ".reply-content",
                "[data-role='assistant']",
                ".ai-response"
            };

            // 等待回复元素出现
            Locator replyElement = null;
            for (String selector : replySelectors) {
                try {
                    replyElement = page.locator(selector);
                    replyElement.waitFor(new Locator.WaitForOptions().setTimeout(30000));
                    // 移除选择器日志输出，保持任务流程简洁
                    break;
                } catch (Exception e) {
                    // 继续尝试下一个选择器
                    continue;
                }
            }

            if (replyElement == null) {
                logInfo.sendTaskLog("未找到" + aiName + "回复元素", userId, aiName);
                return "未能找到" + aiName + "回复内容";
            }

            // 多次尝试获取内容，直到获取到有效内容
            String content = "";
            int maxAttempts = 30; // 最多尝试30次，每次间隔2秒，总共1分钟
            
            for (int i = 0; i < maxAttempts; i++) {
                try {
                    content = replyElement.innerHTML();
                    
                    if (content != null && !content.trim().isEmpty() && 
                        !content.contains("未能提取到内容") && content.length() > 50) {
                        logInfo.sendTaskLog(aiName + "内容获取成功，长度: " + content.length(), userId, aiName);
                        return content;
                    }
                    
                    Thread.sleep(2000); // 等待2秒后重试
                    
                } catch (Exception e) {
                    if (i == maxAttempts - 1) {
                        throw e;
                    }
                    Thread.sleep(2000);
                }
            }

            if (content == null || content.trim().isEmpty()) {
                content = "未能在指定时间内获取到" + aiName + "的回复内容";
            }

            return content;

        } catch (Exception e) {
            logInfo.sendTaskLog(aiName + "内容获取失败: " + e.getMessage(), userId, aiName);
            return "获取" + aiName + "内容失败: " + e.getMessage();
        }
    }
}