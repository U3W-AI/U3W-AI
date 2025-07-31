package com.playwright.controller;

import com.alibaba.fastjson.JSONObject;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.FrameLocator;
import com.playwright.utils.*;
import com.playwright.websocket.WebSocketClientService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

/**
 * ClassName: MediaController
 * Package: com.playwright.controller
 * Description:
 *
 * @author fuchen
 * @version 1.0
 * @createTime 2025/7/22
 */
@RestController
@RequestMapping("/api/media")
@Tag(name = "媒体控制器", description = "媒体相关接口")
public class MediaController {
    // 依赖注入 注入webSocketClientService 进行消息发送
    private final WebSocketClientService webSocketClientService;

    // 构造器注入WebSocket服务
    public MediaController(WebSocketClientService webSocketClientService) {
        this.webSocketClientService = webSocketClientService;
    }

    // 从配置文件中注入URL 调用远程API存储数据
    @Value("${cube.url}")
    private String url;
    @Autowired
    private BrowserUtil browserUtil;
    @Autowired
    private ScreenshotUtil screenshotUtil;
    @Autowired
    private ZhiHuUtil zhiHuUtil;
    @Autowired
    private BaijiahaoUtil baijiahaoUtil;
    @Autowired
    private LogMsgUtil logMsgUtil;

