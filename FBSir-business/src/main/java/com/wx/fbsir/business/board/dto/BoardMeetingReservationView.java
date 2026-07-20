package com.wx.fbsir.business.board.dto;

public record BoardMeetingReservationView(
        String operationId,
        String status,
        String effectivePlanCode,
        Integer agendaCount,
        Integer seatCount,
        Integer remainingCount) {
}
