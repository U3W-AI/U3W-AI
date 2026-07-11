package com.wx.fbsir.business.fbs.service;

import com.wx.fbsir.business.fbs.domain.entity.FbsAuthCode;
import com.wx.fbsir.business.fbs.domain.entity.FbsEnterprise;
import com.wx.fbsir.business.fbs.domain.entity.FbsEnterpriseMember;
import com.wx.fbsir.business.fbs.dto.business.wecom.CommercialHubSyncContext;
import com.wx.fbsir.business.fbs.dto.business.wecom.WecomSyncWriteResponse;
import com.wx.fbsir.business.fbs.mapper.FbsAuthCodeMapper;
import com.wx.fbsir.business.fbs.mapper.FbsEnterpriseMapper;
import com.wx.fbsir.business.fbs.mapper.FbsEnterpriseMemberMapper;
import com.wx.fbsir.business.fbs.service.impl.WecomBusinessSyncServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
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
 *   <li>SINGLE_SELECT 字段：List&lt;Map&gt; 格式（同文本），如 [{"type":"text","text":"true"}]</li>
 * </ul>
 *
 * @author FBSir
 * @date 2026-04-15
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("企微业务同步服务测试")
class WecomBusinessSyncServiceTest {

    @Mock
    private WecomWriteService wecomWriteService;

    @Mock
    private FbsEnterpriseMemberMapper enterpriseMemberMapper;

    @Mock
    private FbsEnterpriseMapper enterpriseMapper;

    @Mock
    private FbsAuthCodeMapper authCodeMapper;

    @InjectMocks
    private WecomBusinessSyncServiceImpl wecomBusinessSyncService;

    // ---- 辅助方法 ----

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

    /** 构建成功的写入响应 */
    private WecomSyncWriteResponse successResponse() {
        return WecomSyncWriteResponse.ok("commercial_hub", 1, 1, 1, 100L, 1L);
    }

    /** 构建基础个人消费上下文 */
    private CommercialHubSyncContext buildPersonalContext() {
        return CommercialHubSyncContext.builder()
                .userId(1001L)
                .packCode("bookwriter-genre-genealogy")
                .pointsAmount(100)
                .remainPoints(900)
                .hostType("WORKBUDDY")
                .usageRecordId("usr_20260418_001")
                .packId(1L)
                .authCode(null)
                .pointsRuleCode("RULE_GENEALOGY")
                .packType(1)
                .build();
    }

    /** 构建基础企业消费上下文 */
    private CommercialHubSyncContext buildEnterpriseContext() {
        return CommercialHubSyncContext.builder()
                .userId(2001L)
                .packCode("bookwriter-genre-startup")
                .pointsAmount(0)
                .remainPoints(50)
                .hostType("ENTERPRISE")
                .usageRecordId("usr_ent_20260418_001")
                .packId(2L)
                .authCode("AC-GENEALOGY-001")
                .pointsRuleCode("RULE_STARTUP")
                .packType(2)
                .build();
    }

    // =====================================================================
    // syncCommercialHub（新签名）
    // =====================================================================

    @Nested
    @DisplayName("syncCommercialHub(CommercialHubSyncContext) — 新签名")
    class NewSignatureTests {

