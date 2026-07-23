package com.wx.fbsir.business.board.attribution.traffic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BoardTrafficClassificationResolverTest {
    private final BoardTrafficClassificationResolver resolver =
            new BoardTrafficClassificationResolver();

    @Test
    void appliesFailClosedConflictPrecedence() {
        assertEquals("SYNTHETIC",
                resolver.resolve("NATURAL", "DIAGNOSTIC", "SYNTHETIC"));
        assertEquals("PROBE",
                resolver.resolve("NATURAL", "UNKNOWN", "PROBE"));
        assertEquals("DIAGNOSTIC",
                resolver.resolve("NATURAL", "DIAGNOSTIC"));
        assertEquals("UNKNOWN",
                resolver.resolve("NATURAL", "unexpected-client-value"));
    }

    @Test
    void allowsNaturalOnlyWhenEveryObservedMarkerIsExplicitlyNatural() {
        assertEquals("NATURAL", resolver.resolve("NATURAL"));
        assertEquals("NATURAL", resolver.resolve("natural", " NATURAL "));
        assertEquals("UNKNOWN", resolver.resolve());
        assertEquals("UNKNOWN", resolver.resolve("", null));
    }
}
