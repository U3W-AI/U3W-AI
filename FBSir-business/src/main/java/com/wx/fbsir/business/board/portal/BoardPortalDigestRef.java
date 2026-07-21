package com.wx.fbsir.business.board.portal;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Produces record-scoped pseudonymous references without exposing stored digests. */
public final class BoardPortalDigestRef {

    private static final Pattern DOMAIN = Pattern.compile("[a-z0-9-]{1,48}");
    private static final Pattern SCOPE = Pattern.compile("[A-Za-z0-9._:-]{4,256}");
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SecretKeySpec referenceKey;

    public BoardPortalDigestRef(byte[] referenceKey) {
        Objects.requireNonNull(referenceKey, "referenceKey");
        if (referenceKey.length != 32) {
            throw new IllegalArgumentException("digest reference key must contain exactly 32 bytes");
        }
        this.referenceKey = new SecretKeySpec(referenceKey.clone(), HMAC_ALGORITHM);
    }

    public String reference(String domain, String recordScope, byte[] storedDigest) {
        if (domain == null || !DOMAIN.matcher(domain).matches()
                || recordScope == null || !SCOPE.matcher(recordScope).matches()
                || storedDigest == null || storedDigest.length != 32) {
            throw new IllegalArgumentException("invalid digest reference input");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(referenceKey);
            mac.update(domain.getBytes(StandardCharsets.US_ASCII));
            mac.update((byte) 0);
            mac.update(recordScope.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 0);
            return "sha256:" + HexFormat.of().formatHex(mac.doFinal(storedDigest));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }
}
