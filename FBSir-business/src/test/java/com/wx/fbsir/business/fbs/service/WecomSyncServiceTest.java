package com.wx.fbsir.business.fbs.service;

import com.wx.fbsir.business.fbs.domain.WecomCliResult;
import com.wx.fbsir.business.fbs.dto.business.wecom.WecomCheckResponse;
import com.wx.fbsir.business.fbs.dto.business.wecom.WecomSyncReadResponse;
import com.wx.fbsir.business.fbs.mapper.FbsWecomSyncLogMapper;
import com.wx.fbsir.business.fbs.service.impl.WecomSyncServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * WecomSyncServiceImpl 单元测试
 *
 * <p>测试 readSheet / check 的业务编排逻辑（CLI 调用 → 解析 → 日志写入）。</p>
 * <p>Mock WecomCliService 和 FbsWecomSyncLogMapper，不依赖真实 CLI 和数据库。</p>
 *
 * @author FBSir
 * @date 2026-04-13
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("企微同步服务测试")
class WecomSyncServiceTest {

    @Mock
    private WecomCliService wecomCliService;

    @Mock
    private FbsWecomSyncLogMapper syncLogMapper;

    @Spy
    @InjectMocks
    private WecomSyncServiceImpl wecomSyncService;

    // ---- 辅助方法 ----

    /** 通过反射设置 docUrl（@Value 注入） */
    private void setDocUrl(String docUrl) throws Exception {
        Field f = WecomSyncServiceImpl.class.getDeclaredField("docUrl");
        f.setAccessible(true);
        f.set(wecomSyncService, docUrl);
    }

    /** 通过反射设置 metaSheetId / commercialHubSheetId（@Value 注入） */
    private void setSheetIds() throws Exception {
        Field f1 = WecomSyncServiceImpl.class.getDeclaredField("metaSheetId");
        f1.setAccessible(true);
        f1.set(wecomSyncService, "q979lj");

        Field f2 = WecomSyncServiceImpl.class.getDeclaredField("commercialHubSheetId");
        f2.setAccessible(true);
        f2.set(wecomSyncService, "04bLwp");
    }

    /** 统一初始化：设置 docUrl + sheetId 映射 */
    private void setupConfig() throws Exception {
        setDocUrl("https://example.com/sheet/test-doc-id");
        setSheetIds();
    }

    /**
     * 生成有效的双层 JSON 模拟 wecom-cli 返回值
     * CLI 实际返回格式: { "content": [{ "text": "{ \"errcode\":0, \"records\":[...] }" }] }
     */
    private String buildValidDoubleLayerJson(int recordCount) {
        StringBuilder records = new StringBuilder("[");
        for (int i = 0; i < recordCount; i++) {
            if (i > 0) records.append(",");
            records.append("{\"record_id\":\"r").append(i + 1).append("\",\"values\":{\"name\":\"record_").append(i + 1).append("\"}}");
        }
        records.append("]");
        String inner = "{\"errcode\":0,\"errmsg\":\"ok\",\"total\":" + recordCount + ",\"records\":" + records.toString() + "}";
        return "{\"content\":[{\"text\":\"" + inner.replace("\"", "\\\"") + "\"}]}";
    }

    // ========================================================================
    // §4.1 readSheet
    // ========================================================================

    @Nested
    @DisplayName("readSheet 读取测试")
    class ReadSheetTests {

        @Test
        @DisplayName("读 meta 成功：返回记录数 + 写日志")
        void testReadSheet_MetaSuccess() throws Exception {
            setupConfig();
            doReturn(true).when(wecomCliService).isAvailable();
            doReturn(WecomCliResult.success(buildValidDoubleLayerJson(3), 500L))
                    .when(wecomCliService).execute(any(), any(), any());

            WecomSyncReadResponse resp = wecomSyncService.readSheet("meta");

            assertTrue(resp.isSuccess());
            assertEquals("meta", resp.getSheetName());
            assertEquals(3, resp.getRecordCount());
            assertEquals(3, resp.getSnapshot().size());
            verify(syncLogMapper, times(1)).insertSyncLog(any());
        }

        @Test
        @DisplayName("读 commercial_hub 成功")
        void testReadSheet_CommercialHubSuccess() throws Exception {
            setupConfig();
            doReturn(true).when(wecomCliService).isAvailable();
            doReturn(WecomCliResult.success(buildValidDoubleLayerJson(5), 800L))
                    .when(wecomCliService).execute(any(), any(), any());

            WecomSyncReadResponse resp = wecomSyncService.readSheet("commercial_hub");

            assertTrue(resp.isSuccess());
            assertEquals("commercial_hub", resp.getSheetName());
            assertEquals(5, resp.getRecordCount());
        }

