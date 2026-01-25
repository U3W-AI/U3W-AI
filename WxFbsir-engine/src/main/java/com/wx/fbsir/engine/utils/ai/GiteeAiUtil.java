package com.wx.fbsir.engine.utils.ai;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.LoadState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Gitee AI Chat 平台工具类（简单原型）
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 核心职责
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 1. 登录状态检测
 * 2. 导航到登录页
 * 3. 消息发送与响应监听
 * 4. 内容提取与清理
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 使用方式
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * ```java
 * @Autowired
 * private GiteeAiUtil giteeAiUtil;
 * 
 * // 检查登录状态
 * String loginStatus = giteeAiUtil.checkLoginStatus(page, true);
 * 
 * // 导航到登录页
 * boolean success = giteeAiUtil.navigateToLoginPage(page);
 * 
 * // 发送消息并等待回复
 * String response = giteeAiUtil.sendMessageAndWaitResponse(page, "你好");
 * ```
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * ⚠️ 注意事项（原型阶段）
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 1. 本工具类是原型实现，选择器（selector）需要根据实际页面结构调整
 * 2. Gitee AI Chat 的具体页面结构需要实际访问后确定
 * 3. 登录方式可能需要调整（OAuth、二维码、账号密码等）
 * 4. 本实现采用通用的 DOM 操作方式，实际可能需要针对性优化
 * 
 * @author 实习生
 * @date 2026-01-22
 * @version 1.0 (原型阶段)
 */
@Component
public class GiteeAiUtil {

    private static final Logger log = LoggerFactory.getLogger(GiteeAiUtil.class);
    
    /**
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * 🔥 重要：以下URL需要根据实际的 Gitee AI Chat 地址调整
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     */
    
    /**
     * Gitee AI Chat 主页地址
     * 🔥 关键：登录和聊天必须在同一个域名，避免跨域Cookie问题
     */
    private static final String GITEE_AI_HOME_URL = "https://chat.gitee.com/";
    
    /**
     * Gitee AI Chat 登录页地址
     * 🔥 修改：不再跨域到 gitee.com，直接使用聊天页（chat.gitee.com）
     *    用户在浏览器中手动登录，Cookie会保存在 chat.gitee.com 域下
     */
    private static final String GITEE_AI_LOGIN_URL = "https://chat.gitee.com/";

    /**
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * 功能1：检查 Gitee AI Chat 登录状态
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * 
     * @param page Playwright 页面对象
     * @param navigate 是否需要先导航到主页
     * @return 登录状态：已登录返回用户名，未登录返回 "false"
     */
    public String checkLoginStatus(Page page, boolean navigate) {
        if (navigate) {
            try {
                log.debug("📍 [Gitee AI] 开始导航到主页");
                page.navigate(GITEE_AI_HOME_URL, new Page.NavigateOptions().setTimeout(10000));
                page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(10000));
                page.waitForTimeout(1000);
                log.debug("✅ [Gitee AI] 页面加载完成");
            } catch (Exception e) {
                log.warn("❌ [Gitee AI] 导航失败: {}", e.getMessage());
                return "false";
            }
        }

        /**
         * 🔥 登录状态检测策略（原型实现）
         * 
         * 由于不确定 Gitee AI Chat 的具体页面结构，这里提供几种常见的检测方式：
         * 
         * 1. 检测登录按钮是否存在（未登录）
         * 2. 检测用户头像/用户名元素（已登录）
         * 3. 检测特定的登录表单（未登录）
         * 4. 通过 localStorage/Cookie 检测
         * 
         * ⚠️ 实际选择器需要根据真实页面调整
         */
        
