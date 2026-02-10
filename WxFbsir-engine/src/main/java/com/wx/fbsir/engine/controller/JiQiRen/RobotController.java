package com.wx.fbsir.engine.controller.JiQiRen;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.wx.fbsir.engine.capability.annotation.StreamCapability;
import com.wx.fbsir.engine.capability.base.StreamTaskHelper;
import com.wx.fbsir.engine.playwright.pool.BrowserPoolManager;
import com.wx.fbsir.engine.playwright.session.BrowserSession;
import com.wx.fbsir.engine.util.BrowserSessionLockUtil;
import com.wx.fbsir.engine.utils.JiQiRen.JiQiRenLoginUtil;
import com.wx.fbsir.engine.websocket.message.EngineMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

/**
 * 企业微信机器人知识库控制器
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 功能概述
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 1. 机器人知识库配置 - 扫码登录企业微信并添加知识库内容
 * 2. 自动化流程 - 完整的机器人知识库配置流程自动化执行
 * 3. 流式输出 - 实时推送执行进度和二维码截图
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 消息类型
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * - ROBOT_SET_KNOWLEDGE: 扫码登录并配置机器人知识库（流式输出）
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 业务流程
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 1. 扫码登录 → 2. 导航到安全与管理 → 3. 进入管理工具
 * → 4. 选择智能机器人 → 5. 点击侧边栏"管理" → 6. 定位目标机器人
 * → 7. 点击"详情" → 8. 点击知识集"查看" → 9. 添加知识库内容
 * 
 * @author wxfbsir
 * @date 2025-01-06
 */
@Controller
public class RobotController extends StreamTaskHelper{
    protected final Logger log = LoggerFactory.getLogger(getClass());
    private static final String Robot_HOME_URL = "https://work.weixin.qq.com/wework_admin/loginpage_wx";
    
    // 企业微信机器人全局锁：确保同时只有一个用户可以操作机器人
    private static final java.util.concurrent.locks.ReentrantLock ROBOT_GLOBAL_LOCK = new java.util.concurrent.locks.ReentrantLock();
    private static volatile String currentUserId = null; // 当前正在使用机器人的用户ID
    
    @Autowired
    private BrowserPoolManager browserPoolManager;
    @Autowired
    private com.wx.fbsir.engine.playwright.util.ScreenshotUploadClient uploadClient;
    @Autowired
    private JiQiRenLoginUtil jiQiRenLoginUtil;
    
