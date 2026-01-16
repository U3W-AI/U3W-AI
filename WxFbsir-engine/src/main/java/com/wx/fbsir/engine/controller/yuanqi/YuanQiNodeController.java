package com.wx.fbsir.engine.controller.yuanqi;

import com.microsoft.playwright.Page;
import com.wx.fbsir.engine.capability.annotation.StreamCapability;
import com.wx.fbsir.engine.capability.base.StreamTaskHelper;
import com.wx.fbsir.engine.playwright.pool.BrowserPoolManager;
import com.wx.fbsir.engine.playwright.session.BrowserSession;
import com.wx.fbsir.engine.utils.yuanqi.YuanQiLoginUtil;
import com.wx.fbsir.engine.utils.yuanqi.YuanQiNodeUtil;
import com.wx.fbsir.engine.utils.yuanqi.YuanQiWorkflowUtil;
import com.wx.fbsir.engine.websocket.message.EngineMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.Map;

/**
 * 元器工作流节点相关能力控制器。
 *
 * <p>
 * 基于 Playwright 驱动浏览器，提供大模型节点编辑、工作流调试、工作流发布等流式能力，
 * 对外通过 WebSocket 与 Admin 进行交互。
 * </p>
 */
@Controller
public class YuanQiNodeController extends StreamTaskHelper {

    @Autowired
    private YuanQiLoginUtil loginUtil;

    @Autowired
    private YuanQiWorkflowUtil workflowUtil;

    @Autowired
    private YuanQiNodeUtil nodeUtil;

    @Autowired
    private BrowserPoolManager browserPool;

    @Autowired
    @Lazy
    private com.wx.fbsir.engine.websocket.client.WebSocketClientManager webSocketClientManager;

    @Autowired
    private com.wx.fbsir.engine.playwright.util.ScreenshotUploadClient uploadClient;

