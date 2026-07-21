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
    void currentAuthorityRechecksTheLiveUserRoleAndPermissionGraph() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("userId", 7L);
        parameters.put("requiredRole", "admin");
        parameters.put("permission", "board:oauth:client:query");
        String sql = sql("selectCurrentAuthority", parameters);

        assertTrue(sql.contains("from sys_user portal_user"));
        assertTrue(sql.contains("portal_user.status = '0'"));
        assertTrue(sql.contains("portal_user.del_flag = '0'"));
        assertTrue(sql.contains("binary required_role.role_key = binary ?"));
        assertTrue(sql.contains("portal_user.user_id = 1"));
        assertFalse(sql.contains("admin_role.role_key = 'admin'"));
        assertTrue(sql.contains("binary permission_menu.perms = binary ?"));
        assertTrue(sql.contains("permission_role.status = '0'"));
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
        assertFalse(sql.contains("f.origin_authorization_code_id as"));
        assertFalse(sql.contains("f.principal_subject_digest as"));
        assertTrue(sql.contains(
                "unhex(b.principal_subject_digest) = f.principal_subject_digest"));
        assertTrue(sql.contains("b.valid_until as current_binding_valid_until"));
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
        assertFalse(sql.contains("ent.connector_binding_id = b.binding_id"));
        assertFalse(sql.contains("ent.connector_verified_at = b.verified_at"));
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

    @Test
    void meCurrentReadsAppendExactMemberUserAndPendingReceiptProof() {
        Map<String, Object> familyParameters = parameters(true);
        familyParameters.put("memberId", 21L);
        familyParameters.put("userId", 7L);
        familyParameters.put("status", null);
        familyParameters.put("highWaterId", null);
        familyParameters.put("lastId", null);
        familyParameters.put("rowLimit", 3);
        familyParameters.put("currentFirst", true);
        String family = sql("selectOAuthFamilies", familyParameters);

        assertTrue(family.contains("f.enterprise_id = ?"));
        assertTrue(family.contains("f.member_id = ?"));
        assertTrue(family.contains("f.user_id = ?"));
        assertTrue(family.contains("access_token.token_type = 'access'"));
        assertTrue(family.contains("pending_refresh.token_type = 'refresh'"));
        assertTrue(family.contains("created_receipt.action = 'token_family_created'"));
        assertTrue(family.contains("created_receipt.evidence_level = 'action_completed'"));
        assertTrue(family.contains("c.registered_at <= f.issued_at"));
        assertTrue(family.contains("access_token.issued_at = f.issued_at"));
        assertTrue(family.contains(
                "access_token.expires_at = timestampadd(minute, 10, f.issued_at)"));
        assertTrue(family.contains("pending_refresh.issued_at = f.issued_at"));
        assertTrue(family.contains("pending_refresh.expires_at = f.expires_at"));
        assertTrue(family.contains("select count(*) from fbs_oauth_token pending_token"));
        assertTrue(family.contains(
                "created_receipt.authorization_code_id = f.origin_authorization_code_id"));
        assertTrue(family.contains("created_receipt.actor_type = 'client'"));
        assertTrue(family.contains(
                "created_receipt.actor_subject_digest = unhex(sha2(f.client_id, 256))"));
        assertTrue(family.contains("created_receipt.created_at = f.issued_at"));
        assertTrue(family.contains("created_receipt.created_at <= ?"));
        assertTrue(family.contains(
                "case when f.status in ('pending_binding', 'active')"
                        + " and f.expires_at > ? then 0 else 1 end"));
        assertTrue(family.endsWith("limit ?"));

        Map<String, Object> bindingParameters = parameters(true);
        bindingParameters.put("memberId", 21L);
        bindingParameters.put("userId", 7L);
        bindingParameters.put("status", null);
        bindingParameters.put("highWaterId", null);
        bindingParameters.put("lastId", null);
        bindingParameters.put("rowLimit", 2);
        String binding = sql("selectConnectorBindings", bindingParameters);

        assertTrue(binding.contains("b.enterprise_id = ?"));
        assertTrue(binding.contains("b.member_id = ?"));
        assertTrue(binding.contains("b.user_id = ?"));
        assertTrue(binding.contains("f.consent_intent = 'first_connect'"));
        assertTrue(binding.contains(
                "activation_receipt.action = 'token_family_activated'"));
        assertTrue(binding.contains("f.consent_intent = 'explicit_reauthorization'"));
        assertTrue(binding.contains(
                "activation_receipt.action = 'token_family_reauthorized'"));
        assertTrue(binding.contains("activation_receipt.actor_type = 'user'"));
        assertTrue(binding.contains("activation_receipt.actor_user_id = f.user_id"));
        assertTrue(binding.contains(
                "activation_receipt.actor_subject_digest = f.principal_subject_digest"));
        assertTrue(binding.contains(
                "binding_receipt.action = 'connector_binding_verified'"));
        assertTrue(binding.contains(
                "unhex(b.principal_subject_digest) = f.principal_subject_digest"));
        assertTrue(binding.contains("f.issued_at <= ?"));
        assertTrue(binding.contains("f.activated_at <= ?"));
        assertTrue(binding.contains("refresh_token.issued_at <= ?"));
        assertTrue(binding.contains("activation_receipt.created_at = f.activated_at"));
        assertTrue(binding.contains("f.activated_at = b.verified_at"));
        assertTrue(binding.contains("f.expires_at = b.valid_until"));
        assertTrue(binding.contains("binding_receipt.actor_user_id = b.user_id"));
        assertTrue(binding.contains("binding_receipt.created_at = b.verified_at"));
    }

    private static Map<String, Object> parameters(boolean tenantScoped) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("status", "ACTIVE");
        parameters.put("memberId", null);
        parameters.put("userId", null);
        parameters.put("now", new Date(1_752_000_000_000L));
        parameters.put("highWaterId", 200L);
        parameters.put("lastId", 123L);
        parameters.put("rowLimit", 101);
        parameters.put("currentFirst", false);
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
