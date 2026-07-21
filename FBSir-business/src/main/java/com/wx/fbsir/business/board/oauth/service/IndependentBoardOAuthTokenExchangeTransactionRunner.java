package com.wx.fbsir.business.board.oauth.service;

import com.wx.fbsir.business.board.oauth.dto.BoardOAuthTokenExchangeCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Hidden root transaction; raw tokens become observable only after proxy commit. */
@Service
class IndependentBoardOAuthTokenExchangeTransactionRunner {
    static final String BOARD_TRANSACTION_MANAGER = "transactionManager";

    private final IndependentBoardOAuthTokenExchangeService exchangeService;

    IndependentBoardOAuthTokenExchangeTransactionRunner(
            IndependentBoardOAuthTokenExchangeService exchangeService) {
        this.exchangeService = exchangeService;
    }

    @Transactional(
            transactionManager = BOARD_TRANSACTION_MANAGER,
            propagation = Propagation.REQUIRED,
            isolation = Isolation.REPEATABLE_READ,
            rollbackFor = Exception.class)
    public BoardOAuthTokenExchangeOutcome exchange(
            BoardOAuthTokenExchangeCommand command) {
        return exchangeService.exchangeForCommit(command);
    }
}
