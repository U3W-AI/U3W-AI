package com.wx.fbsir.business.board.attribution.intent;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BoardIntentClassifierTest {
    private final BoardIntentClassifier classifier = new BoardIntentClassifier();

    @Test
    void mapsEveryOfficialSceneIdToTheFiniteServiceIntentFamily() {
        Map<String, String> scenes = Map.of(
                "operating_diagnosis", "OPERATING_DIAGNOSIS",
                "strategy_transition", "STRATEGY_TRANSITION",
                "capital_transaction", "CAPITAL_TRANSACTION",
                "growth_channel", "GROWTH_CHANNEL",
                "organization_succession", "ORGANIZATION_SUCCESSION",
                "cross_border_expansion", "CROSS_BORDER_EXPANSION",
                "digital_ai_transformation", "DIGITAL_AI_TRANSFORMATION");

        scenes.forEach((signal, expected) ->
                assertEquals(expected, classifier.classify(signal)));
    }

    @Test
    void preservesExplicitOtherAndFailsClosedToUnknownWithoutReadingRawText() {
        assertEquals("OTHER", classifier.classify("other"));
        assertEquals("UNKNOWN", classifier.classify("unknown"));
        assertEquals("UNKNOWN", classifier.classify(""));
        assertEquals("UNKNOWN", classifier.classify(null));
        assertEquals("UNKNOWN", classifier.classify("please analyze alice@example.com"));
    }
}
