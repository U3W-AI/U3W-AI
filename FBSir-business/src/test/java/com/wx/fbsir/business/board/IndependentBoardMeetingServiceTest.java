package com.wx.fbsir.business.board.service;

import com.wx.fbsir.business.board.domain.BoardUsageBudget;
import com.wx.fbsir.business.board.domain.BoardUsageOperation;
import com.wx.fbsir.business.board.dto.BoardEntitlementSnapshot;
import com.wx.fbsir.business.board.dto.BoardMeetingReservationRequest;
import com.wx.fbsir.business.board.dto.BoardMeetingReservationView;
import com.wx.fbsir.business.board.dto.BoardOperationAuditEnvelope;
import com.wx.fbsir.business.board.dto.BoardOperationAuditView;
import com.wx.fbsir.business.board.mapper.IndependentBoardMapper;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IndependentBoardMeetingServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-20T12:00:00Z");
    private IndependentBoardMapper mapper;
    private IndependentBoardEntitlementService entitlementService;
    private IndependentBoardMeetingService service;

    @BeforeEach
    void setUp() {
        mapper = mock(IndependentBoardMapper.class);
        entitlementService = mock(IndependentBoardEntitlementService.class);
        service = new IndependentBoardMeetingService(
                mapper, entitlementService, Clock.fixed(NOW, ZoneOffset.UTC));
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
        when(mapper.selectOperationForUpdate(7L, "operation-123")).thenAnswer(invocation -> {
            BoardUsageOperation existing = attempted.get();
            existing.setStatus("RESERVED");
            existing.setRemainingCount(0);
            return existing;
        });

        BoardMeetingReservationView result = service.reserve(request, 42L);

        assertEquals("RESERVED", result.status());
        assertEquals(0, result.remainingCount());
        verify(mapper, never()).prepareUsageBudget(anyLong(), anyLong(), anyString(), anyString(), any(), anyInt());
        verify(mapper, never()).reserveOneMeeting(anyLong(), anyLong(), anyString(), anyString(), any(), anyInt());
    }

    @Test
    void sameOperationWithDifferentDigestFailsClosed() {
        when(entitlementService.getSnapshotForReservation(7L, 42L)).thenReturn(freeSnapshot());
        when(mapper.insertOperation(any())).thenThrow(new DuplicateKeyException("duplicate operation"));
        BoardUsageOperation existing = operation("operation-123", "0".repeat(64));
        existing.setStatus("RESERVED");
        when(mapper.selectOperationForUpdate(7L, "operation-123")).thenReturn(existing);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.reserve(request("operation-123", 5, 3), 42L));

        assertEquals(409, error.getCode());
        assertEquals("IDEMPOTENCY_DIGEST_CONFLICT", error.getMessage());
        verify(mapper, never()).reserveOneMeeting(anyLong(), anyLong(), anyString(), anyString(), any(), anyInt());
    }

    @Test
    void nonDuplicateDatabaseErrorsAreNotMisclassifiedAsIdempotentReplay() {
        when(entitlementService.getSnapshotForReservation(7L, 42L)).thenReturn(freeSnapshot());
        DataIntegrityViolationException failure = new DataIntegrityViolationException("foreign key failure");
        when(mapper.insertOperation(any())).thenThrow(failure);

        DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
                () -> service.reserve(request("operation-123", 5, 3), 42L));

        assertSame(failure, thrown);
        verify(mapper, never()).selectOperationForUpdate(anyLong(), anyString());
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
}
