package com.wx.fbsir.engine.controller.yuanqi;

import com.microsoft.playwright.Page;
import com.wx.fbsir.engine.capability.annotation.OnceCapability;
import com.wx.fbsir.engine.capability.annotation.StreamCapability;
import com.wx.fbsir.engine.capability.base.StreamTaskHelper;
import com.wx.fbsir.engine.playwright.pool.BrowserPoolManager;
import com.wx.fbsir.engine.playwright.session.BrowserSession;
import com.wx.fbsir.engine.utils.yuanqi.YuanQiLoginUtil;
import com.wx.fbsir.engine.websocket.message.EngineMessage;
import com.wx.fbsir.engine.websocket.message.MessageType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.Map;

/**
 * 元器（YuanQi）登录控制器
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 功能概述
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 1. 登录状态检测 - 检查用户是否已登录元器
 * 2. 二维码扫码登录 - 获取登录二维码，实时监测登录状态
 * 3. 测试功能 - 打开页面供开发者查看登录状态
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 消息类型
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * - YUANQI_CHECK_LOGIN: 检查登录状态
 * - YUANQI_SCAN_LOGIN: 扫码登录
 * - YUANQI_TEST_VIEW: 测试查看页面
 * 
 * @author wxfbsir
 * @date 2025-01-06
 */
@Controller
public class YuanQiLoginController extends StreamTaskHelper {

    @Autowired
    private YuanQiLoginUtil loginUtil;
    
    @Autowired
    private BrowserPoolManager browserPool;
    
    @Autowired
    @Lazy
    private com.wx.fbsir.engine.websocket.client.WebSocketClientManager webSocketClientManager;
    
    @Autowired
    private com.wx.fbsir.engine.playwright.util.ScreenshotUploadClient uploadClient;

    /**
     * 检查元器登录状态（单次返回）
     * 
     * 请求JSON示例：
     * {"type": "YUANQI_CHECK_LOGIN", "engineId": "engine-001"}
     * 
     * 返回数据：
     * - isLoggedIn: 是否已登录（boolean）
     * - userName: 用户名（如果已登录）
     * - platform: 平台名称（YuanQi）
     */
    @OnceCapability(
        type = "YUANQI_CHECK_LOGIN",
        description = "检查元器登录状态",
        timeout = 30000L
    )
    public void handleCheckLogin(EngineMessage message) {
        String userId = message.getUserId();
        String requestId = message.getPayloadValue("requestId");
        
        log.info("[元器登录检测] 开始 - 用户: {}, 请求: {}", userId, requestId);
        
        BrowserSession session = null;
        
        try {
            // 获取持久化浏览器会话
            session = browserPool.acquirePersistent(userId, "yuanqi", false);
            
            // 🔥 使用通用框架恢复登录状态
            if (com.wx.fbsir.engine.playwright.login.manager.LoginStateManager.hasLoginState("yuanqi", userId)) {
                try {
                    boolean restored = com.wx.fbsir.engine.playwright.login.manager.LoginStateManager
                        .restoreLoginState(session, "yuanqi", userId);
                    if (restored) {
                        log.info("[元器登录检测] ✅ 登录状态已恢复 - 用户: {}", userId);
                    } else {
                        log.warn("[元器登录检测] ⚠️ 登录状态恢复失败 - 用户: {}", userId);
                    }
                } catch (Exception e) {
                    log.warn("[元器登录检测] ⚠️ 登录状态恢复异常: {}", e.getMessage());
                }
            }
            
            Page page = session.getOrCreatePage();
            
            // 检查登录状态
            String loginStatus = loginUtil.checkLoginStatus(page, true);
            boolean isLoggedIn = !"false".equals(loginStatus);
            
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("isLoggedIn", isLoggedIn);
            resultData.put("userName", isLoggedIn ? loginStatus : null);
            resultData.put("platform", "YuanQi");
            resultData.put("timestamp", System.currentTimeMillis());
            
            // 发送结果
            sendResult(userId, requestId, resultData);
            log.info("[元器登录检测] 完成 - 用户: {}, 已登录: {}", userId, isLoggedIn);
            
        } catch (Exception e) {
            log.error("[元器登录检测] 失败 - 用户: {}, 请求: {}", userId, requestId, e);
            sendErrorResult(userId, requestId, "登录检测失败: " + e.getMessage());
        } finally {
            if (session != null) {
                try {
                    browserPool.destroy(session);
                    log.debug("[元器登录检测] 已销毁会话释放资源 - 用户: {}", userId);
                } catch (Exception e) {
                    log.warn("[元器登录检测] 销毁会话失败 - 用户: {}, 错误: {}", userId, e.getMessage());
                }
            }
        }
    }
    
