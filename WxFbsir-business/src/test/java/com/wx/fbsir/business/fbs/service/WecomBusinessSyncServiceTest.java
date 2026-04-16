package com.wx.fbsir.business.fbs.service;

import com.wx.fbsir.business.fbs.dto.business.wecom.WecomSyncWriteResponse;
import com.wx.fbsir.business.fbs.service.impl.WecomBusinessSyncServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * WecomBusinessSyncServiceImpl 单元测试
 *
 * <p>测试业务数据同步到企微智能表格的逻辑。</p>
 * <p>Mock WecomWriteService，不依赖真实企微 API。</p>
 *
 * <h3>企微智能表格字段格式</h3>
 * <ul>
 *   <li>文本字段：List&lt;Map&gt; 格式，如 [{"type":"text","text":"内容"}]</li>
 *   <li>数字字段：直接值</li>
 *   <li>布尔字段：直接 boolean 值</li>
 * </ul>
 *
 * @author wxfbsir
 * @date 2026-04-15
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("企微业务同步服务测试")
class WecomBusinessSyncServiceTest {

    @Mock
    private WecomWriteService wecomWriteService;

    @InjectMocks
    private WecomBusinessSyncServiceImpl wecomBusinessSyncService;

    // ---- 辅助方法：提取文本字段内容 ----

    /** 从企微格式文本字段中提取实际文本内容 */
    @SuppressWarnings("unchecked")
    private String extractTextField(Object field) {
        if (field instanceof List) {
            List<Map<String, String>> list = (List<Map<String, String>>) field;
            if (!list.isEmpty() && list.get(0).containsKey("text")) {
                return list.get(0).get("text");
            }
        }
        return null;
    }

    @Nested
    @DisplayName("syncCommercialHub 方法")
    class SyncCommercialHubTests {

        @Test
        @DisplayName("成功同步积分消费")
        void testSyncCommercialHubSuccess() {
            // Given
            Long userId = 1001L;
            String packCode = "FBS-test-pack";
            int pointsAmount = 10;
            int remainPoints = 990;

            WecomSyncWriteResponse successResponse = WecomSyncWriteResponse.ok(
                    "commercial_hub", 1, 1, 1, 100L, 1L
            );
            when(wecomWriteService.writeRecords(eq("commercial_hub"), anyList()))
                    .thenReturn(successResponse);

            // When
            wecomBusinessSyncService.syncCommercialHub(userId, packCode, pointsAmount, remainPoints);

            // Then
            verify(wecomWriteService, times(1)).writeRecords(
                    eq("commercial_hub"),
                    argThat(records -> {
                        if (records.size() != 1) return false;
                        Map<String, Object> record = records.get(0);
                        
                        // 文本字段：从企微格式中提取
                        String recordType = extractTextField(record.get("record_type"));
                        String genre = extractTextField(record.get("genre"));
                        String event = extractTextField(record.get("event"));
                        String status = extractTextField(record.get("status"));
                        
                        return "SKILL_USAGE".equals(recordType)
                                && packCode.equals(genre)
                                && "CONSUME".equals(event)
                                && "SUCCESS".equals(status)
                                // 数字字段：直接比较
                                && userId.equals(record.get("user_id"))
                                && record.get("delta").equals(-pointsAmount)
                                && record.get("balance_after").equals(remainPoints)
                                && record.get("credits_required").equals(pointsAmount);
                    })
            );
        }

        @Test
        @DisplayName("写入服务返回失败，不影响业务")
        void testSyncCommercialHubWriteFailed() {
            // Given
            Long userId = 1002L;
            String packCode = "FBS-test-pack";
            int pointsAmount = 20;
            int remainPoints = 80;

            WecomSyncWriteResponse failResponse = WecomSyncWriteResponse.fail(
                    "commercial_hub", "NET_TIMEOUT", "网络超时", 100L, null
            );
            when(wecomWriteService.writeRecords(eq("commercial_hub"), anyList()))
                    .thenReturn(failResponse);

            // When
            wecomBusinessSyncService.syncCommercialHub(userId, packCode, pointsAmount, remainPoints);

            // Then - 不抛异常，正常执行完成
            verify(wecomWriteService, times(1)).writeRecords(eq("commercial_hub"), anyList());
        }

        @Test
        @DisplayName("写入服务抛异常，不影响业务")
        void testSyncCommercialHubWriteException() {
            // Given
            Long userId = 1003L;
            String packCode = "FBS-test-pack";
            int pointsAmount = 30;
            int remainPoints = 70;

            when(wecomWriteService.writeRecords(eq("commercial_hub"), anyList()))
                    .thenThrow(new RuntimeException("模拟异常"));

            // When
            wecomBusinessSyncService.syncCommercialHub(userId, packCode, pointsAmount, remainPoints);

            // Then - 不抛异常，正常执行完成
            verify(wecomWriteService, times(1)).writeRecords(eq("commercial_hub"), anyList());
        }
    }

    @Nested
    @DisplayName("字段映射验证")
    class FieldMappingTests {

        @Test
        @DisplayName("验证所有字段都正确映射")
        void testAllFieldsMapped() {
            // Given
            Long userId = 2001L;
            String packCode = "FBS-mapping-test";
            int pointsAmount = 50;
            int remainPoints = 450;

            WecomSyncWriteResponse successResponse = WecomSyncWriteResponse.ok(
                    "commercial_hub", 1, 1, 1, 100L, 1L
            );
            when(wecomWriteService.writeRecords(eq("commercial_hub"), anyList()))
                    .thenReturn(successResponse);

            // When
            wecomBusinessSyncService.syncCommercialHub(userId, packCode, pointsAmount, remainPoints);

            // Then - 验证所有字段都存在（文本字段是 List 格式，数字是直接值）
            verify(wecomWriteService).writeRecords(
                    eq("commercial_hub"),
                    argThat(records -> {
                        Map<String, Object> record = records.get(0);
                        
                        // 文本字段：应为 List<Map> 格式
                        boolean textFieldsValid = record.get("record_id") instanceof List
                                && record.get("record_type") instanceof List
                                && record.get("genre") instanceof List
                                && record.get("event") instanceof List
                                && record.get("status") instanceof List
                                && record.get("created_at") instanceof List;
                        
                        // 数字字段：直接值
                        boolean numericFieldsValid = record.containsKey("user_id")
                                && record.containsKey("delta")
                                && record.containsKey("balance_after")
                                && record.containsKey("credits_required");
                        
                        return textFieldsValid && numericFieldsValid;
                    })
            );
        }
    }
}
