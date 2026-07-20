package com.wx.fbsir.business.board.service;

import com.wx.fbsir.business.board.domain.BoardEnterpriseMemberScope;
import com.wx.fbsir.business.board.domain.BoardUsageOperation;
import com.wx.fbsir.business.board.dto.BoardDashboardView;
import com.wx.fbsir.business.board.dto.BoardEnterpriseContextView;
import com.wx.fbsir.business.board.dto.BoardEntitlementSnapshot;
import com.wx.fbsir.business.board.dto.BoardMeetingLookupView;
import com.wx.fbsir.business.board.dto.BoardRecentMeetingView;
import com.wx.fbsir.business.board.mapper.IndependentBoardMapper;
import com.wx.fbsir.common.exception.ServiceException;
import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IndependentBoardDashboardServiceTest {
    private static final long TENANT_ID = 7L;
    private static final long MEMBER_ID = 11L;
    private static final long USER_ID = 42L;

    private IndependentBoardMapper mapper;
    private IndependentBoardEntitlementService entitlementService;
    private IndependentBoardDashboardService service;

    @BeforeEach
    void setUp() {
        mapper = mock(IndependentBoardMapper.class);
        entitlementService = mock(IndependentBoardEntitlementService.class);
        service = new IndependentBoardDashboardService(mapper, entitlementService);
    }

    @Test
    void contextsAreDerivedOnlyFromTheAuthenticatedUserCurrentRead() {
        when(mapper.selectActiveContextsByUser(USER_ID)).thenReturn(List.of(
                context(TENANT_ID, MEMBER_ID, USER_ID, "福帮手", "ADMIN"),
                context(8L, 12L, USER_ID, "示例企业", "MEMBER")));

        List<BoardEnterpriseContextView> result = service.listContexts(USER_ID);

        assertEquals(2, result.size());
        assertEquals(new BoardEnterpriseContextView(TENANT_ID, MEMBER_ID, "福帮手", "ADMIN"),
                result.get(0));
        assertEquals(8L, result.get(1).tenantId());
        verify(mapper).selectActiveContextsByUser(USER_ID);
    }

    @Test
    void contextCurrentReadFailsClosedIfMapperReturnsAnotherUsersMembership() {
        when(mapper.selectActiveContextsByUser(USER_ID)).thenReturn(List.of(
                context(TENANT_ID, MEMBER_ID, 999L, "越权企业", "MEMBER")));

        ServiceException error = assertThrows(
                ServiceException.class, () -> service.listContexts(USER_ID));

        assertEquals(403, error.getCode());
        assertEquals("TENANT_MEMBER_USER_SCOPE_INVALID", error.getMessage());
    }

    @Test
    void contextCurrentReadFailsClosedInsteadOfSilentlyTruncatingMoreThanOneHundredRows() {
        when(mapper.selectActiveContextsByUser(USER_ID)).thenReturn(
                IntStream.rangeClosed(1, 101)
                        .mapToObj(index -> context(
                                (long) index, (long) index, USER_ID,
                                "Tenant " + index, "MEMBER"))
                        .toList());

        ServiceException error = assertThrows(
                ServiceException.class, () -> service.listContexts(USER_ID));

        assertEquals(500, error.getCode());
        assertEquals("BOARD_CONTEXT_LIMIT_EXCEEDED", error.getMessage());
    }

    @Test
    void dashboardMapsPendingConnectorAndKeepsFutureCapabilitiesComingSoon() {
        BoardEnterpriseMemberScope context = context(
                TENANT_ID, MEMBER_ID, USER_ID, "福帮手", "MEMBER");
        BoardEntitlementSnapshot entitlement = snapshot(
                "BOARD_VIP", "BOARD_FREE", "PENDING_CONNECTOR", true, false);
        BoardUsageOperation recent = operation(TENANT_ID, USER_ID, "meeting-001");
        when(mapper.selectActiveContext(TENANT_ID, USER_ID)).thenReturn(context);
        when(entitlementService.getSnapshot(TENANT_ID, USER_ID)).thenReturn(entitlement);
        when(mapper.selectRecentOperationsByTenantAndUser(
                TENANT_ID, USER_ID,
                IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.MEETING_METRIC))
                .thenReturn(List.of(recent));

        BoardDashboardView result = service.getDashboard(TENANT_ID, USER_ID);

        assertEquals(new BoardEnterpriseContextView(
                TENANT_ID, MEMBER_ID, "福帮手", "MEMBER"), result.context());
        assertEquals(entitlement, result.entitlement());
        assertEquals(IndependentBoardDashboardService.CONNECTOR_PENDING, result.connectorState());
        assertEquals(IndependentBoardDashboardService.COMING_SOON, result.webhookState());
        assertEquals(IndependentBoardDashboardService.COMING_SOON, result.watchState());
        assertEquals(1, result.recentMeetings().size());
        assertEquals("meeting-001", result.recentMeetings().get(0).operationId());
        assertEquals(LocalDate.of(2026, 7, 20), result.recentMeetings().get(0).bucketDate());
        assertEquals(recent.getCreateTime(), result.recentMeetings().get(0).createdAt());
    }

    @Test
    void dashboardRejectsCrossTenantBeforeEntitlementOrMeetingReads() {
        long foreignTenant = 99L;
        when(mapper.selectActiveContext(foreignTenant, USER_ID)).thenReturn(null);

        ServiceException error = assertThrows(
                ServiceException.class, () -> service.getDashboard(foreignTenant, USER_ID));

        assertEquals(403, error.getCode());
        assertEquals("TENANT_MEMBER_USER_SCOPE_INVALID", error.getMessage());
        verify(entitlementService, never()).getSnapshot(any(), any());
        verify(mapper, never()).selectRecentOperationsByTenantAndUser(any(), any(), any(), any());
    }

    @Test
    void dashboardFailsClosedIfRecentMeetingReadLeaksAnotherUser() {
        when(mapper.selectActiveContext(TENANT_ID, USER_ID)).thenReturn(
                context(TENANT_ID, MEMBER_ID, USER_ID, "福帮手", "MEMBER"));
        when(entitlementService.getSnapshot(TENANT_ID, USER_ID)).thenReturn(
                snapshot(null, "BOARD_FREE", "FREE", false, false));
        when(mapper.selectRecentOperationsByTenantAndUser(
                TENANT_ID, USER_ID,
                IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.MEETING_METRIC))
                .thenReturn(List.of(operation(TENANT_ID, 999L, "leaked-meeting")));

        ServiceException error = assertThrows(
                ServiceException.class, () -> service.getDashboard(TENANT_ID, USER_ID));

        assertEquals(500, error.getCode());
        assertEquals("BOARD_RECENT_MEETING_SCOPE_INVALID", error.getMessage());
    }

    @Test
    void connectorIsActiveOnlyWhenEntitlementIsBothVerifiedAndActive() {
        when(mapper.selectActiveContext(TENANT_ID, USER_ID)).thenReturn(
                context(TENANT_ID, MEMBER_ID, USER_ID, "福帮手", "MEMBER"));
        when(mapper.selectRecentOperationsByTenantAndUser(
                TENANT_ID, USER_ID,
                IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.MEETING_METRIC))
                .thenReturn(List.of());
        when(entitlementService.getSnapshot(TENANT_ID, USER_ID))
                .thenReturn(snapshot("BOARD_VIP", "BOARD_VIP", "ACTIVE", true, false))
                .thenReturn(snapshot("BOARD_VIP", "BOARD_VIP", "ACTIVE", true, true));

        assertEquals(IndependentBoardDashboardService.CONNECTOR_NOT_CONNECTED,
                service.getDashboard(TENANT_ID, USER_ID).connectorState());
        assertEquals(IndependentBoardDashboardService.CONNECTOR_ACTIVE,
                service.getDashboard(TENANT_ID, USER_ID).connectorState());
    }

    @Test
    void exactMeetingReadReturnsTheSafeProjectionForTheAuthenticatedUser() {
        when(mapper.selectActiveContext(TENANT_ID, USER_ID)).thenReturn(
                context(TENANT_ID, MEMBER_ID, USER_ID, "福帮手", "MEMBER"));
        when(mapper.selectOperation(TENANT_ID, "meeting-001"))
                .thenReturn(operation(TENANT_ID, USER_ID, "meeting-001"));

        BoardMeetingLookupView result = service.getMeetingReservation(
                TENANT_ID, "meeting-001", USER_ID);

        assertTrue(result.found());
        assertEquals("meeting-001", result.meeting().operationId());
        assertEquals("RESERVED", result.meeting().status());
        assertEquals(LocalDate.of(2026, 7, 20), result.meeting().bucketDate());
        verify(mapper).selectOperation(TENANT_ID, "meeting-001");
    }

    @Test
    void exactMeetingReadUsesTheSameNonLeakingResultForMissingAndOtherUsersOperations() {
        when(mapper.selectActiveContext(TENANT_ID, USER_ID)).thenReturn(
                context(TENANT_ID, MEMBER_ID, USER_ID, "福帮手", "MEMBER"));
        when(mapper.selectOperation(TENANT_ID, "missing-001")).thenReturn(null);
        when(mapper.selectOperation(TENANT_ID, "private-001"))
                .thenReturn(operation(TENANT_ID, 999L, "private-001"));

        BoardMeetingLookupView missing = service.getMeetingReservation(
                TENANT_ID, "missing-001", USER_ID);
        BoardMeetingLookupView privateOperation = service.getMeetingReservation(
                TENANT_ID, "private-001", USER_ID);

        assertFalse(missing.found());
        assertNull(missing.meeting());
        assertEquals(missing, privateOperation);
    }

    @Test
    void exactMeetingReadRejectsCrossTenantBeforeLookingUpTheOperation() {
        long foreignTenant = 99L;
        when(mapper.selectActiveContext(foreignTenant, USER_ID)).thenReturn(null);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.getMeetingReservation(foreignTenant, "meeting-001", USER_ID));

        assertEquals(403, error.getCode());
        assertEquals("TENANT_MEMBER_USER_SCOPE_INVALID", error.getMessage());
        verify(mapper, never()).selectOperation(any(), any());
    }

    @Test
    void exactMeetingReadFailsClosedForAnotherProductOrMetric() {
        when(mapper.selectActiveContext(TENANT_ID, USER_ID)).thenReturn(
                context(TENANT_ID, MEMBER_ID, USER_ID, "福帮手", "MEMBER"));
        BoardUsageOperation otherProduct = operation(TENANT_ID, USER_ID, "product-001");
        otherProduct.setProductCode("ANOTHER_PRODUCT");
        BoardUsageOperation otherMetric = operation(TENANT_ID, USER_ID, "metric--001");
        otherMetric.setMetricCode("ANOTHER_METRIC");
        when(mapper.selectOperation(TENANT_ID, "product-001")).thenReturn(otherProduct);
        when(mapper.selectOperation(TENANT_ID, "metric--001")).thenReturn(otherMetric);

        BoardMeetingLookupView productResult = service.getMeetingReservation(
                TENANT_ID, "product-001", USER_ID);
        BoardMeetingLookupView metricResult = service.getMeetingReservation(
                TENANT_ID, "metric--001", USER_ID);

        assertFalse(productResult.found());
        assertNull(productResult.meeting());
        assertEquals(productResult, metricResult);
    }

    @Test
    void exactMeetingReadRejectsInvalidOperationIdBeforeAnyScopeOrDataRead() {
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.getMeetingReservation(TENANT_ID, "short", USER_ID));

        assertEquals(400, error.getCode());
        assertEquals("OPERATION_ID_INVALID", error.getMessage());
        verify(mapper, never()).selectActiveContext(any(), any());
        verify(mapper, never()).selectOperation(any(), any());
    }

    @Test
    void publicDtoShapesRemainExactAndDoNotExposeUserMemberOrDigestInternals() {
        assertArrayEquals(
                new String[] {"tenantId", "memberId", "tenantName", "memberRole"},
                components(BoardEnterpriseContextView.class));
        assertArrayEquals(
                new String[] {"found", "meeting"},
                components(BoardMeetingLookupView.class));
        assertArrayEquals(
                new String[] {"operationId", "status", "effectivePlanCode", "agendaCount",
                        "seatCount", "remainingCount", "bucketDate", "createdAt"},
                components(BoardRecentMeetingView.class));
        assertArrayEquals(
                new String[] {"context", "entitlement", "recentMeetings", "connectorState",
                        "webhookState", "watchState"},
                components(BoardDashboardView.class));
    }

    private String[] components(Class<?> recordType) {
        Set<String> forbidden = Set.of("userId", "requestDigest", "id", "completedAt");
        String[] result = Arrays.stream(recordType.getRecordComponents())
                .map(RecordComponent::getName)
                .toArray(String[]::new);
        for (String component : result) {
            if (forbidden.contains(component)) {
                throw new AssertionError("public dashboard DTO exposes internal field: " + component);
            }
        }
        return result;
    }

    private BoardEnterpriseMemberScope context(
            long tenantId, long memberId, long userId, String tenantName, String memberRole) {
        BoardEnterpriseMemberScope context = new BoardEnterpriseMemberScope();
        context.setTenantId(tenantId);
        context.setMemberId(memberId);
        context.setTenantName(tenantName);
        context.setUserId(userId);
        context.setMemberRole(memberRole);
        context.setStatus(1);
        context.setDelFlag("0");
        return context;
    }

    private BoardEntitlementSnapshot snapshot(
            String grantedPlan, String effectivePlan, String activationState,
            boolean connectorRequired, boolean connectorVerified) {
        return new BoardEntitlementSnapshot(
                TENANT_ID, MEMBER_ID, USER_ID, grantedPlan, effectivePlan, activationState,
                "BOARD_VIP".equals(effectivePlan) ? 5 : 1,
                "BOARD_VIP".equals(effectivePlan) ? 30 : 5,
                "BOARD_VIP".equals(effectivePlan) ? null : 3,
                "BOARD_VIP".equals(effectivePlan), connectorRequired, connectorVerified,
                0, 0, "BOARD_VIP".equals(effectivePlan) ? 5 : 1);
    }

    private BoardUsageOperation operation(long tenantId, long userId, String operationId) {
        BoardUsageOperation operation = new BoardUsageOperation();
        operation.setOperationId(operationId);
        operation.setTenantId(tenantId);
        operation.setMemberId(MEMBER_ID);
        operation.setUserId(userId);
        operation.setProductCode(IndependentBoardEntitlementService.PRODUCT_CODE);
        operation.setMetricCode(IndependentBoardEntitlementService.MEETING_METRIC);
        operation.setStatus("RESERVED");
        operation.setEffectivePlanCode("BOARD_FREE");
        operation.setAgendaCount(3);
        operation.setSeatCount(2);
        operation.setRemainingCount(0);
        operation.setBucketDate(LocalDate.of(2026, 7, 20));
        operation.setCreateTime(new Date(1_790_000_000_000L));
        operation.setRequestDigest("a".repeat(64));
        return operation;
    }
}