        @Test
        @DisplayName("读 15 条记录：预览只返回前 10 条")
        void testReadSheet_PreviewLimit() throws Exception {
            setupConfig();
            doReturn(true).when(wecomCliService).isAvailable();
            doReturn(WecomCliResult.success(buildValidDoubleLayerJson(15), 1200L))
                    .when(wecomCliService).execute(any(), any(), any());

            WecomSyncReadResponse resp = wecomSyncService.readSheet("meta");

            assertTrue(resp.isSuccess());
            assertEquals(15, resp.getRecordCount());
            assertEquals(10, resp.getSnapshot().size());
        }

        @Test
        @DisplayName("sheetName 为 null 时默认读 meta")
        void testReadSheet_DefaultSheet() throws Exception {
            setupConfig();
            doReturn(true).when(wecomCliService).isAvailable();
            doReturn(WecomCliResult.success(buildValidDoubleLayerJson(1), 200L))
                    .when(wecomCliService).execute(any(), any(), any());

            WecomSyncReadResponse resp = wecomSyncService.readSheet(null);

            assertTrue(resp.isSuccess());
            assertEquals("meta", resp.getSheetName());
        }

        @Test
        @DisplayName("JSON 解析失败（非法 JSON）：返回 PARSE_ERROR")
        void testReadSheet_ParseError_InvalidJson() throws Exception {
            setupConfig();
            doReturn(true).when(wecomCliService).isAvailable();
            doReturn(WecomCliResult.success("this is not json", 100L))
                    .when(wecomCliService).execute(any(), any(), any());

            WecomSyncReadResponse resp = wecomSyncService.readSheet("meta");

            assertFalse(resp.isSuccess());
            assertEquals("PARSE_ERROR", resp.getErrorCode());
        }

        @Test
        @DisplayName("P0-2: content 缺失时应为 PARSE_ERROR，不能静默返回 0 条")
        void testReadSheet_ParseError_MissingContent() throws Exception {
            setupConfig();
            doReturn(true).when(wecomCliService).isAvailable();
            doReturn(WecomCliResult.success("{\"status\":\"ok\"}", 200L))
                    .when(wecomCliService).execute(any(), any(), any());

            WecomSyncReadResponse resp = wecomSyncService.readSheet("meta");

            assertFalse(resp.isSuccess());
            assertEquals("PARSE_ERROR", resp.getErrorCode());
            verify(syncLogMapper, times(1)).insertSyncLog(any());
        }

        @Test
        @DisplayName("P0-2: content[0].text 为空时应为 PARSE_ERROR，不能静默返回 0 条")
        void testReadSheet_ParseError_EmptyText() throws Exception {
            setupConfig();
            doReturn(true).when(wecomCliService).isAvailable();
            doReturn(WecomCliResult.success("{\"content\":[{\"text\":\"\"}]}", 200L))
                    .when(wecomCliService).execute(any(), any(), any());

            WecomSyncReadResponse resp = wecomSyncService.readSheet("meta");

            assertFalse(resp.isSuccess());
            assertEquals("PARSE_ERROR", resp.getErrorCode());
            verify(syncLogMapper, times(1)).insertSyncLog(any());
        }

        @Test
        @DisplayName("P0-1: 传入白名单外的 sheetName 时返回 INVALID_SHEET，不发起 CLI 调用")
        void testReadSheet_InvalidSheetName() throws Exception {
            // 白名单校验在 CLI 调用之前，不需要 setupConfig
            WecomSyncReadResponse resp = wecomSyncService.readSheet("hidden_sheet");

            assertFalse(resp.isSuccess());
            assertEquals("INVALID_SHEET", resp.getErrorCode());
            verify(wecomCliService, never()).isAvailable();
            verify(wecomCliService, never()).execute(any(), any(), any());
            verify(syncLogMapper, never()).insertSyncLog(any());
        }

        @Test
        @DisplayName("CLI 执行失败（NET_CONNECTION）：返回对应错误码")
        void testReadSheet_CliExecutionFailed() throws Exception {
            setupConfig();
            doReturn(true).when(wecomCliService).isAvailable();
            doReturn(WecomCliResult.fail("NET_CONNECTION_FAILED", "connection refused", -1, 3000L))
                    .when(wecomCliService).execute(any(), any(), any());

            WecomSyncReadResponse resp = wecomSyncService.readSheet("meta");

            assertFalse(resp.isSuccess());
            assertEquals("NET_CONNECTION_FAILED", resp.getErrorCode());
            verify(syncLogMapper, times(1)).insertSyncLog(any());
        }

