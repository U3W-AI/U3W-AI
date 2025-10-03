package com.playwright.utils.ai;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.playwright.entity.UserInfoRequest;
import com.playwright.utils.common.LogMsgUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @Author Ran Lewis
 * @Description
 * @Date 2025/08/03 11:00 PM
 */
@Component
public class ZHZDUtil {

    @Autowired
    private LogMsgUtil logInfo;

    /**
     * 处理知乎直答思考模式选择 - 支持三种模式
     *
     * @param page   Playwright页面实例
     * @param userId userId
     * @param aiName aiName
     * @param thinkingMode 思考模式：smart(智能思考), deep(深度思考), fast(快速回答)
     */
    private void switchThinkingMode(Page page, String userId, String aiName, String thinkingMode) throws Exception {
        // 检查页面是否已关闭
        if (page.isClosed()) {
            throw new RuntimeException("页面已关闭，无法切换思考模式");
        }

        try {
            // 等待页面稳定后再操作UI元素
            Thread.sleep(800);
            
            // 再次检查页面状态
            if (page.isClosed()) {
                logInfo.sendTaskLog("页面在等待期间已关闭，跳过思考模式设置", userId, aiName);
                return;
            }

            // 新UI结构：查找思考模式下拉按钮
            Locator thinkStyleButton = page.locator("div[data-testid='Button:think_style_btn']").first();
            
            if (thinkStyleButton.count() > 0) {
                try {
                    // 增加页面状态检查和元素状态确认
                    if (page.isClosed()) {
                        logInfo.sendTaskLog("页面已关闭，跳过思考模式设置", userId, aiName);
                        return;
                    }
                    
                    // 等待元素稳定并检查可见性
                    Thread.sleep(500);
                    boolean isVisible = false;
                    try {
                        isVisible = thinkStyleButton.isVisible();
                    } catch (Exception visibilityEx) {
                        logInfo.sendTaskLog("检查按钮可见性时发生异常，尝试重新定位元素", userId, aiName);
                        // 重新定位按钮
                        thinkStyleButton = page.locator("div[data-testid='Button:think_style_btn']").first();
                        if (thinkStyleButton.count() > 0) {
                            isVisible = thinkStyleButton.isVisible();
                        }
                    }
                    
                    if (isVisible) {
                        logInfo.sendTaskLog("找到思考模式下拉按钮，准备点击展开选项", userId, aiName);
                        // 点击思考模式按钮展开选项（使用force选项避免被其他元素拦截）
                        thinkStyleButton.click(new Locator.ClickOptions().setForce(true));
                        Thread.sleep(500); // 等待下拉菜单展开
                        logInfo.sendTaskLog("已点击思考模式下拉按钮，下拉菜单应已展开", userId, aiName);
                        
                        // 检查页面状态
                        if (page.isClosed()) {
                            throw new RuntimeException("页面在展开思考模式选项时已关闭");
                        }
                        
                        // 根据模式选择对应的选项
                        boolean foundAndClicked = false;
                        String targetText = "";
                        
                        switch (thinkingMode) {
                            case "deep":
                                targetText = "深度思考";
                                break;
                            case "smart":
                                targetText = "智能思考";
                                break;
                            case "fast":
                                targetText = "快速回答";
                                break;
                            default:
                                targetText = "智能思考"; // 默认智能思考
                                logInfo.sendTaskLog("未知思考模式，使用默认智能思考", userId, aiName);
                                break;
            }

                        // 策略1：通过包含目标文本的可点击div查找（最可靠）
                        Locator thinkOption1 = page.locator("div[tabindex='0']:has(div:has-text('" + targetText + "'))");
                        if (thinkOption1.count() > 0) {
                            for (int i = 0; i < Math.min(thinkOption1.count(), 5); i++) {
                                try {
                                    Locator option = thinkOption1.nth(i);
                                    if (option.isVisible()) {
                                        option.click(new Locator.ClickOptions().setForce(true));
                                        Thread.sleep(300);
                                        logInfo.sendTaskLog("已通过可点击div选择器切换到: " + targetText, userId, aiName);
                                        foundAndClicked = true;
                                        break;
                                    }
            } catch (Exception e) {
                                    continue;
                                }
                            }
            }

                        // 策略2：通过包含目标文本的所有div查找
                        if (!foundAndClicked) {
                            Locator thinkOption2 = page.locator("div:has-text('" + targetText + "')");
                            if (thinkOption2.count() > 0) {
                                for (int i = 0; i < Math.min(thinkOption2.count(), 10); i++) {
                                    try {
                                        Locator option = thinkOption2.nth(i);
                                        if (option.isVisible()) {
                                            option.click(new Locator.ClickOptions().setForce(true));
                                            Thread.sleep(300);
                                            logInfo.sendTaskLog("已通过文本选择器切换到: " + targetText, userId, aiName);
                                            foundAndClicked = true;
                                            break;
            }
        } catch (Exception e) {
                                        continue;
                                    }
        }
    }
                        }
                        
                        // 策略3：通过父容器查找
                        if (!foundAndClicked) {
                            Locator thinkContainer = page.locator("div.css-175oi2r:has(div:has-text('" + targetText + "'))");
                            if (thinkContainer.count() > 0) {
                                for (int i = 0; i < Math.min(thinkContainer.count(), 10); i++) {
        try {
                                        Locator container = thinkContainer.nth(i);
                                        if (container.isVisible()) {
                                            container.click(new Locator.ClickOptions().setForce(true));
                                            Thread.sleep(300);
                                            logInfo.sendTaskLog("已通过容器选择器切换到: " + targetText, userId, aiName);
                                            foundAndClicked = true;
                                            break;
                                        }
        } catch (Exception e) {
                                        continue;
                                    }
                                }
                            }
                        }
                        
                        if (!foundAndClicked) {
                            logInfo.sendTaskLog("未找到思考模式选项: " + targetText + "，尝试关闭下拉菜单", userId, aiName);
                            // 关闭下拉菜单
                            page.locator("body").click();
                            Thread.sleep(300);
        }
    }
                } catch (Exception e) {
                    // 提供更友好的错误信息
                    String errorMsg = e.getMessage();
                    if (errorMsg != null && errorMsg.contains("worker@")) {
                        logInfo.sendTaskLog("页面状态已变化，跳过思考模式设置（这是正常现象）", userId, aiName);
                    } else if (errorMsg != null && errorMsg.contains("Object doesn't exist")) {
                        logInfo.sendTaskLog("页面元素已更新，跳过思考模式设置", userId, aiName);
                    } else {
                        logInfo.sendTaskLog("思考模式设置时出现异常: " + (errorMsg != null ? errorMsg : "未知错误"), userId, aiName);
                    }
                }
            } else {
                logInfo.sendTaskLog("未找到思考模式下拉按钮", userId, aiName);
            }

            // 兜底：尝试旧版UI的深度思考开关（如果是深度思考模式）
            if ("deep".equals(thinkingMode)) {
                Locator deepThoughtButton = page.locator("[data-testid='Button:deep_thinking_button']");
                if (deepThoughtButton.count() > 0 && deepThoughtButton.isVisible()) {
                    deepThoughtButton.click(new Locator.ClickOptions().setForce(true));
                    Thread.sleep(300);
                    logInfo.sendTaskLog("已通过旧版UI开启深度思考", userId, aiName);
                }
            }
            
        } catch (com.microsoft.playwright.impl.TargetClosedError e) {
            throw new RuntimeException("页面目标在切换思考模式时已关闭", e);
        } catch (TimeoutError e) {
            throw new RuntimeException("切换思考模式超时", e);
        } catch (Exception e) {
            logInfo.sendTaskLog("处理思考模式时发生错误: " + e.getMessage(), userId, aiName);
            throw e;
        }
    }

