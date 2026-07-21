package com.wx.fbsir.business.board.oauth;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardOAuthTokenMaterialGeneratorTest {
    @Test
    void generatesIndependentOpaque256BitValuesWithoutRenderingThem() {
        BoardOAuthTokenMaterialGenerator generator =
                new BoardOAuthTokenMaterialGenerator();
        Set<String> values = new HashSet<>();
        values.add(generator.generateFamilyId());
        values.add(generator.generateAccessToken());
        values.add(generator.generateRefreshToken());
        values.add(generator.generateReceiptId());
        values.add(generator.generateCorrelationId());

        assertEquals(5, values.size());
        assertTrue(values.stream().allMatch(value ->
                value.matches("[A-Za-z0-9_-]{43}")));
        assertEquals("BoardOAuthTokenMaterialGenerator[REDACTED]",
                generator.toString());
        assertFalse(values.stream().anyMatch(generator.toString()::contains));
    }
}
