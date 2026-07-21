package com.wx.fbsir.business.board.service;

import com.wx.fbsir.business.board.config.IndependentBoardConnectorProperties;
import com.wx.fbsir.business.board.domain.BoardConnectorBinding;
import com.wx.fbsir.business.board.domain.BoardConnectorBindingReceipt;
import com.wx.fbsir.business.board.domain.BoardEnterpriseAuthority;
import com.wx.fbsir.business.board.domain.BoardEnterpriseMemberScope;
import com.wx.fbsir.business.board.domain.BoardProductEntitlement;
import com.wx.fbsir.business.board.domain.BoardProductPlan;
import com.wx.fbsir.business.board.dto.BoardConnectorBindingKey;
import com.wx.fbsir.business.board.dto.BoardConnectorBindingRevokeRequest;
import com.wx.fbsir.business.board.dto.BoardConnectorBindingSnapshot;
import com.wx.fbsir.business.board.dto.BoardConnectorProtectedRequestAttestation;
import com.wx.fbsir.business.board.mapper.IndependentBoardMapper;
import com.wx.fbsir.business.board.oauth.BoardOAuthCrypto;
import com.wx.fbsir.business.board.oauth.BoardOAuthConsentIntent;
import com.wx.fbsir.business.board.oauth.BoardOAuthProfile;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthClient;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthReceipt;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthToken;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthTokenFamily;
import com.wx.fbsir.business.board.oauth.mapper.IndependentBoardOAuthMapper;
import com.wx.fbsir.common.exception.ServiceException;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
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
    private IndependentBoardOAuthMapper oauthMapper;
    private IndependentBoardConnectorProperties properties;
    private DataSource boardDataSource;
    private Connection boardConnection;
    private IndependentBoardConnectorBindingService service;

    @BeforeEach
    void setUp() throws Exception {
        mapper = mock(IndependentBoardMapper.class);
        oauthMapper = mock(IndependentBoardOAuthMapper.class);
        properties = properties(ISSUER, RESOURCE);
        boardDataSource = mock(DataSource.class);
        boardConnection = mock(Connection.class);
        when(boardConnection.isClosed()).thenReturn(false);
        when(boardConnection.getAutoCommit()).thenReturn(false);
        when(boardConnection.getTransactionIsolation())
                .thenReturn(Connection.TRANSACTION_REPEATABLE_READ);
        service = new IndependentBoardConnectorBindingService(
                mapper, oauthMapper, properties, boardDataSource,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(mapper.selectActivePlan(
                IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.VIP_PLAN)).thenReturn(vipPlan());
        when(mapper.selectActivePlanForUpdate(
                IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.VIP_PLAN)).thenReturn(vipPlan());
        when(mapper.selectEnterpriseSlotForUpdate(7L)).thenReturn(enterprise(1));
    }

    @Test
    void trustPolicyMustBeExplicitBeforeAnyActivationReadOrWrite() {
        service = new IndependentBoardConnectorBindingService(
                mapper, oauthMapper, properties("", RESOURCE), boardDataSource,
                Clock.fixed(NOW, ZoneOffset.UTC));

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
                            mapper, oauthMapper, invalidPolicy, boardDataSource,
                            Clock.fixed(NOW, ZoneOffset.UTC));
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
    void firstOAuthFamilyActivationCreatesBindingScopesAndReceiptAtomically() {
        ActivationScenario scenario = stubFirstActivation();

        BoardConnectorBindingSnapshot result = inActivationTransaction(() -> {
            BoardConnectorBindingActivationLease lease = service.lockOAuthFamilyActivation(
                    activationKey(), 42L);
            assertEquals(
                    BoardConnectorBindingActivationLease.Mode.FIRST_ACTIVATION,
                    lease.mode());
            BoardOAuthFamilyActivationContext context =
                    service.prepareAndApplyLockedOAuthFamilyActivation(
                            lease, attestation(), scenario.pendingFamilyId);
            assertEquals("TOKEN_FAMILY_ACTIVATED", context.action());
            assertEquals(BoardOAuthConsentIntent.FIRST_CONNECT, context.consentIntent());
            assertEquals(lease.mode(), context.bindingTopology());
            scenario.transitionToFinal(context);
            service.verifyAndAppendLockedOAuthFamilyBindingReceipt(lease);
            scenario.publishW4bReceipt(validW4bReceipt(context));
            service.completeLockedOAuthFamilyActivation(lease);
            return context.binding();
        });

        assertEquals("ACTIVE", result.status());
        assertEquals(7L, result.tenantId());
        assertEquals(11L, result.memberId());
        assertEquals(42L, result.userId());
        assertEquals(1L, result.version());
        verify(mapper, times(4)).selectExactActiveMemberForUpdate(7L, 11L, 42L);
        verify(mapper, times(4)).selectEntitlementForUpdate(
                7L, 11L, IndependentBoardEntitlementService.PRODUCT_CODE);
        verify(mapper, times(4)).selectActivePlanForUpdate(
                IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.VIP_PLAN);
        verify(mapper).selectConnectorBindingSlotForUpdate(
                7L, 11L, IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardConnectorBindingService.SOURCE_CODE,
                IndependentBoardConnectorBindingService.CONNECTOR_CODE);
        verify(mapper, times(4)).insertConnectorBindingScope(anyString(), anyString(), any());
        ArgumentCaptor<BoardConnectorBindingReceipt> receipt =
                ArgumentCaptor.forClass(BoardConnectorBindingReceipt.class);
        verify(mapper).insertConnectorBindingReceipt(receipt.capture());
        assertEquals("CONNECTOR_BINDING_VERIFIED", receipt.getValue().getAction());
        assertEquals("ACTION_COMPLETED", receipt.getValue().getEvidenceLevel());
        assertEquals(64, receipt.getValue().getPayloadDigest().length());
        verify(oauthMapper, times(2)).selectReceiptByReceiptId(anyString());
        verify(mapper, times(2)).selectConnectorBindingReceiptForUpdate(anyString());
        verify(oauthMapper, times(4)).selectActiveTokenFamilySlotForUpdate(
                7L, 11L, IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardConnectorBindingService.SOURCE_CODE,
                IndependentBoardConnectorBindingService.CONNECTOR_CODE);
        verify(oauthMapper, times(4)).selectPendingTokenFamilySlotForUpdate(
                7L, 11L, IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardConnectorBindingService.SOURCE_CODE,
                IndependentBoardConnectorBindingService.CONNECTOR_CODE);
    }

    @Test
    void inactiveEnterpriseStopsActivationAfterEnterpriseThenMemberLocks() {
        stubActivationScope();
        when(mapper.selectEnterpriseSlotForUpdate(7L)).thenReturn(enterprise(0));

        ServiceException failure = assertThrows(
                ServiceException.class,
                () -> inActivationTransaction(() ->
                        service.lockOAuthFamilyActivation(activationKey(), 42L)));

        assertEquals(403, failure.getCode());
        assertEquals("TENANT_ENTERPRISE_SCOPE_INVALID", failure.getMessage());
        InOrder order = inOrder(mapper);
        order.verify(mapper).selectEnterpriseSlotForUpdate(7L);
        order.verify(mapper).selectExactActiveMemberForUpdate(7L, 11L, 42L);
        verify(mapper, never()).selectEntitlementForUpdate(anyLong(), anyLong(), anyString());
        verify(oauthMapper, never()).selectClientForUpdate(anyString());
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
        ActivationScenario scenario = stubFirstActivation();
        doThrow(new DuplicateKeyException("concurrent binding"))
                .when(mapper).insertConnectorBinding(any());

        ServiceException error = assertThrows(ServiceException.class,
                () -> inActivationTransaction(() -> {
                    BoardConnectorBindingActivationLease lease =
                            service.lockOAuthFamilyActivation(activationKey(), 42L);
                    return service.prepareAndApplyLockedOAuthFamilyActivation(
                            lease, attestation(), scenario.pendingFamilyId);
                }));

        assertEquals(409, error.getCode());
        assertEquals("BOARD_CONNECTOR_BINDING_CONFLICT", error.getMessage());
        verify(mapper, never()).insertConnectorBindingReceipt(any());
    }

    @Test
    void oauthFamilyActivationRequiresAnOuterTransactionBeforeAnyDatabaseLock() {
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.lockOAuthFamilyActivation(activationKey(), 42L));

        assertEquals(500, error.getCode());
        assertEquals("BOARD_CONNECTOR_ACTIVATION_TRANSACTION_REQUIRED", error.getMessage());
        verifyNoInteractions(mapper, oauthMapper);
    }

    @Test
    void activationRejectsATransactionThatDoesNotOwnTheBoardDataSource() {
        stubActivationScope();

        ServiceException error = assertThrows(ServiceException.class,
                () -> inActivationTransaction(() -> {
                    TransactionSynchronizationManager.unbindResource(boardDataSource);
                    return service.lockOAuthFamilyActivation(activationKey(), 42L);
                }));

        assertEquals(
                "BOARD_CONNECTOR_ACTIVATION_BOARD_TRANSACTION_REQUIRED",
                error.getMessage());
        verifyNoInteractions(mapper, oauthMapper);
    }

    @Test
    void activationRejectsAReadCommittedPhysicalBoardTransaction()
            throws Exception {
        when(boardConnection.getTransactionIsolation())
                .thenReturn(Connection.TRANSACTION_READ_COMMITTED);

        ServiceException error = assertThrows(ServiceException.class,
                () -> inActivationTransaction(() ->
                        service.lockOAuthFamilyActivation(activationKey(), 42L)));

        assertEquals(
                "BOARD_CONNECTOR_ACTIVATION_BOARD_TRANSACTION_REQUIRED",
                error.getMessage());
        verifyNoInteractions(mapper, oauthMapper);
    }

    @Test
    void activationRejectsAPhysicalBoardConnectionChangeBetweenPhases() {
        stubActivationScope();

        ServiceException error = assertThrows(ServiceException.class,
                () -> inActivationTransaction(() -> {
                    BoardConnectorBindingActivationLease lease =
                            service.lockOAuthFamilyActivation(activationKey(), 42L);
                    TransactionSynchronizationManager.unbindResource(boardDataSource);
                    ConnectionHolder replacement = new ConnectionHolder(mock(Connection.class));
                    replacement.setSynchronizedWithTransaction(true);
                    TransactionSynchronizationManager.bindResource(boardDataSource, replacement);
                    return service.prepareAndApplyLockedOAuthFamilyActivation(
                            lease, attestation(), "pending-family-1");
                }));

        assertEquals("BOARD_CONNECTOR_ACTIVATION_LEASE_INVALID", error.getMessage());
        verifyNoInteractions(oauthMapper);
    }

    @Test
    void activationRejectsAnUntrustedSynchronizationAlreadyPresentAtLeaseBind() {
        stubActivationScope();

        ServiceException error = assertThrows(ServiceException.class,
                () -> inActivationTransaction(() -> {
                    TransactionSynchronizationManager.registerSynchronization(
                            new TransactionSynchronization() { });
                    return service.lockOAuthFamilyActivation(activationKey(), 42L);
                }));

        assertEquals(
                "BOARD_CONNECTOR_ACTIVATION_UNTRUSTED_SYNCHRONIZATION",
                error.getMessage());
    }

    @Test
    void confirmCannotUseTheRemovedFirstBindingBypass() {
        stubActivationScope();

        ServiceException error = assertThrows(
                ServiceException.class,
                () -> service.confirmProtectedRequest(attestation(), 42L));

        assertEquals(409, error.getCode());
        assertEquals("BOARD_CONNECTOR_ACTIVATION_COORDINATOR_REQUIRED", error.getMessage());
        verify(mapper, never()).insertConnectorBinding(any());
        verify(mapper, never()).insertConnectorBindingScope(anyString(), anyString(), any());
        verify(mapper, never()).insertConnectorBindingReceipt(any());
        verifyNoInteractions(oauthMapper);
    }

    @Test
    void missingW4bReceiptCannotCompleteActivation() {
        ActivationScenario scenario = stubFirstActivation();

        ServiceException error = assertThrows(ServiceException.class,
                () -> inActivationTransaction(() -> {
                    BoardConnectorBindingActivationLease lease =
                            service.lockOAuthFamilyActivation(activationKey(), 42L);
                    BoardOAuthFamilyActivationContext context =
                            service.prepareAndApplyLockedOAuthFamilyActivation(
                                    lease, attestation(), scenario.pendingFamilyId);
                    scenario.transitionToFinal(context);
                    service.verifyAndAppendLockedOAuthFamilyBindingReceipt(lease);
                    service.completeLockedOAuthFamilyActivation(lease);
                    return null;
                }));

        assertEquals("BOARD_OAUTH_ACTIVATION_RECEIPT_PROOF_INVALID", error.getMessage());
    }

    @Test
    void historicalW4bReceiptFieldDriftCannotCompleteActivation() {
        ActivationScenario scenario = stubFirstActivation();

        ServiceException error = assertThrows(ServiceException.class,
                () -> inActivationTransaction(() -> {
                    BoardConnectorBindingActivationLease lease =
                            service.lockOAuthFamilyActivation(activationKey(), 42L);
                    BoardOAuthFamilyActivationContext context =
                            service.prepareAndApplyLockedOAuthFamilyActivation(
                                    lease, attestation(), scenario.pendingFamilyId);
                    scenario.transitionToFinal(context);
                    service.verifyAndAppendLockedOAuthFamilyBindingReceipt(lease);
                    BoardOAuthReceipt historical = validW4bReceipt(context);
                    historical.setCorrelationId("historical-correlation");
                    scenario.publishW4bReceipt(historical);
                    service.completeLockedOAuthFamilyActivation(lease);
                    return null;
                }));

        assertEquals("BOARD_OAUTH_ACTIVATION_RECEIPT_PROOF_INVALID", error.getMessage());
    }

    @Test
    void familyChangeAfterCompleteIsRejectedByBeforeCommitRevalidation() {
        ActivationScenario scenario = stubFirstActivation();

        ServiceException error = assertThrows(ServiceException.class,
                () -> inActivationTransaction(() -> {
                    BoardConnectorBindingActivationLease lease =
                            service.lockOAuthFamilyActivation(activationKey(), 42L);
                    BoardOAuthFamilyActivationContext context =
                            service.prepareAndApplyLockedOAuthFamilyActivation(
                                    lease, attestation(), scenario.pendingFamilyId);
                    scenario.transitionToFinal(context);
                    service.verifyAndAppendLockedOAuthFamilyBindingReceipt(lease);
                    scenario.publishW4bReceipt(validW4bReceipt(context));
                    service.completeLockedOAuthFamilyActivation(lease);
                    scenario.currentNewFamily.setStatus("REVOKED");
                    return null;
                }));

        assertEquals("BOARD_OAUTH_ACTIVATION_FINAL_STATE_INVALID", error.getMessage());
    }

    @Test
    void tokenChangeAfterCompleteIsRejectedByBeforeCommitRevalidation() {
        ActivationScenario scenario = stubFirstActivation();

        ServiceException error = assertThrows(ServiceException.class,
                () -> inActivationTransaction(() -> {
                    BoardConnectorBindingActivationLease lease =
                            service.lockOAuthFamilyActivation(activationKey(), 42L);
                    BoardOAuthFamilyActivationContext context =
                            service.prepareAndApplyLockedOAuthFamilyActivation(
                                    lease, attestation(), scenario.pendingFamilyId);
                    scenario.transitionToFinal(context);
                    service.verifyAndAppendLockedOAuthFamilyBindingReceipt(lease);
                    scenario.publishW4bReceipt(validW4bReceipt(context));
                    service.completeLockedOAuthFamilyActivation(lease);
                    scenario.currentNewTokens.get(0).setVersion(1L);
                    return null;
                }));

        assertEquals("BOARD_OAUTH_ACTIVATION_FINAL_STATE_INVALID", error.getMessage());
    }

    @Test
    void oauthClientChangeAfterCompleteIsRejectedByBeforeCommitRevalidation() {
        ActivationScenario scenario = stubFirstActivation();

        ServiceException error = assertThrows(ServiceException.class,
                () -> inActivationTransaction(() -> {
                    BoardConnectorBindingActivationLease lease =
                            service.lockOAuthFamilyActivation(activationKey(), 42L);
                    BoardOAuthFamilyActivationContext context =
                            service.prepareAndApplyLockedOAuthFamilyActivation(
                                    lease, attestation(), scenario.pendingFamilyId);
                    scenario.transitionToFinal(context);
                    service.verifyAndAppendLockedOAuthFamilyBindingReceipt(lease);
                    scenario.publishW4bReceipt(validW4bReceipt(context));
                    service.completeLockedOAuthFamilyActivation(lease);
                    scenario.currentClient.setStatus("REVOKED");
                    scenario.currentClient.setVersion(scenario.currentClient.getVersion() + 1L);
                    return null;
                }));

        assertEquals("BOARD_OAUTH_CLIENT_FINAL_STATE_INVALID", error.getMessage());
    }

    @Test
    void entitlementChangeAfterCompleteIsRejectedByBeforeCommitRevalidation() {
        ActivationScenario scenario = stubFirstActivation();

        ServiceException error = assertThrows(ServiceException.class,
                () -> inActivationTransaction(() -> {
                    BoardConnectorBindingActivationLease lease =
                            service.lockOAuthFamilyActivation(activationKey(), 42L);
                    BoardOAuthFamilyActivationContext context =
                            service.prepareAndApplyLockedOAuthFamilyActivation(
                                    lease, attestation(), scenario.pendingFamilyId);
                    scenario.transitionToFinal(context);
                    service.verifyAndAppendLockedOAuthFamilyBindingReceipt(lease);
                    scenario.publishW4bReceipt(validW4bReceipt(context));
                    service.completeLockedOAuthFamilyActivation(lease);
                    scenario.currentEntitlement.setStatus("REVOKED");
                    scenario.currentEntitlement.setVersion(
                            scenario.currentEntitlement.getVersion() + 1L);
                    return null;
                }));

        assertEquals("BOARD_OAUTH_ACTIVATION_AUTHORITY_DRIFT", error.getMessage());
    }

    @Test
    void accessTokenExpiryDuringTheTransactionRejectsCommit() {
        AtomicReference<Instant> currentTime = new AtomicReference<>(NOW);
        Clock advancingClock = mock(Clock.class);
        when(advancingClock.instant()).thenAnswer(invocation -> currentTime.get());
        service = new IndependentBoardConnectorBindingService(
                mapper, oauthMapper, properties, boardDataSource, advancingClock);
        ActivationScenario scenario = stubFirstActivation();

        ServiceException error = assertThrows(ServiceException.class,
                () -> inActivationTransaction(() -> {
                    BoardConnectorBindingActivationLease lease =
                            service.lockOAuthFamilyActivation(activationKey(), 42L);
                    BoardOAuthFamilyActivationContext context =
                            service.prepareAndApplyLockedOAuthFamilyActivation(
                                    lease, attestation(), scenario.pendingFamilyId);
                    scenario.transitionToFinal(context);
                    service.verifyAndAppendLockedOAuthFamilyBindingReceipt(lease);
                    scenario.publishW4bReceipt(validW4bReceipt(context));
                    service.completeLockedOAuthFamilyActivation(lease);
                    currentTime.set(NOW.plusSeconds(600));
                    return null;
                }));

        assertEquals("BOARD_OAUTH_ACTIVATION_AUTHORITY_EXPIRED", error.getMessage());
    }

    @Test
    void existingActiveBindingUsesNamedReauthorizationAndRequiresExactOldTerminalState() {
        ActivationScenario scenario = stubActiveReauthorization();

        BoardConnectorBindingSnapshot result = inActivationTransaction(() -> {
            BoardConnectorBindingActivationLease lease =
                    service.lockOAuthFamilyActivation(activationKey(), 42L);
            assertEquals(
                    BoardConnectorBindingActivationLease.Mode.EXPLICIT_REAUTHORIZATION,
                    lease.mode());
            BoardOAuthFamilyActivationContext context =
                    service.prepareAndApplyLockedOAuthFamilyActivation(
                            lease, reauthorizationAttestation(), scenario.pendingFamilyId);
            assertEquals("TOKEN_FAMILY_REAUTHORIZED", context.action());
            assertEquals(
                    BoardOAuthConsentIntent.EXPLICIT_REAUTHORIZATION,
                    context.consentIntent());
            assertEquals(lease.mode(), context.bindingTopology());
            scenario.transitionToFinal(context);
            service.verifyAndAppendLockedOAuthFamilyBindingReceipt(lease);
            scenario.publishW4bReceipt(validW4bReceipt(context));
            service.completeLockedOAuthFamilyActivation(lease);
            return context.binding();
        });

        assertEquals(BINDING_ID, result.bindingId());
        assertEquals(4L, result.version());
        assertEquals("REVOKED", scenario.currentOldFamily.getStatus());
        assertEquals(Date.from(NOW), scenario.currentOldFamily.getTerminatedAt());
        assertTrue(scenario.currentOldTokens.stream()
                .allMatch(token -> "REVOKED".equals(token.getStatus())
                        && Date.from(NOW).equals(token.getRevokedAt())));
        verify(mapper).reauthorizeConnectorBindingIfVersion(
                any(), org.mockito.ArgumentMatchers.eq(3L));
        verify(oauthMapper, times(3)).selectTokenFamilyForUpdate(
                scenario.oldActiveFamily.getFamilyId(),
                scenario.oldActiveFamily.getClientId());
    }

    @Test
    void terminalBindingUsesTheSameNamedReauthorizationPathWithoutAnOldActiveFamily() {
        ActivationScenario scenario = stubTerminalReauthorization();

        BoardConnectorBindingSnapshot result = inActivationTransaction(() -> {
            BoardConnectorBindingActivationLease lease =
                    service.lockOAuthFamilyActivation(activationKey(), 42L);
            BoardOAuthFamilyActivationContext context =
                    service.prepareAndApplyLockedOAuthFamilyActivation(
                            lease, reauthorizationAttestation(), scenario.pendingFamilyId);
            scenario.transitionToFinal(context);
            service.verifyAndAppendLockedOAuthFamilyBindingReceipt(lease);
            scenario.publishW4bReceipt(validW4bReceipt(context));
            service.completeLockedOAuthFamilyActivation(lease);
            return context.binding();
        });

        assertEquals(4L, result.version());
        assertEquals(null, scenario.oldActiveFamily);
        verify(mapper).reauthorizeConnectorBindingIfVersion(
                any(), org.mockito.ArgumentMatchers.eq(3L));
    }

    @Test
    void firstConnectIntentFailsIfABindingAppearsBeforeActivation() {
        ActivationScenario scenario = stubActiveReauthorization();
        scenario.pendingFamily.setConsentIntent(BoardOAuthConsentIntent.FIRST_CONNECT);

        ServiceException failure = assertThrows(ServiceException.class,
                () -> inActivationTransaction(() -> {
                    BoardConnectorBindingActivationLease lease =
                            service.lockOAuthFamilyActivation(activationKey(), 42L);
                    return service.prepareAndApplyLockedOAuthFamilyActivation(
                            lease, reauthorizationAttestation(), scenario.pendingFamilyId);
                }));

        assertEquals("BOARD_OAUTH_CONSENT_INTENT_TOPOLOGY_CONFLICT", failure.getMessage());
        assertEquals(409, failure.getCode());
        verify(mapper, never()).reauthorizeConnectorBindingIfVersion(any(), anyLong());
    }

    @Test
    void explicitReauthorizationIntentFailsIfTheBindingIsMissing() {
        ActivationScenario scenario = stubFirstActivation();
        scenario.pendingFamily.setConsentIntent(
                BoardOAuthConsentIntent.EXPLICIT_REAUTHORIZATION);

        ServiceException failure = assertThrows(ServiceException.class,
                () -> inActivationTransaction(() -> {
                    BoardConnectorBindingActivationLease lease =
                            service.lockOAuthFamilyActivation(activationKey(), 42L);
                    return service.prepareAndApplyLockedOAuthFamilyActivation(
                            lease, attestation(), scenario.pendingFamilyId);
                }));

        assertEquals("BOARD_OAUTH_CONSENT_INTENT_TOPOLOGY_CONFLICT", failure.getMessage());
        assertEquals(409, failure.getCode());
        verify(mapper, never()).insertConnectorBinding(any());
    }

    @Test
    void lockedIdentityKeyMustMatchTheVerifiedBearerAttestation() {
        stubActivationScope();
        BoardConnectorProtectedRequestAttestation wrongTenant =
                new BoardConnectorProtectedRequestAttestation(
                        8L, 11L, 42L, ISSUER, RESOURCE, "workbuddy-client",
                        "a".repeat(64), SCOPES,
                        IndependentBoardConnectorBindingService.VERIFY_INITIALIZE,
                        "b".repeat(64), Date.from(NOW.plusSeconds(3600)));

        ServiceException error = assertThrows(ServiceException.class,
                () -> inActivationTransaction(() -> {
                    BoardConnectorBindingActivationLease lease =
                            service.lockOAuthFamilyActivation(activationKey(), 42L);
                    return service.prepareAndApplyLockedOAuthFamilyActivation(
                            lease, wrongTenant, "pending-family-1");
                }));

        assertEquals(403, error.getCode());
        assertEquals("BOARD_CONNECTOR_ACTIVATION_SCOPE_MISMATCH", error.getMessage());
        verify(mapper, never()).insertConnectorBinding(any());
        verify(mapper, never()).reauthorizeConnectorBindingIfVersion(any(), anyLong());
        verifyNoInteractions(oauthMapper);
    }

    @Test
    void w4aReceiptCannotBeWrittenBeforeOAuthFinalStateExists() {
        ActivationScenario scenario = stubFirstActivation();

        ServiceException error = assertThrows(ServiceException.class,
                () -> inActivationTransaction(() -> {
                    BoardConnectorBindingActivationLease lease =
                            service.lockOAuthFamilyActivation(activationKey(), 42L);
                    service.prepareAndApplyLockedOAuthFamilyActivation(
                            lease, attestation(), scenario.pendingFamilyId);
                    service.verifyAndAppendLockedOAuthFamilyBindingReceipt(lease);
                    return null;
                }));

        assertEquals("BOARD_OAUTH_ACTIVATION_FINAL_STATE_INVALID", error.getMessage());
        verify(mapper, never()).insertConnectorBindingReceipt(any());
    }

    @Test
    void incompleteLeaseCannotCommitAfterW4aReceipt() {
        ActivationScenario scenario = stubFirstActivation();

        ServiceException error = assertThrows(ServiceException.class,
                () -> inActivationTransaction(() -> {
                    BoardConnectorBindingActivationLease lease =
                            service.lockOAuthFamilyActivation(activationKey(), 42L);
                    BoardOAuthFamilyActivationContext context =
                            service.prepareAndApplyLockedOAuthFamilyActivation(
                                    lease, attestation(), scenario.pendingFamilyId);
                    scenario.transitionToFinal(context);
                    service.verifyAndAppendLockedOAuthFamilyBindingReceipt(lease);
                    scenario.publishW4bReceipt(validW4bReceipt(context));
                    return null;
                }));

        assertEquals("BOARD_CONNECTOR_ACTIVATION_LEASE_INVALID", error.getMessage());
    }

    @Test
    void completedLeaseCannotBeReusedInAnotherTransaction() {
        ActivationScenario scenario = stubFirstActivation();
        BoardConnectorBindingActivationLease[] captured =
                new BoardConnectorBindingActivationLease[1];

        inActivationTransaction(() -> {
            captured[0] = service.lockOAuthFamilyActivation(activationKey(), 42L);
            BoardOAuthFamilyActivationContext context =
                    service.prepareAndApplyLockedOAuthFamilyActivation(
                            captured[0], attestation(), scenario.pendingFamilyId);
            scenario.transitionToFinal(context);
            service.verifyAndAppendLockedOAuthFamilyBindingReceipt(captured[0]);
            scenario.publishW4bReceipt(validW4bReceipt(context));
            service.completeLockedOAuthFamilyActivation(captured[0]);
            return null;
        });

        ServiceException error = assertThrows(ServiceException.class,
                () -> inActivationTransaction(() -> {
                    service.completeLockedOAuthFamilyActivation(captured[0]);
                    return null;
                }));
        assertEquals("BOARD_CONNECTOR_ACTIVATION_LEASE_INVALID", error.getMessage());
    }

    @Test
    void aSecondActivationLeaseInTheSameTransactionHasAStableConflictCode() {
        stubActivationScope();

        ServiceException error = assertThrows(ServiceException.class,
                () -> inActivationTransaction(() -> {
                    service.lockOAuthFamilyActivation(activationKey(), 42L);
                    service.lockOAuthFamilyActivation(activationKey(), 42L);
                    return null;
                }));

        assertEquals("BOARD_CONNECTOR_ACTIVATION_LEASE_ALREADY_BOUND", error.getMessage());
        verify(mapper, times(1)).selectExactActiveMemberForUpdate(7L, 11L, 42L);
    }

    @Test
    void packagePeerCannotForgeOrAdvanceAServiceOwnedActivationLease() {
        ActivationScenario scenario = stubFirstActivation();

        inActivationTransaction(() -> {
            BoardConnectorBindingActivationLease lease =
                    service.lockOAuthFamilyActivation(activationKey(), 42L);
            Object attackerCapability = new Object();
            assertThrows(IllegalStateException.class,
                    () -> lease.advance(
                            attackerCapability,
                            BoardConnectorBindingActivationLease.Phase.LOCKED,
                            BoardConnectorBindingActivationLease.Phase.COMPLETE));
            assertThrows(IllegalStateException.class,
                    () -> lease.invalidate(attackerCapability));
            assertThrows(IllegalStateException.class,
                    () -> lease.setAppliedBinding(attackerCapability, new BoardConnectorBinding()));

            BoardOAuthFamilyActivationContext context =
                    service.prepareAndApplyLockedOAuthFamilyActivation(
                            lease, attestation(), scenario.pendingFamilyId);
            assertThrows(IllegalStateException.class,
                    () -> lease.activationProof(attackerCapability));
            scenario.transitionToFinal(context);
            service.verifyAndAppendLockedOAuthFamilyBindingReceipt(lease);
            scenario.publishW4bReceipt(validW4bReceipt(context));
            service.completeLockedOAuthFamilyActivation(lease);
            return null;
        });
    }

    @Test
    void transactionSuspensionPermanentlyInvalidatesTheLease() {
        stubActivationScope();

        ServiceException error = assertThrows(ServiceException.class,
                () -> inActivationTransaction(() -> {
                    service.lockOAuthFamilyActivation(activationKey(), 42L);
                    TransactionSynchronization synchronization =
                            TransactionSynchronizationManager.getSynchronizations().get(0);
                    synchronization.suspend();
                    return null;
                }));

        assertEquals(500, error.getCode());
        assertEquals("BOARD_CONNECTOR_ACTIVATION_TRANSACTION_SUSPENDED", error.getMessage());
        verify(mapper, never()).insertConnectorBinding(any());
        verify(mapper, never()).insertConnectorBindingReceipt(any());
    }

    @Test
    void synchronizationRegisteredAfterCompleteCannotMutateAfterFinalProof() {
        ActivationScenario scenario = stubFirstActivation();

        ServiceException error = assertThrows(ServiceException.class,
                () -> inActivationTransaction(() -> {
                    BoardConnectorBindingActivationLease lease =
                            service.lockOAuthFamilyActivation(activationKey(), 42L);
                    BoardOAuthFamilyActivationContext context =
                            service.prepareAndApplyLockedOAuthFamilyActivation(
                                    lease, attestation(), scenario.pendingFamilyId);
                    scenario.transitionToFinal(context);
                    service.verifyAndAppendLockedOAuthFamilyBindingReceipt(lease);
                    scenario.publishW4bReceipt(validW4bReceipt(context));
                    service.completeLockedOAuthFamilyActivation(lease);
                    TransactionSynchronizationManager.registerSynchronization(
                            new TransactionSynchronization() {
                                @Override
                                public void beforeCommit(boolean readOnly) {
                                    scenario.currentNewFamily.setStatus("REVOKED");
                                }
                            });
                    return null;
                }));

        assertEquals(
                "BOARD_CONNECTOR_ACTIVATION_SYNCHRONIZATION_DRIFT",
                error.getMessage());
        assertEquals("ACTIVE", scenario.currentNewFamily.getStatus());
    }

    private ActivationScenario stubFirstActivation() {
        return new ActivationScenario(attestation(), null, null, List.of());
    }

    private ActivationScenario stubActiveReauthorization() {
        BoardConnectorBinding active = binding();
        active.setVersion(3L);
        BoardOAuthTokenFamily oldFamily = oldActiveFamily(active);
        return new ActivationScenario(
                reauthorizationAttestation(), active, oldFamily, activeTokens(oldFamily));
    }

    private ActivationScenario stubTerminalReauthorization() {
        BoardConnectorBinding revoked = binding();
        revoked.setStatus(IndependentBoardConnectorBindingService.STATUS_REVOKED);
        revoked.setRevokedAt(Date.from(NOW.minusSeconds(30)));
        revoked.setVersion(3L);
        return new ActivationScenario(
                reauthorizationAttestation(), revoked, null, List.of());
    }

    private BoardOAuthReceipt validW4bReceipt(BoardOAuthFamilyActivationContext context) {
        BoardOAuthReceipt receipt = new BoardOAuthReceipt();
        receipt.setId(7001L);
        receipt.setReceiptId(context.receiptId());
        receipt.setAction(context.action());
        receipt.setClientId(context.clientId());
        receipt.setFamilyId(context.familyId());
        receipt.setBindingId(context.bindingId());
        receipt.setTenantId(context.tenantId());
        receipt.setMemberId(context.memberId());
        receipt.setUserId(context.userId());
        receipt.setPrincipalSubjectDigest(context.principalSubjectDigest());
        receipt.setActorType(context.actorType());
        receipt.setActorUserId(context.actorUserId());
        receipt.setActorSubjectDigest(context.actorSubjectDigest());
        receipt.setCorrelationId(context.correlationId());
        receipt.setPayloadDigest(context.payloadDigest());
        receipt.setEvidenceLevel(context.evidenceLevel());
        receipt.setCreatedAt(context.createdAt());
        return receipt;
    }

    private BoardOAuthClient oauthClient(BoardConnectorProtectedRequestAttestation candidate) {
        BoardOAuthClient client = new BoardOAuthClient();
        client.setId(101L);
        client.setClientId(candidate.clientId());
        client.setIssuerUri(BoardOAuthProfile.ISSUER);
        client.setResourceUri(BoardOAuthProfile.RESOURCE);
        client.setProductCode(IndependentBoardEntitlementService.PRODUCT_CODE);
        client.setSourceCode(IndependentBoardConnectorBindingService.SOURCE_CODE);
        client.setConnectorCode(IndependentBoardConnectorBindingService.CONNECTOR_CODE);
        client.setRedirectPort(34567);
        client.setRedirectUri("http://127.0.0.1:34567/oauth/callback");
        client.setTokenEndpointAuthMethod("none");
        client.setGrantTypesCanonical("authorization_code refresh_token");
        client.setResponseTypesCanonical("code");
        client.setScopeCanonical(BoardOAuthProfile.CANONICAL_SCOPE);
        client.setScopeDigest(oauthScopeDigest());
        client.setMetadataDigest(BoardOAuthCrypto.sha256Ascii("client-metadata"));
        client.setRegistrationSourceDigest(BoardOAuthCrypto.sha256Ascii("registration-source"));
        client.setStatus("ACTIVE");
        client.setRegisteredAt(Date.from(NOW.minusSeconds(3600)));
        client.setExpiresAt(new Date(candidate.validUntil().getTime() + 3_600_000L));
        client.setVersion(1L);
        return client;
    }

    private BoardOAuthTokenFamily pendingFamily(
            BoardConnectorProtectedRequestAttestation candidate,
            String familyId) {
        BoardOAuthTokenFamily family = baseFamily(
                201L,
                familyId,
                301L,
                candidate.clientId(),
                hexDigest(candidate.principalSubjectDigest()),
                candidate.validUntil());
        family.setStatus("PENDING_BINDING");
        family.setLifecycleSlot("PENDING_BINDING");
        family.setCurrentRefreshGeneration(0L);
        family.setIssuedAt(Date.from(NOW.minusSeconds(60)));
        family.setVersion(0L);
        return family;
    }

    private BoardOAuthTokenFamily oldActiveFamily(BoardConnectorBinding existing) {
        BoardOAuthTokenFamily family = baseFamily(
                202L,
                "old-active-family",
                302L,
                existing.getClientId(),
                hexDigest(existing.getPrincipalSubjectDigest()),
                Date.from(NOW.plusSeconds(3600)));
        family.setBindingId(existing.getBindingId());
        family.setBindingVersion(existing.getVersion());
        family.setStatus("ACTIVE");
        family.setLifecycleSlot("ACTIVE");
        family.setCurrentRefreshGeneration(2L);
        family.setIssuedAt(Date.from(NOW.minusSeconds(3600)));
        family.setActivatedAt(Date.from(NOW.minusSeconds(3500)));
        family.setVersion(5L);
        return family;
    }

    private BoardOAuthTokenFamily baseFamily(
            Long id,
            String familyId,
            Long codeId,
            String clientId,
            byte[] principalDigest,
            Date expiresAt) {
        BoardOAuthTokenFamily family = new BoardOAuthTokenFamily();
        family.setId(id);
        family.setFamilyId(familyId);
        family.setOriginAuthorizationCodeId(codeId);
        family.setClientId(clientId);
        family.setTenantId(7L);
        family.setMemberId(11L);
        family.setUserId(42L);
        family.setProductCode(IndependentBoardEntitlementService.PRODUCT_CODE);
        family.setSourceCode(IndependentBoardConnectorBindingService.SOURCE_CODE);
        family.setConnectorCode(IndependentBoardConnectorBindingService.CONNECTOR_CODE);
        family.setIssuerUri(BoardOAuthProfile.ISSUER);
        family.setResourceUri(BoardOAuthProfile.RESOURCE);
        family.setScopeCanonical(BoardOAuthProfile.CANONICAL_SCOPE);
        family.setScopeDigest(oauthScopeDigest());
        family.setPrincipalSubjectDigest(principalDigest);
        family.setConsentIntent(BoardOAuthConsentIntent.FIRST_CONNECT);
        family.setExpiresAt(copy(expiresAt));
        return family;
    }

    private List<BoardOAuthToken> pendingTokens(BoardOAuthTokenFamily family) {
        Date issuedAt = Date.from(NOW.minusSeconds(30));
        return List.of(
                token(401L, family.getFamilyId(), "ACCESS", 0L, "ACTIVE", 0L,
                        issuedAt, new Date(issuedAt.getTime() + 600_000L)),
                token(402L, family.getFamilyId(), "REFRESH", 0L, "ACTIVE", 0L,
                        issuedAt, family.getExpiresAt()));
    }

    private List<BoardOAuthToken> activeTokens(BoardOAuthTokenFamily family) {
        Date issuedAt = Date.from(NOW.minusSeconds(600));
        return List.of(
                token(403L, family.getFamilyId(), "ACCESS", 2L, "ACTIVE", 1L,
                        issuedAt, Date.from(NOW.plusSeconds(600))),
                token(404L, family.getFamilyId(), "REFRESH", 2L, "ACTIVE", 1L,
                        issuedAt, family.getExpiresAt()));
    }

    private BoardOAuthToken token(
            Long id,
            String familyId,
            String type,
            Long generation,
            String status,
            Long version,
            Date issuedAt,
            Date expiresAt) {
        BoardOAuthToken token = new BoardOAuthToken();
        token.setId(id);
        token.setTokenDigest(BoardOAuthCrypto.sha256Ascii(familyId + "|" + type));
        token.setFamilyId(familyId);
        token.setTokenType(type);
        token.setGeneration(generation);
        token.setResourceUri(BoardOAuthProfile.RESOURCE);
        token.setScopeCanonical(BoardOAuthProfile.CANONICAL_SCOPE);
        token.setScopeDigest(oauthScopeDigest());
        token.setStatus(status);
        token.setIssuedAt(copy(issuedAt));
        token.setExpiresAt(copy(expiresAt));
        token.setVersion(version);
        return token;
    }

    private BoardOAuthTokenFamily copyFamily(BoardOAuthTokenFamily source) {
        BoardOAuthTokenFamily copy = baseFamily(
                source.getId(),
                source.getFamilyId(),
                source.getOriginAuthorizationCodeId(),
                source.getClientId(),
                source.getPrincipalSubjectDigest() == null
                        ? null
                        : source.getPrincipalSubjectDigest().clone(),
                source.getExpiresAt());
        copy.setBindingId(source.getBindingId());
        copy.setBindingVersion(source.getBindingVersion());
        copy.setConsentIntent(source.getConsentIntent());
        copy.setStatus(source.getStatus());
        copy.setLifecycleSlot(source.getLifecycleSlot());
        copy.setCurrentRefreshGeneration(source.getCurrentRefreshGeneration());
        copy.setIssuedAt(copy(source.getIssuedAt()));
        copy.setActivatedAt(copy(source.getActivatedAt()));
        copy.setTerminatedAt(copy(source.getTerminatedAt()));
        copy.setVersion(source.getVersion());
        return copy;
    }

    private BoardOAuthToken copyToken(BoardOAuthToken source) {
        BoardOAuthToken copy = token(
                source.getId(),
                source.getFamilyId(),
                source.getTokenType(),
                source.getGeneration(),
                source.getStatus(),
                source.getVersion(),
                source.getIssuedAt(),
                source.getExpiresAt());
        copy.setTokenDigest(source.getTokenDigest().clone());
        copy.setUsedAt(copy(source.getUsedAt()));
        copy.setRevokedAt(copy(source.getRevokedAt()));
        return copy;
    }

    private byte[] oauthScopeDigest() {
        return BoardOAuthCrypto.sha256Ascii(BoardOAuthProfile.CANONICAL_SCOPE);
    }

    private byte[] hexDigest(String value) {
        return HexFormat.of().parseHex(value);
    }

    private Date copy(Date value) {
        return value == null ? null : new Date(value.getTime());
    }

    private final class ActivationScenario {
        private final BoardConnectorProtectedRequestAttestation candidate;
        private final String pendingFamilyId;
        private final BoardConnectorBinding existingBinding;
        private final BoardEnterpriseMemberScope currentMember;
        private final BoardProductEntitlement currentEntitlement;
        private final BoardProductPlan currentPlan;
        private final BoardOAuthClient currentClient;
        private final BoardOAuthTokenFamily pendingFamily;
        private final List<BoardOAuthToken> pendingTokenImages;
        private final BoardOAuthTokenFamily oldActiveFamily;
        private final List<BoardOAuthToken> oldActiveTokenImages;
        private final AtomicBoolean finalState = new AtomicBoolean();
        private final AtomicReference<BoardConnectorBinding> currentBinding = new AtomicReference<>();
        private final AtomicReference<BoardConnectorBindingReceipt> w4aReceipt = new AtomicReference<>();
        private final AtomicReference<BoardOAuthReceipt> w4bReceipt = new AtomicReference<>();
        private BoardOAuthTokenFamily currentNewFamily;
        private List<BoardOAuthToken> currentNewTokens = List.of();
        private BoardOAuthTokenFamily currentOldFamily;
        private List<BoardOAuthToken> currentOldTokens = List.of();

        private ActivationScenario(
                BoardConnectorProtectedRequestAttestation candidate,
                BoardConnectorBinding existingBinding,
                BoardOAuthTokenFamily oldActiveFamily,
                List<BoardOAuthToken> oldActiveTokens) {
            this.candidate = candidate;
            this.pendingFamilyId = "pending-family-1";
            this.existingBinding = existingBinding;
            this.currentMember = member();
            this.currentEntitlement = entitlement();
            this.currentPlan = vipPlan();
            this.currentClient = oauthClient(candidate);
            this.pendingFamily = pendingFamily(candidate, pendingFamilyId);
            this.pendingFamily.setConsentIntent(existingBinding == null
                    ? BoardOAuthConsentIntent.FIRST_CONNECT
                    : BoardOAuthConsentIntent.EXPLICIT_REAUTHORIZATION);
            this.pendingTokenImages = pendingTokens(pendingFamily);
            this.oldActiveFamily = oldActiveFamily;
            this.oldActiveTokenImages = oldActiveTokens;
            install();
        }

        private void install() {
            when(mapper.selectExactActiveMemberForUpdate(7L, 11L, 42L))
                    .thenReturn(currentMember);
            when(mapper.selectEntitlementForUpdate(
                    7L, 11L, IndependentBoardEntitlementService.PRODUCT_CODE))
                    .thenReturn(currentEntitlement);
            when(mapper.selectActivePlanForUpdate(
                    IndependentBoardEntitlementService.PRODUCT_CODE,
                    IndependentBoardEntitlementService.VIP_PLAN))
                    .thenReturn(currentPlan);
            when(oauthMapper.selectClientForUpdate(candidate.clientId()))
                    .thenReturn(currentClient);
            when(oauthMapper.selectActiveTokenFamilySlotForUpdate(
                    7L, 11L, IndependentBoardEntitlementService.PRODUCT_CODE,
                    IndependentBoardConnectorBindingService.SOURCE_CODE,
                    IndependentBoardConnectorBindingService.CONNECTOR_CODE))
                    .thenAnswer(invocation -> finalState.get()
                            ? currentNewFamily
                            : oldActiveFamily);
            when(oauthMapper.selectPendingTokenFamilySlotForUpdate(
                    7L, 11L, IndependentBoardEntitlementService.PRODUCT_CODE,
                    IndependentBoardConnectorBindingService.SOURCE_CODE,
                    IndependentBoardConnectorBindingService.CONNECTOR_CODE))
                    .thenAnswer(invocation -> finalState.get() ? null : pendingFamily);
            when(oauthMapper.selectFamilyTokensForUpdate(anyString()))
                    .thenAnswer(invocation -> tokensFor(invocation.getArgument(0)));
            when(oauthMapper.selectTokenFamilyForUpdate(anyString(), anyString()))
                    .thenAnswer(invocation -> familyFor(
                            invocation.getArgument(0), invocation.getArgument(1)));
            when(oauthMapper.selectReceiptByReceiptId(anyString()))
                    .thenAnswer(invocation -> {
                        BoardOAuthReceipt receipt = w4bReceipt.get();
                        return receipt != null
                                && receipt.getReceiptId().equals(invocation.getArgument(0))
                                ? receipt
                                : null;
                    });

            when(mapper.insertConnectorBindingScope(anyString(), anyString(), any()))
                    .thenReturn(1);
            when(mapper.selectConnectorBindingScopesForUpdate(anyString()))
                    .thenReturn(SCOPES);
            when(mapper.selectConnectorBindingForUpdate(
                    7L, 11L, 42L, IndependentBoardEntitlementService.PRODUCT_CODE,
                    IndependentBoardConnectorBindingService.SOURCE_CODE,
                    IndependentBoardConnectorBindingService.CONNECTOR_CODE))
                    .thenAnswer(invocation -> currentBinding.get());
            when(mapper.insertConnectorBindingReceipt(any())).thenAnswer(invocation -> {
                w4aReceipt.set(invocation.getArgument(0));
                return 1;
            });
            when(mapper.selectConnectorBindingReceiptForUpdate(anyString()))
                    .thenAnswer(invocation -> {
                        BoardConnectorBindingReceipt receipt = w4aReceipt.get();
                        return receipt != null
                                && receipt.getReceiptId().equals(invocation.getArgument(0))
                                ? receipt
                                : null;
                    });

            if (existingBinding == null) {
                when(mapper.insertConnectorBinding(any())).thenAnswer(invocation -> {
                    BoardConnectorBinding inserted = invocation.getArgument(0);
                    inserted.setId(501L);
                    inserted.setCreatedAt(inserted.getVerifiedAt());
                    inserted.setUpdatedAt(inserted.getVerifiedAt());
                    currentBinding.set(inserted);
                    return 1;
                });
            } else {
                currentBinding.set(existingBinding);
                when(mapper.selectConnectorBindingSlotForUpdate(
                        7L, 11L, IndependentBoardEntitlementService.PRODUCT_CODE,
                        IndependentBoardConnectorBindingService.SOURCE_CODE,
                        IndependentBoardConnectorBindingService.CONNECTOR_CODE))
                        .thenReturn(existingBinding);
                when(mapper.reauthorizeConnectorBindingIfVersion(any(), anyLong()))
                        .thenAnswer(invocation -> {
                            BoardConnectorBinding updated = invocation.getArgument(0);
                            updated.setUpdatedAt(updated.getVerifiedAt());
                            return 1;
                        });
            }
        }

        private void transitionToFinal(BoardOAuthFamilyActivationContext context) {
            assertNotNull(context);
            currentNewFamily = copyFamily(pendingFamily);
            currentNewFamily.setBindingId(context.bindingId());
            currentNewFamily.setBindingVersion(context.bindingVersion());
            currentNewFamily.setStatus("ACTIVE");
            currentNewFamily.setLifecycleSlot("ACTIVE");
            currentNewFamily.setActivatedAt(context.createdAt());
            currentNewFamily.setVersion(pendingFamily.getVersion() + 1L);
            currentNewTokens = pendingTokenImages.stream()
                    .map(IndependentBoardConnectorBindingServiceTest.this::copyToken)
                    .toList();

            if (oldActiveFamily != null) {
                currentOldFamily = copyFamily(oldActiveFamily);
                currentOldFamily.setStatus("REVOKED");
                currentOldFamily.setLifecycleSlot(null);
                currentOldFamily.setTerminatedAt(context.createdAt());
                currentOldFamily.setVersion(oldActiveFamily.getVersion() + 1L);
                currentOldTokens = oldActiveTokenImages.stream()
                        .map(IndependentBoardConnectorBindingServiceTest.this::copyToken)
                        .toList();
                for (BoardOAuthToken token : currentOldTokens) {
                    if ("ACTIVE".equals(token.getStatus())) {
                        token.setStatus("REVOKED");
                        token.setRevokedAt(context.createdAt());
                        token.setVersion(token.getVersion() + 1L);
                    }
                }
            }
            finalState.set(true);
        }

        private void publishW4bReceipt(BoardOAuthReceipt receipt) {
            w4bReceipt.set(receipt);
        }

        private List<BoardOAuthToken> tokensFor(String familyId) {
            if (pendingFamilyId.equals(familyId)) {
                return finalState.get() ? currentNewTokens : pendingTokenImages;
            }
            if (oldActiveFamily != null && oldActiveFamily.getFamilyId().equals(familyId)) {
                return finalState.get() ? currentOldTokens : oldActiveTokenImages;
            }
            return null;
        }

        private BoardOAuthTokenFamily familyFor(String familyId, String clientId) {
            if (!finalState.get()) {
                return null;
            }
            if (currentNewFamily != null
                    && currentNewFamily.getFamilyId().equals(familyId)
                    && currentNewFamily.getClientId().equals(clientId)) {
                return currentNewFamily;
            }
            if (currentOldFamily != null
                    && currentOldFamily.getFamilyId().equals(familyId)
                    && currentOldFamily.getClientId().equals(clientId)) {
                return currentOldFamily;
            }
            return null;
        }
    }

    private void stubActivationScope() {
        when(mapper.selectExactActiveMemberForUpdate(7L, 11L, 42L)).thenReturn(member());
        when(mapper.selectEntitlementForUpdate(
                7L, 11L, IndependentBoardEntitlementService.PRODUCT_CODE)).thenReturn(entitlement());
    }

    private BoardConnectorBindingKey activationKey() {
        return new BoardConnectorBindingKey(
                7L, 11L, 42L, IndependentBoardEntitlementService.PRODUCT_CODE);
    }

    private <T> T inActivationTransaction(Supplier<T> work) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                || TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("test transaction already active");
        }
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        ConnectionHolder boardConnectionHolder = new ConnectionHolder(boardConnection);
        boardConnectionHolder.setSynchronizedWithTransaction(true);
        TransactionSynchronizationManager.bindResource(
                boardDataSource, boardConnectionHolder);
        List<TransactionSynchronization> synchronizations = List.of();
        boolean beforeCompletionCalled = false;
        boolean committed = false;
        try {
            T result = work.get();
            synchronizations = List.copyOf(
                    TransactionSynchronizationManager.getSynchronizations());
            for (TransactionSynchronization synchronization : synchronizations) {
                synchronization.beforeCommit(false);
            }
            for (TransactionSynchronization synchronization : synchronizations) {
                synchronization.beforeCompletion();
            }
            beforeCompletionCalled = true;
            for (TransactionSynchronization synchronization : synchronizations) {
                synchronization.afterCommit();
            }
            committed = true;
            return result;
        } finally {
            if (synchronizations.isEmpty()
                    && TransactionSynchronizationManager.isSynchronizationActive()) {
                synchronizations = List.copyOf(
                        TransactionSynchronizationManager.getSynchronizations());
            }
            if (!beforeCompletionCalled) {
                for (TransactionSynchronization synchronization : synchronizations) {
                    synchronization.beforeCompletion();
                }
            }
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.clearSynchronization();
            }
            int completionStatus = committed
                    ? TransactionSynchronization.STATUS_COMMITTED
                    : TransactionSynchronization.STATUS_ROLLED_BACK;
            for (TransactionSynchronization synchronization : synchronizations) {
                synchronization.afterCompletion(completionStatus);
            }
            if (TransactionSynchronizationManager.hasResource(boardDataSource)) {
                TransactionSynchronizationManager.unbindResource(boardDataSource);
            }
            TransactionSynchronizationManager.setActualTransactionActive(false);
            TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
            TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(null);
            TransactionSynchronizationManager.setCurrentTransactionName(null);
        }
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

    private BoardConnectorProtectedRequestAttestation reauthorizationAttestation() {
        return new BoardConnectorProtectedRequestAttestation(
                7L, 11L, 42L, ISSUER, RESOURCE, "new-workbuddy-client",
                "c".repeat(64), SCOPES,
                IndependentBoardConnectorBindingService.VERIFY_TOOLS_LIST,
                "d".repeat(64), Date.from(NOW.plusSeconds(7200)));
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

    private BoardEnterpriseAuthority enterprise(int status) {
        BoardEnterpriseAuthority value = new BoardEnterpriseAuthority();
        value.setTenantId(7L);
        value.setTenantName("Board tenant");
        value.setStatus(status);
        value.setDelFlag("0");
        return value;
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
        binding.setCreatedAt(Date.from(NOW.minusSeconds(7200)));
        binding.setUpdatedAt(Date.from(NOW.minusSeconds(60)));
        binding.setScopes(SCOPES);
        return binding;
    }
}
