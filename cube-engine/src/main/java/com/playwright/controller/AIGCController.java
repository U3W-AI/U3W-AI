package com.playwright.controller;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.playwright.entity.UnPersisBrowserContextInfo;
import com.playwright.entity.UserInfoRequest;
import com.playwright.utils.*;
import com.playwright.websocket.WebSocketClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.w3c.dom.Node;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI生成内容控制器
 * 处理与各大AI平台（腾讯元宝、豆包等）的交互操作
 *
 * @author 优立方
 * @version JDK 17
 * @date 2025年01月21日 08:53
 */

@RestController
@RequestMapping("/api/browser")
@Tag(name = "AI生成内容控制器", description = "处理与各大AI平台（腾讯元宝、豆包等）的交互操作")
public class AIGCController {

    // 依赖注入 注入webSocketClientService 进行消息发送
    private final WebSocketClientService webSocketClientService;

    // 构造器注入WebSocket服务
    public AIGCController(WebSocketClientService webSocketClientService) {
        this.webSocketClientService = webSocketClientService;
    }

    @Autowired
    private DeepSeekUtil deepSeekUtil;

    // 从配置文件中注入URL 调用远程API存储数据
    @Value("${cube.url}")
    private String url;

    // 腾讯元宝相关操作工具类
    @Autowired
    private TencentUtil tencentUtil;

    // 豆包相关操作工具类
    @Autowired
    private DouBaoUtil douBaoUtil;

    // MiniMax相关操作工具类
    @Autowired
    private MiniMaxUtil miniMaxUtil;

    // 秘塔相关操作工具类
    @Autowired
    private MetasoUtil metasoUtil;

    // KiMi相关操作工具类
    @Autowired
    private KimiUtil kimiUtil;

    // 元素选择器工具类
    @Autowired
    private ElementSelectorUtil elementSelectorUtil;

    // 通义AI相关操作工具类
    @Autowired
    private TongYiUtil tongYiUtil;

    // 知乎直答相关操作工具类
    @Autowired
    private ZHZDUtil zhzdUtil;

    @Autowired
    private QwenUtil qwenUtil;

    // 日志记录工具类
    @Autowired
    private LogMsgUtil logInfo;

    // 浏览器操作工具类
    @Autowired
    private BrowserUtil browserUtil;

    // 浏览器截图操作工具类
    @Autowired
    private ScreenshotUtil screenshotUtil;

    @Autowired
    private ClipboardLockManager clipboardLockManager;

    @Autowired
    private TTHUtil tthUtil;

    // 百度AI相关操作工具类
    @Autowired
    private BaiduUtil baiduUtil;

    @Value("${cube.uploadurl}")
    private String uploadUrl;

    @Autowired
    private BrowserConcurrencyManager browserConcurrencyManager;