    /**
     * 编辑大模型节点（流式返回）。
     *
     * <p>
     * 能力标识：{@code YUANQI_EDIT_NODE}
     * </p>
     */
    @StreamCapability(
            type = "YUANQI_EDIT_NODE",
            description = "编辑元器工作流大模型节点",
            progressInterval = 3000
    )
    public void handleEditNode(EngineMessage message) {
        String userId = message.getUserId();
        String requestId = message.getPayloadValue("requestId");
        String spaceName = message.getPayloadValue("spaceName");
        String agentName = message.getPayloadValue("agentName");
        String workflowName = message.getPayloadValue("workflowName");
        String nodeName = message.getPayloadValue("nodeName");
        Map<String, Object> config = message.getPayloadValue("config");

        log.info("[元器节点编辑] 开始 - 用户: {}, 请求: {}", userId, requestId);

        StreamTask task = startStreamTask(userId, requestId, 3000);
        BrowserSession session = null;
        Page workflowPage = null;

        try {
            // 参数校验
            if (spaceName == null || spaceName.isEmpty()) {
                task.sendError("参数错误: spaceName 不能为空");
                return;
            }
            if (agentName == null || agentName.isEmpty()) {
                task.sendError("参数错误: agentName 不能为空");
                return;
            }
            if (workflowName == null || workflowName.isEmpty()) {
                task.sendError("参数错误: workflowName 不能为空");
                return;
            }
            if (nodeName == null || nodeName.isEmpty()) {
                task.sendError("参数错误: nodeName 不能为空");
                return;
            }
            if (config == null || config.isEmpty()) {
                task.sendError("参数错误: config 不能为空");
                return;
            }

            task.sendLog("正在获取浏览器会话...");

            // 获取持久化浏览器会话
            session = browserPool.acquirePersistent(userId, "yuanqi", false);
            Page page = session.getOrCreatePage();

            // 步骤1：导航到元器首页
            task.sendLog("正在导航到元器首页...");
            if (!loginUtil.navigateToHomePage(page)) {
                task.sendError("导航到元器首页失败，请检查网络连接");
                return;
            }

            // 检查登录状态
            String loginStatus = loginUtil.checkLoginStatus(page, false);
            if ("false".equals(loginStatus)) {
                task.sendError("未登录元器，请先使用 YUANQI_SCAN_LOGIN 进行扫码登录");
                return;
            }
            task.sendLog("已登录元器，用户: " + loginStatus);

            // 步骤2：导航到工作流编辑页面
            task.sendLog("正在导航到工作流编辑页面...");
            YuanQiWorkflowUtil.WorkflowNavigationResult navResult =
                    workflowUtil.navigateToWorkflowEdit(page, spaceName, agentName, workflowName);

            if (!navResult.isSuccess()) {
                task.sendError("导航失败: " + navResult.getMessage());
                String errorScreenshot = captureAndUpload(page, userId, "yuanqi_nav_error");
                if (errorScreenshot != null) {
                    task.sendScreenshot(errorScreenshot);
                }
                return;
            }

            // 【关键修复】获取新页面，如果存在新页面则关闭旧页面
            workflowPage = navResult.getNewPage();
            if (workflowPage != null) {
                // 关闭原始页面（避免多个页面残留）
                try {
                    if (!page.isClosed()) {
                        page.close();
                        log.debug("[元器节点编辑] 已关闭原始页面");
                    }
                } catch (Exception e) {
                    log.warn("[元器节点编辑] 关闭原始页面失败: {}", e.getMessage());
                }
            } else {
                workflowPage = page;
            }

            task.sendLog("已进入工作流编辑页面");

            // 截图当前状态
            String navScreenshot = captureAndUpload(workflowPage, userId, "yuanqi_workflow");
            if (navScreenshot != null) {
                task.sendScreenshot(navScreenshot);
            }

            // 步骤3：编辑节点
            task.sendLog("正在定位节点: " + nodeName);
            // 获取超时时间（秒），默认90秒
            Integer timeoutSeconds = message.getPayloadValue("timeout");
            if (timeoutSeconds == null || timeoutSeconds <= 0) {
                timeoutSeconds = 90; // 默认90秒
            }
            log.info("[元器节点编辑] 使用超时时间: {}秒", timeoutSeconds);

            YuanQiNodeUtil.NodeEditResult editResult = nodeUtil.editLLMNode(workflowPage, nodeName, config, workflowName, timeoutSeconds);

            if (editResult.isSuccess()) {
                task.sendLog("节点配置已保存");

                // 截图编辑结果
                String resultScreenshot = captureAndUpload(workflowPage, userId, "yuanqi_edit_result");
                if (resultScreenshot != null) {
                    task.sendScreenshot(resultScreenshot);
                }

                Map<String, Object> resultData = new HashMap<>();
                resultData.put("message", "节点配置已保存");
                resultData.put("nodeName", nodeName);
                resultData.put("workflowName", workflowName);
                resultData.put("screenshotUrl", resultScreenshot);

                task.sendSuccess("编辑成功", resultData);
                log.info("[元器节点编辑] 成功 - 用户: {}, 节点: {}", userId, nodeName);
            } else {
                task.sendError("节点编辑失败: " + editResult.getMessage());

                // 截图错误状态
                String errorScreenshot = captureAndUpload(workflowPage, userId, "yuanqi_edit_error");
                if (errorScreenshot != null) {
                    task.sendScreenshot(errorScreenshot);
                }

                log.warn("[元器节点编辑] 失败 - 用户: {}, 节点: {}", userId, nodeName);
            }

        } catch (Exception e) {
            log.error("[元器节点编辑] 异常 - 用户: {}, 请求: {}", userId, requestId, e);
            task.sendError("编辑过程发生异常: " + e.getMessage());
        } finally {
            // 【核心修复】确保资源被正确释放
            log.debug("[元器节点编辑] 开始清理资源 - 用户: {}", userId);

            // 1. 先关闭所有页面
            try {
                if (workflowPage != null && !workflowPage.isClosed()) {
                    workflowPage.close();
                    log.debug("[元器节点编辑] 已关闭工作流页面");
                }
            } catch (Exception e) {
                log.warn("[元器节点编辑] 关闭工作流页面失败: {}", e.getMessage());
            }

            // 2. 再停止流式任务
            if (task != null) {
                try {
                    task.stop();
                    log.debug("[元器节点编辑] 流式任务已停止");
                } catch (Exception e) {
                    log.warn("[元器节点编辑] 停止流式任务失败: {}", e.getMessage());
                }
            }

            // 3. 最后销毁浏览器会话
            if (session != null) {
                try {
                    // 【关键】确保调用真正的销毁方法
                    session.destroy();
                    log.info("[元器节点编辑] 浏览器会话已销毁 - 用户: {}", userId);
                } catch (Exception e) {
                    log.error("[元器节点编辑] 销毁浏览器会话失败 - 用户: {}, 错误: {}", userId, e.getMessage(), e);
                    // 兜底方案：强制清理
                    forceCleanupBrowserProcesses();
                }
            } else {
                log.warn("[元器节点编辑] 浏览器会话为空，无法销毁 - 用户: {}", userId);
            }
        }
    }