        @Test
        @DisplayName("个人用户消费完整同步 — 全 24 字段验证")
        void testPersonalConsumeFullSync() {
            CommercialHubSyncContext ctx = buildPersonalContext();

            when(wecomWriteService.writeRecords(eq("commercial_hub"), anyList()))
                    .thenReturn(successResponse());

            wecomBusinessSyncService.syncCommercialHub(ctx);

            verify(wecomWriteService).writeRecords(
                    eq("commercial_hub"),
                    argThat(records -> {
                        Map<String, Object> record = records.get(0);

                        // 全 24 字段都存在
                        assertEquals(24, record.size(), "commercial_hub 应有 24 个字段");

                        // 1. record_id (TEXT)
                        assertNotNull(extractTextField(record.get("record_id")));

                        // 2. record_type
                        assertEquals("SKILL_USAGE", extractTextField(record.get("record_type")));

                        // 3. corp_id — 非企业用户=空字符串
                        assertEquals("", extractTextField(record.get("corp_id")));

                        // 4. user_id — ⚠️ 文本格式验证（非裸 Long）
                        String userId = extractTextField(record.get("user_id"));
                        assertEquals("1001", userId, "user_id 应为文本格式 '1001'");

                        // 5. genre
                        assertEquals("bookwriter-genre-genealogy", extractTextField(record.get("genre")));

                        // 6. event
                        assertEquals("CONSUME", extractTextField(record.get("event")));

                        // 7. delta (NUMBER)
                        assertEquals(-100, record.get("delta"));

                        // 8. balance_after (NUMBER)
                        assertEquals(900, record.get("balance_after"));

                        // 9. credits_required (NUMBER)
                        assertEquals(100, record.get("credits_required"));

                        // 10. code_prefix — 无数据源=空
                        assertEquals("", extractTextField(record.get("code_prefix")));

                        // 11. code_hash — 无数据源=空
                        assertEquals("", extractTextField(record.get("code_hash")));

                        // 12. code_type — 无授权码=空
                        assertEquals("", extractTextField(record.get("code_type")));

                        // 13. redeem_target
                        assertEquals("bookwriter-genre-genealogy", extractTextField(record.get("redeem_target")));

                        // 14. request_id
                        assertEquals("usr_20260418_001", extractTextField(record.get("request_id")));

                        // 15. order_id — MVP 同 request_id
                        assertEquals("usr_20260418_001", extractTextField(record.get("order_id")));

                        // 16. status
                        assertEquals("SUCCESS", extractTextField(record.get("status")));

                        // 17. trial_allowed (SINGLE_SELECT)
                        assertEquals("false", extractTextField(record.get("trial_allowed")));

                        // 18. enterprise_only (SINGLE_SELECT) — packType=1 → false
                        assertEquals("false", extractTextField(record.get("enterprise_only")));

                        // 19. source
                        assertEquals("SKILL_API", extractTextField(record.get("source")));

                        // 20. operator
                        assertEquals("1001", extractTextField(record.get("operator")));

                        // 21. risk_flag — 固定空
                        assertEquals("", extractTextField(record.get("risk_flag")));

                        // 22. payload_json — 非空
                        String payloadJson = extractTextField(record.get("payload_json"));
                        assertNotNull(payloadJson);
                        assertTrue(payloadJson.contains("\"userId\":1001"));
                        assertTrue(payloadJson.contains("\"packCode\":\"bookwriter-genre-genealogy\""));

                        // 23. created_at — 非空时间戳
                        assertNotNull(extractTextField(record.get("created_at")));

                        // 24. updated_at — 同 created_at
                        assertNotNull(extractTextField(record.get("updated_at")));

                        return true;
                    })
            );
        }

        @Test
        @DisplayName("企业用户消费完整同步 — corp_id + enterprise_only=true")
        void testEnterpriseConsumeFullSync() {
            CommercialHubSyncContext ctx = buildEnterpriseContext();

            // Mock 企业关联查询
            FbsEnterpriseMember member = new FbsEnterpriseMember();
            member.setEnterpriseId(10L);
            when(enterpriseMemberMapper.selectActiveByUserId(2001L))
                    .thenReturn(Collections.singletonList(member));

            FbsEnterprise enterprise = new FbsEnterprise();
            enterprise.setEnterpriseCode("ENT_abc123");
            when(enterpriseMapper.selectById(10L)).thenReturn(enterprise);

            // Mock 授权码查询
            FbsAuthCode authCode = new FbsAuthCode();
            authCode.setCodeType(1);
            when(authCodeMapper.selectByAuthCode("AC-GENEALOGY-001")).thenReturn(authCode);

            when(wecomWriteService.writeRecords(eq("commercial_hub"), anyList()))
                    .thenReturn(successResponse());

            wecomBusinessSyncService.syncCommercialHub(ctx);

            verify(wecomWriteService).writeRecords(
                    eq("commercial_hub"),
                    argThat(records -> {
                        Map<String, Object> record = records.get(0);

                        // corp_id 从企业关联查
                        assertEquals("ENT_abc123", extractTextField(record.get("corp_id")));

                        // enterprise_only — packType=2 → true
                        assertEquals("true", extractTextField(record.get("enterprise_only")));

                        // source
                        assertEquals("ENTERPRISE", extractTextField(record.get("source")));

                        // code_type — 从授权码查
                        assertEquals("1", extractTextField(record.get("code_type")));

                        // delta — 企业路径不扣积分
                        assertEquals(0, record.get("delta"));

                        // credits_required — 企业路径=0
                        assertEquals(0, record.get("credits_required"));

                        return true;
                    })
            );
        }
    }

