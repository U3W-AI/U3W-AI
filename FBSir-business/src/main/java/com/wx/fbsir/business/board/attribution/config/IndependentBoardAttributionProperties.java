package com.wx.fbsir.business.board.attribution.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** W4b.2d internal evidence writer settings. Public routes are intentionally absent. */
@Data
@Component
@ConfigurationProperties(prefix = "fbsir.independent-board.attribution")
public class IndependentBoardAttributionProperties {
    private boolean enabled = false;
    private boolean candidateEnabled = false;
    private boolean publicRouteEnabled = false;
    private boolean authoritativeCreditEnabled = false;
    /** Wave 1 append-only observation writer. */
    private boolean observationWriterEnabled = false;
    /** Finite server-side intent classifier. */
    private boolean intentClassifierEnabled = false;
    /** Bounded admin-only aggregate readback. */
    private boolean observationAdminReadEnabled = false;
    /** Signed, exact, internal event/receipt/digest readback. */
    private boolean authoritativeReadbackEnabled = false;
    /** Maximum signed readback request lifetime, hard-capped at 60 seconds. */
    private int authoritativeReadbackTtlSeconds = 60;
    /** Immutable release id returned by the readback response. */
    private String authoritativeReadbackReceiverReleaseId = "";
    /** Active receiver JAR SHA-256 bound by the independent authority receipt. */
    private String authoritativeReadbackReceiverJarSha256 = "";
    /** Physical JAR path whose bytes must match before the route can start. */
    private String authoritativeReadbackReceiverJarPath = "";
    /** Process-local concurrency fence; it does not persist nonce state. */
    private int authoritativeReadbackMaximumConcurrent = 4;
    /** Per verified signing key, process-local one-minute query budget. */
    private int authoritativeReadbackMaximumRequestsPerMinute = 120;
    /** Remains off until natural-traffic evidence is independently approved. */
    private boolean productCreditEnabled = false;
    /** API2 event signing keys by key id. Values use utf8:/hex:/base64:. */
    private Map<String, String> eventKeys = new LinkedHashMap<>();
    /** Explicit active key pair used by the production env-file contract. */
    private String activeEventKeyId = "";
    private String activeEventKey = "";
    /** Optional verify-only predecessor retained during bounded rotation. */
    private String previousEventKeyId = "";
    private String previousEventKey = "";
    /** Independent U3W-only HMAC secret for recomputing sameBindingKey. */
    private String sameBindingSecret = "";
    private String issuer = "api2.u3w.com";
    private String audience = "independent-board-attribution";
    private String keyRef = "fbs.w4b2d.api2.keyring";
    private int receiptTtlSeconds = 120;
    private int retentionHours = 26;
    /**
     * Default-off, time-bounded recovery gate for already durable historical
     * synthetic events. It never applies to natural, probe or diagnostic rows.
     */
    private boolean historicalSyntheticReplayEnabled = false;
    /** Absolute hard cap is enforced by the startup invariant and verifier. */
    private int historicalSyntheticReplayMaxAgeHours = 168;
    /** ISO-8601 instant; an empty value is invalid whenever the gate is on. */
    private String historicalSyntheticReplayNotAfter = "";
    /** Exact stable business digests authorized for the bounded recovery. */
    private Set<String> historicalSyntheticReplayEventDigests =
            new LinkedHashSet<>();

    /**
     * Resolves the map-bound compatibility surface plus the two explicit
     * production key slots. Duplicate ids fail closed instead of silently
     * overriding an existing key.
     */
    public Map<String, String> getResolvedEventKeys() {
        Map<String, String> resolved = new LinkedHashMap<>(eventKeys);
        addExplicitKey(
                resolved, activeEventKeyId, activeEventKey, "active");
        addExplicitKey(
                resolved, previousEventKeyId, previousEventKey, "previous");
        return Map.copyOf(resolved);
    }

    private static void addExplicitKey(
            Map<String, String> target,
            String rawKeyId,
            String rawSecret,
            String slot) {
        String keyId = rawKeyId == null ? "" : rawKeyId.trim();
        String secret = rawSecret == null ? "" : rawSecret.trim();
        if (keyId.isEmpty() && secret.isEmpty()) {
            return;
        }
        if (keyId.isEmpty() || secret.isEmpty()) {
            throw new IllegalStateException(
                    "attribution_" + slot + "_event_key_pair_incomplete");
        }
        String previous = target.putIfAbsent(keyId, secret);
        if (previous != null && !previous.equals(secret)) {
            throw new IllegalStateException(
                    "attribution_event_key_id_conflict");
        }
    }
}
