package com.wx.fbsir.business.board.portal;

import com.wx.fbsir.business.board.mapper.IndependentBoardMapper;
import com.wx.fbsir.business.board.portal.config.BoardPortalReadConfiguration;
import com.wx.fbsir.business.board.portal.mapper.IndependentBoardPortalReadMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class BoardPortalReadConfigurationTest {

    private static final String CURSOR_KEY = key("cursor-key-material-32-bytes-001");
    private static final String REFERENCE_KEY = key("reference-key-material-32-byte02");

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(BoundaryConfiguration.class, BoardPortalReadConfiguration.class);

    @Test
    void candidateIsOffWhenThePropertyIsMissing() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(IndependentBoardPortalReadService.class);
        });
    }

    @Test
    void enabledCandidateRequiresTwoIndependentCanonicalKeys() {
        contextRunner
                .withPropertyValues(
                        "fbsir.independent-board.portal-candidate.enabled=true",
                        "fbsir.independent-board.portal-candidate.cursor-key-base64=" + CURSOR_KEY,
                        "fbsir.independent-board.portal-candidate.reference-key-base64=" + CURSOR_KEY)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage(
                                    "portal cursor and reference keys must be independent");
                });
    }

    @Test
    void enabledCandidateFailsClosedWhenAKeyIsMissing() {
        contextRunner
                .withPropertyValues(
                        "fbsir.independent-board.portal-candidate.enabled=true",
                        "fbsir.independent-board.portal-candidate.cursor-key-base64=" + CURSOR_KEY)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage(
                                    "portal reference key must be an unpadded base64url AES-256 key");
                });
    }

    @Test
    void enabledCandidateBuildsOnlyWithBoundedCursorTtl() {
        contextRunner
                .withPropertyValues(
                        "fbsir.independent-board.portal-candidate.enabled=true",
                        "fbsir.independent-board.portal-candidate.cursor-key-base64=" + CURSOR_KEY,
                        "fbsir.independent-board.portal-candidate.reference-key-base64=" + REFERENCE_KEY,
                        "fbsir.independent-board.portal-candidate.cursor-ttl=PT15M")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(IndependentBoardPortalReadService.class);
                });

        contextRunner
                .withPropertyValues(
                        "fbsir.independent-board.portal-candidate.enabled=true",
                        "fbsir.independent-board.portal-candidate.cursor-key-base64=" + CURSOR_KEY,
                        "fbsir.independent-board.portal-candidate.reference-key-base64=" + REFERENCE_KEY,
                        "fbsir.independent-board.portal-candidate.cursor-ttl=PT1H1S")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage(
                                    "cursor ttl must be between 1 second and 1 hour");
                });
    }

    private static String key(String material) {
        byte[] bytes = material.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length != 32) {
            throw new IllegalArgumentException("test key material must contain 32 bytes");
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Configuration(proxyBeanMethods = false)
    static class BoundaryConfiguration {

        @Bean
        IndependentBoardPortalReadMapper portalMapper() {
            return Mockito.mock(IndependentBoardPortalReadMapper.class);
        }

        @Bean
        IndependentBoardMapper boardMapper() {
            return Mockito.mock(IndependentBoardMapper.class);
        }
    }
}
