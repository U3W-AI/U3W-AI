package com.wx.fbsir.business.board.oauth.service;

import com.wx.fbsir.business.board.oauth.BoardOAuthProtocolException;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthTokenExchangeCommand;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthTokenExchangeResult;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IndependentBoardOAuthTokenExchangeFacadeTest {
    private final IndependentBoardOAuthTokenExchangeTransactionRunner runner =
            mock(IndependentBoardOAuthTokenExchangeTransactionRunner.class);
    private final IndependentBoardOAuthTokenExchangeFacade facade =
            new IndependentBoardOAuthTokenExchangeFacade(runner);

    @AfterEach
    void clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void delegatesOnlyWithoutAnInheritedTransaction() {
        BoardOAuthTokenExchangeResult expected = result();
        when(runner.exchange(null)).thenReturn(
                BoardOAuthTokenExchangeOutcome.completed(expected));

        assertSame(expected, facade.exchange(null));
    }

    @Test
    void raisesReplayInvalidGrantOnlyAfterRunnerReturnsItsCommittedOutcome() {
        when(runner.exchange(null)).thenReturn(
                BoardOAuthTokenExchangeOutcome.invalidGrantAfterCommit(
                        IndependentBoardOAuthTokenExchangeService.CODE_INVALID));

        BoardOAuthProtocolException failure = assertThrows(
                BoardOAuthProtocolException.class,
                () -> facade.exchange(null));

        assertEquals("invalid_grant", failure.oauthError());
        assertEquals(400, failure.httpStatus());
        assertEquals(
                IndependentBoardOAuthTokenExchangeService.CODE_INVALID,
                failure.reasonCode());
    }

    @Test
    void rejectsInheritedTransactionAndSynchronization() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertRootFailure(assertThrows(
                BoardOAuthProtocolException.class,
                () -> facade.exchange(null)));
        verifyNoInteractions(runner);

        TransactionSynchronizationManager.setActualTransactionActive(false);
        TransactionSynchronizationManager.initSynchronization();
        assertRootFailure(assertThrows(
                BoardOAuthProtocolException.class,
                () -> facade.exchange(null)));
        verifyNoInteractions(runner);
    }

    @Test
    void hiddenRunnerOwnsRequiredRepeatableReadNamedTransaction() throws Exception {
        assertTrue(Modifier.isPublic(
                IndependentBoardOAuthTokenExchangeFacade.class.getModifiers()));
        assertTrue(Modifier.isFinal(
                IndependentBoardOAuthTokenExchangeFacade.class.getModifiers()));
        assertFalse(Modifier.isPublic(
                IndependentBoardOAuthTokenExchangeTransactionRunner.class.getModifiers()));
        assertFalse(Modifier.isPublic(
                IndependentBoardOAuthTokenExchangeService.class.getModifiers()));

        Method method = IndependentBoardOAuthTokenExchangeTransactionRunner.class
                .getDeclaredMethod("exchange", BoardOAuthTokenExchangeCommand.class);
        Transactional transaction = method.getAnnotation(Transactional.class);
        assertEquals("transactionManager", transaction.transactionManager());
        assertEquals(Propagation.REQUIRED, transaction.propagation());
        assertEquals(Isolation.REPEATABLE_READ, transaction.isolation());
        assertTrue(Arrays.asList(transaction.rollbackFor()).contains(Exception.class));
        assertNull(IndependentBoardOAuthTokenExchangeFacade.class
                .getDeclaredMethod("exchange", BoardOAuthTokenExchangeCommand.class)
                .getAnnotation(Transactional.class));
        Method commitOnly = IndependentBoardOAuthTokenExchangeService.class
                .getDeclaredMethod(
                        "exchangeForCommit", BoardOAuthTokenExchangeCommand.class);
        assertFalse(Modifier.isPublic(commitOnly.getModifiers()));
        assertNull(commitOnly.getAnnotation(Transactional.class));
        assertThrows(
                NoSuchMethodException.class,
                () -> IndependentBoardOAuthTokenExchangeService.class
                        .getDeclaredMethod(
                                "exchange", BoardOAuthTokenExchangeCommand.class));
    }

    @Test
    void secretDtosAreAlwaysRedacted() {
        BoardOAuthTokenExchangeCommand command = new BoardOAuthTokenExchangeCommand(
                "c".repeat(43), "v".repeat(43), "b".repeat(43),
                "http://127.0.0.1:54321/oauth/callback",
                "https://api.u3w.com/mcp/independent-board");
        BoardOAuthTokenExchangeResult result = result();

        assertEquals("BoardOAuthTokenExchangeCommand[REDACTED]", command.toString());
        assertEquals("BoardOAuthTokenExchangeResult[REDACTED]", result.toString());
        assertFalse(command.toString().contains(command.rawAuthorizationCode()));
        assertFalse(command.toString().contains(command.pkceVerifier()));
        assertFalse(command.toString().contains(command.resource()));
        assertFalse(result.toString().contains(result.rawAccessToken()));
        assertFalse(result.toString().contains(result.rawRefreshToken()));
    }

    private static BoardOAuthTokenExchangeResult result() {
        return new BoardOAuthTokenExchangeResult(
                "a".repeat(43), "r".repeat(43), "Bearer", 600,
                "scope", Instant.EPOCH, Instant.EPOCH,
                "i".repeat(43), "o".repeat(43));
    }

    private static void assertRootFailure(BoardOAuthProtocolException failure) {
        assertEquals("server_error", failure.oauthError());
        assertEquals(500, failure.httpStatus());
        assertEquals(
                IndependentBoardOAuthTokenExchangeFacade.ROOT_TRANSACTION_REQUIRED,
                failure.reasonCode());
    }
}
