package com.wx.fbsir.business.board.service;

import com.wx.fbsir.business.board.domain.BoardEnterpriseMemberScope;
import com.wx.fbsir.business.board.domain.BoardProductEntitlement;
import com.wx.fbsir.business.board.domain.BoardProductPlan;
import com.wx.fbsir.business.board.dto.BoardEntitlementAdminView;
import com.wx.fbsir.business.board.dto.BoardEntitlementGrantRequest;
import com.wx.fbsir.business.board.dto.BoardEntitlementSnapshot;
import com.wx.fbsir.business.board.mapper.IndependentBoardMapper;
import com.wx.fbsir.common.exception.ServiceException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;

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

class IndependentBoardEntitlementServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-20T12:00:00Z");
    private IndependentBoardMapper mapper;
    private IndependentBoardEntitlementService service;

    @BeforeEach
    void setUp() {
        mapper = mock(IndependentBoardMapper.class);
        service = new IndependentBoardEntitlementService(mapper, Clock.fixed(NOW, ZoneOffset.UTC));
        when(mapper.selectActivePlan(IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.FREE_PLAN)).thenReturn(freePlan());
        when(mapper.selectActivePlan(IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.VIP_PLAN)).thenReturn(vipPlan());
    }

    @Test
    void defaultsToExactFreePolicyForServerDerivedMember() {
        when(mapper.selectActiveContext(7L, 42L)).thenReturn(member(7L, 11L, 42L));

        BoardEntitlementSnapshot snapshot = service.getSnapshot(7L, 42L);

        assertNull(snapshot.grantedPlanCode());
        assertEquals("BOARD_FREE", snapshot.effectivePlanCode());
        assertEquals("FREE", snapshot.activationState());
        assertEquals(1, snapshot.dailyMeetingLimit());
        assertEquals(5, snapshot.agendaLimit());
        assertEquals(3, snapshot.seatLimit());
        assertFalse(snapshot.secretaryEnabled());
        assertEquals(1, snapshot.remainingCount());
        verify(mapper).selectActiveContext(7L, 42L);
    }

    @Test
    void vipGrantWithoutConnectorVerificationFailsClosedToFree() {
        when(mapper.selectActiveContext(7L, 42L)).thenReturn(member(7L, 11L, 42L));
        when(mapper.selectEntitlement(7L, 11L, 42L, IndependentBoardEntitlementService.PRODUCT_CODE))
                .thenReturn(entitlement(null, null));

        BoardEntitlementSnapshot snapshot = service.getSnapshot(7L, 42L);

        assertEquals("BOARD_VIP", snapshot.grantedPlanCode());
        assertEquals("BOARD_FREE", snapshot.effectivePlanCode());
        assertEquals("PENDING_CONNECTOR", snapshot.activationState());
        assertTrue(snapshot.connectorRequired());
        assertFalse(snapshot.connectorVerified());
        assertEquals(1, snapshot.dailyMeetingLimit());
    }

    @Test
    void legacyBindingFieldsCannotActivateVipBeforeAuthoritativeW4CurrentRead() {
        when(mapper.selectActiveContext(7L, 42L)).thenReturn(member(7L, 11L, 42L));
        when(mapper.selectEntitlement(7L, 11L, 42L, IndependentBoardEntitlementService.PRODUCT_CODE))
                .thenReturn(entitlement("connector-1", Date.from(NOW.minusSeconds(60))));

        BoardEntitlementSnapshot snapshot = service.getSnapshot(7L, 42L);

        assertEquals("BOARD_VIP", snapshot.grantedPlanCode());
        assertEquals("BOARD_FREE", snapshot.effectivePlanCode());
        assertEquals("PENDING_CONNECTOR", snapshot.activationState());
        assertEquals(1, snapshot.dailyMeetingLimit());
        assertEquals(5, snapshot.agendaLimit());
        assertEquals(3, snapshot.seatLimit());
        assertFalse(snapshot.secretaryEnabled());
        assertFalse(snapshot.connectorVerified());
    }

    @Test
    void rejectsClientTenantWhenMembershipDoesNotMatchAuthenticatedUser() {
        when(mapper.selectActiveContext(99L, 42L)).thenReturn(null);

        ServiceException error = assertThrows(ServiceException.class, () -> service.getSnapshot(99L, 42L));

        assertEquals(403, error.getCode());
        assertEquals("TENANT_MEMBER_USER_SCOPE_INVALID", error.getMessage());
        verify(mapper, never()).selectActivePlan(any(), any());
        verify(mapper, never()).selectEntitlement(any(), any(), any(), any());
        verify(mapper, never()).selectUsageBudget(any(), any(), any(), any(), any());
    }

    @Test
    void grantValidatesExactTenantMemberUserAndWritesImmutableAuditReceipt() {
        when(mapper.selectExactActiveMemberForUpdate(7L, 11L, 42L))
                .thenReturn(member(7L, 11L, 42L));
        when(mapper.selectEntitlementForUpdate(7L, 11L, IndependentBoardEntitlementService.PRODUCT_CODE))
                .thenReturn(null);
        when(mapper.insertEntitlement(any())).thenReturn(1);
        when(mapper.insertEntitlementReceipt(any())).thenReturn(1);
        BoardEntitlementGrantRequest request = new BoardEntitlementGrantRequest(
                7L, 11L, 42L, "BOARD_VIP", Date.from(NOW.plusSeconds(3600)), 0L);

        BoardEntitlementAdminView view = service.grant(request, 900L);

        assertEquals("BOARD_VIP", view.planCode());
        assertEquals("PENDING_CONNECTOR", view.activationState());
        assertEquals(1L, view.version());
        ArgumentCaptor<com.wx.fbsir.business.board.domain.BoardEntitlementReceipt> receipt =
                ArgumentCaptor.forClass(com.wx.fbsir.business.board.domain.BoardEntitlementReceipt.class);
        verify(mapper).insertEntitlementReceipt(receipt.capture());
        assertEquals(900L, receipt.getValue().getActorUserId());
        assertEquals("ENTITLEMENT_GRANTED", receipt.getValue().getAction());
        assertEquals("ACTION_COMPLETED", receipt.getValue().getEvidenceLevel());
        assertEquals(64, receipt.getValue().getPayloadDigest().length());
        verify(mapper, never()).selectActiveContext(any(), any());
    }

    @Test
    void listEntitlementsReturnsOnlyTheBoundedSafeManagementProjection() {
        BoardProductEntitlement entitlement = entitlement(
                "legacy-binding-must-not-leak", Date.from(NOW.minusSeconds(60)));
        when(mapper.selectEntitlementsByTenant(
                7L, IndependentBoardEntitlementService.PRODUCT_CODE))
                .thenReturn(List.of(entitlement));

        List<BoardEntitlementAdminView> result = service.listEntitlements(7L);

        assertEquals(1, result.size());
        BoardEntitlementAdminView view = result.get(0);
        assertEquals(7L, view.tenantId());
        assertEquals(11L, view.memberId());
        assertEquals(42L, view.userId());
        assertEquals("BOARD_VIP", view.planCode());
        assertEquals("ACTIVE", view.entitlementStatus());
        assertEquals("PENDING_CONNECTOR", view.activationState());
        assertEquals(Date.from(NOW.minusSeconds(600)), view.validFrom());
        assertEquals(Date.from(NOW.plusSeconds(3600)), view.validUntil());
        assertEquals(1L, view.version());
        assertEquals(Date.from(NOW.minusSeconds(100)), view.updatedAt());
        assertEquals(List.of(
                        "tenantId", "memberId", "userId", "planCode", "entitlementStatus",
                        "activationState", "validFrom", "validUntil", "version",
                        "updatedAt"),
                Arrays.stream(BoardEntitlementAdminView.class.getRecordComponents())
                        .map(component -> component.getName()).toList());
    }

    @Test
    void entitlementListFailsClosedWhenTheMapperSignalsMoreThanOneHundredRows() {
        when(mapper.selectEntitlementsByTenant(
                7L, IndependentBoardEntitlementService.PRODUCT_CODE))
                .thenReturn(IntStream.range(0, 101)
                        .mapToObj(index -> entitlement(null, null)).toList());

        ServiceException error = assertThrows(
                ServiceException.class, () -> service.listEntitlements(7L));

        assertEquals(500, error.getCode());
        assertEquals("BOARD_ENTITLEMENT_LIMIT_EXCEEDED", error.getMessage());
        verify(mapper, never()).selectActivePlan(any(), any());
    }

    @Test
    void entitlementListRejectsMapperRowsOutsideTheRequestedProductScope() {
        BoardProductEntitlement leaked = entitlement(null, null);
        leaked.setProductCode("ANOTHER_PRODUCT");
        when(mapper.selectEntitlementsByTenant(
                7L, IndependentBoardEntitlementService.PRODUCT_CODE))
                .thenReturn(List.of(leaked));

        ServiceException error = assertThrows(
                ServiceException.class, () -> service.listEntitlements(7L));

        assertEquals(500, error.getCode());
        assertEquals("BOARD_ENTITLEMENT_SCOPE_INVALID", error.getMessage());
        verify(mapper, never()).selectActivePlan(any(), any());
    }

    @Test
    void inactiveEnterpriseOrMemberRejectsGrantBeforeAnyEntitlementWrite() {
        when(mapper.selectExactActiveMemberForUpdate(7L, 11L, 42L)).thenReturn(null);
        BoardEntitlementGrantRequest request = new BoardEntitlementGrantRequest(
                7L, 11L, 42L, "BOARD_VIP", null, 0L);

        ServiceException error = assertThrows(
                ServiceException.class, () -> service.grant(request, 900L));

        assertEquals(403, error.getCode());
        assertEquals("TENANT_MEMBER_USER_SCOPE_INVALID", error.getMessage());
        verify(mapper, never()).selectEntitlementForUpdate(any(), any(), any());
        verify(mapper, never()).insertEntitlement(any());
        verify(mapper, never()).updateEntitlementIfVersion(any(), any());
        verify(mapper, never()).insertEntitlementReceipt(any());
    }

    @Test
    void grantFailsClosedOnOptimisticVersionConflict() {
        when(mapper.selectExactActiveMemberForUpdate(7L, 11L, 42L))
                .thenReturn(member(7L, 11L, 42L));
        BoardProductEntitlement current = entitlement("connector-1", Date.from(NOW.minusSeconds(60)));
        current.setId(8L);
        current.setVersion(3L);
        when(mapper.selectEntitlementForUpdate(7L, 11L, IndependentBoardEntitlementService.PRODUCT_CODE))
                .thenReturn(current);
        BoardEntitlementGrantRequest request = new BoardEntitlementGrantRequest(
                7L, 11L, 42L, "BOARD_VIP", null, 2L);

        ServiceException error = assertThrows(ServiceException.class, () -> service.grant(request, 900L));

        assertEquals(409, error.getCode());
        verify(mapper, never()).updateEntitlementIfVersion(any(), any());
        verify(mapper, never()).insertEntitlementReceipt(any());
    }

    @Test
    void concurrentFirstGrantDuplicateIsReportedAsVersionConflictWithoutAudit() {
        when(mapper.selectExactActiveMemberForUpdate(7L, 11L, 42L))
                .thenReturn(member(7L, 11L, 42L));
        when(mapper.selectEntitlementForUpdate(7L, 11L, IndependentBoardEntitlementService.PRODUCT_CODE))
                .thenReturn(null);
        when(mapper.insertEntitlement(any())).thenThrow(new DuplicateKeyException("concurrent grant"));
        BoardEntitlementGrantRequest request = new BoardEntitlementGrantRequest(
                7L, 11L, 42L, "BOARD_VIP", null, 0L);

        ServiceException error = assertThrows(ServiceException.class, () -> service.grant(request, 900L));

        assertEquals(409, error.getCode());
        assertEquals("ENTITLEMENT_VERSION_CONFLICT", error.getMessage());
        verify(mapper, never()).insertEntitlementReceipt(any());
    }

    @Test
    void concurrentFirstGrantDeadlockIsReportedAsVersionConflictWithoutAudit() {
        when(mapper.selectExactActiveMemberForUpdate(7L, 11L, 42L))
                .thenReturn(member(7L, 11L, 42L));
        when(mapper.selectEntitlementForUpdate(7L, 11L, IndependentBoardEntitlementService.PRODUCT_CODE))
                .thenReturn(null);
        when(mapper.insertEntitlement(any()))
                .thenThrow(new PessimisticLockingFailureException("concurrent grant"));
        BoardEntitlementGrantRequest request = new BoardEntitlementGrantRequest(
                7L, 11L, 42L, "BOARD_VIP", null, 0L);

        ServiceException error = assertThrows(ServiceException.class, () -> service.grant(request, 900L));

        assertEquals(409, error.getCode());
        assertEquals("ENTITLEMENT_VERSION_CONFLICT", error.getMessage());
        verify(mapper, never()).insertEntitlementReceipt(any());
    }

    private BoardEnterpriseMemberScope member(long tenantId, long memberId, long userId) {
        BoardEnterpriseMemberScope member = new BoardEnterpriseMemberScope();
        member.setTenantId(tenantId);
        member.setMemberId(memberId);
        member.setUserId(userId);
        member.setStatus(1);
        member.setDelFlag("0");
        return member;
    }

    private BoardProductEntitlement entitlement(String bindingId, Date verifiedAt) {
        BoardProductEntitlement entitlement = new BoardProductEntitlement();
        entitlement.setId(8L);
        entitlement.setTenantId(7L);
        entitlement.setMemberId(11L);
        entitlement.setUserId(42L);
        entitlement.setProductCode(IndependentBoardEntitlementService.PRODUCT_CODE);
        entitlement.setPlanCode("BOARD_VIP");
        entitlement.setStatus("ACTIVE");
        entitlement.setConnectorBindingId(bindingId);
        entitlement.setConnectorVerifiedAt(verifiedAt);
        entitlement.setValidFrom(Date.from(NOW.minusSeconds(600)));
        entitlement.setValidUntil(Date.from(NOW.plusSeconds(3600)));
        entitlement.setVersion(1L);
        entitlement.setCreatedAt(Date.from(NOW.minusSeconds(700)));
        entitlement.setUpdatedAt(Date.from(NOW.minusSeconds(100)));
        return entitlement;
    }

    private BoardProductPlan freePlan() {
        return plan("BOARD_FREE", false, false, 1, 5, 3, false);
    }

    private BoardProductPlan vipPlan() {
        return plan("BOARD_VIP", true, true, 5, 30, null, true);
    }

    private BoardProductPlan plan(
            String code, boolean vip, boolean connectorRequired, int daily,
            int agenda, Integer seats, boolean secretary) {
        BoardProductPlan plan = new BoardProductPlan();
        plan.setProductCode(IndependentBoardEntitlementService.PRODUCT_CODE);
        plan.setPlanCode(code);
        plan.setVip(vip);
        plan.setConnectorRequired(connectorRequired);
        plan.setDailyMeetingLimit(daily);
        plan.setAgendaLimit(agenda);
        plan.setSeatLimit(seats);
        plan.setSecretaryEnabled(secretary);
        plan.setStatus("ACTIVE");
        return plan;
    }
}
