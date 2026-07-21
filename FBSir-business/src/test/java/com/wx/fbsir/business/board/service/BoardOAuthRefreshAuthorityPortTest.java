package com.wx.fbsir.business.board.service;

import com.wx.fbsir.business.board.config.IndependentBoardConnectorProperties;
import com.wx.fbsir.business.board.domain.BoardConnectorBinding;
import com.wx.fbsir.business.board.domain.BoardConnectorBindingReceipt;
import com.wx.fbsir.business.board.domain.BoardEnterpriseAuthority;
import com.wx.fbsir.business.board.domain.BoardEnterpriseMemberScope;
import com.wx.fbsir.business.board.domain.BoardProductEntitlement;
import com.wx.fbsir.business.board.domain.BoardProductPlan;
import com.wx.fbsir.business.board.mapper.IndependentBoardMapper;
import com.wx.fbsir.business.board.oauth.BoardOAuthPrincipalSubject;
import com.wx.fbsir.business.board.oauth.BoardOAuthProfile;
import com.wx.fbsir.business.board.oauth.mapper.IndependentBoardOAuthMapper;
import com.wx.fbsir.business.board.oauth.service.BoardOAuthRefreshAuthorityPort;
import com.wx.fbsir.common.exception.ServiceException;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BoardOAuthRefreshAuthorityPortTest {
    private static final Instant NOW = Instant.parse("2026-07-21T07:08:09Z");
    private static final Long TENANT_ID = 7L;
    private static final Long MEMBER_ID = 11L;
    private static final Long USER_ID = 42L;
    private static final String PRODUCT = IndependentBoardEntitlementService.PRODUCT_CODE;
    private static final String SOURCE = IndependentBoardConnectorBindingService.SOURCE_CODE;
    private static final String CONNECTOR = IndependentBoardConnectorBindingService.CONNECTOR_CODE;
    private static final String BINDING_ID = "11111111-1111-1111-1111-111111111111";
    private static final String CLIENT_ID = "c".repeat(43);
    private static final List<String> SCOPES = BoardOAuthProfile.REQUIRED_SCOPES;

    private IndependentBoardMapper mapper;
    private IndependentBoardOAuthMapper oauthMapper;
    private DataSource boardDataSource;
    private Connection boardConnection;
    private IndependentBoardConnectorBindingService service;
    private BoardConnectorBinding binding;

    @BeforeEach
    void setUp() throws Exception {
        mapper = mock(IndependentBoardMapper.class);
        oauthMapper = mock(IndependentBoardOAuthMapper.class);
        boardDataSource = mock(DataSource.class);
        boardConnection = mock(Connection.class);
        when(boardConnection.isClosed()).thenReturn(false);
        when(boardConnection.getAutoCommit()).thenReturn(false);
        when(boardConnection.getTransactionIsolation())
                .thenReturn(Connection.TRANSACTION_REPEATABLE_READ);
        service = new IndependentBoardConnectorBindingService(
                mapper,
                oauthMapper,
                properties(),
                boardDataSource,
                Clock.fixed(NOW, ZoneOffset.UTC));
        binding = activeBinding();
        stubAuthorityPrefix(binding);
    }

    @Test
    void lockForRefreshUsesTheAuthorityPrefixThenLocksW4aReceiptsOnDemand() {
        BoardOAuthRefreshAuthorityPort.LockResult result = inRootTransaction(() -> {
            BoardOAuthRefreshAuthorityPort.LockResult locked = service.lockForRefresh(
                        TENANT_ID, MEMBER_ID, USER_ID,
                        PRODUCT, SOURCE, CONNECTOR, NOW.minusMillis(1));
            verify(mapper, never()).selectConnectorBindingReceiptsForUpdate(
                    BINDING_ID);
            service.lockReceiptsForRefresh(locked.lease());
            return locked;
        });

        assertTrue(result.rotationAuthorityCurrentAt(NOW));
        assertEquals(BINDING_ID, result.bindingId());
        assertEquals(1L, result.bindingVersion());
        assertEquals(CLIENT_ID, result.bindingClientId());
        assertArrayEquals(
                BoardOAuthPrincipalSubject.digest(TENANT_ID, MEMBER_ID, USER_ID),
                result.principalSubjectDigest());
        assertEquals(SCOPES, result.scopes());
        assertEquals(NOW, result.observedAt());
        assertEquals(NOW.plusSeconds(3600), result.authorityValidUntilExclusive());

        InOrder order = inOrder(mapper);
        order.verify(mapper).selectEnterpriseSlotForUpdate(TENANT_ID);
        order.verify(mapper).selectMemberSlotForUpdate(TENANT_ID, MEMBER_ID);
        order.verify(mapper).selectEntitlementForUpdate(TENANT_ID, MEMBER_ID, PRODUCT);
        order.verify(mapper).selectPlanSlotForUpdate(
                PRODUCT, IndependentBoardEntitlementService.VIP_PLAN);
        order.verify(mapper).selectConnectorBindingSlotForUpdate(
                TENANT_ID, MEMBER_ID, PRODUCT, SOURCE, CONNECTOR);
        order.verify(mapper).selectConnectorBindingScopesForUpdate(BINDING_ID);
        order.verify(mapper).selectConnectorBindingReceiptsForUpdate(BINDING_ID);
        verifyNoInteractions(oauthMapper);
    }

    @Test
    void leaseRejectsWrongTypeForeignOwnerAndCrossThreadUse() throws Exception {
        inRootTransaction(() -> {
            BoardOAuthRefreshAuthorityPort.LockResult result = lock();

            ServiceException wrongType = assertThrows(
                    ServiceException.class,
                    () -> service.revokeForRefreshReplay(
                            new BoardOAuthRefreshAuthorityPort.Lease() { },
                            BINDING_ID, 1L, NOW));
            assertLeaseInvalid(wrongType);

            IndependentBoardConnectorBindingService foreignService =
                    new IndependentBoardConnectorBindingService(
                            mapper,
                            oauthMapper,
                            properties(),
                            boardDataSource,
                            Clock.fixed(NOW, ZoneOffset.UTC));
            ServiceException foreignOwner = assertThrows(
                    ServiceException.class,
                    () -> foreignService.revokeForRefreshReplay(
                            result.lease(), BINDING_ID, 1L, NOW));
            assertLeaseInvalid(foreignOwner);

            AtomicReference<Throwable> crossThreadFailure = new AtomicReference<>();
            Thread worker = new Thread(() -> {
                try {
                    service.revokeForRefreshReplay(
                            result.lease(), BINDING_ID, 1L, NOW);
                } catch (Throwable failure) {
                    crossThreadFailure.set(failure);
                }
            }, "refresh-authority-cross-thread-test");
            worker.start();
            try {
                worker.join(5_000L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
            assertFalse(worker.isAlive());
            assertTrue(crossThreadFailure.get() instanceof ServiceException);
            assertLeaseInvalid((ServiceException) crossThreadFailure.get());
            return null;
        });

        verify(mapper, never()).revokeConnectorBindingIfVersion(any(), anyLong());
        verify(mapper, never()).insertConnectorBindingReceipt(any());
    }

    @Test
    void replayMutationRequiresTheExplicitW4aReceiptLockPhase() {
        inRootTransaction(() -> {
            BoardOAuthRefreshAuthorityPort.LockResult locked = lock();
            ServiceException failure = assertThrows(
                    ServiceException.class,
                    () -> service.revokeForRefreshReplay(
                            locked.lease(), BINDING_ID, 1L, NOW));
            assertLeaseInvalid(failure);
            return null;
        });

        verify(mapper, never()).revokeConnectorBindingIfVersion(any(), anyLong());
        verify(mapper, never()).insertConnectorBindingReceipt(any());
    }

    @Test
    void leaseRejectsAReplacementTransactionResourceOrPhysicalConnection() throws Exception {
        inRootTransaction(() -> {
            BoardOAuthRefreshAuthorityPort.LockResult result = lock();
            TransactionSynchronizationManager.unbindResource(boardDataSource);
            Connection replacementConnection = mock(Connection.class);
            try {
                when(replacementConnection.isClosed()).thenReturn(false);
                when(replacementConnection.getAutoCommit()).thenReturn(false);
                when(replacementConnection.getTransactionIsolation())
                        .thenReturn(Connection.TRANSACTION_REPEATABLE_READ);
            } catch (Exception failure) {
                throw new AssertionError(failure);
            }
            ConnectionHolder replacement = new ConnectionHolder(replacementConnection);
            replacement.setSynchronizedWithTransaction(true);
            TransactionSynchronizationManager.bindResource(boardDataSource, replacement);

            ServiceException failure = assertThrows(
                    ServiceException.class,
                    () -> service.revokeForRefreshReplay(
                            result.lease(), BINDING_ID, 1L, NOW));
            assertLeaseInvalid(failure);
            return null;
        });

        verify(mapper, never()).revokeConnectorBindingIfVersion(any(), anyLong());
    }

    @Test
    void replayRevocationUsesCasAndProvesTheW4aCurrentReadBeforeConsumingLease() {
        AtomicReference<BoardConnectorBindingReceipt> insertedReceipt =
                stubSuccessfulRevocationCurrentRead();

        inRootTransaction(() -> {
            BoardOAuthRefreshAuthorityPort.LockResult locked = lockWithReceipts();
            BoardOAuthRefreshAuthorityPort.BindingRevocation result =
                    service.revokeForRefreshReplay(
                            locked.lease(), BINDING_ID, 1L, NOW);

            assertEquals(BINDING_ID, result.bindingId());
            assertEquals(1L, result.previousVersion());
            assertEquals(2L, result.revokedVersion());
            assertEquals(NOW, result.revokedAt());
            assertEquals(insertedReceipt.get().getReceiptId(), result.receiptId());
            assertEquals(insertedReceipt.get().getPayloadDigest(),
                    result.receiptPayloadDigest());

            ServiceException duplicate = assertThrows(
                    ServiceException.class,
                    () -> service.revokeForRefreshReplay(
                            locked.lease(), BINDING_ID, 1L, NOW));
            assertLeaseInvalid(duplicate);
            return null;
        });

        ArgumentCaptor<BoardConnectorBinding> updated =
                ArgumentCaptor.forClass(BoardConnectorBinding.class);
        verify(mapper).revokeConnectorBindingIfVersion(updated.capture(), anyLong());
        assertEquals("REVOKED", updated.getValue().getStatus());
        assertEquals(2L, updated.getValue().getVersion());
        assertEquals(Date.from(NOW), updated.getValue().getRevokedAt());
        verify(mapper).selectConnectorBindingForUpdate(
                TENANT_ID, MEMBER_ID, USER_ID, PRODUCT, SOURCE, CONNECTOR);
        verify(mapper).selectConnectorBindingScopes(BINDING_ID);
        verify(mapper).selectConnectorBindingReceiptForUpdate(
                insertedReceipt.get().getReceiptId());
    }

    @Test
    void casLossRejectsBeforeReceiptOrCurrentRead() {
        when(mapper.revokeConnectorBindingIfVersion(any(), anyLong())).thenReturn(0);

        ServiceException failure = assertThrows(
                ServiceException.class,
                () -> inRootTransaction(() -> {
                    BoardOAuthRefreshAuthorityPort.LockResult locked =
                            lockWithReceipts();
                    return service.revokeForRefreshReplay(
                            locked.lease(), BINDING_ID, 1L, NOW);
                }));

        assertEquals(409, failure.getCode());
        assertEquals("BOARD_OAUTH_REFRESH_BINDING_CONFLICT", failure.getMessage());
        verify(mapper, never()).insertConnectorBindingReceipt(any());
        verify(mapper, never()).selectConnectorBindingForUpdate(
                anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyString());
        verify(mapper, never()).selectConnectorBindingReceiptForUpdate(anyString());
    }

    @Test
    void invalidW4aCurrentReadRejectsAfterReceiptWrite() {
        AtomicReference<BoardConnectorBindingReceipt> insertedReceipt =
                stubSuccessfulRevocationCurrentRead();
        when(mapper.selectConnectorBindingForUpdate(
                TENANT_ID, MEMBER_ID, USER_ID, PRODUCT, SOURCE, CONNECTOR))
                .thenReturn(null);

        inRootTransaction(() -> {
            BoardOAuthRefreshAuthorityPort.LockResult locked = lockWithReceipts();
            ServiceException failure = assertThrows(
                    ServiceException.class,
                    () -> service.revokeForRefreshReplay(
                            locked.lease(), BINDING_ID, 1L, NOW));
            assertEquals(500, failure.getCode());
            assertEquals("BOARD_OAUTH_REFRESH_BINDING_CURRENT_READ_INVALID",
                    failure.getMessage());
            assertNotNull(insertedReceipt.get());
            return null;
        });
    }

    private BoardOAuthRefreshAuthorityPort.LockResult lock() {
        return service.lockForRefresh(
                TENANT_ID, MEMBER_ID, USER_ID,
                PRODUCT, SOURCE, CONNECTOR, NOW);
    }

    private BoardOAuthRefreshAuthorityPort.LockResult lockWithReceipts() {
        BoardOAuthRefreshAuthorityPort.LockResult locked = lock();
        service.lockReceiptsForRefresh(locked.lease());
        return locked;
    }

    private void stubAuthorityPrefix(BoardConnectorBinding currentBinding) {
        when(mapper.selectEnterpriseSlotForUpdate(TENANT_ID)).thenReturn(enterprise());
        when(mapper.selectMemberSlotForUpdate(TENANT_ID, MEMBER_ID)).thenReturn(member());
        when(mapper.selectEntitlementForUpdate(TENANT_ID, MEMBER_ID, PRODUCT))
                .thenReturn(entitlement());
        when(mapper.selectPlanSlotForUpdate(
                PRODUCT, IndependentBoardEntitlementService.VIP_PLAN))
                .thenReturn(vipPlan());
        when(mapper.selectConnectorBindingSlotForUpdate(
                TENANT_ID, MEMBER_ID, PRODUCT, SOURCE, CONNECTOR))
                .thenReturn(currentBinding);
        when(mapper.selectConnectorBindingScopesForUpdate(BINDING_ID)).thenReturn(SCOPES);
        when(mapper.selectConnectorBindingReceiptsForUpdate(BINDING_ID))
                .thenReturn(List.of(existingReceipt()));
    }

    private AtomicReference<BoardConnectorBindingReceipt>
            stubSuccessfulRevocationCurrentRead() {
        AtomicReference<BoardConnectorBindingReceipt> inserted = new AtomicReference<>();
        when(mapper.revokeConnectorBindingIfVersion(any(), anyLong())).thenReturn(1);
        when(mapper.insertConnectorBindingReceipt(any())).thenAnswer(invocation -> {
            BoardConnectorBindingReceipt receipt = invocation.getArgument(0);
            inserted.set(receipt);
            return 1;
        });
        when(mapper.selectConnectorBindingForUpdate(
                TENANT_ID, MEMBER_ID, USER_ID, PRODUCT, SOURCE, CONNECTOR))
                .thenAnswer(invocation -> binding);
        when(mapper.selectConnectorBindingScopes(BINDING_ID)).thenReturn(SCOPES);
        when(mapper.selectConnectorBindingReceiptForUpdate(anyString()))
                .thenAnswer(invocation -> {
                    BoardConnectorBindingReceipt receipt = inserted.get();
                    return receipt != null
                                    && receipt.getReceiptId().equals(invocation.getArgument(0))
                            ? receipt : null;
                });
        return inserted;
    }

    private <T> T inRootTransaction(Supplier<T> work) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("test transaction already active");
        }
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        ConnectionHolder holder = new ConnectionHolder(boardConnection);
        holder.setSynchronizedWithTransaction(true);
        TransactionSynchronizationManager.bindResource(boardDataSource, holder);
        try {
            return work.get();
        } finally {
            if (TransactionSynchronizationManager.hasResource(boardDataSource)) {
                TransactionSynchronizationManager.unbindResource(boardDataSource);
            }
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.clearSynchronization();
            }
            TransactionSynchronizationManager.setActualTransactionActive(false);
            TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
            TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(null);
            TransactionSynchronizationManager.setCurrentTransactionName(null);
        }
    }

    private void assertLeaseInvalid(ServiceException failure) {
        assertEquals(500, failure.getCode());
        assertEquals("BOARD_OAUTH_REFRESH_AUTHORITY_LEASE_INVALID", failure.getMessage());
    }

    private IndependentBoardConnectorProperties properties() {
        IndependentBoardConnectorProperties value = new IndependentBoardConnectorProperties();
        value.setIssuerUri(BoardOAuthProfile.ISSUER);
        value.setResourceUri(BoardOAuthProfile.RESOURCE);
        return value;
    }

    private BoardEnterpriseAuthority enterprise() {
        BoardEnterpriseAuthority value = new BoardEnterpriseAuthority();
        value.setTenantId(TENANT_ID);
        value.setStatus(1);
        value.setDelFlag("0");
        return value;
    }

    private BoardEnterpriseMemberScope member() {
        BoardEnterpriseMemberScope value = new BoardEnterpriseMemberScope();
        value.setTenantId(TENANT_ID);
        value.setMemberId(MEMBER_ID);
        value.setUserId(USER_ID);
        value.setStatus(1);
        value.setDelFlag("0");
        return value;
    }

    private BoardProductEntitlement entitlement() {
        BoardProductEntitlement value = new BoardProductEntitlement();
        value.setId(101L);
        value.setTenantId(TENANT_ID);
        value.setMemberId(MEMBER_ID);
        value.setUserId(USER_ID);
        value.setProductCode(PRODUCT);
        value.setPlanCode(IndependentBoardEntitlementService.VIP_PLAN);
        value.setStatus("ACTIVE");
        value.setValidFrom(Date.from(NOW.minusSeconds(3600)));
        value.setValidUntil(Date.from(NOW.plusSeconds(7200)));
        value.setVersion(1L);
        return value;
    }

    private BoardProductPlan vipPlan() {
        BoardProductPlan value = new BoardProductPlan();
        value.setProductCode(PRODUCT);
        value.setPlanCode(IndependentBoardEntitlementService.VIP_PLAN);
        value.setVip(true);
        value.setConnectorRequired(true);
        value.setDailyMeetingLimit(5);
        value.setAgendaLimit(30);
        value.setSeatLimit(null);
        value.setSecretaryEnabled(true);
        value.setStatus("ACTIVE");
        return value;
    }

    private BoardConnectorBinding activeBinding() {
        BoardConnectorBinding value = new BoardConnectorBinding();
        value.setId(201L);
        value.setBindingId(BINDING_ID);
        value.setTenantId(TENANT_ID);
        value.setMemberId(MEMBER_ID);
        value.setUserId(USER_ID);
        value.setProductCode(PRODUCT);
        value.setSourceCode(SOURCE);
        value.setConnectorCode(CONNECTOR);
        value.setIssuerUri(BoardOAuthProfile.ISSUER);
        value.setResourceUri(BoardOAuthProfile.RESOURCE);
        value.setClientId(CLIENT_ID);
        value.setPrincipalSubjectDigest(HexFormat.of().formatHex(
                BoardOAuthPrincipalSubject.digest(TENANT_ID, MEMBER_ID, USER_ID)));
        value.setStatus("ACTIVE");
        value.setVerificationMethod(IndependentBoardConnectorBindingService.VERIFY_INITIALIZE);
        value.setEvidenceDigest("e".repeat(64));
        value.setVerifiedAt(Date.from(NOW.minusSeconds(120)));
        value.setLastSeenAt(Date.from(NOW.minusSeconds(60)));
        value.setValidUntil(Date.from(NOW.plusSeconds(3600)));
        value.setRevokedAt(null);
        value.setVersion(1L);
        value.setCreatedAt(Date.from(NOW.minusSeconds(120)));
        value.setUpdatedAt(Date.from(NOW.minusSeconds(60)));
        value.setScopes(SCOPES);
        return value;
    }

    private BoardConnectorBindingReceipt existingReceipt() {
        BoardConnectorBindingReceipt value = new BoardConnectorBindingReceipt();
        value.setReceiptId("existing-w4a-receipt");
        value.setBindingId(BINDING_ID);
        value.setTenantId(TENANT_ID);
        value.setMemberId(MEMBER_ID);
        value.setUserId(USER_ID);
        value.setActorUserId(USER_ID);
        value.setAction("CONNECTOR_BINDING_VERIFIED");
        value.setPayloadDigest("a".repeat(64));
        value.setEvidenceLevel("ACTION_COMPLETED");
        value.setCreatedAt(Date.from(NOW.minusSeconds(120)));
        return value;
    }
}
