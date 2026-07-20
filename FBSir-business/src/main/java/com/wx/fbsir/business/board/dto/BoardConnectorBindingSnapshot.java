package com.wx.fbsir.business.board.dto;

import java.util.Date;

public record BoardConnectorBindingSnapshot(
        String bindingId,
        Long tenantId,
        Long memberId,
        Long userId,
        String productCode,
        String status,
        Date verifiedAt,
        Date validUntil,
        Long version) {

    public BoardConnectorBindingSnapshot {
        verifiedAt = copy(verifiedAt);
        validUntil = copy(validUntil);
    }

    @Override
    public Date verifiedAt() {
        return copy(verifiedAt);
    }

    @Override
    public Date validUntil() {
        return copy(validUntil);
    }

    private static Date copy(Date value) {
        return value == null ? null : new Date(value.getTime());
    }
}
