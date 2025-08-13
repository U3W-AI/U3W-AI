package com.playwright.service;

import com.microsoft.playwright.*;
import com.playwright.utils.FastLoginChecker;
import com.playwright.utils.LogMsgUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 快速登录状态检测服务
 * 专门用于高性能的登录状态检测，减少CPU和内存占用
 */
@Service
public class FastLoginService {
    
    @Autowired
    private FastLoginChecker fastLoginChecker;
    
    @Autowired
    private LogMsgUtil logInfo;
    
    /**
     * 快速检测百家号登录状态
     */
    public String fastCheckBaijiahaoLogin(String userId) {
        try (BrowserContext context = fastLoginChecker.createFastCheckContext(userId, "baijiahao")) {
            Page page = context.newPage();
            fastLoginChecker.optimizePageForFastCheck(page);
            
            // 快速导航到百家号
            page.navigate("https://baijiahao.baidu.com/", new Page.NavigateOptions().setTimeout(10000));
            fastLoginChecker.waitForFastReady(page, 5000);
            
            // 快速检测登录状态
            return checkBaijiahaoLoginStatus(page);
            
        } catch (Exception e) {
            System.err.println("快速检测百家号登录失败: " + e.getMessage());
            return "false";
        }
    }
    
    /**
     * 快速检测知乎登录状态
     */
    public String fastCheckZhihuLogin(String userId) {
        try (BrowserContext context = fastLoginChecker.createFastCheckContext(userId, "zhihu")) {
            Page page = context.newPage();
            fastLoginChecker.optimizePageForFastCheck(page);
            
            // 快速导航到知乎
            page.navigate("https://www.zhihu.com/", new Page.NavigateOptions().setTimeout(10000));
            fastLoginChecker.waitForFastReady(page, 5000);
            
            // 快速检测登录状态
            return checkZhihuLoginStatus(page);
            
        } catch (Exception e) {
            System.err.println("快速检测知乎登录失败: " + e.getMessage());
            return "false";
        }
    }
    
    /**
     * 快速检测头条号登录状态
     */
    public String fastCheckToutiaoLogin(String userId) {
        try (BrowserContext context = fastLoginChecker.createFastCheckContext(userId, "toutiao")) {
            Page page = context.newPage();
            fastLoginChecker.optimizePageForFastCheck(page);
            
            // 快速导航到头条号
            page.navigate("https://mp.toutiao.com/", new Page.NavigateOptions().setTimeout(10000));
            fastLoginChecker.waitForFastReady(page, 5000);
            
            // 快速检测登录状态
            return checkToutiaoLoginStatus(page);
            
        } catch (Exception e) {
            System.err.println("快速检测头条号登录失败: " + e.getMessage());
            return "false";
        }
    }
    
    /**
     * 快速检测AI平台登录状态（通用方法）
     */
    public String fastCheckAILogin(String userId, String platform, String url) {
        try (BrowserContext context = fastLoginChecker.createFastCheckContext(userId, platform)) {
            Page page = context.newPage();
            fastLoginChecker.optimizePageForFastCheck(page);
            
            // 快速导航
            page.navigate(url, new Page.NavigateOptions().setTimeout(10000));
            fastLoginChecker.waitForFastReady(page, 5000);
            
            // 通用登录状态检测
            return checkGeneralLoginStatus(page);
            
        } catch (Exception e) {
            System.err.println("快速检测" + platform + "登录失败: " + e.getMessage());
            return "false";
        }
    }
    
    /**
     * 百家号登录状态检测逻辑（简化版）
     */
    private String checkBaijiahaoLoginStatus(Page page) {
        try {
            // 等待关键元素加载，但不超过3秒
            page.waitForTimeout(3000);
            
            // 快速检测用户信息区域
            Locator userInfo = page.locator(".user-avatar, .user-name, .userinfo-wrapper").first();
            if (userInfo.count() > 0 && userInfo.isVisible()) {
                String userText = userInfo.textContent();
                if (userText != null && !userText.trim().isEmpty()) {
                    return userText.trim();
                }
            }
            
            // 检测登录按钮
            Locator loginBtn = page.locator(".btnlogin--bI826, .login-btn").first();
            if (loginBtn.count() > 0 && loginBtn.isVisible()) {
                return "false";
            }
            
            // 检测登录表单
            Locator loginForm = page.locator("input[type='password'], form[contains(@action, 'login')]").first();
            if (loginForm.count() > 0) {
                return "false";
            }
            
            return "false";
            
        } catch (Exception e) {
            return "false";
        }
    }
    
