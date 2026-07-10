package com.wx.fbsir.engine.utils.JiQiRen;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * 企业微信智能机器人页面的确定性定位、输入校验和结果验证。
 */
@Component
public class WeComRobotAutomation {

    private static final int MAX_ROBOT_NAME_LENGTH = 128;
    private static final int MAX_IMPORT_URL_LENGTH = 2048;

    public String validateRobotName(String robotName) {
        if (robotName == null || robotName.isBlank()) {
            throw new IllegalArgumentException("robotName 不能为空");
        }
        String normalized = robotName.trim();
        if (normalized.length() > MAX_ROBOT_NAME_LENGTH) {
            throw new IllegalArgumentException("robotName 长度不能超过 " + MAX_ROBOT_NAME_LENGTH);
        }
        return normalized;
    }

    public String validateImportUrl(String importWebUrl) {
        if (importWebUrl == null || importWebUrl.isBlank()) {
            throw new IllegalArgumentException("importWebUrl 不能为空");
        }
        String normalized = importWebUrl.trim();
        if (normalized.length() > MAX_IMPORT_URL_LENGTH) {
            throw new IllegalArgumentException("importWebUrl 长度不能超过 " + MAX_IMPORT_URL_LENGTH);
        }

        try {
            URI uri = new URI(normalized);
            String scheme = uri.getScheme();
            if (scheme == null ||
                !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException("importWebUrl 只允许 http 或 https");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException("importWebUrl 必须包含有效主机名");
            }
            if (uri.getUserInfo() != null) {
                throw new IllegalArgumentException("importWebUrl 不能包含用户名或密码");
            }
            return uri.toASCIIString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("importWebUrl 格式无效", e);
        }
    }

    /**
     * 按机器人名称精确匹配唯一行。重复名称或未找到时安全失败。
     */
    public Locator findUniqueRobotRow(Page page, String robotName) {
        String expectedName = validateRobotName(robotName);
        Locator rows = page.locator(".hl_list_content .hl_lc_line");
        List<Locator> matches = new ArrayList<>();

        for (int i = 0; i < rows.count(); i++) {
            Locator row = rows.nth(i);
            Locator name = row.locator(".account_aibot_name_text");
            if (name.count() > 0 && expectedName.equals(name.first().innerText().trim())) {
                matches.add(row);
            }
        }

        if (matches.size() != 1) {
            throw new IllegalStateException(matches.isEmpty()
                ? "未找到目标机器人: " + expectedName
                : "机器人名称不唯一: " + expectedName + "，匹配数量: " + matches.size());
        }
        return matches.get(0);
    }

    /**
     * 等待知识导入出现可观察的成功或失败证据。超时永远不视为成功。
     */
    public ImportResult waitForKnowledgeImportResult(Page page, String importWebUrl, long timeoutMillis) {
        String expectedUrl = validateImportUrl(importWebUrl);
        long deadline = System.currentTimeMillis() + Math.max(1, timeoutMillis);

        while (System.currentTimeMillis() < deadline) {
            String error = firstVisibleText(page.locator(
                ".t-message--error, .ww_tip_error, [role='alert']:has-text('失败'), [role='alert']:has-text('错误')"));
            if (error != null) {
                return ImportResult.failure(error);
            }

            String success = firstVisibleText(page.locator(
                ".t-message--success, .ww_tip_success, [role='alert']:has-text('导入成功'), " +
                    "[role='alert']:has-text('添加成功'), [role='alert']:has-text('已添加')"));
            if (success != null) {
                return ImportResult.success(success);
            }

            Locator importedResource = page.getByText(expectedUrl, new Page.GetByTextOptions().setExact(true));
            if (hasVisible(importedResource)) {
                return ImportResult.success("已在知识集列表读回导入 URL");
            }

            page.waitForTimeout(200);
        }

        return ImportResult.failure("等待知识导入结果超时，未观察到成功证据");
    }

    private boolean hasVisible(Locator locator) {
        for (int i = 0; i < locator.count(); i++) {
            if (locator.nth(i).isVisible()) {
                return true;
            }
        }
        return false;
    }

    private String firstVisibleText(Locator locator) {
        for (int i = 0; i < locator.count(); i++) {
            Locator candidate = locator.nth(i);
            if (candidate.isVisible()) {
                String text = candidate.innerText();
                return text == null || text.isBlank() ? "页面报告操作失败" : text.trim();
            }
        }
        return null;
    }

    public record ImportResult(boolean success, String message) {
        public static ImportResult success(String message) {
            return new ImportResult(true, message);
        }

        public static ImportResult failure(String message) {
            return new ImportResult(false, message);
        }
    }
}
