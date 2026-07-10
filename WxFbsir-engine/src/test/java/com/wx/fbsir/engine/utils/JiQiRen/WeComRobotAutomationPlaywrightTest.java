package com.wx.fbsir.engine.utils.JiQiRen;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeComRobotAutomationPlaywrightTest {

    private static Playwright playwright;
    private static Browser browser;
    private Page page;
    private WeComRobotAutomation automation;

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(
            new com.microsoft.playwright.BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    static void closeBrowser() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeEach
    void createFixturePage() {
        page = browser.newPage();
        automation = new WeComRobotAutomation();
    }

    @Test
    void findsSpecialCharacterRobotNameWithoutSelectorInterpolation() {
        String robotName = "法务 'A' (测试) 🤖";
        page.setContent("""
            <div class="hl_list_content">
              <div class="hl_lc_line"><span class="account_aibot_name_text">其他机器人</span></div>
              <div class="hl_lc_line"><span class="account_aibot_name_text">法务 'A' (测试) 🤖</span></div>
            </div>
            """);

        assertEquals(robotName,
            automation.findUniqueRobotRow(page, robotName)
                .locator(".account_aibot_name_text").innerText());
    }

    @Test
    void rejectsDuplicateRobotNames() {
        page.setContent("""
            <div class="hl_list_content">
              <div class="hl_lc_line"><span class="account_aibot_name_text">重复机器人</span></div>
              <div class="hl_lc_line"><span class="account_aibot_name_text">重复机器人</span></div>
            </div>
            """);

        assertThrows(IllegalStateException.class,
            () -> automation.findUniqueRobotRow(page, "重复机器人"));
    }

    @Test
    void waitsForObservableImportSuccess() {
        page.setContent("""
            <div id="messages"></div>
            <script>
              setTimeout(() => {
                document.querySelector('#messages').innerHTML =
                  '<div class="t-message--success">导入成功</div>';
              }, 100);
            </script>
            """);

        WeComRobotAutomation.ImportResult result =
            automation.waitForKnowledgeImportResult(page, "https://example.com/knowledge", 2000);

        assertTrue(result.success());
    }

    @Test
    void reportsObservableImportFailure() {
        page.setContent("""
            <div class="t-message--error">导入失败：网页不可访问</div>
            """);

        WeComRobotAutomation.ImportResult result =
            automation.waitForKnowledgeImportResult(page, "https://example.com/knowledge", 1000);

        assertFalse(result.success());
        assertTrue(result.message().contains("导入失败"));
    }

    @Test
    void timeoutNeverBecomesSuccess() {
        page.setContent("<div>处理中</div>");

        WeComRobotAutomation.ImportResult result =
            automation.waitForKnowledgeImportResult(page, "https://example.com/knowledge", 300);

        assertFalse(result.success());
        assertTrue(result.message().contains("超时"));
    }

    @Test
    void rejectsUnsafeImportUrls() {
        assertThrows(IllegalArgumentException.class,
            () -> automation.validateImportUrl("javascript:alert(1)"));
        assertThrows(IllegalArgumentException.class,
            () -> automation.validateImportUrl("https://user:pass@example.com/private"));
        assertEquals("https://example.com/path",
            automation.validateImportUrl("https://example.com/path"));
    }
}