        @Test
        @DisplayName("CLI 不可用：返回 CLI_NOT_FOUND")
        void testReadSheet_CliNotAvailable() throws Exception {
            setupConfig();
            doReturn(false).when(wecomCliService).isAvailable();

            WecomSyncReadResponse resp = wecomSyncService.readSheet("meta");

            assertFalse(resp.isSuccess());
            assertEquals("CLI_NOT_FOUND", resp.getErrorCode());
            verify(syncLogMapper, times(1)).insertSyncLog(any());
        }

        @Test
        @DisplayName("AUTH_REQUIRED 错误码透传，不重试，写 FAILED 日志")
        void testReadSheet_AuthRequired_PassThrough() throws Exception {
            setupConfig();
            doReturn(true).when(wecomCliService).isAvailable();
            doReturn(WecomCliResult.fail("AUTH_REQUIRED", "需要扫码登录", -1, 200L))
                    .when(wecomCliService).execute(any(), any(), any());

            WecomSyncReadResponse resp = wecomSyncService.readSheet("meta");

            assertFalse(resp.isSuccess());
            assertEquals("AUTH_REQUIRED", resp.getErrorCode());
            verify(syncLogMapper, times(1)).insertSyncLog(any());
        }

        @Test
        @DisplayName("空白 sheetName（空格）默认读 meta")
        void testReadSheet_BlankSheetName_Default() throws Exception {
            setupConfig();
            doReturn(true).when(wecomCliService).isAvailable();
            doReturn(WecomCliResult.success(buildValidDoubleLayerJson(2), 300L))
                    .when(wecomCliService).execute(any(), any(), any());

            WecomSyncReadResponse resp = wecomSyncService.readSheet("   ");

            assertTrue(resp.isSuccess());
            assertEquals("meta", resp.getSheetName());
        }

        @Test
        @DisplayName("日志写入失败不影响主流程（返回仍成功）")
        void testReadSheet_LogWriteFailure_NotAffectResult() throws Exception {
            setupConfig();
            doReturn(true).when(wecomCliService).isAvailable();
            doReturn(WecomCliResult.success(buildValidDoubleLayerJson(1), 100L))
                    .when(wecomCliService).execute(any(), any(), any());
            doThrow(new RuntimeException("DB connection lost")).when(syncLogMapper).insertSyncLog(any());

            WecomSyncReadResponse resp = wecomSyncService.readSheet("meta");

            assertTrue(resp.isSuccess());
            assertEquals(1, resp.getRecordCount());
        }

        @Test
        @DisplayName("EXEC_TIMEOUT 错误码映射日志 status 为 TIMEOUT")
        void testReadSheet_ExecTimeout_MapsToTimeoutStatus() throws Exception {
            setupConfig();
            doReturn(true).when(wecomCliService).isAvailable();
            doReturn(WecomCliResult.fail("EXEC_TIMEOUT", "process timeout", -1, 30000L))
                    .when(wecomCliService).execute(any(), any(), any());

            WecomSyncReadResponse resp = wecomSyncService.readSheet("meta");

            assertFalse(resp.isSuccess());
            assertEquals("EXEC_TIMEOUT", resp.getErrorCode());
            verify(syncLogMapper, times(1)).insertSyncLog(any());
        }
    }

    // ========================================================================
    // §4.2 check
    // ========================================================================

    @Nested
    @DisplayName("check 连通性检测测试")
    class CheckTests {

        @Test
        @DisplayName("CLI 文件存在：返回 cliAvailable=true")
        void testCheck_FileExists() {
            doReturn(true).when(wecomCliService).isAvailable();
            doReturn("C:\\tools\\wecom-cli.exe").when(wecomCliService).getCliPath();

            WecomCheckResponse resp = wecomSyncService.check();

            assertTrue(resp.isCliAvailable());
            assertEquals("C:\\tools\\wecom-cli.exe", resp.getCliPath());
            assertNull(resp.getLastError());
            verify(syncLogMapper, never()).insertSyncLog(any());
        }

        @Test
        @DisplayName("CLI 文件不存在：返回 cliAvailable=false + CLI_NOT_FOUND")
        void testCheck_FileNotFound() {
            doReturn(false).when(wecomCliService).isAvailable();
            doReturn("").when(wecomCliService).getCliPath();

            WecomCheckResponse resp = wecomSyncService.check();

            assertFalse(resp.isCliAvailable());
            assertNotNull(resp.getLastError());
            verify(syncLogMapper, never()).insertSyncLog(any());
        }
    }
}
