package com.wx.fbsir.business.board.oauth;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardOAuthProfileTest {
    @Test
    void locksIssuerResourceAndCanonicalScopeValues() {
        assertEquals("https://api2.u3w.com", BoardOAuthProfile.ISSUER);
        assertEquals("https://api2.u3w.com/fbs-mcp/mcp", BoardOAuthProfile.RESOURCE);
        assertEquals("identity.read entitlement.read board.meeting.reserve board.receipt.write",
                BoardOAuthProfile.CANONICAL_SCOPE);
        assertEquals("S256", BoardOAuthProfile.PKCE_METHOD);
        assertThrows(UnsupportedOperationException.class,
                () -> BoardOAuthProfile.REQUIRED_SCOPES.add("unexpected.scope"));
    }

    @Test
    void issuerAndResourceOnlyNormalizeSchemeAndHostCase() {
        assertTrue(BoardOAuthProfile.isExpectedIssuer(BoardOAuthProfile.ISSUER));
        assertTrue(BoardOAuthProfile.isExpectedIssuer("HTTPS://API2.U3W.COM"));
        assertTrue(BoardOAuthProfile.isExpectedResource(BoardOAuthProfile.RESOURCE));
        assertTrue(BoardOAuthProfile.isExpectedResource("HTTPS://API2.U3W.COM/fbs-mcp/mcp"));

        for (String rejected : List.of(
                "http://api2.u3w.com",
                "https://api2.u3w.com/",
                "https://api2.u3w.com:443",
                "https://user@api2.u3w.com",
                "https://api2.u3w.com?issuer=other",
                "https://api2.u3w.com#fragment",
                "https://api2.u3w.com.evil.example")) {
            assertFalse(BoardOAuthProfile.isExpectedIssuer(rejected), rejected);
        }
        assertFalse(BoardOAuthProfile.isExpectedIssuer(null));
        assertFalse(BoardOAuthProfile.isExpectedIssuer("https://api2.u3w.com/发行方"));

        for (String rejected : List.of(
                "http://api2.u3w.com/fbs-mcp/mcp",
                "https://api2.u3w.com:443/fbs-mcp/mcp",
                "https://api2.u3w.com/fbs-mcp/mcp/",
                "https://api2.u3w.com/fbs%2dmcp/mcp",
                "https://api2.u3w.com/fbs-mcp//mcp",
                "https://api2.u3w.com/fbs-mcp/mcp?audience=other",
                "https://api2.u3w.com/fbs-mcp/mcp#fragment")) {
            assertFalse(BoardOAuthProfile.isExpectedResource(rejected), rejected);
        }
        assertFalse(BoardOAuthProfile.isExpectedResource(null));
    }

    @Test
    void exactScopeSetRejectsMissingExtraDuplicateAndAliases() {
        assertTrue(BoardOAuthProfile.hasExactScopeSet(BoardOAuthProfile.REQUIRED_SCOPES));
        assertTrue(BoardOAuthProfile.hasExactScopeSet(List.of(
                "board.receipt.write",
                "identity.read",
                "board.meeting.reserve",
                "entitlement.read")));
        assertEquals(BoardOAuthProfile.CANONICAL_SCOPE,
                BoardOAuthProfile.canonicalScope(List.of(
                        "board.receipt.write",
                        "identity.read",
                        "board.meeting.reserve",
                        "entitlement.read")));

        assertFalse(BoardOAuthProfile.hasExactScopeSet(null));
        assertFalse(BoardOAuthProfile.hasExactScopeSet(List.of()));
        assertFalse(BoardOAuthProfile.hasExactScopeSet(
                BoardOAuthProfile.REQUIRED_SCOPES.subList(0, 3)));
        assertFalse(BoardOAuthProfile.hasExactScopeSet(List.of(
                "identity.read",
                "entitlement.read",
                "board.meeting.reserve",
                "unexpected.scope")));
        assertFalse(BoardOAuthProfile.hasExactScopeSet(List.of(
                "identity.read",
                "entitlement.read",
                "board.meeting.reserve",
                "board.meeting.reserve")));
        assertFalse(BoardOAuthProfile.hasExactScopeSet(List.of(
                "Identity.read",
                "entitlement.read",
                "board.meeting.reserve",
                "board.receipt.write")));
        assertFalse(BoardOAuthProfile.hasExactScopeSet(java.util.Arrays.asList(
                "identity.read", "entitlement.read", "board.meeting.reserve", null)));
        assertThrows(IllegalArgumentException.class,
                () -> BoardOAuthProfile.canonicalScope(List.of("identity.read")));
    }

    @Test
    void loopbackRedirectAcceptsOnlyLiteralIpv4AndBoundedDynamicPort() {
        assertTrue(BoardOAuthProfile.isAllowedLoopbackRedirect(
                "http://127.0.0.1:1024/oauth/callback"));
        assertTrue(BoardOAuthProfile.isAllowedLoopbackRedirect(
                "http://127.0.0.1:49152/oauth/callback"));
        assertTrue(BoardOAuthProfile.isAllowedLoopbackRedirect(
                "http://127.0.0.1:65535/oauth/callback"));
        assertEquals(49152, BoardOAuthProfile.requireLoopbackPort(
                "http://127.0.0.1:49152/oauth/callback"));

        for (String rejected : List.of(
                "http://127.0.0.1:1023/oauth/callback",
                "http://127.0.0.1:65536/oauth/callback",
                "http://127.0.0.1:01024/oauth/callback",
                "http://127.0.0.1/oauth/callback",
                "HTTP://127.0.0.1:49152/oauth/callback",
                "https://127.0.0.1:49152/oauth/callback",
                "http://localhost:49152/oauth/callback",
                "http://[::1]:49152/oauth/callback",
                "http://127.0.0.2:49152/oauth/callback",
                "http://user@127.0.0.1:49152/oauth/callback",
                "http://127.0.0.1:49152/oauth/callback/",
                "http://127.0.0.1:49152/oauth%2fcallback",
                "http://127.0.0.1:49152/oauth/callback?code=x",
                "http://127.0.0.1:49152/oauth/callback#fragment",
                "http://127.0.0.1:49152/oauth/callback\r\nX: y")) {
            assertFalse(BoardOAuthProfile.isAllowedLoopbackRedirect(rejected), rejected);
        }
        assertFalse(BoardOAuthProfile.isAllowedLoopbackRedirect(null));
        assertThrows(IllegalArgumentException.class,
                () -> BoardOAuthProfile.requireLoopbackPort(
                        "http://localhost:49152/oauth/callback"));
    }
}
