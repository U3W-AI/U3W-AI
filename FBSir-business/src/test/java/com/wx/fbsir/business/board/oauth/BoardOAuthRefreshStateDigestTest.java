package com.wx.fbsir.business.board.oauth;

import com.wx.fbsir.business.board.oauth.domain.BoardOAuthToken;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthTokenFamily;
import com.wx.fbsir.business.board.oauth.service.BoardOAuthRefreshAuthorityPort;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class BoardOAuthRefreshStateDigestTest {
    private static final Instant NOW = Instant.parse("2026-07-21T04:00:00.123Z");
    private static final String FAMILY_ID = "f".repeat(43);
    private static final String CLIENT_ID = "c".repeat(43);
    private static final String RAW_REFRESH = "s".repeat(43);

    @Test
    void canonicalDigestIgnoresOrderingAndPhaseButBindsStateChanges() {
        BoardOAuthTokenFamily family = family();
        BoardOAuthToken access = token(
                9L, "ACCESS", "legacy-access", "ACTIVE", null);
        BoardOAuthToken refresh = token(
                10L, "REFRESH", RAW_REFRESH, "ACTIVE", null);
        BoardOAuthRefreshAuthorityPort.LockResult orderedAuthority =
                authority(BoardOAuthProfile.REQUIRED_SCOPES);
        List<String> reversedScopes = new ArrayList<>(
                BoardOAuthProfile.REQUIRED_SCOPES);
        java.util.Collections.reverse(reversedScopes);
        BoardOAuthRefreshAuthorityPort.LockResult reversedAuthority =
                authority(reversedScopes);

        byte[] canonical = BoardOAuthRefreshStateDigest.digest(
                "BEFORE", family, List.of(access, refresh),
                orderedAuthority, null);
        byte[] reordered = BoardOAuthRefreshStateDigest.digest(
                "BEFORE", family, List.of(refresh, access),
                reversedAuthority, null);
        byte[] after = BoardOAuthRefreshStateDigest.digest(
                "AFTER", family, List.of(access, refresh),
                orderedAuthority, null);

        assertArrayEquals(canonical, reordered);
        assertArrayEquals(canonical, after);
        assertEquals(BoardOAuthCrypto.SHA256_BYTES, canonical.length);
        assertFalse(new String(canonical, StandardCharsets.ISO_8859_1)
                .contains(RAW_REFRESH));

        BoardOAuthToken used = token(
                10L, "REFRESH", RAW_REFRESH, "USED", NOW);
        byte[] usedState = BoardOAuthRefreshStateDigest.digest(
                "BEFORE", family, List.of(access, used),
                orderedAuthority, null);
        assertFalse(java.util.Arrays.equals(canonical, usedState));
    }

    @Test
    void rejectsAmbiguousRowsAndInvalidPhaseAsReceiptFailures() {
        BoardOAuthToken duplicate = token(
                10L, "ACCESS", "other", "ACTIVE", null);

        assertReceiptInvalid(() -> BoardOAuthRefreshStateDigest.digest(
                "MIDDLE", family(), List.of(duplicate), authority(
                        BoardOAuthProfile.REQUIRED_SCOPES), null));
        assertReceiptInvalid(() -> BoardOAuthRefreshStateDigest.digest(
                "BEFORE", family(), List.of(
                        token(10L, "REFRESH", RAW_REFRESH, "ACTIVE", null),
                        duplicate), authority(
                                BoardOAuthProfile.REQUIRED_SCOPES), null));
    }

    private static BoardOAuthTokenFamily family() {
        BoardOAuthTokenFamily value = new BoardOAuthTokenFamily();
        value.setId(1L);
        value.setFamilyId(FAMILY_ID);
        value.setOriginAuthorizationCodeId(20L);
        value.setClientId(CLIENT_ID);
        value.setTenantId(100L);
        value.setMemberId(200L);
        value.setUserId(300L);
        value.setProductCode("FBSIR_INDEPENDENT_BOARD");
        value.setSourceCode("WORKBUDDY");
        value.setConnectorCode("fbs-connector");
        value.setIssuerUri(BoardOAuthProfile.ISSUER);
        value.setResourceUri(BoardOAuthProfile.RESOURCE);
        value.setScopeCanonical(BoardOAuthProfile.CANONICAL_SCOPE);
        value.setScopeDigest(scopeDigest());
        value.setPrincipalSubjectDigest(principalDigest());
        value.setConsentIntent(BoardOAuthConsentIntent.FIRST_CONNECT);
        value.setBindingId("binding-verified-1");
        value.setBindingVersion(3L);
        value.setStatus("ACTIVE");
        value.setLifecycleSlot("ACTIVE");
        value.setCurrentRefreshGeneration(0L);
        value.setIssuedAt(Date.from(NOW.minusSeconds(1_000)));
        value.setActivatedAt(Date.from(NOW.minusSeconds(900)));
        value.setExpiresAt(Date.from(NOW.plusSeconds(86_400)));
        value.setTerminatedAt(null);
        value.setVersion(5L);
        return value;
    }

    private static BoardOAuthToken token(
            long id,
            String type,
            String raw,
            String status,
            Instant usedAt) {
        BoardOAuthToken value = new BoardOAuthToken();
        value.setId(id);
        value.setTokenDigest(BoardOAuthCrypto.sha256Ascii(raw));
        value.setFamilyId(FAMILY_ID);
        value.setTokenType(type);
        value.setGeneration(0L);
        value.setResourceUri(BoardOAuthProfile.RESOURCE);
        value.setScopeCanonical(BoardOAuthProfile.CANONICAL_SCOPE);
        value.setScopeDigest(scopeDigest());
        value.setStatus(status);
        value.setIssuedAt(Date.from(NOW.minusSeconds(900)));
        value.setUsedAt(usedAt == null ? null : Date.from(usedAt));
        value.setRevokedAt(null);
        value.setExpiresAt(Date.from(NOW.plusSeconds(86_400)));
        value.setVersion("USED".equals(status) ? 1L : 0L);
        return value;
    }

    private static BoardOAuthRefreshAuthorityPort.LockResult authority(
            List<String> scopes) {
        return new BoardOAuthRefreshAuthorityPort.LockResult(
                mock(BoardOAuthRefreshAuthorityPort.Lease.class),
                true, true, true, true, true, true,
                "binding-verified-1", 3L, CLIENT_ID, principalDigest(),
                scopes, NOW, NOW.plusSeconds(7_200));
    }

    private static byte[] scopeDigest() {
        return BoardOAuthCrypto.sha256Ascii(BoardOAuthProfile.CANONICAL_SCOPE);
    }

    private static byte[] principalDigest() {
        return BoardOAuthPrincipalSubject.digest(100L, 200L, 300L);
    }

    private static void assertReceiptInvalid(Runnable invocation) {
        BoardOAuthProtocolException failure = assertThrows(
                BoardOAuthProtocolException.class, invocation::run);
        assertEquals("server_error", failure.oauthError());
        assertEquals(BoardOAuthRefreshReceiptFactory.RECEIPT_INVALID,
                failure.reasonCode());
    }
}
