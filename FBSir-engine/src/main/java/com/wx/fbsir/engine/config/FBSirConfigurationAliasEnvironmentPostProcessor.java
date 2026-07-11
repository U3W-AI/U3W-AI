package com.wx.fbsir.engine.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Exposes a complete {@code fbsir.*} Engine configuration through the legacy
 * prefix still used by the current configuration-property binders.
 */
public class FBSirConfigurationAliasEnvironmentPostProcessor
    implements EnvironmentPostProcessor, Ordered {

    static final String PRIMARY_PREFIX = "fbsir";
    static final String LEGACY_PREFIX = "wxfbsir";
    static final String PROPERTY_SOURCE_NAME = "fbsirEngineConfigurationAliases";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> primary = collect(environment, PRIMARY_PREFIX);
        if (primary.isEmpty()) {
            return;
        }

        Map<String, Object> legacy = collect(environment, LEGACY_PREFIX);
        if (!legacy.isEmpty()) {
            Set<String> missing = new LinkedHashSet<>(legacy.keySet());
            missing.removeAll(primary.keySet());
            if (!missing.isEmpty()) {
                throw new IllegalStateException("Incomplete primary configuration prefix 'fbsir': missing "
                    + missing + ". Refusing to mix values from legacy prefix 'wxfbsir'.");
            }
        }

        Map<String, Object> aliases = new LinkedHashMap<>();
        primary.forEach((suffix, value) -> aliases.put(LEGACY_PREFIX + "." + suffix, value));
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, aliases));
    }

    private static Map<String, Object> collect(ConfigurableEnvironment environment, String prefix) {
        String canonicalPrefix = canonical(prefix) + ".";
        Map<String, Object> values = new LinkedHashMap<>();
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (!(source instanceof EnumerablePropertySource<?> enumerable)) {
                continue;
            }
            for (String propertyName : enumerable.getPropertyNames()) {
                String canonicalName = canonical(propertyName);
                if (!canonicalName.startsWith(canonicalPrefix)) {
                    continue;
                }
                String suffix = canonicalName.substring(canonicalPrefix.length());
                values.putIfAbsent(suffix, environment.getProperty(propertyName));
            }
        }
        return values;
    }

    private static String canonical(String name) {
        return ConfigurationPropertyName.adapt(name, '.').toString();
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
