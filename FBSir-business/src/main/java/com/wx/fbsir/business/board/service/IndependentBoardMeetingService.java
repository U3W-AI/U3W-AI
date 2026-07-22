package com.wx.fbsir.business.board.service;

import com.wx.fbsir.business.board.domain.BoardUsageOperation;
import com.wx.fbsir.business.board.domain.BoardOperationAuditRow;
import com.wx.fbsir.business.board.dto.BoardMeetingReservationRequest;
import com.wx.fbsir.business.board.dto.BoardMeetingReservationView;
import com.wx.fbsir.business.board.dto.BoardOperationAuditEnvelope;
import com.wx.fbsir.business.board.dto.BoardOperationAuditView;
import com.wx.fbsir.business.board.mapper.IndependentBoardMapper;
import com.wx.fbsir.business.board.plan.domain.BoardPlanPolicyName;
import com.wx.fbsir.business.board.plan.service.BoardPlanPolicyDigest;
import com.wx.fbsir.common.exception.ServiceException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
    private static final Set<String> AUDIT_STATUSES = Set.of(
            "PENDING", "RESERVED", "COMPLETED", "REJECTED", "RELEASED", "FAILED", "UNKNOWN");
    private static final Set<String> AUDIT_PLAN_CODES = Set.of("BOARD_FREE", "BOARD_VIP");
    private static final String SAFE_IDENTIFIER_PATTERN = "^[A-Za-z0-9][A-Za-z0-9._:-]{7,127}$";
    private static final String SHA256_HEX_PATTERN = "^[0-9a-f]{64}$";
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
        List<BoardOperationAuditRow> rows;
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
        Set<String> operationIds = new HashSet<>();
        for (BoardOperationAuditRow row : rows) {
            validateAuditRow(row, tenantId);
            if (!operationIds.add(row.getOperationId())) {
                throw new ServiceException("BOARD_OPERATION_AUDIT_POLICY_LINEAGE_INVALID", 500);
            }
        }
        for (int index = 0; index < resultSize; index++) {
            records.add(toAuditView(rows.get(index)));
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

    private static void validateAuditRow(BoardOperationAuditRow operation, Long expectedTenantId) {
        if (operation == null
                || !Objects.equals(operation.getTenantId(), expectedTenantId)
                || !Objects.equals(operation.getProductCode(),
                        IndependentBoardEntitlementService.PRODUCT_CODE)
                || !Objects.equals(operation.getMetricCode(),
                        IndependentBoardEntitlementService.MEETING_METRIC)) {
            throw new ServiceException("BOARD_OPERATION_AUDIT_SCOPE_INVALID", 500);
        }
        boolean invalidOperation = !validIdentifier(operation.getOperationId())
                || operation.getMemberId() == null || operation.getMemberId() <= 0L
                || operation.getUserId() == null || operation.getUserId() <= 0L
                || !AUDIT_STATUSES.contains(operation.getStatus())
                || !AUDIT_PLAN_CODES.contains(operation.getEffectivePlanCode())
                || operation.getBucketDate() == null
                || operation.getAgendaCount() == null || operation.getAgendaCount() <= 0
                || operation.getSeatCount() == null || operation.getSeatCount() <= 0
                || operation.getRemainingCount() == null || operation.getRemainingCount() < 0
                || operation.getCreateTime() == null || operation.getUpdateTime() == null;
        boolean invalidLineage = !Objects.equals(operation.getLineageTenantId(), expectedTenantId)
                || !Objects.equals(operation.getLineageOperationId(), operation.getOperationId())
                || !Objects.equals(operation.getLineageProductCode(), operation.getProductCode())
                || !Objects.equals(operation.getLineagePlanCode(), operation.getEffectivePlanCode())
                || !validIdentifier(operation.getPolicyReceiptId())
                || operation.getPolicyVersion() == null || operation.getPolicyVersion() <= 0L
                || !validDigest(operation.getPolicyDigest())
                || !Objects.equals(operation.getPolicyReceiptReceiptId(), operation.getPolicyReceiptId())
                || !Objects.equals(operation.getPolicyReceiptProductCode(), operation.getProductCode())
                || !Objects.equals(operation.getPolicyReceiptPlanCode(), operation.getEffectivePlanCode())
                || !Objects.equals(operation.getPolicyReceiptPolicyVersion(), operation.getPolicyVersion())
                || !BoardPlanPolicyDigest.equal(
                        operation.getPolicyReceiptPolicyDigest(), operation.getPolicyDigest())
                || !BoardPlanPolicyName.isValid(operation.getPolicyPlanName());
        if (invalidOperation || invalidLineage) {
            throw new ServiceException("BOARD_OPERATION_AUDIT_POLICY_LINEAGE_INVALID", 500);
        }
    }

    private static BoardOperationAuditView toAuditView(BoardOperationAuditRow operation) {
        return new BoardOperationAuditView(
                operation.getOperationId(), operation.getTenantId(), operation.getMemberId(),
                operation.getUserId(), operation.getStatus(), operation.getEffectivePlanCode(),
                operation.getPolicyReceiptId(), operation.getPolicyVersion(),
                operation.getPolicyDigest(), operation.getPolicyPlanName(),
                operation.getBucketDate(), operation.getAgendaCount(), operation.getSeatCount(),
                operation.getRemainingCount(), operation.getCreateTime(), operation.getUpdateTime(),
                operation.getCompletedAt());
    }

    private static boolean validIdentifier(String value) {
        return value != null && value.matches(SAFE_IDENTIFIER_PATTERN);
    }

    private static boolean validDigest(String value) {
        return value != null && value.matches(SHA256_HEX_PATTERN);
    }
}
