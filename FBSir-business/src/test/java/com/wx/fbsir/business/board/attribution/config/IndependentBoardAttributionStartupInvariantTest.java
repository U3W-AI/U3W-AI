package com.wx.fbsir.business.board.attribution.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IndependentBoardAttributionStartupInvariantTest {

    @Test
    void defaultOffAllowsNoCryptographicMaterial() {
        assertDoesNotThrow(() -> validate(new IndependentBoardAttributionProperties()));
    }

    @Test
    void provisionedDefaultOffKeysAreResolvedAndRemainInactive() {
        IndependentBoardAttributionProperties properties = keys();
        assertDoesNotThrow(() -> validate(properties));
        assertEquals(2, properties.getResolvedEventKeys().size());
    }

    @Test
    void explicitKeyPairMustBeComplete() {
        IndependentBoardAttributionProperties properties =
                new IndependentBoardAttributionProperties();
        properties.setActiveEventKeyId("wave1-k1");
        assertThrows(
                IllegalStateException.class,
                () -> validate(properties),
                "an id without its secret must fail");
    }

    @Test
    void eventAndSameBindingSecretsMustBeIndependent() {
        IndependentBoardAttributionProperties properties = keys();
        properties.setSameBindingSecret(properties.getActiveEventKey());
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> validate(properties));
        assertEquals(
                "attribution_event_and_binding_keys_must_differ",
                error.getMessage());
    }

    @Test
    void crossEncodingEquivalentSecretsAreStillRejected() {
        IndependentBoardAttributionProperties properties =
                new IndependentBoardAttributionProperties();
        properties.setActiveEventKeyId("wave1-k1");
        properties.setActiveEventKey("utf8:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        properties.setSameBindingSecret(
                "hex:61616161616161616161616161616161"
                        + "61616161616161616161616161616161");
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> validate(properties));
        assertEquals(
                "attribution_event_and_binding_keys_must_differ",
                error.getMessage());
    }

    @Test
    void unpaddedBase64UsesTheSameSemanticsAsJavaDecoder() {
        IndependentBoardAttributionProperties properties =
                new IndependentBoardAttributionProperties();
        properties.setActiveEventKeyId("wave1-k1");
        properties.setActiveEventKey(
                "base64:YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE");
        properties.setSameBindingSecret(
                "utf8:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> validate(properties));
        assertEquals(
                "attribution_event_and_binding_keys_must_differ",
                error.getMessage());
    }

    @Test
    void whitespaceInsideHexIsRejected() {
        IndependentBoardAttributionProperties properties = keys();
        properties.setActiveEventKey(
                "hex:6161616161616161 6161616161616161"
                        + "61616161616161616161616161616161");
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> validate(properties));
        assertEquals("attribution_event_keyring_invalid", error.getMessage());
    }

    @Test
    void intentAndCreditCannotBypassTheirParentGates() {
        IndependentBoardAttributionProperties properties = keys();
        properties.setEnabled(true);
        properties.setIntentClassifierEnabled(true);
        IllegalStateException intentError = assertThrows(
                IllegalStateException.class,
                () -> validate(properties));
        assertEquals(
                "attribution_intent_requires_writer",
                intentError.getMessage());

        properties.setObservationWriterEnabled(true);
        properties.setProductCreditEnabled(true);
        IllegalStateException creditError = assertThrows(
                IllegalStateException.class,
                () -> validate(properties));
        assertEquals(
                "attribution_product_credit_gates_incomplete",
                creditError.getMessage());
    }

    @Test
    void w1aSurfaceCannotBypassTheMasterGate() {
        IndependentBoardAttributionProperties properties = keys();
        properties.setObservationAdminReadEnabled(true);
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> validate(properties));
        assertEquals("attribution_master_gate_required", error.getMessage());
    }

    @Test
    void historicalSyntheticReplayIsBoundedAndReportOnly() {
        IndependentBoardAttributionProperties properties = keys();
        properties.setEnabled(true);
        properties.setObservationWriterEnabled(true);
        properties.setHistoricalSyntheticReplayEnabled(true);
        properties.setHistoricalSyntheticReplayMaxAgeHours(169);
        properties.setHistoricalSyntheticReplayNotAfter(
                "2026-08-24T00:00:00Z");
        properties.setHistoricalSyntheticReplayEventDigests(
                java.util.Set.of("a".repeat(64)));
        IllegalStateException ageError = assertThrows(
                IllegalStateException.class, () -> validate(properties));
        assertEquals("historical_synthetic_replay_max_age_invalid",
                ageError.getMessage());

        properties.setHistoricalSyntheticReplayMaxAgeHours(168);
        properties.setHistoricalSyntheticReplayNotAfter("not-an-instant");
        IllegalStateException timeError = assertThrows(
                IllegalStateException.class, () -> validate(properties));
        assertEquals("historical_synthetic_replay_not_after_invalid",
                timeError.getMessage());

        properties.setHistoricalSyntheticReplayNotAfter(
                "2026-08-24T00:00:00Z");
        properties.setAuthoritativeCreditEnabled(true);
        IllegalStateException creditError = assertThrows(
                IllegalStateException.class, () -> validate(properties));
        assertEquals(
                "historical_synthetic_replay_requires_report_only_writer",
                creditError.getMessage());

        properties.setAuthoritativeCreditEnabled(false);
        properties.setHistoricalSyntheticReplayEventDigests(
                java.util.Set.of());
        IllegalStateException allowlistError = assertThrows(
                IllegalStateException.class, () -> validate(properties));
        assertEquals(
                "historical_synthetic_replay_digest_allowlist_invalid",
                allowlistError.getMessage());
    }

    @Test
    void authoritativeReadbackRequiresReportOnlyWriterAndExactRuntimeBinding() {
        IndependentBoardAttributionProperties properties = readback();
        assertDoesNotThrow(() -> validate(properties));

        properties.setObservationWriterEnabled(false);
        IllegalStateException writerError = assertThrows(
                IllegalStateException.class, () -> validate(properties));
        assertEquals(
                "attribution_readback_requires_report_only_writer",
                writerError.getMessage());

        properties.setObservationWriterEnabled(true);
        properties.setProductCreditEnabled(true);
        IllegalStateException creditError = assertThrows(
                IllegalStateException.class, () -> validate(properties));
        assertEquals(
                "attribution_readback_requires_report_only_writer",
                creditError.getMessage());
    }

    @Test
    void authoritativeReadbackRejectsTtlBindingAndActiveKeyDrift() {
        IndependentBoardAttributionProperties ttlProperties = readback();
        ttlProperties.setAuthoritativeReadbackTtlSeconds(61);
        IllegalStateException ttlError = assertThrows(
                IllegalStateException.class,
                () -> validate(ttlProperties));
        assertEquals("attribution_readback_ttl_invalid",
                ttlError.getMessage());

        IndependentBoardAttributionProperties bindingProperties = readback();
        bindingProperties.setAuthoritativeReadbackReceiverJarSha256("bad");
        IllegalStateException bindingError = assertThrows(
                IllegalStateException.class,
                () -> validate(bindingProperties));
        assertEquals("attribution_readback_receiver_binding_invalid",
                bindingError.getMessage());

        IndependentBoardAttributionProperties pathProperties = readback();
        pathProperties.setAuthoritativeReadbackReceiverJarPath("relative.jar");
        IllegalStateException pathError = assertThrows(
                IllegalStateException.class,
                () -> validate(pathProperties));
        assertEquals("attribution_readback_receiver_binding_invalid",
                pathError.getMessage());

        IndependentBoardAttributionProperties keyProperties = readback();
        keyProperties.setActiveEventKeyId("");
        keyProperties.setActiveEventKey("");
        IllegalStateException keyError = assertThrows(
                IllegalStateException.class,
                () -> validate(keyProperties));
        assertEquals("attribution_readback_active_key_invalid",
                keyError.getMessage());

        IndependentBoardAttributionProperties budgetProperties = readback();
        budgetProperties.setAuthoritativeReadbackMaximumConcurrent(0);
        IllegalStateException budgetError = assertThrows(
                IllegalStateException.class,
                () -> validate(budgetProperties));
        assertEquals("attribution_readback_admission_budget_invalid",
                budgetError.getMessage());
    }

    private static IndependentBoardAttributionProperties keys() {
        IndependentBoardAttributionProperties properties =
                new IndependentBoardAttributionProperties();
        properties.setActiveEventKeyId("wave1-k1");
        properties.setActiveEventKey(
                "utf8:event-secret-material-at-least-32-bytes-a");
        properties.setPreviousEventKeyId("wave1-k0");
        properties.setPreviousEventKey(
                "utf8:previous-secret-material-at-least-32-bytes");
        properties.setSameBindingSecret(
                "utf8:binding-secret-material-at-least-32-bytes");
        return properties;
    }

    private static IndependentBoardAttributionProperties readback() {
        IndependentBoardAttributionProperties properties = keys();
        properties.setEnabled(true);
        properties.setObservationWriterEnabled(true);
        properties.setAuthoritativeReadbackEnabled(true);
        properties.setAuthoritativeReadbackTtlSeconds(60);
        properties.setAuthoritativeReadbackReceiverReleaseId(
                "w05e-readback-test");
        properties.setAuthoritativeReadbackReceiverJarSha256(
                "a".repeat(64));
        properties.setAuthoritativeReadbackReceiverJarPath(
                java.nio.file.Path.of(
                        System.getProperty("java.io.tmpdir"),
                        "receiver.jar").toAbsolutePath().toString());
        return properties;
    }

    private static void validate(
            IndependentBoardAttributionProperties properties)
            throws Exception {
        new IndependentBoardAttributionStartupInvariant(properties)
                .afterPropertiesSet();
    }
}
