package com.wx.fbsir.business.board.oauth.service;

import com.wx.fbsir.business.board.domain.BoardConnectorBinding;
import com.wx.fbsir.business.board.domain.BoardEnterpriseAuthority;
import com.wx.fbsir.business.board.domain.BoardEnterpriseMemberScope;
import com.wx.fbsir.business.board.domain.BoardProductEntitlement;
import com.wx.fbsir.business.board.domain.BoardProductPlan;
import com.wx.fbsir.business.board.mapper.IndependentBoardMapper;
import com.wx.fbsir.business.board.oauth.BoardOAuthAuthorizationCodeGenerator;
import com.wx.fbsir.business.board.oauth.BoardOAuthAuthorizationStateAad;
import com.wx.fbsir.business.board.oauth.BoardOAuthConsentIntent;
import com.wx.fbsir.business.board.oauth.BoardOAuthCrypto;
import com.wx.fbsir.business.board.oauth.BoardOAuthPrincipalSubject;
import com.wx.fbsir.business.board.oauth.BoardOAuthProfile;
import com.wx.fbsir.business.board.oauth.BoardOAuthProtocolException;
import com.wx.fbsir.business.board.oauth.BoardOAuthReceiptFactory;
import com.wx.fbsir.business.board.oauth.BoardOAuthRequestHandleGenerator;
import com.wx.fbsir.business.board.oauth.BoardOAuthStateCipher;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthAuthorizationCode;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthAuthorizationRequest;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthClient;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthReceipt;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthAuthorizationApprovalCommand;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthAuthorizationApprovedResult;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthAuthorizationDenialCommand;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthAuthorizationDeniedResult;
import com.wx.fbsir.business.board.oauth.mapper.IndependentBoardOAuthMapper;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IndependentBoardOAuthAuthorizationDecisionServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-21T03:04:05.678Z");
    private static final Long TENANT_ID = 11L;
    private static final Long MEMBER_ID = 22L;
    private static final Long USER_ID = 33L;
    private static final Long REQUEST_ID = 700L;
    private static final String REQUEST_HANDLE = "h".repeat(43);
    private static final String CLIENT_ID = "c".repeat(43);
    private static final String REDIRECT = "http://127.0.0.1:54321/oauth/callback";
    private static final String RAW_STATE = "state-0123456789-abcd";

    private IndependentBoardMapper boardMapper;
    private IndependentBoardOAuthMapper mapper;
    private BoardOAuthStateCipher stateCipher;
    private IndependentBoardOAuthAuthorizationService service;
    private BoardEnterpriseMemberScope member;
    private BoardProductEntitlement entitlement;
    private BoardProductPlan plan;
    private BoardOAuthClient client;
    private BoardOAuthAuthorizationRequest request;

    @BeforeEach
    void setUp() {
        boardMapper = mock(IndependentBoardMapper.class);
        mapper = mock(IndependentBoardOAuthMapper.class);
        stateCipher = mock(BoardOAuthStateCipher.class);
        service = new IndependentBoardOAuthAuthorizationService(
                boardMapper,
                mapper,
                stateCipher,
                new BoardOAuthRequestHandleGenerator(),
                new BoardOAuthAuthorizationCodeGenerator(),
                new BoardOAuthReceiptFactory(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        BoardOAuthAuthorizationRequest locator = new BoardOAuthAuthorizationRequest();
        locator.setId(REQUEST_ID);
        locator.setClientId(CLIENT_ID);
        member = activeMember();
        entitlement = currentVipEntitlement();
        plan = exactVipPlan();
        client = activeClient();
        request = pendingRequest();

        when(mapper.selectAuthorizationRequestLocatorByHandle(any(byte[].class)))
                .thenReturn(locator);
        when(boardMapper.selectEnterpriseSlotForUpdate(TENANT_ID))
                .thenReturn(activeEnterprise());
        when(boardMapper.selectActiveContextForUpdate(TENANT_ID, USER_ID))
                .thenReturn(member);
        when(boardMapper.selectEntitlementForUpdate(
                TENANT_ID, MEMBER_ID,
                IndependentBoardOAuthAuthorizationService.PRODUCT_CODE))
                .thenReturn(entitlement);
        when(boardMapper.selectActivePlanForUpdate(
                IndependentBoardOAuthAuthorizationService.PRODUCT_CODE,
                "BOARD_VIP"))
                .thenReturn(plan);
        when(boardMapper.selectConnectorBindingSlotForUpdate(
                TENANT_ID,
                MEMBER_ID,
                IndependentBoardOAuthAuthorizationService.PRODUCT_CODE,
                IndependentBoardOAuthAuthorizationService.SOURCE_CODE,
                IndependentBoardOAuthAuthorizationService.CONNECTOR_CODE))
                .thenReturn(null);
        when(mapper.selectClientForUpdate(CLIENT_ID)).thenReturn(client);
        when(mapper.selectAuthorizationRequestForUpdate(
                eq(REQUEST_ID), eq(CLIENT_ID), any(byte[].class)))
                .thenReturn(request);
        when(stateCipher.decrypt(
                eq("state-key-v1"),
                any(byte[].class),
                any(byte[].class),
                any(byte[].class)))
                .thenReturn(RAW_STATE);
        when(mapper.approveAuthorizationRequestIfVersion(any(), eq(0L))).thenReturn(1);
        when(mapper.denyAuthorizationRequestIfVersion(any(), eq(0L))).thenReturn(1);
        when(mapper.insertAuthorizationCode(any())).thenAnswer(invocation -> {
            BoardOAuthAuthorizationCode code = invocation.getArgument(0);
            code.setId(900L);
            return 1;
        });
        when(mapper.insertReceipt(any())).thenReturn(1);
    }

    @Test
    void firstConnectApprovalUsesCanonicalLockOrderAndPersistsOnlyCodeDigest() {
        BoardOAuthAuthorizationApprovedResult result = service.approve(firstApproval());

        InOrder order = inOrder(mapper, boardMapper, stateCipher);
        order.verify(mapper).selectAuthorizationRequestLocatorByHandle(any(byte[].class));
        order.verify(boardMapper).selectEnterpriseSlotForUpdate(TENANT_ID);
        order.verify(boardMapper).selectActiveContextForUpdate(TENANT_ID, USER_ID);
        order.verify(boardMapper).selectEntitlementForUpdate(
                TENANT_ID, MEMBER_ID,
                IndependentBoardOAuthAuthorizationService.PRODUCT_CODE);
        order.verify(boardMapper).selectActivePlanForUpdate(
                IndependentBoardOAuthAuthorizationService.PRODUCT_CODE, "BOARD_VIP");
        order.verify(boardMapper).selectConnectorBindingSlotForUpdate(
                TENANT_ID,
                MEMBER_ID,
                IndependentBoardOAuthAuthorizationService.PRODUCT_CODE,
                IndependentBoardOAuthAuthorizationService.SOURCE_CODE,
                IndependentBoardOAuthAuthorizationService.CONNECTOR_CODE);
        order.verify(mapper).selectClientForUpdate(CLIENT_ID);
        order.verify(mapper).selectAuthorizationRequestForUpdate(
                eq(REQUEST_ID), eq(CLIENT_ID), any(byte[].class));
        ArgumentCaptor<byte[]> aad = ArgumentCaptor.forClass(byte[].class);
        order.verify(stateCipher).decrypt(
                eq("state-key-v1"),
                any(byte[].class),
                any(byte[].class),
                aad.capture());
        order.verify(mapper).approveAuthorizationRequestIfVersion(any(), eq(0L));
        ArgumentCaptor<BoardOAuthAuthorizationCode> codeCaptor =
                ArgumentCaptor.forClass(BoardOAuthAuthorizationCode.class);
        order.verify(mapper).insertAuthorizationCode(codeCaptor.capture());
        order.verify(mapper, times(2)).insertReceipt(any());

        assertArrayEquals(BoardOAuthAuthorizationStateAad.digest(request), aad.getValue());
        assertEquals(REDIRECT, result.redirectUri());
        assertEquals(RAW_STATE, result.rawState());
        assertEquals(BoardOAuthProfile.ISSUER, result.issuerUri());
        assertEquals(NOW.plusSeconds(60), result.codeExpiresAt());
        assertEquals(43, result.rawAuthorizationCode().length());

        BoardOAuthAuthorizationCode code = codeCaptor.getValue();
        assertArrayEquals(BoardOAuthCrypto.sha256Ascii(result.rawAuthorizationCode()),
                code.getCodeDigest());
        assertEquals(REQUEST_ID, code.getAuthorizationRequestId());
        assertEquals(CLIENT_ID, code.getClientId());
        assertEquals(REDIRECT, code.getRedirectUri());
        assertEquals(TENANT_ID, code.getTenantId());
        assertEquals(MEMBER_ID, code.getMemberId());
        assertEquals(USER_ID, code.getUserId());
        assertEquals(BoardOAuthConsentIntent.FIRST_CONNECT, request.getConsentIntent());
        assertEquals(request.getConsentIntent(), code.getConsentIntent());
        assertArrayEquals(BoardOAuthPrincipalSubject.digest(TENANT_ID, MEMBER_ID, USER_ID),
                code.getPrincipalSubjectDigest());
        assertEquals("ACTIVE", code.getStatus());
        assertEquals(Date.from(NOW), code.getIssuedAt());
        assertEquals(Date.from(NOW.plusSeconds(60)), code.getExpiresAt());
        assertEquals(0L, code.getVersion());
        assertFalse(Arrays.deepToString(new Object[] {code}).contains(
                result.rawAuthorizationCode()));

        ArgumentCaptor<BoardOAuthReceipt> receipts =
                ArgumentCaptor.forClass(BoardOAuthReceipt.class);
        verify(mapper, times(2)).insertReceipt(receipts.capture());
        assertEquals(List.of("AUTHORIZATION_APPROVED", "AUTHORIZATION_CODE_ISSUED"),
                receipts.getAllValues().stream().map(BoardOAuthReceipt::getAction).toList());
        assertEquals(receipts.getAllValues().get(0).getCorrelationId(),
                receipts.getAllValues().get(1).getCorrelationId());
    }

    @Test
    void explicitReauthorizationRequiresAndLocksNamedExistingBinding() {
        BoardConnectorBinding binding = terminalBinding();
        when(boardMapper.selectConnectorBindingSlotForUpdate(
                TENANT_ID,
                MEMBER_ID,
                IndependentBoardOAuthAuthorizationService.PRODUCT_CODE,
                IndependentBoardOAuthAuthorizationService.SOURCE_CODE,
                IndependentBoardOAuthAuthorizationService.CONNECTOR_CODE))
                .thenReturn(binding);
        when(boardMapper.selectConnectorBindingScopesForUpdate(binding.getBindingId()))
                .thenReturn(BoardOAuthProfile.REQUIRED_SCOPES);

        BoardOAuthAuthorizationApprovedResult result = service.approve(
                new BoardOAuthAuthorizationApprovalCommand(
                        REQUEST_HANDLE,
                        USER_ID,
                        TENANT_ID,
                        BoardOAuthConsentIntent.EXPLICIT_REAUTHORIZATION));

        assertEquals(RAW_STATE, result.rawState());
        assertEquals(
                BoardOAuthConsentIntent.EXPLICIT_REAUTHORIZATION,
                request.getConsentIntent());
        InOrder order = inOrder(boardMapper, mapper);
        order.verify(mapper).selectAuthorizationRequestLocatorByHandle(any(byte[].class));
        order.verify(boardMapper).selectEnterpriseSlotForUpdate(TENANT_ID);
        order.verify(boardMapper).selectActiveContextForUpdate(TENANT_ID, USER_ID);
        order.verify(boardMapper).selectEntitlementForUpdate(
                TENANT_ID, MEMBER_ID,
                IndependentBoardOAuthAuthorizationService.PRODUCT_CODE);
        order.verify(boardMapper).selectActivePlanForUpdate(
                IndependentBoardOAuthAuthorizationService.PRODUCT_CODE, "BOARD_VIP");
        order.verify(boardMapper).selectConnectorBindingSlotForUpdate(
                TENANT_ID,
                MEMBER_ID,
                IndependentBoardOAuthAuthorizationService.PRODUCT_CODE,
                IndependentBoardOAuthAuthorizationService.SOURCE_CODE,
                IndependentBoardOAuthAuthorizationService.CONNECTOR_CODE);
        order.verify(boardMapper).selectConnectorBindingScopesForUpdate(binding.getBindingId());
        order.verify(mapper).selectClientForUpdate(CLIENT_ID);
    }

    @Test
    void consentIntentsNeverFallBackBasedOnDiscoveredBindingState() {
        BoardConnectorBinding binding = terminalBinding();
        when(boardMapper.selectConnectorBindingSlotForUpdate(
                TENANT_ID,
                MEMBER_ID,
                IndependentBoardOAuthAuthorizationService.PRODUCT_CODE,
                IndependentBoardOAuthAuthorizationService.SOURCE_CODE,
                IndependentBoardOAuthAuthorizationService.CONNECTOR_CODE))
                .thenReturn(binding);
        when(boardMapper.selectConnectorBindingScopesForUpdate(binding.getBindingId()))
                .thenReturn(BoardOAuthProfile.REQUIRED_SCOPES);

        assertProtocol(
                () -> service.approve(firstApproval()),
                "invalid_request",
                409,
                IndependentBoardOAuthAuthorizationService.CONSENT_INTENT_CONFLICT);
        verify(boardMapper).selectConnectorBindingScopesForUpdate(binding.getBindingId());
        verify(mapper, never()).selectClientForUpdate(any());

        setUp();
        assertProtocol(
                () -> service.approve(new BoardOAuthAuthorizationApprovalCommand(
                        REQUEST_HANDLE,
                        USER_ID,
                        TENANT_ID,
                        BoardOAuthConsentIntent.EXPLICIT_REAUTHORIZATION)),
                "invalid_request",
                409,
                IndependentBoardOAuthAuthorizationService.CONSENT_INTENT_CONFLICT);
        verify(mapper, never()).selectClientForUpdate(any());
    }

    @Test
    void identityAndEntitlementDriftFailBeforeClientOrRequestLocks() {
        entitlement.setUserId(999L);

        assertProtocol(
                () -> service.approve(firstApproval()),
                "access_denied",
                403,
                IndependentBoardOAuthAuthorizationService.CONSENT_VIP_NOT_CURRENT);
        verify(boardMapper, never()).selectActivePlanForUpdate(any(), any());
        verify(mapper, never()).selectClientForUpdate(any());
        verify(mapper, never()).approveAuthorizationRequestIfVersion(any(), any());
    }

    @Test
    void inactiveEnterpriseFailsBeforeEntitlementClientOrAuthorizationWrites() {
        BoardEnterpriseAuthority inactive = activeEnterprise();
        inactive.setStatus(0);
        when(boardMapper.selectEnterpriseSlotForUpdate(TENANT_ID))
                .thenReturn(inactive);

        assertProtocol(
                () -> service.approve(firstApproval()),
                "access_denied",
                403,
                IndependentBoardOAuthAuthorizationService
                        .CONSENT_ENTERPRISE_NOT_ACTIVE);
        verify(boardMapper, never()).selectEntitlementForUpdate(any(), any(), any());
        verify(mapper, never()).selectClientForUpdate(any());
        verify(mapper, never()).insertAuthorizationCode(any());
    }

    @Test
    void expiredAndIdentityBearingPendingRequestsCannotBeApproved() {
        request.setRequestedAt(Date.from(NOW.minusSeconds(300)));
        request.setExpiresAt(Date.from(NOW));
        assertProtocol(
                () -> service.approve(firstApproval()),
                "invalid_request",
                400,
                IndependentBoardOAuthAuthorizationService.CONSENT_REQUEST_EXPIRED);
        verify(stateCipher, never()).decrypt(any(), any(), any(), any());
        verify(mapper, never()).approveAuthorizationRequestIfVersion(any(), any());

        setUp();
        request.setTenantId(TENANT_ID);
        assertProtocol(
                () -> service.approve(firstApproval()),
                "server_error",
                500,
                IndependentBoardOAuthAuthorizationService.CONSENT_REQUEST_DRIFT);
        verify(stateCipher, never()).decrypt(any(), any(), any(), any());
        verify(mapper, never()).approveAuthorizationRequestIfVersion(any(), any());
    }

    @Test
    void approvalRequiresTheLockedRequestAndClientToOutliveTheFullCodeWindow() {
        request.setRequestedAt(Date.from(NOW.minusSeconds(240)));
        request.setExpiresAt(Date.from(NOW.plusSeconds(60)));
        assertProtocol(
                () -> service.approve(firstApproval()),
                "invalid_request",
                400,
                IndependentBoardOAuthAuthorizationService.CONSENT_REQUEST_EXPIRED);
        verify(mapper, never()).approveAuthorizationRequestIfVersion(any(), any());

        setUp();
        Instant clientExpiry = NOW.plusSeconds(60);
        client.setExpiresAt(Date.from(clientExpiry));
        client.setRegisteredAt(Date.from(clientExpiry.minusSeconds(31L * 24 * 60 * 60)));
        assertProtocol(
                () -> service.approve(firstApproval()),
                "invalid_request",
                400,
                IndependentBoardOAuthAuthorizationService.CLIENT_INVALID);
        verify(mapper, never()).selectAuthorizationRequestForUpdate(any(), any(), any());
        verify(mapper, never()).approveAuthorizationRequestIfVersion(any(), any());
    }

    @Test
    void finalDecisionTimeRejectsLockWaitsThatCrossAuthorityExpiry() {
        Clock decisionClock = mock(Clock.class);
        when(decisionClock.instant()).thenReturn(NOW, NOW.plusSeconds(2));
        replaceServiceClock(decisionClock);
        entitlement.setValidUntil(Date.from(NOW.plusSeconds(1)));

        assertProtocol(
                () -> service.approve(firstApproval()),
                "access_denied",
                403,
                IndependentBoardOAuthAuthorizationService.CONSENT_VIP_NOT_CURRENT);
        verify(mapper, never()).approveAuthorizationRequestIfVersion(any(), any());
        InOrder entitlementOrder = inOrder(decisionClock, boardMapper, mapper);
        entitlementOrder.verify(decisionClock).instant();
        entitlementOrder.verify(mapper).selectAuthorizationRequestForUpdate(
                eq(REQUEST_ID), eq(CLIENT_ID), any(byte[].class));
        entitlementOrder.verify(decisionClock).instant();

        setUp();
        decisionClock = mock(Clock.class);
        when(decisionClock.instant()).thenReturn(NOW, NOW.plusSeconds(2));
        replaceServiceClock(decisionClock);
        Instant clientExpiry = NOW.plusSeconds(61);
        client.setExpiresAt(Date.from(clientExpiry));
        client.setRegisteredAt(Date.from(
                clientExpiry.minusSeconds(31L * 24 * 60 * 60)));

        assertProtocol(
                () -> service.approve(firstApproval()),
                "invalid_request",
                400,
                IndependentBoardOAuthAuthorizationService.CLIENT_INVALID);
        verify(mapper, never()).approveAuthorizationRequestIfVersion(any(), any());

        setUp();
        decisionClock = mock(Clock.class);
        when(decisionClock.instant()).thenReturn(NOW, NOW.plusSeconds(2));
        replaceServiceClock(decisionClock);
        request.setRequestedAt(Date.from(NOW.minusSeconds(239)));
        request.setExpiresAt(Date.from(NOW.plusSeconds(61)));

        assertProtocol(
                () -> service.approve(firstApproval()),
                "invalid_request",
                400,
                IndependentBoardOAuthAuthorizationService.CONSENT_REQUEST_EXPIRED);
        verify(mapper, never()).approveAuthorizationRequestIfVersion(any(), any());
        verify(mapper, never()).insertAuthorizationCode(any());
        verify(mapper, never()).insertReceipt(any());
    }

    @Test
    void aadOrStateDigestTamperFailsBeforeAnyAuthorizationWrite() {
        when(stateCipher.decrypt(any(), any(), any(), any()))
                .thenReturn("different-state-0123456789");

        assertProtocol(
                () -> service.approve(firstApproval()),
                "server_error",
                500,
                IndependentBoardOAuthAuthorizationService.CONSENT_STATE_TAMPERED);
        verify(mapper, never()).approveAuthorizationRequestIfVersion(any(), any());
        verify(mapper, never()).insertAuthorizationCode(any());
        verify(mapper, never()).insertReceipt(any());
    }

    @Test
    void casConflictReturnsNoCodeOrReceipt() {
        when(mapper.approveAuthorizationRequestIfVersion(any(), eq(0L))).thenReturn(0);

        assertProtocol(
                () -> service.approve(firstApproval()),
                "invalid_request",
                409,
                IndependentBoardOAuthAuthorizationService.REQUEST_CONFLICT);
        verify(mapper, never()).insertAuthorizationCode(any());
        verify(mapper, never()).insertReceipt(any());
    }

    @Test
    void receiptFailureThrowsAfterCasAndCodeSoRequiredTransactionMustRollbackAll() {
        when(mapper.insertReceipt(any())).thenReturn(1, 0);

        assertProtocol(
                () -> service.approve(firstApproval()),
                "server_error",
                500,
                IndependentBoardOAuthAuthorizationService.AUTHORIZATION_RECEIPT_WRITE_FAILED);
        verify(mapper).approveAuthorizationRequestIfVersion(any(), eq(0L));
        verify(mapper).insertAuthorizationCode(any());
        verify(mapper, times(2)).insertReceipt(any());
    }

    @Test
    void denialDecryptsAndVerifiesStateBeforeCasThenClearsCiphertextAndWritesOneReceipt() {
        BoardOAuthAuthorizationDeniedResult result = service.deny(
                new BoardOAuthAuthorizationDenialCommand(
                        REQUEST_HANDLE,
                        USER_ID,
                        TENANT_ID,
                        BoardOAuthConsentIntent.FIRST_CONNECT));

        InOrder order = inOrder(stateCipher, mapper);
        order.verify(mapper).selectAuthorizationRequestLocatorByHandle(any(byte[].class));
        order.verify(mapper).selectClientForUpdate(CLIENT_ID);
        order.verify(mapper).selectAuthorizationRequestForUpdate(
                eq(REQUEST_ID), eq(CLIENT_ID), any(byte[].class));
        order.verify(stateCipher).decrypt(any(), any(), any(), any());
        order.verify(mapper).denyAuthorizationRequestIfVersion(any(), eq(0L));
        order.verify(mapper).insertReceipt(any());

        assertEquals(REDIRECT, result.redirectUri());
        assertEquals(RAW_STATE, result.rawState());
        assertEquals(BoardOAuthProfile.ISSUER, result.issuerUri());
        assertEquals("access_denied", result.oauthError());
        assertEquals("DENIED", request.getStatus());
        assertEquals(BoardOAuthConsentIntent.FIRST_CONNECT, request.getConsentIntent());
        assertEquals(TENANT_ID, request.getTenantId());
        assertEquals(MEMBER_ID, request.getMemberId());
        assertEquals(USER_ID, request.getUserId());
        assertNull(request.getStateKeyRef());
        assertNull(request.getStateNonce());
        assertNull(request.getStateCiphertext());
        verify(mapper, never()).insertAuthorizationCode(any());

        ArgumentCaptor<BoardOAuthReceipt> receipt =
                ArgumentCaptor.forClass(BoardOAuthReceipt.class);
        verify(mapper).insertReceipt(receipt.capture());
        assertEquals("AUTHORIZATION_DENIED", receipt.getValue().getAction());
        assertNull(receipt.getValue().getAuthorizationCodeId());
    }

    @Test
    void commandsExposeOnlyTrustedFourFieldShapeAndAllSecretDtosAreRedacted() {
        assertEquals(
                Set.of(
                        "rawRequestHandle",
                        "serverAuthenticatedUserId",
                        "serverValidatedTenantId",
                        "intent"),
                declaredFields(BoardOAuthAuthorizationApprovalCommand.class));
        assertEquals(
                Set.of(
                        "rawRequestHandle",
                        "serverAuthenticatedUserId",
                        "serverValidatedTenantId",
                        "intent"),
                declaredFields(BoardOAuthAuthorizationDenialCommand.class));

        BoardOAuthAuthorizationApprovalCommand approval = firstApproval();
        BoardOAuthAuthorizationDenialCommand denial =
                new BoardOAuthAuthorizationDenialCommand(
                        REQUEST_HANDLE,
                        USER_ID,
                        TENANT_ID,
                        BoardOAuthConsentIntent.FIRST_CONNECT);
        BoardOAuthAuthorizationApprovedResult approved =
                new BoardOAuthAuthorizationApprovedResult(
                        REDIRECT, "raw-code", RAW_STATE, BoardOAuthProfile.ISSUER, NOW);
        BoardOAuthAuthorizationDeniedResult denied =
                new BoardOAuthAuthorizationDeniedResult(
                        REDIRECT, RAW_STATE, BoardOAuthProfile.ISSUER, "access_denied");

        assertEquals("BoardOAuthAuthorizationApprovalCommand[REDACTED]",
                approval.toString());
        assertEquals("BoardOAuthAuthorizationDenialCommand[REDACTED]",
                denial.toString());
        assertEquals("BoardOAuthAuthorizationApprovedResult[REDACTED]",
                approved.toString());
        assertEquals("BoardOAuthAuthorizationDeniedResult[REDACTED]",
                denied.toString());
        assertFalse(approval.toString().contains(REQUEST_HANDLE));
        assertFalse(approved.toString().contains("raw-code"));
        assertFalse(denied.toString().contains(RAW_STATE));
    }

    @Test
    void approveAndDenyCoreMethodsCannotOwnThePublicationTransaction()
            throws Exception {
        Transactional approve = IndependentBoardOAuthAuthorizationService.class
                .getDeclaredMethod("approve", BoardOAuthAuthorizationApprovalCommand.class)
                .getAnnotation(Transactional.class);
        Transactional deny = IndependentBoardOAuthAuthorizationService.class
                .getDeclaredMethod("deny", BoardOAuthAuthorizationDenialCommand.class)
                .getAnnotation(Transactional.class);

        assertNull(approve);
        assertNull(deny);
        assertFalse(Modifier.isPublic(
                IndependentBoardOAuthAuthorizationService.class.getModifiers()));
    }

    private static Set<String> declaredFields(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .map(Field::getName)
                .collect(Collectors.toSet());
    }

    private static BoardOAuthAuthorizationApprovalCommand firstApproval() {
        return new BoardOAuthAuthorizationApprovalCommand(
                REQUEST_HANDLE,
                USER_ID,
                TENANT_ID,
                BoardOAuthConsentIntent.FIRST_CONNECT);
    }

    private void replaceServiceClock(Clock clock) {
        service = new IndependentBoardOAuthAuthorizationService(
                boardMapper,
                mapper,
                stateCipher,
                new BoardOAuthRequestHandleGenerator(),
                new BoardOAuthAuthorizationCodeGenerator(),
                new BoardOAuthReceiptFactory(),
                clock);
    }

    private static BoardEnterpriseAuthority activeEnterprise() {
        BoardEnterpriseAuthority value = new BoardEnterpriseAuthority();
        value.setTenantId(TENANT_ID);
        value.setTenantName("Board tenant");
        value.setStatus(1);
        value.setDelFlag("0");
        return value;
    }

    private static BoardEnterpriseMemberScope activeMember() {
        BoardEnterpriseMemberScope value = new BoardEnterpriseMemberScope();
        value.setTenantId(TENANT_ID);
        value.setMemberId(MEMBER_ID);
        value.setUserId(USER_ID);
        value.setStatus(1);
        value.setDelFlag("0");
        return value;
    }

    private static BoardProductEntitlement currentVipEntitlement() {
        BoardProductEntitlement value = new BoardProductEntitlement();
        value.setId(100L);
        value.setTenantId(TENANT_ID);
        value.setMemberId(MEMBER_ID);
        value.setUserId(USER_ID);
        value.setProductCode(IndependentBoardOAuthAuthorizationService.PRODUCT_CODE);
        value.setPlanCode("BOARD_VIP");
        value.setStatus("ACTIVE");
        value.setValidFrom(Date.from(NOW.minusSeconds(3600)));
        value.setValidUntil(Date.from(NOW.plusSeconds(3600)));
        value.setVersion(0L);
        return value;
    }

    private static BoardProductPlan exactVipPlan() {
        BoardProductPlan value = new BoardProductPlan();
        value.setProductCode(IndependentBoardOAuthAuthorizationService.PRODUCT_CODE);
        value.setPlanCode("BOARD_VIP");
        value.setVip(true);
        value.setConnectorRequired(true);
        value.setDailyMeetingLimit(5);
        value.setAgendaLimit(30);
        value.setSeatLimit(null);
        value.setSecretaryEnabled(true);
        value.setStatus("ACTIVE");
        return value;
    }

    private static BoardOAuthClient activeClient() {
        BoardOAuthClient value = new BoardOAuthClient();
        value.setId(500L);
        value.setClientId(CLIENT_ID);
        value.setClientName("未验证的本地公共客户端");
        value.setIssuerUri(BoardOAuthProfile.ISSUER);
        value.setResourceUri(BoardOAuthProfile.RESOURCE);
        value.setProductCode(IndependentBoardOAuthAuthorizationService.PRODUCT_CODE);
        value.setSourceCode(IndependentBoardOAuthAuthorizationService.SOURCE_CODE);
        value.setConnectorCode(IndependentBoardOAuthAuthorizationService.CONNECTOR_CODE);
        value.setRedirectPort(54321);
        value.setRedirectUri(REDIRECT);
        value.setTokenEndpointAuthMethod("none");
        value.setGrantTypesCanonical("authorization_code refresh_token");
        value.setResponseTypesCanonical("code");
        value.setScopeCanonical(BoardOAuthProfile.CANONICAL_SCOPE);
        value.setScopeDigest(BoardOAuthCrypto.sha256Ascii(BoardOAuthProfile.CANONICAL_SCOPE));
        value.setMetadataDigest(new byte[32]);
        value.setRegistrationSourceDigest(new byte[32]);
        value.setStatus("ACTIVE");
        Instant registeredAt = NOW.minusSeconds(24 * 60 * 60);
        value.setRegisteredAt(Date.from(registeredAt));
        value.setExpiresAt(Date.from(registeredAt.plusSeconds(31L * 24 * 60 * 60)));
        value.setTerminatedAt(null);
        value.setVersion(0L);
        return value;
    }

    private static BoardOAuthAuthorizationRequest pendingRequest() {
        BoardOAuthAuthorizationRequest value = new BoardOAuthAuthorizationRequest();
        value.setId(REQUEST_ID);
        value.setRequestHandleDigest(BoardOAuthCrypto.sha256Ascii(REQUEST_HANDLE));
        value.setClientId(CLIENT_ID);
        value.setRedirectUri(REDIRECT);
        value.setCodeChallenge("A".repeat(43));
        value.setCodeChallengeMethod(BoardOAuthProfile.PKCE_METHOD);
        value.setStateDigest(BoardOAuthCrypto.sha256Ascii(RAW_STATE));
        value.setStateKeyRef("state-key-v1");
        value.setStateNonce(new byte[12]);
        value.setStateCiphertext(new byte[32]);
        value.setIssuerUri(BoardOAuthProfile.ISSUER);
        value.setResourceUri(BoardOAuthProfile.RESOURCE);
        value.setProductCode(IndependentBoardOAuthAuthorizationService.PRODUCT_CODE);
        value.setSourceCode(IndependentBoardOAuthAuthorizationService.SOURCE_CODE);
        value.setConnectorCode(IndependentBoardOAuthAuthorizationService.CONNECTOR_CODE);
        value.setScopeCanonical(BoardOAuthProfile.CANONICAL_SCOPE);
        value.setScopeDigest(BoardOAuthCrypto.sha256Ascii(BoardOAuthProfile.CANONICAL_SCOPE));
        value.setStatus("PENDING");
        value.setRequestedAt(Date.from(NOW.minusSeconds(30)));
        value.setExpiresAt(Date.from(NOW.plusSeconds(270)));
        value.setVersion(0L);
        return value;
    }

    private static BoardConnectorBinding terminalBinding() {
        BoardConnectorBinding value = new BoardConnectorBinding();
        value.setId(800L);
        value.setBindingId("11111111-2222-3333-4444-555555555555");
        value.setTenantId(TENANT_ID);
        value.setMemberId(MEMBER_ID);
        value.setUserId(USER_ID);
        value.setProductCode(IndependentBoardOAuthAuthorizationService.PRODUCT_CODE);
        value.setSourceCode(IndependentBoardOAuthAuthorizationService.SOURCE_CODE);
        value.setConnectorCode(IndependentBoardOAuthAuthorizationService.CONNECTOR_CODE);
        value.setIssuerUri(BoardOAuthProfile.ISSUER);
        value.setResourceUri(BoardOAuthProfile.RESOURCE);
        value.setClientId("b".repeat(43));
        value.setPrincipalSubjectDigest("a".repeat(64));
        value.setStatus("REVOKED");
        value.setVerificationMethod("MCP_INITIALIZE");
        value.setEvidenceDigest("e".repeat(64));
        value.setVerifiedAt(Date.from(NOW.minusSeconds(100)));
        value.setLastSeenAt(Date.from(NOW.minusSeconds(90)));
        value.setValidUntil(Date.from(NOW.minusSeconds(10)));
        value.setRevokedAt(Date.from(NOW.minusSeconds(20)));
        value.setVersion(2L);
        return value;
    }

    private static void assertProtocol(
            Runnable invocation,
            String expectedOAuthError,
            int expectedStatus,
            String expectedReason) {
        BoardOAuthProtocolException failure = assertThrows(
                BoardOAuthProtocolException.class,
                invocation::run);
        assertEquals(expectedOAuthError, failure.oauthError());
        assertEquals(expectedStatus, failure.httpStatus());
        assertEquals(expectedReason, failure.reasonCode());
        assertFalse(failure.toString().contains(REQUEST_HANDLE));
        assertFalse(failure.toString().contains(RAW_STATE));
    }
}
