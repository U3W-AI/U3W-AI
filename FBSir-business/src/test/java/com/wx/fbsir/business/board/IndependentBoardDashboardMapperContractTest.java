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

    private static String sql(String statement, Map<String, Object> parameters) {
        String id = IndependentBoardMapper.class.getName() + "." + statement;
        assertTrue(configuration.hasStatement(id), "missing mapper statement " + id);
        BoundSql boundSql = configuration.getMappedStatement(id).getBoundSql(parameters);
        return boundSql.getSql().replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
