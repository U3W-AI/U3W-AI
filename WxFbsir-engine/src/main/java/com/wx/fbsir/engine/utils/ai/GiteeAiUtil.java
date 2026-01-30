package com.wx.fbsir.engine.utils.ai;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.HashMap;

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
 * @author 郝少聪
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
//                log.debug("📄 [Gitee AI 调试] 页面 body 前 2000 字符：\n{}",
//                    bodyHtml.length() > 2000 ? bodyHtml.substring(0, 2000) : bodyHtml);
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
            
            log.info("✅ [Gitee AI] Gitee AI主页加载完成，开始寻找并点击「未登陆」入口");
            
            // 定位「未登陆」入口
            Locator loginEntry = page.getByText("未登陆");
            
            loginEntry.click();
            log.info("✅ [Gitee AI] 已点击「未登陆」入口，等待登录界面加载");
            
            // 等待登录相关界面加载
            page.waitForTimeout(2000);
            
            // 点击微信登录按钮
            try {
                log.info("📍 [Gitee AI] 开始寻找并点击微信登录按钮");
                
                // 步骤1：先找到并点击展开下拉菜单的按钮
                Locator dropdownButton = page.locator(".session-oauth_other").first();
                if (dropdownButton.count() > 0) {
                    dropdownButton.click();
                    log.info("✅ [Gitee AI] 已点击展开下拉菜单");
                    page.waitForTimeout(1000); // 等待菜单展开
                }
                
                // 步骤2：定位并点击微信登录链接
                // 直接定位并点击指定的微信登录链接
                Locator wechatLoginLink = page.locator("a[href='https://gitee.com/auth/wechat']").first();
                if (wechatLoginLink.count() > 0) {
                    wechatLoginLink.click();
                    log.info("✅ [Gitee AI] 已点击微信登录链接: https://gitee.com/auth/wechat");
                }
                // 备用方案：通过href包含wechat的链接
                else {
                    Locator fallbackWechatLink = page.locator("a[href*='wechat']").first();
                    if (fallbackWechatLink.count() > 0) {
                        fallbackWechatLink.click();
                        log.info("✅ [Gitee AI] 已点击微信登录链接（备用方案）");
                    } else {
                        log.warn("⚠️ [Gitee AI] 未找到微信登录链接");
                    }
                }
                
                // 等待微信登录页面加载
                log.info("⏳ [Gitee AI] 等待微信登录页面加载");
                page.waitForTimeout(3000);
                
            } catch (Exception e) {
                log.warn("⚠️ [Gitee AI] 点击微信登录按钮失败: {}", e.getMessage());
                // 继续执行，不影响整体流程
            }
            
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
    // 方法重载：保持向后兼容性
    public String sendMessageAndWaitResponse(Page page, String query) {
        return sendMessageAndWaitResponse(page, query, false, false);
    }
    
    public String sendMessageAndWaitResponse(Page page, String query, boolean enableOpenSourceExploration, boolean enableHelpCenter) {
        try {
            log.info("💬 [Gitee AI] 开始发送消息: {}", query);
            
            // 步骤1：定位输入框
            Locator inputBox = page.locator("textarea, input[type='text']").first();
            if (inputBox.count() == 0) {
                log.error("❌ [Gitee AI] 未找到输入框");
                return null;
            }
            
            // 步骤2：切换模式（通过点击按钮）
            try {
                if (enableOpenSourceExploration) {
                    toggleGiteeMode(page, "开源探索", true);
                } else if (enableHelpCenter) {
                    toggleGiteeMode(page, "帮助中心", true);
                }
                // 注意：不选择模式时，不强制关闭现有模式，让用户保持当前设置
            } catch (Exception e) {
                log.warn("[Gitee AI] 模式切换失败，继续发送消息: {}", e.getMessage());
            }
            
            // 步骤3：清空并填入问题
            inputBox.click();
            inputBox.fill("");  // 清空
            page.waitForTimeout(500);
            inputBox.fill(query);
            page.waitForTimeout(500);
            
            log.debug("✅ [Gitee AI] 问题已填入输入框");
            
            // 步骤3：点击发送按钮
            try {
                Locator sendButton = page.locator("button:has-text('发送'), button[type='submit'], button:has-text('Send')").first();
                if (sendButton.count() > 0 && sendButton.isVisible()) {
                    sendButton.click();
                    log.debug("✅ [Gitee AI] 发送按钮已点击");
                } else {
                    inputBox.press("Enter");
                    log.debug("✅ [Gitee AI] 已按 Enter 键发送");
                }
            } catch (Exception e) {
                log.warn("点击发送按钮失败，尝试按 Enter: {}", e.getMessage());
                inputBox.press("Enter");
            }
            
            page.waitForTimeout(2000);
            
            // 步骤4：等待 AI 回复（使用更健壮的检测逻辑，参考 DeepSeek）
            log.info("⏳ [Gitee AI] 等待 AI 回复...");
            
            // 🔥 改进的等待逻辑：持续检测内容稳定性
            String currentContent = "";
            String lastContent = "";
            int stableCount = 0;
            int noChangeCount = 0;
            int[] contentLengthHistory = new int[3];
            boolean hasEverHadContent = false;
            
            long startTime = System.currentTimeMillis();
            long maxTimeout = 120000; // 2分钟超时
            int requiredStableCount = 3; // 需要连续3次检测到内容不变
            int checkInterval = 500; // 每500ms检查一次
            
            page.waitForTimeout(3000); // 等待AI开始生成
            log.info("[Gitee AI] 开始检测回复完成状态");
            
            while (true) {
                if (page.isClosed()) {
                    log.error("[Gitee AI] 页面已关闭，停止监听");
                    throw new RuntimeException("页面在监控过程中被关闭");
                }
                
                long elapsedTime = System.currentTimeMillis() - startTime;
                if (elapsedTime > maxTimeout) {
                    log.warn("[Gitee AI] 超时，AI未完成回答或回答时间过长");
                    break;
                }
                
                try {
                    // 提取当前内容
                    Locator proseContainer = page.locator(".n-prose, .prose-borderless, [class*='prose']").last();
                    if (proseContainer.count() > 0) {
                        currentContent = proseContainer.textContent().trim();
                    }
                    
                    int contentLength = currentContent.length();
                    
                    if (contentLength > 0) {
                        hasEverHadContent = true;
                        
                        // 记录内容长度历史
                        for (int i = contentLengthHistory.length - 1; i > 0; i--) {
                            contentLengthHistory[i] = contentLengthHistory[i-1];
                        }
                        contentLengthHistory[0] = contentLength;
                        
                        // 检测内容是否稳定
                        if (currentContent.equals(lastContent)) {
                            stableCount++;
                            noChangeCount++;
                            
                            // 检查是否还在生成（通过检测是否有加载指示器）
                            boolean isGenerating = checkIfGenerating(page);
                            
                            if (!isGenerating && stableCount >= requiredStableCount) {
                                log.info("[Gitee AI] 内容已稳定{}次，回复已完成", stableCount);
                                break;
                            } else if (isGenerating) {
                                log.debug("[Gitee AI] 检测到正在生成，继续等待... (稳定次数: {})", stableCount);
                            }
                        } else {
                            // 内容发生变化，重置计数器
                            if (lastContent.length() > 0) {
                                log.debug("[Gitee AI] 内容发生变化，长度: {} -> {}", lastContent.length(), contentLength);
                            }
                            stableCount = 0;
                            noChangeCount = 0;
                        }
                        
                        lastContent = currentContent;
                    } else {
                        if (hasEverHadContent) {
                            log.debug("[Gitee AI] 内容为空，但之前有内容，继续等待...");
                        }
                    }
                    
                    // 每10秒输出一次进度
                    if (elapsedTime % 10000 < checkInterval) {
                        log.info("[Gitee AI] 等待中... 已等待{}秒，当前内容长度: {}", elapsedTime / 1000, contentLength);
                    }
                    
                } catch (Exception e) {
                    log.trace("[Gitee AI] 检测回复状态异常（忽略）: {}", e.getMessage());
                }
                
                page.waitForTimeout(checkInterval);
            }
            
            log.info("[Gitee AI] AI回复检测完成，准备提取内容");
            page.waitForTimeout(1000); // 等待内容稳定
            
            // 步骤5：提取 AI 回复内容（参考 DeepSeek 的格式化处理）
            log.info("📝 [Gitee AI] 开始提取回复内容");
            
            String aiResponse = extractGiteeResponse(page);
            
            if (aiResponse != null && !aiResponse.isEmpty()) {
                log.info("✅ [Gitee AI] 成功获取 AI 回复，长度: {}", aiResponse.length());
                return aiResponse;
            } else {
                log.error("❌ [Gitee AI] 未能提取到有效回复");
                return null;
            }
            
        } catch (Exception e) {
            log.error("❌ [Gitee AI] 发送消息失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * 功能：提取 Gitee AI 回复内容（参考 DeepSeek 的格式化处理）
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * 
     * @param page Playwright页面对象
     * @return 格式化后的回复内容
     */
    private String extractGiteeResponse(Page page) {
        try {
            log.debug("[Gitee AI] 开始提取格式化的回复内容");
            
            Object jsResult = page.evaluate("""
            () => {
                try {
                    // 策略1：通过 prose 容器提取（优先）
                    const proseContainers = document.querySelectorAll('.n-prose, .prose-borderless, [class*="prose"]');
                    if (proseContainers.length === 0) {
                        return { content: '', source: 'no-prose-containers' };
                    }
                    
                    const latestContainer = proseContainers[proseContainers.length - 1];
                    const containerClone = latestContainer.cloneNode(true);
                    
                    // 移除不需要的元素
                    const elementsToRemove = containerClone.querySelectorAll(
                        'svg, button, [role="button"], ' +
                        '[class*="loading"], [class*="typing"], [class*="cursor"], ' +
                        '[class*="spinner"], [class*="icon"], ' +
                        '.n-spin, .n-loading, .loading-indicator'
                    );
                    elementsToRemove.forEach(el => el.remove());
                    
                    // 移除空的 div
                    const emptyDivs = containerClone.querySelectorAll('div:empty, span:empty, p:empty');
                    emptyDivs.forEach(div => div.remove());
                    
                    // 清理多余的空白
                    const cleanedContent = containerClone.innerHTML
                        .replace(/\\s+/g, ' ')
                        .replace(/<p>\\s*<\\/p>/g, '')
                        .replace(/<div>\\s*<\\/div>/g, '')
                        .trim();
                    
                    return {
                        content: cleanedContent,
                        source: 'prose-container-cleaned',
                        timestamp: Date.now()
                    };
                } catch (e) {
                    return {
                        content: '',
                        source: 'error',
                        error: e.toString()
                    };
                }
            }
            """);

            if (jsResult instanceof Map) {
                Map<String, Object> result = (Map<String, Object>) jsResult;
                String content = (String) result.getOrDefault("content", "");
                String source = (String) result.getOrDefault("source", "");
                
                if (!content.trim().isEmpty()) {
                    log.debug("[Gitee AI] 成功提取格式化内容，来源: {}", source);
                    return content;
                }
            }
            
            log.warn("[Gitee AI] 策略1失败，尝试备用策略");
            
            // 策略2：通过 content-wrapper 提取
            Object fallbackResult = page.evaluate("""
            () => {
                try {
                    const wrappers = document.querySelectorAll('.sipplebar-content-wrapper');
                    if (wrappers.length === 0) {
                        return { content: '', source: 'no-wrappers' };
                    }
                    
                    const latestWrapper = wrappers[wrappers.length - 1];
                    const wrapperClone = latestWrapper.cloneNode(true);
                    
                    // 移除不需要的元素
                    const elementsToRemove = wrapperClone.querySelectorAll(
                        'svg, button, [role="button"], ' +
                        '[class*="loading"], [class*="typing"], [class*="cursor"]'
                    );
                    elementsToRemove.forEach(el => el.remove());
                    
                    const cleanedContent = wrapperClone.innerHTML
                        .replace(/\\s+/g, ' ')
                        .trim();
                    
                    return {
                        content: cleanedContent,
                        source: 'wrapper-cleaned'
                    };
                } catch (e) {
                    return {
                        content: '',
                        source: 'error',
                        error: e.toString()
                    };
                }
            }
            """);
            
            if (fallbackResult instanceof Map) {
                Map<String, Object> result = (Map<String, Object>) fallbackResult;
                String content = (String) result.getOrDefault("content", "");
                String source = (String) result.getOrDefault("source", "");
                
                if (!content.trim().isEmpty()) {
                    log.debug("[Gitee AI] 备用策略成功，来源: {}", source);
                    return content;
                }
            }
            
            return "";
            
        } catch (Exception e) {
            log.error("[Gitee AI] 提取回复内容失败", e);
            return "";
        }
    }

    /**
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * 辅助方法：切换Gitee AI Chat模式（开源探索/帮助中心）
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     */
    private void toggleGiteeMode(Page page, String modeText, boolean shouldActive) {
        try {
            log.debug("[Gitee AI] 开始切换{}模式，目标状态: {}", modeText, shouldActive);
            
            // 第一步：找到"切换人设"按钮
            // 使用更精确的选择器，基于实际的class
            String toggleButtonSelector = ".ant-dropdown-trigger:has-text('切换人设')";
            Locator toggleButton = page.locator(toggleButtonSelector).first();
            
            try {
                // 等待"切换人设"按钮出现，最多等待5秒
                toggleButton.waitFor(new Locator.WaitForOptions().setTimeout(5000));
                log.debug("[Gitee AI] 找到切换人设按钮");
            } catch (Exception e) {
                log.debug("[Gitee AI] 切换人设按钮未找到: {}", e.getMessage());
                return;
            }

            if (!toggleButton.isVisible()) {
                log.debug("[Gitee AI] 切换人设按钮不可见，跳过{}模式切换", modeText);
                return;
            }

            // 第二步：点击"切换人设"按钮展开下拉菜单
            // 先获取按钮的class，检查点击前状态
            String buttonClassBefore = (String) toggleButton.evaluate("el => el.className");
            log.debug("[Gitee AI] 切换人设按钮点击前class: {}", buttonClassBefore);
            
            // 尝试多种方式触发下拉菜单
            try {
                // 方法1：尝试点击div内部的文本元素
                try {
                    Locator textElement = toggleButton.locator("text='切换人设'");
                    if (textElement.count() > 0) {
                        log.debug("[Gitee AI] 找到div内部的文本元素，尝试点击");
                        textElement.first().click(new Locator.ClickOptions().setTimeout(5000).setForce(true));
                    } else {
                        // 方法2：直接点击div
                        log.debug("[Gitee AI] 未找到内部文本元素，直接点击div");
                        toggleButton.click(new Locator.ClickOptions().setTimeout(5000).setForce(true));
                    }
                } catch (Exception e) {
                    log.debug("[Gitee AI] 点击异常: {}", e.getMessage());
                    // 方法3：使用JavaScript点击
                    toggleButton.evaluate("el => el.click()");
                }
            } catch (Exception e) {
                log.debug("[Gitee AI] 触发下拉菜单异常: {}", e.getMessage());
            }
            
            log.debug("[Gitee AI] 等待下拉菜单展开");
            page.waitForTimeout(2000); // 增加等待时间，确保菜单完全展开
            
            // 检查按钮点击后的状态
            String buttonClassAfter = (String) toggleButton.evaluate("el => el.className");
            log.debug("[Gitee AI] 切换人设按钮点击后class: {}", buttonClassAfter);

            // 第三步：在下拉菜单中找到目标模式选项
            // 先检查下拉菜单是否真的展开了
            Locator menuItem = null;
            boolean found = false;
            
            try {
                // 查找所有下拉菜单容器
                Locator dropdownMenus = page.locator(".ant-dropdown, [class*='dropdown-menu'], [role='menu']");
                int menuCount = dropdownMenus.count();

                
                if (menuCount == 0) {
                    log.warn("[Gitee AI] 未找到下拉菜单容器，可能菜单未展开");
                    // 尝试再次点击按钮
                    toggleButton.click(new Locator.ClickOptions().setTimeout(2000).setForce(true));
                    page.waitForTimeout(1000);
                }
                
                // 查找所有菜单项
                Locator allMenuItems = page.locator(".ant-dropdown-menu-item, [class*='dropdown-menu-item'], [class*='menu-item'], [role='menuitem']");
                int count = allMenuItems.count();
                
                // 如果还是没找到，尝试查找所有可见的元素
                if (count == 0) {
                    Locator allVisibleElements = page.locator("*:visible");
                    int visibleCount = allVisibleElements.count();
                    
                    // 查找包含目标文本的元素
                    for (int i = 0; i < visibleCount; i++) {
                        try {
                            Locator element = allVisibleElements.nth(i);
                            String text = element.textContent();
                            if (text != null && text.contains(modeText)) {
                                break;
                            }
                        } catch (Exception e) {
                            // 忽略异常
                        }
                    }
                }
                
                // 遍历菜单项，查找包含目标文本的项
                Locator targetMenuItem = null;
                boolean isCurrentMode = false;
                for (int i = 0; i < count; i++) {
                    try {
                        Locator currentMenuItem = allMenuItems.nth(i);
                        String textContent = currentMenuItem.textContent();
                        String className = (String) currentMenuItem.evaluate("el => el.className");
                        
                        if (textContent.contains(modeText)) {
                            targetMenuItem = currentMenuItem;
                            
                            // 检查是否是当前激活的模式（通过class或其他属性）
                            if (className.contains("active") || className.contains("selected") || className.contains("current") || className.contains("checked")) {
                                isCurrentMode = true;
                                log.debug("[Gitee AI] {}已经是当前激活的模式，无需切换", modeText);
                            }
                            break;
                        }
                    } catch (Exception e) {
                        // 忽略异常
                    }
                }
                
                // 如果已经是当前模式，直接关闭菜单并返回
                if (isCurrentMode) {
                    log.debug("[Gitee AI] 无需切换模式，关闭下拉菜单");
                    // 关闭下拉菜单
                    try {
                        toggleButton.click(new Locator.ClickOptions().setTimeout(2000).setForce(true));
                        page.waitForTimeout(500);
                    } catch (Exception e) {
                        // 忽略关闭失败的异常
                    }
                    return;
                }
                
                if (targetMenuItem != null && targetMenuItem.isVisible()) {
                    // 找到目标菜单项，使用与切换人设按钮相同的多策略点击方法
                    menuItem = targetMenuItem;
                    found = true;
                    
                    // 使用与切换人设按钮相同的多策略点击方法
                    try {
                        // 方法1：尝试点击菜单项内的文本元素
                        Locator menuItemText = menuItem.locator(String.format("text='%s'", modeText));
                        if (menuItemText.count() > 0) {
                            log.debug("[Gitee AI] 找到{}菜单项文本元素，点击文本", modeText);
                            menuItemText.first().click(new Locator.ClickOptions().setTimeout(5000).setForce(true));
                        } else {
                            // 方法2：直接点击菜单项
                            log.debug("[Gitee AI] 未找到{}菜单项文本元素，直接点击菜单项", modeText);
                            menuItem.click(new Locator.ClickOptions().setTimeout(5000).setForce(true));
                        }
                    } catch (Exception e) {
                        log.debug("[Gitee AI] 点击{}菜单项异常: {}", modeText, e.getMessage());
                        // 方法3：使用JavaScript点击
                        menuItem.evaluate("el => el.click()");
                    }
                    
                    log.info("[Gitee AI] 已点击{}模式菜单项", modeText);
                    page.waitForTimeout(1000); // 增加等待时间，确保模式切换完成
                } else {
                    // 如果没找到，尝试其他选择器策略
                    log.debug("[Gitee AI] 未在菜单项中找到{}，尝试其他选择器", modeText);
                }
            } catch (Exception e) {
                log.debug("[Gitee AI] 查找菜单项失败: {}", e.getMessage());
            }
            
            // 如果上面的方法没找到，尝试使用选择器
            if (!found) {
                String[] menuItemSelectors = {
                    String.format(".ant-dropdown-trigger:has-text('%s')", modeText),
                    String.format(".ant-dropdown-menu-item:has-text('%s')", modeText),
                    String.format(".ant-dropdown-menu > *:has-text('%s')", modeText),
                    String.format("li:has-text('%s')", modeText),
                    String.format("span:has-text('%s')", modeText),
                    String.format("div:has-text('%s')", modeText),
                    String.format("[class*='dropdown'] *:has-text('%s')", modeText),
                    String.format("*:has-text('%s')", modeText)
                };
                
                for (String selector : menuItemSelectors) {
                    try {
                        log.debug("[Gitee AI] 尝试选择器: {}", selector);
                        Locator currentLocator = page.locator(selector).first();
                        
                        // 快速检查元素是否存在，不使用waitFor以节省时间
                        if (currentLocator.count() > 0) {
                            log.debug("[Gitee AI] 找到元素，检查可见性");
                            if (currentLocator.isVisible()) {
                                log.debug("[Gitee AI] 元素可见，使用此选择器");
                                menuItem = currentLocator;
                                found = true;
                                break;
                            }
                        }
                    } catch (Exception ex) {
                        log.debug("[Gitee AI] 选择器{}尝试失败: {}", selector, ex.getMessage());
                    }
                }
            }
            
            // 如果通过其他选择器策略找到了元素，执行点击
            if (found && menuItem != null && menuItem.isVisible()) {
                // 使用与切换人设按钮相同的多策略点击方法
                try {
                    // 方法1：尝试点击菜单项内的文本元素
                    Locator menuItemText = menuItem.locator(String.format("text='%s'", modeText));
                    if (menuItemText.count() > 0) {
                        log.debug("[Gitee AI] 找到{}菜单项文本元素，点击文本", modeText);
                        menuItemText.first().click(new Locator.ClickOptions().setTimeout(5000).setForce(true));
                    } else {
                        // 方法2：直接点击菜单项
                        log.debug("[Gitee AI] 未找到{}菜单项文本元素，直接点击菜单项", modeText);
                        menuItem.click(new Locator.ClickOptions().setTimeout(5000).setForce(true));
                    }
                } catch (Exception e) {
                    log.debug("[Gitee AI] 点击{}菜单项异常: {}", modeText, e.getMessage());
                    // 方法3：使用JavaScript点击
                    menuItem.evaluate("el => el.click()");
                }
                
                log.info("[Gitee AI] 已通过其他选择器点击{}模式菜单项", modeText);
                page.waitForTimeout(1000); // 增加等待时间，确保模式切换完成
            }
            
            // 关闭下拉菜单
            try {
                if (toggleButton != null && toggleButton.isVisible()) {
                    toggleButton.click(new Locator.ClickOptions().setTimeout(2000).setForce(true));
                    log.debug("[Gitee AI] 已关闭下拉菜单");
                    page.waitForTimeout(500); // 等待菜单关闭
                }
            } catch (Exception e) {
                // 忽略关闭失败的异常
                log.debug("[Gitee AI] 关闭下拉菜单失败: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.warn("[Gitee AI] 切换{}模式失败: {}", modeText, e.getMessage());
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

    /**
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * 功能：检查是否仍在生成内容
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * 
     * @param page Playwright页面对象
     * @return true表示正在生成，false表示已停止
     */
    private boolean checkIfGenerating(Page page) {
        try {
            Object generatingStatus = page.evaluate("""
            () => {
                try {
                    // 检测Gitee AI特有的加载指示器
                    const thinkingIndicators = document.querySelectorAll(
                        '.n-spin, .n-loading, .loading-indicator, .thinking-indicator, ' +
                        '[class*="loading"], [class*="typing"], [class*="generating"], ' +
                        '.cursor-blink, .typing-indicator'
                    );
                    
                    for (const indicator of thinkingIndicators) {
                        if (indicator && 
                            window.getComputedStyle(indicator).display !== 'none' && 
                            window.getComputedStyle(indicator).visibility !== 'hidden') {
                            return true;
                        }
                    }
                    
                    // 检测停止生成按钮
                    const stopButtons = document.querySelectorAll(
                        'button:has-text("停止"), button:has-text("Stop"), ' +
                        '[title*="停止"], [title*="Stop"], ' +
                        '.stop-generating-button, [class*="stop"]'
                    );
                    
                    for (const btn of stopButtons) {
                        if (btn && 
                            window.getComputedStyle(btn).display !== 'none' && 
                            window.getComputedStyle(btn).visibility !== 'hidden') {
                            return true;
                        }
                    }
                    
                    // 检测光标闪烁（正在输入的标志）
                    const cursors = document.querySelectorAll(
                        '.cursor, [class*="cursor"], .typing-cursor'
                    );
                    
                    for (const cursor of cursors) {
                        if (cursor && 
                            window.getComputedStyle(cursor).display !== 'none' && 
                            window.getComputedStyle(cursor).visibility !== 'hidden') {
                            return true;
                        }
                    }
                    
                    return false;
                } catch (e) {
                    console.error('检查生成状态时出错:', e);
                    return false;
                }
            }
            """);

            return generatingStatus instanceof Boolean ? (Boolean) generatingStatus : false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * 功能：提取当前会话ID
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * 
     * @param page Playwright页面对象
     * @return 会话ID，如果无法提取则返回null
     */
    public String extractChatId(Page page) {
        try {
            log.info("[Gitee AI] ========== 开始提取会话ID ==========");
            
            // 🔥 关键：等待 URL 更新（发送消息后，URL 可能需要时间更新）
            log.info("[Gitee AI] 等待 URL 更新...");
            page.waitForTimeout(2000);
            
            // 步骤1：从URL中提取会话ID（最快、最可靠）
            try {
                String currentUrl = page.url();
                log.info("[Gitee AI] 当前URL: {}", currentUrl);
                
                // Gitee AI Chat 的实际会话URL格式：https://chat.gitee.com/c/{chatId}
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("/c/([^/?#]+)");
                java.util.regex.Matcher matcher = pattern.matcher(currentUrl);
                if (matcher.find()) {
                    String chatId = matcher.group(1);
                    log.info("[Gitee AI] ✅ 从URL提取到会话ID: {}", chatId);
                    return chatId;
                } else {
                    log.warn("[Gitee AI] ⚠️ URL中未找到 /c/ 格式的会话ID");
                }
            } catch (Exception e) {
                log.error("[Gitee AI] ❌ 从URL提取会话ID失败: {}", e.getMessage(), e);
            }
            
            // 步骤2：尝试使用evaluate方法提取（轻量级）
            try {
                log.info("[Gitee AI] 尝试通过JavaScript获取URL...");
                String currentUrl = (String) page.evaluate("() => window.location.href");
                log.info("[Gitee AI] JavaScript获取的URL: {}", currentUrl);
                
                if (currentUrl != null) {
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("/c/([^/?#]+)");
                    java.util.regex.Matcher matcher = pattern.matcher(currentUrl);
                    if (matcher.find()) {
                        String chatId = matcher.group(1);
                        log.info("[Gitee AI] ✅ 从JavaScript提取到会话ID: {}", chatId);
                        return chatId;
                    } else {
                        log.warn("[Gitee AI] ⚠️ JavaScript获取的URL中未找到 /c/ 格式的会话ID");
                    }
                }
            } catch (Exception e) {
                log.error("[Gitee AI] ❌ 从JavaScript提取会话ID失败: {}", e.getMessage(), e);
            }
            
            // 步骤3：尝试从localStorage提取（备选方案）
            try {
                log.info("[Gitee AI] 尝试从localStorage提取...");
                String chatId = (String) page.evaluate("() => localStorage.getItem('chatId')");
                if (chatId != null && !chatId.isEmpty()) {
                    log.info("[Gitee AI] ✅ 从localStorage提取到会话ID: {}", chatId);
                    return chatId;
                } else {
                    log.warn("[Gitee AI] ⚠️ localStorage中没有chatId");
                }
            } catch (Exception e) {
                log.error("[Gitee AI] ❌ 从localStorage提取会话ID失败: {}", e.getMessage(), e);
            }
            
            // 步骤4：尝试从页面中查找会话ID（作为最后的备选方案）
            try {
                log.info("[Gitee AI] 尝试从页面元素中查找...");
                String chatId = (String) page.evaluate("function() { try { const links = document.querySelectorAll('a'); for (let link of links) { const href = link.href; if (href && href.includes('chat.gitee.com/c/')) { const match = href.match(/chat\\.gitee\\.com\\/c\\/([^/?#]+)/); if (match) return match[1]; } } return null; } catch(e) { return null; } }");
                if (chatId != null && !chatId.isEmpty()) {
                    log.info("[Gitee AI] ✅ 从页面元素提取到会话ID: {}", chatId);
                    return chatId;
                } else {
                    log.warn("[Gitee AI] ⚠️ 页面元素中未找到会话ID");
                }
            } catch (Exception e) {
                log.error("[Gitee AI] ❌ 从页面元素提取会话ID失败: {}", e.getMessage(), e);
            }
            
            // 🔥 调试：输出页面结构帮助定位问题
            try {
                log.info("[Gitee AI] ========== 调试信息 ==========");
                log.info("[Gitee AI] 页面标题: {}", page.title());
                
                // 输出所有链接
                String allLinks = (String) page.evaluate("() => Array.from(document.querySelectorAll('a[href*=\"chat.gitee.com/c/\"]')).map(a => a.href).join('\\n')");
                if (allLinks != null && !allLinks.isEmpty()) {
                    log.info("[Gitee AI] 找到的会话链接:\\n{}", allLinks);
                } else {
                    log.warn("[Gitee AI] 未找到任何 chat.gitee.com/c/ 格式的链接");
                }
            } catch (Exception e) {
                log.warn("[Gitee AI] 获取调试信息失败: {}", e.getMessage());
            }
            
            log.error("[Gitee AI] ❌ 所有提取方法都失败，未能提取到会话ID");
            log.info("[Gitee AI] ========== 提取会话ID结束 ==========");
            return null;
            
        } catch (Exception e) {
            log.error("[Gitee AI] ❌ 提取会话ID过程中发生异常", e);
            return null;
        }
    }

    /**
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * 功能：导航到指定会话
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * 
     * @param page Playwright页面对象
     * @param chatId 会话ID
     * @return 是否导航成功
     */
    public boolean navigateToChat(Page page, String chatId) {
        if (chatId == null || chatId.isEmpty()) {
            log.warn("[Gitee AI] 会话ID为空，导航到首页");
            page.navigate(GITEE_AI_HOME_URL, new Page.NavigateOptions().setTimeout(10000));
            return true;
        }
        
        try {
            log.info("[Gitee AI] 导航到会话: {}", chatId);
            // Gitee AI Chat的实际会话URL格式：https://chat.gitee.com/c/{chatId}
            String chatUrl = GITEE_AI_HOME_URL + "c/" + chatId;
            page.navigate(chatUrl, new Page.NavigateOptions()
                .setTimeout(15000)
                .setWaitUntil(WaitUntilState.NETWORKIDLE));
            
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.waitForTimeout(2000);
            
            log.info("[Gitee AI] 成功导航到会话，等待页面稳定");
            return true;
        } catch (Exception e) {
            log.error("[Gitee AI] 导航到会话失败: {}", e.getMessage());
            return false;
        }
    }
}

