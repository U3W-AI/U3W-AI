package com.playwright.utils;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.playwright.entity.UserInfoRequest;
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
     * 检测知乎直答是否开启深度思考
     *
     * @param page   Playwright页面实例
     * @param userId userId
     * @param aiName aiName
     * @return
     */
    private boolean isDeepThinkingEnabled(Page page, String userId, String aiName) {
        try {
            Locator switchBackground = page.locator("div[data-testid='Button:deep_thinking_button'] > div:nth-child(3)");

            String style = switchBackground.getAttribute("style");

            // 检查是否为开启状态的颜色
            if (style != null && style.contains("rgb(90, 77, 248)")) {
                return true;
            } else if (style != null && (style.contains("rgb(196, 199, 206)"))) {
                return false;
            }
            // 默认开启状态
            return true;

        } catch (Exception e) {
            logInfo.sendTaskLog("无法确定深度思考开关状态", userId, aiName);
            // 默认开启状态
            return true;
        }
    }

    /**
     * 开启知乎直答深度思考
     *
     * @param page   Playwright页面实例
     * @param userId userId
     * @param aiName aiName
     * @return
     */
    private void switchDeepThinking(Page page, String userId, String aiName, boolean shouldBeEnabled) {
        try {
            boolean deepThinkingEnabled = isDeepThinkingEnabled(page, userId, aiName);
            if (deepThinkingEnabled == shouldBeEnabled) {
                return;
            }

            Locator deepThoughtButton = page.locator("[data-testid='Button:deep_thinking_button']");
            if (deepThoughtButton.count() > 0) {
                deepThoughtButton.click();
            } else {
                logInfo.sendTaskLog("无法找的深度思考开关", userId, aiName);
            }
        } catch (Exception e) {
            logInfo.sendTaskLog("无法处理深度思考状态", userId, aiName);
            throw e;
        }
    }

    /**
     * 处理知识来源选项
     *
     * @param page            Playwright页面实例
     * @param optionName      知识来源选项名称
     * @param shouldBeEnabled 是否需要开启
     * @param userId          userId
     * @param aiName          aiName
     */
    private void handleKnowledgeSourceOption(Page page, String optionName, boolean shouldBeEnabled, String userId, String aiName) {
        try {
            // 先定位到整个知识来源块
            Locator knowledgeBlock = page.locator("div[data-testid='Block:zhida_focus_source_block']");

            if (knowledgeBlock.count() == 0) {
                logInfo.sendTaskLog("未找到知识来源块", userId, aiName);
                return;
            }

            // 在知识来源块中查找包含特定选项的独立选项容器
            Locator optionContainer = knowledgeBlock.locator(
                    "div[tabindex='0']:has(div[dir='auto']:has-text('" + optionName + "'))"
            ).first();

            if (optionContainer.count() == 0) {
                logInfo.sendTaskLog("未找到知识来源选项: " + optionName, userId, aiName);
                return;
            }

            // 判断当前选项是否开启
            boolean isCurrentlyEnabled = false;
            try {
                isCurrentlyEnabled = isKnowledgeSourceEnabled(optionContainer, optionName, userId, aiName);
            } catch (Exception e) {
                return;
            }

            // 根据期望状态和当前状态决定是否需要点击
            if (shouldBeEnabled && !isCurrentlyEnabled) {
                // 需要开启但当前是关闭状态，点击开启
                optionContainer.click();
            } else if (!shouldBeEnabled && isCurrentlyEnabled) {
                // 需要关闭但当前是开启状态，点击关闭
                optionContainer.click();
            }
        } catch (Exception e) {
            logInfo.sendTaskLog("处理知识来源选项时出错", userId, aiName);
            throw e;
        }
    }

    /**
     * 检测知识来源选项是否开启
     *
     * @param optionContainer 知识来源选项容器
     * @param optionName      知识来源选项名称
     * @param userId          userId
     * @param aiName          aiName
     * @return
     */
    private boolean isKnowledgeSourceEnabled(Locator optionContainer, String optionName, String userId, String aiName) throws Exception {
        try {
            // 获取特定选项容器的HTML内容
            String containerHtml = optionContainer.innerHTML();
            // 构造查找模式，检查选项名称元素的颜色
            String color = "color: rgb(90, 77, 248);";
            String pattern = ">" + optionName + "</div>";
            return containerHtml.contains(color) && containerHtml.contains(pattern);
        } catch (Exception e) {
            logInfo.sendTaskLog(optionName + " 检查状态时出错:", userId, aiName);
            throw e;
        }
    }

    /**
     * 处理知乎直答知识来源选择（全网, 知乎, 学术, 我的知识库)
     *
     * @param page   Playwright页面实例
     * @param roles  用户角色
     * @param userId userId
     * @param aiName aiName
     * @return
     */
    private void switchKnowledgeSource(Page page, String roles, String userId, String aiName) {
        try {
            Locator targetElement = page.locator("div[style*='background-color: rgb(248, 248, 250)'] div[style*='transform: rotate(0deg)']").first();

            // 开启列表
            if (targetElement.count() > 0) {
                targetElement.click();
            } else {
                logInfo.sendTaskLog("展开知识来源列表失败", userId, aiName);
                return;
            }

            // 等待下拉列表出现
            page.waitForTimeout(500);

            // 开启或关闭 全网
            handleKnowledgeSourceOption(page, "全网", roles.contains("zhzd-qw"), userId, aiName);
            // 开启或关闭 知乎
            handleKnowledgeSourceOption(page, "知乎", roles.contains("zhzd-zh"), userId, aiName);
            // 开启或关闭 学术
            handleKnowledgeSourceOption(page, "学术", roles.contains("zhzd-xs"), userId, aiName);
            // 开启或关闭 我的知识库
            handleKnowledgeSourceOption(page, "我的知识库", roles.contains("zhzd-wdzsk"), userId, aiName);
        } catch (Exception e) {
            logInfo.sendTaskLog("无法处理知乎直答知识来源", userId, aiName);
            throw e;
        }
    }

    /**
     * 处理知乎直答特殊模式开启（深度思考, 全网, 知乎, 学术, 我的知识库)
     *
     * @param page   Playwright页面实例
     * @param userId userId
     * @param aiName aiName
     * @return
     */
    private void handleCapabilityTurnOn(Page page, String roles, String userId, String aiName) {
        try {
            switchDeepThinking(page, userId, aiName, roles != null && roles.contains("zhzd-sdsk"));
            switchKnowledgeSource(page, roles, userId, aiName);
        } catch (Exception e) {
            logInfo.sendTaskLog("无法处理知乎直答特殊模式", userId, aiName);
            throw e;
        }
    }

    /**
     * 提取出的知乎直答请求核心处理方法
     *
     * @param page            Playwright页面实例
     * @param userInfoRequest 包含所有请求信息的对象
     * @return 包含处理结果的Map
     */
    public String processZHZDRequest(Page page, UserInfoRequest userInfoRequest) throws Exception {
        String userId = userInfoRequest.getUserId();
        String aiName = "知乎直答";

        try {
            Locator inputBox = page.locator(".Dropzone.Editable-content.RichText.RichText--editable.RichText--clearBoth.ztext");
            if (inputBox == null || inputBox.count() <= 0) {
                throw new RuntimeException("未找到输入框");
            }
            // 切换模式, 深度思考, 全网, 知乎, 学术, 我的知识库
            handleCapabilityTurnOn(page, userInfoRequest.getRoles(), userId, aiName);

            // 提问

            int copyButtonCount = getCopyButtonCount(page);

            inputBox.click();
            inputBox.type(userInfoRequest.getUserPrompt());
            int times = 3;
            String inputText = inputBox.textContent();
            while (!inputText.contains("输入你的问题")) {
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
        } catch (Exception e) {
            logInfo.sendTaskLog("处理通义千问请求时发生错误", userId, aiName);
            throw e;
        }
    }

    private String waitZHZDHtmlDom(Page page, String userId, String aiName, int copyButtonCount) throws Exception {
        try {
            long timeout = 600000;
            long startTime = System.currentTimeMillis();

            // 等待5秒
            page.waitForTimeout(5000);

            while (true) {
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

            Locator contentLocator = page.locator(".Render-markdown").last();
            String htmlContent = contentLocator.first().innerHTML();
            return cleanHtml(htmlContent);
        } catch (Exception e) {
            logInfo.sendTaskLog("等待知乎直答HTML DOM时出错", userId, aiName);
            throw e;
        }
    }

    private int getCopyButtonCount(Page page) {
        Locator copyButton = page.locator("[data-testid='Button:Share:zhida_message_share_btn']");
        return copyButton == null ? 0 : copyButton.count();
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

    /**
     * 检测知乎直答该会话是否存在
     *
     * @param page
     * @return
     */
    public boolean sessionNotFound(Page page) {
        Locator errorDiv = page.locator("div:has-text('很抱歉，服务异常，请稍后重试。')");
        return errorDiv.count() > 0;
    }
}
