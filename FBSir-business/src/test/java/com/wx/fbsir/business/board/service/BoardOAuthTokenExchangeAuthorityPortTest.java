package com.wx.fbsir.business.board.service;

import com.wx.fbsir.business.board.config.IndependentBoardConnectorProperties;
import com.wx.fbsir.business.board.domain.BoardConnectorBinding;
import com.wx.fbsir.business.board.domain.BoardEnterpriseAuthority;
import com.wx.fbsir.business.board.domain.BoardEnterpriseMemberScope;
import com.wx.fbsir.business.board.domain.BoardProductEntitlement;
import com.wx.fbsir.business.board.domain.BoardProductPlan;
import com.wx.fbsir.business.board.mapper.IndependentBoardMapper;
import com.wx.fbsir.business.board.oauth.BoardOAuthConsentIntent;
import com.wx.fbsir.business.board.oauth.mapper.IndependentBoardOAuthMapper;
import com.wx.fbsir.business.board.oauth.service.BoardOAuthTokenExchangeAuthorityPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BoardOAuthTokenExchangeAuthorityPortTest {
    private static final Instant NOW = Instant.parse("2026-07-21T00:00:00.123Z");
    private static final String PRODUCT = "FBSIR_INDEPENDENT_BOARD";

    @Test
    void absentBindingLocksTheUniqueGapAndPermitsOnlyFirstConnectWithoutWrites() {
        IndependentBoardMapper mapper = mock(IndependentBoardMapper.class);
        IndependentBoardConnectorBindingService service = service(mapper);
        when(mapper.selectEnterpriseSlotForUpdate(7L)).thenReturn(enterprise(1));
        when(mapper.selectMemberSlotForUpdate(7L, 8L)).thenReturn(member(1));
        when(mapper.selectEntitlementForUpdate(7L, 8L, PRODUCT))
                .thenReturn(entitlement("ACTIVE"));
        when(mapper.selectPlanSlotForUpdate(PRODUCT, "BOARD_VIP"))
                .thenReturn(plan("ACTIVE"));
        when(mapper.selectConnectorBindingSlotForUpdate(
                7L, 8L, PRODUCT, "WORKBUDDY", "fbs-connector"))
                .thenReturn(null);

        BoardOAuthTokenExchangeAuthorityPort.LockResult result =
                service.lockForTokenExchange(
                        7L, 8L, 9L, PRODUCT, "WORKBUDDY", "fbs-connector", NOW);

        assertTrue(result.issuanceAuthorityCurrent());
        assertTrue(result.permits(BoardOAuthConsentIntent.FIRST_CONNECT));
        assertFalse(result.permits(
                BoardOAuthConsentIntent.EXPLICIT_REAUTHORIZATION));
        InOrder order = inOrder(mapper);
        order.verify(mapper).selectEnterpriseSlotForUpdate(7L);
        order.verify(mapper).selectMemberSlotForUpdate(7L, 8L);
        order.verify(mapper).selectEntitlementForUpdate(7L, 8L, PRODUCT);
        order.verify(mapper).selectPlanSlotForUpdate(PRODUCT, "BOARD_VIP");
        order.verify(mapper).selectConnectorBindingSlotForUpdate(
                7L, 8L, PRODUCT, "WORKBUDDY", "fbs-connector");
        verify(mapper, never()).selectConnectorBindingScopesForUpdate(
                org.mockito.ArgumentMatchers.any());
        verify(mapper, never()).insertConnectorBinding(
                org.mockito.ArgumentMatchers.any());
        verify(mapper, never()).insertConnectorBindingScope(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(mapper, never()).insertConnectorBindingReceipt(
                org.mockito.ArgumentMatchers.any());
        verify(mapper, never()).reauthorizeConnectorBindingIfVersion(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(mapper, never()).revokeConnectorBindingIfVersion(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void tokenExchangeUsesFixedPlanIdentityAndPassesDynamicPolicyValues() {
        IndependentBoardMapper mapper = mock(IndependentBoardMapper.class);
        IndependentBoardConnectorBindingService service = service(mapper);
        when(mapper.selectEnterpriseSlotForUpdate(7L)).thenReturn(enterprise(1));
        when(mapper.selectMemberSlotForUpdate(7L, 8L)).thenReturn(member(1));
        when(mapper.selectEntitlementForUpdate(7L, 8L, PRODUCT))
                .thenReturn(entitlement("ACTIVE"));
        BoardProductPlan dynamic = plan("ACTIVE");
        dynamic.setDailyMeetingLimit(9);
        dynamic.setAgendaLimit(47);
        dynamic.setSeatLimit(12);
        dynamic.setSecretaryEnabled(false);
        when(mapper.selectPlanSlotForUpdate(PRODUCT, "BOARD_VIP"))
                .thenReturn(dynamic);

        BoardOAuthTokenExchangeAuthorityPort.LockResult accepted =
                service.lockForTokenExchange(
                        7L, 8L, 9L, PRODUCT, "WORKBUDDY", "fbs-connector", NOW);

        assertTrue(accepted.planCurrent());
        assertTrue(accepted.issuanceAuthorityCurrent());

        dynamic.setConnectorRequired(false);
        BoardOAuthTokenExchangeAuthorityPort.LockResult rejected =
                service.lockForTokenExchange(
                        7L, 8L, 9L, PRODUCT, "WORKBUDDY", "fbs-connector", NOW);
        assertFalse(rejected.planCurrent());
        assertFalse(rejected.issuanceAuthorityCurrent());
    }

    @Test
    void inactiveEnterpriseIsReportedOnlyAfterEveryAuthoritySlotWasLocked() {
        IndependentBoardMapper mapper = mock(IndependentBoardMapper.class);
        IndependentBoardConnectorBindingService service = service(mapper);
        when(mapper.selectEnterpriseSlotForUpdate(7L)).thenReturn(enterprise(0));
        when(mapper.selectMemberSlotForUpdate(7L, 8L)).thenReturn(member(1));
        when(mapper.selectEntitlementForUpdate(7L, 8L, PRODUCT))
                .thenReturn(entitlement("ACTIVE"));
        when(mapper.selectPlanSlotForUpdate(PRODUCT, "BOARD_VIP"))
                .thenReturn(plan("ACTIVE"));

        BoardOAuthTokenExchangeAuthorityPort.LockResult result =
                service.lockForTokenExchange(
                        7L, 8L, 9L, PRODUCT, "WORKBUDDY", "fbs-connector", NOW);

        assertFalse(result.issuanceAuthorityCurrent());
        assertFalse(result.enterpriseCurrent());
        InOrder order = inOrder(mapper);
        order.verify(mapper).selectEnterpriseSlotForUpdate(7L);
        order.verify(mapper).selectMemberSlotForUpdate(7L, 8L);
        order.verify(mapper).selectEntitlementForUpdate(7L, 8L, PRODUCT);
        order.verify(mapper).selectPlanSlotForUpdate(PRODUCT, "BOARD_VIP");
        order.verify(mapper).selectConnectorBindingSlotForUpdate(
                7L, 8L, PRODUCT, "WORKBUDDY", "fbs-connector");
    }

    @Test
    void entitlementExpiryIsExclusiveForTheFinalIssuanceDecision() {
        IndependentBoardMapper mapper = mock(IndependentBoardMapper.class);
        IndependentBoardConnectorBindingService service = service(mapper);
        when(mapper.selectEnterpriseSlotForUpdate(7L)).thenReturn(enterprise(1));
        when(mapper.selectMemberSlotForUpdate(7L, 8L)).thenReturn(member(1));
        when(mapper.selectEntitlementForUpdate(7L, 8L, PRODUCT))
                .thenReturn(entitlement("ACTIVE"));
        when(mapper.selectPlanSlotForUpdate(PRODUCT, "BOARD_VIP"))
                .thenReturn(plan("ACTIVE"));

        BoardOAuthTokenExchangeAuthorityPort.LockResult result =
                service.lockForTokenExchange(
                        7L, 8L, 9L, PRODUCT, "WORKBUDDY", "fbs-connector", NOW);

        assertTrue(result.issuanceAuthorityCurrentAt(NOW.plusSeconds(59)));
        assertFalse(result.issuanceAuthorityCurrentAt(NOW.plusSeconds(60)));
    }

    @Test
    void expiredTerminalBindingStillPermitsExplicitReauthorization() {
        IndependentBoardMapper mapper = mock(IndependentBoardMapper.class);
        IndependentBoardConnectorBindingService service = service(mapper);
        when(mapper.selectEnterpriseSlotForUpdate(7L)).thenReturn(enterprise(1));
        when(mapper.selectMemberSlotForUpdate(7L, 8L)).thenReturn(member(1));
        when(mapper.selectEntitlementForUpdate(7L, 8L, PRODUCT))
                .thenReturn(entitlement("ACTIVE"));
        when(mapper.selectPlanSlotForUpdate(PRODUCT, "BOARD_VIP"))
                .thenReturn(plan("ACTIVE"));
        BoardConnectorBinding binding = expiredRevokedBinding();
        when(mapper.selectConnectorBindingSlotForUpdate(
                7L, 8L, PRODUCT, "WORKBUDDY", "fbs-connector"))
                .thenReturn(binding);
        when(mapper.selectConnectorBindingScopesForUpdate(binding.getBindingId()))
                .thenReturn(List.copyOf(
                        IndependentBoardConnectorBindingService.REQUIRED_SCOPES));

        BoardOAuthTokenExchangeAuthorityPort.LockResult result =
                service.lockForTokenExchange(
                        7L, 8L, 9L, PRODUCT, "WORKBUDDY", "fbs-connector", NOW);

        assertTrue(result.bindingShapeCurrent());
        assertTrue(result.permits(
                BoardOAuthConsentIntent.EXPLICIT_REAUTHORIZATION, NOW));
        assertTrue(result.issuanceAuthorityCurrentAt(NOW.plusSeconds(59)));
    }

    private static IndependentBoardConnectorBindingService service(
            IndependentBoardMapper mapper) {
        return new IndependentBoardConnectorBindingService(
                mapper,
                mock(IndependentBoardOAuthMapper.class),
                new IndependentBoardConnectorProperties(),
                mock(DataSource.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static BoardEnterpriseAuthority enterprise(int status) {
        BoardEnterpriseAuthority value = new BoardEnterpriseAuthority();
        value.setTenantId(7L);
        value.setTenantName("Board tenant");
        value.setStatus(status);
        value.setDelFlag("0");
        return value;
    }

    private static BoardEnterpriseMemberScope member(int status) {
        BoardEnterpriseMemberScope value = new BoardEnterpriseMemberScope();
        value.setTenantId(7L);
        value.setMemberId(8L);
        value.setUserId(9L);
        value.setStatus(status);
        value.setDelFlag("0");
        return value;
    }

    private static BoardProductEntitlement entitlement(String status) {
        BoardProductEntitlement value = new BoardProductEntitlement();
        value.setId(10L);
        value.setTenantId(7L);
        value.setMemberId(8L);
        value.setUserId(9L);
        value.setProductCode(PRODUCT);
        value.setPlanCode("BOARD_VIP");
        value.setStatus(status);
        value.setValidFrom(Date.from(NOW.minusSeconds(60)));
        value.setValidUntil(Date.from(NOW.plusSeconds(60)));
        value.setVersion(0L);
        return value;
    }

    private static BoardProductPlan plan(String status) {
        BoardProductPlan value = new BoardProductPlan();
        value.setProductCode(PRODUCT);
        value.setPlanCode("BOARD_VIP");
        value.setVip(true);
        value.setConnectorRequired(true);
        value.setDailyMeetingLimit(5);
        value.setAgendaLimit(30);
        value.setSeatLimit(null);
        value.setSecretaryEnabled(true);
        value.setStatus(status);
        return value;
    }

    private static BoardConnectorBinding expiredRevokedBinding() {
        BoardConnectorBinding value = new BoardConnectorBinding();
        value.setId(11L);
        value.setBindingId("11111111-1111-4111-8111-111111111111");
        value.setTenantId(7L);
        value.setMemberId(8L);
        value.setUserId(9L);
        value.setProductCode(PRODUCT);
        value.setSourceCode("WORKBUDDY");
        value.setConnectorCode("fbs-connector");
        value.setIssuerUri("https://api2.u3w.com");
        value.setResourceUri("https://api2.u3w.com/fbs-mcp/mcp");
        value.setClientId("c".repeat(43));
        value.setPrincipalSubjectDigest("a".repeat(64));
        value.setStatus("REVOKED");
        value.setVerificationMethod("MCP_INITIALIZE");
        value.setEvidenceDigest("b".repeat(64));
        value.setVerifiedAt(Date.from(NOW.minusSeconds(180)));
        value.setLastSeenAt(Date.from(NOW.minusSeconds(120)));
        value.setValidUntil(Date.from(NOW.minusSeconds(60)));
        value.setRevokedAt(Date.from(NOW.minusSeconds(30)));
        value.setVersion(2L);
        return value;
    }
}
