package com.wx.fbsir.engine.playwright.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafePathResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesNormalSegmentsUnderRoot() {
        Path result = SafePathResolver.resolveUnder(tempDir, "wecom-smart-bot", "user-01");

        assertTrue(result.startsWith(tempDir.toAbsolutePath().normalize()));
        assertEquals("user-01", result.getFileName().toString());
    }

    @Test
    void rejectsTraversalAndWindowsSpecialCharacters() {
        assertThrows(IllegalArgumentException.class,
            () -> SafePathResolver.resolveUnder(tempDir, "..", "escape"));
        assertThrows(IllegalArgumentException.class,
            () -> SafePathResolver.resolveUnder(tempDir, "ok", "..\\escape"));
        assertThrows(IllegalArgumentException.class,
            () -> SafePathResolver.resolveUnder(tempDir, "ok", "C:evil"));
        assertThrows(IllegalArgumentException.class,
            () -> SafePathResolver.resolveUnder(tempDir, "ok", "bad/name"));
    }
}
