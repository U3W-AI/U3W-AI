package com.wx.fbsir.business.board.portal;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.wx.fbsir.business.board.portal.dto.BoardPortalConnectorBindingView;
import com.wx.fbsir.business.board.portal.dto.BoardPortalConnectorView;
import com.wx.fbsir.business.board.portal.dto.BoardPortalOAuthClientView;
import com.wx.fbsir.business.board.portal.dto.BoardPortalOAuthFamilyView;
import com.wx.fbsir.business.board.portal.dto.BoardPortalReadEnvelope;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardPortalReadContractTest {

    @Test
    void publicViewsExposeOnlyTheFrontendCandidateContract() {
        assertComponents(BoardPortalOAuthClientView.class,
                "clientRef", "displayName", "status", "redirectUri",
                "grantTypes", "responseTypes", "scopes", "registeredAt",
                "expiresAt", "terminatedAt", "version", "metadataDigestRef",
                "registrationSourceDigestRef");
        assertComponents(BoardPortalOAuthFamilyView.class,
                "familyRef", "tenantId", "tenantLabel", "memberLabel", "userLabel",
                "clientRef", "consentIntent", "status", "currentRefreshGeneration",
                "bindingRef", "issuedAt", "activatedAt", "expiresAt", "terminatedAt",
                "version");
        assertComponents(BoardPortalConnectorBindingView.class,
                "bindingRef", "tenantId", "tenantLabel", "memberLabel", "userLabel",
                "productCode", "sourceCode", "connectorCode", "status", "scopes",
                "verificationMethod", "verifiedAt", "lastSeenAt", "validUntil",
                "revokedAt", "clientRef", "subjectDigestRef", "version",
                "entitlementActive", "familyActive", "vipEffective");
        assertComponents(BoardPortalConnectorView.class,
                "tenantId", "memberId", "uiState", "effectivePlanCode", "clientRef",
                "familyRef", "bindingRef", "scopes", "issuedAt", "expiresAt",
                "lastSeenAt", "version", "evidenceLevel");
    }

    @Test
    void everyPublicTimestampHasAnExplicitUtcIso8601WireFormat() throws Exception {
        for (Class<?> recordType : List.of(
                BoardPortalOAuthClientView.class,
                BoardPortalOAuthFamilyView.class,
                BoardPortalConnectorBindingView.class,
                BoardPortalConnectorView.class)) {
            for (RecordComponent component : recordType.getRecordComponents()) {
                if (component.getType() != Instant.class) {
                    continue;
                }
                JsonFormat format = recordType.getDeclaredField(component.getName())
                        .getAnnotation(JsonFormat.class);
                assertNotNull(format, () -> recordType.getSimpleName() + "."
                        + component.getName() + " must define its JSON time contract");
                assertEquals("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", format.pattern());
                assertEquals("UTC", format.timezone());
            }
        }
    }

    @Test
    void envelopeCopiesRecordsAndEnforcesCursorPairing() {
        List<String> source = new ArrayList<>(List.of("one"));
        BoardPortalReadEnvelope<String> envelope =
                new BoardPortalReadEnvelope<>(source, 100, false, null);
        source.add("two");

        assertEquals(List.of("one"), envelope.records());
        assertThrows(UnsupportedOperationException.class,
                () -> envelope.records().add("three"));
        assertThrows(IllegalArgumentException.class,
                () -> new BoardPortalReadEnvelope<>(List.of(), 0, false, null));
        assertThrows(IllegalArgumentException.class,
                () -> new BoardPortalReadEnvelope<>(List.of(), 100, true, null));
        assertThrows(IllegalArgumentException.class,
                () -> new BoardPortalReadEnvelope<>(List.of(), 100, false,
                        "djE6Y2xpZW50OjEyMw"));
    }

    @Test
    void cursorIsSignedCanonicalExpiringAndBoundToTheFullReadContext() {
        Instant now = Instant.parse("2026-07-21T09:00:00Z");
        byte[] signingKey = "0123456789abcdef0123456789abcdef"
                .getBytes(StandardCharsets.US_ASCII);
        BoardPortalReadCursor cursorCodec = new BoardPortalReadCursor(
                signingKey, Clock.fixed(now, ZoneOffset.UTC), Duration.ofMinutes(15));
        BoardPortalReadCursor.Context clientContext = new BoardPortalReadCursor.Context(
                BoardPortalReadCursor.Kind.CLIENT,
                "/business/independent-board/oauth/clients",
                7L,
                null,
                "ACTIVE");
        String cursor = cursorCodec.encode(clientContext, 200L, 123L);

        assertTrue(cursor.matches("[A-Za-z0-9._~-]{16,512}"));
        assertNotEquals(cursor, cursorCodec.encode(clientContext, 200L, 123L));
        assertEquals(new BoardPortalReadCursor.Position(200L, 123L),
                cursorCodec.decode(clientContext, cursor));
        assertNotEquals("djE6Y2xpZW50OjEyMw", cursor);
        assertThrows(IllegalArgumentException.class,
                () -> cursorCodec.decode(new BoardPortalReadCursor.Context(
                        BoardPortalReadCursor.Kind.FAMILY,
                        "/business/independent-board/oauth/families",
                        7L,
                        11L,
                        "ACTIVE"), cursor));
        assertThrows(IllegalArgumentException.class,
                () -> cursorCodec.decode(new BoardPortalReadCursor.Context(
                        BoardPortalReadCursor.Kind.CLIENT,
                        "/business/independent-board/oauth/clients",
                        8L,
                        null,
                        "ACTIVE"), cursor));
        assertThrows(IllegalArgumentException.class,
                () -> cursorCodec.decode(new BoardPortalReadCursor.Context(
                        BoardPortalReadCursor.Kind.CLIENT,
                        "/business/independent-board/oauth/clients",
                        7L,
                        null,
                        "EXPIRED"), cursor));
        assertThrows(IllegalArgumentException.class,
                () -> cursorCodec.decode(clientContext, cursor + "A"));
        assertThrows(IllegalArgumentException.class,
                () -> cursorCodec.decode(clientContext, "short"));
        assertThrows(IllegalArgumentException.class,
                () -> cursorCodec.encode(clientContext, 0L, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> cursorCodec.encode(clientContext, 100L, 101L));

        BoardPortalReadCursor expiredCodec = new BoardPortalReadCursor(
                signingKey,
                Clock.fixed(now.plus(Duration.ofMinutes(15)), ZoneOffset.UTC),
                Duration.ofMinutes(15));
        assertThrows(IllegalArgumentException.class,
                () -> expiredCodec.decode(clientContext, cursor));
    }

    @Test
    void tenantCursorKindIsIndependentFromOAuthPages() {
        assertEquals("TENANT", BoardPortalReadCursor.Kind.valueOf("TENANT").name());
    }

    private static void assertComponents(Class<?> recordType, String... expected) {
        assertEquals(Arrays.asList(expected), Arrays.stream(recordType.getRecordComponents())
                .map(RecordComponent::getName)
                .toList());
    }
}
