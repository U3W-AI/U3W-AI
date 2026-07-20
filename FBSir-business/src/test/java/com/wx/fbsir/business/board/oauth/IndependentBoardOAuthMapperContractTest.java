package com.wx.fbsir.business.board.oauth;

import com.wx.fbsir.business.board.oauth.mapper.IndependentBoardOAuthMapper;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndependentBoardOAuthMapperContractTest {
    private static final String RESOURCE = "mapper/board/IndependentBoardOAuthMapper.xml";
    private static Configuration configuration;
    private static String mapperXml;

    @BeforeAll
    static void parseMapperWithMyBatis() throws Exception {
        configuration = new Configuration();
        configuration.addMapper(IndependentBoardOAuthMapper.class);
        try (InputStream input = IndependentBoardOAuthMapperContractTest.class
                .getClassLoader().getResourceAsStream(RESOURCE)) {
            assertNotNull(input, RESOURCE + " must be on the test classpath");
            byte[] bytes = input.readAllBytes();
            mapperXml = new String(bytes, StandardCharsets.UTF_8);
            try (InputStream parserInput = new java.io.ByteArrayInputStream(bytes)) {
                new XMLMapperBuilder(
                        parserInput, configuration, RESOURCE, configuration.getSqlFragments()).parse();
            }
        }
    }

    @Test
    void everyMapperMethodHasAnXmlStatementAndNoStringSubstitution() {
        for (Method method : IndependentBoardOAuthMapper.class.getDeclaredMethods()) {
            String id = IndependentBoardOAuthMapper.class.getName() + "." + method.getName();
            assertTrue(configuration.hasStatement(id), "missing mapper statement " + id);
        }
        assertFalse(mapperXml.contains("${"), "OAuth persistence must never use string substitution");
    }

    @Test
    void liveFamilyLockUsesTheCompleteIdentityAndConnectorTuple() {
        String sql = sql("selectLiveTokenFamiliesForUpdate", Map.of(
                "tenantId", 7L,
                "memberId", 11L,
                "userId", 42L,
                "productCode", "FBSIR_INDEPENDENT_BOARD",
                "sourceCode", "WORKBUDDY",
                "connectorCode", "fbs-connector"));

        assertTrue(sql.contains("enterprise_id = ?"));
        assertTrue(sql.contains("member_id = ?"));
        assertTrue(sql.contains("user_id = ?"));
        assertTrue(sql.contains("cast(product_code as binary) = cast(? as binary)"));
        assertTrue(sql.contains("source_code = ?"));
        assertTrue(sql.contains("connector_code = ?"));
        assertTrue(sql.contains("status in ('pending_binding', 'active')"));
        assertTrue(sql.endsWith("order by id for update"));
    }

    @Test
    void tokenLookupDoesNotReverseTheFamilyThenTokenLockOrder() {
        String locator = sql("selectTokenLocatorByDigest", Map.of("tokenDigest", new byte[32]));
        String locked = sql("selectTokenForUpdate", Map.of(
                "familyId", "family-1", "tokenDigest", new byte[32]));
        String familyTokens = sql("selectFamilyTokensForUpdate", Map.of("familyId", "family-1"));

        assertFalse(locator.contains("for update"),
                "digest lookup is only a locator; callers must lock the family before the token");
        assertTrue(locked.contains("where family_id = ? and token_digest = ?"));
        assertTrue(locked.endsWith("for update"));
        assertTrue(familyTokens.endsWith("order by id for update"));
    }

    @Test
    void lifecycleMutationsAreVersionedCompareAndSetOperations() {
        String client = sql("terminateClientIfVersion", Map.of(
                "clientId", "client-1",
                "expectedVersion", 0L,
                "targetStatus", "TERMINATED",
                "terminatedAt", new java.util.Date()));
        String code = sql("consumeAuthorizationCodeIfVersion", Map.of(
                "codeId", 1L,
                "expectedVersion", 0L,
                "usedAt", new java.util.Date()));
        String family = sql("advanceTokenFamilyGenerationIfVersion", Map.of(
                "familyId", "family-1",
                "expectedVersion", 0L,
                "expectedGeneration", 0L));
        String refresh = sql("useRefreshTokenIfVersion", Map.of(
                "tokenId", 1L,
                "familyId", "family-1",
                "expectedVersion", 0L,
                "usedAt", new java.util.Date()));

        assertCas(client, "status = 'active'");
        assertCas(code, "status = 'active'");
        assertCas(family, "status = 'active'");
        assertTrue(family.contains("current_refresh_generation = ?"));
        assertCas(refresh, "status = 'active'");
        assertTrue(refresh.contains("token_type = 'refresh'"));
    }

    @Test
    void familyTokenTerminationClassifiesExpiredRowsAndUsesALogicalTimeFloor() {
        String termination = sql("revokeActiveFamilyTokens", Map.of(
                "familyId", "family-1",
                "revokedAt", new java.util.Date()));

        assertTrue(termination.contains(
                "when expires_at <= ? then 'expired' else 'revoked'"));
        assertTrue(termination.contains(
                "when expires_at <= ? then null else greatest(issued_at, ?)"));
        assertTrue(termination.contains("version = version + 1"));
        assertTrue(termination.contains("where family_id = ? and status = 'active'"));
    }

    @Test
    void receiptMapperIsAppendOnly() {
        long receiptMethods = java.util.Arrays.stream(IndependentBoardOAuthMapper.class.getDeclaredMethods())
                .map(Method::getName)
                .filter(name -> name.toLowerCase().contains("receipt"))
                .count();

        assertEquals(1L, receiptMethods);
        assertTrue(configuration.hasStatement(
                IndependentBoardOAuthMapper.class.getName() + ".insertReceipt"));
        String normalized = mapperXml.replaceAll("\\s+", " ").toLowerCase();
        String insert = sql("insertReceipt", Map.of());
        assertTrue(insert.contains(
                "enterprise_id, member_id, user_id, principal_subject_digest, actor_type"));
        assertTrue(normalized.contains("#{principalsubjectdigest}"));
        assertFalse(normalized.contains("update fbs_oauth_receipt"));
        assertFalse(normalized.contains("delete from fbs_oauth_receipt"));
    }

    private static void assertCas(String sql, String expectedState) {
        assertTrue(sql.contains("version = version + 1"));
        assertTrue(sql.contains("version = ?"));
        assertTrue(sql.contains(expectedState));
    }

    private static String sql(String statement, Map<String, Object> parameters) {
        String id = IndependentBoardOAuthMapper.class.getName() + "." + statement;
        assertTrue(configuration.hasStatement(id), "missing mapper statement " + id);
        BoundSql boundSql = configuration.getMappedStatement(id).getBoundSql(parameters);
        return boundSql.getSql().replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
