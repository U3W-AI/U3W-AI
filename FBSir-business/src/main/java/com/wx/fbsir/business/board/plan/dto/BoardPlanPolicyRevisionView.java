package com.wx.fbsir.business.board.plan.dto;

import java.util.Date;

/** Safe administrator projection; internal keys and command digests remain server-side. */
public record BoardPlanPolicyRevisionView(
        String receiptId,
        String planCode,
        Long policyVersion,
        String previousReceiptId,
        String rollbackOfReceiptId,
        String action,
        String actorType,
        Long actorUserId,
        String planName,
        Integer dailyMeetingLimit,
        Integer agendaLimit,
        Integer seatLimit,
        Boolean secretaryEnabled,
        String previousPolicyDigest,
        String policyDigest,
        String evidenceLevel,
        Date createdAt) {
}
