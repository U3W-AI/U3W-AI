package com.wx.fbsir.business.board.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record BoardMeetingReservationRequest(
        @NotNull @Min(1) Long tenantId,
        @NotBlank @Pattern(regexp = BoardMeetingReservationRequest.OPERATION_ID_PATTERN)
        String operationId,
        @NotNull @Min(1) @Max(MAX_AGENDA_COUNT) Integer agendaCount,
        @NotNull @Min(1) @Max(INITIAL_SAFETY_MAX_SEAT_COUNT) Integer seatCount) {
    /** Abuse-prevention ceiling for the initial API, not the VIP product seat limit. */
    public static final int INITIAL_SAFETY_MAX_SEAT_COUNT = 100;

    /** Shared request and committed-policy ceiling for agenda validation. */
    public static final int MAX_AGENDA_COUNT = 30;

    /** Shared route/body contract: 8-128 safe idempotency-key characters. */
    public static final String OPERATION_ID_PATTERN =
            "[A-Za-z0-9][A-Za-z0-9._:-]{7,127}";
}
