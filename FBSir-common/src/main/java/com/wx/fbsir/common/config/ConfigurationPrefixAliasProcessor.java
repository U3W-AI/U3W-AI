package com.wx.fbsir.common.config;

import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Installs an atomic configuration-prefix alias.
 *
 * <p>Configuration families are selected by property-source precedence. A
 * higher-priority legacy source can override lower-priority built-in primary
 * defaults. Primary configuration at the same or a higher priority wins, but
 * lower-priority primary defaults cannot complete an otherwise incomplete
 * explicit primary family.</p>
 */
public final class ConfigurationPrefixAliasProcessor {

    private ConfigurationPrefixAliasProcessor() {
    }

    public static void apply(ConfigurableEnvironment environment,
                             String primaryPrefix,
                             String legacyPrefix,
                             String propertySourceName) {
        List<Map<String, Object>> primaryBySource = collectBySource(environment, primaryPrefix);
        List<Map<String, Object>> legacyBySource = collectBySource(environment, legacyPrefix);
        int firstPrimarySource = firstNonEmpty(primaryBySource);
        int firstLegacySource = firstNonEmpty(legacyBySource);
        if (firstLegacySource < 0) {
            return;
        }

        if (firstPrimarySource >= 0 && firstPrimarySource <= firstLegacySource) {
            Map<String, Object> legacy = collectEffective(legacyBySource, legacyBySource.size() - 1);
            Map<String, Object> explicitPrimary = collectEffective(primaryBySource, firstLegacySource);
            Set<String> missing = new LinkedHashSet<>(legacy.keySet());
            missing.removeAll(explicitPrimary.keySet());
            if (!missing.isEmpty()) {
                throw new IllegalStateException("Incomplete primary configuration prefix '"
                    + primaryPrefix + "': missing " + missing
                    + ". Refusing to mix values from legacy prefix '" + legacyPrefix + "'.");
            }
            return;
        }

        int lastLegacySource = firstPrimarySource < 0
            ? legacyBySource.size() - 1
            : firstPrimarySource - 1;
        Map<String, Object> legacy = collectEffective(legacyBySource, lastLegacySource);
        Map<String, Object> aliases = new LinkedHashMap<>();
        legacy.forEach((suffix, value) -> aliases.put(primaryPrefix + "." + suffix, value));
        environment.getPropertySources().addFirst(new MapPropertySource(propertySourceName, aliases));
    }

    private static List<Map<String, Object>> collectBySource(ConfigurableEnvironment environment, String prefix) {
        String canonicalPrefix = canonical(prefix) + ".";
        List<Map<String, Object>> valuesBySource = new ArrayList<>();
        for (PropertySource<?> source : environment.getPropertySources()) {
            Map<String, Object> values = new LinkedHashMap<>();
            if (source instanceof EnumerablePropertySource<?> enumerable) {
                for (String propertyName : enumerable.getPropertyNames()) {
                    String canonicalName = canonical(propertyName);
                    if (!canonicalName.startsWith(canonicalPrefix)) {
                        continue;
                    }
                    String suffix = canonicalName.substring(canonicalPrefix.length());
                    values.putIfAbsent(suffix, source.getProperty(propertyName));
                }
            }
            valuesBySource.add(values);
        }
        return valuesBySource;
    }

    private static int firstNonEmpty(List<Map<String, Object>> valuesBySource) {
        for (int i = 0; i < valuesBySource.size(); i++) {
            if (!valuesBySource.get(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private static Map<String, Object> collectEffective(List<Map<String, Object>> valuesBySource,
                                                         int lastSourceIndex) {
        Map<String, Object> values = new LinkedHashMap<>();
        int end = Math.min(lastSourceIndex, valuesBySource.size() - 1);
        for (int i = 0; i <= end; i++) {
            for (Map.Entry<String, Object> entry : valuesBySource.get(i).entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                values.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        return values;
    }

    private static String canonical(String name) {
        return ConfigurationPropertyName.adapt(name, '.').toString();
    }
}
