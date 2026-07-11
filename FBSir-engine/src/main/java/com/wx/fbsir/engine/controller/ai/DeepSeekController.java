package com.wx.fbsir.engine.controller.ai;

import com.microsoft.playwright.Page;
import com.wx.fbsir.engine.capability.annotation.OnceCapability;
import com.wx.fbsir.engine.capability.annotation.StreamCapability;
import com.wx.fbsir.engine.capability.base.StreamTaskHelper;
import com.wx.fbsir.engine.playwright.pool.BrowserPoolManager;
import com.wx.fbsir.engine.playwright.session.BrowserSession;
import com.wx.fbsir.engine.playwright.util.ScreenshotUtil;
import com.wx.fbsir.engine.utils.ai.DeepSeekUtil;
import com.wx.fbsir.engine.utils.common.FileDownloadUtil;
import com.wx.fbsir.engine.websocket.message.EngineMessage;
import com.wx.fbsir.engine.websocket.message.MessageType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.Map;

/**
 * 🤖 DeepSeek AI WebSocket 控制器（新手指南）
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📚 基础概念说明
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 【核心概念】
 * 1. sessionId - 前端生成的业务会话ID，用于全链路追踪和数据库存储
 * 2. aiType - AI类型标识（如"deepseek"），用于区分不同AI的消息和数据
 * 3. payload - 请求载荷，Admin透传不解析，Engine端自行解析AI平台特定参数
 * 4. chatId - DeepSeek平台的会话ID，用于AI上下文复用（连续对话）
 * 
 * 【消息流向】
 * 前端 → Admin(透传payload) → Engine(本Controller解析) → DeepSeek平台
 * DeepSeek平台 → Engine(发送AI_TASK_*消息) → Admin(存储) → 前端(显示)
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 功能清单
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 1. 登录状态检测 - 检查用户是否已登录DeepSeek
 * 2. 二维码扫码登录 - 获取登录二维码，实时监测登录状态
 * 3. AI咨询服务 - 支持普通模式、深度思考、联网搜索
 * 4. 会话管理 - 支持会话ID传递，实现上下文连续对话
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 🎯 AIGC消息格式规范（AI_TASK_*）
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 【发送消息】使用 StreamTask 辅助类
 * - task.sendLog("进度文本") → 发送 AI_TASK_LOG 消息
 * - task.sendScreenshot("截图URL") → 发送 AI_TASK_SCREENSHOT 消息
 * - task.sendSuccess("成功提示", resultData) → 发送 AI_TASK_RESULT 消息
 * - task.sendError("错误信息") → 发送 AI_TASK_ERROR 消息
 * 
 * 【Admin自动处理】
 * - AI_TASK_LOG → 追加到 wc_chat_history.data.progressLogs
 * - AI_TASK_SCREENSHOT → 追加到 wc_chat_history.data.screenshots
 * - AI_TASK_RESULT → 合并保存到 wc_chat_history.data（保留progressLogs）
 * - AI_TASK_ERROR → 记录错误信息
 * 
 * 【前端自动显示】
 * - progressLogs → 任务流程区域
 * - screenshots → 可视化轮播区
 * - answer/shareUrl → AI响应结果区
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 💡 新手示例
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * // 1. 从请求中提取参数（payload由Admin透传）
 * String sessionId = extractSessionId(message);  // 前端生成的会话ID
 * String aiType = extractAiType(message);        // 固定为"deepseek"
 * String query = message.getPayloadValue("query"); // 用户问题
 * Boolean enableDeepThinking = message.getPayloadValue("enableDeepThinking");
 * 
 * // 2. 启动AI流式任务（自动发送AI_TASK_*格式消息）
 * StreamTask task = startAiStreamTask(userId, sessionId, aiType, 6000);
 * 
 * // 3. 发送进度日志（自动附带sessionId和aiType）
 * task.sendLog("正在连接DeepSeek...");  // → AI_TASK_LOG
 * 
 * // 4. 发送截图（自动附带sessionId和aiType）
 * task.sendScreenshot(screenshotUrl);   // → AI_TASK_SCREENSHOT
 * 
 * // 5. 发送最终结果（自动附带sessionId和aiType）
 * Map<String, Object> result = new HashMap<>();
 * result.put("answer", aiResponse);
 * result.put("chatId", deepseekChatId);
 * task.sendSuccess("DeepSeek回复完成", result); // → AI_TASK_RESULT
 * 
 * @author FBSir
 * @date 2025-12-25
 * @version 2.0 (AIGC消息格式规范)
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
    
    @Autowired
    private FileDownloadUtil fileDownloadUtil;

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
                
                // 🔥 使用通用框架恢复登录状态
                if (com.wx.fbsir.engine.playwright.login.manager.LoginStateManager.hasLoginState("deepseek", userId)) {
                    try {
                        boolean restored = com.wx.fbsir.engine.playwright.login.manager.LoginStateManager
                            .restoreLoginState(session, "deepseek", userId);
                        if (restored) {
                            log.info("[DeepSeek登录检测] ✅ 登录状态已恢复 - 用户: {}", userId);
                        } else {
                            log.warn("[DeepSeek登录检测] ⚠️ 登录状态恢复失败 - 用户: {}", userId);
                        }
                    } catch (Exception e) {
                        log.warn("[DeepSeek登录检测] ⚠️ 登录状态恢复异常: {}", e.getMessage());
                    }
                }
                
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
                // 🔥 关键：通过池管理器销毁会话，确保 Semaphore 被释放
                if (session != null) {
                    try {
                        browserPool.destroy(session);
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
    
    // ==========================================================================
    // 🔧 参数提取辅助方法（从payload中提取，Admin透传不解析）
    // ==========================================================================
    
    /**
     * 提取sessionId（前端生成的业务会话ID）
     * 
     * 【说明】
     * - sessionId 由前端生成，用于全链路追踪
     * - 与系统的 requestId 不同（requestId由Admin生成）
     * - 用于数据库存储和消息关联
     * 
     * @param message Engine消息对象
     * @return sessionId 或 "unknown"（兜底值）
     */
    private String extractSessionId(EngineMessage message) {
        Object sessionId = message.getPayload().get("sessionId");
        return sessionId != null ? sessionId.toString() : "unknown";
    }
    
    /**
     * 提取aiType（AI类型标识）
     * 
     * 【说明】
     * - aiType 用于区分不同AI的消息和数据
     * - DeepSeek固定为"deepseek"
     * - 用于数据库存储和前端显示区分
     * 
     * @param message Engine消息对象
     * @return aiType 或 "deepseek"（默认值）
     */
    private String extractAiType(EngineMessage message) {
        Object aiType = message.getPayloadValue("aiType");
        return aiType != null ? aiType.toString() : "deepseek";
    }
    
    // ==========================================================================
    // 📤 消息发送方法（非AI业务使用，AI业务请使用StreamTask）
    // ==========================================================================
    
    /**
     * ⚠️ 发送成功结果（仅登录检测等非AI业务使用）
     * 
     * 【注意】
     * - AI咨询业务请使用 task.sendSuccess() 自动发送 AI_TASK_RESULT
     * - 本方法仅用于登录检测等非AI业务场景
     * - 自动附带 sessionId 和 aiType 供Admin存储
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID（前端生成）
     * @param aiType AI类型（如"deepseek"）
     * @param data 返回数据
     */
    private void sendResult(String userId, String sessionId, String aiType, Map<String, Object> data) {
        EngineMessage result = EngineMessage.builder()
            .type(MessageType.TASK_RESULT.getCode())  // ⚠️ 非AI业务使用TASK_RESULT
            .userId(userId)
            .payload("sessionId", sessionId)    // 会话ID（前端生成）
            .payload("aiType", aiType)          // AI类型标识
            .payload("success", true)
            .payload("data", data)
            .payload("timestamp", System.currentTimeMillis())
            .build();
        
        webSocketClientManager.sendMessage(result);
        log.debug("[DeepSeek] 发送结果 - 用户: {}, 会话: {}, AI: {}", userId, sessionId, aiType);
    }
    
    /**
     * ⚠️ 发送错误结果（仅登录检测等非AI业务使用）
     * 
     * 【注意】
     * - AI咨询业务请使用 task.sendError() 自动发送 AI_TASK_ERROR
     * - 本方法仅用于登录检测等非AI业务场景
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID（前端生成）
     * @param aiType AI类型（如"deepseek"）
     * @param errorMessage 错误信息
     */
    private void sendErrorResult(String userId, String sessionId, String aiType, String errorMessage) {
        EngineMessage result = EngineMessage.builder()
            .type(MessageType.AI_TASK_ERROR.getCode())  // 🤖 使用AI_TASK_ERROR
            .userId(userId)
            .payload("sessionId", sessionId)    // 会话ID（前端生成）
            .payload("aiType", aiType)          // AI类型标识
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
        
        // 🔧 登录业务使用通用流式任务（发送 TASK_* 消息）
        StreamTask task = startStreamTask(userId, sessionId, 2000);
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
                    log.debug("[DeepSeek扫码登录] 二维码已生成 - 用户: {}, URL: {}", userId, qrCodeUrl);
                }
                
                long startTime = System.currentTimeMillis();
                long maxWaitTime = 60000; // 1分钟超时
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
                        
                        // 🔥 使用通用框架保存登录状态
                        task.sendLog("正在保存登录状态...");
                        try {
                            Thread.sleep(2000);
                            
                            boolean saved = com.wx.fbsir.engine.playwright.login.manager.LoginStateManager
                                .saveLoginState(session, "deepseek", userId, loginStatus);
                            
                            if (saved) {
                                log.info("[DeepSeek扫码登录] ✅ 登录状态已保存 - 用户: {}", userId);
                            } else {
                                log.warn("[DeepSeek扫码登录] ⚠️ 登录状态保存失败 - 用户: {}", userId);
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } catch (Exception e) {
                            log.error("[DeepSeek扫码登录] 保存登录状态异常: {}", e.getMessage(), e);
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
                    browserPool.destroy(session);
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
     * - enableFileUpload: 是否启用文件上传（可选，默认false）
     * - uploadedFileUrl: 上传的文件URL（可选，enableFileUpload=true时有效）
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
        boolean enableFileUpload = payload.getBooleanValue("enableFileUpload", false);
        String uploadedFileUrl = payload.getString("uploadedFileUrl");
        // 🔥 区分两种ID：chatId是前端数据库分组ID，deepseekChatId是DeepSeek的AI会话ID
        String chatId = payload.getString("chatId");  // 前端分组ID（不用于DeepSeek导航）
        String deepseekChatId = payload.getString("deepseekChatId");  // DeepSeek AI会话ID（用于上下文复用）
        
        // 🔥 输出完整的payload信息，包括文件上传URL
        log.info("[DeepSeek咨询] ✅ 完整Payload内容:");
        log.info("  - query: {}", query);
        log.info("  - enableDeepThinking: {}", enableDeepThinking);
        log.info("  - enableWebSearch: {}", enableWebSearch);
        log.info("  - enableFileUpload: {}", enableFileUpload);
        log.info("  - uploadedFileUrl: {}", uploadedFileUrl != null && !uploadedFileUrl.isEmpty() ? uploadedFileUrl : "未上传文件");
        log.info("  - 前端chatId: {}", chatId);
        log.info("  - deepseekChatId: {}", deepseekChatId);
        
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
        
        // 🤖 AI咨询业务使用AI流式任务（发送 AI_TASK_* 消息）
        StreamTask task = startAiStreamTask(userId, sessionId, aiType, 6000);
        BrowserSession session = null;
        
        try {
            if (query == null || query.trim().isEmpty()) {
                task.sendError("问题内容不能为空");
                return;
            }
            
            task.sendLog("正在打开DeepSeek...");
            
            session = browserPool.acquirePersistent(userId, "deepseek", false);
            
            // 🔥 使用通用框架恢复登录状态
            if (com.wx.fbsir.engine.playwright.login.manager.LoginStateManager.hasLoginState("deepseek", userId)) {
                try {
                    boolean restored = com.wx.fbsir.engine.playwright.login.manager.LoginStateManager
                        .restoreLoginState(session, "deepseek", userId);
                    if (restored) {
                        log.info("[DeepSeek AI咨询] ✅ 登录状态已恢复 - 用户: {}", userId);
                    } else {
                        log.warn("[DeepSeek AI咨询] ⚠️ 登录状态恢复失败 - 用户: {}", userId);
                    }
                } catch (Exception e) {
                    log.warn("[DeepSeek AI咨询] ⚠️ 登录状态恢复异常: {}", e.getMessage());
                }
            }
            
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
                
                // 🔥 处理文件上传（如果有）— 使用通用文件处理工具
                if (enableFileUpload && uploadedFileUrl != null && !uploadedFileUrl.isEmpty()) {
                    task.sendLog("检测到文件上传请求，正在处理...");
                    log.info("[DeepSeek咨询] 开始文件处理流程: {}", uploadedFileUrl);
                    
                    FileDownloadUtil.UploadResult fileResult = fileDownloadUtil.downloadAndUploadToPage(
                        uploadedFileUrl,
                        page,
                        (p, filePath) -> deepSeekUtil.uploadFile(p, filePath),
                        () -> {
                            // 上传前准备：关闭联网搜索按钮（文件上传与联网搜索冲突）
                            task.sendLog("上传文件前关闭联网搜索按钮...");
                            deepSeekUtil.closeWebSearchButton(page);
                            page.waitForTimeout(1500);
                        }
                    );
                    
                    if (fileResult.isSuccess()) {
                        task.sendLog("文件已成功上传到DeepSeek");
                        log.info("[DeepSeek咨询] 文件处理流程完成 - 成功");
                    } else {
                        task.sendLog("文件处理失败(" + fileResult.getErrorMessage() + ")，将继续发送文本消息");
                        log.warn("[DeepSeek咨询] 文件处理流程完成 - 失败: {}", fileResult.getErrorMessage());
                    }
                }
                
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
                
                task.sendLog("AI回复完成，等待页面渲染...");
                
                // 🔥 关键：AI回复完成后等待页面完全渲染
                page.waitForTimeout(2000);
                
                task.sendLog("正在提取会话信息...");
                
                // 提取会话ID
                String newChatId = deepSeekUtil.extractChatId(page);
                String shareUrl = newChatId != null ? 
                    "https://chat.deepseek.com/a/chat/s/" + newChatId : null;
                
                // 🔥 临时禁用截图功能（后续完善长截图逻辑）
                String conversationScreenshotUrl = null;
                log.info("[DeepSeek] 截图功能已临时禁用，只传输文本内容");
                
                java.util.Map<String, Object> resultData = new java.util.HashMap<>();
                resultData.put("query", query);
                resultData.put("chatId", newChatId);
                resultData.put("shareUrl", shareUrl);
                resultData.put("mode", mode);
                resultData.put("elapsedTime", (int) (System.currentTimeMillis() - startTime) / 1000);
                
                // 🔥 数据存储策略（优化版）：
                // - data.textContent字段：始终存储真实的AI文本内容（用于draft_content）
                // - data.answer字段：优先存储截图URL，无截图时存储文本内容（用于前端显示）
                // - data.conversationScreenshot字段：存储截图URL（供draft表使用）
                resultData.put("textContent", answer != null ? answer : "");  // 🔥 始终存储真实文本
                
                if (conversationScreenshotUrl != null) {
                    resultData.put("answer", conversationScreenshotUrl);  // 🔥 优先存储截图URL
                    resultData.put("conversationScreenshot", conversationScreenshotUrl);
                    resultData.put("hasScreenshot", true);
                    log.info("[DeepSeek咨询] ✅ answer存储截图URL，textContent存储文本（{}字符）", 
                        answer != null ? answer.length() : 0);
                } else {
                    resultData.put("answer", answer != null ? answer : "DeepSeek回复完成，但获取内容失败");
                    resultData.put("hasScreenshot", false);
                    log.warn("[DeepSeek咨询] ⚠️ 截图失败，answer存储文本内容");
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
                    browserPool.destroy(session);
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
                log.debug("[DeepSeek截图] 上传成功 - URL: {}", uploadedUrl);
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
    
    /**
     * 截取AI回复区域的完整内容并上传（根据指定class区域截图）
     * 
     * 【截图策略】
     * 1. 定位AI回复容器区域：class="_4f9bf79 d7dc56a8 _43c05b5"
     * 2. 滚动到容器顶部
     * 3. 截取该容器的完整内容（可能需要滚动截图）
     * 
     * @param page Playwright 页面对象
     * @param userId 用户ID
     * @param fileName 文件名（不含扩展名）
     * @return 上传成功返回 URL，失败返回 null
     */
    private String captureFullPageAndUpload(Page page, String userId, String fileName) {
        try {
            // 🔥 定位AI回复的容器区域（兼容新旧DOM结构）
            // 新DOM: class="_4f9bf79 d7dc56a8 _43c05b5"
            // 旧DOM: class="_4f9bf79 _43c05b5"
            String[] possibleSelectors = {
                "div._4f9bf79.d7dc56a8._43c05b5",  // 新DOM（带d7dc56a8）
                "div._4f9bf79._43c05b5"            // 旧DOM（不带d7dc56a8）
            };
            
            String containerSelector = null;
            for (String selector : possibleSelectors) {
                if (page.locator(selector).count() > 0) {
                    containerSelector = selector;
                    log.debug("[DeepSeek截图] 找到AI回复容器: {}", selector);
                    break;
                }
            }
            
            // 检查容器是否存在
            if (containerSelector == null) {
                log.warn("[DeepSeek截图] 未找到AI回复容器，使用全页截图");
                // 回退方案：全页截图
                page.evaluate("window.scrollTo(0, 0)");
                page.waitForTimeout(500);
                
                Page.ScreenshotOptions options = new Page.ScreenshotOptions().setFullPage(true);
                byte[] screenshotBytes = page.screenshot(options);
                
                com.wx.fbsir.engine.playwright.util.ScreenshotUploadClient.UploadResult result = 
                    uploadClient.uploadScreenshot(userId, fileName, screenshotBytes);
                return result.isSuccess() ? result.getUrl() : null;
            }
            
            // 🔥 滚动到容器顶部并确保内容完全渲染（参考老项目cube-engine）
            page.evaluate("(selector) => { " +
                "const containers = document.querySelectorAll(selector); " +
                "if (containers.length > 0) { " +
                "  const latestContainer = containers[containers.length - 1]; " +
                "  latestContainer.scrollIntoView({ behavior: 'smooth', block: 'start' }); " +
                "  window.scrollBy(0, -50); " + // 稍微往上偏移
                "} " +
            "}", containerSelector);
            page.waitForTimeout(1500); // 🔥 等待滚动和内容渲染完成
            
            // 🔥 截取最后一个容器（最新的AI回复）
            com.microsoft.playwright.Locator containerLocator = page.locator(containerSelector).last();
            
            // 🔥 使用CSS给容器添加10px padding再截图
            page.evaluate("(selector) => { " +
                "const element = document.querySelector(selector); " +
                "if (element) { " +
                "  element.style.padding = '10px'; " +
                "} " +
            "}", containerSelector);
            page.waitForTimeout(100); // 等待样式生效
            
            byte[] screenshotBytes = containerLocator.screenshot(
                new com.microsoft.playwright.Locator.ScreenshotOptions()
            );
            
            // 上传到 Admin 服务器
            com.wx.fbsir.engine.playwright.util.ScreenshotUploadClient.UploadResult result = 
                uploadClient.uploadScreenshot(userId, fileName, screenshotBytes);
            
            if (result.isSuccess()) {
                String uploadedUrl = result.getUrl();
                log.info("[DeepSeek区域截图] 上传成功 - 容器: {}, URL: {}", containerSelector, uploadedUrl);
                return uploadedUrl;
            } else {
                log.error("[DeepSeek区域截图] 上传失败 - 错误: {}", result.getErrorMessage());
                return null;
            }
        } catch (Exception e) {
            log.error("[DeepSeek区域截图] 截图失败 - 错误: {}", e.getMessage(), e);
            return null;
        }
    }
}
