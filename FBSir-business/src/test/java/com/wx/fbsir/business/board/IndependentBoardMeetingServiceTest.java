package com.wx.fbsir.business.board.service;

import com.wx.fbsir.business.board.domain.BoardUsageBudget;
import com.wx.fbsir.business.board.domain.BoardUsageOperation;
import com.wx.fbsir.business.board.domain.BoardUsageOperationPolicyReceipt;
import com.wx.fbsir.business.board.dto.BoardEntitlementSnapshot;
import com.wx.fbsir.business.board.dto.BoardMeetingReservationRequest;
import com.wx.fbsir.business.board.dto.BoardMeetingReservationView;
import com.wx.fbsir.business.board.dto.BoardOperationAuditEnvelope;
import com.wx.fbsir.business.board.dto.BoardOperationAuditView;
import com.wx.fbsir.business.board.mapper.IndependentBoardMapper;
import com.wx.fbsir.business.board.plan.domain.BoardPlanPolicySnapshot;
import com.wx.fbsir.business.board.plan.service.BoardPlanPolicyDigest;
import com.wx.fbsir.common.exception.ServiceException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.TransactionSystemException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IndependentBoardMeetingServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-20T12:00:00Z");
    private static final String FREE_POLICY_DIGEST =
            "8ae3df83c9f56974261d1e471eb19034b2b782f793f74333c64a13c61dae8982";
    private static final String VIP_POLICY_DIGEST =
            "02e90096b76d25b69c938fa65cfc3931207ac0a2648447bf613dca591619da51";
    private IndependentBoardMapper mapper;
    private IndependentBoardEntitlementService entitlementService;
    private IndependentBoardMeetingService service;

    @BeforeEach
    void setUp() {
        mapper = mock(IndependentBoardMapper.class);
        entitlementService = mock(IndependentBoardEntitlementService.class);
        service = new IndependentBoardMeetingService(
                mapper, entitlementService, Clock.fixed(NOW, ZoneOffset.UTC));
        when(mapper.selectCurrentPlanPolicy(
                eq(IndependentBoardEntitlementService.PRODUCT_CODE), anyString()))
                .thenAnswer(invocation -> policy(invocation.getArgument(1)));
        when(mapper.insertUsageOperationPolicyReceipt(any())).thenReturn(1);
    }

    @Test
    void reservesOneDailyUnitAtomicallyAndReturnsReadbackRemaining() {
        BoardMeetingReservationRequest request = request("operation-123", 5, 3);
        when(entitlementService.getSnapshotForReservation(7L, 42L)).thenReturn(freeSnapshot());
        when(mapper.insertOperation(any())).thenReturn(1);
        when(mapper.prepareUsageBudget(anyLong(), anyLong(), anyString(), anyString(), any(), anyInt()))
                .thenReturn(1);
        when(mapper.reserveOneMeeting(anyLong(), anyLong(), anyString(), anyString(), any(), anyInt()))
                .thenReturn(1);
        BoardUsageBudget budget = new BoardUsageBudget();
        budget.setReservedCount(1);
        budget.setUsedCount(0);
        when(mapper.selectUsageBudget(7L, 11L, IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.MEETING_METRIC, LocalDate.of(2026, 7, 20)))
                .thenReturn(budget);
        when(mapper.markOperationReserved(7L, "operation-123", 0)).thenReturn(1);

        BoardMeetingReservationView result = service.reserve(request, 42L);

        assertEquals("RESERVED", result.status());
        assertEquals("BOARD_FREE", result.effectivePlanCode());
        assertEquals(0, result.remainingCount());
        verify(mapper).reserveOneMeeting(7L, 11L, IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.MEETING_METRIC, LocalDate.of(2026, 7, 20), 1);
        verify(mapper).markOperationReserved(7L, "operation-123", 0);
        ArgumentCaptor<BoardUsageOperationPolicyReceipt> lineage =
                ArgumentCaptor.forClass(BoardUsageOperationPolicyReceipt.class);
        verify(mapper).insertUsageOperationPolicyReceipt(lineage.capture());
        assertEquals(7L, lineage.getValue().getTenantId());
        assertEquals("operation-123", lineage.getValue().getOperationId());
        assertEquals(IndependentBoardEntitlementService.PRODUCT_CODE,
                lineage.getValue().getProductCode());
        assertEquals("BOARD_FREE", lineage.getValue().getPlanCode());
        assertEquals("plan-policy-baseline-board-free-v1",
                lineage.getValue().getPolicyReceiptId());
        assertEquals(1L, lineage.getValue().getPolicyVersion());
        assertEquals(FREE_POLICY_DIGEST, lineage.getValue().getPolicyDigest());
        InOrder order = inOrder(mapper);
        order.verify(mapper).selectCurrentPlanPolicy(
                IndependentBoardEntitlementService.PRODUCT_CODE, "BOARD_FREE");
        order.verify(mapper).insertOperation(any());
        order.verify(mapper).markOperationReserved(7L, "operation-123", 0);
        order.verify(mapper).insertUsageOperationPolicyReceipt(any());
    }

    @Test
    void sameOperationAndDigestReturnsExistingReservationWithoutSecondQuotaWrite() {
        BoardMeetingReservationRequest request = request("operation-123", 5, 3);
        when(entitlementService.getSnapshotForReservation(7L, 42L)).thenReturn(freeSnapshot());
        AtomicReference<BoardUsageOperation> attempted = new AtomicReference<>();
        when(mapper.insertOperation(any())).thenAnswer(invocation -> {
            attempted.set(invocation.getArgument(0));
            throw new DuplicateKeyException("duplicate operation");
        });
        when(mapper.selectOperationForUpdate(7L, "operation-123"))
                .thenReturn(null)
                .thenAnswer(invocation -> {
            BoardUsageOperation existing = attempted.get();
            existing.setStatus("RESERVED");
            existing.setRemainingCount(0);
            return existing;
        });
        when(mapper.selectOperationPolicyReceipt(7L, "operation-123"))
                .thenReturn(lineage("operation-123"));

        BoardMeetingReservationView result = service.reserve(request, 42L);

        assertEquals("RESERVED", result.status());
        assertEquals(0, result.remainingCount());
        verify(mapper, never()).prepareUsageBudget(anyLong(), anyLong(), anyString(), anyString(), any(), anyInt());
        verify(mapper, never()).reserveOneMeeting(anyLong(), anyLong(), anyString(), anyString(), any(), anyInt());
    }

    @Test
    void sameOperationWithDifferentDigestFailsClosed() {
        BoardUsageOperation existing = operation("operation-123", "0".repeat(64));
        existing.setStatus("RESERVED");
        when(mapper.selectOperationForUpdate(7L, "operation-123")).thenReturn(existing);
        when(mapper.selectOperationPolicyReceipt(7L, "operation-123"))
                .thenReturn(lineage("operation-123"));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.reserve(request("operation-123", 5, 3), 42L));

        assertEquals(409, error.getCode());
        assertEquals("IDEMPOTENCY_DIGEST_CONFLICT", error.getMessage());
        verify(entitlementService, never()).getSnapshotForReservation(anyLong(), anyLong());
        verify(mapper, never()).reserveOneMeeting(anyLong(), anyLong(), anyString(), anyString(), any(), anyInt());
    }

    @Test
    void caseInsensitiveDatabaseCollisionCannotReplayAnotherOperationId() {
        BoardMeetingReservationRequest requested = request("operation-case", 5, 3);
        BoardUsageOperation existing = operation(
                "OPERATION-CASE", reservationDigest(requested, 42L));
        existing.setStatus("RESERVED");
        when(mapper.selectOperationForUpdate(7L, "operation-case")).thenReturn(existing);
        when(mapper.selectOperationPolicyReceipt(7L, "operation-case"))
                .thenReturn(lineage("OPERATION-CASE"));

        ServiceException error = assertThrows(
                ServiceException.class, () -> service.reserve(requested, 42L));

        assertEquals(409, error.getCode());
        assertEquals("IDEMPOTENCY_DIGEST_CONFLICT", error.getMessage());
        verify(entitlementService, never()).getSnapshotForReservation(anyLong(), anyLong());
        verify(mapper, never()).selectOperationPolicyReceipt(anyLong(), anyString());
    }

    @Test
    void committedReplaySurvivesCurrentEntitlementRevocationAndPolicyHeadAdvance() {
        BoardMeetingReservationRequest request = request("operation-123", 5, 3);
        BoardUsageOperation existing = operation(
                request.operationId(), reservationDigest(request, 42L));
        existing.setStatus("RESERVED");
        existing.setRemainingCount(0);
        when(mapper.selectOperationForUpdate(7L, "operation-123")).thenReturn(existing);
        when(mapper.selectOperationPolicyReceipt(7L, "operation-123"))
                .thenReturn(lineage("operation-123"));
        when(entitlementService.getSnapshotForReservation(7L, 42L))
                .thenThrow(new ServiceException("ENTITLEMENT_REVOKED", 403));

        BoardMeetingReservationView result = service.reserve(request, 42L);

        assertEquals("RESERVED", result.status());
        assertEquals("BOARD_FREE", result.effectivePlanCode());
        assertEquals(0, result.remainingCount());
        verify(entitlementService, never()).getSnapshotForReservation(anyLong(), anyLong());
        verify(mapper, never()).selectCurrentPlanPolicy(anyString(), anyString());
        verify(mapper, never()).insertOperation(any());
        verify(mapper, never()).reserveOneMeeting(
                anyLong(), anyLong(), anyString(), anyString(), any(), anyInt());
    }

    @Test
    void committedReplayFailsClosedWhenItsImmutablePolicyLineageIsMissing() {
        BoardMeetingReservationRequest request = request("operation-123", 5, 3);
        BoardUsageOperation existing = operation(
                request.operationId(), reservationDigest(request, 42L));
        existing.setStatus("RESERVED");
        when(mapper.selectOperationForUpdate(7L, "operation-123")).thenReturn(existing);
        when(mapper.selectOperationPolicyReceipt(7L, "operation-123"))
                .thenReturn(null);

        ServiceException error = assertThrows(
                ServiceException.class, () -> service.reserve(request, 42L));

        assertEquals(500, error.getCode());
        assertEquals("MEETING_POLICY_LINEAGE_READBACK_FAILED", error.getMessage());
        verify(entitlementService, never()).getSnapshotForReservation(anyLong(), anyLong());
        verify(mapper, never()).selectCurrentPlanPolicy(anyString(), anyString());
        verify(mapper, never()).insertOperation(any());
    }

    @Test
    void reservationUsesOneCommittedPolicySnapshotForValidationBudgetAndLineage() {
        BoardEntitlementSnapshot staleQuotaProjection = new BoardEntitlementSnapshot(
                7L, 11L, 42L, null, "BOARD_FREE", "FREE",
                1, 5, 3, false, false, false, 0, 0, 1);
        BoardPlanPolicySnapshot committedV2 = policy("BOARD_FREE");
        committedV2.setReceiptId("323e4567-e89b-12d3-a456-426614174000");
        committedV2.setPolicyVersion(2L);
        committedV2.setDailyMeetingLimit(2);
        committedV2.setAgendaLimit(6);
        committedV2.setSeatLimit(4);
        committedV2.setPolicyDigest(BoardPlanPolicyDigest.policyDigest(committedV2));
        when(entitlementService.getSnapshotForReservation(7L, 42L))
                .thenReturn(staleQuotaProjection);
        when(mapper.selectCurrentPlanPolicy(
                IndependentBoardEntitlementService.PRODUCT_CODE, "BOARD_FREE"))
                .thenReturn(committedV2);
        when(mapper.insertOperation(any())).thenReturn(1);
        when(mapper.prepareUsageBudget(anyLong(), anyLong(), anyString(), anyString(), any(), anyInt()))
                .thenReturn(1);
        when(mapper.reserveOneMeeting(anyLong(), anyLong(), anyString(), anyString(), any(), anyInt()))
                .thenReturn(1);
        BoardUsageBudget budget = new BoardUsageBudget();
        budget.setReservedCount(1);
        budget.setUsedCount(0);
        when(mapper.selectUsageBudget(7L, 11L, IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.MEETING_METRIC, LocalDate.of(2026, 7, 20)))
                .thenReturn(budget);
        when(mapper.markOperationReserved(7L, "operation-v2", 1)).thenReturn(1);

        BoardMeetingReservationView result = service.reserve(
                request("operation-v2", 6, 4), 42L);

        assertEquals(1, result.remainingCount());
        verify(mapper).reserveOneMeeting(
                7L, 11L, IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.MEETING_METRIC,
                LocalDate.of(2026, 7, 20), 2);
        ArgumentCaptor<BoardUsageOperationPolicyReceipt> lineage =
                ArgumentCaptor.forClass(BoardUsageOperationPolicyReceipt.class);
        verify(mapper).insertUsageOperationPolicyReceipt(lineage.capture());
        assertEquals(committedV2.getReceiptId(), lineage.getValue().getPolicyReceiptId());
        assertEquals(2L, lineage.getValue().getPolicyVersion());
        assertEquals(committedV2.getPolicyDigest(), lineage.getValue().getPolicyDigest());
    }

    @Test
    void reservationAcceptsPlanNameAtTheSharedUnicodeCodePointLimit() {
        BoardPlanPolicySnapshot policy = policy("BOARD_FREE");
        policy.setPlanName("😀".repeat(128));
        policy.setPolicyDigest(BoardPlanPolicyDigest.policyDigest(policy));
        when(entitlementService.getSnapshotForReservation(7L, 42L)).thenReturn(freeSnapshot());
        when(mapper.selectCurrentPlanPolicy(
                IndependentBoardEntitlementService.PRODUCT_CODE, "BOARD_FREE"))
                .thenReturn(policy);
        when(mapper.insertOperation(any())).thenReturn(1);
        when(mapper.prepareUsageBudget(
                anyLong(), anyLong(), anyString(), anyString(), any(), anyInt()))
                .thenReturn(1);
        when(mapper.reserveOneMeeting(
                anyLong(), anyLong(), anyString(), anyString(), any(), anyInt()))
                .thenReturn(1);
        BoardUsageBudget budget = new BoardUsageBudget();
        budget.setReservedCount(1);
        budget.setUsedCount(0);
        when(mapper.selectUsageBudget(
                7L, 11L, IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.MEETING_METRIC,
                LocalDate.of(2026, 7, 20))).thenReturn(budget);
        when(mapper.markOperationReserved(7L, "operation-unicode-name", 0)).thenReturn(1);

        BoardMeetingReservationView result = service.reserve(
                request("operation-unicode-name", 5, 3), 42L);

        assertEquals("RESERVED", result.status());
        ArgumentCaptor<BoardUsageOperationPolicyReceipt> lineage =
                ArgumentCaptor.forClass(BoardUsageOperationPolicyReceipt.class);
        verify(mapper).insertUsageOperationPolicyReceipt(lineage.capture());
        assertEquals(policy.getPolicyDigest(), lineage.getValue().getPolicyDigest());
    }

    @Test
    void currentPolicyRejectsNullableFixedIdentityBeforeAnyOperationWrite() {
        BoardPlanPolicySnapshot drifted = policy("BOARD_FREE");
        drifted.setIdentityVip(null);
        drifted.setVip(null);
        when(entitlementService.getSnapshotForReservation(7L, 42L)).thenReturn(freeSnapshot());
        when(mapper.selectCurrentPlanPolicy(
                IndependentBoardEntitlementService.PRODUCT_CODE, "BOARD_FREE"))
                .thenReturn(drifted);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.reserve(request("operation-null-identity", 5, 3), 42L));

        assertEquals("MEETING_POLICY_CURRENT_READ_FAILED", error.getMessage());
        verify(mapper, never()).insertOperation(any());
    }

    @Test
    void currentFreePolicyRejectsUnlimitedSeatsBeforeAnyOperationWrite() {
        BoardPlanPolicySnapshot drifted = policy("BOARD_FREE");
        drifted.setSeatLimit(null);
        when(entitlementService.getSnapshotForReservation(7L, 42L)).thenReturn(freeSnapshot());
        when(mapper.selectCurrentPlanPolicy(
                IndependentBoardEntitlementService.PRODUCT_CODE, "BOARD_FREE"))
                .thenReturn(drifted);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.reserve(request("operation-free-unlimited", 5, 3), 42L));

        assertEquals("MEETING_POLICY_CURRENT_READ_FAILED", error.getMessage());
        verify(mapper, never()).insertOperation(any());
    }

    @Test
    void nonDuplicateDatabaseErrorsAreSanitizedWithoutIdempotentReplay() {
        when(entitlementService.getSnapshotForReservation(7L, 42L)).thenReturn(freeSnapshot());
        DataIntegrityViolationException failure = new DataIntegrityViolationException(
                "secret foreign key and values");
        when(mapper.insertOperation(any())).thenThrow(failure);

        ServiceException thrown = assertThrows(ServiceException.class,
                () -> service.reserve(request("operation-123", 5, 3), 42L));

        assertEquals(503, thrown.getCode());
        assertEquals("MEETING_RESERVATION_PERSISTENCE_FAILED", thrown.getMessage());
        verify(mapper).selectOperationForUpdate(7L, "operation-123");
        verify(mapper, never()).selectOperationPolicyReceipt(anyLong(), anyString());
    }

    @Test
    void preflightAndPostDuplicateReplayDatabaseErrorsAreSanitized() {
        when(mapper.selectOperationForUpdate(7L, "operation-preflight"))
                .thenThrow(new DataAccessResourceFailureException("secret preflight SQL"));
        ServiceException preflight = assertThrows(ServiceException.class,
                () -> service.reserve(request("operation-preflight", 5, 3), 42L));
        assertEquals(503, preflight.getCode());
        assertEquals("MEETING_RESERVATION_PERSISTENCE_FAILED", preflight.getMessage());

        when(mapper.selectOperationForUpdate(7L, "operation-contended"))
                .thenReturn(null)
                .thenThrow(new DataAccessResourceFailureException("secret replay SQL"));
        when(entitlementService.getSnapshotForReservation(7L, 42L)).thenReturn(freeSnapshot());
        when(mapper.insertOperation(any())).thenThrow(
                new DuplicateKeyException("secret duplicate values"));
        ServiceException replay = assertThrows(ServiceException.class,
                () -> service.reserve(request("operation-contended", 5, 3), 42L));
        assertEquals(503, replay.getCode());
        assertEquals("MEETING_RESERVATION_PERSISTENCE_FAILED", replay.getMessage());
    }

    @Test
    void lockFailuresReturnStableConcurrencyConflictWithoutDatabaseDetail() {
        when(mapper.selectOperationForUpdate(7L, "operation-locked"))
                .thenThrow(new PessimisticLockingFailureException("secret lock owner"));

        ServiceException conflict = assertThrows(ServiceException.class,
                () -> service.reserve(request("operation-locked", 5, 3), 42L));

        assertEquals(409, conflict.getCode());
        assertEquals("MEETING_RESERVATION_CONCURRENT_CONFLICT", conflict.getMessage());
    }

    @Test
    void transactionBoundaryFailuresAreSanitizedAtTheCoordinator() {
        IndependentBoardMeetingTransactionService transaction =
                mock(IndependentBoardMeetingTransactionService.class);
        service = new IndependentBoardMeetingService(mapper, transaction);
        when(transaction.replayCommittedIfPresent(any(), eq(42L)))
                .thenThrow(new TransactionSystemException("secret commit detail"));

        ServiceException failure = assertThrows(ServiceException.class,
                () -> service.reserve(request("operation-transaction", 5, 3), 42L));

        assertEquals(503, failure.getCode());
        assertEquals("MEETING_RESERVATION_PERSISTENCE_FAILED", failure.getMessage());
    }

    @Test
    void exhaustedDailyQuotaRollsBackBeforeOperationCanFinalize() {
        when(entitlementService.getSnapshotForReservation(7L, 42L)).thenReturn(freeSnapshot());
        when(mapper.insertOperation(any())).thenReturn(1);
        when(mapper.prepareUsageBudget(anyLong(), anyLong(), anyString(), anyString(), any(), anyInt()))
                .thenReturn(2);
        when(mapper.reserveOneMeeting(anyLong(), anyLong(), anyString(), anyString(), any(), anyInt()))
                .thenReturn(0);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.reserve(request("operation-123", 5, 3), 42L));

        assertEquals("DAILY_MEETING_QUOTA_EXHAUSTED", error.getMessage());
        verify(mapper, never()).markOperationReserved(anyLong(), anyString(), anyInt());
    }

    @Test
    void lineageWriteFailureEscapesTheSameRollbackForExceptionTransaction() throws Exception {
        when(entitlementService.getSnapshotForReservation(7L, 42L)).thenReturn(freeSnapshot());
        when(mapper.insertOperation(any())).thenReturn(1);
        when(mapper.prepareUsageBudget(anyLong(), anyLong(), anyString(), anyString(), any(), anyInt()))
                .thenReturn(1);
        when(mapper.reserveOneMeeting(anyLong(), anyLong(), anyString(), anyString(), any(), anyInt()))
                .thenReturn(1);
        BoardUsageBudget budget = new BoardUsageBudget();
        budget.setReservedCount(1);
        budget.setUsedCount(0);
        when(mapper.selectUsageBudget(7L, 11L, IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.MEETING_METRIC, LocalDate.of(2026, 7, 20)))
                .thenReturn(budget);
        when(mapper.markOperationReserved(7L, "operation-123", 0)).thenReturn(1);
        when(mapper.insertUsageOperationPolicyReceipt(any())).thenReturn(0);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.reserve(request("operation-123", 5, 3), 42L));

        assertEquals("MEETING_POLICY_LINEAGE_WRITE_FAILED", error.getMessage());
        Transactional fresh = IndependentBoardMeetingTransactionService.class
                .getMethod("reserveFresh", BoardMeetingReservationRequest.class, Long.class)
                .getAnnotation(Transactional.class);
        assertEquals(Propagation.REQUIRED, fresh.propagation());
        assertEquals(List.of(Exception.class), Arrays.asList(fresh.rollbackFor()));
    }

    @Test
    void replayTransactionsAreRequiresNewAndCannotInheritAFailedClaim() throws Exception {
        for (String method : List.of("replayCommittedIfPresent", "replayCommitted")) {
            Transactional replay = IndependentBoardMeetingTransactionService.class
                    .getMethod(method, BoardMeetingReservationRequest.class, Long.class)
                    .getAnnotation(Transactional.class);
            assertEquals(Propagation.REQUIRES_NEW, replay.propagation(), method);
            assertEquals(List.of(Exception.class), Arrays.asList(replay.rollbackFor()), method);
        }
    }

    @Test
    void freePlanRejectsAgendaAndSeatOverflowBeforeAnyOperationWrite() {
        when(entitlementService.getSnapshotForReservation(7L, 42L)).thenReturn(freeSnapshot());

        assertEquals("AGENDA_LIMIT_EXCEEDED", assertThrows(ServiceException.class,
                () -> service.reserve(request("operation-123", 6, 3), 42L)).getMessage());
        assertEquals("SEAT_LIMIT_EXCEEDED", assertThrows(ServiceException.class,
                () -> service.reserve(request("operation-456", 5, 4), 42L)).getMessage());
        verify(mapper, never()).insertOperation(any());
    }

    @Test
    void usesExplicitShanghaiDateWhenUtcAndTenantCalendarCrossMidnight() {
        Instant afterShanghaiMidnight = Instant.parse("2026-07-20T16:30:00Z");
        service = new IndependentBoardMeetingService(mapper, entitlementService,
                Clock.fixed(afterShanghaiMidnight, ZoneId.of("Asia/Shanghai")));
        when(entitlementService.getSnapshotForReservation(7L, 42L)).thenReturn(freeSnapshot());
        when(mapper.insertOperation(any())).thenReturn(1);
        when(mapper.prepareUsageBudget(anyLong(), anyLong(), anyString(), anyString(), any(), anyInt()))
                .thenReturn(1);
        when(mapper.reserveOneMeeting(anyLong(), anyLong(), anyString(), anyString(), any(), anyInt()))
                .thenReturn(1);
        BoardUsageBudget budget = new BoardUsageBudget();
        budget.setReservedCount(1);
        budget.setUsedCount(0);
        when(mapper.selectUsageBudget(7L, 11L, IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.MEETING_METRIC, LocalDate.of(2026, 7, 21)))
                .thenReturn(budget);
        when(mapper.markOperationReserved(7L, "operation-123", 0)).thenReturn(1);

        service.reserve(request("operation-123", 5, 3), 42L);

        verify(mapper).prepareUsageBudget(7L, 11L, IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.MEETING_METRIC, LocalDate.of(2026, 7, 21), 1);
    }

    @Test
    void vipUnlimitedSeatsStillHonorsInitialApiSafetyCeiling() {
        BoardEntitlementSnapshot vip = new BoardEntitlementSnapshot(
                7L, 11L, 42L, "BOARD_VIP", "BOARD_VIP", "ACTIVE",
                5, 30, null, true, true, true, 0, 0, 5);
        when(entitlementService.getSnapshotForReservation(7L, 42L)).thenReturn(vip);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.reserve(request("operation-123", 30, 101), 42L));

        assertEquals("SEAT_SAFETY_LIMIT_EXCEEDED", error.getMessage());
        verify(mapper, never()).insertOperation(any());
    }

    @Test
    void adminOperationAuditUsesFixedScopeAndReturnsA500RowSafeEnvelope() {
        List<BoardUsageOperation> rows = IntStream.rangeClosed(1, 501)
                .mapToObj(index -> auditOperation(String.format("audit-%03d", index)))
                .toList();
        when(mapper.selectOperationsByTenant(
                7L,
                IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.MEETING_METRIC))
                .thenReturn(rows);

        BoardOperationAuditEnvelope result = service.listOperations(7L);

        assertEquals(500, result.limit());
        assertTrue(result.truncated());
        assertEquals(500, result.records().size());
        BoardOperationAuditView first = result.records().get(0);
        assertEquals("audit-001", first.operationId());
        assertEquals(7L, first.tenantId());
        assertEquals(11L, first.memberId());
        assertEquals(42L, first.userId());
        assertEquals("RESERVED", first.status());
        assertEquals("BOARD_FREE", first.effectivePlanCode());
        assertEquals(LocalDate.of(2026, 7, 20), first.bucketDate());
        assertEquals(3, first.agendaCount());
        assertEquals(2, first.seatCount());
        assertEquals(0, first.remainingCount());
        assertEquals(List.of(
                        "operationId", "tenantId", "memberId", "userId", "status",
                        "effectivePlanCode", "bucketDate", "agendaCount", "seatCount",
                        "remainingCount", "createdAt", "updatedAt", "completedAt"),
                Arrays.stream(BoardOperationAuditView.class.getRecordComponents())
                        .map(component -> component.getName()).toList());
        verify(mapper).selectOperationsByTenant(
                7L,
                IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.MEETING_METRIC);
    }

    @Test
    void adminOperationAuditRejectsRowsOutsideTheFixedScope() {
        BoardUsageOperation wrongTenant = auditOperation("audit-tenant");
        wrongTenant.setTenantId(99L);
        BoardUsageOperation wrongProduct = auditOperation("audit-product");
        wrongProduct.setProductCode("ANOTHER_PRODUCT");
        BoardUsageOperation wrongMetric = auditOperation("audit-metric");
        wrongMetric.setMetricCode("ANOTHER_METRIC");
        when(mapper.selectOperationsByTenant(
                7L,
                IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.MEETING_METRIC))
                .thenReturn(List.of(wrongTenant), List.of(wrongProduct), List.of(wrongMetric));

        for (int attempt = 0; attempt < 3; attempt++) {
            ServiceException error = assertThrows(
                    ServiceException.class, () -> service.listOperations(7L));
            assertEquals(500, error.getCode());
            assertEquals("BOARD_OPERATION_AUDIT_SCOPE_INVALID", error.getMessage());
        }
    }

    @Test
    void adminOperationAuditFailsClosedOnAnImpossibleOversizedMapperResult() {
        when(mapper.selectOperationsByTenant(
                7L,
                IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.MEETING_METRIC))
                .thenReturn(IntStream.range(0, 502)
                        .mapToObj(index -> auditOperation("audit-" + index)).toList());

        ServiceException error = assertThrows(
                ServiceException.class, () -> service.listOperations(7L));

        assertEquals(500, error.getCode());
        assertEquals("BOARD_OPERATION_AUDIT_CURRENT_READ_FAILED", error.getMessage());
    }

    @Test
    void adminOperationAuditDoesNotReportTruncationBelowTheLimit() {
        when(mapper.selectOperationsByTenant(
                7L,
                IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.MEETING_METRIC))
                .thenReturn(List.of(auditOperation("audit-one")));

        BoardOperationAuditEnvelope result = service.listOperations(7L);

        assertFalse(result.truncated());
        assertEquals(1, result.records().size());
    }

    private BoardMeetingReservationRequest request(String operationId, int agendas, int seats) {
        return new BoardMeetingReservationRequest(7L, operationId, agendas, seats);
    }

    private BoardEntitlementSnapshot freeSnapshot() {
        return new BoardEntitlementSnapshot(
                7L, 11L, 42L, null, "BOARD_FREE", "FREE",
                1, 5, 3, false, false, false, 0, 0, 1);
    }

    private BoardUsageOperation operation(String operationId, String digest) {
        BoardUsageOperation operation = new BoardUsageOperation();
        operation.setOperationId(operationId);
        operation.setRequestDigest(digest);
        operation.setTenantId(7L);
        operation.setMemberId(11L);
        operation.setUserId(42L);
        operation.setProductCode(IndependentBoardEntitlementService.PRODUCT_CODE);
        operation.setMetricCode(IndependentBoardEntitlementService.MEETING_METRIC);
        operation.setEffectivePlanCode("BOARD_FREE");
        operation.setAgendaCount(5);
        operation.setSeatCount(3);
        return operation;
    }

    private BoardUsageOperation auditOperation(String operationId) {
        BoardUsageOperation operation = operation(operationId, "a".repeat(64));
        operation.setStatus("RESERVED");
        operation.setBucketDate(LocalDate.of(2026, 7, 20));
        operation.setAgendaCount(3);
        operation.setSeatCount(2);
        operation.setRemainingCount(0);
        operation.setCreateTime(new Date(1_790_000_000_000L));
        operation.setUpdateTime(new Date(1_790_000_001_000L));
        operation.setCompletedAt(new Date(1_790_000_002_000L));
        return operation;
    }

    private BoardUsageOperationPolicyReceipt lineage(String operationId) {
        BoardUsageOperationPolicyReceipt lineage = new BoardUsageOperationPolicyReceipt();
        lineage.setTenantId(7L);
        lineage.setOperationId(operationId);
        lineage.setProductCode(IndependentBoardEntitlementService.PRODUCT_CODE);
        lineage.setPlanCode("BOARD_FREE");
        lineage.setPolicyReceiptId("plan-policy-baseline-board-free-v1");
        lineage.setPolicyVersion(1L);
        lineage.setPolicyDigest(FREE_POLICY_DIGEST);
        return lineage;
    }

    private BoardPlanPolicySnapshot policy(String planCode) {
        boolean vip = "BOARD_VIP".equals(planCode);
        BoardPlanPolicySnapshot policy = new BoardPlanPolicySnapshot();
        policy.setReceiptId("plan-policy-baseline-"
                + planCode.toLowerCase().replace('_', '-') + "-v1");
        policy.setProductCode(IndependentBoardEntitlementService.PRODUCT_CODE);
        policy.setPlanCode(planCode);
        policy.setPolicyVersion(1L);
        policy.setPolicyDigest(vip ? VIP_POLICY_DIGEST : FREE_POLICY_DIGEST);
        policy.setPlanName(vip ? "Independent Board VIP" : "Independent Board Free");
        policy.setVip(vip);
        policy.setConnectorRequired(vip);
        policy.setDailyMeetingLimit(vip ? 5 : 1);
        policy.setAgendaLimit(vip ? 30 : 5);
        policy.setSeatLimit(vip ? null : 3);
        policy.setSecretaryEnabled(vip);
        policy.setStatus("ACTIVE");
        policy.setIdentityProductCode(IndependentBoardEntitlementService.PRODUCT_CODE);
        policy.setIdentityPlanCode(planCode);
        policy.setIdentityVip(vip);
        policy.setIdentityConnectorRequired(vip);
        policy.setIdentityStatus("ACTIVE");
        return policy;
    }

    private String reservationDigest(
            BoardMeetingReservationRequest request, Long authenticatedUserId) {
        return BoardDigest.sha256(String.join("\n",
                IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.MEETING_METRIC,
                String.valueOf(request.tenantId()),
                String.valueOf(authenticatedUserId),
                String.valueOf(request.agendaCount()),
                String.valueOf(request.seatCount())));
    }
}
