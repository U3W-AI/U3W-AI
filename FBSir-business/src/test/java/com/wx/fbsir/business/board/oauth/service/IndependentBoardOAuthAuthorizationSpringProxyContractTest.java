package com.wx.fbsir.business.board.oauth.service;

import com.wx.fbsir.business.board.oauth.BoardOAuthProtocolException;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthAuthorizationApprovalCommand;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthAuthorizationDenialCommand;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthAuthorizationStartCommand;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthAuthorizationStartResult;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.SavepointManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Real Spring-proxy proof for the authorization root-transaction boundary. */
class IndependentBoardOAuthAuthorizationSpringProxyContractTest {
    private AnnotationConfigApplicationContext context;

    @BeforeEach
    void openContext() {
        context = new AnnotationConfigApplicationContext(ProxyTestConfiguration.class);
    }

    @AfterEach
    void closeContextAndClearThreadState() {
        if (context != null) {
            context.close();
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(null);
        TransactionSynchronizationManager.setCurrentTransactionName(null);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
    }

    @Test
    void facadeIsThePublicEntryAndHiddenRunnerHasTheNamedSpringTransactionInterceptor()
            throws Exception {
        IndependentBoardOAuthAuthorizationFacade facade =
                context.getBean(IndependentBoardOAuthAuthorizationFacade.class);
        IndependentBoardOAuthAuthorizationTransactionRunner runner =
                context.getBean(IndependentBoardOAuthAuthorizationTransactionRunner.class);

        assertTrue(Modifier.isPublic(
                IndependentBoardOAuthAuthorizationFacade.class.getModifiers()));
        assertTrue(Modifier.isFinal(
                IndependentBoardOAuthAuthorizationFacade.class.getModifiers()));
        assertFalse(Modifier.isPublic(
                IndependentBoardOAuthAuthorizationTransactionRunner.class.getModifiers()));
        assertFalse(Modifier.isPublic(
                IndependentBoardOAuthAuthorizationService.class.getModifiers()));
        assertFalse(AopUtils.isAopProxy(facade));
        assertTrue(AopUtils.isAopProxy(runner));
        assertTrue(AopUtils.isCglibProxy(runner));

        TransactionInterceptor interceptor = onlyTransactionInterceptor((Advised) runner);
        assertRunnerAttribute(
                interceptor, "start", BoardOAuthAuthorizationStartCommand.class);
        assertRunnerAttribute(
                interceptor, "approve", BoardOAuthAuthorizationApprovalCommand.class);
        assertRunnerAttribute(
                interceptor, "deny", BoardOAuthAuthorizationDenialCommand.class);
    }

    @Test
    void facadeCallsTheRunnerThroughTheNamedManagerAndReturnsOnlyAfterCommit()
            throws Exception {
        IndependentBoardOAuthAuthorizationFacade facade =
                context.getBean(IndependentBoardOAuthAuthorizationFacade.class);
        IndependentBoardOAuthAuthorizationTransactionRunner runner =
                context.getBean(IndependentBoardOAuthAuthorizationTransactionRunner.class);
        IndependentBoardOAuthAuthorizationTransactionRunner targetRunner =
                runnerTarget(runner);
        IndependentBoardOAuthAuthorizationService authorizationService =
                context.getBean(IndependentBoardOAuthAuthorizationService.class);
        RecordingTransactionManager namedManager =
                context.getBean("transactionManager", RecordingTransactionManager.class);
        RecordingTransactionManager decoyManager =
                context.getBean("decoyTransactionManager", RecordingTransactionManager.class);
        BoardOAuthAuthorizationStartResult expected =
                new BoardOAuthAuthorizationStartResult("h".repeat(43), Instant.EPOCH);
        when(authorizationService.start(null)).thenAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(
                    TransactionDefinition.ISOLATION_REPEATABLE_READ,
                    TransactionSynchronizationManager
                            .getCurrentTransactionIsolationLevel());
            return expected;
        });

        BoardOAuthAuthorizationStartResult actual = facade.start(null);

