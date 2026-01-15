package com.wx.fbsir.engine.utils.yuanqi;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.wx.fbsir.engine.capability.base.StreamTaskHelper;
import com.wx.fbsir.engine.controller.yuanqi.YuanQiLoginController;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 元器（YuanQi）登录工具类
 * 
 * 功能说明：
 * 1. 登录状态检测 - 检查左侧菜单是否显示"登录"按钮
 * 2. 导航到登录页面
 * 3. 触发扫码登录
 * 4. 登录状态监测
 * 
 * @author wxfbsir
 * @date 2025-01-06
 */
@Slf4j
@Component
public class YuanQiLoginUtil {
    @Autowired
    private YuanQiLoginController yuanQiLoginController;
    private static final String YUANQI_HOME_URL = "https://yuanqi.tencent.com/";
    
    /**
     * 检查元器登录状态
     * 
     * 检测逻辑：
     * - 检查左侧菜单是否存在"登录"按钮
     * - 如果存在登录按钮，返回 "false"（未登录）
     * - 如果不存在登录按钮，则已登录，尝试提取用户信息
     * 
     * @param page Playwright页面对象
     * @param shouldNavigate 是否先导航到首页（true=导航，false=直接检测当前页面）
     * @return "false"表示未登录，否则返回用户名或"已登录"
     */
    public String checkLoginStatus(Page page, boolean shouldNavigate) {
        try {
            // 如果需要导航，先访问首页
            if (shouldNavigate) {
                log.debug("[元器登录检测] 导航到首页: {}", YUANQI_HOME_URL);
                page.navigate(YUANQI_HOME_URL);
                page.waitForLoadState();
                page.waitForTimeout(3000); // 等待页面完全加载
            }
            
            // 检查左侧菜单是否存在"登录"按钮
            // 根据用户提供的DOM结构：<button data-v-0fb5f024="" type="button">登录</button>
            Locator loginButton = page.getByText("知识库", new Page.GetByTextOptions().setExact(true)).first();
            
            if (loginButton.count() <= 0 ) {
                log.info("[元器登录检测] 未登录（未检测知识库按钮）");
                return "false";
            }
            
            // 如果没有登录按钮，说明已登录
            // 尝试获取用户信息（可能在用户头像、菜单等位置）
            log.info("[元器登录检测] 已登录");
            
            // 尝试从用户菜单获取用户名
            String userName = extractUserName(page);
            return userName != null ? userName : "已登录";
            
        } catch (Exception e) {
            log.error("[元器登录检测] 检测失败", e);
            return "false";
        }
    }
    
    /**
     * 导航到元器首页
     * 
     * @param page Playwright页面对象
     * @return 导航是否成功
     */
    public boolean navigateToHomePage(Page page) {
        try {
            log.debug("[元器导航] 访问首页: {}", YUANQI_HOME_URL);
            page.navigate(YUANQI_HOME_URL);
            page.waitForLoadState();
            page.waitForTimeout(3000); // 等待页面完全加载
            
            log.info("[元器导航] 首页加载完成");
            return true;
        } catch (Exception e) {
            log.error("[元器导航] 导航失败", e);
            return false;
        }
    }
    
    /**
     * 触发扫码登录流程
     * 
     * 步骤：
     * 1. 查找并点击"登录"按钮
     * 2. 等待登录弹窗/页面出现
     * 3. 确保微信扫码选项已选中
     * 
     * @param page Playwright页面对象
     * @return 是否成功触发登录流程
     */
    public boolean triggerScanLogin(Page page) {
        try {
            // 查找左侧菜单的"登录"按钮
            Locator checkButton = page.getByText("知识库", new Page.GetByTextOptions().setExact(true)).first();
            
            if (checkButton.count() > 0) {
                log.warn("[元器扫码登录] 找到知识库按钮，可能已登录");
                return false;
            }
            Locator loginButton = page.locator("button:has-text('新建智能体')").first();
            log.debug("[元器扫码登录] 点击登录按钮");
            loginButton.first().click();
            
            // 等待登录弹窗出现
            page.waitForTimeout(2000);
            
            // 确保选中微信登录选项
            // 根据用户提供的DOM：<label class="t-radio-button t-is-checked">微信</label>
            Locator wechatOption = page.locator("label.t-radio-button:has-text('微信')");
            if (wechatOption.count() > 0 && wechatOption.first().locator("input[checked]").count() > 0) {
                log.debug("[元器扫码登录] 微信登录已选中");
            } else if (wechatOption.count() > 0) {
                log.debug("[元器扫码登录] 切换到微信登录");
                wechatOption.first().click();
                page.waitForTimeout(1000);
            }
            
            // 等待二维码iframe加载
            // 根据DOM：<iframe src="https://open.weixin.qq.com/connect/qrconnect?...
            page.waitForTimeout(2000);
            
            log.info("[元器扫码登录] 登录界面已准备就绪");
            return true;
            
        } catch (Exception e) {
            log.error("[元器扫码登录] 触发登录失败", e);
            return false;
        }
    }
    
