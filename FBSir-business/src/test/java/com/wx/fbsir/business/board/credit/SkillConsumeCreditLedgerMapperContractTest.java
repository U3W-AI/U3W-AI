package com.wx.fbsir.business.board.credit;

import com.wx.fbsir.business.board.credit.mapper.SkillConsumeCreditLedgerMapper;
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

class SkillConsumeCreditLedgerMapperContractTest {
    private static final String RESOURCE = "mapper/board/SkillConsumeCreditLedgerMapper.xml";
    private static Configuration configuration;
    private static String mapperXml;

    @BeforeAll
    static void parseMapperWithMyBatis() throws Exception {
        configuration = new Configuration();
        configuration.addMapper(SkillConsumeCreditLedgerMapper.class);
        try (InputStream input = SkillConsumeCreditLedgerMapperContractTest.class
                .getClassLoader().getResourceAsStream(RESOURCE)) {
            assertNotNull(input, RESOURCE + " must be on the test classpath");
            byte[] bytes = input.readAllBytes();
            mapperXml = new String(bytes, StandardCharsets.UTF_8);
            try (InputStream parserInput = new java.io.ByteArrayInputStream(bytes)) {
                new XMLMapperBuilder(parserInput, configuration, RESOURCE,
                        configuration.getSqlFragments()).parse();
            }
        }
    }

    @Test
    void allMapperMethodsAreBoundAndImmutableRowsHaveNoMutationStatement() {
        for (Method method : SkillConsumeCreditLedgerMapper.class.getDeclaredMethods()) {
            assertTrue(configuration.hasStatement(
                    SkillConsumeCreditLedgerMapper.class.getName() + "." + method.getName()),
                    "missing mapper statement " + method.getName());
        }
        assertFalse(mapperXml.contains("${"));
        assertFalse(mapperXml.matches("(?is).*<(update|delete)[^>]*id=\"[^\"]*(operation|entry).*"));
    }

    @Test
    void freshLockReadsAreNarrowCacheBypassingAndKeepTheIndexedEqualityPredicates() {
        for (String statement : java.util.List.of(
                "selectUserProjectionForUpdate", "selectAccountForUpdate", "selectBridgeForUpdate")) {
            var mapped = configuration.getMappedStatement(
                    SkillConsumeCreditLedgerMapper.class.getName() + "." + statement);
            assertTrue(mapped.isFlushCacheRequired(), statement);
            assertFalse(mapped.isUseCache(), statement);
            assertTrue(sql(statement).endsWith("for update"), statement);
        }
        String account = sql("selectAccountForUpdate");
        String bridge = sql("selectBridgeForUpdate");
        assertTrue(account.contains("account_scope = ?"));
        assertTrue(account.contains("binary account_scope = binary ?"));
        assertTrue(account.contains("currency_code = ?"));
        assertTrue(account.contains("binary currency_code = binary ?"));
        assertTrue(bridge.contains("account_scope = ?"));
        assertTrue(bridge.contains("currency_code = ?"));
    }

    private static String sql(String statement) {
        String id = SkillConsumeCreditLedgerMapper.class.getName() + "." + statement;
        Map<String, Object> parameters = switch (statement) {
            case "selectUserProjectionForUpdate" -> Map.of("userId", 7L);
            case "selectBridgeForUpdate" -> Map.of(
                    "accountId", "023e4567-e89b-12d3-a456-426614174000", "userId", 7L,
                    "accountScope", "USER_GLOBAL", "currencyCode", "FBS_POINTS");
            default -> Map.of("userId", 7L, "accountScope", "USER_GLOBAL",
                    "currencyCode", "FBS_POINTS");
        };
        BoundSql boundSql = configuration.getMappedStatement(id).getBoundSql(parameters);
        return boundSql.getSql().replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
