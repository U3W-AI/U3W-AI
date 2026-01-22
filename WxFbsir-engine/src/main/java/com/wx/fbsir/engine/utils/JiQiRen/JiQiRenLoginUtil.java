package com.wx.fbsir.engine.utils.JiQiRen;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.wx.fbsir.engine.capability.base.StreamTaskHelper;
import com.wx.fbsir.engine.controller.yuanqi.YuanQiLoginController;
import com.wx.fbsir.engine.utils.yuanqi.YuanQiLoginUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 企业微信机器人登录工具类
 * 
 * 功能：提供企业微信扫码登录功能，支持二维码截图上传和登录状态检测
 * 
 * @author wxfbsir
 * @date 2025-01-21
 */
@Component
public class JiQiRenLoginUtil {
    @Autowired
    private YuanQiLoginController yuanQiLoginController;
    
    @Autowired
    private com.wx.fbsir.engine.playwright.util.ScreenshotUploadClient uploadClient;
    /**
     * 企业微信机器人扫码登录
     *
     * @param page Playwright页面对象
     * @param log  日志对象
     * @param task 流式返回
     * @param requestId 请求id
     * @param userId 用户id
     *
     * @return page Playwright页面对象
     */
    public Page scanLogin(Page page, StreamTaskHelper.StreamTask task, Logger log, String userId, String requestId){
        task.sendLog("正在等待企业微信扫码登录...");
        log.info("[机器人扫码登录] 开始等待 - 用户: {}", userId);
        
        long startTime = System.currentTimeMillis();
        long maxWaitTime = 300000; // 5分钟超时
        long lastScreenshotTime = System.currentTimeMillis();
        int screenshotCount = 1;
        String lastQrCodeUrl = null;
        
        // 立即截取初始二维码
        try {
            String initialQrCodeUrl = captureAndUpload(page, userId, "jiqiren_qrcode_initial");
            if (initialQrCodeUrl != null) {
                lastQrCodeUrl = initialQrCodeUrl;
                task.sendScreenshot(initialQrCodeUrl);
                task.sendLog("二维码已生成，请使用企业微信扫码登录");
                log.info("[机器人扫码登录] 二维码已生成 - 用户: {}, URL: {}", userId, initialQrCodeUrl);
            }
        } catch (Exception e) {
            log.warn("[机器人扫码登录] 截取初始二维码失败 - 用户: {}, 错误: {}", userId, e.getMessage());
        }

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
                task.stop();
                log.warn("[机器人扫码登录] 超时 - 用户: {}, 请求: {}", userId, requestId);
                return null;
            }

            // 每30秒更新截图
            if (System.currentTimeMillis() - lastScreenshotTime >= 30000) {
                try {
                    screenshotCount++;
                    String newQrCodeUrl = captureAndUpload(page, userId, "jiqiren_qrcode_" + screenshotCount);
                    if (newQrCodeUrl != null) {
                        lastQrCodeUrl = newQrCodeUrl;
                        task.sendScreenshot(newQrCodeUrl);
                    }
                } catch (Exception e) {
                    log.warn("[机器人扫码登录] 更新截图失败 - 用户: {}, 错误: {}", userId, e.getMessage());
                }
                task.sendLog("等待登录中...（已等待" + (elapsedTime / 1000) + "秒）");
                lastScreenshotTime = System.currentTimeMillis();
            }

            boolean isQuitElementExist = true;
            try {
                // 等待“退出”元素出现，超时30秒
                page.waitForSelector(":has-text('退出')", new Page.WaitForSelectorOptions()
                        .setTimeout(30000));
            } catch (TimeoutError e) {
                // 捕获超时异常 → 判定元素未出现
                isQuitElementExist = false;
            } catch (Exception e) {
                // 其他异常也判定为未出现
                isQuitElementExist = false;
            }
            if (isQuitElementExist) {
                // 元素存在则代表登录成功
                task.sendLog("企业微信登录成功！");
                log.info("[机器人扫码登录] 成功 - 用户: {}", userId);
                
                // 登录成功后截图
                try {
                    String successScreenshot = captureAndUpload(page, userId, "jiqiren_login_success");
                    if (successScreenshot != null) {
                        task.sendScreenshot(successScreenshot);
                    }
                } catch (Exception e) {
                    log.warn("[机器人扫码登录] 截取成功截图失败 - 用户: {}, 错误: {}", userId, e.getMessage());
                }
                break;
            }

            // 等待2秒后再次检测
            page.waitForTimeout(2000);
        }
        return page;
    }
    
    /**
     * 截图并上传到 Admin 服务器
     * 
     * @param page 页面对象
     * @param userId 用户ID
     * @param fileName 文件名（不含扩展名）
     * @return 上传后的图片URL，失败返回null
     */
    private String captureAndUpload(Page page, String userId, String fileName) {
        try {
            // 截图获取字节数组
            byte[] screenshotBytes = page.screenshot();
            
            // 上传到 Admin 服务器
            com.wx.fbsir.engine.playwright.util.ScreenshotUploadClient.UploadResult result = 
                uploadClient.uploadScreenshot(userId, fileName, screenshotBytes);
            
            if (result.isSuccess()) {
                String uploadedUrl = result.getUrl();
                return uploadedUrl;
            } else {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }
}
