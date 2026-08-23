package com.wx.fbsir.business.board.attribution.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IndependentBoardAttributionReadbackRuntimeInvariantTest {
    @TempDir
    Path directory;

    @Test
    void requiresConfiguredPathToBeTheActualRuntimeJarWithMatchingBytes()
            throws Exception {
        Path jar = directory.resolve("receiver.jar").toAbsolutePath();
        Files.writeString(jar, "packaged-receiver-bytes");
        IndependentBoardAttributionProperties properties = properties(jar);

        assertDoesNotThrow(() -> newInvariant(
                properties, jar).afterPropertiesSet());

        Path other = directory.resolve("other.jar").toAbsolutePath();
        Files.copy(jar, other);
        IllegalStateException pathError = assertThrows(
                IllegalStateException.class,
                () -> newInvariant(properties, other).afterPropertiesSet());
        assertEquals(
                "attribution_readback_physical_jar_invalid",
                pathError.getMessage());

        Files.writeString(jar, "tampered");
        IllegalStateException hashError = assertThrows(
                IllegalStateException.class,
                () -> newInvariant(properties, jar).afterPropertiesSet());
        assertEquals(
                "attribution_readback_physical_jar_invalid",
                hashError.getMessage());
    }

    @Test
    void rejectsExplodedClassesDirectory() throws Exception {
        Path jar = directory.resolve("receiver.jar").toAbsolutePath();
        Files.writeString(jar, "packaged-receiver-bytes");
        IndependentBoardAttributionProperties properties = properties(jar);

        assertThrows(
                IllegalStateException.class,
                () -> newInvariant(
                        properties, directory.toAbsolutePath())
                        .afterPropertiesSet());
    }

    private IndependentBoardAttributionReadbackRuntimeInvariant newInvariant(
            IndependentBoardAttributionProperties properties,
            Path actual) {
        return new IndependentBoardAttributionReadbackRuntimeInvariant(
                properties, () -> actual);
    }

    private IndependentBoardAttributionProperties properties(Path jar)
            throws Exception {
        IndependentBoardAttributionProperties properties =
                new IndependentBoardAttributionProperties();
        properties.setAuthoritativeReadbackReceiverJarPath(
                jar.toString());
        properties.setAuthoritativeReadbackReceiverJarSha256(
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(Files.readAllBytes(jar))));
        return properties;
    }
}
