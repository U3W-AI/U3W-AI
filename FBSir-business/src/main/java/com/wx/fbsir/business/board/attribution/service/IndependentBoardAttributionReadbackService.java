package com.wx.fbsir.business.board.attribution.service;

import com.wx.fbsir.business.board.attribution.config.IndependentBoardAttributionProperties;
import com.wx.fbsir.business.board.attribution.domain.BoardAttributionLedgerEvent;
import com.wx.fbsir.business.board.attribution.mapper.IndependentBoardAttributionV1Mapper;
import com.wx.fbsir.business.board.attribution.receipt.BoardAttributionReadbackResponseV1;
import com.wx.fbsir.business.board.attribution.receipt.VerifiedBoardAttributionReadbackRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;

import java.time.Clock;
import java.util.Objects;

/** Primary-datasource, exact-tuple readback with no write-capable mapper call. */
@Service
@ConditionalOnProperty(
        prefix = "fbsir.independent-board.attribution",
        name = "authoritative-readback-enabled",
        havingValue = "true",
        matchIfMissing = false)
public class IndependentBoardAttributionReadbackService {
    public static final String COMMITTED_EXACT = "COMMITTED_EXACT";
    public static final String NOT_FOUND_AUTHORITATIVE =
            "NOT_FOUND_AUTHORITATIVE";
    public static final String IDENTITY_COLLISION = "IDENTITY_COLLISION";
    public static final String READBACK_UNAVAILABLE = "READBACK_UNAVAILABLE";

    private final IndependentBoardAttributionV1Mapper mapper;
    private final IndependentBoardAttributionProperties properties;
    private final Clock clock;

    public IndependentBoardAttributionReadbackService(
            IndependentBoardAttributionV1Mapper mapper,
            IndependentBoardAttributionProperties properties) {
        this(mapper, properties, Clock.systemUTC());
    }

    IndependentBoardAttributionReadbackService(
            IndependentBoardAttributionV1Mapper mapper,
            IndependentBoardAttributionProperties properties,
            Clock clock) {
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Transactional(
            readOnly = true,
            isolation = Isolation.REPEATABLE_READ,
            propagation = Propagation.REQUIRES_NEW,
            timeout = 5)
    public BoardAttributionReadbackResponseV1 read(
            VerifiedBoardAttributionReadbackRequest request) {
        ensureAvailable();
        if (mapper.selectTransactionReadOnlyState() != 1) {
            throw new IndependentBoardAttributionReadbackUnavailableException(
                    "readback_transaction_not_read_only");
        }
        BoardAttributionLedgerEvent byEvent =
                mapper.selectEventByEventId(request.eventId());
        BoardAttributionLedgerEvent byReceipt =
                mapper.selectEventByReceiptId(request.receiptId());
        if (byEvent == null && byReceipt == null) {
            return response(request, NOT_FOUND_AUTHORITATIVE, true);
        }
        if (exact(byEvent, request) && exact(byReceipt, request)) {
            if (byEvent.isProductCreditEligible()
                    || byReceipt.isProductCreditEligible()) {
                throw new IndependentBoardAttributionReadbackUnavailableException(
                        "readback_credit_state_forbidden");
            }
            return response(request, COMMITTED_EXACT, true);
        }
        return response(request, IDENTITY_COLLISION, true);
    }

    public BoardAttributionReadbackResponseV1 unavailable(
            VerifiedBoardAttributionReadbackRequest request) {
        return response(request, READBACK_UNAVAILABLE, false);
    }

    private boolean exact(
            BoardAttributionLedgerEvent row,
            VerifiedBoardAttributionReadbackRequest request) {
        return row != null
                && Objects.equals(row.getEventId(), request.eventId())
                && Objects.equals(row.getReceiptId(), request.receiptId())
                && Objects.equals(row.getEventDigest(), request.eventDigest());
    }

    private BoardAttributionReadbackResponseV1 response(
            VerifiedBoardAttributionReadbackRequest request,
            String status,
            boolean authoritativeRead) {
        return new BoardAttributionReadbackResponseV1(
                BoardAttributionReadbackResponseV1.SCHEMA_VERSION,
                status,
                request.eventId(),
                request.receiptId(),
                request.eventDigest(),
                authoritativeRead,
                0,
                "",
                request.nonceHash(),
                clock.instant().toString(),
                "",
                normalized(properties.getAuthoritativeReadbackReceiverReleaseId()),
                normalized(properties.getAuthoritativeReadbackReceiverJarSha256()),
                false,
                "",
                "");
    }

    private void ensureAvailable() {
        if (!properties.isAuthoritativeReadbackEnabled()
                || normalized(
                    properties.getAuthoritativeReadbackReceiverReleaseId())
                    .isEmpty()
                || !normalized(
                    properties.getAuthoritativeReadbackReceiverJarSha256())
                    .matches("[0-9a-f]{64}")) {
            throw new IndependentBoardAttributionReadbackUnavailableException(
                    "readback_receiver_binding_unavailable");
        }
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim();
    }
}
