package com.wx.fbsir.engine.utils.common;

import com.microsoft.playwright.FileChooser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 通用文件下载与上传工具类
 *
 * 详细使用文档见: docs/功能说明/engine/文件上传工具使用说明.md
 *
 * @author FBSir
 * @version 2.0
 */
@Component
public class FileDownloadUtil {

    private static final Logger log = LoggerFactory.getLogger(FileDownloadUtil.class);

    /** 默认连接超时（毫秒） */
    private static final int DEFAULT_CONNECT_TIMEOUT = 30000;

    /** 默认读取超时（毫秒） */
    private static final int DEFAULT_READ_TIMEOUT = 60000;

    /** 下载重试次数 */
    private static final int MAX_DOWNLOAD_RETRIES = 3;

    /** 重试间隔（毫秒） */
    private static final int RETRY_DELAY_MS = 2000;

    /** 文件名最大长度 */
    private static final int MAX_FILENAME_LENGTH = 100;

    /** 最小有效文件大小（字节），低于此值视为下载不完整 */
    private static final long MIN_VALID_FILE_SIZE = 10;

    // =========================================================================
    // 函数式接口定义
    // =========================================================================

    /**
     * 页面文件上传器 — 各AI平台实现各自的Playwright页面上传逻辑
     *
     * 本工具只负责「把文件送入页面」，上传完成后的异步解析/处理状态
     * 由开发者在Controller层自行轮询判断。
     */
    @FunctionalInterface
    public interface PageFileUploader {
        /**
         * @param page          Playwright页面对象
         * @param localFilePath 本地文件路径
         * @return 是否成功将文件送入页面（不代表平台已解析完成）
         */
        boolean upload(Page page, String localFilePath);
    }

    /**
     * 上传前准备回调（可选）
     */
    @FunctionalInterface
    public interface BeforeUploadAction {
        void execute();
    }

    /**
     * 上传完成检测器（可选） — 开发者可注入自定义的异步完成判断逻辑
     *
     * 典型用途：轮询页面DOM判断「解析中...」是否消失、上传进度条是否100%等
     */
    @FunctionalInterface
    public interface UploadCompletionChecker {
        /**
         * @param page Playwright页面对象
         * @return 是否已完成（true=完成，false=仍在处理中）
         */
        boolean isCompleted(Page page);
    }

    /**
     * 上传步骤 — 多步骤复杂上传流程中的单个操作
     *
     * 使用场景：需要先点击「附件」按钮，等待菜单展开，再点击「本地文件」等多步骤操作
     * 每个步骤独立封装，失败时仅跳过当前步骤并记录日志
     */
    @FunctionalInterface
    public interface UploadStep {
        /**
         * @param page Playwright页面对象
         * @throws Exception 步骤执行中的任何异常
         */
        void execute(Page page) throws Exception;
    }

    // =========================================================================
    // 结果封装
    // =========================================================================

    /**
     * 文件处理结果
     */
    public static class UploadResult {
        private final boolean success;
        private final String errorMessage;
        private final String localFilePath;
        private final ErrorType errorType;

        /** 错误类型枚举，便于调用方做分支处理 */
        public enum ErrorType {
            NONE,
            DOWNLOAD_FAILED,
            FILE_CORRUPTED,
            FILE_EMPTY,
            UPLOAD_FAILED,
            UPLOAD_TIMEOUT,
            BEFORE_ACTION_FAILED,
            UNKNOWN
        }

        private UploadResult(boolean success, String errorMessage, String localFilePath, ErrorType errorType) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.localFilePath = localFilePath;
            this.errorType = errorType;
        }

        public static UploadResult success() {
            return new UploadResult(true, null, null, ErrorType.NONE);
        }

        public static UploadResult success(String localFilePath) {
            return new UploadResult(true, null, localFilePath, ErrorType.NONE);
        }

        public static UploadResult fail(String errorMessage, ErrorType errorType) {
            return new UploadResult(false, errorMessage, null, errorType);
        }

        public static UploadResult fail(String errorMessage) {
            return new UploadResult(false, errorMessage, null, ErrorType.UNKNOWN);
        }