    /**
     * 检查是否仍在登录页面（用于轮询检测登录状态）
     * 
     * @param page Playwright页面对象
     * @return true=仍在登录页面，false=已登录成功
     */
    public boolean isStillOnLoginPage(Page page) {
        try {
            // 检查是否还存在登录弹窗或知识库按钮
            Locator loginDialog = page.locator(".hyc-login__content");
            Locator loginButton =  page.getByText("知识库", new Page.GetByTextOptions().setExact(true)).first();
            
            // 如果登录弹窗存在或知识库按钮不存在，说明还在登录页面
            boolean hasLoginDialog = loginDialog.count() > 0 && loginDialog.isVisible();
            boolean hasLoginButton = loginButton.count() <= 0;
            
            return hasLoginDialog || hasLoginButton;
            
        } catch (Exception e) {
            log.debug("[元器登录状态] 检测登录页面失败，可能已登录", e);
            return false;
        }
    }
    
    /**
     * 提取用户名
     * 
     * @param page Playwright页面对象
     * @return 用户名，如果无法提取则返回null
     */
    private String extractUserName(Page page) {
        try {
            // 尝试多种可能的用户名位置
            // 1. 用户头像旁边的文字
            Locator userNameLocator = page.locator(".user-info .user-name, .profile .name, [class*='username']");
            
            if (userNameLocator.count() > 0) {
                String userName = userNameLocator.first().textContent().trim();
                if (!userName.isEmpty()) {
                    log.debug("[元器用户名] 提取成功: {}", userName);
                    return userName;
                }
            }
            
            log.debug("[元器用户名] 无法提取用户名");
            return null;
            
        } catch (Exception e) {
            log.debug("[元器用户名] 提取失败", e);
            return null;
        }
    }
    /**
     * 腾讯元器扫码登录
     *
     * @param page Playwright页面对象
     * @param log  日志对象
     * @param task 流式返回
     * @param requestId 请求id
     * @param userId 用户id
     *
     * @return page Playwright页面对象
     */
    public Page scanLogin(Page page, StreamTaskHelper.StreamTask task, Logger log, String userId, String requestId) {
        // 检查是否已登录
        String loginStatus = checkLoginStatus(page, false);
        if ("false".equals(loginStatus)) {
            // 触发扫码登录
            task.sendLog("正在触发扫码登录...");
            boolean triggerSuccess = triggerScanLogin(page);
            if (!triggerSuccess) {
                task.sendError("无法触发登录流程，请检查页面状态");
                return null;
            }
            // 等待二维码加载完成
            page.waitForTimeout(2000);

            // 立即截图二维码并返回
            String qrCodeUrl = yuanQiLoginController.captureAndUpload(page, userId, "yuanqi_qrcode_initial");
            if (qrCodeUrl != null) {
                Map<String, Object> qrData = new HashMap<>();
                qrData.put("qrCodeUrl", qrCodeUrl);
                qrData.put("status", "waiting");
                task.sendLog("请使用微信扫码登录");
                task.sendScreenshot(qrCodeUrl);
                log.info("[元器扫码登录] 二维码已生成 - 用户: {}, URL: {}", userId, qrCodeUrl);
            }

            long startTime = System.currentTimeMillis();
            long maxWaitTime = 300000; // 5分钟超时
            long lastScreenshotTime = System.currentTimeMillis();
            int screenshotCount = 1;
            String lastQrCodeUrl = qrCodeUrl;

            // 每2秒检测一次登录状态
            while (true) {
                long elapsedTime = System.currentTimeMillis() - startTime;

                // 检查超时
                if (elapsedTime > maxWaitTime) {
                    Map<String, Object> timeoutData = new HashMap<>();
                    timeoutData.put("success", false);
                    timeoutData.put("timeout", true);
                    timeoutData.put("qrCodeUrl", lastQrCodeUrl);
                    task.sendSuccess("扫码登录超时", timeoutData);
                    log.warn("[元器扫码登录] 超时 - 用户: {}, 请求: {}", userId, requestId);
                    return null;
                }

                // 每30秒更新一次二维码截图（防止过期）
                if (System.currentTimeMillis() - lastScreenshotTime >= 30000) {
                    try {
                        screenshotCount++;
                        String newQrCodeUrl = yuanQiLoginController.captureAndUpload(page, userId,
                                "yuanqi_qrcode_" + screenshotCount);

                        if (newQrCodeUrl != null) {
                            lastQrCodeUrl = newQrCodeUrl;

                            Map<String, Object> progressData = new HashMap<>();
                            progressData.put("qrCodeUrl", lastQrCodeUrl);
                            progressData.put("status", "waiting");
                            progressData.put("elapsedSeconds", elapsedTime / 1000);

                            task.sendLog("二维码已更新，请继续扫码（已等待" + (elapsedTime / 1000) + "秒）");
                            task.sendScreenshot(newQrCodeUrl);
                        }

                        lastScreenshotTime = System.currentTimeMillis();
                    } catch (Exception screenshotEx) {
                        log.warn("[元器扫码登录] 截图更新失败 - 用户: {}", userId, screenshotEx);
                    }
                }

                // 检查登录状态（检查是否还在登录页面）
                page.waitForTimeout(1000);
                Locator closeButton = page.locator("button:has(.v-button-inner-icon) svg.v-icon.v-icon--fill.v-button-inner-icon");
                if (closeButton.count() == 6) {
                    task.sendLog("登录成功！");
                    closeButton.nth(5).click();
                    break;
                }

                // 等待2秒后再次检测
                page.waitForTimeout(2000);


            }

        }
        return page;
    }



}
