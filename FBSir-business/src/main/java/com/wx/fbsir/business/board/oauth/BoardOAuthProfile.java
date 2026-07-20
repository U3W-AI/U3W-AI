package com.wx.fbsir.business.board.oauth;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Immutable protocol constants and fail-closed shape checks for the first
 * independent-board OAuth/MCP profile.
 *
 * <p>This class is deliberately independent from HTTP and Spring Security. It
 * does not publish an endpoint or make an authorization decision.</p>
 */
public final class BoardOAuthProfile {
    public static final String ISSUER = "https://api2.u3w.com";
    public static final String RESOURCE = "https://api2.u3w.com/fbs-mcp/mcp";
    public static final String PKCE_METHOD = "S256";

    public static final List<String> REQUIRED_SCOPES = List.of(
            "identity.read",
            "entitlement.read",
            "board.meeting.reserve",
            "board.receipt.write");

    public static final String CANONICAL_SCOPE = String.join(" ", REQUIRED_SCOPES);

    private static final String API_HOST = "api2.u3w.com";
    private static final Set<String> REQUIRED_SCOPE_SET = Set.copyOf(REQUIRED_SCOPES);
    private static final Pattern LOOPBACK_REDIRECT = Pattern.compile(
            "\\Ahttp://127\\.0\\.0\\.1:([1-9][0-9]{3,4})/oauth/callback\\z");
    private static final int MIN_LOOPBACK_PORT = 1024;
    private static final int MAX_LOOPBACK_PORT = 65535;
    private static final int MAX_REDIRECT_LENGTH = 80;

    private BoardOAuthProfile() {
    }

    /**
     * Matches the fixed issuer. RFC 3986 case normalization is allowed only
     * for scheme and host; all other URI components remain exact.
     */
    public static boolean isExpectedIssuer(String candidate) {
        return matchesFixedHttpsUri(candidate, "");
    }

    /**
     * Matches the sole protected resource. RFC 3986 case normalization is
     * allowed only for scheme and host; path aliases are rejected.
     */
    public static boolean isExpectedResource(String candidate) {
        return matchesFixedHttpsUri(candidate, "/fbs-mcp/mcp");
    }

    /**
     * Requires exactly the four first-release scopes. Ordering is not
     * significant, while duplicates, case variants, missing and extra values
     * are rejected.
     */
    public static boolean hasExactScopeSet(Collection<String> candidateScopes) {
        if (candidateScopes == null || candidateScopes.size() != REQUIRED_SCOPES.size()) {
            return false;
        }
        Set<String> distinctScopes = new HashSet<>();
        for (String scope : candidateScopes) {
            if (scope == null || !distinctScopes.add(scope)) {
                return false;
            }
        }
        return REQUIRED_SCOPE_SET.equals(distinctScopes);
    }

    /**
     * Returns the one storage/wire canonical ordering after exact-set
     * validation.
     */
    public static String canonicalScope(Collection<String> candidateScopes) {
        if (!hasExactScopeSet(candidateScopes)) {
            throw new IllegalArgumentException("OAuth scope set does not match the locked profile");
        }
        return CANONICAL_SCOPE;
    }

    /**
     * Accepts only the literal loopback form
     * {@code http://127.0.0.1:{1024..65535}/oauth/callback}.
     */
    public static boolean isAllowedLoopbackRedirect(String redirectUri) {
        return parseLoopbackPort(redirectUri) >= 0;
    }

    /**
     * Validates the exact loopback redirect and returns its dynamic port.
     */
    public static int requireLoopbackPort(String redirectUri) {
        int port = parseLoopbackPort(redirectUri);
        if (port < 0) {
            throw new IllegalArgumentException("OAuth redirect URI does not match the locked loopback profile");
        }
        return port;
    }

    private static boolean matchesFixedHttpsUri(String candidate, String expectedRawPath) {
        if (!isAscii(candidate)) {
            return false;
        }
        try {
            URI uri = new URI(candidate);
            return uri.isAbsolute()
                    && !uri.isOpaque()
                    && "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getRawUserInfo() == null
                    && uri.getPort() == -1
                    && API_HOST.equalsIgnoreCase(uri.getRawAuthority())
                    && API_HOST.equalsIgnoreCase(uri.getHost())
                    && expectedRawPath.equals(uri.getRawPath())
                    && uri.getRawQuery() == null
                    && uri.getRawFragment() == null;
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private static int parseLoopbackPort(String redirectUri) {
        if (!isAscii(redirectUri) || redirectUri.length() > MAX_REDIRECT_LENGTH) {
            return -1;
        }
        Matcher matcher = LOOPBACK_REDIRECT.matcher(redirectUri);
        if (!matcher.matches()) {
            return -1;
        }
        int port = Integer.parseInt(matcher.group(1));
        return port >= MIN_LOOPBACK_PORT && port <= MAX_LOOPBACK_PORT ? port : -1;
    }

    private static boolean isAscii(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) > 0x7f) {
                return false;
            }
        }
        return true;
    }
}
