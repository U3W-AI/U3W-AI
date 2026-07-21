package com.wx.fbsir.business.board.oauth.service;

import com.wx.fbsir.business.board.oauth.BoardOAuthProtocolException;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthRefreshCommand;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthRefreshResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Sole transport-neutral publication boundary for refresh-token rotation. */
@Service
public final class IndependentBoardOAuthRefreshFacade {
    public static final String ROOT_TRANSACTION_REQUIRED =
            "OAUTH_REFRESH_ROOT_TRANSACTION_REQUIRED";

    private final IndependentBoardOAuthRefreshTransactionRunner runner;

    IndependentBoardOAuthRefreshFacade(
            IndependentBoardOAuthRefreshTransactionRunner runner) {
        this.runner = runner;
    }

    public BoardOAuthRefreshResult refresh(BoardOAuthRefreshCommand command) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()) {
            throw BoardOAuthProtocolException.serverError(ROOT_TRANSACTION_REQUIRED);
        }
        BoardOAuthRefreshOutcome outcome = runner.refresh(command);
        if (outcome == null) {
            throw BoardOAuthProtocolException.serverError(
                    IndependentBoardOAuthRefreshService.OUTCOME_INVALID);
        }
        if (outcome.rejectsAfterCommit()) {
            // The transaction has already committed replay containment here.
            throw BoardOAuthProtocolException.invalidGrant(
                    outcome.invalidGrantReasonAfterCommit());
        }
        return outcome.requireCompletedResult();
    }
}
