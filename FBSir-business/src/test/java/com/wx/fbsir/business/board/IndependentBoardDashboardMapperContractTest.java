package com.wx.fbsir.business.board;

import com.wx.fbsir.business.board.mapper.IndependentBoardMapper;
import java.io.InputStream;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndependentBoardDashboardMapperContractTest {
    private static final String RESOURCE = "mapper/board/IndependentBoardMapper.xml";
    private static Configuration configuration;

    @BeforeAll
    static void parseMapperWithMyBatis() throws Exception {
        configuration = new Configuration();
        configuration.addMapper(IndependentBoardMapper.class);
        try (InputStream input = IndependentBoardDashboardMapperContractTest.class
                .getClassLoader().getResourceAsStream(RESOURCE)) {
            assertNotNull(input, RESOURCE + " must be on the test classpath");
            new XMLMapperBuilder(
                    input, configuration, RESOURCE, configuration.getSqlFragments()).parse();
        }
    }

    @Test
    void contextListIsBoundToTheAuthenticatedUserAndActiveEnterpriseMembership() {
        String sql = sql("selectActiveContextsByUser", Map.of("userId", 42L));

        assertTrue(sql.contains("inner join fbs_enterprise e"));
        assertTrue(sql.contains("where m.user_id = ?"));
        assertTrue(sql.contains("m.status = 1"));
        assertTrue(sql.contains("m.del_flag = '0'"));
        assertTrue(sql.contains("e.status = 1"));
        assertTrue(sql.contains("e.del_flag = '0'"));
        assertTrue(sql.contains("order by m.enterprise_id asc, m.id asc"));
        assertTrue(sql.endsWith("limit 101"));
        assertFalse(sql.contains("${"), "context reads must never use string substitution");
    }

    @Test
    void selectedContextIsBoundToBothTenantAndAuthenticatedUser() {
        String sql = sql("selectActiveContext", Map.of("tenantId", 7L, "userId", 42L));

        assertTrue(sql.contains("inner join fbs_enterprise e"));
        assertTrue(sql.contains("e.id = m.enterprise_id"));
        assertTrue(sql.contains("e.status = 1"));
        assertTrue(sql.contains("e.del_flag = '0'"));
        assertTrue(sql.contains("m.enterprise_id = ?"));
        assertTrue(sql.contains("m.user_id = ?"));
        assertTrue(sql.contains("m.status = 1"));
        assertTrue(sql.contains("m.del_flag = '0'"));
        assertTrue(sql.endsWith("limit 1"));
        assertFalse(sql.contains("${"), "selected context must never use string substitution");
    }

    @Test
    void reservationContextUsesTheSameActiveScopeAndLocksBeforeEntitlement() {
        String sql = sql("selectActiveContextForUpdate", Map.of(
                "tenantId", 7L, "userId", 42L));

        assertTrue(sql.contains("inner join fbs_enterprise e"));
        assertTrue(sql.contains("m.enterprise_id = ?"));
        assertTrue(sql.contains("m.user_id = ?"));
        assertTrue(sql.contains("m.status = 1"));
        assertTrue(sql.contains("e.status = 1"));
        assertTrue(sql.endsWith("limit 1 for update"));
        assertFalse(sql.contains("${"), "reservation context locks must never use substitution");
    }

    @Test
    void recentMeetingReadHasAllScopePredicatesAndAHardLimitOfTen() {
        String sql = sql("selectRecentOperationsByTenantAndUser", Map.of(
                "tenantId", 7L,
                "userId", 42L,
                "productCode", "FBSIR_INDEPENDENT_BOARD",
                "metricCode", "DAILY_MEETING"));

        assertTrue(sql.contains("where enterprise_id = ?"));
        assertTrue(sql.contains("and user_id = ?"));
        assertTrue(sql.contains("and product_code = ?"));
        assertTrue(sql.contains("and metric_code = ?"));
        assertTrue(sql.contains("order by created_at desc, id desc"));
        assertTrue(sql.endsWith("limit 10"));
        assertFalse(sql.contains("${"), "dashboard reads must never use string substitution");
    }

    @Test
    void adminGrantMemberLookupRequiresAnActiveEnterpriseAndExactActiveMember() {
        String sql = sql("selectExactActiveMemberForUpdate", Map.of(
                "tenantId", 7L, "memberId", 11L, "userId", 42L));

        assertTrue(sql.contains("inner join fbs_enterprise e"));
        assertTrue(sql.contains("e.id = m.enterprise_id"));
        assertTrue(sql.contains("e.status = 1"));
        assertTrue(sql.contains("e.del_flag = '0'"));
        assertTrue(sql.contains("m.enterprise_id = ?"));
        assertTrue(sql.contains("m.id = ?"));
        assertTrue(sql.contains("m.user_id = ?"));
        assertTrue(sql.contains("m.status = 1"));
        assertTrue(sql.contains("m.del_flag = '0'"));
        assertTrue(sql.endsWith("limit 1 for update"));
        assertFalse(sql.contains("${"), "grant scope must never use string substitution");
    }

    @Test
    void tokenExchangeAuthorityUsesRawEnterpriseMemberAndPlanSlotsBeforeValidation() {
        String enterprise = sql("selectEnterpriseSlotForUpdate", Map.of(
                "tenantId", 7L));
        String member = sql("selectMemberSlotForUpdate", Map.of(
                "tenantId", 7L, "memberId", 11L));
        String plan = sql("selectPlanSlotForUpdate", Map.of(
                "productCode", "FBSIR_INDEPENDENT_BOARD",
                "planCode", "BOARD_VIP"));

        assertTrue(enterprise.contains("from fbs_enterprise e"));
        assertTrue(enterprise.contains("where e.id = ?"));
        assertFalse(enterprise.contains("e.status = 1"));
        assertFalse(enterprise.contains("e.del_flag = '0'"));
        assertTrue(enterprise.endsWith("limit 1 for update"));

        assertTrue(member.contains("m.enterprise_id = ?"));
        assertTrue(member.contains("m.id = ?"));
        assertFalse(member.contains("m.user_id = ?"));
        assertFalse(member.contains("m.status = 1"));
        assertFalse(member.contains("m.del_flag = '0'"));
        assertFalse(member.contains("join fbs_enterprise"));
        assertTrue(member.endsWith("limit 1 for update"));

        assertTrue(plan.contains("product_code = ?"));
        assertTrue(plan.contains("plan_code = ?"));
        assertFalse(plan.contains("status = 'active'"));
        assertTrue(plan.endsWith("limit 1 for update"));
    }

    @Test
    void adminEntitlementReadIsProductScopedAndFetchesAtMostOneOverflowRow() {
        String sql = sql("selectEntitlementsByTenant", Map.of(
                "tenantId", 7L, "productCode", "FBSIR_INDEPENDENT_BOARD"));

        assertTrue(sql.contains("where enterprise_id = ?"));
        assertTrue(sql.contains("and product_code = ?"));
        assertTrue(sql.contains("order by updated_at desc, id desc"));
        assertTrue(sql.endsWith("limit 101"));
        assertFalse(sql.contains("${"), "entitlement reads must never use string substitution");
    }

    @Test
    void adminPlanCatalogIsProductScopedVersionedStableAndBounded() {
        String sql = sql("selectPlansByProduct", Map.of(
                "productCode", "FBSIR_INDEPENDENT_BOARD"));

        assertTrue(sql.contains("plan_name"));
        assertTrue(sql.contains("r.policy_version as version"));
        assertTrue(sql.contains("r.created_at as updated_at"));
        assertTrue(sql.contains("r.receipt_id as policy_receipt_id"));
        assertTrue(sql.contains("r.policy_digest"));
        assertTrue(sql.contains("from fbs_product_plan p"));
        assertTrue(sql.contains("left join fbs_plan_policy_head h"));
        assertTrue(sql.contains("left join fbs_plan_policy_revision_receipt r"));
        assertTrue(sql.contains("binary r.receipt_id = binary h.active_receipt_id"));
        assertTrue(sql.contains("r.policy_version = h.policy_version"));
        assertTrue(sql.contains("where binary p.product_code = binary ?"));
        assertTrue(sql.contains("order by case p.plan_code"));
        assertTrue(sql.endsWith("limit 3"));
        assertFalse(sql.contains("${"), "plan catalog reads must never use string substitution");
    }

    @Test
    void activePlanConsumesOnlyTheExactCommittedHeadReceipt() {
        String sql = sql("selectActivePlan", Map.of(
                "productCode", "FBSIR_INDEPENDENT_BOARD",
                "planCode", "BOARD_VIP"));

        assertTrue(sql.contains("inner join fbs_plan_policy_head h"));
        assertTrue(sql.contains("inner join fbs_plan_policy_revision_receipt r"));
        assertTrue(sql.contains("binary r.receipt_id = binary h.active_receipt_id"));
        assertTrue(sql.contains("r.policy_version = h.policy_version"));
        assertTrue(sql.contains("r.vip = p.vip"));
        assertTrue(sql.contains("r.connector_required = p.connector_required"));
        assertTrue(sql.contains("binary r.status = binary p.status"));
        assertTrue(sql.contains("r.policy_digest"));
    }

    @Test
    void entitlementLifecycleLockUsesOnlyTheExactDurableEntitlementScope() {
        String sql = sql("selectEntitlementForUpdate", Map.of(
                "tenantId", 7L,
                "memberId", 11L,
                "productCode", "FBSIR_INDEPENDENT_BOARD"));

        assertTrue(sql.contains("where enterprise_id = ?"));
        assertTrue(sql.contains("and member_id = ?"));
        assertTrue(sql.contains("and product_code = ?"));
        assertTrue(sql.endsWith("limit 1 for update"));
        assertFalse(sql.contains("fbs_enterprise_member"),
                "revocation must remain possible after membership removal");
        assertFalse(sql.contains("fbs_enterprise e"),
                "revocation must remain possible after enterprise disablement");
        assertFalse(sql.contains("${"), "entitlement lifecycle locks must never use substitution");
    }

    @Test
    void adminOperationReadUsesImmutablePolicyLineageWithoutCurrentHeadAndHasBoundedOverflowFetch() {
        String sql = sql("selectOperationsByTenant", Map.of(
                "tenantId", 7L,
                "productCode", "FBSIR_INDEPENDENT_BOARD",
                "metricCode", "DAILY_MEETING"));

        assertTrue(sql.contains("from fbs_usage_operation o"));
        assertTrue(sql.contains("left join fbs_usage_operation_policy_receipt l"));
        assertTrue(sql.contains("l.enterprise_id = o.enterprise_id"));
        assertTrue(sql.contains("binary l.operation_id = binary o.operation_id"));
        assertTrue(sql.contains("binary l.plan_code = binary o.effective_plan_code"));
        assertTrue(sql.contains("left join fbs_plan_policy_revision_receipt r"));
        assertTrue(sql.contains("binary r.receipt_id = binary l.policy_receipt_id"));
        assertTrue(sql.contains("r.policy_version = l.policy_version"));
        assertTrue(sql.contains("binary r.policy_digest = binary l.policy_digest"));
        assertTrue(sql.contains("where o.enterprise_id = ?"));
        assertTrue(sql.contains("and o.product_code = ?"));
        assertTrue(sql.contains("and o.metric_code = ?"));
        assertTrue(sql.contains("order by o.created_at desc, o.id desc"));
        assertTrue(sql.endsWith("limit 501"));
        assertFalse(sql.contains("fbs_plan_policy_head"));
        assertFalse(sql.contains("fbs_product_entitlement"));
        assertFalse(sql.contains("for update"));
        assertFalse(sql.contains("${"), "operation reads must never use string substitution");
    }

    @Test
    void entitlementReceiptAuditSelectsOnlyTheSevenSafeFieldsAndIsHardBounded() {
        String sql = sql("selectEntitlementReceiptsByTenant", Map.of("tenantId", 7L));
        String projection = sql.substring("select ".length(), sql.indexOf(" from "));

        assertEquals(
                "receipt_id, enterprise_id, actor_user_id, target_member_id, action, "
                        + "evidence_level, created_at",
                projection);
        assertTrue(sql.contains("where enterprise_id = ?"));
        assertTrue(sql.contains("order by created_at desc, id desc"));
        assertTrue(sql.endsWith("limit 501"));
        assertFalse(sql.contains("payload_digest"));
        assertFalse(sql.contains("product_code"),
                "the board receipt schema has no product_code and must not pretend otherwise");
        assertFalse(sql.contains("${"), "receipt reads must never use string substitution");
    }

    @Test
    void connectorCurrentReadIsBoundToActiveTenantMemberUserProductAndConnector() {
        String sql = sql("selectConnectorBinding", Map.of(
                "tenantId", 7L,
                "memberId", 11L,
                "userId", 42L,
                "productCode", "FBSIR_INDEPENDENT_BOARD",
                "sourceCode", "WORKBUDDY",
                "connectorCode", "fbs-connector"));

        assertTrue(sql.contains("inner join fbs_enterprise_member m"));
        assertTrue(sql.contains("inner join fbs_enterprise e"));
        assertTrue(sql.contains("b.enterprise_id = ?"));
        assertTrue(sql.contains("b.member_id = ?"));
        assertTrue(sql.contains("b.user_id = ?"));
        assertTrue(sql.contains("b.product_code = ?"));
        assertTrue(sql.contains("b.source_code = ?"));
        assertTrue(sql.contains("b.connector_code = ?"));
        assertTrue(sql.endsWith("limit 1"));
        assertFalse(sql.contains("${"), "connector current reads must never use substitution");
    }

    @Test
    void connectorLifecycleLockDoesNotDependOnActiveMembership() {
        String sql = sql("selectConnectorBindingForUpdate", Map.of(
                "tenantId", 7L,
                "memberId", 11L,
                "userId", 42L,
                "productCode", "FBSIR_INDEPENDENT_BOARD",
                "sourceCode", "WORKBUDDY",
                "connectorCode", "fbs-connector"));

        assertTrue(sql.contains("b.enterprise_id = ?"));
        assertTrue(sql.contains("b.member_id = ?"));
        assertTrue(sql.contains("b.user_id = ?"));
        assertTrue(sql.endsWith("limit 1 for update"));
        assertFalse(sql.contains("fbs_enterprise_member"));
        assertFalse(sql.contains("fbs_enterprise e"));
        assertFalse(sql.contains("${"), "connector lifecycle locks must never use substitution");
    }

    @Test
    void connectorAdminBatchUsesOneBoundedJoinInsteadOfNPlusOneScopeReads() {
        String sql = sql("selectConnectorBindingsByTenant", Map.of(
                "tenantId", 7L,
                "productCode", "FBSIR_INDEPENDENT_BOARD",
                "sourceCode", "WORKBUDDY",
                "connectorCode", "fbs-connector"));

        assertTrue(sql.contains("from ( select candidate.*"));
        assertTrue(sql.contains("candidate.enterprise_id = ?"));
        assertTrue(sql.contains("candidate.product_code = ?"));
        assertTrue(sql.contains("inner join fbs_product_entitlement entitlement"));
        assertTrue(sql.contains("cast(entitlement.plan_code as binary) = cast('board_vip' as binary)"));
        assertTrue(sql.contains("entitlement.valid_from <= now(3)"));
        assertTrue(sql.contains("inner join fbs_product_plan plan"));
        assertTrue(sql.contains("cast(plan.product_code as binary) = cast(entitlement.product_code as binary)"));
        assertTrue(sql.contains("cast(plan.plan_code as binary) = cast('board_vip' as binary)"));
        assertTrue(sql.contains("cast(entitlement.status as binary) = cast('active' as binary)"));
        assertTrue(sql.contains("cast(plan.status as binary) = cast('active' as binary)"));
        assertTrue(sql.contains("plan.connector_required = 1"));
        assertFalse(sql.contains("plan.daily_meeting_limit"));
        assertFalse(sql.contains("plan.agenda_limit"));
        assertFalse(sql.contains("plan.seat_limit"));
        assertFalse(sql.contains("plan.secretary_enabled"));
        assertTrue(sql.contains("limit 101"));
        assertTrue(sql.contains("left join fbs_connector_binding_scope s"));
        assertTrue(sql.endsWith("order by b.id asc, s.scope_code asc"));
        assertFalse(sql.contains("${"), "connector batch reads must never use substitution");
    }

    @Test
    void connectorScopeReadHasAOneRowOverflowSentinel() {
        String sql = sql("selectConnectorBindingScopes", Map.of("bindingId", "binding-1"));

        assertTrue(sql.contains("where binding_id = ?"));
        assertTrue(sql.contains("order by scope_code asc"));
        assertTrue(sql.endsWith("limit 5"));
        assertFalse(sql.contains("${"), "connector scope reads must never use substitution");
    }

    @Test
    void connectorScopeLockClosesSnapshotRaceAfterBindingLock() {
        String sql = sql("selectConnectorBindingScopesForUpdate", Map.of("bindingId", "binding-1"));

        assertTrue(sql.contains("where binding_id = ?"));
        assertTrue(sql.contains("order by scope_code asc"));
        assertTrue(sql.endsWith("limit 5 for update"));
        assertFalse(sql.contains("${"), "connector scope locks must never use substitution");
    }

    @Test
    void oauthActivationLocksTheUniqueBindingSlotBeforeValidatingUser() {
        String sql = sql("selectConnectorBindingSlotForUpdate", Map.of(
                "tenantId", 7L,
                "memberId", 11L,
                "productCode", "FBSIR_INDEPENDENT_BOARD",
                "sourceCode", "WORKBUDDY",
                "connectorCode", "fbs-connector"));

        assertTrue(sql.contains("b.enterprise_id = ?"));
        assertTrue(sql.contains("b.member_id = ?"));
        assertTrue(sql.contains("b.product_code = ?"));
        assertTrue(sql.contains("b.source_code = ?"));
        assertTrue(sql.contains("b.connector_code = ?"));
        assertFalse(sql.contains("and b.user_id ="),
                "a drifted user must not turn the unique business slot into a false absence");
        assertTrue(sql.endsWith("limit 1 for update"));
    }

    @Test
    void connectorReceiptCompletionProofIsAnExactCurrentRead() {
        String sql = sql(
                "selectConnectorBindingReceiptForUpdate",
                Map.of("receiptId", "receipt-1"));

        assertTrue(sql.contains("where receipt_id = ?"));
        assertTrue(sql.endsWith("limit 1 for update"));
        assertFalse(sql.contains("${"));
    }

    @Test
    void everyAuthoritativeBoardCurrentReadBypassesMyBatisSessionAndSecondLevelCaches() {
        java.util.List<String> statements = java.util.List.of(
                "selectActiveContextForUpdate",
                "selectExactActiveMemberForUpdate",
                "selectActivePlanForUpdate",
                "selectEntitlementForUpdate",
                "selectConnectorBindingForUpdate",
                "selectConnectorBindingSlotForUpdate",
                "selectConnectorBindingScopesForUpdate",
                "selectConnectorBindingReceiptForUpdate",
                "selectOperationForUpdate",
                "selectCurrentPlanPolicy",
                "selectOperationPolicyReceipt");

        for (String statement : statements) {
            org.apache.ibatis.mapping.MappedStatement mapped = configuration.getMappedStatement(
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