    /**
     * 处理多AI代理的请求
     *
     * @param userInfoRequest 包含用户ID、角色、提示信息等
     * @return 拼接的生成内容
     */
    @Operation(summary = "处理多AI代理请求", description = "根据用户输入启动多个AI平台代理任务")
    @ApiResponse(responseCode = "200", description = "处理成功", content = @Content(mediaType = "application/json"))
    @PostMapping("/startAgent")
    public String startAgent(@RequestBody UserInfoRequest userInfoRequest) throws Exception {
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false, userInfoRequest.getUserId(), "agent")) {

            // 初始化变量
            String userId = userInfoRequest.getUserId();
            String roles = userInfoRequest.getRoles();
            String userPrompt = userInfoRequest.getUserPrompt();
            String isNewChat = userInfoRequest.getIsNewChat();
            String copiedText = "";
            int wrightCopyCount = 0;


            // 根据不同的AI角色创建对应的页面实例
            Page[] pages = new Page[6];

            // 处理 智能体 代理 - 为每个智能体分配独立的页面
            if (roles.contains("cube-trubos-agent")) {
                logInfo.sendTaskLog("AI搜索@元器准备就绪，正在打开页面", userId, "AI搜索@元器");
                pages[0] = (pages[0] == null) ? browserUtil.getOrCreatePage(context) : pages[0];
                String agentUrl = "https://yuanbao.tencent.com/chat/58LgTturCBdj/";
                wrightCopyCount = tencentUtil.handelAgentAI(pages[0], userPrompt, agentUrl, "AI搜索@元器", userId, isNewChat);
            }
            if (roles.contains("cube-turbos-large-agent")) {
                logInfo.sendTaskLog("数智化助手@元器准备就绪，正在打开页面", userId, "数智化助手@元器");
                pages[1] = (pages[1] == null) ? context.newPage() : pages[1];
                String agentUrl = "https://yuanbao.tencent.com/chat/rgzZDhQdsMHZ/";
                wrightCopyCount = tencentUtil.handelAgentAI(pages[1], userPrompt, agentUrl, "数智化助手@元器", userId, isNewChat);
            }
            if (roles.contains("cube-mini-max-agent")) {
                logInfo.sendTaskLog("MiniMax@元器准备就绪，正在打开页面", userId, "MiniMax@元器");
                pages[2] = (pages[2] == null) ? context.newPage() : pages[2];
                String agentUrl = "https://yuanbao.tencent.com/chat/7kNJBgAgQFet/";
                wrightCopyCount = tencentUtil.handelAgentAI(pages[2], userPrompt, agentUrl, "MiniMax@元器", userId, isNewChat);
            }
            if (roles.contains("mini-max-agent")) {
                logInfo.sendTaskLog("MiniMax Chat准备就绪，正在打开页面", userId, "MiniMax Chat");
                pages[2] = (pages[2] == null) ? context.newPage() : pages[2];
                String agentUrl = "https://chat.minimaxi.com/";
                wrightCopyCount = tencentUtil.handelAgentAI(pages[2], userPrompt, agentUrl, "MiniMax Chat", userId, isNewChat);
            }
            if (roles.contains("metaso-agent")) {
                logInfo.sendTaskLog("秘塔准备就绪，正在打开页面", userId, "秘塔");
                pages[3] = (pages[3] == null) ? context.newPage() : pages[3];
                String agentUrl = "https://metaso.cn/";
                wrightCopyCount = tencentUtil.handelAgentAI(pages[3], userPrompt, agentUrl, "秘塔", userId, isNewChat);
            }
            if (roles.contains("cube-sogou-agent")) {
                logInfo.sendTaskLog("搜狗搜索@元器准备就绪，正在打开页面", userId, "搜狗搜索@元器");
                pages[3] = (pages[3] == null) ? context.newPage() : pages[3];
                String agentUrl = "https://yuanbao.tencent.com/chat/u1VeB6jKt0lE/";
                wrightCopyCount = tencentUtil.handelAgentAI(pages[3], userPrompt, agentUrl, "搜狗搜索@元器", userId, isNewChat);
            }
            if (roles.contains("cube-lwss-agent")) {
                logInfo.sendTaskLog("KIMI@元器准备就绪，正在打开页面", userId, "KIMI@元器");
                pages[4] = (pages[4] == null) ? context.newPage() : pages[4];
                String agentUrl = "https://yuanbao.tencent.com/chat/oq4esMyN9VS2/";
                wrightCopyCount = tencentUtil.handelAgentAI(pages[4], userPrompt, agentUrl, "KIMI@元器", userId, isNewChat);
            }
            if (roles.contains("baidu-agent")) {
                logInfo.sendTaskLog("百度AI准备就绪，正在打开页面", userId, "百度AI");
                pages[5] = (pages[5] == null) ? context.newPage() : pages[5];
                String agentUrl = "https://chat.baidu.com/";
                // 直接使用handleBaiduAI方法处理，返回内容而不是计数
                String baiduContent = baiduUtil.handleBaiduAI(pages[5], userPrompt, userId, roles, null);
                wrightCopyCount = baiduContent.length() > 0 ? 1 : 0; // 简单的成功标识
            }

            // 保存各代理生成的数据并拼接结果
            if (roles.contains("cube-trubos-agent")) {
                copiedText = copiedText + "\n\n" + tencentUtil.saveAgentDraftData(pages[0], userInfoRequest, "cube-trubos-agent", userId, wrightCopyCount, "AI搜索@元器", "RETURN_TURBOS_RES");
            }
            if (roles.contains("cube-turbos-large-agent")) {
                copiedText = copiedText + "\n\n" + tencentUtil.saveAgentDraftData(pages[1], userInfoRequest, "cube-turbos-large-agent", userId, wrightCopyCount, "数智化助手@元器", "RETURN_TURBOS_LARGE_RES");
            }
            if (roles.contains("cube-mini-max-agent")) {
                copiedText = copiedText + "\n\n" + tencentUtil.saveAgentDraftData(pages[2], userInfoRequest, "cube-mini-max-agent", userId, wrightCopyCount, "MiniMax@元器", "RETURN_MINI_MAX_RES");
            }
            if (roles.contains("mini-max-agent")) {
                copiedText = copiedText + "\n\n" + tencentUtil.saveAgentDraftData(pages[2], userInfoRequest, "mini-max-agent", userId, wrightCopyCount, "MiniMax Chat", "RETURN_MINI_MAX_RES");
            }
            if (roles.contains("metaso-agent")) {
                copiedText = copiedText + "\n\n" + tencentUtil.saveAgentDraftData(pages[2], userInfoRequest, "metaso-agent", userId, wrightCopyCount, "秘塔", "RETURN_METASO_RES");
            }
            if (roles.contains("cube-sogou-agent")) {
                copiedText = copiedText + "\n\n" + tencentUtil.saveAgentDraftData(pages[3], userInfoRequest, "cube-sogou-agent", userId, wrightCopyCount, "搜狗搜索@元器", "RETURN_SOGOU_RES");
            }
            if (roles.contains("cube-lwss-agent")) {
                copiedText = copiedText + "\n\n" + tencentUtil.saveAgentDraftData(pages[4], userInfoRequest, "cube-lwss-agent", userId, wrightCopyCount, "KIMI@元器", "RETURN_LWSS_RES");
            }
            if (roles.contains("baidu-agent")) {
                // 获取百度AI生成的内容
                String baiduContent = baiduUtil.waitBaiduHtmlDom(pages[5], userId, "百度AI");
                copiedText = copiedText + "\n\n" + baiduUtil.saveBaiduContent(pages[5], userInfoRequest, roles, userId, baiduContent);
            }
            return copiedText;
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * 处理腾讯元宝平台的请求
     *
     * @param userInfoRequest 包含用户信息和会话参数
     * @return 生成的内容（当前版本暂未实现内容返回）
     */
    @Operation(summary = "启动腾讯元宝内容生成", description = "根据角色执行不同类型的腾讯元宝任务（T1和DS）")
    @ApiResponse(responseCode = "200", description = "处理成功", content = @Content(mediaType = "application/json"))
    @PostMapping("/startYB")
    public String startYB(@RequestBody UserInfoRequest userInfoRequest) throws Exception {
        String userId = userInfoRequest.getUserId();
        String currentContent = "";
        String roles = userInfoRequest.getRoles();
        String userPrompt = userInfoRequest.getUserPrompt();
        String t1ChatId = userInfoRequest.getToneChatId();
        String dschatId = userInfoRequest.getYbDsChatId();
        // 验证必要参数
        if (roles == null || roles.trim().isEmpty()) {
            String errorMsg = "错误：roles参数为空，无法执行元宝智能体任务";
            logInfo.sendTaskLog(errorMsg, userId, "元包智能体");

            // 发送错误响应
            try {
                logInfo.sendResData(errorMsg, userId, "元包智能体", "RETURN_YB_RES", "", "");
            } catch (Exception e) {
                // 静默处理
            }
            return errorMsg;
        }

        if (userPrompt == null || userPrompt.trim().isEmpty()) {
            String errorMsg = "错误：userPrompt参数为空，无法执行元宝智能体任务";
            logInfo.sendTaskLog(errorMsg, userId, "元包智能体");

            // 发送错误响应
            try {
                logInfo.sendResData(errorMsg, userId, "元包智能体", "RETURN_YB_RES", "", "");
            } catch (Exception e) {
                // 静默处理
            }
            return errorMsg;
        }
        logInfo.sendTaskLog("元宝智能体任务开始，角色配置: " + roles, userId, "元包智能体");
        try {
            CountDownLatch countDownLatch = new CountDownLatch(2);
            //======================腾讯元宝T1=======================//
            new Thread(() -> {
                try {
                    Page hyPage = tencentUtil.getPage("T1", userId);
                    long start = System.currentTimeMillis();
                    //腾讯元宝T1  根据角色组合处理不同模式（普通/深度思考/联网）
                    logInfo.sendTaskLog("腾讯元宝T1准备就绪，正在打开页面", userId, "腾讯元宝T1");
                    if (roles.contains("yb-hunyuan-pt") && !roles.contains("yb-hunyuan-sdsk") && !roles.contains("yb-hunyuan-lwss")) {
                        tencentUtil.handleYBAI(hyPage, userPrompt, "yb-hunyuan-pt", userId, "腾讯元宝T1", t1ChatId);
                    } else if (roles.contains("yb-hunyuan-sdsk") && !roles.contains("yb-hunyuan-lwss")) {
                        //深度思考
                        tencentUtil.handleYBAI(hyPage, userPrompt, "yb-hunyuan-sdsk", userId, "腾讯元宝T1", t1ChatId);
                    } else if (roles.contains("yb-hunyuan-lwss") && !roles.contains("yb-hunyuan-sdsk")) {
                        //联网
                        tencentUtil.handleYBAI(hyPage, userPrompt, "yb-hunyuan-lwss-1", userId, "腾讯元宝T1", t1ChatId);
                    } else if (roles.contains("yb-hunyuan-lwss") && roles.contains("yb-hunyuan-sdsk")) {
                        //深度思考 + 联网
                        tencentUtil.handleYBAI(hyPage, userPrompt, "yb-hunyuan-lwss-2", userId, "腾讯元宝T1", t1ChatId);
                    }
                    countDownLatch.countDown();
                    countDownLatch.await();
                    hyPage = tencentUtil.getPage("T1", userId);
                    String resultT1 = "";
                    hyPage = tencentUtil.getPage("T1", userId);
                    //保存入库 腾讯元宝T1 - T1和DS独立处理，各自发送响应
                    if (roles.contains("yb-hunyuan-pt") && !roles.contains("yb-hunyuan-sdsk") && !roles.contains("yb-hunyuan-lwss")) {
                        resultT1 = tencentUtil.saveDraftData(hyPage, userInfoRequest, "yb-hunyuan-pt", userId);
                    } else if (roles.contains("yb-hunyuan-sdsk") && !roles.contains("yb-hunyuan-lwss")) {
                        //深度思考
                        resultT1 = tencentUtil.saveDraftData(hyPage, userInfoRequest, "yb-hunyuan-sdsk", userId);
                    } else if (roles.contains("yb-hunyuan-lwss")) {
                        //深度思考 + 联网
                        resultT1 = tencentUtil.saveDraftData(hyPage, userInfoRequest, "yb-hunyuan-lwss", userId);
                    }
                    UserLogUtil.sendNormalLog(userId, "启动腾讯元宝T1生成", "startYB", start, resultT1, url + "/saveLogInfo");
                } catch (Exception e) {
                    logInfo.sendTaskLog("腾讯元宝T1执行异常", userId, "腾讯元宝T1");
                    UserLogUtil.sendExceptionLog(userId, "腾讯元宝T1执行异常", "startYB", e, url + "/saveLogInfo");
                }
            }).start();

            //======================腾讯元宝DS=======================//

            try {
                Page dsPage = tencentUtil.getPage("DS", userId);
                Long start = System.currentTimeMillis();
                logInfo.sendTaskLog("腾讯元宝DS准备就绪，正在打开页面", userId, "腾讯元宝DS");
                Thread.sleep(3000);
                //腾讯元宝DS  根据角色组合处理不同模式（普通/深度思考/联网）
                if (roles.contains("yb-deepseek-pt") && !roles.contains("yb-deepseek-sdsk") && !roles.contains("yb-deepseek-lwss")) {
                    tencentUtil.handleYBAI(dsPage, userPrompt, "yb-deepseek-pt", userId, "腾讯元宝DS", dschatId);
                } else if (roles.contains("yb-deepseek-sdsk") && !roles.contains("yb-deepseek-lwss")) {
                    //深度思考
                    tencentUtil.handleYBAI(dsPage, userPrompt, "yb-deepseek-sdsk", userId, "腾讯元宝DS", dschatId);
                } else if (roles.contains("yb-deepseek-lwss") && !roles.contains("yb-deepseek-sdsk")) {
                    //深度思考 + 联网
                    tencentUtil.handleYBAI(dsPage, userPrompt, "yb-deepseek-lwss-1", userId, "腾讯元宝DS", dschatId);
                } else if (roles.contains("yb-deepseek-lwss") && roles.contains("yb-deepseek-sdsk")) {
                    //深度思考 + 联网
                    tencentUtil.handleYBAI(dsPage, userPrompt, "yb-deepseek-lwss-2", userId, "腾讯元宝DS", dschatId);
                }
                countDownLatch.countDown();
                countDownLatch.await();
                dsPage = tencentUtil.getPage("DS", userId);
                String resultDS = "";
                //保存入库 腾讯元宝DS - DS独立处理，发送自己的响应
                if (roles.contains("yb-deepseek-pt") && !roles.contains("yb-deepseek-sdsk") && !roles.contains("yb-deepseek-lwss")) {
                    resultDS = tencentUtil.saveDraftData(dsPage, userInfoRequest, "yb-deepseek-pt", userId);
                } else if (roles.contains("yb-deepseek-sdsk") && !roles.contains("yb-deepseek-lwss")) {
                    resultDS = tencentUtil.saveDraftData(dsPage, userInfoRequest, "yb-deepseek-sdsk", userId);
                } else if (roles.contains("yb-deepseek-lwss")) {
                    //深度思考 + 联网
                    resultDS = tencentUtil.saveDraftData(dsPage, userInfoRequest, "yb-deepseek-lwss", userId);
                }
                UserLogUtil.sendNormalLog(userId, "启动腾讯元宝DS生成", "startYB", start, resultDS, url + "/saveLogInfo");
            } catch (Exception e) {
                logInfo.sendTaskLog("腾讯元宝DS执行异常", userId, "腾讯元宝DS");
                UserLogUtil.sendExceptionLog(userId, "腾讯元宝DS执行异常", "startYB", e, url + "/saveLogInfo");
            }
            // 等待所有线程执行完毕
            System.out.println("DS跟T1执行完成");
            return "执行完成";
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * 处理MiniMax的常规请求
     *
     * @param userInfoRequest 包含会话ID和用户指令
     * @return AI生成的文本内容
     */
    @Operation(summary = "启动MiniMaxAI生成", description = "调用MiniMax平台生成内容并抓取结果")
    @ApiResponse(responseCode = "200", description = "处理成功", content = @Content(mediaType = "application/json"))
    @PostMapping("/startMiniMax")
    public String startMiniMax(@RequestBody UserInfoRequest userInfoRequest) throws Exception {
        try (
                BrowserContext context = browserUtil.createPersistentBrowserContext(false, userInfoRequest.getUserId(), "MiniMax Chat")) {

            // 初始化变量
            String userId = userInfoRequest.getUserId();
            String maxChatId = userInfoRequest.getMaxChatId();
            logInfo.sendTaskLog("MiniMax准备就绪，正在打开页面", userId, "MiniMax Chat");
            // 获取是否开启深度思考和联网搜索
            String roles = userInfoRequest.getRoles();
            String userPrompt = userInfoRequest.getUserPrompt();

            // 初始化页面并导航到指定会话
            Page page = browserUtil.getOrCreatePage(context);
            if (maxChatId != null && !maxChatId.isEmpty()) {
                page.navigate("https://chat.minimaxi.com/?type=chat&chatID=" + maxChatId);
            } else {
                page.navigate("https://chat.minimaxi.com/");
            }
            page.waitForLoadState(LoadState.LOAD);
            page.waitForTimeout(3000); // 等待页面完全加载

            // 去除弹窗
            try {
                Locator closeBtn = page.locator(".md\\:hover\\:bg-col_text05.text-col_text02.md\\:text-col_text03.z-50.flex.h-\\[30px\\].w-\\[30px\\].cursor-pointer.items-center.justify-center.rounded");
                if (closeBtn.count() > 0) {
                    closeBtn.first().click();
                    page.waitForTimeout(1000);
                }
            } catch (Exception e) {
            }

            // 移除侧边栏
            try {
                Locator aside = page.locator("aside.shadow-s1");
                if (aside.count() > 0) {
                    aside.first().evaluate("el => el.remove()");
                }
            } catch (Exception e) {
            }

            logInfo.sendTaskLog("MiniMax页面打开完成", userId, "MiniMax Chat");

            // 使用工具类定位深度思考按钮
            try {
                Locator deepThoughtButton = elementSelectorUtil.findElementWithMultipleSelectors(
                        page, elementSelectorUtil.getDeepThinkSelectors(), 5000);

                if (deepThoughtButton == null) {
                    logInfo.sendTaskLog("未找到深度思考按钮，跳过此步骤", userId, "MiniMax Chat");
                } else {
                    // 检查按钮激活状态
                    boolean isActiveForSK = elementSelectorUtil.isElementActive(
                            deepThoughtButton, elementSelectorUtil.getActiveClasses());

                    // 处理深度思考模式
                    if (roles.contains("max-sdsk")) {
                        if (!isActiveForSK) {
                            if (elementSelectorUtil.safeClick(deepThoughtButton, "启动深度思考模式")) {
                                page.waitForTimeout(1000);
                                logInfo.sendTaskLog("已启动深度思考模式", userId, "MiniMax Chat");
                            } else {
                                logInfo.sendTaskLog("启动深度思考模式失败", userId, "MiniMax Chat");
                            }
                        } else {
                            logInfo.sendTaskLog("深度思考模式已启用", userId, "MiniMax Chat");
                        }
                    } else {
                        // 关闭深度思考模式
                        if (isActiveForSK) {
                            if (elementSelectorUtil.safeClick(deepThoughtButton, "关闭深度思考模式")) {
                                page.waitForTimeout(1000);
                                logInfo.sendTaskLog("已关闭深度思考模式", userId, "MiniMax Chat");
                            } else {
                                logInfo.sendTaskLog("关闭深度思考模式失败", userId, "MiniMax Chat");
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logInfo.sendTaskLog("处理深度思考按钮时出错", userId, "MiniMax Chat");
            }

            // 使用工具类定位联网搜索按钮
            try {
                Locator internetThoughtButton = elementSelectorUtil.findElementWithMultipleSelectors(
                        page, elementSelectorUtil.getInternetSelectors(), 5000);

                if (internetThoughtButton == null) {
                    logInfo.sendTaskLog("未找到联网按钮，跳过此步骤", userId, "MiniMax Chat");
                } else {
                    // 检查按钮激活状态
                    boolean isActiveForLW = elementSelectorUtil.isElementActive(
                            internetThoughtButton, elementSelectorUtil.getActiveClasses());

                    // 处理联网搜索模式
                    if (roles.contains("max-lwss")) {
                        if (!isActiveForLW) {
                            if (elementSelectorUtil.safeClick(internetThoughtButton, "启动联网搜索模式")) {
                                page.waitForTimeout(1000);
                                logInfo.sendTaskLog("已启动联网搜索模式", userId, "MiniMax Chat");
                            } else {
                                logInfo.sendTaskLog("启动联网搜索模式失败", userId, "MiniMax Chat");
                            }
                        } else {
                            logInfo.sendTaskLog("联网搜索模式已启用", userId, "MiniMax Chat");
                        }
                    } else {
                        // 关闭联网模式
                        if (isActiveForLW) {
                            if (elementSelectorUtil.safeClick(internetThoughtButton, "关闭联网搜索模式")) {
                                page.waitForTimeout(1000);
                                logInfo.sendTaskLog("已关闭联网搜索模式", userId, "MiniMax Chat");
                            } else {
                                logInfo.sendTaskLog("关闭联网搜索模式失败", userId, "MiniMax Chat");
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logInfo.sendTaskLog("处理联网按钮时出错", userId, "MiniMax Chat");
            }

            // 获取输入框并输入内容
            Thread.sleep(1000);
            page.locator("//*[@id=\"chat-input\"]").nth(1).click();
            Thread.sleep(1000);
            page.locator("//*[@id=\"chat-input\"]").nth(1).fill(userPrompt);
            logInfo.sendTaskLog("用户指令已自动输入完成", userId, "MiniMax Chat");
            Thread.sleep(1000);
            page.locator("//*[@id=\"chat-input\"]").nth(1).press("Enter");
            logInfo.sendTaskLog("指令已自动发送成功", userId, "MiniMax Chat");


            // 创建定时截图线程
            AtomicInteger i = new AtomicInteger(0);
            ScheduledExecutorService screenshotExecutor = Executors.newSingleThreadScheduledExecutor();
            // 启动定时任务，每5秒执行一次截图
            ScheduledFuture<?> screenshotFuture = screenshotExecutor.scheduleAtFixedRate(() -> {
                try {
                    int currentCount = i.getAndIncrement(); // 获取当前值并自增
                    logInfo.sendImgData(page, userId + "MiniMax执行过程截图" + currentCount, userId);

                } catch (Exception e) {
                    UserLogUtil.sendExceptionLog(userId, "MinMax截图", "startMiniMax", e, url + "/saveLogInfo");
                }
            }, 0, 8, TimeUnit.SECONDS);

            logInfo.sendTaskLog("开启自动监听任务，持续监听MiniMax回答中", userId, "MiniMax Chat");
            //等待html片段获取完成
            String copiedText = miniMaxUtil.waitMiniMaxHtmlDom(page, userId, "MiniMax Chat");
            //关闭截图
            screenshotFuture.cancel(false);
            screenshotExecutor.shutdown();

            if (!copiedText.contains("换个话题试试吧")) {
                AtomicReference<String> shareUrlRef = new AtomicReference<>();

                clipboardLockManager.runWithClipboardLock(() -> {
                    try {
                        // 点击分享链接按钮
                        page.locator(
                                "(//div[contains(@class,'system-operation-box')])[last()]//div[" +
                                        "contains(@class,'md:hover:bg-col_bg03') and " +
                                        "contains(@class,'flex') and " +
                                        "contains(@class,'h-7') and " +
                                        "contains(@class,'w-7') and " +
                                        "contains(@class,'cursor-pointer') and " +
                                        "contains(@class,'items-center') and " +
                                        "contains(@class,'justify-center') and " +
                                        "contains(@class,'rounded-[8px]')" +
                                        "][3]"
                        ).click();
                        // 等待加载
                        Thread.sleep(1000);
                        page.locator("xpath=/html/body/section/div/div/section/div/div[1]/div/div/main/section/div[2]/div[1]/span[1]").click();
                        // 点击复制链接
                        page.locator("xpath=/html/body/section/div/div/section/div/div[1]/div/div/main/section/div[2]/div[2]/div[2]").click();
                        // 建议适当延迟等待内容更新
                        Thread.sleep(1000);
                        String shareUrl = (String) page.evaluate("navigator.clipboard.readText()");
                        shareUrlRef.set(shareUrl);
                    } catch (Exception e) {
                        UserLogUtil.sendExceptionLog(userId, "MiniMax复制链接", "startMiniMax", e, url + "/saveLogInfo");
                    }
                });

                Thread.sleep(1000);
                String shareUrl = shareUrlRef.get();
                String sharImgUrl = "";
                // 点击分享按钮
                page.locator(
                        "(//div[contains(@class,'system-operation-box')])[last()]//div[" +
                                "contains(@class,'md:hover:bg-col_bg03') and " +
                                "contains(@class,'flex') and " +
                                "contains(@class,'h-7') and " +
                                "contains(@class,'w-7') and " +
                                "contains(@class,'cursor-pointer') and " +
                                "contains(@class,'items-center') and " +
                                "contains(@class,'justify-center') and " +
                                "contains(@class,'rounded-[8px]')" +
                                "][3]"
                ).click();
                Thread.sleep(1000);
                String classSelector = ".bg-col_fill03.inline-flex.h-\\[38px\\].items-center.justify-center.gap-\\[5px\\].rounded-\\[12px\\].px-\\[10px\\].text-\\[14px\\]";
                Locator thinkElements = page.locator(classSelector);
                int count = thinkElements.count();

                for (int j = 0; j < count; j++) {
                    Locator think = thinkElements.nth(j);
                    // 获取点击元素的父元素
                    Locator parent = think.locator("xpath=..");
                    // 获取父元素的兄弟元素
                    Locator sibling = parent.locator("xpath=following-sibling::*");
                    String siblingClass = sibling.getAttribute("class");
                    boolean isHidden = siblingClass != null && siblingClass.contains("hidden");
                    if (isHidden) {
                    } else {
                        try {
                            think.click(new Locator.ClickOptions().setForce(true));
                        } catch (PlaywrightException e) {
                        }
                    }
                }

                // 点击最近10条
                page.locator("xpath=/html/body/section/div/div/section/div/div[1]/div/div/main/section/div[2]/div[1]/span[1]").click();
                Thread.sleep(200);
                page.locator("xpath=/html/body/section/div/div/section/div/div[1]/div/div/main/section/div[2]/div[1]/span[1]").click();
                Thread.sleep(200);
                page.locator("xpath=/html/body/section/div/div/section/div/div[1]/div/div/main/section/div[2]/div[1]/span[1]").click();
                Thread.sleep(1000);
                // 点击生成分享图
                page.locator("xpath=/html/body/section/div/div/section/div/div[1]/div/div/main/section/div[2]/div[2]/div[1]").click();
                Thread.sleep(3000);
                // 点击下载按钮
                sharImgUrl = ScreenshotUtil.downloadAndUploadFile(page, uploadUrl, () -> {
                    page.locator("xpath=/html/body/section/div/div/section/div/div[1]/div/div/main/div[1]/div[2]/div/div[2]").click();
                });

                logInfo.sendTaskLog("执行完成", userId, "MiniMax Chat");
                logInfo.sendChatData(page, "chatID=([0-9]+)", userId, "RETURN_MAX_CHATID", 1);
                logInfo.sendResData(copiedText, userId, "MiniMax Chat", "RETURN_MAX_RES", shareUrl, sharImgUrl);

                //保存数据库
                userInfoRequest.setDraftContent(copiedText);
                userInfoRequest.setAiName("MiniMax Chat");
                userInfoRequest.setShareUrl(shareUrl);
                userInfoRequest.setShareImgUrl(sharImgUrl);
                RestUtils.post(url + "/saveDraftContent", userInfoRequest);
            } else {
                logInfo.sendTaskLog("执行完成,MiniMax 提示换个话题", userId, "MiniMax Chat");
                logInfo.sendChatData(page, "chatID=([0-9]+)", userId, "RETURN_MAX_CHATID", 1);
                logInfo.sendResData(copiedText, userId, "MiniMax Chat", "RETURN_MAX_RES", "", "");

                //保存数据库
                userInfoRequest.setDraftContent(copiedText);
                userInfoRequest.setAiName("MiniMax Chat");
                userInfoRequest.setShareUrl("");
                userInfoRequest.setShareImgUrl("");
                RestUtils.post(url + "/saveDraftContent", userInfoRequest);
            }
            return copiedText;
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * 处理秘塔的常规请求
     *
     * @param userInfoRequest 包含会话ID和用户指令
     * @return AI生成的文本内容
     */
    @Operation(summary = "启动秘塔AI生成", description = "调用秘塔AI平台生成内容并抓取结果")
    @ApiResponse(responseCode = "200", description = "处理成功", content = @Content(mediaType = "application/json"))
    @PostMapping("/startMetaso")
    public String startMetaso(@RequestBody UserInfoRequest userInfoRequest) throws IOException, InterruptedException {
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false, userInfoRequest.getUserId(), "metaso")) {

            // 初始化变量
            String userId = userInfoRequest.getUserId();
            String metasoChatId = userInfoRequest.getMetasoChatId();
            logInfo.sendTaskLog("秘塔准备就绪，正在打开页面", userId, "秘塔");
            String roles = userInfoRequest.getRoles();
            String userPrompt = userInfoRequest.getUserPrompt();

            // 初始化页面并导航到指定会话测试用
            Page page = browserUtil.getOrCreatePage(context);
            if (metasoChatId != null && !metasoChatId.isEmpty()) {
                page.navigate("https://metaso.cn/search/" + metasoChatId);
            } else {
                page.navigate("https://metaso.cn/");
            }
            page.waitForLoadState(LoadState.LOAD);
            Thread.sleep(1000);
            logInfo.sendTaskLog("秘塔页面打开完成", userId, "秘塔");


            if (metasoChatId != null && !metasoChatId.isEmpty()) {
                Thread.sleep(1000);
                page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("继续追问")).click();
                Thread.sleep(1000);
                page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("继续追问")).fill(userPrompt);
                logInfo.sendTaskLog("用户指令已自动输入完成", userId, "秘塔");
                Thread.sleep(1000);
                page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("继续追问")).press("Enter");
                logInfo.sendTaskLog("指令已自动发送成功", userId, "秘塔");
            } else {
                if (roles.contains("metaso-jssk")) {
                    // 定位极速思考按钮
                    Thread.sleep(1000);
                    page.locator("//*[@id=\"searchRoot\"]/div[1]/div[2]/div[5]/form/div[2]/div[1]/div/div").click();
                    Thread.sleep(3000);

                    //点击极速思考按钮
                    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("极速·思考 快速思考，智力在线")).click();

                    Thread.sleep(1000);

                    logInfo.sendTaskLog("已启动极速思考模式", userId, "秘塔");
                } else if (roles.contains("metaso-jisu")) {
                    // 定位极速按钮
                    Thread.sleep(1000);
                    page.locator("//*[@id=\"searchRoot\"]/div[1]/div[2]/div[5]/form/div[2]/div[1]/div/div").click();
                    Thread.sleep(3000);

                    //点击极速按钮
                    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("极速 快如闪电，直给答案")).click();

                    Thread.sleep(1000);

                    logInfo.sendTaskLog("已启动极速模式", userId, "秘塔");
                } else if (roles.contains("metaso-csk")) {
                    // 定位长思考按钮
                    Thread.sleep(1000);
                    page.locator("//*[@id=\"searchRoot\"]/div[1]/div[2]/div[5]/form/div[2]/div[1]/div/div").click();
                    Thread.sleep(3000);

                    //点击长思考按钮
                    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("长思考·R1 DeepSeek-R1-0528模型")).click();

                    Thread.sleep(1000);

                    logInfo.sendTaskLog("已启动长思考模式", userId, "秘塔");
                }

                Thread.sleep(1000);
                page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("请输入，Enter键发送，Shift+Enter键换行")).click();
                Thread.sleep(1000);
                page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("请输入，Enter键发送，Shift+Enter键换行")).fill(userPrompt);
                logInfo.sendTaskLog("用户指令已自动输入完成", userId, "秘塔");
                Thread.sleep(1000);
                page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("请输入，Enter键发送，Shift+Enter键换行")).press("Enter");
                logInfo.sendTaskLog("指令已自动发送成功", userId, "秘塔");
            }
            Thread.sleep(3000);
            //关闭搜索额度用尽弹窗
            if (page.getByText("今日搜索额度已用尽").isVisible()) {
                page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("明天再来")).click();
                return "今日搜索额度已用尽";
            }


            // 创建定时截图线程深度研究
            AtomicInteger i = new AtomicInteger(0);
            ScheduledExecutorService screenshotExecutor = Executors.newSingleThreadScheduledExecutor();
            // 启动定时任务，每5秒执行一次截图
            ScheduledFuture<?> screenshotFuture = screenshotExecutor.scheduleAtFixedRate(() -> {
                try {
                    int currentCount = i.getAndIncrement(); // 获取当前值并自增
                    logInfo.sendImgData(page, userId + "秘塔执行过程截图" + currentCount, userId);
                } catch (Exception e) {
                    UserLogUtil.sendExceptionLog(userId, "秘塔截图异常", "startMetaso", e, url + "/saveLogInfo");
                }
            }, 0, 8, TimeUnit.SECONDS);

            logInfo.sendTaskLog("开启自动监听任务，持续监听秘塔回答中", userId, "秘塔");
            //等待html片段获取完成
            String copiedText = metasoUtil.waitMetasoHtmlDom(page, userId, "秘塔");
            //关闭截图
            screenshotFuture.cancel(false);
            screenshotExecutor.shutdown();

            AtomicReference<String> shareUrlRef = new AtomicReference<>();

            clipboardLockManager.runWithClipboardLock(() -> {
                try {
                    // 点击分享链接按钮
                    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("分享")).click();
                    // 等待加载
                    Thread.sleep(1000);
                    // 点击复制链接
                    page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName("复制链接")).click();
                    // 建议适当延迟等待内容更新
                    Thread.sleep(1000);

                    String shareUrl = (String) page.evaluate("navigator.clipboard.readText()");
                    shareUrlRef.set(shareUrl);
                } catch (Exception e) {
                    UserLogUtil.sendExceptionLog(userId, "秘塔复制链接异常", "startMetaso", e, url + "/saveLogInfo");
                }
            });

            Thread.sleep(1000);
            String shareUrl = shareUrlRef.get();
            String sharImgUrl = "";
            // 点击分享按钮
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("分享")).click();
            Thread.sleep(1000);
            // 点击生成图片按钮
            sharImgUrl = ScreenshotUtil.downloadAndUploadFile(page, uploadUrl, () -> {
                page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName("生成图片")).click();
            });

            logInfo.sendTaskLog("执行完成", userId, "秘塔");
            logInfo.sendChatData(page, "/search/([^/?#]+)", userId, "RETURN_METASO_CHATID", 1);
            logInfo.sendResData(copiedText, userId, "秘塔", "RETURN_METASO_RES", shareUrl, sharImgUrl);

            //保存数据库
            userInfoRequest.setDraftContent(copiedText);
            userInfoRequest.setAiName("秘塔");
            userInfoRequest.setShareUrl(shareUrl);
            userInfoRequest.setShareImgUrl(sharImgUrl);
            RestUtils.post(url + "/saveDraftContent", userInfoRequest);
            return copiedText;
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * 处理豆包的常规请求
     *
     * @param userInfoRequest 包含会话ID和用户指令
     * @return AI生成的文本内容
     */
    @Operation(summary = "启动豆包AI生成", description = "调用豆包AI平台生成内容并抓取结果")
    @ApiResponse(responseCode = "200", description = "处理成功", content = @Content(mediaType = "application/json"))
    @PostMapping("/startDB")
    public String startDB(@RequestBody UserInfoRequest userInfoRequest) throws IOException, InterruptedException {
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false, userInfoRequest.getUserId(), "db")) {

            // 初始化变量
            String userId = userInfoRequest.getUserId();
            String dbchatId = userInfoRequest.getDbChatId();
            logInfo.sendTaskLog("豆包准备就绪，正在打开页面", userId, "豆包");
            String roles = userInfoRequest.getRoles();
            String userPrompt = userInfoRequest.getUserPrompt();

            // 初始化页面并导航到指定会话
            Page page = browserUtil.getOrCreatePage(context);
            if (dbchatId != null) {
                page.navigate("https://www.doubao.com/chat/" + dbchatId);
            } else {
                page.navigate("https://www.doubao.com/chat/");
            }

            page.waitForLoadState(LoadState.LOAD);
            Thread.sleep(500);
            logInfo.sendTaskLog("豆包页面打开完成", userId, "豆包");
            // 定位深度思考按钮
            Locator deepThoughtButton = page.locator("button.semi-button:has-text('深度思考')");
            // 检查按钮是否包含以 active- 开头的类名
            Boolean isActive = (Boolean) deepThoughtButton.evaluate("element => {\n" +
                    "    const classList = Array.from(element.classList);\n" +
                    "    return classList.some(cls => cls.startsWith('active-'));\n" +
                    "}");

            // 确保 isActive 不为 null
            if (isActive != null && !isActive && roles.contains("db-sdsk")) {
                deepThoughtButton.click();
                // 点击后等待一段时间，确保按钮状态更新
                Thread.sleep(1000);

                // 再次检查按钮状态
                isActive = (Boolean) deepThoughtButton.evaluate("element => {\n" +
                        "    const classList = Array.from(element.classList);\n" +
                        "    return classList.some(cls => cls.startsWith('active-'));\n" +
                        "}");
                if (isActive != null && !isActive) {
                    deepThoughtButton.click();
                    Thread.sleep(1000);
                }
                logInfo.sendTaskLog("已启动深度思考模式", userId, "豆包");
            }
            Thread.sleep(1000);
            page.locator("[data-testid='chat_input_input']").click();
            Thread.sleep(1000);
            page.locator("[data-testid='chat_input_input']").fill(userPrompt);
            logInfo.sendTaskLog("用户指令已自动输入完成", userId, "豆包");
            Thread.sleep(1000);
            page.locator("[data-testid='chat_input_input']").press("Enter");
            logInfo.sendTaskLog("指令已自动发送成功", userId, "豆包");

            // 创建定时截图线程
            AtomicInteger i = new AtomicInteger(0);
            ScheduledExecutorService screenshotExecutor = Executors.newSingleThreadScheduledExecutor();
            // 🔥 优化：启动定时任务，增加页面状态检查和错误处理
            ScheduledFuture<?> screenshotFuture = screenshotExecutor.scheduleAtFixedRate(() -> {
                try {
                    // 检查页面是否已关闭
                    if (page.isClosed()) {
                        return;
                    }

                    int currentCount = i.getAndIncrement();
                    logInfo.sendImgData(page, userId + "豆包执行过程截图" + currentCount, userId);
                } catch (com.microsoft.playwright.impl.TargetClosedError e) {
                } catch (com.microsoft.playwright.PlaywrightException e) {
                } catch (Exception e) {
                    // 只记录严重错误到日志系统
                    if (e.getMessage() != null && !e.getMessage().toLowerCase().contains("timeout")) {
                        UserLogUtil.sendExceptionLog(userId, "豆包截图", "startDB", e, url + "/saveLogInfo");
                    }
                }
            }, 1000, 6000, TimeUnit.MILLISECONDS); // 🔥 优化：延迟1秒开始，每6秒执行一次

            logInfo.sendTaskLog("开启自动监听任务，持续监听豆包回答中", userId, "豆包");
            // 等待复制按钮出现并点击
//            String copiedText =  douBaoUtil.waitAndClickDBCopyButton(page,userId,roles);
            //等待html片段获取完成
            String copiedText = douBaoUtil.waitDBHtmlDom(page, userId, "豆包");
            //关闭截图
            screenshotFuture.cancel(false);
            screenshotExecutor.shutdown();

            boolean isRight;

            Locator chatHis = page.locator("//*[@id=\"root\"]/div[1]/div/div[3]/div/main/div/div/div[2]/div/div[1]/div/div/div[2]/div[2]/div/div/div/div/div/div/div[1]/div/div/div[2]/div[1]/div/div");
            if (chatHis.count() > 0) {
                isRight = true;
            } else {
                isRight = false;
            }

            AtomicReference<String> shareUrlRef = new AtomicReference<>();

            clipboardLockManager.runWithClipboardLock(() -> {
                try {
                    if (isRight && page.locator("//*[@id=\"root\"]/div[1]/div/div[3]/aside/div[2]/div/div[1]/div/div[1]/div[3]/div/div/div/div[4]").count() > 0) {
                        page.locator("//*[@id=\"root\"]/div[1]/div/div[3]/aside/div[2]/div/div[1]/div/div[1]/div[3]/div/div/div/div[4]").click();
                        Thread.sleep(1000);
                        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("公开分享")).click();
                        Thread.sleep(500);
                    } else {
                        page.locator("button[data-testid='message_action_share']").last().click();
                        Thread.sleep(2000);
                        page.locator("button[data-testid='thread_share_copy_btn']").first().click();
                    }

                    // 建议适当延迟等待内容更新
                    Thread.sleep(2000);
                    String shareUrl = (String) page.evaluate("navigator.clipboard.readText()");
                    shareUrlRef.set(shareUrl);
                } catch (Exception e) {
                    UserLogUtil.sendExceptionLog(userId, "豆包复制", "startDB", e, url + "/saveLogInfo");
                }
            });

            Thread.sleep(1000);
            String shareUrl = shareUrlRef.get();
            String sharImgUrl = "";
            if (isRight && page.locator("//*[@id=\"root\"]/div[1]/div/div[3]/aside/div[2]/div/div[1]/div/div[1]/div[3]/div/div/div/div[3]").count() > 0) {
                page.locator("//*[@id=\"root\"]/div[1]/div/div[3]/aside/div[2]/div/div[1]/div/div[1]/div[3]/div/div/div/div[3]").click();
                sharImgUrl = ScreenshotUtil.downloadAndUploadFile(page, uploadUrl, () -> {
                    page.getByTestId("popover_select_option_item").nth(1).click();
                });
            } else {
                page.locator("button[data-testid='message_action_share']").last().click();
                Thread.sleep(2000);
                page.locator("//*[@id=\"root\"]/div[1]/div/div[3]/div/main/div/div/div[3]/div/div/div/div/div[1]/div/div/button").click();
                Thread.sleep(5000);
                sharImgUrl = ScreenshotUtil.downloadAndUploadFile(page, uploadUrl, () -> {
                    page.locator("button:has-text(\"下载图片\")").click();
                });
            }

            logInfo.sendTaskLog("执行完成", userId, "豆包");
            logInfo.sendChatData(page, "/chat/([^/?#]+)", userId, "RETURN_DB_CHATID", 1);
            logInfo.sendResData(copiedText, userId, "豆包", "RETURN_DB_RES", shareUrl, sharImgUrl);

            //保存数据库
            userInfoRequest.setDraftContent(copiedText);
            userInfoRequest.setAiName("豆包");
            userInfoRequest.setShareUrl(shareUrl);
            userInfoRequest.setShareImgUrl(sharImgUrl);
            RestUtils.post(url + "/saveDraftContent", userInfoRequest);
            return copiedText;
        } catch (Exception e) {
            throw e;
        }
    }


    /**
     * 处理Kimi的常规请求
     *
     * @param userInfoRequest 包含会话ID和用户指令
     * @return AI生成的文本内容
     */
    @Operation(summary = "启动KimiAI生成", description = "调用Kimi平台生成内容并抓取结果")
    @ApiResponse(responseCode = "200", description = "处理成功", content = @Content(mediaType = "application/json"))
    @PostMapping("/startKimi")
    public String startKimi(@RequestBody UserInfoRequest userInfoRequest) throws Exception {

        String userId = userInfoRequest.getUserId();
        String resText = "";
        String shareUrl = "";
        String sharImgUrl = "";
        ScheduledExecutorService screenshotExecutor = null;
        ScheduledFuture<?> screenshotFuture = null;

        try (
                BrowserContext context = browserUtil.createPersistentBrowserContext(false, userId, "Kimi");
                Page page = browserUtil.getOrCreatePage(context)) {


            // 初始化变量
            String kimiChatId = userInfoRequest.getKimiChatId();
            logInfo.sendTaskLog("kimi准备就绪，正在打开页面", userId, "Kimi");
            String roles = userInfoRequest.getRoles();
            String userPrompt = userInfoRequest.getUserPrompt();

            if (kimiChatId != null && !kimiChatId.isEmpty()) {
                page.navigate("https://www.kimi.com/chat/" + kimiChatId);
            } else page.navigate("https://www.kimi.com/");
            Thread.sleep(1000);
            page.locator("div.icon-button.toolkit-trigger-btn").click();

            // 是否打开联网模式
            if (roles.contains("kimi-lwss")) {
                // 如果当前是关闭状态,则点击打开
                if (!page.locator("input.switch-hidden").first().isChecked()) {
                    page.locator("div.switch-icon").first().click();
                    logInfo.sendTaskLog("已开启联网模式", userId, "Kimi");
                }
                // 如果已经是打开状态,无需操作
            } else {
                // 不打开联网模式
                if (page.locator("input.switch-hidden").first().isChecked()) {
                    page.locator("div.switch-icon").first().click();
                    logInfo.sendTaskLog("已关闭联网模式", userId, "Kimi");
                }
            }

            // 创建定时截图线程
            AtomicInteger i = new AtomicInteger(0);
            screenshotExecutor = Executors.newSingleThreadScheduledExecutor();
            // 启动定时任务，每8秒执行一次截图（优化超时检测）
            screenshotFuture = screenshotExecutor.scheduleAtFixedRate(() -> {
                try {
                    // 检查页面是否已关闭
                    if (page.isClosed()) {
                        return;
                    }
                    int currentCount = i.getAndIncrement(); // 获取当前值并自增
                    logInfo.sendImgData(page, userId + "kimi执行过程截图" + currentCount, userId);
                } catch (Exception e) {
                    UserLogUtil.sendExceptionLog(userId, "kimi截图", "startKimi", e, url + "/saveLogInfo");
                }
            }, 0, 8, TimeUnit.SECONDS);

            // 🔥 优化：增强超时检测和处理
            try {
                // 设置更长的超时时间用于等待响应
                page.setDefaultTimeout(120000); // 2分钟超时

                //存储回答内容文本
                resText = kimiUtil.waitKimiResponse(page, userId, userPrompt, kimiChatId);

            } catch (Exception e) {
                // 检查是否是超时异常
                if (e.getMessage() != null && e.getMessage().toLowerCase().contains("timeout")) {
                    resText = "Kimi响应超时，请稍后重试";
                    logInfo.sendTaskLog("Kimi响应超时，正在终止任务", userId, "Kimi");
                } else {
                    resText = "Kimi处理出现问题，请稍后重试";
                    logInfo.sendTaskLog("Kimi处理出现异常，正在终止任务", userId, "Kimi");
                }
                throw e;
            }

            //关闭截图
            screenshotFuture.cancel(true); // 使用true强制中断
            screenshotExecutor.shutdownNow(); // 立即关闭
            try {
                // 等待执行器完全关闭，最多等待2秒
                if (!screenshotExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Thread.sleep(500);
            //获取对话链接网址
            try {
                page.locator("span:has-text('复制链接')").click();
                Thread.sleep(1000);
                shareUrl = (String) page.evaluate("async () => { return await navigator.clipboard.readText(); }");
                //截取网址部分
                if (shareUrl != null && shareUrl.contains("https://")) {
                    shareUrl = shareUrl.substring(shareUrl.indexOf("https://"));
                }
            } catch (Exception e) {
                shareUrl = "";
                // 不发送技术错误到前端
            }

            // 获取图片
            try {
                sharImgUrl = ScreenshotUtil.downloadAndUploadFile(page, uploadUrl, () -> {
//                    下载图片，尝试五次
                    boolean isDownloadSuccess = false;
                    for (int j = 0; j < 5; j++) {
                        try {
                            page.locator("span:has-text('生成图片')").click();
                            page.locator("button.kimi-button.info.button").nth(1).click();
//                    等待10秒，检查是否下载成功
                            page.waitForSelector("text=下载图片成功",
                                    new Page.WaitForSelectorOptions().setTimeout(13000)
                            );
                            isDownloadSuccess = true;
                            logInfo.sendTaskLog("下载图片成功", userId, "Kimi");
                            break;
                        } catch (Exception e) {
                            try {
                                logInfo.sendTaskLog("下载图片失败,第" + (j + 1) + "次尝试下载", userId, "Kimi");
                                Thread.sleep(2000);
                                page.locator("//header[contains(text(),'分享图片预览')]//*[name()='svg']").click();
                            } catch (Exception ignored) {
                                System.out.println("窗口关闭失败");
                            }
                        }
                    }
                    if (!isDownloadSuccess) {
                        logInfo.sendTaskLog("下载图片失败", userId, "Kimi");
                    }
                });
            } catch (Exception e) {
                sharImgUrl = "";
                // 不发送技术错误到前端
            }

            logInfo.sendTaskLog("执行完成", userId, "Kimi");
            logInfo.sendChatData(page, "/chat/([^/?#]+)", userId, "RETURN_KIMI_CHATID", 1);

            logInfo.sendResData(resText, userId, "kimi", "RETURN_KIMI_RES", shareUrl, sharImgUrl);

            //保存数据库
            userInfoRequest.setDraftContent(resText);
            userInfoRequest.setAiName("Kimi");
            userInfoRequest.setShareUrl(shareUrl);
            userInfoRequest.setShareImgUrl(sharImgUrl);
            RestUtils.post(url + "/saveDraftContent", userInfoRequest);
            return resText;
        } catch (InterruptedException e) {
            throw e;
        } finally {
            // 确保资源清理
            try {
                if (screenshotFuture != null) {
                    screenshotFuture.cancel(true);
                }
                if (screenshotExecutor != null) {
                    screenshotExecutor.shutdownNow();
                }
            } catch (Exception e) {
                UserLogUtil.sendExceptionLog(userId, "Kimi截图", "startKimi", e, url + "/saveLogInfo");
            }
        }
    }


    @Operation(summary = "豆包智能评分", description = "调用豆包平台对内容进行评分并返回评分结果")
    @ApiResponse(responseCode = "200", description = "处理成功", content = @Content(mediaType = "application/json"))
    @PostMapping("/startDBScore")
    public String startDBScore(@RequestBody UserInfoRequest userInfoRequest) throws IOException, InterruptedException {
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false, userInfoRequest.getUserId(), "db")) {

            // 初始化变量
            String userId = userInfoRequest.getUserId();
            logInfo.sendTaskLog("评分准备就绪，正在打开页面", userId, "智能评分");
            String roles = userInfoRequest.getRoles();
            String userPrompt = userInfoRequest.getUserPrompt();

            // 初始化页面并导航到指定会话
            Page page = browserUtil.getOrCreatePage(context);
            page.navigate("https://www.doubao.com/chat/");
            page.waitForLoadState(LoadState.LOAD);
            Thread.sleep(500);
            logInfo.sendTaskLog("智能评分页面打开完成", userId, "智能评分");
            // 定位深度思考按钮
            Locator deepThoughtButton = page.locator("button.semi-button:has-text('深度思考')");
            // 检查按钮是否包含以 active- 开头的类名
            Boolean isActive = (Boolean) deepThoughtButton.evaluate("element => {\n" +
                    "    const classList = Array.from(element.classList);\n" +
                    "    return classList.some(cls => cls.startsWith('active-'));\n" +
                    "}");

            // 确保 isActive 不为 null
            if (isActive != null && !isActive && roles.contains("db-sdsk")) {
                deepThoughtButton.click();
                // 点击后等待一段时间，确保按钮状态更新
                Thread.sleep(1000);

                // 再次检查按钮状态
                isActive = (Boolean) deepThoughtButton.evaluate("element => {\n" +
                        "    const classList = Array.from(element.classList);\n" +
                        "    return classList.some(cls => cls.startsWith('active-'));\n" +
                        "}");
                if (isActive != null && !isActive) {
                    deepThoughtButton.click();
                    Thread.sleep(1000);
                }
            }
            Thread.sleep(500);
            page.locator("[data-testid='chat_input_input']").click();
            Thread.sleep(500);
            page.locator("[data-testid='chat_input_input']").fill(userPrompt);
            logInfo.sendTaskLog("初稿已录入评分系统完成", userId, "智能评分");
            Thread.sleep(500);
            page.locator("[data-testid='chat_input_input']").press("Enter");

            // 创建定时截图线程
            AtomicInteger i = new AtomicInteger(0);
            ScheduledExecutorService screenshotExecutor = Executors.newSingleThreadScheduledExecutor();
            // 启动定时任务，每5秒执行一次截图
            ScheduledFuture<?> screenshotFuture = screenshotExecutor.scheduleAtFixedRate(() -> {
                try {
                    int currentCount = i.getAndIncrement(); // 获取当前值并自增
                    logInfo.sendImgData(page, userId + "评分执行过程截图" + currentCount, userId);
                } catch (Exception e) {
                    UserLogUtil.sendExceptionLog(userId, "豆包评分截图", "startDBScore", e, url + "/saveLogInfo");
                }
            }, 0, 9, TimeUnit.SECONDS);

            logInfo.sendTaskLog("开启自动监听任务，持续监听评分结果", userId, "智能评分");
            // 等待复制按钮出现并点击
            String copiedText = douBaoUtil.waitDBHtmlDom(page, userId, "智能评分");

            //关闭截图
            screenshotFuture.cancel(false);
            screenshotExecutor.shutdown();
            boolean isRight;
            Locator chatHis = page.locator("//*[@id=\"root\"]/div[1]/div/div[3]/div/main/div/div/div[2]/div/div[1]/div/div/div[2]/div[2]/div/div/div/div/div/div/div[1]/div/div/div[2]/div[1]/div/div");
            if (chatHis.count() > 0) {
                isRight = true;
            } else {
                isRight = false;
            }
            //关闭截图
            screenshotFuture.cancel(false);
            screenshotExecutor.shutdown();

            AtomicReference<String> shareUrlRef = new AtomicReference<>();
            clipboardLockManager.runWithClipboardLock(() -> {
                try {
                    if (isRight && page.locator("//*[@id=\"root\"]/div[1]/div/div[3]/aside/div[2]/div/div[1]/div/div[1]/div[3]/div/div/div/div[4]").count() > 0) {
                        page.locator("//*[@id=\"root\"]/div[1]/div/div[3]/aside/div[2]/div/div[1]/div/div[1]/div[3]/div/div/div/div[4]").click();
                        Thread.sleep(1000);
                        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("公开分享")).click();
                        Thread.sleep(500);
                    } else {
                        page.locator("button[data-testid='message_action_share']").last().click();
                        Thread.sleep(2000);
                        page.locator("button[data-testid='thread_share_copy_btn']").first().click();
                    }

                    // 建议适当延迟等待内容更新
                    Thread.sleep(2000);
                    String shareUrl = (String) page.evaluate("navigator.clipboard.readText()");
                    shareUrlRef.set(shareUrl);
                } catch (Exception e) {
                    UserLogUtil.sendExceptionLog(userId, "豆包评分内容复制", "startDBScore", e, url + "/saveLogInfo");
                }
            });

            Thread.sleep(1000);
            String shareUrl = shareUrlRef.get();
            String sharImgUrl = "";
            if (isRight && page.locator("//*[@id=\"root\"]/div[1]/div/div[3]/aside/div[2]/div/div[1]/div/div[1]/div[3]/div/div/div/div[3]").count() > 0) {
                page.locator("//*[@id=\"root\"]/div[1]/div/div[3]/aside/div[2]/div/div[1]/div/div[1]/div[3]/div/div/div/div[3]").click();
                sharImgUrl = ScreenshotUtil.downloadAndUploadFile(page, uploadUrl, () -> {
                    page.getByTestId("popover_select_option_item").nth(1).click();
                });
            } else {
                page.locator("button[data-testid='message_action_share']").last().click();
                Thread.sleep(2000);
                page.locator("//*[@id=\"root\"]/div[1]/div/div[3]/div/main/div/div/div[3]/div/div/div/div/div[1]/div/div/button").click();
                Thread.sleep(5000);
                sharImgUrl = ScreenshotUtil.downloadAndUploadFile(page, uploadUrl, () -> {
                    page.locator("button:has-text(\"下载图片\")").click();
                });
            }

            logInfo.sendTaskLog("执行完成", userId, "智能评分");
            logInfo.sendResData(copiedText, userId, "智能评分", "RETURN_WKPF_RES", shareUrl, sharImgUrl);
            userInfoRequest.setDraftContent(copiedText);
            userInfoRequest.setAiName("智能评分");
            userInfoRequest.setShareUrl(shareUrl);
            userInfoRequest.setShareImgUrl(sharImgUrl);
            RestUtils.post(url + "/saveDraftContent", userInfoRequest);
            return copiedText;
        } catch (Exception e) {
            throw e;
        }
    }


    @Operation(summary = "投递公众号排版", description = "调用豆包平台对内容进行评分并返回评分结果")
    @ApiResponse(responseCode = "200", description = "处理成功", content = @Content(mediaType = "application/json"))
    @PostMapping("/startDBOffice")
    public String startDBOffice(@RequestBody UserInfoRequest userInfoRequest) throws InterruptedException {
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false, userInfoRequest.getUserId(), "db")) {
            // 初始化变量
            String userId = userInfoRequest.getUserId();
            logInfo.sendTaskLog("智能排版准备就绪，正在打开页面", userId, "智能排版");
            String roles = userInfoRequest.getRoles();
            String userPrompt = userInfoRequest.getUserPrompt();

            // 初始化页面并导航到指定会话
            Page page = browserUtil.getOrCreatePage(context);
            page.navigate("https://www.doubao.com/chat/");
            page.waitForLoadState(LoadState.LOAD);
            Thread.sleep(500);
            logInfo.sendTaskLog("智能排版页面打开完成", userId, "智能排版");
            // 定位深度思考按钮
            Locator deepThoughtButton = page.locator("button.semi-button:has-text('深度思考')");
            // 检查按钮是否包含以 active- 开头的类名
            Boolean isActive = (Boolean) deepThoughtButton.evaluate("element => {\n" +
                    "    const classList = Array.from(element.classList);\n" +
                    "    return classList.some(cls => cls.startsWith('active-'));\n" +
                    "}");

            // 确保 isActive 不为 null
            if (isActive != null && !isActive && roles.contains("db-sdsk")) {
                deepThoughtButton.click();
                // 点击后等待一段时间，确保按钮状态更新
                Thread.sleep(1000);

                // 再次检查按钮状态
                isActive = (Boolean) deepThoughtButton.evaluate("element => {\n" +
                        "    const classList = Array.from(element.classList);\n" +
                        "    return classList.some(cls => cls.startsWith('active-'));\n" +
                        "}");
                if (isActive != null && !isActive) {
                    deepThoughtButton.click();
                    Thread.sleep(1000);
                }
            }
            Thread.sleep(500);
            page.locator("[data-testid='chat_input_input']").click();
            Thread.sleep(500);
            page.locator("[data-testid='chat_input_input']").fill(userPrompt);
            logInfo.sendTaskLog("原数据已录入智能排版系统完成", userId, "智能排版");
            Thread.sleep(500);
            page.locator("[data-testid='chat_input_input']").press("Enter");

            // 创建定时截图线程
            AtomicInteger i = new AtomicInteger(0);
            ScheduledExecutorService screenshotExecutor = Executors.newSingleThreadScheduledExecutor();
            // 启动定时任务，每5秒执行一次截图
            ScheduledFuture<?> screenshotFuture = screenshotExecutor.scheduleAtFixedRate(() -> {
                try {
                    int currentCount = i.getAndIncrement(); // 获取当前值并自增
                    logInfo.sendImgData(page, userId + "智能排版执行过程截图" + currentCount, userId);
                } catch (Exception e) {
                    UserLogUtil.sendExceptionLog(userId, "智能排版执行过程截图", "startDBOffice", e, url + "/saveLogInfo");
                }
            }, 0, 9, TimeUnit.SECONDS);

            logInfo.sendTaskLog("开启自动监听任务，持续监听智能排版结果", userId, "智能排版");
            // 等待复制按钮出现并点击
            String copiedText = douBaoUtil.waitPBCopy(page, userId, "智能排版");

            //关闭截图
            screenshotFuture.cancel(false);
            screenshotExecutor.shutdown();

            logInfo.sendTaskLog("执行完成", userId, "智能排版");
            logInfo.sendResData(copiedText, userId, "智能排版", "RETURN_ZNPB_RES", "", "");
            userInfoRequest.setDraftContent(copiedText);
            userInfoRequest.setAiName("智能评分");
            userInfoRequest.setShareUrl("");
            userInfoRequest.setShareImgUrl("");
            RestUtils.post(url + "/saveDraftContent", userInfoRequest);
            return copiedText;
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * 处理DeepSeek的常规请求
     *
     * @param userInfoRequest 包含会话ID和用户指令
     * @return AI生成的文本内容
     */
    @Operation(summary = "启动DeepSeek AI生成", description = "调用DeepSeek AI平台生成内容并抓取结果")
    @ApiResponse(responseCode = "200", description = "处理成功", content = @Content(mediaType = "application/json"))
    @PostMapping("/startDeepSeek")
    public String startDeepSeek(@RequestBody UserInfoRequest userInfoRequest) throws InterruptedException {

        String userId = userInfoRequest.getUserId();
        String chatId = userInfoRequest.getDbChatId();
        String userPrompt = userInfoRequest.getUserPrompt();
        String isNewChat = userInfoRequest.getIsNewChat();
        String roles = userInfoRequest.getRoles();


        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false, userId, "deepseek")) {
            if ("true".equalsIgnoreCase(isNewChat)) {
                chatId = null;
            } else if (chatId != null && !chatId.isEmpty()) {
                logInfo.sendTaskLog("检测到会话ID: " + chatId + "，将继续使用此会话", userId, "DeepSeek");
            }

            // 初始化页面并发送消息
            Page page = browserUtil.getOrCreatePage(context);

            // 🔥 优化：设置更合理的超时时间，提高响应速度
            page.setDefaultTimeout(90000); // 90秒（增加到90秒以减少超时错误）

            // 创建定时截图线程
            AtomicInteger i = new AtomicInteger(0);
            ScheduledExecutorService screenshotExecutor = Executors.newSingleThreadScheduledExecutor();

            // 启动定时任务，每6秒执行一次截图，添加错误处理和状态检查
            ScheduledFuture<?> screenshotFuture = screenshotExecutor.scheduleAtFixedRate(() -> {
                try {
                    // 检查页面是否已关闭，避免对已关闭页面进行操作
                    if (page.isClosed()) {
                        return;
                    }

                    // 🔥 优化：移除页面加载检查，减少不必要的延迟
                    int currentCount = i.getAndIncrement();
                    try {
                        // 使用更安全的截图方式
                        logInfo.sendImgData(page, userId + "DeepSeek执行过程截图" + currentCount, userId);
                    } catch (Exception e) {
                        UserLogUtil.sendExceptionLog(userId, "DeepSeek执行过程截图", "startDeepSeek", e, url + "/saveLogInfo");
                    }
                } catch (Exception e) {
                    UserLogUtil.sendExceptionLog(userId, "DeepSeek执行过程截图", "startDeepSeek", e, url + "/saveLogInfo");
                }
            }, 1000, 4000, TimeUnit.MILLISECONDS); // 🔥 优化：延迟1秒开始，每4秒执行一次（提高截图频率）

            logInfo.sendTaskLog("开启自动监听任务，持续监听DeepSeek回答中", userId, "DeepSeek");

            // 发送消息并获取回答
            String copiedText = "";
            int maxRetries = 3;

            // 重试循环
            for (int retry = 0; retry < maxRetries; retry++) {
                try {
                    if (retry > 0) {
                        // 刷新页面重新开始
                        page.reload();
                        page.waitForLoadState(LoadState.LOAD);
                        Thread.sleep(2000);
                    }

                    // 🔥 新增：检测DeepSeek服务器不可用弹窗
                    try {
                        // 检查是否有服务器不可用的弹窗或错误信息
                        String serverUnavailableCheck = (String) page.evaluate("""
                                    () => {
                                        // 检查常见的服务器不可用提示
                                        const errorMessages = [
                                            '服务器暂时不可用',
                                            '服务暂时不可用', 
                                            'Service temporarily unavailable',
                                            'Server temporarily unavailable',
                                            '系统繁忙',
                                            '服务异常',
                                            '网络异常'
                                        ];
                                        
                                        // 检查页面中是否包含这些错误信息
                                        const bodyText = document.body.innerText || document.body.textContent || '';
                                        for (const message of errorMessages) {
                                            if (bodyText.includes(message)) {
                                                return message;
                                            }
                                        }
                                        
                                        // 检查弹窗或模态框
                                        const modals = document.querySelectorAll('.modal, .dialog, .popup, .alert, [role="dialog"], [role="alert"]');
                                        for (const modal of modals) {
                                            const modalText = modal.innerText || modal.textContent || '';
                                            for (const message of errorMessages) {
                                                if (modalText.includes(message)) {
                                                    return message;
                                                }
                                            }
                                        }
                                        
                                        return null;
                                    }
                                """);

                        if (serverUnavailableCheck != null && !serverUnavailableCheck.equals("null")) {

                            // 安全地关闭截图任务
                            try {
                                screenshotFuture.cancel(true);
                                screenshotExecutor.shutdownNow();
                            } catch (Exception e) {
                            }

                            // 直接返回错误信息给前端
                            String errorMessage = "DeepSeek服务器暂时不可用，请稍后再试";
                            logInfo.sendTaskLog(errorMessage, userId, "DeepSeek");
                            logInfo.sendResData(errorMessage, userId, "DeepSeek", "RETURN_DEEPSEEK_RES", "", "");

                            // 保存错误信息到数据库
                            userInfoRequest.setDraftContent(errorMessage);
                            userInfoRequest.setAiName("DeepSeek");
                            userInfoRequest.setShareUrl("");
                            userInfoRequest.setShareImgUrl("");
                            RestUtils.post(url + "/saveDraftContent", userInfoRequest);

                            return errorMessage;
                        }
                    } catch (Exception e) {
                        // 继续执行正常流程
                    }

                    copiedText = deepSeekUtil.handleDeepSeekAI(page, userPrompt, userId, roles, chatId);

                    if (!copiedText.startsWith("获取内容失败") && !copiedText.isEmpty()) {
                        break; // 成功获取内容，跳出重试循环
                    }

                    Thread.sleep(3000); // 等待3秒后重试
                } catch (Exception e) {
                    if (retry == maxRetries - 1) {
                        copiedText = "获取内容失败：多次尝试后仍然失败";
                        // 不发送技术错误到前端，只记录日志
                    }
                    Thread.sleep(2000); // 出错后等待2秒
                }
            }

            // 安全地关闭截图任务
            try {
                screenshotFuture.cancel(true); // 使用true尝试中断正在执行的任务
                screenshotExecutor.shutdownNow(); // 立即关闭执行器

                // 等待执行器完全关闭，但最多等待3秒
                if (!screenshotExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                    // 截图任务未能完全关闭
                }
            } catch (Exception e) {
                // 关闭截图任务时出错，不发送到前端
            }

            // 如果获取内容失败，尝试从页面中提取任何可能的内容
            if (copiedText.startsWith("获取内容失败") || copiedText.isEmpty()) {
                try {

                    // 使用JavaScript提取页面上的任何文本内容
                    Object extractedContent = page.evaluate("""
                                () => {
                                    // 尝试查找任何可能包含回复的元素
                                    const contentElements = document.querySelectorAll('.ds-markdown, .flow-markdown-body, .message-content, .ds-markdown-paragraph');
                                    if (contentElements.length > 0) {
                                        // 获取最后一个元素的文本
                                        const lastElement = contentElements[contentElements.length - 1];
                                        return lastElement.innerHTML || lastElement.innerText || '';
                                    }

                                    // 如果找不到特定元素，尝试获取页面上的任何文本
                                    const bodyText = document.body.innerText;
                                    if (bodyText && bodyText.length > 50) {
                                        return bodyText;
                                    }

                                    return '无法提取内容';
                                }
                            """);

                    if (extractedContent != null && !extractedContent.toString().isEmpty() &&
                            !extractedContent.toString().equals("无法提取内容")) {
                        copiedText = extractedContent.toString();
                    }
                } catch (Exception e) {
                    // 不发送技术错误到前端
                }
            }

            // 🔥 优化：获取分享链接，增加超时保护
            String shareUrl = "";
            try {
                // 设置较短的超时时间用于分享操作
                page.locator("button:has-text('分享')").click(new Locator.ClickOptions().setTimeout(30000));
                Thread.sleep(1500); // 稍微增加等待时间
                shareUrl = (String) page.evaluate("navigator.clipboard.readText()");
                if (shareUrl != null && !shareUrl.trim().isEmpty()) {
                } else {
                    shareUrl = page.url();
                }
            } catch (Exception e) {
                // 使用当前页面URL作为备选
                try {
                    shareUrl = page.url();
                } catch (Exception ex) {
                    shareUrl = "";
                }
            }

            // DeepSeek不支持图片生成功能，跳过图片处理
            String shareImgUrl = "";

            logInfo.sendTaskLog("执行完成", userId, "DeepSeek");
            logInfo.sendChatData(page, "/chat/([^/?#]+)", userId, "RETURN_DEEPSEEK_CHATID", 1);

            logInfo.sendResData(copiedText, userId, "DeepSeek", "RETURN_DEEPSEEK_RES", shareUrl, shareImgUrl);

            // 保存数据库
            userInfoRequest.setDraftContent(copiedText);
            userInfoRequest.setAiName("DeepSeek");
            userInfoRequest.setShareUrl(shareUrl);
            userInfoRequest.setShareImgUrl(shareImgUrl);
            RestUtils.post(url + "/saveDraftContent", userInfoRequest);

            return copiedText;

        } catch (Exception e) {

            // 发送用户友好的错误信息，不暴露技术细节
            String userFriendlyError = "DeepSeek处理出现问题，请稍后重试";
            logInfo.sendTaskLog(userFriendlyError, userId, "DeepSeek");
            logInfo.sendResData(userFriendlyError, userId, "DeepSeek", "RETURN_DEEPSEEK_RES", "", "");

            return userFriendlyError;
        }
    }

    /**
     * 处理通义千问的常规请求
     *
     * @param userInfoRequest 包含会话ID和用户指令
     * @return 格式化后的AI生成的文本内容
     */
    @Operation(summary = "启动通义千问生成", description = "调用通义千问平台生成内容并抓取结果，最后进行统一格式化")
    @ApiResponse(responseCode = "200", description = "处理成功", content = @Content(mediaType = "application/json"))
    @PostMapping("/startTYQianwen")
    public String startTYQianwen(@RequestBody UserInfoRequest userInfoRequest) throws Exception {
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false, userInfoRequest.getUserId(), "ty")) {

            String userId = userInfoRequest.getUserId();
            String sessionId = userInfoRequest.getTyChatId();
            String isNewChat = userInfoRequest.getIsNewChat();
            String aiName = "通义千问";

            logInfo.sendTaskLog(aiName + "准备就绪，正在打开页面", userId, aiName);

            Page page = browserUtil.getOrCreatePage(context);

            if ("true".equalsIgnoreCase(isNewChat) || sessionId == null || sessionId.isEmpty()) {
                logInfo.sendTaskLog("用户请求新会话", userId, aiName);
                page.navigate("https://www.tongyi.com/qianwen");
            } else {
                logInfo.sendTaskLog("检测到会话ID: " + sessionId + "，将继续使用此会话", userId, aiName);
                page.navigate("https://www.tongyi.com/qianwen?sessionId=" + sessionId);
            }

            page.waitForLoadState(LoadState.LOAD);
            logInfo.sendTaskLog(aiName + "页面打开完成", userId, aiName);

            // 创建定时截图线程
            AtomicInteger i = new AtomicInteger(0);
            ScheduledExecutorService screenshotExecutor = Executors.newSingleThreadScheduledExecutor();
            ScheduledFuture<?> screenshotFuture = screenshotExecutor.scheduleAtFixedRate(() -> {
                try {
                    int currentCount = i.getAndIncrement();
                    logInfo.sendImgData(page, userId + aiName + "执行过程截图" + currentCount, userId);
                } catch (Exception e) {
                    UserLogUtil.sendExceptionLog(userId, "通义千问截图", "startTYQianwen", e, url + "/saveLogInfo");
                }
            }, 0, 8, TimeUnit.SECONDS);

            Map<String, String> qianwenResult = tongYiUtil.processQianwenRequest(page, userInfoRequest);
            String rawHtmlContent = qianwenResult.get("rawHtmlContent");
            String capturedSessionId = qianwenResult.get("sessionId");

            // 关闭截图线程
            screenshotFuture.cancel(false);
            screenshotExecutor.shutdown();

            AtomicReference<String> shareUrlRef = new AtomicReference<>();
            String formattedContent = rawHtmlContent;

            page.locator("div[class*='btn--YtZqkWMA']:not([class*='reloadBtn--'])").last().click();
            page.waitForTimeout(1000);

            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("分享")).click();
            page.waitForTimeout(1000);

            Locator outputLocator = page.locator(".tongyi-markdown").last();
            String lastContent = outputLocator.innerHTML();
            qianwenResult.put("rawHtmlContent", lastContent);
            rawHtmlContent = lastContent;

            // 获取干净回答并封装
            try {
                if (!rawHtmlContent.startsWith("获取内容失败") && !rawHtmlContent.isEmpty()) {
                    Object finalFormattedContent = page.evaluate("""
                            (content) => {
                                try {
                                    const ALLOWED_TAGS = new Set([
                                        'p', 'br', 'strong', 'em', 'b', 'i', 'ul', 'ol', 'li', 'h3', 'hr',
                                        'table', 'thead', 'tbody', 'tr', 'th', 'td',
                                        'pre', 'code'
                                    ]);
                                    const tempDiv = document.createElement('div');
                                    tempDiv.innerHTML = content;
                                    tempDiv.querySelectorAll('.tongyi-design-highlighter').forEach(highlighter => {
                                        const codeElement = highlighter.querySelector('code');
                                        if (!codeElement) return;
                                        const clonedCode = codeElement.cloneNode(true);
                                        clonedCode.querySelectorAll('.react-syntax-highlighter-line-number').forEach(el => el.remove());
                                        const cleanText = clonedCode.innerText;
                                        const newPreElement = document.createElement('pre');
                                        const newCodeElement = document.createElement('code');
                                        newCodeElement.textContent = cleanText;
                                        newPreElement.appendChild(newCodeElement);
                                        highlighter.parentNode.replaceChild(newPreElement, highlighter);
                                    });
                                    const allElements = tempDiv.querySelectorAll('*');
                                    for (let i = allElements.length - 1; i >= 0; i--) {
                                        const el = allElements[i];
                                        if (!ALLOWED_TAGS.has(el.tagName.toLowerCase())) {
                                            el.replaceWith(...el.childNodes);
                                        } else {
                                            while (el.attributes.length > 0) {
                                                el.removeAttribute(el.attributes[0].name);
                                            }
                                        }
                                    }
                                    const sanitizedHtml = tempDiv.innerHTML;
                                    const styledContainer = document.createElement('div');
                                    styledContainer.className = 'tongyi-response';
                                    styledContainer.style.cssText = 'max-width: 800px; margin: 0 auto; background-color: #fff; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); padding: 20px; font-family: Arial, sans-serif; line-height: 1.6; color: #333;';
                                    const customStyles = document.createElement('style');
                                    customStyles.textContent = `
                                        .tongyi-response table { border-collapse: collapse; width: 100%; margin: 1em 0; border: 1px solid #ddd; }
                                        .tongyi-response th, .tongyi-response td { border: 1px solid #ddd; padding: 8px; text-align: left; }
                                        .tongyi-response th { background-color: #f2f2f2; }
                                        .tongyi-response pre { background-color: #f5f5f5; padding: 10px; border-radius: 4px; white-space: pre-wrap; word-wrap: break-word; font-size: 14px;}
                                        .tongyi-response code { font-family: 'Courier New', Courier, monospace; }
                                        .tongyi-response h3 { margin-top: 1.5em; margin-bottom: 0.5em; }
                                        .tongyi-response hr { border: 0; border-top: 1px solid #eee; margin: 1.5em 0; }
                                    `;
                                    styledContainer.appendChild(customStyles);
                                    const contentWrapper = document.createElement('div');
                                    contentWrapper.innerHTML = sanitizedHtml;
                                    styledContainer.appendChild(contentWrapper);
                                    return styledContainer.outerHTML;
                                } catch (e) {
                                    console.error('HTML净化和格式化过程中出错:', e);
                                    return `<div>格式化失败: ${e.message}</div>`;
                                }
                            }
                            """, rawHtmlContent);

                    if (finalFormattedContent != null && !finalFormattedContent.toString().isEmpty()) {
                        formattedContent = finalFormattedContent.toString();
                        logInfo.sendTaskLog("已将回答内容提取为纯文本段落并封装", userId, aiName);
                    }
                }
            } catch (Exception e) {
                logInfo.sendTaskLog("内容格式化处理失败: " + e.getMessage(), userId, aiName);
                UserLogUtil.sendExceptionLog(userId, "通义千问内容格式化", "startTYQianwen", e, url + "/saveLogInfo");
            }

            // 获取分享链接
            clipboardLockManager.runWithClipboardLock(() -> {
                try {
                    logInfo.sendTaskLog("正在获取分享链接...", userId, aiName);

                    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("复制链接")).click();
                    page.waitForTimeout(500);

                    String shareUrl = (String) page.evaluate("navigator.clipboard.readText()");
                    shareUrlRef.set(shareUrl);
                    logInfo.sendTaskLog("成功获取分享链接: " + shareUrl, userId, aiName);
                } catch (Exception e) {
                    logInfo.sendTaskLog("获取分享链接失败", userId, aiName);
                    UserLogUtil.sendExceptionLog(userId, "通义千问获取分型链接", "startTYQianwen", e, url + "/saveLogInfo");
                }
            });

            String shareUrl = shareUrlRef.get();
            String sharImgUrl = "";

            logInfo.sendTaskLog("执行完成", userId, aiName);

            // 回传数据
            logInfo.sendChatData(page, "sessionId=([^&?#]+)", userId, "RETURN_TY_CHATID", 1);
            logInfo.sendResData(formattedContent, userId, aiName, "RETURN_TY_RES", shareUrl, sharImgUrl);

            // 保存数据库
            userInfoRequest.setTyChatId(capturedSessionId);
            userInfoRequest.setDraftContent(formattedContent);
            userInfoRequest.setAiName(aiName);
            userInfoRequest.setShareUrl(shareUrl);
            userInfoRequest.setShareImgUrl(sharImgUrl);
            RestUtils.post(url + "/saveDraftContent", userInfoRequest);

            return formattedContent;

        } catch (Exception e) {
            logInfo.sendTaskLog("执行通义千问任务时发生严重错误", userInfoRequest.getUserId(), "通义千问");
            throw e;
        }
    }

    /**
     * 启动知乎直答常规请求
     *
     * @param userInfoRequest 包含会话ID和用户指令
     * @return 格式化后的AI生成的文本内容
     */
    @Operation(summary = "启动知乎直答生成", description = "调用知乎直答平台生成内容并抓取结果，最后进行统一格式化")
    @ApiResponse(responseCode = "200", description = "处理成功", content = @Content(mediaType = "application/json"))
    @PostMapping("/startZHZD")
    public String startZHZD(@RequestBody UserInfoRequest userInfoRequest) throws Exception {

        String userId = userInfoRequest.getUserId();
        String sessionId = userInfoRequest.getZhzdChatId();
        String userPrompt = userInfoRequest.getUserPrompt();
        String isNewChat = userInfoRequest.getIsNewChat();
        String aiName = "知乎直答";


        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false, userId, "Zhihu")) {
            logInfo.sendTaskLog(aiName + "准备就绪，正在打开页面", userId, aiName);

            Page page = browserUtil.getOrCreatePage(context);

            // 🔥 新增：检测知乎访问限制
            try {
                if ("true".equalsIgnoreCase(isNewChat) || sessionId == null || sessionId.isEmpty()) {
                    logInfo.sendTaskLog("用户请求新会话", userId, aiName);
                    page.navigate("https://zhida.zhihu.com");
                } else {
                    logInfo.sendTaskLog("检测到会话ID: " + sessionId + "，将继续使用此会话", userId, aiName);
                    page.navigate("https://zhida.zhihu.com/search/" + sessionId);
                }

                page.waitForLoadState(LoadState.LOAD);
                Thread.sleep(2000);

                // 检测知乎访问限制
                String accessCheckResult = (String) page.evaluate("""
                            () => {
                                const bodyText = document.body.innerText || document.body.textContent || '';
                                const pageTitle = document.title || '';
                                
                                // 检查常见的访问限制提示
                                const restrictionMessages = [
                                    '您当前请求存在异常，暂时限制本次访问',
                                    '暂时限制本次访问',
                                    '请求存在异常',
                                    '访问受限',
                                    '您的访问出现了异常',
                                    'b87ce5c3c1b4773c6a37cf0ae84ccfb1'
                                ];
                                
                                for (const message of restrictionMessages) {
                                    if (bodyText.includes(message) || pageTitle.includes(message)) {
                                        return message;
                                    }
                                }
                                
                                // 检查是否有错误码
                                if (bodyText.includes('40362') || bodyText.includes('error')) {
                                    return 'access_restricted';
                                }
                                
                                return null;
                            }
                        """);

                if (accessCheckResult != null && !accessCheckResult.equals("null")) {

                    // 直接返回错误信息给前端
                    String errorMessage = "知乎访问受限，请稍后再试或通过手机摇一摇联系知乎小管家";
                    logInfo.sendTaskLog(errorMessage, userId, aiName);
                    logInfo.sendResData(errorMessage, userId, aiName, "RETURN_ZHZD_RES", "", "");

                    // 保存错误信息到数据库
                    userInfoRequest.setZhzdChatId(sessionId);
                    userInfoRequest.setDraftContent(errorMessage);
                    userInfoRequest.setAiName(aiName);
                    userInfoRequest.setShareUrl("");
                    userInfoRequest.setShareImgUrl("");
                    RestUtils.post(url + "/saveDraftContent", userInfoRequest);

                    return errorMessage;
                }

            } catch (Exception e) {
                // 继续执行正常流程
            }
            logInfo.sendTaskLog(aiName + "页面打开完成", userId, aiName);

            // 创建定时截图线程
            AtomicInteger i = new AtomicInteger(0);
            ScheduledExecutorService screenshotExecutor = Executors.newSingleThreadScheduledExecutor();
            ScheduledFuture<?> screenshotFuture = screenshotExecutor.scheduleAtFixedRate(() -> {
                try {
                    // 检查页面是否已关闭
                    if (page.isClosed()) {
                        return;
                    }
                    int currentCount = i.getAndIncrement();
                    logInfo.sendImgData(page, userId + aiName + "执行过程截图" + currentCount, userId);
                } catch (Exception e) {
                    // 不发送技术错误到前端
                }
            }, 0, 8, TimeUnit.SECONDS);

            String rawHtmlContent = zhzdUtil.processZHZDRequest(page, userInfoRequest);

            // 获取sessionId
            String currentUrl = page.url();
            String[] currentUrlSplit = currentUrl.split("/");
            sessionId = currentUrlSplit[currentUrlSplit.length - 1];

            // 关闭截图线程
            screenshotFuture.cancel(false);
            screenshotExecutor.shutdown();

            String formattedContent = rawHtmlContent;
            String shareUrl = "";
            String shareImgUrl = "";

            // 格式化内容
            try {
                if (!rawHtmlContent.startsWith("获取内容失败") && !rawHtmlContent.isEmpty()) {
                    Object finalFormattedContent = page.evaluate("""
                            (content) => {
                                try {
                                    // 创建一个包装容器来处理原始HTML
                                    const tempDiv = document.createElement('div');
                                    tempDiv.innerHTML = content;
                                                        
                                    // 移除所有内联样式和不必要的div/span嵌套
                                    const cleanUpElements = (element) => {
                                        // 移除空的div和span标签
                                        element.querySelectorAll('div, span').forEach(el => {
                                            if (el.children.length === 0 && el.textContent.trim() === '') {
                                                el.remove();
                                            }
                                        });
                                                        
                                        // 移除所有元素的内联样式
                                        element.querySelectorAll('*').forEach(el => {
                                            el.removeAttribute('style');
                                            el.removeAttribute('class');
                                        });
                                                        
                                        // 处理表格元素，添加基本样式
                                        element.querySelectorAll('table').forEach(table => {
                                            table.style.borderCollapse = 'collapse';
                                            table.style.width = '100%';
                                        });
                                                        
                                        element.querySelectorAll('th, td').forEach(cell => {
                                            cell.style.border = '1px solid #ebebec';
                                            cell.style.padding = '8px';
                                            cell.style.textAlign = 'left';
                                        });
                                                        
                                        element.querySelectorAll('th').forEach(th => {
                                            th.style.backgroundColor = '#f8f8fa';
                                            th.style.fontWeight = 'bold';
                                        });
                                    };
                                                        
                                    cleanUpElements(tempDiv);
                                                        
                                    // 创建最终容器
                                    const styledContainer = document.createElement('div');
                                    styledContainer.className = 'zhzd-response';
                                    styledContainer.style.cssText = 'max-width: 800px; margin: 0 auto; background-color: #fff; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); padding: 20px; font-family: Arial, sans-serif; line-height: 1.6; color: #333;';
                                                        
                                    // 将清理后的内容移入容器
                                    styledContainer.innerHTML = tempDiv.innerHTML;
                                                        
                                    // 处理所有直接子元素，确保它们是带有正确样式的p标签
                                    const processChildElements = (container) => {
                                        container.childNodes.forEach(node => {
                                            if (node.nodeType === Node.ELEMENT_NODE) {
                                                // 为所有直接子元素添加统一的段落样式
                                                if (!['STYLE', 'SCRIPT'].includes(node.tagName)) {
                                                    if (node.tagName === 'P') {
                                                        node.style.margin = '0px 0px 16px';
                                                        node.style.padding = '0px';
                                                    } else if (node.tagName === 'H3') {
                                                        node.style.margin = '0px 0px 16px';
                                                        node.style.fontSize = '16px';
                                                        node.style.fontWeight = '500';
                                                        node.style.lineHeight = '27px';
                                                    } else if (node.tagName === 'OL' || node.tagName === 'UL') {
                                                        node.style.margin = '0px 0px 16px 25px';
                                                        node.style.padding = '0px';
                                                    } else if (node.tagName === 'LI') {
                                                        node.style.whiteSpace = 'normal';
                                                        node.style.margin = '0px 0px 16px';
                                                    } else {
                                                        // 将其他元素包装在p标签中
                                                        const p = document.createElement('p');
                                                        p.style.margin = '0px 0px 16px';
                                                        p.style.padding = '0px';
                                                        p.innerHTML = node.outerHTML;
                                                        node.parentNode.replaceChild(p, node);
                                                    }
                                                }
                                            }
                                        });
                                    };
                                                        
                                    processChildElements(styledContainer);
                                                        
                                    return styledContainer.outerHTML;
                                } catch (e) {
                                    console.error('格式化知乎直答内容时出错:', e);
                                    return content;
                                }
                            }
                            """, rawHtmlContent);

                    if (finalFormattedContent != null && !finalFormattedContent.toString().isEmpty()) {
                        formattedContent = finalFormattedContent.toString();
                        logInfo.sendTaskLog("已将回答内容封装为统一的HTML展示样式", userId, aiName);
                    }
                }
            } catch (Exception e) {
                logInfo.sendTaskLog("内容格式化处理失败", userId, aiName);
                // 不发送技术错误到前端
            }

            // 🔥 优化：Zhihu分享操作，增加超时保护
            try {
                page.locator("div:has-text('分享回答')").last().click(new Locator.ClickOptions().setTimeout(30000));
                page.waitForTimeout(1000); // 增加等待时间
                shareUrl = (String) page.evaluate("navigator.clipboard.readText()");

                if (shareUrl != null && !shareUrl.trim().isEmpty()) {
                } else {
                    shareUrl = page.url();
                }

                // 获取分享图片，增加超时保护
                page.locator("div:has-text('保存图片')").last().click(new Locator.ClickOptions().setTimeout(30000));
                shareImgUrl = ScreenshotUtil.downloadAndUploadFile(page, uploadUrl, () -> {
                    page.locator("div:has-text('下载图片')").last().click(new Locator.ClickOptions().setTimeout(30000));
                });

                if (shareImgUrl != null && !shareImgUrl.trim().isEmpty()) {
                } else {
                }
            } catch (Exception e) {
                logInfo.sendTaskLog("获取分享链接处理失败", userId, aiName);
                // 不发送技术错误到前端
                // 尝试备用方法获取分享链接
                try {
                    shareUrl = page.url(); // 使用当前页面URL作为分享链接
                } catch (Exception backupE) {
                    shareUrl = ""; // 确保shareUrl不为null
                }
            }

            try {
                // 回传数据
                logInfo.sendTaskLog("执行完成", userId, aiName);
                logInfo.sendChatData(page, "/search/([^/?#]+)", userId, "RETURN_ZHZD_CHATID", 1);

                logInfo.sendResData(formattedContent, userId, aiName, "RETURN_ZHZD_RES", shareUrl, shareImgUrl);

                // 保存数据库
                userInfoRequest.setZhzdChatId(sessionId);
                userInfoRequest.setDraftContent(formattedContent);
                userInfoRequest.setAiName(aiName);
                userInfoRequest.setShareUrl(shareUrl);
                userInfoRequest.setShareImgUrl(shareImgUrl);
                RestUtils.post(url + "/saveDraftContent", userInfoRequest);
            } catch (Exception e) {
                logInfo.sendTaskLog("执行完成", userId, aiName);
                logInfo.sendChatData(page, "/search/([^/?#]+)", userId, "RETURN_ZHZD_CHATID", 1);
                logInfo.sendResData(formattedContent, userId, aiName, "RETURN_ZHZD_RES", shareUrl, shareImgUrl);
            }

            return formattedContent;
        } catch (Exception e) {
            logInfo.sendTaskLog("执行知乎直答任务时发生严重错误", userInfoRequest.getUserId(), "知乎直答");
            throw e;
        }
    }

    /**
     * 内部调用的豆包排版方法（用于媒体投递）
     * 该方法不发送WebSocket消息，适合被其他控制器内部调用
     *
     * @param userInfoRequest 用户信息请求体
     * @return 排版后的内容
     */
    public String startDBInternal(UserInfoRequest userInfoRequest) throws InterruptedException {
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false, userInfoRequest.getUserId(), "db")) {

            // 初始化变量
            String userId = userInfoRequest.getUserId();
            String dbchatId = userInfoRequest.getDbChatId();
            String roles = userInfoRequest.getRoles();
            String userPrompt = userInfoRequest.getUserPrompt();

            // 初始化页面并导航到指定会话
            Page page = browserUtil.getOrCreatePage(context);
            if (dbchatId != null) {
                page.navigate("https://www.doubao.com/chat/" + dbchatId);
            } else {
                page.navigate("https://www.doubao.com/chat/");
            }

            page.waitForLoadState(LoadState.LOAD);
            Thread.sleep(500);

            // 定位深度思考按钮
            Locator deepThoughtButton = page.locator("button.semi-button:has-text('深度思考')");
            // 检查按钮是否包含以 active- 开头的类名
            Boolean isActive = (Boolean) deepThoughtButton.evaluate("element => {\n" +
                    "    const classList = Array.from(element.classList);\n" +
                    "    return classList.some(cls => cls.startsWith('active-'));\n" +
                    "}");

            // 确保 isActive 不为 null
            if (isActive != null && !isActive && roles.contains("db-sdsk")) {
                deepThoughtButton.click();
                // 点击后等待一段时间，确保按钮状态更新
                Thread.sleep(1000);

                // 再次检查按钮状态
                isActive = (Boolean) deepThoughtButton.evaluate("element => {\n" +
                        "    const classList = Array.from(element.classList);\n" +
                        "    return classList.some(cls => cls.startsWith('active-'));\n" +
                        "}");
                if (isActive != null && !isActive) {
                    deepThoughtButton.click();
                    Thread.sleep(1000);
                }
            }

            Thread.sleep(1000);
            page.locator("[data-testid='chat_input_input']").click();
            Thread.sleep(1000);
            page.locator("[data-testid='chat_input_input']").fill(userPrompt);
            Thread.sleep(1000);
            page.locator("[data-testid='chat_input_input']").press("Enter");

            // 等待文本获取完成（不使用截图和日志发送）
            String copiedText = douBaoUtil.waitAndClickDBCopyButton(page, userId, "豆包");

            return copiedText;
        } catch (Exception e) {
            throw e;
        }
    }

    @Operation(summary = "微头条排版", description = "调用豆包平台对内容排版")
    @ApiResponse(responseCode = "200", description = "处理成功", content = @Content(mediaType = "application/json"))
    @PostMapping("/sendToTTHByDB")
    public String sendToTTHByDB(@RequestBody UserInfoRequest userInfoRequest) throws InterruptedException {
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false, userInfoRequest.getUserId(), "db")) {
            // 初始化变量
            String userId = userInfoRequest.getUserId();
            logInfo.sendTaskLog("微头条排版准备就绪，正在打开页面", userId, "微头条排版");
            String roles = userInfoRequest.getRoles();
            String userPrompt = userInfoRequest.getUserPrompt();

            // 初始化页面并导航到指定会话
            Page page = browserUtil.getOrCreatePage(context);
            page.navigate("https://www.doubao.com/chat/");
            page.waitForLoadState(LoadState.LOAD);
            Thread.sleep(500);
            logInfo.sendTaskLog("微头条排版页面打开完成", userId, "微头条排版");
            // 定位深度思考按钮
            Locator deepThoughtButton = page.locator("button.semi-button:has-text('深度思考')");
            // 检查按钮是否包含以 active- 开头的类名
            Boolean isActive = (Boolean) deepThoughtButton.evaluate("element => {\n" +
                    "    const classList = Array.from(element.classList);\n" +
                    "    return classList.some(cls => cls.startsWith('active-'));\n" +
                    "}");

            // 确保 isActive 不为 null
            if (isActive != null && !isActive && roles.contains("db-sdsk")) {
                deepThoughtButton.click();
                // 点击后等待一段时间，确保按钮状态更新
                Thread.sleep(1000);

                // 再次检查按钮状态
                isActive = (Boolean) deepThoughtButton.evaluate("element => {\n" +
                        "    const classList = Array.from(element.classList);\n" +
                        "    return classList.some(cls => cls.startsWith('active-'));\n" +
                        "}");
                if (isActive != null && !isActive) {
                    deepThoughtButton.click();
                    Thread.sleep(1000);
                }
            }
            Thread.sleep(500);
            page.locator("[data-testid='chat_input_input']").click();
            Thread.sleep(500);
            page.locator("[data-testid='chat_input_input']").fill(userPrompt);
            logInfo.sendTaskLog("原数据已录入微头条排版系统完成", userId, "微头条排版");
            Thread.sleep(500);
            page.locator("[data-testid='chat_input_input']").press("Enter");

            // 创建定时截图线程
            AtomicInteger i = new AtomicInteger(0);
            ScheduledExecutorService screenshotExecutor = Executors.newSingleThreadScheduledExecutor();
            // 启动定时任务，每5秒执行一次截图
            ScheduledFuture<?> screenshotFuture = screenshotExecutor.scheduleAtFixedRate(() -> {
                try {
                    int currentCount = i.getAndIncrement(); // 获取当前值并自增
                    logInfo.sendImgData(page, userId + "微头条排版执行过程截图" + currentCount, userId);
                } catch (Exception e) {
                    UserLogUtil.sendExceptionLog(userId, "微头条排版", "sendToTTHByDB", e, url + "/saveLogInfo");
                }
            }, 0, 9, TimeUnit.SECONDS);

            logInfo.sendTaskLog("开启自动监听任务，持续监听微头条排版结果", userId, "微头条排版");
            // 等待复制按钮出现并点击
            String copiedText = douBaoUtil.waitPBCopy(page, userId, "微头条排版");
            int first = copiedText.indexOf('"') + 1;
            int second = copiedText.indexOf('"', first);
            String title = copiedText.substring(first, second);
            String content = copiedText.substring(second + 1);
            //关闭截图
            screenshotFuture.cancel(false);
            screenshotExecutor.shutdown();

            logInfo.sendTaskLog("执行完成", userId, "微头条排版");
            logInfo.sendContentAndTitle(content, title, userId, "RETURN_TTH_ZNPB_RES");
            userInfoRequest.setDraftContent(copiedText);
            userInfoRequest.setAiName("智能评分");
            userInfoRequest.setShareUrl("");
            userInfoRequest.setShareImgUrl("");
            RestUtils.post(url + "/saveDraftContent", userInfoRequest);
            return copiedText;
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * 处理百度AI的常规请求
     *
     * @param userInfoRequest 包含会话ID和用户指令
     * @return AI生成的文本内容
     */
    @Operation(summary = "启动百度AI生成", description = "调用百度AI平台生成内容并抓取结果")
    @ApiResponse(responseCode = "200", description = "处理成功", content = @Content(mediaType = "application/json"))
    @PostMapping("/startBaidu")
    public String startBaidu(@RequestBody UserInfoRequest userInfoRequest) throws InterruptedException {
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false,
                userInfoRequest.getUserId(), "baidu")) {

            // 初始化变量
            String userId = userInfoRequest.getUserId();
            String roles = userInfoRequest.getRoles();
            String userPrompt = userInfoRequest.getUserPrompt();
            String chatId = userInfoRequest.getBaiduChatId();
            String isNewChat = userInfoRequest.getIsNewChat();

            logInfo.sendTaskLog("百度AI准备就绪，正在打开页面", userId, "百度AI");

            // 如果指定了新会话，则忽略已有的会话ID
            if ("true".equalsIgnoreCase(isNewChat)) {
                logInfo.sendTaskLog("用户请求新会话，将忽略已有会话ID", userId, "百度AI");
                chatId = null;
            } else if (chatId != null && !chatId.isEmpty()) {
                logInfo.sendTaskLog("检测到会话ID: " + chatId + "，将继续使用此会话", userId, "百度AI");
            } else {
                logInfo.sendTaskLog("未检测到会话ID，将创建新会话", userId, "百度AI");
            }

            // 创建页面
            Page page = browserUtil.getOrCreatePage(context);
            page.setDefaultTimeout(60000); // 60秒超时

            // 创建定时截图线程
            AtomicInteger i = new AtomicInteger(0);
            ScheduledExecutorService screenshotExecutor = Executors.newSingleThreadScheduledExecutor();
            ScheduledFuture<?> screenshotFuture = screenshotExecutor.scheduleAtFixedRate(() -> {
                try {
                    if (page.isClosed()) {
                        return;
                    }
                    int currentCount = i.getAndIncrement();
                    logInfo.sendImgData(page, userId + "百度AI执行过程截图" + currentCount, userId);
                } catch (Exception e) {
                    UserLogUtil.sendExceptionLog(userId, "百度AI截图", "startBaidu", e, url + "/saveLogInfo");
                }
            }, 2000, 8000, TimeUnit.MILLISECONDS); // 延迟2秒开始，每8秒执行一次

            logInfo.sendTaskLog("开启自动监听任务，持续监听百度AI回答中", userId, "百度AI");

            // 处理百度AI交互
            String copiedText = "";
            int maxRetries = 3;

            // 重试循环
            for (int retry = 0; retry < maxRetries; retry++) {
                try {
                    if (retry > 0) {
                        logInfo.sendTaskLog("第" + (retry + 1) + "次尝试", userId, "百度AI");
                        Thread.sleep(3000);
                    }

                    copiedText = baiduUtil.handleBaiduAI(page, userPrompt, userId, roles, chatId);

                    if (!copiedText.startsWith("获取内容失败") && !copiedText.isEmpty()) {
                        break; // 成功获取内容，跳出重试循环
                    }

                    Thread.sleep(3000); // 等待3秒后重试
                } catch (Exception e) {
                    if (retry == maxRetries - 1) {
                        copiedText = "获取内容失败：多次尝试后仍然失败";
                        logInfo.sendTaskLog("百度AI处理失败", userId, "百度AI");
                        UserLogUtil.sendExceptionLog(userId, "百度AI处理", "startBaidu", e, url + "/saveLogInfo");

                    }
                    Thread.sleep(2000);
                }
            }

            // 安全地关闭截图任务
            try {
                screenshotFuture.cancel(true);
                screenshotExecutor.shutdownNow();
                if (!screenshotExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                    logInfo.sendTaskLog("截图任务关闭超时", userId, "百度AI");
                }
            } catch (Exception e) {
                logInfo.sendTaskLog("关闭截图任务异常", userId, "百度AI");
                UserLogUtil.sendExceptionLog(userId, "百度AI截图关闭", "startBaidu", e, url + "/saveLogInfo");
            }

            // 如果获取内容失败，尝试备用方法
            if (copiedText.startsWith("获取内容失败") || copiedText.isEmpty()) {
                try {
                    logInfo.sendTaskLog("尝试使用备用方法提取内容", userId, "百度AI");

                    Object extractedContent = page.evaluate("""
                                () => {
                                    const contentElements = document.querySelectorAll('div, p, span');
                                    let longestText = '';
                                    
                                    for (let element of contentElements) {
                                        const text = element.innerHTML;
                                        if (text && text.length > longestText.length && text.length > 100) {
                                            longestText = text;
                                        }
                                    }
                                    
                                    return longestText || '无法提取内容';
                                }
                            """);

                    if (extractedContent != null && !extractedContent.toString().isEmpty() &&
                            !extractedContent.toString().equals("无法提取内容")) {
                        copiedText = extractedContent.toString();
                        logInfo.sendTaskLog("使用备用方法成功提取内容", userId, "百度AI");
                    }
                } catch (Exception e) {
                    logInfo.sendTaskLog("备用提取方法失败", userId, "百度AI");
                    UserLogUtil.sendExceptionLog(userId, "百度AI备用提取", "startBaidu", e, url + "/saveLogInfo");
                }
            }

            // 保存结果
            try {
                copiedText = baiduUtil.saveBaiduContent(page, userInfoRequest, roles, userId, copiedText);
                logInfo.sendTaskLog("执行完成", userId, "百度AI");
            } catch (Exception e) {
                e.printStackTrace();
                logInfo.sendTaskLog("保存百度AI内容到稿库失败", userId, "百度AI");
                UserLogUtil.sendExceptionLog(userId, "保存百度AI内容到稿库", "startBaidu", e, url + "/saveLogInfo");

                // 即使保存失败，也要发送结果数据
                try {
                    String errorContent = copiedText != null && !copiedText.isEmpty() ? copiedText : "获取内容失败：" + e.getMessage();
                    logInfo.sendResData(errorContent, userId, "百度AI", "RETURN_BAIDU_RES", "", "");
                } catch (Exception sendError) {
                }
            }

            return copiedText;

        } catch (Exception e) {
            logInfo.sendTaskLog("百度AI执行异常", userInfoRequest.getUserId(), "百度AI");
            throw e;
        }
    }
}
