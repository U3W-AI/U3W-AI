package com.wx.fbsir.engine.controller.ai;

import com.microsoft.playwright.Page;
import com.wx.fbsir.engine.capability.annotation.OnceCapability;
import com.wx.fbsir.engine.capability.annotation.StreamCapability;
import com.wx.fbsir.engine.capability.base.StreamTaskHelper;
import com.wx.fbsir.engine.playwright.pool.BrowserPoolManager;
import com.wx.fbsir.engine.playwright.session.BrowserSession;
import com.wx.fbsir.engine.playwright.util.ScreenshotUtil;
import com.wx.fbsir.engine.utils.ai.DeepSeekUtil;
import com.wx.fbsir.engine.websocket.message.EngineMessage;
import com.wx.fbsir.engine.websocket.message.MessageType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * DeepSeek AI WebSocket 控制器
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 功能概述
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 1. 登录状态检测 - 检查用户是否已登录DeepSeek
 * 2. 二维码扫码登录 - 获取登录二维码，实时监测登录状态
 * 3. AI咨询服务 - 支持普通模式、深度思考、联网搜索
 * 4. 会话管理 - 支持会话ID传递，实现上下文连续对话
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 消息类型
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * - DEEPSEEK_CHECK_LOGIN: 检查登录状态（非AI业务）
 * - DEEPSEEK_SCAN_LOGIN: 扫码登录（非AI业务）
 * - AI_DEEPSEEK_QUERY: AI咨询（支持深度思考和联网搜索）
 * 
 * @author wxfbsir
 * @date 2025-12-25
 */
@Controller
public class DeepSeekController extends StreamTaskHelper {

    @Autowired
    private DeepSeekUtil deepSeekUtil;
    
    @Autowired
    private BrowserPoolManager browserPool;
    
    @Autowired
    private ScreenshotUtil screenshotUtil;
    
    @Autowired
    @Lazy
    private com.wx.fbsir.engine.websocket.client.WebSocketClientManager webSocketClientManager;
    
    @Autowired
    private com.wx.fbsir.engine.playwright.util.ScreenshotUploadClient uploadClient;

