package com.wx.fbsir.business.board.credit.service;

import com.wx.fbsir.business.fbs.dto.ConsumeResult;
import com.wx.fbsir.common.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * Internal-only default-off 042 writer facade. It is deliberately not injected
 * into the legacy consume path until the dual-MySQL writer matrix is complete.
 */
@Service
public class SkillConsumeCreditWriter {
    private final SkillConsumeCreditTransactionService transactionService;

    @Autowired
    public SkillConsumeCreditWriter(SkillConsumeCreditTransactionService transactionService) {
        this.transactionService = transactionService;
    }

    public ConsumeResult consume(
            Long userId, String usageRecordId, Long packId, String packVersion,
            String skillCode, String ruleCode, Integer amount, String hostType,
            String rawHostSessionId) {
        final SkillConsumeCreditCommand command;
        try {
            command = SkillConsumeCreditCommand.create(
                    userId, usageRecordId, packId, packVersion, skillCode, ruleCode, amount,
                    hostType, rawHostSessionId);
        } catch (IllegalArgumentException invalid) {
            return ConsumeResult.fail(usageRecordId, invalid.getMessage());
        }
        try {
            ConsumeResult replay = transactionService.replayIfPresent(command);
            return replay != null ? replay : transactionService.consumeFresh(command, rawHostSessionId);
        } catch (DuplicateKeyException duplicate) {
            return requireCommittedWinner(command);
        } catch (PessimisticLockingFailureException conflict) {
            return ConsumeResult.fail(command.usageRecordId(), "SKILL_CREDIT_LEDGER_CONCURRENT_CONFLICT");
        } catch (DataAccessException persistence) {
            return ConsumeResult.fail(command.usageRecordId(), "SKILL_CREDIT_LEDGER_PERSISTENCE_FAILED");
        } catch (ServiceException failure) {
            if ("SKILL_CREDIT_LEDGER_REPLAY_REQUIRED".equals(failure.getMessage())) {
                return requireCommittedWinner(command);
            }
            return ConsumeResult.fail(command.usageRecordId(), failure.getMessage());
        }
    }

    private ConsumeResult requireCommittedWinner(SkillConsumeCreditCommand command) {
        try {
            return transactionService.requireReplay(command);
        } catch (PessimisticLockingFailureException conflict) {
            return ConsumeResult.fail(command.usageRecordId(), "SKILL_CREDIT_LEDGER_CONCURRENT_CONFLICT");
        } catch (DataAccessException persistence) {
            return ConsumeResult.fail(command.usageRecordId(), "SKILL_CREDIT_LEDGER_PERSISTENCE_FAILED");
        } catch (ServiceException failure) {
            return ConsumeResult.fail(command.usageRecordId(), failure.getMessage());
        }
    }
}
