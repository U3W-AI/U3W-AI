package com.wx.fbsir.business.board.service;

import com.wx.fbsir.business.board.dto.BoardConnectorBindingKey;
import com.wx.fbsir.business.board.dto.BoardConnectorBindingSnapshot;
import com.wx.fbsir.business.board.oauth.BoardOAuthCrypto;
import com.wx.fbsir.business.board.oauth.BoardOAuthConsentIntent;
import com.wx.fbsir.business.board.oauth.BoardOAuthProfile;
import com.wx.fbsir.business.board.oauth.mapper.IndependentBoardOAuthMapper;
import com.wx.fbsir.business.board.oauth.mapper.IndependentBoardOAuthMapper.TokenContextLocator;
import com.wx.fbsir.common.exception.ServiceException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class IndependentBoardOAuthFirstProtectedRequestContractTest {
    private static final String RAW_TOKEN = "A".repeat(43);
    private static final String AUTHORIZATION = "Bearer " + RAW_TOKEN;

    @AfterEach
    void clearTransactionThreadState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void facadeStrictlyParsesTheHeaderAndPassesOnlyItsDigestToTheRunner() {
        IndependentBoardOAuthFirstProtectedRequestTransactionRunner runner =
                mock(IndependentBoardOAuthFirstProtectedRequestTransactionRunner.class);
        IndependentBoardOAuthFirstProtectedRequestFacade facade =
                new IndependentBoardOAuthFirstProtectedRequestFacade(runner);
        BoardConnectorBindingSnapshot expected = snapshot();
        when(runner.activate(any(byte[].class), eq(
                IndependentBoardConnectorBindingService.VERIFY_INITIALIZE)))
                .thenReturn(expected);

        BoardConnectorBindingSnapshot actual = facade.activate(
                AUTHORIZATION, "initialize");

        org.mockito.ArgumentCaptor<byte[]> digest =
                org.mockito.ArgumentCaptor.forClass(byte[].class);
        verify(runner).activate(
                digest.capture(),
                eq(IndependentBoardConnectorBindingService.VERIFY_INITIALIZE));
        assertSame(expected, actual);
        assertArrayEquals(BoardOAuthCrypto.sha256Ascii(RAW_TOKEN), digest.getValue());
        assertEquals(BoardOAuthCrypto.SHA256_BYTES, digest.getValue().length);
        assertFalse(Arrays.equals(
                RAW_TOKEN.getBytes(StandardCharsets.US_ASCII), digest.getValue()));
        verifyNoMoreInteractions(runner);
    }

    @Test
    void facadeRejectsEveryNonCanonicalBearerHeaderBeforeCallingTheRunner() {
        IndependentBoardOAuthFirstProtectedRequestTransactionRunner runner =
                mock(IndependentBoardOAuthFirstProtectedRequestTransactionRunner.class);
        IndependentBoardOAuthFirstProtectedRequestFacade facade =
                new IndependentBoardOAuthFirstProtectedRequestFacade(runner);
        List<String> invalidHeaders = Arrays.asList(
                null,
                RAW_TOKEN,
                "bearer " + RAW_TOKEN,
                "Bearer  " + RAW_TOKEN,
                "Bearer\t" + RAW_TOKEN,
                "Bearer " + RAW_TOKEN + " ",
                "Bearer " + "A".repeat(42),
                "Bearer " + "A".repeat(44),
                "Bearer " + "A".repeat(42) + ".");

        for (String header : invalidHeaders) {
            ServiceException failure = assertThrows(
                    ServiceException.class,
                    () -> facade.activate(header, "initialize"));
            assertEquals(
                    IndependentBoardOAuthFirstProtectedRequestFacade.BEARER_HEADER_INVALID,
                    failure.getMessage());
            assertEquals(400, failure.getCode());
        }
        verifyNoInteractions(runner);
    }

    @Test
    void facadeMapsOnlyTheTwoExactFirstProtectedMethods() {
        IndependentBoardOAuthFirstProtectedRequestTransactionRunner runner =
                mock(IndependentBoardOAuthFirstProtectedRequestTransactionRunner.class);
        IndependentBoardOAuthFirstProtectedRequestFacade facade =
                new IndependentBoardOAuthFirstProtectedRequestFacade(runner);

        facade.activate(AUTHORIZATION, "initialize");
        facade.activate(AUTHORIZATION, "tools/list");

        verify(runner).activate(
                any(byte[].class),
                eq(IndependentBoardConnectorBindingService.VERIFY_INITIALIZE));
        verify(runner).activate(
                any(byte[].class),
                eq(IndependentBoardConnectorBindingService.VERIFY_TOOLS_LIST));
        verifyNoMoreInteractions(runner);

        reset(runner);
        for (String method : Arrays.asList(
                null, "", "Initialize", "initialize ", " tools/list", "tools/call")) {
            ServiceException failure = assertThrows(
                    ServiceException.class,
                    () -> facade.activate(AUTHORIZATION, method));
            assertEquals(
                    IndependentBoardOAuthFirstProtectedRequestFacade.METHOD_NOT_ALLOWED,
                    failure.getMessage());
            assertEquals(403, failure.getCode());
        }
        verifyNoInteractions(runner);
    }

    @Test
    void facadeRejectsInheritedTransactionOrSynchronizationWithZeroRunnerCalls() {
        IndependentBoardOAuthFirstProtectedRequestTransactionRunner runner =
                mock(IndependentBoardOAuthFirstProtectedRequestTransactionRunner.class);
        IndependentBoardOAuthFirstProtectedRequestFacade facade =
                new IndependentBoardOAuthFirstProtectedRequestFacade(runner);

        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertRootTransactionFailure(assertThrows(
                ServiceException.class,
                () -> facade.activate(AUTHORIZATION, "initialize")));
        TransactionSynchronizationManager.setActualTransactionActive(false);

        TransactionSynchronizationManager.initSynchronization();
        assertRootTransactionFailure(assertThrows(
                ServiceException.class,
                () -> facade.activate(AUTHORIZATION, "tools/list")));

        verifyNoInteractions(runner);
    }

    @Test
    void hiddenRunnerOwnsOneNamedRequiredRepeatableReadRootTransaction()
            throws Exception {
        assertFalse(Modifier.isPublic(
                IndependentBoardOAuthFirstProtectedRequestTransactionRunner.class
                        .getModifiers()));
        assertTrue(Modifier.isPublic(
                IndependentBoardOAuthFirstProtectedRequestFacade.class.getModifiers()));
        assertTrue(Modifier.isFinal(
                IndependentBoardOAuthFirstProtectedRequestFacade.class.getModifiers()));

        Method runnerMethod =
                IndependentBoardOAuthFirstProtectedRequestTransactionRunner.class
                        .getDeclaredMethod("activate", byte[].class, String.class);
        Transactional transactional = runnerMethod.getAnnotation(Transactional.class);
        assertNotNull(transactional);
        assertEquals(
                IndependentBoardOAuthFirstProtectedRequestTransactionRunner
                        .BOARD_TRANSACTION_MANAGER,
                transactional.transactionManager());
        assertEquals(Propagation.REQUIRED, transactional.propagation());
        assertEquals(Isolation.REPEATABLE_READ, transactional.isolation());
        assertTrue(Arrays.asList(transactional.rollbackFor()).contains(Exception.class));
        assertNull(IndependentBoardOAuthFirstProtectedRequestFacade.class
                .getDeclaredMethod("activate", String.class, String.class)
                .getAnnotation(Transactional.class));
    }

    @Test
    void locatorProducesOnlyTheBindingKeyAndNeverAuthorizesTheRequest() {
        IndependentBoardOAuthMapper mapper = mock(IndependentBoardOAuthMapper.class);
        IndependentBoardConnectorBindingService bindingService =
                mock(IndependentBoardConnectorBindingService.class);
        IndependentBoardOAuthFirstProtectedRequestTransactionRunner runner =
                new IndependentBoardOAuthFirstProtectedRequestTransactionRunner(
                        mapper, bindingService);
        TokenContextLocator locator = validLocator();
        locator.setTokenType("REFRESH");
        locator.setTokenStatus("REVOKED");
        locator.setFamilyStatus("COMPROMISED");
        locator.setScopeCanonical("caller-controlled scope");
        locator.setPrincipalSubjectDigest(new byte[] {1});
        byte[] presentedDigest = BoardOAuthCrypto.sha256Ascii(RAW_TOKEN);
        BoardConnectorBindingSnapshot expected = snapshot();
        when(mapper.selectTokenContextLocatorByDigest(any(byte[].class)))
                .thenReturn(locator);
        when(bindingService.activateFirstProtectedOAuthFamily(
                any(BoardConnectorBindingKey.class),
                anyLong(),
                any(byte[].class),
                anyString()))
                .thenReturn(expected);

        BoardConnectorBindingSnapshot actual = runner.activate(
                presentedDigest,
                IndependentBoardConnectorBindingService.VERIFY_INITIALIZE);

        org.mockito.ArgumentCaptor<BoardConnectorBindingKey> key =
                org.mockito.ArgumentCaptor.forClass(BoardConnectorBindingKey.class);
        org.mockito.ArgumentCaptor<byte[]> forwardedDigest =
                org.mockito.ArgumentCaptor.forClass(byte[].class);
        verify(mapper).selectTokenContextLocatorByDigest(any(byte[].class));
        verify(bindingService).activateFirstProtectedOAuthFamily(
                key.capture(),
                eq(42L),
                forwardedDigest.capture(),
                eq(IndependentBoardConnectorBindingService.VERIFY_INITIALIZE));
        assertSame(expected, actual);
        assertEquals(new BoardConnectorBindingKey(
                7L,
                11L,
                42L,
                IndependentBoardEntitlementService.PRODUCT_CODE), key.getValue());
        assertArrayEquals(presentedDigest, forwardedDigest.getValue());
        assertNotSame(presentedDigest, forwardedDigest.getValue());
        verifyNoMoreInteractions(mapper, bindingService);
    }

    @Test
    void invalidLocatorNeverReachesTheBindingService() {
        IndependentBoardOAuthMapper mapper = mock(IndependentBoardOAuthMapper.class);
        IndependentBoardConnectorBindingService bindingService =
                mock(IndependentBoardConnectorBindingService.class);
        IndependentBoardOAuthFirstProtectedRequestTransactionRunner runner =
                new IndependentBoardOAuthFirstProtectedRequestTransactionRunner(
                        mapper, bindingService);
        byte[] digest = BoardOAuthCrypto.sha256Ascii(RAW_TOKEN);

        when(mapper.selectTokenContextLocatorByDigest(any(byte[].class)))
                .thenReturn(null);
        assertInvalidLocator(runner, digest);
        verifyNoInteractions(bindingService);

        List<Consumer<TokenContextLocator>> invalidators = List.of(
                value -> value.setTokenId(0L),
                value -> value.setFamilyId(" "),
                value -> value.setClientId(""),
                value -> value.setTenantId(null),
                value -> value.setMemberId(0L),
                value -> value.setUserId(-1L),
                value -> value.setConsentIntent(null),
                value -> value.setProductCode("OTHER_PRODUCT"),
                value -> value.setSourceCode("OTHER_SOURCE"),
                value -> value.setConnectorCode("other-connector"),
                value -> value.setIssuerUri("https://issuer.invalid"),
                value -> value.setResourceUri("https://resource.invalid"));
        for (Consumer<TokenContextLocator> invalidate : invalidators) {
            reset(mapper, bindingService);
            TokenContextLocator invalid = validLocator();
            invalidate.accept(invalid);
            when(mapper.selectTokenContextLocatorByDigest(any(byte[].class)))
                    .thenReturn(invalid);
            assertInvalidLocator(runner, digest);
            verifyNoInteractions(bindingService);
        }
    }

    private static void assertInvalidLocator(
            IndependentBoardOAuthFirstProtectedRequestTransactionRunner runner,
            byte[] digest) {
        ServiceException failure = assertThrows(
                ServiceException.class,
                () -> runner.activate(
                        digest,
                        IndependentBoardConnectorBindingService.VERIFY_INITIALIZE));
        assertEquals(
                IndependentBoardOAuthFirstProtectedRequestTransactionRunner
                        .ACCESS_TOKEN_INVALID,
                failure.getMessage());
        assertEquals(401, failure.getCode());
    }

    private static void assertRootTransactionFailure(ServiceException failure) {
        assertEquals(
                IndependentBoardOAuthFirstProtectedRequestFacade
                        .ROOT_TRANSACTION_REQUIRED,
                failure.getMessage());
        assertEquals(500, failure.getCode());
    }

    private static TokenContextLocator validLocator() {
        TokenContextLocator value = new TokenContextLocator();
        value.setTokenId(101L);
        value.setFamilyId("pending-family");
        value.setClientId("c".repeat(43));
        value.setTenantId(7L);
        value.setMemberId(11L);
        value.setUserId(42L);
        value.setConsentIntent(BoardOAuthConsentIntent.FIRST_CONNECT);
        value.setProductCode(IndependentBoardEntitlementService.PRODUCT_CODE);
        value.setSourceCode(IndependentBoardConnectorBindingService.SOURCE_CODE);
        value.setConnectorCode(IndependentBoardConnectorBindingService.CONNECTOR_CODE);
        value.setIssuerUri(BoardOAuthProfile.ISSUER);
        value.setResourceUri(BoardOAuthProfile.RESOURCE);
        return value;
    }

    private static BoardConnectorBindingSnapshot snapshot() {
        Date now = new Date(1_700_000_000_000L);
        return new BoardConnectorBindingSnapshot(
                "binding-1",
                7L,
                11L,
                42L,
                IndependentBoardEntitlementService.PRODUCT_CODE,
                "ACTIVE",
                now,
                new Date(now.getTime() + 60_000L),
                1L);
    }
}
