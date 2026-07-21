package com.wx.fbsir.business.board.portal;

import com.wx.fbsir.business.board.portal.mapper.IndependentBoardPortalReadMapper;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndependentBoardPortalReadMapperContractTest {

    private static final String RESOURCE =
            "mapper/board/IndependentBoardPortalReadMapper.xml";
    private static Configuration configuration;
    private static String mapperXml;

    @BeforeAll
    static void parseMapperWithMyBatis() throws Exception {
        configuration = new Configuration();
        configuration.addMapper(IndependentBoardPortalReadMapper.class);
        try (InputStream input = IndependentBoardPortalReadMapperContractTest.class
                .getClassLoader().getResourceAsStream(RESOURCE)) {
            assertNotNull(input, RESOURCE + " must be on the test classpath");
            byte[] bytes = input.readAllBytes();
            mapperXml = new String(bytes, StandardCharsets.UTF_8).toLowerCase();
            try (InputStream parserInput = new ByteArrayInputStream(bytes)) {
                new XMLMapperBuilder(parserInput, configuration, RESOURCE,
                        configuration.getSqlFragments()).parse();
            }
        }
    }

    @Test
    void everyPortalReadIsExplicitBoundedUnlockedAndSubstitutionFree() {
        for (java.lang.reflect.Method method
                : IndependentBoardPortalReadMapper.class.getDeclaredMethods()) {
            assertTrue(configuration.hasStatement(
                    IndependentBoardPortalReadMapper.class.getName()
                            + "." + method.getName()));
        }
        assertFalse(mapperXml.contains("${"));
        assertFalse(mapperXml.contains("select *"));
        assertFalse(mapperXml.contains("for update"));
        assertFalse(mapperXml.contains(" insert "));
        assertFalse(mapperXml.contains(" update "));
        assertFalse(mapperXml.contains(" delete "));
    }

    @Test
    void clientPageDerivesNaturalExpiryAndBindsAStableKeysetWindow() {
        String sql = sql("selectOAuthClients", parameters(false));

        assertTrue(sql.contains("from fbs_oauth_client c"));
        assertTrue(sql.contains(
                "when c.status = 'active' and c.expires_at <= ? then 'expired'"));
        assertTrue(sql.contains("else c.status end = ?"));
        assertTrue(sql.contains("coalesce(c.terminated_at, c.expires_at)"));
        assertTrue(sql.contains("c.id <= ?"));
        assertTrue(sql.contains("c.id < ?"));
        assertTrue(sql.contains("c.product_code")
                && sql.contains("fbsir_independent_board"));
        assertTrue(sql.contains("c.source_code = 'workbuddy'"));
        assertTrue(sql.contains("c.connector_code = 'fbs-connector'"));
        assertTrue(sql.contains("c.token_endpoint_auth_method = 'none'"));
        assertTrue(sql.contains("order by c.id desc"));
        assertTrue(sql.endsWith("limit ?"));
        assertFalse(sql.contains("token_digest"));
        assertFalse(sql.contains("fbs_oauth_authorization_code"));
    }

    @Test
    void familyPageKeepsTenantScopeAndFiltersOnTheEffectiveStatusInSql() {
        String sql = sql("selectOAuthFamilies", parameters(true));

        assertTrue(sql.contains("from fbs_oauth_token_family f"));
        assertTrue(sql.contains("left join fbs_enterprise e"));
        assertTrue(sql.contains("left join fbs_oauth_client c"));
        assertTrue(sql.contains("f.enterprise_id = ?"));
        assertTrue(sql.contains(
                "when f.status in ('pending_binding', 'active') and f.expires_at <= ?"));
        assertTrue(sql.contains("then 'expired' else f.status end = ?"));
        assertTrue(sql.contains("coalesce(f.terminated_at, f.expires_at)"));
        assertTrue(sql.contains("f.id <= ?"));
        assertTrue(sql.contains("f.id < ?"));
        assertTrue(sql.contains("f.product_code")
                && sql.contains("fbsir_independent_board"));
        assertTrue(sql.contains("f.source_code = 'workbuddy'"));
        assertTrue(sql.contains("f.connector_code = 'fbs-connector'"));
        assertTrue(sql.contains("order by f.id desc"));
        assertTrue(sql.endsWith("limit ?"));
        assertFalse(sql.contains("token_digest"));
        assertFalse(sql.contains("origin_authorization_code_id"));
        assertFalse(sql.contains("principal_subject_digest"));
    }

    @Test
    void bindingPageStartsFromHistoryAndDerivesCurrentFlagsWithoutFilteringItAway() {
        String sql = sql("selectConnectorBindings", parameters(true));

        assertTrue(sql.contains("from fbs_connector_binding b"));
        assertTrue(sql.contains("left join fbs_enterprise e"));
        assertTrue(sql.contains(
                "from fbs_connector_binding b left join fbs_enterprise e"));
        assertFalse(sql.contains("from fbs_connector_binding b inner join"));
        assertFalse(sql.contains("inner join fbs_product_entitlement"));
        assertTrue(sql.contains("b.enterprise_id = ?"));
        assertTrue(sql.contains("b.status = ?"));
        assertTrue(sql.contains("b.id <= ?"));
        assertTrue(sql.contains("b.id < ?"));
        assertTrue(sql.contains("exists ( select 1 from fbs_product_entitlement"));
        assertTrue(sql.contains("exists ( select 1 from fbs_oauth_token_family"));
        assertTrue(sql.contains("left join fbs_connector_binding_scope s"));
        assertTrue(sql.contains(
                "case s.scope_code when 'identity.read' then 1"
                        + " when 'entitlement.read' then 2"
                        + " when 'board.meeting.reserve' then 3"
                        + " when 'board.receipt.write' then 4 else 5 end"));
        assertTrue(sql.contains("order by candidate.row_id desc"));
        assertTrue(sql.contains("limit ?"));
        assertFalse(sql.contains("evidence_digest"));
        assertFalse(sql.contains("payload_digest"));
        assertFalse(sql.contains("token_digest"));
    }

    private static Map<String, Object> parameters(boolean tenantScoped) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("status", "ACTIVE");
        parameters.put("now", new Date(1_752_000_000_000L));
        parameters.put("highWaterId", 200L);
        parameters.put("lastId", 123L);
        parameters.put("rowLimit", 101);
        if (tenantScoped) {
            parameters.put("tenantId", 7L);
        }
        return parameters;
    }

    private static String sql(String statement, Map<String, Object> parameters) {
        String id = IndependentBoardPortalReadMapper.class.getName() + "." + statement;
        assertTrue(configuration.hasStatement(id), "missing mapper statement " + id);
        BoundSql boundSql = configuration.getMappedStatement(id).getBoundSql(parameters);
        return boundSql.getSql().replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
