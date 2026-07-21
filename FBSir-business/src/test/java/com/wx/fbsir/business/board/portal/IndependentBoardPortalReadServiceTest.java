package com.wx.fbsir.business.board.portal;

import com.wx.fbsir.business.board.oauth.BoardOAuthCrypto;
import com.wx.fbsir.business.board.oauth.BoardOAuthProfile;
import com.wx.fbsir.business.board.oauth.service.IndependentBoardOAuthClientRegistrationService;
import com.wx.fbsir.business.board.portal.dto.BoardPortalConnectorBindingView;
import com.wx.fbsir.business.board.portal.dto.BoardPortalOAuthClientView;
import com.wx.fbsir.business.board.portal.dto.BoardPortalOAuthFamilyView;
import com.wx.fbsir.business.board.portal.dto.BoardPortalReadEnvelope;
import com.wx.fbsir.business.board.portal.mapper.IndependentBoardPortalReadMapper;
import com.wx.fbsir.business.board.portal.persistence.BoardPortalConnectorBindingRow;
import com.wx.fbsir.business.board.portal.persistence.BoardPortalOAuthClientRow;
import com.wx.fbsir.business.board.portal.persistence.BoardPortalOAuthFamilyRow;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IndependentBoardPortalReadServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-21T09:00:00Z");
    private static final byte[] CURSOR_KEY =
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] REFERENCE_KEY =
            "fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.US_ASCII);

    private IndependentBoardPortalReadMapper mapper;
    private IndependentBoardPortalReadService service;

    @BeforeEach
    void setUp() {
        mapper = mock(IndependentBoardPortalReadMapper.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new IndependentBoardPortalReadService(
                mapper,
                new BoardPortalReadCursor(CURSOR_KEY, clock, Duration.ofMinutes(15)),
                new BoardPortalDigestRef(REFERENCE_KEY),
                clock);
    }

    @Test
    void clientPagingUsesTheLastReturnedRowAndCarriesTheOriginalHighWater() {
        List<BoardPortalOAuthClientRow> firstPage = new ArrayList<>();
        for (long id = 200; id >= 100; id--) {
            firstPage.add(client(id, "ACTIVE"));
        }
        when(mapper.selectOAuthClients("ACTIVE", Date.from(NOW), null, null, 101))
                .thenReturn(firstPage);

        BoardPortalReadEnvelope<BoardPortalOAuthClientView> first =
                service.listOAuthClients(7L, "ACTIVE", null);

        assertEquals(100, first.records().size());
        assertTrue(first.truncated());
        assertNotNull(first.nextCursor());
        assertEquals("client-200", first.records().get(0).clientRef());
        assertEquals("client-101", first.records().get(99).clientRef());
        assertEquals(BoardOAuthProfile.REQUIRED_SCOPES, first.records().get(0).scopes());
        assertNotEquals("sha256:" + "11".repeat(32),
                first.records().get(0).metadataDigestRef());

        reset(mapper);
        when(mapper.selectOAuthClients("ACTIVE", Date.from(NOW), 200L, 101L, 101))
                .thenReturn(List.of(client(99L, "ACTIVE")));
        BoardPortalReadEnvelope<BoardPortalOAuthClientView> second =
                service.listOAuthClients(7L, "ACTIVE", first.nextCursor());

        assertEquals(List.of("client-99"), second.records().stream()
                .map(BoardPortalOAuthClientView::clientRef).toList());
        assertFalse(second.truncated());
        assertNull(second.nextCursor());
    }

    @Test
    void invalidStatusOrCursorFailsBeforeAnyMapperRead() {
        assertThrows(BoardPortalBadRequestException.class,
                () -> service.listOAuthClients(7L, "active", null));
        assertThrows(BoardPortalBadRequestException.class,
                () -> service.listOAuthClients(7L, "ACTIVE", "not-a-valid-cursor"));

        verify(mapper, never()).selectOAuthClients(
                any(), any(), any(), any(), anyInt());
        verify(mapper, never()).selectOAuthFamilies(
                anyLong(), any(), any(), any(), any(), anyInt());
        verify(mapper, never()).selectConnectorBindings(
                anyLong(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void familyNaturalExpiryIsProjectedAtTheExactClockBoundary() {
        BoardPortalOAuthFamilyRow row = family(90L, "ACTIVE");
        row.setExpiresAt(Date.from(NOW));
        row.setEffectiveStatus("EXPIRED");
        row.setEffectiveTerminatedAt(Date.from(NOW));
        when(mapper.selectOAuthFamilies(11L, "EXPIRED", Date.from(NOW),
                null, null, 101)).thenReturn(List.of(row));

        BoardPortalReadEnvelope<BoardPortalOAuthFamilyView> page =
                service.listOAuthFamilies(7L, 11L, "EXPIRED", null);

        BoardPortalOAuthFamilyView view = page.records().get(0);
        assertEquals("EXPIRED", view.status());
        assertEquals(NOW, view.expiresAt());
        assertEquals(NOW, view.terminatedAt());
        assertEquals("企业 11", view.tenantLabel());
        assertEquals("成员 #21", view.memberLabel());
        assertEquals("用户 #31", view.userLabel());
    }

    @Test
    void crossTenantOrTemporallyInvalidRowsFailTheWholeFamilyPage() {
        BoardPortalOAuthFamilyRow crossTenant = family(90L, "ACTIVE");
        crossTenant.setTenantId(12L);
        when(mapper.selectOAuthFamilies(11L, null, Date.from(NOW), null, null, 101))
                .thenReturn(List.of(family(91L, "ACTIVE"), crossTenant));

        assertThrows(BoardPortalDataDriftException.class,
                () -> service.listOAuthFamilies(7L, 11L, null, null));

        reset(mapper);
        BoardPortalOAuthFamilyRow tooLong = family(92L, "ACTIVE");
        tooLong.setExpiresAt(Date.from(tooLong.getIssuedAt().toInstant()
                .plus(Duration.ofDays(30)).plusMillis(1)));
        when(mapper.selectOAuthFamilies(11L, "ACTIVE", Date.from(NOW),
                null, null, 101)).thenReturn(List.of(tooLong));
        assertThrows(BoardPortalDataDriftException.class,
                () -> service.listOAuthFamilies(7L, 11L, "ACTIVE", null));

        reset(mapper);
        BoardPortalOAuthFamilyRow missingStatus = family(93L, "ACTIVE");
        missingStatus.setEffectiveStatus(null);
        when(mapper.selectOAuthFamilies(11L, null, Date.from(NOW),
                null, null, 101)).thenReturn(List.of(missingStatus));
        assertThrows(BoardPortalDataDriftException.class,
                () -> service.listOAuthFamilies(7L, 11L, null, null));
    }

    @Test
    void bindingScopesAreCanonicalAndVipIsDerivedFromAllCurrentFlags() {
        BoardPortalConnectorBindingRow row = binding(80L, "ACTIVE");
        row.setScopes(List.of(
                "board.receipt.write",
                "identity.read",
                "board.meeting.reserve",
                "entitlement.read"));
        when(mapper.selectConnectorBindings(11L, "ACTIVE", Date.from(NOW),
                null, null, 101)).thenReturn(List.of(row));

        BoardPortalReadEnvelope<BoardPortalConnectorBindingView> page =
                service.listConnectorBindings(7L, 11L, "ACTIVE", null);

        BoardPortalConnectorBindingView view = page.records().get(0);
        assertEquals(BoardOAuthProfile.REQUIRED_SCOPES, view.scopes());
        assertTrue(view.entitlementActive());
        assertTrue(view.familyActive());
        assertTrue(view.vipEffective());
        assertTrue(view.subjectDigestRef().matches("sha256:[0-9a-f]{64}"));
        assertNotEquals("sha256:" + row.getPrincipalSubjectDigest(),
                view.subjectDigestRef());
    }

    @Test
    void terminalOrMalformedBindingsCanNeverBecomeVipEffective() {
        BoardPortalConnectorBindingRow terminal = binding(80L, "REVOKED");
        terminal.setRevokedAt(Date.from(NOW.minus(Duration.ofMinutes(30))));
        terminal.setFamilyActive(false);
        when(mapper.selectConnectorBindings(11L, "REVOKED", Date.from(NOW),
                null, null, 101)).thenReturn(List.of(terminal));

        BoardPortalConnectorBindingView view = service.listConnectorBindings(
                7L, 11L, "REVOKED", null).records().get(0);
        assertFalse(view.familyActive());
        assertFalse(view.vipEffective());

        reset(mapper);
        BoardPortalConnectorBindingRow malformed = binding(81L, "ACTIVE");
        malformed.setScopes(List.of(
                "identity.read", "identity.read",
                "board.meeting.reserve", "board.receipt.write"));
        when(mapper.selectConnectorBindings(11L, null, Date.from(NOW),
                null, null, 101)).thenReturn(List.of(malformed));
        assertThrows(BoardPortalDataDriftException.class,
                () -> service.listConnectorBindings(7L, 11L, null, null));
    }

    @Test
    void pseudonymousReferencesAreKeyedAndDomainSeparated() {
        BoardPortalDigestRef digestRef = new BoardPortalDigestRef(REFERENCE_KEY);
        byte[] digest = new byte[32];

        String metadata = digestRef.reference("client-metadata", "client-1", digest);
        String source = digestRef.reference("client-source", "client-1", digest);

        assertTrue(metadata.matches("sha256:[0-9a-f]{64}"));
        assertNotEquals(metadata, source);
        assertEquals(metadata,
                digestRef.reference("client-metadata", "client-1", digest));
        assertThrows(IllegalArgumentException.class,
                () -> digestRef.reference("client-metadata", "client-1", new byte[31]));
    }

    private static BoardPortalOAuthClientRow client(long id, String status) {
        BoardPortalOAuthClientRow row = new BoardPortalOAuthClientRow();
        Instant registeredAt = NOW.minus(Duration.ofDays(1));
        row.setRowId(id);
        row.setClientRef("client-" + id);
        row.setDisplayName(IndependentBoardOAuthClientRegistrationService.NEUTRAL_CLIENT_NAME);
        row.setIssuerUri(BoardOAuthProfile.ISSUER);
        row.setResourceUri(BoardOAuthProfile.RESOURCE);
        row.setProductCode("FBSIR_INDEPENDENT_BOARD");
        row.setSourceCode("WORKBUDDY");
        row.setConnectorCode("fbs-connector");
        row.setRedirectPort(49152);
        row.setRedirectUri("http://127.0.0.1:49152/oauth/callback");
        row.setTokenEndpointAuthMethod("none");
        row.setGrantTypesCanonical("authorization_code refresh_token");
        row.setResponseTypesCanonical("code");
        row.setScopeCanonical(BoardOAuthProfile.CANONICAL_SCOPE);
        row.setScopeDigest(BoardOAuthCrypto.sha256Ascii(BoardOAuthProfile.CANONICAL_SCOPE));
        row.setMetadataDigest(filledDigest(0x11));
        row.setRegistrationSourceDigest(filledDigest(0x22));
        row.setStoredStatus(status);
        row.setEffectiveStatus(status);
        row.setRegisteredAt(Date.from(registeredAt));
        row.setExpiresAt(Date.from(registeredAt.plus(Duration.ofDays(31))));
        row.setVersion(0L);
        return row;
    }

    private static BoardPortalOAuthFamilyRow family(long id, String status) {
        BoardPortalOAuthFamilyRow row = new BoardPortalOAuthFamilyRow();
        Instant issuedAt = NOW.minus(Duration.ofDays(1));
        Instant expiresAt = NOW.plus(Duration.ofDays(29));
        row.setRowId(id);
        row.setFamilyRef("family-" + id);
        row.setClientRef("client-family-1");
        row.setTenantId(11L);
        row.setTenantName("企业 11");
        row.setMemberId(21L);
        row.setUserId(31L);
        row.setProductCode("FBSIR_INDEPENDENT_BOARD");
        row.setSourceCode("WORKBUDDY");
        row.setConnectorCode("fbs-connector");
        row.setIssuerUri(BoardOAuthProfile.ISSUER);
        row.setResourceUri(BoardOAuthProfile.RESOURCE);
        row.setScopeCanonical(BoardOAuthProfile.CANONICAL_SCOPE);
        row.setScopeDigest(BoardOAuthCrypto.sha256Ascii(BoardOAuthProfile.CANONICAL_SCOPE));
        row.setConsentIntent("FIRST_CONNECT");
        row.setStoredStatus(status);
        row.setEffectiveStatus(status);
        row.setIssuedAt(Date.from(issuedAt));
        row.setExpiresAt(Date.from(expiresAt));
        row.setVersion(1L);
        row.setClientDisplayName(
                IndependentBoardOAuthClientRegistrationService.NEUTRAL_CLIENT_NAME);
        row.setClientStatus("ACTIVE");
        row.setClientExpiresAt(Date.from(NOW.plus(Duration.ofDays(30))));
        row.setClientIssuerUri(BoardOAuthProfile.ISSUER);
        row.setClientResourceUri(BoardOAuthProfile.RESOURCE);
        row.setClientProductCode("FBSIR_INDEPENDENT_BOARD");
        row.setClientSourceCode("WORKBUDDY");
        row.setClientConnectorCode("fbs-connector");
        row.setClientScopeCanonical(BoardOAuthProfile.CANONICAL_SCOPE);
        row.setClientScopeDigest(BoardOAuthCrypto.sha256Ascii(BoardOAuthProfile.CANONICAL_SCOPE));
        row.setClientRedirectUri("http://127.0.0.1:49152/oauth/callback");
        row.setClientTokenEndpointAuthMethod("none");
        row.setClientGrantTypesCanonical("authorization_code refresh_token");
        row.setClientResponseTypesCanonical("code");
        if ("PENDING_BINDING".equals(status)) {
            row.setCurrentRefreshGeneration(0L);
        } else {
            row.setBindingRef("binding-family-1");
            row.setBindingVersion(3L);
            row.setActivatedAt(Date.from(NOW.minus(Duration.ofHours(12))));
            row.setCurrentRefreshGeneration(2L);
            row.setCurrentBindingTenantId(11L);
            row.setCurrentBindingMemberId(21L);
            row.setCurrentBindingUserId(31L);
            row.setCurrentBindingClientRef("client-family-1");
            row.setCurrentBindingStatus("ACTIVE");
            row.setCurrentBindingVersion(3L);
            row.setCurrentBindingValidUntil(Date.from(NOW.plus(Duration.ofDays(1))));
        }
        return row;
    }

    private static BoardPortalConnectorBindingRow binding(long id, String status) {
        BoardPortalConnectorBindingRow row = new BoardPortalConnectorBindingRow();
        row.setRowId(id);
        row.setBindingRef("binding-" + id);
        row.setTenantId(11L);
        row.setTenantName("企业 11");
        row.setMemberId(21L);
        row.setUserId(31L);
        row.setProductCode("FBSIR_INDEPENDENT_BOARD");
        row.setSourceCode("WORKBUDDY");
        row.setConnectorCode("fbs-connector");
        row.setIssuerUri(BoardOAuthProfile.ISSUER);
        row.setResourceUri(BoardOAuthProfile.RESOURCE);
        row.setClientRef("client-binding-1");
        row.setPrincipalSubjectDigest("ab".repeat(32));
        row.setStatus(status);
        row.setVerificationMethod("MCP_INITIALIZE");
        row.setVerifiedAt(Date.from(NOW.minus(Duration.ofHours(2))));
        row.setLastSeenAt(Date.from(NOW.minus(Duration.ofHours(1))));
        row.setValidUntil(Date.from(NOW.plus(Duration.ofDays(1))));
        row.setVersion(3L);
        row.setEntitlementActive(true);
        row.setFamilyActive(true);
        row.setScopes(BoardOAuthProfile.REQUIRED_SCOPES);
        return row;
    }

    private static byte[] filledDigest(int value) {
        byte[] digest = new byte[32];
        java.util.Arrays.fill(digest, (byte) value);
        return digest;
    }
}
