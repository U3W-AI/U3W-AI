package com.wx.fbsir.business.board.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Non-leaking result of an authenticated meeting reservation lookup. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record BoardMeetingLookupView(
        boolean found,
        BoardRecentMeetingView meeting) {
}
