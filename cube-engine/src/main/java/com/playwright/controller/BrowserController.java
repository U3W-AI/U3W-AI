package com.playwright.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.playwright.entity.UnPersisBrowserContextInfo;
import com.playwright.utils.*;
import com.playwright.entity.UserInfoRequest;
import com.playwright.websocket.WebSocketClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@RestController
@RequestMapping("/api/browser")
@Tag(name = "AI登录登录控制器", description = "AI登录相关接口")
public class BrowserController {

    // 浏览器操作工具类
    @Autowired
    private ScreenshotUtil screenshotUtil;

    private final WebSocketClientService webSocketClientService;

    @Value("${cube.url}")
    private String logUrl;

    @Autowired
    private TTHUtil tthUtil;

    private final DeepSeekUtil deepSeekUtil;

    @Autowired
    private LogMsgUtil logMsgUtil;

    public BrowserController(WebSocketClientService webSocketClientService, DeepSeekUtil deepSeekUtil) {
        this.webSocketClientService = webSocketClientService;
        this.deepSeekUtil = deepSeekUtil;
    }

    @Autowired
    private BrowserUtil browserUtil;

    @Autowired
    private BaiduUtil baiduUtil;

    /**
     * 检查MiniMax主站登录状态
     * @param userId 用户唯一标识
     * @return 登录状态："false"表示未登录，手机号表示已登录
     */
    @Operation(summary = "检查MiniMax登录状态", description = "返回手机号表示已登录，false 表示未登录")
    @GetMapping("/checkMaxLogin")
    public String checkMaxLogin(@Parameter(description = "用户唯一标识")  @RequestParam("userId") String userId) throws InterruptedException {
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false,userId,"MiniMax Chat")) {
            Page page = browserUtil.getOrCreatePage(context);
            page.navigate("https://chat.minimaxi.com/");
            Thread.sleep(3000);
            Locator loginButton = page.locator("div.text-col_text00.border-col_text00.hover\\:bg-col_text00.hover\\:text-col_text05.flex.h-\\[32px\\].cursor-pointer.items-center.justify-center.rounded-full.border.px-\\[16px\\].text-\\[13px\\].font-medium.leading-\\[17px\\].md\\:h-\\[36px\\]").last();
            if(loginButton.count() == 0){
                // 不存在登录按钮，已登录
                return "已登录";
            } else {
                // 存在登录按钮，未登录
                return "false";
            }

        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * 检查Kimi登录状态
     * @param userId 用户唯一标识
     * @return 登录状态："false"表示未登录，用户昵称表示已登录
     */
    @Operation(summary = "检查Kimi登录状态", description = "返回用户昵称表示已登录，false 表示未登录")
    @GetMapping("/checkKimiLogin")
    public String checkKimiLogin(@Parameter(description = "用户唯一标识") @RequestParam("userId") String userId) throws InterruptedException {
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false, userId, "kimi")) {
            // 1. 打开新页面并访问Kimi网站
            Page page = browserUtil.getOrCreatePage(context);
            page.navigate("https://www.kimi.com/");
            Thread.sleep(5000); // 等待页面加载

            // 2. 检查登录按钮是否存在
            Locator loginLocator = page.locator("span.user-name:has-text('登录')");
            if (loginLocator.count() > 0 && loginLocator.isVisible()) {
                return "false"; // 未登录
            }

            // 3. 已登录情况处理
            page.locator("div.user-info").click(); // 点击用户信息
            Thread.sleep(500);
            page.locator("span:has-text('设置')").click(); // 点击设置
            Thread.sleep(500);

            // 4. 检查用户名元素
            Locator nameLocator = page.locator("div.name");
            if (nameLocator.count() > 0) {
                Thread.sleep(300);
                String nameText = nameLocator.textContent();

                // 双重检查：如果为空再等一次
                if (nameText.isEmpty()) {
                    Thread.sleep(2000);
                    nameText = nameLocator.textContent();
                }

                return nameText.isEmpty() ? "登录" : nameText; // 确保不返回空字符串
            }

            return "false"; // 未找到用户名元素

        } catch (Exception e) {
            throw e;
        }
    }



    /**
     * 检查元宝主站登录状态
     * @param userId 用户唯一标识
     * @return 登录状态："false"表示未登录，手机号表示已登录
     */
    @Operation(summary = "检查元宝登录状态", description = "返回手机号表示已登录，false 表示未登录")
    @GetMapping("/checkLogin")
    public String checkLogin(@Parameter(description = "用户唯一标识")  @RequestParam("userId") String userId) throws InterruptedException {
        try {
            UnPersisBrowserContextInfo browserContextInfo = BrowserContextFactory.getBrowserContext(userId, 2);
            BrowserContext browserContext = browserContextInfo.getBrowserContext();
            Page page = browserContext.pages().get(0);
            page.navigate("https://yuanbao.tencent.com/chat/naQivTmsDa/");
            page.waitForLoadState(LoadState.LOAD);
            Thread.sleep(3000);
            Locator phone = page.locator("//p[@class='nick-info-name']");
            if(phone.count()>0){
                String phoneText = phone.textContent();
                if(phoneText.equals("未登录")){
                    return "false";
                }
                return phoneText;
            }else{
                return "false";
            }
        } catch (Exception e) {
            throw e;
        }
    }




    /**
     * 获取代理版MiniMax登录二维码
     * @param userId 用户唯一标识
     * @return 二维码图片URL 或 "false"表示失败
     */
    @GetMapping("/getMaxQrCode")
    @Operation(summary = "获取代理版MiniMax登录二维码", description = "返回二维码截图 URL 或 false 表示失败")
    public String getMaxQrCode(@Parameter(description = "用户唯一标识") @RequestParam("userId") String userId) throws InterruptedException, IOException {
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false,userId,"MiniMax Chat")) {
            Page page = browserUtil.getOrCreatePage(context);
            page.navigate("https://chat.minimaxi.com/");
            page.locator("xpath=/html/body/section/div/div/section/header/div[2]/div[2]/div[2]/div").click();
            Thread.sleep(3000);
            String url = screenshotUtil.screenshotAndUpload(page,"checkMaxLogin.png");

            JSONObject jsonObject = new JSONObject();
            jsonObject.put("url",url);
            jsonObject.put("userId",userId);
            jsonObject.put("type","RETURN_PC_MAX_QRURL");
            // 发送二维码URL
            webSocketClientService.sendMessage(jsonObject.toJSONString());
            // 查找登录后的元素是否存在，存在则发送登录成功的消息
            Thread.sleep(3000);
            page.locator("body").click();
            // 一直等待登录成功的元素出现
            Locator phone = page.locator(".h-7.w-7.rounded-full").nth(2);
            phone.waitFor(new Locator.WaitForOptions().setTimeout(60000).setState(WaitForSelectorState.ATTACHED));

            if(phone.count()>0){
                JSONObject jsonObjectTwo = new JSONObject();
                jsonObjectTwo.put("status","登录");
                jsonObjectTwo.put("userId",userId);
                jsonObjectTwo.put("type","RETURN_MAX_STATUS");
                webSocketClientService.sendMessage(jsonObjectTwo.toJSONString());
            }

            return url;
        } catch (Exception e) {
            throw e;
        }
    }


    /**
     * 获取KiMi登录二维码
     * @param userId 用户唯一标识
     * @return 二维码图片URL 或 "false"表示失败
     */
    @GetMapping("/getKiMiQrCode")
    @Operation(summary = "获取代理版KiMi登录二维码", description = "返回二维码截图 URL 或 false 表示失败，如果已登录则返回用户信息")
    public String getKiMiQrCode(@Parameter(description = "用户唯一标识") @RequestParam("userId") String userId) throws InterruptedException, IOException {
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false, userId, "KiMi")) {
            Page page = browserUtil.getOrCreatePage(context);

            // 1. 访问Kimi官网
            page.navigate("https://www.kimi.com/");
            Thread.sleep(2000); // 等待页面加载完成

            // 2. 点击用户信息区域
            Locator userLoginLocator = page.locator("div.user-info");
            userLoginLocator.click();
            Thread.sleep(500);

            // 3. 已登录则返回登陆成功的消息
            if (page.locator("span:has-text('设置')").isVisible()){
                page.locator("span:has-text('设置')").click();
                Thread.sleep(500);
                Locator nameLocator = page.locator("div.name");
                String nameText = nameLocator.textContent();
                // 发送登录状态
                JSONObject statusObj = new JSONObject();
                statusObj.put("status", nameText.isEmpty() ? "已登录" : nameText);
                statusObj.put("userId", userId);
                statusObj.put("type", "RETURN_KIMI_STATUS");
                webSocketClientService.sendMessage(statusObj.toJSONString());
                return nameText.isEmpty() ? "已登录" : nameText;
            } else {
                // 4. 截图并发送二维码
                String url = screenshotUtil.screenshotAndUpload(page, "checkKiMiLogin_" + System.currentTimeMillis() + ".png");
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("url", url);
                jsonObject.put("userId", userId);
                jsonObject.put("type", "RETURN_PC_KIMI_QRURL");
                webSocketClientService.sendMessage(jsonObject.toJSONString());

                // 5. 等待登录完成（最长30秒）
                for (int i = 0; i < 30; i++) {
                    if (!page.locator("div.login-modal-mask").isVisible()) {
                        // 登录成功检查
                        userLoginLocator.click();
                        Thread.sleep(1000);

                        Locator settingsLocator = page.locator("span:has-text('设置')");
                        if (settingsLocator.count() > 0) {
                            settingsLocator.click();
                            Thread.sleep(1000);
                            Locator nameLocator = page.locator("div.name");
                            String nameText = nameLocator.textContent();
                            // 双重检查用户名
                            if (nameText.isEmpty()) {
                                nameText = nameLocator.textContent();
                                Thread.sleep(2000);
                            }
                            // 发送登录状态
                            JSONObject statusObj = new JSONObject();
                            statusObj.put("status", nameText.isEmpty() ? "已登录" : nameText);
                            statusObj.put("userId", userId);
                            statusObj.put("type", "RETURN_KIMI_STATUS");
                            webSocketClientService.sendMessage(statusObj.toJSONString());
                            break;
                        }
                    }
                    // 每次循环间隔
                    Thread.sleep(1000);
                }
                return url;
            }

        } catch (Exception e) {
            throw e;
        }
    }



    /**
     * 获取代理版元宝登录二维码
     * @param userId 用户唯一标识
     * @return 二维码图片URL 或 "false"表示失败
     */
    @GetMapping("/getYBQrCode")
    @Operation(summary = "获取代理版元宝登录二维码", description = "返回二维码截图 URL 或 false 表示失败")
    public String getYBQrCode(@Parameter(description = "用户唯一标识") @RequestParam("userId") String userId) throws InterruptedException, IOException {
        try {
            UnPersisBrowserContextInfo browserContextInfo = BrowserContextFactory.getBrowserContext(userId, 2);
            BrowserContext context = null;
            if (browserContextInfo != null) {
                context = browserContextInfo.getBrowserContext();
            }
            Page page = context.pages().get(0);
            page.navigate("https://yuanbao.tencent.com/chat/naQivTmsDa");
            page.locator("//span[contains(text(),'登录')]").click();
            Thread.sleep(4000);
            boolean isLogin = false;
            String url = screenshotUtil.screenshotAndUpload(page,"checkYBLogin.png");
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("url",url);
            jsonObject.put("userId",userId);
            jsonObject.put("type","RETURN_PC_YB_QRURL");
            webSocketClientService.sendMessage(jsonObject.toJSONString());
            Locator phone = page.locator("//p[@class='nick-info-name']");
            String phoneText = phone.textContent();
            for(int i = 0; i < 6; i++) {
//                每十秒刷新一次
                if(phoneText.contains("未登录")) {
                    Thread.sleep(10000);
                    url = screenshotUtil.screenshotAndUpload(page,"checkYBLogin.png");
                    jsonObject.put("url",url);
                    webSocketClientService.sendMessage(jsonObject.toJSONString());
                } else {
                    break;
                }
                phoneText = phone.textContent();
            }
            if(phone.count()>0){
                JSONObject jsonObjectTwo = new JSONObject();
                if(phoneText.contains("未登录")) {
                    jsonObjectTwo.put("status","false");
                } else {
                    isLogin = true;
                    jsonObjectTwo.put("status",phoneText);
                }
                jsonObjectTwo.put("userId",userId);
                jsonObjectTwo.put("type","RETURN_YB_STATUS");
                webSocketClientService.sendMessage(jsonObjectTwo.toJSONString());
            }
            return isLogin ? phoneText : "false";
        } catch (Exception e) {
            throw e;
        }
    }


    /**
     * 检查秘塔登录状态
     * @param userId 用户唯一标识
     * @return 登录状态："false"表示未登录，手机号表示已登录
     */
    @Operation(summary = "检查秘塔登录状态", description = "返回登录表示已登录，false 表示未登录")
    @GetMapping("/checkMetasoLogin")
    public String checkMetasoLogin(@Parameter(description = "用户唯一标识") @RequestParam("userId") String userId) throws InterruptedException {
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false,userId,"metaso")) {
            Page page = browserUtil.getOrCreatePage(context);
            page.navigate("https://metaso.cn/");
            Thread.sleep(5000);
            Locator loginButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("登录/注册"));

            if(loginButton.count() > 0 && loginButton.isVisible()){
                // 存在登录按钮，未登录
                return "false";
            } else {
                Thread.sleep(500);
                Locator phone = page.locator("#left-menu > div > div.LeftMenu_footer__qsJdJ > div > div > div > span");
                if(phone.count()>0){
                    String phoneText = phone.textContent();
                    return phoneText;
                } else {
                    return "false";
                }
            }

        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * 获取秘塔登录二维码
     * @param userId 用户唯一标识
     * @return 二维码图片URL 或 "false"表示失败
     */
    @Operation(summary = "获取秘塔登录二维码", description = "返回二维码截图 URL 或 false 表示失败")
    @GetMapping("/getMetasoQrCode")
    public String getMetasoQrCode(@Parameter(description = "用户唯一标识") @RequestParam("userId") String userId) throws InterruptedException, IOException {
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false,userId,"metaso")) {
            Page page = browserUtil.getOrCreatePage(context);
            page.navigate("https://metaso.cn/");
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("登录/注册")).click();
            Thread.sleep(3000);
            String url = screenshotUtil.screenshotAndUpload(page,"checkMetasoLogin.png");

            JSONObject jsonObject = new JSONObject();
            jsonObject.put("url",url);
            jsonObject.put("userId",userId);
            jsonObject.put("type","RETURN_PC_METASO_QRURL");
            // 发送二维码URL
            webSocketClientService.sendMessage(jsonObject.toJSONString());
            //  查找登录后的元素是否存在，不存在则发送登录成功的消息
            Thread.sleep(3000);
            Locator login = page.locator("#left-menu > div > div.LeftMenu_footer__qsJdJ > div > div > div > span");
            Locator phone = page.locator("#left-menu > div > div.LeftMenu_footer__qsJdJ > div > div > div > span");
            login.waitFor(new Locator.WaitForOptions().setTimeout(60000));
            Thread.sleep(3000);
            if (phone.count() > 0){
                JSONObject jsonObjectTwo = new JSONObject();
                jsonObjectTwo.put("status",phone.textContent());
                jsonObjectTwo.put("userId",userId);
                jsonObjectTwo.put("type","RETURN_METASO_STATUS");
                webSocketClientService.sendMessage(jsonObjectTwo.toJSONString());
            }

            return url;

        } catch (Exception e) {
            throw e;
        }
    }


    /**
     * 检查豆包登录状态
     * @param userId 用户唯一标识
     * @return 登录状态："false"表示未登录，手机号表示已登录
     */
    @Operation(summary = "检查豆包登录状态", description = "返回手机号表示已登录，false 表示未登录")
    @GetMapping("/checkDBLogin")
    public String checkDBLogin(@Parameter(description = "用户唯一标识") @RequestParam("userId") String userId) throws InterruptedException {
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false,userId,"db")) {
            Page page = browserUtil.getOrCreatePage(context);
            page.navigate("https://www.doubao.com/chat/");
            Thread.sleep(5000);
            Locator locator = page.locator("//*[@id=\"root\"]/div[1]/div/div[3]/div/main/div/div/div[1]/div/div/div/div[2]/div/button");
            if (locator.count() > 0 && locator.isVisible()) {
                return "false";
            } else {
                Thread.sleep(500);

                page.locator("[data-testid=\"chat_header_avatar_button\"]").click();
                Thread.sleep(500);
                page.locator("[data-testid=\"chat_header_setting_button\"]").click();
                Thread.sleep(500);
                Locator phone = page.locator(".nickName-cIcGuG");
                if(phone.count()>0){
                    String phoneText = phone.textContent();
                    return phoneText;
                }else{
                    return "false";
                }
            }
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * 获取豆包登录二维码
     * @param userId 用户唯一标识
     * @return 二维码图片URL 或 "false"表示失败
     */
    @Operation(summary = "获取豆包登录二维码", description = "返回二维码截图 URL 或 false 表示失败")
    @GetMapping("/getDBQrCode")
    public String getDBQrCode(@Parameter(description = "用户唯一标识") @RequestParam("userId") String userId) throws InterruptedException, IOException {
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false,userId,"db")) {
            Page page = browserUtil.getOrCreatePage(context);
            page.navigate("https://www.doubao.com/chat/");
            Locator locator = page.locator("//*[@id=\"root\"]/div[1]/div/div[3]/div/main/div/div/div[1]/div/div/div/div[2]/div/button");
            Thread.sleep(2000);
            if (locator.count() > 0 && locator.isVisible()) {
                locator.click();
                page.locator("[data-testid='qrcode_switcher']").evaluate("el => el.click()");

                Thread.sleep(3000);
                String url = screenshotUtil.screenshotAndUpload(page,"checkDBLogin.png");

                JSONObject jsonObject = new JSONObject();
                jsonObject.put("url",url);
                jsonObject.put("userId",userId);
                jsonObject.put("type","RETURN_PC_DB_QRURL");
                webSocketClientService.sendMessage(jsonObject.toJSONString());
                Locator login = page.getByText("登录成功");
                login.waitFor(new Locator.WaitForOptions().setTimeout(60000));
                Thread.sleep(5000);
                page.locator("[data-testid=\"chat_header_avatar_button\"]").click();
                Thread.sleep(1000);
                page.locator("[data-testid=\"chat_header_setting_button\"]").click();
                Thread.sleep(1000);
                Locator phone = page.locator(".nickName-cIcGuG");
                if(phone.count()>0){
                    String phoneText = phone.textContent();
                    JSONObject jsonObjectTwo = new JSONObject();
                    jsonObjectTwo.put("status",phoneText);
                    jsonObjectTwo.put("userId",userId);
                    jsonObjectTwo.put("type","RETURN_DB_STATUS");
                    webSocketClientService.sendMessage(jsonObjectTwo.toJSONString());
                }
            }
        } catch (Exception e) {
            throw e;
        }
        return "false";
    }

    /**
     * 退出腾讯元宝
     */
    @Operation(summary = "退出腾讯元宝登录状态", description = "执行退出操作，返回true表示成功")
    @GetMapping("/loginOut")
    public boolean loginOut(@Parameter(description = "用户唯一标识") @RequestParam("userId") String userId) throws InterruptedException {
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false,userId,"yb")) {
            Page page = browserUtil.getOrCreatePage(context);
            page.navigate("https://yuanbao.tencent.com/chat/naQivTmsDa");
            page.click("span.icon-yb-setting");
            page.click("text=退出登录");
            page.locator("//*[@id=\"hunyuan-bot\"]/div[2]/div/div[2]/div/div/div[3]/button[2]").click();
            Thread.sleep(3000);
            return true;
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * 检查DeepSeek登录状态
     * @param userId 用户唯一标识
     * @return 登录状态："false"表示未登录，用户昵称表示已登录
     */
    @Operation(summary = "检查DeepSeek登录状态", description = "返回用户昵称表示已登录，false 表示未登录")
    @GetMapping("/checkDeepSeekLogin")
    public String checkDeepSeekLogin(@Parameter(description = "用户唯一标识") @RequestParam("userId") String userId) {
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false, userId, "deepseek")) {
            Page page = browserUtil.getOrCreatePage(context);

            // 导航到DeepSeek页面并确保完全加载
            page.navigate("https://chat.deepseek.com/");
            page.waitForLoadState();
            page.waitForTimeout(1500); // 额外等待1.5秒确保页面完全渲染

            // 多次尝试检测登录状态，最多尝试3次
            for (int attempt = 0; attempt < 3; attempt++) {
                // 先使用工具类方法检测
                String loginStatus = deepSeekUtil.checkLoginStatus(page, false);

                // 如果检测到已登录，直接返回
                if (!"false".equals(loginStatus)) {
                    logMsgUtil.sendTaskLog("DeepSeek已登录，用户: " + loginStatus, userId, "DeepSeek");
                    return loginStatus;
                }

                // 如果当前尝试失败，但还有更多尝试，等待后重试
                if (attempt < 2) {
                    page.waitForTimeout(1000);
                    // 刷新页面重试
                    page.reload();
                    page.waitForLoadState();
                    page.waitForTimeout(1500);
                }
            }

            // 所有尝试都失败，返回未登录状态
            return "false";
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * 获取DeepSeek登录二维码
     * @param userId 用户唯一标识
     * @return 二维码图片URL 或 "false"表示失败
     */
    @Operation(summary = "获取DeepSeek登录二维码", description = "返回二维码截图 URL 或 false 表示失败")
    @GetMapping("/getDeepSeekQrCode")
    public String getDeepSeekQrCode(@Parameter(description = "用户唯一标识") @RequestParam("userId") String userId) throws InterruptedException, IOException {
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false, userId, "deepseek")) {
            Page page = browserUtil.getOrCreatePage(context);

            // 首先检查当前登录状态
            String currentStatus = deepSeekUtil.checkLoginStatus(page, true);
            if (!"false".equals(currentStatus)) {
                // 已经登录，直接返回状态
                JSONObject statusObject = new JSONObject();
                statusObject.put("status", currentStatus);
                statusObject.put("userId", userId);
                statusObject.put("type", "RETURN_DEEPSEEK_STATUS");
                webSocketClientService.sendMessage(statusObject.toJSONString());
                logMsgUtil.sendTaskLog("DeepSeek已登录，用户: " + currentStatus, userId, "DeepSeek");

                // 截图返回当前页面
                return screenshotUtil.screenshotAndUpload(page, "deepseekLoggedIn.png");
            }

            // 未登录，获取二维码截图URL
            String url = deepSeekUtil.waitAndGetQRCode(page, userId, screenshotUtil);

            if (!"false".equals(url)) {
                // 发送二维码URL到WebSocket
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("url", url);
                jsonObject.put("userId", userId);
                jsonObject.put("type", "RETURN_PC_DEEPSEEK_QRURL");
                webSocketClientService.sendMessage(jsonObject.toJSONString());

                // 实时监测登录状态 - 最多等待60秒
                int maxAttempts = 30; // 30次尝试
                for (int i = 0; i < maxAttempts; i++) {
                    // 每2秒检查一次登录状态（不刷新页面）
                    Thread.sleep(2000);

                    // 检查当前页面登录状态
                    String loginStatus = deepSeekUtil.checkLoginStatus(page, false);

                    if (!"false".equals(loginStatus)) {
                        // 登录成功，发送状态到WebSocket
                        JSONObject jsonObjectTwo = new JSONObject();
                        jsonObjectTwo.put("status", loginStatus);
                        jsonObjectTwo.put("userId", userId);
                        jsonObjectTwo.put("type", "RETURN_DEEPSEEK_STATUS");
                        webSocketClientService.sendMessage(jsonObjectTwo.toJSONString());

                        // 登录成功，跳出循环
                        logMsgUtil.sendTaskLog("DeepSeek登录成功: " + loginStatus, userId, "DeepSeek");
                        break;
                    }

                    // 每5次尝试重新截图一次，可能二维码已更新
                    if (i % 5 == 4) {
                        try {
                            url = screenshotUtil.screenshotAndUpload(page, "checkDeepSeekLogin.png");
                            JSONObject qrUpdateObject = new JSONObject();
                            qrUpdateObject.put("url", url);
                            qrUpdateObject.put("userId", userId);
                            qrUpdateObject.put("type", "RETURN_PC_DEEPSEEK_QRURL");
                            webSocketClientService.sendMessage(qrUpdateObject.toJSONString());
                        } catch (Exception e) {
                            UserLogUtil.sendExceptionLog(userId, "deepSeek获取二维码截图失败", "checkDeepSeekLogin", e, logUrl + "/saveLogInfo");
                        }
                    }
                }

                return url;
            }
        } catch (Exception e) {
            logMsgUtil.sendTaskLog("获取DeepSeek登录二维码失败: " + e.getMessage(), userId, "DeepSeek");
            throw e;
        }
        return "false";
    }


    /**
     * 检查通义AI登录状态
     * @param userId 用户唯一标识
     * @return 登录状态："false"表示未登录，加密的用户名/手机号表示已登录
     */
    @Operation(summary = "检查通义AI登录状态", description = "返回用户名/手机号表示已登录，false 表示未登录")
    @GetMapping("/checkTongYiLogin")
    public String checkTongYiLogin(@Parameter(description = "用户唯一标识") @RequestParam("userId") String userId) {
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false, userId, "ty")) {
            Page page = browserUtil.getOrCreatePage(context);
            page.navigate("https://www.tongyi.com/");
            page.waitForTimeout(5000);

            Locator loginButton = page.locator("//*[@id=\"new-nav-tab-wrapper\"]/div[2]/li");

            if (loginButton.count() > 0 && loginButton.isVisible()) {
                // 如果找到“登录”按钮，说明未登录
                return "false";
            } else {
                Locator userAvatarArea = page.locator(".popupUser");
                if (userAvatarArea.count() > 0) {
                    userAvatarArea.hover();
                    page.waitForTimeout(1000);

                    Locator userNameElement = page.locator(".userName");
                    if (userNameElement.count() > 0 && userNameElement.isVisible()) {
                        // 返回获取到的用户名
                        return userNameElement.textContent();
                    }
                }
                return "false";
            }
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * 获取通义千问登录二维码
     * @param userId 用户唯一标识
     * @return 二维码图片URL 或 "false"表示失败
     */
    @Operation(summary = "获取通义千问登录二维码", description = "返回二维码截图 URL 或 false 表示失败")
    @GetMapping("/getTongYiQrCode")
    public String getTongYiQrCode(@Parameter(description = "用户唯一标识") @RequestParam("userId") String userId) throws IOException {
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false, userId, "ty")) {
            Page page = browserUtil.getOrCreatePage(context);
            page.navigate("https://www.tongyi.com/");
            page.waitForTimeout(3000);
            Locator loginButton = page.locator("//*[@id=\"new-nav-tab-wrapper\"]/div[2]/li");
            if (loginButton.count() > 0 && loginButton.isVisible()) {
                loginButton.click();
                page.waitForTimeout(2000);

                page.locator("div[class*='qrcodeWrapper']").last().waitFor(new Locator.WaitForOptions().setTimeout(10000));

                String url = screenshotUtil.screenshotAndUpload(page, "checkTongYiLogin.png");

                JSONObject jsonObject = new JSONObject();
                jsonObject.put("url", url);
                jsonObject.put("userId", userId);
                jsonObject.put("type", "RETURN_PC_QW_QRURL");
                webSocketClientService.sendMessage(jsonObject.toJSONString());

                Locator userAvatarArea = page.locator(".popupUser");
                userAvatarArea.waitFor(new Locator.WaitForOptions().setTimeout(60000));

                page.waitForTimeout(3000);

                if (userAvatarArea.count() > 0) {
                    userAvatarArea.hover();
                    page.waitForTimeout(1000);

                    Locator userNameElement = page.locator(".userName");
                    if (userNameElement.count() > 0 && userNameElement.isVisible()) {
                        JSONObject jsonObjectTwo = new JSONObject();
                        jsonObjectTwo.put("status", userNameElement.textContent());
                        jsonObjectTwo.put("userId", userId);
                        jsonObjectTwo.put("type", "RETURN_TY_STATUS");
                        webSocketClientService.sendMessage(jsonObjectTwo.toJSONString());
                    }
                }
            }
        } catch (Exception e) {
            throw e;
        }
        return "false";
    }

    @Operation(summary = "检查百度AI登录状态", description = "返回用户名/手机号表示已登录，false 表示未登录")
    @GetMapping("/checkBaiduLogin")
    public String checkBaiduLogin(@Parameter(description = "用户唯一标识") @RequestParam("userId") String userId) throws Exception {
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false, userId, "baidu")) {
            Page page = browserUtil.getOrCreatePage(context);

            // 使用BaiduUtil检查登录状态
            String loginStatus = baiduUtil.checkBaiduLogin(page, true);

            if (!"false".equals(loginStatus)) {
                return loginStatus; // 返回用户名或登录状态
            } else {
                return "false"; // 未登录
            }

        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * 获取百度登录二维码
     * @param userId
     */
    @Operation(summary = "获取百度登录二维码", description = "返回二维码截图 URL 或 false 表示失败")
    @GetMapping("/getBaiduQrCode")
    public String getBaiduQrCode(@Parameter(description = "用户唯一标识") @RequestParam("userId") String userId) {
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false, userId, "baidu")) {
            Page page = browserUtil.getOrCreatePage(context);
            // 首先检查当前登录状态
            String currentStatus = baiduUtil.checkBaiduLogin(page, true);
            if (!"false".equals(currentStatus)) {
                // 已经登录，直接返回状态
                JSONObject statusObject = new JSONObject();
                statusObject.put("status", currentStatus);
                statusObject.put("userId", userId);
                statusObject.put("type", "RETURN_BAIDU_STATUS");
                webSocketClientService.sendMessage(statusObject.toJSONString());
                logMsgUtil.sendTaskLog("百度AI已登录，用户: " + currentStatus, userId, "百度AI");

                // 截图返回当前页面
                String url = screenshotUtil.screenshotAndUpload(page, "getBaiduLoggedIn.png");
                JSONObject qrUpdateObject = new JSONObject();
                qrUpdateObject.put("url", url);
                qrUpdateObject.put("userId", userId);
                qrUpdateObject.put("type", "RETURN_PC_BAIDU_QRURL");
                webSocketClientService.sendMessage(qrUpdateObject.toJSONString());
                return url;
            }

            // 未登录，使用BaiduUtil获取二维码
            String url = baiduUtil.waitAndGetQRCode(page, userId);

            if (url != null && !url.trim().isEmpty()) {
                // 发送二维码截图
                JSONObject qrUpdateObject = new JSONObject();
                qrUpdateObject.put("url", url);
                qrUpdateObject.put("userId", userId);
                qrUpdateObject.put("type", "RETURN_PC_BAIDU_QRURL");
                webSocketClientService.sendMessage(qrUpdateObject.toJSONString());

                // 实时监测登录状态 - 最多等待60秒
                int maxAttempts = 30; // 30次尝试，每次2秒
                for (int i = 0; i < maxAttempts; i++) {
                    Thread.sleep(2000);

                    // 检查当前页面登录状态
                    String loginStatus = baiduUtil.checkBaiduLogin(page, false);

                    if (!"false".equals(loginStatus)) {
                        // 登录成功，发送状态到WebSocket
                        JSONObject statusSuccessObject = new JSONObject();
                        statusSuccessObject.put("status", loginStatus);
                        statusSuccessObject.put("userId", userId);
                        statusSuccessObject.put("type", "RETURN_BAIDU_STATUS");
                        webSocketClientService.sendMessage(statusSuccessObject.toJSONString());

                        logMsgUtil.sendTaskLog("百度AI登录成功: " + loginStatus, userId, "百度AI");
                        break;
                    }

                    // 每5次尝试重新截图一次，可能二维码已更新
                    if (i % 5 == 4) {
                        try {
                            String newUrl = screenshotUtil.screenshotAndUpload(page, "getBaiduQrCode_refresh.png");
                            JSONObject qrRefreshObject = new JSONObject();
                            qrRefreshObject.put("url", newUrl);
                            qrRefreshObject.put("userId", userId);
                            qrRefreshObject.put("type", "RETURN_PC_BAIDU_QRURL");
                            webSocketClientService.sendMessage(qrRefreshObject.toJSONString());
                        } catch (Exception e) {
                            UserLogUtil.sendExceptionLog(userId, "获取百度AI二维码", "getBaiduQrCode", e, logUrl + "/saveLogInfo");
                        }
                    }
                }
                return url;
            } else {
                logMsgUtil.sendTaskLog("获取百度AI二维码失败", userId, "百度AI");
                // 发送失败消息到前端
                JSONObject errorObject = new JSONObject();
                errorObject.put("url", "");
                errorObject.put("userId", userId);
                errorObject.put("type", "RETURN_PC_BAIDU_QRURL");
                errorObject.put("error", "获取二维码失败");
                webSocketClientService.sendMessage(errorObject.toJSONString());
                return "false";
            }

        } catch (Exception e) {
            logMsgUtil.sendTaskLog("获取百度AI二维码失败", userId, "百度AI");
            // 发送异常消息到前端
            JSONObject errorObject = new JSONObject();
            errorObject.put("url", "");
            errorObject.put("userId", userId);
            errorObject.put("type", "RETURN_PC_BAIDU_QRURL");
            errorObject.put("error", "获取二维码异常");
            webSocketClientService.sendMessage(errorObject.toJSONString());
            UserLogUtil.sendExceptionLog(userId, "获取百度AI二维码", "getBaiduQrCode", e, logUrl + "/saveLogInfo");
            return "false";
        }
    }

}