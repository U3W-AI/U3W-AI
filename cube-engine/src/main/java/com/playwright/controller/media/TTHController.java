package com.playwright.controller.media;

import com.alibaba.fastjson.JSONObject;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Keyboard;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.playwright.entity.UserInfoRequest;
import com.playwright.utils.common.BrowserUtil;
import com.playwright.utils.common.LogMsgUtil;
import com.playwright.utils.common.ScreenshotUtil;
import com.playwright.utils.common.UserLogUtil;
import com.playwright.utils.media.TTHUtil;
import com.playwright.websocket.WebSocketClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;


/**
 * description:  TODO
 * dateStart 2024/8/4 9:34
 * dateNow   2025/7/20 13:52
 */
@RestController
@RequestMapping("/api/toutiao")
@Tag(name = "微头条投递控制器", description = "统一处理微头条内容的排版和投递功能")
public class TTHController {
    private final WebSocketClientService webSocketClientService;

    public TTHController(WebSocketClientService webSocketClientService) {
        this.webSocketClientService = webSocketClientService;
    }

    @Value("${cube.url}")
    private String url;
    // 日志记录工具类
    @Autowired
    private LogMsgUtil logInfo;

    // 浏览器操作工具类
    @Autowired
    private BrowserUtil browserUtil;
    @Autowired
    private TTHUtil tthUtil;
    @Autowired
    private ScreenshotUtil screenshotUtil;

    @PostMapping("/pushToTTH")
    @Operation(summary = "投递内容到微头条", description = "投递内容到微头条")
    public String pushToTTH(@RequestBody UserInfoRequest userInfoRequest) throws IOException {
        String userId = userInfoRequest.getUserId();
        String content = userInfoRequest.getUserPrompt();
        content = content.replaceAll("(\\r\\n)+", "\n");
        BrowserContext tth = browserUtil.createPersistentBrowserContext(false, userId, "tth");
        try {
            Page page = browserUtil.getOrCreatePage(tth);
            String res = tthUtil.checkLoginStatus(page, true);
            if ("false".equals(res)) {
                logInfo.sendTTHFlow("未登录头条号，请先登录！", userId);
                tth.close();
                return "false";
            }
            logInfo.sendTTHFlow("开始发布文章", userId);
            boolean visible = page.locator("//*[name()='path' and contains(@d,'M15 2v1.5H')]").isVisible();
            if (visible) {
                page.locator("//*[name()='path' and contains(@d,'M15 2v1.5H')]").click();
            }
            Locator locator = page.locator("a[href='/profile_v4/weitoutiao/publish']");
            locator.waitFor();
            locator.click();
            logInfo.sendTTHFlow("进入发布页面", userId);

            logInfo.sendTTHFlow("正在填写内容", userId);
            page.locator("//div[@class='ProseMirror']").fill(" ");
            page.keyboard().type(content, new Keyboard.TypeOptions().setDelay(0));
            logInfo.sendTTHFlow("已填写内容", userId);
            logInfo.sendTTHFlow("正在保存草稿", userId);
            page.locator("//span[@class='icon-wrap']//*[name()='svg']").click();
            Thread.sleep(1000);
            page.locator("//span[contains(text(),'存草稿')]").click();
            Thread.sleep(3000);
            logInfo.sendTTHFlow("已保存草稿", userId);
//                截屏
            String url = screenshotUtil.screenshotAndUpload(page, "tthArticle.png");
            JSONObject tthObject = new JSONObject();
            tthObject.put("url", url);
            tthObject.put("userId", userId);
            tthObject.put("type", "RETURN_PC_TTH_IMG");
            webSocketClientService.sendMessage(tthObject.toJSONString());
            tth.close();
            return "success";
        } catch (Exception e) {
            logInfo.sendTTHFlow("发布文章失败: " + e.getMessage(), userId);
            tth.close();
            UserLogUtil.sendExceptionLog(userId, "微头条发布文章", "pushToTTH", e, url + "/saveLogInfo");
        }
        return "false";
    }

}
