package com.wx.fbsir.business.fbs.service;

import com.wx.fbsir.business.fbs.domain.WecomCliResult;
import com.wx.fbsir.business.fbs.dto.business.wecom.WecomSyncWriteResponse;
import com.wx.fbsir.business.fbs.mapper.FbsWecomSyncLogMapper;
import com.wx.fbsir.business.fbs.service.impl.WecomWriteServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * WecomWriteServiceImpl 单元测试
 *
 * <p>测试 writeRecords 的业务编排逻辑（分片写入 + 日志记录）。</p>
 * <p>Mock WecomCliService 和 FbsWecomSyncLogMapper，不依赖真实 CLI 和数据库。</p>
 *
 * @author wxfbsir
 * @date 2026-04-14
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("企微写入服务测试")
class WecomWriteServiceTest {

    @Mock
    private WecomCliService wecomCliService;

    @Mock
    private FbsWecomSyncLogMapper syncLogMapper;

    @Spy
    @InjectMocks
    private WecomWriteServiceImpl wecomWriteService;

    // ---- 辅助方法 ----

    /** 通过反射设置配置字段 */
    private void setField(String fieldName, Object value) throws Exception {
        Field f = WecomWriteServiceImpl.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(wecomWriteService, value);
    }

    /** 构造 CLI 格式的测试记录（小数据） */
    private Map<String, Object> makeRecord(String title, int count) {
        Map<String, Object> record = new HashMap<>();
        Map<String, Object> values = new HashMap<>();
        values.put("标题", Collections.singletonList(Map.of("type", "text", "text", title)));
        values.put("数量", count);
        record.put("values", values);
        return record;
    }

