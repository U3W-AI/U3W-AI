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

class BoardOAuthRequestHandleGeneratorTest {
    @Test
    void generatesExactlyThirtyTwoCsprngBytesAsUnpaddedBase64Url() {
        SecureRandom random = mock(SecureRandom.class);
        doAnswer(invocation -> {
            byte[] target = invocation.getArgument(0);
            for (int index = 0; index < target.length; index++) {
                target[index] = (byte) index;
            }
            return null;
        }).when(random).nextBytes(any(byte[].class));

        String handle = new BoardOAuthRequestHandleGenerator(random).generate();
        byte[] expectedBytes = new byte[BoardOAuthCrypto.SECRET_BYTES];
        for (int index = 0; index < expectedBytes.length; index++) {
            expectedBytes[index] = (byte) index;
        }

        assertEquals(Base64.getUrlEncoder().withoutPadding().encodeToString(expectedBytes), handle);
        assertEquals(43, handle.length());
        assertTrue(handle.matches("[A-Za-z0-9_-]{43}"));
        assertFalse(handle.contains("="));
    }

    @Test
    void randomFailureBecomesStableSecretFreeProtocolError() {
        SecureRandom random = mock(SecureRandom.class);
        doThrow(new IllegalStateException("provider included a secret"))
                .when(random).nextBytes(any(byte[].class));

        BoardOAuthProtocolException failure = assertThrows(
                BoardOAuthProtocolException.class,
                () -> new BoardOAuthRequestHandleGenerator(random).generate());

        assertEquals("server_error", failure.oauthError());
        assertEquals(500, failure.httpStatus());
        assertEquals(BoardOAuthRequestHandleGenerator.HANDLE_GENERATION_FAILED,
                failure.reasonCode());
        assertFalse(failure.toString().contains("provider included a secret"));
    }
}
