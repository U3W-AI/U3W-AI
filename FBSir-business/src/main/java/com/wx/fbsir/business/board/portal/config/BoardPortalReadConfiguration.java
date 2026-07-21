package com.wx.fbsir.business.board.portal.config;

import com.wx.fbsir.business.board.mapper.IndependentBoardMapper;
import com.wx.fbsir.business.board.portal.BoardPortalDigestRef;
import com.wx.fbsir.business.board.portal.BoardPortalReadCursor;
import com.wx.fbsir.business.board.portal.IndependentBoardPortalReadService;
import com.wx.fbsir.business.board.portal.mapper.IndependentBoardPortalReadMapper;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "fbsir.independent-board.portal-candidate",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
public class BoardPortalReadConfiguration {

    private static final Pattern BASE64_URL_256 = Pattern.compile("[A-Za-z0-9_-]{43}");

    @Bean
    IndependentBoardPortalReadService independentBoardPortalReadService(
            IndependentBoardPortalReadMapper portalMapper,
            IndependentBoardMapper boardMapper,
            @Value("${fbsir.independent-board.portal-candidate.cursor-key-base64:}")
            String cursorKeyBase64,
            @Value("${fbsir.independent-board.portal-candidate.reference-key-base64:}")
            String referenceKeyBase64,
            @Value("${fbsir.independent-board.portal-candidate.cursor-ttl:PT15M}")
            String cursorTtlValue) {
        byte[] cursorKey = decodeKey(cursorKeyBase64, "cursor");
        byte[] referenceKey = decodeKey(referenceKeyBase64, "reference");
        if (Arrays.equals(cursorKey, referenceKey)) {
            throw new IllegalStateException("portal cursor and reference keys must be independent");
        }
        Duration cursorTtl = parseTtl(cursorTtlValue);
        Clock clock = Clock.systemUTC();
        return new IndependentBoardPortalReadService(
                portalMapper,
                boardMapper,
                new BoardPortalReadCursor(cursorKey, clock, cursorTtl),
                new BoardPortalDigestRef(referenceKey),
                clock);
    }

    private static byte[] decodeKey(String encoded, String purpose) {
        if (encoded == null || !BASE64_URL_256.matcher(encoded).matches()) {
            throw new IllegalStateException(
                    "portal " + purpose + " key must be an unpadded base64url AES-256 key");
        }
        byte[] decoded;
        try {
            decoded = Base64.getUrlDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "portal " + purpose + " key must be valid unpadded base64url", exception);
        }
        if (decoded.length != 32
                || !encoded.equals(Base64.getUrlEncoder().withoutPadding().encodeToString(decoded))) {
            throw new IllegalStateException(
                    "portal " + purpose + " key must decode canonically to 32 bytes");
        }
        return decoded;
    }

    private static Duration parseTtl(String value) {
        try {
            return Duration.parse(value);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "portal cursor ttl must be an ISO-8601 duration", exception);
        }
    }
}
