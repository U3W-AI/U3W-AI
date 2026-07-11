package com.wx.fbsir.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationPrefixAliasProcessorTest {

    private static final String ALIAS_SOURCE = "testFbsirAliases";

    @Test
    void bindsLegacyOnlyConfigurationToThePrimaryPrefix() {
        StandardEnvironment environment = environment(Map.of(
            "wxfbsir.name", "legacy-name",
            "wxfbsir.version", "1.0.0"
        ));

        ConfigurationPrefixAliasProcessor.apply(environment, "fbsir", "wxfbsir", ALIAS_SOURCE);

        FBSirConfig bound = Binder.get(environment).bind("fbsir", FBSirConfig.class).get();
        assertEquals("legacy-name", bound.getName());
        assertEquals("1.0.0", bound.getVersion());
        assertTrue(environment.getPropertySources().contains(ALIAS_SOURCE));
    }

    @Test
    void leavesPrimaryOnlyConfigurationUntouched() {
        StandardEnvironment environment = environment(Map.of(
            "fbsir.name", "primary-name",
            "fbsir.version", "2.0.0"
        ));

        ConfigurationPrefixAliasProcessor.apply(environment, "fbsir", "wxfbsir", ALIAS_SOURCE);

        assertEquals("primary-name", environment.getProperty("fbsir.name"));
        assertFalse(environment.getPropertySources().contains(ALIAS_SOURCE));
    }

    @Test
    void completePrimaryConfigurationWinsWhenBothFamiliesExist() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("wxfbsir.name", "legacy-name");
        values.put("wxfbsir.version", "1.0.0");
        values.put("fbsir.name", "primary-name");
        values.put("fbsir.version", "2.0.0");
        StandardEnvironment environment = environment(values);

        ConfigurationPrefixAliasProcessor.apply(environment, "fbsir", "wxfbsir", ALIAS_SOURCE);

        assertEquals("primary-name", environment.getProperty("fbsir.name"));
        assertFalse(environment.getPropertySources().contains(ALIAS_SOURCE));
    }

    @Test
    void rejectsPartialPrimaryConfigurationInsteadOfMixingFamilies() {
        StandardEnvironment environment = environment(Map.of(
            "wxfbsir.name", "legacy-name",
            "wxfbsir.version", "1.0.0",
            "fbsir.name", "primary-name"
        ));

        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> ConfigurationPrefixAliasProcessor.apply(
                environment, "fbsir", "wxfbsir", ALIAS_SOURCE));

        assertTrue(error.getMessage().contains("version"));
    }

    @Test
    void higherPriorityLegacyOverridesLowerPriorityPrimaryDefaults() {
        StandardEnvironment environment = layeredEnvironment(
            Map.of(
                "fbsir.name", "built-in-name",
                "fbsir.version", "2.0.0"
            ),
            Map.of(
                "wxfbsir.name", "legacy-external-name",
                "wxfbsir.version", "1.0.0"
            )
        );

        ConfigurationPrefixAliasProcessor.apply(environment, "fbsir", "wxfbsir", ALIAS_SOURCE);

        assertEquals("legacy-external-name", environment.getProperty("fbsir.name"));
        assertEquals("1.0.0", environment.getProperty("fbsir.version"));
        assertTrue(environment.getPropertySources().contains(ALIAS_SOURCE));
    }

    @Test
    void lowerPriorityPrimaryDefaultsCannotCompleteExplicitPrimaryOverrides() {
        StandardEnvironment environment = layeredEnvironment(
            Map.of(
                "fbsir.name", "built-in-name",
                "fbsir.version", "2.0.0"
            ),
            Map.of(
                "wxfbsir.name", "legacy-external-name",
                "wxfbsir.version", "1.0.0"
            )
        );
        environment.getPropertySources().addFirst(new MapPropertySource(
            "primaryOverrides", Map.of("fbsir.name", "explicit-primary-name")));

        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> ConfigurationPrefixAliasProcessor.apply(
                environment, "fbsir", "wxfbsir", ALIAS_SOURCE));

        assertTrue(error.getMessage().contains("version"));
    }

    private static StandardEnvironment environment(Map<String, ?> values) {
        StandardEnvironment environment = new StandardEnvironment();
        Map<String, Object> sourceValues = new LinkedHashMap<>();
        values.forEach(sourceValues::put);
        environment.getPropertySources().addFirst(new MapPropertySource("test", sourceValues));
        return environment;
    }

    private static StandardEnvironment layeredEnvironment(Map<String, ?> primaryDefaults,
                                                           Map<String, ?> legacyExternal) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addLast(new MapPropertySource(
            "builtInPrimaryDefaults", new LinkedHashMap<>(primaryDefaults)));
        environment.getPropertySources().addFirst(new MapPropertySource(
            "legacyExternal", new LinkedHashMap<>(legacyExternal)));
        return environment;
    }
}
