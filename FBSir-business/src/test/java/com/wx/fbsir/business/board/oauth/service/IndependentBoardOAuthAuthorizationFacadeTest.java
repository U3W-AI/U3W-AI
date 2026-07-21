package com.wx.fbsir.business.board.oauth.service;

import com.wx.fbsir.business.board.oauth.BoardOAuthProtocolException;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthAuthorizationApprovalCommand;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthAuthorizationApprovedResult;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthAuthorizationDenialCommand;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthAuthorizationDeniedResult;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthAuthorizationStartCommand;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthAuthorizationStartResult;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IndependentBoardOAuthAuthorizationFacadeTest {
    private final IndependentBoardOAuthAuthorizationTransactionRunner runner =
            mock(IndependentBoardOAuthAuthorizationTransactionRunner.class);
    private final IndependentBoardOAuthAuthorizationFacade facade =
            new IndependentBoardOAuthAuthorizationFacade(runner);

    @AfterEach
    void clearTransactionThreadState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void delegatesOnlyFromAContextWithoutAnInheritedTransaction() {
        BoardOAuthAuthorizationStartResult started =
                new BoardOAuthAuthorizationStartResult("h".repeat(43), Instant.EPOCH);
        BoardOAuthAuthorizationApprovedResult approved =
                new BoardOAuthAuthorizationApprovedResult(
                        "http://127.0.0.1/callback",
                        "c".repeat(43),
                        "state",
                        "https://api2.u3w.com/fbs-mcp",
                        Instant.EPOCH);
        BoardOAuthAuthorizationDeniedResult denied =
                new BoardOAuthAuthorizationDeniedResult(
                        "http://127.0.0.1/callback",
                        "state",
                        "https://api2.u3w.com/fbs-mcp",
                        "access_denied");
        when(runner.start(null)).thenReturn(started);
        when(runner.approve(null)).thenReturn(approved);
        when(runner.deny(null)).thenReturn(denied);

        assertSame(started, facade.start(null));
        assertSame(approved, facade.approve(null));
        assertSame(denied, facade.deny(null));
        verify(runner).start(null);
        verify(runner).approve(null);
        verify(runner).deny(null);
    }

    @Test
    void rejectsAnAlreadyActiveTransactionBeforePublishingAnyRawValue() {
        TransactionSynchronizationManager.setActualTransactionActive(true);

        BoardOAuthProtocolException failure = assertThrows(
                BoardOAuthProtocolException.class,
                () -> facade.start(null));

        assertRootTransactionFailure(failure);
        verifyNoInteractions(runner);
    }

    @Test
    void rejectsAnAlreadyActiveSynchronizationBeforePublishingAnyRawValue() {
        TransactionSynchronizationManager.initSynchronization();

        BoardOAuthProtocolException failure = assertThrows(
                BoardOAuthProtocolException.class,
                () -> facade.approve(null));

        assertRootTransactionFailure(failure);
        verifyNoInteractions(runner);
    }

    @Test
    void hiddenRunnerOwnsExactlyOneNamedRepeatableReadRootTransaction()
            throws Exception {
        assertFalse(Modifier.isPublic(
                IndependentBoardOAuthAuthorizationTransactionRunner.class.getModifiers()));
        assertFalse(Modifier.isFinal(
                IndependentBoardOAuthAuthorizationTransactionRunner.class.getModifiers()));
        assertTrue(Modifier.isPublic(
                IndependentBoardOAuthAuthorizationFacade.class.getModifiers()));
        assertTrue(Modifier.isFinal(
                IndependentBoardOAuthAuthorizationFacade.class.getModifiers()));

        assertRunnerTransaction("start", BoardOAuthAuthorizationStartCommand.class);
        assertRunnerTransaction("approve", BoardOAuthAuthorizationApprovalCommand.class);
        assertRunnerTransaction("deny", BoardOAuthAuthorizationDenialCommand.class);
        assertNull(IndependentBoardOAuthAuthorizationFacade.class
                .getDeclaredMethod("start", BoardOAuthAuthorizationStartCommand.class)
                .getAnnotation(Transactional.class));
    }

    private static void assertRunnerTransaction(
            String methodName,
            Class<?> commandType) throws Exception {
        Method method = IndependentBoardOAuthAuthorizationTransactionRunner.class
                .getDeclaredMethod(methodName, commandType);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertEquals(
                IndependentBoardOAuthAuthorizationTransactionRunner.BOARD_TRANSACTION_MANAGER,
                transactional.transactionManager());
        assertEquals(Propagation.REQUIRED, transactional.propagation());
        assertEquals(Isolation.REPEATABLE_READ, transactional.isolation());
        assertTrue(Arrays.asList(transactional.rollbackFor()).contains(Exception.class));
    }

    private static void assertRootTransactionFailure(
            BoardOAuthProtocolException failure) {
        assertEquals("server_error", failure.oauthError());
        assertEquals(500, failure.httpStatus());
        assertEquals(
                IndependentBoardOAuthAuthorizationFacade.ROOT_TRANSACTION_REQUIRED,
                failure.reasonCode());
    }
}
