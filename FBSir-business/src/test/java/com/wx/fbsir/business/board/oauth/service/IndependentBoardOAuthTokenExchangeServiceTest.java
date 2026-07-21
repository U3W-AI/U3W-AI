package com.wx.fbsir.business.board.oauth.service;

import com.wx.fbsir.business.board.oauth.BoardOAuthConsentIntent;
import com.wx.fbsir.business.board.oauth.BoardOAuthCrypto;
import com.wx.fbsir.business.board.oauth.BoardOAuthProfile;
import com.wx.fbsir.business.board.oauth.BoardOAuthPrincipalSubject;
import com.wx.fbsir.business.board.oauth.BoardOAuthProtocolException;
import com.wx.fbsir.business.board.oauth.BoardOAuthTokenFamilyCreatedReceiptFactory;
import com.wx.fbsir.business.board.oauth.BoardOAuthTokenMaterialGenerator;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthAuthorizationCode;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthAuthorizationRequest;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthClient;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthReceipt;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthToken;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthTokenFamily;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthTokenExchangeCommand;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthTokenExchangeResult;
import com.wx.fbsir.business.board.oauth.mapper.IndependentBoardOAuthMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IndependentBoardOAuthTokenExchangeServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-21T00:00:00.123Z");
    private static final String RAW_CODE = "c".repeat(43);
    private static final String VERIFIER = "v".repeat(43);
    private static final String CLIENT_ID = "b".repeat(43);
    private static final String REDIRECT =
            "http://127.0.0.1:54321/oauth/callback";
    private static final String RESOURCE = BoardOAuthProfile.RESOURCE;
    private static final String FAMILY_ID = "f".repeat(43);
    private static final String ACCESS_TOKEN = "a".repeat(43);
    private static final String REFRESH_TOKEN = "r".repeat(43);
    private static final String RECEIPT_ID = "i".repeat(43);
    private static final String CORRELATION_ID = "o".repeat(43);

    private IndependentBoardOAuthMapper mapper;
    private BoardOAuthTokenExchangeAuthorityPort authorityPort;
    private BoardOAuthTokenMaterialGenerator generator;
    private IndependentBoardOAuthTokenExchangeService service;
    private BoardOAuthClient client;
    private IndependentBoardOAuthMapper.TokenExchangeLockLocator locator;
    private BoardOAuthAuthorizationRequest approvedRequest;
    private BoardOAuthAuthorizationRequest consumedRequest;
    private BoardOAuthAuthorizationCode activeCode;
    private BoardOAuthAuthorizationCode usedCode;
    private BoardOAuthTokenFamily persistedFamily;
    private List<BoardOAuthToken> persistedTokens;
    private AtomicReference<BoardOAuthReceipt> persistedReceipt;

    @BeforeEach
    void setUp() {
        mapper = mock(IndependentBoardOAuthMapper.class);
        authorityPort = mock(BoardOAuthTokenExchangeAuthorityPort.class);
        generator = mock(BoardOAuthTokenMaterialGenerator.class);
        service = new IndependentBoardOAuthTokenExchangeService(
                mapper,
                authorityPort,
                generator,
                Clock.fixed(NOW, ZoneOffset.UTC));

        client = activeClient();
        approvedRequest = approvedRequest();
        consumedRequest = consumedRequest();
        activeCode = activeCode();
        usedCode = usedCode();
        persistedFamily = persistedFamily();
        persistedTokens = List.of(
                persistedToken(2L, TOKEN_ACCESS(), ACCESS_TOKEN,
                        NOW.plusSeconds(600)),
                persistedToken(3L, TOKEN_REFRESH(), REFRESH_TOKEN,
                        client.getExpiresAt().toInstant()));
        persistedReceipt = new AtomicReference<>();

        locator = new IndependentBoardOAuthMapper.TokenExchangeLockLocator();
        locator.setCodeId(20L);
        locator.setAuthorizationRequestId(10L);
        locator.setTenantId(100L);
        locator.setMemberId(200L);
        locator.setUserId(300L);
        locator.setProductCode("FBSIR_INDEPENDENT_BOARD");
        locator.setSourceCode("WORKBUDDY");
        locator.setConnectorCode("fbs-connector");
        locator.setClientId(CLIENT_ID);
        when(mapper.selectTokenExchangeLockLocatorByDigest(any(byte[].class)))
                .thenReturn(locator);
        when(authorityPort.lockForTokenExchange(
                100L, 200L, 300L, "FBSIR_INDEPENDENT_BOARD", "WORKBUDDY",
                "fbs-connector", NOW)).thenReturn(currentAbsentAuthority());
        when(mapper.selectClientsForUpdate(List.of(CLIENT_ID)))
                .thenReturn(List.of(client));
        when(mapper.selectAuthorizationRequestByIdAndClientForUpdate(10L, CLIENT_ID))
                .thenReturn(approvedRequest, consumedRequest);
        when(mapper.selectAuthorizationCodeForUpdate(
                eq(20L), eq(CLIENT_ID), any(byte[].class)))
                .thenReturn(activeCode, usedCode);
        when(mapper.selectTokenFamilyByOriginAuthorizationCodeForUpdate(20L, CLIENT_ID))
                .thenReturn(null);
        when(mapper.consumeAuthorizationCodeForClientIfVersion(
                20L, CLIENT_ID, 0L, Date.from(NOW))).thenReturn(1);
        when(mapper.consumeAuthorizationRequestIfVersion(approvedRequest, 1L))
                .thenReturn(1);

        when(generator.generateFamilyId()).thenReturn(FAMILY_ID);
        when(generator.generateAccessToken()).thenReturn(ACCESS_TOKEN);
        when(generator.generateRefreshToken()).thenReturn(REFRESH_TOKEN);
        when(generator.generateReceiptId()).thenReturn(RECEIPT_ID);
        when(generator.generateCorrelationId()).thenReturn(CORRELATION_ID);

        when(mapper.insertTokenFamily(any())).thenAnswer(invocation -> {
            BoardOAuthTokenFamily value = invocation.getArgument(0);
            value.setId(1L);
            return 1;
        });
        when(mapper.insertToken(any())).thenAnswer(invocation -> {
            BoardOAuthToken value = invocation.getArgument(0);
            value.setId("ACCESS".equals(value.getTokenType()) ? 2L : 3L);
            return 1;
        });
        when(mapper.selectTokenFamilyForUpdate(FAMILY_ID, CLIENT_ID))
                .thenReturn(persistedFamily);
        when(mapper.selectFamilyTokensForUpdate(FAMILY_ID))
                .thenReturn(persistedTokens);
        when(mapper.selectTokenFamilyCreatedReceiptCandidatesForUpdate(
                FAMILY_ID, CLIENT_ID, 20L)).thenReturn(List.of());
        when(mapper.insertReceipt(any())).thenAnswer(invocation -> {
            BoardOAuthReceipt value = invocation.getArgument(0);
            value.setId(4L);
            persistedReceipt.set(value);
            return 1;
        });
        when(mapper.selectReceiptByReceiptId(RECEIPT_ID))
                .thenAnswer(invocation -> persistedReceipt.get());
    }

    @Test
    void exchangesOnceWithCanonicalLocksCasDigestsAndValidatedReceipt() {
        assertConsumedRequestFixture();
        BoardOAuthTokenExchangeResult result = exchange(service, command());

        assertEquals(ACCESS_TOKEN, result.rawAccessToken());
        assertEquals(REFRESH_TOKEN, result.rawRefreshToken());
        assertEquals("Bearer", result.tokenType());
        assertEquals(600L, result.expiresInSeconds());
        assertEquals(BoardOAuthProfile.CANONICAL_SCOPE, result.scopeCanonical());
        assertEquals(NOW.plusSeconds(600), result.accessTokenExpiresAt());
        assertEquals(client.getExpiresAt().toInstant(),
                result.refreshTokenExpiresAt());
        assertEquals(RECEIPT_ID, result.receiptId());
        assertEquals(CORRELATION_ID, result.correlationId());

        InOrder order = inOrder(mapper, authorityPort);
        order.verify(mapper).selectTokenExchangeLockLocatorByDigest(any(byte[].class));
        order.verify(authorityPort).lockForTokenExchange(
                100L, 200L, 300L, "FBSIR_INDEPENDENT_BOARD", "WORKBUDDY",
                "fbs-connector", NOW);
        order.verify(mapper).selectClientsForUpdate(List.of(CLIENT_ID));
        order.verify(mapper).selectAuthorizationRequestByIdAndClientForUpdate(
                10L, CLIENT_ID);
        order.verify(mapper).selectAuthorizationCodeForUpdate(
                eq(20L), eq(CLIENT_ID), any(byte[].class));
        order.verify(mapper).selectTokenFamilyByOriginAuthorizationCodeForUpdate(
                20L, CLIENT_ID);
        order.verify(mapper).consumeAuthorizationCodeForClientIfVersion(
                20L, CLIENT_ID, 0L, Date.from(NOW));
        order.verify(mapper).consumeAuthorizationRequestIfVersion(
                approvedRequest, 1L);
        order.verify(mapper).insertTokenFamily(any());
        order.verify(mapper).insertToken(argThat(
                token -> "ACCESS".equals(token.getTokenType())));
        order.verify(mapper).insertToken(argThat(
                token -> "REFRESH".equals(token.getTokenType())));
        order.verify(mapper).selectAuthorizationRequestByIdAndClientForUpdate(
                10L, CLIENT_ID);
        order.verify(mapper).selectAuthorizationCodeForUpdate(
                eq(20L), eq(CLIENT_ID), any(byte[].class));
        order.verify(mapper).selectTokenFamilyForUpdate(FAMILY_ID, CLIENT_ID);
        order.verify(mapper).selectFamilyTokensForUpdate(FAMILY_ID);
        order.verify(mapper).selectTokenFamilyCreatedReceiptCandidatesForUpdate(
                FAMILY_ID, CLIENT_ID, 20L);
        order.verify(mapper).insertReceipt(any());
        order.verify(mapper).selectReceiptByReceiptId(RECEIPT_ID);

        ArgumentCaptor<BoardOAuthTokenFamily> family =
                ArgumentCaptor.forClass(BoardOAuthTokenFamily.class);
        verify(mapper).insertTokenFamily(family.capture());
        assertEquals(BoardOAuthConsentIntent.FIRST_CONNECT,
                family.getValue().getConsentIntent());
        assertEquals("PENDING_BINDING", family.getValue().getStatus());
        assertEquals(0L, family.getValue().getCurrentRefreshGeneration());
        assertNull(family.getValue().getBindingId());

        ArgumentCaptor<BoardOAuthToken> tokens =
                ArgumentCaptor.forClass(BoardOAuthToken.class);
        verify(mapper, org.mockito.Mockito.times(2)).insertToken(tokens.capture());
        assertArrayEquals(BoardOAuthCrypto.sha256Ascii(ACCESS_TOKEN),
                tokens.getAllValues().get(0).getTokenDigest());
        assertArrayEquals(BoardOAuthCrypto.sha256Ascii(REFRESH_TOKEN),
                tokens.getAllValues().get(1).getTokenDigest());
        assertFalse(new String(tokens.getAllValues().get(0).getTokenDigest(),
                StandardCharsets.US_ASCII).contains(ACCESS_TOKEN));
        assertNotNull(persistedReceipt.get());
        BoardOAuthTokenFamilyCreatedReceiptFactory.validate(
                persistedReceipt.get(), consumedRequest, usedCode,
                persistedFamily, persistedTokens);
    }

    @Test
    void rejectsWrongPkceBeforeAnyCas() {
        BoardOAuthTokenExchangeCommand wrong = new BoardOAuthTokenExchangeCommand(
                RAW_CODE, "x".repeat(43), CLIENT_ID, REDIRECT, RESOURCE);

        assertProtocol(() -> exchange(service, wrong), "invalid_grant",
                IndependentBoardOAuthTokenExchangeService.CODE_INVALID);
        verify(mapper, never()).consumeAuthorizationCodeForClientIfVersion(
                anyLong(), any(), anyLong(), any());
        verify(mapper, never()).insertTokenFamily(any());
    }

    @Test
    void rejectsClientAndRedirectMismatchBeforeCas() {
        BoardOAuthTokenExchangeCommand wrongClient = new BoardOAuthTokenExchangeCommand(
                RAW_CODE, VERIFIER, "z".repeat(43), REDIRECT, RESOURCE);
        assertProtocol(() -> exchange(service, wrongClient), "invalid_grant",
                IndependentBoardOAuthTokenExchangeService.CODE_INVALID);
        verify(mapper, never()).selectClientsForUpdate(any());

        setUp();
        BoardOAuthTokenExchangeCommand wrongRedirect = new BoardOAuthTokenExchangeCommand(
                RAW_CODE, VERIFIER, CLIENT_ID,
                "http://127.0.0.1:54322/oauth/callback", RESOURCE);
        assertProtocol(() -> exchange(service, wrongRedirect), "invalid_grant",
                IndependentBoardOAuthTokenExchangeService.CODE_INVALID);
        verify(mapper, never()).consumeAuthorizationCodeForClientIfVersion(
                anyLong(), any(), anyLong(), any());
    }

    @Test
    void mapsAnUnknownOrExpiredClientToInvalidClient() {
        when(mapper.selectClientsForUpdate(List.of(CLIENT_ID))).thenReturn(List.of());

        assertProtocol(() -> exchange(service, command()), "invalid_client",
                IndependentBoardOAuthTokenExchangeService.CLIENT_INVALID);
        verify(mapper, never()).consumeAuthorizationCodeForClientIfVersion(
                anyLong(), any(), anyLong(), any());
    }

    @Test
    void rejectsMissingAliasedOrMismatchedResourceBeforeLocatorAndCas() {
        assertResourceRejected(null);
        assertResourceRejected(RESOURCE + "/");
        assertResourceRejected("https://api.u3w.com/independent-board");
    }

    @Test
    void rejectsExpiredAndReusedCodesBeforeCas() {
        activeCode.setExpiresAt(Date.from(NOW));
        assertProtocol(() -> exchange(service, command()), "invalid_grant",
                IndependentBoardOAuthTokenExchangeService.CODE_INVALID);
        verify(mapper, never()).consumeAuthorizationCodeForClientIfVersion(
                anyLong(), any(), anyLong(), any());

        setUp();
        activeCode.setStatus("USED");
        activeCode.setUsedAt(Date.from(NOW.minusSeconds(1)));
        activeCode.setVersion(1L);
        assertProtocol(() -> exchange(service, command()), "invalid_grant",
                IndependentBoardOAuthTokenExchangeService.CODE_INVALID);
        verify(mapper, never()).consumeAuthorizationCodeForClientIfVersion(
                anyLong(), any(), anyLong(), any());
    }

    @Test
    void rejectsMissingOrDriftedConsentIntent() {
        approvedRequest.setConsentIntent(null);
        assertProtocol(() -> exchange(service, command()),
                IndependentBoardOAuthTokenExchangeService.CONSENT_INTENT_DRIFT);

        setUp();
        activeCode.setConsentIntent(
                BoardOAuthConsentIntent.EXPLICIT_REAUTHORIZATION);
        assertProtocol(() -> exchange(service, command()),
                IndependentBoardOAuthTokenExchangeService.CONSENT_INTENT_DRIFT);
        verify(mapper, never()).insertTokenFamily(any());
    }

    @Test
    void freshIssuanceFailsClosedWhenEnterpriseBecomesInactiveAfterApproval() {
        when(authorityPort.lockForTokenExchange(
                100L, 200L, 300L, "FBSIR_INDEPENDENT_BOARD", "WORKBUDDY",
                "fbs-connector", NOW)).thenReturn(
                        new BoardOAuthTokenExchangeAuthorityPort.LockResult(
                                false,
                                true,
                                true,
                                true,
                                BoardOAuthTokenExchangeAuthorityPort
                                        .BindingTopology.ABSENT,
                                true));

        assertProtocol(() -> exchange(service, command()), "access_denied",
                IndependentBoardOAuthTokenExchangeService.AUTHORITY_NOT_CURRENT);
        verify(mapper, never()).consumeAuthorizationCodeForClientIfVersion(
                anyLong(), any(), anyLong(), any());
        verify(mapper, never()).insertTokenFamily(any());
    }

    @Test
    void stalePendingClientHintFailsBeforeFamilyTokenOrReceiptWork() {
        String hintedClient = "a".repeat(43);
        String actualClient = "z".repeat(43);
        locator.setPendingClientId(hintedClient);
        BoardOAuthClient historical = historicalClient(hintedClient, 2L);
        when(mapper.selectClientsForUpdate(List.of(hintedClient, CLIENT_ID)))
                .thenReturn(List.of(historical, client));
        BoardOAuthTokenFamily pending = new BoardOAuthTokenFamily();
        pending.setClientId(actualClient);
        when(mapper.selectPendingTokenFamilySlotForUpdate(
                100L, 200L, "FBSIR_INDEPENDENT_BOARD", "WORKBUDDY",
                "fbs-connector")).thenReturn(pending);

        assertProtocol(() -> exchange(service, command()),
                IndependentBoardOAuthTokenExchangeService.CONFLICT);
        verify(mapper, never()).selectTokenFamilyByOriginAuthorizationCodeForUpdate(
                anyLong(), any());
        verify(mapper, never()).selectFamilyTokensForUpdate(any());
        verify(mapper, never()).insertReceipt(any());
    }

    @Test
    void staleHintMayProceedWhenActualPendingClientWasAlreadyLocked() {
        String hintedClient = "a".repeat(43);
        locator.setPendingClientId(hintedClient);
        BoardOAuthClient historical = historicalClient(hintedClient, 2L);
        when(mapper.selectClientsForUpdate(List.of(hintedClient, CLIENT_ID)))
                .thenReturn(List.of(historical, client));
        BoardOAuthTokenFamily pending = new BoardOAuthTokenFamily();
        pending.setClientId(CLIENT_ID);
        when(mapper.selectPendingTokenFamilySlotForUpdate(
                100L, 200L, "FBSIR_INDEPENDENT_BOARD", "WORKBUDDY",
                "fbs-connector")).thenReturn(pending);
        when(mapper.selectTokenFamilyByOriginAuthorizationCodeForUpdate(
                20L, CLIENT_ID)).thenReturn(persistedFamily);

        assertProtocol(() -> exchange(service, command()),
                IndependentBoardOAuthTokenExchangeService.FAMILY_EXISTS);
        verify(mapper).selectTokenFamilyByOriginAuthorizationCodeForUpdate(
                20L, CLIENT_ID);
        verify(mapper, never()).selectFamilyTokensForUpdate(any());
    }

    @Test
    void rejectsExistingOriginFamilyBeforeCas() {
        when(mapper.selectTokenFamilyByOriginAuthorizationCodeForUpdate(20L, CLIENT_ID))
                .thenReturn(persistedFamily);

        assertProtocol(() -> exchange(service, command()),
                IndependentBoardOAuthTokenExchangeService.FAMILY_EXISTS);
        verify(mapper, never()).consumeAuthorizationCodeForClientIfVersion(
                anyLong(), any(), anyLong(), any());
    }

    @Test
    void convertsUniqueCollisionAfterCasIntoWholeTransactionConflict() {
        doThrow(new DuplicateKeyException("collision"))
                .when(mapper).insertTokenFamily(any());

        assertProtocol(() -> exchange(service, command()),
                IndependentBoardOAuthTokenExchangeService.CONFLICT);
        verify(mapper).consumeAuthorizationCodeForClientIfVersion(
                20L, CLIENT_ID, 0L, Date.from(NOW));
        verify(mapper).consumeAuthorizationRequestIfVersion(approvedRequest, 1L);
        verify(mapper, never()).insertToken(any());
        verify(mapper, never()).insertReceipt(any());
    }

    @Test
    void casConflictsStopAtTheExactFailedStateTransition() {
        when(mapper.consumeAuthorizationCodeForClientIfVersion(
                20L, CLIENT_ID, 0L, Date.from(NOW))).thenReturn(0);
        assertProtocol(() -> exchange(service, command()),
                IndependentBoardOAuthTokenExchangeService.CONFLICT);
        verify(mapper, never()).consumeAuthorizationRequestIfVersion(any(), anyLong());
        verify(mapper, never()).insertTokenFamily(any());

        setUp();
        when(mapper.consumeAuthorizationRequestIfVersion(approvedRequest, 1L))
                .thenReturn(0);
        assertProtocol(() -> exchange(service, command()),
                IndependentBoardOAuthTokenExchangeService.CONFLICT);
        verify(mapper).consumeAuthorizationCodeForClientIfVersion(
                20L, CLIENT_ID, 0L, Date.from(NOW));
        verify(mapper, never()).insertTokenFamily(any());
    }

    @Test
    void zeroRowTokenAndReceiptWritesFailClosedAfterCas() {
        doReturn(0).when(mapper).insertToken(any());
        assertProtocol(() -> exchange(service, command()),
                IndependentBoardOAuthTokenExchangeService.WRITE_FAILED);
        verify(mapper, never()).insertReceipt(any());

        setUp();
        doReturn(0).when(mapper).insertReceipt(any());
        assertProtocol(() -> exchange(service, command()),
                IndependentBoardOAuthTokenExchangeService.RECEIPT_WRITE_FAILED);
        verify(mapper, never()).selectReceiptByReceiptId(any());
    }

    @Test
    void missingOrTamperedCurrentReceiptFailsClosed() {
        when(mapper.selectReceiptByReceiptId(RECEIPT_ID)).thenReturn(null);
        assertProtocol(() -> exchange(service, command()),
                BoardOAuthTokenFamilyCreatedReceiptFactory.RECEIPT_INVALID);

        setUp();
        when(mapper.selectReceiptByReceiptId(RECEIPT_ID)).thenAnswer(invocation -> {
            BoardOAuthReceipt value = persistedReceipt.get();
            value.setPayloadDigest(new byte[32]);
            return value;
        });
        assertProtocol(() -> exchange(service, command()),
                BoardOAuthTokenFamilyCreatedReceiptFactory.RECEIPT_INVALID);
    }

    @Test
    void everyPostWriteLineageCurrentReadMustRemainExact() {
        consumedRequest.setStatus("APPROVED");
        assertProtocol(() -> exchange(service, command()),
                IndependentBoardOAuthTokenExchangeService.LINEAGE_DRIFT);

        setUp();
        usedCode.setStatus("ACTIVE");
        assertProtocol(() -> exchange(service, command()),
                IndependentBoardOAuthTokenExchangeService.LINEAGE_DRIFT);

        setUp();
        persistedFamily.setConsentIntent(
                BoardOAuthConsentIntent.EXPLICIT_REAUTHORIZATION);
        assertProtocol(() -> exchange(service, command()),
                IndependentBoardOAuthTokenExchangeService.LINEAGE_DRIFT);

        setUp();
        when(mapper.selectFamilyTokensForUpdate(FAMILY_ID)).thenReturn(List.of());
        assertProtocol(() -> exchange(service, command()),
                IndependentBoardOAuthTokenExchangeService.LINEAGE_DRIFT);
    }

    @Test
    void requiresAnEmptyLockedCreationReceiptCandidateSetBeforeInsert() {
        when(mapper.selectTokenFamilyCreatedReceiptCandidatesForUpdate(
                FAMILY_ID, CLIENT_ID, 20L)).thenReturn(null);
        assertProtocol(() -> exchange(service, command()),
                IndependentBoardOAuthTokenExchangeService
                        .RECEIPT_CARDINALITY_INVALID);
        verify(mapper, never()).insertReceipt(any());

        setUp();
        when(mapper.selectTokenFamilyCreatedReceiptCandidatesForUpdate(
                FAMILY_ID, CLIENT_ID, 20L)).thenReturn(List.of(
                        mock(BoardOAuthReceipt.class)));
        assertProtocol(() -> exchange(service, command()),
                IndependentBoardOAuthTokenExchangeService
                        .RECEIPT_CARDINALITY_INVALID);
        verify(mapper, never()).insertReceipt(any());

        setUp();
        when(mapper.selectTokenFamilyCreatedReceiptCandidatesForUpdate(
                FAMILY_ID, CLIENT_ID, 20L)).thenReturn(
                        java.util.Collections.singletonList(null));
        assertProtocol(() -> exchange(service, command()),
                IndependentBoardOAuthTokenExchangeService
                        .RECEIPT_CARDINALITY_INVALID);
        verify(mapper, never()).insertReceipt(any());
    }

    @Test
    void rejectsClientVersionAndLifecycleTimestampDrift() {
        client.setVersion(1L);
        assertProtocol(() -> exchange(service, command()), "invalid_client",
                IndependentBoardOAuthTokenExchangeService.CLIENT_INVALID);
        verify(mapper, never()).consumeAuthorizationCodeForClientIfVersion(
                anyLong(), any(), anyLong(), any());
        verify(mapper, never()).insertTokenFamily(any());

        setUp();
        client.setCreatedAt(null);
        assertProtocol(() -> exchange(service, command()), "invalid_client",
                IndependentBoardOAuthTokenExchangeService.CLIENT_INVALID);

        setUp();
        client.setUpdatedAt(Date.from(NOW.plusMillis(1)));
        assertProtocol(() -> exchange(service, command()), "invalid_client",
                IndependentBoardOAuthTokenExchangeService.CLIENT_INVALID);

        setUp();
        client.setCreatedAt(Date.from(NOW.minusSeconds(1)));
        client.setUpdatedAt(Date.from(NOW.minusSeconds(2)));
        assertProtocol(() -> exchange(service, command()), "invalid_client",
                IndependentBoardOAuthTokenExchangeService.CLIENT_INVALID);
    }

    @Test
    void familyMustAccommodateTheCompleteTenMinuteAccessLifetime() {
        Instant tooSoon = NOW.plusSeconds(599);
        client.setExpiresAt(Date.from(tooSoon));
        client.setRegisteredAt(Date.from(tooSoon.minusSeconds(31L * 86400)));

        assertProtocol(() -> exchange(service, command()), "invalid_client",
                IndependentBoardOAuthTokenExchangeService.CLIENT_INVALID);
        verify(mapper, never()).consumeAuthorizationCodeForClientIfVersion(
                anyLong(), any(), anyLong(), any());
        verify(mapper, never()).insertTokenFamily(any());
    }

    @Test
    void persistenceOutageMapsToRetryable503WithoutRawMaterial() {
        when(mapper.selectTokenExchangeLockLocatorByDigest(any(byte[].class)))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        BoardOAuthProtocolException failure = assertThrows(
                BoardOAuthProtocolException.class,
                () -> exchange(service, command()));
        assertEquals("temporarily_unavailable", failure.oauthError());
        assertEquals(503, failure.httpStatus());
        assertEquals(
                IndependentBoardOAuthTokenExchangeService.PERSISTENCE_UNAVAILABLE,
                failure.reasonCode());
        assertFalse(failure.toString().contains(RAW_CODE));
        assertFalse(failure.toString().contains(VERIFIER));
        assertFalse(failure.toString().contains(ACCESS_TOKEN));
        assertFalse(failure.toString().contains(REFRESH_TOKEN));
    }

    private static BoardOAuthTokenExchangeCommand command() {
        return new BoardOAuthTokenExchangeCommand(
                RAW_CODE, VERIFIER, CLIENT_ID, REDIRECT, RESOURCE);
    }

    private static BoardOAuthTokenExchangeAuthorityPort.LockResult
            currentAbsentAuthority() {
        return new BoardOAuthTokenExchangeAuthorityPort.LockResult(
                true,
                true,
                true,
                true,
                BoardOAuthTokenExchangeAuthorityPort.BindingTopology.ABSENT,
                true);
    }

    private static BoardOAuthClient activeClient() {
        BoardOAuthClient value = new BoardOAuthClient();
        value.setId(1L);
        value.setClientId(CLIENT_ID);
        value.setClientName("未验证的本地公共客户端");
        value.setIssuerUri(BoardOAuthProfile.ISSUER);
        value.setResourceUri(BoardOAuthProfile.RESOURCE);
        value.setProductCode("FBSIR_INDEPENDENT_BOARD");
        value.setSourceCode("WORKBUDDY");
        value.setConnectorCode("fbs-connector");
        value.setRedirectPort(54321);
        value.setRedirectUri(REDIRECT);
        value.setTokenEndpointAuthMethod("none");
        value.setGrantTypesCanonical("authorization_code refresh_token");
        value.setResponseTypesCanonical("code");
        value.setScopeCanonical(BoardOAuthProfile.CANONICAL_SCOPE);
        value.setScopeDigest(scopeDigest());
        value.setMetadataDigest(new byte[32]);
        value.setRegistrationSourceDigest(new byte[32]);
        value.setStatus("ACTIVE");
        value.setRegisteredAt(Date.from(NOW.minusSeconds(86400)));
        value.setExpiresAt(Date.from(NOW.plusSeconds(30L * 86400)));
        value.setVersion(0L);
        value.setCreatedAt(Date.from(NOW.minusSeconds(86400)));
        value.setUpdatedAt(Date.from(NOW.minusSeconds(1)));
        return value;
    }

    private static BoardOAuthClient historicalClient(String clientId, Long id) {
        BoardOAuthClient value = new BoardOAuthClient();
        value.setId(id);
        value.setClientId(clientId);
        value.setIssuerUri(BoardOAuthProfile.ISSUER);
        value.setResourceUri(BoardOAuthProfile.RESOURCE);
        value.setProductCode("FBSIR_INDEPENDENT_BOARD");
        value.setSourceCode("WORKBUDDY");
        value.setConnectorCode("fbs-connector");
        value.setStatus("REVOKED");
        return value;
    }

    private void assertResourceRejected(String resource) {
        BoardOAuthTokenExchangeCommand invalid = new BoardOAuthTokenExchangeCommand(
                RAW_CODE, VERIFIER, CLIENT_ID, REDIRECT, resource);
        assertProtocol(() -> exchange(service, invalid), "invalid_target",
                IndependentBoardOAuthTokenExchangeService.RESOURCE_INVALID);
        verifyNoInteractions(mapper);
        verifyNoInteractions(generator);
        setUp();
    }

    private static BoardOAuthAuthorizationRequest approvedRequest() {
        BoardOAuthAuthorizationRequest value = requestBase();
        value.setConsentIntent(BoardOAuthConsentIntent.FIRST_CONNECT);
        value.setStatus("APPROVED");
        value.setApprovedAt(Date.from(NOW.minusSeconds(30)));
        value.setVersion(1L);
        value.setUpdatedAt(Date.from(NOW.minusSeconds(30)));
        return value;
    }

    private static BoardOAuthAuthorizationRequest consumedRequest() {
        BoardOAuthAuthorizationRequest value = requestBase();
        value.setConsentIntent(BoardOAuthConsentIntent.FIRST_CONNECT);
        value.setStateKeyRef(null);
        value.setStateNonce(null);
        value.setStateCiphertext(null);
        value.setStatus("CONSUMED");
        value.setApprovedAt(Date.from(NOW.minusSeconds(30)));
        value.setConsumedAt(Date.from(NOW));
        value.setVersion(2L);
        value.setUpdatedAt(Date.from(NOW));
        return value;
    }

    private static BoardOAuthAuthorizationRequest requestBase() {
        BoardOAuthAuthorizationRequest value = new BoardOAuthAuthorizationRequest();
        value.setId(10L);
        value.setRequestHandleDigest(BoardOAuthCrypto.sha256Ascii("h".repeat(43)));
        value.setClientId(CLIENT_ID);
        value.setRedirectUri(REDIRECT);
        value.setCodeChallenge(BoardOAuthCrypto.pkceS256Challenge(VERIFIER));
        value.setCodeChallengeMethod(BoardOAuthProfile.PKCE_METHOD);
        value.setStateDigest(BoardOAuthCrypto.sha256Ascii("state-value"));
        value.setStateKeyRef("state-key-v1");
        value.setStateNonce(new byte[12]);
        value.setStateCiphertext(new byte[32]);
        setProfile(value);
        value.setTenantId(100L);
        value.setMemberId(200L);
        value.setUserId(300L);
        value.setPrincipalSubjectDigest(
                BoardOAuthPrincipalSubject.digest(100L, 200L, 300L));
        value.setRequestedAt(Date.from(NOW.minusSeconds(120)));
        value.setExpiresAt(Date.from(NOW.plusSeconds(180)));
        value.setCreatedAt(Date.from(NOW.minusSeconds(120)));
        return value;
    }

    private static BoardOAuthAuthorizationCode activeCode() {
        BoardOAuthAuthorizationCode value = codeBase();
        value.setStatus("ACTIVE");
        value.setVersion(0L);
        value.setUpdatedAt(Date.from(NOW.minusSeconds(30)));
        return value;
    }

    private static BoardOAuthAuthorizationCode usedCode() {
        BoardOAuthAuthorizationCode value = codeBase();
        value.setStatus("USED");
        value.setUsedAt(Date.from(NOW));
        value.setVersion(1L);
        value.setUpdatedAt(Date.from(NOW));
        return value;
    }

    private static BoardOAuthAuthorizationCode codeBase() {
        BoardOAuthAuthorizationCode value = new BoardOAuthAuthorizationCode();
        value.setId(20L);
        value.setCodeDigest(BoardOAuthCrypto.sha256Ascii(RAW_CODE));
        value.setAuthorizationRequestId(10L);
        value.setClientId(CLIENT_ID);
        value.setRedirectUri(REDIRECT);
        value.setCodeChallenge(BoardOAuthCrypto.pkceS256Challenge(VERIFIER));
        value.setCodeChallengeMethod(BoardOAuthProfile.PKCE_METHOD);
        setProfile(value);
        value.setTenantId(100L);
        value.setMemberId(200L);
        value.setUserId(300L);
        value.setPrincipalSubjectDigest(
                BoardOAuthPrincipalSubject.digest(100L, 200L, 300L));
        value.setConsentIntent(BoardOAuthConsentIntent.FIRST_CONNECT);
        value.setIssuedAt(Date.from(NOW.minusSeconds(30)));
        value.setExpiresAt(Date.from(NOW.plusSeconds(30)));
        value.setCreatedAt(Date.from(NOW.minusSeconds(30)));
        return value;
    }

    private static BoardOAuthTokenFamily persistedFamily() {
        BoardOAuthTokenFamily value = new BoardOAuthTokenFamily();
        value.setId(1L);
        value.setFamilyId(FAMILY_ID);
        value.setOriginAuthorizationCodeId(20L);
        value.setClientId(CLIENT_ID);
        value.setTenantId(100L);
        value.setMemberId(200L);
        value.setUserId(300L);
        value.setProductCode("FBSIR_INDEPENDENT_BOARD");
        value.setSourceCode("WORKBUDDY");
        value.setConnectorCode("fbs-connector");
        value.setIssuerUri(BoardOAuthProfile.ISSUER);
        value.setResourceUri(BoardOAuthProfile.RESOURCE);
        value.setScopeCanonical(BoardOAuthProfile.CANONICAL_SCOPE);
        value.setScopeDigest(scopeDigest());
        value.setPrincipalSubjectDigest(
                BoardOAuthPrincipalSubject.digest(100L, 200L, 300L));
        value.setConsentIntent(BoardOAuthConsentIntent.FIRST_CONNECT);
        value.setStatus("PENDING_BINDING");
        value.setLifecycleSlot("PENDING_BINDING");
        value.setCurrentRefreshGeneration(0L);
        value.setIssuedAt(Date.from(NOW));
        value.setExpiresAt(Date.from(NOW.plusSeconds(30L * 86400)));
        value.setVersion(0L);
        value.setCreatedAt(Date.from(NOW));
        value.setUpdatedAt(Date.from(NOW));
        return value;
    }

    private static BoardOAuthToken persistedToken(
            Long id,
            String type,
            String raw,
            Instant expiresAt) {
        BoardOAuthToken value = new BoardOAuthToken();
        value.setId(id);
        value.setTokenDigest(BoardOAuthCrypto.sha256Ascii(raw));
        value.setFamilyId(FAMILY_ID);
        value.setTokenType(type);
        value.setGeneration(0L);
        value.setResourceUri(BoardOAuthProfile.RESOURCE);
        value.setScopeCanonical(BoardOAuthProfile.CANONICAL_SCOPE);
        value.setScopeDigest(scopeDigest());
        value.setStatus("ACTIVE");
        value.setActiveRefreshSlot("REFRESH".equals(type) ? 1 : null);
        value.setIssuedAt(Date.from(NOW));
        value.setExpiresAt(Date.from(expiresAt));
        value.setVersion(0L);
        value.setCreatedAt(Date.from(NOW));
        value.setUpdatedAt(Date.from(NOW));
        return value;
    }

    private static void setProfile(BoardOAuthAuthorizationRequest value) {
        value.setIssuerUri(BoardOAuthProfile.ISSUER);
        value.setResourceUri(BoardOAuthProfile.RESOURCE);
        value.setProductCode("FBSIR_INDEPENDENT_BOARD");
        value.setSourceCode("WORKBUDDY");
        value.setConnectorCode("fbs-connector");
        value.setScopeCanonical(BoardOAuthProfile.CANONICAL_SCOPE);
        value.setScopeDigest(scopeDigest());
    }

    private static void setProfile(BoardOAuthAuthorizationCode value) {
        value.setIssuerUri(BoardOAuthProfile.ISSUER);
        value.setResourceUri(BoardOAuthProfile.RESOURCE);
        value.setProductCode("FBSIR_INDEPENDENT_BOARD");
        value.setSourceCode("WORKBUDDY");
        value.setConnectorCode("fbs-connector");
        value.setScopeCanonical(BoardOAuthProfile.CANONICAL_SCOPE);
        value.setScopeDigest(scopeDigest());
    }

    private static byte[] scopeDigest() {
        return BoardOAuthCrypto.sha256Ascii(BoardOAuthProfile.CANONICAL_SCOPE);
    }

    private static String TOKEN_ACCESS() {
        return "ACCESS";
    }

    private static String TOKEN_REFRESH() {
        return "REFRESH";
    }

    private static BoardOAuthTokenExchangeResult exchange(
            IndependentBoardOAuthTokenExchangeService service,
            BoardOAuthTokenExchangeCommand command) {
        return service.exchangeForCommit(command).requireCompletedResult();
    }

    private static void assertProtocol(
            Runnable invocation,
            String expectedOAuthError,
            String reason) {
        BoardOAuthProtocolException failure = assertThrows(
                BoardOAuthProtocolException.class, invocation::run);
        assertEquals(expectedOAuthError, failure.oauthError());
        assertEquals(reason, failure.reasonCode());
        assertFalse(failure.toString().contains(RAW_CODE));
        assertFalse(failure.toString().contains(VERIFIER));
        assertFalse(failure.toString().contains(ACCESS_TOKEN));
        assertFalse(failure.toString().contains(REFRESH_TOKEN));
    }

    private static void assertProtocol(Runnable invocation, String reason) {
        BoardOAuthProtocolException failure = assertThrows(
                BoardOAuthProtocolException.class, invocation::run);
        assertEquals(reason, failure.reasonCode());
        assertFalse(failure.toString().contains(RAW_CODE));
        assertFalse(failure.toString().contains(VERIFIER));
        assertFalse(failure.toString().contains(ACCESS_TOKEN));
        assertFalse(failure.toString().contains(REFRESH_TOKEN));
    }

    private void assertConsumedRequestFixture() {
        assertEquals(10L, consumedRequest.getId());
        assertEquals(32, consumedRequest.getRequestHandleDigest().length);
        assertEquals(43, consumedRequest.getClientId().length());
        assertTrue(BoardOAuthProfile.isAllowedLoopbackRedirect(
                consumedRequest.getRedirectUri()));
        assertTrue(BoardOAuthCrypto.isValidPkceS256Challenge(
                consumedRequest.getCodeChallenge()));
        assertEquals(BoardOAuthProfile.PKCE_METHOD,
                consumedRequest.getCodeChallengeMethod());
        assertEquals(32, consumedRequest.getStateDigest().length);
        assertNull(consumedRequest.getStateKeyRef());
        assertNull(consumedRequest.getStateNonce());
        assertNull(consumedRequest.getStateCiphertext());
        assertEquals(BoardOAuthProfile.ISSUER, consumedRequest.getIssuerUri());
        assertEquals(BoardOAuthProfile.RESOURCE, consumedRequest.getResourceUri());
        assertEquals("FBSIR_INDEPENDENT_BOARD", consumedRequest.getProductCode());
        assertEquals("WORKBUDDY", consumedRequest.getSourceCode());
        assertEquals("fbs-connector", consumedRequest.getConnectorCode());
        assertEquals(BoardOAuthProfile.CANONICAL_SCOPE,
                consumedRequest.getScopeCanonical());
        assertArrayEquals(scopeDigest(), consumedRequest.getScopeDigest());
        assertEquals(100L, consumedRequest.getTenantId());
        assertEquals(200L, consumedRequest.getMemberId());
        assertEquals(300L, consumedRequest.getUserId());
        assertEquals(32, consumedRequest.getPrincipalSubjectDigest().length);
        assertNotNull(consumedRequest.getConsentIntent());
        assertEquals("CONSUMED", consumedRequest.getStatus());
        assertNotNull(consumedRequest.getRequestedAt());
        assertNotNull(consumedRequest.getExpiresAt());
        assertNotNull(consumedRequest.getApprovedAt());
        assertNull(consumedRequest.getDeniedAt());
        assertNotNull(consumedRequest.getConsumedAt());
        assertEquals(2L, consumedRequest.getVersion());
        assertTrue(!consumedRequest.getUpdatedAt().before(
                consumedRequest.getCreatedAt()));
    }

}
