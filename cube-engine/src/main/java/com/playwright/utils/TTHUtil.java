package com.playwright.utils;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


/**
 * @author 孔德权
 * description:  TODO
 * dateStart 2024/8/4 9:34
 * dateNow   2025/7/20 16:42
 */
@Component
public class TTHUtil {
    @Autowired
    private LogMsgUtil logInfo;
    /**
     * 检查TTH是否已登录
     *
     * @param page
     * @param navigate
     * @return
     */
    public String checkLoginStatus(Page page, boolean navigate) {
        try {
            if (navigate) {
                page.navigate("https://mp.toutiao.com/");
                page.waitForLoadState();
                page.waitForTimeout(2000); // 增加等待时间确保页面完全加载
            }

            // 检查是否有登录按钮，如果有则表示未登录
            try {
                boolean isLoggedIn = page.locator("//img[@role='presentation']").isVisible();
                if (isLoggedIn) {
                    String s = page.locator(".auth-avator-name").textContent();
                    if(s != null || !s.isEmpty()){
                        return s;
                    }
                    return "false";
                }
                return "false";
            } catch (Exception e) {
                // 忽略检查错误
                return "false";
            }
        }
        catch (Exception e) {
            throw e;
        }
    }

    public String waitAndGetQRCode(Page page, String userId, ScreenshotUtil screenshotUtil) {
        try {
            logInfo.sendTTHFlow("正在获取微头条登录二维码", userId);

            page.navigate("https://mp.toutiao.com/");
            page.waitForLoadState();
            // 1. 勾选协议（确保元素存在）
            Locator agreementCheckbox = page.locator("span[aria-label='协议勾选框']");
            try {
                agreementCheckbox.waitFor(new Locator.WaitForOptions().setTimeout(5000));
                if (!agreementCheckbox.isChecked()) {
                    agreementCheckbox.click();
                    System.out.println("已勾选协议");
                }
            } catch (TimeoutError e) {
                System.out.println("未找到协议勾选框，可能页面结构变化");
            }

            // 2. 点击抖音登录按钮
            Locator douyinLoginButton = page.locator("li[aria-label='抖音登录'] span");
            douyinLoginButton.waitFor();
            douyinLoginButton.click();
            page.waitForLoadState();  // 等待登录弹窗加载
            Thread.sleep(2000);
//             直接截图当前页面（包含登录按钮）
            return screenshotUtil.screenshotAndUpload(page, "tthLogin.png");
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return "false";
        }
    }

}
