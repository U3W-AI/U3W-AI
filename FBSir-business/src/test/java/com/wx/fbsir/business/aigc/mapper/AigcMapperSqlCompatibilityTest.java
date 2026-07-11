package com.wx.fbsir.business.aigc.mapper;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AigcMapperSqlCompatibilityTest {

    @Test
    void draftListSelectsOneDeterministicRowWithoutNonAggregatedGroupBy() throws Exception {
        try (InputStream input = getClass().getClassLoader()
            .getResourceAsStream("mapper/aigc/AigcMapper.xml")) {
            assertNotNull(input, "AigcMapper.xml must be available on the test classpath");
            String xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            String statement = selectStatement(xml, "getPlayWrightDraftList");

            assertFalse(statement.toLowerCase().contains("group by"),
                "draft list must remain compatible with ONLY_FULL_GROUP_BY");
            assertTrue(statement.contains("NOT EXISTS"),
                "draft list must explicitly select the latest row per task");
            assertTrue(statement.contains("newer.id &gt; wpd.id"),
                "equal timestamps need a deterministic primary-key tie breaker");
        }
    }

    private String selectStatement(String xml, String id) {
        String marker = "id=\"" + id + "\"";
        int markerIndex = xml.indexOf(marker);
        assertTrue(markerIndex >= 0, "missing mapper statement: " + id);
        int selectStart = xml.lastIndexOf("<select", markerIndex);
        int selectEnd = xml.indexOf("</select>", markerIndex);
        assertTrue(selectStart >= 0 && selectEnd > markerIndex, "invalid mapper statement: " + id);
        return xml.substring(selectStart, selectEnd);
    }
}