    /**
     * 处理知乎直答特殊模式开启（仅思考模式，移除知识来源）
     *
     * @param page   Playwright页面实例
     * @param roles  角色配置
     * @param userId userId
     * @param aiName aiName
     */
    private void handleCapabilityTurnOn(Page page, String roles, String userId, String aiName) throws Exception {
        // 检查页面是否已关闭
        if (page.isClosed()) {
            throw new RuntimeException("页面已关闭，无法处理思考模式");
        }

        try {
            // 确定思考模式
            String thinkingMode = "smart"; // 默认智能思考
            
            if (roles != null) {
                if (roles.contains("zhzd-sdsk")) {
                    thinkingMode = "deep"; // 深度思考
                } else if (roles.contains("zhzd-ks")) {
                    thinkingMode = "fast"; // 快速回答
                } else if (roles.contains("zhzd-zn")) {
                    thinkingMode = "smart"; // 智能思考
                }
            }
            
            logInfo.sendTaskLog("选择思考模式: " + thinkingMode, userId, aiName);
            switchThinkingMode(page, userId, aiName, thinkingMode);
            
        } catch (com.microsoft.playwright.impl.TargetClosedError e) {
            throw new RuntimeException("页面目标在处理思考模式时已关闭", e);
        } catch (TimeoutError e) {
            throw new RuntimeException("处理思考模式超时", e);
        } catch (Exception e) {
            logInfo.sendTaskLog("无法处理知乎直答思考模式", userId, aiName);
            throw e;
        }
    }

