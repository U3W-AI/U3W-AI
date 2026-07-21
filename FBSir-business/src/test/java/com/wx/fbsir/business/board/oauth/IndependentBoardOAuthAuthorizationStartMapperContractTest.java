package com.wx.fbsir.business.board.oauth;

import com.wx.fbsir.business.board.oauth.domain.BoardOAuthAuthorizationRequest;
import com.wx.fbsir.business.board.oauth.mapper.IndependentBoardOAuthMapper;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndependentBoardOAuthAuthorizationStartMapperContractTest {
    private static final String RESOURCE = "mapper/board/IndependentBoardOAuthMapper.xml";
    private static Configuration configuration;
    private static String normalizedXml;

    @BeforeAll
    static void parseMapper() throws Exception {
        configuration = new Configuration();
        configuration.addMapper(IndependentBoardOAuthMapper.class);
        try (InputStream input = IndependentBoardOAuthAuthorizationStartMapperContractTest.class
                .getClassLoader().getResourceAsStream(RESOURCE)) {
            assertNotNull(input, RESOURCE + " must be on the test classpath");
            byte[] bytes = input.readAllBytes();
            normalizedXml = new String(bytes, StandardCharsets.UTF_8)
                    .replaceAll("\\s+", " ");
            try (InputStream parser = new ByteArrayInputStream(bytes)) {
                new XMLMapperBuilder(
                        parser, configuration, RESOURCE, configuration.getSqlFragments()).parse();
            }
        }
    }

    @Test
    void clientLookupIsAnExactPessimisticLock() {
        String sql = sql("selectClientForUpdate", Map.of("clientId", "c".repeat(43)));

        assertTrue(sql.contains("where client_id = ?"));
        assertTrue(sql.endsWith("for update"));
    }

    @Test
    void pendingInsertPersistsOnlyHandleStateDigestsAndEncryptedState() {
        String sql = sql("insertAuthorizationRequest", new BoardOAuthAuthorizationRequest());

        assertTrue(sql.startsWith("insert into fbs_oauth_authorization_request"));
        assertTrue(sql.contains("request_handle_digest"));
        assertTrue(sql.contains("state_digest"));
        assertTrue(sql.contains("state_key_ref"));
        assertTrue(sql.contains("state_nonce"));
        assertTrue(sql.contains("state_ciphertext"));
        assertTrue(sql.contains("enterprise_id, member_id, user_id, principal_subject_digest"));
        assertFalse(sql.contains("consent_intent"));
        assertFalse(sql.contains("fbs_oauth_receipt"));

        assertTrue(normalizedXml.contains("#{requestHandleDigest}"));
        assertTrue(normalizedXml.contains("#{stateDigest}"));
        assertTrue(normalizedXml.contains("#{stateCiphertext}"));
        assertFalse(normalizedXml.contains("#{requestHandle}"));
        assertFalse(normalizedXml.contains("#{rawRequestHandle}"));
        assertFalse(normalizedXml.contains("#{state}"));
        assertFalse(normalizedXml.contains("#{rawState}"));
    }

    private static String sql(String statement, Object parameters) {
        String id = IndependentBoardOAuthMapper.class.getName() + "." + statement;
        assertTrue(configuration.hasStatement(id), "missing mapper statement " + id);
        BoundSql boundSql = configuration.getMappedStatement(id).getBoundSql(parameters);
        return boundSql.getSql().replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