        public boolean isSuccess()          { return success; }
        public String getErrorMessage()     { return errorMessage; }
        public String getLocalFilePath()    { return localFilePath; }
        public ErrorType getErrorType()     { return errorType; }
    }

    // =========================================================================
    // 核心方法1：完整业务流程（下载 -> 校验 -> 上传 -> 清理）
    // =========================================================================

    /**
     * 完整文件处理流程（推荐入口）
     *
     * 流程：下载文件 -> 校验文件 -> 执行上传前准备 -> 上传到AI平台 -> 清理临时文件
     *
     * 本方法只保证「文件已送入页面」，不等待平台异步解析完成。
     * 如需判断解析完成，请使用带 completionChecker 参数的重载方法，
     * 或在Controller层自行轮询。
     *
     * @param fileUrl      文件下载URL（支持中文路径）
     * @param page         Playwright页面对象
     * @param uploader     平台专属的文件上传逻辑
     * @param beforeUpload 上传前的准备操作，可为null
     * @return 上传结果
     */
    public UploadResult downloadAndUploadToPage(String fileUrl, Page page,
                                                 PageFileUploader uploader,
                                                 BeforeUploadAction beforeUpload) {
        return downloadAndUploadToPage(fileUrl, page, uploader, beforeUpload, null);
    }

    /**
     * 完整文件处理流程（带异步完成检测）
     *
     * @param fileUrl            文件下载URL
     * @param page               Playwright页面对象
     * @param uploader           平台专属的文件上传逻辑
     * @param beforeUpload       上传前的准备操作，可为null
     * @param completionChecker  上传完成检测器，可为null（null时不等待）
     * @return 上传结果
     */
    public UploadResult downloadAndUploadToPage(String fileUrl, Page page,
                                                 PageFileUploader uploader,
                                                 BeforeUploadAction beforeUpload,
                                                 UploadCompletionChecker completionChecker) {
        String localFilePath = null;

        try {
            // 第1步：下载文件（带重试）
            log.info("[文件处理] 开始下载文件: {}", fileUrl);
            localFilePath = downloadFileWithRetry(fileUrl);

            if (localFilePath == null) {
                log.error("[文件处理] 文件下载失败（已重试{}次）: {}", MAX_DOWNLOAD_RETRIES, fileUrl);
                return UploadResult.fail("文件下载失败，请检查URL是否可访问", UploadResult.ErrorType.DOWNLOAD_FAILED);
            }

            // 第2步：校验文件
            UploadResult validateResult = validateDownloadedFile(localFilePath);
            if (!validateResult.isSuccess()) {
                return validateResult;
            }

            log.info("[文件处理] 文件已下载并校验通过: {} ({} bytes)",
                localFilePath, new File(localFilePath).length());

            // 第3步：执行上传前准备操作
            if (beforeUpload != null) {
                try {
                    log.debug("[文件处理] 执行上传前准备操作...");
                    beforeUpload.execute();
                } catch (Exception e) {
                    log.warn("[文件处理] 上传前准备操作失败，继续上传: {}", e.getMessage());
                }
            }

            // 第4步：调用平台专属上传逻辑
            log.info("[文件处理] 开始上传文件到AI平台...");
            boolean uploadSuccess = uploader.upload(page, localFilePath);

            if (!uploadSuccess) {
                log.warn("[文件处理] 平台上传器返回失败");
                return UploadResult.fail("文件上传到AI平台失败", UploadResult.ErrorType.UPLOAD_FAILED);
            }

            log.info("[文件处理] 文件已送入页面");

            // 第5步（可选）：等待异步上传完成
            if (completionChecker != null) {
                log.debug("[文件处理] 开始等待上传完成...");
                boolean completed = waitForUploadCompletion(page, completionChecker, 60);
                if (!completed) {
                    log.warn("[文件处理] 上传完成检测超时，文件可能仍在处理中");
                    return UploadResult.fail("上传完成检测超时，文件可能仍在处理中",
                        UploadResult.ErrorType.UPLOAD_TIMEOUT);
                }
                log.info("[文件处理] 上传完成检测通过");
            }

            return UploadResult.success();

        } catch (Exception e) {
            log.error("[文件处理] 文件处理流程异常: {}", e.getMessage(), e);
            return UploadResult.fail("文件处理异常: " + e.getMessage(), UploadResult.ErrorType.UNKNOWN);

        } finally {
            cleanupTempFile(localFilePath);
        }
    }

    /**
     * 简化版（无上传前准备）
     */
    public UploadResult downloadAndUploadToPage(String fileUrl, Page page,
                                                 PageFileUploader uploader) {
        return downloadAndUploadToPage(fileUrl, page, uploader, null, null);
    }

    // =========================================================================
    // 核心方法2：DOM备用上传（通用 input[type=file] 方式）
    // =========================================================================

    /**
     * 通过DOM定位 input[type=file] 元素上传文件（通用备用方案）
     *
     * 当平台专属的 PageFileUploader 不可用时，可使用此方法作为备用。
     * 自动尝试多种策略定位文件上传入口。
     *
     * @param page          Playwright页面对象
     * @param localFilePath 本地文件路径
     * @param selectors     自定义CSS选择器数组（可为null，使用默认策略）
     * @return 是否成功将文件送入页面
     */
    public boolean uploadViaDOM(Page page, String localFilePath, String[] selectors) {
        Path filePath = Paths.get(localFilePath);
        if (!filePath.toFile().exists()) {
            log.error("[DOM上传] 文件不存在: {}", localFilePath);
            return false;
        }

        // 策略1：直接查找 input[type=file]（最通用）
        try {
            Locator fileInput = page.locator("input[type='file']");
            if (fileInput.count() > 0) {
                log.debug("[DOM上传] 找到 input[type=file] 元素，直接设置文件");
                fileInput.first().setInputFiles(filePath);
                log.info("[DOM上传] 文件已通过 input[type=file] 送入页面");
                page.waitForTimeout(1000);
                return true;
            }
        } catch (Exception e) {
            log.debug("[DOM上传] 策略1(input[type=file])失败: {}", e.getMessage());
        }

        // 策略2：使用自定义选择器点击触发 FileChooser
        if (selectors != null) {
            for (String selector : selectors) {
                try {
                    Locator locator = page.locator(selector);
                    if (locator.count() > 0 && locator.first().isVisible()) {
                        log.debug("[DOM上传] 通过自定义选择器触发文件选择器: {}", selector);
                        Locator button = locator.first();
                        FileChooser fileChooser = page.waitForFileChooser(
                            () -> button.click(new Locator.ClickOptions().setTimeout(5000))
                        );
                        fileChooser.setFiles(filePath);
                        page.waitForTimeout(1000);
                        log.info("[DOM上传] 文件已通过自定义选择器送入页面");
                        return true;
                    }
                } catch (Exception e) {
                    log.debug("[DOM上传] 自定义选择器 {} 失败: {}", selector, e.getMessage());
                }
            }
        }

        // 策略3：查找常见上传按钮模式（中英文）
        String[] commonSelectors = {
            "button:has-text('上传')",
            "[role='button']:has-text('上传')",
            "label[for]:has-text('上传')",
            "button:has-text('Upload')",
            "button:has-text('Attach')",
            "[aria-label='上传文件']",
            "[aria-label='Upload file']",
            "[aria-label='Attach']",
            "div.upload-trigger",
            "button.upload-btn",
            "button.attach-btn",
        };
        for (String selector : commonSelectors) {
            try {
                Locator locator = page.locator(selector);
                if (locator.count() > 0 && locator.first().isVisible()) {
                    log.debug("[DOM上传] 通过通用选择器触发: {}", selector);
                    Locator button = locator.first();
                    FileChooser fileChooser = page.waitForFileChooser(
                        () -> button.click(new Locator.ClickOptions().setTimeout(5000))
                    );
                    fileChooser.setFiles(filePath);
                    page.waitForTimeout(1000);
                    log.info("[DOM上传] 文件已通过通用选择器送入页面");
                    return true;
                }
            } catch (Exception e) {
                log.debug("[DOM上传] 通用选择器 {} 失败: {}", selector, e.getMessage());
            }
        }

        log.warn("[DOM上传] 所有DOM上传策略均失败");
        return false;
    }

    /**
     * DOM备用上传（使用默认选择器策略）
     */
    public boolean uploadViaDOM(Page page, String localFilePath) {
        return uploadViaDOM(page, localFilePath, null);
    }

    // =========================================================================
    // 核心方法2.5：多步骤复杂上传（适用于需要多次点击才能触发文件选择器的平台）
    // =========================================================================

    /**
     * 多步骤复杂文件上传
     *
     * 适用场景：部分平台上传需要先点击「附件」按钮，等待菜单展开，再点击「本地文件」
     * 才会弹出文件选择器。此方法支持任意数量的前置准备步骤。
     *
     * 用法示例：
     * <pre>
     * uploadViaSteps(page, localFilePath,
     *     "li:text('本地文件')",               // 最终触发FileChooser的元素
     *     p -> p.locator(".attach-btn").click(), // 前置步骤1：点击附件按钮
     *     p -> p.waitForTimeout(800)             // 前置步骤2：等待菜单展开
     * );
     * </pre>
     *
     * @param page                       Playwright页面对象
     * @param localFilePath              本地文件路径
     * @param fileChooserTriggerSelector 最终触发文件选择器的元素CSS选择器
     * @param preparationSteps           上传前的准备步骤（顺序执行）
     * @return 是否成功将文件送入页面
     */
    public boolean uploadViaSteps(Page page, String localFilePath,
                                   String fileChooserTriggerSelector,
                                   UploadStep... preparationSteps) {
        Path filePath = Paths.get(localFilePath);
        if (!filePath.toFile().exists()) {
            log.error("[步骤上传] 文件不存在: {}", localFilePath);
            return false;
        }

        try {
            // 顺序执行所有准备步骤
            if (preparationSteps != null) {
                for (int i = 0; i < preparationSteps.length; i++) {
                    try {
                        log.debug("[步骤上传] 执行准备步骤 {}/{}", i + 1, preparationSteps.length);
                        preparationSteps[i].execute(page);
                    } catch (Exception e) {
                        log.warn("[步骤上传] 准备步骤 {} 执行失败（继续）: {}", i + 1, e.getMessage());
                    }
                }
            }

            // 等待触发元素出现并触发FileChooser
            Locator trigger = page.locator(fileChooserTriggerSelector);
            if (trigger.count() == 0) {
                log.warn("[步骤上传] 未找到文件选择器触发元素: {}", fileChooserTriggerSelector);
                return false;
            }

            log.debug("[步骤上传] 触发文件选择器: {}", fileChooserTriggerSelector);
            FileChooser fileChooser = page.waitForFileChooser(
                () -> trigger.first().click(new Locator.ClickOptions().setTimeout(5000))
            );
            fileChooser.setFiles(filePath);
            page.waitForTimeout(1000);
            log.info("[步骤上传] 文件已通过多步骤流程送入页面");
            return true;

        } catch (Exception e) {
            log.error("[步骤上传] 多步骤上传失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 完整流程 + 多步骤上传（下载 -> 校验 -> 准备步骤 -> 上传 -> 清理）
     *
     * @param fileUrl                    文件下载URL
     * @param page                       Playwright页面对象
     * @param fileChooserTriggerSelector 最终触发文件选择器的元素CSS选择器
     * @param beforeUpload               上传前准备（可为null）
     * @param preparationSteps           多步骤上传的准备步骤
     * @return 上传结果
     */
    public UploadResult downloadAndUploadViaSteps(String fileUrl, Page page,
                                                   String fileChooserTriggerSelector,
                                                   BeforeUploadAction beforeUpload,
                                                   UploadStep... preparationSteps) {
        String localFilePath = null;
        try {
            log.info("[文件处理-步骤模式] 开始下载文件: {}", fileUrl);
            localFilePath = downloadFileWithRetry(fileUrl);

            if (localFilePath == null) {
                return UploadResult.fail("文件下载失败", UploadResult.ErrorType.DOWNLOAD_FAILED);
            }

            UploadResult validateResult = validateDownloadedFile(localFilePath);
            if (!validateResult.isSuccess()) {
                return validateResult;
            }

            if (beforeUpload != null) {
                try {
                    beforeUpload.execute();
                } catch (Exception e) {
                    log.warn("[文件处理-步骤模式] 上传前准备失败（继续）: {}", e.getMessage());
                }
            }

            boolean success = uploadViaSteps(page, localFilePath, fileChooserTriggerSelector, preparationSteps);
            if (!success) {
                return UploadResult.fail("多步骤文件上传失败", UploadResult.ErrorType.UPLOAD_FAILED);
            }

            return UploadResult.success();

        } catch (Exception e) {
            log.error("[文件处理-步骤模式] 异常: {}", e.getMessage(), e);
            return UploadResult.fail("文件处理异常: " + e.getMessage(), UploadResult.ErrorType.UNKNOWN);
        } finally {
            cleanupTempFile(localFilePath);
        }
    }

    // =========================================================================
    // 核心方法3：完整流程 + DOM备用（自动降级）
    // =========================================================================

    /**
     * 带自动降级的完整文件处理流程
     *
     * 先尝试平台专属上传器，失败后自动降级到DOM通用上传。
     *
     * @param fileUrl        文件下载URL
     * @param page           Playwright页面对象
     * @param uploader       平台专属上传逻辑（可为null，直接使用DOM方式）
     * @param beforeUpload   上传前准备（可为null）
     * @param domSelectors   DOM备用上传的自定义选择器（可为null）
     * @return 上传结果
     */
    public UploadResult downloadAndUploadWithFallback(String fileUrl, Page page,
                                                       PageFileUploader uploader,
                                                       BeforeUploadAction beforeUpload,
                                                       String[] domSelectors) {
        String localFilePath = null;

        try {
            // 第1步：下载 + 校验
            log.info("[文件处理-降级模式] 开始下载文件: {}", fileUrl);
            localFilePath = downloadFileWithRetry(fileUrl);

            if (localFilePath == null) {
                return UploadResult.fail("文件下载失败", UploadResult.ErrorType.DOWNLOAD_FAILED);
            }

            UploadResult validateResult = validateDownloadedFile(localFilePath);
            if (!validateResult.isSuccess()) {
                return validateResult;
            }

            // 第2步：上传前准备
            if (beforeUpload != null) {
                try {
                    beforeUpload.execute();
                } catch (Exception e) {
                    log.warn("[文件处理-降级模式] 上传前准备失败: {}", e.getMessage());
                }
            }

            // 第3步：尝试平台专属上传
            if (uploader != null) {
                try {
                    log.info("[文件处理-降级模式] 尝试平台专属上传...");
                    boolean success = uploader.upload(page, localFilePath);
                    if (success) {
                        log.info("[文件处理-降级模式] 平台专属上传成功");
                        return UploadResult.success();
                    }
                    log.warn("[文件处理-降级模式] 平台专属上传失败，降级到DOM方式");
                } catch (Exception e) {
                    log.warn("[文件处理-降级模式] 平台专属上传异常，降级到DOM方式: {}", e.getMessage());
                }
            }

            // 第4步：降级 — DOM通用上传
            log.info("[文件处理-降级模式] 使用DOM通用上传...");
            boolean domSuccess = uploadViaDOM(page, localFilePath, domSelectors);
            if (domSuccess) {
                log.info("[文件处理-降级模式] DOM通用上传成功");
                return UploadResult.success();
            }

            return UploadResult.fail("平台专属上传和DOM通用上传均失败", UploadResult.ErrorType.UPLOAD_FAILED);

        } catch (Exception e) {
            log.error("[文件处理-降级模式] 异常: {}", e.getMessage(), e);
            return UploadResult.fail("文件处理异常: " + e.getMessage(), UploadResult.ErrorType.UNKNOWN);
        } finally {
            cleanupTempFile(localFilePath);
        }
    }

    // =========================================================================
    // 核心方法4：页面已上传文件的删除（单个 / 全部）
    // =========================================================================

    /**
     * 删除页面上已上传的文件附件
     *
     * 通过DOM定位文件附件区域的删除按钮并点击。
     * 适用于需要清理已上传文件后重新上传的场景。
     *
     * @param page      Playwright页面对象
     * @param selectors 删除按钮的CSS选择器数组（按优先级尝试）
     * @return 是否成功删除
     */
    public boolean removeUploadedFile(Page page, String[] selectors) {
        if (selectors == null || selectors.length == 0) {
            log.warn("[文件删除] 未提供删除按钮选择器");
            return false;
        }

        for (String selector : selectors) {
            try {
                Locator deleteBtn = page.locator(selector).first();
                if (deleteBtn.count() > 0 && deleteBtn.isVisible()) {
                    log.debug("[文件删除] 找到删除按钮: {}", selector);
                    deleteBtn.click(new Locator.ClickOptions().setTimeout(5000));
                    page.waitForTimeout(1000);
                    log.info("[文件删除] 已点击删除按钮");
                    return true;
                }
            } catch (Exception e) {
                log.debug("[文件删除] 选择器 {} 失败: {}", selector, e.getMessage());
            }
        }

        log.warn("[文件删除] 所有删除按钮选择器均未匹配");
        return false;
    }

    /**
     * 删除页面上所有已上传的文件（批量删除）
     *
     * 与 removeUploadedFile 不同，此方法会点击所有匹配的删除按钮，适用于需要清空全部附件的场景。
     * 采用从后往前删除策略，避免DOM重排导致索引错位。
     *
     * @param page     Playwright页面对象
     * @param selector 删除按钮的CSS选择器
     * @return 成功删除的文件数量
     */
    public int removeAllUploadedFiles(Page page, String selector) {
        if (selector == null || selector.isEmpty()) {
            log.warn("[批量删除] 未提供删除按钮选择器");
            return 0;
        }

        int deletedCount = 0;
        try {
            Locator deleteButtons = page.locator(selector);
            int count = deleteButtons.count();
            if (count == 0) {
                log.debug("[批量删除] 未找到任何已上传文件: {}", selector);
                return 0;
            }
            log.info("[批量删除] 找到 {} 个待删除文件", count);

            // 从后往前删除，避免DOM重排导致索引变化
            for (int i = count - 1; i >= 0; i--) {
                try {
                    deleteButtons.nth(i).click(new Locator.ClickOptions().setTimeout(3000));
                    page.waitForTimeout(500);
                    deletedCount++;
                    log.debug("[批量删除] 已删除第 {} 个文件", i + 1);
                } catch (Exception e) {
                    log.debug("[批量删除] 删除第 {} 个文件失败: {}", i + 1, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("[批量删除] 批量删除异常: {}", e.getMessage());
        }

        log.info("[批量删除] 共删除 {} 个已上传文件", deletedCount);
        return deletedCount;
    }

    // =========================================================================
    // 核心方法5：仅下载文件（带重试和校验）
    // =========================================================================

    /**
     * 下载文件（带重试机制）
     *
     * @param fileUrl 文件URL
     * @return 本地文件路径，失败返回null
     */
    public String downloadFileWithRetry(String fileUrl) {
        for (int attempt = 1; attempt <= MAX_DOWNLOAD_RETRIES; attempt++) {
            log.debug("[文件下载] 第 {}/{} 次尝试: {}", attempt, MAX_DOWNLOAD_RETRIES, fileUrl);

            String localPath = downloadFile(fileUrl);
            if (localPath != null) {
                // 基础校验
                File file = new File(localPath);
                if (file.exists() && file.length() >= MIN_VALID_FILE_SIZE) {
                    return localPath;
                }
                log.warn("[文件下载] 第{}次下载的文件无效（大小: {} bytes），重试...",
                    attempt, file.exists() ? file.length() : 0);
                cleanupTempFile(localPath);
            }

            if (attempt < MAX_DOWNLOAD_RETRIES) {
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * 单次下载文件到本地临时目录（无重试）
     *
     * @param fileUrl 文件URL（支持中文路径）
     * @return 本地文件路径，失败返回null
     */
    public String downloadFile(String fileUrl) {
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        FileOutputStream outputStream = null;

        try {
            String tempDir = System.getProperty("java.io.tmpdir");
            String fileName = extractFileName(fileUrl);
            String localFilePath = tempDir + File.separator + fileName;

            log.debug("[文件下载] 开始下载: {} -> {}", fileUrl, localFilePath);

            String encodedUrl = encodeUrl(fileUrl);
            URL url = new URL(encodedUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(DEFAULT_CONNECT_TIMEOUT);
            connection.setReadTimeout(DEFAULT_READ_TIMEOUT);
            connection.setInstanceFollowRedirects(true);

            connection.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            connection.setRequestProperty("Accept", "*/*");
            connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            connection.setRequestProperty("Connection", "keep-alive");

            connection.connect();

            int responseCode = connection.getResponseCode();

            // 处理重定向（3xx）
            if (responseCode >= 300 && responseCode < 400) {
                String redirectUrl = connection.getHeaderField("Location");
                if (redirectUrl != null) {
                    log.debug("[文件下载] 重定向到: {}", redirectUrl);
                    connection.disconnect();
                    return downloadFile(redirectUrl);
                }
            }

            if (responseCode != HttpURLConnection.HTTP_OK) {
                logHttpError(connection, fileUrl, responseCode);
                return null;
            }

            // 检查Content-Type是否合理（排除HTML错误页面伪装成文件）
            String contentType = connection.getContentType();
            if (contentType != null && contentType.contains("text/html")) {
                long contentLength = connection.getContentLengthLong();
                if (contentLength > 0 && contentLength < 1024) {
                    log.warn("[文件下载] 响应Content-Type为text/html且内容很小({}bytes)，可能是错误页面",
                        contentLength);
                }
            }

            inputStream = connection.getInputStream();
            outputStream = new FileOutputStream(localFilePath);

            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytesRead = 0;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
            }

            log.info("[文件下载] 下载完成: {} ({} bytes)", localFilePath, totalBytesRead);
            return localFilePath;

        } catch (java.net.SocketTimeoutException e) {
            log.error("[文件下载] 连接超时: {}", fileUrl);
            return null;
        } catch (java.net.UnknownHostException e) {
            log.error("[文件下载] 域名解析失败: {}", fileUrl);
            return null;
        } catch (javax.net.ssl.SSLException e) {
            log.error("[文件下载] SSL证书错误: {}", fileUrl);
            return null;
        } catch (java.io.IOException e) {
            log.error("[文件下载] IO异常: {} - {}", fileUrl, e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("[文件下载] 下载失败: {}", fileUrl, e);
            return null;
        } finally {
            closeQuietly(outputStream, inputStream, connection);
        }
    }

    // =========================================================================
    // 核心方法6：临时文件清理
    // =========================================================================

    /**
     * 清理本地临时文件
     *
     * @param localFilePath 本地文件路径，为null时忽略
     */
    public void cleanupTempFile(String localFilePath) {
        if (localFilePath == null) {
            return;
        }
        try {
            boolean deleted = Files.deleteIfExists(Paths.get(localFilePath));
            if (deleted) {
                log.debug("[文件清理] 临时文件已清理: {}", localFilePath);
            }
        } catch (Exception e) {
            log.warn("[文件清理] 清理临时文件失败: {} - {}", localFilePath, e.getMessage());
        }
    }

    // =========================================================================
    // 文件校验
    // =========================================================================

    /**
     * 校验已下载的文件是否有效
     *
     * 检查项：文件是否存在、是否为空、是否过小（可能是错误页面）
     *
     * @param localFilePath 本地文件路径
     * @return 校验结果
     */
    public UploadResult validateDownloadedFile(String localFilePath) {
        File file = new File(localFilePath);

        if (!file.exists()) {
            log.error("[文件校验] 文件不存在: {}", localFilePath);
            return UploadResult.fail("下载的文件不存在", UploadResult.ErrorType.FILE_CORRUPTED);
        }

        if (file.length() == 0) {
            log.error("[文件校验] 文件为空(0 bytes): {}", localFilePath);
            return UploadResult.fail("下载的文件为空", UploadResult.ErrorType.FILE_EMPTY);
        }

        if (file.length() < MIN_VALID_FILE_SIZE) {
            log.warn("[文件校验] 文件过小({} bytes)，可能已损坏: {}", file.length(), localFilePath);
            return UploadResult.fail("下载的文件过小(" + file.length() + " bytes)，可能已损坏",
                UploadResult.ErrorType.FILE_CORRUPTED);
        }

        // 检查是否为HTML错误页面（文件内容以 <html 或 <!DOCTYPE 开头）
        try {
            byte[] header = new byte[256];
            try (InputStream is = Files.newInputStream(Paths.get(localFilePath))) {
                int read = is.read(header);
                if (read > 0) {
                    String headerStr = new String(header, 0, read, StandardCharsets.UTF_8).trim().toLowerCase();
                    if (headerStr.startsWith("<!doctype html") || headerStr.startsWith("<html")) {
                        log.warn("[文件校验] 文件内容为HTML页面，可能是错误页面: {}", localFilePath);
                        return UploadResult.fail("下载内容为HTML页面而非目标文件，URL可能需要认证或已失效",
                            UploadResult.ErrorType.FILE_CORRUPTED);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[文件校验] 读取文件头异常: {}", e.getMessage());
        }

        if (!file.canRead()) {
            log.error("[文件校验] 文件不可读: {}", localFilePath);
            return UploadResult.fail("下载的文件不可读", UploadResult.ErrorType.FILE_CORRUPTED);
        }

        log.debug("[文件校验] 文件校验通过: {} ({} bytes)", localFilePath, file.length());
        return UploadResult.success(localFilePath);
    }

    // =========================================================================
    // URL编码工具
    // =========================================================================

    /**
     * 编码URL中的中文字符和特殊字符
     *
     * 只编码路径部分，保留协议、主机、端口和查询参数不变。
     *
     * @param urlString 原始URL
     * @return 编码后的URL
     */
    public String encodeUrl(String urlString) {
        try {
            int protocolEnd = urlString.indexOf("://");
            if (protocolEnd == -1) {
                return urlString;
            }

            String protocol = urlString.substring(0, protocolEnd + 3);
            String remaining = urlString.substring(protocolEnd + 3);

            int pathStart = remaining.indexOf("/");
            if (pathStart == -1) {
                return urlString;
            }

            String hostAndPort = remaining.substring(0, pathStart);
            String path = remaining.substring(pathStart);

            String pathPart;
            String queryPart = "";
            int queryStart = path.indexOf("?");
            if (queryStart != -1) {
                pathPart = path.substring(0, queryStart);
                queryPart = path.substring(queryStart);
            } else {
                pathPart = path;
            }

            String[] pathSegments = pathPart.split("/");
            StringBuilder encodedPath = new StringBuilder();
            for (String segment : pathSegments) {
                if (!segment.isEmpty()) {
                    encodedPath.append("/").append(
                        URLEncoder.encode(segment, StandardCharsets.UTF_8)
                            .replace("+", "%20"));
                }
            }

            return protocol + hostAndPort + encodedPath.toString() + queryPart;

        } catch (Exception e) {
            log.warn("[URL编码] 编码失败，使用原始URL: {}", urlString, e);
            return urlString;
        }
    }

    // =========================================================================
    // 内部辅助方法
    // =========================================================================

    /**
     * 等待上传完成（轮询检测）
     */
    private boolean waitForUploadCompletion(Page page, UploadCompletionChecker checker,
                                             int maxWaitSeconds) {
        int waited = 0;
        while (waited < maxWaitSeconds) {
            try {
                if (checker.isCompleted(page)) {
                    return true;
                }
            } catch (Exception e) {
                log.debug("[上传完成检测] 检测异常: {}", e.getMessage());
            }
            page.waitForTimeout(2000);
            waited += 2;
        }
        return false;
    }

    /**
     * 从URL中提取文件名
     */
    private String extractFileName(String fileUrl) {
        String fileName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);

        int queryIndex = fileName.indexOf("?");
        if (queryIndex > 0) {
            fileName = fileName.substring(0, queryIndex);
        }

        // URL解码文件名
        try {
            fileName = java.net.URLDecoder.decode(fileName, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.debug("[文件名] URL解码失败，使用原始文件名");
        }

        if (fileName.length() > MAX_FILENAME_LENGTH || fileName.isEmpty()) {
            String extension = "";
            int dotIndex = fileName.lastIndexOf(".");
            if (dotIndex > 0) {
                extension = fileName.substring(dotIndex);
            }
            fileName = UUID.randomUUID().toString() + extension;
        }

        return fileName;
    }

    /**
     * 记录HTTP错误详情
     */
    private void logHttpError(HttpURLConnection connection, String fileUrl, int responseCode) {
        try {
            String errorMsg = connection.getResponseMessage();
            log.error("[文件下载] HTTP {} - {} | URL: {}", responseCode, errorMsg, fileUrl);

            try (InputStream errorStream = connection.getErrorStream()) {
                if (errorStream != null) {
                    byte[] errorBytes = errorStream.readAllBytes();
                    String errorResponse = new String(errorBytes, StandardCharsets.UTF_8);
                    if (errorResponse.length() > 500) {
                        errorResponse = errorResponse.substring(0, 500) + "...(truncated)";
                    }
                    log.error("[文件下载] 服务器响应: {}", errorResponse);
                }
            }
        } catch (Exception e) {
            log.debug("[文件下载] 无法读取错误响应", e);
        }
    }

    /**
     * 静默关闭资源
     */
    private void closeQuietly(FileOutputStream outputStream, InputStream inputStream,
                              HttpURLConnection connection) {
        try { if (outputStream != null) outputStream.close(); }
        catch (Exception e) { log.debug("[文件下载] 关闭输出流失败", e); }
        try { if (inputStream != null) inputStream.close(); }
        catch (Exception e) { log.debug("[文件下载] 关闭输入流失败", e); }
        try { if (connection != null) connection.disconnect(); }
        catch (Exception e) { log.debug("[文件下载] 断开连接失败", e); }
    }
}
