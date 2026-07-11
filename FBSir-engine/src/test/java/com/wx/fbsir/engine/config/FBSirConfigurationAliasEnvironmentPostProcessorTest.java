package com.wx.fbsir.engine.config;

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

class FBSirConfigurationAliasEnvironmentPostProcessorTest {

    private final FBSirConfigurationAliasEnvironmentPostProcessor processor =
        new FBSirConfigurationAliasEnvironmentPostProcessor();

    @Test
    void keepsCompleteLegacyConfigurationWorking() {
        StandardEnvironment environment = environment(Map.of(
            "wxfbsir.engine.host-id", "legacy-host",
            "wxfbsir.engine.ws-url", "ws://legacy"
        ));

        processor.postProcessEnvironment(environment, null);

        assertEquals("legacy-host", environment.getProperty("fbsir.engine.host-id"));
        assertTrue(environment.getPropertySources().contains(
            FBSirConfigurationAliasEnvironmentPostProcessor.PROPERTY_SOURCE_NAME));
        EngineProperties bound = Binder.get(environment).bind("fbsir.engine", EngineProperties.class).get();
        assertEquals("legacy-host", bound.getHostId());
        assertEquals("ws://legacy", bound.getWsUrl());
    }

    @Test
    void keepsCompletePrimaryConfigurationOnPrimaryBinderPrefix() {
        StandardEnvironment environment = environment(Map.of(
            "fbsir.engine.host-id", "primary-host",
            "fbsir.engine.ws-url", "ws://primary"
        ));

        processor.postProcessEnvironment(environment, null);

        assertEquals("primary-host", environment.getProperty("fbsir.engine.host-id"));
        assertEquals("ws://primary", environment.getProperty("fbsir.engine.ws-url"));
        assertFalse(environment.getPropertySources().contains(
            FBSirConfigurationAliasEnvironmentPostProcessor.PROPERTY_SOURCE_NAME));
    }

    @Test
    void completePrimaryConfigurationWinsWhenBothFamiliesExist() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("wxfbsir.engine.host-id", "legacy-host");
        values.put("wxfbsir.engine.ws-url", "ws://legacy");
        values.put("fbsir.engine.host-id", "primary-host");
        values.put("fbsir.engine.ws-url", "ws://primary");
        StandardEnvironment environment = environment(values);

        processor.postProcessEnvironment(environment, null);

        assertEquals("primary-host", environment.getProperty("fbsir.engine.host-id"));
        assertEquals("ws://primary", environment.getProperty("fbsir.engine.ws-url"));
        assertFalse(environment.getPropertySources().contains(
            FBSirConfigurationAliasEnvironmentPostProcessor.PROPERTY_SOURCE_NAME));
    }

    @Test
    void rejectsPartialPrimaryConfigurationInsteadOfMixingFamilies() {
        StandardEnvironment environment = environment(Map.of(
            "wxfbsir.engine.host-id", "legacy-host",
            "wxfbsir.engine.ws-url", "ws://legacy",
            "fbsir.engine.host-id", "primary-host"
        ));

        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> processor.postProcessEnvironment(environment, null));

        assertTrue(error.getMessage().contains("engine.ws-url"));
    }

    @Test
    void higherPriorityLegacyOverridesLowerPriorityPrimaryDefaults() {
        StandardEnvironment environment = layeredEnvironment(
            Map.of(
                "fbsir.engine.host-id", "built-in-host",
                "fbsir.engine.ws-url", "ws://built-in"
            ),
            Map.of(
                "wxfbsir.engine.host-id", "legacy-external-host",
                "wxfbsir.engine.ws-url", "ws://legacy-external"
            )
        );

        processor.postProcessEnvironment(environment, null);

        assertEquals("legacy-external-host", environment.getProperty("fbsir.engine.host-id"));
        assertEquals("ws://legacy-external", environment.getProperty("fbsir.engine.ws-url"));
        assertTrue(environment.getPropertySources().contains(
            FBSirConfigurationAliasEnvironmentPostProcessor.PROPERTY_SOURCE_NAME));
    }

    @Test
    void lowerPriorityPrimaryDefaultsCannotCompleteExplicitPrimaryOverrides() {
        StandardEnvironment environment = layeredEnvironment(
            Map.of(
                "fbsir.engine.host-id", "built-in-host",
                "fbsir.engine.ws-url", "ws://built-in"
            ),
            Map.of(
                "wxfbsir.engine.host-id", "legacy-external-host",
                "wxfbsir.engine.ws-url", "ws://legacy-external"
            )
        );
        environment.getPropertySources().addFirst(new MapPropertySource(
            "primaryOverrides", Map.of("fbsir.engine.host-id", "explicit-primary-host")));

        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> processor.postProcessEnvironment(environment, null));

        assertTrue(error.getMessage().contains("engine.ws-url"));
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
