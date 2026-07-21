package com.wx.fbsir.business.board.oauth.service;

import com.wx.fbsir.business.board.oauth.BoardOAuthProtocolException;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthTokenExchangeResult;
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

/** Spring proxy proof of the publication boundary; this is not a database test. */
class IndependentBoardOAuthTokenExchangeSpringProxyContractTest {
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
    void rawResultCrossesFacadeOnlyAfterNamedProxyCommits() {
        IndependentBoardOAuthTokenExchangeFacade facade =
                context.getBean(IndependentBoardOAuthTokenExchangeFacade.class);
        IndependentBoardOAuthTokenExchangeService service =
                context.getBean(IndependentBoardOAuthTokenExchangeService.class);
        RecordingManager manager =
                context.getBean("transactionManager", RecordingManager.class);
        RecordingManager decoy =
                context.getBean("decoyTransactionManager", RecordingManager.class);
        BoardOAuthTokenExchangeResult expected = new BoardOAuthTokenExchangeResult(
                "a".repeat(43), "r".repeat(43), "Bearer", 600,
                "scope", Instant.EPOCH, Instant.EPOCH,
                "i".repeat(43), "o".repeat(43));
        when(service.exchangeForCommit(null)).thenAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(TransactionDefinition.ISOLATION_REPEATABLE_READ,
                    TransactionSynchronizationManager
                            .getCurrentTransactionIsolationLevel());
            assertEquals(0, manager.commits);
            return BoardOAuthTokenExchangeOutcome.completed(expected);
        });

        BoardOAuthTokenExchangeResult actual = facade.exchange(null);

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
    void successorInsertFailureRollsBackTheCleanupTransactionAndReturnsNoResult() {
        IndependentBoardOAuthTokenExchangeFacade facade =
                context.getBean(IndependentBoardOAuthTokenExchangeFacade.class);
        IndependentBoardOAuthTokenExchangeService service =
                context.getBean(IndependentBoardOAuthTokenExchangeService.class);
        RecordingManager manager =
                context.getBean("transactionManager", RecordingManager.class);
        RecordingManager decoy =
                context.getBean("decoyTransactionManager", RecordingManager.class);
        AtomicBoolean cleanupExecutedInsideTransaction = new AtomicBoolean();
        BoardOAuthProtocolException expected =
                BoardOAuthProtocolException.conflict(
                        IndependentBoardOAuthTokenExchangeService.CONFLICT);
        when(service.exchangeForCommit(null)).thenAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(TransactionDefinition.ISOLATION_REPEATABLE_READ,
                    TransactionSynchronizationManager
                            .getCurrentTransactionIsolationLevel());
            cleanupExecutedInsideTransaction.set(true);
            throw expected;
        });

        BoardOAuthProtocolException actual = assertThrows(
                BoardOAuthProtocolException.class,
                () -> facade.exchange(null));

        assertSame(expected, actual);
        assertTrue(cleanupExecutedInsideTransaction.get());
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
        IndependentBoardOAuthTokenExchangeFacade facade =
                context.getBean(IndependentBoardOAuthTokenExchangeFacade.class);
        IndependentBoardOAuthTokenExchangeService service =
                context.getBean(IndependentBoardOAuthTokenExchangeService.class);
        RecordingManager manager =
                context.getBean("transactionManager", RecordingManager.class);
        RecordingManager decoy =
                context.getBean("decoyTransactionManager", RecordingManager.class);
        when(service.exchangeForCommit(null)).thenAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(0, manager.commits);
            assertEquals(0, manager.rollbacks);
            return BoardOAuthTokenExchangeOutcome.invalidGrantAfterCommit(
                    IndependentBoardOAuthTokenExchangeService.CODE_INVALID);
        });

        BoardOAuthProtocolException failure = assertThrows(
                BoardOAuthProtocolException.class,
                () -> facade.exchange(null));

        assertEquals("invalid_grant", failure.oauthError());
        assertEquals(
                IndependentBoardOAuthTokenExchangeService.CODE_INVALID,
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
        IndependentBoardOAuthTokenExchangeService exchangeService() {
            return mock(IndependentBoardOAuthTokenExchangeService.class);
        }

        @Bean
        IndependentBoardOAuthTokenExchangeTransactionRunner exchangeRunner(
                IndependentBoardOAuthTokenExchangeService service) {
            return new IndependentBoardOAuthTokenExchangeTransactionRunner(service);
        }

        @Bean
        IndependentBoardOAuthTokenExchangeFacade exchangeFacade(
                IndependentBoardOAuthTokenExchangeTransactionRunner runner) {
            return new IndependentBoardOAuthTokenExchangeFacade(runner);
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
}
