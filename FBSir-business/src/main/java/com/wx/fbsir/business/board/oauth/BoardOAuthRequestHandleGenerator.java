package com.wx.fbsir.business.board.oauth;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Generates a one-time authorization request handle with 256 bits of CSPRNG entropy. */
@Component
public final class BoardOAuthRequestHandleGenerator {
    public static final String HANDLE_GENERATION_FAILED =
            "OAUTH_AUTHORIZATION_HANDLE_GENERATION_FAILED";

    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private final SecureRandom secureRandom;

    @Autowired
    public BoardOAuthRequestHandleGenerator() {
        this(new SecureRandom());
    }

    BoardOAuthRequestHandleGenerator(SecureRandom secureRandom) {
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    }

    public String generate() {
        byte[] randomBytes = new byte[BoardOAuthCrypto.SECRET_BYTES];
        try {
            secureRandom.nextBytes(randomBytes);
            return BASE64_URL.encodeToString(randomBytes);
        } catch (RuntimeException failure) {
            throw BoardOAuthProtocolException.serverError(HANDLE_GENERATION_FAILED);
        } finally {
            Arrays.fill(randomBytes, (byte) 0);
        }
    }
}
