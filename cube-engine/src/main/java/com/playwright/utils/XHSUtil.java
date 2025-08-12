package com.playwright.utils;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.playwright.entity.ImageTextRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * @Author Moyesan
 * @Description
 * @Date 2025/08/09
 */
@Component
public class XHSUtil {
    /**
     * 检测小红书登录状态并获取用户名
     * @param page Playwright页面实例
     * @return 用户名或"false"
     */
    public String checkLoginStatus(Page page) throws InterruptedException {
        try {
            // 检查用户头像和用户名（最常见的方式）
            // 知乎登录后，右上角通常有用户头像
            Thread.sleep(1000);
            Locator avatarArea = page.locator(".name-box");
            if (avatarArea.count() > 0) {
                Thread.sleep(1000);
                String userName = page.locator(".name-box").textContent();

                return userName;
            }

            // 检查当前URL是否跳转到登录页面
            String currentUrl = page.url();
            if (currentUrl.contains("signin") || currentUrl.contains("login")) {
                System.out.println("页面跳转到登录页面，用户未登录");
                return "false";
            }
            return "false";

        } catch (Exception e) {
            System.out.println("登录状态检测异常: " + e.getMessage());
            throw e;
        }
    }


}
