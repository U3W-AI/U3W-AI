package com.wx.fbsir.business.board.service;

import com.wx.fbsir.business.board.config.IndependentBoardConnectorProperties;
import com.wx.fbsir.business.board.domain.BoardConnectorBinding;
import com.wx.fbsir.business.board.domain.BoardConnectorBindingReceipt;
import com.wx.fbsir.business.board.domain.BoardEnterpriseAuthority;
import com.wx.fbsir.business.board.domain.BoardEnterpriseMemberScope;
import com.wx.fbsir.business.board.domain.BoardProductEntitlement;
import com.wx.fbsir.business.board.domain.BoardProductPlan;
import com.wx.fbsir.business.board.dto.BoardConnectorBindingSnapshot;
import com.wx.fbsir.business.board.mapper.IndependentBoardMapper;
import com.wx.fbsir.business.board.oauth.BoardOAuthCrypto;
import com.wx.fbsir.business.board.oauth.BoardOAuthConsentIntent;
import com.wx.fbsir.business.board.oauth.BoardOAuthPrincipalSubject;
import com.wx.fbsir.business.board.oauth.BoardOAuthProfile;
import com.wx.fbsir.business.board.oauth.BoardOAuthTokenFamilyCreatedReceiptFactory;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthAuthorizationCode;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthAuthorizationRequest;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthClient;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthReceipt;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthToken;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthTokenFamily;
import com.wx.fbsir.business.board.oauth.mapper.IndependentBoardOAuthMapper;
import com.wx.fbsir.business.board.oauth.mapper.IndependentBoardOAuthMapper.TokenContextLocator;
import com.wx.fbsir.business.board.oauth.service.IndependentBoardOAuthClientRegistrationService;
import com.wx.fbsir.common.exception.ServiceException;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Independent end-to-end unit contract for the only first-protected production
 * entrypoint. The mapper callbacks model database CAS effects; this test never
 * calls the deprecated attestation seam or advances an activation lease by hand.
 */
class IndependentBoardOAuthFirstProtectedRequestProductionChainTest {
    private static final Instant NOW = Instant.parse("2026-07-21T06:00:30Z");
    private static final Instant TOKEN_EXCHANGED_AT = NOW.minusSeconds(30);
    private static final long TENANT_ID = 7L;
    private static final long MEMBER_ID = 11L;
    private static final long USER_ID = 42L;
    private static final String CLIENT_ID = "c".repeat(43);
    private static final String RAW_ACCESS_TOKEN = "A".repeat(43);
    private static final String FAMILY_ID = "family_pending_303";
    private static final String OLD_FAMILY_ID = "family_active_302";
    private static final String OLD_CLIENT_ID = "o".repeat(43);
    private static final String EXISTING_BINDING_ID =
            "11111111-1111-4111-8111-111111111111";
    private static final String RECEIPT_ID = "family_created_receipt_1";
    private static final String CORRELATION_ID = "token_exchange_1";

