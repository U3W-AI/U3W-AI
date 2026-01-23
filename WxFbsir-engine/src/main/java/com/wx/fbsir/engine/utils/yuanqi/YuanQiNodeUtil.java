package com.wx.fbsir.engine.utils.yuanqi;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.ViewportSize;
import com.wx.fbsir.engine.playwright.util.ScreenshotUtil;
import com.wx.fbsir.engine.playwright.util.ScreenshotUploadClient;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.BiPredicate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 元器（YuanQi）节点操作工具类
 *
 * 功能说明：
 * 1. 编辑大模型节点 - 定位节点、填充参数、保存配置
 * 2. 调试工作流 - 点击调试按钮、填入参数、执行调试
 * 3. 发布工作流 - 点击发布按钮、确认发布
 *
 * 安全机制：
 * - 所有操作前进行参数校验
 * - 敏感信息不记录到日志
 * - 操作失败时提供详细错误信息
 *
 * @author wxfbsir
 * @date 2025-01-10
 */
@Slf4j
@Component
public class YuanQiNodeUtil {

    @Autowired
    private ScreenshotUtil screenshotUtil;

    @Autowired
    private ScreenshotUploadClient uploadClient;

    private boolean isRunFailed(Page page) {
        for (Frame frame : page.frames()) {
            if (frame.isDetached()) continue;

            try {
                // ① 通过状态类名（最稳）
                if (frame.locator(
                        ".semi-tag-red, .semi-tag-error, [data-status='error'], [data-status='failed']"
                ).count() > 0) {
                    return true;
                }

                // ② 通过 aria / role
                if (frame.locator(
                        "[role='status'][aria-label*='失败'], [aria-live*='error']"
                ).count() > 0) {
                    return true;
                }

                // ③ 最后兜底：文本（只在 frame 内）
                if (frame.getByText("运行失败").count() > 0
                        || frame.getByText("系统运行异常").count() > 0) {
                    return true;
                }
            } catch (Exception ignore) {}
        }
        return false;
    }

    /**
     * 检测调试是否已成功完成（参考失败检测逻辑，遍历所有 Frame）。
     *
     * <p>优先级从高到低：</p>
     * <ul>
     *   <li>绿色/成功状态类名：如 {@code .semi-tag-green}、{@code [data-status='success']}；</li>
     *   <li>ARIA / role 状态：如 {@code [role='status'][aria-label*='成功'] }；</li>
     *   <li>兜底文本：包含“运行完成”或“运行成功”的标签。</li>
     * </ul>
     */
    private boolean isRunSucceeded(Page page) {
        for (Frame frame : page.frames()) {
            if (frame.isDetached()) continue;

            try {
                // ① 通过状态类名（与失败检测对称）
                if (frame.locator(
                        ".semi-tag-green, .semi-tag-success, [data-status='success'], [data-status='done']"
                ).count() > 0) {
                    return true;
                }

                // ② 通过 aria / role
                if (frame.locator(
                        "[role='status'][aria-label*='成功'], [aria-live*='success']"
                ).count() > 0) {
                    return true;
                }

                // ③ 文本兜底：仅在当前 frame 内查找
                if (frame.getByText("运行完成").count() > 0
                        || frame.getByText("运行成功").count() > 0) {
                    return true;
                }
            } catch (Exception ignore) {}
        }
        return false;
    }


    /**
     * 节点编辑结果
     */
    public static class NodeEditResult {
        private final boolean success;
        private final String message;
        private final String nodeName;

        public NodeEditResult(boolean success, String message, String nodeName) {
            this.success = success;
            this.message = message;
            this.nodeName = nodeName;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public String getNodeName() {
            return nodeName;
        }
    }

    /**
     * 调试结果
     */
    public static class DebugResult {
        private final boolean success;
        private final String message;
        private final String output;
        private final String screenshotUrl;

        public DebugResult(boolean success, String message, String output) {
            this(success, message, output, null);
        }

        public DebugResult(boolean success, String message, String output, String screenshotUrl) {
            this.success = success;
            this.message = message;
            this.output = output;
            this.screenshotUrl = screenshotUrl;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public String getOutput() {
            return output;
        }

        public String getScreenshotUrl() {
            return screenshotUrl;
        }
    }

    /**
     * 发布结果
     */
    public static class PublishResult {
        private final boolean success;
        private final String message;

        public PublishResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }

    private void zoomCanvasFromCenter(Page page, int steps) {
        try {
            // 1️⃣ 获取当前 viewport 尺寸
            int width = page.viewportSize().width;
            int height = page.viewportSize().height;

            int centerX = width / 2;
            int centerY = height / 2;

            // 2️⃣ 移动鼠标到画布中心（关键）
            page.mouse().move(centerX, centerY);
            page.waitForTimeout(50);

            // 3️⃣ Ctrl + 滚轮缩放
            page.keyboard().down("Control");
            for (int i = 0; i < Math.abs(steps); i++) {
                page.mouse().wheel(0, steps > 0 ? -120 : 120);
                page.waitForTimeout(60);
            }
            page.keyboard().up("Control");

            log.info("[Canvas] 已以中心点缩放 {} 次", steps);
        } catch (Exception e) {
            log.error("[Canvas] 中心缩放失败", e);
        }
    }



    /**
     * 强制等待模型选择区域渲染完成
     */
    private boolean waitForModelArea(Page page, int timeoutMs) {
        long start = System.currentTimeMillis();

        while (System.currentTimeMillis() - start < timeoutMs) {
            try {
                // 策略1：通过文本"模型"或"大模型"查找（包含svg图标）
                Locator modelArea = page.locator(
                        "div:has-text('模型'):has(svg), div:has-text('大模型'):has(svg)"
                );

                if (modelArea.count() > 0 && modelArea.first().isVisible()) {
                    log.info("[元器模型] 模型选择区域已渲染完成 (策略1: 带svg)");
                    return true;
                }

                // 策略2：通过文本"模型"查找（不要求svg，更宽松）
                modelArea = page.locator(
                        "div:has-text('模型'), div:has-text('大模型')"
                ).filter(new Locator.FilterOptions().setHas(page.locator("select, [role='combobox'], [class*='select'], [class*='dropdown']")));

                if (modelArea.count() > 0 && modelArea.first().isVisible()) {
                    log.info("[元器模型] 模型选择区域已渲染完成 (策略2: 带选择器)");
                    return true;
                }

                // 策略3：查找包含"模型"文本的容器，且包含可点击的下拉框
                modelArea = page.locator(
                        "div:has-text('模型'), div:has-text('大模型')"
                ).filter(new Locator.FilterOptions().setHas(page.locator("button, [class*='trigger'], [class*='selector']")));

                if (modelArea.count() > 0 && modelArea.first().isVisible()) {
                    log.info("[元器模型] 模型选择区域已渲染完成 (策略3: 带按钮/触发器)");
                    return true;
                }

                // 策略4：直接查找下拉选择框（最宽松）
                Locator selectBox = page.locator(
                        "select[name*='model'], [class*='model-select'], [class*='model-selector'], " +
                        "[data-field='model'], [data-field='modelName'], " +
                        "[role='combobox']:has-text('模型'), [role='combobox']:has-text('大模型')"
                );

                if (selectBox.count() > 0 && selectBox.first().isVisible()) {
                    log.info("[元器模型] 模型选择区域已渲染完成 (策略4: 直接查找选择框)");
                    return true;
                }

                // 策略5：查找包含"模型"文本的父容器（最宽松的匹配）
                modelArea = page.locator("div:has-text('模型'), div:has-text('大模型')").first();
                if (modelArea.count() > 0 && modelArea.isVisible()) {
                    // 检查是否可交互（包含可点击元素）
                    try {
                        Locator clickable = modelArea.locator("button, select, [role='combobox'], [class*='select'], [class*='trigger']");
                        if (clickable.count() > 0) {
                            log.info("[元器模型] 模型选择区域已渲染完成 (策略5: 父容器包含可交互元素)");
                            return true;
                        }
                    } catch (Exception e) {
                        // 忽略检查错误
                    }
                }

            } catch (Exception e) {
                log.debug("[元器模型] 等待模型区域时出现异常: {}", e.getMessage());
            }

            page.waitForTimeout(100);
        }

        log.error("[元器模型] 等待模型选择区域超时 ({}ms)", timeoutMs);
        return false;
    }

