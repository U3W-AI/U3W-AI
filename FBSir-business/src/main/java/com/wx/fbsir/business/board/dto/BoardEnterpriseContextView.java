package com.wx.fbsir.business.board.dto;

/** Active enterprise context visible to the current authenticated user. */
public record BoardEnterpriseContextView(
        Long tenantId,
        Long memberId,
        String tenantName,
        String memberRole) {
}
