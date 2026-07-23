package com.wx.fbsir.business.board.credit.service;

import com.wx.fbsir.business.fbs.dto.ConsumeResult;
import com.wx.fbsir.common.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillConsumeCreditWriterTest {

    @Test
    void freshCommandUsesTheInternalTransactionBoundaryAndKeepsTheRawSessionOutOfCommand() {
        SkillConsumeCreditTransactionService transaction =
                mock(SkillConsumeCreditTransactionService.class);
        SkillConsumeCreditWriter writer = new SkillConsumeCreditWriter(transaction);
        ConsumeResult committed = ConsumeResult.success("usage-001", 75);
        when(transaction.replayIfPresent(any())).thenReturn(null);
        when(transaction.consumeFresh(any(), eq("host-session-secret"))).thenReturn(committed);

        ConsumeResult result = writer.consume(
                7L, "usage-001", 9L, "1.2.3", "skill-code", "rule-code", 25,
                "WORKBUDDY", "host-session-secret");

        assertTrue(result.isSuccess());
        assertEquals(75, result.getRemainPoints());
        ArgumentCaptor<SkillConsumeCreditCommand> command =
                ArgumentCaptor.forClass(SkillConsumeCreditCommand.class);
        verify(transaction).consumeFresh(command.capture(), eq("host-session-secret"));
        assertEquals("usage-001", command.getValue().usageRecordId());
        assertEquals("rule-code", command.getValue().ruleCode());
        assertFalse(command.getValue().toString().contains("host-session-secret"));
        verify(transaction).replayIfPresent(any());
    }

    @Test
    void exactCommittedReplaySkipsTheFreshTransaction() {
        SkillConsumeCreditTransactionService transaction =
                mock(SkillConsumeCreditTransactionService.class);
        SkillConsumeCreditWriter writer = new SkillConsumeCreditWriter(transaction);
        when(transaction.replayIfPresent(any())).thenReturn(ConsumeResult.success("usage-001", 75));

        ConsumeResult result = writer.consume(
                7L, "usage-001", 9L, "1.2.3", "skill-code", "rule-code", 25,
                "API", "host-session-secret");

        assertTrue(result.isSuccess());
        verify(transaction, never()).consumeFresh(any(), any());
    }

    @Test
    void duplicateFreshClaimReadsOnlyTheCommittedWinnerAndUnsafeInputNeverReachesPersistence() {
        SkillConsumeCreditTransactionService transaction =
                mock(SkillConsumeCreditTransactionService.class);
        SkillConsumeCreditWriter writer = new SkillConsumeCreditWriter(transaction);
        when(transaction.replayIfPresent(any())).thenReturn(null);
        when(transaction.consumeFresh(any(), any()))
                .thenThrow(new DuplicateKeyException("duplicate operation"));
        when(transaction.requireReplay(any())).thenReturn(ConsumeResult.success("usage-001", 75));

        ConsumeResult replay = writer.consume(
                7L, "usage-001", 9L, "1.2.3", "skill-code", "rule-code", 25,
                "STANDALONE", "host-session-secret");
        assertTrue(replay.isSuccess());
        verify(transaction).requireReplay(any());
        clearInvocations(transaction);

        ConsumeResult rejected = writer.consume(
                7L, "usage-001", 9L, "1.2.3", "skill-code", "rule-code", 25,
                "ENTERPRISE", "host-session-secret");
        assertFalse(rejected.isSuccess());
        assertEquals("SKILL_CREDIT_COMMAND_INVALID_HOST_TYPE", rejected.getFailReason());
        verify(transaction, never()).consumeFresh(any(), eq("host-session-secret"));
    }

    @Test
    void freshContenderThatFindsAnExactSuccessfulUsageReplaysOnlyAfterItsTransactionRollsBack() {
        SkillConsumeCreditTransactionService transaction =
                mock(SkillConsumeCreditTransactionService.class);
        SkillConsumeCreditWriter writer = new SkillConsumeCreditWriter(transaction);
        when(transaction.replayIfPresent(any())).thenReturn(null);
        when(transaction.consumeFresh(any(), any()))
                .thenThrow(new ServiceException("SKILL_CREDIT_LEDGER_REPLAY_REQUIRED", 409));
        when(transaction.requireReplay(any())).thenReturn(ConsumeResult.success("usage-001", 75));

        ConsumeResult result = writer.consume(
                7L, "usage-001", 9L, "1.2.3", "skill-code", "rule-code", 25,
                "WORKBUDDY", "host-session-secret");

        assertTrue(result.isSuccess());
        verify(transaction).requireReplay(any());
    }

    @Test
    void ambientTransactionFailsClosedBeforeAnyLedgerTransactionIsOpened() {
        SkillConsumeCreditTransactionService transaction =
                mock(SkillConsumeCreditTransactionService.class);
        SkillConsumeCreditWriter writer = new SkillConsumeCreditWriter(transaction);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            ConsumeResult result = writer.consume(
                    7L, "usage-001", 9L, "1.2.3", "skill-code", "rule-code", 25,
                    "WORKBUDDY", null);

            assertFalse(result.isSuccess());
            assertEquals("SKILL_CONSUME_V2_AMBIENT_TRANSACTION_FORBIDDEN",
                    result.getFailReason());
            verify(transaction, never()).replayIfPresent(any());
            verify(transaction, never()).consumeFresh(any(), any());
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }
}
