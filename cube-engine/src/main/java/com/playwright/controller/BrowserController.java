package com.playwright.controller;

import com.alibaba.fastjson.JSONObject;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.playwright.utils.BrowserUtil;
import com.playwright.utils.ScreenshotUtil;
import com.playwright.websocket.WebSocketClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@RestController
@RequestMapping("/api/browser")
@Tag(name = "AI登录登录控制器", description = "AI登录相关接口")
public class BrowserController {

    // 浏览器操作工具类
    @Autowired
    private ScreenshotUtil screenshotUtil;

    private final WebSocketClientService webSocketClientService;

    public BrowserController(WebSocketClientService webSocketClientService) {
        this.webSocketClientService = webSocketClientService;
    }

    @Autowired
    private BrowserUtil browserUtil;

    /**
     * 检查元宝主站登录状态
     * @param userId 用户唯一标识
     * @return 登录状态："false"表示未登录，手机号表示已登录
     */
    @Operation(summary = "检查元宝登录状态", description = "返回手机号表示已登录，false 表示未登录")
    @GetMapping("/checkLogin")
    public String checkLogin(@Parameter(description = "用户唯一标识")  @RequestParam("userId") String userId) {
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false,userId,"yb")) {
            Page page = context.newPage();
            page.navigate("https://yuanbao.tencent.com/chat/naQivTmsDa");
            Locator locator = page.locator("text=你好，我是腾讯元宝");
            if (locator.count() > 0 && locator.isVisible()) {
                return "false";
            } else {
                Thread.sleep(3000);
                page.click("span.icon-yb-setting");
                Thread.sleep(2000);
                Locator phone = page.locator("//*[@id=\"app\"]/div/div[2]/div/div[2]/div[1]/ul/li[1]/div/div[2]/h4");
                if(phone.count()>0){
                    String phoneText = phone.textContent();
                    if(phoneText.equals("未登录")){
                        return "false";
                    }
                    return phoneText;
                }else{
                    return "false";
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }


    /**
     * 检查元器主站登录状态
     * @param userId 用户唯一标识
     * @return 登录状态："false"表示未登录，手机号表示已登录
     */
    @Operation(summary = "检查元器登录状态", description = "返回手机号表示已登录，false 表示未登录")
    @GetMapping("/checkAgentLogin")
    public String checkAgentLogin(@Parameter(description = "用户唯一标识") @RequestParam("userId") String userId) {
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false,userId,"agent")) {
            Page page = context.newPage();
            page.navigate("https://yuanbao.tencent.com/chat/naQivTmsDa");
            Locator locator = page.locator("text=你好，我是腾讯元宝");
            if (locator.count() > 0 && locator.isVisible()) {
                return "false";
            } else {
                Thread.sleep(3000);
                page.click("span.icon-yb-setting");
                Thread.sleep(2000);
                Locator phone = page.locator("//*[@id=\"app\"]/div/div[2]/div/div[2]/div[1]/ul/li[1]/div/div[2]/h4");
                if(phone.count()>0){
                    String phoneText = phone.textContent();
                    if(phoneText.equals("未登录")){
                        return "false";
                    }
                    return phoneText;
                }else{
                    return "false";
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "false";
    }

    /**
     * 获取代理版元器登录二维码
     * @param userId 用户唯一标识
     * @return 二维码图片URL 或 "false"表示失败
     */
    @Operation(summary = "获取代理版元器登录二维码", description = "返回二维码截图 URL 或 false 表示失败")
    @GetMapping("/getAgentQrCode")
    public String getAgentQrCode(@Parameter(description = "用户唯一标识") @RequestParam("userId") String userId) {
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false,userId,"agent")) {
            Page page = context.newPage();
            page.navigate("https://yuanbao.tencent.com/chat/naQivTmsDa");
            page.locator("//*[@id=\"hunyuan-bot\"]/div[3]/div/div/div[3]/div/div[2]/div[1]").click();
            Thread.sleep(3000);
            String url = screenshotUtil.screenshotAndUpload(page,"checkAgentLogin.png");

            JSONObject jsonObject = new JSONObject();
            jsonObject.put("url",url);
            jsonObject.put("userId",userId);
            jsonObject.put("type","RETURN_PC_AGENT_QRURL");
            webSocketClientService.sendMessage(jsonObject.toJSONString());
            Locator locator = page.getByText("登录成功");
            locator.waitFor(new Locator.WaitForOptions().setTimeout(60000));
            Thread.sleep(3000);
            page.click("span.icon-yb-setting");
            Thread.sleep(2000);
            Locator phone = page.locator("//*[@id=\"app\"]/div/div[2]/div/div[2]/div[1]/ul/li[1]/div/div[2]/h4");
            if(phone.count()>0){
                String phoneText = phone.textContent();
                JSONObject jsonObjectTwo = new JSONObject();
                jsonObjectTwo.put("status",phoneText);
                jsonObjectTwo.put("userId",userId);
                jsonObjectTwo.put("type","RETURN_AGENT_STATUS");
                webSocketClientService.sendMessage(jsonObjectTwo.toJSONString());
            }

            return url;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "false";
    }

    /**
     * 获取代理版元宝登录二维码
     * @param userId 用户唯一标识
     * @return 二维码图片URL 或 "false"表示失败
     */
    @GetMapping("/getYBQrCode")
    @Operation(summary = "获取代理版元宝登录二维码", description = "返回二维码截图 URL 或 false 表示失败")
    public String getYBQrCode(@Parameter(description = "用户唯一标识") @RequestParam("userId") String userId) {
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false,userId,"yb")) {
            Page page = context.newPage();
            page.navigate("https://yuanbao.tencent.com/chat/naQivTmsDa");
            page.locator("//*[@id=\"hunyuan-bot\"]/div[3]/div/div/div[3]/div/div[2]/div[1]").click();
            Thread.sleep(3000);
            String url = screenshotUtil.screenshotAndUpload(page,"checkYBLogin.png");

            JSONObject jsonObject = new JSONObject();
            jsonObject.put("url",url);
            jsonObject.put("userId",userId);
            jsonObject.put("type","RETURN_PC_YB_QRURL");
            webSocketClientService.sendMessage(jsonObject.toJSONString());
            Locator locator = page.getByText("登录成功");
            locator.waitFor(new Locator.WaitForOptions().setTimeout(60000));
            Thread.sleep(3000);
            page.click("span.icon-yb-setting");
            Thread.sleep(2000);
            Locator phone = page.locator("//*[@id=\"app\"]/div/div[2]/div/div[2]/div[1]/ul/li[1]/div/div[2]/h4");
            if(phone.count()>0){
                String phoneText = phone.textContent();
                JSONObject jsonObjectTwo = new JSONObject();
                jsonObjectTwo.put("status",phoneText);
                jsonObjectTwo.put("userId",userId);
                jsonObjectTwo.put("type","RETURN_YB_STATUS");
                webSocketClientService.sendMessage(jsonObjectTwo.toJSONString());
            }

            return url;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }


    /**
     * 检查豆包登录状态
     * @param userId 用户唯一标识
     * @return 登录状态："false"表示未登录，手机号表示已登录
     */
    @Operation(summary = "检查豆包登录状态", description = "返回手机号表示已登录，false 表示未登录")
    @GetMapping("/checkDBLogin")
    public String checkDBLogin(@Parameter(description = "用户唯一标识") @RequestParam("userId") String userId) {
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false,userId,"db")) {
            Page page = context.newPage();
            page.navigate("https://www.doubao.com/chat/");
            Thread.sleep(5000);
            Locator locator = page.locator("//*[@id=\"root\"]/div[1]/div/div[3]/div[1]/div[1]/div/div/div[1]/div/div/div/div[2]/div/button");
            if (locator.count() > 0 && locator.isVisible()) {
                return "false";
            } else {
                Thread.sleep(500);

                page.locator("//*[@id=\"root\"]/div[1]/div/div[3]/div[1]/div[1]/div/div/div[1]/div/div/div/div[2]/div/div/span").click();
                Thread.sleep(500);
                page.locator("[data-testid=\"chat_header_setting_button\"]").click();
                Thread.sleep(500);
                Locator phone = page.locator("//*[@id=\"root\"]/div[1]/div/div[3]/div[1]/div[1]/div/div[2]/div/div/div[1]/div/div[1]");
                if(phone.count()>0){
                    String phoneText = phone.textContent();
                    return phoneText;
                }else{
                    return "false";
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "false";
    }



    /**
     * 获取豆包登录二维码
     * @param userId 用户唯一标识
     * @return 二维码图片URL 或 "false"表示失败
     */
    @Operation(summary = "获取豆包登录二维码", description = "返回二维码截图 URL 或 false 表示失败")
    @GetMapping("/getDBQrCode")
    public String getDBQrCode(@Parameter(description = "用户唯一标识") @RequestParam("userId") String userId) {
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false,userId,"db")) {
            Page page = context.newPage();
            page.navigate("https://www.doubao.com/chat/");
            Locator locator = page.locator("//*[@id=\"root\"]/div[1]/div/div[3]/div[1]/div[1]/div/div/div[1]/div/div/div/div[2]/div/button");
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
                page.locator("//*[@id=\"root\"]/div[1]/div/div[3]/div[1]/div[1]/div/div/div[1]/div/div/div/div[2]/div/div/span").click();
                Thread.sleep(1000);
                page.locator("[data-testid=\"chat_header_setting_button\"]").click();
                Thread.sleep(1000);
                Locator phone = page.locator("//*[@id=\"root\"]/div[1]/div/div[3]/div[1]/div[1]/div/div[2]/div/div/div[1]/div/div[1]");
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
            e.printStackTrace();
        }
        return "false";
    }


    /**
     * 退出腾讯元宝
     */
    @Operation(summary = "退出腾讯元宝登录状态", description = "执行退出操作，返回true表示成功")
    @GetMapping("/loginOut")
    public boolean loginOut(@Parameter(description = "用户唯一标识") @RequestParam("userId") String userId) {
        try (BrowserContext context = browserUtil.createPersistentBrowserContext(false,userId,"yb")) {
            Page page = context.newPage();
            page.navigate("https://yuanbao.tencent.com/chat/naQivTmsDa");
            page.click("span.icon-yb-setting");
            page.click("text=退出登录");
            page.locator("//*[@id=\"hunyuan-bot\"]/div[2]/div/div[2]/div/div/div[3]/button[2]").click();
            Thread.sleep(3000);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }






}


