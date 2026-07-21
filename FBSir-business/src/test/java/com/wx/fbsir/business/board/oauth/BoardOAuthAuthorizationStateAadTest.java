package com.wx.fbsir.business.board.oauth;

import com.wx.fbsir.business.board.oauth.domain.BoardOAuthAuthorizationRequest;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoardOAuthAuthorizationStateAadTest {
    private static final Instant REQUESTED_AT = Instant.parse("2026-07-21T02:03:04.567Z");

    @Test
    void digestIsReproducibleAndBindsEveryImmutableRequestContext() {
        BoardOAuthAuthorizationRequest request = request();
        byte[] first = BoardOAuthAuthorizationStateAad.digest(request);
        byte[] second = BoardOAuthAuthorizationStateAad.digest(request());

        assertEquals(BoardOAuthCrypto.SHA256_BYTES, first.length);
        assertArrayEquals(first, second);
        assertEquals("d3929aacef2071aeba2a0b45537b995cd1e13cbc61246c02cf99db56928d8c93",
                HexFormat.of().formatHex(first));

        BoardOAuthAuthorizationRequest changed = request();
        changed.setRedirectUri("http://127.0.0.1:54322/oauth/callback");
        assertFalse(BoardOAuthCrypto.constantTimeEquals(
                first, BoardOAuthAuthorizationStateAad.digest(changed)));

        changed = request();
        changed.setStateDigest(BoardOAuthCrypto.sha256Ascii("different-state-1234"));
        assertFalse(BoardOAuthCrypto.constantTimeEquals(
                first, BoardOAuthAuthorizationStateAad.digest(changed)));

        changed = request();
        changed.setExpiresAt(Date.from(REQUESTED_AT.plusSeconds(301)));
        assertFalse(BoardOAuthCrypto.constantTimeEquals(
                first, BoardOAuthAuthorizationStateAad.digest(changed)));
    }

    @Test
    void mutableLifecycleAndDatabaseFieldsDoNotChangeTheDigest() {
        BoardOAuthAuthorizationRequest request = request();
        byte[] expected = BoardOAuthAuthorizationStateAad.digest(request);

        request.setId(99L);
        request.setStatus("APPROVED");
        request.setTenantId(7L);
        request.setMemberId(11L);
        request.setUserId(42L);
        request.setPrincipalSubjectDigest(new byte[32]);
        request.setConsentIntent(BoardOAuthConsentIntent.EXPLICIT_REAUTHORIZATION);
        request.setApprovedAt(Date.from(REQUESTED_AT.plusSeconds(10)));
        request.setVersion(1L);

        assertArrayEquals(expected, BoardOAuthAuthorizationStateAad.digest(request));
    }

    @Test
    void malformedContextFailsWithStableProtocolReason() {
        BoardOAuthAuthorizationRequest request = request();
        request.setScopeDigest(new byte[31]);

        BoardOAuthProtocolException failure = assertThrows(
                BoardOAuthProtocolException.class,
                () -> BoardOAuthAuthorizationStateAad.digest(request));

        assertEquals("server_error", failure.oauthError());
        assertEquals(500, failure.httpStatus());
        assertEquals(BoardOAuthAuthorizationStateAad.AAD_INVALID, failure.reasonCode());
    }

    private static BoardOAuthAuthorizationRequest request() {
        BoardOAuthAuthorizationRequest request = new BoardOAuthAuthorizationRequest();
        request.setRequestHandleDigest(BoardOAuthCrypto.sha256Ascii("h".repeat(43)));
        request.setClientId("c".repeat(43));
        request.setRedirectUri("http://127.0.0.1:54321/oauth/callback");
        request.setCodeChallenge("A".repeat(43));
        request.setCodeChallengeMethod(BoardOAuthProfile.PKCE_METHOD);
        request.setStateDigest(BoardOAuthCrypto.sha256Ascii("state-0123456789-abcd"));
        request.setIssuerUri(BoardOAuthProfile.ISSUER);
        request.setResourceUri(BoardOAuthProfile.RESOURCE);
        request.setProductCode("FBSIR_INDEPENDENT_BOARD");
        request.setSourceCode("WORKBUDDY");
        request.setConnectorCode("fbs-connector");
        request.setScopeCanonical(BoardOAuthProfile.CANONICAL_SCOPE);
        request.setScopeDigest(BoardOAuthCrypto.sha256Ascii(BoardOAuthProfile.CANONICAL_SCOPE));
        request.setRequestedAt(Date.from(REQUESTED_AT));
        request.setExpiresAt(Date.from(REQUESTED_AT.plusSeconds(300)));
        return request;
    }
}
