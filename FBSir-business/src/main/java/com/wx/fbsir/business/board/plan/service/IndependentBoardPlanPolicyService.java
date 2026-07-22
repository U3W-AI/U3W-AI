package com.wx.fbsir.business.board.plan.service;

import com.wx.fbsir.business.board.plan.dto.BoardPlanPolicyAuditEnvelope;
import com.wx.fbsir.business.board.plan.dto.BoardPlanPolicyRevisionRequest;
import com.wx.fbsir.business.board.plan.dto.BoardPlanPolicyRevisionView;
import com.wx.fbsir.common.exception.ServiceException;
import java.sql.SQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;

/** Non-transactional coordinator for post-rollback exact idempotent replay. */
@Service
public class IndependentBoardPlanPolicyService {
    public static final String PRODUCT_CODE = "FBSIR_INDEPENDENT_BOARD";
    public static final String FREE_PLAN = "BOARD_FREE";
    public static final String VIP_PLAN = "BOARD_VIP";

    private final IndependentBoardPlanPolicyTransactionService transactionService;

    @Autowired
    public IndependentBoardPlanPolicyService(
            IndependentBoardPlanPolicyTransactionService transactionService) {
        this.transactionService = transactionService;
    }

    public BoardPlanPolicyRevisionView revise(
            BoardPlanPolicyRevisionRequest request, Long actorUserId) {
        try {
            return reviseWithReplay(request, actorUserId);
        } catch (PessimisticLockingFailureException conflict) {
            throw new ServiceException("BOARD_PLAN_POLICY_CONCURRENT_CONFLICT", 409);
        } catch (DataAccessException persistence) {
            if (isControlledProcedureVersionConflict(persistence)) {
                throw new ServiceException("BOARD_PLAN_POLICY_VERSION_CONFLICT", 409);
            }
            throw new ServiceException("BOARD_PLAN_POLICY_PERSISTENCE_FAILED", 500);
        }
    }

    private BoardPlanPolicyRevisionView reviseWithReplay(
            BoardPlanPolicyRevisionRequest request, Long actorUserId) {
        try {
            BoardPlanPolicyRevisionView committed =
                    transactionService.replayIfPresent(request, actorUserId);
            if (committed != null) {
                return committed;
            }
            return transactionService.reviseFresh(request, actorUserId);
        } catch (DuplicateKeyException duplicate) {
            return replayRequired(request, actorUserId);
        } catch (ServiceException failure) {
            if (!isVersionConflict(failure)) {
                throw failure;
            }
            BoardPlanPolicyRevisionView committed =
                    transactionService.replayIfPresent(request, actorUserId);
            if (committed != null) {
                return committed;
            }
            throw failure;
        }
    }

    public BoardPlanPolicyAuditEnvelope audit() {
        try {
            return transactionService.audit();
        } catch (PessimisticLockingFailureException conflict) {
            throw new ServiceException("BOARD_PLAN_POLICY_CONCURRENT_CONFLICT", 409);
        } catch (DataAccessException persistence) {
            throw new ServiceException("BOARD_PLAN_POLICY_PERSISTENCE_FAILED", 500);
        }
    }

    private BoardPlanPolicyRevisionView replayRequired(
            BoardPlanPolicyRevisionRequest request, Long actorUserId) {
        BoardPlanPolicyRevisionView committed =
                transactionService.replayRequired(request, actorUserId);
        if (committed == null) {
            throw new ServiceException("BOARD_PLAN_POLICY_UNEXPECTED_UNIQUE_CONFLICT", 500);
        }
        return committed;
    }

    private static boolean isVersionConflict(ServiceException failure) {
        return Integer.valueOf(409).equals(failure.getCode())
                && "BOARD_PLAN_POLICY_VERSION_CONFLICT".equals(failure.getMessage());
    }

    /**
     * MySQL SIGNAL SQLSTATE '45000' is translated by the JDBC/Spring stack into a
     * {@link DataAccessException}; preserve the public optimistic-concurrency contract.
     */
    private static boolean isControlledProcedureVersionConflict(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && sqlException.getErrorCode() == 1644
                    && sqlException.getMessage() != null
                    && sqlException.getMessage().contains("BOARD_PLAN_POLICY_VERSION_CONFLICT")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
