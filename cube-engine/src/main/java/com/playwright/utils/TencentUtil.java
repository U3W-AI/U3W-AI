package com.playwright.utils;

import com.alibaba.fastjson.JSONObject;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.playwright.entity.UserInfoRequest;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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

    @Autowired
    private ClipboardLockManager clipboardLockManager;

    /**
     * 处理智能体AI交互流程
     * @param page Playwright页面实例
     * @param userPrompt 用户输入的指令
     * @param agentUrl 智能体URL
     * @param aiName AI名称
     * @param userId 用户ID
     * @param isNewChat 是否新会话
     * @return 复制按钮数量（用于后续监控）
     */
    public int handelAgentAI(Page page, String userPrompt, String agentUrl, String aiName, String userId, String isNewChat) throws InterruptedException {
        page.navigate(agentUrl);
        logInfo.sendImgData(page,userId+"打开智能体页面",userId);
        logInfo.sendTaskLog( aiName+"页面打开完成",userId,aiName);

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
        logInfo.sendTaskLog( "用户指令已自动输入完成",userId,aiName);
        Thread.sleep(500);
        page.locator(".ql-editor").press("Enter");
        logInfo.sendTaskLog( "指令已自动发送成功",userId,aiName);
        // 获取当前复制按钮数量（用于监控回答状态）
        int copyButtonCount = page.querySelectorAll("div.agent-chat__toolbar__item.agent-chat__toolbar__copy").size();
        Thread.sleep(2000);
        return copyButtonCount;
    }


    /**
     * 保存智能体草稿数据（带执行过程监控）
     * @param page Playwright页面实例
     * @param userInfoRequest 用户信息请求对象
     * @param aiName AI名称
     * @param userId 用户ID
     * @param initialCount 初始复制按钮数量
     * @param agentName 智能体名称
     * @param resName 结果名称
     * @return 抓取到的文本内容
     */
    public String saveAgentDraftData(Page page, UserInfoRequest userInfoRequest, String aiName, String userId, int initialCount, String agentName, String resName){
        // 定时截图监控配置（每10秒截图一次）
        AtomicInteger i = new AtomicInteger(0);
        ScheduledExecutorService screenshotExecutor = Executors.newSingleThreadScheduledExecutor();
        ScheduledFuture<?> screenshotFuture = screenshotExecutor.scheduleAtFixedRate(() -> {
            try {
                int currentCount = i.getAndIncrement(); // 获取当前值并自增
                logInfo.sendImgData(page, userId +agentName+ "工作流执行过程截图"+currentCount, userId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, 10, TimeUnit.SECONDS);
        try {
            logInfo.sendTaskLog( "开启自动监听任务，持续监听"+agentName+"回答中",userId,agentName);
            //等待复制按钮出现并点击
//            String copiedText = waitAndClickYBCopyButton(page,userId,aiName,initialCount,agentName);
            //等待html片段获取完成
            String copiedText = waitHtmlDom(page,agentName,userId);

            AtomicReference<String> shareUrlRef = new AtomicReference<>();

            clipboardLockManager.runWithClipboardLock(() -> {
                try {
                    page.locator("span.icon-yb-ic_share_2504").last().click();
                    Thread.sleep(2000);
                    page.locator("div.agent-chat__share-bar__item__logo").first().click();
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
            // 日志记录与数据保存
            logInfo.sendTaskLog( "执行完成",userId,agentName);
            logInfo.sendResData(copiedText,userId,agentName,resName,shareUrl);

            Thread.sleep(3000);
            userInfoRequest.setDraftContent(copiedText);
            userInfoRequest.setAiName("Agent-"+aiName);
            RestUtils.post(url+"/saveDraftContent", userInfoRequest);
            return copiedText;
        }catch (Exception e) {
            e.printStackTrace();
        }finally {
            // 无论成功还是异常，都取消定时截图任务
            screenshotFuture.cancel(false);
            screenshotExecutor.shutdown();
        }
        return "未获取到内容";
    }



    /**
     * 处理元宝AI交互流程
     * @param page Playwright页面实例
     * @param userPrompt 用户指令
     * @param role 角色/模式标识
     * @param userId 用户ID
     * @param aiName AI名称
     * @param chatId 会话ID
     * @return 初始复制按钮数量
     */
    public int handleYBAI(Page page,String userPrompt,String role,String userId,String aiName,String chatId) throws InterruptedException {

        // 页面导航与元素定位
        page.navigate("https://yuanbao.tencent.com/chat/naQivTmsDa/"+chatId);
        String modelDom ="[dt-button-id=\"model_switch\"]";
        String hunyuanDom = "//*[@id=\"hunyuan-bot\"]/div[7]/div/div/div/div[1]/li/span";
        String deepseekDom = "//*[@id=\"hunyuan-bot\"]/div[7]/div/div/div/div[2]/li/span";
        Locator modelName = page.locator(modelDom);

        modelName.waitFor(new Locator.WaitForOptions().setTimeout(10000));

        // 功能开关定位
        String deepThingDom = "[dt-button-id=\"deep_think\"]";
        Locator deepThing = page.locator(deepThingDom);

        String webSearchDom = "[dt-button-id=\"online_search\"]";
        Locator webSearch = page.locator(webSearchDom);

        logInfo.sendImgData(page,userId+"打开页面",userId);
        logInfo.sendTaskLog( aiName+"页面打开完成",userId,aiName);
        // 根据角色配置不同模式
        int copyButtonCount = page.querySelectorAll("div.agent-chat__toolbar__item.agent-chat__toolbar__copy").size();
        if(role.contains("yb-hunyuan")){
            //切换模型
            clickModelChange(page,modelName,modelDom,hunyuanDom,"hunyuan");
            logInfo.sendImgData(page,userId+"切换混元模型",userId);
            logInfo.sendTaskLog( "自动切换混元模型完成",userId,aiName);
        }
        if(role.contains("yb-deepseek")){
            //切换模型
            clickModelChange(page,modelName,modelDom,deepseekDom,"deep_seek");
            logInfo.sendImgData(page,userId+"切换DS模型",userId);
            logInfo.sendTaskLog( "自动切换DS模型完成",userId,aiName);
        }

        if(role.equals("yb-hunyuan-pt")){
            //是否深度思考
            clickDeepThing(page,deepThing,deepThingDom,"hunyuan_gpt_175B_0404");
            //是否联网搜索  1是 2否
            clickWebSearch(page,webSearch,webSearchDom,"2");
            logInfo.sendImgData(page,userId+"混元普通选择",userId);
        }

        if(role.equals("yb-hunyuan-sdsk")){
            //是否深度思考
            clickDeepThing(page,deepThing,deepThingDom,"hunyuan_t1");
            //是否联网搜索  1是 2否
            clickWebSearch(page,webSearch,webSearchDom,"2");
            logInfo.sendImgData(page,userId+"混元深思选择",userId);
            logInfo.sendTaskLog( "已启动深度思考模式",userId,aiName);
        }

        if(role.equals("yb-hunyuan-lwss-1")){
            //是否深度思考
            clickDeepThing(page,deepThing,deepThingDom,"hunyuan_gpt_175B_0404");

            //是否联网搜索 1是 2否
            clickWebSearch(page,webSearch,webSearchDom,"1");
            logInfo.sendImgData(page,userId+"混元联网选择",userId);
            logInfo.sendTaskLog( "已启动联网搜索模式",userId,aiName);
        }
        if(role.equals("yb-hunyuan-lwss-2")){
            //是否深度思考
            clickDeepThing(page,deepThing,deepThingDom,"hunyuan_t1");
            //是否联网搜索 1是 2否
            clickWebSearch(page,webSearch,webSearchDom,"1");
            logInfo.sendImgData(page,userId+"混元深思联网选择",userId);
            logInfo.sendTaskLog( "已启动深度思考+联网搜索模式",userId,aiName);
        }

        if(role.equals("yb-deepseek-pt")){
            //是否深度思考
            clickDeepThing(page,deepThing,deepThingDom,"deep_seek_v3");
            //是否联网搜索  1是 2否
            clickWebSearch(page,webSearch,webSearchDom,"2");
            logInfo.sendImgData(page,userId+"元宝DS普通选择",userId);
        }
        if(role.equals("yb-deepseek-sdsk")){
            //是否深度思考
            clickDeepThing(page,deepThing,deepThingDom,"deep_seek");
            //是否联网搜索  1是 2否
            clickWebSearch(page,webSearch,webSearchDom,"2");
            logInfo.sendImgData(page,userId+"元宝DS深思选择",userId);
            logInfo.sendTaskLog( "已启动深度思考模式",userId,aiName);
        }
        if(role.equals("yb-deepseek-lwss-1")){
            //是否深度思考
            clickDeepThing(page,deepThing,deepThingDom,"deep_seek_v3");
            //是否联网搜索 1是 2否
            clickWebSearch(page,webSearch,webSearchDom,"1");
            logInfo.sendImgData(page,userId+"元宝DS联网选择",userId);
            logInfo.sendTaskLog( "已启动联网搜索模式",userId,aiName);
        }
        if(role.equals("yb-deepseek-lwss-2")){
            //是否深度思考
            clickDeepThing(page,deepThing,deepThingDom,"deep_seek");
            //是否联网搜索 1是 2否
            clickWebSearch(page,webSearch,webSearchDom,"1");
            logInfo.sendImgData(page,userId+"元宝DS深思联网选择",userId);
            logInfo.sendTaskLog( "已启动深度思考+联网搜索模式",userId,aiName);
        }


        Thread.sleep(500);
        page.locator(".ql-editor > p").click();
        Thread.sleep(500);
        page.locator(".ql-editor").fill(userPrompt);
        logInfo.sendTaskLog( "用户指令已自动输入完成",userId,aiName);
        Thread.sleep(500);
        page.locator(".ql-editor").press("Enter");
        logInfo.sendTaskLog( "指令已自动发送成功",userId,aiName);
        Thread.sleep(1000);
        return copyButtonCount;
    }

    public String saveDraftData(Page page,UserInfoRequest userInfoRequest,String aiName,String userId,int initialCount) throws InterruptedException {

        // 创建定时截图线程
        AtomicInteger i = new AtomicInteger(0);
        ScheduledExecutorService screenshotExecutor = Executors.newSingleThreadScheduledExecutor();
        // 启动定时任务，每5秒执行一次截图
        ScheduledFuture<?> screenshotFuture = screenshotExecutor.scheduleAtFixedRate(() -> {
            try {
                if (Thread.currentThread().isInterrupted()) {
                    System.out.println("任务已结束，跳过截图");
                    return;
                }
                int currentCount = i.getAndIncrement(); // 获取当前值并自增
                logInfo.sendImgData(page, userId + "元宝执行过程截图"+currentCount, userId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, 7, TimeUnit.SECONDS);
        try {
            String agentName = "";
            if (aiName.contains("hunyuan")) {
                agentName = "腾讯元宝T1";
                logInfo.sendTaskLog( "开启自动监听任务，持续监听腾讯元宝T1回答中",userId,agentName);
            } else if (aiName.contains("deepseek")) {
                agentName = "腾讯元宝DS";
                logInfo.sendTaskLog( "开启自动监听任务，持续监听腾讯元宝DS回答中",userId,agentName);
            }
            //等待复制按钮出现并点击
//            String copiedText = waitAndClickYBCopyButton(page,userId,aiName,initialCount,agentName);
            //等待html片段获取
            String copiedText = waitHtmlDom(page,agentName,userId);
            AtomicReference<String> shareUrlRef = new AtomicReference<>();

            clipboardLockManager.runWithClipboardLock(() -> {
                try {
                    page.locator("span.icon-yb-ic_share_2504").last().click();
                    Thread.sleep(2000);
                    page.locator("div.agent-chat__share-bar__item__logo").first().click();

                    // 建议适当延迟等待内容更新
                    Thread.sleep(2000); // 根据实际加载速度调整
                    String shareUrl = (String) page.evaluate("navigator.clipboard.readText()");
                    shareUrlRef.set(shareUrl);
                    System.out.println("剪贴板内容：" + shareUrl);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            Thread.sleep(1000);
            String shareUrl = shareUrlRef.get();

            //关闭截图
            screenshotFuture.cancel(false);
            screenshotExecutor.shutdown();

            if (aiName.contains("hunyuan")) {
                logInfo.sendTaskLog( "执行完成",userId,"腾讯元宝T1");
                logInfo.sendChatData(page,"/chat/([^/]+)/([^/]+)",userId,"RETURN_YBT1_CHATID",2);
                logInfo.sendResData(copiedText,userId,"腾讯元宝T1","RETURN_YBT1_RES",shareUrl);
            } else if (aiName.contains("deepseek")) {
                logInfo.sendTaskLog( "执行完成",userId,"腾讯元宝DS");
                logInfo.sendChatData(page,"/chat/([^/]+)/([^/]+)",userId,"RETURN_YBDS_CHATID",2);
                logInfo.sendResData(copiedText,userId,"腾讯元宝T1","RETURN_YBDS_RES",shareUrl);
            }

            userInfoRequest.setDraftContent(copiedText);
            userInfoRequest.setAiName("腾讯元宝-" + aiName);
            userInfoRequest.setShareUrl(shareUrl);
            RestUtils.post(url + "/saveDraftContent", userInfoRequest);
            return copiedText;
        }catch (Exception e) {
            e.printStackTrace();
        }
        return "未获取到内容";
    }


    public void clickModelChange(Page page,Locator modelName,String modelDom,String modeCheckDom,String aiName) throws InterruptedException {
        if(!modelName.getAttribute("dt-model-id").contains(aiName)){

            Thread.sleep(1000);
            page.locator(modelDom).click();
            Thread.sleep(1000);
            page.locator(modeCheckDom).click();
        }
    }

    public void clickDeepThing(Page page,Locator deepThing,String deepThingDom,String aiName) throws InterruptedException {
        deepThing.waitFor(new Locator.WaitForOptions().setTimeout(5000));
        if(!deepThing.getAttribute("dt-model-id").equals(aiName)){
            Thread.sleep(500);
            page.locator(deepThingDom).click();
        }
    }

    public void clickWebSearch(Page page,Locator webSearch,String webSearchDom,String isWebSearch) throws InterruptedException {
        String searchText = page.locator("//*[@id=\"app\"]/div/div[2]/div/div/div[1]/div/div[3]/div/div[6]/div/div[2]/div[2]/div[3]/div[1]/div[2]/span/div/div/div/span[1]").textContent();
        if(!searchText.equals("自动搜索")){
            webSearch.waitFor(new Locator.WaitForOptions().setTimeout(5000));
            if(!webSearch.getAttribute("dt-ext3").equals(isWebSearch)){
                System.out.println("");
                Thread.sleep(500);
                page.locator(webSearchDom).click();
            }
        }
        if(searchText.equals("自动搜索")&& isWebSearch.equals("2")){

            page.locator(webSearchDom).click();
            Thread.sleep(500);
            page.locator("text=手动控制联网状态").click();
            Thread.sleep(500);

        }
    }

    /**
     * 等待并点击复制按钮（核心监控方法）
     * @param page Playwright页面实例
     * @param userId 用户ID
     * @param aiName AI名称
     * @param initialCount 初始按钮数量
     * @param agentName 智能体名称
     */
    private String waitAndClickYBCopyButton(Page page,String userId,String aiName,int initialCount,String agentName)  {
        try {
            String copiedText = "";
            int timeoutMillis = 600_000;
            int pollIntervalMillis = 3000;

            long startTime = System.currentTimeMillis();

            while (true) {
                int currentCount = page.querySelectorAll("div.agent-chat__toolbar__item.agent-chat__toolbar__copy").size();
                if (currentCount > initialCount) {
                    break;
                }

                if (System.currentTimeMillis() - startTime > timeoutMillis) {
                    throw new RuntimeException("等待复制按钮超时");
                }

                try {
                    Thread.sleep(pollIntervalMillis);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            logInfo.sendTaskLog( agentName+"回答完成，正在自动提取内容",userId,agentName);
            List<ElementHandle> copyButtons = page.querySelectorAll("div.agent-chat__toolbar__item.agent-chat__toolbar__copy");
            Thread.sleep(2000);  // 额外等待确保按钮可点击
            copyButtons.get(copyButtons.size() - 1).click();
            Thread.sleep(3000);
            copiedText = (String) page.evaluate("navigator.clipboard.readText()");
            logInfo.sendTaskLog( agentName+"内容已自动提取完成",userId,agentName);
            return copiedText;

        } catch (Exception e) {
            e.printStackTrace();
        }
        return "获取内容失败";
    }

    /**
     * html片段获取（核心监控方法）
     * @param page Playwright页面实例
     */
    private String waitHtmlDom(Page page,String agentName,String userId)  {
        try {
            // 等待聊天框的内容稳定
            String currentContent = "";
            String lastContent = "";
            // 设置最大等待时间（单位：毫秒），比如 10 分钟
            long timeout = 600000; // 10 分钟
            long startTime = System.currentTimeMillis();  // 获取当前时间戳

            // 进入循环，直到内容不再变化或者超时
            while (true) {
                // 获取当前时间戳
                long elapsedTime = System.currentTimeMillis() - startTime;

                // 如果超时，退出循环
                if (elapsedTime > timeout) {
                    System.out.println("超时，AI未完成回答！");
                    break;
                }
                // 获取最新内容
                Locator outputLocator = page.locator(".hyc-common-markdown").last();
                currentContent = outputLocator.innerHTML();

                // 如果当前内容和上次内容相同，认为 AI 已经完成回答，退出循环
                if (currentContent.equals(lastContent)) {
                    logInfo.sendTaskLog( agentName+"回答完成，正在自动提取内容",userId,agentName);
                    break;
                }

                // 更新上次内容为当前内容
                lastContent = currentContent;

                // 等待 2 秒后再次检查
                page.waitForTimeout(15000);  // 等待2秒
            }
            currentContent = currentContent.replaceAll("<div class=\"hyc-common-markdown__ref-list\".*?</div>|<span>.*?</span>","");
            currentContent = currentContent.replaceAll(
                    "<div class=\"hyc-common-markdown__ref-list__trigger\"[^>]*>\\s*<div class=\"hyc-common-markdown__ref-list__item\"></div>\\s*</div>",
                    ""
            );
            Document doc = Jsoup.parse(currentContent);
            currentContent = doc.text();  // 提取纯文本内容
            logInfo.sendTaskLog( agentName+"内容已自动提取完成",userId,agentName);
            return currentContent;

        } catch (Exception e) {
            e.printStackTrace();
        }
        return "获取内容失败";
    }



}
