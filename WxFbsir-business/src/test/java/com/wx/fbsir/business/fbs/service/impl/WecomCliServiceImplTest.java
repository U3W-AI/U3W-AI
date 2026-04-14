package com.wx.fbsir.business.fbs.service.impl;

import com.wx.fbsir.business.fbs.domain.WecomCliResult;
import com.wx.fbsir.business.fbs.service.WecomCliService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * WecomCliServiceImpl 单元测试
 *
 * <p>核心约束：WecomCliService 不写日志，日志由上层 WecomSyncService 统一负责。</p>
 * <p>测试类放在 impl 包下，以便 spy package-private 的 doExecute 方法。</p>
 *
 * @author wxfbsir
 * @date 2026-04-13
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("wecom-cli 执行服务测试")
class WecomCliServiceImplTest {

    @Spy
    @InjectMocks
    private WecomCliServiceImpl wecomCliService;

    // ---- 辅助方法：通过反射设置私有字段 ----

    private void setCliPath(String path) throws Exception {
        Field f = WecomCliServiceImpl.class.getDeclaredField("cliPath");
        f.setAccessible(true);
        f.set(wecomCliService, path);
    }

    private void setTimeoutMs(long ms) throws Exception {
        Field f = WecomCliServiceImpl.class.getDeclaredField("timeoutMs");
        f.setAccessible(true);
        f.set(wecomCliService, ms);
    }

    @AfterEach
    void reset() {
        // 确保不会因为测试修改导致状态泄漏
    }

    // ========================================================================
    // §2.3.1 isAvailable
    // ========================================================================

    @Test
    @DisplayName("文件存在时 isAvailable 返回 true")
    void testIsAvailable_FileExists() throws Exception {
        setCliPath(System.getProperty("java.home") + "/bin/java.exe");
        assertTrue(wecomCliService.isAvailable());
    }

    @Test
    @DisplayName("文件不存在时 isAvailable 返回 false")
    void testIsAvailable_FileNotFound() throws Exception {
        setCliPath("C:\\nonexistent\\path\\wecom-cli.exe");
        assertFalse(wecomCliService.isAvailable());
    }

    @Test
    @DisplayName("路径为空字符串时 isAvailable 返回 false")
    void testIsAvailable_EmptyPath() throws Exception {
        setCliPath("");
        assertFalse(wecomCliService.isAvailable());
    }

    // ========================================================================
    // §2.3.2 execute — CLI 不存在
    // ========================================================================

    @Test
    @DisplayName("CLI 不存在时 execute 返回 CLI_NOT_FOUND（不启动进程）")
    void testExecute_CliNotFound() throws Exception {
        setCliPath("C:\\nonexistent\\wecom-cli.exe");

        WecomCliResult result = wecomCliService.execute("doc", "smartsheet_get_records", "{}");

        assertFalse(result.isSuccess());
        assertEquals("CLI_NOT_FOUND", result.getErrorCode());
        assertTrue(result.getErrorMessage().contains("不存在"));
    }

    // ========================================================================
    // §2.3.3 execute — 成功（spy doExecute）
    // ========================================================================

    @Test
    @DisplayName("execute 成功：spy doExecute 返回模拟结果")
    void testExecute_Success_SpyDoExecute() throws Exception {
        setCliPath(System.getProperty("java.home") + "/bin/java.exe");
        doReturn(true).when(wecomCliService).isAvailable();

        WecomCliResult mockResult = WecomCliResult.success(
                "{\"content\":[{\"text\":\"[{\\\"id\\\":1}]\"}]}", 100L);
        doReturn(mockResult).when(wecomCliService).doExecute("doc", "smartsheet_get_records", "{}");

        WecomCliResult result = wecomCliService.execute("doc", "smartsheet_get_records", "{}");

        assertTrue(result.isSuccess());
        assertEquals("{\"content\":[{\"text\":\"[{\\\"id\\\":1}]\"}]}", result.getRawOutput());
        assertEquals(100L, result.getDurationMs());
    }

    // ========================================================================
    // §2.3.4 execute — 非 NET_ 错误不重试
    // ========================================================================

    @Test
    @DisplayName("非 NET_ 错误只执行一次不重试")
    void testExecute_NonNetError_NoRetry() throws Exception {
        doReturn(true).when(wecomCliService).isAvailable();
        WecomCliResult failResult = WecomCliResult.fail("CLI_ERROR", "exit code: 1", 1, 50L);
        doReturn(failResult).when(wecomCliService).doExecute("doc", "smartsheet_get_records", "{}");

        WecomCliResult result = wecomCliService.execute("doc", "smartsheet_get_records", "{}");

        assertFalse(result.isSuccess());
        assertEquals("CLI_ERROR", result.getErrorCode());
        // doExecute 应该只调用 1 次（不重试）
        verify(wecomCliService, times(1)).doExecute("doc", "smartsheet_get_records", "{}");
    }

