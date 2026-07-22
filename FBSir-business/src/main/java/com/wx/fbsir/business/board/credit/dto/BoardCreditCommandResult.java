package com.wx.fbsir.business.board.credit.dto;

import java.util.Date;

/** Minimal mutation response; internal IDs, digests, hashes and free notes are not exposed. */
public record BoardCreditCommandResult(
        String operationId,
        String operationType,
        Long userId,
        Long delta,
        Long balanceAfter,
        String reversalOfOperationId,
        Date createdAt) {
}
