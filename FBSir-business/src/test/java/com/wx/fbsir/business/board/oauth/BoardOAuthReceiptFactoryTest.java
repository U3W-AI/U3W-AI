package com.wx.fbsir.business.board.oauth;

import com.wx.fbsir.business.board.oauth.domain.BoardOAuthAuthorizationCode;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthAuthorizationRequest;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthReceipt;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Queue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoardOAuthReceiptFactoryTest {
    private static final Instant NOW = Instant.parse("2026-07-21T03:04:05.678Z");

    @Test
    void approvalCreatesTwoTypedCorrelatedDigestOnlyReceipts() {
        Queue<String> ids = new ArrayDeque<>();
        ids.add("correlation_1");
        ids.add("approved_1");
        ids.add("code_issued_1");
        BoardOAuthReceiptFactory factory = new BoardOAuthReceiptFactory(ids::remove);
        BoardOAuthAuthorizationRequest request = approvedRequest();
        BoardOAuthAuthorizationCode code = activeCode(request);

        BoardOAuthReceiptFactory.ApprovalReceipts pair = factory.approval(
                request, code, request.getUserId(), NOW);
        BoardOAuthReceipt approved = pair.authorizationApproved();
        BoardOAuthReceipt issued = pair.authorizationCodeIssued();

        assertEquals("AUTHORIZATION_APPROVED", approved.getAction());
        assertEquals("AUTHORIZATION_CODE_ISSUED", issued.getAction());
        assertEquals("correlation_1", approved.getCorrelationId());
        assertEquals("correlation_1", issued.getCorrelationId());
        assertEquals(request.getId(), approved.getAuthorizationRequestId());
        assertEquals(request.getId(), issued.getAuthorizationRequestId());
        assertNull(approved.getAuthorizationCodeId());
        assertEquals(code.getId(), issued.getAuthorizationCodeId());
        assertNull(approved.getFamilyId());
        assertNull(approved.getTokenId());
        assertNull(approved.getBindingId());
        assertEquals("USER", approved.getActorType());
        assertEquals(request.getUserId(), approved.getActorUserId());
        assertArrayEquals(request.getPrincipalSubjectDigest(),
                approved.getActorSubjectDigest());
        assertEquals("ACTION_COMPLETED", approved.getEvidenceLevel());
        assertEquals(Date.from(NOW), approved.getCreatedAt());
        assertEquals(32, approved.getPayloadDigest().length);
        assertEquals(32, issued.getPayloadDigest().length);
        assertFalse(BoardOAuthCrypto.constantTimeEquals(
                approved.getPayloadDigest(), issued.getPayloadDigest()));
        assertEquals("ApprovalReceipts[REDACTED]", pair.toString());
    }

    @Test
    void denialCreatesOnlyTheRequestScopedTypedReceipt() {
        Queue<String> ids = new ArrayDeque<>();
        ids.add("correlation_2");
        ids.add("denied_1");
        BoardOAuthReceiptFactory factory = new BoardOAuthReceiptFactory(ids::remove);
        BoardOAuthAuthorizationRequest request = approvedRequest();
        request.setStatus("DENIED");
        request.setApprovedAt(null);
        request.setDeniedAt(Date.from(NOW));
        request.setStateKeyRef(null);
        request.setStateNonce(null);
        request.setStateCiphertext(null);

        BoardOAuthReceipt receipt = factory.denial(request, request.getUserId(), NOW);

        assertEquals("AUTHORIZATION_DENIED", receipt.getAction());
        assertEquals(request.getId(), receipt.getAuthorizationRequestId());
        assertNull(receipt.getAuthorizationCodeId());
        assertNull(receipt.getFamilyId());
        assertNull(receipt.getTokenId());
        assertNull(receipt.getBindingId());
        assertEquals("ACTION_COMPLETED", receipt.getEvidenceLevel());
    }

    @Test
    void malformedContextAndIdGenerationFailClosed() {
        BoardOAuthAuthorizationRequest malformedRequest = approvedRequest();
        malformedRequest.setPrincipalSubjectDigest(new byte[31]);
        BoardOAuthReceiptFactory factory = new BoardOAuthReceiptFactory(() -> "valid_id");

        BoardOAuthProtocolException malformed = assertThrows(
                BoardOAuthProtocolException.class,
                () -> factory.approval(
                        malformedRequest, activeCode(malformedRequest), 33L, NOW));
        assertEquals(BoardOAuthReceiptFactory.RECEIPT_INVALID, malformed.reasonCode());

        BoardOAuthReceiptFactory invalidId = new BoardOAuthReceiptFactory(
                () -> "raw state must not become an id");
        BoardOAuthAuthorizationRequest validRequest = approvedRequest();
        BoardOAuthAuthorizationCode validCode = activeCode(validRequest);
        BoardOAuthProtocolException idFailure = assertThrows(
                BoardOAuthProtocolException.class,
                () -> invalidId.approval(validRequest, validCode, 33L, NOW));
        assertEquals(BoardOAuthReceiptFactory.RECEIPT_ID_GENERATION_FAILED,
                idFailure.reasonCode());
        assertFalse(idFailure.toString().contains("raw state"));
    }

    @Test
    void consentIntentIsMandatoryAndCodeMustCarryTheSameLineage() {
        BoardOAuthReceiptFactory factory = new BoardOAuthReceiptFactory(() -> "valid_id");

        BoardOAuthAuthorizationRequest missing = approvedRequest();
        missing.setConsentIntent(null);
        assertInvalid(() -> factory.denial(denied(missing), 33L, NOW));

        BoardOAuthAuthorizationRequest request = approvedRequest();
        BoardOAuthAuthorizationCode mismatched = activeCode(request);
        mismatched.setConsentIntent(BoardOAuthConsentIntent.EXPLICIT_REAUTHORIZATION);
        assertInvalid(() -> factory.approval(request, mismatched, 33L, NOW));
    }

    @Test
    void canonicalV2DigestBindsTheTrustedConsentIntent() {
        BoardOAuthAuthorizationRequest firstRequest = approvedRequest();
        BoardOAuthAuthorizationCode firstCode = activeCode(firstRequest);
        Queue<String> firstIds = new ArrayDeque<>(
                java.util.List.of("correlation", "approved", "issued"));
        BoardOAuthReceipt first = new BoardOAuthReceiptFactory(firstIds::remove)
                .approval(firstRequest, firstCode, 33L, NOW)
                .authorizationCodeIssued();

        BoardOAuthAuthorizationRequest secondRequest = approvedRequest();
        secondRequest.setConsentIntent(BoardOAuthConsentIntent.EXPLICIT_REAUTHORIZATION);
        BoardOAuthAuthorizationCode secondCode = activeCode(secondRequest);
        Queue<String> secondIds = new ArrayDeque<>(
                java.util.List.of("correlation", "approved", "issued"));
        BoardOAuthReceipt second = new BoardOAuthReceiptFactory(secondIds::remove)
                .approval(secondRequest, secondCode, 33L, NOW)
                .authorizationCodeIssued();

        assertEquals(
                "FBSIR:OAUTH:AUTHORIZATION_RECEIPT:v2",
                BoardOAuthReceiptFactory.CANONICAL_DOMAIN_V2);
        assertFalse(BoardOAuthCrypto.constantTimeEquals(
                first.getPayloadDigest(), second.getPayloadDigest()));
    }

    private static BoardOAuthAuthorizationRequest approvedRequest() {
        BoardOAuthAuthorizationRequest request = new BoardOAuthAuthorizationRequest();
        request.setId(700L);
        request.setRequestHandleDigest(BoardOAuthCrypto.sha256Ascii("h".repeat(43)));
        request.setClientId("c".repeat(43));
        request.setRedirectUri("http://127.0.0.1:54321/oauth/callback");
        request.setCodeChallenge("A".repeat(43));
        request.setCodeChallengeMethod(BoardOAuthProfile.PKCE_METHOD);
        request.setStateDigest(BoardOAuthCrypto.sha256Ascii("state-0123456789-abcd"));
        request.setIssuerUri(BoardOAuthProfile.ISSUER);
        request.setResourceUri(BoardOAuthProfile.RESOURCE);
        request.setProductCode("FBSIR_INDEPENDENT_BOARD");
        request.setSourceCode("WORKBUDDY");
        request.setConnectorCode("fbs-connector");
        request.setScopeCanonical(BoardOAuthProfile.CANONICAL_SCOPE);
        request.setScopeDigest(BoardOAuthCrypto.sha256Ascii(BoardOAuthProfile.CANONICAL_SCOPE));
        request.setTenantId(11L);
        request.setMemberId(22L);
        request.setUserId(33L);
        request.setPrincipalSubjectDigest(BoardOAuthPrincipalSubject.digest(11L, 22L, 33L));
        request.setConsentIntent(BoardOAuthConsentIntent.FIRST_CONNECT);
        request.setStatus("APPROVED");
        request.setRequestedAt(Date.from(NOW.minusSeconds(30)));
        request.setExpiresAt(Date.from(NOW.plusSeconds(270)));
        request.setApprovedAt(Date.from(NOW));
        request.setDeniedAt(null);
        request.setConsumedAt(null);
        request.setVersion(1L);
        request.setStateKeyRef("key-v1");
        request.setStateNonce(new byte[12]);
        request.setStateCiphertext(new byte[32]);
        return request;
    }

    private static BoardOAuthAuthorizationCode activeCode(
            BoardOAuthAuthorizationRequest request) {
        BoardOAuthAuthorizationCode code = new BoardOAuthAuthorizationCode();
        code.setId(900L);
        code.setCodeDigest(BoardOAuthCrypto.sha256Ascii("z".repeat(43)));
        code.setAuthorizationRequestId(request.getId());
        code.setClientId(request.getClientId());
        code.setRedirectUri(request.getRedirectUri());
        code.setCodeChallenge(request.getCodeChallenge());
        code.setCodeChallengeMethod(request.getCodeChallengeMethod());
        code.setIssuerUri(request.getIssuerUri());
        code.setResourceUri(request.getResourceUri());
        code.setProductCode(request.getProductCode());
        code.setSourceCode(request.getSourceCode());
        code.setConnectorCode(request.getConnectorCode());
        code.setScopeCanonical(request.getScopeCanonical());
        code.setScopeDigest(request.getScopeDigest());
        code.setTenantId(request.getTenantId());
        code.setMemberId(request.getMemberId());
        code.setUserId(request.getUserId());
        code.setPrincipalSubjectDigest(request.getPrincipalSubjectDigest());
        code.setConsentIntent(request.getConsentIntent());
        code.setStatus("ACTIVE");
        code.setIssuedAt(Date.from(NOW));
        code.setExpiresAt(Date.from(NOW.plusSeconds(60)));
        code.setVersion(0L);
        return code;
    }

    private static BoardOAuthAuthorizationRequest denied(
            BoardOAuthAuthorizationRequest request) {
        request.setStatus("DENIED");
        request.setApprovedAt(null);
        request.setDeniedAt(Date.from(NOW));
        request.setStateKeyRef(null);
        request.setStateNonce(null);
        request.setStateCiphertext(null);
        return request;
    }

    private static void assertInvalid(Runnable invocation) {
        BoardOAuthProtocolException failure = assertThrows(
                BoardOAuthProtocolException.class, invocation::run);
        assertEquals(BoardOAuthReceiptFactory.RECEIPT_INVALID, failure.reasonCode());
    }
}
