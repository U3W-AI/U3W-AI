package com.wx.fbsir.engine.utils.yuanqi;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 元器（YuanQi）登录工具类
 * 
 * 功能说明：
 * 1. 登录状态检测 - 检查左侧菜单是否显示"登录"按钮
 * 2. 导航到登录页面
 * 3. 触发扫码登录
 * 4. 登录状态监测
 * 
 * @author wxfbsir
 * @date 2025-01-06
 */
@Slf4j
@Component
public class YuanQiLoginUtil {

    private static final String YUANQI_HOME_URL = "https://yuanqi.tencent.com/";
    
    /**
     * 检查元器登录状态
     * 
     * 检测逻辑（2026-01-20更新）：
     * - 检查左侧菜单是否存在"新建智能体"按钮
     * - 如果存在该按钮，说明未登录（点击会弹出登录页面）
     * - 如果不存在该按钮，说明已登录
     * 
     * @param page Playwright页面对象
     * @param shouldNavigate 是否先导航到首页（true=导航，false=直接检测当前页面）
     * @return "false"表示未登录，否则返回用户名或"已登录"
     */
    public String checkLoginStatus(Page page, boolean shouldNavigate) {
        try {
            // 如果需要导航，先访问首页
            if (shouldNavigate) {
                log.debug("[元器登录检测] 导航到首页: {}", YUANQI_HOME_URL);
                page.navigate(YUANQI_HOME_URL);
                page.waitForLoadState();
                page.waitForTimeout(3000); // 等待页面完全加载
            }
            
            // ✅ 优先级1: 检查"新建智能体"弹窗（扫码登录成功后的明确标志）
            // DOM: <div class="v-modalDialog__content"><div class="v-page-header__heading__left__title">新建智能体</div>
            Locator createAgentDialog = page.locator(".v-modalDialog__content .v-page-header__heading__left__title:has-text('新建智能体')");
            if (createAgentDialog.count() > 0) {
                try {
                    if (createAgentDialog.first().isVisible()) {
                        log.info("[元器登录检测] 已登录（检测到新建智能体弹窗）");
                        String userName = extractUserName(page);
                        return userName != null ? userName : "已登录";
                    }
                } catch (Exception e) {
                    log.debug("[元器登录检测] 弹窗可见性检查失败，继续其他检测");
                }
            }
            
            // ✅ 优先级2: 检查 .spaceBtn 区域（不管里面文字是什么，有下拉箭头就说明登录了）
            // DOM: <div class="spaceBtn"><span>个人空间</span><svg class="collapse-icon">...
            Locator spaceBtn = page.locator(".spaceBtn");
            if (spaceBtn.count() > 0) {
                try {
                    if (spaceBtn.first().isVisible()) {
                        log.info("[元器登录检测] 已登录（检测到个人空间下拉区域）");
                        String userName = extractUserName(page);
                        return userName != null ? userName : "已登录";
                    }
                } catch (Exception e) {
                    log.debug("[元器登录检测] .spaceBtn 可见性检查失败，继续其他检测");
                }
            }
            
            // ✅ 优先级3: 检查侧边栏是否有"知识库"选项（已登录才有）
            // DOM: <div class="v-collapse-item"><span class="menu-name">知识库</span>
            Locator knowledgeBase = page.locator(".v-collapse-item .menu-name:has-text('知识库')");
            if (knowledgeBase.count() > 0) {
                try {
                    if (knowledgeBase.first().isVisible()) {
                        log.info("[元器登录检测] 已登录（检测到知识库选项）");
                        String userName = extractUserName(page);
                        return userName != null ? userName : "已登录";
                    }
                } catch (Exception e) {
                    log.debug("[元器登录检测] 知识库选项可见性检查失败，继续其他检测");
                }
            }
            
            // ✅ 优先级4: 检查侧边栏是否有"我的智能体"选项（已登录才有）
            // DOM: <div class="v-collapse-item"><span class="menu-name">我的智能体</span>
            Locator myAgents = page.locator(".v-collapse-item .menu-name:has-text('我的智能体')");
            if (myAgents.count() > 0) {
                try {
                    if (myAgents.first().isVisible()) {
                        log.info("[元器登录检测] 已登录（检测到我的智能体选项）");
                        String userName = extractUserName(page);
                        return userName != null ? userName : "已登录";
                    }
                } catch (Exception e) {
                    log.debug("[元器登录检测] 我的智能体选项可见性检查失败，继续其他检测");
                }
            }
            
            // ❌ 最后: 检查是否有且仅有"新建智能体"按钮（无其他登录标志 = 未登录）
            // DOM: <button class="create-agent-button">新建智能体</button>
            Locator createAgentButton = page.locator("button.create-agent-button");
            if (createAgentButton.count() > 0) {
                try {
                    if (createAgentButton.first().isVisible()) {
                        log.info("[元器登录检测] 未登录（仅有新建智能体按钮，无其他登录标志）");
                        return "false";
                    }
                } catch (Exception e) {
                    log.debug("[元器登录检测] 新建智能体按钮检查失败");
                }
            }
            
            // 都不满足，保守判定为已登录
            log.info("[元器登录检测] 已登录（未检测到未登录标志）");
            String userName = extractUserName(page);
            return userName != null ? userName : "已登录";
            
        } catch (Exception e) {
            log.error("[元器登录检测] 检测失败", e);
            return "false";
        }
    }
    
