package com.wx.fbsir.business.board.oauth;

import java.util.HexFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoardOAuthPrincipalSubjectTest {
    @Test
    void versionedBinaryFramingHasAStableKnownVector() {
        byte[] first = BoardOAuthPrincipalSubject.digest(101L, 202L, 303L);
        byte[] second = BoardOAuthPrincipalSubject.digest(101L, 202L, 303L);

        assertArrayEquals(first, second);
        assertEquals(BoardOAuthCrypto.SHA256_BYTES, first.length);
        assertEquals("e8bd785504f84a9edd317a08b4d5d697abf60d55c08497d4220ccd6467dfc9a9",
                HexFormat.of().formatHex(first));
        assertFalse(BoardOAuthCrypto.constantTimeEquals(
                first, BoardOAuthPrincipalSubject.digest(101L, 202L, 304L)));
    }

    @Test
    void nonPositiveIdentityFailsClosedWithAStableReason() {
        BoardOAuthProtocolException failure = assertThrows(
                BoardOAuthProtocolException.class,
                () -> BoardOAuthPrincipalSubject.digest(101L, 0L, 303L));

        assertEquals("server_error", failure.oauthError());
        assertEquals(BoardOAuthPrincipalSubject.SUBJECT_INVALID, failure.reasonCode());
    }
}
