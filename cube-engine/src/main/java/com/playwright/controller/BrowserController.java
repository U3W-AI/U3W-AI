package com.playwright.controller;

import com.alibaba.fastjson.JSONObject;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.playwright.entity.UnPersisBrowserContextInfo;
import com.playwright.utils.*;
import com.playwright.websocket.WebSocketClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;


@RestController
@RequestMapping("/api/browser")
@Tag(name = "AI登录登录控制器", description = "AI登录相关接口")
@Slf4j
public class BrowserController {

    @Autowired
    private MetasoUtil metasoUtil;
    // 浏览器操作工具类
    @Autowired
    private ScreenshotUtil screenshotUtil;

    @Autowired
    private WebSocketClientService webSocketClientService;

    @Value("${cube.url}")
    private String logUrl;

    @Autowired
    private LogMsgUtil logMsgUtil;

    @Autowired
    private BrowserUtil browserUtil;

    @Autowired
    private BaiduUtil baiduUtil;

    @Autowired
    private DeepSeekUtil deepSeekUtil;

    @Autowired
    private ZhiHuUtil zhiHuUtil;
    @Autowired
    private TongYiUtil tongYiUtil;

    @Value("${cube.url}")
    private String url;
    public static final ConcurrentHashMap<String, String> loginMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Integer> lockMap = new ConcurrentHashMap<>();


