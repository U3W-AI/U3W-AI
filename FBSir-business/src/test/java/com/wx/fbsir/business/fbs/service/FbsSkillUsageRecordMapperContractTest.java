package com.wx.fbsir.business.fbs.service;

import com.wx.fbsir.business.fbs.mapper.FbsSkillUsageRecordMapper;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FbsSkillUsageRecordMapperContractTest {
    private static final String RESOURCE = "mapper/fbs/FbsSkillUsageRecordMapper.xml";
    private static Configuration configuration;

    @BeforeAll
    static void parseMapperWithMyBatis() throws Exception {
        configuration = new Configuration();
        configuration.addMapper(FbsSkillUsageRecordMapper.class);
        try (InputStream input = FbsSkillUsageRecordMapperContractTest.class
                .getClassLoader().getResourceAsStream(RESOURCE)) {
            assertNotNull(input, RESOURCE + " must be on the test classpath");
            new XMLMapperBuilder(
                    input, configuration, RESOURCE, configuration.getSqlFragments()).parse();
        }
    }

    @Test
    void terminalTransitionIsCompareAndSetFromInProgressOnly() {
        Map<String, Object> params = new HashMap<>();
        params.put("usageRecordId", "usage-001");
        params.put("status", 1);
        params.put("errorMessage", null);

        BoundSql boundSql = configuration.getMappedStatement(
                FbsSkillUsageRecordMapper.class.getName() + ".updateStatusByRecordId")
                .getBoundSql(params);
        String sql = boundSql.getSql().replaceAll("\\s+", " ")
                .trim().toLowerCase(Locale.ROOT);

        assertTrue(sql.contains("where usage_record_id = ? and status = 0"));
        assertFalse(sql.contains("${"), "usage status CAS must never use string substitution");
    }
}
