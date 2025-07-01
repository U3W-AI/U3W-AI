package com.playwright.controller;

import com.alibaba.fastjson.JSONObject;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.playwright.entity.UserInfoRequest;
import com.playwright.utils.*;
import com.playwright.websocket.WebSocketClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
/**
 * AI生成内容控制器
 * 处理与各大AI平台（腾讯元宝、豆包等）的交互操作
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

    // 从配置文件中注入URL 调用远程API存储数据
    @Value("${cube.url}")
    private String url;

    // 腾讯元宝相关操作工具类
    @Autowired
    private TencentUtil tencentUtil;

    // 豆包相关操作工具类
    @Autowired
    private DouBaoUtil douBaoUtil;

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

    @Value("${cube.uploadurl}")
    private String uploadUrl;


    /**
     * 处理多AI代理的请求
     * @param userInfoRequest 包含用户ID、角色、提示信息等
     * @return 拼接的生成内容
     */
    @Operation(summary = "处理多AI代理请求", description = "根据用户输入启动多个AI平台代理任务")
    @ApiResponse(responseCode = "200", description = "处理成功", content = @Content(mediaType = "application/json"))
    @PostMapping("/startAgent")
    public String startAgent(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "用户信息请求体", required = true,
            content = @Content(schema = @Schema(implementation = UserInfoRequest.class))) @RequestBody UserInfoRequest userInfoRequest) {
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false,userInfoRequest.getUserId(),"agent")) {

              // 初始化变量
              String userId = userInfoRequest.getUserId();
              String roles = userInfoRequest.getRoles();
              String userPrompt = userInfoRequest.getUserPrompt();
              String isNewChat = userInfoRequest.getIsNewChat();
              String copiedText = "";
              int wrightCopyCount = 0;


              // 根据不同的AI角色创建对应的页面实例
              Page[] pages = new Page[5];

              // 处理 智能体 代理
              if(roles.contains("cube-trubos-agent")){
                  logInfo.sendTaskLog( "TurboS@元器准备就绪，正在打开页面",userId,"TurboS@元器");
                  pages[0] = context.newPage();
                  String agentUrl = "https://yuanbao.tencent.com/chat/58LgTturCBdj/";
                  wrightCopyCount = tencentUtil.handelAgentAI(pages[0],userPrompt,agentUrl,"TurboS@元器",userId,isNewChat);
              }
              if(roles.contains("cube-turbos-large-agent")){
                  logInfo.sendTaskLog( "TurboS长文版@元器准备就绪，正在打开页面",userId,"TurboS长文版@元器");
                  pages[1] = context.newPage();
                  String agentUrl = "https://yuanbao.tencent.com/chat/rgzZDhQdsMHZ/";
                  wrightCopyCount = tencentUtil.handelAgentAI(pages[1],userPrompt,agentUrl,"TurboS长文版@元器",userId,isNewChat);
              }
              if(roles.contains("cube-mini-max-agent")){
                  logInfo.sendTaskLog( "MiniMax@元器准备就绪，正在打开页面",userId,"MiniMax@元器");
                  pages[2] = context.newPage();
                  String agentUrl = "https://yuanbao.tencent.com/chat/7kNJBgAgQFet/";
                  wrightCopyCount = tencentUtil.handelAgentAI(pages[2],userPrompt,agentUrl,"MiniMax@元器",userId,isNewChat);
              }
              if(roles.contains("cube-sogou-agent")){
                  logInfo.sendTaskLog( "搜狗搜索@元器准备就绪，正在打开页面",userId,"搜狗搜索@元器");
                  pages[3] = context.newPage();
                  String agentUrl = "https://yuanbao.tencent.com/chat/u1VeB6jKt0lE/";
                  wrightCopyCount = tencentUtil.handelAgentAI(pages[3],userPrompt,agentUrl,"搜狗搜索@元器",userId,isNewChat);
              }
              if(roles.contains("cube-lwss-agent")){
                  logInfo.sendTaskLog( "KIMI@元器准备就绪，正在打开页面",userId,"KIMI@元器");
                  pages[4] = context.newPage();
                  String agentUrl = "https://yuanbao.tencent.com/chat/oq4esMyN9VS2/";
                  wrightCopyCount = tencentUtil.handelAgentAI(pages[4],userPrompt,agentUrl,"KIMI@元器",userId,isNewChat);
              }

             // 保存各代理生成的数据并拼接结果
              if(roles.contains("cube-trubos-agent")){
                  copiedText = copiedText +"\n\n"+ tencentUtil.saveAgentDraftData(pages[0],userInfoRequest,"cube-trubos-agent",userId,wrightCopyCount,"TurboS@元器","RETURN_TURBOS_RES");
              }
              if(roles.contains("cube-turbos-large-agent")){
                  copiedText = copiedText +"\n\n"+ tencentUtil.saveAgentDraftData(pages[1],userInfoRequest,"cube-turbos-large-agent",userId,wrightCopyCount,"TurboS长文版@元器","RETURN_TURBOS_LARGE_RES");
              }
              if(roles.contains("cube-mini-max-agent")){
                  copiedText = copiedText +"\n\n"+ tencentUtil.saveAgentDraftData(pages[2],userInfoRequest,"cube-mini-max-agent",userId,wrightCopyCount,"MiniMax@元器","RETURN_MINI_MAX_RES");
              }
              if(roles.contains("cube-sogou-agent")){
                  copiedText = copiedText +"\n\n"+ tencentUtil.saveAgentDraftData(pages[3],userInfoRequest,"cube-sogou-agent",userId,wrightCopyCount,"搜狗搜索@元器","RETURN_SOGOU_RES");
              }
              if(roles.contains("cube-lwss-agent")){
                  copiedText = copiedText +"\n\n"+ tencentUtil.saveAgentDraftData(pages[4],userInfoRequest,"cube-lwss-agent",userId,wrightCopyCount,"KIMI@元器","RETURN_LWSS_RES");
              }
               return copiedText;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "获取内容失败";
    }

    /**
     * 处理腾讯元宝平台的请求
     * @param userInfoRequest 包含用户信息和会话参数
     * @return 生成的内容（当前版本暂未实现内容返回）
     */
    @Operation(summary = "启动腾讯元宝内容生成", description = "根据角色执行不同类型的腾讯元宝任务（T1和DS）")
    @ApiResponse(responseCode = "200", description = "处理成功", content = @Content(mediaType = "application/json"))
    @PostMapping("/startYB")
    public String startYB(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "用户信息请求体", required = true,
            content = @Content(schema = @Schema(implementation = UserInfoRequest.class))) @RequestBody UserInfoRequest userInfoRequest) {
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false,userInfoRequest.getUserId(),"yb")) {

               String userId =userInfoRequest.getUserId();
               String currentContent = "";
               String roles = userInfoRequest.getRoles();
               String userPrompt = userInfoRequest.getUserPrompt();
               String t1ChatId = userInfoRequest.getToneChatId();
               String dschatId = userInfoRequest.getYbDsChatId();

               Page[] pages = new Page[2];
               int t1CopyCount = 0;
               int dsCopyCount = 0;

               //腾讯元宝T1  根据角色组合处理不同模式（普通/深度思考/联网）
               logInfo.sendTaskLog( "腾讯元宝准备就绪，正在打开页面",userId,"腾讯元宝T1");
               if(roles.contains("yb-hunyuan-pt")&& !roles.contains("yb-hunyuan-sdsk") && !roles.contains("yb-hunyuan-lwss")){
                   pages[0] = context.newPage();
                   t1CopyCount =  tencentUtil.handleYBAI(pages[0], userPrompt, "yb-hunyuan-pt",userId,"腾讯元宝T1",t1ChatId);
               }else if(roles.contains("yb-hunyuan-sdsk") && !roles.contains("yb-hunyuan-lwss")){
                   //深度思考
                   pages[0] = context.newPage();
                   t1CopyCount = tencentUtil.handleYBAI( pages[0], userPrompt, "yb-hunyuan-sdsk",userId,"腾讯元宝T1",t1ChatId);
               }else if(roles.contains("yb-hunyuan-lwss") && !roles.contains("yb-hunyuan-sdsk")){
                   //深度思考 + 联网
                   pages[0] = context.newPage();
                   t1CopyCount = tencentUtil.handleYBAI( pages[0], userPrompt, "yb-hunyuan-lwss-1",userId,"腾讯元宝T1",t1ChatId);
               }else if(roles.contains("yb-hunyuan-lwss") && roles.contains("yb-hunyuan-sdsk")){
                   //深度思考 + 联网
                   pages[0] = context.newPage();
                   t1CopyCount = tencentUtil.handleYBAI( pages[0], userPrompt, "yb-hunyuan-lwss-2",userId,"腾讯元宝T1",t1ChatId);
               }

            //腾讯元宝DS  根据角色组合处理不同模式（普通/深度思考/联网）
               if(roles.contains("yb-deepseek-pt") && !roles.contains("yb-deepseek-sdsk") && !roles.contains("yb-deepseek-lwss")){
                   pages[1] = context.newPage();
                   dsCopyCount = tencentUtil.handleYBAI(pages[1],userPrompt,"yb-deepseek-pt",userId,"腾讯元宝DS",dschatId);
               }else  if(roles.contains("yb-deepseek-sdsk")  && !roles.contains("yb-deepseek-lwss")){
                   //深度思考
                   pages[1] = context.newPage();
                   dsCopyCount = tencentUtil.handleYBAI(pages[1],userPrompt,"yb-deepseek-sdsk",userId,"腾讯元宝DS",dschatId);
               }else if(roles.contains("yb-deepseek-lwss") && !roles.contains("yb-deepseek-sdsk") ){
                   //深度思考 + 联网
                   pages[1] = context.newPage();
                   dsCopyCount = tencentUtil.handleYBAI(pages[1],userPrompt,"yb-deepseek-lwss-1",userId,"腾讯元宝DS",dschatId);
               }else if(roles.contains("yb-deepseek-lwss") && roles.contains("yb-deepseek-sdsk") ){
                   //深度思考 + 联网
                   pages[1] = context.newPage();
                   dsCopyCount = tencentUtil.handleYBAI(pages[1],userPrompt,"yb-deepseek-lwss-2",userId,"腾讯元宝DS",dschatId);
               }


               //保存入库 腾讯元宝T1
               if(roles.contains("yb-hunyuan-pt")&& !roles.contains("yb-hunyuan-sdsk") && !roles.contains("yb-hunyuan-lwss")){
                   currentContent = currentContent +"\n\n"+ tencentUtil.saveDraftData(pages[0],userInfoRequest,"yb-hunyuan-pt",userId,t1CopyCount);
               }else if(roles.contains("yb-hunyuan-sdsk") && !roles.contains("yb-hunyuan-lwss")){
                   //深度思考
                   currentContent = currentContent +"\n\n"+ tencentUtil.saveDraftData(pages[0],userInfoRequest,"yb-hunyuan-sdsk",userId,t1CopyCount);
               }else if(roles.contains("yb-hunyuan-lwss")){
                   //深度思考 + 联网
                   currentContent = currentContent +"\n\n"+ tencentUtil.saveDraftData(pages[0],userInfoRequest,"yb-hunyuan-lwss",userId,t1CopyCount);
               }

                //保存入库 腾讯元宝DS
               if(roles.contains("yb-deepseek-pt") && !roles.contains("yb-deepseek-sdsk") && !roles.contains("yb-deepseek-lwss")){
                   currentContent = currentContent +"\n\n"+ tencentUtil.saveDraftData(pages[1],userInfoRequest,"yb-deepseek-pt",userId,dsCopyCount);
               }else  if(roles.contains("yb-deepseek-sdsk")  && !roles.contains("yb-deepseek-lwss")){
                   currentContent = currentContent +"\n\n"+ tencentUtil.saveDraftData(pages[1],userInfoRequest,"yb-deepseek-sdsk",userId,dsCopyCount);
               }else if(roles.contains("yb-deepseek-lwss")){
                   //深度思考 + 联网
                   currentContent = currentContent +"\n\n"+ tencentUtil.saveDraftData(pages[1],userInfoRequest,"yb-deepseek-lwss",userId,dsCopyCount);
               }
               return currentContent;

        } catch (Exception e) {
            e.printStackTrace();
        }
        return "获取内容失败";
    }


    /**
     * 处理豆包的常规请求
     * @param userInfoRequest 包含会话ID和用户指令
     * @return AI生成的文本内容
     */
    @Operation(summary = "启动豆包AI生成", description = "调用豆包AI平台生成内容并抓取结果")
    @ApiResponse(responseCode = "200", description = "处理成功", content = @Content(mediaType = "application/json"))
    @PostMapping("/startDB")
    public String startDB(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "用户信息请求体", required = true,
            content = @Content(schema = @Schema(implementation = UserInfoRequest.class))) @RequestBody UserInfoRequest userInfoRequest){
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false,userInfoRequest.getUserId(),"db")) {

            // 初始化变量
            String userId = userInfoRequest.getUserId();
            String dbchatId = userInfoRequest.getDbChatId();
            logInfo.sendTaskLog( "豆包准备就绪，正在打开页面",userId,"豆包");
            String roles = userInfoRequest.getRoles();
            String userPrompt = userInfoRequest.getUserPrompt();

            // 初始化页面并导航到指定会话
            Page page = context.newPage();
            if(dbchatId!=null){
                page.navigate("https://www.doubao.com/chat/"+dbchatId);
            }else {
                page.navigate("https://www.doubao.com/chat/");
            }

            page.waitForLoadState(LoadState.LOAD);
            Thread.sleep(500);
            logInfo.sendTaskLog( "豆包页面打开完成",userId,"豆包");
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
                logInfo.sendTaskLog( "已启动深度思考模式",userId,"豆包");
            }
            Thread.sleep(1000);
            page.locator("[data-testid='chat_input_input']").click();
            Thread.sleep(1000);
            page.locator("[data-testid='chat_input_input']").fill(userPrompt);
            logInfo.sendTaskLog( "用户指令已自动输入完成",userId,"豆包");
            Thread.sleep(1000);
            page.locator("[data-testid='chat_input_input']").press("Enter");
            logInfo.sendTaskLog( "指令已自动发送成功",userId,"豆包");

            // 创建定时截图线程
            AtomicInteger i = new AtomicInteger(0);
            ScheduledExecutorService screenshotExecutor = Executors.newSingleThreadScheduledExecutor();
            // 启动定时任务，每5秒执行一次截图
            ScheduledFuture<?> screenshotFuture = screenshotExecutor.scheduleAtFixedRate(() -> {
                try {
                    int currentCount = i.getAndIncrement(); // 获取当前值并自增
                    logInfo.sendImgData(page, userId + "豆包执行过程截图"+currentCount, userId);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, 0, 8, TimeUnit.SECONDS);

            logInfo.sendTaskLog( "开启自动监听任务，持续监听豆包回答中",userId,"豆包");
            // 等待复制按钮出现并点击
//            String copiedText =  douBaoUtil.waitAndClickDBCopyButton(page,userId,roles);
            //等待html片段获取完成
            String copiedText =  douBaoUtil.waitDBHtmlDom(page,userId,"豆包");
            //关闭截图
            screenshotFuture.cancel(false);
            screenshotExecutor.shutdown();

            boolean isRight;

            Locator chatHis = page.locator("//*[@id=\"root\"]/div[1]/div/div[3]/div/main/div/div/div[2]/div/div[1]/div/div/div[2]/div[2]/div/div/div/div/div/div/div[1]/div/div/div[2]/div[1]/div/div");
            if(chatHis.count()>0){
                isRight =true;
            } else {
                isRight = false;
            }

            AtomicReference<String> shareUrlRef = new AtomicReference<>();

            clipboardLockManager.runWithClipboardLock(() -> {
                try {
                    if(isRight && page.locator("//*[@id=\"root\"]/div[1]/div/div[3]/aside/div[2]/div/div[1]/div/div[1]/div[3]/div/div/div/div[4]").count()>0){
                        page.locator("//*[@id=\"root\"]/div[1]/div/div[3]/aside/div[2]/div/div[1]/div/div[1]/div[3]/div/div/div/div[4]").click();
                        Thread.sleep(1000);
                        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("公开分享")).click();
                        Thread.sleep(500);
                    }else{
                        page.locator("button[data-testid='message_action_share']").last().click();
                        Thread.sleep(2000);
                        page.locator("button[data-testid='thread_share_copy_btn']").first().click();
                    }

                    // 建议适当延迟等待内容更新
                    Thread.sleep(2000);
                    String shareUrl = (String) page.evaluate("navigator.clipboard.readText()");
                    shareUrlRef.set(shareUrl);
                    System.out.println("剪贴板内容：" + shareUrl);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            Thread.sleep(1000);
            String shareUrl = shareUrlRef.get();
            String sharImgUrl = "";
            if (isRight && page.locator("//*[@id=\"root\"]/div[1]/div/div[3]/aside/div[2]/div/div[1]/div/div[1]/div[3]/div/div/div/div[3]").count()>0) {
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

            logInfo.sendTaskLog( "执行完成",userId,"豆包");
            logInfo.sendChatData(page,"/chat/([^/?#]+)",userId,"RETURN_DB_CHATID",1);
            logInfo.sendResData(copiedText,userId,"豆包","RETURN_DB_RES",shareUrl,sharImgUrl);

            //保存数据库
            userInfoRequest.setDraftContent(copiedText);
            userInfoRequest.setAiName("豆包");
            userInfoRequest.setShareUrl(shareUrl);
            userInfoRequest.setShareImgUrl(sharImgUrl);
            RestUtils.post(url+"/saveDraftContent", userInfoRequest);
            return copiedText;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "获取内容失败";
    }


    @Operation(summary = "豆包智能评分", description = "调用豆包平台对内容进行评分并返回评分结果")
    @ApiResponse(responseCode = "200", description = "处理成功", content = @Content(mediaType = "application/json"))
    @PostMapping("/startDBScore")
    public String startDBScore(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "用户信息请求体", required = true,
            content = @Content(schema = @Schema(implementation = UserInfoRequest.class))) @RequestBody UserInfoRequest userInfoRequest){
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false,userInfoRequest.getUserId(),"db")) {

            // 初始化变量
            String userId = userInfoRequest.getUserId();
            logInfo.sendTaskLog( "评分准备就绪，正在打开页面",userId,"智能评分");
            String roles = userInfoRequest.getRoles();
            String userPrompt = userInfoRequest.getUserPrompt();

            // 初始化页面并导航到指定会话
            Page page = context.newPage();
            page.navigate("https://www.doubao.com/chat/");
            page.waitForLoadState(LoadState.LOAD);
            Thread.sleep(500);
            logInfo.sendTaskLog( "智能评分页面打开完成",userId,"智能评分");
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
            logInfo.sendTaskLog( "初稿已录入评分系统完成",userId,"智能评分");
            Thread.sleep(500);
            page.locator("[data-testid='chat_input_input']").press("Enter");

            // 创建定时截图线程
            AtomicInteger i = new AtomicInteger(0);
            ScheduledExecutorService screenshotExecutor = Executors.newSingleThreadScheduledExecutor();
            // 启动定时任务，每5秒执行一次截图
            ScheduledFuture<?> screenshotFuture = screenshotExecutor.scheduleAtFixedRate(() -> {
                try {
                    int currentCount = i.getAndIncrement(); // 获取当前值并自增
                    logInfo.sendImgData(page, userId + "评分执行过程截图"+currentCount, userId);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, 0, 9, TimeUnit.SECONDS);

            logInfo.sendTaskLog( "开启自动监听任务，持续监听评分结果",userId,"智能评分");
            // 等待复制按钮出现并点击
            String copiedText =  douBaoUtil.waitDBHtmlDom(page,userId,"智能评分");

            //关闭截图
            screenshotFuture.cancel(false);
            screenshotExecutor.shutdown();
            boolean isRight;
            Locator chatHis = page.locator("//*[@id=\"root\"]/div[1]/div/div[3]/div/main/div/div/div[2]/div/div[1]/div/div/div[2]/div[2]/div/div/div/div/div/div/div[1]/div/div/div[2]/div[1]/div/div");
            if(chatHis.count()>0){
                isRight =true;
            } else {
                isRight = false;
            }
            //关闭截图
            screenshotFuture.cancel(false);
            screenshotExecutor.shutdown();

            AtomicReference<String> shareUrlRef = new AtomicReference<>();
            clipboardLockManager.runWithClipboardLock(() -> {
                try {
                    if(isRight && page.locator("//*[@id=\"root\"]/div[1]/div/div[3]/aside/div[2]/div/div[1]/div/div[1]/div[3]/div/div/div/div[4]").count()>0){
                        page.locator("//*[@id=\"root\"]/div[1]/div/div[3]/aside/div[2]/div/div[1]/div/div[1]/div[3]/div/div/div/div[4]").click();
                        Thread.sleep(1000);
                        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("公开分享")).click();
                        Thread.sleep(500);
                    }else{
                        page.locator("button[data-testid='message_action_share']").last().click();
                        Thread.sleep(2000);
                        page.locator("button[data-testid='thread_share_copy_btn']").first().click();
                    }

                    // 建议适当延迟等待内容更新
                    Thread.sleep(2000);
                    String shareUrl = (String) page.evaluate("navigator.clipboard.readText()");
                    shareUrlRef.set(shareUrl);
                    System.out.println("剪贴板内容：" + shareUrl);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            Thread.sleep(1000);
            String shareUrl = shareUrlRef.get();
            String sharImgUrl = "";
            if (isRight && page.locator("//*[@id=\"root\"]/div[1]/div/div[3]/aside/div[2]/div/div[1]/div/div[1]/div[3]/div/div/div/div[3]").count()>0) {
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

            logInfo.sendTaskLog( "执行完成",userId,"智能评分");
            logInfo.sendResData(copiedText,userId,"智能评分","RETURN_WKPF_RES",shareUrl,sharImgUrl);
            userInfoRequest.setDraftContent(copiedText);
            userInfoRequest.setAiName("智能评分");
            userInfoRequest.setShareUrl(shareUrl);
            userInfoRequest.setShareImgUrl(sharImgUrl);
            RestUtils.post(url+"/saveDraftContent", userInfoRequest);
            return copiedText;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "获取内容失败";
    }





    /**
     * 处理千问的常规请求
     * @param userInfoRequest 包含会话ID和用户指令
     * @return AI生成的文本内容
     */
    @Operation(summary = "启动千问AI生成", description = "调用千问AI平台生成内容并抓取结果")
    @ApiResponse(responseCode = "200", description = "处理成功", content = @Content(mediaType = "application/json"))
    @PostMapping("/startQW")
    public String startQW(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "用户信息请求体", required = true,
            content = @Content(schema = @Schema(implementation = UserInfoRequest.class))) @RequestBody UserInfoRequest userInfoRequest){
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false,userInfoRequest.getUserId(),"qwen")) {

            // 初始化变量
            String userId = userInfoRequest.getUserId();
            String qwchatId = userInfoRequest.getQwChatId();
            logInfo.sendTaskLog( "通义千问准备就绪，正在打开页面",userId,"通义千问");
            String roles = userInfoRequest.getRoles();
            String userPrompt = userInfoRequest.getUserPrompt();

            // 初始化页面并导航到指定会话
            Page page = context.newPage();
            if(qwchatId!=null){
                page.navigate("https://www.tongyi.com/qianwen?sessionId="+qwchatId);
            }else {
                page.navigate("https://www.tongyi.com/qianwen");
            }

            page.waitForLoadState(LoadState.LOAD);
            Thread.sleep(500);
            logInfo.sendTaskLog( "通义千问页面打开完成",userId,"通义千问");
            // 定位深度思考按钮

            // 确保 isActive 不为 null
//            if (roles.contains("qw-sdsk")) {
//                page.locator("//*[@id=\"ice-container\"]/div/div/div[2]/div/div[2]/div[1]/div[3]/div[1]/div[1]/div[2]").click();
//                // 点击后等待一段时间，确保按钮状态更新
//                Thread.sleep(1000);
//
//                logInfo.sendTaskLog( "已启动深度思考模式",userId,"通义千问");
//                page.locator("//*[@id=\"ice-container\"]/div/div/div[2]/div/div[2]/div[1]/div[3]/div/div[2]/div[2]/div/textarea").click();
//                Thread.sleep(1000);
//                page.locator("//*[@id=\"ice-container\"]/div/div/div[2]/div/div[2]/div[1]/div[3]/div/div[2]/div[2]/div/textarea").fill(userPrompt);
//                logInfo.sendTaskLog( "用户指令已自动输入完成",userId,"通义千问");
//                Thread.sleep(1000);
//                page.locator("//*[@id=\"ice-container\"]/div/div/div[2]/div/div[2]/div[1]/div[3]/div/div[2]/div[2]/div/textarea").press("Enter");
//                logInfo.sendTaskLog( "指令已自动发送成功",userId,"通义千问");
//            }else{
//                page.locator("//*[@id=\"ice-container\"]/div/div/div[2]/div/div[2]/div[1]/div[3]/div[2]/div/div[2]/div/textarea").click();
//                Thread.sleep(1000);
//                page.locator("//*[@id=\"ice-container\"]/div/div/div[2]/div/div[2]/div[1]/div[3]/div[2]/div/div[2]/div/textarea").fill(userPrompt);
//                logInfo.sendTaskLog( "用户指令已自动输入完成",userId,"通义千问");
//                Thread.sleep(1000);
//                page.locator("//*[@id=\"ice-container\"]/div/div/div[2]/div/div[2]/div[1]/div[3]/div[2]/div/div[2]/div/textarea").press("Enter");
//                logInfo.sendTaskLog( "指令已自动发送成功",userId,"通义千问");
//            }
            Thread.sleep(1000);



            // 创建定时截图线程
            AtomicInteger i = new AtomicInteger(0);
            ScheduledExecutorService screenshotExecutor = Executors.newSingleThreadScheduledExecutor();
            // 启动定时任务，每5秒执行一次截图
            ScheduledFuture<?> screenshotFuture = screenshotExecutor.scheduleAtFixedRate(() -> {
                try {
                    int currentCount = i.getAndIncrement(); // 获取当前值并自增
                    logInfo.sendImgData(page, userId + "通义千问执行过程截图"+currentCount, userId);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, 0, 8, TimeUnit.SECONDS);

            logInfo.sendTaskLog( "开启自动监听任务，持续监听通义千问回答中",userId,"通义千问");
            // 等待复制按钮出现并点击
//            String copiedText =  douBaoUtil.waitAndClickDBCopyButton(page,userId,roles);
            //等待html片段获取完成
            String copiedText =  qwenUtil.waitQWHtmlDom(page,userId);
            //关闭截图
            screenshotFuture.cancel(false);
            screenshotExecutor.shutdown();


            AtomicReference<String> shareUrlRef = new AtomicReference<>();

            clipboardLockManager.runWithClipboardLock(() -> {
                try {
                    Locator allShareIcons = page.locator("use[xlink\\:href='#tongyi-share-line']");
                    int count = allShareIcons.count();
                    allShareIcons.nth(count - 1).locator("xpath=../../..").click();
                    Thread.sleep(2000);
                    page.locator("//*[@id=\"ice-container\"]/div/div/div[2]/div/div[2]/div[1]/div[3]/button[2]").click();
                    // 建议适当延迟等待内容更新
                    Thread.sleep(2000);
                    page.getByText("复制链接").last().click();
                    String shareUrl = (String) page.evaluate("navigator.clipboard.readText()");
                    shareUrlRef.set(shareUrl);
                    System.out.println("剪贴板内容：" + shareUrl);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            Thread.sleep(1000);
            String shareUrl = shareUrlRef.get();

            logInfo.sendTaskLog( "执行完成",userId,"通义千问");
            logInfo.sendChatData(page,"/chat/([^/?#]+)",userId,"RETURN_DB_CHATID",1);
            logInfo.sendResData(copiedText,userId,"通义千问","RETURN_DB_RES",shareUrl,null);

            //保存数据库
            userInfoRequest.setDraftContent(copiedText);
            userInfoRequest.setAiName("通义千问");
            userInfoRequest.setShareUrl(shareUrl);
            userInfoRequest.setShareImgUrl(null);
            RestUtils.post(url+"/saveDraftContent", userInfoRequest);
            return copiedText;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "获取内容失败";
    }



}
