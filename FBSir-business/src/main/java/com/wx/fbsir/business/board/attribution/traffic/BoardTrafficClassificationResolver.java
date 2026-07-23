package com.wx.fbsir.business.board.attribution.traffic;

import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Resolves conflicting traffic markers with the FBS fail-closed precedence:
 * synthetic, probe, diagnostic, unknown, natural.
 */
@Component
public class BoardTrafficClassificationResolver {
    public String resolve(String... markers) {
        if (markers == null || markers.length == 0) {
            return "UNKNOWN";
        }
        int strongest = 0;
        for (String marker : markers) {
            String normalized = marker == null
                    ? "" : marker.trim().toUpperCase(Locale.ROOT);
            strongest = Math.max(strongest, rank(normalized));
        }
        return switch (strongest) {
            case 5 -> "SYNTHETIC";
            case 4 -> "PROBE";
            case 3 -> "DIAGNOSTIC";
            case 1 -> "NATURAL";
            default -> "UNKNOWN";
        };
    }

    private int rank(String marker) {
        return switch (marker) {
            case "SYNTHETIC" -> 5;
            case "PROBE" -> 4;
            case "DIAGNOSTIC" -> 3;
            case "NATURAL" -> 1;
            default -> 2;
        };
    }
}
