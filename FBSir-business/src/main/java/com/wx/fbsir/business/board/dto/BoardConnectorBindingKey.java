package com.wx.fbsir.business.board.dto;

public record BoardConnectorBindingKey(Long tenantId, Long memberId, Long userId, String productCode) {
}