        assertSame(expected, actual);
        assertEquals(1, namedManager.begins);
        assertEquals(1, namedManager.commits);
        assertEquals(0, namedManager.rollbacks);
        assertEquals(TransactionDefinition.ISOLATION_REPEATABLE_READ,
                namedManager.lastIsolation);
        assertEquals(0, decoyManager.begins);
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
        verify(targetRunner).start(null);
        verify(authorizationService).start(null);
    }

    @Test
    void anOuterRequiredTransactionIsRejectedBeforeTheRunnerTargetIsCalled()
            throws Exception {
        RequiredCaller caller = context.getBean(RequiredCaller.class);
        IndependentBoardOAuthAuthorizationTransactionRunner targetRunner =
                runnerTarget(context.getBean(
                        IndependentBoardOAuthAuthorizationTransactionRunner.class));
        IndependentBoardOAuthAuthorizationService authorizationService =
                context.getBean(IndependentBoardOAuthAuthorizationService.class);
        RecordingTransactionManager manager =
                context.getBean("transactionManager", RecordingTransactionManager.class);

        BoardOAuthProtocolException failure = assertThrows(
                BoardOAuthProtocolException.class,
                caller::callFacadeInsideRequired);

        assertRootTransactionFailure(failure);
        verify(targetRunner, never()).start(any());
        verifyNoInteractions(authorizationService);
        assertEquals(1, manager.begins);
        assertEquals(0, manager.commits);
        assertEquals(1, manager.rollbacks);
    }

    @Test
    void anActualNestedTransactionIsRejectedBeforeTheRunnerTargetIsCalled()
            throws Exception {
        RequiredCaller caller = context.getBean(RequiredCaller.class);
        IndependentBoardOAuthAuthorizationTransactionRunner targetRunner =
                runnerTarget(context.getBean(
                        IndependentBoardOAuthAuthorizationTransactionRunner.class));
        IndependentBoardOAuthAuthorizationService authorizationService =
                context.getBean(IndependentBoardOAuthAuthorizationService.class);
        RecordingTransactionManager manager =
                context.getBean("transactionManager", RecordingTransactionManager.class);

        BoardOAuthProtocolException failure = assertThrows(
                BoardOAuthProtocolException.class,
                caller::callFacadeInsideActualNestedTransaction);

        assertRootTransactionFailure(failure);
        verify(targetRunner, never()).start(any());
        verifyNoInteractions(authorizationService);
        assertEquals(1, manager.begins);
        assertEquals(1, manager.savepointsCreated);
        assertEquals(1, manager.savepointsRolledBack);
        assertEquals(0, manager.commits);
        assertEquals(1, manager.rollbacks);
    }

    private static TransactionInterceptor onlyTransactionInterceptor(Advised advised) {
        List<TransactionInterceptor> interceptors = Arrays.stream(advised.getAdvisors())
                .map(Advisor::getAdvice)
                .filter(TransactionInterceptor.class::isInstance)
                .map(TransactionInterceptor.class::cast)
                .toList();
        assertEquals(1, interceptors.size());
        return interceptors.get(0);
    }

    private static void assertRunnerAttribute(
            TransactionInterceptor interceptor,
            String methodName,
            Class<?> commandType) throws Exception {
        Method method = IndependentBoardOAuthAuthorizationTransactionRunner.class
                .getDeclaredMethod(methodName, commandType);
        assertNotNull(interceptor.getTransactionAttributeSource());
        TransactionAttribute attribute = interceptor.getTransactionAttributeSource()
                .getTransactionAttribute(
                        method,
                        IndependentBoardOAuthAuthorizationTransactionRunner.class);
        assertNotNull(attribute);
        assertEquals("transactionManager", attribute.getQualifier());
        assertEquals(TransactionDefinition.PROPAGATION_REQUIRED,
                attribute.getPropagationBehavior());
        assertEquals(TransactionDefinition.ISOLATION_REPEATABLE_READ,
                attribute.getIsolationLevel());
        assertTrue(attribute.rollbackOn(new Exception("checked failure")));
    }

    private static IndependentBoardOAuthAuthorizationTransactionRunner runnerTarget(
            IndependentBoardOAuthAuthorizationTransactionRunner runner) throws Exception {
        return (IndependentBoardOAuthAuthorizationTransactionRunner) ((Advised) runner)
                .getTargetSource()
                .getTarget();
    }

    private static void assertRootTransactionFailure(
            BoardOAuthProtocolException failure) {
        assertEquals("server_error", failure.oauthError());
        assertEquals(500, failure.httpStatus());
        assertEquals(
                "OAUTH_AUTHORIZATION_ROOT_TRANSACTION_REQUIRED",
                failure.reasonCode());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class ProxyTestConfiguration {
        @Bean("transactionManager")
        RecordingTransactionManager transactionManager() {
            return new RecordingTransactionManager();
        }

        @Bean("decoyTransactionManager")
        RecordingTransactionManager decoyTransactionManager() {
            return new RecordingTransactionManager();
        }

        @Bean
        IndependentBoardOAuthAuthorizationService authorizationService() {
            return mock(IndependentBoardOAuthAuthorizationService.class);
        }

        @Bean
        IndependentBoardOAuthAuthorizationTransactionRunner authorizationRunner(
                IndependentBoardOAuthAuthorizationService authorizationService) {
            return spy(new IndependentBoardOAuthAuthorizationTransactionRunner(
                    authorizationService));
        }

        @Bean
        IndependentBoardOAuthAuthorizationFacade authorizationFacade(
                IndependentBoardOAuthAuthorizationTransactionRunner runner) {
            return new IndependentBoardOAuthAuthorizationFacade(runner);
        }

        @Bean
        NestedCaller nestedCaller(IndependentBoardOAuthAuthorizationFacade facade) {
            return new NestedCaller(facade);
        }

        @Bean
        RequiredCaller requiredCaller(
                IndependentBoardOAuthAuthorizationFacade facade,
                NestedCaller nestedCaller) {
            return new RequiredCaller(facade, nestedCaller);
        }
    }

    static class RequiredCaller {
        private final IndependentBoardOAuthAuthorizationFacade facade;
        private final NestedCaller nestedCaller;

        RequiredCaller(
                IndependentBoardOAuthAuthorizationFacade facade,
                NestedCaller nestedCaller) {
            this.facade = facade;
            this.nestedCaller = nestedCaller;
        }

        @Transactional(
                transactionManager = "transactionManager",
                propagation = Propagation.REQUIRED,
                rollbackFor = Exception.class)
        public void callFacadeInsideRequired() {
            facade.start(null);
        }

        @Transactional(
                transactionManager = "transactionManager",
                propagation = Propagation.REQUIRED,
                rollbackFor = Exception.class)
        public void callFacadeInsideActualNestedTransaction() {
            nestedCaller.callFacadeInsideNested();
        }
    }

    static class NestedCaller {
        private final IndependentBoardOAuthAuthorizationFacade facade;

        NestedCaller(IndependentBoardOAuthAuthorizationFacade facade) {
            this.facade = facade;
        }

        @Transactional(
                transactionManager = "transactionManager",
                propagation = Propagation.NESTED,
                rollbackFor = Exception.class)
        public void callFacadeInsideNested() {
            facade.start(null);
        }
    }

    static final class RecordingTransactionManager
            extends AbstractPlatformTransactionManager {
        private static final long serialVersionUID = 1L;

        private final ThreadLocal<RecordingTransaction> current = new ThreadLocal<>();
        private int begins;
        private int commits;
        private int rollbacks;
        private int savepointsCreated;
        private int savepointsRolledBack;
        private int lastIsolation = TransactionDefinition.ISOLATION_DEFAULT;

        RecordingTransactionManager() {
            setNestedTransactionAllowed(true);
        }

        @Override
        protected Object doGetTransaction() {
            RecordingTransaction existing = current.get();
            return existing == null ? new RecordingTransaction(this) : existing;
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return ((RecordingTransaction) transaction).active;
        }

        @Override
        protected void doBegin(
                Object transaction,
                TransactionDefinition definition) {
            RecordingTransaction recording = (RecordingTransaction) transaction;
            recording.active = true;
            current.set(recording);
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
        protected void doSetRollbackOnly(DefaultTransactionStatus status) {
            ((RecordingTransaction) status.getTransaction()).rollbackOnly = true;
        }

        @Override
        protected void doCleanupAfterCompletion(Object transaction) {
            RecordingTransaction recording = (RecordingTransaction) transaction;
            recording.active = false;
            recording.rollbackOnly = false;
            current.remove();
        }
    }

    static final class RecordingTransaction implements SavepointManager {
        private final RecordingTransactionManager owner;
        private boolean active;
        private boolean rollbackOnly;
        private int nextSavepoint;

        RecordingTransaction(RecordingTransactionManager owner) {
            this.owner = owner;
        }

        @Override
        public Object createSavepoint() {
            owner.savepointsCreated++;
            return ++nextSavepoint;
        }

        @Override
        public void rollbackToSavepoint(Object savepoint) {
            owner.savepointsRolledBack++;
        }

        @Override
        public void releaseSavepoint(Object savepoint) {
            // The count is not part of the contract under test.
        }
    }
}
