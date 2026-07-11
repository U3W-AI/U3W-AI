package com.wx.fbsir.web.core.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.EnvironmentPostProcessorApplicationListener;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies startup configuration validation without opening external services.
 */
class EnvironmentConfigValidatorTest {

    private final EnvironmentConfigValidator validator = new EnvironmentConfigValidator();

    @Test
    void runsAfterApplicationConfigurationIsLoaded() {
        assertTrue(validator.getOrder() > EnvironmentPostProcessorApplicationListener.DEFAULT_ORDER);
    }

    @Test
    void acceptsCompletePrimaryEnvironmentWithoutDruidConsoleCredentials() {
        Map<String, String> environment = completeFamily("FBSIR_");

        assertDoesNotThrow(() -> validator.validateEnvironmentConfig(environment));
    }

    @Test
    void acceptsCompleteLegacyEnvironment() {
        Map<String, String> environment = completeFamily("WXFBSIR_");

        assertDoesNotThrow(() -> validator.validateEnvironmentConfig(environment));
    }

    @Test
    void rejectsPartialPrimaryEnvironmentEvenWhenLegacyIsComplete() {
        Map<String, String> environment = completeFamily("WXFBSIR_");
        environment.put("FBSIR_MYSQL_URL", "jdbc:mysql://primary/fbsir");

        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> validator.validateEnvironmentConfig(environment));

        assertTrue(error.getMessage().contains("FBSIR_MYSQL_PASSWORD"));
    }

    @Test
    void startupRejectsPasswordOnlyPrimaryEnvironmentEvenWithValidEffectiveConfiguration() {
        Map<String, String> systemEnvironment = Map.of(
            "FBSIR_MYSQL_PASSWORD", "configured-secret"
        );
        MockEnvironment effectiveConfiguration = new MockEnvironment()
            .withProperty("spring.datasource.druid.master.password", "configured-secret")
            .withProperty("spring.datasource.druid.statViewServlet.enabled", "false");

        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> validator.validateStartupConfig(systemEnvironment, effectiveConfiguration));

        assertTrue(error.getMessage().contains("FBSIR_MYSQL_URL"));
    }

    @Test
    void rejectsEmptyEffectiveDatabasePasswordBeforeConnectionsAreOpened() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("spring.datasource.druid.master.password", "")
            .withProperty("spring.datasource.druid.statViewServlet.enabled", "false");

        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> validator.validateEffectiveConfig(environment));

        assertTrue(error.getMessage().contains("complete FBSIR_* family"));
    }

    @Test
    void packagedDruidDefaultsContainNoPasswordAndFailPreflight() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        for (PropertySource<?> source : new YamlPropertySourceLoader().load(
            "application-druid", new ClassPathResource("application-druid.yml"))) {
            environment.getPropertySources().addLast(source);
        }

        assertEquals("", environment.getProperty("spring.datasource.druid.master.password"));
        assertEquals("false", environment.getProperty(
            "spring.datasource.druid.statViewServlet.enabled"));
        assertThrows(IllegalStateException.class, () -> validator.validateEffectiveConfig(environment));
    }

    @Test
    void packagedApplicationDefaultsPreserveHistoricalUploadPath() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        for (PropertySource<?> source : new YamlPropertySourceLoader().load(
            "application", new ClassPathResource("application.yml"))) {
            environment.getPropertySources().addLast(source);
        }

        assertEquals("D:/WxFbsir/uploadPath", environment.getProperty("fbsir.profile"));
    }

    @Test
    void acceptsExplicitDatabasePasswordWhenDruidConsoleIsDisabled() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("spring.datasource.druid.master.password", "configured-secret")
            .withProperty("spring.datasource.druid.statViewServlet.enabled", "false");

        assertDoesNotThrow(() -> validator.validateEffectiveConfig(environment));
    }

    @Test
    void rejectsEnabledDruidConsoleWithoutCredentials() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("spring.datasource.druid.master.password", "configured-secret")
            .withProperty("spring.datasource.druid.statViewServlet.enabled", "true");

        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> validator.validateEffectiveConfig(environment));

        assertTrue(error.getMessage().contains("Druid console"));
    }

    @Test
    void acceptsEnabledDruidConsoleWithExplicitCredentials() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("spring.datasource.druid.master.password", "configured-secret")
            .withProperty("spring.datasource.druid.statViewServlet.enabled", "true")
            .withProperty("spring.datasource.druid.statViewServlet.login-username", "operator")
            .withProperty("spring.datasource.druid.statViewServlet.login-password", "console-secret");

        assertDoesNotThrow(() -> validator.validateEffectiveConfig(environment));
    }

    private static Map<String, String> completeFamily(String prefix) {
        Map<String, String> environment = new HashMap<>();
        for (String suffix : EnvironmentConfigValidator.REQUIRED_ENV_SUFFIXES) {
            environment.put(prefix + suffix, "configured-" + suffix.toLowerCase());
        }
        return environment;
    }
}
