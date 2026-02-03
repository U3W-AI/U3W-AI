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
 * 📌 功能清单
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
 * @author wxfbsir
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
            
            // 截图并上传 - 使用微信二维码区域截图
            String screenshotUrl = captureWechatQrCode(page, userId, "gitee_login_qr");
            
            if (screenshotUrl != null) {
                task.sendLog("登录二维码已获取，请使用 Gitee 账号扫码");
                task.sendScreenshot(screenshotUrl);
            } else {
                task.sendLog("二维码截图失败，使用全屏截图作为备用");
                // 备用方案：使用全屏截图
                String fullScreenshotUrl = captureAndUpload(page, userId, "gitee_login_qr_full");
                if (fullScreenshotUrl != null) {
                    task.sendScreenshot(fullScreenshotUrl);
                }
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
     *   "chatId": "平台会话ID",
     *   "shareUrl": "分享链接（如果有）",
     *   "mode": "使用的模式",
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
        
        log.info("🤖 [Gitee AI咨询] 收到消息 - 会话: {}, AI: {}", sessionId, aiType);
        
        // 直接从原始JSON解析payload，完全绕过fastjson2的Map序列化问题
        String rawJson = message.getRawJson();
        if (rawJson == null || rawJson.isEmpty()) {
            log.error("[Gitee咨询] 原始JSON为空");
            return;
        }
        
        // 直接解析原始JSON获取payload
        com.alibaba.fastjson2.JSONObject rootJson = com.alibaba.fastjson2.JSON.parseObject(rawJson);
        if (rootJson == null) {
            log.error("[Gitee咨询] JSON解析失败");
            return;
        }
        
        com.alibaba.fastjson2.JSONObject payload = rootJson.getJSONObject("payload");
        if (payload == null) {
            log.error("[Gitee咨询] payload字段不存在");
            return;
        }
        
        // 使用JSONObject原生方法提取参数
        String query = payload.getString("query");
        // 🔥 区分两种ID：chatId是前端数据库分组ID，giteeChatId是Gitee的AI会话ID
        String chatId = payload.getString("chatId");  // 前端分组ID（不用于Gitee导航）
        // 🔥 兼容两种参数名：gitee使用giteeChatId，前端可能传递chatId
        String giteeChatId = payload.getString("giteeChatId");  // Gitee AI会话ID（用于上下文复用）
        // 注意：不再自动使用chatId作为giteeChatId，确保新建会话时不会尝试恢复会话
        // if (giteeChatId == null || giteeChatId.isEmpty()) {
        //     giteeChatId = payload.getString("chatId");  // 兼容前端传递的chatId参数
        // }
        
        // 提取模式参数
        boolean enableOpenSourceExploration = payload.getBooleanValue("openSourceExploration", false);
        boolean enableHelpCenter = payload.getBooleanValue("helpCenter", false);
        
        log.info("[Gitee咨询] ✅ 解析参数 - query: {}, openSourceExploration: {}, helpCenter: {}, 前端chatId: {}, giteeChatId: {}", 
            query, enableOpenSourceExploration, enableHelpCenter, chatId, giteeChatId);
        
        // 确定使用的模式
        String mode = "normal";
        if (enableOpenSourceExploration) {
            mode = "openSourceExploration";
        } else if (enableHelpCenter) {
            mode = "helpCenter";
        }
        
        log.info("[Gitee咨询] 开始 - 用户: {}, sessionId: {}, 模式: {}, 前端chatId: {}, giteeChatId: {}", 
            userId, sessionId, mode, chatId, giteeChatId != null ? giteeChatId : "新会话");
        
        // 启动 AI 流式任务（自动发送 AI_TASK_* 格式消息）
        StreamTask task = startAiStreamTask(userId, sessionId, aiType, 6000);
        
        BrowserSession session = null;
        long startTime = System.currentTimeMillis();
        
        try {
            task.sendLog("正在连接 Gitee AI Chat...");
            
            // 获取持久化浏览器会话
            session = browserPool.acquirePersistent(userId, "gitee", false);
            Page page = session.getOrCreatePage();
            
            // 🔥 使用giteeChatId进行会话恢复（AI上下文复用）
            if (giteeChatId != null && !giteeChatId.isEmpty()) {
                task.sendLog("正在恢复Gitee会话: " + giteeChatId);
                boolean navigated = giteeAiUtil.navigateToChat(page, giteeChatId);
                log.info("[Gitee咨询] 导航到Gitee会话 {} 结果: {}", giteeChatId, navigated ? "成功" : "失败");
                if (!navigated) {
                    task.sendError("导航到Gitee会话失败: " + giteeChatId);
                    return;
                }
                
                // 在会话页面检查登录状态
                task.sendLog("正在检查登录状态...");
                String loginStatus = giteeAiUtil.checkLoginStatus(page, false);
                if ("false".equals(loginStatus)) {
                    task.sendError("未登录，请先完成扫码登录");
                    return;
                }
            } else {
                log.info("[Gitee咨询] 未提供 giteeChatId，将创建新Gitee会话");
                // 访问首页并检查登录状态
                task.sendLog("正在检查登录状态...");
                String loginStatus = giteeAiUtil.checkLoginStatus(page, true);
                if ("false".equals(loginStatus)) {
                    task.sendError("未登录，请先完成扫码登录");
                    return;
                }
            }
            
            task.sendLog("登录验证通过，准备发送问题...");
            
            // 🔥 启动定时截图和日志推送（参考DeepSeek实现）
            task.startAutoProgress(count -> {
                try {
                    // 文本日志消息
                    long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;
                    String logMessage = "Gitee AI正在生成回复（已等待" + elapsedSeconds + "秒）...";
                    task.sendLog(logMessage);
                    
                    // 截图消息（独立发送）
                    String screenshotUrl = captureAndUpload(page, userId, 
                        "gitee_progress_" + count);
                    if (screenshotUrl != null) {
                        task.sendScreenshot(screenshotUrl);
                    }
                    
                    return logMessage; // 返回文本供日志记录
                } catch (Exception e) {
                    log.warn("[Gitee咨询] 进度更新失败", e);
                    return "Gitee AI正在处理中...";
                }
            });
            
            // 发送问题并等待回复
            task.sendLog("正在向 Gitee AI 发送问题...");
            task.sendLog("当前模式: " + mode);
            String aiResponse = giteeAiUtil.sendMessageAndWaitResponse(page, query, enableOpenSourceExploration, enableHelpCenter);
            
            if (aiResponse == null || aiResponse.isEmpty()) {
                task.sendError("AI 未返回有效回复");
                return;
            }
            
            // 🔥 关键：回复完成后立即停止定时任务，避免继续发送正在生成的日志
            task.stop();
            task.sendLog("✅ Gitee AI 回复完成，等待页面渲染...");
            
            
            // 等待页面渲染完成
            page.waitForTimeout(2000);
            
            task.sendLog("正在提取会话信息...");
            
            // 提取会话ID
            String newChatId = giteeAiUtil.extractChatId(page);
            String shareUrl = newChatId != null ? 
                "https://chat.gitee.com/c/" + newChatId : null;
            
            // 截图保存结果
            String screenshotUrl = captureAndUpload(page, userId, "gitee_ai_result");
            if (screenshotUrl != null) {
                task.sendScreenshot(screenshotUrl);
            }
            
            // 构建返回数据
            long elapsedTime = (System.currentTimeMillis() - startTime) / 1000;
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("query", query);
            resultData.put("chatId", newChatId);  // 返回新的会话ID供下次复用（与DeepSeek保持一致）
            resultData.put("shareUrl", shareUrl);  // 分享链接
            resultData.put("mode", mode);  // 添加使用的模式
            resultData.put("elapsedTime", elapsedTime);
            
            // 🔥 数据存储策略（优化版）：
            // - answer字段：存储AI回复内容
            resultData.put("answer", aiResponse != null ? aiResponse : "Gitee AI回复完成，但获取内容失败");
            
            // 发送成功结果
            task.sendSuccess("Gitee AI Chat 回复完成", resultData);
            
            log.info("[Gitee AI咨询] 成功 - 用户: {}, 会话: {}, 耗时: {}秒", userId, sessionId, elapsedTime);
            
        } catch (Exception e) {
            log.error("[Gitee AI咨询] 失败 - 用户: {}, 会话: {}, 错误: {}", userId, sessionId, e.getMessage(), e);
            task.sendError("AI 咨询失败: " + e.getMessage());
        } finally {
            task.stop();
            
            if (session != null) {
                try {
                    browserPool.release(session);
                    log.debug("[Gitee咨询] 已释放会话到池中 - 用户: {}", userId);
                } catch (Exception e) {
                    log.warn("[Gitee咨询] 释放会话失败 - 用户: {}, 错误: {}", userId, e.getMessage());
                }
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
    
    /**
     * 截取微信二维码区域并上传到 Admin 服务器
     */
    private String captureWechatQrCode(Page page, String userId, String fileName) {
        try {
            log.info("[Gitee微信二维码截图] 开始定位微信二维码区域");
            
            // 定位微信二维码区域
            com.microsoft.playwright.Locator qrCodeLocator = giteeAiUtil.locateWechatQrCode(page);
            
            if (qrCodeLocator != null && qrCodeLocator.isVisible()) {
                log.info("[Gitee微信二维码截图] 找到微信二维码区域，开始截图");
                
                // 🔥 放大微信二维码图片
                try {
                    // 使用 JavaScript 调整二维码元素的大小
                    page.evaluate("""
                        () => {
                            // 找到二维码图片元素
                            const qrCodeImg = document.querySelector('.js_qrcode_img.web_qrcode_img');
                            if (qrCodeImg) {
                                // 保存原始样式
                                const originalStyle = qrCodeImg.getAttribute('style') || '';
                                qrCodeImg.setAttribute('data-original-style', originalStyle);
                                
                                // 放大二维码图片
                                qrCodeImg.style.width = '300px';
                                qrCodeImg.style.height = '300px';
                                qrCodeImg.style.objectFit = 'contain';
                                qrCodeImg.style.border = '2px solid #fff';
                                qrCodeImg.style.backgroundColor = '#fff';
                                qrCodeImg.style.padding = '10px';
                                
                                // 也调整父容器的大小
                                const parentContainer = qrCodeImg.parentElement;
                                if (parentContainer) {
                                    parentContainer.style.width = '324px';
                                    parentContainer.style.height = '324px';
                                    parentContainer.style.display = 'flex';
                                    parentContainer.style.alignItems = 'center';
                                    parentContainer.style.justifyContent = 'center';
                                    parentContainer.style.backgroundColor = '#fff';
                                }
                            }
                        }
                    """);
                    
                    // 等待样式生效
                    page.waitForTimeout(1000);
                    log.info("[Gitee微信二维码截图] 已放大微信二维码图片");
                } catch (Exception e) {
                    log.debug("[Gitee微信二维码截图] 放大二维码失败，使用原始大小截图: {}", e.getMessage());
                }
                
                // 截取微信二维码区域
                byte[] screenshotBytes = qrCodeLocator.screenshot(
                    new com.microsoft.playwright.Locator.ScreenshotOptions()
                );
                
                // 恢复原始样式
                try {
                    page.evaluate("""
                        () => {
                            const qrCodeImg = document.querySelector('.js_qrcode_img.web_qrcode_img');
                            if (qrCodeImg) {
                                const originalStyle = qrCodeImg.getAttribute('data-original-style') || '';
                                if (originalStyle) {
                                    qrCodeImg.setAttribute('style', originalStyle);
                                    qrCodeImg.removeAttribute('data-original-style');
                                } else {
                                    qrCodeImg.removeAttribute('style');
                                }
                            }
                        }
                    """);
                } catch (Exception e) {
                    log.debug("[Gitee微信二维码截图] 恢复原始样式失败: {}", e.getMessage());
                }
                
                // 上传到 Admin 服务器
                com.wx.fbsir.engine.playwright.util.ScreenshotUploadClient.UploadResult result = 
                    uploadClient.uploadScreenshot(userId, fileName, screenshotBytes);
                
                if (result.isSuccess()) {
                    String uploadedUrl = result.getUrl();
                    log.info("[Gitee微信二维码截图] 上传成功 - URL: {}", uploadedUrl);
                    return uploadedUrl;
                } else {
                    log.error("[Gitee微信二维码截图] 上传失败 - 错误: {}", result.getErrorMessage());
                    return null;
                }
            } else {
                log.warn("[Gitee微信二维码截图] 未找到微信二维码区域，使用全屏截图作为备用");
                // 备用方案：使用全屏截图
                return captureAndUpload(page, userId, fileName + "_full");
            }
        } catch (Exception e) {
            log.error("[Gitee微信二维码截图] 截图失败 - 错误: {}", e.getMessage(), e);
            // 异常情况下使用全屏截图作为备用
            return captureAndUpload(page, userId, fileName + "_error");
        }
    }
}