    /**
     * 企业微信机器人知识库配置（流式返回）
     * 
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * 📌 功能说明
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * 
     * 完整的机器人知识库配置流程，包括：
     * 1. 自动扫码登录企业微信（如果未登录）
     * 2. 导航到机器人管理页面
     * 3. 定位目标机器人
     * 4. 添加网页内容到机器人知识库
     * 
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * 📌 请求参数
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * 
     * 请求JSON示例：
     * ```json
     * {
     *   "type": "ROBOT_SET_KNOWLEDGE",
     *   "engineId": "engine-001",
     *   "userId": "user-123",
     *   "payload": {
     *     "requestId": "req-001",
     *     "robotName": "智能客服机器人",
     *     "importWebUrl": "https://example.com/knowledge"
     *   }
     * }
     * ```
     * 
     * 参数说明：
     * | 参数名 | 类型 | 必填 | 说明 | 示例值 |
     * |--------|------|------|------|--------|
     * | `robotName` | String | ✅ | 智能体机器人名称 | `"智能客服机器人"` |
     * | `importWebUrl` | String | ✅ | 要导入的网页URL | `"https://example.com/knowledge"` |
     * | `requestId` | String | ⭕ | 请求ID（Admin自动生成） | `"req-001"` |
     *
     * 
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * 📌 进度推送
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * 
     * 流式输出消息类型：
     * - `TASK_LOG`: 执行步骤日志（如"请使用微信扫码登录"）
     * - `TASK_SCREENSHOT`: 二维码截图（登录过程中）
     * - `TASK_RESULT`: 最终结果（成功/失败）
     * 
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * 📌 返回数据
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * 
     * 成功响应：
     * ```json
     * {
     *   "type": "TASK_RESULT",
     *   "userId": "user-123",
     *   "payload": {
     *     "requestId": "req-001",
     *     "success": true,
     *     "data": {
     *       "message": "知识集添加完成",
     *       "robotName": "智能客服机器人",
     *       "importWebUrl": "https://example.com/knowledge",
     *       "timestamp": 1736144400000
     *     }
     *   }
     * }
     * ```
     * 
     * 错误响应：
     * ```json
     * {
     *   "type": "TASK_RESULT",
     *   "userId": "user-123",
     *   "payload": {
     *     "requestId": "req-001",
     *     "success": false,
     *     "errorCode": "TASK_ERROR",
     *     "errorMessage": "机器人知识库配置失败: 无法找到机器人",
     *     "timestamp": 1736144400000
     *   }
     * }
     * ```
     * 
     * @param message 消息对象，包含userId和payload参数
     */
    @StreamCapability(
            type = "ROBOT_SET_KNOWLEDGE",
            description = "企业微信机器人添加知识库",
            progressInterval = 2000  // 推送间隔（毫秒）
    )
    public void robot_addKnowledge(EngineMessage message){
        //获取用户id
        String userId = message.getUserId();

        String requestId = message.getPayloadValue("requestId");

        //获取用户添加的知识库url
        String importWebUrl = message.getPayloadValue("importWebUrl");
        //获取用户添加知识库的机器人名称
        String robotName = message.getPayloadValue("robotName");

        log.info("[企业微信机器人知识库配置] 开始 - 用户: {}, 请求: {},配置的知识库url：{}, 智能体: {}",
                userId, requestId,importWebUrl,  robotName);

        StreamTaskHelper.StreamTask task = startStreamTask(userId, requestId, 2000);
        BrowserSession session = null;
        boolean globalLockAcquired = false;
        try {
            // 步骤0: 尝试获取企业微信机器人全局锁
            globalLockAcquired = ROBOT_GLOBAL_LOCK.tryLock(0, TimeUnit.SECONDS);
            
            if (!globalLockAcquired) {
                // 锁被占用，说明有其他用户正在使用机器人
                String currentUser = currentUserId != null ? "用户" + currentUserId : "其他用户";
                int queueLength = ROBOT_GLOBAL_LOCK.hasQueuedThreads() ? ROBOT_GLOBAL_LOCK.getQueueLength() : 0;
                
                // 发送排队信息
                task.sendLog("企业微信机器人当前正被" + currentUser + "使用中");
                if (queueLength > 0) {
                    task.sendLog("当前排队人数: " + queueLength + " 人，请稍后...");
                } else {
                    task.sendLog("正在等待机器人空闲...");
                }
                
                log.info("[企业微信机器人知识库配置] 需要排队 - 用户: {}, 当前使用者: {}, 排队人数: {}", 
                    userId, currentUserId, queueLength);
                
                // 等待获取锁（最多等待10分钟）
                task.sendLog("正在排队等待...");
                globalLockAcquired = ROBOT_GLOBAL_LOCK.tryLock(10, TimeUnit.MINUTES);
                
                if (!globalLockAcquired) {
                    task.sendError("等待超时，请稍后重试");
                    log.warn("[企业微信机器人知识库配置] 排队等待超时 - 用户: {}", userId);
                    return;
                }
                
                // 排队成功，获取到锁
                task.sendLog("排队完成，开始配置机器人...");
            }
            
            // 成功获取锁，记录当前用户
            currentUserId = userId;
            log.info("[企业微信机器人知识库配置] 获取全局锁成功 - 用户: {}", userId);
            
            // 步骤1: 获取非持久化浏览器会话（不保存登录状态，每次都需要重新扫码）
            task.sendLog("正在获取浏览器会话...");
            session = browserPoolManager.acquireTemporary(requestId, false);

            Page page = session.getOrCreatePage();

            // 步骤2: 扫码登录（如果未登录）
            task.sendLog("正在打开企业微信登录页面...");
            page.navigate(Robot_HOME_URL);
            page.waitForLoadState();
            page.waitForTimeout(3000); // 等待页面完全加载

            Page page_login = jiQiRenLoginUtil.scanLogin(page,task,log,userId,requestId);
            if (page_login == null) {
                task.sendError("扫码登陆失败");
                return;
            }else{
                page = page_login;
            }

            task.sendLog("登录完成，开始配置机器人知识库...");

            // ========== 步骤3：导航到安全与管理 ==========
            task.sendLog("正在导航到安全与管理页面...");
            Locator securityManageBtn = page.locator("button:has-text('安全与管理'), a:has-text('安全与管理')").first();
            securityManageBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
            securityManageBtn.click();
            page.waitForLoadState();
            page.waitForTimeout(2000); // 🔥 增加到2秒，确保页面完全加载
            task.sendLog("已进入安全与管理页面");

            // ========== 步骤4：进入管理工具 ==========
            task.sendLog("正在进入管理工具页面...");
            Locator manageToolBtn = page.locator("button:has-text('管理工具'), a:has-text('管理工具')").first();
            manageToolBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
            manageToolBtn.click();
            page.waitForLoadState();
            page.waitForTimeout(2000); // 🔥 增加到2秒，确保页面完全加载
            task.sendLog("已进入管理工具页面");

            // ========== 步骤5：选择智能机器人 ==========
            task.sendLog("正在进入智能机器人页面...");
            // 在管理工具页面中，通过managetool_cnt_items_app区域定位"智能机器人"链接
            Locator robotLink = page.locator(".managetool_cnt_items_app_title:has-text('智能机器人')").first();
            if (robotLink.count() == 0) {
                // 备选：通过链接href匹配
                robotLink = page.locator("a[href*='aiHelper']").first();
            }
            if (robotLink.count() == 0) {
                // 再备选：通过文本匹配
                robotLink = page.locator("a:has-text('智能机器人')").first();
            }
            robotLink.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(10000));
            robotLink.click();
            page.waitForLoadState();
            page.waitForTimeout(3000); // 等待智能机器人页面完全加载
            task.sendLog("已进入智能机器人页面");

            // ========== 步骤6：点击侧边栏"管理"菜单 ==========
            task.sendLog("正在切换到管理视图...");
            // 侧边栏菜单结构: sidebar_menu > menu_item，点击"管理"选项
            Locator manageMenuItem = page.locator(".sidebar_menu .menu_item:has-text('管理')").first();
            if (manageMenuItem.count() == 0) {
                // 备选：通过li文本匹配
                manageMenuItem = page.locator("li.menu_item:has-text('管理')").first();
            }
            manageMenuItem.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(10000));
            manageMenuItem.click();
            page.waitForLoadState();
            page.waitForTimeout(3000); // 等待管理列表完全加载
            task.sendLog("已切换到管理视图");