    /**
     * 导航到元器首页
     * 
     * @param page Playwright页面对象
     * @return 导航是否成功
     */
    public boolean navigateToHomePage(Page page) {
        try {
            log.debug("[元器导航] 访问首页: {}", YUANQI_HOME_URL);
            page.navigate(YUANQI_HOME_URL);
            page.waitForLoadState();
            page.waitForTimeout(3000); // 等待页面完全加载
            
            log.info("[元器导航] 首页加载完成");
            return true;
        } catch (Exception e) {
            log.error("[元器导航] 导航失败", e);
            return false;
        }
    }
    
    /**
     * 触发扫码登录流程（2026-01-20更新）
     * 
     * 步骤：
     * 1. 查找并点击"新建智能体"按钮
     * 2. 等待登录弹窗出现（包含iframe）
     * 3. 等待二维码加载
     * 
     * @param page Playwright页面对象
     * @return 是否成功触发登录流程
     */
    public boolean triggerScanLogin(Page page) {
        try {
            // 查找"新建智能体"按钮
            Locator createAgentButton = page.locator("button.create-agent-button:has-text('新建智能体')");
            
            if (createAgentButton.count() == 0) {
                log.warn("[元器扫码登录] 未找到新建智能体按钮，可能已登录");
                return false;
            }
            
            log.debug("[元器扫码登录] 点击新建智能体按钮");
            createAgentButton.first().click();
            
            // 等待登录弹窗出现
            page.waitForTimeout(2000);
            
            // 检查登录iframe是否出现
            // DOM: <iframe src="https://yuanqi.tencent.com/login?..." class="login-iframe-container">
            Locator loginIframe = page.locator("iframe.login-iframe-container");
            if (loginIframe.count() > 0) {
                log.debug("[元器扫码登录] 登录iframe已加载");
            }
            
            // 等待二维码完全加载
            page.waitForTimeout(2000);
            
            log.info("[元器扫码登录] 登录界面已准备就绪");
            return true;
            
        } catch (Exception e) {
            log.error("[元器扫码登录] 触发登录失败", e);
            return false;
        }
    }
    
