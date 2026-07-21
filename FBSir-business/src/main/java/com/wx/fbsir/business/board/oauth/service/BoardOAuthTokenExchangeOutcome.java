package com.wx.fbsir.business.board.oauth.service;

import com.wx.fbsir.business.board.oauth.dto.BoardOAuthTokenExchangeResult;
import java.util.Objects;

/**
 * Transaction-internal exchange outcome.
 *
 * <p>A replay rejection is deliberately data, rather than an exception, until
 * the hidden root transaction has committed its security state and receipts.</p>
 */
final class BoardOAuthTokenExchangeOutcome {
    private final BoardOAuthTokenExchangeResult result;
    private final String invalidGrantReasonAfterCommit;

    private BoardOAuthTokenExchangeOutcome(
            BoardOAuthTokenExchangeResult result,
            String invalidGrantReasonAfterCommit) {
        this.result = result;
        this.invalidGrantReasonAfterCommit = invalidGrantReasonAfterCommit;
    }

    static BoardOAuthTokenExchangeOutcome completed(
            BoardOAuthTokenExchangeResult result) {
        return new BoardOAuthTokenExchangeOutcome(
                Objects.requireNonNull(result, "result"), null);
    }

    static BoardOAuthTokenExchangeOutcome invalidGrantAfterCommit(
            String reasonCode) {
        return new BoardOAuthTokenExchangeOutcome(
                null, Objects.requireNonNull(reasonCode, "reasonCode"));
    }

    BoardOAuthTokenExchangeResult requireCompletedResult() {
        if (result == null || invalidGrantReasonAfterCommit != null) {
            throw new IllegalStateException("exchange outcome is not completed");
        }
        return result;
    }

    boolean rejectsAfterCommit() {
        return invalidGrantReasonAfterCommit != null;
    }

    String invalidGrantReasonAfterCommit() {
        if (!rejectsAfterCommit() || result != null) {
            throw new IllegalStateException("exchange outcome is not a rejection");
        }
        return invalidGrantReasonAfterCommit;
    }

    @Override
    public String toString() {
        return "BoardOAuthTokenExchangeOutcome[REDACTED]";
    }
}
