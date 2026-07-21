package com.wx.fbsir.business.board.oauth.service;

import com.wx.fbsir.business.board.oauth.dto.BoardOAuthRefreshResult;
import java.util.Objects;

/** Transaction-internal refresh outcome; replay rejection is raised after commit. */
final class BoardOAuthRefreshOutcome {
    private final BoardOAuthRefreshResult result;
    private final String invalidGrantReasonAfterCommit;

    private BoardOAuthRefreshOutcome(
            BoardOAuthRefreshResult result,
            String invalidGrantReasonAfterCommit) {
        this.result = result;
        this.invalidGrantReasonAfterCommit = invalidGrantReasonAfterCommit;
    }

    static BoardOAuthRefreshOutcome completed(BoardOAuthRefreshResult result) {
        return new BoardOAuthRefreshOutcome(
                Objects.requireNonNull(result, "result"), null);
    }

    static BoardOAuthRefreshOutcome invalidGrantAfterCommit(String reasonCode) {
        return new BoardOAuthRefreshOutcome(
                null, Objects.requireNonNull(reasonCode, "reasonCode"));
    }

    BoardOAuthRefreshResult requireCompletedResult() {
        if (result == null || invalidGrantReasonAfterCommit != null) {
            throw new IllegalStateException("refresh outcome is not completed");
        }
        return result;
    }

    boolean rejectsAfterCommit() {
        return invalidGrantReasonAfterCommit != null;
    }

    String invalidGrantReasonAfterCommit() {
        if (!rejectsAfterCommit() || result != null) {
            throw new IllegalStateException("refresh outcome is not a rejection");
        }
        return invalidGrantReasonAfterCommit;
    }

    @Override
    public String toString() {
        return "BoardOAuthRefreshOutcome[REDACTED]";
    }
}
