package com.wx.fbsir.business.board.oauth;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class BoardOAuthAuthorizationCodeGeneratorTest {
    @Test
    void generatesExactlyThirtyTwoCsprngBytesAsUnpaddedBase64Url() {
        SecureRandom random = mock(SecureRandom.class);
        doAnswer(invocation -> {
            byte[] target = invocation.getArgument(0);
            for (int index = 0; index < target.length; index++) {
                target[index] = (byte) (255 - index);
            }
            return null;
        }).when(random).nextBytes(any(byte[].class));

        String code = new BoardOAuthAuthorizationCodeGenerator(random).generate();
        byte[] expected = new byte[BoardOAuthCrypto.SECRET_BYTES];
        for (int index = 0; index < expected.length; index++) {
            expected[index] = (byte) (255 - index);
        }

        assertEquals(Base64.getUrlEncoder().withoutPadding().encodeToString(expected), code);
        assertEquals(43, code.length());
        assertTrue(code.matches("[A-Za-z0-9_-]{43}"));
        assertFalse(code.contains("="));
    }

    @Test
    void randomFailureBecomesStableSecretFreeProtocolError() {
        SecureRandom random = mock(SecureRandom.class);
        doThrow(new IllegalStateException("provider-secret"))
                .when(random).nextBytes(any(byte[].class));

        BoardOAuthProtocolException failure = assertThrows(
                BoardOAuthProtocolException.class,
                () -> new BoardOAuthAuthorizationCodeGenerator(random).generate());

        assertEquals("server_error", failure.oauthError());
        assertEquals(500, failure.httpStatus());
        assertEquals(BoardOAuthAuthorizationCodeGenerator.CODE_GENERATION_FAILED,
                failure.reasonCode());
        assertFalse(failure.toString().contains("provider-secret"));
    }
}