    /**
     * 检查DeepSeek登录状态（单次返回）
     * 
     * 请求参数：
     * - userId: 用户ID（必填）
     * - requestId: 请求ID（Admin自动生成）
     * 
     * 返回数据：
     * - isLoggedIn: 是否已登录（boolean）
     * - userName: 用户名（如果已登录）
     */
    @OnceCapability(
        type = "DEEPSEEK_CHECK_LOGIN",
        description = "检查DeepSeek登录状态",
        timeout = 30000L
    )
    public void handleCheckLogin(EngineMessage message) {
        String userId = message.getUserId();
        String sessionId = extractSessionId(message);
        String aiType = extractAiType(message);
        
        log.info("[DeepSeek登录检测] 开始 - 用户: {}, 会话: {}, AI: {}", userId, sessionId, aiType);
        
        try {
            BrowserSession session = null;
            try {
                session = browserPool.acquirePersistent(userId, "deepseek", false);
                String loginStatus = deepSeekUtil.checkLoginStatus(session.getOrCreatePage(), true);
                boolean isLoggedIn = !"false".equals(loginStatus);
                
                Map<String, Object> resultData = new HashMap<>();
                resultData.put("isLoggedIn", isLoggedIn);
                resultData.put("userName", isLoggedIn ? loginStatus : null);
                resultData.put("platform", "DeepSeek");
                resultData.put("timestamp", System.currentTimeMillis());
                
                // 发送结果（携带sessionId和aiType）
                sendResult(userId, sessionId, aiType, resultData);
                log.info("[DeepSeek登录检测] 完成 - 用户: {}, 会话: {}, 已登录: {}", userId, sessionId, isLoggedIn);
            } finally {
                // 🔥 关键：完全销毁会话释放锁文件（数据已持久化到磁盘）
                if (session != null) {
                    try {
                        session.destroy();
                        log.debug("[DeepSeek登录检测] 已销毁会话释放资源 - 用户: {}", userId);
                    } catch (Exception e) {
                        log.warn("[DeepSeek登录检测] 销毁会话失败 - 用户: {}, 错误: {}", userId, e.getMessage());
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("[DeepSeek登录检测] 失败 - 用户: {}, 会话: {}", userId, sessionId, e);
            sendErrorResult(userId, sessionId, aiType, "登录检测失败: " + e.getMessage());
        }
    }
    
    /**
     * 提取sessionId（会话ID，与系统的requestId区分）
     */
    private String extractSessionId(EngineMessage message) {
        Object sessionId = message.getPayloadValue("sessionId");
        return sessionId != null ? sessionId.toString() : "unknown";
    }
    
    /**
     * 提取aiType
     */
    private String extractAiType(EngineMessage message) {
        Object aiType = message.getPayloadValue("aiType");
        return aiType != null ? aiType.toString() : "deepseek";
    }
    
    /**
     * 发送成功结果（携带sessionId和aiType）
     */
    private void sendResult(String userId, String sessionId, String aiType, Map<String, Object> data) {
        EngineMessage result = EngineMessage.builder()
            .type(MessageType.TASK_RESULT.getCode())
            .userId(userId)
            .payload("sessionId", sessionId)    // 会话ID
            .payload("aiType", aiType)          // AI类型
            .payload("success", true)
            .payload("data", data)
            .payload("timestamp", System.currentTimeMillis())
            .build();
        
        webSocketClientManager.sendMessage(result);
        log.debug("[DeepSeek] 发送结果 - 用户: {}, 会话: {}, AI: {}", userId, sessionId, aiType);
    }
    
    /**
     * 发送错误结果（携带sessionId和aiType）
     */
    private void sendErrorResult(String userId, String sessionId, String aiType, String errorMessage) {
        EngineMessage result = EngineMessage.builder()
            .type(MessageType.TASK_RESULT.getCode())
            .userId(userId)
            .payload("sessionId", sessionId)    // 会话ID
            .payload("aiType", aiType)          // AI类型
            .payload("success", false)
            .payload("errorCode", "TASK_ERROR")
            .payload("errorMessage", errorMessage)
            .payload("timestamp", System.currentTimeMillis())
            .build();
        
        webSocketClientManager.sendMessage(result);
        log.error("[DeepSeek] 发送错误 - 用户: {}, 会话: {}, AI: {}, 错误: {}", userId, sessionId, aiType, errorMessage);
    }

    /**
     * DeepSeek扫码登录（流式返回）
     * 
     * 功能说明：
     * 1. 导航到登录页面
     * 2. 立即截图二维码并返回给用户
     * 3. 每2秒检测一次登录状态
     * 4. 每30秒更新一次二维码截图（防止过期）
     * 5. 登录成功后立即返回用户信息
     * 
     * 请求参数：
     * - userId: 用户ID（必填）
     * - requestId: 请求ID（Admin自动生成）
     * 
     * 进度推送：
     * - qrCodeUrl: 二维码图片URL
     * - status: 当前状态（waiting/checking/success/timeout）
     * 
     * 返回数据：
     * - success: 是否登录成功
     * - userName: 用户名（登录成功时）
     * - qrCodeUrl: 最后一次二维码URL
     */
    @StreamCapability(
        type = "DEEPSEEK_SCAN_LOGIN",
        description = "DeepSeek扫码登录（每2秒检测登录状态）",
        progressInterval = 2000
    )
    public void handleScanLogin(EngineMessage message) {
        String userId = message.getUserId();
        String sessionId = extractSessionId(message);
        String aiType = extractAiType(message);
        
        log.info("[DeepSeek扫码登录] 开始 - 用户: {}, 会话: {}, AI: {}", userId, sessionId, aiType);
        
        StreamTask task = startStreamTask(userId, sessionId, aiType, 2000);
        BrowserSession session = null;
        
        try {
            task.sendLog("正在打开DeepSeek登录页面...");
            
            session = browserPool.acquirePersistent(userId, "deepseek", false);
                Page page = session.getOrCreatePage();
                
                task.sendLog("正在加载二维码...");
                boolean navSuccess = deepSeekUtil.navigateToLoginPage(page);
                
                if (!navSuccess) {
                    task.sendError("无法加载登录页面，请检查网络连接");
                    return;
                }
                
                // 立即截图二维码并返回
                String qrCodeUrl = captureAndUpload(page, userId, "deepseek_qrcode_initial");
                if (qrCodeUrl != null) {
                    Map<String, Object> qrData = new HashMap<>();
                    qrData.put("qrCodeUrl", qrCodeUrl);
                    qrData.put("status", "waiting");
                    task.sendLog("请使用微信扫码登录");
                    task.sendScreenshot(qrCodeUrl);
                    log.info("[DeepSeek扫码登录] 二维码已生成 - 用户: {}, URL: {}", userId, qrCodeUrl);
                }
                
                long startTime = System.currentTimeMillis();
                long maxWaitTime = 300000; // 5分钟超时
                long lastScreenshotTime = System.currentTimeMillis();
                int screenshotCount = 1;
                String lastQrCodeUrl = qrCodeUrl;
                
                // 每2秒检测一次登录状态
                while (true) {
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    
                    if (elapsedTime > maxWaitTime) {
                        Map<String, Object> timeoutData = new HashMap<>();
                        timeoutData.put("success", false);
                        timeoutData.put("timeout", true);
                        timeoutData.put("qrCodeUrl", lastQrCodeUrl);
                        task.sendSuccess("扫码登录超时", timeoutData);
                        log.warn("[DeepSeek扫码登录] 超时 - 用户: {}, 会话: {}", userId, sessionId);
                        return;
                    }
                    
                    // 每30秒更新一次二维码截图（防止过期）
                    if (System.currentTimeMillis() - lastScreenshotTime >= 30000) {
                        try {
                            screenshotCount++;
                            String newQrCodeUrl = captureAndUpload(page, userId, 
                                "deepseek_qrcode_" + screenshotCount);
                            
                            if (newQrCodeUrl != null) {
                                lastQrCodeUrl = newQrCodeUrl;
                                
                                Map<String, Object> progressData = new HashMap<>();
                                progressData.put("qrCodeUrl", lastQrCodeUrl);
                                progressData.put("status", "waiting");
                                progressData.put("elapsedSeconds", elapsedTime / 1000);
                                
                                task.sendLog("二维码已更新，请继续扫码（已等待" + (elapsedTime / 1000) + "秒）");
                                if (newQrCodeUrl != null) {
                                    task.sendScreenshot(newQrCodeUrl);
                                }
                                log.debug("[DeepSeek扫码登录] 更新二维码 #{} - 用户: {}", screenshotCount, userId);
                            }
                            
                            lastScreenshotTime = System.currentTimeMillis();
                        } catch (Exception screenshotEx) {
                            log.warn("[DeepSeek扫码登录] 截图更新失败 - 用户: {}", userId, screenshotEx);
                        }
                    }
                    
                    // 检查登录状态（不导航，直接检测当前页面）
                    String loginStatus = deepSeekUtil.checkLoginStatus(page, false);
                    if (!"false".equals(loginStatus)) {
                        Map<String, Object> successData = new HashMap<>();
                        successData.put("success", true);
                        successData.put("userName", loginStatus);
                        successData.put("qrCodeUrl", lastQrCodeUrl);
                        successData.put("loginTime", elapsedTime / 1000);
                        
                        task.sendSuccess("登录成功！欢迎，" + loginStatus, successData);
                        log.info("[DeepSeek扫码登录] 成功 - 用户: {}, DeepSeek用户: {}", userId, loginStatus);
                        
                        // 🔥 关键：登录成功后等待3秒让Chromium完成数据持久化
                        // Cookies/LocalStorage需要异步写入磁盘
                        try {
                            Thread.sleep(3000);
                            log.debug("[DeepSeek扫码登录] 等待数据持久化完成 - 用户: {}", userId);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        
                        return;
                    }
                    
                    // 等待2秒后再次检测
                    page.waitForTimeout(2000);
                }
            
        } catch (Exception e) {
            log.error("[DeepSeek扫码登录] 失败 - 用户: {}, 会话: {}", userId, sessionId, e);
            task.sendError("扫码登录失败: " + e.getMessage());
        } finally {
            task.stop();
            
            // 🔥 关键：完全销毁会话释放SingletonLock（数据已持久化）
            if (session != null) {
                try {
                    session.destroy();
                    log.debug("[DeepSeek扫码登录] 已销毁会话释放资源 - 用户: {}", userId);
                } catch (Exception e) {
                    log.warn("[DeepSeek扫码登录] 销毁会话失败 - 用户: {}, 错误: {}", userId, e.getMessage());
                }
            }
        }
    }

    /**
     * DeepSeek AI咨询（流式返回）
     * 
     * 功能说明：
     * 1. 支持普通模式、深度思考、联网搜索
     * 2. 支持会话ID传递，实现上下文连续对话
     * 3. 实时推送进度（每6秒截图一次）
     * 4. 自动提取AI回复内容
     * 5. 返回会话ID供下次使用
     * 
     * 请求参数：
     * - userId: 用户ID（必填）
     * - requestId: 请求ID（Admin自动生成）
     * - query: 用户问题（必填）
     * - enableDeepThinking: 是否启用深度思考（可选，默认false）
     * - enableWebSearch: 是否启用联网搜索（可选，默认false）
     * - chatId: 会话ID（可选，传入后继续该会话）
     * 
     * 进度推送：
     * - status: 当前状态（sending/waiting/processing/extracting）
     * - screenshotUrl: 进度截图URL
     * - elapsedSeconds: 已耗时（秒）
     * 
     * 返回数据：
     * - answer: AI回复内容
     * - chatId: 会话ID（用于下次继续对话）
     * - shareUrl: 分享链接
     * - mode: 使用的模式（normal/deepThinking/webSearch/both）
     */
    @StreamCapability(
        type = "AI_DEEPSEEK_QUERY",
        description = "DeepSeek AI咨询（支持深度思考和联网搜索）",
        progressInterval = 6000
    )
    public void handleQuery(EngineMessage message) {
        String userId = message.getUserId();
        String sessionId = extractSessionId(message);
        String aiType = extractAiType(message);
        
        log.info("[DeepSeek咨询] 收到消息 - 会话: {}, AI: {}", sessionId, aiType);
        
        // 直接从原始JSON解析payload，完全绕过fastjson2的Map序列化问题
        String rawJson = message.getRawJson();
        if (rawJson == null || rawJson.isEmpty()) {
            log.error("[DeepSeek咨询] 原始JSON为空");
            return;
        }
        
        // 直接解析原始JSON获取payload
        com.alibaba.fastjson2.JSONObject rootJson = com.alibaba.fastjson2.JSON.parseObject(rawJson);
        if (rootJson == null) {
            log.error("[DeepSeek咨询] JSON解析失败");
            return;
        }
        
        com.alibaba.fastjson2.JSONObject payload = rootJson.getJSONObject("payload");
        if (payload == null) {
            log.error("[DeepSeek咨询] payload字段不存在");
            return;
        }
        
        // 使用JSONObject原生方法提取参数
        String query = payload.getString("query");
        boolean enableDeepThinking = payload.getBooleanValue("enableDeepThinking", false);
        boolean enableWebSearch = payload.getBooleanValue("enableWebSearch", false);
        // 🔥 区分两种ID：chatId是前端数据库分组ID，deepseekChatId是DeepSeek的AI会话ID
        String chatId = payload.getString("chatId");  // 前端分组ID（不用于DeepSeek导航）
        String deepseekChatId = payload.getString("deepseekChatId");  // DeepSeek AI会话ID（用于上下文复用）
        log.info("[DeepSeek咨询] ✅ 解析参数 - query: {}, deepThinking: {}, webSearch: {}, 前端chatId: {}, deepseekChatId: {}", 
            query, enableDeepThinking, enableWebSearch, chatId, deepseekChatId);
        
        String mode = "normal";
        if (enableDeepThinking && enableWebSearch) {
            mode = "deepThinking+webSearch";
        } else if (enableDeepThinking) {
            mode = "deepThinking";
        } else if (enableWebSearch) {
            mode = "webSearch";
        }
        
        log.info("[DeepSeek咨询] 开始 - 用户: {}, sessionId: {}, 模式: {}, 前端chatId: {}, deepseekChatId: {}", 
            userId, sessionId, mode, chatId, deepseekChatId != null ? deepseekChatId : "新会话");
        
        StreamTask task = startStreamTask(userId, sessionId, aiType, 6000);
        BrowserSession session = null;
        
        try {
            if (query == null || query.trim().isEmpty()) {
                task.sendError("问题内容不能为空");
                return;
            }
            
            task.sendLog("正在打开DeepSeek...");
            
            session = browserPool.acquirePersistent(userId, "deepseek", false);
            Page page = session.getOrCreatePage();
            
            // 🔥 使用deepseekChatId进行会话恢复（AI上下文复用）
            if (deepseekChatId != null && !deepseekChatId.isEmpty()) {
                task.sendLog("正在恢复DeepSeek会话: " + deepseekChatId);
                boolean navigated = deepSeekUtil.navigateToChat(page, deepseekChatId);
                log.info("[DeepSeek咨询] 导航到DeepSeek会话 {} 结果: {}", deepseekChatId, navigated ? "成功" : "失败");
                if (!navigated) {
                    task.sendError("导航到DeepSeek会话失败: " + deepseekChatId);
                    return;
                }
                
                // 在会话页面检查登录状态
                task.sendLog("正在检查登录状态...");
                String loginStatus = deepSeekUtil.checkLoginStatus(page, false);
                if ("false".equals(loginStatus)) {
                    task.sendError("未登录，请先完成扫码登录");
                    return;
                }
            } else {
                log.info("[DeepSeek咨询] 未提供 deepseekChatId，将创建新DeepSeek会话");
                // 访问首页并检查登录状态
                task.sendLog("正在检查登录状态...");
                String loginStatus = deepSeekUtil.checkLoginStatus(page, true);
                if ("false".equals(loginStatus)) {
                    task.sendError("未登录，请先完成扫码登录");
                    return;
                }
            }
            
            task.sendLog("登录验证通过，准备发送问题...");
                
                // 发送消息并等待回复
                task.sendLog("正在发送问题...");
                
                long startTime = System.currentTimeMillis();
                
                // 🔥 启动定时截图和日志推送（参考老项目的双通道设计）
                task.startAutoProgress(count -> {
                    try {
                        // 文本日志消息
                        long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;
                        String logMessage = "AI正在思考中（已等待" + elapsedSeconds + "秒）...";
                        task.sendLog(logMessage);
                        
                        // 截图消息（独立发送）
                        String screenshotUrl = captureAndUpload(page, userId, 
                            "deepseek_progress_" + count);
                        if (screenshotUrl != null) {
                            task.sendScreenshot(screenshotUrl);
                        }
                        
                        return logMessage; // 返回文本供日志记录
                    } catch (Exception e) {
                        log.warn("[DeepSeek咨询] 进度更新失败", e);
                        return "AI正在处理中...";
                    }
                });
                
                String answer = deepSeekUtil.sendMessageAndWaitResponse(page, query, 
                    enableDeepThinking, enableWebSearch);
                
                task.sendLog("正在提取会话信息和截图...");
                
                // 提取会话ID
                String newChatId = deepSeekUtil.extractChatId(page);
                String shareUrl = newChatId != null ? 
                    "https://chat.deepseek.com/a/chat/s/" + newChatId : null;
                
                // 截取最终对话截图
                String conversationScreenshotUrl = captureAndUpload(page, userId, 
                    "deepseek_conversation_" + newChatId);
                log.debug("[DeepSeek咨询] 对话截图已上传 - URL: {}", conversationScreenshotUrl);
                
                java.util.Map<String, Object> resultData = new java.util.HashMap<>();
                resultData.put("query", query);
                resultData.put("answer", answer);
                resultData.put("chatId", newChatId);
                resultData.put("shareUrl", shareUrl);
                resultData.put("mode", mode);
                resultData.put("elapsedTime", (int) (System.currentTimeMillis() - startTime) / 1000);
                
                if (conversationScreenshotUrl != null) {
                    resultData.put("conversationScreenshot", conversationScreenshotUrl);
                    log.info("[DeepSeek咨询] ✅ 对话截图URL: {}", conversationScreenshotUrl);
                }
                
                task.sendSuccess("DeepSeek回复完成", resultData);
                log.info("[DeepSeek咨询] 完成 - 用户: {}, 会话: {}, 耗时: {}秒", 
                    userId, sessionId, (System.currentTimeMillis() - startTime) / 1000);
            
        } catch (Exception e) {
            log.error("[DeepSeek咨询] 失败 - 用户: {}, 会话: {}", userId, sessionId, e);
            task.sendError("咨询失败: " + e.getMessage());
        } finally {
            task.stop();
            
            // 🔥 关键：完全销毁会话释放资源
            if (session != null) {
                try {
                    session.destroy();
                    log.debug("[DeepSeek咨询] 已销毁会话释放资源 - 用户: {}", userId);
                } catch (Exception e) {
                    log.warn("[DeepSeek咨询] 销毁会话失败 - 用户: {}, 错误: {}", userId, e.getMessage());
                }
            }
        }
    }
    
    /**
     * 截图并上传到 Admin 服务器
     * 
     * @param page Playwright 页面对象
     * @param userId 用户ID
     * @param fileName 文件名（不含扩展名）
     * @return 上传成功返回 URL，失败返回 null
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
                log.info("[DeepSeek截图] 上传成功 - URL: {}", uploadedUrl);
                return uploadedUrl;
            } else {
                log.error("[DeepSeek截图] 上传失败 - 错误: {}", result.getErrorMessage());
                return null;
            }
        } catch (Exception e) {
            log.error("[DeepSeek截图] 截图失败 - 错误: {}", e.getMessage(), e);
            return null;
        }
    }
}
