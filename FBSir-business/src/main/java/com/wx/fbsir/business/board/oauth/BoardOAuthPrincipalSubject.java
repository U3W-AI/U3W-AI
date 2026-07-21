package com.wx.fbsir.business.board.oauth;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Versioned server-canonical subject digest for one tenant/member/user tuple. */
public final class BoardOAuthPrincipalSubject {
    public static final String SUBJECT_INVALID = "OAUTH_PRINCIPAL_SUBJECT_INVALID";
    public static final String SUBJECT_GENERATION_FAILED =
            "OAUTH_PRINCIPAL_SUBJECT_GENERATION_FAILED";

    private static final String FORMAT = "FBSIR:OAUTH:PRINCIPAL_SUBJECT:v1";

    private BoardOAuthPrincipalSubject() {
    }

    public static byte[] digest(Long tenantId, Long memberId, Long userId) {
        if (!isPositive(tenantId) || !isPositive(memberId) || !isPositive(userId)) {
            throw BoardOAuthProtocolException.serverError(SUBJECT_INVALID);
        }
        byte[] encoded = null;
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                DataOutputStream output = new DataOutputStream(buffer)) {
            byte[] format = FORMAT.getBytes(StandardCharsets.US_ASCII);
            output.writeInt(format.length);
            output.write(format);
            output.writeLong(tenantId);
            output.writeLong(memberId);
            output.writeLong(userId);
            output.flush();
            encoded = buffer.toByteArray();
            return BoardOAuthCrypto.sha256(encoded);
        } catch (IOException | RuntimeException failure) {
            throw BoardOAuthProtocolException.serverError(SUBJECT_GENERATION_FAILED);
        } finally {
            if (encoded != null) {
                Arrays.fill(encoded, (byte) 0);
            }
        }
    }

    private static boolean isPositive(Long value) {
        return value != null && value > 0L;
    }
}
