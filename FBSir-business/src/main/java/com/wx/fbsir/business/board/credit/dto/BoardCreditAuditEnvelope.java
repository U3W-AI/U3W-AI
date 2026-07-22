package com.wx.fbsir.business.board.credit.dto;

import java.util.Date;
import java.util.List;

/** Bounded exact-scope current read for one initialized shadow account. */
public record BoardCreditAuditEnvelope(
        String accountId,
        Long userId,
        String accountScope,
        String currencyCode,
        Long openingBalance,
        Long balance,
        Long version,
        String lastEntryHash,
        Date updatedAt,
        List<BoardCreditAuditRecord> records,
        int limit,
        boolean truncated) {
}