    /**
     * 调试工作流（流式返回）。
     *
     * <p>
     * 能力标识：{@code YUANQI_DEBUG_WORKFLOW}
     * </p>
     */
    @StreamCapability(
            type = "YUANQI_DEBUG_WORKFLOW",
            description = "调试元器工作流",
            progressInterval = 5000
    )
    public void handleDebugWorkflow(EngineMessage message) {
        String userId = message.getUserId();
        String requestId = message.getPayloadValue("requestId");
        String spaceName = message.getPayloadValue("spaceName");
        String agentName = message.getPayloadValue("agentName");
        String workflowName = message.getPayloadValue("workflowName");
        Map<String, Object> debugInput = message.getPayloadValue("debugInput");

        log.info("[元器工作流调试] 开始 - 用户: {}, 请求: {}", userId, requestId);

        StreamTask task = startStreamTask(userId, requestId, 5000);
        BrowserSession session = null;
        Page workflowPage = null;

        try {
            // 参数校验
            if (spaceName == null || spaceName.isEmpty()) {
                task.sendError("参数错误: spaceName 不能为空");
                return;
            }
            if (agentName == null || agentName.isEmpty()) {
                task.sendError("参数错误: agentName 不能为空");
                return;
            }
            if (workflowName == null || workflowName.isEmpty()) {
                task.sendError("参数错误: workflowName 不能为空");
                return;
            }

            task.sendLog("正在获取浏览器会话...");

            // 获取持久化浏览器会话
            session = browserPool.acquirePersistent(userId, "yuanqi", false);
            Page page = session.getOrCreatePage();

            // 导航到元器首页
            task.sendLog("正在导航到元器首页...");
            if (!loginUtil.navigateToHomePage(page)) {
                task.sendError("导航到元器首页失败");
                return;
            }

            // 检查登录状态
            String loginStatus = loginUtil.checkLoginStatus(page, false);
            if ("false".equals(loginStatus)) {
                task.sendError("未登录元器，请先使用 YUANQI_SCAN_LOGIN 进行扫码登录");
                return;
            }
            task.sendLog("已登录元器，用户: " + loginStatus);

            // 导航到工作流编辑页面
            task.sendLog("正在导航到工作流编辑页面...");
            YuanQiWorkflowUtil.WorkflowNavigationResult navResult =
                    workflowUtil.navigateToWorkflowEdit(page, spaceName, agentName, workflowName);

            if (!navResult.isSuccess()) {
                task.sendError("导航失败: " + navResult.getMessage());
                return;
            }

            // 【关键修复】获取新页面，如果存在新页面则关闭旧页面
            workflowPage = navResult.getNewPage();
            if (workflowPage != null) {
                try {
                    if (!page.isClosed()) {
                        page.close();
                        log.debug("[元器工作流调试] 已关闭原始页面");
                    }
                } catch (Exception e) {
                    log.warn("[元器工作流调试] 关闭原始页面失败: {}", e.getMessage());
                }
            } else {
                workflowPage = page;
            }

            task.sendLog("已进入工作流编辑页面");

            // 获取超时时间（秒），默认90秒
            Integer timeoutSeconds = message.getPayloadValue("timeout");
            if (timeoutSeconds == null || timeoutSeconds <= 0) {
                timeoutSeconds = 90; // 默认90秒
            }
            log.info("[元器工作流调试] 使用超时时间: {}秒", timeoutSeconds);

            // 执行调试
            task.sendLog("正在执行调试... (超时时间: " + timeoutSeconds + "秒)");
            YuanQiNodeUtil.DebugResult debugResult = nodeUtil.debugWorkflow(workflowPage, debugInput, userId, timeoutSeconds);

            // 优先使用方法内部返回的截图
            String debugScreenshot = debugResult.getScreenshotUrl();
            if (debugScreenshot == null) {
                debugScreenshot = captureAndUpload(workflowPage, userId, "yuanqi_debug");
            }

            if (debugScreenshot != null) {
                task.sendScreenshot(debugScreenshot);
            }

            if (debugResult.isSuccess()) {
                Map<String, Object> resultData = new HashMap<>();
                resultData.put("message", debugResult.getMessage());
                resultData.put("output", debugResult.getOutput());
                resultData.put("workflowName", workflowName);
                resultData.put("screenshotUrl", debugScreenshot);

                task.sendSuccess("调试完成", resultData);
                log.info("[元器工作流调试] 成功 - 用户: {}, 工作流: {}", userId, workflowName);
            } else {
                task.sendError("调试失败: " + debugResult.getMessage());
                log.warn("[元器工作流调试] 失败 - 用户: {}, 工作流: {}", userId, workflowName);
            }

        } catch (Exception e) {
            log.error("[元器工作流调试] 异常 - 用户: {}, 请求: {}", userId, requestId, e);
            task.sendError("调试失败: " + e.getMessage());
        } finally {
            // 【核心修复】确保资源被正确释放
            log.debug("[元器工作流调试] 开始清理资源 - 用户: {}", userId);

            // 1. 先关闭所有页面
            try {
                if (workflowPage != null && !workflowPage.isClosed()) {
                    workflowPage.close();
                    log.debug("[元器工作流调试] 已关闭工作流页面");
                }
            } catch (Exception e) {
                log.warn("[元器工作流调试] 关闭工作流页面失败: {}", e.getMessage());
            }

            // 2. 再停止流式任务
            if (task != null) {
                try {
                    task.stop();
                    log.debug("[元器工作流调试] 流式任务已停止");
                } catch (Exception e) {
                    log.warn("[元器工作流调试] 停止流式任务失败: {}", e.getMessage());
                }
            }

            // 3. 最后销毁浏览器会话
            if (session != null) {
                try {
                    session.destroy();
                    log.info("[元器工作流调试] 浏览器会话已销毁 - 用户: {}", userId);
                } catch (Exception e) {
                    log.error("[元器工作流调试] 销毁浏览器会话失败 - 用户: {}, 错误: {}", userId, e.getMessage(), e);
                    forceCleanupBrowserProcesses();
                }
            }
        }
    }

