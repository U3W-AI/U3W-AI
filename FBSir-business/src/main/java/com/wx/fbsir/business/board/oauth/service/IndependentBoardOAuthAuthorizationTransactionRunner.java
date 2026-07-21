package com.wx.fbsir.business.board.oauth.service;

import com.wx.fbsir.business.board.oauth.dto.BoardOAuthAuthorizationApprovalCommand;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthAuthorizationApprovedResult;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthAuthorizationDenialCommand;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthAuthorizationDeniedResult;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthAuthorizationStartCommand;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthAuthorizationStartResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Hidden root-transaction boundary; raw one-time values leave only after commit. */
@Service
class IndependentBoardOAuthAuthorizationTransactionRunner {
    static final String BOARD_TRANSACTION_MANAGER = "transactionManager";

    private final IndependentBoardOAuthAuthorizationService authorizationService;

    IndependentBoardOAuthAuthorizationTransactionRunner(
            IndependentBoardOAuthAuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @Transactional(
            transactionManager = BOARD_TRANSACTION_MANAGER,
            propagation = Propagation.REQUIRED,
            isolation = Isolation.REPEATABLE_READ,
            rollbackFor = Exception.class)
    public BoardOAuthAuthorizationStartResult start(
            BoardOAuthAuthorizationStartCommand command) {
        return authorizationService.start(command);
    }

    @Transactional(
            transactionManager = BOARD_TRANSACTION_MANAGER,
            propagation = Propagation.REQUIRED,
            isolation = Isolation.REPEATABLE_READ,
            rollbackFor = Exception.class)
    public BoardOAuthAuthorizationApprovedResult approve(
            BoardOAuthAuthorizationApprovalCommand command) {
        return authorizationService.approve(command);
    }

    @Transactional(
            transactionManager = BOARD_TRANSACTION_MANAGER,
            propagation = Propagation.REQUIRED,
            isolation = Isolation.REPEATABLE_READ,
            rollbackFor = Exception.class)
    public BoardOAuthAuthorizationDeniedResult deny(
            BoardOAuthAuthorizationDenialCommand command) {
        return authorizationService.deny(command);
    }
}
