package com.wx.fbsir.business.board.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.util.Date;

/** Safe user-facing projection of a meeting reservation operation. */
public record BoardRecentMeetingView(
        String operationId,
        String status,
        String effectivePlanCode,
        Integer agendaCount,
        Integer seatCount,
        Integer remainingCount,
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate bucketDate,
        Date createdAt) {
}
