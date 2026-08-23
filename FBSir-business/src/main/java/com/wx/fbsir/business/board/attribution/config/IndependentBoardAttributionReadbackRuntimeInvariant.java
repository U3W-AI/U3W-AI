package com.wx.fbsir.business.board.attribution.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.function.Supplier;

/** Binds the configured response identity to the physical executable JAR. */
@Component
@ConditionalOnProperty(
        prefix = "fbsir.independent-board.attribution",
        name = "authoritative-readback-enabled",
        havingValue = "true",
        matchIfMissing = false)
public class IndependentBoardAttributionReadbackRuntimeInvariant
        implements InitializingBean {
    private final IndependentBoardAttributionProperties properties;
    private final Supplier<Path> runtimeSource;

    public IndependentBoardAttributionReadbackRuntimeInvariant(
            IndependentBoardAttributionProperties properties) {
        this(properties, () -> {
            var source = new ApplicationHome(
                    IndependentBoardAttributionReadbackRuntimeInvariant.class)
                    .getSource();
            return source == null ? null : source.toPath();
        });
    }

    IndependentBoardAttributionReadbackRuntimeInvariant(
            IndependentBoardAttributionProperties properties,
            Supplier<Path> runtimeSource) {
        this.properties = properties;
        this.runtimeSource = runtimeSource;
    }

    @Override
    public void afterPropertiesSet() {
        String configured = normalized(
                properties.getAuthoritativeReadbackReceiverJarPath());
        String expected = normalized(
                properties.getAuthoritativeReadbackReceiverJarSha256());
        try {
            Path supplied = Path.of(configured);
            Path actual = runtimeSource.get();
            if (!supplied.isAbsolute() || actual == null
                    || !actual.isAbsolute()) {
                fail();
            }
            Path resolved = supplied.toRealPath();
            Path running = actual.toRealPath();
            if (!Files.isRegularFile(resolved, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isRegularFile(running, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(resolved)
                    || Files.isSymbolicLink(running)
                    || !resolved.equals(running)
                    || Files.size(resolved) <= 0
                    || !expected.equals(sha256(resolved))) {
                fail();
            }
        } catch (IOException | IllegalArgumentException error) {
            throw new IllegalStateException(
                    "attribution_readback_physical_jar_invalid", error);
        }
    }

    private String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[1024 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, count);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    private void fail() {
        throw new IllegalStateException(
                "attribution_readback_physical_jar_invalid");
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim();
    }
}