    /**
     * 检查知乎登录状态
     * @param userId 用户唯一标识
     * @return 登录状态："false"表示未登录，用户名表示已登录
     */
    @Operation(summary = "检查知乎登录状态", description = "返回用户名表示已登录，false 表示未登录")
    @GetMapping("/checkZhihuLogin")
    public String checkZhihuLogin(@Parameter(description = "用户唯一标识")  @RequestParam("userId") String userId) {
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false,userId,"Zhihu")) {
            Page page = context.newPage();

            // 先导航到知乎首页而不是登录页面，这样能更好地检测登录状态
            page.navigate("https://www.zhihu.com/");
            page.waitForLoadState();
            Thread.sleep(3000);

            // 检查当前URL是否跳转到登录页面
            String currentUrl = page.url();
            if (currentUrl.contains("signin") || currentUrl.contains("login")) {
                System.out.println("页面跳转到登录页面，用户未登录");
                return "false";
            }

            // 检测登录状态
            String userName = zhiHuUtil.checkLoginStatus(page);

            if (!"false".equals(userName)) {
                System.out.println("知乎登录检测成功，用户: " + userName);
                return userName;
            }

            return "false";

        } catch (Exception e) {
            e.printStackTrace();
            return "false";
        }
    }
    /**
     * 检查百家号登录状态
     * @param userId 用户唯一标识
     * @return 登录状态："false"表示未登录，用户名表示已登录
     */
    @Operation(summary = "检查百家号登录状态", description = "返回用户名表示已登录，false 表示未登录")
    @GetMapping("/checkBaijiahaoLogin")
    public String checkBaijiahaoLogin(@Parameter(description = "用户唯一标识")  @RequestParam("userId") String userId) {
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false,userId,"Baijiahao")) {
            Page page = context.newPage();

            // 先导航到百家号首页而不是登录页面，这样能更好地检测登录状态
            page.navigate("https://baijiahao.baidu.com/");
            page.waitForLoadState();
            Thread.sleep(3000);

            // 检查当前URL是否跳转到登录页面
            String currentUrl = page.url();
            if (currentUrl.contains("login") || currentUrl.contains("signin")) {
                System.out.println("页面跳转到登录页面，用户未登录");
                return "false";
            }

            // 检测登录状态
            String userName = baijiahaoUtil.checkLoginStatus(page);

            if (!"false".equals(userName)) {
                System.out.println("百家号登录检测成功，用户: " + userName);
                return userName;
            }

            return "false";

        } catch (Exception e) {
            e.printStackTrace();
            return "false";
        }
    }



    /**
     * 获取知乎登录二维码
     * @param userId 用户唯一标识
     * @return 二维码图片URL 或 "false"表示失败
     */
    @GetMapping("/getZhihuQrCode")
    @Operation(summary = "获取知乎登录二维码", description = "返回二维码截图 URL 或 false 表示失败")
    public String getZhihuQrCode(@Parameter(description = "用户唯一标识") @RequestParam("userId") String userId) {
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false,userId,"Zhihu")) {
            Page page = context.newPage();
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
                    System.out.println("切换到扫码登录标签页");
                }
            } catch (Exception e) {
                System.out.println("切换扫码标签页失败，可能默认就是扫码登录: " + e.getMessage());
            }

            // 等待二维码加载
            try {
                Locator qrCodeArea = page.locator(".Qrcode, .qrcode, canvas, img[src*='qr']");
                if (qrCodeArea.count() > 0) {
                    qrCodeArea.first().waitFor(new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE)
                            .setTimeout(10000));
                    System.out.println("二维码区域已加载");
                } else {
                    System.out.println("未找到二维码区域，继续截图");
                }
            } catch (Exception e) {
                System.out.println("等待二维码加载失败: " + e.getMessage());
            }

            // 截图并上传二维码
            String qrCodeUrl = screenshotUtil.screenshotAndUpload(page, "zhihuQrCode_" + userId + ".png");

            // 发送二维码URL到前端
            JSONObject qrCodeObject = new JSONObject();
            qrCodeObject.put("url", qrCodeUrl);
            qrCodeObject.put("userId", userId);
            qrCodeObject.put("type", "RETURN_PC_ZHIHU_QRURL");
            webSocketClientService.sendMessage(qrCodeObject.toJSONString());

            System.out.println("知乎二维码已发送，开始监听登录状态");

            // 监听登录状态变化 - 最多等待60秒
            int maxAttempts = 30; // 30次尝试，每次2秒
            boolean loginSuccess = false;
            String finalUserName = "false";

            for (int i = 0; i < maxAttempts; i++) {


                try {
                    Thread.sleep(2000);
                    // 检查当前页面URL是否已经跳转（登录成功）
                    String nowUrl = page.url();
                    System.out.println("当前URL: " + nowUrl);

                    if (!nowUrl.contains("signin") && !nowUrl.contains("login")) {
                        System.out.println("检测到页面跳转，验证登录状态");

                        // 验证登录状态并获取用户名
                        String userName = zhiHuUtil.checkLoginStatus(page);
                        if (!"false".equals(userName)) {
                            finalUserName = userName;
                            loginSuccess = true;
                            System.out.println("URL跳转检测：知乎登录成功，用户名: " + userName);
                            break;
                        } else {
                            System.out.println("URL跳转了但登录状态检测失败，继续监听");
                        }
                        break;
                    }

                    // 检查登录页面是否有错误提示或状态变化
                    Locator errorMsg = page.locator(".Error, .error, .ErrorMessage, [class*='error']");
                    if (errorMsg.count() > 0) {
                        String errorText = errorMsg.first().textContent();
                        if (errorText != null && !errorText.trim().isEmpty()) {
                            System.out.println("检测到错误信息: " + errorText);
                        }
                    }
                } catch (Exception e) {
                    System.out.println("登录状态检查异常: " + e.getMessage());
                }
            }

            // 发送最终的登录状态
            if (loginSuccess) {
                JSONObject loginSuccessObject = new JSONObject();
                loginSuccessObject.put("status", finalUserName);
                loginSuccessObject.put("userId", userId);
                loginSuccessObject.put("type", "RETURN_ZHIHU_STATUS");
                webSocketClientService.sendMessage(loginSuccessObject.toJSONString());

                System.out.println("知乎登录成功消息已发送: " + finalUserName);
            } else {
                // 超时未登录，发送超时提示
                JSONObject timeoutObject = new JSONObject();
                timeoutObject.put("status", "timeout");
                timeoutObject.put("userId", userId);
                timeoutObject.put("type", "RETURN_ZHIHU_LOGIN_TIMEOUT");
                webSocketClientService.sendMessage(timeoutObject.toJSONString());

                System.out.println("知乎登录超时，已发送超时通知");
            }

            return qrCodeUrl;

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("获取知乎二维码失败: " + e.getMessage());
        }
        return "false";
    }

    /**
     * 获取百家号登录二维码
     * @param userId 用户唯一标识
     * @return 二维码图片URL 或 "false"表示失败
     */
    @GetMapping("/getBaijiahaoQrCode")
    @Operation(summary = "获取百家号登录二维码", description = "返回二维码截图 URL 或 false 表示失败")
    public String getBaijiahaoQrCode(@Parameter(description = "用户唯一标识") @RequestParam("userId") String userId) {
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false,userId,"Baijiahao")) {
            Page page = context.newPage();
            page.navigate("https://baijiahao.baidu.com/builder/theme/bjh/login");
            page.setDefaultTimeout(120000);
            page.waitForLoadState();

// 检查是否已经登录
            String currentUrl = page.url();
            if (!currentUrl.contains("login") && !currentUrl.contains("register")) {
                JSONObject loginStatusObject = new JSONObject();
                loginStatusObject.put("status", baijiahaoUtil.checkLoginStatus(page));
                loginStatusObject.put("userId", userId);
                loginStatusObject.put("type", "RETURN_BAIJIAHAO_STATUS");
                webSocketClientService.sendMessage(loginStatusObject.toJSONString());

                return screenshotUtil.screenshotAndUpload(page, "baijiahaoAlreadyLogin.png");
            }

// 点击扫码登录选项卡（如果存在）
            try {
                page.waitForSelector("div.btnlogin--bI826", new Page.WaitForSelectorOptions().setTimeout(5000));
                Locator qrCodeTab = page.locator("div.btnlogin--bI826").first();
                qrCodeTab.click();
                System.out.println("切换到扫码登录标签页");
            } catch (Exception e) {
                System.out.println("扫码登录标签未找到，可能默认就是扫码登录: " + e.getMessage());
            }

// 等待二维码加载
            try {
                page.waitForSelector(".tang-pass-qrcode-img", new Page.WaitForSelectorOptions().setTimeout(10000));
                Locator qrCodeArea = page.locator(".tang-pass-qrcode-img").first();
                System.out.println("二维码区域已加载");
            } catch (Exception e) {
                System.out.println("等待二维码加载失败或未找到二维码区域: " + e.getMessage());
            }


            // 截图并上传二维码
            String qrCodeUrl = screenshotUtil.screenshotAndUpload(page, "baijiahaoQrCode_" + userId + ".png");

            // 发送二维码URL到前端
            JSONObject qrCodeObject = new JSONObject();
            qrCodeObject.put("url", qrCodeUrl);
            qrCodeObject.put("userId", userId);
            qrCodeObject.put("type", "RETURN_PC_BAIJIAHAO_QRURL");
            webSocketClientService.sendMessage(qrCodeObject.toJSONString());

            System.out.println("百家号二维码已发送，开始监听登录状态");

            // 监听登录状态变化 - 最多等待60秒
            int maxAttempts = 30; // 30次尝试，每次2秒
            boolean loginSuccess = false;
            String finalUserName = "false";

            for (int i = 0; i < maxAttempts; i++) {
                try {
                    Thread.sleep(2000);
                    // 检查当前页面URL是否已经跳转（登录成功）
                    String nowUrl = page.url();
                    System.out.println("当前URL: " + nowUrl);

                    if (!nowUrl.contains("login") && !nowUrl.contains("register")) {
                        System.out.println("检测到页面跳转，验证登录状态");

                        // 验证登录状态并获取用户名
                        String userName = baijiahaoUtil.checkLoginStatus(page);
                        if (!"false".equals(userName)) {
                            finalUserName = userName;
                            loginSuccess = true;
                            System.out.println("URL跳转检测：百家号登录成功，用户名: " + userName);
                            break;
                        } else {
                            System.out.println("URL跳转了但登录状态检测失败，继续监听");
                        }
                        break;
                    }

                    // 检查登录页面是否有错误提示或状态变化
                    Locator errorMsg = page.locator(".Error, .error, .ErrorMessage, [class*='error']");
                    if (errorMsg.count() > 0) {
                        String errorText = errorMsg.first().textContent();
                        if (errorText != null && !errorText.trim().isEmpty()) {
                            System.out.println("检测到错误信息: " + errorText);
                        }
                    }
                } catch (Exception e) {
                    System.out.println("登录状态检查异常: " + e.getMessage());
                }
            }

            // 发送最终的登录状态
            if (loginSuccess) {
                JSONObject loginSuccessObject = new JSONObject();
                loginSuccessObject.put("status", finalUserName);
                loginSuccessObject.put("userId", userId);
                loginSuccessObject.put("type", "RETURN_BAIJIAHAO_STATUS");
                webSocketClientService.sendMessage(loginSuccessObject.toJSONString());

                System.out.println("百家号登录成功消息已发送: " + finalUserName);
            } else {
                // 超时未登录，发送超时提示
                JSONObject timeoutObject = new JSONObject();
                timeoutObject.put("status", "timeout");
                timeoutObject.put("userId", userId);
                timeoutObject.put("type", "RETURN_BAIJIAHAO_LOGIN_TIMEOUT");
                webSocketClientService.sendMessage(timeoutObject.toJSONString());

                System.out.println("百家号登录超时，已发送超时通知");
            }

            return qrCodeUrl;

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("获取百家号二维码失败: " + e.getMessage());
        }
        return "false";
    }


    /**
     * 将内容投递到知乎
     * @param userId 用户唯一标识
     * @param title 文章标题
     * @param content 文章内容
     * @return
     */
    @PostMapping("/sendToZhihu")
    @Operation(summary = "投递内容到知乎", description = "将处理后的内容自动投递到知乎草稿箱")
    public String sendToZhihu(@Parameter(description = "用户唯一标识") @RequestParam("userId") String userId,
                              @Parameter(description = "文章标题") @RequestParam("title") String title,
                              @Parameter(description = "文章内容") @RequestParam("content") String content){
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false,userId,"Zhihu")) {

            // 初始化页面和变量package com.playwright.controller;
            Page page = context.newPage();

            // 进入到知乎的文章发布页，并设置最大超时时间为5分钟
            page.navigate("https://zhuanlan.zhihu.com/write", new Page.NavigateOptions()
                    .setTimeout(300000)); // 5分钟超时
            // 等待页面加载完成，最大等待时间与导航超时一致
            page.waitForLoadState();

            logMsgUtil.sendImgData(page, "知乎编辑页面", userId);

            // 检查登录状态
            String loginStatus = zhiHuUtil.checkLoginStatus(page);
            if(loginStatus.equals("false")){
                logMsgUtil.sendMediaTaskLog("检测到知乎未登录，请先登录", userId, "投递到知乎");
                return "false";
            }

            // 定位标题输入框
            Locator titleInput = page.locator("#root > div > main > div > div.WriteIndexLayout-main.WriteIndex.css-1losy9j > div.WriteIndexMain.WriteIndexMain-aiAssistantOpen > div > div.css-i6bazn > label > textarea");

            // 检查元素是否可见，等待元素加载
            titleInput.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
            // 输入标题
            titleInput.fill(title);
            logMsgUtil.sendImgData(page,  "知乎标题输入过程截图", userId);

            // 定位内容输入框
            Locator contentInput = page.locator("#root > div > main > div > div.WriteIndexLayout-main.WriteIndex.css-1losy9j > div.WriteIndexMain.WriteIndexMain-aiAssistantOpen > div > div.PostEditor-wrapper > div.css-eehorp > div > div.Dropzone.Editable-content.RichText.RichText--editable.RichText--clearBoth.ztext > div");
            // 检查元素是否可见，等待元素加载
            contentInput.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
            // 输入内容
            contentInput.click();
            Thread.sleep(2000);
            int charCount = 0;
            boolean isLineBreak = false;
            boolean is_ = false;
            for (int i = 0; i < content.length(); i++) {
                char c = content.charAt(i);

                if (c == '\r' || c == '\n') {
//                    if(is_){
//                        int delay = 40 + (int) (Math.random() * 21) - 10; // 生成30到50之间的随机数
//                        contentInput.type("\n", new Locator.TypeOptions().setDelay(delay));
//                        is_ = false;
//                    }
                    if (!isLineBreak) {
                        int delay = 20 + (int) (Math.random() * 21) - 10;
                        contentInput.type("\n", new Locator.TypeOptions().setDelay(delay));
                        isLineBreak = true;
                        Locator titleLocator = page.locator("#root > div > main > div > div.WriteIndexLayout-main.WriteIndex.css-1losy9j > div:nth-child(1) > div > div > div > div.mainArea.css-5jm82r > div:nth-child(2) > div > button");
                        if("Button ToolbarButton css-1czbfii FEfUrdfMIKpQDJDqkjte Button--plain fEPKGkUK5jyc4fUuT0QP".equals(titleLocator.getAttribute("class"))){
                            titleLocator.click();
                            Thread.sleep(1000);
                            page.locator("#Popover3-content > div > button:nth-child(2)").click();
                            Thread.sleep(500);
                        }
                        Locator listLocator = page.locator("#root > div > main > div > div.WriteIndexLayout-main.WriteIndex.css-1losy9j > div:nth-child(1) > div > div > div > div.mainArea.css-5jm82r > div:nth-child(3) > div.Popover > button");
                        if("Button ToolbarButton css-1czbfii FEfUrdfMIKpQDJDqkjte Button--plain fEPKGkUK5jyc4fUuT0QP".equals(listLocator.getAttribute("class"))){
                            listLocator.click();
                            Thread.sleep(1000);
                            page.locator("#Popover4-content > div > button:nth-child(2)").click();
                            Thread.sleep(500);
                        }
                    }
                } else {
                    // 检查当前字符是否为 '-' 且是新行的开始
                    if (c == '-' && (i == 0 || content.charAt(i - 1) == '\n' || content.charAt(i - 1) == '\r')) {
                        // 在当前行结束后插入一个额外的回车
                        is_ = true;
                    }
                    int delay = 40 + (int) (Math.random() * 21) - 10; // 生成30到50之间的随机数
                    contentInput.type(String.valueOf(c), new Locator.TypeOptions().setDelay(delay));
                    isLineBreak = false;
                }
                charCount++;

                if (charCount % 500 == 0) {
                    Thread.sleep(2000);
                    logMsgUtil.sendImgData(page, "知乎正文输入进度截图（已输入" + charCount + "字）", userId);
                }
            }
            // 输入结束后，若最后一次未满500字但有内容，也补发一次截图
            if (charCount % 500 != 0) {
                logMsgUtil.sendImgData(page, "知乎正文输入进度截图（已输入" + charCount + "字，输入结束）", userId);
            }

            // 识别草稿保存状态，轮询div中的文字，直到不包含“草稿保存中”为止
            Locator draftStatusDiv = page.locator("#root > div > main > div > div.WriteIndexLayout-main.WriteIndex.css-1losy9j > div.WriteIndexMain.WriteIndexMain-aiAssistantOpen > div > div.PostEditor-wrapper > div.css-13mrzb0 > div.css-1ppjin3 > div > div.css-1ozfthc > div");
            int maxWait = 30; // 最多等待30秒
            int waited = 0;
            while (waited < maxWait) {
                String text = draftStatusDiv.textContent();
                if (text == null || !text.contains("草稿保存中")) {
                    break;
                }
                Thread.sleep(1000);
                waited++;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "投递失败: " + e.getMessage();
        }
        return "true";
    }

    /**
     * 将内容投递到百家号
     * @param userId 用户唯一标识
     * @param title 文章标题
     * @param content 文章内容
     * @return
     */
    @PostMapping("/sendToBaijiahao")
    @Operation(summary = "投递内容到百家号", description = "将处理后的内容自动投递到百家号草稿箱")
    public String sendToBaijiahao(@Parameter(description = "用户唯一标识") @RequestParam("userId") String userId,
                                  @Parameter(description = "文章标题") @RequestParam("title") String title,
                                  @Parameter(description = "文章内容") @RequestParam("content") String content){
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false,userId,"Baijiahao")) {

            //处理文章 拆成正文和标题
            String[] paragraphs = content.split("\\r?\\n");
            String contentTitle = paragraphs.length > 0 ? paragraphs[0] : "";
            StringBuilder contentBodyBuilder = new StringBuilder();
            for (int i = 1; i < paragraphs.length; i++) {
                contentBodyBuilder.append(paragraphs[i]);
                if (i != paragraphs.length - 1) {
                    contentBodyBuilder.append("\n");
                }
            }
            String contentBody = contentBodyBuilder.toString();

            // 初始化页面和变量
            Page page = context.newPage();
            System.out.println("userId："+ userId);
            System.out.println("title："+ title);
            System.out.println("content："+ content);
            // 进入到百家号的首页
            page.navigate("https://baijiahao.baidu.com/builder/rc/home", new Page.NavigateOptions()
                    .setTimeout(60000)); // 1分钟超时
            // 等待页面加载完成，最大等待时间与导航超时一致
            page.waitForLoadState();

            // 检查登录状态
            String loginStatus = baijiahaoUtil.checkLoginStatus(page);
            if(loginStatus.equals("false")){
                logMsgUtil.sendMediaTaskLog("检测到百家号未登录，请先登录", userId, "投递到百家号");
                return "false";
            }
            //进入草稿箱页面，并设置最大超时时间为5分钟
            //进入草稿箱页面，并设置最大超时时间为5分钟
            page.navigate("https://baijiahao.baidu.com/builder/rc/edit?type=news&is_from_cms=1", new Page.NavigateOptions()
                    .setTimeout(300000)); // 5分钟超时
            logMsgUtil.sendImgData(page, "百家号编辑页面", userId);

            // 定位标题输入框
            Locator titleInput = page.locator("textarea[placeholder='请输入标题（2 - 64字）']");
            // 检查元素是否可见，等待元素加载
            titleInput.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
            // 输入标题
            titleInput.fill(contentTitle);
            logMsgUtil.sendImgData(page,  "百家号标题输入过程截图", userId);


            // 等待 iframe 出现
            page.waitForSelector("iframe#ueditor_0");

            // 进入 iframe
            FrameLocator editorFrame = page.frameLocator("iframe#ueditor_0");
            if (editorFrame == null) {
                throw new RuntimeException("未找到百家号正文编辑器 iframe");
            }

            // 定位正文输入框（iframe 内的 body）
            Locator contentInput = editorFrame.locator("body");
            contentInput.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));

            // 点击输入框激活
            contentInput.click();
            Thread.sleep(2000);

            int charCount = 0;
            boolean isLineBreak = false;

            for (int i = 0; i < contentBody.length(); i++) {
                char c = contentBody.charAt(i);
                int delay = 10 + (int) (Math.random() * 21); // 每个字符输入延时 10~30ms

                if (c == '\r' || c == '\n') {
                    if (!isLineBreak) {
                        contentInput.type("\n", new Locator.TypeOptions().setDelay(delay));
                        isLineBreak = true;
                    }
                } else {
                    contentInput.type(String.valueOf(c), new Locator.TypeOptions().setDelay(delay));
                    isLineBreak = false;
                }

                charCount++;

                if (charCount % 500 == 0) {
                    Thread.sleep(2000);
                    logMsgUtil.sendImgData(page, "百家号正文输入进度截图（已输入" + charCount + "字）", userId);
                }
            }

            // 输入结束后，若最后一次未满500字但有内容，也补发一次截图
            if (charCount % 500 != 0) {
                logMsgUtil.sendImgData(page, "百家号正文输入进度截图（已输入" + charCount + "字，输入结束）", userId);
            }
            //点击存草稿按钮
            // 等待“存草稿”按钮出现
            Locator saveDraftBtn = page.locator("button:has-text('存草稿')");
            saveDraftBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));

            // 点击“存草稿”按钮
            saveDraftBtn.click();
            saveDraftBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));

            // 等待消息提醒“内容已存入草稿”出现
            Locator successToast = page.locator("text=内容已存入草稿").first();
            successToast.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(10000)); // 最多等10秒

            System.out.println("草稿保存成功");

        } catch (Exception e) {
            e.printStackTrace();
            return "投递失败: " + e.getMessage();
        }
        return "true";
    }

}
