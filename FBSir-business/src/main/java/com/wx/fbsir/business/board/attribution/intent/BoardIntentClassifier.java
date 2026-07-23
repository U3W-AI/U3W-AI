package com.wx.fbsir.business.board.attribution.intent;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

/**
 * Maps only finite signals emitted by the official package scene router.
 * Raw prompts and conversation content are intentionally outside this API.
 */
@Component
public class BoardIntentClassifier {
    private static final Map<String, String> OFFICIAL_SCENES = Map.of(
            "operating_diagnosis", "OPERATING_DIAGNOSIS",
            "strategy_transition", "STRATEGY_TRANSITION",
            "capital_transaction", "CAPITAL_TRANSACTION",
            "growth_channel", "GROWTH_CHANNEL",
            "organization_succession", "ORGANIZATION_SUCCESSION",
            "cross_border_expansion", "CROSS_BORDER_EXPANSION",
            "digital_ai_transformation", "DIGITAL_AI_TRANSFORMATION");

    public String classify(String signal) {
        if (signal == null) {
            return "UNKNOWN";
        }
        String normalized = signal.trim().toLowerCase(Locale.ROOT);
        if ("other".equals(normalized)) {
            return "OTHER";
        }
        if ("unknown".equals(normalized) || normalized.isEmpty()) {
            return "UNKNOWN";
        }
        return OFFICIAL_SCENES.getOrDefault(normalized, "UNKNOWN");
    }
}
