package com.wx.fbsir.business.board.dto;

import java.util.List;

/** Bounded global-admin current-read response. */
public record BoardEntitlementReceiptAuditEnvelope(
        List<BoardEntitlementReceiptView> records,
        int limit,
        boolean truncated) {
}
