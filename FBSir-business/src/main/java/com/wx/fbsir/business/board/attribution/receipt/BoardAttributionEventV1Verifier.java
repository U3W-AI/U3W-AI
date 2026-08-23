package com.wx.fbsir.business.board.attribution.receipt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.fbsir.business.board.attribution.config.IndependentBoardAttributionProperties;
import com.wx.fbsir.business.board.attribution.intent.BoardIntentClassifier;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Side-effect-free verifier for the exact registered listed-package identities.
 *
 * <p>The registry deliberately contains full host/version tuples instead of
 * semver ranges.  This keeps historical durable-outbox replay compatible while
 * preventing an unreviewed host or package version from becoming authoritative
 * by accident.</p>
 */
public final class BoardAttributionEventV1Verifier
        implements BoardAttributionEventVerifier {
    public static final String SCHEMA_VERSION =
            "fbsir.independentBoardAttributionEvent.v1";
    public static final String CONTRACT_ID =
            "FBSIR_INDEPENDENT_BOARD_W1A_V1";
    public static final String SIGNATURE_ALGORITHM = "hmac-sha256-v1";
    private static final int MIN_SECRET_BYTES = 32;
    private static final int MAX_TTL_SECONDS = 120;
    private static final int MAX_FIELD_CHARS = 256;
    /**
     * These two signed dimensions are persisted in VARCHAR(64) columns by
     * the W1A migration.  Rejecting longer values at the receipt boundary
     * keeps a valid HMAC from reaching a deterministic database truncation
     * failure (and an avoidable 500/retry loop).
     */
    private static final int MAX_PERSISTED_TOKEN_CHARS = 64;
    private static final int MAX_HOST_VERSION_CHARS = 32;
    private static final int MAX_SIGNER_KEY_ID_CHARS = 96;
    private static final int MAX_CANONICAL_CHARS = 32_768;
    private static final long CLOCK_SKEW_SECONDS = 30;
    private static final int MAX_HISTORICAL_SYNTHETIC_REPLAY_HOURS = 168;
    private static final Pattern HEX_64 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern BINDING = Pattern.compile(
            "(?:[0-9a-f]{64}|srv_[A-Za-z0-9_-]{12,80})");
    private static final Pattern HOST_VERSION = Pattern.compile(
            "(?:[0-9]+(?:\\.[0-9]+){1,3}|UNKNOWN)");
    private static final Pattern SAFE_TOKEN = Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9_.:-]{0,255}");
    private static final Pattern TRACEPARENT = Pattern.compile(
            "00-(?!0{32})[0-9a-f]{32}-(?!0{16})[0-9a-f]{16}-[0-9a-f]{2}");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final IdentityRegistry IDENTITY_REGISTRY =
            loadIdentityRegistry();
    private static final Set<String> TERMINALS = Set.of(
            "WORKBUDDY_WINDOWS", "WORKBUDDY_MACOS", "WORKBUDDYAI", "UNKNOWN");
    private static final Set<String> CHANNELS =
            Set.of("OFFICIAL_EXPERTS", "UNKNOWN");
    private static final Set<String> REQUEST_SOURCES =
            IDENTITY_REGISTRY.requestSources();
    private static final Set<String> CLASSIFICATION_SOURCES =
            Set.of("PACKAGE_SCENE_ROUTER", "SERVER_CLASSIFIER", "UNKNOWN");
    private static final Set<String> CONFIDENCE_BUCKETS =
            Set.of("HIGH", "MEDIUM", "LOW", "UNKNOWN");
    private static final Set<String> REVIEW_MODES =
            Set.of("QUICK_REVIEW", "STANDARD_REVIEW", "DEEP_REVIEW", "UNKNOWN");
    private static final Set<String> TRAFFIC_CLASSES =
            Set.of("NATURAL", "PROBE", "DIAGNOSTIC", "SYNTHETIC", "UNKNOWN");
    private static final Set<String> OUTCOMES =
            Set.of("SUCCESS", "FAILED", "WITHHELD");
    private static final Set<String> REGISTERED_HOST_CLIENT_FAMILIES =
            IDENTITY_REGISTRY.hostClientFamilies();
    private static final Set<OfficialIdentityProfile> OFFICIAL_IDENTITY_PROFILES =
            IDENTITY_REGISTRY.profiles();

    private final Map<String, byte[]> keyring;
    private final Clock clock;
    private final BoardIntentClassifier intentClassifier;

    public BoardAttributionEventV1Verifier(
            Map<String, String> encodedKeys, Clock clock) {
        this(encodedKeys, clock, new BoardIntentClassifier());
    }

    public BoardAttributionEventV1Verifier(
            Map<String, String> encodedKeys,
            Clock clock,
            BoardIntentClassifier intentClassifier) {
        this.keyring = decodeKeyring(encodedKeys);
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.intentClassifier = intentClassifier == null
                ? new BoardIntentClassifier() : intentClassifier;
    }

    @Override
    public boolean isConfigured() {
        return !keyring.isEmpty();
    }

    @Override
    public VerifiedBoardAttributionEvent verify(
            BoardAttributionEventV1 event,
            IndependentBoardAttributionProperties properties) {
        if (event == null) {
            reject("event_required");
        }
        if (properties == null) {
            reject("properties_required");
        }
        verifyCanonicalWireText(event);
        verifyIdentity(event);
        verifyFiniteDimensions(event);
        verifySequence(event);
        verifyPrivacy(event);

        Instant issuedAt = parseInstant(event.getIssuedAt());
        Instant expiresAt = parseInstant(event.getExpiresAt());
        Instant occurredAt = parseInstant(event.getOccurredAt());
        Instant now = clock.instant();
        long configuredTtl = Math.max(1,
                Math.min(properties.getReceiptTtlSeconds(), MAX_TTL_SECONDS));
        long ttl;
        try {
            ttl = expiresAt.getEpochSecond() - issuedAt.getEpochSecond();
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException(
                    "event_expired_or_ttl_invalid", error);
        }
        if (ttl <= 0 || ttl > configuredTtl
                || now.isBefore(issuedAt.minusSeconds(CLOCK_SKEW_SECONDS))
                || now.isAfter(expiresAt)) {
            reject("event_expired_or_ttl_invalid");
        }
        if (!isConfigured()) {
            reject("event_signature_keyring_unconfigured");
        }
        String keyId = text(event.getKeyId());
        byte[] secret = keyring.get(keyId);
        if (secret == null) {
            reject("event_signature_key_unknown");
        }
        if (!SIGNATURE_ALGORITHM.equals(text(event.getSignatureAlgorithm()))) {
            reject("signature_algorithm_unsupported");
        }
        String supplied = signatureHex(event.getSignature());
        if (supplied.isEmpty()) {
            reject("signature_malformed");
        }
        String canonical = canonicalJson(signedFields(event));
        if (canonical.length() > MAX_CANONICAL_CHARS) {
            reject("event_too_large");
        }
        byte[] computed = hmacSha256(
                secret, canonical.getBytes(StandardCharsets.UTF_8));
        if (!MessageDigest.isEqual(computed, HexFormat.of().parseHex(supplied))) {
            reject("signature_mismatch");
        }

        String businessCanonical = canonicalJson(businessFields(event));
        String canonicalDigest = sha256Hex(
                businessCanonical.getBytes(StandardCharsets.UTF_8));
        String nonceHash = sha256Hex(
                text(event.getNonce()).getBytes(StandardCharsets.UTF_8));
        String eventDigest = sha256Hex(
                ("FBSIR_INDEPENDENT_BOARD_EVENT_DIGEST_V1\n"
                        + businessCanonical).getBytes(StandardCharsets.UTF_8));
        long retentionSeconds = Math.max(1L, properties.getRetentionHours())
                * 60L * 60L;
        boolean outsideOrdinaryRetention =
                occurredAt.isBefore(now.minusSeconds(retentionSeconds));
        if ((outsideOrdinaryRetention
                && !historicalSyntheticReplayAllowed(
                        event, eventDigest, occurredAt, now, properties))
                || occurredAt.isAfter(now.plusSeconds(CLOCK_SKEW_SECONDS))) {
            reject("event_occurred_at_outside_retention");
        }
        return new VerifiedBoardAttributionEvent(
                event, issuedAt, expiresAt, canonicalDigest, supplied,
                nonceHash, eventDigest,
                intentClassifier.classify(event.getIntentSignal()));
    }

    private void verifyCanonicalWireText(BoardAttributionEventV1 event) {
        Object[] values = {
                event.getSchemaVersion(), event.getEventId(),
                event.getReceiptId(), event.getContractId(),
                event.getEventType(), event.getOccurredAt(),
                event.getProductId(), event.getPackageId(),
                event.getAgentName(), event.getMarketplace(),
                event.getListedSurface(), event.getListedManifestVersion(),
                event.getEmbeddedContractVersion(),
                event.getHostClientFamily(), event.getHostVersion(),
                event.getTerminal(), event.getChannel(),
                event.getRequestSource(), event.getIntentSignal(),
                event.getClassificationSource(), event.getClassifierVersion(),
                event.getConfidenceBucket(), event.getReviewMode(),
                event.getJourneyId(), event.getServerBindingId(),
                event.getSameBindingKey(), event.getTenantSubjectDigest(),
                event.getTrafficClass(), event.getTrafficAuthority(),
                event.getOutcome(), event.getPreviousEventDigest(),
                event.getTraceparent(), event.getIssuedAt(),
                event.getExpiresAt(), event.getNonce(), event.getKeyId(),
                event.getSignatureAlgorithm(), event.getSignature()
        };
        for (Object value : values) {
            if (value instanceof String supplied
                    && !supplied.equals(supplied.trim())) {
                reject("noncanonical_text");
            }
        }
    }

    private void verifyIdentity(BoardAttributionEventV1 event) {
        if (!SCHEMA_VERSION.equals(text(event.getSchemaVersion()))
                || !CONTRACT_ID.equals(text(event.getContractId()))
                || !"fbsir-eight-seat-board".equals(text(event.getProductId()))
                || !"fbsir-eight-seat-board".equals(text(event.getPackageId()))
                || !"board-convener".equals(text(event.getAgentName()))
                || !"experts".equals(text(event.getMarketplace()))
                || !"listed_runtime_state".equals(text(event.getListedSurface()))) {
            reject("official_identity_mismatch");
        }
        String hostClientFamily = text(event.getHostClientFamily());
        if (!REGISTERED_HOST_CLIENT_FAMILIES.contains(hostClientFamily)) {
            reject("official_identity_mismatch");
        }
        OfficialIdentityProfile suppliedProfile = OFFICIAL_IDENTITY_PROFILES
                .stream()
                .filter(profile -> profile.matches(
                        hostClientFamily,
                        text(event.getListedManifestVersion()),
                        text(event.getEmbeddedContractVersion())))
                .findFirst()
                .orElse(null);
        if (suppliedProfile == null) {
            reject("listed_identity_mismatch");
        }
        verifyHostProjection(event, suppliedProfile);
        if (!sha256(event.getEventId()) || !sha256(event.getReceiptId())
                || !sha256(event.getJourneyId())
                || !BINDING.matcher(text(event.getServerBindingId())).matches()
                || !sha256(event.getTenantSubjectDigest())) {
            reject("identity_digest_invalid");
        }
        if (!text(event.getSameBindingKey()).isEmpty()) {
            reject("upstream_same_binding_key_forbidden");
        }
    }

    private void verifyFiniteDimensions(BoardAttributionEventV1 event) {
        String hostVersion = text(event.getHostVersion());
        if (hostVersion.length() > MAX_HOST_VERSION_CHARS
                || !HOST_VERSION.matcher(hostVersion).matches()
                || !TERMINALS.contains(text(event.getTerminal()))
                || !CHANNELS.contains(text(event.getChannel()))
                || !REQUEST_SOURCES.contains(text(event.getRequestSource()))
                || !CLASSIFICATION_SOURCES.contains(
                        text(event.getClassificationSource()))
                || !CONFIDENCE_BUCKETS.contains(
                        text(event.getConfidenceBucket()))
                || !REVIEW_MODES.contains(text(event.getReviewMode()))
                || !TRAFFIC_CLASSES.contains(text(event.getTrafficClass()))
                || !OUTCOMES.contains(text(event.getOutcome()))
                || !safeToken(event.getIntentSignal(), MAX_PERSISTED_TOKEN_CHARS)
                || !safeToken(event.getClassifierVersion(), MAX_PERSISTED_TOKEN_CHARS)
                || !safeToken(event.getNonce())
                || !safeToken(event.getKeyId(), MAX_SIGNER_KEY_ID_CHARS)) {
            reject("finite_dimension_invalid");
        }
        if ("NATURAL".equals(text(event.getTrafficClass()))
                && !"API2_SERVER_CLASSIFIER_V1".equals(
                text(event.getTrafficAuthority()))) {
            reject("traffic_authority_invalid");
        }
        if (!"API2_SERVER_CLASSIFIER_V1".equals(
                text(event.getTrafficAuthority()))) {
            reject("traffic_authority_invalid");
        }
    }

    private void verifyHostProjection(
            BoardAttributionEventV1 event,
            OfficialIdentityProfile suppliedProfile) {
        String hostClientFamily = suppliedProfile.hostClientFamily();
        String requestSource = text(event.getRequestSource());
        if ((!IDENTITY_REGISTRY.sharedRequestSources().contains(requestSource)
                    && !suppliedProfile.officialEntryRequestSource()
                        .equals(requestSource))
                || ("WORKBUDDYAI".equals(text(event.getTerminal()))
                    && !"WORKBUDDYAI".equals(hostClientFamily))) {
            reject("host_projection_mismatch");
        }
    }

    private boolean historicalSyntheticReplayAllowed(
            BoardAttributionEventV1 event,
            String eventDigest,
            Instant occurredAt,
            Instant now,
            IndependentBoardAttributionProperties properties) {
        if (!properties.isHistoricalSyntheticReplayEnabled()
                || !"SYNTHETIC".equals(text(event.getTrafficClass()))) {
            return false;
        }
        int maxAgeHours = properties.getHistoricalSyntheticReplayMaxAgeHours();
        if (maxAgeHours < properties.getRetentionHours()
                || maxAgeHours > MAX_HISTORICAL_SYNTHETIC_REPLAY_HOURS) {
            return false;
        }
        Instant notAfter;
        try {
            notAfter = Instant.parse(text(
                    properties.getHistoricalSyntheticReplayNotAfter()));
        } catch (DateTimeException error) {
            return false;
        }
        long maxAgeSeconds;
        try {
            maxAgeSeconds = Math.multiplyExact((long) maxAgeHours, 3_600L);
        } catch (ArithmeticException error) {
            return false;
        }
        return !now.isAfter(notAfter)
                && !occurredAt.isBefore(now.minusSeconds(maxAgeSeconds))
                && properties.getHistoricalSyntheticReplayEventDigests()
                    != null
                && properties.getHistoricalSyntheticReplayEventDigests()
                    .contains(eventDigest);
    }

    private void verifySequence(BoardAttributionEventV1 event) {
        long sequence = event.getSequenceNo();
        String type = text(event.getEventType());
        boolean valid = ("ENTRY_OBSERVED".equals(type) && sequence == 1)
                || ("INTENT_CLASSIFIED".equals(type) && sequence == 2)
                || ("FIRST_VALUE_COMPLETED".equals(type) && sequence == 3);
        if (!valid) {
            reject("event_sequence_invalid");
        }
        String previous = text(event.getPreviousEventDigest());
        if ((sequence == 1 && !previous.isEmpty())
                || (sequence > 1 && !sha256(previous))) {
            reject("previous_event_digest_invalid");
        }
    }

    private void verifyPrivacy(BoardAttributionEventV1 event) {
        if (event.isRawContentStored()) {
            reject("raw_content_forbidden");
        }
        String traceparent = text(event.getTraceparent());
        if (!traceparent.isEmpty()
                && !TRACEPARENT.matcher(traceparent).matches()) {
            reject("traceparent_invalid");
        }
    }

    private Map<String, String> signedFields(BoardAttributionEventV1 event) {
        Map<String, String> values = new TreeMap<>();
        values.put("agentName", text(event.getAgentName()));
        values.put("channel", text(event.getChannel()));
        values.put("classificationSource", text(event.getClassificationSource()));
        values.put("classifierVersion", text(event.getClassifierVersion()));
        values.put("confidenceBucket", text(event.getConfidenceBucket()));
        values.put("contractId", text(event.getContractId()));
        values.put("embeddedContractVersion",
                text(event.getEmbeddedContractVersion()));
        values.put("eventId", text(event.getEventId()));
        values.put("eventType", text(event.getEventType()));
        values.put("expiresAt", text(event.getExpiresAt()));
        values.put("hostClientFamily", text(event.getHostClientFamily()));
        values.put("hostVersion", text(event.getHostVersion()));
        values.put("intentSignal", text(event.getIntentSignal()));
        values.put("issuedAt", text(event.getIssuedAt()));
        values.put("journeyId", text(event.getJourneyId()));
        values.put("keyId", text(event.getKeyId()));
        values.put("listedManifestVersion",
                text(event.getListedManifestVersion()));
        values.put("listedSurface", text(event.getListedSurface()));
        values.put("marketplace", text(event.getMarketplace()));
        values.put("nonce", text(event.getNonce()));
        values.put("occurredAt", text(event.getOccurredAt()));
        values.put("outcome", text(event.getOutcome()));
        values.put("packageId", text(event.getPackageId()));
        values.put("previousEventDigest", text(event.getPreviousEventDigest()));
        values.put("productId", text(event.getProductId()));
        values.put("rawContentStored",
                Boolean.toString(event.isRawContentStored()));
        values.put("receiptId", text(event.getReceiptId()));
        values.put("requestSource", text(event.getRequestSource()));
        values.put("reviewMode", text(event.getReviewMode()));
        values.put("sameBindingKey", text(event.getSameBindingKey()));
        values.put("schemaVersion", text(event.getSchemaVersion()));
        values.put("sequenceNo", Long.toString(event.getSequenceNo()));
        values.put("serverBindingId", text(event.getServerBindingId()));
        values.put("signatureAlgorithm", text(event.getSignatureAlgorithm()));
        values.put("tenantSubjectDigest", text(event.getTenantSubjectDigest()));
        values.put("terminal", text(event.getTerminal()));
        values.put("traceparent", text(event.getTraceparent()));
        values.put("trafficAuthority", text(event.getTrafficAuthority()));
        values.put("trafficClass", text(event.getTrafficClass()));
        return values;
    }

    /**
     * Stable business identity used for idempotency and chain linkage.
     *
     * Short-lived authentication fields are deliberately excluded so a
     * durable outbox can re-sign the same event after a transport outage
     * without creating a second business event or breaking the chain.
     */
    private Map<String, String> businessFields(
            BoardAttributionEventV1 event) {
        Map<String, String> values = new TreeMap<>(signedFields(event));
        values.remove("issuedAt");
        values.remove("expiresAt");
        values.remove("nonce");
        values.remove("keyId");
        return values;
    }

    private static Map<String, byte[]> decodeKeyring(
            Map<String, String> encodedKeys) {
        if (encodedKeys == null) {
            return Map.of();
        }
        Map<String, byte[]> decoded = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : encodedKeys.entrySet()) {
            String keyId = text(entry.getKey());
            byte[] secret = decodeSecret(entry.getValue());
            if (!keyId.isEmpty() && secret != null
                    && secret.length >= MIN_SECRET_BYTES) {
                decoded.put(keyId, secret.clone());
            }
        }
        return Map.copyOf(decoded);
    }

    private static byte[] decodeSecret(String encoded) {
        String value = text(encoded);
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

    private static Instant parseInstant(String value) {
        try {
            return Instant.parse(text(value));
        } catch (DateTimeException error) {
            throw new IllegalArgumentException("timestamp_malformed", error);
        }
    }

    private static String canonicalJson(Map<String, String> values) {
        try {
            return JSON.writeValueAsString(new TreeMap<>(values));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException(
                    "Unable to canonicalize attribution event", error);
        }
    }

    private static byte[] hmacSha256(byte[] secret, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(message);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("HmacSHA256 unavailable", error);
        }
    }

    private static String sha256Hex(byte[] input) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(input));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    private static String signatureHex(String signature) {
        String value = text(signature).toLowerCase(java.util.Locale.ROOT);
        if (value.startsWith("v1=")) {
            value = value.substring(3);
        }
        return HEX_64.matcher(value).matches() ? value : "";
    }

    private static boolean sha256(String value) {
        return HEX_64.matcher(text(value)).matches();
    }

    private static boolean safeToken(String value) {
        return safeToken(value, MAX_FIELD_CHARS);
    }

    private static boolean safeToken(String value, int maxChars) {
        String normalized = text(value);
        return normalized.length() <= maxChars
                && SAFE_TOKEN.matcher(normalized).matches();
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static void reject(String reason) {
        throw new IllegalArgumentException(reason);
    }

    private static IdentityRegistry loadIdentityRegistry() {
        String resource =
                "/contracts/independent-board-attribution-identity-registry-v1.json";
        try (InputStream input =
                BoardAttributionEventV1Verifier.class.getResourceAsStream(
                        resource)) {
            if (input == null) {
                throw new IllegalStateException(
                        "attribution_identity_registry_missing");
            }
            JsonNode root = JSON.readTree(input);
            JsonNode common = root.path("commonIdentity");
            if (!"fbsir.independentBoardAttributionIdentityRegistry.v1"
                    .equals(root.path("schemaVersion").asText())
                    || !SCHEMA_VERSION.equals(
                        common.path("eventSchemaVersion").asText())
                    || !CONTRACT_ID.equals(common.path("contractId").asText())
                    || !"fbsir-eight-seat-board".equals(
                        common.path("productId").asText())
                    || !"fbsir-eight-seat-board".equals(
                        common.path("packageId").asText())
                    || !"board-convener".equals(
                        common.path("agentName").asText())
                    || !"experts".equals(common.path("marketplace").asText())
                    || !"listed_runtime_state".equals(
                        common.path("listedSurface").asText())
                    || root.path("productCreditEligible").asBoolean(true)) {
                throw new IllegalStateException(
                        "attribution_identity_registry_common_identity_invalid");
            }
            Set<OfficialIdentityProfile> profiles = new LinkedHashSet<>();
            Set<String> profileIds = new LinkedHashSet<>();
            Set<String> hosts = new LinkedHashSet<>();
            Set<String> sharedSources = new LinkedHashSet<>();
            for (JsonNode source : root.path("sharedRequestSources")) {
                sharedSources.add(source.asText());
            }
            for (JsonNode node : root.path("profiles")) {
                String profileId = node.path("profileId").asText();
                OfficialIdentityProfile profile = new OfficialIdentityProfile(
                        node.path("hostClientFamily").asText(),
                        node.path("listedManifestVersion").asText(),
                        node.path("embeddedContractVersion").asText(),
                        node.path("officialEntryRequestSource").asText());
                if (!SAFE_TOKEN.matcher(profileId).matches()
                        || !profileIds.add(profileId)
                        || !profiles.add(profile)
                        || !SAFE_TOKEN.matcher(
                            profile.hostClientFamily()).matches()
                        || !SAFE_TOKEN.matcher(
                            profile.listedManifestVersion()).matches()
                        || !SAFE_TOKEN.matcher(
                            profile.embeddedContractVersion()).matches()
                        || !SAFE_TOKEN.matcher(
                            profile.officialEntryRequestSource()).matches()) {
                    throw new IllegalStateException(
                            "attribution_identity_registry_profile_invalid");
                }
                hosts.add(profile.hostClientFamily());
            }
            if (profiles.size() != 3
                    || !sharedSources.equals(Set.of(
                        "HOST_FORWARDING", "UNKNOWN"))) {
                throw new IllegalStateException(
                        "attribution_identity_registry_cardinality_invalid");
            }
            Set<String> requestSources = new LinkedHashSet<>(sharedSources);
            profiles.forEach(profile -> requestSources.add(
                    profile.officialEntryRequestSource()));
            return new IdentityRegistry(
                    Set.copyOf(profiles), Set.copyOf(hosts),
                    Set.copyOf(sharedSources), Set.copyOf(requestSources));
        } catch (IOException error) {
            throw new IllegalStateException(
                    "attribution_identity_registry_unreadable", error);
        }
    }

    private record OfficialIdentityProfile(
            String hostClientFamily,
            String listedManifestVersion,
            String embeddedContractVersion,
            String officialEntryRequestSource) {
        private boolean matches(
                String suppliedHost,
                String suppliedListedVersion,
                String suppliedEmbeddedVersion) {
            return hostClientFamily.equals(suppliedHost)
                    && listedManifestVersion.equals(suppliedListedVersion)
                    && embeddedContractVersion.equals(
                        suppliedEmbeddedVersion);
        }
    }

    private record IdentityRegistry(
            Set<OfficialIdentityProfile> profiles,
            Set<String> hostClientFamilies,
            Set<String> sharedRequestSources,
            Set<String> requestSources) {
    }
}
