package com.wx.fbsir.business.board.plan;

import com.wx.fbsir.business.board.plan.domain.BoardPlanPolicyReceipt;
import com.wx.fbsir.business.board.plan.mapper.IndependentBoardPlanPolicyMapper;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndependentBoardPlanPolicyMapperContractTest {
    private static final String RESOURCE =
            "mapper/board/IndependentBoardPlanPolicyMapper.xml";
    private static Configuration configuration;
    private static String mapperXml;

    @BeforeAll
    static void parseMapperWithMyBatis() throws Exception {
        configuration = new Configuration();
        configuration.addMapper(IndependentBoardPlanPolicyMapper.class);
        try (InputStream input = IndependentBoardPlanPolicyMapperContractTest.class
                .getClassLoader().getResourceAsStream(RESOURCE)) {
            assertNotNull(input, RESOURCE + " must be on the test classpath");
            byte[] bytes = input.readAllBytes();
            mapperXml = new String(bytes, StandardCharsets.UTF_8);
            try (InputStream parserInput = new java.io.ByteArrayInputStream(bytes)) {
                new XMLMapperBuilder(
                        parserInput, configuration, RESOURCE,
                        configuration.getSqlFragments()).parse();
            }
        }
    }

    @Test
    void everyMapperMethodIsBoundWithoutUnsafeSubstitutionOrBaselineMutation() {
        for (Method method : IndependentBoardPlanPolicyMapper.class.getDeclaredMethods()) {
            assertTrue(configuration.hasStatement(
                    IndependentBoardPlanPolicyMapper.class.getName() + "." + method.getName()),
                    "missing mapper statement " + method.getName());
        }
        assertFalse(mapperXml.contains("${"));
        assertFalse(mapperXml.matches("(?is).*update\\s+fbs_product_plan.*"));
        assertFalse(mapperXml.matches(
                "(?is).*<(update|delete)[^>]*id=\"[^\"]*receipt[^\"]*\".*"));
    }

    @Test
    void currentReadRequiresIdentityHeadAndExactCommittedReceiptTuple() {
        String current = sql("selectCurrentPolicies", Map.of(
                "productCode", "FBSIR_INDEPENDENT_BOARD"));

        assertTrue(current.contains("from fbs_product_plan p"));
        assertTrue(current.contains("inner join fbs_plan_policy_head h"));
        assertTrue(current.contains("inner join fbs_plan_policy_revision_receipt r"));
        assertTrue(current.contains(
                "binary h.active_receipt_id = binary r.receipt_id"));
        assertTrue(current.contains("h.policy_version = r.policy_version"));
        assertTrue(current.contains("binary p.product_code = binary r.product_code"));
        assertTrue(current.contains("binary p.plan_code = binary r.plan_code"));
        assertTrue(current.contains("order by case p.plan_code"));
    }

    @Test
    void freshCommandLocksOnlyMutableHeadsInDeterministicOrderAndUsesFullCas() {
        String locking = sql("selectPolicyHeadCodesForUpdate", Map.of(
                "productCode", "FBSIR_INDEPENDENT_BOARD"));
        assertTrue(locking.contains("from fbs_plan_policy_head h"));
        assertFalse(locking.contains("fbs_product_plan"));
        assertFalse(locking.contains("fbs_plan_policy_revision_receipt"));
        assertFalse(locking.contains(" join "));
        assertTrue(locking.contains("h.product_code = ?"));
        assertTrue(locking.contains("binary h.product_code = binary ?"));
        assertTrue(locking.contains("order by case h.plan_code"));
        assertTrue(locking.endsWith("for update"));
        var mapped = configuration.getMappedStatement(
                IndependentBoardPlanPolicyMapper.class.getName()
                        + ".selectPolicyHeadCodesForUpdate");
        assertTrue(mapped.isFlushCacheRequired());
        assertFalse(mapped.isUseCache());

        String cas = sql("updateHeadIfCurrent", Map.of(
                "productCode", "FBSIR_INDEPENDENT_BOARD",
                "planCode", "BOARD_VIP",
                "expectedReceiptId", "plan-policy-baseline-board-vip-v1",
                "expectedVersion", 1L,
                "nextReceiptId", "123e4567-e89b-12d3-a456-426614174000",
                "nextVersion", 2L,
                "updatedAt", new Date()));
        assertTrue(cas.contains("active_receipt_id = ?"));
        assertTrue(cas.contains("policy_version = ?"));
        assertTrue(cas.contains("binary active_receipt_id = binary ?"));
        assertTrue(cas.contains("and policy_version = ?"));
    }

    @Test
    void idempotencyLookupIsActorScopedAndCaseExact() {
        Map<String, Object> parameters = Map.of(
                "productCode", "FBSIR_INDEPENDENT_BOARD",
                "actorType", "ADMIN_USER",
                "actorUserId", 900L,
                "idempotencyKeyDigest", "a".repeat(64));
        String replay = sql("selectReceiptByActorAndIdempotencyDigest", parameters);
        assertTrue(replay.contains("binary r.product_code = binary ?"));
        assertTrue(replay.contains("binary r.actor_type = binary ?"));
        assertTrue(replay.contains("r.actor_user_id = ?"));
        assertTrue(replay.contains("binary r.idempotency_key_digest = binary ?"));
        assertFalse(replay.contains("for update"));
    }

    @Test
    void receiptInsertPersistsCompleteTypedStateAndAuditIsBounded() {
        BoardPlanPolicyReceipt receipt = new BoardPlanPolicyReceipt();
        receipt.setReceiptId("123e4567-e89b-12d3-a456-426614174000");
        String insert = sql("insertReceipt", receipt);
        for (String column : java.util.List.of(
                "rollback_of_receipt_id", "actor_type", "actor_user_id",
                "idempotency_key_digest", "command_digest", "previous_policy_digest",
                "policy_digest", "daily_meeting_limit", "agenda_limit", "seat_limit",
                "secretary_enabled", "evidence_level")) {
            assertTrue(insert.contains(column), column);
        }
        String audit = sql("selectPolicyReceipts", Map.of(
                "productCode", "FBSIR_INDEPENDENT_BOARD", "limit", 101));
        assertTrue(audit.contains("order by r.created_at desc, r.id desc"));
        assertTrue(audit.endsWith("limit ?"));
    }

    private static String sql(String statement, Object parameters) {
        String id = IndependentBoardPlanPolicyMapper.class.getName() + "." + statement;
        assertTrue(configuration.hasStatement(id), "missing mapper statement " + id);
        BoundSql boundSql = configuration.getMappedStatement(id).getBoundSql(parameters);
        return boundSql.getSql().replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