    /**
     * 发布工作流（流式返回）。
     *
     * <p>
     * 能力标识：{@code YUANQI_PUBLISH_WORKFLOW}
     * </p>
     */
    @StreamCapability(
            type = "YUANQI_PUBLISH_WORKFLOW",
            description = "发布元器工作流",
            progressInterval = 3000
    )
    public void handlePublishWorkflow(EngineMessage message) {
        String userId = message.getUserId();
        String requestId = message.getPayloadValue("requestId");
        String spaceName = message.getPayloadValue("spaceName");
        String agentName = message.getPayloadValue("agentName");
        String workflowName = message.getPayloadValue("workflowName");

        log.info("[元器工作流发布] 开始 - 用户: {}, 请求: {}", userId, requestId);

        StreamTask task = startStreamTask(userId, requestId, 3000);
        BrowserSession session = null;

        try {
            // 参数校验
            if (spaceName == null || spaceName.isEmpty()) {
                task.sendError("参数错误: spaceName 不能为空");
                return;
            }
            if (agentName == null || agentName.isEmpty()) {
                task.sendError("参数错误: agentName 不能为空");
                return;
            }
            if (workflowName == null || workflowName.isEmpty()) {
                task.sendError("参数错误: workflowName 不能为空");
                return;
            }

            task.sendLog("正在获取浏览器会话...");

            // 获取持久化浏览器会话
            session = browserPool.acquirePersistent(userId, "yuanqi", false);
            Page page = session.getOrCreatePage();

            // 导航到元器首页
            task.sendLog("正在导航到元器首页...");
            if (!loginUtil.navigateToHomePage(page)) {
                task.sendError("导航到元器首页失败");
                return;
            }

            // 检查登录状态
            String loginStatus = loginUtil.checkLoginStatus(page, false);
            if ("false".equals(loginStatus)) {
                task.sendError("未登录元器，请先使用 YUANQI_SCAN_LOGIN 进行扫码登录");
                return;
            }
            task.sendLog("已登录元器，用户: " + loginStatus);

            // 获取超时时间（秒），默认90秒
            Integer timeoutSeconds = message.getPayloadValue("timeout");
            if (timeoutSeconds == null || timeoutSeconds <= 0) {
                timeoutSeconds = 90; // 默认90秒
            }
            log.info("[元器工作流发布] 使用超时时间: {}秒", timeoutSeconds);

            // 直接调用 publishAgent 进行导航和发布
            task.sendLog("正在执行智能体发布流程... (超时时间: " + timeoutSeconds + "秒)");
            YuanQiNodeUtil.PublishResult publishResult = nodeUtil.publishAgent(page, spaceName, agentName, timeoutSeconds);

            // 截图发布结果
            String publishScreenshot = captureAndUpload(page, userId, "yuanqi_publish");
            if (publishScreenshot != null) {
                task.sendScreenshot(publishScreenshot);
            }

            if (publishResult.isSuccess()) {
                Map<String, Object> resultData = new HashMap<>();
                resultData.put("message", publishResult.getMessage());
                resultData.put("workflowName", workflowName);
                resultData.put("screenshotUrl", publishScreenshot);

                task.sendSuccess("发布成功", resultData);
                log.info("[元器工作流发布] 成功 - 用户: {}, 智能体: {}", userId, agentName);
            } else {
                task.sendError("发布失败: " + publishResult.getMessage());
                log.warn("[元器工作流发布] 失败 - 用户: {}, 智能体: {}", userId, agentName);
            }

        } catch (Exception e) {
            log.error("[元器工作流发布] 异常 - 用户: {}, 请求: {}", userId, requestId, e);
            task.sendError("发布失败: " + e.getMessage());
        } finally {
            // 【核心修复】确保资源被正确释放
            log.debug("[元器工作流发布] 开始清理资源 - 用户: {}", userId);

            // 1. 先停止流式任务
            if (task != null) {
                try {
                    task.stop();
                    log.debug("[元器工作流发布] 流式任务已停止");
                } catch (Exception e) {
                    log.warn("[元器工作流发布] 停止流式任务失败: {}", e.getMessage());
                }
            }

            // 2. 最后销毁浏览器会话
            if (session != null) {
                try {
                    session.destroy();
                    log.info("[元器工作流发布] 浏览器会话已销毁 - 用户: {}", userId);
                } catch (Exception e) {
                    log.error("[元器工作流发布] 销毁浏览器会话失败 - 用户: {}, 错误: {}", userId, e.getMessage(), e);
                    forceCleanupBrowserProcesses();
                }
            }
        }
    }

    /**
     * 强制清理浏览器残留进程（兜底方案）
     */
    private void forceCleanupBrowserProcesses() {
        try {
            log.warn("[浏览器清理] 开始强制清理残留浏览器进程");

            // Windows系统使用taskkill命令
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                String[] commands = {
                        "taskkill /F /IM chrome.exe",
                        "taskkill /F /IM chromium.exe",
                        "taskkill /F /IM msedge.exe",
                        "taskkill /F /IM node.exe"
                };

                for (String cmd : commands) {
                    try {
                        Process process = Runtime.getRuntime().exec(cmd);
                        process.waitFor(); // 等待命令执行完成
                        log.debug("[浏览器清理] 执行命令: {}", cmd);
                    } catch (Exception e) {
                        log.debug("[浏览器清理] 执行命令失败: {}, 错误: {}", cmd, e.getMessage());
                    }
                }
            }
            // 其他系统（Linux/Mac）可以添加相应的清理命令

            log.info("[浏览器清理] 强制清理完成");
        } catch (Exception e) {
            log.error("[浏览器清理] 强制清理异常: {}", e.getMessage(), e);
        }
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