            // ========== 步骤7：在管理列表中定位目标机器人 ==========
            task.sendLog("正在查找目标机器人: " + robotName);
            // 在hl_list管理列表中，通过account_aibot_name_text定位机器人名称
            Locator targetRobotRow = page.locator(".hl_list_content .hl_lc_line:has(.account_aibot_name_text:has-text('" + robotName + "'))").first();
            if (targetRobotRow.count() == 0) {
                // 备选：直接通过机器人名称文本定位所在行
                targetRobotRow = page.locator(".hl_lc_line:has-text('" + robotName + "')").first();
            }
            if (targetRobotRow.count() == 0) {
                log.error("[企业微信机器人知识库配置] 未查询到目标机器人: {}", robotName);
                task.sendError("未查询到指定机器人: " + robotName);
                return;
            }
            targetRobotRow.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(10000));
            task.sendLog("已定位到目标机器人: " + robotName);

            // ========== 步骤8：点击目标机器人的"详情"链接 ==========
            task.sendLog("正在进入机器人详情页面...");
            // 在目标机器人所在行中，点击hl_lc_detail区域的"详情"链接
            Locator detailLink = targetRobotRow.locator(".hl_lc_detail a").first();
            if (detailLink.count() == 0) {
                // 备选：直接在行内找"详情"文本
                detailLink = targetRobotRow.locator("a:has-text('详情')").first();
            }
            detailLink.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(10000));
            detailLink.click();
            page.waitForLoadState();
            page.waitForTimeout(3000); // 等待详情页完全加载
            task.sendLog("已进入机器人详情页面");

            // ========== 步骤9：点击知识集的"查看"按钮 ==========
            task.sendLog("正在进入知识集页面...");
            // 在详情页中定位包含"知识集"标签的section区域，点击其中的"查看"链接
            Locator knowledgeSetView = page.locator(".section:has(.section_label:has-text('知识集')) .section_value a.link:has-text('查看')").first();
            if (knowledgeSetView.count() == 0) {
                // 备选：直接在section_value中找"查看"链接
                knowledgeSetView = page.locator(".section_field:has(.section_label:has-text('知识集')) a:has-text('查看')").first();
            }
            if (knowledgeSetView.count() == 0) {
                // 再备选：通过文本"知识集"附近的"查看"链接
                knowledgeSetView = page.locator("a.link:has-text('查看')").first();
            }
            knowledgeSetView.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(10000));
            knowledgeSetView.click();
            page.waitForLoadState();
            page.waitForTimeout(3000); // 等待知识集页面完全加载
            task.sendLog("已进入知识集页面");

            // ========== 步骤10：添加知识库内容 ==========
            task.sendLog("正在添加知识库内容...");
            Locator addContentBtn = page.locator("button:has-text('添加内容'), a:has-text('添加内容')").first();
            addContentBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
            addContentBtn.click();
            page.waitForLoadState();
            page.waitForTimeout(2000); // 等待弹窗完全加载
            task.sendLog("已打开添加内容弹窗");
            
            // ========== 步骤11：选择网页导入 ==========
            task.sendLog("正在选择网页导入方式...");
            Locator webBtn = page.locator("h4.card_title:has-text('网页')").first();
            webBtn.click();
            page.waitForLoadState();
            page.waitForTimeout(2000); // 等待网页导入选项加载完成
            task.sendLog("已选择网页导入方式");

            // ========== 步骤12：输入URL并确认 ==========
            task.sendLog("正在输入网页URL: " + importWebUrl);
            // 定位URL输入框（适配class/label特征，可根据实际DOM替换）
            Locator urlInput = page.locator("input.t-input__inner[type='text'][placeholder*='请输入或粘贴以https://或http://开头的链接']").first();
            urlInput.fill(importWebUrl); // 输入URL
            page.waitForTimeout(500);
            task.sendLog("已输入网页URL");

            // 点击确定按钮
            task.sendLog("正在确认添加知识库内容...");
            Locator confirmBtn = page.locator("button:has-text('确定')").first();
            confirmBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
            confirmBtn.click();
            
            // 🔥 简化等待逻辑：直接等待30秒让后台处理完成
            task.sendLog("等待企业微信后台处理（最长30秒）...");
            page.waitForTimeout(30000); // 等待30秒让后台处理完成
            
            task.sendLog("知识库内容添加完成");

            // ========== 步骤13：完成配置 ==========
            task.sendLog("机器人知识库配置完成！");
            
            // 构建返回数据
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("message", "知识集添加完成");
            resultData.put("robotName", robotName);
            resultData.put("importWebUrl", importWebUrl);
            task.sendSuccess("机器人知识库配置完成！", resultData);
            log.info("[企业微信机器人知识库配置] 完成 - 用户: {}, 机器人: {}, URL: {}", 
                userId, robotName, importWebUrl);
        } catch (Exception e) {
            log.error("[企业微信机器人知识库配置] 执行失败 - 用户: {}, 请求: {}", userId, requestId, e);
            task.sendError("机器人知识库配置失败");
        } finally {
            // 释放全局锁
            if (globalLockAcquired) {
                currentUserId = null;
                ROBOT_GLOBAL_LOCK.unlock();
                log.info("[企业微信机器人知识库配置] 释放全局锁 - 用户: {}", userId);
            }
            
            task.stop();
            // 确保资源释放
            if (session != null) {
                try {
                    session.destroy();
                    log.debug("[企业微信机器人知识库配置] 已销毁会话释放资源 - 用户: {}", userId);
                } catch (Exception e) {
                    log.warn("[企业微信机器人知识库配置] 销毁会话失败 - 用户: {}, 错误: {}", userId, e.getMessage());
                }
            }
        }
    }
    /**
     * 截图并上传到 Admin 服务器
     * 
     * @param page 页面对象
     * @param userId 用户ID
     * @param fileName 截图文件名
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
                log.info("[机器人截图] 上传成功 - URL: {}", uploadedUrl);
                return uploadedUrl;
            } else {
                log.error("[机器人截图] 上传失败 - 错误: {}", result.getErrorMessage());
                return null;
            }
        } catch (Exception e) {
            log.error("[机器人截图] 截图失败 - 错误: {}", e.getMessage(), e);
            return null;
        }
    }
}
