package com.wx.fbsir.business.board.oauth.service;

import com.wx.fbsir.business.board.oauth.BoardOAuthConsentIntent;
import com.wx.fbsir.business.board.oauth.BoardOAuthCrypto;
import com.wx.fbsir.business.board.oauth.BoardOAuthPrincipalSubject;
import com.wx.fbsir.business.board.oauth.BoardOAuthProfile;
import com.wx.fbsir.business.board.oauth.BoardOAuthProtocolException;
import com.wx.fbsir.business.board.oauth.BoardOAuthTokenFamilyCreatedReceiptFactory;
import com.wx.fbsir.business.board.oauth.BoardOAuthTokenMaterialGenerator;
import com.wx.fbsir.business.board.oauth.BoardOAuthTokenSecurityReceiptFactory;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthAuthorizationCode;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthAuthorizationRequest;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthClient;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthReceipt;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthToken;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthTokenFamily;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthTokenExchangeCommand;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthTokenExchangeResult;
import com.wx.fbsir.business.board.oauth.mapper.IndependentBoardOAuthMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IndependentBoardOAuthTokenExchangeSecurityServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-21T00:00:00.123Z");
    private static final String RAW_CODE = "c".repeat(43);
    private static final String VERIFIER = "v".repeat(43);
    private static final String CLIENT_ID = "b".repeat(43);
    private static final String OLD_CLIENT_ID = "a".repeat(43);
    private static final String REDIRECT =
            "http://127.0.0.1:54321/oauth/callback";
    private static final String FAMILY_ID = "f".repeat(43);
    private static final String ACCESS_TOKEN = "a".repeat(43);
    private static final String REFRESH_TOKEN = "r".repeat(43);

    @Test
    void consumedCodeReplayStillCommitsContainmentWhenAuthorityIsInactive() {
        assertConsumedCodeReplayCommitsContainment(
                inactiveAuthority(), activeClient());
    }

    @Test
    void consumedCodeReplayStillCommitsContainmentWhenClientIsRevoked() {
        BoardOAuthClient revoked = activeClient();
        revoked.setStatus("REVOKED");
        revoked.setTerminatedAt(Date.from(NOW.minusSeconds(1)));
        revoked.setVersion(1L);
        assertConsumedCodeReplayCommitsContainment(
                currentAbsentAuthority(), revoked);
    }

    @Test
    void consumedCodeReplayStillCommitsContainmentWhenClientIsExpired() {
        BoardOAuthClient expired = activeClient();
        Instant expiresAt = NOW.minusSeconds(1);
        expired.setRegisteredAt(Date.from(expiresAt.minusSeconds(31L * 86400)));
        expired.setExpiresAt(Date.from(expiresAt));
        assertConsumedCodeReplayCommitsContainment(
                currentAbsentAuthority(), expired);
    }

    @Test
    void consumedReplayEventTimeIsCapturedAfterTokenAndReceiptLocks() {
        assertConsumedCodeReplayCommitsContainment(
                currentAbsentAuthority(), activeClient(), true);
    }

    private void assertConsumedCodeReplayCommitsContainment(
            BoardOAuthTokenExchangeAuthorityPort.LockResult authority,
            BoardOAuthClient lockedClient) {
        assertConsumedCodeReplayCommitsContainment(authority, lockedClient, false);
    }

    private void assertConsumedCodeReplayCommitsContainment(
            BoardOAuthTokenExchangeAuthorityPort.LockResult authority,
            BoardOAuthClient lockedClient,
            boolean advanceAfterBranchLocks) {
        IndependentBoardOAuthMapper mapper = mock(IndependentBoardOAuthMapper.class);
        BoardOAuthTokenMaterialGenerator generator =
                mock(BoardOAuthTokenMaterialGenerator.class);
        Clock branchClock;
        Instant eventAt;
        if (advanceAfterBranchLocks) {
            branchClock = mock(Clock.class);
            eventAt = NOW.plusSeconds(10);
            when(branchClock.instant()).thenReturn(NOW, eventAt);
        } else {
            branchClock = Clock.fixed(NOW, ZoneOffset.UTC);
            eventAt = NOW;
        }
        IndependentBoardOAuthTokenExchangeService service = service(
                mapper, generator, authority, branchClock);
        BoardOAuthAuthorizationRequest request = consumedRequest(10L, NOW);
        BoardOAuthAuthorizationCode code = usedCode(20L, 10L, RAW_CODE, NOW);
        BoardOAuthTokenFamily liveFamily = pendingFamily(
                FAMILY_ID, 20L, NOW, NOW.plusSeconds(30L * 86400));
        List<BoardOAuthToken> liveTokens = activeTokens(
                FAMILY_ID, NOW, liveFamily.getExpiresAt().toInstant(), 2L);
        BoardOAuthReceipt creationReceipt = creationReceipt(
                "z".repeat(43), request, code, liveFamily, liveTokens, NOW, 40L);
        BoardOAuthTokenFamily compromised = terminalFamily(
                liveFamily, "COMPROMISED", eventAt, 1L);
        List<BoardOAuthToken> terminalTokens = terminalTokens(liveTokens, eventAt);
        Map<String, BoardOAuthReceipt> inserted = new HashMap<>();

        stubCommonLocator(mapper, request, code, lockedClient);
        when(mapper.selectPendingTokenFamilySlotForUpdate(
                100L, 200L, "FBSIR_INDEPENDENT_BOARD", "WORKBUDDY",
                "fbs-connector")).thenReturn(liveFamily);
        when(mapper.selectTokenFamilyByOriginAuthorizationCodeForUpdate(
                20L, CLIENT_ID)).thenReturn(liveFamily);
        when(mapper.selectFamilyTokensForUpdate(FAMILY_ID))
                .thenReturn(liveTokens, terminalTokens);
        when(mapper.selectTokenFamilyCreatedReceiptCandidatesForUpdate(
                FAMILY_ID, CLIENT_ID, 20L)).thenReturn(List.of(creationReceipt));
        when(mapper.selectAuthorizationCodeReplayReceiptCandidatesForUpdate(
                FAMILY_ID, CLIENT_ID, 20L)).thenReturn(List.of());
        when(mapper.selectTokenFamilyCompromisedReceiptCandidatesForUpdate(
                FAMILY_ID, CLIENT_ID)).thenReturn(List.of());
        when(mapper.revokeActiveFamilyTokensAtLogicalTime(
                FAMILY_ID, Date.from(eventAt))).thenReturn(2);
        when(mapper.compromiseTokenFamilyIfVersion(
                FAMILY_ID, CLIENT_ID, 0L, Date.from(eventAt))).thenReturn(1);
        when(mapper.selectTokenFamilyForUpdate(FAMILY_ID, CLIENT_ID))
                .thenReturn(compromised);
        when(generator.generateCorrelationId()).thenReturn("q".repeat(43));
        when(generator.generateReceiptId()).thenReturn(
                "j".repeat(43), "k".repeat(43));
        stubReceiptPersistence(mapper, inserted);

        BoardOAuthTokenExchangeOutcome outcome =
                service.exchangeForCommit(command());

        assertTrue(outcome.rejectsAfterCommit());
        assertEquals(
                IndependentBoardOAuthTokenExchangeService.CODE_INVALID,
                outcome.invalidGrantReasonAfterCommit());
        assertEquals(2, inserted.size());
        assertTrue(inserted.values().stream().anyMatch(receipt ->
                "TOKEN_FAMILY_COMPROMISED".equals(receipt.getAction())));
        assertTrue(inserted.values().stream().anyMatch(receipt ->
                "AUTHORIZATION_CODE_REPLAY_DETECTED".equals(
                        receipt.getAction())));
        assertTrue(terminalTokens.stream().noneMatch(token ->
                "ACTIVE".equals(token.getStatus())));

        InOrder order = inOrder(mapper);
        order.verify(mapper).selectActiveTokenFamilySlotForUpdate(
                100L, 200L, "FBSIR_INDEPENDENT_BOARD", "WORKBUDDY",
                "fbs-connector");
        order.verify(mapper).selectPendingTokenFamilySlotForUpdate(
                100L, 200L, "FBSIR_INDEPENDENT_BOARD", "WORKBUDDY",
                "fbs-connector");
        order.verify(mapper).selectTokenFamilyByOriginAuthorizationCodeForUpdate(
                20L, CLIENT_ID);
        order.verify(mapper).selectFamilyTokensForUpdate(FAMILY_ID);
        order.verify(mapper).selectTokenFamilyCreatedReceiptCandidatesForUpdate(
                FAMILY_ID, CLIENT_ID, 20L);
        order.verify(mapper).selectAuthorizationCodeReplayReceiptCandidatesForUpdate(
                FAMILY_ID, CLIENT_ID, 20L);
        order.verify(mapper).selectTokenFamilyCompromisedReceiptCandidatesForUpdate(
                FAMILY_ID, CLIENT_ID);
        order.verify(mapper).revokeActiveFamilyTokensAtLogicalTime(
                FAMILY_ID, Date.from(eventAt));
        order.verify(mapper).compromiseTokenFamilyIfVersion(
                FAMILY_ID, CLIENT_ID, 0L, Date.from(eventAt));
        if (advanceAfterBranchLocks) {
            InOrder temporalOrder = inOrder(branchClock, mapper);
            temporalOrder.verify(branchClock).instant();
            temporalOrder.verify(mapper).selectFamilyTokensForUpdate(FAMILY_ID);
            temporalOrder.verify(mapper)
                    .selectTokenFamilyCreatedReceiptCandidatesForUpdate(
                            FAMILY_ID, CLIENT_ID, 20L);
            temporalOrder.verify(mapper)
                    .selectAuthorizationCodeReplayReceiptCandidatesForUpdate(
                            FAMILY_ID, CLIENT_ID, 20L);
            temporalOrder.verify(mapper)
                    .selectTokenFamilyCompromisedReceiptCandidatesForUpdate(
                            FAMILY_ID, CLIENT_ID);
            temporalOrder.verify(branchClock).instant();
        }
    }

    @Test
    void aConcurrentReplayLoserAcceptsOnlyTheLockedCommittedReceiptAndDoesNotRewrite() {
        IndependentBoardOAuthMapper mapper = mock(IndependentBoardOAuthMapper.class);
        BoardOAuthTokenMaterialGenerator generator =
                mock(BoardOAuthTokenMaterialGenerator.class);
        IndependentBoardOAuthTokenExchangeService service = service(mapper, generator);
        BoardOAuthAuthorizationRequest request = consumedRequest(10L, NOW);
        BoardOAuthAuthorizationCode code = usedCode(20L, 10L, RAW_CODE, NOW);
        BoardOAuthTokenFamily origin = pendingFamily(
                FAMILY_ID, 20L, NOW, NOW.plusSeconds(30L * 86400));
        BoardOAuthTokenFamily compromised = terminalFamily(
                origin, "COMPROMISED", NOW, 1L);
        List<BoardOAuthToken> liveTokens = activeTokens(
                FAMILY_ID, NOW, origin.getExpiresAt().toInstant(), 2L);
        List<BoardOAuthToken> terminalTokens = terminalTokens(liveTokens, NOW);
        BoardOAuthReceipt creationReceipt = creationReceipt(
                "z".repeat(43), request, code, origin, liveTokens, NOW, 40L);
        BoardOAuthReceipt replayReceipt =
                BoardOAuthTokenSecurityReceiptFactory
                        .authorizationCodeReplayDetected(
                                "k".repeat(43),
                                "q".repeat(43),
                                NOW,
                                request,
                                code,
                                compromised,
                                 terminalTokens,
                                 creationReceipt);
        replayReceipt.setId(41L);
        BoardOAuthReceipt compromisedReceipt =
                BoardOAuthTokenSecurityReceiptFactory.tokenFamilyCompromised(
                        "j".repeat(43),
                        "q".repeat(43),
                        NOW,
                        request,
                        code,
                        compromised,
                        terminalTokens,
                        creationReceipt);
        compromisedReceipt.setId(42L);

        stubCommonLocator(mapper, request, code);
        when(mapper.selectTokenFamilyByOriginAuthorizationCodeForUpdate(
                20L, CLIENT_ID)).thenReturn(compromised);
        when(mapper.selectFamilyTokensForUpdate(FAMILY_ID))
                .thenReturn(terminalTokens);
        when(mapper.selectTokenFamilyCreatedReceiptCandidatesForUpdate(
                FAMILY_ID, CLIENT_ID, 20L)).thenReturn(List.of(creationReceipt));
        when(mapper.selectAuthorizationCodeReplayReceiptCandidatesForUpdate(
                FAMILY_ID, CLIENT_ID, 20L)).thenReturn(List.of(replayReceipt));
        when(mapper.selectTokenFamilyCompromisedReceiptCandidatesForUpdate(
                FAMILY_ID, CLIENT_ID)).thenReturn(List.of(compromisedReceipt));

        BoardOAuthTokenExchangeOutcome outcome =
                service.exchangeForCommit(command());

        assertTrue(outcome.rejectsAfterCommit());
        verify(mapper, never()).revokeActiveFamilyTokensAtLogicalTime(any(), any());
        verify(mapper, never()).compromiseTokenFamilyIfVersion(
                any(), any(), any(), any());
        verify(mapper, never()).insertReceipt(any());
        verify(generator, never()).generateReceiptId();
    }

    @Test
    void consumedReplayRequiresTheCompleteImmutableAndTemporalLineage() {
        assertReplayLineageDrift((request, code) ->
                request.setRequestHandleDigest(null));
        assertReplayLineageDrift((request, code) -> request.setStateDigest(null));
        assertReplayLineageDrift((request, code) -> {
            request.setRedirectUri("http://127.0.0.1:54322/oauth/callback");
            code.setRedirectUri(request.getRedirectUri());
        });
        assertReplayLineageDrift((request, code) -> {
            request.setCodeChallenge("not-a-canonical-s256-challenge");
            code.setCodeChallenge(request.getCodeChallenge());
        });
        assertReplayLineageDrift((request, code) ->
                request.setDeniedAt(Date.from(NOW.minusSeconds(1))));
        assertReplayLineageDrift((request, code) -> request.setCreatedAt(null));
        assertReplayLineageDrift((request, code) ->
                request.setUpdatedAt(Date.from(NOW.minusSeconds(6))));
        assertReplayLineageDrift((request, code) -> {
            request.setIssuerUri("https://issuer.invalid");
            code.setIssuerUri(request.getIssuerUri());
        });
        assertReplayLineageDrift((request, code) -> code.setCreatedAt(null));
        assertReplayLineageDrift((request, code) ->
                code.setUpdatedAt(Date.from(NOW.minusSeconds(6))));
        assertReplayLineageDrift((request, code) ->
                code.setRevokedAt(Date.from(NOW)));
        assertReplayLineageDrift((request, code) -> {
            byte[] wrongSubject = new byte[32];
            request.setPrincipalSubjectDigest(wrongSubject);
            code.setPrincipalSubjectDigest(wrongSubject.clone());
        });
        assertReplayLineageDrift((request, code) -> {
            request.setConsentIntent(null);
            code.setConsentIntent(null);
        });
    }

    @Test
    void terminalCompromisedFamilyRequiresExactlyOneValidCompromiseReceipt() {
        assertTerminalCompromiseReceiptRejected(List.of(), null);
        assertTerminalCompromiseReceiptRejected(null, "duplicate");
        assertTerminalCompromiseReceiptRejected(null, "tampered");
    }

    @Test
    void aCrossClientPendingFamilyIsRevokedWithSuccessorCausalityBeforeInsert() {
        IndependentBoardOAuthMapper mapper = mock(IndependentBoardOAuthMapper.class);
        BoardOAuthTokenMaterialGenerator generator =
                mock(BoardOAuthTokenMaterialGenerator.class);
        IndependentBoardOAuthTokenExchangeService service = service(mapper, generator);
        BoardOAuthAuthorizationRequest approved = approvedRequest(10L);
        BoardOAuthAuthorizationRequest consumed = consumedRequest(10L, NOW);
        BoardOAuthAuthorizationCode activeCode = activeCode(20L, 10L, RAW_CODE);
        BoardOAuthAuthorizationCode usedCode = usedCode(20L, 10L, RAW_CODE, NOW);

        Instant oldIssued = NOW.minusSeconds(60);
        BoardOAuthAuthorizationRequest oldRequest = consumedRequest(9L, oldIssued);
        BoardOAuthAuthorizationCode oldCode = usedCode(
                19L, 9L, "d".repeat(43), oldIssued);
        BoardOAuthTokenFamily oldPending = pendingFamily(
                "p".repeat(43), 19L, oldIssued,
                NOW.plusSeconds(29L * 86400));
        oldRequest.setClientId(OLD_CLIENT_ID);
        oldCode.setClientId(OLD_CLIENT_ID);
        oldPending.setClientId(OLD_CLIENT_ID);
        List<BoardOAuthToken> oldActiveTokens = activeTokens(
                oldPending.getFamilyId(), oldIssued,
                oldPending.getExpiresAt().toInstant(), 50L);
        BoardOAuthReceipt oldCreationReceipt = creationReceipt(
                "y".repeat(43), oldRequest, oldCode, oldPending,
                oldActiveTokens, oldIssued, 60L);
        BoardOAuthTokenFamily oldRevoked = terminalFamily(
                oldPending, "REVOKED", NOW, 1L);
        oldRevoked.setClientId(OLD_CLIENT_ID);
        List<BoardOAuthToken> oldTerminalTokens =
                terminalTokens(oldActiveTokens, NOW);

        BoardOAuthTokenFamily newPersisted = pendingFamily(
                FAMILY_ID, 20L, NOW, NOW.plusSeconds(30L * 86400));
        List<BoardOAuthToken> newTokens = activeTokens(
                FAMILY_ID, NOW, newPersisted.getExpiresAt().toInstant(), 70L);
        Map<String, BoardOAuthReceipt> inserted = new HashMap<>();

        IndependentBoardOAuthMapper.TokenExchangeLockLocator locator =
                locator(20L, 10L);
        locator.setPendingClientId(OLD_CLIENT_ID);
        when(mapper.selectTokenExchangeLockLocatorByDigest(any(byte[].class)))
                .thenReturn(locator);
        when(mapper.selectClientsForUpdate(List.of(OLD_CLIENT_ID, CLIENT_ID)))
                .thenReturn(List.of(
                        historicalClient(OLD_CLIENT_ID, 2L),
                        activeClient()));
        when(mapper.selectAuthorizationRequestByIdAndClientForUpdate(10L, CLIENT_ID))
                .thenReturn(approved, consumed);
        when(mapper.selectAuthorizationCodeForUpdate(
                eq(20L), eq(CLIENT_ID), any(byte[].class)))
                .thenReturn(activeCode, usedCode);
        when(mapper.selectPendingTokenFamilySlotForUpdate(
                100L, 200L, "FBSIR_INDEPENDENT_BOARD", "WORKBUDDY",
                "fbs-connector")).thenReturn(oldPending);
        when(mapper.selectTokenFamilyByOriginAuthorizationCodeForUpdate(
                20L, CLIENT_ID)).thenReturn(null);
        when(mapper.selectFamilyTokensForUpdate(oldPending.getFamilyId()))
                .thenReturn(oldActiveTokens, oldTerminalTokens);
        when(mapper.selectTokenFamilyCreatedReceiptCandidatesForUpdate(
                oldPending.getFamilyId(), OLD_CLIENT_ID, 19L))
                .thenReturn(List.of(oldCreationReceipt));
        when(mapper.selectTokenFamilyRevokedReceiptCandidatesForUpdate(
                oldPending.getFamilyId(), OLD_CLIENT_ID)).thenReturn(List.of());
        when(mapper.revokeActiveFamilyTokensAtLogicalTime(
                oldPending.getFamilyId(), Date.from(NOW))).thenReturn(2);
        when(mapper.revokeTokenFamilyIfVersion(
                oldPending.getFamilyId(), OLD_CLIENT_ID, 0L, Date.from(NOW)))
                .thenReturn(1);
        when(mapper.selectTokenFamilyForUpdate(
                oldPending.getFamilyId(), OLD_CLIENT_ID)).thenReturn(oldRevoked);

        when(mapper.consumeAuthorizationCodeForClientIfVersion(
                20L, CLIENT_ID, 0L, Date.from(NOW))).thenReturn(1);
        when(mapper.consumeAuthorizationRequestIfVersion(approved, 1L))
                .thenReturn(1);
        when(mapper.insertTokenFamily(any())).thenAnswer(invocation -> {
            BoardOAuthTokenFamily value = invocation.getArgument(0);
            value.setId(69L);
            return 1;
        });
        when(mapper.insertToken(any())).thenAnswer(invocation -> {
            BoardOAuthToken value = invocation.getArgument(0);
            value.setId("ACCESS".equals(value.getTokenType()) ? 70L : 71L);
            return 1;
        });
        when(mapper.selectTokenFamilyForUpdate(FAMILY_ID, CLIENT_ID))
                .thenReturn(newPersisted);
        when(mapper.selectFamilyTokensForUpdate(FAMILY_ID)).thenReturn(newTokens);
        when(mapper.selectTokenFamilyCreatedReceiptCandidatesForUpdate(
                FAMILY_ID, CLIENT_ID, 20L)).thenReturn(List.of());

        when(generator.generateReceiptId()).thenReturn(
                "s".repeat(43), "i".repeat(43));
        when(generator.generateCorrelationId()).thenReturn(
                "t".repeat(43), "o".repeat(43));
        when(generator.generateFamilyId()).thenReturn(FAMILY_ID);
        when(generator.generateAccessToken()).thenReturn(ACCESS_TOKEN);
        when(generator.generateRefreshToken()).thenReturn(REFRESH_TOKEN);
        stubReceiptPersistence(mapper, inserted);

        BoardOAuthTokenExchangeResult result = exchange(service, command());

        assertEquals(ACCESS_TOKEN, result.rawAccessToken());
        assertEquals(REFRESH_TOKEN, result.rawRefreshToken());
        assertEquals(2, inserted.size());
        BoardOAuthReceipt revokedReceipt = inserted.values().stream()
                .filter(receipt -> "TOKEN_FAMILY_REVOKED".equals(
                        receipt.getAction()))
                .findFirst()
                .orElseThrow();
        assertEquals(oldPending.getFamilyId(), revokedReceipt.getFamilyId());
        assertEquals(OLD_CLIENT_ID, revokedReceipt.getClientId());
        assertArrayEquals(
                BoardOAuthCrypto.sha256Ascii(CLIENT_ID),
                revokedReceipt.getActorSubjectDigest());
        assertEquals(result.correlationId(), revokedReceipt.getCorrelationId());
        BoardOAuthReceipt successorReceipt = inserted.values().stream()
                .filter(receipt -> "TOKEN_FAMILY_CREATED".equals(
                        receipt.getAction()))
                .findFirst()
                .orElseThrow();
        assertEquals(
                revokedReceipt.getCorrelationId(),
                successorReceipt.getCorrelationId());
        assertTrue(oldTerminalTokens.stream().noneMatch(token ->
                "ACTIVE".equals(token.getStatus())));

        InOrder order = inOrder(mapper);
        order.verify(mapper).selectPendingTokenFamilySlotForUpdate(
                100L, 200L, "FBSIR_INDEPENDENT_BOARD", "WORKBUDDY",
                "fbs-connector");
        order.verify(mapper).selectTokenFamilyByOriginAuthorizationCodeForUpdate(
                20L, CLIENT_ID);
        order.verify(mapper).revokeActiveFamilyTokensAtLogicalTime(
                oldPending.getFamilyId(), Date.from(NOW));
        order.verify(mapper).revokeTokenFamilyIfVersion(
                oldPending.getFamilyId(), OLD_CLIENT_ID, 0L, Date.from(NOW));
        order.verify(mapper).insertReceipt(eq(revokedReceipt));
        order.verify(mapper).consumeAuthorizationCodeForClientIfVersion(
                20L, CLIENT_ID, 0L, Date.from(NOW));
        order.verify(mapper).insertTokenFamily(any());
    }

    @Test
    void pendingReceiptLockWaitCannotCarryFreshIssuancePastCodeExpiry() {
        IndependentBoardOAuthMapper mapper = mock(IndependentBoardOAuthMapper.class);
        BoardOAuthTokenMaterialGenerator generator =
                mock(BoardOAuthTokenMaterialGenerator.class);
        Clock branchClock = mock(Clock.class);
        when(branchClock.instant()).thenReturn(NOW, NOW.plusSeconds(31));
        IndependentBoardOAuthTokenExchangeService service = service(
                mapper, generator, currentAbsentAuthority(), branchClock);

        BoardOAuthAuthorizationRequest approved = approvedRequest(10L);
        BoardOAuthAuthorizationCode activeCode = activeCode(20L, 10L, RAW_CODE);
        Instant oldIssued = NOW.minusSeconds(60);
        BoardOAuthAuthorizationRequest oldRequest = consumedRequest(9L, oldIssued);
        BoardOAuthAuthorizationCode oldCode = usedCode(
                19L, 9L, "d".repeat(43), oldIssued);
        BoardOAuthTokenFamily oldPending = pendingFamily(
                "p".repeat(43), 19L, oldIssued,
                NOW.plusSeconds(29L * 86400));
        List<BoardOAuthToken> oldTokens = activeTokens(
                oldPending.getFamilyId(), oldIssued,
                oldPending.getExpiresAt().toInstant(), 50L);
        BoardOAuthReceipt oldCreation = creationReceipt(
                "y".repeat(43), oldRequest, oldCode, oldPending,
                oldTokens, oldIssued, 60L);

        when(mapper.selectTokenExchangeLockLocatorByDigest(any(byte[].class)))
                .thenReturn(locator(20L, 10L));
        when(mapper.selectClientsForUpdate(List.of(CLIENT_ID)))
                .thenReturn(List.of(activeClient()));
        when(mapper.selectAuthorizationRequestByIdAndClientForUpdate(10L, CLIENT_ID))
                .thenReturn(approved);
        when(mapper.selectAuthorizationCodeForUpdate(
                eq(20L), eq(CLIENT_ID), any(byte[].class)))
                .thenReturn(activeCode);
        when(mapper.selectPendingTokenFamilySlotForUpdate(
                100L, 200L, "FBSIR_INDEPENDENT_BOARD", "WORKBUDDY",
                "fbs-connector")).thenReturn(oldPending);
        when(mapper.selectTokenFamilyByOriginAuthorizationCodeForUpdate(
                20L, CLIENT_ID)).thenReturn(null);
        when(mapper.selectFamilyTokensForUpdate(oldPending.getFamilyId()))
                .thenReturn(oldTokens);
        when(mapper.selectTokenFamilyCreatedReceiptCandidatesForUpdate(
                oldPending.getFamilyId(), CLIENT_ID, 19L))
                .thenReturn(List.of(oldCreation));
        when(mapper.selectTokenFamilyRevokedReceiptCandidatesForUpdate(
                oldPending.getFamilyId(), CLIENT_ID)).thenReturn(List.of());
        when(generator.generateFamilyId()).thenReturn(FAMILY_ID);
        when(generator.generateAccessToken()).thenReturn(ACCESS_TOKEN);
        when(generator.generateRefreshToken()).thenReturn(REFRESH_TOKEN);
        when(generator.generateReceiptId()).thenReturn("i".repeat(43));
        when(generator.generateCorrelationId()).thenReturn("o".repeat(43));

        BoardOAuthProtocolException failure = assertThrows(
                BoardOAuthProtocolException.class,
                () -> service.exchangeForCommit(command()));

        assertEquals("invalid_grant", failure.oauthError());
        assertEquals(IndependentBoardOAuthTokenExchangeService.CODE_INVALID,
                failure.reasonCode());
        verify(mapper, never()).revokeActiveFamilyTokensAtLogicalTime(any(), any());
        verify(mapper, never()).consumeAuthorizationCodeForClientIfVersion(
                any(), any(), any(), any());
        verify(mapper, never()).insertTokenFamily(any());
        verify(mapper, never()).insertReceipt(any());

        InOrder order = inOrder(branchClock, mapper);
        order.verify(branchClock).instant();
        order.verify(mapper).selectFamilyTokensForUpdate(oldPending.getFamilyId());
        order.verify(mapper).selectTokenFamilyCreatedReceiptCandidatesForUpdate(
                oldPending.getFamilyId(), CLIENT_ID, 19L);
        order.verify(mapper).selectTokenFamilyRevokedReceiptCandidatesForUpdate(
                oldPending.getFamilyId(), CLIENT_ID);
        order.verify(branchClock).instant();
    }

    @Test
    void successorInsertFailureOccursAfterCleanupAndPublishesNoTokenOrCreationReceipt() {
        IndependentBoardOAuthMapper mapper = mock(IndependentBoardOAuthMapper.class);
        BoardOAuthTokenMaterialGenerator generator =
                mock(BoardOAuthTokenMaterialGenerator.class);
        IndependentBoardOAuthTokenExchangeService service = service(mapper, generator);
        BoardOAuthAuthorizationRequest approved = approvedRequest(10L);
        BoardOAuthAuthorizationRequest consumed = consumedRequest(10L, NOW);
        BoardOAuthAuthorizationCode activeCode = activeCode(20L, 10L, RAW_CODE);
        BoardOAuthAuthorizationCode usedCode = usedCode(20L, 10L, RAW_CODE, NOW);

        Instant oldIssued = NOW.minusSeconds(60);
        BoardOAuthAuthorizationRequest oldRequest = consumedRequest(9L, oldIssued);
        BoardOAuthAuthorizationCode oldCode = usedCode(
                19L, 9L, "d".repeat(43), oldIssued);
        BoardOAuthTokenFamily oldPending = pendingFamily(
                "p".repeat(43), 19L, oldIssued,
                NOW.plusSeconds(29L * 86400));
        List<BoardOAuthToken> oldTokens = activeTokens(
                oldPending.getFamilyId(), oldIssued,
                oldPending.getExpiresAt().toInstant(), 50L);
        BoardOAuthReceipt oldCreation = creationReceipt(
                "y".repeat(43), oldRequest, oldCode, oldPending,
                oldTokens, oldIssued, 60L);
        BoardOAuthTokenFamily revoked = terminalFamily(
                oldPending, "REVOKED", NOW, 1L);
        List<BoardOAuthToken> terminalTokens = terminalTokens(oldTokens, NOW);
        Map<String, BoardOAuthReceipt> inserted = new HashMap<>();

        when(mapper.selectTokenExchangeLockLocatorByDigest(any(byte[].class)))
                .thenReturn(locator(20L, 10L));
        when(mapper.selectClientsForUpdate(List.of(CLIENT_ID)))
                .thenReturn(List.of(activeClient()));
        when(mapper.selectAuthorizationRequestByIdAndClientForUpdate(10L, CLIENT_ID))
                .thenReturn(approved, consumed);
        when(mapper.selectAuthorizationCodeForUpdate(
                eq(20L), eq(CLIENT_ID), any(byte[].class)))
                .thenReturn(activeCode, usedCode);
        when(mapper.selectPendingTokenFamilySlotForUpdate(
                100L, 200L, "FBSIR_INDEPENDENT_BOARD", "WORKBUDDY",
                "fbs-connector")).thenReturn(oldPending);
        when(mapper.selectTokenFamilyByOriginAuthorizationCodeForUpdate(
                20L, CLIENT_ID)).thenReturn(null);
        when(mapper.selectFamilyTokensForUpdate(oldPending.getFamilyId()))
                .thenReturn(oldTokens, terminalTokens);
        when(mapper.selectTokenFamilyCreatedReceiptCandidatesForUpdate(
                oldPending.getFamilyId(), CLIENT_ID, 19L))
                .thenReturn(List.of(oldCreation));
        when(mapper.selectTokenFamilyRevokedReceiptCandidatesForUpdate(
                oldPending.getFamilyId(), CLIENT_ID)).thenReturn(List.of());
        when(mapper.revokeActiveFamilyTokensAtLogicalTime(
                oldPending.getFamilyId(), Date.from(NOW))).thenReturn(2);
        when(mapper.revokeTokenFamilyIfVersion(
                oldPending.getFamilyId(), CLIENT_ID, 0L, Date.from(NOW)))
                .thenReturn(1);
        when(mapper.selectTokenFamilyForUpdate(
                oldPending.getFamilyId(), CLIENT_ID)).thenReturn(revoked);
        when(mapper.consumeAuthorizationCodeForClientIfVersion(
                20L, CLIENT_ID, 0L, Date.from(NOW))).thenReturn(1);
        when(mapper.consumeAuthorizationRequestIfVersion(approved, 1L))
                .thenReturn(1);
        when(mapper.insertTokenFamily(any()))
                .thenThrow(new DuplicateKeyException("successor collision"));
        when(generator.generateFamilyId()).thenReturn(FAMILY_ID);
        when(generator.generateAccessToken()).thenReturn(ACCESS_TOKEN);
        when(generator.generateRefreshToken()).thenReturn(REFRESH_TOKEN);
        when(generator.generateReceiptId()).thenReturn(
                "s".repeat(43), "i".repeat(43));
        when(generator.generateCorrelationId()).thenReturn("t".repeat(43));
        stubReceiptPersistence(mapper, inserted);

        BoardOAuthProtocolException failure = assertThrows(
                BoardOAuthProtocolException.class,
                () -> service.exchangeForCommit(command()));

        assertEquals(IndependentBoardOAuthTokenExchangeService.CONFLICT,
                failure.reasonCode());
        assertEquals(1, inserted.size());
        assertTrue(inserted.values().stream().allMatch(receipt ->
                "TOKEN_FAMILY_REVOKED".equals(receipt.getAction())));
        InOrder order = inOrder(mapper);
        order.verify(mapper).revokeTokenFamilyIfVersion(
                oldPending.getFamilyId(), CLIENT_ID, 0L, Date.from(NOW));
        order.verify(mapper).insertReceipt(any());
        order.verify(mapper).consumeAuthorizationCodeForClientIfVersion(
                20L, CLIENT_ID, 0L, Date.from(NOW));
        order.verify(mapper).consumeAuthorizationRequestIfVersion(approved, 1L);
        order.verify(mapper).insertTokenFamily(any());
        verify(mapper, never()).insertToken(any());
    }

    private static IndependentBoardOAuthTokenExchangeService service(
            IndependentBoardOAuthMapper mapper,
            BoardOAuthTokenMaterialGenerator generator) {
        return service(mapper, generator, currentAbsentAuthority());
    }

    private static IndependentBoardOAuthTokenExchangeService service(
            IndependentBoardOAuthMapper mapper,
            BoardOAuthTokenMaterialGenerator generator,
            BoardOAuthTokenExchangeAuthorityPort.LockResult authority) {
        return service(
                mapper,
                generator,
                authority,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static IndependentBoardOAuthTokenExchangeService service(
            IndependentBoardOAuthMapper mapper,
            BoardOAuthTokenMaterialGenerator generator,
            BoardOAuthTokenExchangeAuthorityPort.LockResult authority,
            Clock clock) {
        BoardOAuthTokenExchangeAuthorityPort authorityPort =
                mock(BoardOAuthTokenExchangeAuthorityPort.class);
        when(authorityPort.lockForTokenExchange(
                any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(authority);
        return new IndependentBoardOAuthTokenExchangeService(
                mapper,
                authorityPort,
                generator,
                clock);
    }

    private static BoardOAuthTokenExchangeResult exchange(
            IndependentBoardOAuthTokenExchangeService service,
            BoardOAuthTokenExchangeCommand command) {
        return service.exchangeForCommit(command).requireCompletedResult();
    }

    private static void assertReplayLineageDrift(
            BiConsumer<BoardOAuthAuthorizationRequest,
                    BoardOAuthAuthorizationCode> mutator) {
        IndependentBoardOAuthMapper mapper = mock(IndependentBoardOAuthMapper.class);
        BoardOAuthTokenMaterialGenerator generator =
                mock(BoardOAuthTokenMaterialGenerator.class);
        IndependentBoardOAuthTokenExchangeService service = service(mapper, generator);
        BoardOAuthAuthorizationRequest request = consumedRequest(10L, NOW);
        BoardOAuthAuthorizationCode code = usedCode(20L, 10L, RAW_CODE, NOW);
        BoardOAuthTokenFamily family = pendingFamily(
                FAMILY_ID, 20L, NOW, NOW.plusSeconds(30L * 86400));
        List<BoardOAuthToken> tokens = activeTokens(
                FAMILY_ID, NOW, family.getExpiresAt().toInstant(), 2L);
        BoardOAuthReceipt creationReceipt = creationReceipt(
                "z".repeat(43), request, code, family, tokens, NOW, 40L);
        mutator.accept(request, code);

        stubCommonLocator(mapper, request, code);
        when(mapper.selectPendingTokenFamilySlotForUpdate(
                100L, 200L, "FBSIR_INDEPENDENT_BOARD", "WORKBUDDY",
                "fbs-connector")).thenReturn(family);
        when(mapper.selectTokenFamilyByOriginAuthorizationCodeForUpdate(
                20L, CLIENT_ID)).thenReturn(family);
        when(mapper.selectFamilyTokensForUpdate(FAMILY_ID)).thenReturn(tokens);
        when(mapper.selectTokenFamilyCreatedReceiptCandidatesForUpdate(
                FAMILY_ID, CLIENT_ID, 20L)).thenReturn(List.of(creationReceipt));
        when(mapper.selectAuthorizationCodeReplayReceiptCandidatesForUpdate(
                FAMILY_ID, CLIENT_ID, 20L)).thenReturn(List.of());
        when(mapper.selectTokenFamilyCompromisedReceiptCandidatesForUpdate(
                FAMILY_ID, CLIENT_ID)).thenReturn(List.of());

        BoardOAuthProtocolException failure = assertThrows(
                BoardOAuthProtocolException.class,
                () -> service.exchangeForCommit(command()));
        assertEquals(
                IndependentBoardOAuthTokenExchangeService.REPLAY_LINEAGE_DRIFT,
                failure.reasonCode());
        verify(mapper, never()).revokeActiveFamilyTokensAtLogicalTime(any(), any());
        verify(mapper, never()).compromiseTokenFamilyIfVersion(
                any(), any(), any(), any());
        verify(mapper, never()).insertReceipt(any());
    }

    private static void assertTerminalCompromiseReceiptRejected(
            List<BoardOAuthReceipt> fixedCandidates,
            String mode) {
        IndependentBoardOAuthMapper mapper = mock(IndependentBoardOAuthMapper.class);
        BoardOAuthTokenMaterialGenerator generator =
                mock(BoardOAuthTokenMaterialGenerator.class);
        IndependentBoardOAuthTokenExchangeService service = service(mapper, generator);
        BoardOAuthAuthorizationRequest request = consumedRequest(10L, NOW);
        BoardOAuthAuthorizationCode code = usedCode(20L, 10L, RAW_CODE, NOW);
        BoardOAuthTokenFamily origin = pendingFamily(
                FAMILY_ID, 20L, NOW, NOW.plusSeconds(30L * 86400));
        BoardOAuthTokenFamily compromised = terminalFamily(
                origin, "COMPROMISED", NOW, 1L);
        List<BoardOAuthToken> liveTokens = activeTokens(
                FAMILY_ID, NOW, origin.getExpiresAt().toInstant(), 2L);
        List<BoardOAuthToken> terminalTokens = terminalTokens(liveTokens, NOW);
        BoardOAuthReceipt creationReceipt = creationReceipt(
                "z".repeat(43), request, code, origin, liveTokens, NOW, 40L);
        BoardOAuthReceipt compromisedReceipt =
                BoardOAuthTokenSecurityReceiptFactory.tokenFamilyCompromised(
                        "j".repeat(43), "q".repeat(43), NOW,
                        request, code, compromised, terminalTokens, creationReceipt);
        compromisedReceipt.setId(42L);
        List<BoardOAuthReceipt> candidates = fixedCandidates;
        if ("duplicate".equals(mode)) {
            candidates = List.of(compromisedReceipt, compromisedReceipt);
        } else if ("tampered".equals(mode)) {
            compromisedReceipt.setPayloadDigest(new byte[32]);
            candidates = List.of(compromisedReceipt);
        }

        stubCommonLocator(mapper, request, code);
        when(mapper.selectTokenFamilyByOriginAuthorizationCodeForUpdate(
                20L, CLIENT_ID)).thenReturn(compromised);
        when(mapper.selectFamilyTokensForUpdate(FAMILY_ID)).thenReturn(terminalTokens);
        when(mapper.selectTokenFamilyCreatedReceiptCandidatesForUpdate(
                FAMILY_ID, CLIENT_ID, 20L)).thenReturn(List.of(creationReceipt));
        when(mapper.selectAuthorizationCodeReplayReceiptCandidatesForUpdate(
                FAMILY_ID, CLIENT_ID, 20L)).thenReturn(List.of());
        when(mapper.selectTokenFamilyCompromisedReceiptCandidatesForUpdate(
                FAMILY_ID, CLIENT_ID)).thenReturn(candidates);

        BoardOAuthProtocolException failure = assertThrows(
                BoardOAuthProtocolException.class,
                () -> service.exchangeForCommit(command()));
        String expectedReason = "tampered".equals(mode)
                ? BoardOAuthTokenSecurityReceiptFactory.RECEIPT_INVALID
                : IndependentBoardOAuthTokenExchangeService
                        .SECURITY_RECEIPT_CARDINALITY_INVALID;
        assertEquals(expectedReason, failure.reasonCode());
        verify(mapper, never()).insertReceipt(any());
        verify(generator, never()).generateReceiptId();
    }

    private static void stubCommonLocator(
            IndependentBoardOAuthMapper mapper,
            BoardOAuthAuthorizationRequest request,
            BoardOAuthAuthorizationCode code) {
        stubCommonLocator(mapper, request, code, activeClient());
    }

    private static void stubCommonLocator(
            IndependentBoardOAuthMapper mapper,
            BoardOAuthAuthorizationRequest request,
            BoardOAuthAuthorizationCode code,
            BoardOAuthClient lockedClient) {
        when(mapper.selectTokenExchangeLockLocatorByDigest(any(byte[].class)))
                .thenReturn(locator(code.getId(), request.getId()));
        when(mapper.selectClientsForUpdate(List.of(CLIENT_ID)))
                .thenReturn(List.of(lockedClient));
        when(mapper.selectAuthorizationRequestByIdAndClientForUpdate(
                request.getId(), CLIENT_ID)).thenReturn(request);
        when(mapper.selectAuthorizationCodeForUpdate(
                eq(code.getId()), eq(CLIENT_ID), any(byte[].class)))
                .thenReturn(code);
    }

    private static void stubReceiptPersistence(
            IndependentBoardOAuthMapper mapper,
            Map<String, BoardOAuthReceipt> persisted) {
        when(mapper.insertReceipt(any())).thenAnswer(invocation -> {
            BoardOAuthReceipt receipt = invocation.getArgument(0);
            receipt.setId(100L + persisted.size());
            persisted.put(receipt.getReceiptId(), receipt);
            return 1;
        });
        when(mapper.selectReceiptByReceiptId(any())).thenAnswer(invocation ->
                persisted.get(invocation.getArgument(0)));
    }

    private static BoardOAuthTokenExchangeCommand command() {
        return new BoardOAuthTokenExchangeCommand(
                RAW_CODE, VERIFIER, CLIENT_ID, REDIRECT,
                BoardOAuthProfile.RESOURCE);
    }

    private static BoardOAuthClient activeClient() {
        BoardOAuthClient value = new BoardOAuthClient();
        value.setId(1L);
        value.setClientId(CLIENT_ID);
        value.setClientName(
                IndependentBoardOAuthClientRegistrationService.NEUTRAL_CLIENT_NAME);
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

    private static BoardOAuthAuthorizationRequest approvedRequest(Long id) {
        BoardOAuthAuthorizationRequest value = requestBase(id);
        value.setConsentIntent(BoardOAuthConsentIntent.FIRST_CONNECT);
        value.setStatus("APPROVED");
        value.setApprovedAt(Date.from(NOW.minusSeconds(30)));
        value.setVersion(1L);
        value.setUpdatedAt(Date.from(NOW.minusSeconds(30)));
        return value;
    }

    private static BoardOAuthAuthorizationRequest consumedRequest(
            Long id,
            Instant consumedAt) {
        BoardOAuthAuthorizationRequest value = requestBase(id);
        value.setConsentIntent(BoardOAuthConsentIntent.FIRST_CONNECT);
        value.setStateKeyRef(null);
        value.setStateNonce(null);
        value.setStateCiphertext(null);
        value.setStatus("CONSUMED");
        value.setApprovedAt(Date.from(consumedAt.minusSeconds(30)));
        value.setConsumedAt(Date.from(consumedAt));
        value.setVersion(2L);
        value.setUpdatedAt(Date.from(consumedAt));
        return value;
    }

    private static BoardOAuthAuthorizationRequest requestBase(Long id) {
        BoardOAuthAuthorizationRequest value = new BoardOAuthAuthorizationRequest();
        value.setId(id);
        value.setRequestHandleDigest(
                BoardOAuthCrypto.sha256Ascii("h".repeat(43)));
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

    private static BoardOAuthAuthorizationCode activeCode(
            Long id,
            Long requestId,
            String rawCode) {
        BoardOAuthAuthorizationCode value = codeBase(id, requestId, rawCode);
        value.setStatus("ACTIVE");
        value.setVersion(0L);
        value.setUpdatedAt(Date.from(NOW.minusSeconds(30)));
        return value;
    }

    private static BoardOAuthAuthorizationCode usedCode(
            Long id,
            Long requestId,
            String rawCode,
            Instant usedAt) {
        BoardOAuthAuthorizationCode value = codeBase(id, requestId, rawCode);
        value.setIssuedAt(Date.from(usedAt.minusSeconds(30)));
        value.setExpiresAt(Date.from(usedAt.plusSeconds(30)));
        value.setCreatedAt(Date.from(usedAt.minusSeconds(30)));
        value.setStatus("USED");
        value.setUsedAt(Date.from(usedAt));
        value.setVersion(1L);
        value.setUpdatedAt(Date.from(usedAt));
        return value;
    }

    private static BoardOAuthAuthorizationCode codeBase(
            Long id,
            Long requestId,
            String rawCode) {
        BoardOAuthAuthorizationCode value = new BoardOAuthAuthorizationCode();
        value.setId(id);
        value.setCodeDigest(BoardOAuthCrypto.sha256Ascii(rawCode));
        value.setAuthorizationRequestId(requestId);
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

    private static IndependentBoardOAuthMapper.TokenExchangeLockLocator locator(
            Long id,
            Long requestId) {
        IndependentBoardOAuthMapper.TokenExchangeLockLocator value =
                new IndependentBoardOAuthMapper.TokenExchangeLockLocator();
        value.setCodeId(id);
        value.setAuthorizationRequestId(requestId);
        value.setTenantId(100L);
        value.setMemberId(200L);
        value.setUserId(300L);
        value.setProductCode("FBSIR_INDEPENDENT_BOARD");
        value.setSourceCode("WORKBUDDY");
        value.setConnectorCode("fbs-connector");
        value.setClientId(CLIENT_ID);
        return value;
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

    private static BoardOAuthTokenExchangeAuthorityPort.LockResult
            inactiveAuthority() {
        return new BoardOAuthTokenExchangeAuthorityPort.LockResult(
                false,
                true,
                true,
                true,
                BoardOAuthTokenExchangeAuthorityPort.BindingTopology.ABSENT,
                true);
    }

    private static BoardOAuthTokenFamily pendingFamily(
            String familyId,
            Long codeId,
            Instant issuedAt,
            Instant expiresAt) {
        BoardOAuthTokenFamily value = new BoardOAuthTokenFamily();
        value.setId(codeId + 1L);
        value.setFamilyId(familyId);
        value.setOriginAuthorizationCodeId(codeId);
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
        value.setIssuedAt(Date.from(issuedAt));
        value.setExpiresAt(Date.from(expiresAt));
        value.setVersion(0L);
        value.setCreatedAt(Date.from(issuedAt));
        value.setUpdatedAt(Date.from(issuedAt));
        return value;
    }

    private static BoardOAuthTokenFamily terminalFamily(
            BoardOAuthTokenFamily source,
            String status,
            Instant terminatedAt,
            Long version) {
        BoardOAuthTokenFamily value = pendingFamily(
                source.getFamilyId(),
                source.getOriginAuthorizationCodeId(),
                source.getIssuedAt().toInstant(),
                source.getExpiresAt().toInstant());
        value.setId(source.getId());
        value.setStatus(status);
        value.setLifecycleSlot(null);
        value.setTerminatedAt(Date.from(terminatedAt));
        value.setVersion(version);
        value.setUpdatedAt(Date.from(terminatedAt));
        return value;
    }

    private static List<BoardOAuthToken> activeTokens(
            String familyId,
            Instant issuedAt,
            Instant familyExpiresAt,
            Long firstId) {
        return List.of(
                token(firstId, familyId, "ACCESS", "a".repeat(43), issuedAt,
                        issuedAt.plusSeconds(600), "ACTIVE", null, 0L),
                token(firstId + 1L, familyId, "REFRESH", "r".repeat(43),
                        issuedAt, familyExpiresAt, "ACTIVE", null, 0L));
    }

    private static List<BoardOAuthToken> terminalTokens(
            List<BoardOAuthToken> source,
            Instant revokedAt) {
        List<BoardOAuthToken> result = new ArrayList<>();
        for (BoardOAuthToken original : source) {
            result.add(token(
                    original.getId(),
                    original.getFamilyId(),
                    original.getTokenType(),
                    "ACCESS".equals(original.getTokenType())
                            ? "a".repeat(43) : "r".repeat(43),
                    original.getIssuedAt().toInstant(),
                    original.getExpiresAt().toInstant(),
                    "REVOKED",
                    revokedAt,
                    original.getVersion() + 1L));
        }
        return List.copyOf(result);
    }

    private static BoardOAuthToken token(
            Long id,
            String familyId,
            String type,
            String raw,
            Instant issuedAt,
            Instant expiresAt,
            String status,
            Instant revokedAt,
            Long version) {
        BoardOAuthToken value = new BoardOAuthToken();
        value.setId(id);
        value.setTokenDigest(BoardOAuthCrypto.sha256Ascii(raw));
        value.setFamilyId(familyId);
        value.setTokenType(type);
        value.setGeneration(0L);
        value.setResourceUri(BoardOAuthProfile.RESOURCE);
        value.setScopeCanonical(BoardOAuthProfile.CANONICAL_SCOPE);
        value.setScopeDigest(scopeDigest());
        value.setStatus(status);
        value.setActiveRefreshSlot(
                "REFRESH".equals(type) && "ACTIVE".equals(status) ? 1 : null);
        value.setIssuedAt(Date.from(issuedAt));
        value.setRevokedAt(revokedAt == null ? null : Date.from(revokedAt));
        value.setExpiresAt(Date.from(expiresAt));
        value.setVersion(version);
        value.setCreatedAt(Date.from(issuedAt));
        value.setUpdatedAt(revokedAt == null
                ? Date.from(issuedAt) : Date.from(revokedAt));
        return value;
    }

    private static BoardOAuthReceipt creationReceipt(
            String receiptId,
            BoardOAuthAuthorizationRequest request,
            BoardOAuthAuthorizationCode code,
            BoardOAuthTokenFamily family,
            List<BoardOAuthToken> tokens,
            Instant createdAt,
            Long id) {
        BoardOAuthReceipt value = BoardOAuthTokenFamilyCreatedReceiptFactory.create(
                receiptId,
                ("x" + receiptId.substring(1)),
                createdAt,
                request,
                code,
                family,
                tokens);
        value.setId(id);
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
}