    // =====================================================================
    // user_id 字段格式修复
    // =====================================================================

    @Nested
    @DisplayName("user_id 字段格式修复（OpenSpec #13）")
    class UserIdFormatTests {

        @Test
        @DisplayName("user_id 为文本格式（非裸 Long）")
        void testUserIdIsTextFormat() {
            CommercialHubSyncContext ctx = buildPersonalContext();

            when(wecomWriteService.writeRecords(eq("commercial_hub"), anyList()))
                    .thenReturn(successResponse());

            wecomBusinessSyncService.syncCommercialHub(ctx);

            verify(wecomWriteService).writeRecords(
                    eq("commercial_hub"),
                    argThat(records -> {
                        Map<String, Object> record = records.get(0);

                        // user_id 应该是 List<Map> 格式（文本包装），不是裸 Long
                        Object userIdField = record.get("user_id");
                        assertInstanceOf(List.class, userIdField, "user_id 应为 List 格式（文本包装）");

                        String userIdText = extractTextField(userIdField);
                        assertEquals("1001", userIdText, "user_id 文本值应为 '1001'");

                        return true;
                    })
            );
        }
    }

    // =====================================================================
    // resolveCorpId 测试
    // =====================================================================

    @Nested
    @DisplayName("resolveCorpId — 企业编码关联查询")
    class ResolveCorpIdTests {

        @Test
        @DisplayName("正常路径：userId → enterpriseCode")
        void testResolveCorpIdSuccess() {
            CommercialHubSyncContext ctx = buildEnterpriseContext();

            FbsEnterpriseMember member = new FbsEnterpriseMember();
            member.setEnterpriseId(10L);
            when(enterpriseMemberMapper.selectActiveByUserId(2001L))
                    .thenReturn(Collections.singletonList(member));

            FbsEnterprise enterprise = new FbsEnterprise();
            enterprise.setEnterpriseCode("ENT_xyz789");
            when(enterpriseMapper.selectById(10L)).thenReturn(enterprise);

            when(wecomWriteService.writeRecords(eq("commercial_hub"), anyList()))
                    .thenReturn(successResponse());

            wecomBusinessSyncService.syncCommercialHub(ctx);

            verify(wecomWriteService).writeRecords(
                    eq("commercial_hub"),
                    argThat(records -> {
                        Map<String, Object> record = records.get(0);
                        assertEquals("ENT_xyz789", extractTextField(record.get("corp_id")));
                        return true;
                    })
            );
        }

        @Test
        @DisplayName("降级：非企业用户 → 空字符串")
        void testResolveCorpIdNoEnterprise() {
            CommercialHubSyncContext ctx = buildPersonalContext();

            when(enterpriseMemberMapper.selectActiveByUserId(1001L))
                    .thenReturn(Collections.emptyList());

            when(wecomWriteService.writeRecords(eq("commercial_hub"), anyList()))
                    .thenReturn(successResponse());

            wecomBusinessSyncService.syncCommercialHub(ctx);

            verify(wecomWriteService).writeRecords(
                    eq("commercial_hub"),
                    argThat(records -> {
                        Map<String, Object> record = records.get(0);
                        assertEquals("", extractTextField(record.get("corp_id")));
                        return true;
                    })
            );
        }

        @Test
        @DisplayName("降级：查询抛异常 → 空字符串，同步不中断")
        void testResolveCorpIdException() {
            CommercialHubSyncContext ctx = buildPersonalContext();

            when(enterpriseMemberMapper.selectActiveByUserId(1001L))
                    .thenThrow(new RuntimeException("DB连接异常"));

            when(wecomWriteService.writeRecords(eq("commercial_hub"), anyList()))
                    .thenReturn(successResponse());

            // 不应抛异常
            assertDoesNotThrow(() -> wecomBusinessSyncService.syncCommercialHub(ctx));

            verify(wecomWriteService).writeRecords(
                    eq("commercial_hub"),
                    argThat(records -> {
                        Map<String, Object> record = records.get(0);
                        assertEquals("", extractTextField(record.get("corp_id")));
                        return true;
                    })
            );
        }
    }