    /**
     * 获取秘塔登录二维码
     *
     * @param userId 用户唯一标识
     * @return 二维码图片URL 或 "false"表示失败
     */
    @Operation(summary = "获取秘塔登录二维码", description = "返回二维码截图 URL 或 false 表示失败")
    @GetMapping("/getMetasoQrCode")
    public String getMetasoQrCode(@Parameter(description = "用户唯一标识") @RequestParam("userId") String userId) throws InterruptedException, IOException {
        String key = userId + "-mt";
        if (loginMap.containsKey(key)) {
            JSONObject jsonObjectTwo = new JSONObject();
            jsonObjectTwo.put("status",loginMap.get(key));
            jsonObjectTwo.put("userId",userId);
            jsonObjectTwo.put("type","RETURN_METASO_STATUS");
            webSocketClientService.sendMessage(jsonObjectTwo.toJSONString());
            return loginMap.get(key);
        }
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false, userId, "metaso")) {
            Page page = browserUtil.getOrCreatePage(context);
            page.navigate("https://metaso.cn/");
            Thread.sleep(2000);
            String s = metasoUtil.checkLogin(page, userId);
//            未登录
            if (s == null) {
//                每20秒刷新一次二维码
                for (int j = 0; j < 3; j++) {
                    Locator loginLocator = page.locator("//button[contains(text(),'登录/注册')]");
                    loginLocator.click();
                    Thread.sleep(3000);
                    String url = screenshotUtil.screenshotAndUpload(page, "checkMetasoLogin.png");
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("url", url);
                    jsonObject.put("userId", userId);
                    jsonObject.put("type", "RETURN_PC_METASO_QRURL");
                    // 发送二维码URL
                    webSocketClientService.sendMessage(jsonObject.toJSONString());
                    for (int i = 0; i < 10; i++) {
//                每两秒检擦一次登陆状态
                        Thread.sleep(2000);
                        String userName = metasoUtil.checkLogin(page, userId);
                        if (userName != null) {
                            loginMap.put(key, s);
                            return userName;
                        }
                    }
                }
            }
            return s;
        } catch (Exception e) {
            throw e;
        }
    }


    /**
     * 检查秘塔登录状态
     *
     * @param userId 用户唯一标识
     * @return 登录状态："false"表示未登录，手机号表示已登录
     */
    @Operation(summary = "检查秘塔登录状态", description = "返回登录表示已登录，false 表示未登录")
    @GetMapping("/checkMetasoLogin")
    public String checkMetasoLogin(@Parameter(description = "用户唯一标识") @RequestParam("userId") String userId) throws InterruptedException {
        String key = userId + "-mt";
        if (loginMap.containsKey(key)) {
            // 如果当前用户正在处理，则返回"处理中"
            return loginMap.get(key);
        }
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false, userId, "metaso")) {
            Page page = browserUtil.getOrCreatePage(context);
            page.navigate("https://metaso.cn/");
            Thread.sleep(5000);
            String s = metasoUtil.checkLogin(page, userId);
            if (s == null) {
                return "false";
            }
            loginMap.put(key, s);
            return s;
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * 检查通义AI登录状态
     *
     * @param userId 用户唯一标识
     * @return 登录状态："false"表示未登录，加密的用户名/手机号表示已登录
     */
    @Operation(summary = "检查通义AI登录状态", description = "返回用户名/手机号表示已登录，false 表示未登录")
    @GetMapping("/checkTongYiLogin")
    public String checkTongYiLogin(@Parameter(description = "用户唯一标识") @RequestParam("userId") String userId) {
        String key = userId + "-ty";
        if (loginMap.containsKey(key)) {
            // 如果当前用户正在处理，则返回"处理中"
            return loginMap.get(key);
        }
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
                        loginMap.put(key, userNameElement.textContent());
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
     *
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
//            Locator loginButton = page.locator("(//button[contains(text(),'立即登录')])");
            Locator loginButton = page.locator("(//span[contains(text(),'立即登录')])[1]");
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


    /**
     * 检查DeepSeek登录状态
     *
     * @param userId 用户唯一标识
     * @return 登录状态："false"表示未登录，手机号表示已登录
     */
    @Operation(summary = "检查DeepSeek登录状态", description = "返回手机号表示已登录，false 表示未登录")
    @GetMapping("/checkDSLogin")
    public String checkDSLogin(@Parameter(description = "用户唯一标识") @RequestParam("userId") String userId) throws InterruptedException {
        String key = userId + "-ds";
        if (loginMap.containsKey(key)) {
            // 如果当前用户正在处理，则返回"处理中"
            return loginMap.get(key);
        }
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false, userId, "deepseek")) {
            Page page = browserUtil.getOrCreatePage(context);

            // 导航到DeepSeek页面并确保完全加载
            page.navigate("https://chat.deepseek.com/");
            page.waitForLoadState();
            page.waitForTimeout(1500); // 额外等待1.5秒确保页面完全渲染

            // 先使用工具类方法检测
            String loginStatus = deepSeekUtil.checkLoginStatus(page, false);

            // 如果检测到已登录，直接返回
            if (!"false".equals(loginStatus) || !"未登录".equals(loginStatus)) {
                logMsgUtil.sendTaskLog("DeepSeek已登录，用户: " + loginStatus, userId, "DeepSeek");
                loginMap.put(key, loginStatus);
                return loginStatus;
            }

            // 所有尝试都失败，返回未登录状态
            return "false";
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * 获取DeepSeek登录二维码
     *
     * @param userId 用户唯一标识
     * @return 二维码图片URL 或 "false"表示失败
     */
    @Operation(summary = "获取DeepSeek登录二维码", description = "返回二维码截图 URL 或 false 表示失败")
    @GetMapping("/getDSQrCode")
    public String getDSQrCode(@Parameter(description = "用户唯一标识") @RequestParam("userId") String userId) throws Exception, IOException {
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
     * 检查元宝主站登录状态
     *
     * @param userId 用户唯一标识
     * @return 登录状态："false"表示未登录，手机号表示已登录
     */
    @Operation(summary = "检查元宝登录状态", description = "返回手机号表示已登录，false 表示未登录")
    @GetMapping("/checkLogin")
    public String checkYBLogin(@Parameter(description = "用户唯一标识") @RequestParam("userId") String userId) throws InterruptedException {
        try {
            String key = userId + "-yb";
//            加锁，同一个用户只能有一个检查
            if ((loginMap.get(key) == null || loginMap.get(key).contains("未登录")) && lockMap.get(key) == null) {
                loginMap.remove(key);
                lockMap.put(key, 1);
                UnPersisBrowserContextInfo browserContextInfo = BrowserContextFactory.getBrowserContext(userId, 2);
                BrowserContext browserContext = null;
                if (browserContextInfo != null) {
                    browserContext = browserContextInfo.getBrowserContext();
                }
                Page page = browserContext.pages().get(0);
                page.navigate("https://yuanbao.tencent.com/chat/naQivTmsDa/");
                page.waitForLoadState(LoadState.LOAD);
                Thread.sleep(3000);
                Locator phone = page.locator("//p[@class='nick-info-name']");
                if (phone.count() > 0) {
                    String phoneText = phone.textContent();
                    if (phoneText.equals("未登录")) {
                        loginMap.put(key, "未登录");
                        lockMap.remove(key);
                        return "false";
                    }
                    loginMap.put(key, phoneText);
                    lockMap.remove(key);
                    return phoneText;
                } else {
                    loginMap.put(key, "未登录");
                    lockMap.remove(key);
                    return "false";
                }
            } else {
                log.info("已有其他线程检测,等待登录状态变化");
                // 等待其他线程检测登录状态
                for (int i = 0; i < 10; i++) {
                    if (loginMap.get(key) != null) {
                        if (loginMap.get(key).contains("未登录")) {
                            log.info("检测到未登录");
                            return "false";
                        } else {
                            log.info("检测到已登录");
                            return loginMap.get(key);
                        }
                    }
                    Thread.sleep(3000);
                }
                log.info("检测超时");
                return "false";
            }
        } catch (Exception e) {
            throw e;
        }
    }


    /**
     * 获取代理版元宝登录二维码
     *
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
            String url = screenshotUtil.screenshotAndUpload(page, "checkYBLogin.png");
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("url", url);
            jsonObject.put("userId", userId);
            jsonObject.put("type", "RETURN_PC_YB_QRURL");
            webSocketClientService.sendMessage(jsonObject.toJSONString());
            Locator phone = page.locator("//p[@class='nick-info-name']");
            String phoneText = phone.textContent();
            for (int i = 0; i < 6; i++) {
//                每十秒刷新一次
                if (phoneText.contains("未登录")) {
                    Thread.sleep(10000);
                    url = screenshotUtil.screenshotAndUpload(page, "checkYBLogin.png");
                    jsonObject.put("url", url);
                    webSocketClientService.sendMessage(jsonObject.toJSONString());
                } else {
                    break;
                }
                phoneText = phone.textContent();
            }
            if (phone.count() > 0) {
                JSONObject jsonObjectTwo = new JSONObject();
                if (phoneText.contains("未登录")) {
                    jsonObjectTwo.put("status", "false");
                } else {
                    isLogin = true;
                    jsonObjectTwo.put("status", phoneText);
                }
                jsonObjectTwo.put("userId", userId);
                jsonObjectTwo.put("type", "RETURN_YB_STATUS");
                webSocketClientService.sendMessage(jsonObjectTwo.toJSONString());
            }
            return isLogin ? phoneText : "false";
        } catch (Exception e) {
            throw e;
        }
    }


    /**
     * 检查豆包登录状态
     *
     * @param userId 用户唯一标识
     * @return 登录状态："false"表示未登录，手机号表示已登录
     */
    @Operation(summary = "检查豆包登录状态", description = "返回手机号表示已登录，false 表示未登录")
    @GetMapping("/checkDBLogin")
    public String checkDBLogin(@Parameter(description = "用户唯一标识") @RequestParam("userId") String userId) throws InterruptedException {
        String key = userId + "-db";
        if (loginMap.containsKey(key)) {
            // 如果当前用户正在处理，则返回"处理中"
            return loginMap.get(key);
        }
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false, userId, "db")) {
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
//                Thread.sleep(1500);
                Locator phone = page.locator(".nickName-cIcGuG");
                phone.waitFor(new Locator.WaitForOptions().setTimeout(3000));
                if (phone.count() > 0) {
                    String phoneText = phone.textContent();
                    loginMap.put(key, phoneText);
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
     * 获取豆包登录二维码
     *
     * @param userId 用户唯一标识
     * @return 二维码图片URL 或 "false"表示失败
     */
    @Operation(summary = "获取豆包登录二维码", description = "返回二维码截图 URL 或 false 表示失败")
    @GetMapping("/getDBQrCode")
    public String getDBQrCode(@Parameter(description = "用户唯一标识") @RequestParam("userId") String userId) throws InterruptedException, IOException {
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false, userId, "db")) {
            Page page = browserUtil.getOrCreatePage(context);
            page.navigate("https://www.doubao.com/chat/");
            Locator locator = page.locator("//*[@id=\"root\"]/div[1]/div/div[3]/div/main/div/div/div[1]/div/div/div/div[2]/div/button");
            Thread.sleep(2000);
            if (locator.count() > 0 && locator.isVisible()) {
                locator.click();
                page.locator("[data-testid='qrcode_switcher']").evaluate("el => el.click()");

                Thread.sleep(3000);
                String url = screenshotUtil.screenshotAndUpload(page, "checkDBLogin.png");

                JSONObject jsonObject = new JSONObject();
                jsonObject.put("url", url);
                jsonObject.put("userId", userId);
                jsonObject.put("type", "RETURN_PC_DB_QRURL");
                webSocketClientService.sendMessage(jsonObject.toJSONString());
                Locator login = page.getByText("登录成功");
                login.waitFor(new Locator.WaitForOptions().setTimeout(60000));
                Thread.sleep(5000);
                page.locator("[data-testid=\"chat_header_avatar_button\"]").click();
                Thread.sleep(1000);
                page.locator("[data-testid=\"chat_header_setting_button\"]").click();
                Thread.sleep(1000);
                Locator phone = page.locator(".nickName-cIcGuG");
                if (phone.count() > 0) {
                    String phoneText = phone.textContent();
                    JSONObject jsonObjectTwo = new JSONObject();
                    jsonObjectTwo.put("status", phoneText);
                    jsonObjectTwo.put("userId", userId);
                    jsonObjectTwo.put("type", "RETURN_DB_STATUS");
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
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false, userId, "yb")) {
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

    @Operation(summary = "检查百度AI登录状态", description = "返回用户名/手机号表示已登录，false 表示未登录")
    @GetMapping("/checkBaiduLogin")
    public String checkBaiduLogin(@Parameter(description = "用户唯一标识") @RequestParam("userId") String userId) throws Exception {
        String key = userId + "-bd";
        if (loginMap.containsKey(key)) {
            // 如果当前用户正在处理，则返回"处理中"
            return loginMap.get(key);
        }
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false, userId, "baidu")) {
            Page page = browserUtil.getOrCreatePage(context);
            // 使用BaiduUtil检查登录状态
            String loginStatus = baiduUtil.checkBaiduLogin(page, true);

            if (!"false".equals(loginStatus) && !"未登录".equals(loginStatus)) {
                loginMap.put(key, loginStatus);
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
     *
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

    /**
     * 获取知乎登录二维码
     *
     * @param userId 用户唯一标识
     * @return 二维码图片URL 或 "false"表示失败
     */
    @GetMapping("/getZhihuQrCode")
    @Operation(summary = "获取知乎登录二维码", description = "返回二维码截图 URL 或 false 表示失败")
    public String getZhihuQrCode(@Parameter(description = "用户唯一标识") @RequestParam("userId") String userId) {
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false, userId, "Zhihu")) {
            Page page = browserUtil.getOrCreatePage(context);
            page.navigate("https://www.zhihu.com/signin");
            page.setDefaultTimeout(120000);
            page.waitForLoadState();
            Thread.sleep(3000);

            // 首先检查是否已经登录
            String currentUrl = page.url();
            if (!currentUrl.contains("signin")) {
                // 已经登录，直接返回登录状态
                JSONObject loginStatusObject = new JSONObject();
                loginStatusObject.put("status", "已登录");
                loginStatusObject.put("userId", userId);
                loginStatusObject.put("type", "RETURN_ZHIHU_STATUS");
                webSocketClientService.sendMessage(loginStatusObject.toJSONString());

                return screenshotUtil.screenshotAndUpload(page, "zhihuAlreadyLogin.png");
            }

            // 查找并点击扫码登录选项卡（如果存在）
            try {
                Locator qrCodeTab = page.locator("div[role='tab']:has-text('扫码登录'), .login-tab:has-text('扫码登录')");
                if (qrCodeTab.count() > 0) {
                    qrCodeTab.first().click();
                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                UserLogUtil.sendExceptionLog(userId, "知乎切换扫码标签页", "getZhihuQrCode", e, url + "/saveLogInfo");
            }

            // 等待二维码加载
            try {
                Locator qrCodeArea = page.locator(".Qrcode, .qrcode, canvas, img[src*='qr']");
                if (qrCodeArea.count() > 0) {
                    qrCodeArea.first().waitFor(new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE)
                            .setTimeout(10000));
                } else {
                }
            } catch (Exception e) {
                UserLogUtil.sendExceptionLog(userId, "知乎等待二维码加载", "getZhihuQrCode", e, url + "/saveLogInfo");

            }

            // 截图并上传二维码
            String qrCodeUrl = screenshotUtil.screenshotAndUpload(page, "zhihuQrCode_" + userId + ".png");

            // 发送二维码URL到前端
            JSONObject qrCodeObject = new JSONObject();
            qrCodeObject.put("url", qrCodeUrl);
            qrCodeObject.put("userId", userId);
            qrCodeObject.put("type", "RETURN_PC_ZHIHU_QRURL");
            webSocketClientService.sendMessage(qrCodeObject.toJSONString());


            // 监听登录状态变化 - 最多等待60秒
            int maxAttempts = 30; // 30次尝试，每次2秒
            boolean loginSuccess = false;
            String finalUserName = "false";

            for (int i = 0; i < maxAttempts; i++) {


                try {
                    Thread.sleep(2000);
                    // 检查当前页面URL是否已经跳转（登录成功）
                    String nowUrl = page.url();

                    if (!nowUrl.contains("signin") && !nowUrl.contains("login")) {

                        // 验证登录状态并获取用户名
                        String userName = zhiHuUtil.checkLoginStatus(page);
                        if (!"false".equals(userName)) {
                            finalUserName = userName;
                            loginSuccess = true;
                            break;
                        } else {
                        }
                        break;
                    }

                    // 检查登录页面是否有错误提示或状态变化
                    Locator errorMsg = page.locator(".Error, .error, .ErrorMessage, [class*='error']");
                    if (errorMsg.count() > 0) {
                        String errorText = errorMsg.first().textContent();
                        if (errorText != null && !errorText.trim().isEmpty()) {
                        }
                    }
                } catch (Exception e) {
                    UserLogUtil.sendExceptionLog(userId, "知乎登录状态检查", "getZhihuQrCode", e, url + "/saveLogInfo");
                }
            }

            // 发送最终的登录状态
            if (loginSuccess) {
                JSONObject loginSuccessObject = new JSONObject();
                loginSuccessObject.put("status", finalUserName);
                loginSuccessObject.put("userId", userId);
                loginSuccessObject.put("type", "RETURN_ZHIHU_STATUS");
                webSocketClientService.sendMessage(loginSuccessObject.toJSONString());

            } else {
                // 超时未登录，发送超时提示
                JSONObject timeoutObject = new JSONObject();
                timeoutObject.put("status", "timeout");
                timeoutObject.put("userId", userId);
                timeoutObject.put("type", "RETURN_ZHIHU_LOGIN_TIMEOUT");
                webSocketClientService.sendMessage(timeoutObject.toJSONString());

            }

            return qrCodeUrl;

        } catch (Exception e) {
            UserLogUtil.sendExceptionLog(userId, "获取知乎二维码", "getZhihuQrCode", e, url + "/saveLogInfo");
        }
        return "false";
    }


    /**
     * 检查知乎登录状态
     *
     * @param userId 用户唯一标识
     * @return 登录状态："false"表示未登录，用户名表示已登录
     */
    @Operation(summary = "检查知乎登录状态", description = "返回用户名表示已登录，false 表示未登录")
    @GetMapping("/checkZhihuLogin")
    public String checkZhihuLogin(@Parameter(description = "用户唯一标识") @RequestParam("userId") String userId) throws InterruptedException {
        String key = userId + "-zhzd";
        if (loginMap.containsKey(key)) {
            // 如果当前用户正在处理，则返回"处理中"
            return loginMap.get(key);
        }
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false, userId, "Zhihu")) {
            Page page = browserUtil.getOrCreatePage(context);

            // 先导航到知乎首页而不是登录页面，这样能更好地检测登录状态
            page.navigate("https://www.zhihu.com/");
            page.waitForLoadState();
            Thread.sleep(3000);

            // 检查当前URL是否跳转到登录页面
            String currentUrl = page.url();
            if (currentUrl.contains("signin") || currentUrl.contains("login")) {
                return "false";
            }

            // 检测登录状态
            String userName = zhiHuUtil.checkLoginStatus(page);

            if (!"false".equals(userName) && !"未登录".equals(userName)) {
                loginMap.put(key, userName);
                return userName;
            }

            return "false";

        } catch (Exception e) {
            throw e;
        }
    }
}