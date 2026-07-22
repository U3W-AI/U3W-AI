package com.wx.fbsir.business.board.plan.service;

import com.wx.fbsir.business.board.plan.domain.BoardPlanPolicyReceipt;
import com.wx.fbsir.business.board.plan.domain.BoardPlanPolicyName;
import com.wx.fbsir.business.board.plan.domain.BoardPlanPolicySnapshot;
import com.wx.fbsir.business.board.plan.dto.BoardPlanPolicyAuditEnvelope;
import com.wx.fbsir.business.board.plan.dto.BoardPlanPolicyRevisionRequest;
import com.wx.fbsir.business.board.plan.dto.BoardPlanPolicyRevisionView;
import com.wx.fbsir.business.board.plan.mapper.IndependentBoardPlanPolicyMapper;
import com.wx.fbsir.common.exception.ServiceException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IndependentBoardPlanPolicyServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-22T08:00:00Z");
    private static final String NEXT_RECEIPT =
            "123e4567-e89b-12d3-a456-426614174000";

    private IndependentBoardPlanPolicyMapper mapper;
    private IndependentBoardPlanPolicyTransactionService transaction;
    private IndependentBoardPlanPolicyService service;

    @BeforeEach
    void setUp() {
        mapper = mock(IndependentBoardPlanPolicyMapper.class);
        transaction = new IndependentBoardPlanPolicyTransactionService(
                mapper, Clock.fixed(NOW, ZoneOffset.UTC), () -> NEXT_RECEIPT);
        service = new IndependentBoardPlanPolicyService(transaction);
    }

    @Test
    void exactReplayReturnsCommittedReceiptBeforeReadingMutableHeads() {
        BoardPlanPolicyRevisionRequest request = revision(
                "BOARD_VIP", 1L, "独董会 VIP 增强版", 8, 30, null, true,
                null, "plan:20260722:0001");
        BoardPlanPolicyReceipt committed = revisedReceipt(request, 900L, 2L, NEXT_RECEIPT);
        when(mapper.selectReceiptByActorAndIdempotencyDigest(
                IndependentBoardPlanPolicyService.PRODUCT_CODE,
                "ADMIN_USER", 900L,
                BoardPlanPolicyDigest.idempotencyKeyDigest(request.idempotencyKey())))
                .thenReturn(committed);

        BoardPlanPolicyRevisionView result = service.revise(request, 900L);

        assertEquals(NEXT_RECEIPT, result.receiptId());
        assertEquals(2L, result.policyVersion());
        verify(mapper, never()).selectPolicyHeadCodesForUpdate(anyString());
        verify(mapper, never()).transitionReceiptThroughControlledProcedure(any());
    }

    @Test
    void sameKeyWithDifferentCommandFailsClosedBeforeLockingHeads() {
        BoardPlanPolicyRevisionRequest storedCommand = revision(
                "BOARD_VIP", 1L, "独董会 VIP 增强版", 8, 30, null, true,
                null, "plan:20260722:0001");
        BoardPlanPolicyRevisionRequest conflicting = revision(
                "BOARD_VIP", 1L, "独董会 VIP 增强版", 9, 30, null, true,
                null, "plan:20260722:0001");
        when(mapper.selectReceiptByActorAndIdempotencyDigest(
                anyString(), anyString(), anyLong(), anyString()))
                .thenReturn(revisedReceipt(storedCommand, 900L, 2L, NEXT_RECEIPT));

        ServiceException error = assertThrows(
                ServiceException.class, () -> service.revise(conflicting, 900L));

        assertEquals(409, error.getCode());
        assertEquals("BOARD_PLAN_POLICY_IDEMPOTENCY_CONFLICT", error.getMessage());
        verify(mapper, never()).selectPolicyHeadCodesForUpdate(anyString());
        verify(mapper, never()).transitionReceiptThroughControlledProcedure(any());
    }

    @Test
    void duplicateRecoveryDatabaseFailureIsConvertedWithoutRawSqlDetail() {
        IndependentBoardPlanPolicyTransactionService failing =
                mock(IndependentBoardPlanPolicyTransactionService.class);
        IndependentBoardPlanPolicyService subject =
                new IndependentBoardPlanPolicyService(failing);
        BoardPlanPolicyRevisionRequest request = revision(
                "BOARD_VIP", 1L, "Independent Board VIP Plus",
                8, 30, null, true, null, "plan:20260722:recovery");
        when(failing.replayIfPresent(request, 900L)).thenReturn(null);
        when(failing.reviseFresh(request, 900L)).thenThrow(
                new DuplicateKeyException("secret duplicate sql"));
        when(failing.replayRequired(request, 900L)).thenThrow(
                new DataAccessResourceFailureException("secret replay sql"));

        ServiceException error = assertThrows(
                ServiceException.class, () -> subject.revise(request, 900L));

        assertEquals(500, error.getCode());
        assertEquals("BOARD_PLAN_POLICY_PERSISTENCE_FAILED", error.getMessage());
    }

    @Test
    void controlledProcedureVersionConflictPreservesTheStable409Contract() {
        IndependentBoardPlanPolicyTransactionService failing =
                mock(IndependentBoardPlanPolicyTransactionService.class);
        IndependentBoardPlanPolicyService subject =
                new IndependentBoardPlanPolicyService(failing);
        BoardPlanPolicyRevisionRequest request = revision(
                "BOARD_VIP", 1L, "Independent Board VIP Plus",
                8, 30, null, true, null, "plan:20260723:procedure-conflict");
        when(failing.replayIfPresent(request, 900L)).thenReturn(null);
        when(failing.reviseFresh(request, 900L)).thenThrow(
                new DataIntegrityViolationException("stored procedure failed",
                        new SQLException("BOARD_PLAN_POLICY_VERSION_CONFLICT", "45000", 1644)));

        ServiceException error = assertThrows(
                ServiceException.class, () -> subject.revise(request, 900L));

        assertEquals(409, error.getCode());
        assertEquals("BOARD_PLAN_POLICY_VERSION_CONFLICT", error.getMessage());
    }

    @Test
    void freshRevisionUsesControlledProcedureForOneVerifiedSuccessor() {
        BoardPlanPolicyRevisionRequest request = revision(
                "BOARD_VIP", 1L, "独董会 VIP 增强版", 8, 30, null, true,
                null, "plan:20260722:0001");
        List<BoardPlanPolicySnapshot> current = baselineCatalog();
        BoardPlanPolicySnapshot committed = snapshotFromReceipt(
                revisedReceipt(normalized(request), 900L, 2L, NEXT_RECEIPT));
        stubLockedCatalog(current);
        when(mapper.selectCurrentPolicy(
                IndependentBoardPlanPolicyService.PRODUCT_CODE, "BOARD_VIP"))
                .thenReturn(committed);

        BoardPlanPolicyRevisionView result = service.revise(request, 900L);

        assertEquals("独董会 VIP 增强版", result.planName());
        assertEquals("PLAN_POLICY_REVISED", result.action());
        assertEquals("ADMIN_USER", result.actorType());
        assertEquals(2L, result.policyVersion());
        ArgumentCaptor<BoardPlanPolicyReceipt> receipt =
                ArgumentCaptor.forClass(BoardPlanPolicyReceipt.class);
        verify(mapper).transitionReceiptThroughControlledProcedure(receipt.capture());
        assertEquals("plan-policy-baseline-board-vip-v1",
                receipt.getValue().getPreviousReceiptId());
        assertEquals(current.get(1).getPolicyDigest(),
                receipt.getValue().getPreviousPolicyDigest());
        assertEquals(900L, receipt.getValue().getActorUserId());
        assertNull(receipt.getValue().getRollbackOfReceiptId());
    }

    @Test
    void boundaryWhitespaceNamesFailBeforeAnyDatabaseAccess() {
        for (String name : List.of(
                " leading", "trailing ", "\u00A0vip", "vip\u202F")) {
            ServiceException error = assertThrows(ServiceException.class,
                    () -> service.revise(revision(
                            "BOARD_VIP", 1L, name, 8, 30, null, true,
                            null, "plan:20260722:name:" + Integer.toHexString(name.hashCode())),
                            900L));
            assertEquals("BOARD_PLAN_POLICY_REQUEST_INVALID", error.getMessage());
        }
        verifyNoInteractions(mapper);
    }

    @Test
    void planNameLengthCountsUnicodeCodePointsInsteadOfUtf16Units() {
        assertTrue(BoardPlanPolicyName.isValid("😀".repeat(128)));
        assertFalse(BoardPlanPolicyName.isValid("😀".repeat(129)));
        assertFalse(BoardPlanPolicyName.isValid("\uD800"));
        assertThrows(IllegalArgumentException.class, () -> BoardPlanPolicyDigest.policyDigest(
                IndependentBoardPlanPolicyService.PRODUCT_CODE,
                IndependentBoardPlanPolicyService.VIP_PLAN,
                "\uD800", true, true, 5, 30, null, true, "ACTIVE"));
    }

    @Test
    void incompleteMutableHeadLockSetFailsBeforeAnyCatalogOrReceiptWrite() {
        when(mapper.selectPolicyHeadCodesForUpdate(
                IndependentBoardPlanPolicyService.PRODUCT_CODE))
                .thenReturn(List.of(IndependentBoardPlanPolicyService.FREE_PLAN));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.revise(revision(
                        "BOARD_VIP", 1L, "Independent Board VIP Plus", 8, 30,
                        null, true, null, "plan:20260722:head-drift"), 900L));

        assertEquals("BOARD_PLAN_POLICY_HEAD_LOCK_DRIFT", error.getMessage());
        verify(mapper, never()).selectCurrentPolicies(anyString());
        verify(mapper, never()).transitionReceiptThroughControlledProcedure(any());
    }

    @Test
    void staleVersionAndNoOpBothProduceZeroWrites() {
        stubLockedCatalog(baselineCatalog());

        ServiceException stale = assertThrows(ServiceException.class,
                () -> service.revise(revision(
                        "BOARD_VIP", 2L, "Independent Board VIP", 5, 30, null, true,
                        null, "plan:20260722:stale"), 900L));
        assertEquals("BOARD_PLAN_POLICY_VERSION_CONFLICT", stale.getMessage());

        ServiceException noOp = assertThrows(ServiceException.class,
                () -> service.revise(revision(
                        "BOARD_FREE", 1L, "Independent Board Free", 1, 5, 3, false,
                        null, "plan:20260722:no-op"), 900L));
        assertEquals("BOARD_PLAN_POLICY_NO_CHANGE", noOp.getMessage());
        verify(mapper, never()).transitionReceiptThroughControlledProcedure(any());
    }

    @Test
    void rollbackCopiesHistoricalPolicyIntoANewCompensationVersion() {
        String targetId = "223e4567-e89b-12d3-a456-426614174000";
        BoardPlanPolicyRevisionRequest request = revision(
                "BOARD_VIP", 2L, "Independent Board VIP", 5, 30, null, true,
                targetId, "plan:20260722:rollback");
        BoardPlanPolicySnapshot free = baselineCatalog().get(0);
        BoardPlanPolicySnapshot currentVip = snapshotFromReceipt(revisedReceipt(
                revision("BOARD_VIP", 1L, "独董会 VIP 增强版", 8, 30, null, true,
                        null, "plan:20260722:older"), 901L, 2L,
                "323e4567-e89b-12d3-a456-426614174000"));
        BoardPlanPolicyReceipt target = baselineCatalog().get(1);
        target.setReceiptId(targetId);
        BoardPlanPolicySnapshot committed = snapshotFromReceipt(revisedReceipt(
                request, 900L, 3L, NEXT_RECEIPT));
        committed.setAction("PLAN_POLICY_ROLLED_BACK");
        committed.setRollbackOfReceiptId(targetId);
        committed.setCommandDigest(BoardPlanPolicyDigest.commandDigest(request, 900L));
        stubLockedCatalog(List.of(free, currentVip));
        when(mapper.selectReceiptByReceiptId(
                IndependentBoardPlanPolicyService.PRODUCT_CODE, targetId)).thenReturn(target);
        when(mapper.selectCurrentPolicy(anyString(), anyString())).thenReturn(committed);

        BoardPlanPolicyRevisionView result = service.revise(request, 900L);

        assertEquals("PLAN_POLICY_ROLLED_BACK", result.action());
        assertEquals(targetId, result.rollbackOfReceiptId());
        assertEquals(3L, result.policyVersion());
        ArgumentCaptor<BoardPlanPolicyReceipt> inserted =
                ArgumentCaptor.forClass(BoardPlanPolicyReceipt.class);
        verify(mapper).transitionReceiptThroughControlledProcedure(inserted.capture());
        assertEquals("PLAN_POLICY_ROLLED_BACK", inserted.getValue().getAction());
        assertEquals(targetId, inserted.getValue().getRollbackOfReceiptId());
    }

    @Test
    void crossPlanMonotonicityAndAuthorityConflictFailClosed() {
        stubLockedCatalog(baselineCatalog());
        ServiceException invariant = assertThrows(ServiceException.class,
                () -> service.revise(revision(
                        "BOARD_FREE", 1L, "独董会免费增强版", 6, 5, 3, false,
                        null, "plan:20260722:invariant"), 900L));
        assertEquals("BOARD_PLAN_POLICY_CATALOG_INVARIANT", invariant.getMessage());
        verify(mapper, never()).transitionReceiptThroughControlledProcedure(any());

        when(mapper.insertReceipt(any())).thenReturn(1);
        when(mapper.updateHeadIfCurrent(
                anyString(), anyString(), anyString(), anyLong(), anyString(), anyLong(), any()))
                .thenReturn(0);
        when(mapper.selectCurrentPolicy(anyString(), anyString())).thenThrow(
                new ServiceException("BOARD_PLAN_POLICY_VERSION_CONFLICT", 409));
        ServiceException cas = assertThrows(ServiceException.class,
                () -> service.revise(revision(
                        "BOARD_VIP", 1L, "独董会 VIP 增强版", 8, 30, null, true,
                        null, "plan:20260722:cas-fail"), 900L));
        assertEquals("BOARD_PLAN_POLICY_VERSION_CONFLICT", cas.getMessage());
    }

    @Test
    void auditIsBoundedAndUsesOnlySafeProjection() {
        List<BoardPlanPolicyReceipt> rows = java.util.stream.IntStream.range(0, 101)
                .mapToObj(index -> revisedReceipt(
                        revision("BOARD_VIP", index + 1L, "独董会 VIP 版", 5, 30,
                                null, true, null, "plan:20260722:audit:" + index),
                        900L, index + 2L,
                        String.format("%08d-e89b-12d3-a456-426614174000", index)))
                .toList();
        when(mapper.selectPolicyReceipts(
                IndependentBoardPlanPolicyService.PRODUCT_CODE, 101)).thenReturn(rows);

        BoardPlanPolicyAuditEnvelope result = service.audit();

        assertEquals(100, result.records().size());
        assertEquals(100, result.limit());
        assertEquals(true, result.truncated());
    }

    @Test
    void auditFailsClosedOnHistoricalIdentityOrEvidenceDrift() {
        BoardPlanPolicyReceipt receipt = revisedReceipt(
                revision("BOARD_VIP", 1L, "Independent Board VIP Plus", 8, 30,
                        null, true, null, "plan:20260722:audit-drift"),
                900L, 2L, NEXT_RECEIPT);
        when(mapper.selectPolicyReceipts(
                IndependentBoardPlanPolicyService.PRODUCT_CODE, 101))
                .thenReturn(List.of(receipt));

        receipt.setEvidenceLevel(null);
        ServiceException evidence = assertThrows(ServiceException.class, service::audit);
        assertEquals("BOARD_PLAN_POLICY_RECEIPT_DRIFT", evidence.getMessage());

        receipt.setEvidenceLevel("ACTION_COMPLETED");
        receipt.setVip(false);
        receipt.setPolicyDigest(BoardPlanPolicyDigest.policyDigest(receipt));
        ServiceException identity = assertThrows(ServiceException.class, service::audit);
        assertEquals("BOARD_PLAN_POLICY_RECEIPT_DRIFT", identity.getMessage());
    }

    @Test
    void baselinePolicyDigestVectorsStayAlignedWithMigration() {
        List<BoardPlanPolicySnapshot> catalog = baselineCatalog();

        assertEquals("8ae3df83c9f56974261d1e471eb19034b2b782f793f74333c64a13c61dae8982",
                catalog.get(0).getPolicyDigest());
        assertEquals("02e90096b76d25b69c938fa65cfc3931207ac0a2648447bf613dca591619da51",
                catalog.get(1).getPolicyDigest());
    }

    private void stubLockedCatalog(List<BoardPlanPolicySnapshot> catalog) {
        when(mapper.selectPolicyHeadCodesForUpdate(
                IndependentBoardPlanPolicyService.PRODUCT_CODE))
                .thenReturn(List.of(
                        IndependentBoardPlanPolicyService.FREE_PLAN,
                        IndependentBoardPlanPolicyService.VIP_PLAN));
        when(mapper.selectCurrentPolicies(
                IndependentBoardPlanPolicyService.PRODUCT_CODE)).thenReturn(catalog);
    }

    private static BoardPlanPolicyRevisionRequest revision(
            String planCode, long expectedVersion, String planName,
            int daily, int agenda, Integer seats, boolean secretary,
            String rollbackOfReceiptId, String idempotencyKey) {
        return new BoardPlanPolicyRevisionRequest(
                planCode, expectedVersion, planName, daily, agenda, seats, secretary,
                rollbackOfReceiptId, idempotencyKey);
    }

    private static BoardPlanPolicyRevisionRequest normalized(
            BoardPlanPolicyRevisionRequest request) {
        return revision(request.planCode(), request.expectedVersion(), request.planName(),
                request.dailyMeetingLimit(), request.agendaLimit(), request.seatLimit(),
                request.secretaryEnabled(), request.rollbackOfReceiptId(), request.idempotencyKey());
    }

    private static List<BoardPlanPolicySnapshot> baselineCatalog() {
        BoardPlanPolicySnapshot free = baseline(
                "BOARD_FREE", "Independent Board Free", false, false, 1, 5, 3, false);
        BoardPlanPolicySnapshot vip = baseline(
                "BOARD_VIP", "Independent Board VIP", true, true, 5, 30, null, true);
        return List.of(free, vip);
    }

    private static BoardPlanPolicySnapshot baseline(
            String planCode, String name, boolean vip, boolean connector,
            int daily, int agenda, Integer seats, boolean secretary) {
        BoardPlanPolicySnapshot value = new BoardPlanPolicySnapshot();
        value.setReceiptId("plan-policy-baseline-" + planCode.toLowerCase().replace('_', '-') + "-v1");
        value.setProductCode(IndependentBoardPlanPolicyService.PRODUCT_CODE);
        value.setPlanCode(planCode);
        value.setPolicyVersion(1L);
        value.setAction("PLAN_POLICY_BASELINED");
        value.setActorType("SYSTEM_MIGRATION");
        value.setIdempotencyKeyDigest("a".repeat(64));
        value.setCommandDigest("b".repeat(64));
        value.setPlanName(name);
        value.setVip(vip);
        value.setConnectorRequired(connector);
        value.setDailyMeetingLimit(daily);
        value.setAgendaLimit(agenda);
        value.setSeatLimit(seats);
        value.setSecretaryEnabled(secretary);
        value.setStatus("ACTIVE");
        value.setEvidenceLevel("ACTION_COMPLETED");
        value.setCreatedAt(Date.from(Instant.parse("2026-07-20T00:00:00Z")));
        value.setPolicyDigest(BoardPlanPolicyDigest.policyDigest(value));
        identity(value);
        return value;
    }

    private static BoardPlanPolicyReceipt revisedReceipt(
            BoardPlanPolicyRevisionRequest request, long actorUserId,
            long policyVersion, String receiptId) {
        BoardPlanPolicyReceipt value = new BoardPlanPolicyReceipt();
        value.setReceiptId(receiptId);
        value.setProductCode(IndependentBoardPlanPolicyService.PRODUCT_CODE);
        value.setPlanCode(request.planCode());
        value.setPolicyVersion(policyVersion);
        value.setPreviousReceiptId(policyVersion == 2L
                ? "plan-policy-baseline-board-vip-v1"
                : "323e4567-e89b-12d3-a456-426614174000");
        value.setRollbackOfReceiptId(request.rollbackOfReceiptId());
        value.setAction(request.rollbackOfReceiptId() == null
                ? "PLAN_POLICY_REVISED" : "PLAN_POLICY_ROLLED_BACK");
        value.setActorType("ADMIN_USER");
        value.setActorUserId(actorUserId);
        value.setIdempotencyKeyDigest(
                BoardPlanPolicyDigest.idempotencyKeyDigest(request.idempotencyKey()));
        value.setCommandDigest(BoardPlanPolicyDigest.commandDigest(request, actorUserId));
        value.setPreviousPolicyDigest("c".repeat(64));
        value.setPlanName(request.planName());
        value.setVip("BOARD_VIP".equals(request.planCode()));
        value.setConnectorRequired("BOARD_VIP".equals(request.planCode()));
        value.setDailyMeetingLimit(request.dailyMeetingLimit());
        value.setAgendaLimit(request.agendaLimit());
        value.setSeatLimit(request.seatLimit());
        value.setSecretaryEnabled(request.secretaryEnabled());
        value.setStatus("ACTIVE");
        value.setEvidenceLevel("ACTION_COMPLETED");
        value.setCreatedAt(Date.from(NOW));
        value.setPolicyDigest(BoardPlanPolicyDigest.policyDigest(value));
        return value;
    }

    private static BoardPlanPolicySnapshot snapshotFromReceipt(BoardPlanPolicyReceipt receipt) {
        BoardPlanPolicySnapshot value = new BoardPlanPolicySnapshot();
        value.copyFrom(receipt);
        identity(value);
        return value;
    }

    private static void identity(BoardPlanPolicySnapshot value) {
        value.setIdentityProductCode(value.getProductCode());
        value.setIdentityPlanCode(value.getPlanCode());
        value.setIdentityVip(value.getVip());
        value.setIdentityConnectorRequired(value.getConnectorRequired());
        value.setIdentityStatus(value.getStatus());
    }
}
