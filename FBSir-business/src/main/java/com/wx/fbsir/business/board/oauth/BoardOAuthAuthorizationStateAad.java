package com.wx.fbsir.business.board.oauth;

import com.wx.fbsir.business.board.oauth.domain.BoardOAuthAuthorizationRequest;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;

/**
 * Builds the reproducible SHA-256 AAD digest for encrypted OAuth state.
 *
 * <p>Only immutable authorization-request fields are included. Database ids,
 * lifecycle status and later identity fields are deliberately excluded so the
 * same digest can be reconstructed after approval.</p>
 */
public final class BoardOAuthAuthorizationStateAad {
    public static final String AAD_INVALID = "OAUTH_AUTHORIZATION_STATE_AAD_INVALID";
    public static final String AAD_GENERATION_FAILED =
            "OAUTH_AUTHORIZATION_STATE_AAD_GENERATION_FAILED";

    private static final String FORMAT = "FBSIR:OAUTH:AUTHORIZATION_STATE_AAD:v1";

    private BoardOAuthAuthorizationStateAad() {
    }

    public static byte[] digest(BoardOAuthAuthorizationRequest request) {
        if (request == null) {
            throw BoardOAuthProtocolException.serverError(AAD_INVALID);
        }
        byte[] encoded = null;
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                DataOutputStream output = new DataOutputStream(buffer)) {
            writeAscii(output, FORMAT);
            writeDigest(output, request.getRequestHandleDigest());
            writeAscii(output, request.getClientId());
            writeAscii(output, request.getRedirectUri());
            writeAscii(output, request.getCodeChallenge());
            writeAscii(output, request.getCodeChallengeMethod());
            writeDigest(output, request.getStateDigest());
            writeAscii(output, request.getIssuerUri());
            writeAscii(output, request.getResourceUri());
            writeAscii(output, request.getProductCode());
            writeAscii(output, request.getSourceCode());
            writeAscii(output, request.getConnectorCode());
            writeAscii(output, request.getScopeCanonical());
            writeDigest(output, request.getScopeDigest());
            writeDate(output, request.getRequestedAt());
            writeDate(output, request.getExpiresAt());
            output.flush();
            encoded = buffer.toByteArray();
            return BoardOAuthCrypto.sha256(encoded);
        } catch (BoardOAuthProtocolException known) {
            throw known;
        } catch (IOException | RuntimeException failure) {
            throw BoardOAuthProtocolException.serverError(AAD_GENERATION_FAILED);
        } finally {
            if (encoded != null) {
                Arrays.fill(encoded, (byte) 0);
            }
        }
    }

    private static void writeAscii(DataOutputStream output, String value) throws IOException {
        if (value == null || value.isEmpty()) {
            throw BoardOAuthProtocolException.serverError(AAD_INVALID);
        }
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) > 0x7f) {
                throw BoardOAuthProtocolException.serverError(AAD_INVALID);
            }
        }
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static void writeDigest(DataOutputStream output, byte[] value) throws IOException {
        if (value == null || value.length != BoardOAuthCrypto.SHA256_BYTES) {
            throw BoardOAuthProtocolException.serverError(AAD_INVALID);
        }
        output.write(value);
    }

    private static void writeDate(DataOutputStream output, Date value) throws IOException {
        if (value == null) {
            throw BoardOAuthProtocolException.serverError(AAD_INVALID);
        }
        output.writeLong(value.getTime());
    }
}
