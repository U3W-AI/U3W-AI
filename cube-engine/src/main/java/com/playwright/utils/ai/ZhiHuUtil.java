package com.playwright.utils.ai;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
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
    public String checkLoginStatus(Page page) throws Exception {
        // 检查页面是否已关闭
        if (page.isClosed()) {
            throw new RuntimeException("页面已关闭，无法检查知乎登录状态");
        }

        try {
            // 检查用户头像和用户名（最常见的方式）
            // 知乎登录后，右上角通常有用户头像
            Thread.sleep(1000);
            
            // 尝试多种头像选择器
            String[] avatarSelectors = {
                ".Avatar.AppHeader-profileAvatar.css-15snhfr",
                ".Avatar.AppHeader-profileAvatar",
                "[class*='Avatar'][class*='AppHeader-profileAvatar']",
                "[data-testid='avatar']",
                ".AppHeader .Avatar"
            };
            
            for (String selector : avatarSelectors) {
                // 检查页面状态
                if (page.isClosed()) {
                    throw new RuntimeException("页面在查找用户头像时已关闭");
                }
                
                Locator avatarArea = page.locator(selector);
                if (avatarArea.count() > 0) {
                    try {
                        String avatarAlt = avatarArea.first().getAttribute("alt");
                        String userName = avatarAlt;
                        if (userName != null && !userName.trim().isEmpty()) {
                            // 清理用户名格式
                            if (userName.startsWith("点击打开")) {
                                userName = userName.substring("点击打开".length());
                            }
                            if (userName.endsWith("的主页")) {
                                userName = userName.substring(0, userName.length() - "的主页".length());
                            }
                            return userName.trim();
                        }
                    } catch (Exception e) {
                        continue;
                    }
                }
            }

            // 检查是否存在登录按钮
            String[] loginSelectors = {
                "button:has-text('登录')",
                "a:has-text('登录')", 
                ".signin-btn", 
                ".login-btn",
                "[data-testid='login-button']",
                ".AppHeader button:has-text('登录')"
            };
            
            for (String selector : loginSelectors) {
                // 检查页面状态
                if (page.isClosed()) {
                    throw new RuntimeException("页面在查找登录按钮时已关闭");
                }
                
                Locator loginButtons = page.locator(selector);
                if (loginButtons.count() > 0) {
                    return "false";
                }
            }

            // 检查URL是否重定向到登录页面
            String currentUrl = page.url();
            if (currentUrl.contains("signin") || currentUrl.contains("login")) {
                return "false";
            }

            // 如果没有找到明确的登录状态标识，返回false
            return "false";

        } catch (com.microsoft.playwright.impl.TargetClosedError e) {
            throw new RuntimeException("页面目标在检查知乎登录状态时已关闭", e);
        } catch (TimeoutError e) {
            throw new RuntimeException("检查知乎登录状态超时", e);
        } catch (Exception e) {
            throw e;
        }
    }
}