    /**
     * 严格模式模型选择（失败即终止流程）
     * 支持长列表滚动查找
     */
    /**
     * 选择模型并编辑参数（优化版）
     * 
     * @param page 页面对象
     * @param modelName 模型名称
     * @param config 配置参数（可选，包含 temperature, topP, maxTokens）
     * @return 是否成功
     */
    private boolean selectModelStrict(Page page, String modelName, Map<String, Object> config) {
        try {
            log.info("[元器模型选择] 开始选择模型: {}", modelName);

            // 1️⃣ 精确定位并点击显示当前模型的下拉框
            // 根据HTML结构：.model_select 容器内包含 .ant-select（下拉框）和 button（高级设置按钮）
            Locator modelDropdown = null;
            boolean clicked = false;

            // 策略1：直接通过CSS选择器定位 .model_select 容器内的 .ant-select（排除高级设置按钮）
            try {
                log.debug("[元器模型选择] 通过CSS选择器直接定位下拉框");
                
                // 方法1：通过 .model_select 容器定位 .ant-select
                Locator dropdown = page.locator(".model_select .ant-select").first();
                if (dropdown.count() > 0 && dropdown.isVisible()) {
                    modelDropdown = dropdown;
                    log.info("[元器模型选择] 通过 .model_select .ant-select 找到下拉框");
                }
            } catch (Exception e) {
                log.debug("[元器模型选择] CSS选择器定位失败: {}", e.getMessage());
            }

            // 策略2：通过"模型"标签所在的section定位下拉框
            if (modelDropdown == null || modelDropdown.count() == 0) {
                try {
                    log.debug("[元器模型选择] 通过'模型'标签所在的section定位下拉框");
                    // 找到包含"模型"文本的section
                    Locator modelSection = page.locator(
                        ".panel__section:has(.section-header-title:has-text('模型')), " +
                        "div:has-text('模型'):has(.model_select)"
                    ).first();
                    
                    if (modelSection.count() > 0) {
                        // 在section内查找 .model_select .ant-select
                        Locator dropdown = modelSection.locator(".model_select .ant-select").first();
                        if (dropdown.count() > 0 && dropdown.isVisible()) {
                            modelDropdown = dropdown;
                            log.info("[元器模型选择] 通过section定位找到下拉框");
                        }
                    }
                } catch (Exception e) {
                    log.debug("[元器模型选择] 通过section定位失败: {}", e.getMessage());
                }
            }

            // 策略3：通过"高级设置"按钮的父容器定位（确保不点击高级设置按钮本身）
            if (modelDropdown == null || modelDropdown.count() == 0) {
                try {
                    log.debug("[元器模型选择] 通过'高级设置'按钮的父容器定位下拉框");
                    Locator advancedSettingsBtn = page.getByText("高级设置").first();
                    
                    if (advancedSettingsBtn.count() > 0 && advancedSettingsBtn.isVisible()) {
                        // 找到包含"高级设置"按钮的 .model_select 容器
                        Locator modelSelectContainer = advancedSettingsBtn.locator("xpath=ancestor::div[contains(@class, 'model_select')]").first();
                        
                        if (modelSelectContainer.count() > 0) {
                            // 在容器内查找 .ant-select（排除高级设置按钮）
                            Locator dropdown = modelSelectContainer.locator(".ant-select").first();
                            if (dropdown.count() > 0 && dropdown.isVisible()) {
                                modelDropdown = dropdown;
                                log.info("[元器模型选择] 通过高级设置按钮的父容器找到下拉框");
                            }
                        } else {
                            // 如果没找到 .model_select 容器，尝试找前一个兄弟元素
                            Locator previousSibling = advancedSettingsBtn.locator("xpath=preceding-sibling::div[contains(@class, 'ant-select')]").first();
                            if (previousSibling.count() > 0 && previousSibling.isVisible()) {
                                modelDropdown = previousSibling;
                                log.info("[元器模型选择] 通过前一个兄弟元素找到下拉框");
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("[元器模型选择] 通过高级设置按钮定位失败: {}", e.getMessage());
                }
            }

            // 策略4：通过"模型"标签查找父容器内的下拉框
            if (modelDropdown == null || modelDropdown.count() == 0) {
                try {
                    log.debug("[元器模型选择] 通过'模型'标签查找下拉框");
                    Locator modelLabel = page.getByText("模型").first();
                    if (modelLabel.count() > 0 && modelLabel.isVisible()) {
                        // 查找包含"模型"标签的section，然后找 .model_select .ant-select
                        Locator section = modelLabel.locator("xpath=ancestor::div[contains(@class, 'panel__section') or contains(@class, 'section-content')]").first();
                        if (section.count() > 0) {
                            Locator dropdown = section.locator(".model_select .ant-select, .ant-select").first();
                            if (dropdown.count() > 0 && dropdown.isVisible()) {
                                // 确保不是高级设置按钮
                                String text = dropdown.textContent();
                                if (text == null || !text.contains("高级设置")) {
                                    modelDropdown = dropdown;
                                    log.info("[元器模型选择] 通过'模型'标签找到下拉框");
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("[元器模型选择] 通过'模型'标签查找失败: {}", e.getMessage());
                }
            }

            // 必须先点击下拉框打开列表
            if (modelDropdown == null || modelDropdown.count() == 0) {
                log.error("[元器模型选择] 未找到模型下拉框，所有策略均失败");
                return false;
            }

            // 点击下拉箭头打开列表（新界面：需要点击下拉箭头，而不是直接点击下拉框）
            try {
                log.info("[元器模型选择] 准备点击模型下拉箭头打开列表");
                modelDropdown.scrollIntoViewIfNeeded();
                page.waitForTimeout(200);
                
                // 方法1：优先点击 .ant-select-arrow（下拉箭头）
                try {
                    Locator arrow = modelDropdown.locator(".ant-select-arrow").first();
                    if (arrow.count() > 0 && arrow.isVisible()) {
                        arrow.click();
                        clicked = true;
                        log.info("[元器模型选择] 已点击 .ant-select-arrow，等待列表打开");
                    }
                } catch (Exception e1) {
                    log.debug("[元器模型选择] 点击 .ant-select-arrow 失败: {}", e1.getMessage());
                }
                
                // 方法2：如果箭头点击失败，尝试点击 .ant-select-selector（这是实际可点击的部分）
                if (!clicked) {
                    try {
                        Locator selector = modelDropdown.locator(".ant-select-selector").first();
                        if (selector.count() > 0 && selector.isVisible()) {
                            selector.click();
                            clicked = true;
                            log.info("[元器模型选择] 已点击 .ant-select-selector，等待列表打开");
                        }
                    } catch (Exception e2) {
                        log.debug("[元器模型选择] 点击 .ant-select-selector 失败: {}", e2.getMessage());
                    }
                }
                
                // 方法3：如果selector点击失败，尝试点击整个 .ant-select
                if (!clicked) {
                    try {
                        modelDropdown.click();
                        clicked = true;
                        log.info("[元器模型选择] 已点击整个 .ant-select，等待列表打开");
                    } catch (Exception e3) {
                        log.debug("[元器模型选择] 点击整个 .ant-select 失败: {}", e3.getMessage());
                        
                        // 方法4：尝试点击内部的输入框或combobox
                        try {
                            Locator input = modelDropdown.locator(
                                "input[role='combobox'], input[type='search'], " +
                                ".ant-select-selection-search-input"
                            ).first();
                            if (input.count() > 0 && input.isVisible()) {
                                input.click();
                                clicked = true;
                                log.info("[元器模型选择] 通过内部输入框点击成功");
                            }
                        } catch (Exception e4) {
                            log.debug("[元器模型选择] 点击内部输入框失败: {}", e4.getMessage());
                        }
                        
                        // 方法5：最后尝试JavaScript强制点击
                        if (!clicked) {
                            try {
                                modelDropdown.evaluate("el => el.click()");
                                clicked = true;
                                log.info("[元器模型选择] 通过JavaScript点击成功");
                            } catch (Exception e5) {
                                log.error("[元器模型选择] 所有点击方式均失败: {}", e5.getMessage());
                                throw e3; // 抛出原始异常
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("[元器模型选择] 点击下拉框失败: {}", e.getMessage());
                return false;
            }

            // 等待下拉菜单/弹窗出现
            page.waitForTimeout(1500);

            // 2️⃣ 在下拉列表打开后，查找下拉列表容器（Ant Design Select的下拉列表）
            // 说明：这里不再使用下拉框自带的搜索输入框，而是统一通过遍历 + 滚动的方式在下拉列表内查找目标模型。
            Locator dropdownContainer = null;
            
            // 等待下拉列表完全打开（通过等待选项出现来确认）
            try {
                log.debug("[元器模型选择] 等待下拉列表打开...");
                // 等待至少一个选项出现
                Locator firstOption = page.locator(
                    "div[role='option'], .ant-select-item-option, div.ant-select-item"
                ).first();
                firstOption.waitFor(new Locator.WaitForOptions()
                        .setTimeout(5000)
                        .setState(WaitForSelectorState.VISIBLE));
                log.info("[元器模型选择] 下拉列表已打开");
                page.waitForTimeout(500); // 额外等待确保完全渲染
            } catch (Exception e) {
                log.warn("[元器模型选择] 等待下拉列表打开超时: {}", e.getMessage());
            }
            
            // 查找下拉列表容器（Ant Design Select的下拉列表）
            // 这里收紧选择器，优先、并尽量只匹配真正的模型下拉弹层，避免误命中左侧“搜索节点”等区域
            String[] containerSelectors = {
                "div.ant-select-dropdown:visible",
                "div[class*='ant-select-dropdown']:visible",
                "div[role='listbox']:visible"
            };

            for (String selector : containerSelectors) {
                try {
                    // 使用 last()，优先选择「最新弹出的」下拉浮层，避免命中页面上其它旧的/隐藏的下拉
                    Locator container = page.locator(selector).last();
                    if (container.count() > 0 && container.isVisible()) {
                        // 进一步排除明显来自左侧“搜索节点”等区域的下拉
                        String containerText = null;
                        try {
                            containerText = container.textContent();
                        } catch (Exception ignore) {}

                        if (containerText != null && containerText.contains("搜索节点")) {
                            log.debug("[元器模型选择] 跳过疑似左侧搜索下拉容器 (包含“搜索节点”文案)");
                            continue;
                        }

                        // 验证容器确实包含选项（确保是模型选择的下拉列表）
                        Locator options = container.locator(
                            "div[role='option'], .ant-select-item-option, div.ant-select-item"
                        );
                        int optionCount = options.count();
                        if (optionCount > 0) {
                            dropdownContainer = container;
                            log.info("[元器模型选择] 找到下拉列表容器: {} (包含 {} 个选项)", selector, optionCount);
                            break;
                        }
                    }
                } catch (Exception e) {
                    // 忽略
                }
            }
            
            // 如果没找到容器，尝试通过选项的父容器推断（使用最后一个选项，优先匹配最近弹出的下拉）
            if (dropdownContainer == null || dropdownContainer.count() == 0) {
                try {
                    Locator options = page.locator(
                        "div[role='option'], .ant-select-item-option, div.ant-select-item"
                    );
                    int optionCount = options.count();
                    if (optionCount >= 2) {
                        Locator lastOption = options.last();
                        dropdownContainer = lastOption.locator(
                            "xpath=ancestor::div[contains(@class, 'ant-select-dropdown') or contains(@class, 'dropdown') or @role='listbox'][1]"
                        ).first();
                        if (dropdownContainer.count() == 0) {
                            dropdownContainer = lastOption.locator("xpath=ancestor::div[1]");
                        }
                        log.info("[元器模型选择] 通过选项推断找到下拉列表容器 (选项数量: {})", optionCount);
                    }
                } catch (Exception e) {
                    log.debug("[元器模型选择] 通过选项推断失败: {}", e.getMessage());
                }
            }

            // 只在下拉列表容器内查找并点击模型选项，不再使用搜索框过滤
            // 为了简化逻辑并避免输入焦点问题，这里统一走下面的遍历 + 滚动查找逻辑
            if (dropdownContainer == null || dropdownContainer.count() == 0) {
                log.warn("[元器模型选择] 未找到下拉列表容器，将直接使用页面级选项定位与滚动查找");
            } else {
                log.debug("[元器模型选择] 已找到下拉列表容器，将通过遍历/滚动选项来选择模型，不使用搜索框");
            }

            // 如果之前没找到容器，再次尝试查找下拉列表容器
            if (dropdownContainer == null || dropdownContainer.count() == 0) {
                log.debug("[元器模型选择] 重新查找下拉列表容器");
                String[] additionalSelectors = {
                    "[role='listbox']",
                    "[class*='popover']",
                    "[class*='dropdown']",
                    "[class*='select-dropdown']",
                    "[class*='menu']",
                    "[class*='option-list']",
                    "[class*='select-option']",
                    "div[class*='semi-select']",
                    "div[class*='semi-dropdown']"
                };

                for (String selector : additionalSelectors) {
                    try {
                        // 同样使用 last()，优先匹配最近弹出的下拉或弹层
                        Locator container = page.locator(selector).last();
                        if (container.count() > 0 && container.isVisible()) {
                            dropdownContainer = container;
                            log.debug("[元器模型选择] 找到下拉列表容器: {}", selector);
                            break;
                        }
                    } catch (Exception e) {
                        // 忽略
                    }
                }

                // 如果还是没找到容器，尝试查找包含多个选项的区域（使用最后一个选项）
                if (dropdownContainer == null || dropdownContainer.count() == 0) {
                    try {
                        Locator options = page.locator("div[role='option'], li[role='option'], [class*='option'], [class*='item']");
                        if (options.count() >= 2) {
                            Locator lastOption = options.last();
                            dropdownContainer = lastOption.locator("xpath=ancestor::div[1]");
                            log.debug("[元器模型选择] 通过选项数量推断下拉列表已打开");
                        }
                    } catch (Exception e) {
                        log.debug("[元器模型选择] 无法确认下拉列表状态: {}", e.getMessage());
                    }
                }
            }

            if (dropdownContainer == null || dropdownContainer.count() == 0) {
                log.warn("[元器模型选择] 无法确认下拉列表是否已打开，尝试继续查找模型选项");
            }

            // 3️⃣ 在下拉列表中查找目标模型（支持滚动）
            // 注意：忽略128K、64K等后缀，只匹配模型名称的主要部分
            Locator modelOption = null;
            boolean optionFound = false;
            int optionCount = 0; // 选项数量
            
            // 清理模型名称：去掉数字K后缀（如"混元大模型长文本版 256K" -> "混元大模型长文本版"）
            String cleanModelName = modelName.replaceAll("\\s*\\d+[Kk]\\s*$", "").trim();
            log.debug("[元器模型选择] 清理后的模型名称: {} (原始: {})", cleanModelName, modelName);

            // 策略1：先尝试直接查找（可能在可见区域）
            // 匹配逻辑：选项文本包含清理后的模型名称（忽略128K等后缀）
            try {
                // 扩展选项选择器，包括更多可能的选项元素
                String[] optionSelectors = {
                    "div[role='option']",
                    "li[role='option']",
                    "[class*='option']",
                    "[class*='item']",
                    "div[class*='semi-select-option']",
                    "div[class*='select-option']",
                    "div[class*='dropdown-item']",
                    "div[class*='menu-item']",
                    "div:has-text('混元')",  // 包含"混元"的所有div
                    "div:has-text('模型')"   // 包含"模型"的所有div
                };
                
                Locator allOptions = null;
                int maxCount = 0;
                
                // 尝试不同的选择器，找到选项最多的
                for (String selector : optionSelectors) {
                    try {
                        Locator options = null;
                        if (dropdownContainer != null && dropdownContainer.count() > 0) {
                            options = dropdownContainer.locator(selector);
                        } else {
                            options = page.locator(selector);
                        }
                        int count = options.count();
                        if (count > maxCount) {
                            maxCount = count;
                            allOptions = options;
                            log.debug("[元器模型选择] 使用选择器找到 {} 个选项: {}", count, selector);
                        }
                    } catch (Exception e) {
                        // 忽略
                    }
                }
                
                // 如果没找到，使用默认选择器
                if (allOptions == null || allOptions.count() == 0) {
                    if (dropdownContainer != null && dropdownContainer.count() > 0) {
                        allOptions = dropdownContainer.locator("div, li");
                    } else {
                        allOptions = page.locator("div[role='option'], li[role='option'], [class*='option'], [class*='item']");
                    }
                }
                
                optionCount = allOptions.count();
                log.debug("[元器模型选择] 找到 {} 个选项", optionCount);
                
                // 如果选项数量为0，尝试输出调试信息
                if (optionCount == 0) {
                    log.warn("[元器模型选择] 未找到任何选项，尝试输出页面可见文本用于调试");
                    try {
                        // 输出下拉容器中的所有文本
                        if (dropdownContainer != null && dropdownContainer.count() > 0) {
                            String containerText = dropdownContainer.textContent();
                            log.debug("[元器模型选择] 下拉容器文本: {}", containerText != null ? containerText.substring(0, Math.min(500, containerText.length())) : "null");
                        }
                        // 输出页面中所有包含"混元"或"模型"的元素
                        Locator debugElements = page.locator("div:has-text('混元'), div:has-text('模型')");
                        int debugCount = debugElements.count();
                        log.debug("[元器模型选择] 页面中包含'混元'或'模型'的元素数量: {}", debugCount);
                        for (int i = 0; i < Math.min(debugCount, 10); i++) {
                            try {
                                String text = debugElements.nth(i).textContent();
                                if (text != null && text.length() > 0) {
                                    log.debug("[元器模型选择] 调试元素 {}: {}", i, text.substring(0, Math.min(100, text.length())));
                                }
                            } catch (Exception e) {
                                // 忽略
                            }
                        }
                    } catch (Exception e) {
                        log.debug("[元器模型选择] 调试信息输出失败: {}", e.getMessage());
                    }
                }
                
                // 遍历所有选项，查找匹配的模型（忽略128K等后缀）
                for (int i = 0; i < optionCount; i++) {
                    try {
                        Locator option = allOptions.nth(i);
                        // 不检查isVisible，因为可能有些选项需要滚动才能看到
                        String optionText = option.textContent();
                        if (optionText != null && optionText.trim().length() > 0) {
                            // 清理选项文本（去掉128K等后缀）
                            String cleanOptionText = optionText.replaceAll("\\s*\\d+[Kk]\\s*$", "").trim();
                            
                            // 输出前几个选项的文本用于调试
                            if (i < 5) {
                                log.debug("[元器模型选择] 选项 {}: {} (清理后: {})", i, optionText, cleanOptionText);
                            }
                            
                            // 匹配：选项包含清理后的模型名称，或者完全匹配
                            if (cleanOptionText.contains(cleanModelName) || cleanModelName.contains(cleanOptionText)) {
                                modelOption = option;
                                optionFound = true;
                                log.info("[元器模型选择] 在可见区域找到模型选项: {} (原始文本: {})", cleanOptionText, optionText);
                                break;
                            }
                        }
                    } catch (Exception e) {
                        // 忽略单个选项的错误
                        log.debug("[元器模型选择] 处理选项 {} 时出错: {}", i, e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.debug("[元器模型选择] 直接查找失败: {}", e.getMessage());
            }

            // 策略1.5：如果选项数量为0，尝试直接通过文本查找（限制在当前下拉容器内，避免点到其他区域/搜索框）
            if (!optionFound && optionCount == 0) {
                log.info("[元器模型选择] 选项数量为0，尝试在当前下拉容器内通过文本查找模型");
                try {
                    Locator textScope = (dropdownContainer != null && dropdownContainer.count() > 0)
                        ? dropdownContainer
                        : page.locator("body");
                    // 直接查找包含模型名称的文本元素（优先在下拉容器内）
                    Locator textElement = textScope.getByText(cleanModelName).first();
                    if (textElement.count() > 0) {
                        // 找到包含该文本的父元素（可能是选项）
                        Locator parentOption = textElement.locator("xpath=ancestor::div[1]");
                        if (parentOption.count() > 0) {
                            String parentText = parentOption.textContent();
                            if (parentText != null && parentText.contains(cleanModelName)) {
                                modelOption = parentOption;
                                optionFound = true;
                                log.info("[元器模型选择] 通过文本查找找到模型选项: {}", parentText);
                            }
                        }
                    }
                    
                    // 如果还没找到，尝试查找包含"长文本"的元素（同样限制在当前容器范围内）
                    if (!optionFound && cleanModelName.contains("长文本")) {
                        Locator longTextElement = textScope.getByText("长文本").first();
                        if (longTextElement.count() > 0) {
                            Locator parentOption = longTextElement.locator("xpath=ancestor::div[1]");
                            if (parentOption.count() > 0) {
                                String parentText = parentOption.textContent();
                                if (parentText != null && parentText.contains("混元") && parentText.contains("长文本")) {
                                    modelOption = parentOption;
                                    optionFound = true;
                                    log.info("[元器模型选择] 通过'长文本'关键词找到模型选项: {}", parentText);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("[元器模型选择] 文本查找失败: {}", e.getMessage());
                }
            }

            // 策略2：如果没找到或不可见，尝试滚动查找
            if (!optionFound) {
                log.info("[元器模型选择] 开始滚动查找模型: {}", cleanModelName);
                Locator foundOption = scrollAndFindModelOption(page, dropdownContainer, cleanModelName);
                if (foundOption != null && foundOption.count() > 0) {
                    modelOption = foundOption;
                    optionFound = true;
                }
            }

            // 策略3：如果滚动后仍没找到，尝试更宽松的模糊匹配
            if (!optionFound) {
                log.debug("[元器模型选择] 尝试更宽松的模糊匹配");
                try {
                    Locator allOptions = null;
                    if (dropdownContainer != null && dropdownContainer.count() > 0) {
                        allOptions = dropdownContainer.locator("div[role='option'], li[role='option'], [class*='option'], [class*='item'], div[class*='semi-select-option']");
                    } else {
                        allOptions = page.locator("div[role='option'], li[role='option'], [class*='option'], [class*='item'], div[class*='semi-select-option']");
                    }
                    
                    optionCount = allOptions.count();
                    for (int i = 0; i < optionCount; i++) {
                        try {
                            Locator option = allOptions.nth(i);
                            String optionText = option.textContent();
                            if (optionText != null) {
                                // 更宽松的匹配：只要包含关键部分即可
                                String cleanOptionText = optionText.replaceAll("\\s*\\d+[Kk]\\s*$", "").trim();
                                // 提取模型名称的关键词（去掉"混元"、"大模型"等通用词）
                                String[] keywords = cleanModelName.split("\\s+");
                                boolean allKeywordsMatch = true;
                                for (String keyword : keywords) {
                                    if (keyword.length() > 1 && !cleanOptionText.contains(keyword)) {
                                        allKeywordsMatch = false;
                                        break;
                                    }
                                }
                                if (allKeywordsMatch && cleanOptionText.length() > 0) {
                                    modelOption = option;
                                    optionFound = true;
                                    log.info("[元器模型选择] 通过宽松匹配找到模型: {} (原始文本: {})", cleanOptionText, optionText);
                                    break;
                                }
                            }
                        } catch (Exception e) {
                            // 忽略单个选项的错误
                        }
                    }
                } catch (Exception e) {
                    log.debug("[元器模型选择] 宽松匹配失败: {}", e.getMessage());
                }
            }

            if (!optionFound || modelOption == null) {
                log.error("[元器模型选择] 未找到模型选项: {} (已尝试滚动查找)", modelName);
                return false;
            }

            // 4️⃣ 点击模型选项（统一由我们手动点击，不依赖搜索框自动选择）
                try {
                    // 滚动到选项可见
                    modelOption.scrollIntoViewIfNeeded();
                    page.waitForTimeout(300);

                    // 点击模型选项
                    modelOption.click();
                    log.info("[元器模型选择] 已点击模型选项: {}", modelName);
                    page.waitForTimeout(1000); // 等待选择生效
                } catch (Exception e) {
                    log.error("[元器模型选择] 点击模型选项失败: {}", e.getMessage());
                    return false;
            }

            // 5️⃣ 强校验：模型名必须出现在右侧配置面板（忽略128K等后缀）
            String cleanModelNameForVerify = modelName.replaceAll("\\s*\\d+[Kk]\\s*$", "").trim();
            boolean verifySuccess = false;
            for (int i = 0; i < 5; i++) {
                // 检查是否包含清理后的模型名称
                Locator modelText = page.getByText(cleanModelNameForVerify).first();
                if (modelText.count() > 0) {
                    log.info("[元器模型选择] 模型选择成功: {} (验证文本: {})", modelName, cleanModelNameForVerify);
                    verifySuccess = true;
                    break;
                }
                page.waitForTimeout(500);
            }

            if (!verifySuccess) {
                log.error("[元器模型选择] 点击后未检测到模型文本: {} (验证文本: {})", modelName, cleanModelNameForVerify);
                return false;
            }
            
            // 6️⃣ 如果提供了参数配置，直接在下拉框中编辑参数（不需要再打开高级设置）
            if (config != null && (config.containsKey("temperature") || config.containsKey("topP") || config.containsKey("maxTokens"))) {
                log.info("[元器模型选择] 开始直接编辑参数（在下拉框中）");
                
                // 确保下拉框仍然打开（如果已关闭，需要重新打开）
                boolean dropdownOpen = false;
                try {
                    Locator checkDropdown = page.locator("div.ant-select-dropdown:visible, [role='listbox']:visible").first();
                    if (checkDropdown.count() > 0 && checkDropdown.isVisible()) {
                        dropdownOpen = true;
                    }
                } catch (Exception e) {
                    // 忽略
                }
                
                // 如果下拉框已关闭，需要重新打开（点击模型下拉框）
                if (!dropdownOpen) {
                    log.debug("[元器模型选择] 下拉框已关闭，重新打开以编辑参数");
                    try {
                        Locator modelDropdownForParams = page.locator(".model_select .ant-select").first();
                        if (modelDropdownForParams.count() > 0 && modelDropdownForParams.isVisible()) {
                            Locator arrow = modelDropdownForParams.locator(".ant-select-arrow").first();
                            if (arrow.count() > 0 && arrow.isVisible()) {
                                arrow.click();
                            } else {
                                modelDropdownForParams.click();
                            }
                            page.waitForTimeout(1500); // 等待下拉框打开
                        }
                    } catch (Exception e) {
                        log.warn("[元器模型选择] 重新打开下拉框失败: {}", e.getMessage());
                    }
                }
                
                // 在下拉框中编辑参数
                // 填充温度（Temperature）
                if (config.containsKey("temperature")) {
                    Object tempValue = config.get("temperature");
                    log.debug("[元器模型选择] 设置温度: {}", tempValue);
                    fillAdvancedParameter(page, "温度", tempValue);
                    page.waitForTimeout(300);
                }

                // 填充TopP
                if (config.containsKey("topP")) {
                    Object topPValue = config.get("topP");
                    log.debug("[元器模型选择] 设置TopP: {}", topPValue);
                    fillAdvancedParameter(page, "Top P", topPValue);
                    page.waitForTimeout(300);
                }

                // 填充最大回复Token（可选，部分模型没有此参数）
                if (config.containsKey("maxTokens")) {
                    Object maxTokensValue = config.get("maxTokens");
                    log.debug("[元器模型选择] 设置最大回复Token: {}", maxTokensValue);
                    fillAdvancedParameter(page, "最大回复Token", maxTokensValue);
                    page.waitForTimeout(300);
                }
                
                // 编辑完参数后，点击旁边的空白处关闭下拉框
                log.debug("[元器模型选择] 参数编辑完成，点击空白处关闭下拉框");
                closeAdvancedSettings(page); // 复用关闭高级设置的方法（点击空白处）
            }
            
            return true;

        } catch (Exception e) {
            log.error("[元器模型选择] 异常", e);
            return false;
        }
    }

    /**
     * 在下拉列表中滚动查找模型选项
     * 
     * @param page 页面对象
     * @param dropdownContainer 下拉列表容器（可为null，会在页面中查找）
     * @param modelName 模型名称
     * @return 找到的模型选项Locator，如果未找到返回null
     */
    private Locator scrollAndFindModelOption(Page page, Locator dropdownContainer, String modelName) {
        try {
            log.debug("[元器模型选择] 开始滚动查找模型选项: {}", modelName);

            // 确定滚动容器（只在真正的下拉列表容器内滚动，不再进行整个页面级别的滚动）
            Locator scrollContainer = dropdownContainer;
            if (scrollContainer == null || scrollContainer.count() == 0) {
                // 如果没提供容器，尝试查找可滚动的下拉列表（使用 last()，优先最新弹出的下拉）
                String[] scrollSelectors = {
                    "[role='listbox']",
                    "[class*='popover']",
                    "[class*='dropdown']",
                    "[class*='select-dropdown']",
                    "[class*='menu']",
                    "div[class*='semi-select']",
                    "div[class*='semi-dropdown']"
                };
                for (String selector : scrollSelectors) {
                    try {
                        Locator container = page.locator(selector).last();
                        if (container.count() > 0 && container.isVisible()) {
                            scrollContainer = container;
                            log.debug("[元器模型选择] 找到滚动容器: {}", selector);
                            break;
                        }
                    } catch (Exception e) {
                        // 忽略
                    }
                }
            }

            // 如果还是没找到容器，就不再做页面级别滚动，直接放弃滚动查找，避免误触其他区域
            if (scrollContainer == null || scrollContainer.count() == 0) {
                log.warn("[元器模型选择] 未找到下拉列表滚动容器，放弃滚动查找，避免对页面其他区域进行误滚动");
                return null;
            }

            // 获取所有选项
            Locator allOptions = scrollContainer.locator("div[role='option'], li[role='option'], [class*='option'], [class*='item'], div[class*='semi-select-option']");

            int optionCount = allOptions.count();
            log.debug("[元器模型选择] 下拉列表中共有 {} 个选项", optionCount);

            if (optionCount == 0) {
                log.warn("[元器模型选择] 下拉列表中未找到任何选项");
                return null;
            }

            // 滚动查找策略：先检查当前可见区域，然后只向下滚动查找（不上下翻动）
            int maxScrollAttempts = 20; // 减少滚动次数，最多滚动20次
            int scrollDistance = 200; // 增加每次滚动距离，提高效率

            // 先检查当前可见区域（忽略128K等后缀）
            for (int i = 0; i < optionCount; i++) {
                try {
                    Locator option = allOptions.nth(i);
                    if (option.isVisible()) {
                        String optionText = option.textContent();
                        if (optionText != null) {
                            // 清理选项文本（去掉128K等后缀）
                            String cleanOptionText = optionText.replaceAll("\\s*\\d+[Kk]\\s*$", "").trim();
                            // 匹配：选项包含清理后的模型名称，或者完全匹配
                            if (cleanOptionText.contains(modelName) || modelName.contains(cleanOptionText)) {
                                log.info("[元器模型选择] 在当前可见区域找到模型选项: {} (索引: {}, 原始文本: {})", cleanOptionText, i, optionText);
                                return option;
                            }
                        }
                    }
                } catch (Exception e) {
                    // 忽略单个选项的错误，继续查找
                }
            }

            // 为了保证“鼠标先进入下拉框再滚动”，在滚动前先把鼠标移动到滚动容器中心
                try {
                    BoundingBox box = scrollContainer.boundingBox();
                    if (box != null) {
                        double centerX = box.x + box.width / 2.0;
                        double centerY = box.y + box.height / 2.0;
                        page.mouse().move(centerX, centerY);
                        log.debug("[元器模型选择] 鼠标已移动到滚动容器中心 ({}, {})", centerX, centerY);
                    }
                } catch (Exception e) {
                    log.debug("[元器模型选择] 将鼠标移动到滚动容器中心失败: {}", e.getMessage());
            }

            // 只向下滚动查找（不上下翻动，避免不必要的滚动）
            for (int attempt = 0; attempt < maxScrollAttempts; attempt++) {
                // 滚动：只在当前下拉容器内使用真实鼠标滚轮，不再做页面级滚动
                    try {
                        BoundingBox boundingBox = scrollContainer.boundingBox();
                        if (boundingBox != null) {
                            double centerX = boundingBox.x + boundingBox.width / 2.0;
                            double centerY = boundingBox.y + boundingBox.height / 2.0;
                            page.mouse().move(centerX, centerY);
                            page.mouse().wheel(0, scrollDistance);
                            log.debug("[元器模型选择] 在下拉容器内使用鼠标滚轮向下滚动 {}", scrollDistance);
                        } else {
                        log.debug("[元器模型选择] boundingBox 为空，无法安全滚动下拉容器，停止滚动查找");
                        break;
                        }
                    } catch (Exception e) {
                    log.debug("[元器模型选择] 容器内滚轮滚动失败，停止滚动查找: {}", e.getMessage());
                    break;
                }
                
                page.waitForTimeout(300); // 等待滚动完成和DOM更新

                // 检查当前可见区域是否有目标模型（忽略128K等后缀）
                for (int i = 0; i < optionCount; i++) {
                    try {
                        Locator option = allOptions.nth(i);
                        if (option.isVisible()) {
                            String optionText = option.textContent();
                            if (optionText != null) {
                                // 清理选项文本（去掉128K等后缀）
                                String cleanOptionText = optionText.replaceAll("\\s*\\d+[Kk]\\s*$", "").trim();
                                // 匹配：选项包含清理后的模型名称，或者完全匹配
                                if (cleanOptionText.contains(modelName) || modelName.contains(cleanOptionText)) {
                                    log.info("[元器模型选择] 通过滚动找到模型选项: {} (索引: {}, 滚动次数: {}, 原始文本: {})", cleanOptionText, i, attempt + 1, optionText);
                                    // 确保选项完全可见
                                    option.scrollIntoViewIfNeeded();
                                    page.waitForTimeout(200);
                                    return option;
                                }
                            }
                        }
                    } catch (Exception e) {
                        // 忽略单个选项的错误，继续查找
                    }
                }

                // 每5次滚动后，记录进度（不再向上滚动，避免不必要的上下翻动）
                if (attempt > 0 && attempt % 5 == 0) {
                    log.debug("[元器模型选择] 已向下滚动 {} 次，未找到目标模型", attempt + 1);
                }
            }

            log.warn("[元器模型选择] 滚动查找未找到模型: {} (已滚动 {} 次)", modelName, maxScrollAttempts);
            return null;

        } catch (Exception e) {
            log.error("[元器模型选择] 滚动查找异常: {}", e.getMessage(), e);
            return null;
        }
    }


    /**
     * 编辑大模型节点
     *
     * 操作流程（根据实际元器平台UI修正）：
     * 1. 等待工作流画布加载完成
     * 2. 定位并点击指定节点（通过节点名称如"混元大模型"、"DeepSeek大模型"）
     * 3. 等待右侧配置面板出现
     * 4. 选择模型（如果需要）
     * 5. 点击"高级设置"打开参数弹窗
     * 6. 填充Temperature、TopP、MaxTokens（MaxTokens可选，部分模型没有）
     * 7. 点击确定关闭弹窗
     * 8. 填充提示词
     *
     * @param page 工作流编辑页面
     * @param nodeName 节点名称（如"精调大模型"、"混元大模型"）
     * @param config 配置参数（modelName, temperature, topP, maxTokens, prompt）
     * @return 编辑结果
     */
    public NodeEditResult editLLMNode(Page page, String nodeName, Map<String, Object> config) {
        return editLLMNode(page, nodeName, config, null);
    }

    /**
     * 编辑大模型节点（带workflowName参数）
     *
     * @param page 工作流编辑页面
     * @param nodeName 节点名称（如"精调大模型"、"混元大模型"）
     * @param config 配置参数（modelName, temperature, topP, maxTokens, prompt）
     * @param workflowName 工作流名称（用于判断是否需要特殊处理提示词）
     * @return 编辑结果
     */
    public NodeEditResult editLLMNode(Page page, String nodeName, Map<String, Object> config, String workflowName) {
        return editLLMNode(page, nodeName, config, workflowName, null);
    }

    /**
     * 编辑大模型节点（带workflowName参数 + 总超时兜底）
     *
     * @param page 工作流编辑页面
     * @param nodeName 节点名称（如"精调大模型"、"混元大模型"）
     * @param config 配置参数（modelName, temperature, topP, maxTokens, prompt）
     * @param workflowName 工作流名称（用于判断是否需要特殊处理提示词）
     * @param timeoutSeconds 总超时（秒），为空或<=0则默认90秒
     * @return 编辑结果
     */
    public NodeEditResult editLLMNode(Page page, String nodeName, Map<String, Object> config, String workflowName, Integer timeoutSeconds) {
        try {
            int timeout = (timeoutSeconds != null && timeoutSeconds > 0) ? timeoutSeconds : 90;
            long deadlineMs = System.currentTimeMillis() + timeout * 1000L;
            log.info("[元器节点编辑] 开始编辑节点: {} (超时: {}秒)", nodeName, timeout);

            // 参数校验
            if (nodeName == null || nodeName.isEmpty()) {
                return new NodeEditResult(false, "节点名称不能为空", null);
            }
            if (config == null || config.isEmpty()) {
                return new NodeEditResult(false, "配置参数不能为空", nodeName);
            }
            if (System.currentTimeMillis() > deadlineMs) {
                return new NodeEditResult(false, "节点编辑超时（已等待" + timeout + "秒）", nodeName);
            }

            // 步骤1：等待画布加载
            log.debug("[元器节点编辑] 步骤1: 等待工作流画布加载...");
            page.waitForLoadState();
            safeWait(page, 1000, deadlineMs);

            // ① 以屏幕中心缩小画布（等价 Ctrl + 滚轮）
            zoomCanvasFromCenter(page, -8);
            if (System.currentTimeMillis() > deadlineMs) {
                return new NodeEditResult(false, "节点编辑超时（已等待" + timeout + "秒）", nodeName);
            }



            // 步骤2：定位并点击节点
            log.debug("[元器节点编辑] 步骤2: 定位节点: {}", nodeName);
            Locator node = findNode(page, nodeName);
            if (node == null || node.count() == 0) {
                log.warn("[元器节点编辑] 未找到节点: {}", nodeName);
                return new NodeEditResult(false, "未找到节点: " + nodeName, nodeName);
            }

            // 单击选中节点，打开右侧配置面板
            log.debug("[元器节点编辑] 点击选中节点");
            node.first().click();
            safeWait(page, 1000, deadlineMs);
            if (System.currentTimeMillis() > deadlineMs) {
                return new NodeEditResult(false, "节点编辑超时（已等待" + timeout + "秒）", nodeName);
            }

            // 步骤3：等待右侧配置面板出现
            log.debug("[元器节点编辑] 步骤3: 等待配置面板出现");
            
            // 尝试等待配置面板的关键元素出现（如"模型"、"提示词"等）
            try {
                Locator configPanel = page.locator("div:has-text('模型'), div:has-text('提示词'), div:has-text('输入变量')").first();
                if (configPanel.count() > 0) {
                    long remainingMs = Math.max(1, deadlineMs - System.currentTimeMillis());
                    long waitMs = Math.min(5000, remainingMs);
                    configPanel.waitFor(new Locator.WaitForOptions()
                            .setTimeout((double) waitMs)
                            .setState(WaitForSelectorState.VISIBLE));
                    log.debug("[元器节点编辑] 配置面板已出现");
                }
            } catch (Exception e) {
                log.debug("[元器节点编辑] 等待配置面板超时，继续执行: {}", e.getMessage());
            }
            if (System.currentTimeMillis() > deadlineMs) {
                return new NodeEditResult(false, "节点编辑超时（已等待" + timeout + "秒）", nodeName);
            }

            // 步骤4：打开“选择模型/参数”面板，在面板内选择模型并编辑参数，最后点击空白处关闭（新界面流程）
            boolean needModelOrParams =
                    config.containsKey("modelName") || config.containsKey("temperature") || config.containsKey("topP") || config.containsKey("maxTokens");
            if (needModelOrParams) {
                // 🔴 核心：等待模型区域出现（受总超时限制）
                long remainingMs = Math.max(1, deadlineMs - System.currentTimeMillis());
                int waitModelAreaMs = (int) Math.min(2000, remainingMs);
                boolean modelAreaReady = waitForModelArea(page, waitModelAreaMs);
                if (!modelAreaReady) {
                    log.error("[元器节点编辑] 模型选择区域未渲染，终止后续操作");
                    return new NodeEditResult(false, "未检测到选择框：模型选择区域未加载完成", nodeName);
                }

                // 1) 打开“选择模型/参数”面板
                if (!openAdvancedSettings(page)) {
                    return new NodeEditResult(false, "打开模型参数面板失败", nodeName);
                }

                // 2) 在面板内选择模型（如果需要）
                if (config.containsKey("modelName")) {
                    String modelName = String.valueOf(config.get("modelName"));
                    log.info("[元器节点编辑] 准备在面板内选择模型: {}", modelName);
                    boolean modelSelected = selectModelInAdvancedPanel(page, modelName);
                    if (!modelSelected) {
                        closeAdvancedSettings(page);
                        return new NodeEditResult(false, "模型选择失败: " + modelName, nodeName);
                    }
                }

                // 3) 编辑三个参数（优化：只在第一次打开面板，然后连续设置所有参数）
                // 检查是否需要设置参数
                boolean needParams = config.containsKey("temperature") || 
                                    config.containsKey("topP") || 
                                    config.containsKey("maxTokens");
                
                if (needParams) {
                    // 只在第一次打开面板（如果之前没有打开过，或者面板已关闭）
                    boolean panelAlreadyOpen = false;
                    try {
                        Locator panelKey = page.locator(
                                "text=选择模型, text=重置参数, text=参数, text=温度, text=Top P, text=最大回复Token"
                        ).first();
                        if (panelKey.count() > 0 && panelKey.isVisible()) {
                            panelAlreadyOpen = true;
                            log.debug("[元器节点编辑] 面板已打开，无需重复打开");
                        }
                    } catch (Exception e) {
                        log.debug("[元器节点编辑] 检测面板状态失败，将重新打开: {}", e.getMessage());
                    }
                    
                    // 如果面板未打开，则打开一次
                    if (!panelAlreadyOpen) {
                        log.debug("[元器节点编辑] 面板未打开，打开面板以设置参数");
                        if (!openAdvancedSettings(page)) {
                            log.warn("[元器节点编辑] 打开面板失败，跳过参数设置");
                        } else {
                            safeWait(page, 200, deadlineMs); // 等待面板完全打开
                        }
                    }
                    
                    // 连续设置所有参数（面板已打开，不需要重复打开）
                if (config.containsKey("temperature")) {
                    Object tempValue = config.get("temperature");
                    log.debug("[元器节点编辑] 设置温度: {}", tempValue);
                    fillAdvancedParameter(page, "温度", tempValue);
                        safeWait(page, 80, deadlineMs); // 缩短等待时间
                }

                if (config.containsKey("topP")) {
                    Object topPValue = config.get("topP");
                    log.debug("[元器节点编辑] 设置TopP: {}", topPValue);
                    fillAdvancedParameter(page, "Top P", topPValue);
                        safeWait(page, 80, deadlineMs); // 缩短等待时间
                }

                if (config.containsKey("maxTokens")) {
                    Object maxTokensValue = config.get("maxTokens");
                    log.debug("[元器节点编辑] 设置最大回复Token: {}", maxTokensValue);
                    fillAdvancedParameter(page, "最大回复Token", maxTokensValue);
                        safeWait(page, 80, deadlineMs); // 缩短等待时间
                    }
                }

                // 4) 不再点击"确定"，改为点击旁边空白处关闭（会触发自动保存）
                closeAdvancedSettings(page);
                safeWait(page, 800, deadlineMs); // 增加等待时间，确保面板完全关闭
            }
            if (System.currentTimeMillis() > deadlineMs) {
                return new NodeEditResult(false, "节点编辑超时（已等待" + timeout + "秒）", nodeName);
            }

            // 步骤8：向下滑动检测提示词文本框，然后填充提示词（不记录具体内容到日志，保护敏感信息）
            if (config.containsKey("prompt")) {
                String prompt = String.valueOf(config.get("prompt"));
                log.info("[元器节点编辑] 步骤8: 开始向下滑动检测提示词文本框，然后填充提示词 (长度: {} 字符, workflowName: {})", prompt.length(), workflowName);
                
                // 在处理提示词之前，先将整个页面缩小50%
                try {
                    log.debug("[元器节点编辑] 步骤8.1: 缩小页面50% (zoom=0.5)");
                    page.evaluate("document.body.style.zoom = '0.5'");
                    page.waitForTimeout(1000); // 等待渲染调整
                } catch (Exception e) {
                    log.warn("[元器节点编辑] 页面缩小失败: {}", e.getMessage());
                }
                
                // 确保配置面板仍然打开（如果关闭了需要重新点击节点）
                try {
                    Locator configPanel = page.locator("div:has-text('模型'), div:has-text('提示词'), div:has-text('输入变量')").first();
                    if (configPanel.count() == 0 || !configPanel.isVisible()) {
                        log.warn("[元器节点编辑] 配置面板已关闭，重新点击节点打开");
                        // 这里不能重复声明与上方同名的 node 变量，否则会编译报错
                        Locator nodeForPrompt = findNode(page, nodeName);
                        if (nodeForPrompt != null && nodeForPrompt.count() > 0) {
                            nodeForPrompt.first().click();
                            safeWait(page, 1000, deadlineMs);
                        }
                    }
                } catch (Exception e) {
                    log.debug("[元器节点编辑] 检查配置面板状态失败: {}", e.getMessage());
                }
                
                // 新界面：向下滑动来检测提示词文本框
                scrollToFindPromptTextarea(page);
                
                // 填充提示词（参考原始版本逻辑，确保能正确执行）
                fillPrompt(page, prompt, workflowName);
                
                log.info("[元器节点编辑] 步骤8: 提示词配置完成");
            }
            if (System.currentTimeMillis() > deadlineMs) {
                return new NodeEditResult(false, "节点编辑超时（已等待" + timeout + "秒）", nodeName);
            }

            // 步骤9：等待5秒，确保系统完成自动保存
            log.debug("[元器节点编辑] 步骤9: 等待系统自动保存 (5秒)");
            safeWait(page, 5000, deadlineMs);
            if (System.currentTimeMillis() > deadlineMs) {
                return new NodeEditResult(false, "节点编辑超时（已等待" + timeout + "秒）", nodeName);
            }

            log.info("[元器节点编辑] 节点配置完成: {}", nodeName);
            return new NodeEditResult(true, "节点配置已完成", nodeName);

        } catch (Exception e) {
            log.error("[元器节点编辑] 编辑失败 - 节点: {}", nodeName, e);
            return new NodeEditResult(false, "编辑失败: " + e.getMessage(), nodeName);
        }
    }

    /**
     * 安全等待：会根据剩余时间裁剪等待，避免超过总超时。
     */
    private void safeWait(Page page, long waitMs, long deadlineMs) {
        long remaining = deadlineMs - System.currentTimeMillis();
        if (remaining <= 0) {
            return;
        }
        long actual = Math.min(waitMs, remaining);
        if (actual > 0) {
            page.waitForTimeout(actual);
        }
    }

    /**
     * 打开高级设置界面（新界面：再次点击模型下拉框进入高级设置）
     */
    private boolean openAdvancedSettings(Page page) {
        try {
            // 新界面：再次点击“模型选择区域”进入“选择模型/参数”面板（不是打开下拉列表）
            // 查找模型下拉框（.model_select 容器内的 .ant-select）
            Locator modelDropdown = null;
            
            // 策略1：通过 .model_select 容器定位 .ant-select
            try {
                Locator dropdown = page.locator(".model_select .ant-select").first();
                if (dropdown.count() > 0 && dropdown.isVisible()) {
                    modelDropdown = dropdown;
                    log.debug("[元器高级设置] 通过 .model_select .ant-select 找到下拉框");
                }
            } catch (Exception e) {
                log.debug("[元器高级设置] CSS选择器定位失败: {}", e.getMessage());
            }
            
            // 策略2：通过"模型"标签所在的section定位下拉框
            if (modelDropdown == null || modelDropdown.count() == 0) {
                try {
                    Locator modelSection = page.locator(
                        ".panel__section:has(.section-header-title:has-text('模型')), " +
                        "div:has-text('模型'):has(.model_select)"
                    ).first();
                    
                    if (modelSection.count() > 0) {
                        Locator dropdown = modelSection.locator(".model_select .ant-select").first();
                        if (dropdown.count() > 0 && dropdown.isVisible()) {
                            modelDropdown = dropdown;
                            log.debug("[元器高级设置] 通过section定位找到下拉框");
                        }
                    }
                } catch (Exception e) {
                    log.debug("[元器高级设置] 通过section定位失败: {}", e.getMessage());
                }
            }
            
            if (modelDropdown == null || modelDropdown.count() == 0) {
                log.warn("[元器高级设置] 未找到模型下拉框");
                return false;
            }
            
            // 点击模型选择器（selector）打开“选择模型/参数”面板
            modelDropdown.scrollIntoViewIfNeeded();
            // 打开面板的等待不需要太久，适当缩短
            page.waitForTimeout(100);
            try {
                Locator selector = modelDropdown.locator(".ant-select-selector").first();
                if (selector.count() > 0 && selector.isVisible()) {
                    selector.click();
                } else {
                    modelDropdown.click();
                }
            } catch (Exception e) {
                modelDropdown.click();
            }
            page.waitForTimeout(250);

            // 等待面板关键元素出现：如“选择模型/参数/重置参数/温度”等（进一步缩短等待，只做一次轻量检测）
            try {
                Locator panelKey = page.locator(
                        "text=选择模型, text=重置参数, text=参数, text=温度"
                ).first();
                if (panelKey.count() > 0) {
                panelKey.waitFor(new Locator.WaitForOptions()
                            .setTimeout(800.0)
                        .setState(WaitForSelectorState.VISIBLE));
                }
            } catch (Exception ignore) {
                // 允许继续：后续 selectModelInAdvancedPanel/fillAdvancedParameter 会再兜底
            }

            log.debug("[元器高级设置] 已点击模型选择器，面板应已打开");
            return true;
        } catch (Exception e) {
            log.warn("[元器高级设置] 打开失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 确保“选择模型/参数”面板处于打开状态。
     *
     * - 如果面板已经打开：只做一次轻量级检测，不再重复点击任何按钮；
     * - 如果面板因为某些原因被自动关闭：再调用一次 openAdvancedSettings 重新打开。
     */
    private boolean ensureAdvancedSettingsOpen(Page page) {
        try {
            // 通过面板内的一些关键文案来判断面板是否仍然打开
            Locator panelKey = page.locator(
                    "text=选择模型, text=重置参数, text=参数, text=温度, text=Top P, text=最大回复Token"
            ).first();
            if (panelKey.count() > 0 && panelKey.isVisible()) {
                // 面板仍然打开，不做任何点击
                return true;
            }
        } catch (Exception e) {
            // 检测失败时，按“可能已关闭”处理，走兜底打开逻辑
            log.debug("[元器高级设置] 检测面板状态失败，尝试重新打开: {}", e.getMessage());
        }

        // 如果关键元素不存在/不可见，说明面板可能已被关闭，这里只调用一次统一的打开逻辑
        log.debug("[元器高级设置] 检测到面板可能已关闭，尝试重新打开");
        return openAdvancedSettings(page);
    }

    /**
     * 在“选择模型/参数”面板中选择模型（面板内逻辑）
     *
     * 旧逻辑（已废弃）：点面板内下拉箭头 -> 点击搜索框 -> 输入 modelName -> Enter -> 再点击匹配项。
     * 新逻辑：点面板内“第二个下拉框” -> 打开下拉列表 -> 在下拉列表内部滚动查找包含 modelName 的选项 -> 直接点击该选项。
     *
     * 重点约束：
     * 1. 不再点击搜索框，不再往搜索框里输入，也不再依赖 Enter 进行选择。
     * 2. 所有滚动行为限定在当前模型下拉列表容器内部，避免滚动整页或面板外区域。
     */
    private boolean selectModelInAdvancedPanel(Page page, String modelName) {
        try {
            String cleanModelName = modelName.replaceAll("\\s*\\d+[Kk]\\s*$", "").trim();
            log.info("[元器模型选择] (面板内) 开始选择模型: {}", modelName);

            // 1) 找到“选择模型”面板（尽量用可见文本锚定）
            Locator panel = null;
            String[] panelSelectors = {
                    "div:has-text('选择模型'):visible",
                    "div:has-text('参数'):visible",
                    "div:has-text('重置参数'):visible",
                    ".ant-modal-content:visible",
                    ".ant-drawer-content:visible",
                    ".ant-popover:visible",
                    ".ant-popover-content:visible"
            };
            for (String s : panelSelectors) {
                Locator c = page.locator(s).first();
                if (c.count() > 0 && c.isVisible()) {
                    panel = c;
                    break;
                }
            }
            if (panel == null || panel.count() == 0) {
                log.warn("[元器模型选择] (面板内) 未找到面板容器，回退全局查找");
                panel = page.locator("body");
            }

            // 2) 在面板内找“第二个下拉框”（你截图中：上面是厂商/类型，下面这个才是真正的模型选择）
            Locator panelModelSelect;
            Locator allSelectsInPanel = panel.locator(".ant-select:visible");
            int selectCount = allSelectsInPanel.count();
            if (selectCount >= 2) {
                // 优先选第二个
                panelModelSelect = allSelectsInPanel.nth(1);
                log.debug("[元器模型选择] (面板内) 使用第2个下拉框作为模型选择 (总数: {})", selectCount);
            } else if (selectCount == 1) {
                panelModelSelect = allSelectsInPanel.first();
                log.debug("[元器模型选择] (面板内) 只有1个下拉框，使用该下拉框作为模型选择");
            } else {
                // 兜底：全局找一个可见的select
                panelModelSelect = page.locator(".ant-select:visible").first();
                log.debug("[元器模型选择] (面板内) 面板中未找到下拉框，回退到全局可见下拉框");
            }
            if (panelModelSelect.count() == 0) {
                log.error("[元器模型选择] (面板内) 未找到模型下拉框");
                return false;
            }

            panelModelSelect.scrollIntoViewIfNeeded();
            page.waitForTimeout(150);
            boolean opened = false;
            try {
                Locator arrow = panelModelSelect.locator(".ant-select-arrow").first();
                if (arrow.count() > 0 && arrow.isVisible()) {
                    arrow.click(new Locator.ClickOptions().setForce(true));
                    opened = true;
                }
            } catch (Exception ignore) {}
            if (!opened) {
                try {
                    panelModelSelect.click(new Locator.ClickOptions().setForce(true));
                } catch (Exception e) {
                    panelModelSelect.evaluate("el => el.click()");
                }
            }

            // 3) 等待下拉列表完全打开
            page.waitForTimeout(800);

            // 4) 不再点击搜索框，不输入，不按 Enter，而是在当前下拉列表中遍历 + 滚动查找目标模型选项并点击
            Locator dropdownContainer = null;
            try {
                dropdownContainer = page.locator(".ant-select-dropdown:visible, [role='listbox']:visible").last();
                if (dropdownContainer == null || dropdownContainer.count() == 0 || !dropdownContainer.isVisible()) {
                    dropdownContainer = null;
                }
            } catch (Exception ignore) {
                dropdownContainer = null;
            }

            if (dropdownContainer == null || dropdownContainer.count() == 0) {
                log.warn("[元器模型选择] (面板内) 未找到下拉列表容器，将尝试直接在页面内滚动查找模型选项");
            }

            // 4.1 先在当前可见区域内直接查找匹配选项
            Locator modelOption = null;
            try {
                Locator optionScope = (dropdownContainer != null && dropdownContainer.count() > 0)
                        ? dropdownContainer
                        : page.locator("body");

                Locator allOptions = optionScope.locator(
                        "div[role='option'], .ant-select-item-option, div.ant-select-item, " +
                        "li[role='option'], [class*='option'], [class*='item'], div[class*='semi-select-option']"
                );

                int optionCount = allOptions.count();
                log.debug("[元器模型选择] (面板内) 初始可见区域内选项数量: {}", optionCount);

                for (int i = 0; i < optionCount; i++) {
                    try {
                        Locator opt = allOptions.nth(i);
                        if (!opt.isVisible()) {
                            continue;
                        }
                        String text = opt.textContent();
                        if (text == null || text.trim().isEmpty()) {
                            continue;
                        }
                        String cleanText = text.replaceAll("\\s*\\d+[Kk]\\s*$", "").trim();
                        if (cleanText.contains(cleanModelName) || cleanModelName.contains(cleanText)) {
                            modelOption = opt;
                            log.info("[元器模型选择] (面板内) 在可见区域找到模型选项: {} (原始: {})", cleanText, text);
                            break;
                        }
                    } catch (Exception ignoreSingle) {
                        // 忽略单个选项异常
                    }
                }
            } catch (Exception e) {
                log.debug("[元器模型选择] (面板内) 初始区域查找失败: {}", e.getMessage());
            }

            // 4.2 如未找到，则在下拉容器内部进行“只向下”的滚动查找
            if (modelOption == null) {
                log.info("[元器模型选择] (面板内) 开始在下拉列表内滚动查找模型: {}", cleanModelName);
                Locator found = scrollAndFindModelOption(page, dropdownContainer, cleanModelName);
                if (found != null && found.count() > 0) {
                    modelOption = found;
                }
            }

            if (modelOption == null || modelOption.count() == 0) {
                log.error("[元器模型选择] (面板内) 滚动查找后仍未找到模型选项: {}", cleanModelName);
                return false;
            }

            // 4.3 点击找到的模型选项（只点击这一处，不再点击搜索框）
            try {
                modelOption.scrollIntoViewIfNeeded();
                        page.waitForTimeout(200);
                modelOption.click(new Locator.ClickOptions().setForce(true));
                page.waitForTimeout(600);
                log.info("[元器模型选择] (面板内) 已点击模型选项: {}", cleanModelName);
            } catch (Exception e) {
                log.error("[元器模型选择] (面板内) 点击模型选项失败: {}", e.getMessage());
                return false;
            }

            // 5) 验证：面板/右侧应出现模型名（忽略128K等后缀）
            for (int i = 0; i < 6; i++) {
                Locator modelText = page.getByText(cleanModelName).first();
                if (modelText.count() > 0) {
                    log.info("[元器模型选择] (面板内) 模型选择成功: {}", modelName);
                    return true;
                }
                page.waitForTimeout(300);
            }

            log.warn("[元器模型选择] (面板内) 未验证到模型文本: {}", cleanModelName);
            return false;
        } catch (Exception e) {
            log.error("[元器模型选择] (面板内) 异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 关闭高级设置界面（新界面：点击空白处保存并关闭）
     */
    private void closeAdvancedSettings(Page page) {
        try {
            // 新界面：点击空白处（页面其他区域）来保存并关闭高级设置界面
            // 策略1：点击配置面板外的区域（如左侧画布区域）
            try {
                // 查找左侧画布区域（通常包含工作流节点）
                Locator canvasArea = page.locator(".workflow-canvas, .canvas-area, [class*='canvas']").first();
                if (canvasArea.count() > 0 && canvasArea.isVisible()) {
                    canvasArea.click();
                    page.waitForTimeout(500);
                    log.debug("[元器高级设置] 已点击画布区域，保存并关闭高级设置");
                    return;
                }
            } catch (Exception e) {
                log.debug("[元器高级设置] 点击画布区域失败: {}", e.getMessage());
            }
            
            // 策略2：点击页面顶部或底部区域
            try {
                // 点击页面顶部（通常有标题栏或工具栏）
                Locator topArea = page.locator("header, .header, [class*='header']").first();
                if (topArea.count() > 0 && topArea.isVisible()) {
                    topArea.click();
                    page.waitForTimeout(500);
                    log.debug("[元器高级设置] 已点击顶部区域，保存并关闭高级设置");
                    return;
                }
            } catch (Exception e) {
                log.debug("[元器高级设置] 点击顶部区域失败: {}", e.getMessage());
            }
            
            // 策略3：使用鼠标点击页面中心偏左的位置（避开右侧配置面板）
            try {
                ViewportSize viewportSize = page.viewportSize();
                if (viewportSize != null) {
                    int clickX = (int) (viewportSize.width / 4); // 左侧1/4位置
                    int clickY = (int) (viewportSize.height / 2); // 垂直居中
                    page.mouse().click(clickX, clickY);
                    page.waitForTimeout(500);
                    log.debug("[元器高级设置] 已点击页面左侧区域 ({}, {})，保存并关闭高级设置", clickX, clickY);
                    return;
                }
            } catch (Exception e) {
                log.debug("[元器高级设置] 点击页面左侧区域失败: {}", e.getMessage());
            }
            
            // 策略4：备选方案 - 按ESC键关闭（如果支持）
            try {
                page.keyboard().press("Escape");
                page.waitForTimeout(500);
                log.debug("[元器高级设置] 已按ESC键，尝试关闭高级设置");
            } catch (Exception e) {
                log.debug("[元器高级设置] 按ESC键失败: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.warn("[元器高级设置] 关闭失败: {}", e.getMessage());
        }
    }

    /**
     * 填充高级设置参数（在弹窗中）
     * 参数名称使用中文，如"温度"、"Top P"、"最大回复Token"
     */
    private boolean fillAdvancedParameter(Page page, String paramLabel, Object value) {
        try {
            // 在弹窗中查找参数标签对应的输入框
            // 元器平台使用滑块+输入框的组合，输入框在标签右侧

            // 策略1：通过标签文本定位相邻的输入框
            Locator labelLocator = page.getByText(paramLabel).first();
            if (labelLocator.count() > 0) {
                // 查找同一行的输入框
                Locator input = page.locator("input[type='number'], input[type='text']")
                    .filter(new Locator.FilterOptions().setHas(page.getByText(paramLabel)));

                if (input.count() == 0) {
                    // 备选：查找标签后面的输入框
                    input = labelLocator.locator("xpath=following::input[1]");
                }

                if (input.count() > 0) {
                    // 优化：先聚焦输入框，然后清空并填充，避免点击操作可能触发面板关闭
                    Locator inputLocator = input.first();
                    // 使用 focus() 而不是 click()，减少意外触发关闭事件的风险
                    try {
                        inputLocator.focus();
                        page.waitForTimeout(50); // 短暂等待聚焦完成
                    } catch (Exception e) {
                        // 如果focus失败，再尝试click
                        log.debug("[元器参数填充] focus失败，尝试click: {}", e.getMessage());
                        inputLocator.click();
                        page.waitForTimeout(50);
                    }
                    inputLocator.fill("");
                    inputLocator.fill(String.valueOf(value));
                    log.debug("[元器参数填充] {} = {}", paramLabel, value);
                    return true;
                }
            }

            // 策略2：通过包含参数名的容器查找
            Locator container = page.locator("div:has-text('" + paramLabel + "')").first();
            if (container.count() > 0) {
                Locator input = container.locator("input").first();
                if (input.count() > 0) {
                    // 优化：先聚焦输入框，然后清空并填充，避免点击操作可能触发面板关闭
                    try {
                        input.focus();
                        page.waitForTimeout(50); // 短暂等待聚焦完成
                    } catch (Exception e) {
                        // 如果focus失败，再尝试click
                        log.debug("[元器参数填充] focus失败，尝试click: {}", e.getMessage());
                    input.click();
                        page.waitForTimeout(50);
                    }
                    input.fill("");
                    input.fill(String.valueOf(value));
                    log.debug("[元器参数填充] {} = {} (容器策略)", paramLabel, value);
                    return true;
                }
            }

            log.warn("[元器参数填充] 未找到参数输入框: {}", paramLabel);
            return false;
        } catch (Exception e) {
            log.warn("[元器参数填充] 填充失败 - 参数: {}, 错误: {}", paramLabel, e.getMessage());
            return false;
        }
    }

    /**
     * 调试工作流
     *
     * 操作流程：
     * 1. 点击调试按钮（右上角）
     * 2. 点击"去调试"按钮（下方）
     * 3. 填入调试输入参数（在对话框中）
     * 4. 点击发送按钮
     * 5. 等待调试结果
     *
     * @param page 工作流编辑页面
     * @param debugInput 调试输入参数（可选）
     * @return 调试结果
     */
    /**
     * 调试工作流
     *
     * 操作流程：
     * 1. 点击调试按钮（右上角）
     * 2. 点击"去调试"按钮（下方）
     * 3. 填入调试输入参数（在对话框中）
     * 4. 点击发送按钮
     * 5. 等待调试结果
     *
     * @param page 工作流编辑页面
     * @param debugInput 调试输入参数（可选）
     * @param userId 用户ID（用于截图上传）
     * @param timeoutSeconds 超时时间（秒），默认90秒
     * @return 调试结果
     */
    public DebugResult debugWorkflow(Page page, Map<String, Object> debugInput, String userId, Integer timeoutSeconds) {
        try {
            log.info("[元器工作流调试] 开始调试 - 当前URL: {}", page.url());

            // 1. 确保页面已加载
            try {
                page.waitForLoadState();
            } catch (Exception e) {
                log.warn("[元器工作流调试] 等待页面加载状态超时，尝试继续");
            }
            page.waitForTimeout(2000);

            // 【新增】执行缩小操作，防止页面过小导致元素遮挡
            try {
                log.debug("[元器工作流调试] 执行页面缩小操作 (zoom=0.8)");
                page.evaluate("document.body.style.zoom = '0.8'");
                page.waitForTimeout(1000); // 等待渲染调整
            } catch (Exception e) {
                log.warn("[元器工作流调试] 页面缩小失败: {}", e.getMessage());
            }

            // 步骤1：点击调试按钮
            log.debug("[元器工作流调试] 步骤1: 点击右上角调试按钮");
            Locator debugButton = findDebugButton(page);
            if (debugButton == null) {
                String screenshot = captureAndUpload(page, userId, "debug_no_btn_" + System.currentTimeMillis());
                return new DebugResult(false, "未找到调试按钮", null, screenshot);
            }
            debugButton.click();
            // 稍作等待，让右侧/下方调试面板有反应
            page.waitForTimeout(1000);

            // 步骤2：点击"去调试"按钮
            log.debug("[元器工作流调试] 步骤2: 点击'去调试'按钮");
            Locator runButton = findRunButton(page);
            if (runButton != null) {
                runButton.click();
                // 等待调试对话框出现
                page.waitForTimeout(1000);
            } else {
                log.warn("[元器工作流调试] 未找到'去调试'按钮，尝试直接进行后续操作(可能已在调试状态)");
            }

            // 步骤3：填入调试输入参数
            log.debug("[元器工作流调试] 步骤3: 填入调试参数");
            String inputContent = "";
            if (debugInput != null && !debugInput.isEmpty()) {
                // 优先获取 article_topic，否则获取第一个值
                if (debugInput.containsKey("article_topic")) {
                    inputContent = String.valueOf(debugInput.get("article_topic"));
                } else {
                    inputContent = String.valueOf(debugInput.values().iterator().next());
                }
            }

            if (!inputContent.isEmpty()) {
                // 查找输入框时内部已有 waitFor，这里直接调用
                // 【修改】增加重试机制，因为点击"去调试"后，对话框可能需要几秒钟才会渲染出来
                Locator chatInput = null;
                int maxRetries = 3;
                for (int i = 0; i < maxRetries; i++) {
                    chatInput = findChatInput(page);
                    if (chatInput != null) {
                        break;
                    }
                    if (i < maxRetries - 1) {
                        log.warn("[元器工作流调试] 第{}次未找到对话输入框，等待2秒后重试...", i + 1);
                        page.waitForTimeout(2000);
                    }
                }
                
                if (chatInput != null) {
                    chatInput.fill(inputContent);
                    page.waitForTimeout(500);

                    // 步骤4：点击发送按钮
                    page.keyboard().press("Enter");

                } else {
                    // 【修改】如果找不到对话输入框，直接报错，不再尝试旧版逻辑
                    log.error("[元器工作流调试] 未找到对话输入框");
                    String screenshot = captureAndUpload(page, userId, "debug_no_input_" + System.currentTimeMillis());
                    return new DebugResult(false, "未找到对话输入框，请检查页面结构", null, screenshot);
                }
            } else {
                // 如果没有输入内容，可能是不需要输入的调试？暂且保留
                log.warn("[元器工作流调试] 未提供调试输入内容");
            }

            // 步骤5：等待调试结果
            // 使用传入的超时时间，默认90秒
            int timeout = (timeoutSeconds != null && timeoutSeconds > 0) ? timeoutSeconds : 90;
            long timeoutMs = timeout * 1000L;
            log.debug("[元器工作流调试] 步骤5: 等待调试结果 (超时时间: {}秒)", timeout);
            long startTime = System.currentTimeMillis();
            long start = System.currentTimeMillis();

            while (System.currentTimeMillis() - start < timeoutMs) {

                // ① 优先失败态（最高优先级）
                if (isRunFailed(page)) {
                    String screenshot = captureAndUpload(page, userId, "debug_failed");
                    return new DebugResult(false, "运行失败", null, screenshot);
                }

                // ② 成功态（有输出）
                String output = extractDebugOutput(page);
                if (output != null && !output.isBlank()) {
                    String screenshot = captureAndUpload(page, userId, "debug_success");
                    return new DebugResult(true, "运行成功", output, screenshot);
                }

                // ③ 成功态（绿色“运行完成”标签 / 成功状态），参考 isRunFailed 逻辑遍历所有 Frame
                if (isRunSucceeded(page)) {
                    String finalOutput = output;
                    if (finalOutput == null || finalOutput.isBlank()) {
                        finalOutput = "运行完成（检测到成功状态标签，但未检测到额外输出内容）";
                    }
                    String screenshot = captureAndUpload(page, userId, "debug_success_done_tag");
                    return new DebugResult(true, "运行成功", finalOutput, screenshot);
                }

                page.waitForTimeout(1000);
            }

// 超时 = 失败
            String screenshot = captureAndUpload(page, userId, "debug_timeout");
            return new DebugResult(false, "调试超时（已等待" + timeout + "秒）", null, screenshot);


        } catch (Exception e) {
            log.error("[元器工作流调试] 调试失败", e);
            String screenshot = captureAndUpload(page, userId, "debug_exception_" + System.currentTimeMillis());
            return new DebugResult(false, "调试失败: " + e.getMessage(), null, screenshot);
        }
    }

    /**
     * 发布智能体（完整流程：导航 + 发布）
     *
     * @param page 页面对象
     * @param spaceName 空间名称（如"个人空间"）
     * @param agentName 智能体名称（如"日更助手"）
     * @return 发布结果
     */
    public PublishResult publishAgent(Page page, String spaceName, String agentName) {
        return publishAgent(page, spaceName, agentName, null);
    }

    /**
     * 发布智能体（带超时时间）
     *
     * @param page 页面实例
     * @param spaceName 空间名称
     * @param agentName 智能体名称
     * @param timeoutSeconds 超时时间（秒），默认90秒
     * @return 发布结果
     */
    public PublishResult publishAgent(Page page, String spaceName, String agentName, Integer timeoutSeconds) {
        try {
            log.info("[元器发布流程] 开始执行: 空间={}, 智能体={}, 超时={}秒", spaceName, agentName, 
                    timeoutSeconds != null ? timeoutSeconds : 90);

            // 1. 导航到智能体详情页
            boolean navSuccess = navigateToAgentDetail(page, spaceName, agentName);
            if (!navSuccess) {
                return new PublishResult(false, "导航失败: 未找到指定空间或智能体");
            }

            // 2. 执行发布操作
            return publishWorkflow(page, timeoutSeconds);

        } catch (Exception e) {
            log.error("[元器发布流程] 执行异常", e);
            return new PublishResult(false, "流程异常: " + e.getMessage());
        }
    }

    /**
     * 发布工作流（从智能体详情页）
     *
     * 操作流程（根据实际元器平台UI）：
     * 1. 检查右上角"发布"按钮是否可点击
     *    - 如果按钮为灰色/禁用状态，返回"暂无可发布的工作流"
     * 2. 点击"发布"按钮，进入发布详情页
     * 3. 滚动到页面底部（使用鼠标滚轮和键盘End键）
     * 4. 点击底部的"发布"按钮
     * 5. 等待发布完成，检查"已发布"状态
     *
     * 注意：此方法应在智能体详情页调用
     *
     * @param page 智能体详情页面
     * @param timeoutSeconds 超时时间（秒），默认90秒
     * @return 发布结果
     */
    public PublishResult publishWorkflow(Page page, Integer timeoutSeconds) {
        try {
            log.info("[元器工作流发布] 开始发布");

            // 等待页面完全加载
            page.waitForLoadState();
            page.waitForTimeout(2000);

            // 步骤1：检查发布按钮状态
            log.debug("[元器工作流发布] 步骤1: 检查发布按钮状态");
            Locator publishButton = findPublishButton(page);

            // 等待按钮出现
            try {
                if (publishButton != null) {
                    publishButton.first().waitFor(new Locator.WaitForOptions().setTimeout(5000));
                }
            } catch (Exception e) {
                log.warn("[元器工作流发布] 等待发布按钮超时");
            }

            if (publishButton == null || publishButton.count() == 0) {
                return new PublishResult(false, "未找到发布按钮");
            }

            // 检查按钮是否为禁用状态
            boolean isDisabled = false;
            try {
                Locator firstButton = publishButton.first();
                String disabledAttr = firstButton.getAttribute("disabled");
                String className = firstButton.getAttribute("class");
                isDisabled = disabledAttr != null ||
                        (className != null && (className.contains("disabled") || className.contains("gray") || className.contains("is-disabled")));
            } catch (Exception e) {
                log.debug("[元器工作流发布] 无法检测按钮状态: {}", e.getMessage());
            }

            if (isDisabled) {
                log.info("[元器工作流发布] 发布按钮为禁用状态，暂无可发布的工作流");
                return new PublishResult(false, "暂无可发布的工作流");
            }

            // 步骤2：点击发布按钮
            log.debug("[元器工作流发布] 步骤2: 点击发布按钮");
            publishButton.first().click();

            // 步骤3：等待发布页面加载
            log.debug("[元器工作流发布] 步骤3: 等待发布页面加载");
            page.waitForLoadState();
            page.waitForTimeout(3000); // 增加等待时间

            // 步骤4：先缩小页面，确保底部按钮可见
            log.debug("[元器工作流发布] 步骤4: 执行页面缩小操作 (zoom=0.8)");
            boolean zoomApplied = false;
            try {
                page.evaluate("document.body.style.zoom = '0.8'");
                page.waitForTimeout(2000); // 等待渲染调整
                zoomApplied = true;
                log.debug("[元器工作流发布] 页面缩小成功");
            } catch (Exception e) {
                log.warn("[元器工作流发布] 页面缩小失败: {}", e.getMessage());
            }

            // 步骤5：滚动到页面底部
            log.debug("[元器工作流发布] 步骤5: 滚动到页面底部");

            // 方法1：鼠标滚轮滚动（模拟用户操作）
            try {
                // 聚焦页面
                page.mouse().click(100, 100);
                for (int i = 0; i < 15; i++) {
                    page.mouse().wheel(0, 500);
                    page.waitForTimeout(200);
                }
                log.debug("[元器工作流发布] 鼠标滚动完成");
            } catch (Exception e) {
                log.warn("[元器工作流发布] 鼠标滚动失败", e);
            }

            // 方法2：键盘操作（End键）
            try {
                page.keyboard().press("End");
                page.waitForTimeout(1000);
                log.debug("[元器工作流发布] End键滚动完成");
            } catch (Exception e) {
                log.warn("[元器工作流发布] 键盘滚动失败", e);
            }

            // 方法3：JS滚动（作为兜底，多次滚动确保到底部）
            try {
                page.evaluate("window.scrollTo(0, document.body.scrollHeight)");
                page.waitForTimeout(500);
                page.evaluate("window.scrollTo(0, document.body.scrollHeight)");
                page.waitForTimeout(500);
                page.evaluate("window.scrollTo(0, document.documentElement.scrollHeight)");
                page.waitForTimeout(1000);
                log.debug("[元器工作流发布] JS滚动完成");
            } catch (Exception e) {
                log.warn("[元器工作流发布] JS滚动失败", e);
            }

            // 步骤6：查找并点击底部发布按钮
            log.debug("[元器工作流发布] 步骤6: 查找底部发布按钮");
            
            // 缩小和滚动后，等待页面稳定（增加等待时间，确保DOM完全渲染）
            page.waitForTimeout(3000);
            log.debug("[元器工作流发布] 等待页面渲染完成");
            
            // 再次确保滚动到底部（缩小后可能需要重新计算位置）
            try {
                page.evaluate("window.scrollTo(0, Math.max(document.body.scrollHeight, document.documentElement.scrollHeight))");
                page.waitForTimeout(1000);
                log.debug("[元器工作流发布] 重新滚动到底部完成");
            } catch (Exception e) {
                log.debug("[元器工作流发布] 重新滚动失败: {}", e.getMessage());
            }
            
            // 调试：输出当前页面底部区域的HTML结构（用于诊断）
            try {
                String bottomHtml = (String) page.evaluate("() => { " +
                        "const footer = document.querySelector('.release-content-footer'); " +
                        "if (footer) return footer.outerHTML.substring(0, 500); " +
                        "return '未找到.release-content-footer'; " +
                        "}");
                log.debug("[元器工作流发布] 底部区域HTML: {}", bottomHtml);
            } catch (Exception e) {
                log.debug("[元器工作流发布] 获取底部HTML失败: {}", e.getMessage());
            }
            
            // 根据实际HTML结构，使用更精确的选择器
            // HTML结构: <div class="release-content-footer"><div class="button-container"><button class="v-button v-button--primary">发布</button></div></div>
            Locator bottomPublishButton = null;
            String[] bottomSelectors = {
                    // 优先使用精确的选择器（不使用:has-text，因为可能不稳定）
                    ".release-content-footer .v-button--primary",
                    ".release-content-footer button.v-button--primary",
                    ".button-container .v-button--primary",
                    ".button-container button.v-button--primary",
                    "[class*='release-content-footer'] [class*='button-container'] button.v-button--primary",
                    // 使用getByText作为备选
                    "button.v-button--primary",
                    "[class*='release-content-footer'] button",
                    "[class*='footer'] button.v-button--primary"
            };

            // 方法1：使用精确选择器 + 文本匹配
            for (String selector : bottomSelectors) {
                try {
                    log.debug("[元器工作流发布] 尝试选择器: {}", selector);
                    Locator btn = page.locator(selector);
                    int count = btn.count();
                    log.debug("[元器工作流发布] 选择器 {} 找到 {} 个元素", selector, count);
                    
                    if (count > 0) {
                        // 遍历所有匹配的元素，查找包含"发布"文本的按钮
                        for (int i = 0; i < count; i++) {
                            try {
                                Locator btnItem = btn.nth(i);
                                // 检查文本内容
                                try {
                                    String text = btnItem.textContent();
                                    log.debug("[元器工作流发布] 按钮 {} (选择器: {}, 索引: {}) 文本内容: {}", 
                                            i, selector, i, text);
                                    if (text != null && text.contains("发布")) {
                                        // 尝试检查可见性和可点击性
                                        boolean isUsable = false;
                                        try {
                                            // 先检查是否可见
                                            if (btnItem.isVisible()) {
                                                isUsable = true;
                                                log.debug("[元器工作流发布] 按钮 {} 可见", i);
                                            } else {
                                                // 即使不可见，也尝试使用（可能因为缩小导致可见性检测不准确）
                                                log.debug("[元器工作流发布] 按钮 {} 不可见，但将尝试使用", i);
                                                isUsable = true;
                                            }
                                        } catch (Exception e) {
                                            // 如果检查可见性失败，直接使用（可能是因为缩小导致）
                                            log.debug("[元器工作流发布] 按钮 {} 可见性检查失败，将尝试使用: {}", i, e.getMessage());
                                            isUsable = true;
                                        }
                                        
                                        // 如果可用，记录并使用
                                        if (isUsable) {
                                            bottomPublishButton = btnItem;
                                            log.info("[元器工作流发布] 找到发布按钮 (选择器: {}, 索引: {}, 文本: {})", 
                                                    selector, i, text);
                                            break;
                                        }
                                    }
                                } catch (Exception e) {
                                    log.debug("[元器工作流发布] 获取按钮文本失败 (索引: {}): {}", i, e.getMessage());
                                }
                            } catch (Exception e) {
                                log.debug("[元器工作流发布] 检查按钮索引 {} 失败: {}", i, e.getMessage());
                            }
                        }
                        if (bottomPublishButton != null) {
                            break;
                        }
                    }
                } catch (Exception e) {
                    log.debug("[元器工作流发布] 查找按钮选择器 {} 失败: {}", selector, e.getMessage());
                }
            }

            // 方法2：使用getByText（如果方法1失败）
            if (bottomPublishButton == null) {
                log.debug("[元器工作流发布] 尝试使用getByText查找发布按钮");
                try {
                    Locator btnByText = page.getByText("发布", new Page.GetByTextOptions().setExact(false));
                    int count = btnByText.count();
                    log.debug("[元器工作流发布] getByText找到 {} 个包含'发布'的元素", count);
                    
                    if (count > 0) {
                        // 查找类型为button且包含v-button--primary类的元素
                        for (int i = 0; i < count; i++) {
                            try {
                                Locator btnItem = btnByText.nth(i);
                                // 检查是否是button标签
                                try {
                                    String tagName = (String) btnItem.evaluate("el => el.tagName");
                                    log.debug("[元器工作流发布] 元素 {} 标签: {}", i, tagName);
                                    
                                    if ("BUTTON".equalsIgnoreCase(tagName)) {
                                        // 检查类名
                                        try {
                                            String className = (String) btnItem.evaluate("el => el.className || ''");
                                            log.debug("[元器工作流发布] 元素 {} 类名: {}", i, className);
                                            
                                            if (className.contains("v-button--primary") || 
                                                className.contains("button") || 
                                                className.contains("primary")) {
                                                bottomPublishButton = btnItem;
                                                log.info("[元器工作流发布] 通过getByText找到发布按钮 (索引: {}, 类名: {})", i, className);
                                                break;
                                            }
                                        } catch (Exception e) {
                                            // 如果无法获取类名，但标签是button，也可以尝试
                                            bottomPublishButton = btnItem;
                                            log.info("[元器工作流发布] 通过getByText找到发布按钮（无法获取类名）(索引: {})", i);
                                            break;
                                        }
                                    }
                                } catch (Exception e) {
                                    log.debug("[元器工作流发布] 获取元素标签失败 (索引: {}): {}", i, e.getMessage());
                                }
                            } catch (Exception e) {
                                log.debug("[元器工作流发布] 检查getByText元素 {} 失败: {}", i, e.getMessage());
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("[元器工作流发布] getByText查找失败: {}", e.getMessage());
                }
            }

            // 方法3：使用JavaScript直接查找并点击（作为最后的手段）
            boolean clicked = false;
            if (bottomPublishButton != null) {
                try {
                    // 确保按钮在视口内
                    try {
                        bottomPublishButton.scrollIntoViewIfNeeded();
                        page.waitForTimeout(500);
                    } catch (Exception e) {
                        log.debug("[元器工作流发布] 滚动到按钮失败: {}", e.getMessage());
                    }
                    
                    // 点击按钮
                    bottomPublishButton.click(new Locator.ClickOptions().setTimeout(5000));
                    page.waitForTimeout(3000); // 点击后短暂等待
                    log.info("[元器工作流发布] 已点击底部发布按钮");
                    clicked = true;
                } catch (Exception e) {
                    log.warn("[元器工作流发布] 点击底部发布按钮失败: {}", e.getMessage());
                    // 继续尝试JS点击
                }
            }
            
            // 如果常规点击失败或没找到按钮，使用JavaScript点击
            if (!clicked) {
                log.debug("[元器工作流发布] 尝试使用JavaScript点击发布按钮");
                try {
                    // 先输出调试信息：查找所有可能的按钮
                    String debugInfo = (String) page.evaluate("() => { " +
                            "const result = []; " +
                            "// 查找footer " +
                            "const footer = document.querySelector('.release-content-footer'); " +
                            "result.push('footer存在: ' + (footer ? '是' : '否')); " +
                            "if (footer) { " +
                            "  const buttons = footer.querySelectorAll('button'); " +
                            "  result.push('footer中按钮数量: ' + buttons.length); " +
                            "  buttons.forEach((btn, idx) => { " +
                            "    result.push('按钮' + idx + ': 文本=' + btn.textContent + ', 类名=' + btn.className); " +
                            "  }); " +
                            "} " +
                            "// 查找所有primary按钮 " +
                            "const allPrimary = document.querySelectorAll('button.v-button--primary'); " +
                            "result.push('所有primary按钮数量: ' + allPrimary.length); " +
                            "allPrimary.forEach((btn, idx) => { " +
                            "  if (btn.textContent && btn.textContent.includes('发布')) { " +
                            "    result.push('primary按钮' + idx + '包含发布: 文本=' + btn.textContent + ', 类名=' + btn.className); " +
                            "  } " +
                            "}); " +
                            "return result.join('\\n'); " +
                            "}");
                    log.debug("[元器工作流发布] 按钮查找调试信息:\n{}", debugInfo);
                    
                    String jsClick = "(() => { " +
                            "try { " +
                            "  // 方法1: 查找footer中的primary按钮 " +
                            "  const footer = document.querySelector('.release-content-footer'); " +
                            "  if (footer) { " +
                            "    const btn = footer.querySelector('button.v-button--primary'); " +
                            "    if (btn && btn.textContent && btn.textContent.trim().includes('发布')) { " +
                            "      btn.scrollIntoView({ behavior: 'instant', block: 'center' }); " +
                            "      setTimeout(() => btn.click(), 100); " +
                            "      return 'SUCCESS-footer-primary'; " +
                            "    } " +
                            "  } " +
                            "  // 方法2: 查找footer中button-container中的primary按钮 " +
                            "  if (footer) { " +
                            "    const container = footer.querySelector('.button-container'); " +
                            "    if (container) { " +
                            "      const btn = container.querySelector('button.v-button--primary'); " +
                            "      if (btn && btn.textContent && btn.textContent.trim().includes('发布')) { " +
                            "        btn.scrollIntoView({ behavior: 'instant', block: 'center' }); " +
                            "        setTimeout(() => btn.click(), 100); " +
                            "        return 'SUCCESS-container-primary'; " +
                            "      } " +
                            "    } " +
                            "  } " +
                            "  // 方法3: 查找所有包含'发布'的primary按钮，选择最后一个（通常是底部的） " +
                            "  const buttons = Array.from(document.querySelectorAll('button.v-button--primary')); " +
                            "  for (let i = buttons.length - 1; i >= 0; i--) { " +
                            "    const btn = buttons[i]; " +
                            "    if (btn.textContent && btn.textContent.trim().includes('发布')) { " +
                            "      btn.scrollIntoView({ behavior: 'instant', block: 'center' }); " +
                            "      setTimeout(() => btn.click(), 100); " +
                            "      return 'SUCCESS-all-primary-' + i; " +
                            "    } " +
                            "  } " +
                            "  // 方法4: 查找footer中的任何按钮（包含发布） " +
                            "  if (footer) { " +
                            "    const btn = footer.querySelector('button'); " +
                            "    if (btn && btn.textContent && btn.textContent.trim().includes('发布')) { " +
                            "      btn.scrollIntoView({ behavior: 'instant', block: 'center' }); " +
                            "      setTimeout(() => btn.click(), 100); " +
                            "      return 'SUCCESS-footer-any'; " +
                            "    } " +
                            "  } " +
                            "  // 方法5: 查找所有按钮，找到包含'发布'和primary的 " +
                            "  const allButtons = Array.from(document.querySelectorAll('button')); " +
                            "  for (let i = allButtons.length - 1; i >= 0; i--) { " +
                            "    const btn = allButtons[i]; " +
                            "    const text = btn.textContent && btn.textContent.trim(); " +
                            "    const className = btn.className || ''; " +
                            "    if (text && text.includes('发布') && " +
                            "        (className.includes('primary') || className.includes('v-button--primary'))) { " +
                            "      btn.scrollIntoView({ behavior: 'instant', block: 'center' }); " +
                            "      setTimeout(() => btn.click(), 100); " +
                            "      return 'SUCCESS-any-primary-' + i; " +
                            "    } " +
                            "  } " +
                            "  return 'FAILED-not-found'; " +
                            "} catch (e) { " +
                            "  return 'FAILED-error: ' + e.message; " +
                            "} " +
                            "})()";
                    Object result = page.evaluate(jsClick);
                    page.waitForTimeout(3000); // 等待JS点击执行
                    log.info("[元器工作流发布] JavaScript点击执行结果: {}", result);
                    
                    // 检查结果
                    if (result != null && result.toString().startsWith("SUCCESS")) {
                        clicked = true;
                        page.waitForTimeout(2000); // 额外等待，确保点击生效
                        log.info("[元器工作流发布] JavaScript点击成功，方法: {}", result);
                    } else {
                        log.warn("[元器工作流发布] JavaScript点击失败: {}", result);
                    }
                } catch (Exception e2) {
                    log.error("[元器工作流发布] JavaScript点击执行异常: {}", e2.getMessage(), e2);
                }
            }
            
            // 如果所有方法都失败，使用最终的JavaScript兜底策略
            if (!clicked) {
                log.warn("[元器工作流发布] 常规方法失败，尝试最终的JavaScript兜底策略");
                try {
                    // 最终的兜底：直接使用JavaScript查找并点击，不依赖任何Playwright查找
                    String finalJsClick = "(() => { " +
                            "try { " +
                            "  // 强制滚动到底部 " +
                            "  window.scrollTo(0, Math.max(document.body.scrollHeight, document.documentElement.scrollHeight)); " +
                            "  // 策略1: 直接查找.release-content-footer中的第一个primary按钮 " +
                            "  const footer = document.querySelector('.release-content-footer'); " +
                            "  if (footer) { " +
                            "    const btn = footer.querySelector('button.v-button--primary'); " +
                            "    if (btn) { " +
                            "      btn.scrollIntoView({ behavior: 'instant', block: 'center' }); " +
                            "      btn.click(); " +
                            "      return 'SUCCESS-footer-primary-direct'; " +
                            "    } " +
                            "    // 策略2: 查找button-container中的primary按钮 " +
                            "    const container = footer.querySelector('.button-container'); " +
                            "    if (container) { " +
                            "      const btn2 = container.querySelector('button.v-button--primary'); " +
                            "      if (btn2) { " +
                            "        btn2.scrollIntoView({ behavior: 'instant', block: 'center' }); " +
                            "        btn2.click(); " +
                            "        return 'SUCCESS-container-primary-direct'; " +
                            "      } " +
                            "    } " +
                            "    // 策略3: 查找footer中任何包含'发布'的按钮 " +
                            "    const allBtns = footer.querySelectorAll('button'); " +
                            "    for (const btn of allBtns) { " +
                            "      if (btn.textContent && btn.textContent.trim().includes('发布')) { " +
                            "        btn.scrollIntoView({ behavior: 'instant', block: 'center' }); " +
                            "        btn.click(); " +
                            "        return 'SUCCESS-footer-any-publish'; " +
                            "      } " +
                            "    } " +
                            "  } " +
                            "  // 策略4: 查找所有primary按钮，选择最后一个包含'发布'的 " +
                            "  const allPrimary = document.querySelectorAll('button.v-button--primary'); " +
                            "  for (let i = allPrimary.length - 1; i >= 0; i--) { " +
                            "    const btn = allPrimary[i]; " +
                            "    if (btn.textContent && btn.textContent.trim().includes('发布')) { " +
                            "      btn.scrollIntoView({ behavior: 'instant', block: 'center' }); " +
                            "      btn.click(); " +
                            "      return 'SUCCESS-all-primary-last'; " +
                            "    } " +
                            "  } " +
                            "  return 'FAILED-not-found'; " +
                            "} catch (e) { " +
                            "  return 'ERROR: ' + e.message; " +
                            "} " +
                            "})()";
                    Object finalResult = page.evaluate(finalJsClick);
                    page.waitForTimeout(3000); // 等待点击执行
                    log.info("[元器工作流发布] 最终JavaScript兜底策略执行结果: {}", finalResult);
                    
                    // 检查结果
                    if (finalResult != null && finalResult.toString().startsWith("SUCCESS")) {
                        clicked = true;
                        page.waitForTimeout(2000); // 额外等待，确保点击生效
                        log.info("[元器工作流发布] 最终JavaScript兜底策略成功，方法: {}", finalResult);
                    } else {
                        log.warn("[元器工作流发布] 最终JavaScript兜底策略失败: {}", finalResult);
                    }
                } catch (Exception e) {
                    log.error("[元器工作流发布] 最终JavaScript兜底策略执行异常: {}", e.getMessage(), e);
                }
            }
            
            // 如果所有方法都失败
            if (!clicked) {
                log.error("[元器工作流发布] 所有方法都失败，未找到或无法点击发布按钮");
                return new PublishResult(false, "未找到发布确认按钮或无法点击");
            }

            // 点击发布按钮后，恢复页面缩放，提高后续状态检测的准确性
            if (zoomApplied) {
                try {
                    log.debug("[元器工作流发布] 恢复页面缩放 (zoom=1.0)");
                    page.evaluate("document.body.style.zoom = '1.0'");
                    page.waitForTimeout(2000); // 等待渲染调整
                } catch (Exception e) {
                    log.warn("[元器工作流发布] 恢复页面缩放失败: {}", e.getMessage());
                }
            }

            // 步骤7：等待发布完成，检查"已发布"状态
            // 使用传入的超时时间，默认90秒
            int timeout = (timeoutSeconds != null && timeoutSeconds > 0) ? timeoutSeconds : 90;
            int checkInterval = 3; // 每次检查间隔3秒（提高检测频率）
            int maxChecks = timeout / checkInterval; // 根据超时时间计算检查次数
            log.debug("[元器工作流发布] 步骤7: 等待发布完成 (超时时间: {}秒, 检查次数: {})", timeout, maxChecks);
            
            // 记录初始URL，用于检测页面跳转
            String initialUrl = page.url();
            boolean hasPageChanged = false;
            
            // 根据超时时间动态计算检查次数
            for (int i = 0; i < maxChecks; i++) {
                int waitedSeconds = (i + 1) * 3;
                
                // 每9秒输出一次等待日志
                if (waitedSeconds % 9 == 0) {
                    log.debug("[元器工作流发布] 等待发布完成... 已等待{}秒", waitedSeconds);
                }
                
                page.waitForTimeout(3000);

                // 检查URL是否变化（发布成功后可能跳转）
                try {
                    String currentUrl = page.url();
                    if (!currentUrl.equals(initialUrl) && !hasPageChanged) {
                        hasPageChanged = true;
                        log.debug("[元器工作流发布] 检测到页面跳转，可能发布成功");
                        // 页面跳转后等待加载完成
                        page.waitForLoadState();
                        page.waitForTimeout(2000);
                    }
                } catch (Exception e) {
                    // 忽略页面已关闭的错误
                }

                // 🔥 核心检测：检查"发布详情"表格中的"已发布"状态
                // DOM结构: <div class="publish-table"><div class="validate-status-text"> 已发布 </div></div>
                try {
                    // 方法1：精确检测发布详情表格中的状态文本
                    Locator publishTable = page.locator(".publish-table");
                    if (publishTable.count() > 0) {
                        log.debug("[元器工作流发布] 找到发布详情表格，检查发布状态...");
                        
                        // 查找所有"已发布"状态文本
                        Locator publishedStatus = publishTable.locator(".validate-status-text:has-text('已发布')");
                        int publishedCount = publishedStatus.count();
                        
                        log.debug("[元器工作流发布] 发布详情表格中'已发布'状态数量: {}", publishedCount);
                        
                        // 如果至少有一个渠道显示"已发布"，则认为发布成功
                        // 根据DOM结构，通常有2个渠道（官方小程序、元器官网）
                        if (publishedCount > 0) {
                            log.info("[元器工作流发布] 发布成功 - 发布详情表格中检测到{}个'已发布'状态", publishedCount);
                            return new PublishResult(true, "发布成功");
                        }
                    }
                } catch (Exception e) {
                    log.debug("[元器工作流发布] 检查发布详情表格时出错: {}", e.getMessage());
                }

                // 方法2：检查多种成功状态文本（使用模糊匹配，提高容错性）
                try {
                    // 检查"已发布"状态（页面可能刷新或出现提示）
                    if (page.getByText("已发布", new Page.GetByTextOptions().setExact(false)).count() > 0 ||
                            page.getByText("发布成功", new Page.GetByTextOptions().setExact(false)).count() > 0 ||
                            page.getByText("发布完成", new Page.GetByTextOptions().setExact(false)).count() > 0) {
                        log.info("[元器工作流发布] 发布成功 - 检测到成功文本");
                        return new PublishResult(true, "发布成功");
                    }
                } catch (Exception e) {
                    log.debug("[元器工作流发布] 检查文本状态时出错: {}", e.getMessage());
                }

                // 检查是否有成功提示消息（多种选择器）
                try {
                    Locator successTip1 = page.locator("[class*='success'], [class*='message']").filter(new Locator.FilterOptions().setHasText("成功"));
                    if (successTip1.count() > 0) {
                        Locator firstTip = successTip1.first();
                        if (firstTip.isVisible()) {
                            log.info("[元器工作流发布] 检测到成功提示消息");
                            return new PublishResult(true, "发布成功");
                        }
                    }
                    
                    // 检查Toast提示
                    Locator toastSuccess = page.locator(".el-message--success, .ant-message-success, [class*='toast-success']");
                    if (toastSuccess.count() > 0 && toastSuccess.first().isVisible()) {
                        log.info("[元器工作流发布] 检测到成功Toast提示");
                        return new PublishResult(true, "发布成功");
                    }
                } catch (Exception e) {
                    log.debug("[元器工作流发布] 检查提示消息时出错: {}", e.getMessage());
                }

                // 检查按钮状态变化（从"发布"变为"已发布"或其他禁用状态）
                try {
                    Locator publishButtonAfter = page.locator("button:has-text('已发布'), button:has-text('发布中')").last();
                    if (publishButtonAfter.count() > 0) {
                        // 如果按钮文本变为"已发布"，说明发布成功
                        if (publishButtonAfter.getByText("已发布").count() > 0) {
                            log.info("[元器工作流发布] 发布成功 - 按钮状态变为已发布");
                            return new PublishResult(true, "发布成功");
                        }
                    }
                } catch (Exception e) {
                    log.debug("[元器工作流发布] 检查按钮状态时出错: {}", e.getMessage());
                }

                // 检查是否出现加载完成的标识（如果页面有加载状态）
                try {
                    // 检查是否有加载动画消失（意味着操作完成）
                    Locator loadingIndicator = page.locator("[class*='loading'], [class*='spinner']").filter(new Locator.FilterOptions().setHasNotText("发布"));
                    // 如果之前有加载，现在没有了，可能是完成了
                    // 这个检查比较弱，主要作为辅助
                } catch (Exception e) {
                    // 忽略
                }

                // 定期进行轻微交互，保持页面活跃（避免因页面缩小导致的检测问题）
                if (i % 3 == 0 && i > 0) {
                    try {
                        // 轻微滚动，保持页面活跃
                        page.evaluate("window.scrollBy(0, 10); window.scrollBy(0, -10);");
                    } catch (Exception e) {
                        // 忽略
                    }
                }
            }

            // 最终检查：即使超时，也尝试最后一次检测
            try {
                if (page.getByText("已发布", new Page.GetByTextOptions().setExact(false)).count() > 0 ||
                        page.getByText("发布成功", new Page.GetByTextOptions().setExact(false)).count() > 0) {
                    log.info("[元器工作流发布] 最终检测：发布成功");
                    return new PublishResult(true, "发布成功");
                }
            } catch (Exception e) {
                // 忽略
            }

            // 超时处理
            log.warn("[元器工作流发布] 发布超时（已等待{}秒），未检测到明确成功状态", timeout);
            return new PublishResult(false, "发布超时（已等待" + timeout + "秒），请检查发布状态");

        } catch (Exception e) {
            log.error("[元器工作流发布] 发布失败", e);
            return new PublishResult(false, "发布失败: " + e.getMessage());
        }
    }


    /**
     * 查找节点
     *
     * 根据实际元器平台UI，节点通过名称标识，如"混元大模型"、"DeepSeek大模型"、"精调大模型"等
     * 使用多种选择器策略，提高容错性
     */
    private Locator findNode(Page page, String nodeName) {
        log.debug("[元器节点定位] 开始查找节点: {}", nodeName);

        // 策略1：通过getByText精确匹配节点名称（推荐）
        // 元器平台节点显示名称如"混元大模型"、"DeepSeek大模型"
        Locator node = page.getByText(nodeName, new Page.GetByTextOptions().setExact(true));
        if (node.count() > 0) {
            log.debug("[元器节点定位] 策略1成功: getByText精确匹配");
            return node;
        }

        // 策略2：通过getByText模糊匹配
        node = page.getByText(nodeName, new Page.GetByTextOptions().setExact(false));
        if (node.count() > 0) {
            log.debug("[元器节点定位] 策略2成功: getByText模糊匹配");
            return node;
        }

        // 策略3：通过节点名称类（如果平台使用了特定class）
        node = page.locator(".node-name:has-text('" + nodeName + "'), .node-title:has-text('" + nodeName + "'), .node-label:has-text('" + nodeName + "')");
        if (node.count() > 0) {
            log.debug("[元器节点定位] 策略3成功: node-name/node-title/node-label");
            return node;
        }

        // 策略4：通过画布中的节点容器查找
        node = page.locator("[class*='node']:has-text('" + nodeName + "'), [class*='canvas'] :has-text('" + nodeName + "')");
        if (node.count() > 0) {
            log.debug("[元器节点定位] 策略4成功: 节点容器");
            return node;
        }

        // 策略5：通过包含文本的div（最宽松的匹配）
        node = page.locator("div:has-text('" + nodeName + "')").first();
        if (node.count() > 0) {
            log.debug("[元器节点定位] 策略5成功: div:has-text");
            return node;
        }

        log.warn("[元器节点定位] 所有策略均失败，未找到节点: {}", nodeName);
        return null;
    }

    /**
     * 选择模型
     */
    private boolean selectModel(Page page, String modelName) {
        try {
            // 查找模型选择下拉框
            Locator modelSelect = page.locator("[class*='model-select'], select[name*='model'], .model-selector, [data-field='model']");
            if (modelSelect.count() > 0) {
                modelSelect.first().click();
                page.waitForTimeout(500);

                // 选择指定模型
                Locator modelOption = page.locator("text=" + modelName);
                if (modelOption.count() > 0) {
                    modelOption.first().click();
                    page.waitForTimeout(500);
                    return true;
                }
            }

            // 备选：直接在输入框中输入模型名称
            Locator modelInput = page.locator("input[name*='model'], input[placeholder*='模型']");
            if (modelInput.count() > 0) {
                modelInput.first().clear();
                modelInput.first().fill(modelName);
                page.waitForTimeout(500);
                return true;
            }

            return false;
        } catch (Exception e) {
            log.warn("[元器模型选择] 选择失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 填充参数
     */
    private void fillParameter(Page page, String paramName, Object value) {
        try {
            // 多种选择器策略
            String[] selectors = {
                "input[name*='" + paramName + "']",
                "[data-param='" + paramName + "'] input",
                "[data-field='" + paramName + "'] input",
                "label:has-text('" + paramName + "') + input",
                "label:has-text('" + paramName + "') ~ input"
            };

            for (String selector : selectors) {
                Locator input = page.locator(selector);
                if (input.count() > 0) {
                    input.first().clear();
                    input.first().fill(String.valueOf(value));
                    log.debug("[元器参数填充] {} = {} (选择器: {})", paramName, value, selector);
                    return;
                }
            }

            log.warn("[元器参数填充] 未找到参数输入框: {}", paramName);
        } catch (Exception e) {
            log.warn("[元器参数填充] 填充失败 - 参数: {}, 错误: {}", paramName, e.getMessage());
        }
    }

    /**
     * 向下滑动检测提示词文本框（新界面：需要向下滑动才能看到提示词输入框）
     */
    private void scrollToFindPromptTextarea(Page page) {
        try {
            log.debug("[元器提示词检测] 开始向下滑动检测提示词文本框");
            
            // 查找配置面板的滚动容器
            Locator configPanel = page.locator(
                ".panel__section, " +
                ".section-content, " +
                "[class*='config-panel'], " +
                "[class*='right-panel']"
            ).first();
            
            // 查找提示词相关的标签（"系统提示词"或"用户提示词"）
            String[] promptLabels = {"系统提示词", "用户提示词"};
            Locator promptLabel = null;
            
            for (String label : promptLabels) {
                try {
                    Locator labelLocator = page.getByText(label).first();
                    if (labelLocator.count() > 0 && labelLocator.isVisible()) {
                        promptLabel = labelLocator;
                        log.debug("[元器提示词检测] 找到提示词标签: {}", label);
                        break;
                    }
                } catch (Exception e) {
                    // 继续查找下一个标签
                }
            }
            
            // 如果已经找到提示词标签，说明已经在可见区域，不需要滚动
            if (promptLabel != null && promptLabel.count() > 0) {
                log.debug("[元器提示词检测] 提示词文本框已在可见区域，无需滚动");
                return;
            }
            
            // 如果没找到，需要向下滑动（需要一路滚到面板底部附近，并在过程中持续检测）
            log.debug("[元器提示词检测] 未找到提示词文本框，开始向下滑动并持续检测直到接近底部");
            
            int maxScrollAttempts = 24; // 最多滚动24次（更接近真实“滑到最底部”）
            int scrollDistance = 260; // 每次滚动略大一点，加快到底部的速度
            
            for (int attempt = 0; attempt < maxScrollAttempts; attempt++) {
                // 在配置面板内滚动（优先只滚这个右侧配置区域，避免影响其他区域）
                if (configPanel != null && configPanel.count() > 0) {
                    try {
                        // 先把鼠标移动到配置面板中心，确保滚轮事件发生在该容器内
                        try {
                            BoundingBox box = configPanel.boundingBox();
                            if (box != null) {
                                double centerX = box.x + box.width / 2.0;
                                double centerY = box.y + box.height / 2.0;
                                page.mouse().move(centerX, centerY);
                                log.debug("[元器提示词检测] 鼠标已移动到配置面板中心 ({}, {})", centerX, centerY);
                            }
                        } catch (Exception moveEx) {
                            log.debug("[元器提示词检测] 鼠标移动到配置面板中心失败: {}", moveEx.getMessage());
                        }

                        // 方法1：使用 JavaScript 精确增加 scrollTop
                        configPanel.evaluate("el => { el.scrollTop += " + scrollDistance + "; }");
                    } catch (Exception e) {
                        // 方法2：使用鼠标滚轮
                        try {
                            configPanel.hover();
                            page.mouse().wheel(0, scrollDistance);
                        } catch (Exception e2) {
                            // 方法3：页面滚动
                            page.mouse().wheel(0, scrollDistance);
                        }
                    }
                } else {
                    // 如果没找到配置面板，使用页面滚动
                    page.mouse().wheel(0, scrollDistance);
                }
                
                page.waitForTimeout(260); // 等待滚动完成和DOM稳定
                
                // 检查是否找到提示词标签
                for (String label : promptLabels) {
                    try {
                        Locator labelLocator = page.getByText(label).first();
                        if (labelLocator.count() > 0 && labelLocator.isVisible()) {
                            log.info("[元器提示词检测] 通过滚动找到提示词标签: {} (滚动次数: {})", label, attempt + 1);
                            page.waitForTimeout(500); // 额外等待确保稳定
                            return;
                        }
                    } catch (Exception e) {
                        // 继续查找
                    }
                }
                
                // 检查是否找到提示词输入框
                String[] promptSelectors = {
                    "[data-slate-editor][contenteditable='true']",
                    "[data-w-e-textarea='true'] [contenteditable='true']",
                    ".qa-editor__editor [contenteditable='true']",
                    "textarea[placeholder*='提示词']"
                };
                
                for (String selector : promptSelectors) {
                    try {
                        Locator promptInput = page.locator(selector).first();
                        if (promptInput.count() > 0 && promptInput.isVisible()) {
                            log.info("[元器提示词检测] 通过滚动找到提示词输入框: {} (滚动次数: {})", selector, attempt + 1);
                            page.waitForTimeout(500); // 额外等待确保稳定
                            return;
                        }
                    } catch (Exception e) {
                        // 继续查找
                    }
                }
            }
            
            // 如果循环结束仍未找到，尝试把配置面板直接滚到底（兜底）
            try {
                if (configPanel != null && configPanel.count() > 0) {
                    configPanel.evaluate("el => { el.scrollTop = el.scrollHeight; }");
                    log.warn("[元器提示词检测] 滚动 {} 次后仍未找到提示词文本框，已将配置面板直接滚动到底部", maxScrollAttempts);
                } else {
                    log.warn("[元器提示词检测] 滚动 {} 次后仍未找到提示词文本框，且未能确定配置面板容器", maxScrollAttempts);
                }
            } catch (Exception ex) {
                log.warn("[元器提示词检测] 兜底滚动到底部失败: {}", ex.getMessage());
            }
        } catch (Exception e) {
            log.warn("[元器提示词检测] 滚动检测失败: {}，继续执行", e.getMessage());
        }
    }

    /**
     * 填充提示词区域。
     *
     * <p>策略说明：</p>
     * <ul>
     *   <li>workflowName = {@code 日更助手-高优先级}：通过模拟用户输入"以 + / + Enter + 为题，"的方式重建占位符结构；</li>
     *   <li>workflowName = {@code 分析助手-高优先级-多模型}：在主提示词后自动追加"模型1/模型2/模型3结果"占位符；</li>
     *   <li>其他：直接整体替换提示词内容。</li>
     * </ul>
     *
     * <p>注意：提示词输入框通常为富文本编辑器（contenteditable div），而非普通 textarea。</p>
     */
    private void fillPrompt(Page page, String newPrompt, String workflowName) {
        try {
            Locator promptArea = null;
            String usedSelector = null;
            boolean isContentEditable = false;
            
            // 策略1：优先通过"用户提示词"标签精确定位输入框（新界面）
            try {
                Locator userPromptLabel = page.getByText("用户提示词").first();
                if (userPromptLabel.count() > 0) {
                    // 找到"用户提示词"标签后，在其父容器或兄弟容器中查找输入框
                    // HTML结构：.Input-word-name 包含标签，.Input-word 包含整个输入区域
                    Locator inputWordContainer = userPromptLabel.locator("xpath=ancestor::div[contains(@class, 'Input-word')]").first();
                    if (inputWordContainer.count() > 0) {
                        // 在容器内查找输入框
                        Locator editor = inputWordContainer.locator(
                            "[data-slate-editor][contenteditable='true'], " +
                            "[data-w-e-textarea='true'] [contenteditable='true'], " +
                            ".qa-editor__editor [contenteditable='true']"
                        ).first();
                        if (editor.count() > 0) {
                            promptArea = editor;
                            usedSelector = "用户提示词容器内的编辑器";
                            isContentEditable = true;
                            log.info("[元器提示词填充] 通过'用户提示词'标签精确定位到输入框");
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("[元器提示词填充] 通过'用户提示词'标签定位失败: {}", e.getMessage());
            }
            
            // 策略2：如果策略1失败，使用通用选择器查找（可能有多个，优先选择"用户提示词"对应的）
            if (promptArea == null || promptArea.count() == 0) {
                // 先找到所有可能的输入框
                Locator allEditors = page.locator(
                    "[data-slate-editor][contenteditable='true'], " +
                    "[data-w-e-textarea='true'] [contenteditable='true']"
                );
                int editorCount = allEditors.count();
                log.debug("[元器提示词填充] 找到 {} 个可能的编辑器", editorCount);
                
                // 如果有多个编辑器，尝试找到"用户提示词"对应的那个
                if (editorCount > 1) {
                    try {
                        Locator userPromptLabel = page.getByText("用户提示词").first();
                        if (userPromptLabel.count() > 0) {
                            // 找到"用户提示词"标签后，查找最近的编辑器
                            for (int i = 0; i < editorCount; i++) {
                                Locator editor = allEditors.nth(i);
                                // 检查这个编辑器是否在"用户提示词"的同一容器内
                                try {
                                    Locator editorContainer = editor.locator("xpath=ancestor::div[contains(@class, 'Input-word')]").first();
                                    if (editorContainer.count() > 0) {
                                        // 检查这个容器是否包含"用户提示词"标签
                                        Locator labelInContainer = editorContainer.locator("text=用户提示词").first();
                                        if (labelInContainer.count() > 0) {
                                            promptArea = editor;
                                            usedSelector = "用户提示词对应的编辑器（多个编辑器中选择）";
                                            isContentEditable = true;
                                            log.info("[元器提示词填充] 从多个编辑器中选择'用户提示词'对应的编辑器");
                    break;
                                        }
                                    }
                                } catch (Exception e) {
                                    // 继续查找下一个
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.debug("[元器提示词填充] 从多个编辑器中选择失败: {}", e.getMessage());
                    }
                }
                
                // 如果还是没找到，使用最后一个编辑器（通常"用户提示词"在"系统提示词"后面）
                if ((promptArea == null || promptArea.count() == 0) && editorCount > 0) {
                    promptArea = allEditors.last();
                    usedSelector = "最后一个编辑器（假设是用户提示词）";
                    isContentEditable = true;
                    log.info("[元器提示词填充] 使用最后一个编辑器（假设是用户提示词）");
                }
            }
            
            // 策略3：如果还是没找到，先滚动查找，然后再找一次
            if (promptArea == null || promptArea.count() == 0) {
                log.debug("[元器提示词填充] 首次查找未找到输入框，先滚动查找");
                scrollToFindPromptTextarea(page);
                // 滚动后再次尝试策略1和策略2
                try {
                    Locator userPromptLabel = page.getByText("用户提示词").first();
                    if (userPromptLabel.count() > 0) {
                        Locator inputWordContainer = userPromptLabel.locator("xpath=ancestor::div[contains(@class, 'Input-word')]").first();
                        if (inputWordContainer.count() > 0) {
                            Locator editor = inputWordContainer.locator(
                                "[data-slate-editor][contenteditable='true'], " +
                                "[data-w-e-textarea='true'] [contenteditable='true']"
                            ).first();
                            if (editor.count() > 0) {
                                promptArea = editor;
                                usedSelector = "滚动后通过'用户提示词'标签定位";
                                isContentEditable = true;
                                log.info("[元器提示词填充] 滚动后通过'用户提示词'标签找到输入框");
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("[元器提示词填充] 滚动后通过标签定位失败: {}", e.getMessage());
                }
                
                // 如果还是没找到，使用通用选择器
                if (promptArea == null || promptArea.count() == 0) {
                    Locator allEditors = page.locator("[data-slate-editor][contenteditable='true']");
                    if (allEditors.count() > 0) {
                        promptArea = allEditors.last(); // 使用最后一个（通常是用户提示词）
                        usedSelector = "滚动后使用最后一个编辑器";
                        isContentEditable = true;
                        log.info("[元器提示词填充] 滚动后使用最后一个编辑器");
                    }
                }
            }

            if (promptArea == null || promptArea.count() == 0) {
                log.error("[元器提示词填充] 未找到提示词输入框，所有尝试均失败");
                return;
            }
            
            // 确保输入框可见
            try {
                promptArea.scrollIntoViewIfNeeded();
                page.waitForTimeout(200);
            } catch (Exception e) {
                log.debug("[元器提示词填充] 滚动到输入框失败: {}", e.getMessage());
            }

            // 读取当前提示词内容
            String currentPrompt = null;
            try {
                if (isContentEditable) {
                    // 对于contenteditable元素，使用textContent或innerText
                    currentPrompt = promptArea.textContent();
                    if (currentPrompt == null || currentPrompt.trim().isEmpty()) {
                        // 尝试通过JavaScript获取
                        currentPrompt = (String) promptArea.evaluate("el => el.innerText || el.textContent || ''");
                    }
                } else {
                    // 对于textarea，使用inputValue
                    currentPrompt = promptArea.inputValue();
                }
            } catch (Exception e) {
                log.debug("[元器提示词填充] 获取当前内容失败: {}", e.getMessage());
                currentPrompt = "";
            }

            if (currentPrompt == null) {
                currentPrompt = "";
            }

            log.debug("[元器提示词填充] 当前提示词内容长度: {}, workflowName: {}", currentPrompt.length(), workflowName);

            // 判断是否需要特殊处理
            boolean shouldPreservePlaceholder = "日更助手-高优先级".equals(workflowName);
            boolean shouldAddModelPlaceholders = "分析助手-高优先级-多模型".equals(workflowName);
            log.info("[元器提示词填充] workflowName判断: workflowName='{}', shouldPreservePlaceholder={}, shouldAddModelPlaceholders={}", 
                    workflowName, shouldPreservePlaceholder, shouldAddModelPlaceholders);
            
            // 点击输入框获得焦点
            promptArea.scrollIntoViewIfNeeded();
            page.waitForTimeout(200);
            promptArea.click();
            page.waitForTimeout(300); // 等待焦点稳定

            // 对于contenteditable元素，使用更细粒度的键盘输入控制
            if (isContentEditable) {
                boolean hasCustomPlaceholders = newPrompt != null && newPrompt.contains("{{");

                if (hasCustomPlaceholders) {
                    // ⭐ 新逻辑：按 {{名称2}} 这样的占位符进行变量插入
                    log.debug("[元器提示词填充] 检测到自定义占位符语法，按 {{名称2}} 规则处理 (workflowName: {})", workflowName);

                    // 1️⃣ 清空现有内容
                    promptArea.click();
                    page.waitForTimeout(100);
                    promptArea.press("Control+a");
                    page.waitForTimeout(100);
                    promptArea.press("Delete");
                    page.waitForTimeout(200);

                    // 确保焦点在输入框
                    promptArea.click();
                    page.waitForTimeout(200);

                    // 2️⃣ 按 {{xxxN}} 语法逐段输入
                    typePromptWithVariablePlaceholders(page, promptArea, newPrompt, workflowName);
                    page.waitForTimeout(300);
                } else if (shouldPreservePlaceholder) {
                    // 特殊处理：重建占位符结构
                    // 流程：清空 -> 输入"以" -> 输入"/" -> 按Enter -> 输入"为题，" -> 输入新提示词
                    log.debug("[元器提示词填充] 执行特殊处理：重建占位符结构 (workflowName: {})", workflowName);
                    
                    // 1️⃣ 清空现有内容
                    promptArea.click();
                    page.waitForTimeout(100);
                    promptArea.press("Control+a");
                    page.waitForTimeout(100);
                    promptArea.press("Delete");
                    page.waitForTimeout(200);
                    
                    // 确保焦点在输入框
                    promptArea.click();
                    page.waitForTimeout(200);
                    
                    // 2️⃣ 输入"以"
                    promptArea.type("以", new Locator.TypeOptions().setDelay(50));
                    page.waitForTimeout(200);
                    
                    // 3️⃣ 输入"/"触发占位符列表
                    promptArea.type("/", new Locator.TypeOptions().setDelay(50));
                    page.waitForTimeout(800); // 等待占位符列表出现（增加等待时间）
                    
                    // 检查占位符列表是否出现（可选，用于调试）
                    try {
                        Locator placeholderList = page.locator("[role='listbox'], [class*='dropdown'], [class*='menu'], [class*='slot']").first();
                        if (placeholderList.count() > 0) {
                            log.debug("[元器提示词填充] 占位符列表已出现");
                        }
                    } catch (Exception e) {
                        log.debug("[元器提示词填充] 检查占位符列表时出错: {}", e.getMessage());
                    }
                    
                    // 4️⃣ 按Enter确认选择占位符（默认选择第一个，通常是article_topic）
                    promptArea.press("Enter");
                    page.waitForTimeout(500); // 等待占位符插入完成（增加等待时间）
                    
                    // 5️⃣ 输入"为题，"
                    promptArea.type("为题，", new Locator.TypeOptions().setDelay(50));
                    page.waitForTimeout(200);
                    
                    // 6️⃣ 输入新的提示词内容
                    promptArea.type(newPrompt, new Locator.TypeOptions().setDelay(10));
                    page.waitForTimeout(500);
                    
                    log.info("[元器提示词填充] 通过用户行为模拟成功（占位符重建）");
                } else if (shouldAddModelPlaceholders) {
                    // 特殊处理：分析助手-高优先级-多模型
                    // 流程：清空 -> 输入新提示词 -> 添加三个模型占位符
                    log.debug("[元器提示词填充] 执行特殊处理：添加模型占位符 (workflowName: {})", workflowName);
                    
                    // 1️⃣ 清空现有内容
                    promptArea.click();
                    page.waitForTimeout(100);
                    promptArea.press("Control+a");
                    page.waitForTimeout(100);
                    promptArea.press("Delete");
                    page.waitForTimeout(200);
                    
                    // 确保焦点在输入框
                    promptArea.click();
                    page.waitForTimeout(200);
                    
                    // 2️⃣ 输入新的提示词内容
                    promptArea.type(newPrompt, new Locator.TypeOptions().setDelay(10));
                    page.waitForTimeout(300);
                    
                    // 2.5️⃣ 按Enter换行（为后续占位符做准备）
                    promptArea.press("Enter");
                    page.waitForTimeout(200);
                    
                    // 3️⃣ 添加三个模型占位符
                    addModelPlaceholders(page, promptArea);
                    
                    log.info("[元器提示词填充] 通过用户行为模拟成功（添加模型占位符）");
                } else {
                    // 普通处理：直接替换全部内容
                    log.debug("[元器提示词填充] 执行普通处理：直接替换全部内容");
                    try {
                        // 全选并删除
                        promptArea.press("Control+a");
                        page.waitForTimeout(100);
                        promptArea.press("Delete");
                        page.waitForTimeout(200);
                        // 输入新内容
                        promptArea.type(newPrompt, new Locator.TypeOptions().setDelay(10));
                        page.waitForTimeout(300);
                        log.debug("[元器提示词填充] 通过type方法设置内容成功");
                    } catch (Exception e) {
                        log.warn("[元器提示词填充] type方法失败，尝试JavaScript: {}", e.getMessage());
                        // 使用JavaScript作为备选
                        try {
                            promptArea.evaluate("(el, text) => { " +
                                "el.focus(); " +
                                "el.innerText = text; " +
                                "el.textContent = text; " +
                                "const event = new Event('input', { bubbles: true }); " +
                                "el.dispatchEvent(event); " +
                                "}", newPrompt);
                            page.waitForTimeout(300);
                            log.debug("[元器提示词填充] 通过JavaScript设置内容成功");
                        } catch (Exception e2) {
                            log.error("[元器提示词填充] 所有方法均失败: {}", e2.getMessage());
                        }
                    }
                }
            } else {
                // 对于普通textarea，使用fill方法
                String finalPrompt;
                if (shouldPreservePlaceholder && currentPrompt != null && !currentPrompt.isEmpty() && currentPrompt.contains("为题，")) {
                    int index = currentPrompt.indexOf("为题，");
                    String prefix = currentPrompt.substring(0, index + "为题，".length());
                    finalPrompt = prefix + newPrompt;
                } else {
                    finalPrompt = newPrompt;
                }
                promptArea.fill("");
                page.waitForTimeout(200);
                promptArea.fill(finalPrompt);
                page.waitForTimeout(300);
            }
            
            log.info("[元器提示词填充] 成功 (选择器: {}, contenteditable: {}, workflowName: {}, 保留占位符: {}, 添加模型占位符: {})", 
                    usedSelector, isContentEditable, workflowName, shouldPreservePlaceholder, shouldAddModelPlaceholders);
        } catch (Exception e) {
            log.warn("[元器提示词填充] 填充失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 添加模型占位符（用于分析助手-高优先级-多模型）。
     *
     * <p>流程：</p>
     * <ol>
     *   <li>输入 {@code 模型X结果：/} 触发占位符列表；</li>
     *   <li>在下拉列表中精确选择“模型1 / 模型2 / 模型3”；</li>
     *   <li>除最后一个模型外，每个模型后追加换行。</li>
     * </ol>
     */
    private void addModelPlaceholders(Page page, Locator promptArea) {
        try {
            String[] modelNames = {"模型1", "模型2", "模型3"};

            for (int i = 0; i < modelNames.length; i++) {
                String modelName = modelNames[i];
                log.debug("[元器提示词填充] 开始添加占位符: {}", modelName);

                // 1️⃣ 输入"模型X结果："
                String prefix = modelName + "结果：";
                promptArea.type(prefix, new Locator.TypeOptions().setDelay(50));
                page.waitForTimeout(50);

                // 2️⃣ 输入"/"触发占位符列表
                promptArea.type("/", new Locator.TypeOptions().setDelay(50));
                page.waitForTimeout(100); // 等待占位符列表出现

                // 3️⃣ 根据模型索引使用键盘导航选择
                // 模型1：直接按Enter
                // 模型2：按1次向下箭头 + Enter
                // 模型3：按2次向下箭头 + Enter
                log.debug("[元器提示词填充] 使用键盘导航选择: {} (索引: {})", modelName, i);

                // 根据索引按下相应的向下箭头键次数
                for (int k = 0; k < i; k++) {
                    promptArea.press("ArrowDown");
                    page.waitForTimeout(100);
                    log.debug("[元器提示词填充] 按了一次向下箭头键");
                }

                // 确认选择
                promptArea.press("Enter");
                page.waitForTimeout(300);
                log.info("[元器提示词填充] 已通过键盘导航选择: {} (按了 {} 次向下箭头)", modelName, i);

                // 4️⃣ 按Enter换行（最后一个模型不需要换行）
                if (i < modelNames.length - 1) {
                    promptArea.press("Enter");
                    page.waitForTimeout(200);
                    log.debug("[元器提示词填充] 已换行，准备下一个模型");
                }

                log.info("[元器提示词填充] 已完成占位符: {}", modelName);
            }

            log.info("[元器提示词填充] 所有模型占位符添加完成");
        } catch (Exception e) {
            log.error("[元器提示词填充] 添加模型占位符失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 按 {{名称2}} 语法输入提示词，其中名称为说明，末尾数字为变量序号。
     *
     * <p>规则：</p>
     * <ul>
     *   <li>{{名称2}} 表示选择第2个变量占位符（通过 / + 向下(ID-1)次 + Enter 实现）；</li>
     *   <li>{{名称}} 中不含数字时，默认序号=1；</li>
     *   <li>workflowName 为“日更助手-高优先级”时，最大序号=1；</li>
     *   <li>workflowName 为“分析助手-高优先级-多模型”时，最大序号=3；</li>
     *   <li>序号超过最大值时按最大值处理。</li>
     * </ul>
     *
     * <p>原始提示词字符串不会被修改，仅在实际输入时根据占位符进行变量插入。</p>
     */
    private void typePromptWithVariablePlaceholders(Page page, Locator promptArea, String prompt, String workflowName) {
        try {
            // 计算当前工作流允许的最大变量序号
            int maxId;
            if ("日更助手-高优先级".equals(workflowName)) {
                maxId = 1;
            } else if ("分析助手-高优先级-多模型".equals(workflowName)) {
                maxId = 3;
            } else {
                maxId = Integer.MAX_VALUE;
            }

            // 使用正则匹配 {{...}} 占位符，并抽取内部数字作为序号
            Pattern pattern = Pattern.compile("\\{\\{([^}]*)\\}\\}");
            Matcher matcher = pattern.matcher(prompt);

            List<Integer> idQueue = new ArrayList<>();
            List<int[]> placeholderRanges = new ArrayList<>();

            while (matcher.find()) {
                String inner = matcher.group(1); // 例如 "名称2"
                int id = 1; // 默认1
                if (inner != null) {
                    Matcher numMatcher = Pattern.compile("(\\d+)").matcher(inner);
                    if (numMatcher.find()) {
                        try {
                            id = Integer.parseInt(numMatcher.group(1));
                        } catch (NumberFormatException ignore) {
                            id = 1;
                        }
                    }
                }
                // 限制最大值
                if (id > maxId) {
                    id = maxId;
                }
                idQueue.add(id);
                placeholderRanges.add(new int[]{matcher.start(), matcher.end()});
            }

            // 没有占位符时，直接整体输入
            if (placeholderRanges.isEmpty()) {
                promptArea.type(prompt, new Locator.TypeOptions().setDelay(10));
                return;
            }

            int lastIndex = 0;
            for (int i = 0; i < placeholderRanges.size(); i++) {
                int[] range = placeholderRanges.get(i);
                int start = range[0];
                int end = range[1];

                // 先输入占位符之前的普通文本
                if (start > lastIndex) {
                    String textSegment = prompt.substring(lastIndex, start);
                    if (!textSegment.isEmpty()) {
                        promptArea.type(textSegment, new Locator.TypeOptions().setDelay(10));
                    }
                }

                // 处理占位符 -> 使用 / + ArrowDown + Enter 选择对应变量
                int id = idQueue.get(i);
                log.debug("[元器提示词填充] 处理占位符区间: [{} , {}), 对应变量序号: {}", start, end, id);

                // 输入 "/" 触发占位符列表
                promptArea.type("/", new Locator.TypeOptions().setDelay(50));
                page.waitForTimeout(500);

                // 通过向下箭头选择第 id 个变量（id=1 时不按向下）
                int downTimes = Math.max(0, id - 1);
                for (int k = 0; k < downTimes; k++) {
                    promptArea.press("ArrowDown");
                    page.waitForTimeout(200);
                }

                // 回车确认选择
                promptArea.press("Enter");
                page.waitForTimeout(300);

                // 更新上一次处理位置
                lastIndex = end;
            }

            // 输入最后一个占位符之后的普通文本
            if (lastIndex < prompt.length()) {
                String tail = prompt.substring(lastIndex);
                if (!tail.isEmpty()) {
                    promptArea.type(tail, new Locator.TypeOptions().setDelay(10));
                }
            }

            log.info("[元器提示词填充] 已按占位符规则完成提示词输入 (workflowName: {}, maxId: {}, 占位符数量: {})",
                    workflowName, maxId, idQueue.size());
        } catch (Exception e) {
            log.warn("[元器提示词填充] 按占位符规则输入失败，回退为直接输入: {}", e.getMessage());
            // 兜底：直接整体输入
            try {
                promptArea.type(prompt, new Locator.TypeOptions().setDelay(10));
            } catch (Exception ignore) {
                // 最终失败忽略
            }
        }
    }
    /**
     * 保存节点配置
     */
    private boolean saveNodeConfig(Page page) {
        try {
            // 查找保存按钮
            Locator saveButton = page.locator("button:has-text('保存'), button:has-text('确定'), .save-btn, [data-action='save']");
            if (saveButton.count() > 0) {
                saveButton.first().click();
                page.waitForTimeout(2000);

                // 检查是否有成功提示
                Locator successTip = page.locator(".t-message--success, .el-message--success");
                return successTip.count() > 0 || true; // 即使没有成功提示也认为保存成功
            }

            log.warn("[元器节点保存] 未找到保存按钮");
            return false;
        } catch (Exception e) {
            log.warn("[元器节点保存] 保存失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 查找调试按钮
     */
    private Locator findDebugButton(Page page) {
        try {
            log.debug("[元器调试] 查找调试按钮...");
            // 优先尝试文本定位
            try {
                Locator button = page.getByText("调试").last();
                // 显式等待可见
                button.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(5000));
                return button;
            } catch (Exception e) {
                // 忽略超时，继续尝试其他选择器
            }

            String[] selectors = {
                "button:has-text('调试')",
                "[class*='debug-btn']",
                "[data-action='debug']",
                "button:has-text('Debug')"
            };

            for (String selector : selectors) {
                try {
                    Locator btn = page.locator(selector).last();
                    if (btn.count() > 0) {
                        btn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(2000));
                        return btn;
                    }
                } catch (Exception e) {
                    // 忽略
                }
            }
        } catch (Exception e) {
            log.warn("[元器调试] 查找调试按钮异常: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 查找运行按钮 ("去调试")
     */
    private Locator findRunButton(Page page) {
        try {
            log.debug("[元器调试] 查找'去调试'按钮...");
            // 优先尝试文本定位 "去调试"
            try {
                Locator button = page.getByText("去调试").last();
                button.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(5000));
                return button;
            } catch (Exception e) {
                // 忽略
            }

            String[] selectors = {
                "button:has-text('去调试')",
                "button:has-text('运行')",
                "button:has-text('执行')",
                "[class*='run-btn']"
            };

            for (String selector : selectors) {
                try {
                    Locator btn = page.locator(selector).last();
                    if (btn.count() > 0) {
                        btn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(2000));
                        return btn;
                    }
                } catch (Exception e) {
                    // 忽略
                }
            }
        } catch (Exception e) {
            log.warn("[元器调试] 查找运行按钮异常: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 查找对话输入框
     */
    private Locator findChatInput(Page page) {
        try {
            log.debug("[元器调试] 查找对话输入框 (含Iframe遍历)...");
            
            // 定义输入元素检查逻辑
            java.util.function.BiPredicate<String, Locator> isInput = (tagName, el) -> {
                if (tagName == null) return false;
                if ("TEXTAREA".equalsIgnoreCase(tagName) || "INPUT".equalsIgnoreCase(tagName)) return true;
                try {
                    return "true".equals(el.getAttribute("contenteditable"));
                } catch (Exception e) { return false; }
            };

            // 1. 尝试从焦点获取 (包括 Iframe 穿透)
            try {
                // 遍历所有 Frame (包括主页面) 查找焦点
                for (Frame frame : page.frames()) {
                    try {
                        Locator focused = frame.locator("*:focus");
                        if (focused.count() > 0) {
                            String tagName = (String) focused.evaluate("el => el.tagName");
                            if (isInput.test(tagName, focused)) {
                                log.info("[元器调试] 通过焦点在 Frame({}) 中找到输入框", frame.url());
                                return focused;
                            }
                        }
                    } catch (Exception e) {
                        // 忽略无法访问的 frame
                    }
                }
            } catch (Exception e) {
                // ignore
            }

            // 2. 使用选择器遍历查找
            String[] selectors = {
                "textarea[placeholder*='请输入你的问题']", 
                "textarea[placeholder*='输入']",
                "textarea[placeholder*='问题']",
                "textarea[class*='input']",
                "[role='textbox']",
                ".chat-input textarea",
                "input[type='text'][placeholder*='输入']",
                "div[contenteditable='true']", 
                "textarea:visible"
            };

            // 遍历所有 Frame (包括主页面)
            for (Frame frame : page.frames()) {
                if (frame.isDetached()) continue;
                try {
                    for (String selector : selectors) {
                        Locator input = frame.locator(selector).last();
                        if (input.count() > 0) {
                            // 显式等待可见
                            try {
                                input.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(500));
                                log.info("[元器调试] 在 Frame({}) 中找到输入框: {}", frame.url(), selector);
                                return input;
                            } catch (Exception e) {
                                // 即使超时，如果 count > 0 且 visible 也返回
                                if (input.isVisible()) return input;
                            }
                        }
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
        } catch (Exception e) {
            log.warn("[元器调试] 查找对话输入框异常: {}", e.getMessage());
        }
        return null;
    }


    /**
     * 填入调试输入参数
     */
    private void fillDebugInput(Page page, Map<String, Object> debugInput) {
        try {
            for (Map.Entry<String, Object> entry : debugInput.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                // 查找对应的输入框
                Locator input = page.locator("input[name='" + key + "'], [data-field='" + key + "'] input, label:has-text('" + key + "') ~ input");
                if (input.count() > 0) {
                    input.first().clear();
                    input.first().fill(String.valueOf(value));
                    log.debug("[元器调试输入] {} = {}", key, value);
                }
            }
        } catch (Exception e) {
            log.warn("[元器调试输入] 填充失败: {}", e.getMessage());
        }
    }

    /**
     * 提取调试输出
     */
    private String extractDebugOutput(Page page) {
        try {
            // 尝试多种选择器
            String[] selectors = {
                ".debug-output",
                ".result-panel",
                "[class*='output']",
                ".debug-result"
            };

            for (String selector : selectors) {
                Locator output = page.locator(selector);
                if (output.count() > 0) {
                    return output.first().textContent();
                }
            }

            return null;
        } catch (Exception e) {
            log.warn("[元器调试输出] 提取失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 查找发布按钮
     */
    private Locator findPublishButton(Page page) {
        String[] selectors = {
            "button:has-text('发布')",
            "[class*='publish-btn']",
            "[data-action='publish']",
            "button:has-text('Publish')"
        };

        for (String selector : selectors) {
            Locator button = page.locator(selector);
            if (button.count() > 0) {
                return button;
            }
        }

        return null;
    }

    /**
     * 导航到智能体详情页（用于发布功能）
     *
     * 导航流程（根据实际元器平台UI）：
     * 1. 点击"个人空间"按钮
     * 2. 选择指定空间
     * 3. 展开"我的智能体"菜单
     * 4. 点击指定智能体卡片
     *
     * @param page 元器首页
     * @param spaceName 空间名称（如"个人空间"、"福帮手开源"）
     * @param agentName 智能体名称（如"日更助手"、"123"）
     * @return 是否导航成功
     */
    public boolean navigateToAgentDetail(Page page, String spaceName, String agentName) {
        try {
            log.info("[元器导航] 导航到智能体详情页 - 空间: {}, 智能体: {}", spaceName, agentName);

            // 初始等待，确保页面加载完成
            page.waitForLoadState();
            page.waitForTimeout(2000);

            // 步骤1：点击个人空间按钮/空间切换按钮
            log.debug("[元器导航] 步骤1: 点击空间切换按钮");
            Locator spaceButton = page.locator("div.spaceBtn, [class*='space-btn']").first();
            if (spaceButton.count() == 0) {
                spaceButton = page.getByText("个人空间").first();
            }

            if (spaceButton.count() > 0) {
                spaceButton.waitFor(); // 等待可见
                spaceButton.click();
                page.waitForTimeout(1000);
            }

            // 步骤2：选择空间
            log.debug("[元器导航] 步骤2: 选择空间: {}", spaceName);
            // 关键修正：防止再次点击到spaceButton导致下拉收起
            // 优先查找下拉列表中的选项
            Locator spaceOption = null;

            // 尝试定位下拉菜单容器中的选项
            Locator dropdownOption = page.locator(".space-select-dropdown, [role='listbox'], [role='menu']").locator("[role='option'], li, div").filter(new Locator.FilterOptions().setHasText(spaceName)).last();

            if (dropdownOption.count() > 0 && dropdownOption.isVisible()) {
                spaceOption = dropdownOption;
            } else {
                // 备选：通过文本查找，但取最后一个（通常列表在DOM最后，且为了避开按钮本身）
                spaceOption = page.getByText(spaceName).last();
            }

            if (spaceOption != null && spaceOption.count() > 0 && spaceOption.isVisible()) {
                // 检查是否就是按钮本身（如果文本相同且没有下拉列表）
                // 简单的判断：如果option和button是同一个元素，则不要点
                // 但Playwright的Locator对象很难直接比较。
                // 依赖 .last() 策略通常有效。
                spaceOption.click();
                // 等待页面加载完成，防止过早检测导致检测不到智能体
                log.debug("[元器导航] 已选择空间，等待页面加载完成（2秒）");
                page.waitForTimeout(2000);
            } else {
                log.warn("[元器导航] 未找到指定空间选项: {}，可能已在当前空间", spaceName);
                // 即使未找到空间选项，也等待一下，确保页面状态稳定
                page.waitForTimeout(2000);
            }

            // 步骤3：展开"我的智能体"菜单
            log.debug("[元器导航] 步骤3: 寻找智能体菜单");
            // 在选择空间后，等待页面完全加载，防止过早检测导致检测不到智能体
            log.debug("[元器导航] 等待页面加载完成（2秒）");
            page.waitForTimeout(2000);
            
            Locator myAgentMenu = page.locator("div:has-text('我的智能体')").first();
            if (myAgentMenu.count() > 0 && myAgentMenu.isVisible()) {
                // 只有当它看起来像菜单项且未展开时才点击？
                // 这里简单处理，点一下无妨，或者是标题
                myAgentMenu.click();
                page.waitForTimeout(1000);
            }

            // 【新增】清除会话存储，防止记住上次的 Tab 或编辑器状态
            try {
                page.evaluate("try { window.sessionStorage.clear(); } catch(e) {}");
                log.debug("[元器导航] 已清除 sessionStorage 以重置 UI 状态");
            } catch (Exception e) {
                log.warn("[元器导航] 清除 sessionStorage 失败", e);
            }

            // 步骤4：点击指定智能体卡片
            log.debug("[元器导航] 步骤4: 点击智能体卡片: {}", agentName);

            Locator agentCard = null;
            try {
                // 增加等待时间，因为空间切换可能需要加载
                // 使用更精确的定位策略
                Locator agentCardLocator = page.getByText(agentName);
                if (agentCardLocator.count() == 0) {
                    agentCardLocator = page.locator("[class*='card']").filter(new Locator.FilterOptions().setHasText(agentName));
                }

                if (agentCardLocator.count() > 0) {
                    agentCardLocator.first().waitFor(new Locator.WaitForOptions().setTimeout(10000));
                    agentCard = agentCardLocator.first();
                }
            } catch (Exception e) {
                log.warn("[元器导航] 等待智能体卡片超时");
            }

            if (agentCard != null && agentCard.isVisible()) {
                agentCard.click();
                page.waitForTimeout(3000);

                // 【新增】检测是否误入工作流编辑器（Editor），如果是则尝试退出
                if (page.url().contains("/editor") || page.url().contains("/flow") ||
                        page.locator(".flow-editor, .canvas-container, [class*='graph']").count() > 0) {
                    log.warn("[元器导航] 检测到误入编辑器，尝试返回详情页");

                    // 策略1：点击左上角返回按钮
                    Locator backBtn = page.locator(".back-btn, .header-back, [aria-label='返回'], button:has-text('返回')").first();
                    if (backBtn.count() > 0 && backBtn.isVisible()) {
                        backBtn.click();
                        page.waitForTimeout(2000);
                    } else {
                        // 策略2：点击左上角面包屑中的智能体名称
                        Locator breadcrumb = page.locator(".breadcrumb-item, [class*='breadcrumb'] a").last();
                        if (breadcrumb.count() > 0 && breadcrumb.isVisible()) {
                            breadcrumb.click();
                            page.waitForTimeout(2000);
                        } else {
                            // 策略3：强制跳转
                            log.warn("[元器导航] 无法通过按钮退出编辑器，尝试浏览器后退");
                            page.goBack();
                            page.waitForTimeout(2000);
                        }
                    }
                }

                // 关键步骤：确保进入"应用设置"页面
                try {
                    Locator appSettingsTab = page.locator("div[role='tab'], li, [class*='tab-item']").filter(new Locator.FilterOptions().setHasText("应用设置")).first();
                    if (appSettingsTab.count() > 0) {
                        log.debug("[元器导航] 切换到'应用设置'标签页");
                        appSettingsTab.click();
                        page.waitForTimeout(1000);
                    } else {
                        Locator textTab = page.getByText("应用设置").first();
                        if (textTab.count() > 0 && textTab.isVisible()) {
                            textTab.click();
                            page.waitForTimeout(1000);
                        }
                    }
                } catch (Exception e) {
                    log.warn("[元器导航] 切换标签页失败，可能已在正确页面: {}", e.getMessage());
                }

                log.info("[元器导航] 已进入智能体详情页");
                return true;
            }

            log.warn("[元器导航] 未找到智能体: {}", agentName);
            return false;

        } catch (Exception e) {
            log.error("[元器导航] 导航失败", e);
            return false;
        }
    }



    /**
     * 截图并上传到 Admin 服务器
     */
    private String captureAndUpload(Page page, String userId, String fileName) {
        try {
            if (userId == null) {
                // 如果没有 userId，使用 default
                userId = "default";
            }
            
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
