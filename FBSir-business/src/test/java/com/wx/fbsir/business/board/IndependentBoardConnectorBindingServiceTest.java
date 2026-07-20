package com.wx.fbsir.business.board.service;

import com.wx.fbsir.business.board.config.IndependentBoardConnectorProperties;
import com.wx.fbsir.business.board.domain.BoardConnectorBinding;
import com.wx.fbsir.business.board.domain.BoardConnectorBindingReceipt;
import com.wx.fbsir.business.board.domain.BoardEnterpriseMemberScope;
import com.wx.fbsir.business.board.domain.BoardProductEntitlement;
import com.wx.fbsir.business.board.domain.BoardProductPlan;
import com.wx.fbsir.business.board.dto.BoardConnectorBindingKey;
import com.wx.fbsir.business.board.dto.BoardConnectorBindingRevokeRequest;
import com.wx.fbsir.business.board.dto.BoardConnectorBindingSnapshot;
import com.wx.fbsir.business.board.dto.BoardConnectorProtectedRequestAttestation;
import com.wx.fbsir.business.board.mapper.IndependentBoardMapper;
import com.wx.fbsir.common.exception.ServiceException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IndependentBoardConnectorBindingServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-20T12:00:00Z");
    private static final String ISSUER = "https://api2.u3w.com";
    private static final String RESOURCE = "https://api2.u3w.com/fbs-mcp/mcp";
    private static final String BINDING_ID = "11111111-1111-1111-1111-111111111111";
    private static final List<String> SCOPES = List.of(
            "identity.read",
            "entitlement.read",
            "board.meeting.reserve",
            "board.receipt.write");

    private IndependentBoardMapper mapper;
    private IndependentBoardConnectorProperties properties;
    private IndependentBoardConnectorBindingService service;

    @BeforeEach
    void setUp() {
        mapper = mock(IndependentBoardMapper.class);
        properties = properties(ISSUER, RESOURCE);
        service = new IndependentBoardConnectorBindingService(
                mapper, properties, Clock.fixed(NOW, ZoneOffset.UTC));
        when(mapper.selectActivePlan(
                IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.VIP_PLAN)).thenReturn(vipPlan());
    }

    @Test
    void trustPolicyMustBeExplicitBeforeAnyActivationReadOrWrite() {
        service = new IndependentBoardConnectorBindingService(
                mapper, properties("", RESOURCE), Clock.fixed(NOW, ZoneOffset.UTC));

        ServiceException error = assertThrows(
                ServiceException.class, () -> service.confirmProtectedRequest(attestation(), 42L));

        assertEquals(503, error.getCode());
        assertEquals("CONNECTOR_TRUST_POLICY_NOT_CONFIGURED", error.getMessage());
        assertFalse(service.hasAuthoritativeCurrentBinding(
                7L, 11L, 42L, IndependentBoardEntitlementService.PRODUCT_CODE, false));
        verify(mapper, never()).selectExactActiveMemberForUpdate(anyLong(), anyLong(), anyLong());
        verify(mapper, never()).insertConnectorBinding(any());
    }

    @Test
    void trustPolicyRejectsNonFixedNonAsciiAndOversizedUrisBeforeDatabaseAccess() {
        List<IndependentBoardConnectorProperties> invalidPolicies = List.of(
                properties(ISSUER, "https://api2.u3w.com/another-resource"),
                properties("https://api2.u3w.com/发行方", RESOURCE),
                properties("https://api2.u3w.com/" + "a".repeat(513), RESOURCE));

        for (IndependentBoardConnectorProperties invalidPolicy : invalidPolicies) {
            IndependentBoardConnectorBindingService invalidService =
                    new IndependentBoardConnectorBindingService(
                            mapper, invalidPolicy, Clock.fixed(NOW, ZoneOffset.UTC));
            ServiceException error = assertThrows(
                    ServiceException.class,
                    () -> invalidService.confirmProtectedRequest(attestation(), 42L));
            assertEquals(503, error.getCode());
            assertEquals("CONNECTOR_TRUST_POLICY_NOT_CONFIGURED", error.getMessage());
            assertFalse(invalidService.hasAuthoritativeCurrentBinding(
                    7L, 11L, 42L, IndependentBoardEntitlementService.PRODUCT_CODE, false));
        }
        verifyNoInteractions(mapper);
    }

    @Test
    void firstValidatedProtectedRequestCreatesBindingScopesAndReceiptAtomically() {
        when(mapper.selectExactActiveMemberForUpdate(7L, 11L, 42L)).thenReturn(member());
        when(mapper.selectEntitlementForUpdate(
                7L, 11L, IndependentBoardEntitlementService.PRODUCT_CODE)).thenReturn(entitlement());
        when(mapper.selectActivePlan(
                IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.VIP_PLAN)).thenReturn(vipPlan());
        when(mapper.insertConnectorBinding(any())).thenReturn(1);
        when(mapper.insertConnectorBindingScope(anyString(), anyString(), any())).thenReturn(1);
        when(mapper.insertConnectorBindingReceipt(any())).thenReturn(1);

        BoardConnectorBindingSnapshot result = service.confirmProtectedRequest(attestation(), 42L);

        assertEquals("ACTIVE", result.status());
        assertEquals(7L, result.tenantId());
        assertEquals(11L, result.memberId());
        assertEquals(42L, result.userId());
        assertEquals(1L, result.version());
        verify(mapper).selectExactActiveMemberForUpdate(7L, 11L, 42L);
        verify(mapper).selectEntitlementForUpdate(
                7L, 11L, IndependentBoardEntitlementService.PRODUCT_CODE);
        verify(mapper).selectConnectorBindingForUpdate(
                7L, 11L, 42L, IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardConnectorBindingService.SOURCE_CODE,
                IndependentBoardConnectorBindingService.CONNECTOR_CODE);
        verify(mapper, times(4)).insertConnectorBindingScope(anyString(), anyString(), any());
        ArgumentCaptor<BoardConnectorBindingReceipt> receipt =
                ArgumentCaptor.forClass(BoardConnectorBindingReceipt.class);
        verify(mapper).insertConnectorBindingReceipt(receipt.capture());
        assertEquals("CONNECTOR_BINDING_VERIFIED", receipt.getValue().getAction());
        assertEquals("ACTION_COMPLETED", receipt.getValue().getEvidenceLevel());
        assertEquals(64, receipt.getValue().getPayloadDigest().length());
    }

    @Test
    void exactFourScopeSetIsRequiredBeforeAnyDatabaseRead() {
        BoardConnectorProtectedRequestAttestation missingScope = new BoardConnectorProtectedRequestAttestation(
                7L, 11L, 42L, ISSUER, RESOURCE, "workbuddy-client",
                "a".repeat(64), SCOPES.subList(0, 3),
                IndependentBoardConnectorBindingService.VERIFY_INITIALIZE,
                "b".repeat(64), Date.from(NOW.plusSeconds(3600)));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.confirmProtectedRequest(missingScope, 42L));

        assertEquals(403, error.getCode());
        assertEquals("CONNECTOR_SCOPE_SET_INVALID", error.getMessage());
        verify(mapper, never()).selectExactActiveMemberForUpdate(anyLong(), anyLong(), anyLong());
    }

    @Test
    void identityPolicyDigestMethodAndTimeAreRejectedBeforeAnyDatabaseRead() {
        assertPreDatabaseRejection(attestation(), 43L, 403,
                "CONNECTOR_ATTESTATION_ACTOR_MISMATCH");
        assertPreDatabaseRejection(new BoardConnectorProtectedRequestAttestation(
                        7L, 11L, 42L, "https://issuer.invalid", RESOURCE, "workbuddy-client",
                        "a".repeat(64), SCOPES,
                        IndependentBoardConnectorBindingService.VERIFY_INITIALIZE,
                        "b".repeat(64), Date.from(NOW.plusSeconds(3600))),
                42L, 403, "CONNECTOR_ISSUER_MISMATCH");
        assertPreDatabaseRejection(new BoardConnectorProtectedRequestAttestation(
                        7L, 11L, 42L, ISSUER, "https://resource.invalid", "workbuddy-client",
                        "a".repeat(64), SCOPES,
                        IndependentBoardConnectorBindingService.VERIFY_INITIALIZE,
                        "b".repeat(64), Date.from(NOW.plusSeconds(3600))),
                42L, 403, "CONNECTOR_RESOURCE_MISMATCH");
        assertPreDatabaseRejection(new BoardConnectorProtectedRequestAttestation(
                        7L, 11L, 42L, ISSUER, RESOURCE, "client id with spaces",
                        "a".repeat(64), SCOPES,
                        IndependentBoardConnectorBindingService.VERIFY_INITIALIZE,
                        "b".repeat(64), Date.from(NOW.plusSeconds(3600))),
                42L, 400, "CONNECTOR_CLIENT_ID_INVALID");
        assertPreDatabaseRejection(new BoardConnectorProtectedRequestAttestation(
                        7L, 11L, 42L, ISSUER, RESOURCE, "c".repeat(192),
                        "a".repeat(64), SCOPES,
                        IndependentBoardConnectorBindingService.VERIFY_INITIALIZE,
                        "b".repeat(64), Date.from(NOW.plusSeconds(3600))),
                42L, 400, "CONNECTOR_CLIENT_ID_INVALID");
        assertPreDatabaseRejection(new BoardConnectorProtectedRequestAttestation(
                        7L, 11L, 42L, ISSUER, RESOURCE, "workbuddy-client",
                        "A".repeat(64), SCOPES,
                        IndependentBoardConnectorBindingService.VERIFY_INITIALIZE,
                        "b".repeat(64), Date.from(NOW.plusSeconds(3600))),
                42L, 400, "CONNECTOR_SUBJECT_DIGEST_INVALID");
        assertPreDatabaseRejection(new BoardConnectorProtectedRequestAttestation(
                        7L, 11L, 42L, ISSUER, RESOURCE, "workbuddy-client",
                        "a".repeat(64), SCOPES, "HTTP_200",
                        "b".repeat(64), Date.from(NOW.plusSeconds(3600))),
                42L, 400, "CONNECTOR_VERIFICATION_METHOD_INVALID");
        assertPreDatabaseRejection(new BoardConnectorProtectedRequestAttestation(
                        7L, 11L, 42L, ISSUER, RESOURCE, "workbuddy-client",
                        "a".repeat(64), SCOPES,
                        IndependentBoardConnectorBindingService.VERIFY_INITIALIZE,
                        "b".repeat(64), Date.from(NOW)),
                42L, 403, "CONNECTOR_CREDENTIAL_EXPIRED");
        verifyNoInteractions(mapper);
    }

    @Test
    void protectedRequestAttestationDefensivelyCopiesMutableEvidence() {
        List<String> mutableScopes = new ArrayList<>(SCOPES);
        Date mutableExpiry = Date.from(NOW.plusSeconds(3600));
        BoardConnectorProtectedRequestAttestation immutable =
                new BoardConnectorProtectedRequestAttestation(
                        7L, 11L, 42L, ISSUER, RESOURCE, "workbuddy-client",
                        "a".repeat(64), mutableScopes,
                        IndependentBoardConnectorBindingService.VERIFY_INITIALIZE,
                        "b".repeat(64), mutableExpiry);

        mutableScopes.clear();
        mutableExpiry.setTime(0L);
        Date returnedExpiry = immutable.validUntil();
        returnedExpiry.setTime(0L);

        assertEquals(SCOPES, immutable.scopes());
        assertEquals(Date.from(NOW.plusSeconds(3600)), immutable.validUntil());
        assertThrows(UnsupportedOperationException.class,
                () -> immutable.scopes().add("unexpected.scope"));
    }

    @Test
    void bindingSnapshotDefensivelyCopiesDates() {
        Date verifiedAt = Date.from(NOW.minusSeconds(60));
        Date validUntil = Date.from(NOW.plusSeconds(3600));
        BoardConnectorBindingSnapshot snapshot = new BoardConnectorBindingSnapshot(
                BINDING_ID, 7L, 11L, 42L,
                IndependentBoardEntitlementService.PRODUCT_CODE, "ACTIVE",
                verifiedAt, validUntil, 1L);

        verifiedAt.setTime(0L);
        validUntil.setTime(0L);
        Date returnedVerifiedAt = snapshot.verifiedAt();
        Date returnedValidUntil = snapshot.validUntil();
        returnedVerifiedAt.setTime(0L);
        returnedValidUntil.setTime(0L);

        assertEquals(Date.from(NOW.minusSeconds(60)), snapshot.verifiedAt());
        assertEquals(Date.from(NOW.plusSeconds(3600)), snapshot.validUntil());
    }

    @Test
    void legacyOrWeakProofCannotActivateWithoutCurrentVipEntitlement() {
        when(mapper.selectExactActiveMemberForUpdate(7L, 11L, 42L)).thenReturn(member());
        BoardProductEntitlement free = entitlement();
        free.setPlanCode(IndependentBoardEntitlementService.FREE_PLAN);
        when(mapper.selectEntitlementForUpdate(
                7L, 11L, IndependentBoardEntitlementService.PRODUCT_CODE)).thenReturn(free);

        ServiceException error = assertThrows(
                ServiceException.class, () -> service.confirmProtectedRequest(attestation(), 42L));

        assertEquals(403, error.getCode());
        assertEquals("CONNECTOR_VIP_ENTITLEMENT_NOT_CURRENT", error.getMessage());
        verify(mapper, never()).selectConnectorBindingForUpdate(
                anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyString());
        verify(mapper, never()).insertConnectorBinding(any());
    }

    @Test
    void repeatedProtectedRequestIsIdempotentForTheSameCurrentBinding() {
        when(mapper.selectExactActiveMemberForUpdate(7L, 11L, 42L)).thenReturn(member());
        when(mapper.selectEntitlementForUpdate(
                7L, 11L, IndependentBoardEntitlementService.PRODUCT_CODE)).thenReturn(entitlement());
        when(mapper.selectActivePlan(
                IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.VIP_PLAN)).thenReturn(vipPlan());
        BoardConnectorBinding existing = binding();
        when(mapper.selectConnectorBindingForUpdate(
                7L, 11L, 42L, IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardConnectorBindingService.SOURCE_CODE,
                IndependentBoardConnectorBindingService.CONNECTOR_CODE)).thenReturn(existing);
        when(mapper.selectConnectorBindingScopesForUpdate(BINDING_ID)).thenReturn(SCOPES);

        BoardConnectorBindingSnapshot result = service.confirmProtectedRequest(attestation(), 42L);

        assertEquals(BINDING_ID, result.bindingId());
        assertEquals(1L, result.version());
        verify(mapper, never()).insertConnectorBinding(any());
        verify(mapper, never()).insertConnectorBindingScope(anyString(), anyString(), any());
        verify(mapper, never()).insertConnectorBindingReceipt(any());
    }

    @Test
    void revokedBindingCannotBeImplicitlyRestoredByAnotherProtectedRequest() {
        when(mapper.selectExactActiveMemberForUpdate(7L, 11L, 42L)).thenReturn(member());
        when(mapper.selectEntitlementForUpdate(
                7L, 11L, IndependentBoardEntitlementService.PRODUCT_CODE)).thenReturn(entitlement());
        when(mapper.selectActivePlan(
                IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.VIP_PLAN)).thenReturn(vipPlan());
        BoardConnectorBinding revoked = binding();
        revoked.setStatus(IndependentBoardConnectorBindingService.STATUS_REVOKED);
        revoked.setRevokedAt(Date.from(NOW.minusSeconds(1)));
        when(mapper.selectConnectorBindingForUpdate(
                7L, 11L, 42L, IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardConnectorBindingService.SOURCE_CODE,
                IndependentBoardConnectorBindingService.CONNECTOR_CODE)).thenReturn(revoked);
        when(mapper.selectConnectorBindingScopesForUpdate(BINDING_ID)).thenReturn(SCOPES);

        ServiceException error = assertThrows(
                ServiceException.class, () -> service.confirmProtectedRequest(attestation(), 42L));

        assertEquals(409, error.getCode());
        assertEquals("BOARD_CONNECTOR_BINDING_CONFLICT", error.getMessage());
        verify(mapper, never()).insertConnectorBinding(any());
        verify(mapper, never()).insertConnectorBindingReceipt(any());
    }

    @Test
    void authoritativeReadRequiresExactPolicyTimeAndScopes() {
        BoardConnectorBinding current = binding();
        when(mapper.selectActiveContext(7L, 42L)).thenReturn(member());
        when(mapper.selectEntitlement(
                7L, 11L, 42L, IndependentBoardEntitlementService.PRODUCT_CODE))
                .thenReturn(entitlement());
        when(mapper.selectActivePlan(
                IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.VIP_PLAN)).thenReturn(vipPlan());
        when(mapper.selectConnectorBinding(
                7L, 11L, 42L, IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardConnectorBindingService.SOURCE_CODE,
                IndependentBoardConnectorBindingService.CONNECTOR_CODE)).thenReturn(current);
        when(mapper.selectConnectorBindingScopes(BINDING_ID)).thenReturn(SCOPES);

        assertTrue(service.hasAuthoritativeCurrentBinding(
                7L, 11L, 42L, IndependentBoardEntitlementService.PRODUCT_CODE, false));

        when(mapper.selectConnectorBindingScopes(BINDING_ID))
                .thenReturn(SCOPES.subList(0, 3));
        assertFalse(service.hasAuthoritativeCurrentBinding(
                7L, 11L, 42L, IndependentBoardEntitlementService.PRODUCT_CODE, false));

        current.setLastSeenAt(Date.from(NOW.plusSeconds(1)));
        when(mapper.selectConnectorBindingScopes(BINDING_ID)).thenReturn(SCOPES);
        assertFalse(service.hasAuthoritativeCurrentBinding(
                7L, 11L, 42L, IndependentBoardEntitlementService.PRODUCT_CODE, false));

        current.setLastSeenAt(Date.from(NOW.minusSeconds(30)));
        current.setValidUntil(Date.from(NOW));
        when(mapper.selectConnectorBindingScopes(BINDING_ID)).thenReturn(SCOPES);
        assertFalse(service.hasAuthoritativeCurrentBinding(
                7L, 11L, 42L, IndependentBoardEntitlementService.PRODUCT_CODE, false));
    }

    @Test
    void authoritativeReadFailsClosedWhenVipPlanIsDisabledOrDrifted() {
        when(mapper.selectActiveContext(7L, 42L)).thenReturn(member());
        when(mapper.selectEntitlement(
                7L, 11L, 42L, IndependentBoardEntitlementService.PRODUCT_CODE))
                .thenReturn(entitlement());
        when(mapper.selectConnectorBinding(
                7L, 11L, 42L, IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardConnectorBindingService.SOURCE_CODE,
                IndependentBoardConnectorBindingService.CONNECTOR_CODE)).thenReturn(binding());
        when(mapper.selectConnectorBindingScopes(BINDING_ID)).thenReturn(SCOPES);

        when(mapper.selectActivePlan(
                IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.VIP_PLAN)).thenReturn(null);
        ServiceException missing = assertThrows(ServiceException.class,
                () -> service.hasAuthoritativeCurrentBinding(
                        7L, 11L, 42L,
                        IndependentBoardEntitlementService.PRODUCT_CODE, false));
        assertEquals(500, missing.getCode());
        assertEquals("BOARD_PLAN_CONTRACT_DRIFT", missing.getMessage());

        BoardProductPlan drifted = vipPlan();
        drifted.setAgendaLimit(29);
        when(mapper.selectActivePlan(
                IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.VIP_PLAN)).thenReturn(drifted);
        ServiceException drift = assertThrows(ServiceException.class,
                () -> service.hasAuthoritativeCurrentBinding(
                        7L, 11L, 42L,
                        IndependentBoardEntitlementService.PRODUCT_CODE, false));
        assertEquals(500, drift.getCode());
        assertEquals("BOARD_PLAN_CONTRACT_DRIFT", drift.getMessage());
    }

    @Test
    void batchCurrentReadUsesOneBoundedMapperReadAndRejectsCrossTenantRows() {
        BoardConnectorBinding current = binding();
        current.setScopes(SCOPES);
        when(mapper.selectConnectorBindingsByTenant(
                7L, IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardConnectorBindingService.SOURCE_CODE,
                IndependentBoardConnectorBindingService.CONNECTOR_CODE))
                .thenReturn(List.of(current));

        Set<BoardConnectorBindingKey> result = service.selectAuthoritativeCurrentBindingKeys(
                7L, IndependentBoardEntitlementService.PRODUCT_CODE);

        assertEquals(Set.of(new BoardConnectorBindingKey(
                7L, 11L, 42L, IndependentBoardEntitlementService.PRODUCT_CODE)), result);
        verify(mapper).selectConnectorBindingsByTenant(
                7L, IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardConnectorBindingService.SOURCE_CODE,
                IndependentBoardConnectorBindingService.CONNECTOR_CODE);
        verify(mapper, never()).selectConnectorBindingScopes(anyString());

        current.setTenantId(8L);
        ServiceException leak = assertThrows(ServiceException.class,
                () -> service.selectAuthoritativeCurrentBindingKeys(
                        7L, IndependentBoardEntitlementService.PRODUCT_CODE));
        assertEquals("BOARD_CONNECTOR_BINDING_SCOPE_INVALID", leak.getMessage());
    }

    @Test
    void batchSentinelOverOneHundredFailsClosedBeforeProjection() {
        List<BoardConnectorBinding> rows = new ArrayList<>();
        for (int index = 0; index < 101; index++) {
            rows.add(binding());
        }
        when(mapper.selectConnectorBindingsByTenant(
                7L, IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardConnectorBindingService.SOURCE_CODE,
                IndependentBoardConnectorBindingService.CONNECTOR_CODE)).thenReturn(rows);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.selectAuthoritativeCurrentBindingKeys(
                        7L, IndependentBoardEntitlementService.PRODUCT_CODE));

        assertEquals(500, error.getCode());
        assertEquals("BOARD_CONNECTOR_BINDING_CURRENT_READ_FAILED", error.getMessage());
    }

    @Test
    void batchCurrentReadRejectsMissingVipPlanBeforeBindingsCanBeSilentlyFiltered() {
        when(mapper.selectActivePlan(
                IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.VIP_PLAN)).thenReturn(null);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.selectAuthoritativeCurrentBindingKeys(
                        7L, IndependentBoardEntitlementService.PRODUCT_CODE));

        assertEquals(500, error.getCode());
        assertEquals("BOARD_PLAN_CONTRACT_DRIFT", error.getMessage());
        verify(mapper, never()).selectConnectorBindingsByTenant(
                anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void entitlementRevocationCanRevokeBindingWithoutActiveMemberLookup() {
        BoardConnectorBinding current = binding();
        current.setVersion(3L);
        current.setLastSeenAt(Date.from(NOW.plusSeconds(5)));
        when(mapper.selectConnectorBindingForUpdate(
                7L, 11L, 42L, IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardConnectorBindingService.SOURCE_CODE,
                IndependentBoardConnectorBindingService.CONNECTOR_CODE)).thenReturn(current);
        when(mapper.selectConnectorBindingScopesForUpdate(BINDING_ID)).thenReturn(SCOPES);
        when(mapper.revokeConnectorBindingIfVersion(any(), anyLong())).thenReturn(1);
        when(mapper.insertConnectorBindingReceipt(any())).thenReturn(1);

        service.revokeForEntitlement(
                7L, 11L, 42L, IndependentBoardEntitlementService.PRODUCT_CODE, 900L);

        ArgumentCaptor<BoardConnectorBinding> updated =
                ArgumentCaptor.forClass(BoardConnectorBinding.class);
        verify(mapper).revokeConnectorBindingIfVersion(updated.capture(),
                org.mockito.ArgumentMatchers.eq(3L));
        assertEquals("REVOKED", updated.getValue().getStatus());
        assertEquals(4L, updated.getValue().getVersion());
        assertTrue(updated.getValue().getRevokedAt().after(updated.getValue().getLastSeenAt()));
        verify(mapper, never()).selectExactActiveMemberForUpdate(anyLong(), anyLong(), anyLong());
        ArgumentCaptor<BoardConnectorBindingReceipt> receipt =
                ArgumentCaptor.forClass(BoardConnectorBindingReceipt.class);
        verify(mapper).insertConnectorBindingReceipt(receipt.capture());
        assertEquals("CONNECTOR_BINDING_REVOKED", receipt.getValue().getAction());
        assertEquals(900L, receipt.getValue().getActorUserId());
    }

    @Test
    void explicitRevokeLocksEntitlementBeforeExactBindingVersionAndRejectsStaleVersion() {
        when(mapper.selectEntitlementForUpdate(
                7L, 11L, IndependentBoardEntitlementService.PRODUCT_CODE)).thenReturn(entitlement());
        BoardConnectorBinding current = binding();
        current.setVersion(3L);
        when(mapper.selectConnectorBindingForUpdate(
                7L, 11L, 42L, IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardConnectorBindingService.SOURCE_CODE,
                IndependentBoardConnectorBindingService.CONNECTOR_CODE)).thenReturn(current);
        when(mapper.selectConnectorBindingScopesForUpdate(BINDING_ID)).thenReturn(SCOPES);

        ServiceException error = assertThrows(ServiceException.class, () -> service.revoke(
                new BoardConnectorBindingRevokeRequest(7L, 11L, 42L, BINDING_ID, 2L), 42L));

        assertEquals(409, error.getCode());
        assertEquals("CONNECTOR_BINDING_SCOPE_OR_VERSION_CONFLICT", error.getMessage());
        verify(mapper, never()).revokeConnectorBindingIfVersion(any(), anyLong());
        verify(mapper, never()).insertConnectorBindingReceipt(any());
    }

    @Test
    void duplicateFirstInsertIsReportedAsConflictWithoutReceipt() {
        when(mapper.selectExactActiveMemberForUpdate(7L, 11L, 42L)).thenReturn(member());
        when(mapper.selectEntitlementForUpdate(
                7L, 11L, IndependentBoardEntitlementService.PRODUCT_CODE)).thenReturn(entitlement());
        when(mapper.selectActivePlan(
                IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.VIP_PLAN)).thenReturn(vipPlan());
        when(mapper.insertConnectorBinding(any()))
                .thenThrow(new DuplicateKeyException("concurrent binding"));

        ServiceException error = assertThrows(
                ServiceException.class, () -> service.confirmProtectedRequest(attestation(), 42L));

        assertEquals(409, error.getCode());
        assertEquals("BOARD_CONNECTOR_BINDING_CONFLICT", error.getMessage());
        verify(mapper, never()).insertConnectorBindingReceipt(any());
    }

    private IndependentBoardConnectorProperties properties(String issuer, String resource) {
        IndependentBoardConnectorProperties result = new IndependentBoardConnectorProperties();
        result.setIssuerUri(issuer);
        result.setResourceUri(resource);
        return result;
    }

    private BoardConnectorProtectedRequestAttestation attestation() {
        return new BoardConnectorProtectedRequestAttestation(
                7L, 11L, 42L, ISSUER, RESOURCE, "workbuddy-client",
                "a".repeat(64), SCOPES,
                IndependentBoardConnectorBindingService.VERIFY_INITIALIZE,
                "b".repeat(64), Date.from(NOW.plusSeconds(3600)));
    }

    private void assertPreDatabaseRejection(
            BoardConnectorProtectedRequestAttestation candidate,
            Long actorUserId,
            int expectedStatus,
            String expectedCode) {
        ServiceException error = assertThrows(
                ServiceException.class,
                () -> service.confirmProtectedRequest(candidate, actorUserId));
        assertEquals(expectedStatus, error.getCode());
        assertEquals(expectedCode, error.getMessage());
    }

    private BoardEnterpriseMemberScope member() {
        BoardEnterpriseMemberScope member = new BoardEnterpriseMemberScope();
        member.setTenantId(7L);
        member.setMemberId(11L);
        member.setUserId(42L);
        member.setStatus(1);
        member.setDelFlag("0");
        return member;
    }

    private BoardProductEntitlement entitlement() {
        BoardProductEntitlement entitlement = new BoardProductEntitlement();
        entitlement.setId(8L);
        entitlement.setTenantId(7L);
        entitlement.setMemberId(11L);
        entitlement.setUserId(42L);
        entitlement.setProductCode(IndependentBoardEntitlementService.PRODUCT_CODE);
        entitlement.setPlanCode(IndependentBoardEntitlementService.VIP_PLAN);
        entitlement.setStatus("ACTIVE");
        entitlement.setValidFrom(Date.from(NOW.minusSeconds(600)));
        entitlement.setValidUntil(Date.from(NOW.plusSeconds(7200)));
        entitlement.setVersion(1L);
        return entitlement;
    }

    private BoardProductPlan vipPlan() {
        BoardProductPlan plan = new BoardProductPlan();
        plan.setProductCode(IndependentBoardEntitlementService.PRODUCT_CODE);
        plan.setPlanCode(IndependentBoardEntitlementService.VIP_PLAN);
        plan.setVip(true);
        plan.setConnectorRequired(true);
        plan.setDailyMeetingLimit(5);
        plan.setAgendaLimit(30);
        plan.setSeatLimit(null);
        plan.setSecretaryEnabled(true);
        plan.setStatus("ACTIVE");
        return plan;
    }

    private BoardConnectorBinding binding() {
        BoardConnectorBinding binding = new BoardConnectorBinding();
        binding.setId(9L);
        binding.setBindingId(BINDING_ID);
        binding.setTenantId(7L);
        binding.setMemberId(11L);
        binding.setUserId(42L);
        binding.setProductCode(IndependentBoardEntitlementService.PRODUCT_CODE);
        binding.setSourceCode(IndependentBoardConnectorBindingService.SOURCE_CODE);
        binding.setConnectorCode(IndependentBoardConnectorBindingService.CONNECTOR_CODE);
        binding.setIssuerUri(ISSUER);
        binding.setResourceUri(RESOURCE);
        binding.setClientId("workbuddy-client");
        binding.setPrincipalSubjectDigest("a".repeat(64));
        binding.setStatus("ACTIVE");
        binding.setVerificationMethod(IndependentBoardConnectorBindingService.VERIFY_INITIALIZE);
        binding.setEvidenceDigest("b".repeat(64));
        binding.setVerifiedAt(Date.from(NOW.minusSeconds(60)));
        binding.setLastSeenAt(Date.from(NOW.minusSeconds(60)));
        binding.setValidUntil(Date.from(NOW.plusSeconds(3600)));
        binding.setVersion(1L);
        binding.setScopes(SCOPES);
        return binding;
    }
}
