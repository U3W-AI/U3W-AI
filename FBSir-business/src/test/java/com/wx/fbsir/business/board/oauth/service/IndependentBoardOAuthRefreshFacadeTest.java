package com.wx.fbsir.business.board.oauth.service;

import com.wx.fbsir.business.board.oauth.BoardOAuthProfile;
import com.wx.fbsir.business.board.oauth.BoardOAuthProtocolException;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthRefreshCommand;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthRefreshResult;
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

class IndependentBoardOAuthRefreshFacadeTest {
    private final IndependentBoardOAuthRefreshTransactionRunner runner =
            mock(IndependentBoardOAuthRefreshTransactionRunner.class);
    private final IndependentBoardOAuthRefreshFacade facade =
            new IndependentBoardOAuthRefreshFacade(runner);

    @AfterEach
    void clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void publishesCompletedTokensOnlyAfterRunnerReturns() {
        BoardOAuthRefreshResult expected = result();
        when(runner.refresh(null)).thenReturn(
                BoardOAuthRefreshOutcome.completed(expected));

        assertSame(expected, facade.refresh(null));
    }

    @Test
    void raisesReplayInvalidGrantOnlyAfterRunnerReturnsCommittedRejection() {
        when(runner.refresh(null)).thenReturn(
                BoardOAuthRefreshOutcome.invalidGrantAfterCommit(
                        IndependentBoardOAuthRefreshService.REPLAY_DETECTED));

        BoardOAuthProtocolException failure = assertThrows(
                BoardOAuthProtocolException.class,
                () -> facade.refresh(null));

        assertEquals("invalid_grant", failure.oauthError());
        assertEquals(400, failure.httpStatus());
        assertEquals(IndependentBoardOAuthRefreshService.REPLAY_DETECTED,
                failure.reasonCode());
    }

    @Test
    void nullRunnerOutcomeFailsClosed() {
        when(runner.refresh(null)).thenReturn(null);

        BoardOAuthProtocolException failure = assertThrows(
                BoardOAuthProtocolException.class,
                () -> facade.refresh(null));

        assertEquals("server_error", failure.oauthError());
        assertEquals(IndependentBoardOAuthRefreshService.OUTCOME_INVALID,
                failure.reasonCode());
    }

    @Test
    void rejectsInheritedTransactionAndSynchronizationWithoutCallingRunner() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertRootFailure(assertThrows(
                BoardOAuthProtocolException.class,
                () -> facade.refresh(null)));
        verifyNoInteractions(runner);

        TransactionSynchronizationManager.setActualTransactionActive(false);
        TransactionSynchronizationManager.initSynchronization();
        assertRootFailure(assertThrows(
                BoardOAuthProtocolException.class,
                () -> facade.refresh(null)));
        verifyNoInteractions(runner);
    }

    @Test
    void hiddenRunnerOwnsRequiredRepeatableReadNamedTransaction() throws Exception {
        assertTrue(Modifier.isPublic(
                IndependentBoardOAuthRefreshFacade.class.getModifiers()));
        assertTrue(Modifier.isFinal(
                IndependentBoardOAuthRefreshFacade.class.getModifiers()));
        assertFalse(Modifier.isPublic(
                IndependentBoardOAuthRefreshTransactionRunner.class.getModifiers()));
        assertFalse(Modifier.isPublic(
                IndependentBoardOAuthRefreshService.class.getModifiers()));

        Method method = IndependentBoardOAuthRefreshTransactionRunner.class
                .getDeclaredMethod("refresh", BoardOAuthRefreshCommand.class);
        Transactional transaction = method.getAnnotation(Transactional.class);
        assertEquals("transactionManager", transaction.transactionManager());
        assertEquals(Propagation.REQUIRED, transaction.propagation());
        assertEquals(Isolation.REPEATABLE_READ, transaction.isolation());
        assertTrue(Arrays.asList(transaction.rollbackFor()).contains(Exception.class));
        assertNull(IndependentBoardOAuthRefreshFacade.class
                .getDeclaredMethod("refresh", BoardOAuthRefreshCommand.class)
                .getAnnotation(Transactional.class));
        Method commitOnly = IndependentBoardOAuthRefreshService.class
                .getDeclaredMethod(
                        "refreshForCommit", BoardOAuthRefreshCommand.class);
        assertFalse(Modifier.isPublic(commitOnly.getModifiers()));
        assertNull(commitOnly.getAnnotation(Transactional.class));
        assertThrows(
                NoSuchMethodException.class,
                () -> IndependentBoardOAuthRefreshService.class
                        .getDeclaredMethod(
                                "refresh", BoardOAuthRefreshCommand.class));
    }

    @Test
    void secretDtosAndOutcomesAreAlwaysRedacted() {
        BoardOAuthRefreshCommand command = new BoardOAuthRefreshCommand(
                "s".repeat(43), "c".repeat(43),
                BoardOAuthProfile.RESOURCE, BoardOAuthProfile.CANONICAL_SCOPE);
        BoardOAuthRefreshResult result = result();
        BoardOAuthRefreshOutcome completed =
                BoardOAuthRefreshOutcome.completed(result);
        BoardOAuthRefreshOutcome rejected =
                BoardOAuthRefreshOutcome.invalidGrantAfterCommit(
                        IndependentBoardOAuthRefreshService.REPLAY_DETECTED);

        assertEquals("BoardOAuthRefreshCommand[REDACTED]", command.toString());
        assertEquals("BoardOAuthRefreshResult[REDACTED]", result.toString());
        assertEquals("BoardOAuthRefreshOutcome[REDACTED]", completed.toString());
        assertEquals("BoardOAuthRefreshOutcome[REDACTED]", rejected.toString());
        assertFalse(command.toString().contains(command.rawRefreshToken()));
        assertFalse(command.toString().contains(command.clientId()));
        assertFalse(command.toString().contains(command.resource()));
        assertFalse(result.toString().contains(result.rawAccessToken()));
        assertFalse(result.toString().contains(result.rawRefreshToken()));
    }

    private static BoardOAuthRefreshResult result() {
        return new BoardOAuthRefreshResult(
                "a".repeat(43), "r".repeat(43), "Bearer", 600,
                BoardOAuthProfile.CANONICAL_SCOPE,
                Instant.EPOCH.plusSeconds(600), Instant.EPOCH.plusSeconds(3_600),
                "i".repeat(43), "o".repeat(43));
    }

    private static void assertRootFailure(BoardOAuthProtocolException failure) {
        assertEquals("server_error", failure.oauthError());
        assertEquals(500, failure.httpStatus());
        assertEquals(
                IndependentBoardOAuthRefreshFacade.ROOT_TRANSACTION_REQUIRED,
                failure.reasonCode());
    }
}
