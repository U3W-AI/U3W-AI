package com.playwright.utils;

import com.alibaba.fastjson.JSONObject;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.playwright.entity.UserInfoRequest;
import com.playwright.entity.mcp.McpResult;
import com.playwright.websocket.WebSocketClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 百度对话AI工具类
 *
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
     *
     * @param page     Playwright页面对象
     * @param navigate 是否需要先导航到百度对话AI页面
     * @return 登录状态，如果已登录则返回用户名，否则返回"false"
     */
    public String checkBaiduLogin(Page page, boolean navigate) throws Exception {
        try {
            if (navigate) {
                page.navigate("https://chat.baidu.com/", new Page.NavigateOptions().setTimeout(60000)); // 🔥 增加导航超时

                page.waitForLoadState(LoadState.LOAD, new Page.WaitForLoadStateOptions().setTimeout(30000)); // 🔥 增加加载超时

                Thread.sleep(2000);
            }

            // 等待页面完全加载
            page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(45000)); // 🔥 增加网络空闲超时
            Thread.sleep(1000);

            // 优先检查是否有登录按钮（未登录的关键标志）
            try {
                // 新的登录按钮选择器，支持多种可能的结构
                String[] loginButtonSelectors = {
                    ".login-btn", // 新的登录按钮类名
                    ".login-btn div", // 登录按钮内部div
                    "div[data-click-log*='login_button']", // 通过data-click-log属性定位
                    "div[data-show-ext*='login_button']", // 通过data-show-ext属性定位
                    "//*[@id=\"app\"]/div/div[1]/div[2]/div/div", // 旧的选择器作为备用
                    "button:has-text('登录')", // 通用登录按钮
                    "div:has-text('登录')" // 通用登录div
                };

                for (String selector : loginButtonSelectors) {
                    try {
                        Locator loginButtonElement = page.locator(selector);
                        if (loginButtonElement.count() > 0 && loginButtonElement.isVisible()) {
                            String buttonText = loginButtonElement.textContent();
                                                         if (buttonText != null && (buttonText.contains("登录") || buttonText.contains("登陆"))) {
                                 return "false"; // 存在登录按钮，说明未登录
                             }
                        }
                    } catch (Exception selectorException) {
                        // 继续尝试下一个选择器
                        continue;
                    }
                }
            } catch (Exception e) {
//                继续其他检查
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
        } catch (com.microsoft.playwright.impl.TargetClosedError e) {
            return "false";
        } catch (TimeoutError e) {
            return "false";
        } catch (Exception e) {
            // 如果是页面导航导致的异常，可能是登录成功了
            if (e.getMessage() != null && e.getMessage().contains("navigation")) {
                try {
                    // 等待页面稳定后再次检查
                    Thread.sleep(2000);
                    page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(30000));

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
            throw e;
//            return "false";
        }
    }

    /**
     * 处理百度对话AI页面交互
     *
     * @param page       Playwright页面对象
     * @param userPrompt 用户提示词
     * @param userId     用户ID
     * @param roles      角色配置 (支持: baidu-sdss深度搜索)
     * @param chatId     会话ID
     * @return AI生成内容
     */
    public String handleBaiduAI(Page page, String userPrompt, String userId, String roles, String chatId) throws Exception {
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

            // 等待但不提取回复内容，//百度AI图文并茂提取图片HTML无效且过长
            String content = extractBaiduContent(page, userId);
//            String content = "百度AI调用成功";  //百度AI图文并茂提取图片HTML无效且过长

            // 添加获取结果截图
            logInfo.sendImgData(page, userId + "百度对话AI生成完成", userId);

            return content;

        } catch (Exception e) {
            logInfo.sendTaskLog("百度对话AI处理异常", userId, "百度AI");
            throw e;
        }
    }

    /**
     * 配置百度对话AI功能模式
     *
     * @param page   Playwright页面对象
     * @param roles  角色配置字符串
     * @param userId 用户ID
     */
    private void configureBaiduModes(Page page, String roles, String userId) throws InterruptedException {
        try {
            // 解析角色配置，只支持深度搜索
            boolean enableInternet = roles != null && (roles.contains("baidu-sdss") || roles.contains("sdss"));

            logInfo.sendTaskLog("配置百度对话AI模式 - 深度搜索: " + enableInternet, userId, "百度AI");

            // 先切换到智能模式
//            switchToSmartMode(page, userId);

            // 设置深度搜索模式状态
//            toggleInternetSearchMode(page, enableInternet, userId);
            if (roles != null && (roles.contains("baidu-sdss") || roles.contains("sdss"))) {
                page.locator(".deep-search-icon").click();
                Thread.sleep(500);
            }
            if (roles != null && (roles.contains("dsr1") || roles.contains("dsv3") || roles.contains("wenxin") || roles.contains("web"))) {
                page.locator(".model-select-toggle").click();
                Thread.sleep(500);
                if (roles != null && (roles.contains("web"))) {
                    page.locator(".cos-switcher.cos-sm").click();
                    Thread.sleep(500);
                }
                if (roles != null && roles.contains("dsr1")) {
                    page.locator(".input-capsules-model-list-item:has-text('DeepSeek-R1')").click();
                } else if (roles != null && roles.contains("dsv3")) {
                    page.locator(".input-capsules-model-list-item:has-text('DeepSeek-V3')").click();
                } else if (roles != null && roles.contains("wenxin")) {
                    page.locator(".input-capsules-model-list-item:has-text('文心')").click();
                }
                Thread.sleep(500);
                page.locator(".model-select-toggle").click();
            }
            // 等待配置生效
            Thread.sleep(1000);

        } catch (Exception e) {
            logInfo.sendTaskLog("配置百度对话AI模式失败", userId, "百度AI");
            throw e;
        }
    }

    /**
     * 切换到智能模式
     *
     * @param page   Playwright页面对象
     * @param userId 用户ID
     */
    private void switchToSmartMode(Page page, String userId) throws InterruptedException {
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
            logInfo.sendTaskLog("切换智能模式失败", userId, "百度AI");
            throw e;

        }
    }

    /**
     * 切换联网搜索模式（深度搜索）
     *
     * @param page   Playwright页面对象
     * @param enable 是否启用
     * @param userId 用户ID
     */
    private void toggleInternetSearchMode(Page page, boolean enable, String userId) throws InterruptedException {
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
            logInfo.sendTaskLog("深度搜索模式操作失败: ", userId, "百度AI");
            throw e;
        }
    }

    /**
     * 发送提示词到百度对话AI
     *
     * @param page       Playwright页面对象
     * @param userPrompt 用户提示词
     * @param userId     用户ID
     */
    private void sendPromptToBaidu(Page page, String userPrompt, String userId) throws InterruptedException {
        try {
            // 百度对话AI输入框 XPath
            String inputSelector = "//*[@id=\"chat-input-box\"]";

            Locator inputBox = page.locator(inputSelector);
            if (inputBox.count() == 0) {
                inputBox = page.locator("#chat-textarea");
                if (inputBox.isVisible()) {
                    Thread.sleep(500);
                    inputBox.fill(userPrompt);
                }
            } else {
                // 点击输入框并输入内容
                inputBox.click();
                Thread.sleep(500);
                inputBox.fill(userPrompt);
            }


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
            logInfo.sendTaskLog("发送提示词失败", userId, "百度AI");
            throw e;
        }
    }

    /**
     * 提取百度对话AI回复内容
     *
     * @param page   Playwright页面对象
     * @param userId 用户ID
     * @return 提取的内容
     */
    private String extractBaiduContent(Page page, String userId) throws InterruptedException {
        try {
            logInfo.sendTaskLog("等待百度对话AI回复...", userId, "百度AI");

            // 定期截图任务
//            AtomicInteger screenshotCounter = new AtomicInteger(0);
//            Thread screenshotThread = new Thread(() -> {
//                try {
//                    while (!Thread.currentThread().isInterrupted()) {
//                        Thread.sleep(8000); // 每8秒截图一次
//                        if (!Thread.currentThread().isInterrupted()) {
//                            // 检查页面是否已关闭
//                            if (page.isClosed()) {
//                                break;
//                            }
//                            int count = screenshotCounter.getAndIncrement();
//                            logInfo.sendImgData(page, userId + "百度对话AI生成过程" + count, userId);
//                        }
//                    }
//                } catch (InterruptedException e) {
//                    // 正常中断，不需要处理
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            });
//            screenshotThread.start();

            Locator container = page.locator("div.chat-qa-container").last();
            // 百度对话AI回复内容选择器，使用您提供的准确XPath
            String[] replySelectors = {
//                    "//*[@id=\"1\"]/div/div"             // 百度AI回答内容的准确XPath
//                    "//*[@id=\"answer_text_id\"]/div",     // 备选选择器
//                    ".message-item.assistant .content",
//                    ".chat-message.assistant",
//                    ".reply-content",
//                    "[data-role='assistant']",
//                    ".ai-response"
//                    "div.cosd-markdown-content"
                    "div.data-show-ext"
            };
            try {
                // 等待回复出现，最多等待3秒
                boolean replyFound = false;
                Locator replyElement = null;

                for (String selector : replySelectors) {
                    try {
                        replyElement = container.locator(selector).last();
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

                try {

                    for (int i = 0; i < 10; i++) {
                        try {

                            Thread.sleep(10000);
                            boolean visible = page.locator("//img[@class='pause-icon']").isVisible();
                            if (!visible) {
                                logInfo.sendTaskLog("百度对话AI生成完成", userId, "百度AI");
                                break;
                            }
                        } catch (Exception e) {
                            // 按钮可能已经消失或变化，生成可能完成
                            logInfo.sendTaskLog("按钮状态变化，百度对话AI生成完成", userId, "百度AI");
                            break;
                        }
                        if (i % 2 == 0) {
                            logInfo.sendTaskLog("百度对话AI生成中...", userId, "百度AI");
                        }
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
                            //                            String currentContent = replyElement.innerText();
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
//                screenshotThread.interrupt();
            }

//            // 提取最终内容
//            String content = "";
//            StringBuilder contentBuilder = new StringBuilder();
//            for (String selector : replySelectors) {
//                try {
//                    Locator elements = container.locator(selector);
//                    if (elements.count() > 0) {
//                        // 获取最新的回复
//
//                        elements.all().forEach((e) -> contentBuilder.append(e.innerHTML()));
//                        content=contentBuilder.toString();
//                        if (content != null && !content.toString().trim().isEmpty()) {
//                            if(content.length() > 8192){
//
//                                content = content.substring(0, 8192);
//
//                                logInfo.sendTaskLog("成功提取内容，长度已裁剪: " + 8192, userId, "百度AI");
//                            }else{
//                                logInfo.sendTaskLog("成功提取内容，长度: " + content.length(), userId, "百度AI");
//                            }
//                            break;
//                        }
//                    }
//                } catch (Exception e) {
//                    // 继续尝试下一个选择器
//                    continue;
//                }
//            }
//
//            // 如果还是没有内容，尝试通用提取
//            if (content == null || content.toString().trim().isEmpty()) {
//                content = (String)page.evaluate("""
//                            () => {
//                                // 尝试查找包含AI回复的元素
//                                const possibleElements = document.querySelectorAll('div, p, span');
//                                let longestText = '';
//
//                                for (let element of possibleElements) {
//                                    const text = element.innerHTML;
//                                    if (text && text.length > longestText.length && text.length > 100) {
//                                        longestText = text;
//                                    }
//                                }
//
//                                return longestText || '未能提取到内容';
//                            }
//                        """);
//            }
//
//            logInfo.sendTaskLog("内容提取完成", userId, "百度AI");
//            return content.toString();

            String content = "本次回复无文本内容";
            Locator editor = page.locator("div#editor-container");
            Locator comate = page.locator("div#comate-chat-workspace");
            if(editor.count()>0){
                Locator copyButton = page.locator("i.cos-icon.cos-icon-copy.button_AxaRd");
                if(copyButton.count()>0){
                    copyButton.click();
                    Thread.sleep(1000);
                    content = (String) page.evaluate("navigator.clipboard.readText()");
                }
            }else if(comate.count()>0){
                Locator copyButton = page.locator("i.cos-icon.cos-icon-copy.button_f81z6_14");
                if(copyButton.count()>0){
                    copyButton.click();
                    Thread.sleep(1000);
                    content = (String) page.evaluate("navigator.clipboard.readText()");
                }
            }else{

                Locator locator = page.locator("div.chat-qa-container");
                Locator element = locator.last().locator(".answer-box.last-answer-box");
                Locator copyButton = element.locator("i.cos-icon.cos-icon-copy.icon_1nicr_12").last();
                // 百度AI无法分享的组件也有分享按钮只是不可见，不可用
                if(copyButton.count()>0){
                    if(copyButton.isVisible()){
                        copyButton.click();
                        Thread.sleep(1000);
                        content = (String) page.evaluate("navigator.clipboard.readText()");
                    }
                }

            }
            return content;

        } catch (Exception e) {
            logInfo.sendTaskLog("内容提取失败", userId, "百度AI");
            throw e;
        }
    }

    /**
     * 获取百度AI原链接（从历史记录中提取ori_lid）
     *
     * @param page   Playwright页面对象
     * @param userId 用户ID
     * @return 百度AI原链接
     */
    public String getBaiduOriginalUrl(Page page, String userId) throws Exception {
        try {
            logInfo.sendTaskLog("正在获取百度AI原链接...", userId, "百度AI");

            // 历史记录列表的选择器
            String historyListSelector = "//*[@id=\"app\"]/div/div[2]/div[1]/div/div/div[2]/div/div[2]/div[2]/div/div[1]/div";


            // 等待历史记录列表加载
            Locator historyList = page.locator("xpath=" + historyListSelector);
            int listCount = historyList.count();

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
                }

                logInfo.sendTaskLog("未找到历史记录列表", userId, "百度AI");
                return "";
            }

            // 遍历历史记录项
            Locator historyItems = historyList.locator("xpath=./div"); // 直接子div
            int itemCount = historyItems.count();


            // 如果没有直接子div，尝试其他方式
            if (itemCount == 0) {
                historyItems = historyList.locator("div");
                itemCount = historyItems.count();
            }

            for (int i = 0; i < itemCount && i < 10; i++) { // 限制最多检查10个项目
                try {
                    Locator item = historyItems.nth(i);

                    // 获取项目的HTML结构用于调试
                    String itemHtml = item.innerHTML();

                    // 查找包含history-item-content类的元素
                    Locator contentElement = item.locator("xpath=.//*[contains(@class, 'history-item-content')]");
                    int contentCount = contentElement.count();

                    if (contentCount == 0) {
                        // 尝试查找其他可能的类名
                        String[] possibleClasses = {"history", "item", "content", "chat", "conversation"};
                        for (String className : possibleClasses) {
                            Locator altElement = item.locator("xpath=.//*[contains(@class, '" + className + "')]");
                            int altCount = altElement.count();
                            if (altCount > 0) {
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


                        // 获取data-show-ext属性
                        String dataShowExt = singleContent.getAttribute("data-show-ext");

                        if (dataShowExt != null && !dataShowExt.isEmpty()) {

                            // 从data-show-ext中提取ori_lid
                            String oriLid = extractOriLidFromDataShowExt(dataShowExt);

                            if (oriLid != null && !oriLid.isEmpty()) {
                                // 构造原链接
                                String originalUrl = "https://chat.baidu.com/search?isShowHello=1&extParams=%7B%22ori_lid%22%3A%22" + oriLid + "%22%2C%22subEnterType%22%3A%22his_middle%22%2C%22enter_type%22%3A%22chat_url%22%7D";

                                logInfo.sendTaskLog("成功获取百度AI原链接，ori_lid: " + oriLid, userId, "百度AI");
                                return originalUrl;
                            } else {
                            }
                        } else {
                        }
                    }
                } catch (Exception e) {
                    continue;
                }
            }

            logInfo.sendTaskLog("未能从历史记录中提取到ori_lid", userId, "百度AI");
            return "";

        } catch (Exception e) {
            logInfo.sendTaskLog("获取百度AI原链接失败", userId, "百度AI");
            throw e;
        }
    }

    /**
     * 从data-show-ext属性中提取ori_lid
     *
     * @param dataShowExt data-show-ext属性值
     * @return ori_lid值
     */
    private String extractOriLidFromDataShowExt(String dataShowExt) throws Exception {
        try {

            // data-show-ext可能是JSON格式，尝试解析
            if (dataShowExt.contains("ori_lid")) {

                // 使用正则表达式提取ori_lid的值
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"ori_lid\"\\s*:\\s*\"([^\"]+)\"");
                java.util.regex.Matcher matcher = pattern.matcher(dataShowExt);

                if (matcher.find()) {
                    String oriLid = matcher.group(1);
                    return oriLid;
                } else {

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
                            return oriLid;
                        }
                    }
                }
            } else {
            }
            return null;
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * 从百度AI原链接URL中提取ori_lid
     *
     * @param originalUrl 原链接URL
     * @return ori_lid值
     */
    private String extractOriLidFromUrl(String originalUrl) throws Exception {
        try {
            if (originalUrl == null || originalUrl.trim().isEmpty()) {
                return null;
            }


            // 从URL中提取ori_lid，格式：%22ori_lid%22%3A%22...%22
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("%22ori_lid%22%3A%22([^%\"]+)%22");
            java.util.regex.Matcher matcher = pattern.matcher(originalUrl);

            if (matcher.find()) {
                String oriLid = matcher.group(1);
                return oriLid;
            } else {
                return null;
            }
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * 获取百度对话AI分享链接
     *
     * @param page   Playwright页面对象
     * @param userId 用户ID
     * @return 分享链接
     */
    public String getBaiduShareUrl(Page page, String userId) throws Exception {
        AtomicReference<String> shareUrlRef = new AtomicReference<>();

        clipboardLockManager.runWithClipboardLock(() -> {
            try {

                Locator editor = page.locator("div#editor-container");
                Locator comate = page.locator("div#comate-chat-workspace");
                //检测是否打开了右侧文本编辑框
                if (editor.count() > 0) {
                    String[] shareSelectors = {
                            "i.share-button.cos-icon"
                    };

                    Locator shareButton = null;
                    for (String selector : shareSelectors) {
                        Locator temp = editor.locator(selector);
                        if (temp.count() > 0) {
                            shareButton = temp.last();
                            break;
                        }
                    }

                    if (shareButton != null) {
                        shareButton.click();
                    }
                    Thread.sleep(2000);
                    String[] copySelectors = {
                            "button:has-text('复制链接')",
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
                        Thread.sleep(2000);

                        // 读取剪贴板内容
                        shareUrlRef.set((String) page.evaluate("navigator.clipboard.readText()"));
                    }
                    Thread.sleep(2000);
                    return;
                } else if (comate.count() > 0) {
                    Locator downloadButton = page.locator(".cos-icon.cos-icon-download.button_1uqi9_1");
                    if (downloadButton.count() > 0) {
                        String url = screenshotUtil.downloadAndUploadFile(page, screenshotUtil.uploadUrl, () -> {
                            try {
                                Thread.sleep(2000);
                                downloadButton.last().click();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        });
                        shareUrlRef.set(url);
                        return;
                    }
                }


                Locator container = page.locator("div.chat-qa-container").last();
                Locator directShareButton = container.locator("//i[contains(@class, 'cos-icon') and contains(@class, 'cos-icon-share1')]");
//                测试用
//                Locator directShareButton = container.locator("//i[contains(@class, 'nosuchbutton') and contains(@class, 'abcdefg')]");
                if (directShareButton.count() > 0) {
                    directShareButton.last().click();
                }
                Thread.sleep(500);
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
                    shareUrl = shareUrl.substring(shareUrl.indexOf('h'));
                    shareUrlRef.set(shareUrl);

                    logInfo.sendTaskLog("分享链接获取成功", userId, "百度AI");
                    return;
                }
                // 如果没找到按钮，不输出"未找到"信息

                // 没有分享，先点击编辑，分享按钮才出现
//                Locator edit = container.locator("i.cos-icon.cos-icon-rewrite");
//                if(edit.count()>0){
//                    edit.click();
//                }
//                Thread.sleep(2000);
//                Locator editor = page.locator("div#editor-container");
//                if(editor.count()== 0){
//                    Locator editIcon = page.locator(".leftImg_s68m1_22").last();
//                    editIcon.click();
//                }
//                Thread.sleep(2000);

                // 如果没找到按钮，不输出"未找到"信息
                // 如果没找到分享按钮，不输出"未找到分享按钮"信息

            } catch (Exception e) {
                // 静默处理分享链接获取失败，不影响主流程
                UserLogUtil.sendExceptionLog(userId, "百度AI分享链接获取", "getBaiduShareUrl", e, url + "/saveLogInfo");
            }
        });

        return shareUrlRef.get();
    }

    /**
     * 格式化百度对话AI内容
     *
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
     *
     * @param page   Playwright页面对象
     * @param userId 用户ID
     * @return 二维码截图URL，失败返回null
     */
    public String waitAndGetQRCode(Page page, String userId) throws Exception {
        try {
            // 导航到百度AI登录页面
            page.navigate("https://chat.baidu.com/", new Page.NavigateOptions().setTimeout(60000)); // 🔥 增加导航超时
            page.waitForLoadState(LoadState.LOAD, new Page.WaitForLoadStateOptions().setTimeout(30000)); // 🔥 增加加载超时
            Thread.sleep(2000);

            // 检查是否已经登录
            String loginStatus = checkBaiduLogin(page, false);
            if (!"false".equals(loginStatus)) {
                // 如果已经登录，返回当前页面截图
                logInfo.sendTaskLog("百度AI已登录，用户: " + loginStatus, userId, "百度AI");
                return screenshotUtil.screenshotAndUpload(page, "getBaiduLoggedIn.png");
            }

            // 查找并点击登录按钮 - 支持多种登录按钮结构
            String[] loginButtonSelectors = {
                ".login-btn", // 新的登录按钮类名
                ".login-btn div", // 登录按钮内部div
                "div[data-click-log*='login_button']", // 通过data-click-log属性定位
                "div[data-show-ext*='login_button']", // 通过data-show-ext属性定位
                "//*[@id=\"app\"]/div/div[1]/div[2]/div/div", // 旧的选择器作为备用
                "button:has-text('登录')", // 通用登录按钮
                "div:has-text('登录')" // 通用登录div
            };

            Locator loginButton = null;
            String usedSelector = "";
            
            // 尝试多个选择器找到登录按钮
            for (String selector : loginButtonSelectors) {
                try {
                    Locator tempButton = page.locator(selector);
                    if (tempButton.count() > 0 && tempButton.isVisible()) {
                        String buttonText = tempButton.textContent();
                        if (buttonText != null && (buttonText.contains("登录") || buttonText.contains("登陆"))) {
                            loginButton = tempButton;
                            usedSelector = selector;
                            logInfo.sendTaskLog("找到登录按钮，选择器: " + selector + "，文本内容: " + buttonText, userId, "百度AI");
                            break;
                        }
                    }
                } catch (Exception e) {
                    // 继续尝试下一个选择器
                    continue;
                }
            }

            if (loginButton != null) {
                try {
                    // 点击登录按钮
                    loginButton.click();
                    logInfo.sendTaskLog("已点击登录按钮，等待登录页面加载", userId, "百度AI");
                    Thread.sleep(3000); // 等待登录页面加载

                    // 等待QR码出现或登录界面稳定
                    page.waitForLoadState(LoadState.NETWORKIDLE);
                    Thread.sleep(2000);

                    // 截图并返回二维码
                    logInfo.sendTaskLog("准备截图二维码", userId, "百度AI");
                    
                    // 尝试多个二维码选择器
                    String[] qrCodeSelectors = {
                        "#TANGRAM__PSP_11__qrcodeContent", // 原始选择器
                        ".qr-code", // 通用二维码选择器
                        ".login-qr", // 登录二维码选择器
                        "[class*='qr']", // 包含qr的类名
                        "[id*='qr']", // 包含qr的id
                        ".passport-login-qrcode", // 百度登录二维码
                        "#qrcode" // 简单的二维码id
                    };
                    
                    Locator qrCodeArea = null;
                    for (String qrSelector : qrCodeSelectors) {
                        try {
                            Locator tempQr = page.locator(qrSelector);
                            if (tempQr.count() > 0 && tempQr.isVisible()) {
                                qrCodeArea = tempQr;
                                logInfo.sendTaskLog("找到二维码区域，选择器: " + qrSelector, userId, "百度AI");
                                break;
                            }
                        } catch (Exception e) {
                            continue;
                        }
                    }
                    
                    if (qrCodeArea != null) {
                        byte[] qrCodeBytes = qrCodeArea.screenshot(new Locator.ScreenshotOptions().setTimeout(45000));

                        BufferedImage inputImage = ImageIO.read(new ByteArrayInputStream(qrCodeBytes));

                        int newWidth = inputImage.getWidth() * 2;
                        int newHeight = inputImage.getHeight() * 2;

                        // 创建一个新的BufferedImage对象，用于存储放大后的图片
                        BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D g2d = resizedImage.createGraphics();

                        // 绘制放大后的图片
                        g2d.drawImage(inputImage, 0, 0, newWidth, newHeight, null);
                        g2d.dispose();

                        // 保存放大后的图片
                        ImageIO.write(resizedImage, "png", new File("getBaiduQrCode.png"));
                        String response = ScreenshotUtil.uploadFile(screenshotUtil.uploadUrl, "getBaiduQrCode.png");
                        JSONObject jsonObject = JSONObject.parseObject(response);
                        String url = jsonObject.get("url") + "";
                        Files.delete(Paths.get("getBaiduQrCode.png"));
                        return url;
                    } else {
                        logInfo.sendTaskLog("未找到二维码区域，返回整页截图", userId, "百度AI");
                        return screenshotUtil.screenshotAndUpload(page, "getBaiduLoginPageAfterClick.png");
                    }
                } catch (Exception clickException) {
                    logInfo.sendTaskLog("点击登录按钮失败: " + clickException.getMessage(), userId, "百度AI");
                    // 如果点击失败，返回当前页面截图
                    return screenshotUtil.screenshotAndUpload(page, "getBaiduLoginClickError.png");
                }
            } else {
                logInfo.sendTaskLog("未找到任何登录按钮", userId, "百度AI");
            }

            // 如果没有找到登录按钮，直接截图当前页面
            logInfo.sendTaskLog("截图当前页面", userId, "百度AI");
            return screenshotUtil.screenshotAndUpload(page, "getBaiduLoginPage.png");

        } catch (Exception e) {
            logInfo.sendTaskLog("获取二维码异常", userId, "百度AI");
            try {
                // 出现异常时也返回当前页面截图
                return screenshotUtil.screenshotAndUpload(page, "getBaiduLoginError.png");
            } catch (Exception screenshotException) {
                logInfo.sendTaskLog("截图异常", userId, "百度AI");
                throw e;
            }
        }
    }

    /**
     * 解析百度对话AI角色配置
     *
     * @param roles 角色配置字符串
     * @return 解析结果描述
     */
    public String parseBaiduRoles(String roles) throws Exception {
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
     *
     * @param page            Playwright页面对象
     * @param userInfoRequest 用户请求信息
     * @param roles           角色配置 (支持: baidu-sdss深度搜索模式)
     * @param userId          用户ID
     * @param content         内容
     * @return 格式化后的内容
     */
    public McpResult saveBaiduContent(Page page, UserInfoRequest userInfoRequest, String roles,
                                      String userId, String content) throws Exception {
        try {
            // 获取会话ID
            String sessionId = extractSessionId(page);
            // 获取分享链接
            String shareUrl = getBaiduShareUrl(page, userId);
            String shareImgUrl = "";

            Locator editor = page.locator("div#editor-container");
            Locator comate = page.locator("div#comate-chat-workspace");
            //检测是否打开了右侧文本编辑框
            if (editor.count() > 0) {
                Locator exitButton = page.locator("i.cos-icon.cos-icon-close.button_AxaRd");
                if (exitButton.count() > 0) {
                    exitButton.last().click();
                }
            } else if (comate.count() > 0) {
                Locator exitButton = page.locator("i.cos-icon.cos-icon-close.button_f81z6_14");
                if (exitButton.count() > 0) {
                    exitButton.last().click();
                }
            } else if (shareUrl != null && !shareUrl.isEmpty()) {
                String[] shareSelectors = {
                        "button:has-text('分享图片')",
                };

                Locator shareButton = null;
                for (String selector : shareSelectors) {
                    Locator temp = page.locator(selector);

                    if (temp.count() > 0) {
                        shareButton = temp.first();
                        break;
                    }
                }
                shareButton.click();
                Thread.sleep(12000);

                String[] copySelectors = {
                        "button:has-text('下载图片')",
                };

                Locator copyButton = null;
                for (String selector : copySelectors) {
                    Locator temp = page.locator(selector);

                    if (temp.count() > 0) {
                        copyButton = temp.first();
                        break;
                    }
                }
                if(copyButton != null){
                    final Locator temp = copyButton;
                    shareImgUrl = ScreenshotUtil.downloadAndUploadFile(page, screenshotUtil.uploadUrl, () -> {
                        temp.click();
                    });
                }else{  // 取消分享界面，回退到默认界面样式，以便后续截图
                    Locator exit = page.locator("i.cos-icon.cos-icon-close.close-btn_1iln9_41");
                    if(exit.count()>0){
                        exit.click();
                    }
                }
            }
            // 获取原链接并提取ori_lid
            String originalUrl = getBaiduOriginalUrl(page, userId);
            String oriLid = extractOriLidFromUrl(originalUrl);
            // 如果没有直接图片分享，获取截图链接
            if (shareImgUrl == null || shareImgUrl.trim().isEmpty()) {
                Locator element = page.locator("div#conversation-flow-container").last();
                Locator answer = page.locator("//*[@id=\"1\"]/div/div").last();

                double scrollHeight = ((Number) page.evaluate("(ele) => ele.scrollHeight", element.elementHandle())).doubleValue();
                double scrollTop = ((Number) page.evaluate("(ele) => ele.scrollTop", element.elementHandle())).doubleValue();
                double clientHeight = ((Number) page.evaluate("(ele) => ele.clientHeight", element.elementHandle())).doubleValue();
                // 先悬停在滑动文本框上以便后续滚动
                answer.hover();
                // 先滚动到页面顶部以便定位
                while (scrollTop > 5) {
                    page.mouse().wheel(0, -clientHeight);
                    Thread.sleep(100);
                    scrollTop = ((Number) page.evaluate("(ele) => ele.scrollTop", element.elementHandle())).doubleValue();
                }
                //隐藏跳到底部元素
                if (page.locator("#cs-bottom > div.false.false.cs-scroll-to-bottom-btn").count() > 0) {
                    try {
                        page.evaluate("""
                                    () => {
                                        const element = document.querySelector('#cs-bottom > div.false.false.cs-scroll-to-bottom-btn');
                                        element.style.display = 'none';
                                        element.style.visibility = 'hidden';
                                    }
                                """);
                    } catch (Exception e) {
                        System.err.println("隐藏跳到底部元素失败: " + e.getMessage());
                    }
                }


                // 跳过之前的问答
                Locator containers = page.locator("div.chat-qa-container");
                for (int i = 0; i < containers.count() - 1; ++i) {
                    double containerHeight = ((Number) page.evaluate("(ele) => ele.clientHeight", containers.nth(i).elementHandle())).doubleValue();
                    page.mouse().wheel(0, containerHeight);
                }
                Thread.sleep(2000);
                // 对最新一次回复截多张图
                ArrayList<byte[]> images = new ArrayList<>();
//                double lastScrollTop = scrollTop;
//                while (clientHeight + scrollTop + 300 < scrollHeight) {
//                    Thread.sleep(500);
//                    lastScrollTop = scrollTop;
//                    page.mouse().wheel(0, clientHeight);
//                    scrollTop = ((Number) page.evaluate("(ele) => ele.scrollTop", element.elementHandle())).doubleValue();
//                    images.add(element.screenshot(new Locator.ScreenshotOptions()));
//                }
                int lastHeight = 0;
                double lastScrollTop = -250;
                scrollTop = ((Number) page.evaluate("(ele) => ele.scrollTop", element.elementHandle())).doubleValue();
                while (scrollTop - lastScrollTop > 200) {
                    images.add(element.screenshot(new Locator.ScreenshotOptions()));
                    lastHeight=(int)scrollTop - (int)lastScrollTop;
                    Thread.sleep(500);
                    lastScrollTop = scrollTop;
                    page.mouse().wheel(0, clientHeight);
                    Thread.sleep(1500);
                    scrollTop = ((Number) page.evaluate("(ele) => ele.scrollTop", element.elementHandle())).doubleValue();
                }
                byte[] concatenatedImageBytes = null;
                if(images.size() == 1){
                    concatenatedImageBytes = images.get(0);
                }else {
                    // 拼接多张截图
                    BufferedImage firstImage = ImageIO.read(new ByteArrayInputStream(images.get(0)));
                    int width = firstImage.getWidth();
                    int totalHeight = 0;

                    // 计算总高度
                    for (int i = 0; i < images.size() - 1; ++i) {
                        BufferedImage img = ImageIO.read(new ByteArrayInputStream(images.get(i)));
                        totalHeight += img.getHeight();
                    }
                    totalHeight += lastHeight;

                    // 创建一个新的 BufferedImage，用于拼接
                    BufferedImage result = new BufferedImage(width, totalHeight, BufferedImage.TYPE_INT_ARGB);
                    int currentHeight = 0;

                    // 按顺序拼接图片
                    for (int i = 0; i < images.size() - 1; ++i) {
                        BufferedImage img = ImageIO.read(new ByteArrayInputStream(images.get(i)));
                        result.getGraphics().drawImage(img, 0, currentHeight, null);
                        currentHeight += img.getHeight();
                    }
                    // 最后一张图特殊处理，需要裁剪
                        BufferedImage img = ImageIO.read(new ByteArrayInputStream(images.get(images.size() - 1)));
                        img = img.getSubimage(0, (int)Math.max(0,clientHeight-lastHeight), img.getWidth(), (int)Math.min(lastHeight,img.getHeight()));
                        result.getGraphics().drawImage(img, 0, currentHeight, null);
                        currentHeight += img.getHeight();
                    // 将结果图片转换为 byte[] 数组
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(result, "png", baos);
                    concatenatedImageBytes = baos.toByteArray();
                }

                String filepath = userId + "百度AI合成截图.png";

                FileOutputStream fos = new FileOutputStream(filepath);
                fos.write(concatenatedImageBytes);
                fos.close();

                String response = ScreenshotUtil.uploadFile(screenshotUtil.uploadUrl, filepath);
                JSONObject jsonObject = JSONObject.parseObject(response);

                shareImgUrl = jsonObject.get("url") + "";
                if (shareUrl == null || shareUrl.trim().isEmpty()) {
                    shareUrl = shareImgUrl;
                }
                // 不输出具体URL，只记录获取方式
                logInfo.sendTaskLog("已获取图片链接", userId, "百度AI");
            }

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
            userInfoRequest.setShareImgUrl(shareImgUrl);
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
//            String formattedContent = formatBaiduContent(content);
            String formattedContent = content;

            // 使用原链接作为分享链接，如果获取不到原链接则使用传统分享链接
            String finalShareUrl = (shareUrl != null && !shareUrl.trim().isEmpty()) ? shareUrl : originalUrl;
            logInfo.sendResData(formattedContent, userId, "百度AI", "RETURN_BAIDU_RES", finalShareUrl, shareImgUrl);

            if (oriLid != null && !oriLid.trim().isEmpty()) {
                logInfo.sendTaskLog("百度AI会话ID已保存: " + oriLid, userId, "百度AI");
            }
            logInfo.sendTaskLog("百度对话AI内容已保存到稿库", userId, "百度AI");

            return McpResult.success(formattedContent, finalShareUrl);

        } catch (Exception e) {
            logInfo.sendTaskLog("保存百度对话AI内容失败", userId, "百度AI");
            throw e;
        }
    }

    /**
     * 从页面URL提取会话ID
     *
     * @param page Playwright页面对象
     * @return 会话ID
     */
    private String extractSessionId(Page page) throws Exception {
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
            throw e;
        }
    }

    /**
     * 等待百度对话AI HTML DOM内容完成
     *
     * @param page   Playwright页面对象
     * @param userId 用户ID
     * @param aiName AI名称
     * @return 提取的HTML内容
     */
    public String waitBaiduHtmlDom(Page page, String userId, String aiName) throws InterruptedException {
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
            logInfo.sendTaskLog(aiName + "内容获取失败", userId, aiName);
            throw e;
        }
    }
}