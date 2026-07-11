package com.wx.fbsir.business.fbs.service;

import com.wx.fbsir.business.fbs.domain.WecomCliResult;
import com.wx.fbsir.business.fbs.dto.business.wecom.*;
import com.wx.fbsir.business.fbs.mapper.FbsWecomSyncLogMapper;
import com.wx.fbsir.business.fbs.service.impl.WecomSchemaServiceImpl;
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
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * WecomSchemaServiceImpl Unit Test
 *
 * @author FBSir
 * @date 2026-04-15
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Wecom Schema Service Test")
class WecomSchemaServiceTest {

    /** 合法的 docid（长度 >= 20） */
    private static final String VALID_DOCID = "dcIxqVAUO6KLCapL2psEoEbEy5Dn_il9ITZz5tdOYFVubhcKVNiWgEojwnuc0qPGkCPlVydF628yel8-hoeuPObg";
    /** 合法的 sheetId */
    private static final String VALID_SHEET_ID = "sheet-abc123def456";

    @Mock
    private WecomCliService wecomCliService;

    @Mock
    private FbsWecomSyncLogMapper syncLogMapper;

    @Spy
    @InjectMocks
    private WecomSchemaServiceImpl wecomSchemaService;

    @BeforeEach
    void setUp() throws Exception {
        // no config fields needed for schema operations
    }

    // ===================== Sheet Management Tests =====================

    @Nested
    @DisplayName("getSheets")
    class GetSheetsTests {

        @Test
        @DisplayName("success - returns sheet list")
        void getSheets_success() {
            String rawOutput = "{\"errcode\":0,\"sheets\":[{\"sheet_id\":\"s1\",\"title\":\"meta\",\"row_count\":3},{\"sheet_id\":\"s2\",\"title\":\"commercial_hub\",\"row_count\":2}]}";
            when(wecomCliService.execute(eq("doc"), eq("smartsheet_get_sheet"), anyString()))
                    .thenReturn(WecomCliResult.success(rawOutput, 50L));

            WecomGetSheetsResponse response = wecomSchemaService.getSheets(VALID_DOCID);

            assertNotNull(response.getSheets());
            assertEquals(2, response.getSheets().size());
            assertEquals("s1", response.getSheets().get(0).getSheetId());
            assertEquals("meta", response.getSheets().get(0).getTitle());
            assertEquals(3, response.getSheets().get(0).getRowCount());
        }

        @Test
        @DisplayName("CLI error - returns empty list")
        void getSheets_cliError() {
            when(wecomCliService.execute(eq("doc"), eq("smartsheet_get_sheet"), anyString()))
                    .thenReturn(WecomCliResult.fail("CLI_ERROR", "CLI error", 1, 50L));

            WecomGetSheetsResponse response = wecomSchemaService.getSheets(VALID_DOCID);

            assertNotNull(response.getSheets());
            assertTrue(response.getSheets().isEmpty());
        }

        @Test
        @DisplayName("parse error - returns empty list")
        void getSheets_parseError() {
            when(wecomCliService.execute(eq("doc"), eq("smartsheet_get_sheet"), anyString()))
                    .thenReturn(WecomCliResult.success("invalid-json", 50L));

            WecomGetSheetsResponse response = wecomSchemaService.getSheets(VALID_DOCID);

            assertNotNull(response.getSheets());
            assertTrue(response.getSheets().isEmpty());
        }
    }

    @Nested
    @DisplayName("addSheet")
    class AddSheetTests {

        @Test
        @DisplayName("success")
        void addSheet_success() {
            when(wecomCliService.execute(eq("doc"), eq("smartsheet_add_sheet"), anyString()))
                    .thenReturn(WecomCliResult.success("{\"errcode\":0,\"sheet_id\":\"new-sheet-id\",\"title\":\"new-sheet\"}", 100L));

            WecomAddSheetResponse response = wecomSchemaService.addSheet(VALID_DOCID, "new-sheet");

            assertEquals("new-sheet-id", response.getSheetId());
            assertEquals("new-sheet", response.getTitle());
            verify(syncLogMapper, times(1)).insertSyncLog(any());
        }

        @Test
        @DisplayName("CLI error - logs")
        void addSheet_cliError() {
            when(wecomCliService.execute(eq("doc"), eq("smartsheet_add_sheet"), anyString()))
                    .thenReturn(WecomCliResult.fail("CLI_ERROR", "add failed", 1, 100L));

            WecomAddSheetResponse response = wecomSchemaService.addSheet(VALID_DOCID, "new-sheet");

            assertNull(response.getSheetId());
            verify(syncLogMapper, times(1)).insertSyncLog(any());
        }
    }

    @Nested
    @DisplayName("updateSheet")
    class UpdateSheetTests {