    // =====================================================================
    // resolveCodeType 测试
    // =====================================================================

    @Nested
    @DisplayName("resolveCodeType — 授权码类型关联查询")
    class ResolveCodeTypeTests {

        @Test
        @DisplayName("正常路径：authCode → codeType")
        void testResolveCodeTypeSuccess() {
            CommercialHubSyncContext ctx = buildEnterpriseContext();

            FbsAuthCode authCode = new FbsAuthCode();
            authCode.setCodeType(2);
            when(authCodeMapper.selectByAuthCode("AC-GENEALOGY-001")).thenReturn(authCode);

            // Mock 企业查询（corp_id 也需要）
            when(enterpriseMemberMapper.selectActiveByUserId(2001L))
                    .thenReturn(Collections.emptyList());

            when(wecomWriteService.writeRecords(eq("commercial_hub"), anyList()))
                    .thenReturn(successResponse());

            wecomBusinessSyncService.syncCommercialHub(ctx);

            verify(wecomWriteService).writeRecords(
                    eq("commercial_hub"),
                    argThat(records -> {
                        Map<String, Object> record = records.get(0);
                        assertEquals("2", extractTextField(record.get("code_type")));
                        return true;
                    })
            );
        }

        @Test
        @DisplayName("降级：无授权码 → 空字符串")
        void testResolveCodeTypeNoAuthCode() {
            CommercialHubSyncContext ctx = buildPersonalContext(); // authCode=null

            when(wecomWriteService.writeRecords(eq("commercial_hub"), anyList()))
                    .thenReturn(successResponse());

            wecomBusinessSyncService.syncCommercialHub(ctx);

            verify(wecomWriteService).writeRecords(
                    eq("commercial_hub"),
                    argThat(records -> {
                        Map<String, Object> record = records.get(0);
                        assertEquals("", extractTextField(record.get("code_type")));
                        return true;
                    })
            );
        }

        @Test
        @DisplayName("降级：查询抛异常 → 空字符串，同步不中断")
        void testResolveCodeTypeException() {
            CommercialHubSyncContext ctx = CommercialHubSyncContext.builder()
                    .userId(1001L)
                    .packCode("test-pack")
                    .pointsAmount(10)
                    .remainPoints(90)
                    .hostType("WORKBUDDY")
                    .usageRecordId("usr_test")
                    .packId(1L)
                    .authCode("AC-ERROR")  // 有授权码但查询会失败
                    .pointsRuleCode("RULE_TEST")
                    .packType(1)
                    .build();

            when(authCodeMapper.selectByAuthCode("AC-ERROR"))
                    .thenThrow(new RuntimeException("DB连接异常"));

            when(wecomWriteService.writeRecords(eq("commercial_hub"), anyList()))
                    .thenReturn(successResponse());

            assertDoesNotThrow(() -> wecomBusinessSyncService.syncCommercialHub(ctx));

            verify(wecomWriteService).writeRecords(
                    eq("commercial_hub"),
                    argThat(records -> {
                        Map<String, Object> record = records.get(0);
                        assertEquals("", extractTextField(record.get("code_type")));
                        return true;
                    })
            );
        }
    }

    // =====================================================================
    // code_prefix / code_hash 空值
    // =====================================================================

    @Nested
    @DisplayName("code_prefix / code_hash — 无数据源写空字符串")
    class CodePrefixHashTests {

        @Test
        @DisplayName("code_prefix 和 code_hash 始终为空字符串")
        void testCodePrefixAndHashEmpty() {
            CommercialHubSyncContext ctx = buildPersonalContext();

            when(wecomWriteService.writeRecords(eq("commercial_hub"), anyList()))
                    .thenReturn(successResponse());

            wecomBusinessSyncService.syncCommercialHub(ctx);

            verify(wecomWriteService).writeRecords(
                    eq("commercial_hub"),
                    argThat(records -> {
                        Map<String, Object> record = records.get(0);
                        assertEquals("", extractTextField(record.get("code_prefix")));
                        assertEquals("", extractTextField(record.get("code_hash")));
                        return true;
                    })
            );
        }
    }

