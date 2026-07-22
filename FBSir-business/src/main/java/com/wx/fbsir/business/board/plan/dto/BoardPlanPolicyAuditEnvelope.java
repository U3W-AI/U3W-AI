package com.wx.fbsir.business.board.plan.dto;

import java.util.List;

public record BoardPlanPolicyAuditEnvelope(
        List<BoardPlanPolicyRevisionView> records,
        int limit,
        boolean truncated) {
}
