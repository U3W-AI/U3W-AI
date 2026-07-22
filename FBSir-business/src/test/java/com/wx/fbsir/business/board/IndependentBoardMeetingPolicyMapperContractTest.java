package com.wx.fbsir.business.board;

import com.wx.fbsir.business.board.mapper.IndependentBoardMapper;
import java.io.InputStream;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndependentBoardMeetingPolicyMapperContractTest {
    private static final String RESOURCE = "mapper/board/IndependentBoardMapper.xml";
    private static Configuration configuration;

    @BeforeAll
    static void parseMapperWithMyBatis() throws Exception {
        configuration = new Configuration();
        configuration.addMapper(IndependentBoardMapper.class);
        try (InputStream input = IndependentBoardMeetingPolicyMapperContractTest.class
                .getClassLoader().getResourceAsStream(RESOURCE)) {
            assertNotNull(input, RESOURCE + " must be on the test classpath");
            new XMLMapperBuilder(
                    input, configuration, RESOURCE, configuration.getSqlFragments()).parse();
        }
    }

    @Test
    void freshReservationReadsOneExactCommittedHeadAndImmutableTypedReceipt() {
        String sql = sql("selectCurrentPlanPolicy", Map.of(
                "productCode", "FBSIR_INDEPENDENT_BOARD",
                "planCode", "BOARD_FREE"));

        assertTrue(sql.contains("from fbs_product_plan p"));
        assertTrue(sql.contains("inner join fbs_plan_policy_head h"));
        assertTrue(sql.contains("inner join fbs_plan_policy_revision_receipt r"));
        assertTrue(sql.contains("h.product_code = p.product_code"));
        assertTrue(sql.contains("h.plan_code = p.plan_code"));
        assertTrue(sql.contains("r.product_code = h.product_code"));
        assertTrue(sql.contains("r.plan_code = h.plan_code"));
        assertTrue(sql.contains("binary r.receipt_id = binary h.active_receipt_id"));
        assertTrue(sql.contains("r.policy_version = h.policy_version"));
        assertTrue(sql.contains("p.product_code = ?"));
        assertTrue(sql.contains("p.plan_code = ?"));
        assertTrue(sql.endsWith("limit 1"));
        assertFalse(sql.contains("for update"),
                "immutable policy reads must not serialize every reservation globally");
        assertFalse(sql.contains("${"));
    }

    @Test
    void committedReservationWritesTheCompleteImmutablePolicyLineage() {
        String sql = sql("insertUsageOperationPolicyReceipt", Map.of());

        assertTrue(sql.startsWith("insert into fbs_usage_operation_policy_receipt"));
        assertTrue(sql.contains("enterprise_id, operation_id, product_code, plan_code"));
        assertTrue(sql.contains("policy_receipt_id, policy_version, policy_digest"));
        assertTrue(sql.contains("values (?, ?, ?, ?, ?, ?, ?)"));
        assertFalse(sql.contains("on duplicate key"));
        assertFalse(sql.contains("${"));
    }

    @Test
    void historicalReplayReadsOnlyTheCommittedOperationLineageAndHistoricalReceipt() {
        String sql = sql("selectOperationPolicyReceipt", Map.of(
                "tenantId", 7L,
                "operationId", "operation-123"));

        assertTrue(sql.contains("from fbs_usage_operation o"));
        assertTrue(sql.contains("inner join fbs_usage_operation_policy_receipt l"));
        assertTrue(sql.contains("l.enterprise_id = o.enterprise_id"));
        assertTrue(sql.contains("l.operation_id = o.operation_id"));
        assertTrue(sql.contains("binary l.operation_id = binary o.operation_id"));
        assertTrue(sql.contains("l.product_code = o.product_code"));
        assertTrue(sql.contains("l.plan_code = o.effective_plan_code"));
        assertTrue(sql.contains("inner join fbs_plan_policy_revision_receipt r"));
        assertTrue(sql.contains("r.product_code = l.product_code"));
        assertTrue(sql.contains("r.plan_code = l.plan_code"));
        assertTrue(sql.contains("r.receipt_id = l.policy_receipt_id"));
        assertTrue(sql.contains("binary r.receipt_id = binary l.policy_receipt_id"));
        assertTrue(sql.contains("r.policy_version = l.policy_version"));
        assertTrue(sql.contains("binary r.policy_digest = binary l.policy_digest"));
        assertTrue(sql.contains("o.enterprise_id = ?"));
        assertTrue(sql.contains("o.operation_id = ?"));
        assertTrue(sql.contains("binary o.operation_id = binary ?"));
        assertTrue(sql.endsWith("limit 1"));
        assertFalse(sql.contains("for update"),
                "the operation is already locked and immutable receipts must stay concurrent");
        assertFalse(sql.contains("fbs_plan_policy_head"),
                "historical replay must never reinterpret the current policy head");
        assertFalse(sql.contains("fbs_product_entitlement"),
                "historical replay must survive later entitlement revocation");
        assertFalse(sql.contains("fbs_enterprise_member"),
                "historical replay must survive later membership removal");
        assertFalse(sql.contains("${"));
    }

    @Test
    void everyMeetingPolicyReadBypassesMyBatisCaches() {
        for (String statement : java.util.List.of(
                "selectCurrentPlanPolicy",
                "selectOperationPolicyReceipt")) {
            MappedStatement mapped = configuration.getMappedStatement(
                    IndependentBoardMapper.class.getName() + "." + statement);
            assertTrue(mapped.isFlushCacheRequired(), statement + " must flush the local cache");
            assertFalse(mapped.isUseCache(), statement + " must bypass the second-level cache");
        }
    }

    private static String sql(String statement, Map<String, Object> parameters) {
        String id = IndependentBoardMapper.class.getName() + "." + statement;
        assertTrue(configuration.hasStatement(id), "missing mapper statement " + id);
        BoundSql boundSql = configuration.getMappedStatement(id).getBoundSql(parameters);
        return boundSql.getSql().replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
