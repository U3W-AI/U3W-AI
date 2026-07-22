package com.wx.fbsir.business.board.credit;

import com.wx.fbsir.business.board.credit.domain.BoardCreditAccount;
import com.wx.fbsir.business.board.credit.mapper.IndependentBoardCreditMapper;
import java.io.InputStream;
import java.lang.reflect.Method;
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

class IndependentBoardCreditMapperContractTest {
    private static final String RESOURCE = "mapper/board/IndependentBoardCreditMapper.xml";
    private static Configuration configuration;
    private static String mapperXml;

    @BeforeAll
    static void parseMapperWithMyBatis() throws Exception {
        configuration = new Configuration();
        configuration.addMapper(IndependentBoardCreditMapper.class);
        try (InputStream input = IndependentBoardCreditMapperContractTest.class
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
    void everyMapperMethodIsBoundAndNoUnsafeStringSubstitutionExists() {
        for (Method method : IndependentBoardCreditMapper.class.getDeclaredMethods()) {
            assertTrue(configuration.hasStatement(
                    IndependentBoardCreditMapper.class.getName() + "." + method.getName()),
                    "missing mapper statement " + method.getName());
        }
        assertFalse(mapperXml.contains("${"));
        assertFalse(mapperXml.matches("(?is).*<(update|delete)[^>]*id=\"[^\"]*(operation|entry).*"),
                "immutable operation and entry rows must have no update/delete mapper");
    }

    @Test
    void financialLocksAreNarrowCurrentReadsThatBypassCaches() {
        for (String statement : java.util.List.of(
                "selectUserProjectionForUpdate", "selectAccountForUpdate",
                "selectOperationByOperationIdForUpdate")) {
            var mapped = configuration.getMappedStatement(
                    IndependentBoardCreditMapper.class.getName() + "." + statement);
            assertTrue(mapped.isFlushCacheRequired(), statement);
            assertFalse(mapped.isUseCache(), statement);
            assertTrue(sql(statement, parameters(statement)).endsWith("for update"), statement);
        }
    }

    @Test
    void idempotencyAndFixedAssetSelectorsAreCaseExact() {
        String replay = sql("selectOperationByIdempotencyKey",
                Map.of("idempotencyKey", "Grant:20260722:0001"));
        String account = sql("selectAccountForUpdate", Map.of(
                "userId", 42L, "accountScope", "USER_GLOBAL",
                "currencyCode", "FBS_POINTS"));

        assertTrue(replay.contains("binary idempotency_key = binary ?"));
        assertTrue(account.contains("binary account_scope = binary ?"));
        assertTrue(account.contains("binary currency_code = binary ?"));
    }

    @Test
    void accountMutationAdvancesVersionSequenceAndHashWithFullStateCas() {
        BoardCreditAccount next = new BoardCreditAccount();
        next.setAccountId("023e4567-e89b-12d3-a456-426614174000");
        next.setUserId(42L);
        next.setAccountScope("USER_GLOBAL");
        next.setCurrencyCode("FBS_POINTS");
        next.setBalance(600L);
        next.setVersion(4L);
        next.setLastEntrySequence(4L);
        next.setLastEntryHash("b".repeat(64));
        next.setUpdatedAt(new java.util.Date());
        String update = sql("updateAccountIfVersion", Map.of(
                "account", next,
                "expectedVersion", 3L,
                "expectedLastEntrySequence", 3L,
                "expectedBalance", 500L,
                "expectedLastEntryHash", "a".repeat(64)));

        assertTrue(update.contains("last_entry_sequence = ?"));
        assertTrue(update.contains("version = ?"));
        assertTrue(update.contains("last_entry_sequence = ?"));
        assertTrue(update.contains("balance = ?"));
        assertTrue(update.contains("last_entry_hash = ?"));
        assertTrue(update.contains(
                "updated_at = greatest(updated_at, current_timestamp(3))"),
                "database time must advance monotonically across host clock rollback");
    }

    @Test
    void auditReadIsStableAndHardBoundedByOneOverflowRow() {
        String audit = sql("selectAuditRowsByAccountId", Map.of(
                "accountId", "023e4567-e89b-12d3-a456-426614174000"));
        assertTrue(audit.contains("order by e.sequence_no desc"));
        assertTrue(audit.endsWith("limit 101"));
        assertTrue(audit.contains("e.request_digest as entry_request_digest"));
        assertTrue(audit.contains("e.account_id as entry_account_id"));
        assertTrue(audit.contains("e.delta_amount as entry_delta_amount"));
        assertTrue(audit.contains("e.balance_before as entry_balance_before"));
        assertTrue(audit.contains("e.balance_after as entry_balance_after"));
        assertTrue(audit.contains("e.created_at as entry_created_at"));
        assertTrue(audit.contains("left join fbs_credit_operation original"));
        assertTrue(audit.contains(
                "original.operation_id = o.reversal_of_operation_id"));
        assertTrue(audit.contains("original.delta_amount as original_delta_amount"));
    }

    @Test
    void committedMembershipProofRequiresEverySequenceLinkThroughTheLockedHead() {
        String proof = sql("selectCommittedChainProof", Map.of(
                "accountId", "023e4567-e89b-12d3-a456-426614174000",
                "fromSequence", 4L,
                "toSequence", 9L));

        assertTrue(proof.contains("count(*) as entry_count"));
        assertTrue(proof.contains("p.sequence_no = e.sequence_no - 1"));
        assertTrue(proof.contains(
                "binary e.previous_entry_hash = binary p.entry_hash"));
        assertTrue(proof.contains("e.balance_before = p.balance_after"));
        assertTrue(proof.contains("e.sequence_no between ? and ?"));
        assertTrue(proof.contains("binary e.account_id = binary ?"));
        assertTrue(proof.endsWith("for share"),
                "the chain proof must be a locking current read after the account-head lock");
    }

    private static Map<String, Object> parameters(String statement) {
        return switch (statement) {
            case "selectUserProjectionForUpdate" -> Map.of("userId", 42L);
            case "selectAccountForUpdate" -> Map.of(
                    "userId", 42L, "accountScope", "USER_GLOBAL",
                    "currencyCode", "FBS_POINTS");
            default -> Map.of("operationId", "123e4567-e89b-12d3-a456-426614174000");
        };
    }

    private static String sql(String statement, Map<String, Object> parameters) {
        String id = IndependentBoardCreditMapper.class.getName() + "." + statement;
        assertTrue(configuration.hasStatement(id), "missing mapper statement " + id);
        BoundSql boundSql = configuration.getMappedStatement(id).getBoundSql(parameters);
        return boundSql.getSql().replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