    /**
     * 检查是否仍在登录页面（用于轮询检测登录状态）（2026-01-20更新）
     * 
     * 检测逻辑（优先级从高到低）：
     * 1. ✅ 检查登录成功标志：
     *    - 新建智能体弹窗
     *    - 个人空间菜单
     *    - 我的智能体/知识库
     * 2. ❌ 仍在登录中：
     *    - 登录iframe仍然可见
     * 
     * @param page Playwright页面对象
     * @return true=仍在登录页面，false=已登录成功
     */
    public boolean isStillOnLoginPage(Page page) {
        try {
            // ✅ 优先级1: 检查是否出现新建智能体弹窗（扫码成功的标志）
            // DOM: <div class="v-modalDialog__content"><div class="v-page-header__heading__left__title">新建智能体</div>
            Locator createAgentDialog = page.locator(".v-modalDialog__content .v-page-header__heading__left__title:has-text('新建智能体')");
            if (createAgentDialog.count() > 0) {
                try {
                    if (createAgentDialog.first().isVisible()) {
                        log.info("[元器登录状态] 检测到新建智能体弹窗，登录成功");
                        
                        // 自动关闭弹窗
                        try {
                            Locator closeButton = page.locator(".v-modalDialog__content button.is-icon svg use[xlink\\:href='#v-basic_close_line']").locator("..");
                            if (closeButton.count() > 0) {
                                log.debug("[元器登录状态] 关闭新建智能体弹窗");
                                closeButton.first().click();
                                page.waitForTimeout(1000);
                            }
                        } catch (Exception e) {
                            log.debug("[元器登录状态] 关闭弹窗失败（不影响登录结果）", e);
                        }
                        
                        return false; // 登录成功，不再等待
                    }
                } catch (Exception e) {
                    log.debug("[元器登录状态] 弹窗检测失败，继续其他检测");
                }
            }
            
            // ✅ 优先级2: 检查 .spaceBtn 区域（不管里面文字是什么，有就说明登录了）
            Locator spaceBtn = page.locator(".spaceBtn");
            if (spaceBtn.count() > 0) {
                try {
                    if (spaceBtn.first().isVisible()) {
                        log.info("[元器登录状态] 检测到个人空间下拉区域，登录成功");
                        return false;
                    }
                } catch (Exception e) {
                    log.debug("[元器登录状态] .spaceBtn 检测失败，继续其他检测");
                }
            }
            
            // ✅ 优先级3: 检查侧边栏是否有"知识库"选项（已登录才有）
            Locator knowledgeBase = page.locator(".v-collapse-item .menu-name:has-text('知识库')");
            if (knowledgeBase.count() > 0) {
                try {
                    if (knowledgeBase.first().isVisible()) {
                        log.info("[元器登录状态] 检测到知识库选项，登录成功");
                        return false;
                    }
                } catch (Exception e) {
                    log.debug("[元器登录状态] 知识库检测失败，继续其他检测");
                }
            }
            
            // ✅ 优先级4: 检查侧边栏是否有"我的智能体"选项（已登录才有）
            Locator myAgents = page.locator(".v-collapse-item .menu-name:has-text('我的智能体')");
            if (myAgents.count() > 0) {
                try {
                    if (myAgents.first().isVisible()) {
                        log.info("[元器登录状态] 检测到我的智能体选项，登录成功");
                        return false;
                    }
                } catch (Exception e) {
                    log.debug("[元器登录状态] 我的智能体检测失败，继续其他检测");
                }
            }
            
            // ❌ 检查是否仍在登录iframe页面
            Locator loginIframe = page.locator("iframe.login-iframe-container");
            boolean hasLoginIframe = loginIframe.count() > 0 && loginIframe.isVisible();
            
            if (hasLoginIframe) {
                log.debug("[元器登录状态] 仍在登录页面，等待扫码");
                return true;
            }
            
            // 都不满足，保守判定为已登录（避免死循环）
            log.info("[元器登录状态] 登录完成（未检测到登录iframe）");
            return false;
            
        } catch (Exception e) {
            log.debug("[元器登录状态] 检测登录页面失败，认为已登录", e);
            return false;
        }
    }
    
    /**
     * 提取用户名
     * 
     * @param page Playwright页面对象
     * @return 用户名，如果无法提取则返回null
     */
    private String extractUserName(Page page) {
        try {
            // 尝试多种可能的用户名位置
            // 1. 用户头像旁边的文字
            Locator userNameLocator = page.locator(".user-info .user-name, .profile .name, [class*='username']");
            
            if (userNameLocator.count() > 0) {
                String userName = userNameLocator.first().textContent().trim();
                if (!userName.isEmpty()) {
                    log.debug("[元器用户名] 提取成功: {}", userName);
                    return userName;
                }
            }
            
            log.debug("[元器用户名] 无法提取用户名");
            return null;
            
        } catch (Exception e) {
            log.debug("[元器用户名] 提取失败", e);
            return null;
        }
    }
}
