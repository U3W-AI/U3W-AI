package com.wx.fbsir.business.board.attribution.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

/**
 * Fails startup for configuration combinations that could make the production
 * env-file attestation disagree with the actual W1A runtime behavior.
 */
@Component
public class IndependentBoardAttributionStartupInvariant
        implements InitializingBean {
    private static final int MIN_SECRET_BYTES = 32;
    private static final int MAX_HISTORICAL_SYNTHETIC_REPLAY_HOURS = 168;
    private static final int MAX_HISTORICAL_SYNTHETIC_REPLAY_EVENTS = 16;
    private final IndependentBoardAttributionProperties properties;

    public IndependentBoardAttributionStartupInvariant(
            IndependentBoardAttributionProperties properties) {
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        boolean writer = properties.isObservationWriterEnabled();
        boolean intent = properties.isIntentClassifierEnabled();
        boolean adminRead = properties.isObservationAdminReadEnabled();
        boolean productCredit = properties.isProductCreditEnabled();
        boolean historicalSyntheticReplay =
                properties.isHistoricalSyntheticReplayEnabled();
        boolean anyW1aSurface = writer || intent || adminRead || productCredit
                || historicalSyntheticReplay;

        if (anyW1aSurface && !properties.isEnabled()) {
            throw new IllegalStateException(
                    "attribution_master_gate_required");
        }
        if (intent && !writer) {
            throw new IllegalStateException(
                    "attribution_intent_requires_writer");
        }
        if (productCredit && (!writer || !intent
                || !properties.isAuthoritativeCreditEnabled())) {
            throw new IllegalStateException(
                    "attribution_product_credit_gates_incomplete");
        }
        if (writer && (properties.isCandidateEnabled()
                || properties.isPublicRouteEnabled())) {
            throw new IllegalStateException(
                    "attribution_w1a_writer_requires_legacy_routes_off");
        }
        if (historicalSyntheticReplay) {
            if (!writer || productCredit
                    || properties.isAuthoritativeCreditEnabled()) {
                throw new IllegalStateException(
                        "historical_synthetic_replay_requires_report_only_writer");
            }
            int maxAgeHours =
                    properties.getHistoricalSyntheticReplayMaxAgeHours();
            if (maxAgeHours < properties.getRetentionHours()
                    || maxAgeHours > MAX_HISTORICAL_SYNTHETIC_REPLAY_HOURS) {
                throw new IllegalStateException(
                        "historical_synthetic_replay_max_age_invalid");
            }
            try {
                Instant.parse(normalized(
                        properties.getHistoricalSyntheticReplayNotAfter()));
            } catch (DateTimeException error) {
                throw new IllegalStateException(
                        "historical_synthetic_replay_not_after_invalid", error);
            }
            var replayDigests =
                    properties.getHistoricalSyntheticReplayEventDigests();
            if (replayDigests == null || replayDigests.isEmpty()
                    || replayDigests.size()
                    > MAX_HISTORICAL_SYNTHETIC_REPLAY_EVENTS
                    || replayDigests.stream().anyMatch(value ->
                        value == null
                            || !value.matches("[0-9a-f]{64}"))) {
                throw new IllegalStateException(
                        "historical_synthetic_replay_digest_allowlist_invalid");
            }
        }

        Map<String, String> eventKeys = properties.getResolvedEventKeys();
        String bindingValue = normalized(properties.getSameBindingSecret());
        if (eventKeys.isEmpty() && bindingValue.isEmpty()) {
            return;
        }
        if (eventKeys.isEmpty() || bindingValue.isEmpty()) {
            throw new IllegalStateException(
                    "attribution_cryptographic_custody_incomplete");
        }
        byte[] bindingSecret = decode(bindingValue);
        if (bindingSecret == null
                || bindingSecret.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "same_binding_secret_must_be_at_least_32_bytes");
        }
        for (Map.Entry<String, String> entry : eventKeys.entrySet()) {
            String keyId = normalized(entry.getKey());
            byte[] eventSecret = decode(entry.getValue());
            if (!keyId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,95}")
                    || eventSecret == null
                    || eventSecret.length < MIN_SECRET_BYTES) {
                throw new IllegalStateException(
                        "attribution_event_keyring_invalid");
            }
            if (MessageDigest.isEqual(eventSecret, bindingSecret)) {
                throw new IllegalStateException(
                        "attribution_event_and_binding_keys_must_differ");
            }
        }
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    private static byte[] decode(String encoded) {
        String value = normalized(encoded);
        if (value.isEmpty()) {
            return null;
        }
        try {
            if (value.startsWith("base64:")) {
                return Base64.getDecoder().decode(value.substring(7));
            }
            if (value.startsWith("hex:")) {
                String hex = value.substring(4);
                if (hex.length() % 2 != 0
                        || !hex.matches("[0-9a-fA-F]+")) {
                    return null;
                }
                return HexFormat.of().parseHex(hex);
            }
            String raw = value.startsWith("utf8:")
                    ? value.substring(5) : value;
            return raw.getBytes(StandardCharsets.UTF_8);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }
}
