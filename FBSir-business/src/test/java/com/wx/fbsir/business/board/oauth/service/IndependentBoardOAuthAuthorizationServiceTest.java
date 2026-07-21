package com.wx.fbsir.business.board.oauth.service;

import com.wx.fbsir.business.board.oauth.AesGcmBoardOAuthStateCipher;
import com.wx.fbsir.business.board.mapper.IndependentBoardMapper;
import com.wx.fbsir.business.board.oauth.BoardOAuthAuthorizationCodeGenerator;
import com.wx.fbsir.business.board.oauth.BoardOAuthAuthorizationStateAad;
import com.wx.fbsir.business.board.oauth.BoardOAuthCrypto;
import com.wx.fbsir.business.board.oauth.BoardOAuthProfile;
import com.wx.fbsir.business.board.oauth.BoardOAuthProtocolException;
import com.wx.fbsir.business.board.oauth.BoardOAuthReceiptFactory;
import com.wx.fbsir.business.board.oauth.BoardOAuthRequestHandleGenerator;
import com.wx.fbsir.business.board.oauth.BoardOAuthStateCipher;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthAuthorizationRequest;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthClient;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthAuthorizationStartCommand;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthAuthorizationStartResult;
import com.wx.fbsir.business.board.oauth.mapper.IndependentBoardOAuthMapper;
import com.wx.fbsir.common.exception.ServiceException;
import java.lang.reflect.Modifier;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IndependentBoardOAuthAuthorizationServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-21T03:04:05.678Z");
    private static final String CLIENT_ID = "c".repeat(43);
    private static final String REDIRECT = "http://127.0.0.1:54321/oauth/callback";
    private static final String CHALLENGE = "A".repeat(43);
    private static final String RAW_STATE = "state-0123456789-abcd";

    private IndependentBoardOAuthMapper mapper;
    private IndependentBoardMapper boardMapper;
    private BoardOAuthStateCipher stateCipher;
    private IndependentBoardOAuthAuthorizationService service;

    @BeforeEach
    void setUp() {
        mapper = mock(IndependentBoardOAuthMapper.class);
        boardMapper = mock(IndependentBoardMapper.class);
        stateCipher = mock(BoardOAuthStateCipher.class);
        service = new IndependentBoardOAuthAuthorizationService(
                boardMapper,
                mapper,
                stateCipher,
                new BoardOAuthRequestHandleGenerator(),
                new BoardOAuthAuthorizationCodeGenerator(),
                new BoardOAuthReceiptFactory(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void lockedExactClientCreatesOnlyOneFiveMinutePendingRequest() {
        BoardOAuthClient client = validClient();
        when(mapper.selectClientForUpdate(CLIENT_ID)).thenReturn(client);
        when(stateCipher.encrypt(eq(RAW_STATE), any(byte[].class)))
                .thenReturn(encryptedState());
        when(mapper.insertAuthorizationRequest(any())).thenReturn(1);

        BoardOAuthAuthorizationStartResult result = service.start(validCommand());

        ArgumentCaptor<byte[]> aadCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<BoardOAuthAuthorizationRequest> requestCaptor =
                ArgumentCaptor.forClass(BoardOAuthAuthorizationRequest.class);
        InOrder order = inOrder(mapper, stateCipher);
        order.verify(mapper).selectClientForUpdate(CLIENT_ID);
        order.verify(stateCipher).encrypt(eq(RAW_STATE), aadCaptor.capture());
        order.verify(mapper).insertAuthorizationRequest(requestCaptor.capture());

        BoardOAuthAuthorizationRequest request = requestCaptor.getValue();
        assertEquals(43, result.requestHandle().length());
        assertTrue(result.requestHandle().matches("[A-Za-z0-9_-]{43}"));
        assertArrayEquals(BoardOAuthCrypto.sha256Ascii(result.requestHandle()),
                request.getRequestHandleDigest());
        assertEquals(CLIENT_ID, request.getClientId());
        assertEquals(REDIRECT, request.getRedirectUri());
        assertEquals(CHALLENGE, request.getCodeChallenge());
        assertEquals(BoardOAuthProfile.PKCE_METHOD, request.getCodeChallengeMethod());
        assertArrayEquals(BoardOAuthCrypto.sha256Ascii(RAW_STATE), request.getStateDigest());
        assertEquals("state-key-v1", request.getStateKeyRef());
        assertArrayEquals(new byte[12], request.getStateNonce());
        assertArrayEquals(ciphertext(), request.getStateCiphertext());
        assertEquals(BoardOAuthProfile.ISSUER, request.getIssuerUri());
        assertEquals(BoardOAuthProfile.RESOURCE, request.getResourceUri());
        assertEquals(IndependentBoardOAuthAuthorizationService.PRODUCT_CODE,
                request.getProductCode());
        assertEquals(IndependentBoardOAuthAuthorizationService.SOURCE_CODE,
                request.getSourceCode());
        assertEquals(IndependentBoardOAuthAuthorizationService.CONNECTOR_CODE,
                request.getConnectorCode());
        assertEquals(BoardOAuthProfile.CANONICAL_SCOPE, request.getScopeCanonical());
        assertArrayEquals(BoardOAuthCrypto.sha256Ascii(BoardOAuthProfile.CANONICAL_SCOPE),
                request.getScopeDigest());
        assertNull(request.getTenantId());
        assertNull(request.getMemberId());
        assertNull(request.getUserId());
        assertNull(request.getPrincipalSubjectDigest());
        assertEquals("PENDING", request.getStatus());
        assertEquals(Date.from(NOW), request.getRequestedAt());
        assertEquals(Date.from(NOW.plusSeconds(300)), request.getExpiresAt());
        assertEquals(NOW.plusSeconds(300), result.expiresAt());
        assertNull(request.getApprovedAt());
        assertNull(request.getDeniedAt());
        assertNull(request.getConsumedAt());
        assertEquals(0L, request.getVersion());
        assertArrayEquals(BoardOAuthAuthorizationStateAad.digest(request),
                aadCaptor.getValue());
        verify(mapper, never()).insertReceipt(any());
    }

    @Test
    void commandAndResultToStringNeverExposeHandleOrState() {
        BoardOAuthAuthorizationStartCommand command = validCommand();
        BoardOAuthAuthorizationStartResult result =
                new BoardOAuthAuthorizationStartResult("raw-handle-secret", NOW);

        assertEquals("BoardOAuthAuthorizationStartCommand[REDACTED]", command.toString());
        assertFalse(command.toString().contains(RAW_STATE));
        assertFalse(command.toString().contains(CLIENT_ID));
        assertEquals("BoardOAuthAuthorizationStartResult[REDACTED]", result.toString());
        assertFalse(result.toString().contains("raw-handle-secret"));
    }

    @Test
    void commandDefensivelyCopiesScopeInput() {
        ArrayList<String> scopes = new ArrayList<>(BoardOAuthProfile.REQUIRED_SCOPES);
        BoardOAuthAuthorizationStartCommand command = command(scopes);
        scopes.clear();

        assertEquals(BoardOAuthProfile.REQUIRED_SCOPES, command.scopes());
        assertThrows(UnsupportedOperationException.class,
                () -> command.scopes().add("unexpected"));
    }

    @Test
    void malformedRequestsFailBeforeClientLockOrSecretWork() {
        List<BoardOAuthAuthorizationStartCommand> invalid = Arrays.asList(
                null,
                new BoardOAuthAuthorizationStartCommand(
                        "token", CLIENT_ID, REDIRECT, CHALLENGE, "S256", RAW_STATE,
                        BoardOAuthProfile.RESOURCE, BoardOAuthProfile.REQUIRED_SCOPES),
                new BoardOAuthAuthorizationStartCommand(
                        "code", CLIENT_ID, REDIRECT, CHALLENGE, "plain", RAW_STATE,
                        BoardOAuthProfile.RESOURCE, BoardOAuthProfile.REQUIRED_SCOPES),
                new BoardOAuthAuthorizationStartCommand(
                        "code", CLIENT_ID, REDIRECT, "short", "S256", RAW_STATE,
                        BoardOAuthProfile.RESOURCE, BoardOAuthProfile.REQUIRED_SCOPES),
                new BoardOAuthAuthorizationStartCommand(
                        "code", CLIENT_ID, "http://localhost:54321/oauth/callback",
                        CHALLENGE, "S256", RAW_STATE, BoardOAuthProfile.RESOURCE,
                        BoardOAuthProfile.REQUIRED_SCOPES),
                new BoardOAuthAuthorizationStartCommand(
                        "code", CLIENT_ID, REDIRECT, CHALLENGE, "S256", "short",
                        BoardOAuthProfile.RESOURCE, BoardOAuthProfile.REQUIRED_SCOPES),
                new BoardOAuthAuthorizationStartCommand(
                        "code", CLIENT_ID, REDIRECT, CHALLENGE, "S256",
                        "state-0123456789\n", BoardOAuthProfile.RESOURCE,
                        BoardOAuthProfile.REQUIRED_SCOPES),
                new BoardOAuthAuthorizationStartCommand(
                        "code", CLIENT_ID, REDIRECT, CHALLENGE, "S256", RAW_STATE,
                        BoardOAuthProfile.RESOURCE + "/", BoardOAuthProfile.REQUIRED_SCOPES),
                command(BoardOAuthProfile.REQUIRED_SCOPES.subList(0, 3)),
                command(List.of(
                        "identity.read", "entitlement.read",
                        "board.meeting.reserve", "board.meeting.reserve")));

        for (BoardOAuthAuthorizationStartCommand candidate : invalid) {
            assertFailure(candidate, "invalid_request", 400,
                    IndependentBoardOAuthAuthorizationService.REQUEST_INVALID);
        }
        verify(mapper, never()).selectClientForUpdate(any());
        verify(mapper, never()).insertAuthorizationRequest(any());
        verify(stateCipher, never()).encrypt(any(), any());
    }

    @Test
    void terminalFutureOrExpiredClientFailsClosedWithoutPersistence() {
        assertClientRejected(client -> client.setStatus("REVOKED"),
                IndependentBoardOAuthAuthorizationService.CLIENT_INVALID, 400);
        assertClientRejected(client -> client.setRegisteredAt(Date.from(NOW.plusSeconds(1))),
                IndependentBoardOAuthAuthorizationService.CLIENT_INVALID, 400);
        assertClientRejected(client -> {
            client.setRegisteredAt(Date.from(NOW.minusSeconds(31L * 24 * 60 * 60)));
            client.setExpiresAt(Date.from(NOW));
        }, IndependentBoardOAuthAuthorizationService.CLIENT_INVALID, 400);
        assertClientRejected(client -> {
            Instant expiresAtRequestBoundary = NOW.plusSeconds(300);
            client.setExpiresAt(Date.from(expiresAtRequestBoundary));
            client.setRegisteredAt(Date.from(
                    expiresAtRequestBoundary.minusSeconds(31L * 24 * 60 * 60)));
        }, IndependentBoardOAuthAuthorizationService.CLIENT_INVALID, 400);
    }

    @Test
    void anyLockedClientProfileDriftIsAStableServerFailure() {
        assertClientRejected(client -> client.setIssuerUri(BoardOAuthProfile.ISSUER + "/"),
                IndependentBoardOAuthAuthorizationService.CLIENT_PROFILE_DRIFT, 500);
        assertClientRejected(client -> client.setResourceUri(BoardOAuthProfile.RESOURCE + "/"),
                IndependentBoardOAuthAuthorizationService.CLIENT_PROFILE_DRIFT, 500);
        assertClientRejected(client -> client.setRedirectPort(54322),
                IndependentBoardOAuthAuthorizationService.CLIENT_PROFILE_DRIFT, 500);
        assertClientRejected(client -> client.setScopeDigest(new byte[31]),
                IndependentBoardOAuthAuthorizationService.CLIENT_PROFILE_DRIFT, 500);
        assertClientRejected(client -> client.setGrantTypesCanonical("authorization_code"),
                IndependentBoardOAuthAuthorizationService.CLIENT_PROFILE_DRIFT, 500);
        assertClientRejected(client -> client.setExpiresAt(
                        Date.from(client.getRegisteredAt().toInstant().plusSeconds(
                                31L * 24 * 60 * 60 + 1))),
                IndependentBoardOAuthAuthorizationService.CLIENT_PROFILE_DRIFT, 500);
    }

    @Test
    void missingStateKeyAndCipherFailureCannotCreateARequest() {
        when(mapper.selectClientForUpdate(CLIENT_ID)).thenReturn(validClient());
        when(stateCipher.encrypt(eq(RAW_STATE), any(byte[].class)))
                .thenThrow(new ServiceException(
                        AesGcmBoardOAuthStateCipher.KEY_NOT_CONFIGURED, 503));

        assertFailure(validCommand(), "temporarily_unavailable", 503,
                IndependentBoardOAuthAuthorizationService.STATE_PROTECTION_UNAVAILABLE);
        verify(mapper, never()).insertAuthorizationRequest(any());

        reset(stateCipher);
        when(stateCipher.encrypt(eq(RAW_STATE), any(byte[].class)))
                .thenThrow(new IllegalStateException("raw-state=" + RAW_STATE));
        assertFailure(validCommand(), "server_error", 500,
                IndependentBoardOAuthAuthorizationService.STATE_PROTECTION_FAILED);
        verify(mapper, never()).insertAuthorizationRequest(any());
    }

    @Test
    void insertConflictCountAndDatabaseFailureUseStableErrorsAndNoReceipt() {
        when(mapper.selectClientForUpdate(CLIENT_ID)).thenReturn(validClient());
        when(stateCipher.encrypt(eq(RAW_STATE), any(byte[].class)))
                .thenReturn(encryptedState());
        doThrow(new DuplicateKeyException("constraint included raw values"))
                .when(mapper).insertAuthorizationRequest(any());
        assertFailure(validCommand(), "invalid_request", 409,
                IndependentBoardOAuthAuthorizationService.REQUEST_CONFLICT);

        reset(mapper);
        when(mapper.selectClientForUpdate(CLIENT_ID)).thenReturn(validClient());
        when(mapper.insertAuthorizationRequest(any())).thenReturn(0);
        assertFailure(validCommand(), "server_error", 500,
                IndependentBoardOAuthAuthorizationService.REQUEST_WRITE_FAILED);

        reset(mapper);
        doThrow(new DataAccessResourceFailureException("database down"))
                .when(mapper).selectClientForUpdate(CLIENT_ID);
        assertFailure(validCommand(), "temporarily_unavailable", 503,
                IndependentBoardOAuthAuthorizationService.REQUEST_WRITE_FAILED);
        verify(mapper, never()).insertReceipt(any());
    }

    @Test
    void authorizationCoreIsHiddenAndCannotOwnThePublicationTransaction()
            throws Exception {
        assertFalse(Modifier.isPublic(
                IndependentBoardOAuthAuthorizationService.class.getModifiers()));
        assertNull(IndependentBoardOAuthAuthorizationService.class
                .getDeclaredMethod("start", BoardOAuthAuthorizationStartCommand.class)
                .getAnnotation(Transactional.class));
    }

    private void assertClientRejected(
            Consumer<BoardOAuthClient> mutation,
            String expectedReason,
            int expectedStatus) {
        reset(mapper, stateCipher);
        BoardOAuthClient client = validClient();
        mutation.accept(client);
        when(mapper.selectClientForUpdate(CLIENT_ID)).thenReturn(client);

        assertFailure(validCommand(),
                expectedStatus == 500 ? "server_error" : "invalid_request",
                expectedStatus,
                expectedReason);
        verify(mapper, never()).insertAuthorizationRequest(any());
        verify(stateCipher, never()).encrypt(any(), any());
    }

    private void assertFailure(
            BoardOAuthAuthorizationStartCommand command,
            String expectedOAuthError,
            int expectedStatus,
            String expectedReason) {
        BoardOAuthProtocolException failure = assertThrows(
                BoardOAuthProtocolException.class,
                () -> service.start(command));
        assertEquals(expectedOAuthError, failure.oauthError());
        assertEquals(expectedStatus, failure.httpStatus());
        assertEquals(expectedReason, failure.reasonCode());
        assertEquals(expectedReason, failure.getMessage());
        assertFalse(failure.toString().contains(RAW_STATE));
    }

    private static BoardOAuthAuthorizationStartCommand validCommand() {
        return command(List.of(
                "board.receipt.write",
                "identity.read",
                "board.meeting.reserve",
                "entitlement.read"));
    }

    private static BoardOAuthAuthorizationStartCommand command(List<String> scopes) {
        return new BoardOAuthAuthorizationStartCommand(
                "code",
                CLIENT_ID,
                REDIRECT,
                CHALLENGE,
                BoardOAuthProfile.PKCE_METHOD,
                RAW_STATE,
                BoardOAuthProfile.RESOURCE,
                scopes);
    }

    private static BoardOAuthClient validClient() {
        BoardOAuthClient client = new BoardOAuthClient();
        client.setClientId(CLIENT_ID);
        client.setClientName("未验证的本地公共客户端");
        client.setIssuerUri(BoardOAuthProfile.ISSUER);
        client.setResourceUri(BoardOAuthProfile.RESOURCE);
        client.setProductCode("FBSIR_INDEPENDENT_BOARD");
        client.setSourceCode("WORKBUDDY");
        client.setConnectorCode("fbs-connector");
        client.setRedirectPort(54321);
        client.setRedirectUri(REDIRECT);
        client.setTokenEndpointAuthMethod("none");
        client.setGrantTypesCanonical("authorization_code refresh_token");
        client.setResponseTypesCanonical("code");
        client.setScopeCanonical(BoardOAuthProfile.CANONICAL_SCOPE);
        client.setScopeDigest(BoardOAuthCrypto.sha256Ascii(BoardOAuthProfile.CANONICAL_SCOPE));
        client.setMetadataDigest(new byte[32]);
        client.setRegistrationSourceDigest(new byte[32]);
        client.setStatus("ACTIVE");
        Instant registeredAt = NOW.minusSeconds(24 * 60 * 60);
        client.setRegisteredAt(Date.from(registeredAt));
        client.setExpiresAt(Date.from(registeredAt.plusSeconds(31L * 24 * 60 * 60)));
        client.setTerminatedAt(null);
        client.setVersion(0L);
        return client;
    }

    private static BoardOAuthStateCipher.EncryptedState encryptedState() {
        return new BoardOAuthStateCipher.EncryptedState(
                "state-key-v1", new byte[12], ciphertext());
    }

    private static byte[] ciphertext() {
        byte[] ciphertext = new byte[32];
        Arrays.fill(ciphertext, (byte) 0x55);
        return ciphertext;
    }
}
