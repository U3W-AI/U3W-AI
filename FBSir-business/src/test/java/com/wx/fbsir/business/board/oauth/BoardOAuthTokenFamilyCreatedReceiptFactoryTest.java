package com.wx.fbsir.business.board.oauth;

import com.wx.fbsir.business.board.oauth.domain.BoardOAuthAuthorizationCode;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthAuthorizationRequest;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthReceipt;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthToken;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthTokenFamily;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoardOAuthTokenFamilyCreatedReceiptFactoryTest {
    private static final Instant EVENT_AT =
            Instant.parse("2026-07-21T05:06:07.890Z");
    private static final String RECEIPT_ID = "family_created_receipt_1";
    private static final String CORRELATION_ID = "token_exchange_1";

    @Test
    void createsSchemaValidClientActorReceiptAndRecomputesIt() {
        Fixture fixture = fixture();

        BoardOAuthReceipt receipt = BoardOAuthTokenFamilyCreatedReceiptFactory.create(
                RECEIPT_ID,
                CORRELATION_ID,
                EVENT_AT,
                fixture.request,
                fixture.code,
                fixture.family,
                fixture.tokens());

        assertNull(receipt.getId());
        assertEquals(RECEIPT_ID, receipt.getReceiptId());
        assertEquals("TOKEN_FAMILY_CREATED", receipt.getAction());
        assertEquals(fixture.request.getClientId(), receipt.getClientId());
        assertNull(receipt.getAuthorizationRequestId());
        assertEquals(fixture.code.getId(), receipt.getAuthorizationCodeId());
        assertEquals(fixture.family.getFamilyId(), receipt.getFamilyId());
        assertNull(receipt.getTokenId());
        assertNull(receipt.getBindingId());
        assertEquals(fixture.family.getTenantId(), receipt.getTenantId());
        assertEquals(fixture.family.getMemberId(), receipt.getMemberId());
        assertEquals(fixture.family.getUserId(), receipt.getUserId());
        assertArrayEquals(
                fixture.family.getPrincipalSubjectDigest(),
                receipt.getPrincipalSubjectDigest());
        assertEquals("CLIENT", receipt.getActorType());
        assertNull(receipt.getActorUserId());
        assertArrayEquals(
                BoardOAuthCrypto.sha256Ascii(fixture.request.getClientId()),
                receipt.getActorSubjectDigest());
        assertEquals(CORRELATION_ID, receipt.getCorrelationId());
        assertEquals("ACTION_COMPLETED", receipt.getEvidenceLevel());
        assertEquals(Date.from(EVENT_AT), receipt.getCreatedAt());
        assertEquals(BoardOAuthCrypto.SHA256_BYTES,
                receipt.getPayloadDigest().length);
        assertEquals(
                "FBSIR:OAUTH:TOKEN_FAMILY_CREATED_RECEIPT:v2",
                BoardOAuthTokenFamilyCreatedReceiptFactory.CANONICAL_DOMAIN_V2);

        assertInvalid(() ->
                BoardOAuthTokenFamilyCreatedReceiptFactory.validate(
                        receipt,
                        fixture.request,
                        fixture.code,
                        fixture.family,
                        fixture.tokens()));
        receipt.setId(501L);

        assertArrayEquals(
                receipt.getPayloadDigest(),
                BoardOAuthTokenFamilyCreatedReceiptFactory
                        .recomputePayloadDigest(
                                receipt,
                                fixture.request,
                                fixture.code,
                                fixture.family,
                                fixture.tokens()));
        assertDoesNotThrow(() ->
                BoardOAuthTokenFamilyCreatedReceiptFactory.validate(
                        receipt,
                        fixture.request,
                        fixture.code,
                        fixture.family,
                        fixture.tokens()));
    }

    @Test
    void canonicalizesCallerTokenOrderByDatabaseId() {
        Fixture fixture = fixture();

        BoardOAuthReceipt reverseInput =
                BoardOAuthTokenFamilyCreatedReceiptFactory.create(
                        RECEIPT_ID,
                        CORRELATION_ID,
                        EVENT_AT,
                        fixture.request,
                        fixture.code,
                        fixture.family,
                        List.of(fixture.refresh, fixture.access));
        BoardOAuthReceipt forwardInput =
                BoardOAuthTokenFamilyCreatedReceiptFactory.create(
                        RECEIPT_ID,
                        CORRELATION_ID,
                        EVENT_AT,
                        fixture.request,
                        fixture.code,
                        fixture.family,
                        List.of(fixture.access, fixture.refresh));

        assertArrayEquals(
                forwardInput.getPayloadDigest(), reverseInput.getPayloadDigest());

        long accessId = fixture.access.getId();
        fixture.access.setId(fixture.refresh.getId());
        fixture.refresh.setId(accessId);
        assertInvalid(() ->
                BoardOAuthTokenFamilyCreatedReceiptFactory.validate(
                        forwardInput,
                        fixture.request,
                        fixture.code,
                        fixture.family,
                        fixture.tokens()));
    }

    @Test
    void payloadBindsFieldsNotProjectedIntoReceipt() {
        assertDriftRejected(fixture -> fixture.request.setUpdatedAt(
                Date.from(EVENT_AT.plusMillis(1))));
        assertDriftRejected(fixture -> fixture.code.setUpdatedAt(
                Date.from(EVENT_AT.plusMillis(1))));
        assertDriftRejected(fixture -> fixture.family.setUpdatedAt(
                Date.from(EVENT_AT.plusMillis(1))));
        assertDriftRejected(fixture -> fixture.access.setUpdatedAt(
                Date.from(EVENT_AT.plusMillis(1))));
        assertDriftRejected(fixture -> fixture.refresh.setUpdatedAt(
                Date.from(EVENT_AT.plusMillis(1))));
        assertDriftRejected(fixture -> fixture.request.setRequestHandleDigest(
                digest("different-request-handle")));
        assertDriftRejected(fixture -> fixture.code.setCodeDigest(
                digest("different-code")));
        assertDriftRejected(fixture -> fixture.access.setTokenDigest(
                digest("different-access-token")));
        assertContextInvalid(fixture -> fixture.family
                .setOriginAuthorizationCodeId(999L));
        assertContextInvalid(fixture -> fixture.request.setConsentIntent(null));
        assertContextInvalid(fixture -> fixture.code.setConsentIntent(
                BoardOAuthConsentIntent.EXPLICIT_REAUTHORIZATION));
        assertContextInvalid(fixture -> fixture.family.setConsentIntent(
                BoardOAuthConsentIntent.EXPLICIT_REAUTHORIZATION));
    }

    @Test
    void rejectsMissingDuplicateOrNonComplementaryTokenPairs() {
        Fixture missing = fixture();
        assertInvalid(() ->
                BoardOAuthTokenFamilyCreatedReceiptFactory.create(
                        RECEIPT_ID,
                        CORRELATION_ID,
                        EVENT_AT,
                        missing.request,
                        missing.code,
                        missing.family,
                        List.of(missing.access)));

        Fixture duplicate = fixture();
        assertInvalid(() ->
                BoardOAuthTokenFamilyCreatedReceiptFactory.create(
                        RECEIPT_ID,
                        CORRELATION_ID,
                        EVENT_AT,
                        duplicate.request,
                        duplicate.code,
                        duplicate.family,
                        List.of(duplicate.access, duplicate.access)));

        Fixture tooMany = fixture();
        BoardOAuthToken third = token(
                403L,
                tooMany.family,
                "ACCESS",
                digest("third-token"));
        assertInvalid(() ->
                BoardOAuthTokenFamilyCreatedReceiptFactory.create(
                        RECEIPT_ID,
                        CORRELATION_ID,
                        EVENT_AT,
                        tooMany.request,
                        tooMany.code,
                        tooMany.family,
                        List.of(tooMany.access, tooMany.refresh, third)));

        Fixture sameType = fixture();
        sameType.refresh.setTokenType("ACCESS");
        sameType.refresh.setActiveRefreshSlot(null);
        sameType.refresh.setExpiresAt(Date.from(EVENT_AT.plusSeconds(600)));
        assertInvalid(() ->
                BoardOAuthTokenFamilyCreatedReceiptFactory.create(
                        RECEIPT_ID,
                        CORRELATION_ID,
                        EVENT_AT,
                        sameType.request,
                        sameType.code,
                        sameType.family,
                        sameType.tokens()));

        Fixture sameDigest = fixture();
        sameDigest.refresh.setTokenDigest(
                sameDigest.access.getTokenDigest().clone());
        assertInvalid(() ->
                BoardOAuthTokenFamilyCreatedReceiptFactory.create(
                        RECEIPT_ID,
                        CORRELATION_ID,
                        EVENT_AT,
                        sameDigest.request,
                        sameDigest.code,
                        sameDigest.family,
                        sameDigest.tokens()));
    }

    @Test
    void rejectsEveryCrossObjectEventTimeMismatch() {
        assertContextInvalid(fixture -> fixture.request.setConsumedAt(
                Date.from(EVENT_AT.plusMillis(1))));
        assertContextInvalid(fixture -> fixture.code.setUsedAt(
                Date.from(EVENT_AT.plusMillis(1))));
        assertContextInvalid(fixture -> fixture.family.setIssuedAt(
                Date.from(EVENT_AT.plusMillis(1))));
        assertContextInvalid(fixture -> fixture.access.setIssuedAt(
                Date.from(EVENT_AT.plusMillis(1))));
        assertContextInvalid(fixture -> fixture.refresh.setIssuedAt(
                Date.from(EVENT_AT.plusMillis(1))));
        assertContextInvalid(fixture -> fixture.refresh.setExpiresAt(
                Date.from(fixture.family.getExpiresAt().toInstant().minusMillis(1))));
        assertContextInvalid(fixture -> fixture.request.setCreatedAt(
                Date.from(fixture.request.getRequestedAt().toInstant()
                        .minusSeconds(6))));
        assertContextInvalid(fixture -> fixture.code.setCreatedAt(
                Date.from(fixture.code.getIssuedAt().toInstant()
                        .minusSeconds(6))));
        assertContextInvalid(fixture -> fixture.family.setCreatedAt(
                Date.from(EVENT_AT.minusSeconds(6))));
        assertContextInvalid(fixture -> fixture.access.setUpdatedAt(
                Date.from(EVENT_AT.plusSeconds(6))));

        Fixture receiptTime = fixture();
        BoardOAuthReceipt receipt = receiptTime.createReceipt();
        receipt.setCreatedAt(Date.from(EVENT_AT.plusMillis(1)));
        assertInvalid(() ->
                BoardOAuthTokenFamilyCreatedReceiptFactory.validate(
                        receipt,
                        receiptTime.request,
                        receiptTime.code,
                        receiptTime.family,
                        receiptTime.tokens()));
    }

    @Test
    void rejectsReceiptShapeAndActorDriftBeforeDigestComparison() {
        Fixture codeLineage = fixture();
        BoardOAuthReceipt codeReceipt = codeLineage.createReceipt();
        codeReceipt.setAuthorizationCodeId(codeLineage.code.getId() + 1L);
        assertInvalid(() ->
                BoardOAuthTokenFamilyCreatedReceiptFactory.validate(
                        codeReceipt,
                        codeLineage.request,
                        codeLineage.code,
                        codeLineage.family,
                        codeLineage.tokens()));

        Fixture tokenProjection = fixture();
        BoardOAuthReceipt tokenReceipt = tokenProjection.createReceipt();
        tokenReceipt.setTokenId(tokenProjection.access.getId());
        assertInvalid(() ->
                BoardOAuthTokenFamilyCreatedReceiptFactory.validate(
                        tokenReceipt,
                        tokenProjection.request,
                        tokenProjection.code,
                        tokenProjection.family,
                        tokenProjection.tokens()));

        Fixture actor = fixture();
        BoardOAuthReceipt actorReceipt = actor.createReceipt();
        actorReceipt.setActorSubjectDigest(
                actor.family.getPrincipalSubjectDigest().clone());
        actorReceipt.setActorUserId(actor.family.getUserId());
        assertInvalid(() ->
                BoardOAuthTokenFamilyCreatedReceiptFactory.validate(
                        actorReceipt,
                        actor.request,
                        actor.code,
                        actor.family,
                        actor.tokens()));

        assertContextInvalid(fixture -> {
            byte[] nonCanonicalSubject = digest("non-canonical-subject");
            fixture.request.setPrincipalSubjectDigest(
                    nonCanonicalSubject.clone());
            fixture.code.setPrincipalSubjectDigest(
                    nonCanonicalSubject.clone());
            fixture.family.setPrincipalSubjectDigest(
                    nonCanonicalSubject.clone());
        });
    }

    @Test
    void validReceiptDigestChangesWithReceiptIdentityAndCorrelation() {
        Fixture fixture = fixture();
        BoardOAuthReceipt first = fixture.createReceipt();
        BoardOAuthReceipt second =
                BoardOAuthTokenFamilyCreatedReceiptFactory.create(
                        "family_created_receipt_2",
                        CORRELATION_ID,
                        EVENT_AT,
                        fixture.request,
                        fixture.code,
                        fixture.family,
                        fixture.tokens());
        BoardOAuthReceipt third =
                BoardOAuthTokenFamilyCreatedReceiptFactory.create(
                        RECEIPT_ID,
                        "token_exchange_2",
                        EVENT_AT,
                        fixture.request,
                        fixture.code,
                        fixture.family,
                        fixture.tokens());

        assertFalse(BoardOAuthCrypto.constantTimeEquals(
                first.getPayloadDigest(), second.getPayloadDigest()));
        assertFalse(BoardOAuthCrypto.constantTimeEquals(
                first.getPayloadDigest(), third.getPayloadDigest()));
    }

    @Test
    void canonicalV2DigestBindsConsentIntentAcrossTheWholeLineage() {
        Fixture first = fixture();
        BoardOAuthReceipt firstReceipt = first.createReceipt();

        Fixture second = fixture();
        second.request.setConsentIntent(BoardOAuthConsentIntent.EXPLICIT_REAUTHORIZATION);
        second.code.setConsentIntent(BoardOAuthConsentIntent.EXPLICIT_REAUTHORIZATION);
        second.family.setConsentIntent(BoardOAuthConsentIntent.EXPLICIT_REAUTHORIZATION);
        BoardOAuthReceipt secondReceipt = second.createReceipt();

        assertFalse(BoardOAuthCrypto.constantTimeEquals(
                firstReceipt.getPayloadDigest(), secondReceipt.getPayloadDigest()));
    }

    @Test
    void failuresAndToStringDoNotExposeDigestOrRawTokenMaterial() {
        Fixture fixture = fixture();
        BoardOAuthReceipt receipt = fixture.createReceipt();
        String accessDigest = HexFormat.of().formatHex(
                fixture.access.getTokenDigest());
        receipt.setPayloadDigest(digest("tampered-receipt-payload"));

        BoardOAuthProtocolException failure = assertThrows(
                BoardOAuthProtocolException.class,
                () -> BoardOAuthTokenFamilyCreatedReceiptFactory.validate(
                        receipt,
                        fixture.request,
                        fixture.code,
                        fixture.family,
                        fixture.tokens()));

        assertEquals(
                BoardOAuthTokenFamilyCreatedReceiptFactory.RECEIPT_INVALID,
                failure.reasonCode());
        assertFalse(failure.toString().contains(accessDigest));
        assertFalse(failure.toString().contains("access-token-secret"));
        assertFalse(failure.toString().contains(fixture.family.getFamilyId()));
        assertFalse(receipt.toString().contains(accessDigest));
        assertFalse(receipt.toString().contains("access-token-secret"));
    }

    private static void assertDriftRejected(Consumer<Fixture> drift) {
        Fixture fixture = fixture();
        BoardOAuthReceipt receipt = fixture.createReceipt();
        byte[] originalDigest = receipt.getPayloadDigest().clone();

        drift.accept(fixture);

        byte[] recomputed = BoardOAuthTokenFamilyCreatedReceiptFactory
                .recomputePayloadDigest(
                        receipt,
                        fixture.request,
                        fixture.code,
                        fixture.family,
                        fixture.tokens());
        assertNotEquals(
                HexFormat.of().formatHex(originalDigest),
                HexFormat.of().formatHex(recomputed));
        assertInvalid(() ->
                BoardOAuthTokenFamilyCreatedReceiptFactory.validate(
                        receipt,
                        fixture.request,
                        fixture.code,
                        fixture.family,
                        fixture.tokens()));
    }

    private static void assertContextInvalid(Consumer<Fixture> drift) {
        Fixture fixture = fixture();
        drift.accept(fixture);
        assertInvalid(fixture::createReceipt);
    }

    private static void assertInvalid(Runnable invocation) {
        BoardOAuthProtocolException failure = assertThrows(
                BoardOAuthProtocolException.class, invocation::run);
        assertEquals(
                BoardOAuthTokenFamilyCreatedReceiptFactory.RECEIPT_INVALID,
                failure.reasonCode());
        assertEquals("server_error", failure.oauthError());
        assertEquals(500, failure.httpStatus());
    }

    private static Fixture fixture() {
        BoardOAuthAuthorizationRequest request = request();
        BoardOAuthAuthorizationCode code = code(request);
        BoardOAuthTokenFamily family = family(code);
        BoardOAuthToken access = token(
                401L,
                family,
                "ACCESS",
                digest("access-token-secret"));
        BoardOAuthToken refresh = token(
                402L,
                family,
                "REFRESH",
                digest("refresh-token-secret"));
        return new Fixture(request, code, family, access, refresh);
    }

    private static BoardOAuthAuthorizationRequest request() {
        BoardOAuthAuthorizationRequest request =
                new BoardOAuthAuthorizationRequest();
        request.setId(101L);
        request.setRequestHandleDigest(digest("request-handle"));
        request.setClientId("c".repeat(43));
        request.setRedirectUri("http://127.0.0.1:17654/oauth/callback");
        request.setCodeChallenge("p".repeat(43));
        request.setCodeChallengeMethod(BoardOAuthProfile.PKCE_METHOD);
        request.setStateDigest(digest("opaque-state"));
        request.setStateKeyRef(null);
        request.setStateNonce(null);
        request.setStateCiphertext(null);
        applyProfile(request);
        request.setTenantId(11L);
        request.setMemberId(22L);
        request.setUserId(33L);
        request.setPrincipalSubjectDigest(
                BoardOAuthPrincipalSubject.digest(11L, 22L, 33L));
        request.setConsentIntent(BoardOAuthConsentIntent.FIRST_CONNECT);
        request.setStatus("CONSUMED");
        request.setRequestedAt(Date.from(EVENT_AT.minusSeconds(120)));
        request.setExpiresAt(Date.from(EVENT_AT.plusSeconds(180)));
        request.setApprovedAt(Date.from(EVENT_AT.minusSeconds(30)));
        request.setDeniedAt(null);
        request.setConsumedAt(Date.from(EVENT_AT));
        request.setVersion(2L);
        request.setCreatedAt(Date.from(EVENT_AT.minusSeconds(120)));
        request.setUpdatedAt(Date.from(EVENT_AT));
        return request;
    }

    private static BoardOAuthAuthorizationCode code(
            BoardOAuthAuthorizationRequest request) {
        BoardOAuthAuthorizationCode code = new BoardOAuthAuthorizationCode();
        code.setId(202L);
        code.setCodeDigest(digest("authorization-code-secret"));
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
        code.setScopeDigest(request.getScopeDigest().clone());
        code.setTenantId(request.getTenantId());
        code.setMemberId(request.getMemberId());
        code.setUserId(request.getUserId());
        code.setPrincipalSubjectDigest(
                request.getPrincipalSubjectDigest().clone());
        code.setConsentIntent(request.getConsentIntent());
        code.setStatus("USED");
        code.setIssuedAt(Date.from(EVENT_AT.minusSeconds(30)));
        code.setExpiresAt(Date.from(EVENT_AT.plusSeconds(30)));
        code.setUsedAt(Date.from(EVENT_AT));
        code.setRevokedAt(null);
        code.setVersion(1L);
        code.setCreatedAt(Date.from(EVENT_AT.minusSeconds(30)));
        code.setUpdatedAt(Date.from(EVENT_AT));
        return code;
    }

    private static BoardOAuthTokenFamily family(
            BoardOAuthAuthorizationCode code) {
        BoardOAuthTokenFamily family = new BoardOAuthTokenFamily();
        family.setId(303L);
        family.setFamilyId("family_303");
        family.setOriginAuthorizationCodeId(code.getId());
        family.setClientId(code.getClientId());
        family.setTenantId(code.getTenantId());
        family.setMemberId(code.getMemberId());
        family.setUserId(code.getUserId());
        family.setProductCode(code.getProductCode());
        family.setSourceCode(code.getSourceCode());
        family.setConnectorCode(code.getConnectorCode());
        family.setIssuerUri(code.getIssuerUri());
        family.setResourceUri(code.getResourceUri());
        family.setScopeCanonical(code.getScopeCanonical());
        family.setScopeDigest(code.getScopeDigest().clone());
        family.setPrincipalSubjectDigest(
                code.getPrincipalSubjectDigest().clone());
        family.setConsentIntent(code.getConsentIntent());
        family.setBindingId(null);
        family.setBindingVersion(null);
        family.setStatus("PENDING_BINDING");
        family.setLifecycleSlot("PENDING_BINDING");
        family.setCurrentRefreshGeneration(0L);
        family.setIssuedAt(Date.from(EVENT_AT));
        family.setActivatedAt(null);
        family.setExpiresAt(Date.from(EVENT_AT.plusSeconds(30L * 24L * 60L * 60L)));
        family.setTerminatedAt(null);
        family.setVersion(0L);
        family.setCreatedAt(Date.from(EVENT_AT));
        family.setUpdatedAt(Date.from(EVENT_AT));
        return family;
    }

    private static BoardOAuthToken token(
            Long id,
            BoardOAuthTokenFamily family,
            String tokenType,
            byte[] tokenDigest) {
        BoardOAuthToken token = new BoardOAuthToken();
        token.setId(id);
        token.setTokenDigest(tokenDigest);
        token.setFamilyId(family.getFamilyId());
        token.setTokenType(tokenType);
        token.setGeneration(0L);
        token.setResourceUri(family.getResourceUri());
        token.setScopeCanonical(family.getScopeCanonical());
        token.setScopeDigest(family.getScopeDigest().clone());
        token.setStatus("ACTIVE");
        token.setActiveRefreshSlot("REFRESH".equals(tokenType) ? 1 : null);
        token.setIssuedAt(Date.from(EVENT_AT));
        token.setUsedAt(null);
        token.setRevokedAt(null);
        token.setExpiresAt("ACCESS".equals(tokenType)
                ? Date.from(EVENT_AT.plusSeconds(600))
                : new Date(family.getExpiresAt().getTime()));
        token.setVersion(0L);
        token.setCreatedAt(Date.from(EVENT_AT));
        token.setUpdatedAt(Date.from(EVENT_AT));
        return token;
    }

    private static void applyProfile(
            BoardOAuthAuthorizationRequest request) {
        request.setIssuerUri(BoardOAuthProfile.ISSUER);
        request.setResourceUri(BoardOAuthProfile.RESOURCE);
        request.setProductCode("FBSIR_INDEPENDENT_BOARD");
        request.setSourceCode("WORKBUDDY");
        request.setConnectorCode("fbs-connector");
        request.setScopeCanonical(BoardOAuthProfile.CANONICAL_SCOPE);
        request.setScopeDigest(
                BoardOAuthCrypto.sha256Ascii(
                        BoardOAuthProfile.CANONICAL_SCOPE));
    }

    private static byte[] digest(String value) {
        return BoardOAuthCrypto.sha256Ascii(value);
    }

    private static final class Fixture {
        private final BoardOAuthAuthorizationRequest request;
        private final BoardOAuthAuthorizationCode code;
        private final BoardOAuthTokenFamily family;
        private final BoardOAuthToken access;
        private final BoardOAuthToken refresh;

        private Fixture(
                BoardOAuthAuthorizationRequest request,
                BoardOAuthAuthorizationCode code,
                BoardOAuthTokenFamily family,
                BoardOAuthToken access,
                BoardOAuthToken refresh) {
            this.request = request;
            this.code = code;
            this.family = family;
            this.access = access;
            this.refresh = refresh;
        }

        private List<BoardOAuthToken> tokens() {
            return List.of(access, refresh);
        }

        private BoardOAuthReceipt createReceipt() {
            BoardOAuthReceipt receipt = BoardOAuthTokenFamilyCreatedReceiptFactory.create(
                    RECEIPT_ID,
                    CORRELATION_ID,
                    EVENT_AT,
                    request,
                    code,
                    family,
                    tokens());
            receipt.setId(501L);
            return receipt;
        }

        @Override
        public String toString() {
            return "Fixture[REDACTED]";
        }
    }
}
