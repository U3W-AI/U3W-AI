package com.wx.fbsir.business.board.service;

import com.wx.fbsir.business.board.domain.BoardUsageOperation;
import com.wx.fbsir.business.board.dto.BoardMeetingReservationRequest;
import com.wx.fbsir.business.board.dto.BoardMeetingReservationView;
import com.wx.fbsir.business.board.dto.BoardOperationAuditEnvelope;
import com.wx.fbsir.business.board.dto.BoardOperationAuditView;
import com.wx.fbsir.business.board.mapper.IndependentBoardMapper;
import com.wx.fbsir.common.exception.ServiceException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

/**
 * Non-transactional reservation coordinator.
 *
 * <p>The first plain INSERT claim and an idempotent replay deliberately run in separate
 * transactions. A duplicate-key claim must fully roll back before the current-read replay starts;
 * otherwise concurrent losers can deadlock while upgrading duplicate-check record locks.</p>
 */
@Service
public class IndependentBoardMeetingService {
    public static final int ADMIN_OPERATION_LIMIT = 500;
    public static final int ADMIN_OPERATION_FETCH_LIMIT = ADMIN_OPERATION_LIMIT + 1;
    private final IndependentBoardMapper mapper;
    private final IndependentBoardMeetingTransactionService transactionService;

    @Autowired
    public IndependentBoardMeetingService(
            IndependentBoardMapper mapper,
            IndependentBoardMeetingTransactionService transactionService) {
        this.mapper = mapper;
        this.transactionService = transactionService;
    }

    IndependentBoardMeetingService(
            IndependentBoardMapper mapper,
            IndependentBoardEntitlementService entitlementService,
            Clock clock) {
        this(mapper, new IndependentBoardMeetingTransactionService(mapper, entitlementService, clock));
    }

    public BoardMeetingReservationView reserve(
            BoardMeetingReservationRequest request, Long authenticatedUserId) {
        try {
            BoardMeetingReservationView committed = transactionService.replayCommittedIfPresent(
                    request, authenticatedUserId);
            if (committed != null) {
                return committed;
            }
        } catch (PessimisticLockingFailureException conflict) {
            throw stableConcurrencyConflict();
        } catch (DataAccessException persistence) {
            throw stablePersistenceFailure();
        } catch (TransactionException transactionFailure) {
            throw stablePersistenceFailure();
        }
        try {
            return transactionService.reserveFresh(request, authenticatedUserId);
        } catch (DuplicateKeyException duplicate) {
            return replayCommittedAfterDuplicate(request, authenticatedUserId);
        } catch (PessimisticLockingFailureException conflict) {
            throw stableConcurrencyConflict();
        } catch (DataAccessException persistence) {
            throw stablePersistenceFailure();
        } catch (TransactionException transactionFailure) {
            throw stablePersistenceFailure();
        }
    }

    public BoardOperationAuditEnvelope listOperations(Long tenantId) {
        if (tenantId == null || tenantId <= 0L) {
            throw new ServiceException("TENANT_REQUIRED", 400);
        }
        List<BoardUsageOperation> rows;
        try {
            rows = mapper.selectOperationsByTenant(
                    tenantId,
                    IndependentBoardEntitlementService.PRODUCT_CODE,
                    IndependentBoardEntitlementService.MEETING_METRIC);
        } catch (PessimisticLockingFailureException conflict) {
            throw stableConcurrencyConflict();
        } catch (DataAccessException persistence) {
            throw stablePersistenceFailure();
        }
        if (rows == null || rows.size() > ADMIN_OPERATION_FETCH_LIMIT) {
            throw new ServiceException("BOARD_OPERATION_AUDIT_CURRENT_READ_FAILED", 500);
        }
        boolean truncated = rows.size() > ADMIN_OPERATION_LIMIT;
        int resultSize = Math.min(rows.size(), ADMIN_OPERATION_LIMIT);
        List<BoardOperationAuditView> records = new ArrayList<>(resultSize);
        for (int index = 0; index < resultSize; index++) {
            records.add(toAuditView(rows.get(index), tenantId));
        }
        return new BoardOperationAuditEnvelope(records, ADMIN_OPERATION_LIMIT, truncated);
    }

    private BoardMeetingReservationView replayCommittedAfterDuplicate(
            BoardMeetingReservationRequest request, Long authenticatedUserId) {
        try {
            return transactionService.replayCommitted(request, authenticatedUserId);
        } catch (PessimisticLockingFailureException conflict) {
            throw stableConcurrencyConflict();
        } catch (DataAccessException persistence) {
            throw stablePersistenceFailure();
        } catch (TransactionException transactionFailure) {
            throw stablePersistenceFailure();
        }
    }

    private static ServiceException stableConcurrencyConflict() {
        return new ServiceException("MEETING_RESERVATION_CONCURRENT_CONFLICT", 409);
    }

    private static ServiceException stablePersistenceFailure() {
        return new ServiceException("MEETING_RESERVATION_PERSISTENCE_FAILED", 503);
    }

    private BoardOperationAuditView toAuditView(
            BoardUsageOperation operation,
            Long expectedTenantId) {
        if (operation == null
                || !Objects.equals(operation.getTenantId(), expectedTenantId)
                || !Objects.equals(operation.getProductCode(),
                        IndependentBoardEntitlementService.PRODUCT_CODE)
                || !Objects.equals(operation.getMetricCode(),
                        IndependentBoardEntitlementService.MEETING_METRIC)) {
            throw new ServiceException("BOARD_OPERATION_AUDIT_SCOPE_INVALID", 500);
        }
        return new BoardOperationAuditView(
                operation.getOperationId(), operation.getTenantId(), operation.getMemberId(),
                operation.getUserId(), operation.getStatus(), operation.getEffectivePlanCode(),
                operation.getBucketDate(), operation.getAgendaCount(), operation.getSeatCount(),
                operation.getRemainingCount(), operation.getCreateTime(), operation.getUpdateTime(),
                operation.getCompletedAt());
    }
}
