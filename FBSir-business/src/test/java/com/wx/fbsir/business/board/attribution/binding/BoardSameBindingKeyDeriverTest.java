package com.wx.fbsir.business.board.attribution.binding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardSameBindingKeyDeriverTest {
    private final BoardSameBindingKeyDeriver deriver =
            new BoardSameBindingKeyDeriver(
                    "utf8:abcdef0123456789abcdef0123456789");

    @Test
    void derivesTheServerKeyFromTheExactOfficialIdentityAndBindingContext() {
        String key = derive("26.7.21", "3".repeat(64));

        assertEquals(
                "be10b068af65e0059f9fa65a888a699b2b48bd9a76b5345f3e1366e0fab60d6b",
                key);
        assertTrue(deriver.matches(key,
                "FBSIR_INDEPENDENT_BOARD_W1A_V1",
                "5".repeat(64),
                "srv_wave1Binding01",
                "3".repeat(64),
                "fbsir-eight-seat-board",
                "26.7.21"));
    }

    @Test
    void rejectsAClientChosenKeyAndAnyOldListedVersion() {
        String current = derive("26.7.21", "3".repeat(64));

        assertFalse(deriver.matches("0".repeat(64),
                "FBSIR_INDEPENDENT_BOARD_W1A_V1",
                "5".repeat(64),
                "srv_wave1Binding01",
                "3".repeat(64),
                "fbsir-eight-seat-board",
                "26.7.21"));
        assertFalse(deriver.matches(current,
                "FBSIR_INDEPENDENT_BOARD_W1A_V1",
                "5".repeat(64),
                "srv_wave1Binding01",
                "3".repeat(64),
                "fbsir-eight-seat-board",
                "26.7.20"));
    }

    private String derive(String listedVersion, String journeyId) {
        return deriver.derive(
                "FBSIR_INDEPENDENT_BOARD_W1A_V1",
                "5".repeat(64),
                "srv_wave1Binding01",
                journeyId,
                "fbsir-eight-seat-board",
                listedVersion);
    }
}
