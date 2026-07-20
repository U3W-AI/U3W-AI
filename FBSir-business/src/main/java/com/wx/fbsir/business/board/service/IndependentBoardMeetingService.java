package com.wx.fbsir.business.board.service;

import com.wx.fbsir.business.board.domain.BoardUsageBudget;
import com.wx.fbsir.business.board.domain.BoardUsageOperation;
import com.wx.fbsir.business.board.dto.BoardEntitlementSnapshot;
import com.wx.fbsir.business.board.dto.BoardMeetingReservationRequest;
import com.wx.fbsir.business.board.dto.BoardMeetingReservationView;
import com.wx.fbsir.business.board.mapper.IndependentBoardMapper;
import com.wx.fbsir.common.exception.ServiceException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IndependentBoardMeetingService {
    private final IndependentBoardMapper mapper;
    private final IndependentBoardEntitlementService entitlementService;
    private final Clock clock;

    @Autowired
    public IndependentBoardMeetingService(
            IndependentBoardMapper mapper,
            IndependentBoardEntitlementService entitlementService) {
        this(mapper, entitlementService, Clock.system(IndependentBoardEntitlementService.INITIAL_TENANT_ZONE));
    }

    IndependentBoardMeetingService(
            IndependentBoardMapper mapper,
            IndependentBoardEntitlementService entitlementService,
            Clock clock) {
        this.mapper = mapper;
        this.entitlementService = entitlementService;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public BoardMeetingReservationView reserve(
            BoardMeetingReservationRequest request, Long authenticatedUserId) {
        BoardEntitlementSnapshot snapshot = entitlementService.getSnapshotForReservation(
                request.tenantId(), authenticatedUserId);
        validateMeetingShape(request, snapshot);
        LocalDate today = LocalDate.now(clock);
        String digest = reservationDigest(request, authenticatedUserId);

        BoardUsageOperation pending = new BoardUsageOperation();
        pending.setOperationId(request.operationId());
        pending.setRequestDigest(digest);
        pending.setTenantId(request.tenantId());
        pending.setMemberId(snapshot.memberId());
        pending.setUserId(authenticatedUserId);
        pending.setProductCode(IndependentBoardEntitlementService.PRODUCT_CODE);
        pending.setMetricCode(IndependentBoardEntitlementService.MEETING_METRIC);
        pending.setBucketDate(today);
        pending.setUnits(1);
        pending.setStatus("PENDING");
        pending.setEffectivePlanCode(snapshot.effectivePlanCode());
        pending.setAgendaCount(request.agendaCount());
        pending.setSeatCount(request.seatCount());
        pending.setRemainingCount(0);

        try {
            if (mapper.insertOperation(pending) != 1) {
                throw new ServiceException("MEETING_OPERATION_WRITE_FAILED", 500);
            }
        } catch (DuplicateKeyException duplicate) {
            BoardUsageOperation existing = mapper.selectOperationForUpdate(
                    request.tenantId(), request.operationId());
            return verifyIdempotentReplay(existing, pending);
        }

        mapper.prepareUsageBudget(
                request.tenantId(), snapshot.memberId(), IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.MEETING_METRIC, today, snapshot.dailyMeetingLimit());
        if (mapper.reserveOneMeeting(
                request.tenantId(), snapshot.memberId(), IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.MEETING_METRIC, today, snapshot.dailyMeetingLimit()) != 1) {
            throw new ServiceException("DAILY_MEETING_QUOTA_EXHAUSTED", 409);
        }
        BoardUsageBudget budget = mapper.selectUsageBudget(
                request.tenantId(), snapshot.memberId(), IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.MEETING_METRIC, today);
        if (budget == null) {
            throw new ServiceException("USAGE_BUDGET_READBACK_FAILED", 500);
        }
        int remaining = Math.max(0, snapshot.dailyMeetingLimit()
                - nullSafe(budget.getUsedCount()) - nullSafe(budget.getReservedCount()));
        if (mapper.markOperationReserved(request.tenantId(), request.operationId(), remaining) != 1) {
            throw new ServiceException("MEETING_RESERVATION_FINALIZE_FAILED", 500);
        }
        pending.setStatus("RESERVED");
        pending.setRemainingCount(remaining);
        return toView(pending);
    }

    @Transactional(readOnly = true)
    public List<BoardUsageOperation> listOperations(Long tenantId) {
        if (tenantId == null || tenantId <= 0L) {
            throw new ServiceException("TENANT_REQUIRED", 400);
        }
        return mapper.selectOperationsByTenant(tenantId, IndependentBoardEntitlementService.PRODUCT_CODE);
    }

    private BoardMeetingReservationView verifyIdempotentReplay(
            BoardUsageOperation existing, BoardUsageOperation requested) {
        if (existing == null) {
            throw new ServiceException("IDEMPOTENCY_COLLISION_READBACK_FAILED", 409);
        }
        boolean sameIdentity = Objects.equals(existing.getTenantId(), requested.getTenantId())
                && Objects.equals(existing.getMemberId(), requested.getMemberId())
                && Objects.equals(existing.getUserId(), requested.getUserId())
                && Objects.equals(existing.getProductCode(), requested.getProductCode())
                && Objects.equals(existing.getMetricCode(), requested.getMetricCode())
                && BoardDigest.equal(existing.getRequestDigest(), requested.getRequestDigest());
        if (!sameIdentity) {
            throw new ServiceException("IDEMPOTENCY_DIGEST_CONFLICT", 409);
        }
        if (!Objects.equals(existing.getStatus(), "RESERVED")) {
            throw new ServiceException("IDEMPOTENT_OPERATION_NOT_FINAL", 409);
        }
        return toView(existing);
    }

    private void validateMeetingShape(
            BoardMeetingReservationRequest request, BoardEntitlementSnapshot snapshot) {
        if (request.agendaCount() > snapshot.agendaLimit()) {
            throw new ServiceException("AGENDA_LIMIT_EXCEEDED", 400);
        }
        if (request.seatCount() > BoardMeetingReservationRequest.INITIAL_SAFETY_MAX_SEAT_COUNT) {
            throw new ServiceException("SEAT_SAFETY_LIMIT_EXCEEDED", 400);
        }
        if (snapshot.seatLimit() != null && request.seatCount() > snapshot.seatLimit()) {
            throw new ServiceException("SEAT_LIMIT_EXCEEDED", 400);
        }
    }

    private String reservationDigest(BoardMeetingReservationRequest request, Long authenticatedUserId) {
        String canonical = String.join("\n",
                IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.MEETING_METRIC,
                String.valueOf(request.tenantId()),
                String.valueOf(authenticatedUserId),
                String.valueOf(request.agendaCount()),
                String.valueOf(request.seatCount()));
        return BoardDigest.sha256(canonical);
    }

    private BoardMeetingReservationView toView(BoardUsageOperation operation) {
        return new BoardMeetingReservationView(
                operation.getOperationId(), operation.getStatus(), operation.getEffectivePlanCode(),
                operation.getAgendaCount(), operation.getSeatCount(), operation.getRemainingCount());
    }

    private int nullSafe(Integer value) {
        return value == null ? 0 : value;
    }
}
