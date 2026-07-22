package com.wx.fbsir.business.board.credit.service;

import com.wx.fbsir.business.board.credit.domain.BoardCreditAccount;
import com.wx.fbsir.business.board.credit.domain.BoardCreditAuditRow;
import com.wx.fbsir.business.board.credit.domain.BoardCreditChainProof;
import com.wx.fbsir.business.board.credit.domain.BoardCreditEntry;
import com.wx.fbsir.business.board.credit.domain.BoardCreditOperation;
import com.wx.fbsir.business.board.credit.domain.BoardCreditUserProjection;
import com.wx.fbsir.business.board.credit.dto.BoardCreditCommandResult;
import com.wx.fbsir.business.board.credit.dto.BoardCreditAuditEnvelope;
import com.wx.fbsir.business.board.credit.dto.BoardCreditGrantRequest;
import com.wx.fbsir.business.board.credit.dto.BoardCreditReversalRequest;
import com.wx.fbsir.business.board.credit.mapper.IndependentBoardCreditMapper;
import com.wx.fbsir.common.exception.ServiceException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Queue;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IndependentBoardCreditServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-22T01:00:00Z");
    private static final String ACCOUNT_ID = "023e4567-e89b-12d3-a456-426614174000";
    private static final String OPERATION_ID = "123e4567-e89b-12d3-a456-426614174000";
    private static final String ENTRY_ID = "223e4567-e89b-12d3-a456-426614174000";
    private static final String REVERSAL_ID = "323e4567-e89b-12d3-a456-426614174000";
    private static final String REVERSAL_ENTRY_ID = "423e4567-e89b-12d3-a456-426614174000";
    private IndependentBoardCreditMapper mapper;
    private IndependentBoardCreditService service;

    @BeforeEach
    void setUp() {
        mapper = mock(IndependentBoardCreditMapper.class);
        service = serviceWithIds(ACCOUNT_ID, OPERATION_ID, ENTRY_ID, REVERSAL_ID, REVERSAL_ENTRY_ID);
    }

    @Test
    void firstGrantOpensFromLockedLegacyProjectionAndCommitsOneHashChainedEntry() {
        when(mapper.selectUserProjectionForUpdate(42L)).thenReturn(activeUser(42L, 500));
        when(mapper.selectAccountForUpdate(
                42L, IndependentBoardCreditService.ACCOUNT_SCOPE,
                IndependentBoardCreditService.CURRENCY_CODE)).thenReturn(null);
        when(mapper.insertAccount(any())).thenReturn(1);
        when(mapper.insertOperation(any())).thenReturn(1);
        when(mapper.updateAccountIfVersion(
                any(), anyLong(), anyLong(), anyLong(), anyString())).thenReturn(1);
        when(mapper.updateUserProjectionIfBalance(42L, 500L, 600L)).thenReturn(1);
        when(mapper.insertEntry(any())).thenReturn(1);

        BoardCreditCommandResult result = service.grant(grant(42L, 100), 900L);

        assertEquals(OPERATION_ID, result.operationId());
        assertEquals("GRANT", result.operationType());
        assertEquals(42L, result.userId());
        assertEquals(100L, result.delta());
        assertEquals(600L, result.balanceAfter());
        ArgumentCaptor<BoardCreditAccount> account = ArgumentCaptor.forClass(BoardCreditAccount.class);
        verify(mapper).insertAccount(account.capture());
        assertEquals(ACCOUNT_ID, account.getValue().getAccountId());
        assertEquals(500L, account.getValue().getOpeningBalance());
        assertEquals(500L, account.getValue().getBalance());
        assertEquals(0L, account.getValue().getVersion());
        assertEquals(0L, account.getValue().getLastEntrySequence());
        assertEquals("0".repeat(64), account.getValue().getLastEntryHash());

        ArgumentCaptor<BoardCreditOperation> operation =
                ArgumentCaptor.forClass(BoardCreditOperation.class);
        ArgumentCaptor<BoardCreditEntry> entry = ArgumentCaptor.forClass(BoardCreditEntry.class);
        verify(mapper).insertOperation(operation.capture());
        verify(mapper).insertEntry(entry.capture());
        assertEquals("USER_GLOBAL", operation.getValue().getAccountScope());
        assertEquals("FBS_POINTS", operation.getValue().getCurrencyCode());
        assertEquals("CUSTOMER_SUPPORT", operation.getValue().getReasonCode());
        assertEquals(900L, operation.getValue().getActorUserId());
        assertEquals(500L, operation.getValue().getBalanceBefore());
        assertEquals(600L, operation.getValue().getBalanceAfter());
        assertEquals(operation.getValue().getRequestDigest(), entry.getValue().getRequestDigest());
        assertEquals("0".repeat(64), entry.getValue().getPreviousEntryHash());
        assertEquals(1L, entry.getValue().getSequenceNo());
        assertNotNull(entry.getValue().getEntryHash());
        assertEquals(64, entry.getValue().getEntryHash().length());
        assertNotEquals(entry.getValue().getPreviousEntryHash(), entry.getValue().getEntryHash());
    }

    @Test
    void existingAccountProjectionDriftFailsBeforeAnyOperationClaim() {
        when(mapper.selectUserProjectionForUpdate(42L)).thenReturn(activeUser(42L, 501));
        when(mapper.selectAccountForUpdate(anyLong(), anyString(), anyString()))
                .thenReturn(account(42L, 500L, 3L, "a".repeat(64)));

        ServiceException error = assertThrows(
                ServiceException.class, () -> service.grant(grant(42L, 100), 900L));

        assertEquals(409, error.getCode());
        assertEquals("CREDIT_PROJECTION_DRIFT", error.getMessage());
        verify(mapper, never()).insertOperation(any());
        verify(mapper, never()).updateAccountIfVersion(
                any(), anyLong(), anyLong(), anyLong(), anyString());
        verify(mapper, never()).updateUserProjectionIfBalance(anyLong(), anyLong(), anyLong());
    }

    @Test
    void accountCasConflictCannotReachLegacyProjectionOrEntryWrite() {
        when(mapper.selectUserProjectionForUpdate(42L)).thenReturn(activeUser(42L, 500));
        when(mapper.selectAccountForUpdate(anyLong(), anyString(), anyString()))
                .thenReturn(account(42L, 500L, 3L, "a".repeat(64)));
        when(mapper.insertOperation(any())).thenReturn(1);
        when(mapper.insertEntry(any())).thenReturn(1);
        when(mapper.updateAccountIfVersion(
                any(), anyLong(), anyLong(), anyLong(), anyString())).thenReturn(0);

        ServiceException error = assertThrows(
                ServiceException.class, () -> service.grant(grant(42L, 100), 900L));

        assertEquals("CREDIT_ACCOUNT_VERSION_CONFLICT", error.getMessage());
        verify(mapper, never()).updateUserProjectionIfBalance(anyLong(), anyLong(), anyLong());
        verify(mapper).insertEntry(any());
    }

    @Test
    void duplicateGrantRollsBackFreshClaimThenReturnsExactCommittedReplay() {
        BoardCreditAccount account = account(42L, 600L, 4L, "b".repeat(64));
        BoardCreditOperation stored = grantOperation();
        BoardCreditEntry storedEntry = entryFor(stored, 4L, "a".repeat(64));
        when(mapper.selectUserProjectionForUpdate(42L)).thenReturn(activeUser(42L, 600));
        when(mapper.selectAccountForUpdate(anyLong(), anyString(), anyString())).thenReturn(account);
        when(mapper.insertOperation(any())).thenThrow(new DuplicateKeyException("duplicate"));
        when(mapper.selectOperationByIdempotencyKey("grant:20260722:0001"))
                .thenReturn(null)
                .thenReturn(stored);
        when(mapper.selectEntryByOperationId(OPERATION_ID)).thenReturn(storedEntry);

        BoardCreditCommandResult result = service.grant(grant(42L, 100), 900L);

        assertEquals(OPERATION_ID, result.operationId());
        assertEquals(600L, result.balanceAfter());
        verify(mapper, never()).updateAccountIfVersion(
                any(), anyLong(), anyLong(), anyLong(), anyString());
        verify(mapper, never()).updateUserProjectionIfBalance(anyLong(), anyLong(), anyLong());
        verify(mapper, never()).insertEntry(any());
    }

    @Test
    void exactGrantReplayDoesNotDependOnMutableCurrentUserState() {
        BoardCreditOperation stored = grantOperation();
        BoardCreditEntry storedEntry = entryFor(stored, 4L, "a".repeat(64));
        when(mapper.selectOperationByIdempotencyKey("grant:20260722:0001"))
                .thenReturn(stored);
        when(mapper.selectEntryByOperationId(OPERATION_ID)).thenReturn(storedEntry);

        BoardCreditCommandResult result = service.grant(grant(42L, 100), 900L);

        assertEquals(OPERATION_ID, result.operationId());
        assertEquals(600L, result.balanceAfter());
        verify(mapper, never()).selectUserProjectionForUpdate(anyLong());
        verify(mapper, never()).selectAccountForUpdate(anyLong(), anyString(), anyString());
        verify(mapper, never()).insertOperation(any());
    }

    @Test
    void sameGrantKeyWithDifferentDigestFailsClosedWithoutFinancialMutation() {
        BoardCreditOperation stored = grantOperation();
        stored.setRequestDigest("f".repeat(64));
        when(mapper.selectOperationByIdempotencyKey("grant:20260722:0001"))
                .thenReturn(stored);

        ServiceException error = assertThrows(
                ServiceException.class, () -> service.grant(grant(42L, 101), 900L));

        assertEquals(409, error.getCode());
        assertEquals("CREDIT_IDEMPOTENCY_DIGEST_CONFLICT", error.getMessage());
        verify(mapper, never()).selectUserProjectionForUpdate(anyLong());
        verify(mapper, never()).selectAccountForUpdate(anyLong(), anyString(), anyString());
        verify(mapper, never()).insertOperation(any());
        verify(mapper, never()).insertEntry(any());
    }

    @Test
    void exactReversalReplayDoesNotDependOnMutableCurrentAccountState() {
        BoardCreditOperation original = grantOperation();
        BoardCreditOperation stored = reversalOperation();
        BoardCreditEntry originalEntry = entryFor(original, 4L, "a".repeat(64));
        BoardCreditEntry reversalEntry = entryFor(stored, 5L, originalEntry.getEntryHash());
        reversalEntry.setEntryId(REVERSAL_ENTRY_ID);
        reversalEntry.setEntryHash(BoardCreditDigest.entryHash(reversalEntry));
        when(mapper.selectOperationByIdempotencyKey("reverse:20260722:0001"))
                .thenReturn(stored);
        when(mapper.selectOperationByOperationId(OPERATION_ID)).thenReturn(original);
        when(mapper.selectEntryByOperationId(OPERATION_ID)).thenReturn(originalEntry);
        when(mapper.selectEntryByOperationId(REVERSAL_ID)).thenReturn(reversalEntry);

        BoardCreditCommandResult result = service.reverse(reversal(), 901L);

        assertEquals(REVERSAL_ID, result.operationId());
        assertEquals(500L, result.balanceAfter());
        verify(mapper, never()).selectUserProjectionForUpdate(anyLong());
        verify(mapper, never()).selectAccountForUpdate(anyLong(), anyString(), anyString());
        verify(mapper, never()).insertOperation(any());
    }

    @Test
    void reversalDerivesTargetAndNegativeDeltaOnlyFromOriginalGrant() {
        BoardCreditOperation original = grantOperation();
        BoardCreditAccount current = account(42L, 600L, 4L, "b".repeat(64));
        BoardCreditEntry originalEntry = entryFor(original, 4L, "a".repeat(64));
        current.setLastEntryHash(originalEntry.getEntryHash());
        when(mapper.selectOperationByOperationId(OPERATION_ID)).thenReturn(original);
        when(mapper.selectUserProjectionForUpdate(42L)).thenReturn(activeUser(42L, 600));
        when(mapper.selectAccountForUpdate(anyLong(), anyString(), anyString())).thenReturn(current);
        when(mapper.selectOperationByOperationIdForUpdate(OPERATION_ID)).thenReturn(original);
        when(mapper.selectEntryByOperationId(OPERATION_ID)).thenReturn(originalEntry);
        when(mapper.selectCommittedChainProof(ACCOUNT_ID, 4L, 4L))
                .thenReturn(chainProof(4L, 4L, 1L, originalEntry.getEntryHash(), 600L));
        when(mapper.insertOperation(any())).thenReturn(1);
        when(mapper.updateAccountIfVersion(
                any(), anyLong(), anyLong(), anyLong(), anyString())).thenReturn(1);
        when(mapper.updateUserProjectionIfBalance(42L, 600L, 500L)).thenReturn(1);
        when(mapper.insertEntry(any())).thenReturn(1);

        BoardCreditCommandResult result = service.reverse(reversal(), 901L);

        assertEquals("REVERSAL", result.operationType());
        assertEquals(-100L, result.delta());
        assertEquals(500L, result.balanceAfter());
        assertEquals(OPERATION_ID, result.reversalOfOperationId());
        ArgumentCaptor<BoardCreditOperation> operation =
                ArgumentCaptor.forClass(BoardCreditOperation.class);
        verify(mapper).insertOperation(operation.capture());
        assertEquals(-original.getDelta(), operation.getValue().getDelta());
        assertEquals(original.getUserId(), operation.getValue().getUserId());
        assertEquals(original.getAccountId(), operation.getValue().getAccountId());
        assertEquals("OPERATOR_ERROR", operation.getValue().getReasonCode());
        assertEquals(901L, operation.getValue().getActorUserId());
    }

    @Test
    void reversalCannotOverdrawTheCurrentAccount() {
        BoardCreditOperation original = grantOperation();
        BoardCreditEntry originalEntry = entryFor(original, 4L, "a".repeat(64));
        when(mapper.selectOperationByOperationId(OPERATION_ID)).thenReturn(original);
        when(mapper.selectUserProjectionForUpdate(42L)).thenReturn(activeUser(42L, 50));
        when(mapper.selectAccountForUpdate(anyLong(), anyString(), anyString()))
                .thenReturn(account(42L, 50L, 5L, "c".repeat(64)));
        when(mapper.selectOperationByOperationIdForUpdate(OPERATION_ID)).thenReturn(original);
        when(mapper.selectEntryByOperationId(OPERATION_ID)).thenReturn(originalEntry);
        when(mapper.selectCommittedChainProof(ACCOUNT_ID, 4L, 5L))
                .thenReturn(chainProof(4L, 5L, 2L, "c".repeat(64), 50L));

        ServiceException error = assertThrows(
                ServiceException.class, () -> service.reverse(reversal(), 901L));

        assertEquals(409, error.getCode());
        assertEquals("CREDIT_REVERSAL_INSUFFICIENT_BALANCE", error.getMessage());
        verify(mapper, never()).insertOperation(any());
    }

    @Test
    void reversalRejectsAnOriginalGrantWhoseImmutableEntryIsMissing() {
        BoardCreditOperation original = grantOperation();
        when(mapper.selectOperationByOperationId(OPERATION_ID)).thenReturn(original);
        when(mapper.selectUserProjectionForUpdate(42L)).thenReturn(activeUser(42L, 600));
        when(mapper.selectAccountForUpdate(anyLong(), anyString(), anyString()))
                .thenReturn(account(42L, 600L, 4L, "b".repeat(64)));
        when(mapper.selectOperationByOperationIdForUpdate(OPERATION_ID)).thenReturn(original);
        when(mapper.selectEntryByOperationId(OPERATION_ID)).thenReturn(null);

        ServiceException error = assertThrows(
                ServiceException.class, () -> service.reverse(reversal(), 901L));

        assertEquals(500, error.getCode());
        assertEquals("CREDIT_ENTRY_CONTRACT_DRIFT", error.getMessage());
        verify(mapper, never()).insertOperation(any());
    }

    @Test
    void reversalRejectsAnOriginalGrantWhoseStoredDigestDoesNotMatchItsSemantics() {
        BoardCreditOperation original = grantOperation();
        original.setRequestDigest("f".repeat(64));
        when(mapper.selectOperationByOperationId(OPERATION_ID)).thenReturn(original);

        ServiceException error = assertThrows(
                ServiceException.class, () -> service.reverse(reversal(), 901L));

        assertEquals(500, error.getCode());
        assertEquals("CREDIT_ORIGINAL_OPERATION_DIGEST_DRIFT", error.getMessage());
        verify(mapper, never()).selectUserProjectionForUpdate(anyLong());
        verify(mapper, never()).insertOperation(any());
    }

    @Test
    void replayReversalRejectsSameKeyDifferentOriginalBeforeLookingUpThatObject() {
        String differentOriginal = "923e4567-e89b-12d3-a456-426614174000";
        BoardCreditOperation stored = reversalOperation();
        when(mapper.selectOperationByIdempotencyKey("reverse:20260722:0001"))
                .thenReturn(stored);
        IndependentBoardCreditTransactionService transaction =
                new IndependentBoardCreditTransactionService(
                        mapper, Clock.fixed(NOW, ZoneOffset.UTC), () -> REVERSAL_ID);
        BoardCreditReversalRequest conflicting = new BoardCreditReversalRequest(
                differentOriginal, "OPERATOR_ERROR", "operator correction",
                "reverse:20260722:0001");

        ServiceException error = assertThrows(
                ServiceException.class,
                () -> transaction.replayReversal(conflicting, 901L));

        assertEquals(409, error.getCode());
        assertEquals("CREDIT_IDEMPOTENCY_DIGEST_CONFLICT", error.getMessage());
        verify(mapper, never()).selectOperationByOperationId(differentOriginal);
    }

    @Test
    void rawDatabaseFailuresAreMappedWithoutLeakingSqlOrTableNames() {
        when(mapper.selectUserProjectionForUpdate(42L)).thenThrow(
                new DataIntegrityViolationException(
                        "SQL update fbs_credit_operation violated secret constraint"));

        ServiceException error = assertThrows(
                ServiceException.class, () -> service.grant(grant(42L, 100), 900L));

        assertEquals(500, error.getCode());
        assertEquals("CREDIT_LEDGER_PERSISTENCE_FAILED", error.getMessage());
        assertFalse(error.getMessage().contains("SQL"));
        assertFalse(error.getMessage().contains("fbs_credit"));
    }

    @Test
    void auditReturnsStableNotFoundWhenTheUserHasNoCreditAccount() {
        when(mapper.selectAccount(
                42L, IndependentBoardCreditService.ACCOUNT_SCOPE,
                IndependentBoardCreditService.CURRENCY_CODE)).thenReturn(null);

        ServiceException error = assertThrows(
                ServiceException.class, () -> service.audit(42L));

        assertEquals(404, error.getCode());
        assertEquals("CREDIT_ACCOUNT_NOT_FOUND", error.getMessage());
        verify(mapper, never()).selectAuditRowsByAccountId(anyString());
    }

    @Test
    void auditRejectsASequenceGapEvenWhenTheStoredHashesLink() {
        BoardCreditAccount account = account(42L, 700L, 3L, "0".repeat(64));
        BoardCreditAuditRow oldest = auditRow(
                "523e4567-e89b-12d3-a456-426614174000",
                "623e4567-e89b-12d3-a456-426614174000",
                1L, 500L, 600L, "0".repeat(64));
        BoardCreditAuditRow newest = auditRow(
                "723e4567-e89b-12d3-a456-426614174000",
                "823e4567-e89b-12d3-a456-426614174000",
                3L, 600L, 700L, oldest.getEntryHash());
        account.setLastEntryHash(newest.getEntryHash());
        when(mapper.selectAccount(
                42L, IndependentBoardCreditService.ACCOUNT_SCOPE,
                IndependentBoardCreditService.CURRENCY_CODE)).thenReturn(account);
        when(mapper.selectAuditRowsByAccountId(ACCOUNT_ID))
                .thenReturn(List.of(newest, oldest));

        ServiceException error = assertThrows(
                ServiceException.class, () -> service.audit(42L));

        assertEquals(500, error.getCode());
        assertEquals("CREDIT_AUDIT_CHAIN_DRIFT", error.getMessage());
    }

    @Test
    void auditRecomputesThePersistedCommandDigestAndReturnsTheVerifiedHead() {
        BoardCreditAuditRow row = auditRow(
                "523e4567-e89b-12d3-a456-426614174000",
                "623e4567-e89b-12d3-a456-426614174000",
                1L, 500L, 600L, "0".repeat(64));
        BoardCreditAccount account = account(42L, 600L, 1L, row.getEntryHash());
        when(mapper.selectAccount(
                42L, IndependentBoardCreditService.ACCOUNT_SCOPE,
                IndependentBoardCreditService.CURRENCY_CODE)).thenReturn(account);
        when(mapper.selectAuditRowsByAccountId(ACCOUNT_ID)).thenReturn(List.of(row));

        BoardCreditAuditEnvelope result = service.audit(42L);

        assertEquals(600L, result.balance());
        assertEquals(row.getEntryHash(), result.lastEntryHash());
        assertEquals(1, result.records().size());
        assertFalse(result.truncated());
    }

    @Test
    void auditRejectsActorDriftAgainstThePersistedRequestDigest() {
        BoardCreditAuditRow row = auditRow(
                "523e4567-e89b-12d3-a456-426614174000",
                "623e4567-e89b-12d3-a456-426614174000",
                1L, 500L, 600L, "0".repeat(64));
        row.setActorUserId(901L);
        BoardCreditAccount account = account(42L, 600L, 1L, row.getEntryHash());
        when(mapper.selectAccount(
                42L, IndependentBoardCreditService.ACCOUNT_SCOPE,
                IndependentBoardCreditService.CURRENCY_CODE)).thenReturn(account);
        when(mapper.selectAuditRowsByAccountId(ACCOUNT_ID)).thenReturn(List.of(row));

        ServiceException error = assertThrows(
                ServiceException.class, () -> service.audit(42L));

        assertEquals(500, error.getCode());
        assertEquals("CREDIT_AUDIT_ROW_DRIFT", error.getMessage());
    }

    private IndependentBoardCreditService serviceWithIds(String... ids) {
        Queue<String> queue = new ArrayDeque<>(java.util.List.of(ids));
        Supplier<String> generator = () -> {
            String value = queue.poll();
            if (value == null) {
                throw new AssertionError("unexpected ID generation");
            }
            return value;
        };
        return new IndependentBoardCreditService(
                mapper, Clock.fixed(NOW, ZoneOffset.UTC), generator);
    }

    private BoardCreditGrantRequest grant(Long userId, int amount) {
        return new BoardCreditGrantRequest(
                userId, amount, "CUSTOMER_SUPPORT", "approved support grant",
                "grant:20260722:0001");
    }

    private BoardCreditReversalRequest reversal() {
        return new BoardCreditReversalRequest(
                OPERATION_ID, "OPERATOR_ERROR", "operator correction",
                "reverse:20260722:0001");
    }

    private BoardCreditUserProjection activeUser(Long userId, int points) {
        BoardCreditUserProjection user = new BoardCreditUserProjection();
        user.setUserId(userId);
        user.setPoints(points);
        user.setStatus("0");
        user.setDelFlag("0");
        return user;
    }

    private BoardCreditAccount account(Long userId, long balance, long version, String lastHash) {
        BoardCreditAccount account = new BoardCreditAccount();
        account.setAccountId(ACCOUNT_ID);
        account.setUserId(userId);
        account.setAccountScope(IndependentBoardCreditService.ACCOUNT_SCOPE);
        account.setCurrencyCode(IndependentBoardCreditService.CURRENCY_CODE);
        account.setOpeningBalance(500L);
        account.setBalance(balance);
        account.setVersion(version);
        account.setLastEntrySequence(version);
        account.setLastEntryHash(lastHash);
        account.setCreatedAt(Date.from(NOW.minusSeconds(60)));
        account.setUpdatedAt(Date.from(NOW));
        return account;
    }

    private BoardCreditOperation grantOperation() {
        BoardCreditOperation operation = new BoardCreditOperation();
        operation.setOperationId(OPERATION_ID);
        operation.setIdempotencyKey("grant:20260722:0001");
        operation.setRequestDigest(BoardCreditDigest.grantDigest(grant(42L, 100), 900L));
        operation.setAccountId(ACCOUNT_ID);
        operation.setUserId(42L);
        operation.setAccountScope(IndependentBoardCreditService.ACCOUNT_SCOPE);
        operation.setCurrencyCode(IndependentBoardCreditService.CURRENCY_CODE);
        operation.setOperationType("GRANT");
        operation.setDelta(100L);
        operation.setReasonCode("CUSTOMER_SUPPORT");
        operation.setReasonNote("approved support grant");
        operation.setActorUserId(900L);
        operation.setBalanceBefore(500L);
        operation.setBalanceAfter(600L);
        operation.setCreatedAt(Date.from(NOW));
        return operation;
    }

    private BoardCreditOperation reversalOperation() {
        BoardCreditOperation operation = new BoardCreditOperation();
        operation.setOperationId(REVERSAL_ID);
        operation.setIdempotencyKey("reverse:20260722:0001");
        operation.setRequestDigest(BoardCreditDigest.reversalDigest(
                reversal(), 901L, 42L, -100L));
        operation.setAccountId(ACCOUNT_ID);
        operation.setUserId(42L);
        operation.setAccountScope(IndependentBoardCreditService.ACCOUNT_SCOPE);
        operation.setCurrencyCode(IndependentBoardCreditService.CURRENCY_CODE);
        operation.setOperationType("REVERSAL");
        operation.setDelta(-100L);
        operation.setReasonCode("OPERATOR_ERROR");
        operation.setReasonNote("operator correction");
        operation.setActorUserId(901L);
        operation.setReversalOfOperationId(OPERATION_ID);
        operation.setBalanceBefore(600L);
        operation.setBalanceAfter(500L);
        operation.setCreatedAt(Date.from(NOW));
        return operation;
    }

    private BoardCreditEntry entryFor(
            BoardCreditOperation operation, long sequence, String previousHash) {
        BoardCreditEntry entry = new BoardCreditEntry();
        entry.setEntryId(ENTRY_ID);
        entry.setOperationId(operation.getOperationId());
        entry.setRequestDigest(operation.getRequestDigest());
        entry.setAccountId(operation.getAccountId());
        entry.setSequenceNo(sequence);
        entry.setDelta(operation.getDelta());
        entry.setBalanceBefore(operation.getBalanceBefore());
        entry.setBalanceAfter(operation.getBalanceAfter());
        entry.setPreviousEntryHash(previousHash);
        entry.setCreatedAt(operation.getCreatedAt());
        entry.setEntryHash(BoardCreditDigest.entryHash(entry));
        return entry;
    }

    private BoardCreditAuditRow auditRow(
            String operationId,
            String entryId,
            long sequence,
            long balanceBefore,
            long balanceAfter,
            String previousHash) {
        BoardCreditAuditRow row = new BoardCreditAuditRow();
        row.setOperationId(operationId);
        row.setEntryId(entryId);
        String idempotencyKey = "audit:20260722:" + sequence;
        row.setIdempotencyKey(idempotencyKey);
        row.setAccountId(ACCOUNT_ID);
        row.setUserId(42L);
        row.setAccountScope(IndependentBoardCreditService.ACCOUNT_SCOPE);
        row.setCurrencyCode(IndependentBoardCreditService.CURRENCY_CODE);
        row.setOperationType("GRANT");
        row.setDelta(balanceAfter - balanceBefore);
        row.setReasonCode("CUSTOMER_SUPPORT");
        row.setReasonNote("approved support grant");
        row.setActorUserId(900L);
        row.setBalanceBefore(balanceBefore);
        row.setBalanceAfter(balanceAfter);
        row.setSequenceNo(sequence);
        row.setPreviousEntryHash(previousHash);
        row.setCreatedAt(Date.from(NOW.plusSeconds(sequence)));
        row.setRequestDigest(BoardCreditDigest.grantDigest(
                new BoardCreditGrantRequest(
                        42L, (int) (balanceAfter - balanceBefore),
                        row.getReasonCode(), row.getReasonNote(), idempotencyKey),
                row.getActorUserId()));
        row.setEntryRequestDigest(row.getRequestDigest());
        row.setEntryAccountId(ACCOUNT_ID);
        row.setEntryDelta(row.getDelta());
        row.setEntryBalanceBefore(balanceBefore);
        row.setEntryBalanceAfter(balanceAfter);
        row.setEntryCreatedAt(row.getCreatedAt());
        BoardCreditEntry entry = new BoardCreditEntry();
        entry.setEntryId(entryId);
        entry.setOperationId(operationId);
        entry.setRequestDigest(row.getRequestDigest());
        entry.setAccountId(ACCOUNT_ID);
        entry.setSequenceNo(sequence);
        entry.setDelta(row.getDelta());
        entry.setBalanceBefore(balanceBefore);
        entry.setBalanceAfter(balanceAfter);
        entry.setPreviousEntryHash(previousHash);
        entry.setCreatedAt(row.getCreatedAt());
        entry.setEntryHash(BoardCreditDigest.entryHash(entry));
        row.setEntryHash(entry.getEntryHash());
        return row;
    }

    private BoardCreditChainProof chainProof(
            long fromSequence,
            long toSequence,
            long count,
            String headHash,
            long headBalance) {
        BoardCreditChainProof proof = new BoardCreditChainProof();
        proof.setEntryCount(count);
        proof.setValidTransitionCount(count);
        proof.setMinSequence(fromSequence);
        proof.setMaxSequence(toSequence);
        proof.setHeadHash(headHash);
        proof.setHeadBalance(headBalance);
        return proof;
    }
}