        try {
            // 🎯 策略0：检测"未登陆"文字（最优先，最准确）
            try {
                Locator notLoggedIn = page.locator("text=未登陆, text=未登录");
                if (notLoggedIn.count() > 0 && notLoggedIn.first().isVisible()) {
                    log.debug("🔍 [Gitee AI] 检测到'未登陆'文字，用户未登录");
                    return "false";
                }
            } catch (Exception e) {
                log.trace("检测'未登陆'文字异常: {}", e.getMessage());
            }
            
            // 策略1：检测是否有"登录"按钮（未登录的标志）
            try {
                Locator loginButton = page.locator("button:has-text('登录'), a:has-text('登录'), button:has-text('立即登录')");
                if (loginButton.count() > 0 && loginButton.first().isVisible()) {
                    log.debug("🔍 [Gitee AI] 检测到登录按钮，用户未登录");
                    return "false";
                }
            } catch (Exception e) {
                log.trace("检测登录按钮异常: {}", e.getMessage());
            }
            
            // 策略2：通过 URL 判断（如果在登录页则未登录）
            String currentUrl = page.url();
            if (currentUrl.contains("login") || currentUrl.contains("signin") || currentUrl.contains("sign_in")) {
                log.debug("🔍 [Gitee AI] 当前在登录页，用户未登录");
                return "false";
            }
            
            // 策略3：检测用户头像或用户名（已登录的标志）
            try {
                // 尝试获取用户名（如果能获取到用户名，说明已登录）
                Locator userNameElement = page.locator(".user-name, .username, [class*='username'], [class*='user-info']").first();
                if (userNameElement.count() > 0 && userNameElement.isVisible()) {
                    String userName = userNameElement.textContent().trim();
                    if (!userName.isEmpty() && !userName.equals("未登陆") && !userName.equals("未登录")) {
                        log.debug("✅ [Gitee AI] 已登录，用户: {}", userName);
                        return userName;
                    }
                }
            } catch (Exception e) {
                log.trace("获取用户名异常: {}", e.getMessage());
            }
            
            // 策略4：检测用户头像（已登录的标志）
            try {
                Locator userAvatar = page.locator(".user-avatar, .avatar, [class*='avatar']").first();
                if (userAvatar.count() > 0 && userAvatar.isVisible()) {
                    log.debug("✅ [Gitee AI] 已登录（检测到头像）");
                    return "Gitee用户";
                }
            } catch (Exception e) {
                log.trace("检测用户头像异常: {}", e.getMessage());
            }
            
            // 🔍 调试：输出页面结构帮助定位正确的选择器
            try {
                String bodyHtml = page.locator("body").innerHTML();
                log.warn("⚠️ [Gitee AI] 无法确定登录状态，默认返回未登录（URL: {}）", currentUrl);
                log.debug("📄 [Gitee AI 调试] 页面 body 前 2000 字符：\n{}", 
                    bodyHtml.length() > 2000 ? bodyHtml.substring(0, 2000) : bodyHtml);
            } catch (Exception e) {
                log.warn("⚠️ [Gitee AI] 无法确定登录状态，默认返回未登录（URL: {}）", currentUrl);
            }
            return "false";
            
        } catch (Exception e) {
            log.error("❌ [Gitee AI] 登录状态检测失败: {}", e.getMessage());
            return "false";
        }
    }

    /**
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * 功能2：导航到 Gitee AI Chat 登录页
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * 
     * @param page Playwright 页面对象
     * @return 是否导航成功
     */
    public boolean navigateToLoginPage(Page page) {
        try {
            log.info("📍 [Gitee AI] 开始导航到登录页");
            
            page.navigate(GITEE_AI_LOGIN_URL, new Page.NavigateOptions().setTimeout(15000));
            page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(15000));
            page.waitForTimeout(2000);
            
            log.info("✅ [Gitee AI] 登录页加载完成");
            return true;
            
        } catch (Exception e) {
            log.error("❌ [Gitee AI] 导航到登录页失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * 功能3：发送消息并等待 AI 回复
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * 
     * @param page Playwright 页面对象
     * @param query 用户问题
     * @return AI 回复内容
     */
    public String sendMessageAndWaitResponse(Page page, String query) {
        try {
            log.info("💬 [Gitee AI] 开始发送消息: {}", query);
            
            /**
             * 🔥 消息发送流程（原型实现）
             * 
             * 1. 找到输入框
             * 2. 填入问题
             * 3. 点击发送按钮
             * 4. 等待 AI 回复
             * 5. 提取回复内容
             * 
             * ⚠️ 实际选择器需要根据真实页面调整
             */
            
            // 步骤1：定位输入框
            Locator inputBox = page.locator("textarea, input[type='text']").first();
            if (inputBox.count() == 0) {
                log.error("❌ [Gitee AI] 未找到输入框");
                return null;
            }
            
            // 步骤2：清空并填入问题
            inputBox.click();
            inputBox.fill("");  // 清空
            page.waitForTimeout(500);
            inputBox.fill(query);
            page.waitForTimeout(500);
            
            log.debug("✅ [Gitee AI] 问题已填入输入框");
            
            // 步骤3：点击发送按钮
            // 🔥 TODO: 根据实际页面调整选择器
            try {
                Locator sendButton = page.locator("button:has-text('发送'), button[type='submit'], button:has-text('Send')").first();
                if (sendButton.count() > 0 && sendButton.isVisible()) {
                    sendButton.click();
                    log.debug("✅ [Gitee AI] 发送按钮已点击");
                } else {
                    // 如果没有发送按钮，尝试按 Enter 键
                    inputBox.press("Enter");
                    log.debug("✅ [Gitee AI] 已按 Enter 键发送");
                }
            } catch (Exception e) {
                log.warn("点击发送按钮失败，尝试按 Enter: {}", e.getMessage());
                inputBox.press("Enter");
            }
            
            page.waitForTimeout(2000);
            
            // 步骤4：等待 AI 回复
            log.debug("⏳ [Gitee AI] 等待 AI 回复...");
            
            /**
             * 🔥 等待策略（原型实现）
             * 
             * 由于不确定具体的加载指示器，采用简单的轮询策略：
             * 1. 等待一段时间让 AI 开始生成
             * 2. 检测回复内容是否出现
             * 3. 等待回复完成（通过检测"停止生成"按钮消失或"重新生成"按钮出现）
             */
            
            // 等待 AI 开始响应（增加等待时间）
            log.debug("⏳ [Gitee AI] 等待5秒让AI开始生成回复...");
            page.waitForTimeout(5000);
            
            // 轮询检测回复完成（最多等待60秒）
            int maxAttempts = 60;
            boolean responseComplete = false;
            
            log.debug("🔍 [Gitee AI] 开始检测回复是否完成...");
            for (int i = 0; i < maxAttempts; i++) {
                try {
                    // 检测"停止生成"按钮是否消失（表示生成完成）
                    Locator stopButton = page.locator("button:has-text('停止'), button:has-text('Stop')");
                    if (stopButton.count() == 0 || !stopButton.first().isVisible()) {
                        responseComplete = true;
                        log.debug("✅ [Gitee AI] 检测到回复已完成（停止按钮消失）");
                        break;
                    }
                    if (i % 5 == 0) {
                        log.debug("⏳ [Gitee AI] 等待回复完成... ({}/{}秒)", i, maxAttempts);
                    }
                } catch (Exception e) {
                    // 忽略检测异常
                }
                
                page.waitForTimeout(1000);
            }
            
            if (!responseComplete) {
                log.warn("⚠️ [Gitee AI] 等待回复超时，尝试提取当前内容");
            }
            
            // 步骤5：提取 AI 回复内容
            log.debug("📝 [Gitee AI] 开始提取回复内容");
            
            /**
             * 🔥 内容提取策略（原型实现）
             * 
             * 尝试多种可能的选择器：
             * 1. 最后一条消息
             * 2. AI 回复区域
             * 3. 消息内容容器
             * 
             * ⚠️ 需要根据实际页面结构调整
             */
            
            String aiResponse = null;
            
            // 策略1：通过 Gitee AI Chat 特有的 prose 容器（根据实际页面结构）
            try {
                log.debug("🔍 [策略1] 尝试通过 prose 容器提取...");
                Locator proseContainer = page.locator(".n-prose, .prose-borderless, [class*='prose']").last();
                int count = proseContainer.count();
                log.debug("   找到 {} 个 prose 容器", count);
                if (count > 0) {
                    aiResponse = proseContainer.textContent().trim();
                    log.debug("✅ [Gitee AI] 通过 prose 容器提取到回复，长度: {}", aiResponse.length());
                } else {
                    log.debug("   未找到 prose 容器");
                }
            } catch (Exception e) {
                log.debug("❌ [策略1] 失败: {}", e.getMessage());
            }
            
            // 策略2：通过 sipplebar-content-wrapper
            if (aiResponse == null || aiResponse.isEmpty()) {
                try {
                    log.debug("🔍 [策略2] 尝试通过 content-wrapper 提取...");
                    Locator contentWrapper = page.locator(".sipplebar-content-wrapper").last();
                    int count = contentWrapper.count();
                    log.debug("   找到 {} 个 content-wrapper", count);
                    if (count > 0) {
                        aiResponse = contentWrapper.textContent().trim();
                        log.debug("✅ [Gitee AI] 通过 content-wrapper 提取到回复，长度: {}", aiResponse.length());
                    } else {
                        log.debug("   未找到 content-wrapper");
                    }
                } catch (Exception e) {
                    log.debug("❌ [策略2] 失败: {}", e.getMessage());
                }
            }
            
            // 策略3：获取最后一条消息
            if (aiResponse == null || aiResponse.isEmpty()) {
                try {
                    Locator messages = page.locator(".message, .chat-message, [class*='message']");
                    if (messages.count() > 0) {
                        Locator lastMessage = messages.last();
                        aiResponse = lastMessage.textContent().trim();
                        log.debug("✅ [Gitee AI] 通过消息列表提取到回复");
                    }
                } catch (Exception e) {
                    log.trace("策略3（消息列表）失败: {}", e.getMessage());
                }
            }
            
            // 策略4：通过 AI 回复区域
            if (aiResponse == null || aiResponse.isEmpty()) {
                try {
                    Locator aiReply = page.locator(".ai-response, .assistant-message, [class*='assistant']").last();
                    if (aiReply.count() > 0) {
                        aiResponse = aiReply.textContent().trim();
                        log.debug("✅ [Gitee AI] 通过 AI 回复区域提取到回复");
                    }
                } catch (Exception e) {
                    log.trace("策略4（AI回复区域）失败: {}", e.getMessage());
                }
            }
            
            if (aiResponse != null && !aiResponse.isEmpty()) {
                log.info("✅ [Gitee AI] 成功获取 AI 回复，长度: {}", aiResponse.length());
                return aiResponse;
            } else {
                log.error("❌ [Gitee AI] 未能提取到有效回复");
                
                // 🔍 调试：输出页面结构帮助定位选择器
                try {
                    log.debug("🔍 [调试] 开始分析页面结构...");
                    
                    // 输出最后 10 个 div 的 class
                    String divClasses = page.evaluate(
                        "Array.from(document.querySelectorAll('div[class]')).slice(-10).map((el, i) => `${i}: ${el.className}`).join('\\n')"
                    ).toString();
                    log.debug("📄 [调试] 最后 10 个 div 的 class:\n{}", divClasses);
                    
                    // 输出最后 5 个有文本的叶子元素
                    String textElements = page.evaluate(
                        "Array.from(document.querySelectorAll('*')).filter(el => el.childElementCount === 0 && el.textContent.trim().length > 10).slice(-5).map(el => `${el.tagName}.${el.className}: ${el.textContent.trim().substring(0, 80)}`).join('\\n\\n')"
                    ).toString();
                    log.debug("📝 [调试] 最后 5 个有文本的叶子元素:\n{}", textElements);
                    
                } catch (Exception debugEx) {
                    log.warn("调试信息获取失败: {}", debugEx.getMessage());
                }
                
                return null;
            }
            
        } catch (Exception e) {
            log.error("❌ [Gitee AI] 发送消息失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * 辅助方法：清理文本内容
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     */
    private String cleanText(String text) {
        if (text == null) return "";
        return text.replaceAll("\\s+", " ").trim();
    }
}

