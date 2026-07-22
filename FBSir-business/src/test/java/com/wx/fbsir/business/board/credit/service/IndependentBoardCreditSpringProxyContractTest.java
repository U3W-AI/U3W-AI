package com.wx.fbsir.business.board.credit.service;

import com.wx.fbsir.business.board.credit.domain.BoardCreditAccount;
import com.wx.fbsir.business.board.credit.domain.BoardCreditEntry;
import com.wx.fbsir.business.board.credit.domain.BoardCreditOperation;
import com.wx.fbsir.business.board.credit.domain.BoardCreditUserProjection;
import com.wx.fbsir.business.board.credit.dto.BoardCreditCommandResult;
import com.wx.fbsir.business.board.credit.dto.BoardCreditGrantRequest;
import com.wx.fbsir.business.board.credit.mapper.IndependentBoardCreditMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IndependentBoardCreditSpringProxyContractTest {
    private static final String ACCOUNT_ID = "023e4567-e89b-12d3-a456-426614174000";
    private static final String OPERATION_ID = "123e4567-e89b-12d3-a456-426614174000";
    private static final String ENTRY_ID = "223e4567-e89b-12d3-a456-426614174000";
    private static final Date CREATED_AT = Date.from(Instant.parse("2026-07-22T01:00:00Z"));
    private AnnotationConfigApplicationContext context;

    @BeforeEach
    void openContext() {
        context = new AnnotationConfigApplicationContext(ConfigurationUnderTest.class);
    }

    @AfterEach
    void closeContext() {
        if (context != null) {
            context.close();
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(null);
    }

    @Test
    void duplicateFreshClaimRollsBackBeforeRequiresNewReplayCommits() {
        IndependentBoardCreditMapper mapper = context.getBean(IndependentBoardCreditMapper.class);
        IndependentBoardCreditTransactionService runner =
                context.getBean(IndependentBoardCreditTransactionService.class);
        IndependentBoardCreditService service = context.getBean(IndependentBoardCreditService.class);
        RecordingManager manager = context.getBean(RecordingManager.class);
        BoardCreditGrantRequest request = new BoardCreditGrantRequest(
                42L, 1L, 100, "CUSTOMER_SUPPORT", "approved support grant",
                "grant:20260722:0001");
        BoardCreditOperation stored = storedGrant(request);
        BoardCreditEntry entry = storedEntry(stored);
        when(mapper.selectUserProjectionForUpdate(42L)).thenReturn(activeUser());
        when(mapper.selectAccountForUpdate(
                42L, IndependentBoardCreditService.ACCOUNT_SCOPE,
                IndependentBoardCreditService.CURRENCY_CODE)).thenReturn(account());
        when(mapper.insertOperation(any())).thenThrow(new DuplicateKeyException("duplicate"));
        when(mapper.selectOperationByIdempotencyKey(request.idempotencyKey()))
                .thenReturn(null)
                .thenAnswer(invocation -> {
                    assertEquals(
                            List.of("begin", "commit", "begin", "rollback", "begin"),
                            manager.events);
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                    return stored;
                });
        when(mapper.selectEntryByOperationId(OPERATION_ID)).thenReturn(entry);

        BoardCreditCommandResult result = service.grant(request, 900L);

        assertEquals(OPERATION_ID, result.operationId());
        assertEquals(
                List.of("begin", "commit", "begin", "rollback", "begin", "commit"),
                manager.events);
        assertTrue(AopUtils.isAopProxy(runner));
        assertFalse(AopUtils.isAopProxy(service));
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    private static BoardCreditUserProjection activeUser() {
        BoardCreditUserProjection user = new BoardCreditUserProjection();
        user.setUserId(42L);
        user.setPoints(500);
        user.setStatus("0");
        user.setDelFlag("0");
        return user;
    }

    private static BoardCreditAccount account() {
        BoardCreditAccount account = new BoardCreditAccount();
        account.setAccountId(ACCOUNT_ID);
        account.setUserId(42L);
        account.setAccountScope(IndependentBoardCreditService.ACCOUNT_SCOPE);
        account.setCurrencyCode(IndependentBoardCreditService.CURRENCY_CODE);
        account.setOpeningBalance(400L);
        account.setBalance(500L);
        account.setVersion(1L);
        account.setLastEntrySequence(1L);
        account.setLastEntryHash("a".repeat(64));
        account.setCreatedAt(new Date(CREATED_AT.getTime() - 1000L));
        account.setUpdatedAt(CREATED_AT);
        return account;
    }

    private static BoardCreditOperation storedGrant(BoardCreditGrantRequest request) {
        BoardCreditOperation operation = new BoardCreditOperation();
        operation.setOperationId(OPERATION_ID);
        operation.setIdempotencyKey(request.idempotencyKey());
        operation.setRequestDigest(BoardCreditDigest.grantDigest(request, 900L));
        operation.setAccountId(ACCOUNT_ID);
        operation.setUserId(42L);
        operation.setAccountScope(IndependentBoardCreditService.ACCOUNT_SCOPE);
        operation.setCurrencyCode(IndependentBoardCreditService.CURRENCY_CODE);
        operation.setOperationType("GRANT");
        operation.setDelta(100L);
        operation.setReasonCode(request.reasonCode());
        operation.setReasonNote(request.note());
        operation.setActorUserId(900L);
        operation.setBalanceBefore(400L);
        operation.setBalanceAfter(500L);
        operation.setCreatedAt(CREATED_AT);
        return operation;
    }

    private static BoardCreditEntry storedEntry(BoardCreditOperation operation) {
        BoardCreditEntry entry = new BoardCreditEntry();
        entry.setEntryId(ENTRY_ID);
        entry.setOperationId(operation.getOperationId());
        entry.setRequestDigest(operation.getRequestDigest());
        entry.setAccountId(operation.getAccountId());
        entry.setSequenceNo(1L);
        entry.setDelta(operation.getDelta());
        entry.setBalanceBefore(operation.getBalanceBefore());
        entry.setBalanceAfter(operation.getBalanceAfter());
        entry.setPreviousEntryHash("0".repeat(64));
        entry.setCreatedAt(operation.getCreatedAt());
        entry.setEntryHash(BoardCreditDigest.entryHash(entry));
        return entry;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class ConfigurationUnderTest {
        @Bean
        RecordingManager transactionManager() {
            return new RecordingManager();
        }

        @Bean
        IndependentBoardCreditMapper mapper() {
            return mock(IndependentBoardCreditMapper.class);
        }

        @Bean
        IndependentBoardCreditTransactionService transactionService(
                IndependentBoardCreditMapper mapper) {
            return new IndependentBoardCreditTransactionService(mapper);
        }

        @Bean
        IndependentBoardCreditService creditService(
                IndependentBoardCreditTransactionService transactionService) {
            return new IndependentBoardCreditService(transactionService);
        }
    }

    static final class RecordingManager extends AbstractPlatformTransactionManager {
        private static final long serialVersionUID = 1L;
        private final ThreadLocal<TransactionState> current = new ThreadLocal<>();
        private final List<String> events = new ArrayList<>();

        @Override
        protected Object doGetTransaction() {
            TransactionState state = current.get();
            return state == null ? new TransactionState() : state;
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return ((TransactionState) transaction).active;
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            TransactionState state = (TransactionState) transaction;
            state.active = true;
            current.set(state);
            events.add("begin");
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            events.add("commit");
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            events.add("rollback");
        }

        @Override
        protected void doCleanupAfterCompletion(Object transaction) {
            ((TransactionState) transaction).active = false;
            current.remove();
        }
    }

    static final class TransactionState {
        private boolean active;
    }
}
