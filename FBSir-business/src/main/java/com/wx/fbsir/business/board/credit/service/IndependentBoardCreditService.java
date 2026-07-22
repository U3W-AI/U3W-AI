package com.wx.fbsir.business.board.credit.service;

import com.wx.fbsir.business.board.credit.dto.BoardCreditAuditEnvelope;
import com.wx.fbsir.business.board.credit.dto.BoardCreditCommandResult;
import com.wx.fbsir.business.board.credit.dto.BoardCreditGrantRequest;
import com.wx.fbsir.business.board.credit.dto.BoardCreditReversalRequest;
import com.wx.fbsir.business.board.credit.mapper.IndependentBoardCreditMapper;
import com.wx.fbsir.common.exception.ServiceException;
import java.time.Clock;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * Non-transactional coordinator. A duplicate fresh claim must roll back before a
 * REQUIRES_NEW replay reads and verifies the committed winner.
 */
@Service
public class IndependentBoardCreditService {
    public static final String ACCOUNT_SCOPE = "USER_GLOBAL";
    public static final String CURRENCY_CODE = "FBS_POINTS";

    private final IndependentBoardCreditTransactionService transactionService;

    @Autowired
    public IndependentBoardCreditService(
            IndependentBoardCreditTransactionService transactionService) {
        this.transactionService = transactionService;
    }

    IndependentBoardCreditService(
            IndependentBoardCreditMapper mapper, Clock clock, Supplier<String> idGenerator) {
        this.transactionService = new IndependentBoardCreditTransactionService(
                mapper, clock, idGenerator);
    }

    public BoardCreditCommandResult grant(
            BoardCreditGrantRequest request, Long actorUserId) {
        try {
            BoardCreditCommandResult committed = transactionService.replayGrantIfPresent(
                    request, actorUserId);
            if (committed != null) {
                return committed;
            }
            return transactionService.grantFresh(request, actorUserId);
        } catch (DuplicateKeyException duplicate) {
            return replayGrant(request, actorUserId);
        } catch (ServiceException failure) {
            return recoverGrantAfterVersionConflict(request, actorUserId, failure);
        } catch (PessimisticLockingFailureException conflict) {
            throw stableConcurrencyConflict();
        } catch (DataAccessException persistence) {
            throw stablePersistenceFailure();
        }
    }

    public BoardCreditCommandResult reverse(
            BoardCreditReversalRequest request, Long actorUserId) {
        try {
            BoardCreditCommandResult committed = transactionService.replayReversalIfPresent(
                    request, actorUserId);
            if (committed != null) {
                return committed;
            }
            return transactionService.reverseFresh(request, actorUserId);
        } catch (DuplicateKeyException duplicate) {
            return replayReversal(request, actorUserId);
        } catch (ServiceException failure) {
            return recoverReversalAfterVersionConflict(request, actorUserId, failure);
        } catch (PessimisticLockingFailureException conflict) {
            throw stableConcurrencyConflict();
        } catch (DataAccessException persistence) {
            throw stablePersistenceFailure();
        }
    }

    public BoardCreditAuditEnvelope audit(Long userId) {
        try {
            return transactionService.audit(userId);
        } catch (PessimisticLockingFailureException conflict) {
            throw stableConcurrencyConflict();
        } catch (DataAccessException persistence) {
            throw stablePersistenceFailure();
        }
    }

    private BoardCreditCommandResult replayGrant(
            BoardCreditGrantRequest request, Long actorUserId) {
        try {
            return transactionService.replayGrant(request, actorUserId);
        } catch (PessimisticLockingFailureException conflict) {
            throw stableConcurrencyConflict();
        } catch (DataAccessException persistence) {
            throw stablePersistenceFailure();
        }
    }

    private BoardCreditCommandResult replayReversal(
            BoardCreditReversalRequest request, Long actorUserId) {
        try {
            return transactionService.replayReversal(request, actorUserId);
        } catch (PessimisticLockingFailureException conflict) {
            throw stableConcurrencyConflict();
        } catch (DataAccessException persistence) {
            throw stablePersistenceFailure();
        }
    }

    /**
     * A same-key contender can wait behind the winner's user/account locks and
     * observe the advanced version before it reaches the unique operation claim.
     * Recheck only the exact committed key after the fresh transaction rolled back.
     */
    private BoardCreditCommandResult recoverGrantAfterVersionConflict(
            BoardCreditGrantRequest request, Long actorUserId, ServiceException failure) {
        if (!isAccountVersionConflict(failure)) {
            throw failure;
        }
        try {
            BoardCreditCommandResult committed = transactionService.replayGrantIfPresent(
                    request, actorUserId);
            if (committed != null) {
                return committed;
            }
            throw failure;
        } catch (PessimisticLockingFailureException conflict) {
            throw stableConcurrencyConflict();
        } catch (DataAccessException persistence) {
            throw stablePersistenceFailure();
        }
    }

    private BoardCreditCommandResult recoverReversalAfterVersionConflict(
            BoardCreditReversalRequest request, Long actorUserId, ServiceException failure) {
        if (!isAccountVersionConflict(failure)) {
            throw failure;
        }
        try {
            BoardCreditCommandResult committed = transactionService.replayReversalIfPresent(
                    request, actorUserId);
            if (committed != null) {
                return committed;
            }
            throw failure;
        } catch (PessimisticLockingFailureException conflict) {
            throw stableConcurrencyConflict();
        } catch (DataAccessException persistence) {
            throw stablePersistenceFailure();
        }
    }

    private boolean isAccountVersionConflict(ServiceException failure) {
        return Integer.valueOf(409).equals(failure.getCode())
                && "CREDIT_ACCOUNT_VERSION_CONFLICT".equals(failure.getMessage());
    }

    private ServiceException stableConcurrencyConflict() {
        return new ServiceException("CREDIT_LEDGER_CONCURRENT_CONFLICT", 409);
    }

    private ServiceException stablePersistenceFailure() {
        return new ServiceException("CREDIT_LEDGER_PERSISTENCE_FAILED", 500);
    }
}
