    package com.playwright.utils;

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
    public String checkLoginStatus(Page page) {
        try {
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
                System.out.println("已捕获用户头像和用户名，正在处理...");
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
            System.out.println("未找到用户头像和用户名，正在尝试通过其他方式获取用户名...");
            Locator userInfoArea = userNameElement;
            if (userInfoArea.count() > 0) {
                String userInfoText = userInfoArea.first().textContent();
                if (userInfoText != null && !userInfoText.trim().isEmpty()) {
                    // 简单处理，通常用户名在前面部分
                    String[] parts = userInfoText.split("\\s+");
                    if (parts.length > 0) {
                        System.out.println("已捕获用户名："+parts[0].trim());
                        return parts[0].trim();
                    }
                }
            }

            System.out.println("未通过其他方式获取用户名,正在检查是否登录...");
            // 检查是否存在登录按钮或登录链接
            Locator loginButtons = page.locator(
                    "div.btnlogin--bI826"
            );
            if (loginButtons.count() > 0) {
                System.out.println("发现登录按钮，用户未登录");
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
                System.out.println("检测到登录页面元素，用户未登录");
                return "false";
            }

            System.out.println("代码有误，未查到登录状态！");
            return "false";

        } catch (Exception e) {
            System.out.println("百家号登录状态检测异常: " + e.getMessage());
            return "false";
        }
    }
}
