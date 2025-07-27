package com.playwright.utils;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.springframework.stereotype.Component;

/**
 * ClassName: ZhiHuUtil
 * Package: com.playwright.utils
 * Description:
 *
 * @author fuchen
 * @version 1.0
 * @createTime 2025/7/22
 */
@Component
public class ZhiHuUtil {
    /**
     * 检测知乎登录状态并获取用户名
     * @param page Playwright页面实例
     * @return 用户名或"false"
     */
    public String checkLoginStatus(Page page) {
        try {
            // 检查用户头像和用户名（最常见的方式）
            // 知乎登录后，右上角通常有用户头像
            Thread.sleep(1000);
            Locator avatarArea = page.locator(".Avatar.AppHeader-profileAvatar.css-15snhfr");
            if (avatarArea.count() > 0) {
//                System.out.println("发现用户头像区域");
                // 点击用户头像，然后点击用户主页按钮
//                avatarArea.first().click();
                Thread.sleep(1000);
                String avatarAlt = page.locator(".Avatar.AppHeader-profileAvatar.css-15snhfr").first().getAttribute("alt");
                String userName = avatarAlt;
                if (userName != null) {
                    if (userName.startsWith("点击打开")) {
                        userName = userName.substring("点击打开".length());
                    }
                    if (userName.endsWith("的主页")) {
                        userName = userName.substring(0, userName.length() - "的主页".length());
                    }
                }
                return userName;
            }

            // 检查是否存在登录按钮
            Locator loginButtons = page.locator("button:has-text('登录'), a:has-text('登录'), .signin-btn, .login-btn");
            if (loginButtons.count() > 0) {
                System.out.println("发现登录按钮，用户未登录");
                return "false";
            }

            return "false";

        } catch (Exception e) {
            System.out.println("登录状态检测异常: " + e.getMessage());
            return "false";
        }
    }
}
