package com.wx.fbsir.business.board.service;

import com.wx.fbsir.business.board.domain.BoardEnterpriseMemberScope;
import com.wx.fbsir.business.board.domain.BoardEntitlementReceipt;
import com.wx.fbsir.business.board.domain.BoardProductEntitlement;
import com.wx.fbsir.business.board.domain.BoardProductPlan;
import com.wx.fbsir.business.board.dto.BoardEntitlementAdminView;
import com.wx.fbsir.business.board.dto.BoardEntitlementGrantRequest;
import com.wx.fbsir.business.board.dto.BoardProductPlanAdminView;
import com.wx.fbsir.business.board.dto.BoardEntitlementReceiptAuditEnvelope;
import com.wx.fbsir.business.board.dto.BoardEntitlementReceiptView;
import com.wx.fbsir.business.board.dto.BoardEntitlementRevokeRequest;
import com.wx.fbsir.business.board.dto.BoardEntitlementSnapshot;
import com.wx.fbsir.business.board.mapper.IndependentBoardMapper;
import com.wx.fbsir.common.exception.ServiceException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IndependentBoardEntitlementServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-20T12:00:00Z");
    private IndependentBoardMapper mapper;
    private BoardConnectorBindingPort connectorBindingPort;
    private IndependentBoardEntitlementService service;

    @BeforeEach
    void setUp() {
        mapper = mock(IndependentBoardMapper.class);
        connectorBindingPort = mock(BoardConnectorBindingPort.class);
        service = new IndependentBoardEntitlementService(
                mapper, Clock.fixed(NOW, ZoneOffset.UTC), connectorBindingPort);
        when(connectorBindingPort.selectAuthoritativeCurrentBindingKeys(
                any(), any())).thenReturn(Set.of());
        when(connectorBindingPort.hasAuthoritativeCurrentBinding(
                any(), any(), any(), any(), anyBoolean())).thenReturn(false);
        when(mapper.selectActivePlan(IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.FREE_PLAN)).thenReturn(freePlan());
        when(mapper.selectActivePlan(IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.VIP_PLAN)).thenReturn(vipPlan());
    }

    @Test
    void listsTheExactVersionedProductPlanCatalogForAdminCurrentRead() {
        BoardProductPlan free = versionedPlan(freePlan(), "免费版", 3L);
        BoardProductPlan vip = versionedPlan(vipPlan(), "VIP版", 7L);
        when(mapper.selectPlansByProduct(IndependentBoardEntitlementService.PRODUCT_CODE))
                .thenReturn(List.of(free, vip));

        List<BoardProductPlanAdminView> plans = service.listPlans();

        assertEquals(2, plans.size());
        assertEquals("BOARD_FREE", plans.get(0).planCode());
        assertEquals("免费版", plans.get(0).planName());
        assertEquals(3L, plans.get(0).version());
        assertEquals("BOARD_VIP", plans.get(1).planCode());
        assertEquals(5, plans.get(1).dailyMeetingLimit());
        assertEquals(30, plans.get(1).agendaLimit());
        assertNull(plans.get(1).seatLimit());
        assertEquals(Date.from(NOW), plans.get(1).updatedAt());

        free.getUpdatedAt().setTime(0L);
        Date returnedUpdatedAt = plans.get(0).updatedAt();
        returnedUpdatedAt.setTime(0L);
        assertEquals(Date.from(NOW), plans.get(0).updatedAt());
    }

    @Test
    void planCatalogFailsClosedOnMissingDuplicateOrUnversionedPolicy() {
        when(mapper.selectPlansByProduct(IndependentBoardEntitlementService.PRODUCT_CODE))
                .thenReturn(List.of(versionedPlan(freePlan(), "免费版", 3L)));
        ServiceException missing = assertThrows(ServiceException.class, service::listPlans);
        assertEquals("BOARD_PLAN_CURRENT_READ_FAILED", missing.getMessage());

        BoardProductPlan duplicate = versionedPlan(freePlan(), "免费版", 4L);
        when(mapper.selectPlansByProduct(IndependentBoardEntitlementService.PRODUCT_CODE))
                .thenReturn(List.of(
                        versionedPlan(freePlan(), "免费版", 3L), duplicate));
        ServiceException duplicateError = assertThrows(ServiceException.class, service::listPlans);
        assertEquals("BOARD_PLAN_CONTRACT_DRIFT", duplicateError.getMessage());

        BoardProductPlan unversioned = versionedPlan(vipPlan(), "VIP版", 7L);
        unversioned.setUpdatedAt(null);
        when(mapper.selectPlansByProduct(IndependentBoardEntitlementService.PRODUCT_CODE))
                .thenReturn(List.of(
                        versionedPlan(freePlan(), "免费版", 3L), unversioned));
        ServiceException metadataError = assertThrows(ServiceException.class, service::listPlans);
        assertEquals("BOARD_PLAN_CONTRACT_DRIFT", metadataError.getMessage());

        when(mapper.selectPlansByProduct(IndependentBoardEntitlementService.PRODUCT_CODE))
                .thenReturn(Arrays.asList(
                        versionedPlan(freePlan(), "free", 3L), null));
        ServiceException nullRow = assertThrows(ServiceException.class, service::listPlans);
        assertEquals("BOARD_PLAN_CONTRACT_DRIFT", nullRow.getMessage());
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
    void currentVipEntitlementWithMissingPlanFailsClosedAsContractDrift() {
        when(mapper.selectActiveContext(7L, 42L)).thenReturn(member(7L, 11L, 42L));
        when(mapper.selectEntitlement(
                7L, 11L, 42L, IndependentBoardEntitlementService.PRODUCT_CODE))
                .thenReturn(entitlement(null, null));
        when(mapper.selectActivePlan(
                IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.VIP_PLAN)).thenReturn(null);

        ServiceException error = assertThrows(
                ServiceException.class, () -> service.getSnapshot(7L, 42L));

        assertEquals(500, error.getCode());
        assertEquals("BOARD_PLAN_CONTRACT_DRIFT", error.getMessage());
        verify(connectorBindingPort, never()).hasAuthoritativeCurrentBinding(
                any(), any(), any(), any(), anyBoolean());
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
    void authoritativeCurrentBindingActivatesVipWithoutTrustingLegacyColumns() {
        when(mapper.selectActiveContext(7L, 42L)).thenReturn(member(7L, 11L, 42L));
        when(mapper.selectEntitlement(7L, 11L, 42L, IndependentBoardEntitlementService.PRODUCT_CODE))
                .thenReturn(entitlement("legacy-binding-must-not-be-read", null));
        when(connectorBindingPort.hasAuthoritativeCurrentBinding(
                7L, 11L, 42L, IndependentBoardEntitlementService.PRODUCT_CODE, false))
                .thenReturn(true);

        BoardEntitlementSnapshot snapshot = service.getSnapshot(7L, 42L);

        assertEquals("BOARD_VIP", snapshot.grantedPlanCode());
        assertEquals("BOARD_VIP", snapshot.effectivePlanCode());
        assertEquals("ACTIVE", snapshot.activationState());
        assertEquals(5, snapshot.dailyMeetingLimit());
        assertEquals(30, snapshot.agendaLimit());
        assertNull(snapshot.seatLimit());
        assertTrue(snapshot.secretaryEnabled());
        assertTrue(snapshot.connectorVerified());
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
    void freePlanAdjustmentRevokesAnyAuthoritativeConnectorBindingInTheSameTransaction() {
        when(mapper.selectExactActiveMemberForUpdate(7L, 11L, 42L))
                .thenReturn(member(7L, 11L, 42L));
        BoardProductEntitlement current = entitlement(null, null);
        when(mapper.selectEntitlementForUpdate(
                7L, 11L, IndependentBoardEntitlementService.PRODUCT_CODE))
                .thenReturn(current);
        when(mapper.updateEntitlementIfVersion(any(), any())).thenReturn(1);
        when(mapper.insertEntitlementReceipt(any())).thenReturn(1);

        BoardEntitlementAdminView view = service.grant(
                new BoardEntitlementGrantRequest(
                        7L, 11L, 42L, IndependentBoardEntitlementService.FREE_PLAN,
                        Date.from(NOW.plusSeconds(3600)), 1L),
                900L);

        assertEquals(IndependentBoardEntitlementService.FREE_PLAN, view.planCode());
        assertEquals("FREE", view.activationState());
        verify(connectorBindingPort).revokeForEntitlement(
                7L, 11L, 42L, IndependentBoardEntitlementService.PRODUCT_CODE, 900L);
        verify(connectorBindingPort, never()).hasAuthoritativeCurrentBinding(
                any(), any(), any(), any(), anyBoolean());
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
        verify(connectorBindingPort).selectAuthoritativeCurrentBindingKeys(
                7L, IndependentBoardEntitlementService.PRODUCT_CODE);
        verify(connectorBindingPort, never()).hasAuthoritativeCurrentBinding(
                any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void entitlementListRejectsCurrentVipEntitlementWhenPlanIsMissing() {
        when(mapper.selectEntitlementsByTenant(
                7L, IndependentBoardEntitlementService.PRODUCT_CODE))
                .thenReturn(List.of(entitlement(null, null)));
        when(mapper.selectActivePlan(
                IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.VIP_PLAN)).thenReturn(null);

        ServiceException error = assertThrows(
                ServiceException.class, () -> service.listEntitlements(7L));

        assertEquals(500, error.getCode());
        assertEquals("BOARD_PLAN_CONTRACT_DRIFT", error.getMessage());
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
    void grantCannotReactivateARevokedEntitlementWithoutAReviewedRestoreContract() {
        when(mapper.selectExactActiveMemberForUpdate(7L, 11L, 42L))
                .thenReturn(member(7L, 11L, 42L));
        BoardProductEntitlement revoked = entitlement(null, null);
        revoked.setStatus("REVOKED");
        when(mapper.selectEntitlementForUpdate(
                7L, 11L, IndependentBoardEntitlementService.PRODUCT_CODE))
                .thenReturn(revoked);
        BoardEntitlementGrantRequest request = new BoardEntitlementGrantRequest(
                7L, 11L, 42L, "BOARD_VIP", null, 1L);

        ServiceException error = assertThrows(
                ServiceException.class, () -> service.grant(request, 900L));

        assertEquals(409, error.getCode());
        assertEquals("ENTITLEMENT_SCOPE_OR_VERSION_CONFLICT", error.getMessage());
        verify(mapper, never()).updateEntitlementIfVersion(any(), any());
        verify(mapper, never()).insertEntitlementReceipt(any());
    }

    @Test
    void grantUpdateRejectsAnOverflowingExpectedVersionBeforeMutation() {
        when(mapper.selectExactActiveMemberForUpdate(7L, 11L, 42L))
                .thenReturn(member(7L, 11L, 42L));
        BoardProductEntitlement current = entitlement(null, null);
        current.setVersion(Long.MAX_VALUE);
        when(mapper.selectEntitlementForUpdate(
                7L, 11L, IndependentBoardEntitlementService.PRODUCT_CODE))
                .thenReturn(current);
        BoardEntitlementGrantRequest request = new BoardEntitlementGrantRequest(
                7L, 11L, 42L, "BOARD_VIP", null, Long.MAX_VALUE);

        ServiceException error = assertThrows(
                ServiceException.class, () -> service.grant(request, 900L));

        assertEquals(400, error.getCode());
        assertEquals("ENTITLEMENT_EXPECTED_VERSION_OUT_OF_RANGE", error.getMessage());
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

    @Test
    void revokedSnapshotIsExplicitlyRevokedAndUsesTheFreePolicy() {
        when(mapper.selectActiveContext(7L, 42L)).thenReturn(member(7L, 11L, 42L));
        BoardProductEntitlement revoked = entitlement("legacy-binding", Date.from(NOW.minusSeconds(60)));
        revoked.setStatus("REVOKED");
        revoked.setValidUntil(Date.from(NOW));
        when(mapper.selectEntitlement(
                7L, 11L, 42L, IndependentBoardEntitlementService.PRODUCT_CODE))
                .thenReturn(revoked);

        BoardEntitlementSnapshot snapshot = service.getSnapshot(7L, 42L);

        assertEquals("BOARD_VIP", snapshot.grantedPlanCode());
        assertEquals("BOARD_FREE", snapshot.effectivePlanCode());
        assertEquals("REVOKED", snapshot.activationState());
        assertFalse(snapshot.connectorRequired());
        assertFalse(snapshot.connectorVerified());
        assertEquals(1, snapshot.dailyMeetingLimit());
        assertEquals(5, snapshot.agendaLimit());
        assertEquals(3, snapshot.seatLimit());
        assertFalse(snapshot.secretaryEnabled());
        verify(mapper, never()).selectActivePlan(
                IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.VIP_PLAN);
    }

    @Test
    void revokeDoesNotDependOnAnActiveMembershipAndWritesAnImmutableReceipt() {
        BoardProductEntitlement current = entitlement(
                "legacy-binding", Date.from(NOW.minusSeconds(60)));
        current.setVersion(3L);
        when(mapper.selectEntitlementForUpdate(
                7L, 11L, IndependentBoardEntitlementService.PRODUCT_CODE))
                .thenReturn(current);
        when(mapper.updateEntitlementIfVersion(any(), any())).thenReturn(1);
        when(mapper.insertEntitlementReceipt(any())).thenReturn(1);

        BoardEntitlementAdminView view = service.revoke(
                new BoardEntitlementRevokeRequest(7L, 11L, 42L, 3L), 900L);

        assertEquals("REVOKED", view.entitlementStatus());
        assertEquals("REVOKED", view.activationState());
        assertEquals(4L, view.version());
        assertEquals(Date.from(NOW), view.validUntil());
        ArgumentCaptor<BoardProductEntitlement> updated =
                ArgumentCaptor.forClass(BoardProductEntitlement.class);
        verify(mapper).updateEntitlementIfVersion(updated.capture(), org.mockito.ArgumentMatchers.eq(3L));
        assertEquals("REVOKED", updated.getValue().getStatus());
        assertEquals(4L, updated.getValue().getVersion());
        assertEquals(Date.from(NOW), updated.getValue().getValidUntil());
        ArgumentCaptor<BoardEntitlementReceipt> receipt =
                ArgumentCaptor.forClass(BoardEntitlementReceipt.class);
        verify(mapper).insertEntitlementReceipt(receipt.capture());
        assertEquals(7L, receipt.getValue().getTenantId());
        assertEquals(900L, receipt.getValue().getActorUserId());
        assertEquals(11L, receipt.getValue().getTargetMemberId());
        assertEquals("ENTITLEMENT_REVOKED", receipt.getValue().getAction());
        assertEquals("ACTION_COMPLETED", receipt.getValue().getEvidenceLevel());
        assertEquals(64, receipt.getValue().getPayloadDigest().length());
        verify(mapper, never()).selectExactActiveMemberForUpdate(any(), any(), any());
        verify(mapper, never()).selectActiveContext(any(), any());
        verify(mapper, never()).selectActivePlan(
                IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.VIP_PLAN);
        verify(connectorBindingPort).revokeForEntitlement(
                7L, 11L, 42L, IndependentBoardEntitlementService.PRODUCT_CODE, 900L);
    }

    @Test
    void revokeRejectsMissingMismatchedOrAlreadyRevokedRowsWithoutMutationOrReceipt() {
        BoardEntitlementRevokeRequest request =
                new BoardEntitlementRevokeRequest(7L, 11L, 42L, 3L);
        when(mapper.selectEntitlementForUpdate(
                7L, 11L, IndependentBoardEntitlementService.PRODUCT_CODE))
                .thenReturn(null);
        assertRevokeConflict(request);

        BoardProductEntitlement wrongUser = entitlement(null, null);
        wrongUser.setVersion(3L);
        wrongUser.setUserId(43L);
        when(mapper.selectEntitlementForUpdate(
                7L, 11L, IndependentBoardEntitlementService.PRODUCT_CODE))
                .thenReturn(wrongUser);
        assertRevokeConflict(request);

        BoardProductEntitlement stale = entitlement(null, null);
        stale.setVersion(4L);
        when(mapper.selectEntitlementForUpdate(
                7L, 11L, IndependentBoardEntitlementService.PRODUCT_CODE))
                .thenReturn(stale);
        assertRevokeConflict(request);

        BoardProductEntitlement revoked = entitlement(null, null);
        revoked.setVersion(3L);
        revoked.setStatus("REVOKED");
        when(mapper.selectEntitlementForUpdate(
                7L, 11L, IndependentBoardEntitlementService.PRODUCT_CODE))
                .thenReturn(revoked);
        assertRevokeConflict(request);

        verify(mapper, never()).updateEntitlementIfVersion(any(), any());
        verify(mapper, never()).insertEntitlementReceipt(any());
    }

    @Test
    void revokeCasFailureDoesNotCreateAReceipt() {
        BoardProductEntitlement current = entitlement(null, null);
        current.setVersion(3L);
        when(mapper.selectEntitlementForUpdate(
                7L, 11L, IndependentBoardEntitlementService.PRODUCT_CODE))
                .thenReturn(current);
        when(mapper.updateEntitlementIfVersion(any(), any())).thenReturn(0);

        ServiceException error = assertThrows(ServiceException.class, () -> service.revoke(
                new BoardEntitlementRevokeRequest(7L, 11L, 42L, 3L), 900L));

        assertEquals(409, error.getCode());
        assertEquals("ENTITLEMENT_VERSION_CONFLICT", error.getMessage());
        verify(mapper, never()).insertEntitlementReceipt(any());
    }

    @Test
    void revokeAtTheGrantInstantPreservesTheDatabaseValidityCheckWithOneMillisecondFloor() {
        BoardProductEntitlement current = entitlement(null, null);
        current.setVersion(3L);
        current.setValidFrom(Date.from(NOW));
        when(mapper.selectEntitlementForUpdate(
                7L, 11L, IndependentBoardEntitlementService.PRODUCT_CODE))
                .thenReturn(current);
        when(mapper.updateEntitlementIfVersion(any(), any())).thenReturn(1);
        when(mapper.insertEntitlementReceipt(any())).thenReturn(1);

        BoardEntitlementAdminView view = service.revoke(
                new BoardEntitlementRevokeRequest(7L, 11L, 42L, 3L), 900L);

        assertEquals(new Date(NOW.toEpochMilli() + 1L), view.validUntil());
        assertEquals("REVOKED", view.entitlementStatus());
        verify(mapper).updateEntitlementIfVersion(any(), org.mockito.ArgumentMatchers.eq(3L));
        verify(mapper).insertEntitlementReceipt(any());
    }

    @Test
    void revokeRejectsAnUnrepresentableValidityFloorBeforeAnyWrite() {
        BoardProductEntitlement current = entitlement(null, null);
        current.setVersion(3L);
        current.setValidFrom(new Date(Long.MAX_VALUE));
        when(mapper.selectEntitlementForUpdate(
                7L, 11L, IndependentBoardEntitlementService.PRODUCT_CODE))
                .thenReturn(current);

        ServiceException error = assertThrows(ServiceException.class, () -> service.revoke(
                new BoardEntitlementRevokeRequest(7L, 11L, 42L, 3L), 900L));

        assertEquals(409, error.getCode());
        assertEquals("ENTITLEMENT_VALIDITY_CONFLICT", error.getMessage());
        verify(mapper, never()).updateEntitlementIfVersion(any(), any());
        verify(mapper, never()).insertEntitlementReceipt(any());
    }

    @Test
    void revokeRejectsAnOverflowingExpectedVersionBeforeAnyCurrentReadOrWrite() {
        ServiceException error = assertThrows(ServiceException.class, () -> service.revoke(
                new BoardEntitlementRevokeRequest(7L, 11L, 42L, Long.MAX_VALUE), 900L));

        assertEquals(400, error.getCode());
        assertEquals("ENTITLEMENT_EXPECTED_VERSION_OUT_OF_RANGE", error.getMessage());
        verify(mapper, never()).selectEntitlementForUpdate(any(), any(), any());
        verify(mapper, never()).updateEntitlementIfVersion(any(), any());
        verify(mapper, never()).insertEntitlementReceipt(any());
    }

    @Test
    void receiptAuditReturnsOnlyTheBoundedSevenFieldProjection() {
        BoardEntitlementReceipt receipt = receipt("receipt-1", 7L);
        receipt.setPayloadDigest("secret-digest-must-not-leak");
        when(mapper.selectEntitlementReceiptsByTenant(7L)).thenReturn(List.of(receipt));

        BoardEntitlementReceiptAuditEnvelope result = service.listReceipts(7L);

        assertEquals(500, result.limit());
        assertFalse(result.truncated());
        assertEquals(1, result.records().size());
        BoardEntitlementReceiptView view = result.records().get(0);
        assertEquals("receipt-1", view.receiptId());
        assertEquals(7L, view.tenantId());
        assertEquals(900L, view.actorUserId());
        assertEquals(11L, view.targetMemberId());
        assertEquals("ENTITLEMENT_REVOKED", view.action());
        assertEquals("ACTION_COMPLETED", view.evidenceLevel());
        assertEquals(Date.from(NOW), view.createdAt());
        assertEquals(List.of(
                        "receiptId", "tenantId", "actorUserId", "targetMemberId",
                        "action", "evidenceLevel", "createdAt"),
                Arrays.stream(BoardEntitlementReceiptView.class.getRecordComponents())
                        .map(component -> component.getName()).toList());
    }

    @Test
    void receiptAuditTruncatesExactlyAtFiveHundredAndFailsClosedBeyondFetchLimit() {
        when(mapper.selectEntitlementReceiptsByTenant(7L)).thenReturn(
                IntStream.range(0, 501)
                        .mapToObj(index -> receipt("receipt-" + index, 7L)).toList());

        BoardEntitlementReceiptAuditEnvelope truncated = service.listReceipts(7L);

        assertEquals(500, truncated.records().size());
        assertTrue(truncated.truncated());

        when(mapper.selectEntitlementReceiptsByTenant(7L)).thenReturn(
                IntStream.range(0, 502)
                        .mapToObj(index -> receipt("receipt-" + index, 7L)).toList());
        ServiceException error = assertThrows(ServiceException.class, () -> service.listReceipts(7L));
        assertEquals(500, error.getCode());
        assertEquals("BOARD_ENTITLEMENT_RECEIPT_CURRENT_READ_FAILED", error.getMessage());
    }

    @Test
    void receiptAuditValidatesTheFiveHundredAndFirstTruncationSentinel() {
        List<BoardEntitlementReceipt> rows = new ArrayList<>(IntStream.range(0, 501)
                .mapToObj(index -> receipt("receipt-" + index, 7L)).toList());

        rows.set(500, receipt("receipt-cross-tenant-sentinel", 8L));
        when(mapper.selectEntitlementReceiptsByTenant(7L)).thenReturn(rows);
        assertReceiptAuditScopeFailure();

        BoardEntitlementReceipt unknownAction = receipt("receipt-action-sentinel", 7L);
        unknownAction.setAction("ENTITLEMENT_PURGED");
        rows.set(500, unknownAction);
        when(mapper.selectEntitlementReceiptsByTenant(7L)).thenReturn(rows);
        assertReceiptAuditScopeFailure();

        BoardEntitlementReceipt weakEvidence = receipt("receipt-evidence-sentinel", 7L);
        weakEvidence.setEvidenceLevel("DECLARED");
        rows.set(500, weakEvidence);
        when(mapper.selectEntitlementReceiptsByTenant(7L)).thenReturn(rows);
        assertReceiptAuditScopeFailure();
    }

    @Test
    void receiptAuditRejectsAnyMapperRowOutsideTheRequestedTenant() {
        when(mapper.selectEntitlementReceiptsByTenant(7L))
                .thenReturn(List.of(receipt("receipt-leak", 8L)));

        ServiceException error = assertThrows(ServiceException.class, () -> service.listReceipts(7L));

        assertEquals(500, error.getCode());
        assertEquals("BOARD_ENTITLEMENT_RECEIPT_SCOPE_INVALID", error.getMessage());
    }

    @Test
    void receiptAuditRejectsUnknownActionsAndEvidenceLevels() {
        BoardEntitlementReceipt unknownAction = receipt("receipt-action", 7L);
        unknownAction.setAction("ENTITLEMENT_PURGED");
        when(mapper.selectEntitlementReceiptsByTenant(7L)).thenReturn(List.of(unknownAction));
        ServiceException actionError = assertThrows(
                ServiceException.class, () -> service.listReceipts(7L));
        assertEquals("BOARD_ENTITLEMENT_RECEIPT_SCOPE_INVALID", actionError.getMessage());

        BoardEntitlementReceipt weakEvidence = receipt("receipt-evidence", 7L);
        weakEvidence.setEvidenceLevel("DECLARED");
        when(mapper.selectEntitlementReceiptsByTenant(7L)).thenReturn(List.of(weakEvidence));
        ServiceException evidenceError = assertThrows(
                ServiceException.class, () -> service.listReceipts(7L));
        assertEquals("BOARD_ENTITLEMENT_RECEIPT_SCOPE_INVALID", evidenceError.getMessage());
    }

    private void assertRevokeConflict(BoardEntitlementRevokeRequest request) {
        ServiceException error = assertThrows(
                ServiceException.class, () -> service.revoke(request, 900L));
        assertEquals(409, error.getCode());
        assertEquals("ENTITLEMENT_SCOPE_OR_VERSION_CONFLICT", error.getMessage());
    }

    private void assertReceiptAuditScopeFailure() {
        ServiceException error = assertThrows(
                ServiceException.class, () -> service.listReceipts(7L));
        assertEquals(500, error.getCode());
        assertEquals("BOARD_ENTITLEMENT_RECEIPT_SCOPE_INVALID", error.getMessage());
    }

    private BoardEntitlementReceipt receipt(String receiptId, long tenantId) {
        BoardEntitlementReceipt receipt = new BoardEntitlementReceipt();
        receipt.setReceiptId(receiptId);
        receipt.setTenantId(tenantId);
        receipt.setActorUserId(900L);
        receipt.setTargetMemberId(11L);
        receipt.setAction("ENTITLEMENT_REVOKED");
        receipt.setPayloadDigest("a".repeat(64));
        receipt.setEvidenceLevel("ACTION_COMPLETED");
        receipt.setCreatedAt(Date.from(NOW));
        return receipt;
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

    private BoardProductPlan versionedPlan(BoardProductPlan plan, String planName, long version) {
        plan.setPlanName(planName);
        plan.setVersion(version);
        plan.setUpdatedAt(Date.from(NOW));
        return plan;
    }
}
