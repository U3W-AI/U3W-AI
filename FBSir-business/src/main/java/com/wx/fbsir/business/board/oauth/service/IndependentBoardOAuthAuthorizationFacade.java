package com.wx.fbsir.business.board.oauth.service;

import com.wx.fbsir.business.board.oauth.BoardOAuthProtocolException;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthAuthorizationApprovalCommand;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthAuthorizationApprovedResult;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthAuthorizationDenialCommand;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthAuthorizationDeniedResult;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthAuthorizationStartCommand;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthAuthorizationStartResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Only externally injectable authorization entrypoint.
 *
 * <p>It refuses transaction inheritance so a raw request handle or authorization
 * code can never become visible before its database transaction commits.</p>
 */
@Service
public final class IndependentBoardOAuthAuthorizationFacade {
    public static final String ROOT_TRANSACTION_REQUIRED =
            "OAUTH_AUTHORIZATION_ROOT_TRANSACTION_REQUIRED";

    private final IndependentBoardOAuthAuthorizationTransactionRunner runner;

    IndependentBoardOAuthAuthorizationFacade(
            IndependentBoardOAuthAuthorizationTransactionRunner runner) {
        this.runner = runner;
    }

    public BoardOAuthAuthorizationStartResult start(
            BoardOAuthAuthorizationStartCommand command) {
        requireNoInheritedTransaction();
        return runner.start(command);
    }

    public BoardOAuthAuthorizationApprovedResult approve(
            BoardOAuthAuthorizationApprovalCommand command) {
        requireNoInheritedTransaction();
        return runner.approve(command);
    }

    public BoardOAuthAuthorizationDeniedResult deny(
            BoardOAuthAuthorizationDenialCommand command) {
        requireNoInheritedTransaction();
        return runner.deny(command);
    }

    private void requireNoInheritedTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()) {
            throw BoardOAuthProtocolException.serverError(ROOT_TRANSACTION_REQUIRED);
        }
    }
}
