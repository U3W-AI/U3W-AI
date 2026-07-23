package com.wx.fbsir.business.point.mapper;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PointsMapperSqlContractTest {

    private static final String RESOURCE = "mapper/point/PointsMapper.xml";
    private static Configuration configuration;
    private static String mapperXml;

    @BeforeAll
    static void parseMapperWithMyBatis() throws Exception {
        configuration = new Configuration();
        configuration.addMapper(PointsMapper.class);
        try (InputStream input = PointsMapperSqlContractTest.class.getClassLoader()
                .getResourceAsStream(RESOURCE)) {
            assertNotNull(input, RESOURCE + " must be packaged with the business module");
            byte[] bytes = input.readAllBytes();
            mapperXml = new String(bytes, StandardCharsets.UTF_8);
            try (InputStream parserInput = new ByteArrayInputStream(bytes)) {
                new XMLMapperBuilder(parserInput, configuration, RESOURCE, configuration.getSqlFragments()).parse();
            }
        }
    }

    @Test
    void everyMapperMethodHasAnXmlStatementWithoutStringSubstitution() {
        for (Method method : PointsMapper.class.getDeclaredMethods()) {
            assertTrue(configuration.hasStatement(PointsMapper.class.getName() + "." + method.getName()),
                    "missing mapper statement " + method.getName());
        }
        assertFalse(mapperXml.contains("${"), "points mapper must never use string substitution");
    }

    @Test
    void conditionalBalanceUpdateIsAnExactParameterizedCompareAndSet() {
        BoundSql boundSql = configuration.getMappedStatement(
                PointsMapper.class.getName() + ".updateUserPointsIfBalance")
                .getBoundSql(Map.of("userId", 1L, "expectedPoints", 100, "points", 90));
        String sql = boundSql.getSql().replaceAll("\\s+", " ").trim().toLowerCase();

        assertEquals("update sys_user set points = ? where user_id = ? "
                        + "and (points = ? or (points is null and ? = 0))", sql);
        assertEquals(List.of("points", "userId", "expectedPoints", "expectedPoints"),
                boundSql.getParameterMappings().stream()
                        .map(mapping -> mapping.getProperty())
                        .collect(Collectors.toList()));
    }
}
