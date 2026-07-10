package com.wx.fbsir.engine.playwright.login.persistence;

import com.alibaba.fastjson2.JSONArray;
import com.wx.fbsir.engine.playwright.config.PlaywrightProperties;
import com.wx.fbsir.engine.playwright.login.model.LoginState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginStatePersistenceTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void configurePersistenceRoot() {
        PlaywrightProperties properties = new PlaywrightProperties();
        properties.setDataDir(tempDir.toString());
        new LoginStatePersistence(properties);
    }

    @Test
    void savesAndLoadsAtomicallyInsideConfiguredRoot() throws Exception {
        LoginState state = new LoginState();
        state.setPlatform("wecom");
        state.setUserId("admin-01");
        state.setTimestamp(System.currentTimeMillis());
        state.setCookies(new JSONArray());
        state.setOrigins(new JSONArray());

        assertTrue(LoginStatePersistence.save(state));
        Path directory = LoginStatePersistence.getLoginStateDirectory("wecom", "admin-01");
        assertTrue(directory.startsWith(tempDir.toAbsolutePath().normalize()));
        assertTrue(Files.exists(directory.resolve("login-state.json")));
        try (var files = Files.list(directory)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }

        LoginState loaded = LoginStatePersistence.load("wecom", "admin-01");
        assertNotNull(loaded);
        assertEquals("wecom", loaded.getPlatform());
        assertEquals("admin-01", loaded.getUserId());
    }

    @Test
    void rejectsPathTraversalInPlatformAndUserId() {
        LoginState state = new LoginState();
        state.setPlatform("../outside");
        state.setUserId("admin");

        assertFalse(LoginStatePersistence.save(state));
        assertFalse(LoginStatePersistence.exists("wecom", "..\\outside"));
    }
}
