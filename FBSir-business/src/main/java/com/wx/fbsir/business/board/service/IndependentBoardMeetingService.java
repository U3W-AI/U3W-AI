package com.wx.fbsir.business.board.service;

import com.wx.fbsir.business.board.domain.BoardUsageOperation;
import com.wx.fbsir.business.board.dto.BoardMeetingReservationRequest;
import com.wx.fbsir.business.board.dto.BoardMeetingReservationView;
import com.wx.fbsir.business.board.mapper.IndependentBoardMapper;
import com.wx.fbsir.common.exception.ServiceException;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Non-transactional reservation coordinator.
 *
 * <p>The first plain INSERT claim and an idempotent replay deliberately run in separate
 * transactions. A duplicate-key claim must fully roll back before the current-read replay starts;
 * otherwise concurrent losers can deadlock while upgrading duplicate-check record locks.</p>
 */
@Service
public class IndependentBoardMeetingService {
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
            return transactionService.reserveFresh(request, authenticatedUserId);
        } catch (DuplicateKeyException duplicate) {
            return transactionService.replayCommitted(request, authenticatedUserId);
        }
    }

    @Transactional(readOnly = true)
    public List<BoardUsageOperation> listOperations(Long tenantId) {
        if (tenantId == null || tenantId <= 0L) {
            throw new ServiceException("TENANT_REQUIRED", 400);
        }
        return mapper.selectOperationsByTenant(tenantId, IndependentBoardEntitlementService.PRODUCT_CODE);
    }
}