    /**
     * 提取出的知乎直答请求核心处理方法
     *
     * @param page            Playwright页面实例
     * @param userInfoRequest 包含所有请求信息的对象
     * @return 包含处理结果的字符串
     */
    public String processZHZDRequest(Page page, UserInfoRequest userInfoRequest) throws Exception {
        String userId = userInfoRequest.getUserId();
        String aiName = "知乎直答";

        // 检查页面是否已关闭
        if (page.isClosed()) {
            throw new RuntimeException("页面已关闭，无法处理知乎直答请求");
        }

        try {
            Locator inputBox = page.locator(".Dropzone.Editable-content.RichText.RichText--editable.RichText--clearBoth.ztext");
            if (inputBox == null || inputBox.count() <= 0) {
                throw new RuntimeException("未找到输入框");
            }
            
            // 切换思考模式: 深度思考、智能思考、快速回答
            handleCapabilityTurnOn(page, userInfoRequest.getRoles(), userId, aiName);

            // 检查页面状态
            if (page.isClosed()) {
                throw new RuntimeException("页面在配置模式后已关闭");
            }

            // 提问
            int copyButtonCount = getCopyButtonCount(page);

            inputBox.click();
            Thread.sleep(300);
            inputBox.type(userInfoRequest.getUserPrompt());
            
            int times = 3;
            String inputText = inputBox.textContent();
            while (!inputText.contains("输入你的问题")) {
                // 检查页面状态
                if (page.isClosed()) {
                    throw new RuntimeException("页面在发送指令时已关闭");
                }
                
                inputBox.press("Enter");
                Thread.sleep(1000);
                inputText = inputBox.textContent();
                if(times-- < 0) {
                    throw new RuntimeException("指令输入失败");
                }
            }
            
            logInfo.sendTaskLog("指令已自动发送成功", userId, aiName);
            logInfo.sendTaskLog("开启自动监听任务，持续监听" + aiName + "回答中", userId, aiName);

            // 获取原始回答HTML
            return waitZHZDHtmlDom(page, userId, aiName, copyButtonCount);
            
        } catch (com.microsoft.playwright.impl.TargetClosedError e) {
            throw new RuntimeException("页面目标在处理知乎直答请求时已关闭", e);
        } catch (TimeoutError e) {
            throw new RuntimeException("处理知乎直答请求超时", e);
        } catch (Exception e) {
            logInfo.sendTaskLog("处理知乎直答请求时发生错误", userId, aiName);
            throw e;
        }
    }

    private String waitZHZDHtmlDom(Page page, String userId, String aiName, int copyButtonCount) throws Exception {
        // 检查页面是否已关闭
        if (page.isClosed()) {
            throw new RuntimeException("页面已关闭，无法等待知乎直答回复");
        }

        try {
            long timeout = 600000; // 10分钟超时
            long startTime = System.currentTimeMillis();

            // 等待5秒
            page.waitForTimeout(5000);

            while (true) {
                // 检查页面状态
                if (page.isClosed()) {
                    throw new RuntimeException("页面在等待回复过程中已关闭");
                }

                long elapsedTime = System.currentTimeMillis() - startTime;

                if (elapsedTime > timeout) {
                    logInfo.sendTaskLog("AI回答超时，任务中断", userId, aiName);
                    break;
                }

                if (copyButtonCount != getCopyButtonCount(page)) {
                    logInfo.sendTaskLog("AI回复已完成", userId, aiName);
                    break;
                }

                page.waitForTimeout(5000);
            }

            // 检查页面状态
            if (page.isClosed()) {
                throw new RuntimeException("页面在提取内容时已关闭");
            }

            Locator contentLocator = page.locator(".Render-markdown").last();
            String htmlContent = contentLocator.first().innerHTML();
            return cleanHtml(htmlContent);
            
        } catch (com.microsoft.playwright.impl.TargetClosedError e) {
            throw new RuntimeException("页面目标在等待知乎直答回复时已关闭", e);
        } catch (TimeoutError e) {
            throw new RuntimeException("等待知乎直答回复超时", e);
        } catch (Exception e) {
            logInfo.sendTaskLog("等待知乎直答HTML DOM时出错", userId, aiName);
            throw e;
        }
    }

    private int getCopyButtonCount(Page page) throws Exception {
        // 检查页面是否已关闭
        if (page.isClosed()) {
            throw new RuntimeException("页面已关闭，无法获取复制按钮数量");
        }

        try {
        Locator copyButton = page.locator("[data-testid='Button:Share:zhida_message_share_btn']");
        return copyButton == null ? 0 : copyButton.count();
        } catch (Exception e) {
            return 0;
        }
    }

    private String cleanHtml(String html) {
        // 删除所有Popover相关的div
        html = html.replaceAll("<div class=\"Popover[^\"]*\">.*?</div>", "");
        // 删除所有包含css-vurnku的元素
        html = html.replaceAll("<[^>]*class=\"[^\"]*css-vurnku[^\"]*\"[^>]*>.*?</[^>]*>", "");
        // 删除特定的SVG图标
        html = html.replaceAll("<svg width=\"12\" height=\"12\"[^>]*>.*?</svg>", "");
        html = html.replaceAll("<img[^>]*>", "");
        return html;
    }


}
