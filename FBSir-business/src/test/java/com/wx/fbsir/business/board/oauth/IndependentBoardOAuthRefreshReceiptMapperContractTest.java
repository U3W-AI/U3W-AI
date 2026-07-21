package com.wx.fbsir.business.board.oauth;

import com.wx.fbsir.business.board.oauth.domain.BoardOAuthReceipt;
import com.wx.fbsir.business.board.oauth.mapper.IndependentBoardOAuthMapper;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndependentBoardOAuthRefreshReceiptMapperContractTest {
    private static final String RESOURCE = "mapper/board/IndependentBoardOAuthMapper.xml";
    private static Configuration configuration;

    @BeforeAll
    static void parseMapper() throws Exception {
        configuration = new Configuration();
        configuration.addMapper(IndependentBoardOAuthMapper.class);
        try (InputStream input = IndependentBoardOAuthRefreshReceiptMapperContractTest.class
                .getClassLoader().getResourceAsStream(RESOURCE)) {
            assertNotNull(input, RESOURCE + " must be on the test classpath");
            byte[] bytes = input.readAllBytes();
            try (InputStream parser = new ByteArrayInputStream(bytes)) {
                new XMLMapperBuilder(
                        parser, configuration, RESOURCE, configuration.getSqlFragments()).parse();
            }
        }
    }

    @Test
    void oldReceiptInsertUsesDatabaseV1DefaultWithoutV2Columns() {
        BoardOAuthReceipt receipt = new BoardOAuthReceipt();
        String sql = sql("insertReceipt", receipt);

        assertFalse(sql.contains("receipt_format_version"));
        assertFalse(sql.contains("subject_generation"));
        assertFalse(sql.contains("causation_receipt_id"));
        assertFalse(sql.contains("subject_token_type"));
        assertFalse(sql.contains("security_event_slot"));
    }

    @Test
    void v2ReceiptInsertPersistsAuthoredFieldsButNeverGeneratedColumns() {
        BoardOAuthReceipt receipt = new BoardOAuthReceipt();
        receipt.setReceiptFormatVersion(2);
        String sql = sql("insertReceipt", receipt);

        assertTrue(sql.contains("receipt_format_version"));
        assertTrue(sql.contains("subject_generation"));
        assertTrue(sql.contains("result_generation"));
        assertTrue(sql.contains("causation_receipt_id"));
        assertTrue(sql.contains("before_state_digest"));
        assertTrue(sql.contains("after_state_digest"));
        assertFalse(sql.contains("subject_token_type"));
        assertFalse(sql.contains("security_event_slot"));
    }

    @Test
    void resultMapReadsEveryV2AndGeneratedProjection() {
        ResultMap resultMap = configuration.getResultMap(
                IndependentBoardOAuthMapper.class.getName() + ".OAuthReceiptMap");
        Set<String> properties = resultMap.getResultMappings().stream()
                .map(mapping -> mapping.getProperty())
                .collect(Collectors.toSet());

        assertTrue(properties.containsAll(Set.of(
                "receiptFormatVersion", "subjectGeneration", "resultGeneration",
                "causationReceiptId", "beforeStateDigest", "afterStateDigest",
                "subjectTokenType", "securityEventSlot")));

        BoardOAuthReceipt receipt = new BoardOAuthReceipt();
        MetaObject mapped = SystemMetaObject.forObject(receipt);
        mapped.setValue("subjectTokenType", "REFRESH");
        mapped.setValue("securityEventSlot", "R:family_1:0000000001");
        assertEquals("REFRESH", receipt.getSubjectTokenType());
        assertEquals("R:family_1:0000000001", receipt.getSecurityEventSlot());
    }

    @Test
    void familySecurityLockUsesOneStableIdOrder() {
        String sql = sql("selectRefreshFamilySecurityReceiptsForUpdate",
                Map.of("familyId", "family_1", "clientId", "c".repeat(43)));

        assertTrue(sql.contains("where family_id = ? and client_id = ?"));
        assertTrue(sql.contains("'token_family_created'"));
        assertTrue(sql.contains("'token_family_rotated'"));
        assertTrue(sql.contains("'refresh_replay_detected'"));
        assertTrue(sql.contains("'token_family_revoked'"));
        assertTrue(sql.contains("'token_family_compromised'"));
        assertTrue(sql.endsWith("order by id for update"));
    }

    @Test
    void refreshHotPathUsesBoundedSentinelsInTheSameIdLockOrder() {
        String tokens = sql("selectRefreshFamilyTokensForUpdate",
                Map.of("familyId", "family_1", "rowLimit", 10_001));
        assertTrue(tokens.contains("where family_id = ?"));
        assertTrue(tokens.endsWith("order by id limit ? for update"));

        String receipts = sql(
                "selectBoundedRefreshFamilySecurityReceiptsForUpdate",
                Map.of("familyId", "family_1", "clientId", "c".repeat(43),
                        "rowLimit", 6_001));
        assertTrue(receipts.contains("where family_id = ? and client_id = ?"));
        assertTrue(receipts.endsWith("order by id limit ? for update"));
    }

    @Test
    void exactCauseAndFineGrainedCurrentReadsArePessimisticLocks() {
        String cause = sql("selectReceiptByReceiptIdAndScopeForUpdate",
                Map.of("receiptId", "cause_1", "familyId", "family_1",
                        "clientId", "c".repeat(43)));
        assertTrue(cause.contains(
                "where receipt_id = ? and family_id = ? and client_id = ?"));
        assertTrue(cause.endsWith("limit 1 for update"));

        String rotation = sql("selectTokenFamilyRotatedReceiptCandidatesForUpdate",
                Map.of("familyId", "family_1", "clientId", "c".repeat(43),
                        "tokenId", 401L, "subjectGeneration", 0L));
        assertTrue(rotation.contains("receipt_format_version = 2"));
        assertTrue(rotation.endsWith("order by id for update"));

        String byGeneration = sql(
                "selectTokenFamilyRotatedReceiptByResultGenerationCandidatesForUpdate",
                Map.of("familyId", "family_1", "clientId", "c".repeat(43),
                        "resultGeneration", 1L));
        assertTrue(byGeneration.contains("result_generation = ?"));
        assertTrue(byGeneration.endsWith("order by id for update"));

        String replay = sql("selectRefreshReplayDetectedReceiptCandidatesForUpdate",
                Map.of("familyId", "family_1", "clientId", "c".repeat(43)));
        assertTrue(replay.contains("action = 'refresh_replay_detected'"));
        assertTrue(replay.endsWith("order by id for update"));
    }

    private static String sql(String statement, Object parameters) {
        String id = IndependentBoardOAuthMapper.class.getName() + "." + statement;
        assertTrue(configuration.hasStatement(id), "missing mapper statement " + id);
        BoundSql boundSql = configuration.getMappedStatement(id).getBoundSql(parameters);
        return boundSql.getSql().replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
