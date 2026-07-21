package com.wx.fbsir.business.board.oauth;

import com.wx.fbsir.business.board.oauth.domain.BoardOAuthTokenFamily;
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
    void compatibilityLiveFamilyLockRemainsAvailableForExistingCallers() {
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
    void requestCodeAndTokenContextLocatorsNeverTakeAuthorizationLocks() {
        String request = sql("selectAuthorizationRequestLocatorByHandle", Map.of(
                "requestHandleDigest", new byte[32]));
        String code = sql("selectAuthorizationCodeLocatorByDigest", Map.of(
                "codeDigest", new byte[32]));
        String tokenExchange = sql("selectTokenExchangeLockLocatorByDigest", Map.of(
                "codeDigest", new byte[32]));
        String codeById = sql("selectAuthorizationCodeLocatorById", Map.of(
                "codeId", 4L));
        String family = sql("selectTokenFamilyLocator", Map.of("familyId", "family-1"));
        String originFamily = sql("selectTokenFamilyByOriginAuthorizationCodeLocator", Map.of(
                "authorizationCodeId", 9L));
        String tokenContext = sql("selectTokenContextLocatorByDigest", Map.of(
                "tokenDigest", new byte[32]));

        for (String locator : java.util.List.of(
                request, code, tokenExchange, codeById, family, originFamily,
                tokenContext)) {
            assertFalse(locator.contains("for update"),
                    "locators discover lock keys but must never authorize or lock");
        }
        assertTrue(codeById.contains("where id = ?"));
        assertTrue(codeById.endsWith("limit 1"));
        assertTrue(tokenContext.contains("inner join fbs_oauth_token_family"));
        assertTrue(tokenContext.contains("f.client_id"));
        assertTrue(tokenContext.contains("f.binding_version"));
        assertTrue(tokenContext.contains("f.principal_subject_digest"));
        String tokenExchangeProjection = tokenExchange.substring(
                tokenExchange.indexOf("select "),
                tokenExchange.indexOf(" from fbs_oauth_authorization_code c"));
        assertFalse(tokenExchangeProjection.contains("code_digest"));
        assertFalse(tokenExchangeProjection.contains("redirect_uri"));
        assertFalse(tokenExchangeProjection.contains("code_challenge"));
        assertFalse(tokenExchangeProjection.contains("state_"));
        assertFalse(tokenExchangeProjection.contains("consent_intent"));
        assertTrue(tokenExchange.contains("as pending_client_id"));
    }

    @Test
    void oauthClientsAreBatchLockedInCanonicalAsciiBinaryOrder() {
        String clients = sql("selectClientsForUpdate", Map.of(
                "clientIds", java.util.List.of("A-client", "a-client")));

        assertTrue(clients.contains("where client_id in ( ? , ? )"));
        assertTrue(clients.endsWith(
                "order by client_id collate ascii_bin for update"));
    }

    @Test
    void exactRequestCodeAndTokenLocksRevalidateLocatorIdentity() {
        String request = sql("selectAuthorizationRequestForUpdate", Map.of(
                "requestId", 3L,
                "clientId", "client-1",
                "requestHandleDigest", new byte[32]));
        String code = sql("selectAuthorizationCodeForUpdate", Map.of(
                "codeId", 4L,
                "clientId", "client-1",
                "codeDigest", new byte[32]));
        String requestByIdAndClient = sql(
                "selectAuthorizationRequestByIdAndClientForUpdate", Map.of(
                        "requestId", 3L,
                        "clientId", "client-1"));
        String codeByIdAndClient = sql(
                "selectAuthorizationCodeByIdAndClientForUpdate", Map.of(
                        "codeId", 4L,
                        "clientId", "client-1"));
        String originFamily = sql("selectTokenFamilyByOriginAuthorizationCodeForUpdate", Map.of(
                "authorizationCodeId", 4L,
                "clientId", "client-1"));
        String token = sql("selectTokenByIdAndDigestForUpdate", Map.of(
                "tokenId", 5L,
                "familyId", "family-1",
                "tokenDigest", new byte[32]));

        assertTrue(request.contains(
                "where id = ? and client_id = ? and request_handle_digest = ?"));
        assertTrue(code.contains("where id = ? and client_id = ? and code_digest = ?"));
        assertTrue(requestByIdAndClient.contains("where id = ? and client_id = ?"));
        assertTrue(codeByIdAndClient.contains("where id = ? and client_id = ?"));
        assertTrue(originFamily.contains(
                "where origin_authorization_code_id = ? and client_id = ?"));
        assertTrue(token.contains(
                "where id = ? and family_id = ? and token_digest = ?"));
        for (String locked : java.util.List.of(
                request, code, requestByIdAndClient, codeByIdAndClient, originFamily, token)) {
            assertTrue(locked.endsWith("limit 1 for update"));
        }
    }

    @Test
    void tokenFamilyCreatedProvenanceReadUsesAllExactLockKeysAndExposesMultiplicity() throws Exception {
        String candidates = sql("selectTokenFamilyCreatedReceiptCandidatesForUpdate", Map.of(
                "familyId", "family-1",
                "clientId", "client-1",
                "authorizationCodeId", 4L));

        assertTrue(candidates.contains("where family_id = ?"));
        assertTrue(candidates.contains("and client_id = ?"));
        assertTrue(candidates.contains("and authorization_code_id = ?"));
        assertTrue(candidates.contains("and action = 'token_family_created'"),
                "the provenance action must be fixed in SQL, never caller-controlled");
        assertTrue(candidates.endsWith("order by id for update"));
        assertFalse(candidates.contains(" limit "),
                "the mapper must expose duplicate candidates so callers can require exactly one");
        assertEquals(java.util.List.class, IndependentBoardOAuthMapper.class
                .getMethod("selectTokenFamilyCreatedReceiptCandidatesForUpdate",
                        String.class, String.class, Long.class)
                .getReturnType(), "a List preserves 0/1/many provenance cardinality");
    }

    @Test
    void activeAndPendingFamiliesUseSeparateUniqueSlotLocksInCanonicalOrder() {
        Map<String, Object> parameters = Map.of(
                "tenantId", 7L,
                "memberId", 11L,
                "productCode", "FBSIR_INDEPENDENT_BOARD",
                "sourceCode", "WORKBUDDY",
                "connectorCode", "fbs-connector");
        String active = sql("selectActiveTokenFamilySlotForUpdate", parameters);
        String pending = sql("selectPendingTokenFamilySlotForUpdate", parameters);

        for (String slot : java.util.List.of(active, pending)) {
            assertTrue(slot.contains("enterprise_id = ?"));
            assertTrue(slot.contains("member_id = ?"));
            assertTrue(slot.contains("cast(product_code as binary) = cast(? as binary)"));
            assertTrue(slot.contains("source_code = ?"));
            assertTrue(slot.contains("connector_code = ?"));
            assertFalse(slot.contains("user_id"),
                    "the unique live slot omits user_id; callers validate the locked row afterwards");
            assertTrue(slot.endsWith("limit 1 for update"));
        }
        assertTrue(active.contains("lifecycle_slot = 'active' and status = 'active'"));
        assertTrue(pending.contains(
                "lifecycle_slot = 'pending_binding' and status = 'pending_binding'"));
        assertTrue(mapperXml.indexOf("id=\"selectActiveTokenFamilySlotForUpdate\"")
                < mapperXml.indexOf("id=\"selectPendingTokenFamilySlotForUpdate\""),
                "the mapper documents the required ACTIVE then PENDING_BINDING call order");
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
    void completionProofRereadsTheExactActiveFamilyTokensAndImmutableReceipt() {
        String family = sql("selectActiveTokenFamilyForUpdate", Map.of(
                "familyId", "family-1", "clientId", "client-1"));
        String tokens = sql("selectActiveFamilyTokensForClientForUpdate", Map.of(
                "familyId", "family-1", "clientId", "client-1"));
        String receipt = sql("selectReceiptByReceiptId", Map.of("receiptId", "receipt-1"));

        assertTrue(family.contains(
                "where family_id = ? and client_id = ? and status = 'active'"));
        assertTrue(family.endsWith("limit 1 for update"));
        assertTrue(tokens.contains("inner join fbs_oauth_token_family f"));
        assertTrue(tokens.contains("f.client_id = ?"));
        assertTrue(tokens.contains("f.status = 'active'"));
        assertTrue(tokens.contains("where t.family_id = ?"));
        assertTrue(tokens.endsWith("order by t.id for update"));
        assertTrue(receipt.contains("where receipt_id = ?"));
        assertTrue(receipt.endsWith("limit 1 for update"));
        assertFalse(receipt.contains("${"));
    }

    @Test
    void everyAuthoritativeOAuthCurrentReadBypassesMyBatisSessionAndSecondLevelCaches() {
        java.util.List<String> statements = java.util.List.of(
                "selectClientForUpdate",
                "selectClientsForUpdate",
                "selectAuthorizationRequestForUpdate",
                "selectAuthorizationRequestByIdAndClientForUpdate",
                "selectAuthorizationRequestByHandleForUpdate",
                "selectAuthorizationCodeForUpdate",
                "selectAuthorizationCodeByIdAndClientForUpdate",
                "selectAuthorizationCodeByDigestForUpdate",
                "selectTokenFamilyByOriginAuthorizationCodeForUpdate",
                "selectTokenFamilyForUpdate",
                "selectActiveTokenFamilyForUpdate",
                "selectLiveTokenFamiliesForUpdate",
                "selectActiveTokenFamilySlotForUpdate",
                "selectPendingTokenFamilySlotForUpdate",
                "selectTokenForUpdate",
                "selectTokenByIdAndDigestForUpdate",
                "selectFamilyTokensForUpdate",
                "selectActiveFamilyTokensForClientForUpdate",
                "selectReceiptByReceiptId",
                "selectTokenFamilyCreatedReceiptCandidatesForUpdate");

        for (String statement : statements) {
            org.apache.ibatis.mapping.MappedStatement mapped = configuration.getMappedStatement(
                    IndependentBoardOAuthMapper.class.getName() + "." + statement);
            assertTrue(mapped.isFlushCacheRequired(), statement + " must flush the local cache");
            assertFalse(mapped.isUseCache(), statement + " must bypass the second-level cache");
        }
    }

    @Test
    void requestAndCodeLifecycleCasUsesStrictTemporalBoundsAndClearsExpiredState() {
        java.util.Date eventAt = new java.util.Date();
        String approve = sql("approveAuthorizationRequestIfVersion", Map.of(
                "request", requestAt(eventAt, "approved"),
                "expectedVersion", 0L));
        String consume = sql("consumeAuthorizationRequestIfVersion", Map.of(
                "request", requestAt(eventAt, "consumed"),
                "expectedVersion", 1L));
        String expireRequest = sql("expireAuthorizationRequestIfVersion", Map.of(
                "requestId", 1L,
                "clientId", "client-1",
                "expectedVersion", 0L,
                "expiredAt", eventAt));
        String consumeCode = sql("consumeAuthorizationCodeForClientIfVersion", Map.of(
                "codeId", 2L,
                "clientId", "client-1",
                "expectedVersion", 0L,
                "usedAt", eventAt));
        String revokeCode = sql("revokeAuthorizationCodeForClientIfVersion", Map.of(
                "codeId", 2L,
                "clientId", "client-1",
                "expectedVersion", 0L,
                "revokedAt", eventAt));
        String expireCode = sql("expireAuthorizationCodeIfVersion", Map.of(
                "codeId", 2L,
                "clientId", "client-1",
                "expectedVersion", 0L,
                "expiredAt", eventAt));

        assertTrue(approve.contains("requested_at <= ? and expires_at > ?"));
        assertTrue(consume.contains("approved_at <= ? and expires_at > ?"));
        assertTrue(consume.contains("consent_intent is not null"));
        assertTrue(expireRequest.contains("status in ('pending', 'approved')"));
        assertTrue(expireRequest.contains(
                "state_key_ref = null, state_nonce = null, state_ciphertext = null"));
        assertTrue(expireRequest.contains("expires_at <= ?"));
        for (String mutation : java.util.List.of(consumeCode, revokeCode)) {
            assertTrue(mutation.contains("client_id = ?"));
            assertTrue(mutation.contains("issued_at <= ?"));
            assertTrue(mutation.contains("expires_at > ?"));
        }
        assertTrue(consumeCode.contains("consent_intent is not null"));
        assertTrue(expireCode.contains("set status = 'expired'"));
        assertTrue(expireCode.contains("expires_at <= ?"));
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
    void namedClientAndFamilyTransitionsCannotAcceptArbitraryTargetStates() {
        java.util.Date eventAt = new java.util.Date();
        String revokeClient = sql("revokeClientIfVersion", Map.of(
                "clientId", "client-1", "expectedVersion", 0L, "revokedAt", eventAt));
        String expireClient = sql("expireClientIfVersion", Map.of(
                "clientId", "client-1", "expectedVersion", 0L, "expiredAt", eventAt));
        String revokeFamily = sql("revokeTokenFamilyIfVersion", Map.of(
                "familyId", "family-1", "clientId", "client-1",
                "expectedVersion", 0L, "revokedAt", eventAt));
        String compromiseFamily = sql("compromiseTokenFamilyIfVersion", Map.of(
                "familyId", "family-1", "clientId", "client-1",
                "expectedVersion", 0L, "compromisedAt", eventAt));
        String expireFamily = sql("expireTokenFamilyIfVersion", Map.of(
                "familyId", "family-1", "clientId", "client-1",
                "expectedVersion", 0L, "expiredAt", eventAt));

        assertTrue(revokeClient.contains("set status = 'revoked'"));
        assertTrue(revokeClient.contains("expires_at > ?"));
        assertTrue(expireClient.contains("set status = 'expired'"));
        assertTrue(expireClient.contains("expires_at <= ?"));
        assertNamedFamilyTransition(revokeFamily, "revoked");
        assertNamedFamilyTransition(compromiseFamily, "compromised");
        assertNamedFamilyTransition(expireFamily, "expired");
        assertTrue(expireFamily.contains("expires_at <= ?"));
    }

    @Test
    void familyActivationAndRefreshRotationBindContextGenerationAndTime() {
        java.util.Date eventAt = new java.util.Date();
        BoardOAuthTokenFamily family = new BoardOAuthTokenFamily();
        family.setFamilyId("family-1");
        family.setClientId("client-1");
        family.setTenantId(7L);
        family.setMemberId(11L);
        family.setUserId(42L);
        family.setProductCode("FBSIR_INDEPENDENT_BOARD");
        family.setSourceCode("WORKBUDDY");
        family.setConnectorCode("fbs-connector");
        family.setIssuerUri("https://api2.u3w.com");
        family.setResourceUri("https://api2.u3w.com/fbs-mcp/mcp");
        family.setScopeCanonical("scope");
        family.setScopeDigest(new byte[32]);
        family.setPrincipalSubjectDigest(new byte[32]);
        family.setConsentIntent(BoardOAuthConsentIntent.FIRST_CONNECT);
        family.setBindingId("binding-1");
        family.setBindingVersion(2L);
        family.setActivatedAt(eventAt);
        String activation = sql("activateTokenFamilyIfVersion", Map.of(
                "family", family, "expectedVersion", 0L));
        String generation = sql("advanceTokenFamilyGenerationAtIfVersion", Map.of(
                "familyId", "family-1",
                "clientId", "client-1",
                "bindingId", "binding-1",
                "bindingVersion", 2L,
                "expectedVersion", 0L,
                "expectedGeneration", 0L,
                "rotatedAt", eventAt));
        String refresh = sql("useRefreshTokenForGenerationIfVersion", Map.of(
                "tokenId", 1L,
                "familyId", "family-1",
                "expectedVersion", 0L,
                "expectedGeneration", 0L,
                "usedAt", eventAt));

        assertTrue(activation.contains("source_code = ?"));
        assertTrue(activation.contains("connector_code = ?"));
        assertTrue(activation.contains("issuer_uri = ?"));
        assertTrue(activation.contains("scope_digest = ?"));
        assertTrue(activation.contains("principal_subject_digest = ?"));
        assertTrue(activation.contains("consent_intent = ?"));
        assertTrue(activation.contains("current_refresh_generation = 0"));
        assertTrue(activation.contains("issued_at <= ? and expires_at > ?"));
        assertTrue(generation.contains("client_id = ?"));
        assertTrue(generation.contains("binding_id = ? and binding_version = ?"));
        assertTrue(generation.contains("current_refresh_generation < 4294967295"));
        assertTrue(generation.contains(
                "issued_at <= ? and activated_at <= ? and expires_at > ?"));
        assertTrue(refresh.contains("generation = ?"));
        assertTrue(refresh.contains("issued_at <= ? and expires_at > ?"));
    }

    @Test
    void safeFamilyTokenTerminationUsesOneCallerSuppliedLogicalTimeAndReturnsAffectedCount() throws Exception {
        String compatibility = sql("revokeActiveFamilyTokens", Map.of(
                "familyId", "family-1",
                "revokedAt", new java.util.Date()));
        String termination = sql("revokeActiveFamilyTokensAtLogicalTime", Map.of(
                "familyId", "family-1",
                "revokedAt", new java.util.Date()));

        assertTrue(compatibility.contains("greatest(issued_at, ?)"),
                "the legacy method remains available only for existing callers");
        assertTrue(termination.contains(
                "when expires_at <= ? then 'expired' else 'revoked'"));
        assertTrue(termination.contains(
                "when expires_at <= ? then null else ?"));
        assertFalse(termination.contains("greatest("));
        assertTrue(termination.contains("version = version + 1"));
        assertTrue(termination.contains(
                "where family_id = ? and status = 'active' and issued_at <= ?"));
        assertEquals(int.class, IndependentBoardOAuthMapper.class
                .getMethod("revokeActiveFamilyTokensAtLogicalTime", String.class, java.util.Date.class)
                .getReturnType(), "callers must compare the affected count with the locked ACTIVE set");
    }

    @Test
    void receiptMapperIsAppendOnly() {
        long receiptMethods = java.util.Arrays.stream(IndependentBoardOAuthMapper.class.getDeclaredMethods())
                .map(Method::getName)
                .filter(name -> name.toLowerCase().contains("receipt"))
                .count();

        assertEquals(6L, receiptMethods);
        assertTrue(configuration.hasStatement(
                IndependentBoardOAuthMapper.class.getName() + ".insertReceipt"));
        assertTrue(configuration.hasStatement(
                IndependentBoardOAuthMapper.class.getName() + ".selectReceiptByReceiptId"));
        assertTrue(configuration.hasStatement(
                IndependentBoardOAuthMapper.class.getName()
                        + ".selectTokenFamilyCreatedReceiptCandidatesForUpdate"));
        assertTrue(configuration.hasStatement(
                IndependentBoardOAuthMapper.class.getName()
                        + ".selectAuthorizationCodeReplayReceiptCandidatesForUpdate"));
        assertTrue(configuration.hasStatement(
                IndependentBoardOAuthMapper.class.getName()
                        + ".selectTokenFamilyCompromisedReceiptCandidatesForUpdate"));
        assertTrue(configuration.hasStatement(
                IndependentBoardOAuthMapper.class.getName()
                        + ".selectTokenFamilyRevokedReceiptCandidatesForUpdate"));
        String normalized = mapperXml.replaceAll("\\s+", " ").toLowerCase();
        String insert = sql("insertReceipt", Map.of());
        String currentRead = sql("selectReceiptByReceiptId", Map.of("receiptId", "receipt-1"));
        assertTrue(insert.contains(
                "enterprise_id, member_id, user_id, principal_subject_digest, actor_type"));
        assertTrue(currentRead.endsWith("limit 1 for update"),
                "completion proof must use an InnoDB current read under REPEATABLE READ");
        assertTrue(normalized.contains("#{principalsubjectdigest}"));
        assertFalse(normalized.contains("update fbs_oauth_receipt"));
        assertFalse(normalized.contains("delete from fbs_oauth_receipt"));
    }

    @Test
    void replayAndFamilyTerminalReceiptCandidatesUseExactLockedProvenance() {
        String replay = sql(
                "selectAuthorizationCodeReplayReceiptCandidatesForUpdate",
                Map.of(
                        "familyId", "family-1",
                        "clientId", "client-1",
                        "authorizationCodeId", 9L));
        String compromised = sql(
                "selectTokenFamilyCompromisedReceiptCandidatesForUpdate",
                Map.of("familyId", "family-1", "clientId", "client-1"));
        String revoked = sql(
                "selectTokenFamilyRevokedReceiptCandidatesForUpdate",
                Map.of("familyId", "family-1", "clientId", "client-1"));

        assertTrue(replay.contains("family_id = ?"));
        assertTrue(replay.contains("client_id = ?"));
        assertTrue(replay.contains("authorization_code_id = ?"));
        assertTrue(replay.contains("action = 'authorization_code_replay_detected'"));
        assertTrue(replay.endsWith("order by id for update"));
        assertTrue(compromised.contains("authorization_code_id is null"));
        assertTrue(compromised.contains("action = 'token_family_compromised'"));
        assertTrue(compromised.endsWith("order by id for update"));
        assertTrue(revoked.contains("authorization_code_id is null"));
        assertTrue(revoked.contains("action = 'token_family_revoked'"));
        assertTrue(revoked.endsWith("order by id for update"));
    }

    private static void assertCas(String sql, String expectedState) {
        assertTrue(sql.contains("version = version + 1"));
        assertTrue(sql.contains("version = ?"));
        assertTrue(sql.contains(expectedState));
    }

    private static void assertNamedFamilyTransition(String sql, String targetState) {
        assertTrue(sql.contains("set status = '" + targetState + "'"));
        assertTrue(sql.contains("client_id = ?"));
        assertTrue(sql.contains("version = ?"));
        assertTrue(sql.contains("status in ('pending_binding', 'active')"));
        assertTrue(sql.contains("issued_at <= ?"));
    }

    private static com.wx.fbsir.business.board.oauth.domain.BoardOAuthAuthorizationRequest requestAt(
            java.util.Date eventAt, String field) {
        com.wx.fbsir.business.board.oauth.domain.BoardOAuthAuthorizationRequest request =
                new com.wx.fbsir.business.board.oauth.domain.BoardOAuthAuthorizationRequest();
        request.setId(1L);
        request.setClientId("client-1");
        if ("approved".equals(field)) {
            request.setApprovedAt(eventAt);
        } else if ("consumed".equals(field)) {
            request.setConsumedAt(eventAt);
        }
        return request;
    }

    private static String sql(String statement, Map<String, Object> parameters) {
        String id = IndependentBoardOAuthMapper.class.getName() + "." + statement;
        assertTrue(configuration.hasStatement(id), "missing mapper statement " + id);
        BoundSql boundSql = configuration.getMappedStatement(id).getBoundSql(parameters);
        return boundSql.getSql().replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
