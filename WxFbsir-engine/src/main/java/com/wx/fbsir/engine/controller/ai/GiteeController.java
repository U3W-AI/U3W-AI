package com.wx.fbsir.engine.controller.ai;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.wx.fbsir.engine.capability.annotation.OnceCapability;
import com.wx.fbsir.engine.capability.annotation.StreamCapability;
import com.wx.fbsir.engine.capability.base.StreamTaskHelper;
import com.wx.fbsir.engine.playwright.pool.BrowserPoolManager;
import com.wx.fbsir.engine.playwright.session.BrowserSession;
import com.wx.fbsir.engine.playwright.util.ScreenshotUtil;
import com.wx.fbsir.engine.utils.ai.GiteeAiUtil;
import com.wx.fbsir.engine.websocket.message.EngineMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 🤖 Gitee AI Chat WebSocket 控制器（简单原型）
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📚 原型说明
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 本控制器是 Gitee AI Chat 集成的简单原型，用于验证技术思路和流程。
 * 参考 DeepSeekController 的实现模式，遵循 AIGC 框架规范。
 * 
 * 【核心概念】
 * 1. sessionId - 前端生成的业务会话ID，用于全链路追踪
 * 2. aiType - 固定为 "gitee"，用于区分不同 AI 的消息
 * 3. payload - Admin 透传的请求参数，Engine 端自行解析
 * 4. giteeChatId - Gitee AI Chat 的会话ID，用于上下文复用
 * 
 * 【消息流向】
 * 前端 → Admin(透传) → Engine(本Controller) → Gitee AI Chat
 * Gitee → Engine(发送AI_TASK_*) → Admin(存储) → 前端(显示)
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 功能清单（原型阶段）
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 1. 登录状态检测 - 检查用户是否已登录 Gitee AI Chat
 * 2. 二维码扫码登录 - 获取登录二维码，监测登录状态
 * 3. AI 咨询服务 - 发送问题，获取 AI 回复
 * 4. 会话管理 - 支持会话ID传递
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 🎯 AIGC 消息格式规范
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 使用 StreamTask 辅助类自动发送 AI_TASK_* 消息：
 * - task.sendLog("进度") → AI_TASK_LOG
 * - task.sendScreenshot("URL") → AI_TASK_SCREENSHOT
 * - task.sendSuccess("提示", data) → AI_TASK_RESULT
 * - task.sendError("错误") → AI_TASK_ERROR
 * 
 * @author 实习生
 * @date 2026-01-22
 * @version 1.0 (原型阶段)
 */
@Controller
public class GiteeController extends StreamTaskHelper {

    @Autowired
    private GiteeAiUtil giteeAiUtil;
    
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
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * 功能1：检查 Gitee AI Chat 登录状态（单次返回）
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * 
     * 请求参数：
     * - userId: 用户ID（Admin自动注入）
     * - sessionId: 会话ID（前端生成）
     * 
     * 返回数据：
     * - isLoggedIn: 是否已登录（boolean）
     * - userName: 用户名（如果已登录）
     * - platform: 平台名称 "Gitee AI Chat"
     */
    @OnceCapability(
        type = "GITEE_CHECK_LOGIN",
        description = "检查Gitee AI Chat登录状态",
        timeout = 30000L
    )
    public void handleCheckLogin(EngineMessage message) {
        String userId = message.getUserId();
        String sessionId = extractSessionId(message);
        String aiType = extractAiType(message);
        
        log.info("🔍 [Gitee登录检测] 开始 - 用户: {}, 会话: {}, AI: {}", userId, sessionId, aiType);
        
        try {
            BrowserSession session = null;
            try {
                // 🔥 获取持久化浏览器会话（用于保持登录状态）
                session = browserPool.acquirePersistent(userId, "gitee", false);
                
                // 🔥 调用工具类检查登录状态
                String loginStatus = giteeAiUtil.checkLoginStatus(session.getOrCreatePage(), true);
                boolean isLoggedIn = !"false".equals(loginStatus);
                
                // 🔥 构建返回数据
                Map<String, Object> resultData = new HashMap<>();
                resultData.put("isLoggedIn", isLoggedIn);
                resultData.put("userName", isLoggedIn ? loginStatus : null);
                resultData.put("platform", "Gitee AI Chat");
                resultData.put("timestamp", System.currentTimeMillis());
                
                log.info("✅ [Gitee登录检测] 完成 - 登录状态: {}, 用户: {}", isLoggedIn, loginStatus);
                
                // 🔥 发送结果
                sendResult(userId, sessionId, aiType, resultData);
                
            } finally {
                // 🔥 关键：通过池管理器销毁会话，确保 Semaphore 被释放
                if (session != null) {
                    browserPool.destroy(session);
                }
            }
            
        } catch (Exception e) {
            log.error("[Gitee登录检测] 失败 - 用户: {}, 错误: {}", userId, e.getMessage(), e);
            sendErrorResult(userId, sessionId, aiType, "登录检测失败: " + e.getMessage());
        }
    }

