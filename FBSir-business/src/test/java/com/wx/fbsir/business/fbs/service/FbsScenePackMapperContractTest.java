package com.wx.fbsir.business.fbs.service;

import com.wx.fbsir.business.fbs.mapper.FbsScenePackMapper;
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

class FbsScenePackMapperContractTest {
    private static final String RESOURCE = "mapper/fbs/FbsScenePackMapper.xml";
    private static Configuration configuration;

    @BeforeAll
    static void parseMapperWithMyBatis() throws Exception {
        configuration = new Configuration();
        configuration.addMapper(FbsScenePackMapper.class);
        try (InputStream input = FbsScenePackMapperContractTest.class
                .getClassLoader().getResourceAsStream(RESOURCE)) {
            assertNotNull(input, RESOURCE + " must be on the test classpath");
            new XMLMapperBuilder(
                    input, configuration, RESOURCE, configuration.getSqlFragments()).parse();
        }
    }

    @Test
    void apiKeyScopeIdentityReadIsBinaryExactAndNarrow() {
        Map<String, Object> params = new HashMap<>();
        params.put("packCode", "PACK_BOARD");

        BoundSql boundSql = configuration.getMappedStatement(
                FbsScenePackMapper.class.getName() + ".selectIdentitiesByPackCodeExact")
                .getBoundSql(params);
        String sql = boundSql.getSql().replaceAll("\\s+", " ")
                .trim().toLowerCase(Locale.ROOT);

        assertTrue(sql.startsWith("select id, pack_code from fbs_scene_pack"));
        assertTrue(sql.contains("where pack_code = ? and binary pack_code = binary ?"
                + " and del_flag = '0'"));
        assertTrue(sql.endsWith("limit 2"),
                "scope resolution must detect ambiguity without an unbounded drift scan");
        assertFalse(sql.contains("content_snapshot"));
        assertFalse(sql.contains("pack_name"));
        assertFalse(sql.contains("${"), "scope identity lookup must never use string substitution");
    }
}
