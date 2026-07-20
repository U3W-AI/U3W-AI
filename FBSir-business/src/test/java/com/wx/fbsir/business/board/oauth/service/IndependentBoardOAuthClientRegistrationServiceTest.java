package com.wx.fbsir.business.board.oauth.service;

import com.wx.fbsir.business.board.oauth.BoardOAuthCrypto;
import com.wx.fbsir.business.board.oauth.BoardOAuthProfile;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthClient;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthReceipt;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthClientRegistrationRequest;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthClientRegistrationResponse;
import com.wx.fbsir.business.board.oauth.mapper.IndependentBoardOAuthMapper;
import com.wx.fbsir.common.exception.ServiceException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IndependentBoardOAuthClientRegistrationServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-21T01:02:03Z");
    private static final byte[] METADATA = ("{\"client_name\":\"untrusted\","
            + "\"logo_uri\":\"http://127.0.0.1/internal\","
            + "\"unknown\":true}").getBytes(StandardCharsets.UTF_8);
    private static final byte[] REGISTRATION_SOURCE =
            "203.0.113.10".getBytes(StandardCharsets.US_ASCII);

    private IndependentBoardOAuthMapper mapper;
    private SecureRandom secureRandom;
    private IndependentBoardOAuthClientRegistrationService service;

    @BeforeEach
    void setUp() {
        mapper = mock(IndependentBoardOAuthMapper.class);
        secureRandom = mock(SecureRandom.class);
        doAnswer(invocation -> {
            byte[] target = invocation.getArgument(0);
            for (int index = 0; index < target.length; index++) {
                target[index] = (byte) index;
            }
            return null;
        }).when(secureRandom).nextBytes(any(byte[].class));
        service = new IndependentBoardOAuthClientRegistrationService(
                mapper, Clock.fixed(NOW, ZoneOffset.UTC), secureRandom);
    }

    @Test
    void exactProfilePersistsOnlyDigestsAndReturnsNeutralRegistration() {
        when(mapper.insertClient(any())).thenReturn(1);
        when(mapper.insertReceipt(any())).thenReturn(1);

        BoardOAuthClientRegistrationResponse response = service.register(validRequest());

        ArgumentCaptor<BoardOAuthClient> captor = ArgumentCaptor.forClass(BoardOAuthClient.class);
        verify(mapper, times(1)).insertClient(captor.capture());
        BoardOAuthClient client = captor.getValue();
        byte[] deterministicBytes = new byte[BoardOAuthCrypto.SECRET_BYTES];
        for (int index = 0; index < deterministicBytes.length; index++) {
            deterministicBytes[index] = (byte) index;
        }
        String expectedClientId = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(deterministicBytes);

        assertEquals(expectedClientId, client.getClientId());
        assertEquals(43, client.getClientId().length());
        assertTrue(client.getClientId().matches("[A-Za-z0-9_-]{43}"));
        assertEquals(IndependentBoardOAuthClientRegistrationService.NEUTRAL_CLIENT_NAME,
                client.getClientName());
        assertEquals(BoardOAuthProfile.ISSUER, client.getIssuerUri());
        assertEquals(BoardOAuthProfile.RESOURCE, client.getResourceUri());
        assertEquals(IndependentBoardOAuthClientRegistrationService.PRODUCT_CODE,
                client.getProductCode());
        assertEquals(IndependentBoardOAuthClientRegistrationService.SOURCE_CODE,
                client.getSourceCode());
        assertEquals(IndependentBoardOAuthClientRegistrationService.CONNECTOR_CODE,
                client.getConnectorCode());
        assertEquals(54321, client.getRedirectPort());
        assertEquals("http://127.0.0.1:54321/oauth/callback", client.getRedirectUri());
        assertEquals("none", client.getTokenEndpointAuthMethod());
        assertEquals("authorization_code refresh_token", client.getGrantTypesCanonical());
        assertEquals("code", client.getResponseTypesCanonical());
        assertEquals(BoardOAuthProfile.CANONICAL_SCOPE, client.getScopeCanonical());
        assertArrayEquals(BoardOAuthCrypto.sha256Ascii(BoardOAuthProfile.CANONICAL_SCOPE),
                client.getScopeDigest());
        assertArrayEquals(BoardOAuthCrypto.sha256(METADATA), client.getMetadataDigest());
        assertArrayEquals(BoardOAuthCrypto.sha256(REGISTRATION_SOURCE),
                client.getRegistrationSourceDigest());
        assertEquals("ACTIVE", client.getStatus());
        assertEquals(Date.from(NOW), client.getRegisteredAt());
        assertEquals(Date.from(NOW.plusSeconds(31L * 24 * 60 * 60)), client.getExpiresAt());
        assertNull(client.getTerminatedAt());
        assertEquals(0L, client.getVersion());

        ArgumentCaptor<BoardOAuthReceipt> receiptCaptor =
                ArgumentCaptor.forClass(BoardOAuthReceipt.class);
        verify(mapper, times(1)).insertReceipt(receiptCaptor.capture());
        BoardOAuthReceipt receipt = receiptCaptor.getValue();
        assertEquals("oauth_" + expectedClientId, receipt.getReceiptId());
        assertEquals("OAUTH_CLIENT_REGISTERED", receipt.getAction());
        assertEquals(expectedClientId, receipt.getClientId());
        assertNull(receipt.getAuthorizationRequestId());
        assertNull(receipt.getAuthorizationCodeId());
        assertNull(receipt.getFamilyId());
        assertNull(receipt.getTokenId());
        assertNull(receipt.getBindingId());
        assertNull(receipt.getTenantId());
        assertNull(receipt.getMemberId());
        assertNull(receipt.getUserId());
        assertNull(receipt.getPrincipalSubjectDigest());
        assertEquals("CLIENT", receipt.getActorType());
        assertNull(receipt.getActorUserId());
        assertArrayEquals(BoardOAuthCrypto.sha256(REGISTRATION_SOURCE),
                receipt.getActorSubjectDigest());
        assertEquals(receipt.getReceiptId(), receipt.getCorrelationId());
        String canonicalPayload = String.join("|",
                "OAUTH_CLIENT_REGISTERED",
                expectedClientId,
                BoardOAuthProfile.ISSUER,
                BoardOAuthProfile.RESOURCE,
                "http://127.0.0.1:54321/oauth/callback",
                HexFormat.of().formatHex(BoardOAuthCrypto.sha256Ascii(
                        BoardOAuthProfile.CANONICAL_SCOPE)),
                HexFormat.of().formatHex(BoardOAuthCrypto.sha256(METADATA)),
                HexFormat.of().formatHex(BoardOAuthCrypto.sha256(REGISTRATION_SOURCE)),
                Long.toString(NOW.toEpochMilli()),
                Long.toString(NOW.plusSeconds(31L * 24 * 60 * 60).toEpochMilli()));
        assertArrayEquals(BoardOAuthCrypto.sha256Ascii(canonicalPayload),
                receipt.getPayloadDigest());
        assertEquals("ACTION_COMPLETED", receipt.getEvidenceLevel());
        assertEquals(Date.from(NOW), receipt.getCreatedAt());

        assertEquals(expectedClientId, response.clientId());
        assertEquals(NOW.getEpochSecond(), response.clientIdIssuedAt());
        assertEquals(NOW.plusSeconds(31L * 24 * 60 * 60).getEpochSecond(),
                response.clientIdExpiresAt());
        assertEquals(List.of("http://127.0.0.1:54321/oauth/callback"),
                response.redirectUris());
        assertEquals(List.of("authorization_code", "refresh_token"), response.grantTypes());
        assertEquals(List.of("code"), response.responseTypes());
        assertEquals("none", response.tokenEndpointAuthMethod());
        assertEquals(IndependentBoardOAuthClientRegistrationService.NEUTRAL_CLIENT_NAME,
                response.clientName());
        assertEquals(BoardOAuthProfile.CANONICAL_SCOPE, response.scope());
        verify(secureRandom, times(2)).nextBytes(any(byte[].class));
    }

    @Test
    void rejectsEveryKnownMetadataProfileDriftBeforeRandomOrDatabaseUse() {
        List<BoardOAuthClientRegistrationRequest> invalid = List.of(
                request("client_secret_basic",
                        List.of("authorization_code", "refresh_token"),
                        List.of("code"), BoardOAuthProfile.REQUIRED_SCOPES),
                request("none", List.of("authorization_code"),
                        List.of("code"), BoardOAuthProfile.REQUIRED_SCOPES),
                request("none",
                        List.of("authorization_code", "authorization_code"),
                        List.of("code"), BoardOAuthProfile.REQUIRED_SCOPES),
                request("none",
                        List.of("authorization_code", "refresh_token", "client_credentials"),
                        List.of("code"), BoardOAuthProfile.REQUIRED_SCOPES),
                request("none", List.of("authorization_code", "refresh_token"),
                        List.of("token"), BoardOAuthProfile.REQUIRED_SCOPES),
                request("none", List.of("authorization_code", "refresh_token"),
                        List.of("code", "code"), BoardOAuthProfile.REQUIRED_SCOPES),
                request("none", List.of("authorization_code", "refresh_token"),
                        List.of("code"), BoardOAuthProfile.REQUIRED_SCOPES.subList(0, 3)),
                request("none", List.of("authorization_code", "refresh_token"),
                        List.of("code"), List.of(
                                "identity.read", "entitlement.read",
                                "board.meeting.reserve", "board.meeting.reserve")));

        for (BoardOAuthClientRegistrationRequest candidate : invalid) {
            assertFailure(candidate, 400,
                    IndependentBoardOAuthClientRegistrationService.INVALID_CLIENT_METADATA);
        }
        assertFailure(null, 400,
                IndependentBoardOAuthClientRegistrationService.INVALID_CLIENT_METADATA);
        verify(mapper, never()).insertClient(any());
        verify(mapper, never()).insertReceipt(any());
        verify(secureRandom, never()).nextBytes(any(byte[].class));
    }

    @Test
    void acceptsOnlyOneExactLiteralIpv4LoopbackRedirect() {
        List<List<String>> invalidRedirects = List.of(
                List.of(),
                List.of("http://localhost:54321/oauth/callback"),
                List.of("http://[::1]:54321/oauth/callback"),
                List.of("http://127.0.0.1:1023/oauth/callback"),
                List.of("http://127.0.0.1:65536/oauth/callback"),
                List.of("http://127.0.0.1:54321/oauth/callback?next=/"),
                List.of("http://127.0.0.1:54321/oauth/callback/"),
                List.of("http://127.0.0.1:54321/oauth/callback",
                        "http://127.0.0.1:54322/oauth/callback"));

        for (List<String> redirectUris : invalidRedirects) {
            BoardOAuthClientRegistrationRequest candidate = new BoardOAuthClientRegistrationRequest(
                    redirectUris,
                    "none",
                    List.of("authorization_code", "refresh_token"),
                    List.of("code"),
                    BoardOAuthProfile.REQUIRED_SCOPES,
                    METADATA,
                    REGISTRATION_SOURCE);
            assertFailure(candidate, 400,
                    IndependentBoardOAuthClientRegistrationService.INVALID_REDIRECT_URI);
        }
        BoardOAuthClientRegistrationRequest nullRedirect = new BoardOAuthClientRegistrationRequest(
                null,
                "none",
                List.of("authorization_code", "refresh_token"),
                List.of("code"),
                BoardOAuthProfile.REQUIRED_SCOPES,
                METADATA,
                REGISTRATION_SOURCE);
        assertFailure(nullRedirect, 400,
                IndependentBoardOAuthClientRegistrationService.INVALID_REDIRECT_URI);
        verify(mapper, never()).insertClient(any());
        verify(mapper, never()).insertReceipt(any());
        verify(secureRandom, never()).nextBytes(any(byte[].class));
    }

    @Test
    void metadataAndRegistrationSourceEvidenceFailClosedBeforePersistence() {
        BoardOAuthClientRegistrationRequest missingMetadata = new BoardOAuthClientRegistrationRequest(
                List.of("http://127.0.0.1:54321/oauth/callback"),
                "none",
                List.of("authorization_code", "refresh_token"),
                List.of("code"),
                BoardOAuthProfile.REQUIRED_SCOPES,
                null,
                REGISTRATION_SOURCE);
        assertFailure(missingMetadata, 400,
                IndependentBoardOAuthClientRegistrationService.INVALID_CLIENT_METADATA);

        BoardOAuthClientRegistrationRequest missingSource = new BoardOAuthClientRegistrationRequest(
                List.of("http://127.0.0.1:54321/oauth/callback"),
                "none",
                List.of("authorization_code", "refresh_token"),
                List.of("code"),
                BoardOAuthProfile.REQUIRED_SCOPES,
                METADATA,
                null);
        assertFailure(missingSource, 500,
                IndependentBoardOAuthClientRegistrationService.REGISTRATION_CONTEXT_INVALID);
        verify(mapper, never()).insertClient(any());
        verify(mapper, never()).insertReceipt(any());
        verify(secureRandom, never()).nextBytes(any(byte[].class));
    }

    @Test
    void unexpectedInsertCountAndDuplicateAreStableServiceFailures() {
        when(mapper.insertClient(any())).thenReturn(0);
        assertFailure(validRequest(), 500,
                IndependentBoardOAuthClientRegistrationService.CLIENT_WRITE_FAILED);
        verify(mapper, times(1)).insertClient(any());

        mapper = mock(IndependentBoardOAuthMapper.class);
        service = new IndependentBoardOAuthClientRegistrationService(
                mapper, Clock.fixed(NOW, ZoneOffset.UTC), secureRandom);
        doThrow(new DuplicateKeyException("collision"))
                .when(mapper).insertClient(any());
        assertFailure(validRequest(), 409,
                IndependentBoardOAuthClientRegistrationService.CLIENT_CONFLICT);
        verify(mapper, times(1)).insertClient(any());
    }

    @Test
    void randomFailureIsStableAndCannotReachTheMapper() {
        doThrow(new IllegalStateException("rng unavailable"))
                .when(secureRandom).nextBytes(any(byte[].class));

        assertFailure(validRequest(), 500,
                IndependentBoardOAuthClientRegistrationService.RANDOM_GENERATION_FAILED);

        verify(mapper, never()).insertClient(any());
        verify(mapper, never()).insertReceipt(any());
    }

    @Test
    void receiptFailureIsStableAndCannotReportRegistrationSuccess() {
        when(mapper.insertClient(any())).thenReturn(1);
        when(mapper.insertReceipt(any())).thenReturn(0);

        assertFailure(validRequest(), 500,
                IndependentBoardOAuthClientRegistrationService.RECEIPT_WRITE_FAILED);

        verify(mapper, times(1)).insertClient(any());
        verify(mapper, times(1)).insertReceipt(any());
    }

    private BoardOAuthClientRegistrationRequest validRequest() {
        return request(
                "none",
                List.of("refresh_token", "authorization_code"),
                List.of("code"),
                List.of(
                        "board.receipt.write",
                        "identity.read",
                        "board.meeting.reserve",
                        "entitlement.read"));
    }

    private BoardOAuthClientRegistrationRequest request(
            String tokenEndpointAuthMethod,
            List<String> grantTypes,
            List<String> responseTypes,
            List<String> scopes) {
        return new BoardOAuthClientRegistrationRequest(
                List.of("http://127.0.0.1:54321/oauth/callback"),
                tokenEndpointAuthMethod,
                grantTypes,
                responseTypes,
                scopes,
                METADATA,
                REGISTRATION_SOURCE);
    }

    private void assertFailure(
            BoardOAuthClientRegistrationRequest request,
            int expectedStatus,
            String expectedCode) {
        ServiceException error = assertThrows(ServiceException.class, () -> service.register(request));
        assertEquals(expectedStatus, error.getCode());
        assertEquals(expectedCode, error.getMessage());
    }
}