        @Test
        @DisplayName("success")
        void updateSheet_success() {
            when(wecomCliService.execute(eq("doc"), eq("smartsheet_update_sheet"), anyString()))
                    .thenReturn(WecomCliResult.success("{\"errcode\":0,\"sheet_id\":\"sheet-1\",\"title\":\"updated-title\"}", 100L));

            WecomAddSheetResponse response = wecomSchemaService.updateSheet(VALID_DOCID, VALID_SHEET_ID, "updated-title");

            assertEquals("sheet-1", response.getSheetId());
            assertEquals("updated-title", response.getTitle());
            verify(syncLogMapper, times(1)).insertSyncLog(any());
        }
    }

    @Nested
    @DisplayName("deleteSheet")
    class DeleteSheetTests {

        @Test
        @DisplayName("success")
        void deleteSheet_success() {
            when(wecomCliService.execute(eq("doc"), eq("smartsheet_delete_sheet"), anyString()))
                    .thenReturn(WecomCliResult.success("{\"errcode\":0}", 100L));

            boolean result = wecomSchemaService.deleteSheet(VALID_DOCID, VALID_SHEET_ID);

            assertTrue(result);
            verify(syncLogMapper, times(1)).insertSyncLog(any());
        }

        @Test
        @DisplayName("idempotent - repeated delete returns success")
        void deleteSheet_idempotent() {
            when(wecomCliService.execute(eq("doc"), eq("smartsheet_delete_sheet"), anyString()))
                    .thenReturn(WecomCliResult.success("{\"errcode\":0}", 100L));

            assertTrue(wecomSchemaService.deleteSheet(VALID_DOCID, VALID_SHEET_ID));
            assertTrue(wecomSchemaService.deleteSheet(VALID_DOCID, VALID_SHEET_ID));

            verify(syncLogMapper, times(2)).insertSyncLog(any());
        }
    }

    // ===================== Field Management Tests =====================

    @Nested
    @DisplayName("getFields")
    class GetFieldsTests {

        @Test
        @DisplayName("success - returns field list")
        void getFields_success() {
            String rawOutput = "{\"errcode\":0,\"fields\":[{\"field_id\":\"f1\",\"field_title\":\"name\",\"field_type\":\"text\"},{\"field_id\":\"f2\",\"field_title\":\"age\",\"field_type\":\"number\"}]}";
            when(wecomCliService.execute(eq("doc"), eq("smartsheet_get_fields"), anyString()))
                    .thenReturn(WecomCliResult.success(rawOutput, 50L));

            WecomGetFieldsResponse response = wecomSchemaService.getFields(VALID_DOCID, VALID_SHEET_ID);

            assertNotNull(response.getFields());
            assertEquals(2, response.getFields().size());
            assertEquals("f1", response.getFields().get(0).getFieldId());
            assertEquals("name", response.getFields().get(0).getFieldTitle());
            assertEquals("text", response.getFields().get(0).getFieldType());
        }

        @Test
        @DisplayName("CLI error - returns empty list")
        void getFields_cliError() {
            when(wecomCliService.execute(eq("doc"), eq("smartsheet_get_fields"), anyString()))
                    .thenReturn(WecomCliResult.fail("CLI_ERROR", "error", 1, 50L));

            WecomGetFieldsResponse response = wecomSchemaService.getFields(VALID_DOCID, VALID_SHEET_ID);

            assertNotNull(response.getFields());
            assertTrue(response.getFields().isEmpty());
        }
    }

    @Nested
    @DisplayName("addFields")
    class AddFieldsTests {

        @Test
        @DisplayName("success")
        void addFields_success() {
            when(wecomCliService.execute(eq("doc"), eq("smartsheet_get_fields"), anyString()))
                    .thenReturn(WecomCliResult.success("{\"errcode\":0,\"fields\":[]}", 50L));
            when(wecomCliService.execute(eq("doc"), eq("smartsheet_add_fields"), anyString()))
                    .thenReturn(WecomCliResult.success("{\"errcode\":0,\"fields\":[{\"field_id\":\"new-f1\",\"field_title\":\"new-field\",\"field_type\":\"text\"}]}", 100L));

            WecomAddFieldsRequest.FieldItem[] fields = {
                    new WecomAddFieldsRequest.FieldItem() {{
                        setFieldTitle("new-field");
                        setFieldType("text");
                    }}
            };
            WecomGetFieldsResponse response = wecomSchemaService.addFields(VALID_DOCID, VALID_SHEET_ID, fields);

            assertNotNull(response.getFields());
            assertEquals(1, response.getFields().size());
            verify(syncLogMapper, times(1)).insertSyncLog(any());
        }

