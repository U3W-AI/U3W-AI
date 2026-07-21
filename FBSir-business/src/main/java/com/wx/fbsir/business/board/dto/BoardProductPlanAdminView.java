package com.wx.fbsir.business.board.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

/** Safe, read-only global-admin projection of the current plan policy. */
public record BoardProductPlanAdminView(
        String productCode,
        String planCode,
        String planName,
        boolean vip,
        boolean connectorRequired,
        int dailyMeetingLimit,
        int agendaLimit,
        Integer seatLimit,
        boolean secretaryEnabled,
        String status,
        long version,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Shanghai")
        Date updatedAt) {
    public BoardProductPlanAdminView {
        updatedAt = updatedAt == null ? null : new Date(updatedAt.getTime());
    }

    @Override
    public Date updatedAt() {
        return updatedAt == null ? null : new Date(updatedAt.getTime());
    }
}