    // =====================================================================
    // 旧签名兼容
    // =====================================================================

    @Nested
    @DisplayName("旧 4 参数签名兼容（@Deprecated）")
    class LegacySignatureTests {

        @Test
        @DisplayName("旧签名转调新签名，核心字段正常写入")
        @SuppressWarnings("deprecation")
        void testLegacySignatureCompatibility() {
            Long userId = 3001L;
            String packCode = "legacy-pack";
            int pointsAmount = 30;
            int remainPoints = 970;

            when(wecomWriteService.writeRecords(eq("commercial_hub"), anyList()))
                    .thenReturn(successResponse());

            wecomBusinessSyncService.syncCommercialHub(userId, packCode, pointsAmount, remainPoints);

            verify(wecomWriteService).writeRecords(
                    eq("commercial_hub"),
                    argThat(records -> {
                        Map<String, Object> record = records.get(0);

                        // 核心字段正常
                        assertEquals("3001", extractTextField(record.get("user_id")));
                        assertEquals("legacy-pack", extractTextField(record.get("genre")));
                        assertEquals(-30, record.get("delta"));
                        assertEquals(970, record.get("balance_after"));

                        // 扩展字段为默认值
                        assertEquals("", extractTextField(record.get("corp_id")));
                        assertEquals("SKILL_API", extractTextField(record.get("source")));
                        assertEquals("false", extractTextField(record.get("enterprise_only")));
                        assertEquals("false", extractTextField(record.get("trial_allowed")));
                        assertEquals("", extractTextField(record.get("code_type")));
                        assertEquals("", extractTextField(record.get("request_id")));

                        return true;
                    })
            );
        }
    }

    // =====================================================================
    // enterprise_only 判断逻辑
    // =====================================================================

    @Nested
    @DisplayName("enterprise_only 判断逻辑")
    class EnterpriseOnlyTests {

        @Test
        @DisplayName("packType=2 → enterprise_only=true")
        void testEnterpriseOnlyPackType2() {
            CommercialHubSyncContext ctx = CommercialHubSyncContext.builder()
                    .userId(1001L).packCode("ent-pack").pointsAmount(10).remainPoints(90)
                    .hostType("ENTERPRISE").usageRecordId("usr_1").packId(2L)
                    .authCode(null).pointsRuleCode("R").packType(2).build();

            when(wecomWriteService.writeRecords(eq("commercial_hub"), anyList()))
                    .thenReturn(successResponse());

            wecomBusinessSyncService.syncCommercialHub(ctx);

            verify(wecomWriteService).writeRecords(
                    eq("commercial_hub"),
                    argThat(records -> {
                        assertEquals("true", extractTextField(records.get(0).get("enterprise_only")));
                        return true;
                    })
            );
        }

        @Test
        @DisplayName("packType=1 → enterprise_only=false")
        void testEnterpriseOnlyPackType1() {
            CommercialHubSyncContext ctx = CommercialHubSyncContext.builder()
                    .userId(1001L).packCode("plat-pack").pointsAmount(10).remainPoints(90)
                    .hostType("WORKBUDDY").usageRecordId("usr_2").packId(1L)
                    .authCode(null).pointsRuleCode("R").packType(1).build();

            when(wecomWriteService.writeRecords(eq("commercial_hub"), anyList()))
                    .thenReturn(successResponse());

            wecomBusinessSyncService.syncCommercialHub(ctx);

            verify(wecomWriteService).writeRecords(
                    eq("commercial_hub"),
                    argThat(records -> {
                        assertEquals("false", extractTextField(records.get(0).get("enterprise_only")));
                        return true;
                    })
            );
        }

        @Test
        @DisplayName("packType=0（默认）→ enterprise_only=false")
        void testEnterpriseOnlyPackType0() {
            CommercialHubSyncContext ctx = CommercialHubSyncContext.builder()
                    .userId(1001L).packCode("default-pack").pointsAmount(10).remainPoints(90)
                    .hostType("WORKBUDDY").usageRecordId("usr_3").packId(1L)
                    .authCode(null).pointsRuleCode("R").packType(0).build();

            when(wecomWriteService.writeRecords(eq("commercial_hub"), anyList()))
                    .thenReturn(successResponse());

            wecomBusinessSyncService.syncCommercialHub(ctx);

            verify(wecomWriteService).writeRecords(
                    eq("commercial_hub"),
                    argThat(records -> {
                        assertEquals("false", extractTextField(records.get(0).get("enterprise_only")));
                        return true;
                    })
            );
        }
    }

