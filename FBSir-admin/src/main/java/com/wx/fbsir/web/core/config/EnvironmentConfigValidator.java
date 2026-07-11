package com.wx.fbsir.web.core.config;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.env.EnvironmentPostProcessorApplicationListener;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Validates the environment-variable configuration before application startup.
 *
 * <p>{@code FBSIR_*} is the primary family. Once any primary variable
 * is present, every required primary variable must be present; legacy values
 * are not used to fill gaps. If no primary variable is present, a complete
 * {@code WXFBSIR_*} family remains supported.</p>
 */
public class EnvironmentConfigValidator
    implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

    static final String PRIMARY_ENV_PREFIX = "FBSIR_";
    static final String LEGACY_ENV_PREFIX = "WXFBSIR_";

    static final String[] REQUIRED_ENV_SUFFIXES = {
        "MYSQL_URL",
        "MYSQL_USERNAME",
        "MYSQL_PASSWORD",
        "REDIS_HOST",
        "REDIS_PORT",
        "REDIS_DATABASE",
        "AES_SECRET_KEY",
        "TOKEN_SECRET",
        "ENGINE_TOKEN",
        "DOMAIN",
        "FILE_PATH"
    };

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        try {
            validateStartupConfig(System.getenv(), event.getEnvironment());
        } catch (IllegalStateException e) {
            System.err.println("\n"
                + "=======================================================\n"
                + "  Environment configuration validation failed\n"
                + "=======================================================\n"
                + e.getMessage() + "\n"
                + "=======================================================");
            throw e;
        }
    }

    void validateEnvironmentConfig(Map<String, String> environment) {
        if (containsAny(environment, PRIMARY_ENV_PREFIX)) {
            validateSelectedFamily(environment, PRIMARY_ENV_PREFIX);
        } else if (containsAny(environment, LEGACY_ENV_PREFIX)) {
            validateSelectedFamily(environment, LEGACY_ENV_PREFIX);
        } else {
            logUsingDefaultConfig();
        }
    }

    void validateStartupConfig(Map<String, String> systemEnvironment, Environment environment) {
        validateEnvironmentConfig(systemEnvironment);
        validateEffectiveConfig(environment);
    }

    /**
     * Validates resolved configuration without opening a database or Redis
     * connection. A password must be supplied explicitly through an environment
     * variable or an external configuration file.
     */
    void validateEffectiveConfig(Environment environment) {
        String databasePassword = environment.getProperty("spring.datasource.druid.master.password");
        if (!hasText(databasePassword)) {
            throw new IllegalStateException(
                "Database password is empty. Environment-variable mode requires the complete "
                    + "FBSIR_* family (including FBSIR_MYSQL_PASSWORD), or the complete legacy "
                    + "WXFBSIR_* family. Alternatively, provide "
                    + "spring.datasource.druid.master.password in an external configuration file.");
        }

        String engineToken = environment.getProperty("fbsir.websocket.engine-token");
        if (!hasText(engineToken) || engineToken.length() < 32) {
            throw new IllegalStateException(
                "Engine credential is empty or too short. Configure FBSIR_ENGINE_TOKEN with at least "
                    + "32 characters (legacy WXFBSIR_ENGINE_TOKEN is supported), or provide "
                    + "fbsir.websocket.engine-token in an external configuration file.");
        }

        boolean consoleEnabled = Boolean.parseBoolean(environment.getProperty(
            "spring.datasource.druid.statViewServlet.enabled", "false"));
        if (!consoleEnabled) {
            return;
        }

        String consoleUsername = environment.getProperty(
            "spring.datasource.druid.statViewServlet.login-username");
        String consolePassword = environment.getProperty(
            "spring.datasource.druid.statViewServlet.login-password");
        if (!hasText(consoleUsername) || !hasText(consolePassword)) {
            throw new IllegalStateException(
                "Druid console is enabled without credentials. With a complete FBSIR_* "
                    + "environment family, also set FBSIR_DRUID_USERNAME and FBSIR_DRUID_PASSWORD "
                    + "(legacy WXFBSIR_* is supported as a complete family), or disable "
                    + "spring.datasource.druid.statViewServlet.enabled.");
        }
    }

    private boolean containsAny(Map<String, String> environment, String prefix) {
        return environment.entrySet().stream()
            .anyMatch(entry -> entry.getKey().startsWith(prefix) && hasText(entry.getValue()));
    }

    private void validateSelectedFamily(Map<String, String> environment, String prefix) {
        List<String> existingVars = new ArrayList<>();
        List<String> missingVars = new ArrayList<>();
        for (String suffix : REQUIRED_ENV_SUFFIXES) {
            String name = prefix + suffix;
            if (hasText(environment.get(name))) {
                existingVars.add(name);
            } else {
                missingVars.add(name);
            }
        }

        if (!missingVars.isEmpty()) {
            throwIncompleteConfigError(existingVars, missingVars);
        }
        logUsingEnvironmentConfig(prefix);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private void logUsingDefaultConfig() {
        System.out.println("\n"
            + "=======================================================\n"
            + "  Configuration mode: application configuration files\n"
            + "  No FBSir environment-variable family was selected.\n"
            + "=======================================================");
    }

    private void logUsingEnvironmentConfig(String prefix) {
        System.out.println("\n"
            + "=======================================================\n"
            + "  Configuration mode: environment variables\n"
            + "  Active family: " + prefix + "*\n"
            + "=======================================================");
    }

    private void throwIncompleteConfigError(List<String> existingVars, List<String> missingVars) {
        StringBuilder error = new StringBuilder();
        error.append("Selected environment-variable family is incomplete.\n\n");
        error.append("Configured variables:\n");
        existingVars.forEach(name -> error.append("  + ").append(name).append('\n'));
        error.append("\nMissing variables:\n");
        missingVars.forEach(name -> error.append("  - ").append(name).append('\n'));
        error.append("\nProvide the complete selected family or remove it and use configuration files.");
        throw new IllegalStateException(error.toString());
    }

    @Override
    public int getOrder() {
        // application.yml must be loaded and placeholders resolved before the
        // effective database/Druid checks run.
        return EnvironmentPostProcessorApplicationListener.DEFAULT_ORDER + 10;
    }
}
