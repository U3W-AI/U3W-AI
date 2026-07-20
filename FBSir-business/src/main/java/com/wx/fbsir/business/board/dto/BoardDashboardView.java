package com.wx.fbsir.business.board.dto;

import java.util.List;

/** Current independent-board control-plane dashboard for one selected enterprise context. */
public record BoardDashboardView(
        BoardEnterpriseContextView context,
        BoardEntitlementSnapshot entitlement,
        List<BoardRecentMeetingView> recentMeetings,
        String connectorState,
        String webhookState,
        String watchState) {
}
