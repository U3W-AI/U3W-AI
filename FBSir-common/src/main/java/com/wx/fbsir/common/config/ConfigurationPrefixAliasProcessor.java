package com.wx.fbsir.common.config;

import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Installs an atomic configuration-prefix alias.
 *
 * <p>The primary family is never partially completed with values from the
 * legacy family. A legacy-only configuration is exposed under the primary
 * prefix. If both families are visible, the primary family must contain every
 * legacy key and wins without installing aliases.</p>
 */
public final class ConfigurationPrefixAliasProcessor {

    private ConfigurationPrefixAliasProcessor() {
    }

    public static void apply(ConfigurableEnvironment environment,
                             String primaryPrefix,
                             String legacyPrefix,
                             String propertySourceName) {
        Map<String, Object> legacy = collect(environment, legacyPrefix);
        if (legacy.isEmpty()) {
            return;
        }

        Map<String, Object> primary = collect(environment, primaryPrefix);
        if (!primary.isEmpty()) {
            Set<String> missing = new LinkedHashSet<>(legacy.keySet());
            missing.removeAll(primary.keySet());
            if (!missing.isEmpty()) {
                throw new IllegalStateException("Incomplete primary configuration prefix '"
                    + primaryPrefix + "': missing " + missing
                    + ". Refusing to mix values from legacy prefix '" + legacyPrefix + "'.");
            }
            return;
        }

        Map<String, Object> aliases = new LinkedHashMap<>();
        legacy.forEach((suffix, value) -> aliases.put(primaryPrefix + "." + suffix, value));
        environment.getPropertySources().addFirst(new MapPropertySource(propertySourceName, aliases));
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
}