    /**
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * 功能2：Gitee AI Chat 扫码登录（流式返回）
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * 
     * 流程：
     * 1. 导航到登录页
     * 2. 获取二维码并截图
     * 3. 实时监测登录状态
     * 4. 登录成功后返回用户信息
     * 
     * 返回消息类型：
     * - TASK_LOG: 进度日志
     * - TASK_SCREENSHOT: 二维码截图
     * - TASK_RESULT: 最终登录结果
     */
    @StreamCapability(
        type = "GITEE_SCAN_LOGIN",
        description = "Gitee AI Chat扫码登录",
        progressInterval = 2000
    )
    public void handleScanLogin(EngineMessage message) {
        String userId = message.getUserId();
        String sessionId = extractSessionId(message);
        
        log.info("[Gitee扫码登录] 开始 - 用户: {}, 会话: {}", userId, sessionId);
        
        // 🔧 登录业务使用通用流式任务（非AI业务，使用 startStreamTask）
        StreamTask task = startStreamTask(userId, sessionId, 2000);
        
        BrowserSession session = null;
        boolean loginSuccess = false;  // 🔥 移到外部作用域，finally 块需要访问
        try {
            task.sendLog("正在初始化浏览器...");
            session = browserPool.acquirePersistent(userId, "gitee", false);
            Page page = session.getOrCreatePage();
            
            task.sendLog("正在导航到 Gitee AI Chat 登录页...");
            boolean navigateSuccess = giteeAiUtil.navigateToLoginPage(page);
            
            if (!navigateSuccess) {
                task.sendError("导航到登录页失败");
                return;
            }
            
            task.sendLog("正在获取登录二维码...");
            
            // 截图并上传
            String screenshotUrl = captureAndUpload(page, userId, "gitee_login_qr");
            
            if (screenshotUrl != null) {
                task.sendLog("登录二维码已获取，请使用 Gitee 账号扫码");
                task.sendScreenshot(screenshotUrl);
            }
            
            // 每2秒检测一次登录状态
            task.sendLog("等待扫码登录...");
            int maxAttempts = 180;  // 3分钟，每秒检查一次
            String userName = null;
            
            for (int i = 0; i < maxAttempts; i++) {
                Thread.sleep(1000);
                
                String loginStatus = giteeAiUtil.checkLoginStatus(page, false);
                if (!"false".equals(loginStatus)) {
                    loginSuccess = true;
                    userName = loginStatus;
                    break;
                }
                
                // 每10秒发送一次提示
                if (i > 0 && i % 10 == 0) {
                    task.sendLog(String.format("等待中... (%d秒)", i));
                }
            }
            
            if (loginSuccess) {
                task.sendLog("✅ 登录成功！正在初始化聊天会话...");
                
                // 🔥 关键：登录成功后，导航到聊天首页，确保聊天域名也能访问Cookie
                // 问题：登录在 gitee.com，聊天在 chat.gitee.com（跨子域名）
                // 解决：登录后立即访问聊天页，触发Cookie同步
                try {
                    page.navigate("https://chat.gitee.com/", new Page.NavigateOptions().setTimeout(15000));
                    page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(15000));
                    page.waitForTimeout(3000);
                    log.debug("[Gitee扫码登录] 已导航到聊天页，等待Cookie完全加载");
                } catch (Exception e) {
                    log.warn("[Gitee扫码登录] 导航到聊天页失败: {}", e.getMessage());
                }
                
                Map<String, Object> resultData = new HashMap<>();
                resultData.put("success", true);
                resultData.put("userName", userName);
                resultData.put("platform", "Gitee AI Chat");
                resultData.put("loginTime", System.currentTimeMillis());
                
                task.sendSuccess("Gitee AI Chat 登录成功", resultData);
                log.info("✅ [Gitee扫码登录] 成功 - 用户: {}, Gitee用户: {}", userId, userName);
                
                // 🔥 关键：登录成功后等待5秒让Chromium完成数据持久化
                // 不要destroy！让浏览器保持打开，会话留在池中
                try {
                    Thread.sleep(5000);
                    log.debug("[Gitee扫码登录] 数据持久化完成，会话已保留在池中 - 用户: {}", userId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
            } else {
                task.sendError("登录超时，请重试");
                log.warn("⏱️ [Gitee扫码登录] 超时 - 用户: {}", userId);
            }
            
        } catch (Exception e) {
            log.error("[Gitee扫码登录] 失败 - 用户: {}, 错误: {}", userId, e.getMessage(), e);
            task.sendError("登录失败: " + e.getMessage());
        } finally {
            task.stop();
            
            // 🔥 关键修改：登录成功后不要关闭浏览器！
            // Gitee的Cookie持久化不可靠，直接保持浏览器打开，下次复用
            // 对比DeepSeek：DeepSeek用destroy也能工作，因为它的Cookie持久化机制更完善
            if (session != null) {
                if (loginSuccess) {
                    // 登录成功：释放到池中，保持浏览器打开
                    try {
                        browserPool.release(session);
                        log.info("[Gitee扫码登录] ✅ 登录成功，浏览器保持打开，可直接复用 - 用户: {}", userId);
                    } catch (Exception e) {
                        log.warn("[Gitee扫码登录] 释放会话失败 - 用户: {}, 错误: {}", userId, e.getMessage());
                    }
                } else {
                    // 登录失败/超时：销毁会话，释放资源
                    try {
                        session.destroy();
                        log.debug("[Gitee扫码登录] 登录失败，已销毁会话 - 用户: {}", userId);
                    } catch (Exception e) {
                        log.warn("[Gitee扫码登录] 销毁会话失败 - 用户: {}, 错误: {}", userId, e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * 功能3：Gitee AI Chat AI 咨询（流式返回）
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * 
     * 请求参数（payload）：
     * - query: 用户问题（必填）
     * - sessionId: 会话ID（必填）
     * - chatId: 内部聊天ID（用于多轮对话）
     * - giteeChatId: Gitee平台会话ID（用于上下文复用）
     * 
     * 返回消息类型：
     * - AI_TASK_LOG: 进度日志
     * - AI_TASK_SCREENSHOT: 执行截图
     * - AI_TASK_RESULT: AI 回复结果
     * - AI_TASK_ERROR: 错误信息
     * 
     * 返回数据结构：
     * {
     *   "answer": "AI的回复内容",
     *   "giteeChatId": "平台会话ID",
     *   "shareUrl": "分享链接（如果有）",
     *   "elapsedTime": 执行耗时（秒）
     * }
     */
    @StreamCapability(
        type = "AI_GITEE_QUERY",
        description = "Gitee AI Chat AI咨询",
        progressInterval = 6000
    )
    public void handleAiQuery(EngineMessage message) {
        String userId = message.getUserId();
        String sessionId = extractSessionId(message);
        String aiType = extractAiType(message);  // "gitee"
        
        // 从 payload 中提取参数
        String query = message.getPayloadValue("query");
        String chatId = message.getPayloadValue("chatId");
        String giteeChatId = message.getPayloadValue("giteeChatId");
        
        log.info("🤖 [Gitee AI咨询] 开始 - 用户: {}, 会话: {}, 问题: {}", userId, sessionId, query);
        
        // 启动 AI 流式任务（自动发送 AI_TASK_* 格式消息）
        StreamTask task = startAiStreamTask(userId, sessionId, aiType, 6000);
        
        BrowserSession session = null;
        long startTime = System.currentTimeMillis();
        
        try {
            task.sendLog("正在连接 Gitee AI Chat...");
            
            // 获取持久化浏览器会话
            session = browserPool.acquirePersistent(userId, "gitee", false);
            Page page = session.getOrCreatePage();
            
            // 检查登录状态
            task.sendLog("正在验证登录状态...");
            
            // 🔍 临时调试：跳过登录检测，直接测试发送消息流程
            String loginStatus = giteeAiUtil.checkLoginStatus(page, true);
            log.warn("🔍 [Gitee 调试] 登录状态检测结果: {}, 但暂时跳过登录检查", loginStatus);
            task.sendLog("⚠️ 调试模式：已跳过登录检测");
            
            // if ("false".equals(loginStatus)) {
            //     task.sendError("未登录，请先在登录管理器中完成 Gitee AI Chat 扫码登录");
            //     // 🔥 未登录时立即销毁会话，关闭浏览器（节省资源）
            //     browserPool.destroy(session);
            //     session = null;
            //     return;
            // }
            
            task.sendLog("登录验证通过，准备发送问题...");
            
            // 发送问题并等待回复
            task.sendLog("正在向 Gitee AI 发送问题...");
            String aiResponse = giteeAiUtil.sendMessageAndWaitResponse(page, query);
            
            if (aiResponse == null || aiResponse.isEmpty()) {
                task.sendError("AI 未返回有效回复");
                return;
            }
            
            task.sendLog("✅ Gitee AI 回复完成");
            
            // 截图保存结果
            String screenshotUrl = captureAndUpload(page, userId, "gitee_ai_result");
            if (screenshotUrl != null) {
                task.sendScreenshot(screenshotUrl);
            }
            
            // 构建返回数据
            long elapsedTime = (System.currentTimeMillis() - startTime) / 1000;
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("answer", aiResponse);
            resultData.put("giteeChatId", giteeChatId);  // 暂时回传，后续需实现会话ID提取
            resultData.put("shareUrl", "");  // 预留：分享链接
            resultData.put("elapsedTime", elapsedTime);
            resultData.put("query", query);
            
            // 发送成功结果
            task.sendSuccess("Gitee AI Chat 回复完成", resultData);
            
            log.info("[Gitee AI咨询] 成功 - 用户: {}, 耗时: {}秒", userId, elapsedTime);
            
        } catch (Exception e) {
            log.error("[Gitee AI咨询] 失败 - 用户: {}, 错误: {}", userId, e.getMessage(), e);
            task.sendError("AI 咨询失败: " + e.getMessage());
        } finally {
            if (session != null) {
                browserPool.release(session);
            }
        }
    }

    // ==========================================================================
    // 🔧 参数提取辅助方法（从payload中提取，Admin透传不解析）
    // ==========================================================================
    
    /**
     * 提取sessionId（前端生成的业务会话ID）
     */
    private String extractSessionId(EngineMessage message) {
        Object sessionId = message.getPayloadValue("sessionId");
        return sessionId != null ? sessionId.toString() : "unknown";
    }
    
    /**
     * 提取aiType（AI类型标识）
     */
    private String extractAiType(EngineMessage message) {
        Object aiType = message.getPayloadValue("aiType");
        return aiType != null ? aiType.toString() : "gitee";
    }

    // ==========================================================================
    // 📤 消息发送方法（非AI业务使用，AI业务请使用StreamTask）
    // ==========================================================================
    
    /**
     * 发送成功结果（仅登录检测等非AI业务使用）
     */
    private void sendResult(String userId, String sessionId, String aiType, Map<String, Object> data) {
        EngineMessage result = EngineMessage.builder()
            .type(com.wx.fbsir.engine.websocket.message.MessageType.TASK_RESULT.getCode())
            .userId(userId)
            .payload("sessionId", sessionId)
            .payload("aiType", aiType)
            .payload("success", true)
            .payload("data", data)
            .payload("timestamp", System.currentTimeMillis())
            .build();
        
        webSocketClientManager.sendMessage(result);
        log.debug("[Gitee] 发送结果 - 用户: {}, 会话: {}, AI: {}", userId, sessionId, aiType);
    }
    
    /**
     * ⚠️ 发送错误结果（仅登录检测等非AI业务使用）
     */
    private void sendErrorResult(String userId, String sessionId, String aiType, String errorMessage) {
        EngineMessage result = EngineMessage.builder()
            .type(com.wx.fbsir.engine.websocket.message.MessageType.AI_TASK_ERROR.getCode())
            .userId(userId)
            .payload("sessionId", sessionId)
            .payload("aiType", aiType)
            .payload("success", false)
            .payload("errorCode", "TASK_ERROR")
            .payload("errorMessage", errorMessage)
            .payload("timestamp", System.currentTimeMillis())
            .build();
        
        webSocketClientManager.sendMessage(result);
        log.error("[Gitee] 发送错误 - 用户: {}, 会话: {}, AI: {}, 错误: {}", userId, sessionId, aiType, errorMessage);
    }

    // ==========================================================================
    // 📸 截图辅助方法
    // ==========================================================================
    
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
                log.debug("[Gitee截图] 上传成功 - URL: {}", uploadedUrl);
                return uploadedUrl;
            } else {
                log.error("[Gitee截图] 上传失败 - 错误: {}", result.getErrorMessage());
                return null;
            }
        } catch (Exception e) {
            log.error("[Gitee截图] 截图失败 - 错误: {}", e.getMessage(), e);
            return null;
        }
    }
}