    /**
     * 元器扫码登录（流式返回）
     * 
     * 请求参数：
     * - userId: 用户ID（必填）
     * - requestId: 请求ID（Admin自动生成）
     * 
     * 进度推送：
     * - qrCodeUrl: 二维码图片URL
     * - status: 当前状态（waiting/checking/success/timeout）
     */
    @StreamCapability(
        type = "YUANQI_SCAN_LOGIN",
        description = "元器扫码登录",
        progressInterval = 2000
    )
    public void handleScanLogin(EngineMessage message) {
        String userId = message.getUserId();
        String requestId = message.getPayloadValue("requestId");
        
        log.info("[元器扫码登录] 开始 - 用户: {}, 请求: {}", userId, requestId);
        
        StreamTask task = startStreamTask(userId, requestId, 2000);
        BrowserSession session = null;
        
        try {
            task.sendLog("正在打开元器登录页面...");
            
            // 获取持久化浏览器会话
            session = browserPool.acquirePersistent(userId, "yuanqi", false);
            Page page = session.getOrCreatePage();
            
            // 导航到首页
            task.sendLog("正在加载元器首页...");
            boolean navSuccess = loginUtil.navigateToHomePage(page);
            
            if (!navSuccess) {
                task.sendError("无法加载元器首页，请检查网络连接");
                return;
            }
            
            // 检查是否已登录
            String loginStatus = loginUtil.checkLoginStatus(page, false);
            if (!"false".equals(loginStatus)) {
                task.sendLog("检测到已登录状态，无需扫码");
                Map<String, Object> alreadyLoggedInData = new HashMap<>();
                alreadyLoggedInData.put("success", true);
                alreadyLoggedInData.put("userName", loginStatus);
                alreadyLoggedInData.put("alreadyLoggedIn", true);
                task.sendSuccess("已登录，用户: " + loginStatus, alreadyLoggedInData);
                return;
            }
            
            // 触发扫码登录
            task.sendLog("正在触发扫码登录...");
            boolean triggerSuccess = loginUtil.triggerScanLogin(page);
            
            if (!triggerSuccess) {
                task.sendError("无法触发登录流程，请检查页面状态");
                return;
            }
            
            // 等待二维码加载完成
            page.waitForTimeout(2000);
            
            // 立即截图二维码并返回
            String qrCodeUrl = captureAndUpload(page, userId, "yuanqi_qrcode_initial");
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
                    return;
                }
                
                // 每30秒更新一次二维码截图（防止过期）
                if (System.currentTimeMillis() - lastScreenshotTime >= 30000) {
                    try {
                        screenshotCount++;
                        String newQrCodeUrl = captureAndUpload(page, userId, 
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
                boolean stillOnLoginPage = loginUtil.isStillOnLoginPage(page);
                
                if (!stillOnLoginPage) {
                    // 页面已刷新，说明登录成功
                    page.waitForTimeout(2000); // 等待页面稳定
                    
                    // 再次检查登录状态确认
                    String finalLoginStatus = loginUtil.checkLoginStatus(page, false);
                    
                    if (!"false".equals(finalLoginStatus)) {
                        Map<String, Object> successData = new HashMap<>();
                        successData.put("success", true);
                        successData.put("isLoggedIn", true);  // 明确标识已登录
                        successData.put("userName", finalLoginStatus);
                        successData.put("platform", "YuanQi");
                        successData.put("qrCodeUrl", lastQrCodeUrl);
                        successData.put("loginTime", elapsedTime / 1000);
                        successData.put("timestamp", System.currentTimeMillis());
                        
                        task.sendSuccess("登录成功！欢迎，" + finalLoginStatus, successData);
                        log.info("[元器扫码登录] 成功 - 用户: {}, 元器用户: {}", userId, finalLoginStatus);
                        
                        // 🔥 使用通用框架保存登录状态
                        task.sendLog("正在保存登录状态...");
                        try {
                            Thread.sleep(2000);
                            
                            boolean saved = com.wx.fbsir.engine.playwright.login.manager.LoginStateManager
                                .saveLoginState(session, "yuanqi", userId, finalLoginStatus);
                            
                            if (saved) {
                                log.info("[元器扫码登录] ✅ 登录状态已保存 - 用户: {}", userId);
                            } else {
                                log.warn("[元器扫码登录] ⚠️ 登录状态保存失败 - 用户: {}", userId);
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } catch (Exception e) {
                            log.error("[元器扫码登录] 保存登录状态异常: {}", e.getMessage(), e);
                        }
                        
                        return;
                    }
                }
                
                // 等待2秒后再次检测
                page.waitForTimeout(2000);
            }
            
        } catch (Exception e) {
            log.error("[元器扫码登录] 失败 - 用户: {}, 请求: {}", userId, requestId, e);
            task.sendError("扫码登录失败: " + e.getMessage());
        } finally {
            task.stop();
            
            if (session != null) {
                try {
                    browserPool.destroy(session);
                    log.debug("[元器扫码登录] 已销毁会话释放资源 - 用户: {}", userId);
                } catch (Exception e) {
                    log.warn("[元器扫码登录] 销毁会话失败 - 用户: {}, 错误: {}", userId, e.getMessage());
                }
            }
        }
    }
    
    /**
     * 测试功能：打开元器页面5秒供开发者查看
     */
    @StreamCapability(
        type = "YUANQI_TEST_VIEW",
        description = "测试查看元器页面",
        progressInterval = 1000
    )
    public void handleTestView(EngineMessage message) {
        String userId = message.getUserId();
        String requestId = message.getPayloadValue("requestId");
        
        log.info("[元器测试查看] 开始 - 用户: {}, 请求: {}", userId, requestId);
        
        StreamTask task = startStreamTask(userId, requestId, 1000);
        BrowserSession session = null;
        
        try {
            task.sendLog("正在打开元器页面...");
            
            // 获取持久化浏览器会话
            session = browserPool.acquirePersistent(userId, "yuanqi", false);
            Page page = session.getOrCreatePage();
            
            // 导航到首页
            task.sendLog("正在加载元器首页...");
            boolean navSuccess = loginUtil.navigateToHomePage(page);
            
            if (!navSuccess) {
                task.sendError("无法加载元器首页，请检查网络连接");
                return;
            }
            
            // 保持页面打开5秒，每秒截图一次
            java.util.List<String> screenshots = new java.util.ArrayList<>();
            
            for (int i = 1; i <= 5; i++) {
                task.sendLog("测试查看中... (" + i + "/5秒)");
                
                String screenshotUrl = captureAndUpload(page, userId, "yuanqi_test_" + i);
                if (screenshotUrl != null) {
                    screenshots.add(screenshotUrl);
                    task.sendScreenshot(screenshotUrl);
                }
                
                // 等待1秒
                if (i < 5) {
                    page.waitForTimeout(1000);
                }
            }
            
            // 检查登录状态
            task.sendLog("正在检查登录状态...");
            String loginStatus = loginUtil.checkLoginStatus(page, false);
            boolean isLoggedIn = !"false".equals(loginStatus);
            
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("screenshots", screenshots);
            resultData.put("loginStatus", isLoggedIn ? loginStatus : "未登录");
            resultData.put("isLoggedIn", isLoggedIn);
            resultData.put("testDuration", 5);
            resultData.put("platform", "YuanQi");
            
            task.sendSuccess("测试完成，共截图" + screenshots.size() + "张", resultData);
            log.info("[元器测试查看] 完成 - 用户: {}, 登录状态: {}, 截图数: {}", 
                userId, isLoggedIn, screenshots.size());
            
        } catch (Exception e) {
            log.error("[元器测试查看] 失败 - 用户: {}, 请求: {}", userId, requestId, e);
            task.sendError("测试查看失败: " + e.getMessage());
        } finally {
            task.stop();
            
            if (session != null) {
                try {
                    browserPool.destroy(session);
                    log.debug("[元器测试查看] 已销毁会话释放资源 - 用户: {}", userId);
                } catch (Exception e) {
                    log.warn("[元器测试查看] 销毁会话失败 - 用户: {}, 错误: {}", userId, e.getMessage());
                }
            }
        }
    }
    
    /**
     * 发送成功结果
     */
    private void sendResult(String userId, String requestId, Map<String, Object> data) {
        EngineMessage result = EngineMessage.builder()
            .type(MessageType.TASK_RESULT.getCode())
            .userId(userId)
            .payload("requestId", requestId)
            .payload("success", true)
            .payload("data", data)
            .payload("timestamp", System.currentTimeMillis())
            .build();
        
        webSocketClientManager.sendMessage(result);
        log.debug("[元器登录] 发送结果 - 用户: {}, 请求: {}", userId, requestId);
    }
    
    /**
     * 发送错误结果
     */
    private void sendErrorResult(String userId, String requestId, String errorMessage) {
        EngineMessage result = EngineMessage.builder()
            .type(MessageType.TASK_RESULT.getCode())
            .userId(userId)
            .payload("requestId", requestId)
            .payload("success", false)
            .payload("errorCode", "TASK_ERROR")
            .payload("errorMessage", errorMessage)
            .payload("timestamp", System.currentTimeMillis())
            .build();
        
        webSocketClientManager.sendMessage(result);
        log.error("[元器登录] 发送错误 - 用户: {}, 请求: {}, 错误: {}", userId, requestId, errorMessage);
    }
    
    /**
     * 截图并上传到 Admin 服务器
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
                log.info("[元器截图] 上传成功 - URL: {}", uploadedUrl);
                return uploadedUrl;
            } else {
                log.error("[元器截图] 上传失败 - 错误: {}", result.getErrorMessage());
                return null;
            }
        } catch (Exception e) {
            log.error("[元器截图] 截图失败 - 错误: {}", e.getMessage(), e);
            return null;
        }
    }
}
