package com.wx.fbsir.business.board.credit.dto;

import java.util.Date;

/** Traceable but note-free record exposed only on the dedicated administrator audit surface. */
public record BoardCreditAuditRecord(
        String operationId,
        String idempotencyKey,
        String requestDigest,
        String operationType,
        Long delta,
        String reasonCode,
        Long actorUserId,
        String reversalOfOperationId,
        Long balanceBefore,
        Long balanceAfter,
        Long sequenceNo,
        String previousEntryHash,
        String entryHash,
        Date createdAt) {
}
