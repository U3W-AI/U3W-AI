package com.wx.fbsir.business.board.dto;

public record BoardConnectorBindingRevokeRequest(
        Long tenantId,
        Long memberId,
        Long userId,
        String bindingId,
        Long expectedVersion) {
}
