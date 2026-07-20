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
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

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
        when(mapper.selectActiveMember(7L, 42L)).thenReturn(member(7L, 11L, 42L));

        BoardEntitlementSnapshot snapshot = service.getSnapshot(7L, 42L);

        assertNull(snapshot.grantedPlanCode());
        assertEquals("BOARD_FREE", snapshot.effectivePlanCode());
        assertEquals("FREE", snapshot.activationState());
        assertEquals(1, snapshot.dailyMeetingLimit());
        assertEquals(5, snapshot.agendaLimit());
        assertEquals(3, snapshot.seatLimit());
        assertFalse(snapshot.secretaryEnabled());
        assertEquals(1, snapshot.remainingCount());
        verify(mapper).selectActiveMember(7L, 42L);
    }

    @Test
    void vipGrantWithoutConnectorVerificationFailsClosedToFree() {
        when(mapper.selectActiveMember(7L, 42L)).thenReturn(member(7L, 11L, 42L));
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
        when(mapper.selectActiveMember(7L, 42L)).thenReturn(member(7L, 11L, 42L));
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
        when(mapper.selectActiveMember(99L, 42L)).thenReturn(null);

        ServiceException error = assertThrows(ServiceException.class, () -> service.getSnapshot(99L, 42L));

        assertEquals(403, error.getCode());
        assertEquals("TENANT_MEMBER_USER_SCOPE_INVALID", error.getMessage());
        verify(mapper, never()).selectEntitlement(any(), any(), any(), any());
    }

    @Test
    void grantValidatesExactTenantMemberUserAndWritesImmutableAuditReceipt() {
        when(mapper.selectExactActiveMember(7L, 11L, 42L)).thenReturn(member(7L, 11L, 42L));
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
    }

    @Test
    void grantFailsClosedOnOptimisticVersionConflict() {
        when(mapper.selectExactActiveMember(7L, 11L, 42L)).thenReturn(member(7L, 11L, 42L));
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
        when(mapper.selectExactActiveMember(7L, 11L, 42L)).thenReturn(member(7L, 11L, 42L));
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
