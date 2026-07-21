package com.wx.fbsir.business.board.oauth.service;

import com.wx.fbsir.business.board.oauth.BoardOAuthProtocolException;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthTokenExchangeCommand;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthTokenExchangeResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Sole transport-neutral publication boundary for authorization-code exchange. */
@Service
public final class IndependentBoardOAuthTokenExchangeFacade {
    public static final String ROOT_TRANSACTION_REQUIRED =
            "OAUTH_TOKEN_EXCHANGE_ROOT_TRANSACTION_REQUIRED";

    private final IndependentBoardOAuthTokenExchangeTransactionRunner runner;

    IndependentBoardOAuthTokenExchangeFacade(
            IndependentBoardOAuthTokenExchangeTransactionRunner runner) {
        this.runner = runner;
    }

    public BoardOAuthTokenExchangeResult exchange(
            BoardOAuthTokenExchangeCommand command) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()) {
            throw BoardOAuthProtocolException.serverError(ROOT_TRANSACTION_REQUIRED);
        }
        BoardOAuthTokenExchangeOutcome outcome = runner.exchange(command);
        if (outcome == null) {
            throw BoardOAuthProtocolException.serverError(
                    IndependentBoardOAuthTokenExchangeService.OUTCOME_INVALID);
        }
        if (outcome.rejectsAfterCommit()) {
            // This exception is deliberately outside the proxied root transaction.
            throw BoardOAuthProtocolException.invalidGrant(
                    outcome.invalidGrantReasonAfterCommit());
        }
        return outcome.requireCompletedResult();
    }
}