    // =====================================================================
    // source 字段映射
    // =====================================================================

    @Nested
    @DisplayName("source 字段映射逻辑（hostType → source）")
    class SourceMappingTests {

        @Test
        @DisplayName("hostType=WORKBUDDY → source=SKILL_API")
        void testSourceWorkbuddy() {
            CommercialHubSyncContext ctx = CommercialHubSyncContext.builder()
                    .userId(1001L).packCode("pack").pointsAmount(10).remainPoints(90)
                    .hostType("WORKBUDDY").usageRecordId("usr").packId(1L)
                    .authCode(null).pointsRuleCode("R").packType(1).build();

            when(wecomWriteService.writeRecords(eq("commercial_hub"), anyList()))
                    .thenReturn(successResponse());

            wecomBusinessSyncService.syncCommercialHub(ctx);

            verify(wecomWriteService).writeRecords(
                    eq("commercial_hub"),
                    argThat(records -> {
                        assertEquals("SKILL_API", extractTextField(records.get(0).get("source")));
                        return true;
                    })
            );
        }

        @Test
        @DisplayName("hostType=ENTERPRISE → source=ENTERPRISE")
        void testSourceEnterprise() {
            CommercialHubSyncContext ctx = CommercialHubSyncContext.builder()
                    .userId(1001L).packCode("pack").pointsAmount(0).remainPoints(50)
                    .hostType("ENTERPRISE").usageRecordId("usr").packId(2L)
                    .authCode(null).pointsRuleCode("R").packType(2).build();

            when(wecomWriteService.writeRecords(eq("commercial_hub"), anyList()))
                    .thenReturn(successResponse());

            wecomBusinessSyncService.syncCommercialHub(ctx);

            verify(wecomWriteService).writeRecords(
                    eq("commercial_hub"),
                    argThat(records -> {
                        assertEquals("ENTERPRISE", extractTextField(records.get(0).get("source")));
                        return true;
                    })
            );
        }

        @Test
        @DisplayName("hostType=null → source=SKILL_API（默认）")
        void testSourceNull() {
            CommercialHubSyncContext ctx = CommercialHubSyncContext.builder()
                    .userId(1001L).packCode("pack").pointsAmount(10).remainPoints(90)
                    .hostType(null).usageRecordId("usr").packId(1L)
                    .authCode(null).pointsRuleCode("R").packType(1).build();

            when(wecomWriteService.writeRecords(eq("commercial_hub"), anyList()))
                    .thenReturn(successResponse());

            wecomBusinessSyncService.syncCommercialHub(ctx);

            verify(wecomWriteService).writeRecords(
                    eq("commercial_hub"),
                    argThat(records -> {
                        assertEquals("SKILL_API", extractTextField(records.get(0).get("source")));
                        return true;
                    })
            );
        }
    }

    // =====================================================================
    // 写入服务异常兜底
    // =====================================================================

    @Nested
    @DisplayName("写入服务异常兜底")
    class WriteExceptionTests {

        @Test
        @DisplayName("写入服务返回失败，不影响业务")
        void testWriteFailed() {
            CommercialHubSyncContext ctx = buildPersonalContext();

            WecomSyncWriteResponse failResponse = WecomSyncWriteResponse.fail(
                    "commercial_hub", "NET_TIMEOUT", "网络超时", 100L, null
            );
            when(wecomWriteService.writeRecords(eq("commercial_hub"), anyList()))
                    .thenReturn(failResponse);

            assertDoesNotThrow(() -> wecomBusinessSyncService.syncCommercialHub(ctx));
        }

        @Test
        @DisplayName("写入服务抛异常，不影响业务")
        void testWriteException() {
            CommercialHubSyncContext ctx = buildPersonalContext();

            when(wecomWriteService.writeRecords(eq("commercial_hub"), anyList()))
                    .thenThrow(new RuntimeException("模拟异常"));

            assertDoesNotThrow(() -> wecomBusinessSyncService.syncCommercialHub(ctx));
        }
    }
}
