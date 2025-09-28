    package com.playwright.utils.media;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.springframework.stereotype.Component;

/**
 * ClassName: BaijiahaoUtil
 * Package: com.playwright.utils
 * Description: 百家号登录状态检测工具类
 *
 * @author fuchen
 * @version 1.0
 * @createTime 2025/7/27
 */
@Component
public class BaijiahaoUtil {
    /**
     * 检测百家号登录状态并获取用户名
     * @param page Playwright页面实例
     * @return 用户名或"false"
     */
    public String checkLoginStatus(Page page) throws InterruptedException {
        try {
            closeNewWindows(page);
            // 等待页面加载
            Thread.sleep(1000);
            // 检查百家号用户头像和用户名区域
            // 百家号登录后，右上角通常有用户头像和用户名
            page.waitForSelector("img[alt='头像']").hover();
            Locator userAvatar = page.locator("img[alt='头像']").first();
            page.waitForSelector("a.user-name");
            Locator userNameElement = page.locator("a.user-name").first();
            // 如果同时找到头像和用户名元素，则认为已登录
            if (userAvatar.count() > 0 && userNameElement.count() > 0) {
                String userName = userNameElement.first().textContent();
                if (userName != null) {
                    userName = userName.trim();
                    // 去掉问候语，例如“晚上好,”、“早上好,”、“下午好,”等
                    userName = userName.replaceAll("^(早上好|上午好|中午好|下午好|晚上好|你好)[,，\\s]*", "");
                }
                if (userName != null && !userName.trim().isEmpty()) {
                    return userName.trim();
                }
            }

            // 尝试通过其他方式获取用户名
            Locator userInfoArea = userNameElement;
            if (userInfoArea.count() > 0) {
                String userInfoText = userInfoArea.first().textContent();
                if (userInfoText != null && !userInfoText.trim().isEmpty()) {
                    // 简单处理，通常用户名在前面部分
                    String[] parts = userInfoText.split("\\s+");
                    if (parts.length > 0) {
                        return parts[0].trim();
                    }
                }
            }

            // 检查是否存在登录按钮或登录链接
            Locator loginButtons = page.locator(
                    "div.btnlogin--bI826"
            );
            if (loginButtons.count() > 0) {
                return "false";
            }

            // 检查是否在登录页面
            Locator loginPageIndicators = page.locator(
                    "input[type='text'][placeholder*='用户名'], " +
                            "input[type='text'][placeholder*='账号'], " +
                            "input[type='password'], " +
                            "form:has(input[type='text']):has(input[type='password'])"
            );
            if (loginPageIndicators.count() > 0) {
                return "false";
            }

            return "false";

        } catch (Exception e) {
            throw e;
        }
    }

    public void closeNewWindows(Page page) {
        try {
            Thread.sleep(2000);
            Locator locator = page.locator("//div[@class='H7UjFl3ggoAjO5vDX6VK']//img");
            if(locator.isVisible()) {
                locator.click();
            }
            Thread.sleep(1500);
            Locator nextLocator = page.locator("//span[contains(text(),'下一步')]");
            if(nextLocator.isVisible()) {
                nextLocator.click();
            }
            Thread.sleep(1500);
            Locator completeLocator = page.locator("//span[contains(text(),'完成')]");
            if(completeLocator.isVisible()) {
                completeLocator.click();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
