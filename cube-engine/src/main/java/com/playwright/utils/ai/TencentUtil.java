package com.playwright.utils.ai;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.LoadState;
import com.playwright.entity.UnPersisBrowserContextInfo;
import com.playwright.entity.UserInfoRequest;
import com.playwright.entity.mcp.McpResult;
import com.playwright.utils.common.*;
import com.playwright.websocket.WebSocketClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 腾讯智能体工具类
 * 提供与腾讯智能体（如元宝AI）交互的自动化操作功能，包括：
 * - 智能体页面操作
 * - 模型切换控制
 * - 回答内容抓取
 * - 日志记录与监控
 *
 * @author 优立方
 * @version JDK 17
 * @date 2025年05月27日 10:19
 */
@Component
public class TencentUtil {

    @Autowired
    private LogMsgUtil logInfo;

    @Value("${cube.url}")
    private String url;

    @Value("${cube.uploadurl}")
    private String uploadUrl;

    @Autowired
    private ClipboardLockManager clipboardLockManager;
    @Autowired
    private WebSocketClientService webSocketClientService;

    /**
     * 检查元宝登录状态
     *
     * @param userId 用户ID
     * @return 登录状态信息
     * @throws InterruptedException 中断异常
     */
    public String checkLogin(String userId) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        try {
            UnPersisBrowserContextInfo browserContextInfo = BrowserContextFactory.getBrowserContext(userId, 2);
            BrowserContext browserContext = browserContextInfo.getBrowserContext();
            Page page = browserContext.pages().get(0);
            page.navigate("https://yuanbao.tencent.com/chat/naQivTmsDa/");
            page.waitForLoadState(LoadState.LOAD);
            Thread.sleep(3000);
            Locator phone = page.locator("//p[@class='nick-info-name']");
            if (phone.count() > 0) {
                String phoneText = phone.textContent();
                if (phoneText.equals("未登录")) {
                    // 记录登录状态异常
                    UserLogUtil.sendLoginStatusLog(userId, "腾讯元宝", "用户未登录", url + "/saveLogInfo");
                    return "false";
                }
                // 记录登录检查成功
                UserLogUtil.sendAISuccessLog(userId, "腾讯元宝", "登录检查", "登录状态正常：" + phoneText, startTime, url + "/saveLogInfo");
                return phoneText;
            } else {
                // 记录元素不可见异常
                UserLogUtil.sendElementNotVisibleLog(userId, "腾讯元宝", "//p[@class='nick-info-name']", page.url(), url + "/saveLogInfo");
                return "false";
            }
        } catch (TimeoutError e) {
            // 记录超时异常
            UserLogUtil.sendAITimeoutLog(userId, "腾讯元宝", "登录检查", e, "页面加载或元素定位", url + "/saveLogInfo");
            throw e;
        } catch (Exception e) {
            // 记录其他异常
            UserLogUtil.sendAIExceptionLog(userId, "腾讯元宝", "checkLogin", e, startTime, "登录状态检查失败", url + "/saveLogInfo");
            throw e;
        }
    }

    public Page getPage(String userId) {
        long startTime = System.currentTimeMillis();
        try {
            UnPersisBrowserContextInfo browserContextInfo = BrowserContextFactory.getBrowserContext(userId, 1);

            // 检查浏览器上下文是否创建成功
            if (browserContextInfo == null || browserContextInfo.getBrowserContext() == null) {
                String errorMsg = "浏览器上下文创建失败，无法执行腾讯元宝任务";
                logInfo.sendTaskLog(errorMsg, userId, "腾讯元宝");

                // 使用增强日志记录
                UserLogUtil.sendAIBusinessLog(userId, "腾讯元宝", "浏览器初始化", errorMsg, startTime, url + "/saveLogInfo");

                // 发送错误响应
                try {
                    logInfo.sendResData(errorMsg, userId, "腾讯元宝", "RETURN_YB_RES", "", "");
                } catch (Exception e) {
                    UserLogUtil.sendAIExceptionLog(userId, "腾讯元宝", "getPage", e, startTime, "发送错误响应失败", url + "/saveLogInfo");
                }
                return null;
            }

            BrowserContext context = browserContextInfo.getBrowserContext();
            List<Page> pages = context.pages();

            // 检查页面是否可用，只需要一个页面即可
            if (pages == null || pages.isEmpty()) {
                String errorMsg = "浏览器页面不可用，当前页面数: " + (pages != null ? pages.size() : 0);
                logInfo.sendTaskLog(errorMsg, userId, "腾讯元宝");

                // 使用增强日志记录
                UserLogUtil.sendAIBusinessLog(userId, "腾讯元宝", "页面检查", errorMsg, startTime, url + "/saveLogInfo");

                // 发送错误响应
                try {
                    logInfo.sendResData(errorMsg, userId, "腾讯元宝", "RETURN_YB_RES", "", "");
                } catch (Exception e) {
                    UserLogUtil.sendAIExceptionLog(userId, "腾讯元宝", "getPage", e, startTime, "发送错误响应失败", url + "/saveLogInfo");
                }
                return null;
            }

            // 使用第一个页面，统一处理混元和DeepSeek
            Page targetPage = pages.get(0);

            if (targetPage != null) {
                // 记录页面获取成功
                UserLogUtil.sendAISuccessLog(userId, "腾讯元宝", "页面获取", "成功获取腾讯元宝页面", startTime, url + "/saveLogInfo");
            }

            return targetPage;

        } catch (Exception e) {
            UserLogUtil.sendAIExceptionLog(userId, "腾讯元宝", "getPage", e, startTime, "获取页面失败", url + "/saveLogInfo");
            return null;
        }
    }

    /**
     * 处理智能体AI交互流程
     *
     * @param page       Playwright页面实例
     * @param userPrompt 用户输入的指令
     * @param agentUrl   智能体URL
     * @param aiName     AI名称
     * @param userId     用户ID
     * @param isNewChat  是否新会话
     * @return 复制按钮数量（用于后续监控）
     */
    public int handelAgentAI(Page page, String userPrompt, String agentUrl, String aiName, String userId, String isNewChat) throws InterruptedException, IOException {
        page.navigate(agentUrl);
        logInfo.sendImgData(page, userId + "打开智能体页面", userId);
        logInfo.sendTaskLog(aiName + "页面打开完成", userId, aiName);

        String currentUrl = page.url();
        Pattern pattern = Pattern.compile("/chat/([^/]+)/([^/]+)");
        Matcher matcher = pattern.matcher(currentUrl);
        // 新会话初始化操作
//        if (!matcher.find() && isNewChat.equals("true")) {
//            page.locator("//*[@id=\"app\"]/div/div[2]/div/div/div[1]/div/div[4]").click();
//            Thread.sleep(500);
//            page.locator("//*[@id=\"hunyuan-bot\"]/div[7]/div/div[2]/div/div/div[2]/div/div[2]/div[1]/span[2]").click();
//            Thread.sleep(500);
//            page.locator("//*[@id=\"hunyuan-bot\"]/div[8]/div/div[2]/div/div/div[3]/button[2]").click();
//        }
        Thread.sleep(500);
        // 用户指令输入与发送
        page.locator(".ql-editor > p").click();
        Thread.sleep(500);
        page.locator(".ql-editor").fill(userPrompt);
        logInfo.sendTaskLog("用户指令已自动输入完成", userId, aiName);
        Thread.sleep(500);
        page.locator(".ql-editor").press("Enter");
        logInfo.sendTaskLog("指令已自动发送成功", userId, aiName);
        // 获取当前复制按钮数量（用于监控回答状态）
        int copyButtonCount = page.querySelectorAll("div.agent-chat__toolbar__item.agent-chat__toolbar__copy").size();
        Thread.sleep(2000);
        return copyButtonCount;
    }


    /**
     * 保存智能体草稿数据（带执行过程监控）
     *
     * @param page            Playwright页面实例
     * @param userInfoRequest 用户信息请求对象
     * @param aiName          AI名称
     * @param userId          用户ID
     * @param initialCount    初始复制按钮数量
     * @param agentName       智能体名称
     * @param resName         结果名称
     * @return 抓取到的文本内容
     */
    public String saveAgentDraftData(Page page, UserInfoRequest userInfoRequest, String aiName, String userId, int initialCount, String agentName, String resName) {
        // 定时截图监控配置（每10秒截图一次）
        AtomicInteger i = new AtomicInteger(0);
        ScheduledExecutorService screenshotExecutor = Executors.newSingleThreadScheduledExecutor();
        ScheduledFuture<?> screenshotFuture = screenshotExecutor.scheduleAtFixedRate(() -> {
            try {
                int currentCount = i.getAndIncrement(); // 获取当前值并自增
                logInfo.sendImgData(page, userId + agentName + "工作流执行过程截图" + currentCount, userId);
            } catch (Exception e) {
                UserLogUtil.sendExceptionLog(userId, agentName + "截图异常", "saveAgentDraftData", e, url + "/saveLogInfo");
            }
        }, 0, 10, TimeUnit.SECONDS);
        try {
            logInfo.sendTaskLog("开启自动监听任务，持续监听" + agentName + "回答中", userId, agentName);
            //等待复制按钮出现并点击
//            String copiedText = waitAndClickYBCopyButton(page,userId,aiName,initialCount,agentName);
            //等待html片段获取完成
            String copiedText = waitHtmlDom(page, agentName, userId, userInfoRequest);

            AtomicReference<String> shareUrlRef = new AtomicReference<>();

            clipboardLockManager.runWithClipboardLock(() -> {
                try {
                    page.locator("span.icon-yb-ic_share_2504").last().click();
                    Thread.sleep(2000);
                    page.locator("div.agent-chat__share-bar__item__logo").first().click();
                    // 建议适当延迟等待内容更新
                    Thread.sleep(2000);
                    String shareUrl = (String) page.evaluate("navigator.clipboard.readText()");
                    Pattern pattern = Pattern.compile("https?://\\S+");
                    Matcher matcher = pattern.matcher(shareUrl);
                    String url = null;
                    if (matcher.find()) {
                        url = matcher.group();
                    }
                    shareUrlRef.set(url);
                } catch (Exception e) {
                    UserLogUtil.sendExceptionLog(userId, agentName + "复制异常", "saveAgentDraftData", e, url + "/saveLogInfo");
                }
            });
            Thread.sleep(1000);
            String shareUrl = shareUrlRef.get();

            page.locator("div.agent-chat__share-bar__item__logo").nth(1).click();

            String sharImgUrl = ScreenshotUtil.downloadAndUploadFile(page, uploadUrl, () -> {
                page.locator("div.hyc-photo-view__control__btn-download").click();
            });

            // 日志记录与数据保存
            logInfo.sendTaskLog("执行完成", userId, agentName);
            logInfo.sendResData(copiedText, userId, agentName, resName, shareUrl, sharImgUrl);

            Thread.sleep(3000);
            userInfoRequest.setDraftContent(copiedText);
            userInfoRequest.setAiName("Agent-" + aiName);
            userInfoRequest.setShareUrl(shareUrl);
            userInfoRequest.setShareImgUrl(sharImgUrl);
            RestUtils.post(url + "/saveDraftContent", userInfoRequest);
            return copiedText;
        } catch (TimeoutError e) {
            // 记录超时异常
            UserLogUtil.sendAITimeoutLog(userId, agentName, "智能体任务执行", e, "等待回答生成或分享操作", url + "/saveLogInfo");
            logInfo.sendTaskLog("执行超时：" + e.getMessage(), userId, agentName);
        } catch (Exception e) {
            // 记录智能体业务执行异常
            UserLogUtil.sendAIBusinessLog(userId, agentName, "智能体任务执行", e.getMessage(), System.currentTimeMillis(), url + "/saveLogInfo");
            logInfo.sendTaskLog("执行异常：" + e.getMessage(), userId, agentName);
        } finally {
            // 无论成功还是异常，都取消定时截图任务
            screenshotFuture.cancel(false);
            screenshotExecutor.shutdown();
        }
        return "未获取到内容";
    }


    /**
     * 处理元宝AI交互流程
     *
     * @param page       Playwright页面实例
     * @param userPrompt 用户指令
     * @param role       角色/模式标识
     * @param userId     用户ID
     * @param aiName     AI名称
     * @param chatId     会话ID
     * @return 初始复制按钮数量
     */
    public synchronized int
    handleYBAI(Page page, String userPrompt, String role, String userId, String aiName, String chatId) throws Exception {

        // 页面导航与元素定位
        page.navigate("https://yuanbao.tencent.com/chat/naQivTmsDa/" + chatId);
        String modelDom = "[dt-button-id=\"model_switch\"]";
        String hunyuanDom = "//div[normalize-space()='Hunyuan']";
        String deepseekDom = "//div[normalize-space()='DeepSeek']";
        Thread.sleep(3000);
        Locator modelName = page.locator(modelDom);

        modelName.waitFor(new Locator.WaitForOptions().setTimeout(10000));

        Locator locator = page.getByText("我知道了").first();
        if (locator.count() > 0 && locator.isVisible()) {
            page.getByText("我知道了").first().click();
        }
        Locator locatorTwo = page.getByText("我知道了").nth(1);
        if (locatorTwo.count() > 0 && locatorTwo.isVisible()) {
            page.getByText("我知道了").nth(1).click();
        }
        Locator locatorThree = page.getByText("我知道了").nth(2);
        if (locatorThree.count() > 0 && locatorThree.isVisible()) {
            page.getByText("我知道了").nth(2).click();
        }


        // 功能开关定位
        String deepThingDom = "[dt-button-id=\"deep_think\"]";
        Locator deepThing = page.locator(deepThingDom);

        String webSearchDom = "[dt-button-id=\"online_search\"]";
        Locator webSearch = page.locator(webSearchDom);

        logInfo.sendImgData(page, userId + "打开页面", userId);
        logInfo.sendTaskLog(aiName + "页面打开完成", userId, aiName);

        // 根据角色配置不同模式
        int copyButtonCount = page.querySelectorAll("div.agent-chat__toolbar__item.agent-chat__toolbar__copy").size();
        if (role.contains("yb-hunyuan")) {
            //切换模型
            clickModelChange(page, modelName, modelDom, hunyuanDom, "hunyuan");
            logInfo.sendImgData(page, userId + "切换混元模型", userId);
            logInfo.sendTaskLog("自动切换混元模型完成", userId, aiName);
        }
        if (role.contains("yb-deepseek")) {
            //切换模型
            clickModelChange(page, modelName, modelDom, deepseekDom, "deep_seek");
            logInfo.sendImgData(page, userId + "切换DS模型", userId);
            logInfo.sendTaskLog("自动切换DS模型完成", userId, aiName);
        }

        // 混元模型各种配置 - 使用else if避免重复匹配
        if (role.equals("yb-hunyuan-lwss-2")) {
            // 最具体的条件放在最前面：混元深度思考+联网搜索
            clickDeepThinkSmart(page, deepThing, deepThingDom, "hunyuan_t1", userId);
            clickWebSearchSmart(page, webSearch, webSearchDom, "1", userId);
            logInfo.sendImgData(page, userId + "混元深思联网选择", userId);
            logInfo.sendTaskLog("混元深度思考+联网搜索模式已启动", userId, aiName);
        } else if (role.equals("yb-hunyuan-lwss") || role.equals("yb-hunyuan-lwss-1")) {
            // 混元联网搜索（普通）
            clickDeepThinkSmart(page, deepThing, deepThingDom, "hunyuan_gpt_175B_0404", userId);
            clickWebSearchSmart(page, webSearch, webSearchDom, "1", userId);
            logInfo.sendImgData(page, userId + "混元联网选择", userId);
            logInfo.sendTaskLog("混元联网搜索模式已启动", userId, aiName);
        } else if (role.equals("yb-hunyuan-sdsk") || role.contains("yb-hunyuan-sdsk")) {
            // 混元深度思考
            clickDeepThinkSmart(page, deepThing, deepThingDom, "hunyuan_t1", userId);
            clickWebSearchSmart(page, webSearch, webSearchDom, "2", userId);
            logInfo.sendImgData(page, userId + "混元深思选择", userId);
            logInfo.sendTaskLog("混元深度思考模式已启动", userId, aiName);
        } else if (role.equals("yb-hunyuan-pt") || role.contains("yb-hunyuan-pt")) {
            // 混元普通模式
            clickDeepThinkSmart(page, deepThing, deepThingDom, "hunyuan_gpt_175B_0404", userId);
            clickWebSearchSmart(page, webSearch, webSearchDom, "2", userId);
            logInfo.sendImgData(page, userId + "混元普通选择", userId);
            logInfo.sendTaskLog("混元普通模式", userId, aiName);
        }

        // DeepSeek模型各种配置 - 使用else if避免重复匹配
        if (role.equals("yb-deepseek-lwss-2")) {
            // 最具体的条件放在最前面：DeepSeek深度思考+联网搜索
            clickDeepThinkSmart(page, deepThing, deepThingDom, "deep_seek", userId);
            clickWebSearchSmart(page, webSearch, webSearchDom, "1", userId);
            logInfo.sendImgData(page, userId + "元宝DS深思联网选择", userId);
            logInfo.sendTaskLog("DeepSeek深度思考+联网搜索模式已启动", userId, aiName);
        } else if (role.equals("yb-deepseek-lwss") || role.equals("yb-deepseek-lwss-1")) {
            // DeepSeek联网搜索（普通）
            clickDeepThinkSmart(page, deepThing, deepThingDom, "deep_seek_v3", userId);
            clickWebSearchSmart(page, webSearch, webSearchDom, "1", userId);
            logInfo.sendImgData(page, userId + "元宝DS联网选择", userId);
            logInfo.sendTaskLog("DeepSeek联网搜索模式已启动", userId, aiName);
        } else if (role.equals("yb-deepseek-sdsk") || role.contains("yb-deepseek-sdsk")) {
            // DeepSeek深度思考
            clickDeepThinkSmart(page, deepThing, deepThingDom, "deep_seek", userId);
            clickWebSearchSmart(page, webSearch, webSearchDom, "2", userId);
            logInfo.sendImgData(page, userId + "元宝DS深思选择", userId);
            logInfo.sendTaskLog("DeepSeek深度思考模式已启动", userId, aiName);
        } else if (role.equals("yb-deepseek-pt") || role.contains("yb-deepseek-pt")) {
            // DeepSeek普通模式
            clickDeepThinkSmart(page, deepThing, deepThingDom, "deep_seek_v3", userId);
            clickWebSearchSmart(page, webSearch, webSearchDom, "2", userId);
            logInfo.sendImgData(page, userId + "元宝DS普通选择", userId);
            logInfo.sendTaskLog("DeepSeek普通模式", userId, aiName);
        }


        Thread.sleep(500);
        page.locator(".ql-editor > p").click();
        Thread.sleep(500);
        page.locator(".ql-editor").fill(userPrompt);
        logInfo.sendTaskLog("用户指令已自动输入完成", userId, aiName);
        Thread.sleep(500);
        page.locator(".ql-editor").press("Enter");
        logInfo.sendTaskLog("指令已自动发送成功", userId, aiName);
        Thread.sleep(1000);
        return copyButtonCount;
    }

    public synchronized McpResult saveDraftData(Page page, UserInfoRequest userInfoRequest, String aiName, String userId) throws InterruptedException, IOException {

        // 创建定时截图线程
        AtomicInteger i = new AtomicInteger(0);
        ScheduledExecutorService screenshotExecutor = Executors.newSingleThreadScheduledExecutor();
        // 启动定时任务，每5秒执行一次截图
        ScheduledFuture<?> screenshotFuture = null;
        if (!aiName.contains("znpb")) {
            screenshotFuture = screenshotExecutor.scheduleAtFixedRate(() -> {
                try {
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    int currentCount = i.getAndIncrement(); // 获取当前值并自增
                    logInfo.sendImgData(page, userId + "元宝执行过程截图" + currentCount, userId);
                } catch (Exception e) {
                    UserLogUtil.sendExceptionLog(userId, "元宝截图", "saveDraftData", e, url + "/saveLogInfo");
                }
            }, 0, 7, TimeUnit.SECONDS);
        }
        try {
            String agentName = "";
            if (aiName.contains("znpb")) {
                agentName = "智能排版";
                logInfo.sendTaskLog("开启自动监听任务，持续监听智能排版中", userId, agentName);
            } else if (aiName.contains("hunyuan")) {
                agentName = "腾讯元宝T1";
                logInfo.sendTaskLog("开启自动监听任务，持续监听腾讯元宝T1回答中", userId, agentName);
            } else if (aiName.contains("deepseek")) {
                agentName = "腾讯元宝DS";
                logInfo.sendTaskLog("开启自动监听任务，持续监听腾讯元宝DS回答中", userId, agentName);
            }

            //等待复制按钮出现并点击
//            String copiedText = waitAndClickYBCopyButton(page,userId,aiName,initialCount,agentName);
            //等待html片段获取
            String copiedText = waitHtmlDom(page, agentName, userId, userInfoRequest);

            //关闭截图
            if (screenshotFuture != null) {
                screenshotFuture.cancel(false);
                screenshotExecutor.shutdown();
            }
            AtomicReference<String> shareUrlRef = new AtomicReference<>();

            // 🔥 修复Lambda表达式中变量必须是final的问题
            final String finalUserId = userId;
            final String finalAgentName = agentName;
            final String finalAiName = aiName;
            final String finalUrl = url;

            clipboardLockManager.runWithClipboardLock(() -> {
                try {
                    // 🔥 修复：确保分享按钮可见并点击
                    logInfo.sendTaskLog("正在点击分享按钮...", finalUserId, finalAgentName);

                    // 等待分享按钮出现并点击
                    page.waitForSelector("span.icon-yb-ic_share_2504", new Page.WaitForSelectorOptions().setTimeout(10000));
                    Thread.sleep(2000);
                    page.locator("span.icon-yb-ic_share_2504").last().click();
                    Thread.sleep(2000);

                    // 确保分享选项出现
                    page.waitForSelector("div.agent-chat__share-bar__item__logo", new Page.WaitForSelectorOptions().setTimeout(5000));

                    // 点击复制链接（第一个选项）
                    page.locator("div.agent-chat__share-bar__item__logo").first().click();
                    logInfo.sendTaskLog("已点击复制链接按钮", finalUserId, finalAgentName);

                    // 等待剪贴板更新
                    Thread.sleep(3000);
                    String shareUrl = (String) page.evaluate("navigator.clipboard.readText()");
                    logInfo.sendTaskLog("获取到剪贴板内容: " + shareUrl, finalUserId, finalAgentName);

                    Pattern pattern = Pattern.compile("https://yuanbao\\.tencent\\.com/[^\s\"']+");
                    Matcher matcher = pattern.matcher(shareUrl);

                    String extractedUrl = null;
                    if (matcher.find()) {
                        extractedUrl = matcher.group();
                        logInfo.sendTaskLog("提取到分享链接: " + extractedUrl, finalUserId, finalAgentName);
                    } else {
                        logInfo.sendTaskLog("未能提取到有效的分享链接，原始内容: " + shareUrl, finalUserId, finalAgentName);
                    }
                    shareUrlRef.set(extractedUrl);
                } catch (TimeoutError e) {
                    // 记录分享操作超时
                    logInfo.sendTaskLog("分享按钮点击超时: " + e.getMessage(), finalUserId, finalAgentName);
                    UserLogUtil.sendAITimeoutLog(finalUserId, finalAiName, "分享链接获取", e, "点击分享按钮或复制链接", finalUrl + "/saveLogInfo");
                } catch (Exception e) {
                    // 记录分享操作异常
                    logInfo.sendTaskLog("分享操作异常: " + e.getMessage(), finalUserId, finalAgentName);
                    UserLogUtil.sendAIBusinessLog(finalUserId, finalAiName, "分享操作", e.getMessage(), System.currentTimeMillis(), finalUrl + "/saveLogInfo");
                }
            });

            Thread.sleep(1000);
            String shareUrl = shareUrlRef.get();
            String sharImgUrl = "";
            if (agentName.contains("腾讯元宝")) {
                try {
                    logInfo.sendTaskLog("正在生成分享图片...", userId, agentName);

                    // 点击生成图片按钮（第二个选项）
                    page.locator("div.agent-chat__share-bar__item__logo").nth(1).click();
                    Thread.sleep(2000);

                    // 等待图片生成并下载
                    page.waitForSelector("div.hyc-photo-view__control__btn-download", new Page.WaitForSelectorOptions().setTimeout(15000));

                    sharImgUrl = ScreenshotUtil.downloadAndUploadFile(page, uploadUrl, () -> {
                        try {
                            page.locator("div.hyc-photo-view__control__btn-download").click();
                            Thread.sleep(3000);
                            logInfo.sendTaskLog("图片下载完成", finalUserId, finalAgentName);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    });

                    if (sharImgUrl != null && !sharImgUrl.isEmpty()) {
                        logInfo.sendTaskLog("分享图片上传成功: " + sharImgUrl, userId, agentName);
                    } else {
                        logInfo.sendTaskLog("分享图片上传失败", userId, agentName);
                    }
                } catch (Exception e) {
                    logInfo.sendTaskLog("生成分享图片失败: " + e.getMessage(), userId, agentName);
                    // 即使截图失败，也继续执行后续逻辑
                }
            }

            try {
                Thread.sleep(3000);
                if (aiName.contains("znpb")) {
                    try {
                        logInfo.sendTaskLog("执行完成", userId, "智能排版");
                        logInfo.sendResData(copiedText, userId, "智能排版", "RETURN_ZNPB_RES", "", "");
                        // 等待所有线程执行完毕
                        userInfoRequest.setDraftContent(copiedText);
                        userInfoRequest.setAiName("智能排版");
                        userInfoRequest.setShareUrl(shareUrl);
                        userInfoRequest.setShareImgUrl(sharImgUrl);
                        RestUtils.post(url + "/saveDraftContent", userInfoRequest);
                        return McpResult.success(copiedText, shareUrl);
                    } catch (Exception e) {
                        return McpResult.fail(copiedText, shareUrl);
                    }
                } else if (aiName.contains("hunyuan")) {
                    // 🔥 修复腾讯元宝T1结果处理
                    logInfo.sendTaskLog("腾讯元宝T1执行完成，正在发送结果...", userId, "腾讯元宝T1");
                    logInfo.sendChatData(page, "/chat/([^/]+)/([^/]+)", userId, "RETURN_YBT1_CHATID", 2);

                    // 确保有内容才发送
                    if (copiedText != null && !copiedText.trim().isEmpty()) {
                        logInfo.sendResData(copiedText, userId, "腾讯元宝T1", "RETURN_YBT1_RES", shareUrl, sharImgUrl);
                        logInfo.sendTaskLog("腾讯元宝T1结果已发送到前端", userId, "腾讯元宝T1");
                    } else {
                        logInfo.sendTaskLog("腾讯元宝T1内容为空，跳过发送", userId, "腾讯元宝T1");
                    }
                } else if (aiName.contains("deepseek")) {
                    // 🔥 修复腾讯元宝DS结果处理
                    logInfo.sendTaskLog("腾讯元宝DS执行完成，正在发送结果...", userId, "腾讯元宝DS");
                    logInfo.sendChatData(page, "/chat/([^/]+)/([^/]+)", userId, "RETURN_YBDS_CHATID", 2);

                    // 确保有内容才发送，并修正AI名称
                    if (copiedText != null && !copiedText.trim().isEmpty()) {
                        logInfo.sendResData(copiedText, userId, "腾讯元宝DS", "RETURN_YBDS_RES", shareUrl, sharImgUrl);
                        logInfo.sendTaskLog("腾讯元宝DS结果已发送到前端", userId, "腾讯元宝DS");
                    } else {
                        logInfo.sendTaskLog("腾讯元宝DS内容为空，跳过发送", userId, "腾讯元宝DS");
                    }
                }
            } catch (InterruptedException e) {
                logInfo.sendTaskLog("线程被中断: " + e.getMessage(), userId, agentName);
            } catch (Exception e) {
                logInfo.sendTaskLog("结果处理异常: " + e.getMessage(), userId, agentName);
            }

            // 🔥 确保数据库保存逻辑正确执行
            try {
                userInfoRequest.setDraftContent(copiedText);
                userInfoRequest.setAiName("腾讯元宝-" + aiName);
                userInfoRequest.setShareUrl(shareUrl);
                userInfoRequest.setShareImgUrl(sharImgUrl);

                Object saveResult = RestUtils.post(url + "/saveDraftContent", userInfoRequest);
                logInfo.sendTaskLog("内容已保存到稿库: " + (saveResult != null ? "成功" : "失败"), userId, agentName);

                return McpResult.success(copiedText, shareUrl);
            } catch (Exception e) {
                logInfo.sendTaskLog("保存到稿库失败: " + e.getMessage(), userId, agentName);
                // 即使保存失败，也返回成功，让前端能显示内容
                return McpResult.success(copiedText, shareUrl);
            }
        } catch (Exception e) {
            UserLogUtil.sendExceptionLog(userId, aiName + "任务执行异常", "saveDraftData", e, url + "/saveLogInfo");
        }
        return McpResult.fail("保存失败", null);
    }


    public void clickModelChange(Page page, Locator modelName, String modelDom, String modeCheckDom, String aiName) throws
            InterruptedException {
        if (!modelName.getAttribute("dt-model-id").contains(aiName)) {

            Thread.sleep(1000);
            if (page.locator(modeCheckDom).count() > 0) {
                page.locator(modeCheckDom).click();
            } else {
                page.locator(modelDom).click();
                Thread.sleep(1000);
                page.locator(modeCheckDom).click();
            }
        }
    }

    public void clickDeepThing(Page page, Locator deepThing, String deepThingDom, String aiName) throws
            InterruptedException {
        deepThing.waitFor(new Locator.WaitForOptions().setTimeout(5000));
        if (!deepThing.getAttribute("dt-model-id").equals(aiName)) {
            Thread.sleep(1000);
            page.locator(deepThingDom).click();
        }
    }

    /**
     * 智能深度思考点击 - 根据目标模型精确控制深度思考状态
     * 支持检测 checked checked_ds 状态
     */
    public void clickDeepThinkSmart(Page page, Locator deepThing, String deepThingDom, String targetModelId, String userId) throws
            InterruptedException {
        long startTime = System.currentTimeMillis();
        try {
            deepThing.waitFor(new Locator.WaitForOptions().setTimeout(5000));
            
            // 获取当前状态
            String currentClass = deepThing.getAttribute("class");
            String currentModelId = deepThing.getAttribute("dt-model-id");
            
            boolean isCurrentlyChecked = currentClass != null && currentClass.contains("checked");
            
            // 核心逻辑：判断是否需要切换
            boolean shouldClick = false;
            
            // 情况1：当前没有开启深度思考，目标模型需要深度思考
            if (!isCurrentlyChecked && (targetModelId.equals("deep_seek") || targetModelId.equals("hunyuan_t1"))) {
                shouldClick = true;
            }
            // 情况2：当前已开启深度思考，但模型ID不匹配，需要切换到目标模型
            else if (isCurrentlyChecked && currentModelId != null && !currentModelId.equals(targetModelId)) {
                shouldClick = true;
            }
            // 情况3：当前已开启深度思考，目标是非深度思考模型，需要切换
            else if (isCurrentlyChecked && !targetModelId.equals("deep_seek") && !targetModelId.equals("hunyuan_t1") && !targetModelId.equals(currentModelId)) {
                shouldClick = true;
            }
            
            if (shouldClick) {
                Thread.sleep(1000);
                page.locator(deepThingDom).click();
                Thread.sleep(1000);
                
                // 记录成功操作
                UserLogUtil.sendAISuccessLog(userId, "腾讯元宝", "深度思考配置", 
                    "成功切换深度思考模型到: " + targetModelId, startTime, url + "/saveLogInfo");
            }
        } catch (Exception e) {
            // 记录深度思考配置异常
            UserLogUtil.sendAIBusinessLog(userId, "腾讯元宝", "深度思考配置", 
                "深度思考配置失败: " + e.getMessage(), startTime, url + "/saveLogInfo");
            // 兜底使用原方法
            clickDeepThing(page, deepThing, deepThingDom, targetModelId);
        }
    }

    public void clickWebSearch(Page page, Locator webSearch, String webSearchDom, String isWebSearch) throws
            InterruptedException {
        String searchText = "自动搜索";
        boolean visible = page.locator("//div[@class='yb-switch-internet-search-btn__left']").isVisible();
        if (visible) {
            searchText = page.locator("//div[@class='yb-switch-internet-search-btn__left']").textContent();
        }
        if (!searchText.equals("自动搜索")) {
            webSearch.waitFor(new Locator.WaitForOptions().setTimeout(5000));
            if (!webSearch.getAttribute("dt-ext3").equals(isWebSearch)) {
                Thread.sleep(1000);
                page.locator(webSearchDom).click();
            }
        }
        if (searchText.equals("自动搜索") && isWebSearch.equals("2")) {

            page.locator(webSearchDom).click();
            Thread.sleep(1000);
            page.locator("text=手动控制联网状态").click();
            Thread.sleep(1000);

        }
    }

    /**
     * 智能联网搜索点击 - 根据目标状态精确控制联网搜索
     * 优化版：仅使用class和ext3属性进行可靠检测
     */
    public void clickWebSearchSmart(Page page, Locator webSearch, String webSearchDom, String isWebSearch, String userId) throws
            InterruptedException {
        long startTime = System.currentTimeMillis();
        try {
            // 等待元素可见
            webSearch.waitFor(new Locator.WaitForOptions().setTimeout(5000));
            
            // 获取当前状态 - 仅使用可靠检测方式
            String currentClass = webSearch.getAttribute("class");
            String currentExt3 = webSearch.getAttribute("dt-ext3");
            
            // 主要检测：通过class检测（最可靠）
            boolean isCurrentlyEnabled = currentClass != null && currentClass.contains("checked");
            
            // 备用检测：通过ext3属性检测
            if (!isCurrentlyEnabled) {
                isCurrentlyEnabled = "1".equals(currentExt3);
            }
            
            boolean needWebSearch = "1".equals(isWebSearch);
            
            // 核心逻辑：仅在状态不一致时点击
            boolean shouldClick = (needWebSearch != isCurrentlyEnabled) || 
                                  (isWebSearch.equals("2") && !"1".equals(currentExt3));
            
            if (shouldClick) {
                Thread.sleep(1000);
                page.locator(webSearchDom).click();
                Thread.sleep(1000);
                
                // 如果出现手动控制选项，点击它
                if (page.locator("text=手动控制联网状态").count() > 0) {
                    page.locator("text=手动控制联网状态").click();
                    Thread.sleep(1000);
                }
                
                // 记录成功操作
                UserLogUtil.sendAISuccessLog(userId, "腾讯元宝", "联网搜索配置", 
                    "成功" + (needWebSearch ? "开启" : "关闭") + "联网搜索", startTime, url + "/saveLogInfo");
            }
        } catch (Exception e) {
            // 记录联网搜索配置异常
            UserLogUtil.sendAIBusinessLog(userId, "腾讯元宝", "联网搜索配置", 
                "联网搜索配置失败: " + e.getMessage(), startTime, url + "/saveLogInfo");
            // 兜底使用原方法
            clickWebSearch(page, webSearch, webSearchDom, isWebSearch);
        }
    }

    /**
     * html片段获取（核心监控方法）
     *
     * @param page Playwright页面实例
     */
    private String waitHtmlDom(Page page, String agentName, String userId, UserInfoRequest userInfoRequest) {
        try {
            // 等待聊天框的内容稳定
            String currentContent = "";
            String lastContent = "";
            String textContent = "";
            // 设置最大等待时间（单位：毫秒），比如 10 分钟
            long timeout = 600000; // 10 分钟
            long startTime = System.currentTimeMillis();  // 获取当前时间戳
            
            // 先等待5秒，确保AI开始响应
            logInfo.sendTaskLog(agentName + "等待AI开始响应...", userId, agentName);
            Thread.sleep(5000);
            
            // 获取最新会话的data-conv-idx
            int latestConvIdx = getLatestConversationIndex(page);
            logInfo.sendTaskLog(agentName + "检测到最新会话索引: " + latestConvIdx, userId, agentName);
            
            // 进入循环，直到内容不再变化或者超时
            while (true) {
                // 获取当前时间戳
                long elapsedTime = System.currentTimeMillis() - startTime;

                // 如果超时，退出循环
                if (elapsedTime > timeout) {
                    logInfo.sendTaskLog(agentName + "等待超时，停止监听", userId, agentName);
                    break;
                }
                
                // 优先检查是否有分享按钮（最准确的完成标志）
                if (hasShareButtonInLatestConversation(page, latestConvIdx)) {
                    logInfo.sendTaskLog(agentName + "检测到分享按钮，正在获取最终内容...", userId, agentName);
                    
                    // 重新获取最终完整内容
                    Locator finalOutputLocator = getLatestConversationContent(page, latestConvIdx);
                    if (finalOutputLocator != null) {
                        textContent = finalOutputLocator.textContent();
                        currentContent = finalOutputLocator.innerHTML();
                        logInfo.sendTaskLog(agentName + "最终内容获取完成，内容长度: " + (currentContent != null ? currentContent.length() : 0), userId, agentName);
                    }
                    
                    logInfo.sendTaskLog(agentName + "内容生成完成", userId, agentName);
                    break;
                }
                
                // 检查最新会话是否还在进行中
                if (isConversationInProgress(page, latestConvIdx)) {
                    logInfo.sendTaskLog(agentName + "会话仍在进行中，继续等待...", userId, agentName);
                    Thread.sleep(3000);
                    continue;
                }
                
                // 获取最新会话的内容
                Locator outputLocator = getLatestConversationContent(page, latestConvIdx);
                if (outputLocator == null) {
                    // 调试信息：打印会话区域的HTML结构
                    try {
                        Locator conversation = page.locator(".agent-chat__list__item--ai[data-conv-idx='" + latestConvIdx + "']");
                        if (conversation.count() > 0) {
                            String conversationHtml = conversation.first().innerHTML();
                            logInfo.sendTaskLog(agentName + "会话区域HTML: " + conversationHtml.substring(0, Math.min(500, conversationHtml.length())), userId, agentName);
                        }
                    } catch (Exception e) {
                        // 忽略调试信息异常
                    }
                    
                    logInfo.sendTaskLog(agentName + "未找到最新会话内容，继续等待...", userId, agentName);
                    Thread.sleep(2000);
                    continue;
                }
                
                textContent = outputLocator.textContent();
                currentContent = outputLocator.innerHTML();
                
                // 调试信息
                logInfo.sendTaskLog(agentName + "当前内容长度: " + (currentContent != null ? currentContent.length() : 0), userId, agentName);

                // 如果当前内容和上次内容相同，但没有分享按钮，继续等待
                if (currentContent.equals(lastContent) && !currentContent.isEmpty()) {
                    logInfo.sendTaskLog(agentName + "内容稳定但未发现分享按钮，继续等待分享按钮出现...", userId, agentName);
                    Thread.sleep(2000);
                    continue;
                }

                if (userInfoRequest.getAiName() != null && userInfoRequest.getAiName().contains("stream")) {
                    webSocketClientService.sendMessage(userInfoRequest, McpResult.success(textContent, ""), userInfoRequest.getAiName());
                }
                // 更新上次内容为当前内容
                lastContent = currentContent;

                // 等待 2 秒后再次检查
                Thread.sleep(2000);
            }
            
            if (userInfoRequest.getAiName() != null && userInfoRequest.getAiName().contains("stream")) {
                //延迟3秒结束，确保剩余内容全部输出
                Thread.sleep(3000);
                webSocketClientService.sendMessage(userInfoRequest, McpResult.success("END", ""), userInfoRequest.getAiName());
            }
            
            // 清理引用标签
            currentContent = currentContent.replaceAll("<div class=\"hyc-common-markdown__ref-list\".*?</div>|<span>.*?</span>", "");
            currentContent = currentContent.replaceAll(
                    "<div class=\"hyc-common-markdown__ref-list__trigger\"[^>]*>\\s*<div class=\"hyc-common-markdown__ref-list__item\"></div>\\s*</div>",
                    ""
            );
            
            logInfo.sendTaskLog(agentName + "内容已自动提取完成", userId, agentName);
            
            // 添加调试信息
            if (currentContent == null || currentContent.trim().isEmpty()) {
                logInfo.sendTaskLog(agentName + "警告：提取的内容为空！", userId, agentName);
            } else {
                logInfo.sendTaskLog(agentName + "内容提取成功，长度: " + currentContent.length(), userId, agentName);
            }
            
            if (agentName.contains("智能排版")) {
                return textContent;
            }
            return currentContent;

        } catch (Exception e) {
            UserLogUtil.sendExceptionLog(userId, agentName + "获取内容失败", "waitHtmlDom", e, url + "/saveLogInfo");
        }
        return "获取内容失败";
    }
    
    /**
     * 获取最新会话的data-conv-idx索引
     * 会话索引按2,4,6,8...递增
     */
    private int getLatestConversationIndex(Page page) {
        try {
            // 查找所有AI回复的会话项
            Locator aiConversations = page.locator(".agent-chat__list__item--ai[data-conv-idx]");
            int maxIndex = 0;
            
            for (int i = 0; i < aiConversations.count(); i++) {
                String convIdx = aiConversations.nth(i).getAttribute("data-conv-idx");
                if (convIdx != null) {
                    int index = Integer.parseInt(convIdx);
                    maxIndex = Math.max(maxIndex, index);
                }
            }
            
            return maxIndex;
        } catch (Exception e) {
            logInfo.sendTaskLog("获取会话索引失败，使用默认值", "", "");
            return 2; // 默认返回2
        }
    }
    
    /**
     * 检查指定会话是否还在进行中
     * 通过检查是否有"--last"类名来判断
     */
    private boolean isConversationInProgress(Page page, int convIdx) {
        try {
            Locator conversation = page.locator(".agent-chat__list__item--ai[data-conv-idx='" + convIdx + "']");
            if (conversation.count() == 0) {
                return true; // 如果找不到会话，认为还在进行中
            }
            
            // 检查是否有"--last"类名，如果有说明是最新的且可能还在进行
            String className = conversation.first().getAttribute("class");
            boolean hasLastClass = className != null && className.contains("--last");
            
            // 如果有--last类名，进一步检查内容是否在变化
            if (hasLastClass) {
                // 检查是否有加载动画或进度指示器
                Locator loadingIndicator = conversation.locator(".hyc-card-box-process-list");
                if (loadingIndicator.count() > 0) {
                    String style = loadingIndicator.first().getAttribute("style");
                    // 如果动画正在运行或不是隐藏状态，说明还在加载
                    return style == null || !style.contains("--hidden");
                }
            }
            
            return false;
        } catch (Exception e) {
            return true; // 出错时保守地认为还在进行中
        }
    }
    
    /**
     * 获取指定会话的内容定位器
     */
    private Locator getLatestConversationContent(Page page, int convIdx) {
        try {
            Locator conversation = page.locator(".agent-chat__list__item--ai[data-conv-idx='" + convIdx + "']");
            if (conversation.count() == 0) {
                return null;
            }
            
            // 尝试多种选择器获取内容
            String[] contentSelectors = {
                ".hyc-common-markdown",
                ".markdown-content", 
                ".ai-content",
                ".response-content",
                ".chat-content",
                ".agent-response",
                "[data-testid='markdown-content']",
                ".message-content"
            };
            
            for (String selector : contentSelectors) {
                Locator contentLocator = conversation.locator(selector).first();
                if (contentLocator.count() > 0) {
                    return contentLocator;
                }
            }
            
            return null;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 检查最新会话区域是否出现分享按钮
     * 分享按钮的出现表示AI已完全生成完毕
     */
    private boolean hasShareButtonInLatestConversation(Page page, int convIdx) {
        try {
            // 定位到指定的会话项
            Locator conversation = page.locator(".agent-chat__list__item--ai[data-conv-idx='" + convIdx + "']");
            if (conversation.count() == 0) {
                return false;
            }
            
            // 在该会话区域内查找分享按钮
            Locator shareButton = conversation.locator("span.icon-yb-ic_share_2504");
            boolean hasButton = shareButton.count() > 0;
            
            if (hasButton) {
                // 进一步检查按钮是否可见和可点击
                try {
                    return shareButton.first().isVisible();
                } catch (Exception e) {
                    // 如果检查可见性失败，认为按钮存在但可能还未完全加载
                    return true;
                }
            }
            
            return false;
        } catch (Exception e) {
            // 出错时返回false，继续等待
            return false;
        }
    }


}
