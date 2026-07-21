package com.wx.fbsir.business.board.oauth.service;

import com.wx.fbsir.business.board.oauth.BoardOAuthConsentIntent;
import com.wx.fbsir.business.board.oauth.BoardOAuthCrypto;
import com.wx.fbsir.business.board.oauth.BoardOAuthPrincipalSubject;
import com.wx.fbsir.business.board.oauth.BoardOAuthProfile;
import com.wx.fbsir.business.board.oauth.BoardOAuthProtocolException;
import com.wx.fbsir.business.board.oauth.BoardOAuthRefreshReceiptFactory;
import com.wx.fbsir.business.board.oauth.BoardOAuthTokenMaterialGenerator;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthClient;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthReceipt;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthToken;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthTokenFamily;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthRefreshCommand;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthRefreshResult;
import com.wx.fbsir.business.board.oauth.mapper.IndependentBoardOAuthMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IndependentBoardOAuthRefreshServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-21T04:00:00.123Z");
    private static final String RAW_REFRESH = "s".repeat(43);
    private static final String CLIENT_ID = "c".repeat(43);
    private static final String FAMILY_ID = "f".repeat(43);
    private static final String BINDING_ID = "binding-verified-1";
    private static final String ACCESS_TOKEN = "a".repeat(43);
    private static final String NEXT_REFRESH = "n".repeat(43);
    private static final String RECEIPT_ID = "i".repeat(43);
    private static final String CORRELATION_ID = "o".repeat(43);
    private static final long MAX_UNSIGNED_INT = 4_294_967_295L;

    private IndependentBoardOAuthMapper mapper;
    private BoardOAuthRefreshAuthorityPort authorityPort;
    private BoardOAuthTokenMaterialGenerator generator;
    private IndependentBoardOAuthRefreshService service;
    private IndependentBoardOAuthMapper.TokenContextLocator locator;
    private BoardOAuthClient client;
    private BoardOAuthTokenFamily family;
    private BoardOAuthToken source;
    private List<BoardOAuthToken> tokens;
    private BoardOAuthReceipt creationReceipt;
    private AtomicReference<BoardOAuthReceipt> insertedReceipt;

    @BeforeEach
    void setUp() {
        mapper = mock(IndependentBoardOAuthMapper.class);
        authorityPort = mock(BoardOAuthRefreshAuthorityPort.class);
        generator = mock(BoardOAuthTokenMaterialGenerator.class);
        service = new IndependentBoardOAuthRefreshService(
                mapper, authorityPort, generator,
                Clock.fixed(NOW, ZoneOffset.UTC));

        locator = locator(0L);
        client = activeClient();
        family = activeFamily(0L, 5L);
        source = token(10L, "REFRESH", 0L, RAW_REFRESH, "ACTIVE",
                NOW.minusSeconds(900), null, null,
                NOW.plusSeconds(86_400), 2L);
        tokens = List.of(
                token(9L, "ACCESS", 0L, "legacy-access", "ACTIVE",
                        NOW.minusSeconds(900), null, null,
                        NOW.plusSeconds(300), 0L),
                source);
        creationReceipt = creationReceipt();
        insertedReceipt = new AtomicReference<>();

        when(mapper.selectTokenContextLocatorByDigest(any(byte[].class)))
                .thenReturn(locator);
        when(authorityPort.lockForRefresh(
                100L, 200L, 300L, "FBSIR_INDEPENDENT_BOARD", "WORKBUDDY",
                "fbs-connector", NOW)).thenReturn(currentAuthority(NOW, 3L, true));
        when(mapper.selectClientForUpdate(CLIENT_ID)).thenReturn(client);
        when(mapper.selectActiveTokenFamilySlotForUpdate(
                100L, 200L, "FBSIR_INDEPENDENT_BOARD", "WORKBUDDY",
                "fbs-connector")).thenReturn(family);
        when(mapper.selectPendingTokenFamilySlotForUpdate(
                100L, 200L, "FBSIR_INDEPENDENT_BOARD", "WORKBUDDY",
                "fbs-connector")).thenReturn(null);
        when(mapper.selectTokenFamilyForUpdate(FAMILY_ID, CLIENT_ID))
                .thenReturn(family, rotatedFamily());
        when(mapper.selectRefreshFamilyTokensForUpdate(FAMILY_ID, 10_001))
                .thenReturn(tokens, rotatedTokens());
        when(mapper.selectBoundedRefreshFamilySecurityReceiptsForUpdate(
                FAMILY_ID, CLIENT_ID, 6_001)).thenReturn(List.of(creationReceipt));
        when(mapper.useRefreshTokenForGenerationIfVersion(
                10L, FAMILY_ID, 2L, 0L, Date.from(NOW))).thenReturn(1);
        when(mapper.advanceTokenFamilyGenerationAtIfVersion(
                FAMILY_ID, CLIENT_ID, BINDING_ID, 3L, 5L, 0L,
                Date.from(NOW))).thenReturn(1);

        when(generator.generateAccessToken()).thenReturn(ACCESS_TOKEN);
        when(generator.generateRefreshToken()).thenReturn(NEXT_REFRESH);
        when(generator.generateReceiptId()).thenReturn(RECEIPT_ID);
        when(generator.generateCorrelationId()).thenReturn(CORRELATION_ID);

        AtomicLong tokenId = new AtomicLong(11L);
        when(mapper.insertToken(any())).thenAnswer(invocation -> {
            BoardOAuthToken value = invocation.getArgument(0);
            value.setId(tokenId.getAndIncrement());
            return 1;
        });
        when(mapper.insertReceipt(any())).thenAnswer(invocation -> {
            BoardOAuthReceipt value = invocation.getArgument(0);
            value.setId(40L);
            insertedReceipt.set(value);
            return 1;
        });
        when(mapper.selectReceiptByReceiptIdAndScopeForUpdate(
                RECEIPT_ID, FAMILY_ID, CLIENT_ID))
                .thenAnswer(invocation -> insertedReceipt.get());
    }

    @Test
    void rotatesOnceWithCanonicalLockOrderCurrentReadsAndDigestOnlyPersistence() {
        BoardOAuthRefreshOutcome outcome = service.refreshForCommit(command());
        BoardOAuthRefreshResult result = outcome.requireCompletedResult();

        assertFalse(outcome.rejectsAfterCommit());
        assertEquals(ACCESS_TOKEN, result.rawAccessToken());
        assertEquals(NEXT_REFRESH, result.rawRefreshToken());
        assertEquals("Bearer", result.tokenType());
        assertEquals(600L, result.expiresInSeconds());
        assertEquals(BoardOAuthProfile.CANONICAL_SCOPE, result.scopeCanonical());
        assertEquals(NOW.plusSeconds(600), result.accessTokenExpiresAt());
        assertEquals(family.getExpiresAt().toInstant(),
                result.refreshTokenExpiresAt());
        assertEquals(RECEIPT_ID, result.receiptId());
        assertEquals(CORRELATION_ID, result.correlationId());

        InOrder order = inOrder(mapper, authorityPort);
        order.verify(mapper).selectTokenContextLocatorByDigest(any(byte[].class));
        order.verify(authorityPort).lockForRefresh(
                100L, 200L, 300L, "FBSIR_INDEPENDENT_BOARD", "WORKBUDDY",
                "fbs-connector", NOW);
        order.verify(mapper).selectClientForUpdate(CLIENT_ID);
        order.verify(mapper).selectActiveTokenFamilySlotForUpdate(
                100L, 200L, "FBSIR_INDEPENDENT_BOARD", "WORKBUDDY",
                "fbs-connector");
        order.verify(mapper).selectPendingTokenFamilySlotForUpdate(
                100L, 200L, "FBSIR_INDEPENDENT_BOARD", "WORKBUDDY",
                "fbs-connector");
        order.verify(mapper).selectTokenFamilyForUpdate(FAMILY_ID, CLIENT_ID);
        order.verify(mapper).selectRefreshFamilyTokensForUpdate(
                FAMILY_ID, 10_001);
        order.verify(authorityPort).lockReceiptsForRefresh(any());
        order.verify(mapper).selectBoundedRefreshFamilySecurityReceiptsForUpdate(
                FAMILY_ID, CLIENT_ID, 6_001);
        order.verify(mapper).useRefreshTokenForGenerationIfVersion(
                10L, FAMILY_ID, 2L, 0L, Date.from(NOW));
        order.verify(mapper).insertToken(argThat(
                value -> "ACCESS".equals(value.getTokenType())));
        order.verify(mapper).insertToken(argThat(
                value -> "REFRESH".equals(value.getTokenType())));
        order.verify(mapper).advanceTokenFamilyGenerationAtIfVersion(
                FAMILY_ID, CLIENT_ID, BINDING_ID, 3L, 5L, 0L,
                Date.from(NOW));
        order.verify(mapper).selectTokenFamilyForUpdate(FAMILY_ID, CLIENT_ID);
        order.verify(mapper).selectRefreshFamilyTokensForUpdate(
                FAMILY_ID, 10_001);
        order.verify(mapper).insertReceipt(any());
        order.verify(mapper).selectReceiptByReceiptIdAndScopeForUpdate(
                RECEIPT_ID, FAMILY_ID, CLIENT_ID);
        verify(authorityPort, never()).revokeForRefreshReplay(
                any(), any(), anyLong(), any());

        ArgumentCaptor<BoardOAuthToken> writtenTokens =
                ArgumentCaptor.forClass(BoardOAuthToken.class);
        verify(mapper, org.mockito.Mockito.times(2))
                .insertToken(writtenTokens.capture());
        assertArrayEquals(BoardOAuthCrypto.sha256Ascii(ACCESS_TOKEN),
                writtenTokens.getAllValues().get(0).getTokenDigest());
        assertArrayEquals(BoardOAuthCrypto.sha256Ascii(NEXT_REFRESH),
                writtenTokens.getAllValues().get(1).getTokenDigest());
        assertFalse(new String(writtenTokens.getAllValues().get(0).getTokenDigest(),
                StandardCharsets.US_ASCII).contains(ACCESS_TOKEN));
        assertFalse(new String(writtenTokens.getAllValues().get(1).getTokenDigest(),
                StandardCharsets.US_ASCII).contains(NEXT_REFRESH));

        BoardOAuthReceipt receipt = insertedReceipt.get();
        assertNotNull(receipt);
        assertEquals(BoardOAuthRefreshReceiptFactory.TOKEN_FAMILY_ROTATED,
                receipt.getAction());
        assertEquals(2, receipt.getReceiptFormatVersion());
        assertEquals(0L, receipt.getSubjectGeneration());
        assertEquals(1L, receipt.getResultGeneration());
        assertEquals(32, receipt.getPayloadDigest().length);
        String persistedText = String.join("|",
                receipt.getReceiptId(), receipt.getAction(), receipt.getClientId(),
                receipt.getFamilyId(), receipt.getBindingId(),
                receipt.getCorrelationId(), receipt.getCausationReceiptId());
        assertFalse(persistedText.contains(RAW_REFRESH));
        assertFalse(persistedText.contains(ACCESS_TOKEN));
        assertFalse(persistedText.contains(NEXT_REFRESH));
    }

    @Test
    void usedRefreshContainsReplayAndReturnsCommitThenInvalidGrantOutcome() {
        configureReplayBeforeContainment();
        BoardOAuthRefreshAuthorityPort.LockResult authority =
                currentAuthority(NOW, 3L, true);
        BoardOAuthRefreshAuthorityPort.BindingRevocation revocation =
                new BoardOAuthRefreshAuthorityPort.BindingRevocation(
                        BINDING_ID, 3L, 4L, NOW,
                        "w".repeat(43), "1".repeat(64));
        when(authorityPort.lockForRefresh(
                100L, 200L, 300L, "FBSIR_INDEPENDENT_BOARD", "WORKBUDDY",
                "fbs-connector", NOW)).thenReturn(authority);
        when(mapper.compromiseTokenFamilyIfVersion(
                FAMILY_ID, CLIENT_ID, 6L, Date.from(NOW))).thenReturn(1);
        when(mapper.revokeActiveFamilyTokensAtLogicalTime(
                FAMILY_ID, Date.from(NOW))).thenReturn(3);
        when(authorityPort.revokeForRefreshReplay(
                authority.lease(), BINDING_ID, 3L, NOW)).thenReturn(revocation);

        BoardOAuthRefreshOutcome outcome = service.refreshForCommit(command());

        assertTrue(outcome.rejectsAfterCommit());
        assertEquals(IndependentBoardOAuthRefreshService.REPLAY_DETECTED,
                outcome.invalidGrantReasonAfterCommit());
        assertThrows(IllegalStateException.class, outcome::requireCompletedResult);

        InOrder order = inOrder(mapper, authorityPort);
        order.verify(mapper).selectTokenContextLocatorByDigest(any(byte[].class));
        order.verify(authorityPort).lockForRefresh(
                100L, 200L, 300L, "FBSIR_INDEPENDENT_BOARD", "WORKBUDDY",
                "fbs-connector", NOW);
        order.verify(mapper).selectClientForUpdate(CLIENT_ID);
        order.verify(mapper).selectActiveTokenFamilySlotForUpdate(
                100L, 200L, "FBSIR_INDEPENDENT_BOARD", "WORKBUDDY",
                "fbs-connector");
        order.verify(mapper).selectPendingTokenFamilySlotForUpdate(
                100L, 200L, "FBSIR_INDEPENDENT_BOARD", "WORKBUDDY",
                "fbs-connector");
        order.verify(mapper).selectTokenFamilyForUpdate(FAMILY_ID, CLIENT_ID);
        order.verify(mapper).selectRefreshFamilyTokensForUpdate(
                FAMILY_ID, 10_001);
        order.verify(authorityPort).lockReceiptsForRefresh(authority.lease());
        order.verify(mapper).selectBoundedRefreshFamilySecurityReceiptsForUpdate(
                FAMILY_ID, CLIENT_ID, 6_001);
        order.verify(mapper).compromiseTokenFamilyIfVersion(
                FAMILY_ID, CLIENT_ID, 6L, Date.from(NOW));
        order.verify(mapper).revokeActiveFamilyTokensAtLogicalTime(
                FAMILY_ID, Date.from(NOW));
        order.verify(authorityPort).revokeForRefreshReplay(
                authority.lease(), BINDING_ID, 3L, NOW);
        order.verify(mapper).selectTokenFamilyForUpdate(FAMILY_ID, CLIENT_ID);
        order.verify(mapper).selectRefreshFamilyTokensForUpdate(
                FAMILY_ID, 10_001);
        order.verify(mapper).insertReceipt(any());
        order.verify(mapper).selectReceiptByReceiptIdAndScopeForUpdate(
                RECEIPT_ID, FAMILY_ID, CLIENT_ID);
        verify(generator, never()).generateAccessToken();
        verify(generator, never()).generateRefreshToken();
        assertEquals(BoardOAuthRefreshReceiptFactory.REFRESH_REPLAY_DETECTED,
                insertedReceipt.get().getAction());
    }

    @Test
    void alreadyCompromisedFamilyIsAStableRejectionWithoutSecondMutation() {
        BoardOAuthTokenFamily compromised = compromisedFamily(NOW.minusSeconds(50));
        List<BoardOAuthToken> revoked = compromisedTokens(NOW.minusSeconds(50));
        BoardOAuthReceipt rotation = rotationReceipt();
        BoardOAuthReceipt replay = replayReceipt(
                compromised, revoked.get(1), rotation, NOW.minusSeconds(50));
        when(authorityPort.lockForRefresh(
                100L, 200L, 300L, "FBSIR_INDEPENDENT_BOARD", "WORKBUDDY",
                "fbs-connector", NOW)).thenReturn(currentAuthority(NOW, 4L, false));
        when(mapper.selectActiveTokenFamilySlotForUpdate(
                100L, 200L, "FBSIR_INDEPENDENT_BOARD", "WORKBUDDY",
                "fbs-connector")).thenReturn(null);
        when(mapper.selectTokenFamilyForUpdate(FAMILY_ID, CLIENT_ID))
                .thenReturn(compromised);
        when(mapper.selectRefreshFamilyTokensForUpdate(FAMILY_ID, 10_001))
                .thenReturn(revoked);
        when(mapper.selectBoundedRefreshFamilySecurityReceiptsForUpdate(
                FAMILY_ID, CLIENT_ID, 6_001)).thenReturn(
                        List.of(creationReceipt, rotation, replay));

        BoardOAuthRefreshOutcome outcome = service.refreshForCommit(command());

        assertTrue(outcome.rejectsAfterCommit());
        assertEquals(IndependentBoardOAuthRefreshService.REPLAY_DETECTED,
                outcome.invalidGrantReasonAfterCommit());
        verify(mapper, never()).compromiseTokenFamilyIfVersion(
                any(), any(), anyLong(), any());
        verify(mapper, never()).revokeActiveFamilyTokensAtLogicalTime(any(), any());
        verify(authorityPort, never()).revokeForRefreshReplay(
                any(), any(), anyLong(), any());
        verify(mapper, never()).insertReceipt(any());
        verifyNoInteractions(generator);
    }

    @Test
    void expiryAtFinalAuthorityLockTimeFailsBeforeAnyRotationWrite() {
        Instant finalLockTime = NOW.plusSeconds(1);
        source.setExpiresAt(Date.from(finalLockTime));
        when(authorityPort.lockForRefresh(
                100L, 200L, 300L, "FBSIR_INDEPENDENT_BOARD", "WORKBUDDY",
                "fbs-connector", NOW)).thenReturn(
                        currentAuthority(finalLockTime, 3L, true));

        assertProtocol(() -> service.refreshForCommit(command()),
                "invalid_grant", IndependentBoardOAuthRefreshService.TOKEN_INVALID);
        verify(mapper, never()).useRefreshTokenForGenerationIfVersion(
                anyLong(), any(), anyLong(), anyLong(), any());
        verify(mapper, never()).insertToken(any());
        verifyNoInteractions(generator);
    }

    @Test
    void authorityMustRemainCurrentForTheWholeAccessTokenLifetime() {
        BoardOAuthRefreshAuthorityPort.LockResult shortAuthority =
                new BoardOAuthRefreshAuthorityPort.LockResult(
                        mock(BoardOAuthRefreshAuthorityPort.Lease.class),
                        true, true, true, true, true, true,
                        BINDING_ID, 3L, CLIENT_ID, principalDigest(),
                        BoardOAuthProfile.REQUIRED_SCOPES, NOW,
                        NOW.plusSeconds(599));
        when(authorityPort.lockForRefresh(
                100L, 200L, 300L, "FBSIR_INDEPENDENT_BOARD", "WORKBUDDY",
                "fbs-connector", NOW)).thenReturn(shortAuthority);

        assertProtocol(() -> service.refreshForCommit(command()),
                "invalid_grant",
                IndependentBoardOAuthRefreshService.AUTHORITY_NOT_CURRENT);
        verify(mapper, never()).useRefreshTokenForGenerationIfVersion(
                anyLong(), any(), anyLong(), anyLong(), any());
        verifyNoInteractions(generator);
    }

    @Test
    void maximumGenerationFailsClosedBeforeMaterialGenerationOrCas() {
        family.setCurrentRefreshGeneration(MAX_UNSIGNED_INT);
        source.setGeneration(MAX_UNSIGNED_INT);
        locator.setTokenGeneration(MAX_UNSIGNED_INT);

        assertProtocol(() -> service.refreshForCommit(command()),
                "invalid_grant", IndependentBoardOAuthRefreshService.TOKEN_INVALID);
        verify(mapper, never()).useRefreshTokenForGenerationIfVersion(
                anyLong(), any(), anyLong(), anyLong(), any());
        verify(mapper, never()).insertToken(any());
        verifyNoInteractions(generator);
    }

    @Test
    void validatesResourceClientScopeAndTokenBeforeAnyDiscovery() {
        assertProtocol(
                () -> service.refreshForCommit(new BoardOAuthRefreshCommand(
                        RAW_REFRESH, CLIENT_ID, BoardOAuthProfile.RESOURCE + "/", null)),
                "invalid_target", IndependentBoardOAuthRefreshService.RESOURCE_INVALID);
        assertProtocol(
                () -> service.refreshForCommit(new BoardOAuthRefreshCommand(
                        RAW_REFRESH, "bad", BoardOAuthProfile.RESOURCE, null)),
                "invalid_client", IndependentBoardOAuthRefreshService.CLIENT_INVALID);
        assertProtocol(
                () -> service.refreshForCommit(new BoardOAuthRefreshCommand(
                        RAW_REFRESH, CLIENT_ID, BoardOAuthProfile.RESOURCE,
                        BoardOAuthProfile.CANONICAL_SCOPE + " extra")),
                "invalid_scope", IndependentBoardOAuthRefreshService.SCOPE_INVALID);
        assertProtocol(
                () -> service.refreshForCommit(new BoardOAuthRefreshCommand(
                        "short", CLIENT_ID, BoardOAuthProfile.RESOURCE, null)),
                "invalid_grant", IndependentBoardOAuthRefreshService.TOKEN_INVALID);

        verifyNoInteractions(mapper, authorityPort, generator);
    }

    @Test
    void validButWrongClientIsAnOpaqueInvalidGrantBeforeAuthorityLocks() {
        locator.setClientId("z".repeat(43));

        assertProtocol(() -> service.refreshForCommit(command()),
                "invalid_grant", IndependentBoardOAuthRefreshService.TOKEN_INVALID);
        verify(mapper).selectTokenContextLocatorByDigest(any(byte[].class));
        verifyNoInteractions(authorityPort, generator);
        verify(mapper, never()).selectClientForUpdate(any());
    }

    @Test
    void mapsPersistenceCollisionAndUnexpectedFailureWithoutRawCanaries() {
        when(mapper.selectTokenContextLocatorByDigest(any(byte[].class)))
                .thenThrow(new DuplicateKeyException("contains " + RAW_REFRESH));
        assertProtocol(() -> service.refreshForCommit(command()),
                "invalid_request", IndependentBoardOAuthRefreshService.CONFLICT);

        setUp();
        when(mapper.selectTokenContextLocatorByDigest(any(byte[].class)))
                .thenThrow(new DataAccessResourceFailureException(
                        "contains " + RAW_REFRESH));
        assertProtocol(() -> service.refreshForCommit(command()),
                "temporarily_unavailable",
                IndependentBoardOAuthRefreshService.PERSISTENCE_UNAVAILABLE);

        setUp();
        when(mapper.selectTokenContextLocatorByDigest(any(byte[].class)))
                .thenThrow(new IllegalStateException("contains " + RAW_REFRESH));
        assertProtocol(() -> service.refreshForCommit(command()),
                "server_error", IndependentBoardOAuthRefreshService.WRITE_FAILED);
    }

    private void configureReplayBeforeContainment() {
        family = activeFamily(1L, 6L);
        source = token(10L, "REFRESH", 0L, RAW_REFRESH, "USED",
                NOW.minusSeconds(900), NOW.minusSeconds(100), null,
                NOW.plusSeconds(86_400), 3L);
        tokens = List.of(
                token(9L, "ACCESS", 0L, "legacy-access", "ACTIVE",
                        NOW.minusSeconds(900), null, null,
                        NOW.plusSeconds(300), 0L),
                source,
                token(11L, "ACCESS", 1L, ACCESS_TOKEN, "ACTIVE",
                        NOW.minusSeconds(100), null, null,
                        NOW.plusSeconds(500), 0L),
                token(12L, "REFRESH", 1L, NEXT_REFRESH, "ACTIVE",
                        NOW.minusSeconds(100), null, null,
                        NOW.plusSeconds(86_400), 0L));
        BoardOAuthTokenFamily compromised = compromisedFamily(NOW);
        List<BoardOAuthToken> revoked = compromisedTokens(NOW);
        when(mapper.selectActiveTokenFamilySlotForUpdate(
                100L, 200L, "FBSIR_INDEPENDENT_BOARD", "WORKBUDDY",
                "fbs-connector")).thenReturn(family);
        when(mapper.selectTokenFamilyForUpdate(FAMILY_ID, CLIENT_ID))
                .thenReturn(family, compromised);
        when(mapper.selectRefreshFamilyTokensForUpdate(FAMILY_ID, 10_001))
                .thenReturn(tokens, revoked);
        when(mapper.selectBoundedRefreshFamilySecurityReceiptsForUpdate(
                FAMILY_ID, CLIENT_ID, 6_001)).thenReturn(
                        List.of(creationReceipt, rotationReceipt()));
    }

    private static BoardOAuthRefreshCommand command() {
        return new BoardOAuthRefreshCommand(
                RAW_REFRESH, CLIENT_ID, BoardOAuthProfile.RESOURCE, null);
    }

    private static IndependentBoardOAuthMapper.TokenContextLocator locator(
            long generation) {
        IndependentBoardOAuthMapper.TokenContextLocator value =
                new IndependentBoardOAuthMapper.TokenContextLocator();
        value.setTokenId(10L);
        value.setFamilyId(FAMILY_ID);
        value.setTokenType("REFRESH");
        value.setTokenGeneration(generation);
        value.setClientId(CLIENT_ID);
        value.setTenantId(100L);
        value.setMemberId(200L);
        value.setUserId(300L);
        value.setProductCode("FBSIR_INDEPENDENT_BOARD");
        value.setSourceCode("WORKBUDDY");
        value.setConnectorCode("fbs-connector");
        return value;
    }

    private static BoardOAuthRefreshAuthorityPort.LockResult currentAuthority(
            Instant observedAt,
            long bindingVersion,
            boolean bindingActive) {
        return new BoardOAuthRefreshAuthorityPort.LockResult(
                mock(BoardOAuthRefreshAuthorityPort.Lease.class),
                true, true, true, true, true, bindingActive,
                BINDING_ID, bindingVersion, CLIENT_ID, principalDigest(),
                BoardOAuthProfile.REQUIRED_SCOPES, observedAt,
                observedAt.plusSeconds(7_200));
    }

    private static BoardOAuthClient activeClient() {
        BoardOAuthClient value = new BoardOAuthClient();
        value.setId(1L);
        value.setClientId(CLIENT_ID);
        value.setIssuerUri(BoardOAuthProfile.ISSUER);
        value.setResourceUri(BoardOAuthProfile.RESOURCE);
        value.setProductCode("FBSIR_INDEPENDENT_BOARD");
        value.setSourceCode("WORKBUDDY");
        value.setConnectorCode("fbs-connector");
        value.setTokenEndpointAuthMethod("none");
        value.setGrantTypesCanonical("authorization_code refresh_token");
        value.setResponseTypesCanonical("code");
        value.setScopeCanonical(BoardOAuthProfile.CANONICAL_SCOPE);
        value.setScopeDigest(scopeDigest());
        value.setMetadataDigest(BoardOAuthCrypto.sha256Ascii("metadata"));
        value.setRegistrationSourceDigest(BoardOAuthCrypto.sha256Ascii("source"));
        value.setStatus("ACTIVE");
        value.setRegisteredAt(Date.from(NOW.minusSeconds(86_400)));
        value.setExpiresAt(Date.from(NOW.plusSeconds(86_400)));
        value.setTerminatedAt(null);
        value.setVersion(2L);
        value.setCreatedAt(Date.from(NOW.minusSeconds(86_400)));
        value.setUpdatedAt(Date.from(NOW.minusSeconds(60)));
        return value;
    }

    private static BoardOAuthTokenFamily activeFamily(long generation, long version) {
        BoardOAuthTokenFamily value = familyBase(generation, version);
        value.setStatus("ACTIVE");
        value.setLifecycleSlot("ACTIVE");
        value.setTerminatedAt(null);
        return value;
    }

    private static BoardOAuthTokenFamily rotatedFamily() {
        return activeFamily(1L, 6L);
    }

    private static BoardOAuthTokenFamily compromisedFamily(Instant terminatedAt) {
        BoardOAuthTokenFamily value = familyBase(1L, 7L);
        value.setStatus("COMPROMISED");
        value.setLifecycleSlot(null);
        value.setTerminatedAt(Date.from(terminatedAt));
        return value;
    }

    private static BoardOAuthTokenFamily familyBase(long generation, long version) {
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
        value.setPrincipalSubjectDigest(principalDigest());
        value.setConsentIntent(BoardOAuthConsentIntent.FIRST_CONNECT);
        value.setBindingId(BINDING_ID);
        value.setBindingVersion(3L);
        value.setCurrentRefreshGeneration(generation);
        value.setIssuedAt(Date.from(NOW.minusSeconds(1_000)));
        value.setActivatedAt(Date.from(NOW.minusSeconds(900)));
        value.setExpiresAt(Date.from(NOW.plusSeconds(86_400)));
        value.setVersion(version);
        value.setCreatedAt(Date.from(NOW.minusSeconds(1_000)));
        value.setUpdatedAt(Date.from(NOW.minusSeconds(10)));
        return value;
    }

    private static List<BoardOAuthToken> rotatedTokens() {
        return List.of(
                token(9L, "ACCESS", 0L, "legacy-access", "ACTIVE",
                        NOW.minusSeconds(900), null, null,
                        NOW.plusSeconds(300), 0L),
                token(10L, "REFRESH", 0L, RAW_REFRESH, "USED",
                        NOW.minusSeconds(900), NOW, null,
                        NOW.plusSeconds(86_400), 3L),
                token(11L, "ACCESS", 1L, ACCESS_TOKEN, "ACTIVE",
                        NOW, null, null, NOW.plusSeconds(600), 0L),
                token(12L, "REFRESH", 1L, NEXT_REFRESH, "ACTIVE",
                        NOW, null, null, NOW.plusSeconds(86_400), 0L));
    }

    private static List<BoardOAuthToken> compromisedTokens(Instant revokedAt) {
        return List.of(
                token(9L, "ACCESS", 0L, "legacy-access", "REVOKED",
                        NOW.minusSeconds(900), null, revokedAt,
                        NOW.plusSeconds(300), 1L),
                token(10L, "REFRESH", 0L, RAW_REFRESH, "USED",
                        NOW.minusSeconds(900), NOW.minusSeconds(100), null,
                        NOW.plusSeconds(86_400), 3L),
                token(11L, "ACCESS", 1L, ACCESS_TOKEN, "REVOKED",
                        NOW.minusSeconds(100), null, revokedAt,
                        NOW.plusSeconds(500), 1L),
                token(12L, "REFRESH", 1L, NEXT_REFRESH, "REVOKED",
                        NOW.minusSeconds(100), null, revokedAt,
                        NOW.plusSeconds(86_400), 1L));
    }

    private static BoardOAuthToken token(
            long id,
            String type,
            long generation,
            String raw,
            String status,
            Instant issuedAt,
            Instant usedAt,
            Instant revokedAt,
            Instant expiresAt,
            long version) {
        BoardOAuthToken value = new BoardOAuthToken();
        value.setId(id);
        value.setTokenDigest(BoardOAuthCrypto.sha256Ascii(raw));
        value.setFamilyId(FAMILY_ID);
        value.setTokenType(type);
        value.setGeneration(generation);
        value.setResourceUri(BoardOAuthProfile.RESOURCE);
        value.setScopeCanonical(BoardOAuthProfile.CANONICAL_SCOPE);
        value.setScopeDigest(scopeDigest());
        value.setStatus(status);
        value.setActiveRefreshSlot(
                "REFRESH".equals(type) && "ACTIVE".equals(status) ? 1 : null);
        value.setIssuedAt(Date.from(issuedAt));
        value.setUsedAt(usedAt == null ? null : Date.from(usedAt));
        value.setRevokedAt(revokedAt == null ? null : Date.from(revokedAt));
        value.setExpiresAt(Date.from(expiresAt));
        value.setVersion(version);
        value.setCreatedAt(Date.from(issuedAt));
        value.setUpdatedAt(Date.from(
                revokedAt == null ? (usedAt == null ? issuedAt : usedAt) : revokedAt));
        return value;
    }

    private static BoardOAuthReceipt creationReceipt() {
        BoardOAuthReceipt value = new BoardOAuthReceipt();
        value.setId(30L);
        value.setReceiptId("k".repeat(43));
        value.setAction("TOKEN_FAMILY_CREATED");
        value.setReceiptFormatVersion(1);
        value.setClientId(CLIENT_ID);
        value.setAuthorizationCodeId(20L);
        value.setFamilyId(FAMILY_ID);
        value.setTenantId(100L);
        value.setMemberId(200L);
        value.setUserId(300L);
        value.setPrincipalSubjectDigest(principalDigest());
        value.setActorType("CLIENT");
        value.setActorUserId(null);
        value.setActorSubjectDigest(BoardOAuthCrypto.sha256Ascii(CLIENT_ID));
        value.setEvidenceLevel("ACTION_COMPLETED");
        value.setPayloadDigest(BoardOAuthCrypto.sha256Ascii("family-created"));
        value.setCreatedAt(Date.from(NOW.minusSeconds(800)));
        return value;
    }

    private static BoardOAuthReceipt rotationReceipt() {
        BoardOAuthReceipt value = new BoardOAuthReceipt();
        value.setId(31L);
        value.setReceiptId("t".repeat(43));
        value.setAction(BoardOAuthRefreshReceiptFactory.TOKEN_FAMILY_ROTATED);
        value.setReceiptFormatVersion(2);
        value.setClientId(CLIENT_ID);
        value.setFamilyId(FAMILY_ID);
        value.setTokenId(10L);
        value.setBindingId(BINDING_ID);
        value.setTenantId(100L);
        value.setMemberId(200L);
        value.setUserId(300L);
        value.setPrincipalSubjectDigest(principalDigest());
        value.setActorType("CLIENT");
        value.setActorUserId(null);
        value.setActorSubjectDigest(BoardOAuthCrypto.sha256Ascii(CLIENT_ID));
        value.setEvidenceLevel("ACTION_COMPLETED");
        value.setSubjectGeneration(0L);
        value.setResultGeneration(1L);
        value.setPayloadDigest(BoardOAuthCrypto.sha256Ascii("rotation-cause"));
        value.setCreatedAt(Date.from(NOW.minusSeconds(100)));
        return value;
    }

    private static BoardOAuthReceipt replayReceipt(
            BoardOAuthTokenFamily compromised,
            BoardOAuthToken replayed,
            BoardOAuthReceipt cause,
            Instant createdAt) {
        BoardOAuthRefreshReceiptFactory.AuthoritySnapshot snapshot =
                new BoardOAuthRefreshReceiptFactory.AuthoritySnapshot(
                        CLIENT_ID, FAMILY_ID, replayed.getId(), BINDING_ID,
                        100L, 200L, 300L, principalDigest(),
                        replayed.getTokenDigest(), replayed.getGeneration(),
                        compromised.getCurrentRefreshGeneration(),
                        compromised.getVersion(), replayed.getVersion(),
                        compromised.getStatus(), replayed.getStatus(),
                        replayed.getUsedAt().toInstant(),
                        compromised.getTerminatedAt().toInstant(),
                        BoardOAuthCrypto.sha256Ascii("before-replay"),
                        BoardOAuthCrypto.sha256Ascii("after-replay"));
        BoardOAuthRefreshReceiptFactory.CausationSnapshot causation =
                new BoardOAuthRefreshReceiptFactory.CausationSnapshot(
                        cause.getReceiptId(), cause.getAction(),
                        cause.getReceiptFormatVersion(), cause.getResultGeneration(),
                        cause.getPayloadDigest(), cause.getCreatedAt().toInstant());
        BoardOAuthReceipt value = BoardOAuthRefreshReceiptFactory.createReplay(
                "p".repeat(43), "q".repeat(43), createdAt, snapshot, causation);
        value.setId(32L);
        return value;
    }

    private static byte[] scopeDigest() {
        return BoardOAuthCrypto.sha256Ascii(BoardOAuthProfile.CANONICAL_SCOPE);
    }

    private static byte[] principalDigest() {
        return BoardOAuthPrincipalSubject.digest(100L, 200L, 300L);
    }

    private static void assertProtocol(
            Runnable invocation,
            String expectedOAuthError,
            String expectedReason) {
        BoardOAuthProtocolException failure = assertThrows(
                BoardOAuthProtocolException.class, invocation::run);
        assertEquals(expectedOAuthError, failure.oauthError());
        assertEquals(expectedReason, failure.reasonCode());
        assertFalse(failure.toString().contains(RAW_REFRESH));
        assertFalse(failure.toString().contains(ACCESS_TOKEN));
        assertFalse(failure.toString().contains(NEXT_REFRESH));
    }
}
