package com.wx.fbsir.business.board.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.util.Date;

/** Safe global-admin projection of a meeting reservation ledger row. */
public record BoardOperationAuditView(
        String operationId,
        Long tenantId,
        Long memberId,
        Long userId,
        String status,
        String effectivePlanCode,
        String policyReceiptId,
        Long policyVersion,
        String policyDigest,
        String policyPlanName,
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate bucketDate,
        Integer agendaCount,
        Integer seatCount,
        Integer remainingCount,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Shanghai")
        Date createdAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Shanghai")
        Date updatedAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Shanghai")
        Date completedAt) {
}