        @Test
        @DisplayName("invalid field type - throws IllegalArgumentException")
        void addFields_invalidFieldType() {
            WecomAddFieldsRequest.FieldItem[] fields = {
                    new WecomAddFieldsRequest.FieldItem() {{
                        setFieldTitle("new-field");
                        setFieldType("invalid_type");
                    }}
            };
            assertThrows(IllegalArgumentException.class,
                    () -> wecomSchemaService.addFields(VALID_DOCID, VALID_SHEET_ID, fields));
            verify(wecomCliService, never()).execute(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("field count exceed limit (140 existing + 15 new > 150) - throws IllegalArgumentException")
        void addFields_exceedLimit() {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < 140; i++) {
                if (i > 0) sb.append(",");
                sb.append("{\"field_id\":\"f").append(i).append("\",\"field_title\":\"field").append(i).append("\",\"field_type\":\"text\"}");
            }
            sb.append("]");
            when(wecomCliService.execute(eq("doc"), eq("smartsheet_get_fields"), anyString()))
                    .thenReturn(WecomCliResult.success("{\"errcode\":0,\"fields\":" + sb + "}", 50L));

            WecomAddFieldsRequest.FieldItem[] fields = new WecomAddFieldsRequest.FieldItem[15];
            for (int i = 0; i < 15; i++) {
                WecomAddFieldsRequest.FieldItem item = new WecomAddFieldsRequest.FieldItem();
                item.setFieldTitle("field-" + i);
                item.setFieldType("text");
                fields[i] = item;
            }

            assertThrows(IllegalArgumentException.class,
                    () -> wecomSchemaService.addFields(VALID_DOCID, VALID_SHEET_ID, fields));
            verify(wecomCliService, never()).execute(eq("doc"), eq("smartsheet_add_fields"), anyString());
        }
    }

    @Nested
    @DisplayName("updateFields")
    class UpdateFieldsTests {

        @Test
        @DisplayName("success")
        void updateFields_success() {
            when(wecomCliService.execute(eq("doc"), eq("smartsheet_update_fields"), anyString()))
                    .thenReturn(WecomCliResult.success("{\"errcode\":0,\"fields\":[{\"field_id\":\"f1\",\"field_title\":\"updated-title\",\"field_type\":\"text\"}]}", 100L));

            WecomUpdateFieldsRequest.FieldItem[] fields = {
                    new WecomUpdateFieldsRequest.FieldItem() {{
                        setFieldId("f1");
                        setFieldTitle("updated-title");
                    }}
            };
            WecomGetFieldsResponse response = wecomSchemaService.updateFields(VALID_DOCID, VALID_SHEET_ID, fields);

            assertNotNull(response.getFields());
            assertEquals(1, response.getFields().size());
            verify(syncLogMapper, times(1)).insertSyncLog(any());
        }
    }

    @Nested
    @DisplayName("deleteFields")
    class DeleteFieldsTests {

        @Test
        @DisplayName("success")
        void deleteFields_success() {
            when(wecomCliService.execute(eq("doc"), eq("smartsheet_delete_fields"), anyString()))
                    .thenReturn(WecomCliResult.success("{\"errcode\":0}", 100L));

            int deleted = wecomSchemaService.deleteFields(VALID_DOCID, VALID_SHEET_ID, new String[]{"f1", "f2"});

            assertEquals(2, deleted);
            verify(syncLogMapper, times(1)).insertSyncLog(any());
        }

        @Test
        @DisplayName("CLI error - returns 0")
        void deleteFields_cliError() {
            when(wecomCliService.execute(eq("doc"), eq("smartsheet_delete_fields"), anyString()))
                    .thenReturn(WecomCliResult.fail("CLI_ERROR", "delete failed", 1, 100L));

            int deleted = wecomSchemaService.deleteFields(VALID_DOCID, VALID_SHEET_ID, new String[]{"f1"});

            assertEquals(0, deleted);
            verify(syncLogMapper, times(1)).insertSyncLog(any());
        }
    }

    // ===================== Parameter Validation Tests (Fail-Closed) =====================

    @Nested
    @DisplayName("parameter validation")
    class ParamValidationTests {

        @Test
        @DisplayName("null docid - throws IllegalArgumentException")
        void nullDocid_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> wecomSchemaService.getSheets(null));
        }

        @Test
        @DisplayName("empty docid - throws IllegalArgumentException")
        void emptyDocid_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> wecomSchemaService.getSheets(""));
        }

        @Test
        @DisplayName("short docid - throws IllegalArgumentException")
        void shortDocid_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> wecomSchemaService.getSheets("too-short"));
        }

        @Test
        @DisplayName("null sheetId - throws IllegalArgumentException")
        void nullSheetId_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> wecomSchemaService.getFields(VALID_DOCID, null));
        }

        @Test
        @DisplayName("empty sheetId - throws IllegalArgumentException")
        void emptySheetId_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> wecomSchemaService.deleteSheet(VALID_DOCID, ""));
        }

        @Test
        @DisplayName("null title - throws IllegalArgumentException")
        void nullTitle_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> wecomSchemaService.addSheet(VALID_DOCID, null));
        }

        @Test
        @DisplayName("null fields array - throws IllegalArgumentException")
        void nullFields_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> wecomSchemaService.addFields(VALID_DOCID, VALID_SHEET_ID, null));
        }

        @Test
        @DisplayName("null fieldIds array - throws IllegalArgumentException")
        void nullFieldIds_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> wecomSchemaService.deleteFields(VALID_DOCID, VALID_SHEET_ID, null));
        }
    }
}
