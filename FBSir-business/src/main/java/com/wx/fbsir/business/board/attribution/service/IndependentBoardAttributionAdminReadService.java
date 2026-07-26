package com.wx.fbsir.business.board.attribution.service;

import com.wx.fbsir.business.board.attribution.config.IndependentBoardAttributionProperties;
import com.wx.fbsir.business.board.attribution.domain.BoardAttributionLedgerEvent;
import com.wx.fbsir.business.board.attribution.domain.BoardAttributionSummaryRow;
import com.wx.fbsir.business.board.attribution.mapper.IndependentBoardAttributionV1Mapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Pattern;

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
    private static final Pattern EVENT_ID = Pattern.compile("[0-9a-f]{64}");
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

    /**
     * Returns a narrow, content-free receipt projection for correlating an API2 event ID
     * with the durable U3W ledger. The raw binding key and all subject identifiers remain
     * server-only; callers can compare only its stable SHA-256 fingerprint.
     */
    @Transactional(readOnly = true)
    public Receipt receipt(String requestedEventId) {
        if (!properties.isObservationAdminReadEnabled()) {
            throw new IllegalStateException("attribution_admin_read_disabled");
        }
        String eventId = requestedEventId == null ? ""
                : requestedEventId.trim();
        if (!EVENT_ID.matcher(eventId).matches()) {
            throw new IllegalArgumentException("event_id_invalid");
        }
        BoardAttributionLedgerEvent row = mapper.selectEventByEventId(eventId);
        if (row == null) {
            throw new NoSuchElementException("attribution_event_not_found");
        }
        return new Receipt(
                row.getEventId(), row.getReceiptId(),
                sameBindingFingerprint(row.getSameBindingKey()),
                row.getEventType(), row.getSequenceNo(),
                row.getOccurredAt() == null ? null
                        : row.getOccurredAt().toInstant().toString(),
                row.getProductId(), row.getListedManifestVersion(),
                row.getIntentFamily(), row.getTrafficClass(),
                row.isProductCreditEligible(), row.getEventWatermark());
    }

    private Instant instant(String value) {
        try {
            return Instant.parse(value == null ? "" : value.trim());
        } catch (DateTimeException error) {
            throw new IllegalArgumentException("window_timestamp_invalid", error);
        }
    }

    private String sameBindingFingerprint(String sameBindingKey) {
        if (sameBindingKey == null || sameBindingKey.isBlank()) {
            throw new IllegalStateException("attribution_binding_fingerprint_unavailable");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(sameBindingKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                value.append(String.format("%02x", item));
            }
            return value.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("sha256_unavailable", error);
        }
    }

    public record Summary(
            String windowStart,
            String windowEnd,
            String mode,
            long eventHighWatermark,
            List<BoardAttributionSummaryRow> rows) {
    }

    public record Receipt(
            String eventId,
            String receiptId,
            String sameBindingFingerprint,
            String eventType,
            long sequenceNo,
            String occurredAt,
            String productId,
            String listedManifestVersion,
            String intentFamily,
            String trafficClass,
            boolean productCreditEligible,
            long eventWatermark) {
    }
}
