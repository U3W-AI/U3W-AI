package com.wx.fbsir.business.board.dto;

public record BoardEntitlementSnapshot(
        Long tenantId,
        Long memberId,
        Long userId,
        String grantedPlanCode,
        String effectivePlanCode,
        String activationState,
        Integer dailyMeetingLimit,
        Integer agendaLimit,
        Integer seatLimit,
        boolean secretaryEnabled,
        boolean connectorRequired,
        boolean connectorVerified,
        Integer usedCount,
        Integer reservedCount,
        Integer remainingCount) {
}
