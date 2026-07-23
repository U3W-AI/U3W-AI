package com.wx.fbsir.business.board.credit.service;

import com.wx.fbsir.business.board.credit.domain.BoardCreditAccount;
import com.wx.fbsir.business.board.credit.domain.BoardCreditEntry;
import com.wx.fbsir.business.board.credit.domain.BoardCreditUserProjection;
import com.wx.fbsir.business.board.credit.domain.SkillCreditOperation;
import com.wx.fbsir.business.board.credit.domain.SkillCreditProjectionBridge;
import com.wx.fbsir.business.board.credit.mapper.SkillConsumeCreditLedgerMapper;
import com.wx.fbsir.business.fbs.domain.entity.FbsSkillUsageRecord;
import com.wx.fbsir.business.fbs.domain.enums.UsageStatus;
import com.wx.fbsir.business.fbs.dto.ConsumeResult;
import com.wx.fbsir.business.fbs.mapper.FbsSkillUsageRecordMapper;
import com.wx.fbsir.common.exception.ServiceException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Queue;
import java.util.ArrayDeque;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillConsumeCreditTransactionServiceTest {
    private static final String ACCOUNT_ID = "023e4567-e89b-12d3-a456-426614174000";
    private static final String OPERATION_ID = "123e4567-e89b-12d3-a456-426614174000";
    private static final String ENTRY_ID = "223e4567-e89b-12d3-a456-426614174000";
    private static final Instant NOW = Instant.parse("2026-07-23T02:00:00Z");

    private SkillConsumeCreditLedgerMapper mapper;
    private FbsSkillUsageRecordMapper usageMapper;
    private SkillConsumeCreditTransactionService transaction;

    @BeforeEach
    void setUp() {
        mapper = mock(SkillConsumeCreditLedgerMapper.class);
        usageMapper = mock(FbsSkillUsageRecordMapper.class);
        Queue<String> ids = new ArrayDeque<>();
        ids.add(ACCOUNT_ID);
        ids.add(OPERATION_ID);
        ids.add(ENTRY_ID);
        transaction = new SkillConsumeCreditTransactionService(
                mapper, usageMapper, Clock.fixed(NOW, ZoneOffset.UTC), ids::remove);
    }

    @Test
    void freshClaimLocksInContractOrderAndCommitsOneV2OperationEntryBridgeAndUsageCas() {
        when(mapper.selectUserProjectionForUpdate(7L)).thenReturn(user(100));
        when(mapper.countLegacyCreditAccounts(7L)).thenReturn(0);
        when(usageMapper.selectByRecordIdForUpdate("usage-001"))
                .thenReturn(null, usage(UsageStatus.IN_PROGRESS.getCode()));
        when(usageMapper.insertUsageRecord(any())).thenReturn(1);
        when(mapper.insertAccount(any())).thenReturn(1);
        when(mapper.insertBridge(any())).thenReturn(1);
        when(mapper.insertOperation(any())).thenReturn(1);
        when(mapper.insertEntry(any())).thenReturn(1);
        when(mapper.updateAccountIfVersion(any(), anyLong(), anyLong(), anyLong(), anyString()))
                .thenReturn(1);
        when(mapper.updateBridgeIfVersion(any(), anyLong(), anyLong())).thenReturn(1);
        when(mapper.updateUserProjectionIfBalance(7L, 100L, 75L)).thenReturn(1);
        when(usageMapper.updateStatusByRecordId(
                eq("usage-001"), eq(UsageStatus.SUCCESS.getCode()), isNull()))
                .thenReturn(1);

        ConsumeResult result = transaction.consumeFresh(command(), "host-session-secret");

        assertEquals(true, result.isSuccess());
        assertEquals(75, result.getRemainPoints());
        ArgumentCaptor<BoardCreditAccount> account = ArgumentCaptor.forClass(BoardCreditAccount.class);
        ArgumentCaptor<SkillCreditProjectionBridge> bridge =
                ArgumentCaptor.forClass(SkillCreditProjectionBridge.class);
        ArgumentCaptor<SkillCreditOperation> operation =
                ArgumentCaptor.forClass(SkillCreditOperation.class);
        ArgumentCaptor<BoardCreditEntry> entry = ArgumentCaptor.forClass(BoardCreditEntry.class);
        verify(mapper).insertAccount(account.capture());
        verify(mapper).insertBridge(bridge.capture());
        verify(mapper).insertOperation(operation.capture());
        verify(mapper).insertEntry(entry.capture());
        assertEquals(ACCOUNT_ID, account.getValue().getAccountId());
        assertEquals(100L, account.getValue().getOpeningBalance());
        assertEquals(100L, bridge.getValue().getProjectedBalance());
        assertEquals(OPERATION_ID, operation.getValue().getOperationId());
        assertEquals(-25L, operation.getValue().getDelta());
        assertEquals("SKILL_CONSUME", operation.getValue().getOperationType());
        assertEquals("SERVICE", operation.getValue().getIssuerType());
        assertEquals(ENTRY_ID, entry.getValue().getEntryId());
        assertEquals(1L, entry.getValue().getSequenceNo());
        InOrder lockAndMutationOrder = inOrder(mapper, usageMapper);
        lockAndMutationOrder.verify(mapper).selectUserProjectionForUpdate(7L);
        lockAndMutationOrder.verify(mapper).countLegacyCreditAccounts(7L);
        lockAndMutationOrder.verify(usageMapper).selectByRecordIdForUpdate("usage-001");
        lockAndMutationOrder.verify(usageMapper).insertUsageRecord(any());
        lockAndMutationOrder.verify(usageMapper).selectByRecordIdForUpdate("usage-001");
        lockAndMutationOrder.verify(mapper).selectAccountForUpdate(
                7L, "USER_GLOBAL", "FBS_POINTS");
        lockAndMutationOrder.verify(mapper).insertAccount(any());
        lockAndMutationOrder.verify(mapper).insertBridge(any());
        lockAndMutationOrder.verify(mapper).insertOperation(any());
        lockAndMutationOrder.verify(mapper).insertEntry(any());
        lockAndMutationOrder.verify(mapper).updateAccountIfVersion(
                any(), anyLong(), anyLong(), anyLong(), anyString());
        lockAndMutationOrder.verify(mapper).updateBridgeIfVersion(any(), anyLong(), anyLong());
        lockAndMutationOrder.verify(mapper).updateUserProjectionIfBalance(
                7L, 100L, 75L);
        lockAndMutationOrder.verify(usageMapper).updateStatusByRecordId(
                "usage-001", UsageStatus.SUCCESS.getCode(), null);
        verify(mapper).updateAccountIfVersion(
                any(), eq(0L), eq(0L), eq(100L), eq("0".repeat(64)));
        verify(mapper).updateBridgeIfVersion(any(), eq(0L), eq(100L));
        verify(mapper).updateUserProjectionIfBalance(7L, 100L, 75L);
        verify(usageMapper).updateStatusByRecordId(
                "usage-001", UsageStatus.SUCCESS.getCode(), null);
    }

    @Test
    void insufficientBalanceFailsBeforeAnyV2OperationOrUsageTerminalMutation() {
        BoardCreditAccount account = account(20, 0, "0".repeat(64));
        account.setOpeningBalance(20L);
        when(mapper.selectUserProjectionForUpdate(7L)).thenReturn(user(20));
        when(mapper.countLegacyCreditAccounts(7L)).thenReturn(0);
        when(usageMapper.selectByRecordIdForUpdate("usage-001"))
                .thenReturn(usage(UsageStatus.IN_PROGRESS.getCode()));
        when(mapper.selectAccountForUpdate(anyLong(), anyString(), anyString())).thenReturn(account);
        when(mapper.selectBridgeForUpdate(anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(bridge(20, 0));

        ServiceException error = assertThrows(ServiceException.class,
                () -> transaction.consumeFresh(command(), "host-session-secret"));

        assertEquals("SKILL_CREDIT_LEDGER_INSUFFICIENT_BALANCE", error.getMessage());
        verify(mapper, never()).insertOperation(any());
        verify(mapper, never()).insertEntry(any());
        verify(mapper, never()).updateUserProjectionIfBalance(anyLong(), anyLong(), anyLong());
        verify(usageMapper, never()).updateStatusByRecordId(anyString(), any(), any());
    }

    @Test
    void usageTerminalCasLoserEscapesTheFreshTransactionInsteadOfReportingSuccess() {
        when(mapper.selectUserProjectionForUpdate(7L)).thenReturn(user(100));
        when(mapper.countLegacyCreditAccounts(7L)).thenReturn(0);
        when(usageMapper.selectByRecordIdForUpdate("usage-001"))
                .thenReturn(null, usage(UsageStatus.IN_PROGRESS.getCode()));
        when(usageMapper.insertUsageRecord(any())).thenReturn(1);
        when(mapper.insertAccount(any())).thenReturn(1);
        when(mapper.insertBridge(any())).thenReturn(1);
        when(mapper.insertOperation(any())).thenReturn(1);
        when(mapper.insertEntry(any())).thenReturn(1);
        when(mapper.updateAccountIfVersion(any(), anyLong(), anyLong(), anyLong(), anyString()))
                .thenReturn(1);
        when(mapper.updateBridgeIfVersion(any(), anyLong(), anyLong())).thenReturn(1);
        when(mapper.updateUserProjectionIfBalance(7L, 100L, 75L)).thenReturn(1);
        when(usageMapper.updateStatusByRecordId(
                eq("usage-001"), eq(UsageStatus.SUCCESS.getCode()), isNull()))
                .thenReturn(0);

        ServiceException error = assertThrows(ServiceException.class,
                () -> transaction.consumeFresh(command(), "host-session-secret"));

        assertEquals("SKILL_USAGE_RECORD_TERMINAL_CAS_CONFLICT", error.getMessage());
    }

    private SkillConsumeCreditCommand command() {
        return SkillConsumeCreditCommand.create(
                7L, "usage-001", 9L, "1.2.3", "skill-code", "rule-code", 25,
                "WORKBUDDY", "host-session-secret");
    }

    private BoardCreditUserProjection user(int points) {
        BoardCreditUserProjection user = new BoardCreditUserProjection();
        user.setUserId(7L);
        user.setPoints(points);
        user.setStatus("0");
        user.setDelFlag("0");
        return user;
    }

    private FbsSkillUsageRecord usage(int status) {
        FbsSkillUsageRecord usage = new FbsSkillUsageRecord();
        usage.setUsageRecordId("usage-001");
        usage.setUserId(7L);
        usage.setPackId(9L);
        usage.setPackVersion("1.2.3");
        usage.setSkillCode("skill-code");
        usage.setHostType("WORKBUDDY");
        usage.setHostSessionId("host-session-secret");
        usage.setPointsAmount(25);
        usage.setStatus(status);
        return usage;
    }

    private BoardCreditAccount account(long balance, long version, String lastHash) {
        BoardCreditAccount account = new BoardCreditAccount();
        account.setAccountId(ACCOUNT_ID);
        account.setUserId(7L);
        account.setAccountScope("USER_GLOBAL");
        account.setCurrencyCode("FBS_POINTS");
        account.setOpeningBalance(100L);
        account.setBalance(balance);
        account.setVersion(version);
        account.setLastEntrySequence(version);
        account.setLastEntryHash(lastHash);
        account.setCreatedAt(Date.from(NOW));
        return account;
    }

    private SkillCreditProjectionBridge bridge(long balance, long version) {
        SkillCreditProjectionBridge bridge = new SkillCreditProjectionBridge();
        bridge.setAccountId(ACCOUNT_ID);
        bridge.setUserId(7L);
        bridge.setAccountScope("USER_GLOBAL");
        bridge.setCurrencyCode("FBS_POINTS");
        bridge.setProjectedBalance(balance);
        bridge.setProjectionVersion(version);
        bridge.setCreatedAt(Date.from(NOW));
        return bridge;
    }
}