    @AfterEach
    void clearTransactionThreadLocals() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(null);
        TransactionSynchronizationManager.setCurrentTransactionName(null);
    }

    @Test
    void pendingAccessRunsTheWholeProductionChainAndCompletesAtCommit() throws Exception {
        Scenario scenario = new Scenario();

        BoardConnectorBindingSnapshot result = scenario.facade.activate(
                "Bearer " + RAW_ACCESS_TOKEN,
                "initialize");

        assertEquals("ACTIVE", result.status());
        assertEquals(TENANT_ID, result.tenantId());
        assertEquals(MEMBER_ID, result.memberId());
        assertEquals(USER_ID, result.userId());
        assertEquals(1L, result.version());
        assertEquals(1, scenario.runner.successfulCompletions());
        assertEquals(0, scenario.runner.rolledBackCompletions());
        assertTrue(scenario.familyActivated.get());
        assertEquals("ACTIVE", scenario.pendingFamily.getStatus());
        assertEquals("ACTIVE", scenario.pendingFamily.getLifecycleSlot());
        assertEquals(result.bindingId(), scenario.pendingFamily.getBindingId());
        assertEquals(result.version(), scenario.pendingFamily.getBindingVersion());
        assertNotNull(scenario.w4aReceipt.get());
        assertNotNull(scenario.w4bReceipt.get());
        assertEquals("CONNECTOR_BINDING_VERIFIED", scenario.w4aReceipt.get().getAction());
        assertEquals("TOKEN_FAMILY_ACTIVATED", scenario.w4bReceipt.get().getAction());

        verify(scenario.oauthMapper, times(2))
                .selectTokenContextLocatorByDigest(any(byte[].class));
        verify(scenario.oauthMapper).activateTokenFamilyIfVersion(
                any(BoardOAuthTokenFamily.class),
                org.mockito.ArgumentMatchers.eq(0L));
        verify(scenario.mapper).insertConnectorBindingReceipt(
                any(BoardConnectorBindingReceipt.class));
        verify(scenario.oauthMapper).insertReceipt(any(BoardOAuthReceipt.class));

        InOrder durableChain = inOrder(scenario.oauthMapper, scenario.mapper);
        durableChain.verify(scenario.oauthMapper, times(2))
                .selectTokenContextLocatorByDigest(any(byte[].class));
        durableChain.verify(scenario.oauthMapper)
                .selectTokenFamilyLocator(FAMILY_ID);
        durableChain.verify(scenario.oauthMapper)
                .selectAuthorizationCodeLocatorById(202L);
        durableChain.verify(scenario.oauthMapper)
                .selectAuthorizationRequestByIdAndClientForUpdate(101L, CLIENT_ID);
        durableChain.verify(scenario.oauthMapper)
                .selectAuthorizationCodeByIdAndClientForUpdate(202L, CLIENT_ID);
        durableChain.verify(scenario.oauthMapper)
                .selectTokenFamilyCreatedReceiptCandidatesForUpdate(
                        FAMILY_ID, CLIENT_ID, 202L);
        durableChain.verify(scenario.mapper)
                .insertConnectorBinding(any(BoardConnectorBinding.class));
        durableChain.verify(scenario.oauthMapper)
                .activateTokenFamilyIfVersion(
                        any(BoardOAuthTokenFamily.class),
                        org.mockito.ArgumentMatchers.eq(0L));
        durableChain.verify(scenario.mapper)
                .insertConnectorBindingReceipt(any(BoardConnectorBindingReceipt.class));
        durableChain.verify(scenario.oauthMapper)
                .insertReceipt(any(BoardOAuthReceipt.class));
    }

    @Test
    void explicitReauthorizationRunsTheBearerDerivedProductionChainAndReturnsAfterCommit()
            throws Exception {
        Scenario scenario = new Scenario();
        scenario.installExplicitReauthorization();

        assertEquals(
                BoardOAuthConsentIntent.EXPLICIT_REAUTHORIZATION,
                scenario.request.getConsentIntent());
        assertEquals(scenario.request.getConsentIntent(), scenario.code.getConsentIntent());
        assertEquals(scenario.code.getConsentIntent(), scenario.pendingFamily.getConsentIntent());
        assertEquals("TOKEN_FAMILY_CREATED", scenario.createdReceipt.getAction());
        assertEquals(
                "FBSIR:OAUTH:TOKEN_FAMILY_CREATED_RECEIPT:v2",
                BoardOAuthTokenFamilyCreatedReceiptFactory.CANONICAL_DOMAIN_V2);
        BoardOAuthTokenFamilyCreatedReceiptFactory.validate(
                scenario.createdReceipt,
                scenario.request,
                scenario.code,
                scenario.pendingFamily,
                scenario.pendingTokens);

        BoardConnectorBindingSnapshot result = scenario.facade.activate(
                "Bearer " + RAW_ACCESS_TOKEN,
                "tools/list");

        assertEquals(EXISTING_BINDING_ID, result.bindingId());
        assertEquals("ACTIVE", result.status());
        assertEquals(4L, result.version());
        assertEquals(1, scenario.runner.successfulCompletions());
        assertEquals(0, scenario.runner.rolledBackCompletions());

        assertTrue(scenario.familyActivated.get());
        assertEquals("ACTIVE", scenario.pendingFamily.getStatus());
        assertEquals("ACTIVE", scenario.pendingFamily.getLifecycleSlot());
        assertEquals(EXISTING_BINDING_ID, scenario.pendingFamily.getBindingId());
        assertEquals(4L, scenario.pendingFamily.getBindingVersion());
        assertEquals("REVOKED", scenario.oldActiveFamily.getStatus());
        assertNull(scenario.oldActiveFamily.getLifecycleSlot());
        assertEquals(Date.from(NOW), scenario.oldActiveFamily.getTerminatedAt());
        assertTrue(scenario.oldActiveTokens.stream().allMatch(token ->
                "REVOKED".equals(token.getStatus())
                        && token.getActiveRefreshSlot() == null
                        && Date.from(NOW).equals(token.getRevokedAt())));

        BoardConnectorBinding binding = scenario.currentBinding.get();
        assertNotNull(binding);
        assertEquals(EXISTING_BINDING_ID, binding.getBindingId());
        assertEquals(CLIENT_ID, binding.getClientId());
        assertEquals(
                IndependentBoardConnectorBindingService.VERIFY_TOOLS_LIST,
                binding.getVerificationMethod());
        assertEquals(4L, binding.getVersion());
        assertFalse("e".repeat(64).equals(binding.getEvidenceDigest()));

        assertNotNull(scenario.w4aReceipt.get());
        assertNotNull(scenario.w4bReceipt.get());
        assertEquals("CONNECTOR_BINDING_VERIFIED", scenario.w4aReceipt.get().getAction());
        assertEquals("TOKEN_FAMILY_REAUTHORIZED", scenario.w4bReceipt.get().getAction());
        assertEquals(EXISTING_BINDING_ID, scenario.w4aReceipt.get().getBindingId());
        assertEquals(EXISTING_BINDING_ID, scenario.w4bReceipt.get().getBindingId());

        verify(scenario.oauthMapper, times(2))
                .selectTokenContextLocatorByDigest(any(byte[].class));
        verify(scenario.oauthMapper, atLeastOnce())
                .selectTokenFamilyCreatedReceiptCandidatesForUpdate(
                        FAMILY_ID, CLIENT_ID, 202L);
        verify(scenario.mapper).reauthorizeConnectorBindingIfVersion(
                any(BoardConnectorBinding.class),
                org.mockito.ArgumentMatchers.eq(3L));
        verify(scenario.mapper, never()).insertConnectorBinding(any(BoardConnectorBinding.class));
        verify(scenario.oauthMapper).revokeActiveFamilyTokensAtLogicalTime(
                org.mockito.ArgumentMatchers.eq(OLD_FAMILY_ID),
                org.mockito.ArgumentMatchers.eq(Date.from(NOW)));
        verify(scenario.oauthMapper).revokeTokenFamilyIfVersion(
                OLD_FAMILY_ID,
                OLD_CLIENT_ID,
                5L,
                Date.from(NOW));
        verify(scenario.oauthMapper).activateTokenFamilyIfVersion(
                any(BoardOAuthTokenFamily.class),
                org.mockito.ArgumentMatchers.eq(0L));
        verify(scenario.mapper).insertConnectorBindingReceipt(
                any(BoardConnectorBindingReceipt.class));
        verify(scenario.oauthMapper).insertReceipt(any(BoardOAuthReceipt.class));
    }

    @Test
    void reauthorizationUsesThePostReceiptLockEventTimeAndExpiresOldTokens()
            throws Exception {
        Instant eventAt = NOW.plusSeconds(2);
        Clock branchClock = mock(Clock.class);
        when(branchClock.instant()).thenReturn(NOW, eventAt);
        Scenario scenario = new Scenario(branchClock);
        scenario.installExplicitReauthorization();
        BoardOAuthToken oldAccess = scenario.oldActiveTokens.stream()
                .filter(token -> "ACCESS".equals(token.getTokenType()))
                .findFirst()
                .orElseThrow();
        oldAccess.setExpiresAt(Date.from(NOW.plusSeconds(1)));

        BoardConnectorBindingSnapshot result = scenario.facade.activate(
                "Bearer " + RAW_ACCESS_TOKEN,
                "tools/list");

        assertEquals(eventAt, result.verifiedAt().toInstant());
        assertEquals(eventAt, scenario.pendingFamily.getActivatedAt().toInstant());
        assertEquals(eventAt, scenario.oldActiveFamily.getTerminatedAt().toInstant());
        assertEquals("EXPIRED", oldAccess.getStatus());
        assertNull(oldAccess.getRevokedAt());
        BoardOAuthToken oldRefresh = scenario.oldActiveTokens.stream()
                .filter(token -> "REFRESH".equals(token.getTokenType()))
                .findFirst()
                .orElseThrow();
        assertEquals("REVOKED", oldRefresh.getStatus());
        assertEquals(eventAt, oldRefresh.getRevokedAt().toInstant());
        assertEquals(eventAt, scenario.w4aReceipt.get().getCreatedAt().toInstant());
        assertEquals(eventAt, scenario.w4bReceipt.get().getCreatedAt().toInstant());

        InOrder order = inOrder(branchClock, scenario.oauthMapper);
        order.verify(branchClock).instant();
        order.verify(scenario.oauthMapper)
                .selectTokenFamilyCreatedReceiptCandidatesForUpdate(
                        FAMILY_ID, CLIENT_ID, 202L);
        order.verify(branchClock, atLeastOnce()).instant();
    }

    @Test
    void locatorDriftBetweenDiscoveryAndLockedCurrentReadFailsClosed() throws Exception {
        Scenario scenario = new Scenario();
        TokenContextLocator discovered = tokenLocator(
                scenario.pendingFamily, scenario.pendingTokens.get(0));
        TokenContextLocator drifted = tokenLocator(
                scenario.pendingFamily, scenario.pendingTokens.get(0));
        drifted.setUserId(USER_ID + 1L);
        when(scenario.oauthMapper.selectTokenContextLocatorByDigest(any(byte[].class)))
                .thenReturn(discovered, drifted);

        ServiceException failure = assertThrows(
                ServiceException.class,
                () -> scenario.facade.activate("Bearer " + RAW_ACCESS_TOKEN, "initialize"));

        assertEquals(
                IndependentBoardOAuthFirstProtectedRequestTransactionRunner.ACCESS_TOKEN_INVALID,
                failure.getMessage());
        assertEquals(401, failure.getCode());
        assertEquals(0, scenario.runner.successfulCompletions());
        assertEquals(1, scenario.runner.rolledBackCompletions());
        assertNull(scenario.currentBinding.get());
        assertFalse(scenario.familyActivated.get());
    }

    @Test
    void lockedConsentIntentMismatchFailsBeforeAnyBindingMutation() throws Exception {
        Scenario scenario = new Scenario();
        scenario.request.setConsentIntent(
                BoardOAuthConsentIntent.EXPLICIT_REAUTHORIZATION);

        ServiceException failure = assertThrows(
                ServiceException.class,
                () -> scenario.facade.activate("Bearer " + RAW_ACCESS_TOKEN, "initialize"));

        assertEquals("BOARD_OAUTH_CONSENT_INTENT_LINEAGE_INVALID", failure.getMessage());
        assertEquals(409, failure.getCode());
        assertNull(scenario.currentBinding.get());
        assertFalse(scenario.familyActivated.get());
        assertNull(scenario.w4aReceipt.get());
        assertNull(scenario.w4bReceipt.get());
    }

    @Test
    void consentIntentTamperOnFinalCurrentReadRollsBackTheWholeChain() throws Exception {
        Scenario scenario = new Scenario();
        BoardOAuthAuthorizationRequest tampered = authorizationRequest();
        tampered.setConsentIntent(
                BoardOAuthConsentIntent.EXPLICIT_REAUTHORIZATION);
        when(scenario.oauthMapper.selectAuthorizationRequestByIdAndClientForUpdate(
                101L, CLIENT_ID)).thenReturn(scenario.request, tampered);

        ServiceException failure = assertThrows(
                ServiceException.class,
                () -> scenario.facade.activate("Bearer " + RAW_ACCESS_TOKEN, "initialize"));

        assertEquals("BOARD_OAUTH_AUTHORIZATION_LINEAGE_DRIFT", failure.getMessage());
        assertEquals(409, failure.getCode());
        assertEquals(0, scenario.runner.successfulCompletions());
        assertEquals(1, scenario.runner.rolledBackCompletions());
        assertNull(scenario.w4aReceipt.get());
        assertNull(scenario.w4bReceipt.get());
    }

    @Test
    void missingFamilyCreationReceiptFailsBeforeAnyBindingMutation() throws Exception {
        Scenario scenario = new Scenario();
        when(scenario.oauthMapper.selectTokenFamilyCreatedReceiptCandidatesForUpdate(
                FAMILY_ID, CLIENT_ID, 202L)).thenReturn(List.of());

        ServiceException failure = assertThrows(
                ServiceException.class,
                () -> scenario.facade.activate("Bearer " + RAW_ACCESS_TOKEN, "initialize"));

        assertEquals(
                "BOARD_OAUTH_TOKEN_FAMILY_CREATED_RECEIPT_CARDINALITY_INVALID",
                failure.getMessage());
        assertEquals(500, failure.getCode());
        assertEquals(0, scenario.runner.successfulCompletions());
        assertEquals(1, scenario.runner.rolledBackCompletions());
        assertNull(scenario.currentBinding.get());
        assertFalse(scenario.familyActivated.get());
    }

    @Test
    void duplicateFamilyCreationReceiptsFailBeforeAnyBindingMutation() throws Exception {
        Scenario scenario = new Scenario();
        when(scenario.oauthMapper.selectTokenFamilyCreatedReceiptCandidatesForUpdate(
                FAMILY_ID, CLIENT_ID, 202L))
                .thenReturn(List.of(scenario.createdReceipt, scenario.createdReceipt));

        ServiceException failure = assertThrows(
                ServiceException.class,
                () -> scenario.facade.activate("Bearer " + RAW_ACCESS_TOKEN, "initialize"));

        assertEquals(
                "BOARD_OAUTH_TOKEN_FAMILY_CREATED_RECEIPT_CARDINALITY_INVALID",
                failure.getMessage());
        assertEquals(500, failure.getCode());
        assertEquals(0, scenario.runner.successfulCompletions());
        assertEquals(1, scenario.runner.rolledBackCompletions());
        assertNull(scenario.currentBinding.get());
        assertFalse(scenario.familyActivated.get());
    }

    @Test
    void pendingFamilyCasLossRollsBackBeforeEitherCompletionReceipt() throws Exception {
        Scenario scenario = new Scenario();
        when(scenario.oauthMapper.activateTokenFamilyIfVersion(
                any(BoardOAuthTokenFamily.class),
                org.mockito.ArgumentMatchers.eq(0L))).thenReturn(0);

        ServiceException failure = assertThrows(
                ServiceException.class,
                () -> scenario.facade.activate("Bearer " + RAW_ACCESS_TOKEN, "initialize"));

        assertEquals("BOARD_OAUTH_PENDING_FAMILY_CONFLICT", failure.getMessage());
        assertEquals(409, failure.getCode());
        assertEquals(0, scenario.runner.successfulCompletions());
        assertEquals(1, scenario.runner.rolledBackCompletions());
        assertFalse(scenario.familyActivated.get());
        assertNull(scenario.w4aReceipt.get());
        assertNull(scenario.w4bReceipt.get());
    }

    private static final class Scenario {
        private final IndependentBoardMapper mapper = mock(IndependentBoardMapper.class);
        private final IndependentBoardOAuthMapper oauthMapper =
                mock(IndependentBoardOAuthMapper.class);
        private final DataSource dataSource = mock(DataSource.class);
        private final Connection connection = mock(Connection.class);
        private final BoardOAuthAuthorizationRequest request = authorizationRequest();
        private final BoardOAuthAuthorizationCode code = authorizationCode(request);
        private final BoardOAuthTokenFamily pendingFamily = pendingFamily(code);
        private final List<BoardOAuthToken> pendingTokens = List.of(
                token(401L, pendingFamily, "ACCESS",
                        BoardOAuthCrypto.sha256Ascii(RAW_ACCESS_TOKEN)),
                token(402L, pendingFamily, "REFRESH",
                        BoardOAuthCrypto.sha256Ascii("refresh-token-secret")));
        private BoardOAuthReceipt createdReceipt = createdReceipt(
                request, code, pendingFamily, pendingTokens);
        private final BoardEnterpriseMemberScope member = member();
        private final BoardProductEntitlement entitlement = entitlement();
        private final BoardProductPlan plan = plan();
        private final BoardOAuthClient client = client();
        private final AtomicBoolean familyActivated = new AtomicBoolean();
        private final AtomicReference<BoardConnectorBinding> currentBinding =
                new AtomicReference<>();
        private final AtomicReference<BoardConnectorBindingReceipt> w4aReceipt =
                new AtomicReference<>();
        private final AtomicReference<BoardOAuthReceipt> w4bReceipt =
                new AtomicReference<>();
        private BoardOAuthTokenFamily oldActiveFamily;
        private List<BoardOAuthToken> oldActiveTokens = List.of();
        private final TransactionHarnessRunner runner;
        private final IndependentBoardOAuthFirstProtectedRequestFacade facade;

        private Scenario() throws Exception {
            this(Clock.fixed(NOW, ZoneOffset.UTC));
        }

        private Scenario(Clock clock) throws Exception {
            when(connection.isClosed()).thenReturn(false);
            when(connection.getAutoCommit()).thenReturn(false);
            when(connection.getTransactionIsolation())
                    .thenReturn(Connection.TRANSACTION_REPEATABLE_READ);

            installAuthorityReads();
            installOAuthReadsAndCas();
            installBindingWritesAndReads();
            installReceiptWritesAndReads();

            IndependentBoardConnectorProperties properties =
                    new IndependentBoardConnectorProperties();
            properties.setIssuerUri(BoardOAuthProfile.ISSUER);
            properties.setResourceUri(BoardOAuthProfile.RESOURCE);
            IndependentBoardConnectorBindingService bindingService =
                    new IndependentBoardConnectorBindingService(
                            mapper,
                            oauthMapper,
                            properties,
                            dataSource,
                            clock);
            runner = new TransactionHarnessRunner(
                    oauthMapper, bindingService, dataSource, connection);
            facade = new IndependentBoardOAuthFirstProtectedRequestFacade(runner);
        }

        private void installExplicitReauthorization() {
            request.setConsentIntent(BoardOAuthConsentIntent.EXPLICIT_REAUTHORIZATION);
            code.setConsentIntent(request.getConsentIntent());
            pendingFamily.setConsentIntent(code.getConsentIntent());
            createdReceipt = createdReceipt(request, code, pendingFamily, pendingTokens);

            BoardConnectorBinding existing = existingBinding();
            oldActiveFamily = oldActiveFamily(existing);
            oldActiveTokens = oldActiveTokens(oldActiveFamily);
            currentBinding.set(existing);

            TokenContextLocator locator = tokenLocator(
                    pendingFamily, pendingTokens.get(0));
            when(oauthMapper.selectTokenContextLocatorByDigest(any(byte[].class)))
                    .thenReturn(locator);
            when(mapper.selectConnectorBindingSlotForUpdate(
                    TENANT_ID,
                    MEMBER_ID,
                    IndependentBoardEntitlementService.PRODUCT_CODE,
                    IndependentBoardConnectorBindingService.SOURCE_CODE,
                    IndependentBoardConnectorBindingService.CONNECTOR_CODE))
                    .thenReturn(existing);
            when(mapper.reauthorizeConnectorBindingIfVersion(
                    any(BoardConnectorBinding.class),
                    org.mockito.ArgumentMatchers.eq(3L)))
                    .thenAnswer(invocation -> {
                        BoardConnectorBinding updated = invocation.getArgument(0);
                        updated.setUpdatedAt(copy(updated.getVerifiedAt()));
                        currentBinding.set(updated);
                        return 1;
                    });
            when(oauthMapper.selectActiveTokenFamilySlotForUpdate(
                    TENANT_ID,
                    MEMBER_ID,
                    IndependentBoardEntitlementService.PRODUCT_CODE,
                    IndependentBoardConnectorBindingService.SOURCE_CODE,
                    IndependentBoardConnectorBindingService.CONNECTOR_CODE))
                    .thenAnswer(invocation -> familyActivated.get()
                            ? pendingFamily
                            : oldActiveFamily);
            when(oauthMapper.selectPendingTokenFamilySlotForUpdate(
                    TENANT_ID,
                    MEMBER_ID,
                    IndependentBoardEntitlementService.PRODUCT_CODE,
                    IndependentBoardConnectorBindingService.SOURCE_CODE,
                    IndependentBoardConnectorBindingService.CONNECTOR_CODE))
                    .thenAnswer(invocation -> familyActivated.get() ? null : pendingFamily);
            when(oauthMapper.selectFamilyTokensForUpdate(OLD_FAMILY_ID))
                    .thenAnswer(invocation -> oldActiveTokens);
            when(oauthMapper.selectTokenFamilyForUpdate(OLD_FAMILY_ID, OLD_CLIENT_ID))
                    .thenAnswer(invocation -> oldActiveFamily);
            when(oauthMapper.revokeActiveFamilyTokensAtLogicalTime(
                    org.mockito.ArgumentMatchers.eq(OLD_FAMILY_ID),
                    any(Date.class)))
                    .thenAnswer(invocation -> {
                        Date revokedAt = copy(invocation.getArgument(1));
                        for (BoardOAuthToken token : oldActiveTokens) {
                            boolean expired = !token.getExpiresAt().after(revokedAt);
                            token.setStatus(expired ? "EXPIRED" : "REVOKED");
                            token.setActiveRefreshSlot(null);
                            token.setRevokedAt(expired ? null : copy(revokedAt));
                            token.setUpdatedAt(copy(revokedAt));
                            token.setVersion(token.getVersion() + 1L);
                        }
                        return oldActiveTokens.size();
                    });
            when(oauthMapper.revokeTokenFamilyIfVersion(
                    org.mockito.ArgumentMatchers.eq(OLD_FAMILY_ID),
                    org.mockito.ArgumentMatchers.eq(OLD_CLIENT_ID),
                    org.mockito.ArgumentMatchers.eq(5L),
                    any(Date.class)))
                    .thenAnswer(invocation -> {
                        Date revokedAt = copy(invocation.getArgument(3));
                        oldActiveFamily.setStatus("REVOKED");
                        oldActiveFamily.setLifecycleSlot(null);
                        oldActiveFamily.setTerminatedAt(copy(revokedAt));
                        oldActiveFamily.setUpdatedAt(copy(revokedAt));
                        oldActiveFamily.setVersion(6L);
                        return 1;
                    });
        }

        private void installAuthorityReads() {
            when(mapper.selectEnterpriseSlotForUpdate(TENANT_ID))
                    .thenReturn(enterprise());
            when(mapper.selectExactActiveMemberForUpdate(
                    TENANT_ID, MEMBER_ID, USER_ID)).thenReturn(member);
            when(mapper.selectEntitlementForUpdate(
                    TENANT_ID,
                    MEMBER_ID,
                    IndependentBoardEntitlementService.PRODUCT_CODE))
                    .thenReturn(entitlement);
            when(mapper.selectActivePlanForUpdate(
                    IndependentBoardEntitlementService.PRODUCT_CODE,
                    IndependentBoardEntitlementService.VIP_PLAN))
                    .thenReturn(plan);
            when(mapper.selectConnectorBindingSlotForUpdate(
                    TENANT_ID,
                    MEMBER_ID,
                    IndependentBoardEntitlementService.PRODUCT_CODE,
                    IndependentBoardConnectorBindingService.SOURCE_CODE,
                    IndependentBoardConnectorBindingService.CONNECTOR_CODE))
                    .thenReturn(null);
        }

        private void installOAuthReadsAndCas() {
            TokenContextLocator locator = tokenLocator(pendingFamily, pendingTokens.get(0));
            when(oauthMapper.selectTokenContextLocatorByDigest(any(byte[].class)))
                    .thenReturn(locator);
            when(oauthMapper.selectTokenFamilyLocator(FAMILY_ID))
                    .thenReturn(pendingFamily);
            when(oauthMapper.selectAuthorizationCodeLocatorById(202L))
                    .thenReturn(code);
            when(oauthMapper.selectClientForUpdate(CLIENT_ID)).thenReturn(client);
            when(oauthMapper.selectAuthorizationRequestByIdAndClientForUpdate(
                    101L, CLIENT_ID)).thenReturn(request);
            when(oauthMapper.selectAuthorizationCodeByIdAndClientForUpdate(
                    202L, CLIENT_ID)).thenReturn(code);
            when(oauthMapper.selectActiveTokenFamilySlotForUpdate(
                    TENANT_ID,
                    MEMBER_ID,
                    IndependentBoardEntitlementService.PRODUCT_CODE,
                    IndependentBoardConnectorBindingService.SOURCE_CODE,
                    IndependentBoardConnectorBindingService.CONNECTOR_CODE))
                    .thenAnswer(invocation -> familyActivated.get() ? pendingFamily : null);
            when(oauthMapper.selectPendingTokenFamilySlotForUpdate(
                    TENANT_ID,
                    MEMBER_ID,
                    IndependentBoardEntitlementService.PRODUCT_CODE,
                    IndependentBoardConnectorBindingService.SOURCE_CODE,
                    IndependentBoardConnectorBindingService.CONNECTOR_CODE))
                    .thenAnswer(invocation -> familyActivated.get() ? null : pendingFamily);
            when(oauthMapper.selectFamilyTokensForUpdate(FAMILY_ID))
                    .thenReturn(pendingTokens);
            when(oauthMapper.selectTokenFamilyForUpdate(FAMILY_ID, CLIENT_ID))
                    .thenAnswer(invocation -> familyActivated.get() ? pendingFamily : null);
            when(oauthMapper.selectTokenFamilyCreatedReceiptCandidatesForUpdate(
                     FAMILY_ID, CLIENT_ID, 202L))
                    .thenAnswer(invocation -> List.of(createdReceipt));
            when(oauthMapper.activateTokenFamilyIfVersion(
                    any(BoardOAuthTokenFamily.class),
                    org.mockito.ArgumentMatchers.eq(0L)))
                    .thenAnswer(invocation -> {
                        BoardOAuthTokenFamily activation = invocation.getArgument(0);
                        pendingFamily.setBindingId(activation.getBindingId());
                        pendingFamily.setBindingVersion(activation.getBindingVersion());
                        pendingFamily.setStatus("ACTIVE");
                        pendingFamily.setLifecycleSlot("ACTIVE");
                        pendingFamily.setActivatedAt(copy(activation.getActivatedAt()));
                        pendingFamily.setUpdatedAt(copy(activation.getActivatedAt()));
                        pendingFamily.setVersion(1L);
                        familyActivated.set(true);
                        return 1;
                    });
        }

        private void installBindingWritesAndReads() {
            when(mapper.insertConnectorBinding(any(BoardConnectorBinding.class)))
                    .thenAnswer(invocation -> {
                        BoardConnectorBinding binding = invocation.getArgument(0);
                        binding.setId(501L);
                        binding.setCreatedAt(copy(binding.getVerifiedAt()));
                        binding.setUpdatedAt(copy(binding.getVerifiedAt()));
                        currentBinding.set(binding);
                        return 1;
                    });
            when(mapper.insertConnectorBindingScope(
                    anyString(), anyString(), any(Date.class))).thenReturn(1);
            when(mapper.selectConnectorBindingForUpdate(
                    TENANT_ID,
                    MEMBER_ID,
                    USER_ID,
                    IndependentBoardEntitlementService.PRODUCT_CODE,
                    IndependentBoardConnectorBindingService.SOURCE_CODE,
                    IndependentBoardConnectorBindingService.CONNECTOR_CODE))
                    .thenAnswer(invocation -> currentBinding.get());
            when(mapper.selectConnectorBindingScopesForUpdate(anyString()))
                    .thenAnswer(invocation -> currentBinding.get() == null
                            ? null
                            : currentBinding.get().getScopes());
        }

        private void installReceiptWritesAndReads() {
            when(mapper.insertConnectorBindingReceipt(
                    any(BoardConnectorBindingReceipt.class)))
                    .thenAnswer(invocation -> {
                        BoardConnectorBindingReceipt receipt = invocation.getArgument(0);
                        w4aReceipt.set(receipt);
                        return 1;
                    });
            when(mapper.selectConnectorBindingReceiptForUpdate(anyString()))
                    .thenAnswer(invocation -> {
                        BoardConnectorBindingReceipt receipt = w4aReceipt.get();
                        return receipt != null
                                        && receipt.getReceiptId().equals(invocation.getArgument(0))
                                ? receipt
                                : null;
                    });
            when(oauthMapper.insertReceipt(any(BoardOAuthReceipt.class)))
                    .thenAnswer(invocation -> {
                        BoardOAuthReceipt receipt = invocation.getArgument(0);
                        receipt.setId(701L);
                        w4bReceipt.set(receipt);
                        return 1;
                    });
            when(oauthMapper.selectReceiptByReceiptId(anyString()))
                    .thenAnswer(invocation -> {
                        BoardOAuthReceipt receipt = w4bReceipt.get();
                        return receipt != null
                                        && receipt.getReceiptId().equals(invocation.getArgument(0))
                                ? receipt
                                : null;
                    });
        }
    }

    /**
     * The production runner is normally opened by Spring AOP. This harness
     * supplies the same physical RR boundary while preserving the public
     * facade's root-transaction check and all production synchronization guards.
     */
    private static final class TransactionHarnessRunner
            extends IndependentBoardOAuthFirstProtectedRequestTransactionRunner {
        private final DataSource dataSource;
        private final Connection connection;
        private final AtomicInteger successfulCompletions = new AtomicInteger();
        private final AtomicInteger rolledBackCompletions = new AtomicInteger();

        private TransactionHarnessRunner(
                IndependentBoardOAuthMapper oauthMapper,
                IndependentBoardConnectorBindingService bindingService,
                DataSource dataSource,
                Connection connection) {
            super(oauthMapper, bindingService);
            this.dataSource = dataSource;
            this.connection = connection;
        }

        @Override
        public BoardConnectorBindingSnapshot activate(
                byte[] presentedAccessTokenDigest,
                String verificationMethod) {
            return inRepeatableReadTransaction(() ->
                    super.activate(presentedAccessTokenDigest, verificationMethod));
        }

        private int successfulCompletions() {
            return successfulCompletions.get();
        }

        private int rolledBackCompletions() {
            return rolledBackCompletions.get();
        }

        private <T> T inRepeatableReadTransaction(Supplier<T> work) {
            assertNull(TransactionSynchronizationManager.getResource(dataSource));
            TransactionSynchronizationManager.setActualTransactionActive(true);
            TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
            TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(
                    Connection.TRANSACTION_REPEATABLE_READ);
            TransactionSynchronizationManager.setCurrentTransactionName(
                    "first-protected-production-chain-test");
            TransactionSynchronizationManager.initSynchronization();
            ConnectionHolder holder = new ConnectionHolder(connection);
            holder.setSynchronizedWithTransaction(true);
            TransactionSynchronizationManager.bindResource(dataSource, holder);

            List<TransactionSynchronization> synchronizations = List.of();
            boolean beforeCompletionCalled = false;
            boolean committed = false;
            try {
                T result = work.get();
                synchronizations = List.copyOf(
                        TransactionSynchronizationManager.getSynchronizations());
                for (TransactionSynchronization synchronization : synchronizations) {
                    synchronization.beforeCommit(false);
                }
                for (TransactionSynchronization synchronization : synchronizations) {
                    synchronization.beforeCompletion();
                }
                beforeCompletionCalled = true;
                for (TransactionSynchronization synchronization : synchronizations) {
                    synchronization.afterCommit();
                }
                committed = true;
                successfulCompletions.incrementAndGet();
                return result;
            } finally {
                if (synchronizations.isEmpty()
                        && TransactionSynchronizationManager.isSynchronizationActive()) {
                    synchronizations = List.copyOf(
                            TransactionSynchronizationManager.getSynchronizations());
                }
                if (!beforeCompletionCalled) {
                    for (TransactionSynchronization synchronization : synchronizations) {
                        synchronization.beforeCompletion();
                    }
                }
                if (TransactionSynchronizationManager.isSynchronizationActive()) {
                    TransactionSynchronizationManager.clearSynchronization();
                }
                int completionStatus = committed
                        ? TransactionSynchronization.STATUS_COMMITTED
                        : TransactionSynchronization.STATUS_ROLLED_BACK;
                for (TransactionSynchronization synchronization : synchronizations) {
                    synchronization.afterCompletion(completionStatus);
                }
                if (!committed) {
                    rolledBackCompletions.incrementAndGet();
                }
                if (TransactionSynchronizationManager.hasResource(dataSource)) {
                    TransactionSynchronizationManager.unbindResource(dataSource);
                }
                TransactionSynchronizationManager.setActualTransactionActive(false);
                TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
                TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(null);
                TransactionSynchronizationManager.setCurrentTransactionName(null);
            }
        }
    }

    private static BoardOAuthAuthorizationRequest authorizationRequest() {
        BoardOAuthAuthorizationRequest request = new BoardOAuthAuthorizationRequest();
        request.setId(101L);
        request.setRequestHandleDigest(digest("request-handle"));
        request.setClientId(CLIENT_ID);
        request.setRedirectUri("http://127.0.0.1:17654/oauth/callback");
        request.setCodeChallenge("p".repeat(43));
        request.setCodeChallengeMethod(BoardOAuthProfile.PKCE_METHOD);
        request.setStateDigest(digest("opaque-state"));
        applyProfile(request);
        request.setTenantId(TENANT_ID);
        request.setMemberId(MEMBER_ID);
        request.setUserId(USER_ID);
        request.setPrincipalSubjectDigest(principalDigest());
        request.setConsentIntent(BoardOAuthConsentIntent.FIRST_CONNECT);
        request.setStatus("CONSUMED");
        request.setRequestedAt(Date.from(TOKEN_EXCHANGED_AT.minusSeconds(120)));
        request.setExpiresAt(Date.from(TOKEN_EXCHANGED_AT.plusSeconds(180)));
        request.setApprovedAt(Date.from(TOKEN_EXCHANGED_AT.minusSeconds(30)));
        request.setConsumedAt(Date.from(TOKEN_EXCHANGED_AT));
        request.setVersion(2L);
        request.setCreatedAt(Date.from(TOKEN_EXCHANGED_AT.minusSeconds(120)));
        request.setUpdatedAt(Date.from(TOKEN_EXCHANGED_AT));
        return request;
    }

    private static BoardOAuthAuthorizationCode authorizationCode(
            BoardOAuthAuthorizationRequest request) {
        BoardOAuthAuthorizationCode code = new BoardOAuthAuthorizationCode();
        code.setId(202L);
        code.setCodeDigest(digest("authorization-code-secret"));
        code.setAuthorizationRequestId(request.getId());
        code.setClientId(request.getClientId());
        code.setRedirectUri(request.getRedirectUri());
        code.setCodeChallenge(request.getCodeChallenge());
        code.setCodeChallengeMethod(request.getCodeChallengeMethod());
        code.setIssuerUri(request.getIssuerUri());
        code.setResourceUri(request.getResourceUri());
        code.setProductCode(request.getProductCode());
        code.setSourceCode(request.getSourceCode());
        code.setConnectorCode(request.getConnectorCode());
        code.setScopeCanonical(request.getScopeCanonical());
        code.setScopeDigest(request.getScopeDigest().clone());
        code.setTenantId(request.getTenantId());
        code.setMemberId(request.getMemberId());
        code.setUserId(request.getUserId());
        code.setPrincipalSubjectDigest(request.getPrincipalSubjectDigest().clone());
        code.setConsentIntent(request.getConsentIntent());
        code.setStatus("USED");
        code.setIssuedAt(Date.from(TOKEN_EXCHANGED_AT.minusSeconds(30)));
        code.setExpiresAt(Date.from(TOKEN_EXCHANGED_AT.plusSeconds(30)));
        code.setUsedAt(Date.from(TOKEN_EXCHANGED_AT));
        code.setVersion(1L);
        code.setCreatedAt(Date.from(TOKEN_EXCHANGED_AT.minusSeconds(30)));
        code.setUpdatedAt(Date.from(TOKEN_EXCHANGED_AT));
        return code;
    }

    private static BoardOAuthTokenFamily pendingFamily(
            BoardOAuthAuthorizationCode code) {
        BoardOAuthTokenFamily family = new BoardOAuthTokenFamily();
        family.setId(303L);
        family.setFamilyId(FAMILY_ID);
        family.setOriginAuthorizationCodeId(code.getId());
        family.setClientId(code.getClientId());
        family.setTenantId(code.getTenantId());
        family.setMemberId(code.getMemberId());
        family.setUserId(code.getUserId());
        family.setProductCode(code.getProductCode());
        family.setSourceCode(code.getSourceCode());
        family.setConnectorCode(code.getConnectorCode());
        family.setIssuerUri(code.getIssuerUri());
        family.setResourceUri(code.getResourceUri());
        family.setScopeCanonical(code.getScopeCanonical());
        family.setScopeDigest(code.getScopeDigest().clone());
        family.setPrincipalSubjectDigest(code.getPrincipalSubjectDigest().clone());
        family.setConsentIntent(code.getConsentIntent());
        family.setStatus("PENDING_BINDING");
        family.setLifecycleSlot("PENDING_BINDING");
        family.setCurrentRefreshGeneration(0L);
        family.setIssuedAt(Date.from(TOKEN_EXCHANGED_AT));
        family.setExpiresAt(Date.from(
                TOKEN_EXCHANGED_AT.plusSeconds(30L * 24L * 60L * 60L)));
        family.setVersion(0L);
        family.setCreatedAt(Date.from(TOKEN_EXCHANGED_AT));
        family.setUpdatedAt(Date.from(TOKEN_EXCHANGED_AT));
        return family;
    }

    private static BoardOAuthToken token(
            Long id,
            BoardOAuthTokenFamily family,
            String tokenType,
            byte[] tokenDigest) {
        BoardOAuthToken token = new BoardOAuthToken();
        token.setId(id);
        token.setTokenDigest(tokenDigest);
        token.setFamilyId(family.getFamilyId());
        token.setTokenType(tokenType);
        token.setGeneration(0L);
        token.setResourceUri(family.getResourceUri());
        token.setScopeCanonical(family.getScopeCanonical());
        token.setScopeDigest(family.getScopeDigest().clone());
        token.setStatus("ACTIVE");
        token.setActiveRefreshSlot("REFRESH".equals(tokenType) ? 1 : null);
        token.setIssuedAt(Date.from(TOKEN_EXCHANGED_AT));
        token.setExpiresAt("ACCESS".equals(tokenType)
                ? Date.from(TOKEN_EXCHANGED_AT.plusSeconds(600))
                : copy(family.getExpiresAt()));
        token.setVersion(0L);
        token.setCreatedAt(Date.from(TOKEN_EXCHANGED_AT));
        token.setUpdatedAt(Date.from(TOKEN_EXCHANGED_AT));
        return token;
    }

    private static BoardConnectorBinding existingBinding() {
        BoardConnectorBinding binding = new BoardConnectorBinding();
        binding.setId(501L);
        binding.setBindingId(EXISTING_BINDING_ID);
        binding.setTenantId(TENANT_ID);
        binding.setMemberId(MEMBER_ID);
        binding.setUserId(USER_ID);
        binding.setProductCode(IndependentBoardEntitlementService.PRODUCT_CODE);
        binding.setSourceCode(IndependentBoardConnectorBindingService.SOURCE_CODE);
        binding.setConnectorCode(IndependentBoardConnectorBindingService.CONNECTOR_CODE);
        binding.setIssuerUri(BoardOAuthProfile.ISSUER);
        binding.setResourceUri(BoardOAuthProfile.RESOURCE);
        binding.setClientId(OLD_CLIENT_ID);
        binding.setPrincipalSubjectDigest(
                java.util.HexFormat.of().formatHex(principalDigest()));
        binding.setStatus("ACTIVE");
        binding.setVerificationMethod(
                IndependentBoardConnectorBindingService.VERIFY_INITIALIZE);
        binding.setEvidenceDigest("e".repeat(64));
        binding.setVerifiedAt(Date.from(NOW.minusSeconds(300)));
        binding.setLastSeenAt(Date.from(NOW.minusSeconds(120)));
        binding.setValidUntil(Date.from(NOW.plusSeconds(3600)));
        binding.setVersion(3L);
        binding.setCreatedAt(Date.from(NOW.minusSeconds(7200)));
        binding.setUpdatedAt(Date.from(NOW.minusSeconds(120)));
        binding.setScopes(BoardOAuthProfile.REQUIRED_SCOPES);
        return binding;
    }

    private static BoardOAuthTokenFamily oldActiveFamily(
            BoardConnectorBinding existing) {
        BoardOAuthTokenFamily family = new BoardOAuthTokenFamily();
        family.setId(302L);
        family.setFamilyId(OLD_FAMILY_ID);
        family.setOriginAuthorizationCodeId(909L);
        family.setClientId(OLD_CLIENT_ID);
        family.setTenantId(TENANT_ID);
        family.setMemberId(MEMBER_ID);
        family.setUserId(USER_ID);
        family.setProductCode(IndependentBoardEntitlementService.PRODUCT_CODE);
        family.setSourceCode(IndependentBoardConnectorBindingService.SOURCE_CODE);
        family.setConnectorCode(IndependentBoardConnectorBindingService.CONNECTOR_CODE);
        family.setIssuerUri(BoardOAuthProfile.ISSUER);
        family.setResourceUri(BoardOAuthProfile.RESOURCE);
        family.setScopeCanonical(BoardOAuthProfile.CANONICAL_SCOPE);
        family.setScopeDigest(scopeDigest());
        family.setPrincipalSubjectDigest(principalDigest());
        family.setConsentIntent(BoardOAuthConsentIntent.FIRST_CONNECT);
        family.setBindingId(existing.getBindingId());
        family.setBindingVersion(existing.getVersion());
        family.setStatus("ACTIVE");
        family.setLifecycleSlot("ACTIVE");
        family.setCurrentRefreshGeneration(1L);
        family.setIssuedAt(Date.from(NOW.minusSeconds(3600)));
        family.setActivatedAt(Date.from(NOW.minusSeconds(3500)));
        family.setExpiresAt(Date.from(NOW.plusSeconds(3600)));
        family.setVersion(5L);
        family.setCreatedAt(Date.from(NOW.minusSeconds(3600)));
        family.setUpdatedAt(Date.from(NOW.minusSeconds(3500)));
        return family;
    }

    private static List<BoardOAuthToken> oldActiveTokens(
            BoardOAuthTokenFamily family) {
        return List.of(
                oldActiveToken(
                        801L,
                        family,
                        "ACCESS",
                        digest("old-access-token")),
                oldActiveToken(
                        802L,
                        family,
                        "REFRESH",
                        digest("old-refresh-token")));
    }

    private static BoardOAuthToken oldActiveToken(
            Long id,
            BoardOAuthTokenFamily family,
            String tokenType,
            byte[] tokenDigest) {
        BoardOAuthToken token = token(id, family, tokenType, tokenDigest);
        Date issuedAt = Date.from(NOW.minusSeconds(600));
        token.setGeneration(1L);
        token.setIssuedAt(copy(issuedAt));
        token.setExpiresAt("ACCESS".equals(tokenType)
                ? Date.from(NOW.plusSeconds(600))
                : copy(family.getExpiresAt()));
        token.setVersion(2L);
        token.setCreatedAt(copy(issuedAt));
        token.setUpdatedAt(copy(issuedAt));
        return token;
    }

    private static BoardOAuthReceipt createdReceipt(
            BoardOAuthAuthorizationRequest request,
            BoardOAuthAuthorizationCode code,
            BoardOAuthTokenFamily family,
            List<BoardOAuthToken> tokens) {
        BoardOAuthReceipt receipt = BoardOAuthTokenFamilyCreatedReceiptFactory.create(
                RECEIPT_ID,
                CORRELATION_ID,
                TOKEN_EXCHANGED_AT,
                request,
                code,
                family,
                tokens);
        receipt.setId(404L);
        return receipt;
    }

    private static TokenContextLocator tokenLocator(
            BoardOAuthTokenFamily family,
            BoardOAuthToken accessToken) {
        TokenContextLocator locator = new TokenContextLocator();
        locator.setTokenId(accessToken.getId());
        locator.setFamilyId(family.getFamilyId());
        locator.setTokenType(accessToken.getTokenType());
        locator.setTokenGeneration(accessToken.getGeneration());
        locator.setTokenStatus(accessToken.getStatus());
        locator.setTokenVersion(accessToken.getVersion());
        locator.setTokenIssuedAt(copy(accessToken.getIssuedAt()));
        locator.setTokenExpiresAt(copy(accessToken.getExpiresAt()));
        locator.setClientId(family.getClientId());
        locator.setTenantId(family.getTenantId());
        locator.setMemberId(family.getMemberId());
        locator.setUserId(family.getUserId());
        locator.setProductCode(family.getProductCode());
        locator.setSourceCode(family.getSourceCode());
        locator.setConnectorCode(family.getConnectorCode());
        locator.setIssuerUri(family.getIssuerUri());
        locator.setResourceUri(family.getResourceUri());
        locator.setScopeCanonical(family.getScopeCanonical());
        locator.setScopeDigest(family.getScopeDigest().clone());
        locator.setPrincipalSubjectDigest(family.getPrincipalSubjectDigest().clone());
        locator.setConsentIntent(family.getConsentIntent());
        locator.setBindingId(family.getBindingId());
        locator.setBindingVersion(family.getBindingVersion());
        locator.setFamilyStatus(family.getStatus());
        locator.setCurrentRefreshGeneration(family.getCurrentRefreshGeneration());
        locator.setFamilyVersion(family.getVersion());
        locator.setFamilyIssuedAt(copy(family.getIssuedAt()));
        locator.setFamilyActivatedAt(copy(family.getActivatedAt()));
        locator.setFamilyExpiresAt(copy(family.getExpiresAt()));
        return locator;
    }

    private static BoardOAuthClient client() {
        Date registeredAt = Date.from(TOKEN_EXCHANGED_AT);
        BoardOAuthClient client = new BoardOAuthClient();
        client.setId(91L);
        client.setClientId(CLIENT_ID);
        client.setClientName(
                IndependentBoardOAuthClientRegistrationService.NEUTRAL_CLIENT_NAME);
        client.setIssuerUri(BoardOAuthProfile.ISSUER);
        client.setResourceUri(BoardOAuthProfile.RESOURCE);
        client.setProductCode(IndependentBoardEntitlementService.PRODUCT_CODE);
        client.setSourceCode(IndependentBoardConnectorBindingService.SOURCE_CODE);
        client.setConnectorCode(IndependentBoardConnectorBindingService.CONNECTOR_CODE);
        client.setRedirectPort(17654);
        client.setRedirectUri("http://127.0.0.1:17654/oauth/callback");
        client.setTokenEndpointAuthMethod("none");
        client.setGrantTypesCanonical("authorization_code refresh_token");
        client.setResponseTypesCanonical("code");
        client.setScopeCanonical(BoardOAuthProfile.CANONICAL_SCOPE);
        client.setScopeDigest(scopeDigest());
        client.setMetadataDigest(digest("client-metadata"));
        client.setRegistrationSourceDigest(digest("registration-source"));
        client.setStatus("ACTIVE");
        client.setRegisteredAt(registeredAt);
        client.setExpiresAt(new Date(
                registeredAt.getTime() + 31L * 24L * 60L * 60L * 1000L));
        client.setVersion(0L);
        client.setCreatedAt(copy(registeredAt));
        client.setUpdatedAt(copy(registeredAt));
        return client;
    }

    private static BoardEnterpriseAuthority enterprise() {
        BoardEnterpriseAuthority value = new BoardEnterpriseAuthority();
        value.setTenantId(TENANT_ID);
        value.setTenantName("Board tenant");
        value.setStatus(1);
        value.setDelFlag("0");
        return value;
    }

    private static BoardEnterpriseMemberScope member() {
        BoardEnterpriseMemberScope member = new BoardEnterpriseMemberScope();
        member.setTenantId(TENANT_ID);
        member.setMemberId(MEMBER_ID);
        member.setUserId(USER_ID);
        member.setTenantName("FBSir test tenant");
        member.setMemberRole("OWNER");
        member.setStatus(1);
        member.setDelFlag("0");
        return member;
    }

    private static BoardProductEntitlement entitlement() {
        BoardProductEntitlement entitlement = new BoardProductEntitlement();
        entitlement.setId(81L);
        entitlement.setTenantId(TENANT_ID);
        entitlement.setMemberId(MEMBER_ID);
        entitlement.setUserId(USER_ID);
        entitlement.setProductCode(IndependentBoardEntitlementService.PRODUCT_CODE);
        entitlement.setPlanCode(IndependentBoardEntitlementService.VIP_PLAN);
        entitlement.setStatus("ACTIVE");
        entitlement.setValidFrom(Date.from(NOW.minusSeconds(3600)));
        entitlement.setValidUntil(Date.from(
                NOW.plusSeconds(40L * 24L * 60L * 60L)));
        entitlement.setVersion(1L);
        entitlement.setCreatedAt(Date.from(NOW.minusSeconds(7200)));
        entitlement.setUpdatedAt(Date.from(NOW.minusSeconds(3600)));
        return entitlement;
    }

    private static BoardProductPlan plan() {
        BoardProductPlan plan = new BoardProductPlan();
        plan.setProductCode(IndependentBoardEntitlementService.PRODUCT_CODE);
        plan.setPlanCode(IndependentBoardEntitlementService.VIP_PLAN);
        plan.setVip(true);
        plan.setConnectorRequired(true);
        plan.setDailyMeetingLimit(5);
        plan.setAgendaLimit(30);
        plan.setSeatLimit(null);
        plan.setSecretaryEnabled(true);
        plan.setStatus("ACTIVE");
        return plan;
    }

    private static void applyProfile(BoardOAuthAuthorizationRequest request) {
        request.setIssuerUri(BoardOAuthProfile.ISSUER);
        request.setResourceUri(BoardOAuthProfile.RESOURCE);
        request.setProductCode(IndependentBoardEntitlementService.PRODUCT_CODE);
        request.setSourceCode(IndependentBoardConnectorBindingService.SOURCE_CODE);
        request.setConnectorCode(IndependentBoardConnectorBindingService.CONNECTOR_CODE);
        request.setScopeCanonical(BoardOAuthProfile.CANONICAL_SCOPE);
        request.setScopeDigest(scopeDigest());
    }

    private static byte[] principalDigest() {
        return BoardOAuthPrincipalSubject.digest(TENANT_ID, MEMBER_ID, USER_ID);
    }

    private static byte[] scopeDigest() {
        return BoardOAuthCrypto.sha256Ascii(BoardOAuthProfile.CANONICAL_SCOPE);
    }

    private static byte[] digest(String value) {
        return BoardOAuthCrypto.sha256Ascii(value);
    }

    private static Date copy(Date value) {
        return value == null ? null : new Date(value.getTime());
    }
}
