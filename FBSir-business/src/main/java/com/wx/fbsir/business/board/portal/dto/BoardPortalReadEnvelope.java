package com.wx.fbsir.business.board.portal.dto;

import java.util.List;
import java.util.regex.Pattern;

public record BoardPortalReadEnvelope<T>(
        List<T> records,
        int limit,
        boolean truncated,
        String nextCursor) {

    private static final Pattern CURSOR_PATTERN =
            Pattern.compile("[A-Za-z0-9._~-]{16,512}");

    public BoardPortalReadEnvelope {
        if (records == null) {
            throw new IllegalArgumentException("records are required");
        }
        records = List.copyOf(records);
        if (limit < 1 || limit > 500 || records.size() > limit) {
            throw new IllegalArgumentException("invalid portal read limit");
        }
        if (truncated != (nextCursor != null)) {
            throw new IllegalArgumentException("cursor must be paired with truncation");
        }
        if (nextCursor != null && !CURSOR_PATTERN.matcher(nextCursor).matches()) {
            throw new IllegalArgumentException("invalid portal read cursor");
        }
    }
}
