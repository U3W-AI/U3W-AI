package com.wx.fbsir.business.board.portal;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Short-lived, authenticated-encrypted keyset cursor for independent-board reads.
 *
 * <p>The cursor is only a paging position. Every database read must still
 * repeat the full authorization, tenant and fixed-profile predicates.</p>
 */
public final class BoardPortalReadCursor {

    private static final Pattern CURSOR_PATTERN =
            Pattern.compile("[A-Za-z0-9._~-]{16,512}");
    private static final Pattern PATH_PATTERN =
            Pattern.compile("/[A-Za-z0-9/_-]{1,191}");
    private static final Pattern FILTER_PATTERN =
            Pattern.compile("[A-Z0-9_=-]{1,64}");
    private static final Base64.Encoder BASE64_URL_ENCODER =
            Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final long MAX_TTL_SECONDS = Duration.ofHours(1).toSeconds();

    private final SecretKeySpec encryptionKey;
    private final Clock clock;
    private final long ttlSeconds;
    private final SecureRandom secureRandom;

    public BoardPortalReadCursor(byte[] encryptionKey, Clock clock, Duration ttl) {
        Objects.requireNonNull(encryptionKey, "encryptionKey");
        if (encryptionKey.length != 32) {
            throw new IllegalArgumentException("cursor encryption key must contain exactly 32 bytes");
        }
        this.clock = Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(ttl, "ttl");
        this.ttlSeconds = ttl.toSeconds();
        if (ttlSeconds < 1 || ttlSeconds > MAX_TTL_SECONDS) {
            throw new IllegalArgumentException("cursor ttl must be between 1 second and 1 hour");
        }
        this.encryptionKey = new SecretKeySpec(encryptionKey.clone(), "AES");
        this.secureRandom = new SecureRandom();
    }

    public String encode(Context context, long highWaterRowId, long lastReturnedRowId) {
        Objects.requireNonNull(context, "context");
        if (highWaterRowId <= 0 || lastReturnedRowId <= 0
                || lastReturnedRowId > highWaterRowId) {
            throw new IllegalArgumentException("invalid cursor high-water or last row id");
        }
        long issuedAt = clock.instant().getEpochSecond();
        long expiresAt = Math.addExact(issuedAt, ttlSeconds);
        byte[] payload = String.join(":",
                "v1",
                context.kind().wireValue,
                Long.toString(highWaterRowId),
                Long.toString(lastReturnedRowId),
                Long.toString(issuedAt),
                Long.toString(expiresAt)).getBytes(StandardCharsets.US_ASCII);
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        byte[] ciphertext = encrypt(payload, nonce, context);
        byte[] encoded = Arrays.copyOf(nonce, nonce.length + ciphertext.length);
        System.arraycopy(ciphertext, 0, encoded, nonce.length, ciphertext.length);
        String cursor = BASE64_URL_ENCODER.encodeToString(encoded);
        if (!CURSOR_PATTERN.matcher(cursor).matches()) {
            throw new IllegalStateException("encoded cursor violates the public cursor contract");
        }
        return cursor;
    }

    public Position decode(Context expectedContext, String cursor) {
        Objects.requireNonNull(expectedContext, "expectedContext");
        if (cursor == null || !CURSOR_PATTERN.matcher(cursor).matches()) {
            throw invalidCursor();
        }
        byte[] encoded;
        try {
            encoded = BASE64_URL_DECODER.decode(cursor);
        } catch (IllegalArgumentException exception) {
            throw invalidCursor();
        }
        if (!cursor.equals(BASE64_URL_ENCODER.encodeToString(encoded))
                || encoded.length <= NONCE_BYTES + GCM_TAG_BITS / Byte.SIZE) {
            throw invalidCursor();
        }
        byte[] nonce = Arrays.copyOfRange(encoded, 0, NONCE_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(encoded, NONCE_BYTES, encoded.length);
        byte[] payloadBytes = decrypt(ciphertext, nonce, expectedContext);

        String[] fields = new String(payloadBytes, StandardCharsets.US_ASCII).split(":", -1);
        if (fields.length != 6 || !"v1".equals(fields[0])) {
            throw invalidCursor();
        }
        Kind encodedKind = Kind.fromWireValue(fields[1]);
        long highWaterRowId = parsePositiveLong(fields[2]);
        long lastReturnedRowId = parsePositiveLong(fields[3]);
        long issuedAt = parseLong(fields[4]);
        long expiresAt = parseLong(fields[5]);
        long now = clock.instant().getEpochSecond();
        if (encodedKind != expectedContext.kind()
                || lastReturnedRowId > highWaterRowId
                || issuedAt > now
                || expiresAt <= now
                || expiresAt <= issuedAt
                || expiresAt - issuedAt > MAX_TTL_SECONDS) {
            throw invalidCursor();
        }
        return new Position(highWaterRowId, lastReturnedRowId);
    }

    private byte[] encrypt(byte[] payload, byte[] nonce, Context context) {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey,
                    new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(context.canonicalBytes());
            return cipher.doFinal(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("AES-GCM is unavailable", exception);
        }
    }

    private byte[] decrypt(byte[] ciphertext, byte[] nonce, Context context) {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey,
                    new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(context.canonicalBytes());
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException exception) {
            throw invalidCursor();
        }
    }

    private static long parsePositiveLong(String value) {
        long parsed = parseLong(value);
        if (parsed <= 0) {
            throw invalidCursor();
        }
        return parsed;
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw invalidCursor();
        }
    }

    private static IllegalArgumentException invalidCursor() {
        return new IllegalArgumentException("invalid portal read cursor");
    }

    public enum Kind {
        CLIENT("client"),
        FAMILY("family"),
        BINDING("binding"),
        TENANT("tenant");

        private final String wireValue;

        Kind(String wireValue) {
            this.wireValue = wireValue;
        }

        private static Kind fromWireValue(String value) {
            for (Kind kind : values()) {
                if (kind.wireValue.equals(value)) {
                    return kind;
                }
            }
            throw invalidCursor();
        }
    }

    public record Position(long highWaterRowId, long lastReturnedRowId) {

        public Position {
            if (highWaterRowId <= 0 || lastReturnedRowId <= 0
                    || lastReturnedRowId > highWaterRowId) {
                throw new IllegalArgumentException("invalid cursor position");
            }
        }
    }

    public record Context(
            Kind kind,
            String canonicalPath,
            long principalId,
            Long tenantId,
            String normalizedFilter) {

        public Context {
            Objects.requireNonNull(kind, "kind");
            if (canonicalPath == null || !PATH_PATTERN.matcher(canonicalPath).matches()) {
                throw new IllegalArgumentException("invalid cursor path context");
            }
            if (principalId <= 0 || (tenantId != null && tenantId <= 0)) {
                throw new IllegalArgumentException("invalid cursor principal or tenant context");
            }
            if (normalizedFilter == null
                    || !FILTER_PATTERN.matcher(normalizedFilter).matches()) {
                throw new IllegalArgumentException("invalid cursor filter context");
            }
        }

        private byte[] canonicalBytes() {
            return String.join("\n",
                    kind.wireValue,
                    canonicalPath,
                    Long.toString(principalId),
                    tenantId == null ? "-" : tenantId.toString(),
                    normalizedFilter).getBytes(StandardCharsets.UTF_8);
        }
    }
}