    /** 构造超大记录（超过 20KB） */
    private Map<String, Object> makeOversizedRecord() {
        Map<String, Object> record = new HashMap<>();
        Map<String, Object> values = new HashMap<>();
        // 30000 字符的文本 → 序列化后约 60KB
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 30000; i++) {
            sb.append("X");
        }
        values.put("大文本", Collections.singletonList(Map.of("type", "text", "text", sb.toString())));
        record.put("values", values);
        return record;
    }

    @BeforeEach
    void setUp() throws Exception {
        setField("docId", "test-doc-id");
        setField("metaSheetId", "test-meta-sheet-id");
        setField("commercialHubSheetId", "test-commercial-hub-sheet-id");
        setField("maxPayloadBytes", 20480);
    }

    @Nested
    @DisplayName("正常写入")
    class WriteSuccess {

        @Test
        @DisplayName("写入成功 - 小数据无需分片")
        void writeSmallData_success() {
            // Arrange
            List<Map<String, Object>> records = List.of(
                    makeRecord("测试A", 100),
                    makeRecord("测试B", 200)
            );
            when(wecomCliService.execute(eq("doc"), eq("smartsheet_add_records"), anyString()))
                    .thenReturn(WecomCliResult.success("{\"errcode\":0}", 100L));

            // Act
            WecomSyncWriteResponse response = wecomWriteService.writeRecords("meta", records);

            // Assert
            assertTrue(response.isSuccess());
            assertEquals("meta", response.getSheetName());
            assertEquals(2, response.getTotalRecords());
            assertEquals(2, response.getWrittenRecords());
            assertEquals(1, response.getShardCount());
            verify(wecomCliService, times(1)).execute(eq("doc"), eq("smartsheet_add_records"), anyString());
            verify(syncLogMapper, times(1)).insertSyncLog(any());
        }

        @Test
        @DisplayName("写入成功 - 需分片 x2")
        void writeLargeData_sharded() throws Exception {
            // Arrange: 将 maxPayloadBytes 设小以触发分片
            setField("maxPayloadBytes", 200);

            // 每条约 60-70 字节，3 条记录约 200 字节 → 需要分片
            List<Map<String, Object>> records = List.of(
                    makeRecord("分片测试A", 1),
                    makeRecord("分片测试B", 2),
                    makeRecord("分片测试C", 3)
            );
            when(wecomCliService.execute(eq("doc"), eq("smartsheet_add_records"), anyString()))
                    .thenReturn(WecomCliResult.success("{\"errcode\":0}", 50L));

            // Act
            WecomSyncWriteResponse response = wecomWriteService.writeRecords("meta", records);

            // Assert
            assertTrue(response.isSuccess());
            assertEquals(3, response.getTotalRecords());
            assertEquals(3, response.getWrittenRecords());
            assertTrue(response.getShardCount() >= 2, "应该至少分 2 片");
            // CLI 应该被调用至少 2 次
            verify(wecomCliService, atLeast(2)).execute(eq("doc"), eq("smartsheet_add_records"), anyString());
            verify(syncLogMapper, times(1)).insertSyncLog(any());
        }

        @Test
        @DisplayName("写入成功 - sheetName 默认 meta")
        void writeDefaultSheet() {
            List<Map<String, Object>> records = List.of(makeRecord("默认Sheet", 1));
            when(wecomCliService.execute(eq("doc"), eq("smartsheet_add_records"), anyString()))
                    .thenReturn(WecomCliResult.success("{\"errcode\":0}", 50L));

            WecomSyncWriteResponse response = wecomWriteService.writeRecords(null, records);

            assertTrue(response.isSuccess());
            assertEquals("meta", response.getSheetName());
        }

        @Test
        @DisplayName("写入成功 - sheetName 空串默认 meta")
        void writeEmptySheetName() {
            List<Map<String, Object>> records = List.of(makeRecord("空串Sheet", 1));
            when(wecomCliService.execute(eq("doc"), eq("smartsheet_add_records"), anyString()))
                    .thenReturn(WecomCliResult.success("{\"errcode\":0}", 50L));

            WecomSyncWriteResponse response = wecomWriteService.writeRecords("  ", records);

            assertTrue(response.isSuccess());
            assertEquals("meta", response.getSheetName());
        }
    }

    @Nested
    @DisplayName("拒绝写入")
    class WriteRejected {

        @Test
        @DisplayName("单条记录超 20KB → BIZ_PAYLOAD_TOO_LARGE")
        void singleRecordTooLarge() {
            List<Map<String, Object>> records = List.of(makeRecord("正常", 1), makeOversizedRecord());

            WecomSyncWriteResponse response = wecomWriteService.writeRecords("meta", records);

            assertFalse(response.isSuccess());
            assertEquals("BIZ_PAYLOAD_TOO_LARGE", response.getErrorCode());
            verify(wecomCliService, never()).execute(anyString(), anyString(), anyString());
            verify(syncLogMapper, never()).insertSyncLog(any());
        }

        @Test
        @DisplayName("白名单外 sheetName → INVALID_SHEET")
        void invalidSheet() {
            List<Map<String, Object>> records = List.of(makeRecord("测试", 1));

            WecomSyncWriteResponse response = wecomWriteService.writeRecords("forbidden_sheet", records);

            assertFalse(response.isSuccess());
            assertEquals("INVALID_SHEET", response.getErrorCode());
            verify(wecomCliService, never()).execute(anyString(), anyString(), anyString());
            verify(syncLogMapper, never()).insertSyncLog(any());
        }

        @Test
        @DisplayName("records 为 null → INVALID_REQUEST")
        void nullRecords() {
            WecomSyncWriteResponse response = wecomWriteService.writeRecords("meta", null);

            assertFalse(response.isSuccess());
            assertEquals("INVALID_REQUEST", response.getErrorCode());
            verify(wecomCliService, never()).execute(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("空记录列表 → no-op")
        void emptyRecords() {
            WecomSyncWriteResponse response = wecomWriteService.writeRecords("meta", Collections.emptyList());

            assertTrue(response.isSuccess());
            assertEquals(0, response.getWrittenRecords());
            assertEquals(0, response.getShardCount());
            verify(wecomCliService, never()).execute(anyString(), anyString(), anyString());
            verify(syncLogMapper, never()).insertSyncLog(any());
        }
    }

    @Nested
    @DisplayName("写入失败")
    class WriteFailed {

        @Test
        @DisplayName("CLI 失败 → 写入 FAILED 日志")
        void cliFailure() {
            List<Map<String, Object>> records = List.of(makeRecord("失败", 1));
            when(wecomCliService.execute(eq("doc"), eq("smartsheet_add_records"), anyString()))
                    .thenReturn(WecomCliResult.fail("CLI_ERROR", "CLI 执行失败", 1, 100L));

            WecomSyncWriteResponse response = wecomWriteService.writeRecords("meta", records);

            assertFalse(response.isSuccess());
            assertEquals("CLI_ERROR", response.getErrorCode());
            verify(syncLogMapper, times(1)).insertSyncLog(any());
        }

        @Test
        @DisplayName("部分分片失败 → PARTIAL_WRITE_FAILED")
        void partialShardFailure() throws Exception {
            // 将 maxPayloadBytes 设小以触发分片
            setField("maxPayloadBytes", 200);

            List<Map<String, Object>> records = List.of(
                    makeRecord("分片A", 1),
                    makeRecord("分片B", 2),
                    makeRecord("分片C", 3)
            );

            // 第 1 次调用成功，第 2 次调用失败
            when(wecomCliService.execute(eq("doc"), eq("smartsheet_add_records"), anyString()))
                    .thenReturn(WecomCliResult.success("{\"errcode\":0}", 50L))
                    .thenReturn(WecomCliResult.fail("EXEC_TIMEOUT", "执行超时", 1, 30000L));

            WecomSyncWriteResponse response = wecomWriteService.writeRecords("meta", records);

            assertFalse(response.isSuccess());
            assertEquals("PARTIAL_WRITE_FAILED", response.getErrorCode());
            assertTrue(response.getWrittenRecords() > 0, "应该有部分记录已写入");
            assertTrue(response.getWrittenRecords() < 3, "不应该全部写入");
verify(syncLogMapper, times(1)).insertSyncLog(any());
        }
    }

    // ===================== updateRecords 测试 =====================

    @Nested
    @DisplayName("updateRecords - 更新记录")
    class UpdateRecordsTests {

        @Test
        @DisplayName("更新成功")
        void updateRecords_success() {
            List<Map<String, Object>> records = List.of(
                    makeRecord("更新内容", 1)
            );
            when(wecomCliService.execute(eq("doc"), eq("smartsheet_update_records"), anyString()))
                    .thenReturn(WecomCliResult.success("{\"errcode\":0}", 100L));

            WecomSyncWriteResponse response = wecomWriteService.updateRecords("meta", records);

            assertTrue(response.isSuccess());
            assertEquals(1, response.getTotalRecords());
            assertEquals(1, response.getWrittenRecords());
            verify(syncLogMapper, times(1)).insertSyncLog(any());
        }

        @Test
        @DisplayName("记录数量超限（>100） → RECORD_LIMIT_EXCEEDED")
        void updateRecords_exceedLimit() {
            List<Map<String, Object>> records = new ArrayList<>();
            for (int i = 0; i < 101; i++) {
                records.add(makeRecord("记录" + i, i));
            }

            WecomSyncWriteResponse response = wecomWriteService.updateRecords("meta", records);

            assertFalse(response.isSuccess());
            assertEquals("RECORD_LIMIT_EXCEEDED", response.getErrorCode());
            verify(wecomCliService, never()).execute(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("records 为 null → INVALID_REQUEST")
        void updateRecords_nullRecords() {
            WecomSyncWriteResponse response = wecomWriteService.updateRecords("meta", null);

            assertFalse(response.isSuccess());
            assertEquals("INVALID_REQUEST", response.getErrorCode());
            verify(wecomCliService, never()).execute(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("空记录列表 → no-op")
        void updateRecords_emptyRecords() {
            WecomSyncWriteResponse response = wecomWriteService.updateRecords("meta", Collections.emptyList());

            assertTrue(response.isSuccess());
            assertEquals(0, response.getTotalRecords());
            verify(wecomCliService, never()).execute(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("白名单外 sheetName → INVALID_SHEET")
        void updateRecords_invalidSheet() {
            List<Map<String, Object>> records = List.of(makeRecord("测试", 1));

            WecomSyncWriteResponse response = wecomWriteService.updateRecords("forbidden", records);

            assertFalse(response.isSuccess());
            assertEquals("INVALID_SHEET", response.getErrorCode());
            verify(wecomCliService, never()).execute(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("CLI 失败 → 写日志")
        void updateRecords_cliError() {
            List<Map<String, Object>> records = List.of(makeRecord("测试", 1));
            when(wecomCliService.execute(eq("doc"), eq("smartsheet_update_records"), anyString()))
                    .thenReturn(WecomCliResult.fail("CLI_ERROR", "更新失败", 1, 100L));

            WecomSyncWriteResponse response = wecomWriteService.updateRecords("meta", records);

            assertFalse(response.isSuccess());
            assertEquals("CLI_ERROR", response.getErrorCode());
            verify(syncLogMapper, times(1)).insertSyncLog(any());
        }
    }

    // ===================== deleteRecords 测试 =====================

    @Nested
    @DisplayName("deleteRecords - 删除记录")
    class DeleteRecordsTests {

        @Test
        @DisplayName("删除成功")
        void deleteRecords_success() {
            List<String> recordIds = List.of("r1", "r2");
            when(wecomCliService.execute(eq("doc"), eq("smartsheet_delete_records"), anyString()))
                    .thenReturn(WecomCliResult.success("{\"errcode\":0}", 100L));

            WecomSyncWriteResponse response = wecomWriteService.deleteRecords("meta", recordIds);

            assertTrue(response.isSuccess());
            assertEquals(2, response.getTotalRecords());
            verify(syncLogMapper, times(1)).insertSyncLog(any());
        }

        @Test
        @DisplayName("记录数量超限（>100） → RECORD_LIMIT_EXCEEDED")
        void deleteRecords_exceedLimit() {
            List<String> recordIds = new ArrayList<>();
            for (int i = 0; i < 101; i++) {
                recordIds.add("r" + i);
            }

            WecomSyncWriteResponse response = wecomWriteService.deleteRecords("meta", recordIds);

            assertFalse(response.isSuccess());
            assertEquals("RECORD_LIMIT_EXCEEDED", response.getErrorCode());
            verify(wecomCliService, never()).execute(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("recordIds 为 null → INVALID_REQUEST")
        void deleteRecords_nullRecordIds() {
            WecomSyncWriteResponse response = wecomWriteService.deleteRecords("meta", null);

            assertFalse(response.isSuccess());
            assertEquals("INVALID_REQUEST", response.getErrorCode());
            verify(wecomCliService, never()).execute(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("空记录列表 → no-op")
        void deleteRecords_emptyRecordIds() {
            WecomSyncWriteResponse response = wecomWriteService.deleteRecords("meta", Collections.emptyList());

            assertTrue(response.isSuccess());
            assertEquals(0, response.getTotalRecords());
            verify(wecomCliService, never()).execute(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("白名单外 sheetName → INVALID_SHEET")
        void deleteRecords_invalidSheet() {
            List<String> recordIds = List.of("r1");

            WecomSyncWriteResponse response = wecomWriteService.deleteRecords("forbidden", recordIds);

            assertFalse(response.isSuccess());
            assertEquals("INVALID_SHEET", response.getErrorCode());
            verify(wecomCliService, never()).execute(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("CLI 失败 → 写日志")
        void deleteRecords_cliError() {
            List<String> recordIds = List.of("r1");
            when(wecomCliService.execute(eq("doc"), eq("smartsheet_delete_records"), anyString()))
                    .thenReturn(WecomCliResult.fail("CLI_ERROR", "删除失败", 1, 100L));

            WecomSyncWriteResponse response = wecomWriteService.deleteRecords("meta", recordIds);

            assertFalse(response.isSuccess());
            assertEquals("CLI_ERROR", response.getErrorCode());
            verify(syncLogMapper, times(1)).insertSyncLog(any());
        }
    }
}
