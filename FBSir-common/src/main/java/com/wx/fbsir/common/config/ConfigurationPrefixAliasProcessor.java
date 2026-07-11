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
 * <p>The primary family is never completed with values from the legacy family.
 * If both families are visible, the primary family must contain every legacy
 * key before it is exposed under the legacy prefix used by existing binders.</p>
 */
public final class ConfigurationPrefixAliasProcessor {

    private ConfigurationPrefixAliasProcessor() {
    }

    public static void apply(ConfigurableEnvironment environment,
                             String primaryPrefix,
                             String legacyPrefix,
                             String propertySourceName) {
        Map<String, Object> primary = collect(environment, primaryPrefix);
        if (primary.isEmpty()) {
            return;
        }

        Map<String, Object> legacy = collect(environment, legacyPrefix);
        if (!legacy.isEmpty()) {
            Set<String> missing = new LinkedHashSet<>(legacy.keySet());
            missing.removeAll(primary.keySet());
            if (!missing.isEmpty()) {
                throw new IllegalStateException("Incomplete primary configuration prefix '"
                    + primaryPrefix + "': missing " + missing
                    + ". Refusing to mix values from legacy prefix '" + legacyPrefix + "'.");
            }
        }

        Map<String, Object> aliases = new LinkedHashMap<>();
        primary.forEach((suffix, value) -> aliases.put(legacyPrefix + "." + suffix, value));
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
