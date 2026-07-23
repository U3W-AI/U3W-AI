package com.wx.fbsir.business.board.attribution.service;

import com.wx.fbsir.business.board.attribution.config.IndependentBoardAttributionProperties;
import com.wx.fbsir.business.board.attribution.domain.BoardAttributionSummaryRow;
import com.wx.fbsir.business.board.attribution.mapper.IndependentBoardAttributionV1Mapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@ConditionalOnProperty(
        prefix = "fbsir.independent-board.attribution",
        name = "observation-admin-read-enabled",
        havingValue = "true",
        matchIfMissing = false)
public class IndependentBoardAttributionAdminReadService {
    private static final Duration MAX_WINDOW = Duration.ofHours(24);
    private static final Set<String> MODES = Set.of(
            "ALL", "NATURAL", "PROBE", "DIAGNOSTIC", "SYNTHETIC", "UNKNOWN");
    private final IndependentBoardAttributionV1Mapper mapper;
    private final IndependentBoardAttributionProperties properties;

    public IndependentBoardAttributionAdminReadService(
            IndependentBoardAttributionV1Mapper mapper,
            IndependentBoardAttributionProperties properties) {
        this.mapper = mapper;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public Summary summary(
            String windowStart, String windowEnd, String requestedMode) {
        if (!properties.isObservationAdminReadEnabled()) {
            throw new IllegalStateException("attribution_admin_read_disabled");
        }
        Instant start = instant(windowStart);
        Instant end = instant(windowEnd);
        Duration duration = Duration.between(start, end);
        if (duration.isZero() || duration.isNegative()
                || duration.compareTo(MAX_WINDOW) > 0) {
            throw new IllegalArgumentException(
                    "window_must_be_positive_and_at_most_24_hours");
        }
        String mode = requestedMode == null || requestedMode.isBlank()
                ? "ALL"
                : requestedMode.trim().toUpperCase(Locale.ROOT);
        if (!MODES.contains(mode)) {
            throw new IllegalArgumentException("traffic_mode_invalid");
        }
        List<BoardAttributionSummaryRow> rows =
                mapper.selectAttributionSummary(
                        Date.from(start), Date.from(end), mode);
        List<BoardAttributionSummaryRow> safeRows =
                rows == null ? List.of() : List.copyOf(rows);
        long highWatermark = safeRows.stream()
                .mapToLong(BoardAttributionSummaryRow::getEventHighWatermark)
                .max()
                .orElse(0);
        return new Summary(
                start.toString(), end.toString(), mode,
                highWatermark, safeRows);
    }

    private Instant instant(String value) {
        try {
            return Instant.parse(value == null ? "" : value.trim());
        } catch (DateTimeException error) {
            throw new IllegalArgumentException("window_timestamp_invalid", error);
        }
    }

    public record Summary(
            String windowStart,
            String windowEnd,
            String mode,
            long eventHighWatermark,
            List<BoardAttributionSummaryRow> rows) {
    }
}
