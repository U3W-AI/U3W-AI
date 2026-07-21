package com.wx.fbsir.business.board.oauth.service;

import com.wx.fbsir.business.board.oauth.BoardOAuthProfile;
import com.wx.fbsir.business.board.oauth.BoardOAuthProtocolException;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthRefreshResult;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Spring proxy proof for refresh publication and replay-containment commit order. */
class IndependentBoardOAuthRefreshSpringProxyContractTest {
    private AnnotationConfigApplicationContext context;

    @BeforeEach
    void openContext() {
        context = new AnnotationConfigApplicationContext(ConfigurationUnderTest.class);
    }

    @AfterEach
    void closeContext() {
        if (context != null) {
            context.close();
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(null);
    }

    @Test
    void rawReplacementTokensCrossFacadeOnlyAfterNamedProxyCommits() {
        IndependentBoardOAuthRefreshFacade facade =
                context.getBean(IndependentBoardOAuthRefreshFacade.class);
        IndependentBoardOAuthRefreshService service =
                context.getBean(IndependentBoardOAuthRefreshService.class);
        RecordingManager manager =
                context.getBean("transactionManager", RecordingManager.class);
        RecordingManager decoy =
                context.getBean("decoyTransactionManager", RecordingManager.class);
        BoardOAuthRefreshResult expected = result();
        when(service.refreshForCommit(null)).thenAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(TransactionDefinition.ISOLATION_REPEATABLE_READ,
                    TransactionSynchronizationManager
                            .getCurrentTransactionIsolationLevel());
            assertEquals(0, manager.commits);
            return BoardOAuthRefreshOutcome.completed(expected);
        });

        BoardOAuthRefreshResult actual = facade.refresh(null);

        assertSame(expected, actual);
        assertEquals(1, manager.begins);
        assertEquals(1, manager.commits);
        assertEquals(0, manager.rollbacks);
        assertEquals(TransactionDefinition.ISOLATION_REPEATABLE_READ,
                manager.lastIsolation);
        assertEquals(0, decoy.begins);
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    @Test
    void successorWriteFailureRollsBackAndPublishesNoRawResult() {
        IndependentBoardOAuthRefreshFacade facade =
                context.getBean(IndependentBoardOAuthRefreshFacade.class);
        IndependentBoardOAuthRefreshService service =
                context.getBean(IndependentBoardOAuthRefreshService.class);
        RecordingManager manager =
                context.getBean("transactionManager", RecordingManager.class);
        RecordingManager decoy =
                context.getBean("decoyTransactionManager", RecordingManager.class);
        AtomicBoolean failureObservedInsideTransaction = new AtomicBoolean();
        BoardOAuthProtocolException expected =
                BoardOAuthProtocolException.conflict(
                        IndependentBoardOAuthRefreshService.CONFLICT);
        when(service.refreshForCommit(null)).thenAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(TransactionDefinition.ISOLATION_REPEATABLE_READ,
                    TransactionSynchronizationManager
                            .getCurrentTransactionIsolationLevel());
            failureObservedInsideTransaction.set(true);
            throw expected;
        });

        BoardOAuthProtocolException actual = assertThrows(
                BoardOAuthProtocolException.class,
                () -> facade.refresh(null));

        assertSame(expected, actual);
        assertTrue(failureObservedInsideTransaction.get());
        assertEquals(1, manager.begins);
        assertEquals(0, manager.commits);
        assertEquals(1, manager.rollbacks);
        assertEquals(TransactionDefinition.ISOLATION_REPEATABLE_READ,
                manager.lastIsolation);
        assertEquals(0, decoy.begins);
        assertEquals(0, decoy.commits);
        assertEquals(0, decoy.rollbacks);
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    @Test
    void replayContainmentCommitsBeforeFacadeRaisesInvalidGrant() {
        IndependentBoardOAuthRefreshFacade facade =
                context.getBean(IndependentBoardOAuthRefreshFacade.class);
        IndependentBoardOAuthRefreshService service =
                context.getBean(IndependentBoardOAuthRefreshService.class);
        RecordingManager manager =
                context.getBean("transactionManager", RecordingManager.class);
        RecordingManager decoy =
                context.getBean("decoyTransactionManager", RecordingManager.class);
        when(service.refreshForCommit(null)).thenAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(0, manager.commits);
            assertEquals(0, manager.rollbacks);
            return BoardOAuthRefreshOutcome.invalidGrantAfterCommit(
                    IndependentBoardOAuthRefreshService.REPLAY_DETECTED);
        });

        BoardOAuthProtocolException failure = assertThrows(
                BoardOAuthProtocolException.class,
                () -> facade.refresh(null));

        assertEquals("invalid_grant", failure.oauthError());
        assertEquals(IndependentBoardOAuthRefreshService.REPLAY_DETECTED,
                failure.reasonCode());
        assertEquals(1, manager.begins);
        assertEquals(1, manager.commits);
        assertEquals(0, manager.rollbacks);
        assertEquals(0, decoy.begins);
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class ConfigurationUnderTest {
        @Bean("transactionManager")
        RecordingManager transactionManager() {
            return new RecordingManager();
        }

        @Bean("decoyTransactionManager")
        RecordingManager decoyTransactionManager() {
            return new RecordingManager();
        }

        @Bean
        IndependentBoardOAuthRefreshService refreshService() {
            return mock(IndependentBoardOAuthRefreshService.class);
        }

        @Bean
        IndependentBoardOAuthRefreshTransactionRunner refreshRunner(
                IndependentBoardOAuthRefreshService service) {
            return new IndependentBoardOAuthRefreshTransactionRunner(service);
        }

        @Bean
        IndependentBoardOAuthRefreshFacade refreshFacade(
                IndependentBoardOAuthRefreshTransactionRunner runner) {
            return new IndependentBoardOAuthRefreshFacade(runner);
        }
    }

    static final class RecordingManager extends AbstractPlatformTransactionManager {
        private static final long serialVersionUID = 1L;
        private final ThreadLocal<TransactionState> current = new ThreadLocal<>();
        private int begins;
        private int commits;
        private int rollbacks;
        private int lastIsolation = TransactionDefinition.ISOLATION_DEFAULT;

        @Override
        protected Object doGetTransaction() {
            TransactionState state = current.get();
            return state == null ? new TransactionState() : state;
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return ((TransactionState) transaction).active;
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            TransactionState state = (TransactionState) transaction;
            state.active = true;
            current.set(state);
            begins++;
            lastIsolation = definition.getIsolationLevel();
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commits++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks++;
        }

        @Override
        protected void doCleanupAfterCompletion(Object transaction) {
            ((TransactionState) transaction).active = false;
            current.remove();
        }
    }

    static final class TransactionState {
        private boolean active;
    }

    private static BoardOAuthRefreshResult result() {
        return new BoardOAuthRefreshResult(
                "a".repeat(43), "r".repeat(43), "Bearer", 600,
                BoardOAuthProfile.CANONICAL_SCOPE,
                Instant.EPOCH.plusSeconds(600), Instant.EPOCH.plusSeconds(3_600),
                "i".repeat(43), "o".repeat(43));
    }
}
