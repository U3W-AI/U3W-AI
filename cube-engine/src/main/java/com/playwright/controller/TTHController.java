package com.playwright.controller;

import com.alibaba.fastjson.JSONObject;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Keyboard;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.playwright.utils.BrowserUtil;
import com.playwright.utils.LogMsgUtil;
import com.playwright.utils.ScreenshotUtil;
import com.playwright.utils.TTHUtil;
import com.playwright.websocket.WebSocketClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * description:  TODO
 * dateStart 2024/8/4 9:34
 * dateNow   2025/7/20 13:52
 */
@RestController
@RequestMapping("/api/toutiao")
public class TTHController {
    private final WebSocketClientService webSocketClientService;

    public TTHController(WebSocketClientService webSocketClientService) {
        this.webSocketClientService = webSocketClientService;
    }

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
    public String pushToTTH(@RequestBody Map map) throws InterruptedException {
        Integer i = (Integer) map.get("userId");
        String userId = i.toString();
        String title = (String) map.get("title");
        String content = (String) map.get("content");
        content = content.replaceAll("(\\r\\n)+", "\n");
        BrowserContext tth = browserUtil.createPersistentBrowserContext(false, userId, "tth");
        Page page = tth.newPage();
        String res = tthUtil.checkLoginStatus(page, true);
        if ("false".equals(res)) {
            logInfo.sendTTHFlow("未登录头条号，请先登录！", userId);
            tth.close();
            return "false";
        }
        logInfo.sendTTHFlow("开始发布文章", userId);
        Locator locator = page.locator("a[href='/profile_v4/weitoutiao/publish']");
        locator.waitFor();
        locator.click();
        logInfo.sendTTHFlow("进入发布页面", userId);

        logInfo.sendTTHFlow("正在填写内容", userId);
        page.locator("//div[@class='ProseMirror']").fill(" ");
        page.keyboard().type(title + content, new Keyboard.TypeOptions().setDelay(0));
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
    }

}
