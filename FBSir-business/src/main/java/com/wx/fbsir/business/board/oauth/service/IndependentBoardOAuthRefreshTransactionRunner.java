package com.wx.fbsir.business.board.oauth.service;

import com.wx.fbsir.business.board.oauth.dto.BoardOAuthRefreshCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Hidden root transaction; raw replacement tokens become visible only after commit. */
@Service
class IndependentBoardOAuthRefreshTransactionRunner {
    static final String BOARD_TRANSACTION_MANAGER = "transactionManager";

    private final IndependentBoardOAuthRefreshService refreshService;

    IndependentBoardOAuthRefreshTransactionRunner(
            IndependentBoardOAuthRefreshService refreshService) {
        this.refreshService = refreshService;
    }

    @Transactional(
            transactionManager = BOARD_TRANSACTION_MANAGER,
            propagation = Propagation.REQUIRED,
            isolation = Isolation.REPEATABLE_READ,
            rollbackFor = Exception.class)
    public BoardOAuthRefreshOutcome refresh(BoardOAuthRefreshCommand command) {
        return refreshService.refreshForCommit(command);
    }
}