    // ========================================================================
    // §2.3.5 execute — NET_ 错误会重试 ×1
    // ========================================================================

    @Test
    @DisplayName("NET_ 错误会重试一次")
    void testExecute_NetError_RetryOnce() throws Exception {
        doReturn(true).when(wecomCliService).isAvailable();

        WecomCliResult netFail = WecomCliResult.fail("NET_TIMEOUT", "connection timeout", -1, 100L);
        WecomCliResult success = WecomCliResult.success("{\"content\":[]}", 200L);

        doReturn(netFail).doReturn(success)
                .when(wecomCliService).doExecute("doc", "smartsheet_get_records", "{}");

        WecomCliResult result = wecomCliService.execute("doc", "smartsheet_get_records", "{}");

        assertTrue(result.isSuccess());
        // doExecute 应该调用 2 次（首次 + 1 次重试）
        verify(wecomCliService, times(2)).doExecute("doc", "smartsheet_get_records", "{}");
    }

    // ========================================================================
    // §2.3.6 getCliPath
    // ========================================================================

    @Test
    @DisplayName("getCliPath 返回配置的路径")
    void testGetCliPath() throws Exception {
        setCliPath("C:\\tools\\wecom-cli.exe");
        assertEquals("C:\\tools\\wecom-cli.exe", wecomCliService.getCliPath());
    }

    @Test
    @DisplayName("getCliPath 路径为 null 时返回空字符串")
    void testGetCliPath_Null() throws Exception {
        setCliPath(null);
        assertEquals("", wecomCliService.getCliPath());
    }

    // ========================================================================
    // §2.3.7 execute — AUTH_REQUIRED 不重试
    // ========================================================================

    @Test
    @DisplayName("AUTH_REQUIRED 错误只执行一次不重试")
    void testExecute_AuthRequired_NoRetry() throws Exception {
        doReturn(true).when(wecomCliService).isAvailable();
        WecomCliResult authFail = WecomCliResult.fail("AUTH_REQUIRED", "auth expired", -1, 50L);
        doReturn(authFail).when(wecomCliService).doExecute("doc", "smartsheet_get_records", "{}");

        WecomCliResult result = wecomCliService.execute("doc", "smartsheet_get_records", "{}");

        assertFalse(result.isSuccess());
        assertEquals("AUTH_REQUIRED", result.getErrorCode());
        verify(wecomCliService, times(1)).doExecute("doc", "smartsheet_get_records", "{}");
    }

    // ========================================================================
    // §2.3.8 execute — EXEC_TIMEOUT 不重试
    // ========================================================================

    @Test
    @DisplayName("EXEC_TIMEOUT 错误只执行一次不重试")
    void testExecute_ExecTimeout_NoRetry() throws Exception {
        doReturn(true).when(wecomCliService).isAvailable();
        WecomCliResult timeoutFail = WecomCliResult.fail("EXEC_TIMEOUT", "process timeout", -1, 30000L);
        doReturn(timeoutFail).when(wecomCliService).doExecute("doc", "smartsheet_get_records", "{}");

        WecomCliResult result = wecomCliService.execute("doc", "smartsheet_get_records", "{}");

        assertFalse(result.isSuccess());
        assertEquals("EXEC_TIMEOUT", result.getErrorCode());
        verify(wecomCliService, times(1)).doExecute("doc", "smartsheet_get_records", "{}");
    }

    // ========================================================================
    // §2.3.9 CLI_ERROR 不重试
    // ========================================================================

    @Test
    @DisplayName("CLI_ERROR（其他非 NET 错误）只执行一次不重试")
    void testExecute_CliError_NoRetry() throws Exception {
        doReturn(true).when(wecomCliService).isAvailable();
        WecomCliResult cliError = WecomCliResult.fail("CLI_ERROR", "unknown error output", 1, 50L);
        doReturn(cliError).when(wecomCliService).doExecute("doc", "smartsheet_get_records", "{}");

        WecomCliResult result = wecomCliService.execute("doc", "smartsheet_get_records", "{}");

        assertFalse(result.isSuccess());
        assertEquals("CLI_ERROR", result.getErrorCode());
        verify(wecomCliService, times(1)).doExecute("doc", "smartsheet_get_records", "{}");
    }
}
