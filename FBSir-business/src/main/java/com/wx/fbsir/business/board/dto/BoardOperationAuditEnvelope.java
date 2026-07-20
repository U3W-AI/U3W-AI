package com.wx.fbsir.business.board.dto;

import java.util.List;

/** Bounded global-admin audit result; one extra row is used only to signal truncation. */
public record BoardOperationAuditEnvelope(
        List<BoardOperationAuditView> records,
        int limit,
        boolean truncated) {
    public BoardOperationAuditEnvelope {
        records = List.copyOf(records);
    }
}
