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

    private static void validate(
            IndependentBoardAttributionProperties properties)
            throws Exception {
        new IndependentBoardAttributionStartupInvariant(properties)
                .afterPropertiesSet();
    }
}