    /**
     * 知乎登录状态检测逻辑（简化版）
     */
    private String checkZhihuLoginStatus(Page page) {
        try {
            // 快速检测
            page.waitForTimeout(3000);
            
            // 检测用户头像或用户名
            Locator userElement = page.locator(".Avatar, .AppHeader-profile, .css-1qyytj7").first();
            if (userElement.count() > 0 && userElement.isVisible()) {
                // 尝试获取用户名
                String userName = (String) page.evaluate("""
                    () => {
                        const selectors = ['.Avatar', '.AppHeader-profile', '.css-1qyytj7'];
                        for (const selector of selectors) {
                            const element = document.querySelector(selector);
                            if (element) {
                                return element.getAttribute('alt') || element.textContent || 'Logged';
                            }
                        }
                        return null;
                    }
                """);
                return userName != null ? userName : "Logged";
            }
            
            // 检测登录按钮
            Locator loginBtn = page.locator("button:has-text('登录'), a:has-text('登录')").first();
            if (loginBtn.count() > 0 && loginBtn.isVisible()) {
                return "false";
            }
            
            return "false";
            
        } catch (Exception e) {
            return "false";
        }
    }
    
    /**
     * 头条号登录状态检测逻辑（简化版）
     */
    private String checkToutiaoLoginStatus(Page page) {
        try {
            page.waitForTimeout(3000);
            
            // 检测用户信息
            Locator userInfo = page.locator(".user-info, .avatar-wrapper, .header-user").first();
            if (userInfo.count() > 0 && userInfo.isVisible()) {
                String userText = userInfo.textContent();
                if (userText != null && !userText.trim().isEmpty()) {
                    return userText.trim();
                }
            }
            
            // 检测登录相关元素
            Locator loginElement = page.locator("input[type='password'], .login-btn, button:has-text('登录')").first();
            if (loginElement.count() > 0) {
                return "false";
            }
            
            return "false";
            
        } catch (Exception e) {
            return "false";
        }
    }
    
    /**
     * 通用登录状态检测（适用于大多数AI平台）
     */
    private String checkGeneralLoginStatus(Page page) {
        try {
            page.waitForTimeout(2000);
            
            // 检测常见的登录状态指示器
            Object loginStatus = page.evaluate("""
                () => {
                    // 检测用户头像、用户名等已登录元素
                    const loggedInSelectors = [
                        '.avatar', '.user-avatar', '.user-name', '.username',
                        '.user-info', '.profile', '.account-info',
                        '[data-testid="user-menu"]', '[data-testid="profile"]'
                    ];
                    
                    for (const selector of loggedInSelectors) {
                        const element = document.querySelector(selector);
                        if (element && element.offsetParent !== null) {
                            const text = element.textContent || element.alt || '';
                            if (text.trim()) {
                                return text.trim();
                            }
                            return 'Logged';
                        }
                    }
                    
                    // 检测登录按钮或登录表单（表示未登录）
                    const loginSelectors = [
                        'button:contains("登录")', 'a:contains("登录")', '.login-btn',
                        'input[type="password"]', 'form[action*="login"]',
                        'button:contains("Sign in")', 'button:contains("Login")'
                    ];
                    
                    for (const selector of loginSelectors) {
                        const element = document.querySelector(selector);
                        if (element && element.offsetParent !== null) {
                            return 'false';
                        }
                    }
                    
                    return 'unknown';
                }
            """);
            
            return loginStatus != null ? loginStatus.toString() : "false";
            
        } catch (Exception e) {
            return "false";
        }
    }
} 