package com.wx.fbsir.business.board.service;

import com.wx.fbsir.business.board.dto.BoardConnectorBindingKey;
import com.wx.fbsir.business.board.dto.BoardConnectorBindingSnapshot;
import com.wx.fbsir.business.board.oauth.BoardOAuthCrypto;
import com.wx.fbsir.business.board.oauth.BoardOAuthProfile;
import com.wx.fbsir.business.board.oauth.mapper.IndependentBoardOAuthMapper;
import com.wx.fbsir.business.board.oauth.mapper.IndependentBoardOAuthMapper.TokenContextLocator;
import com.wx.fbsir.common.exception.ServiceException;
import java.util.Objects;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Hidden root transaction; callers cannot submit a family or attestation. */
@Service
class IndependentBoardOAuthFirstProtectedRequestTransactionRunner {
    static final String BOARD_TRANSACTION_MANAGER = "transactionManager";
    static final String ACCESS_TOKEN_INVALID = "OAUTH_ACCESS_TOKEN_INVALID";
    static final String ACCESS_TOKEN_LOOKUP_FAILED =
            "OAUTH_ACCESS_TOKEN_LOOKUP_FAILED";

    private final IndependentBoardOAuthMapper oauthMapper;
    private final IndependentBoardConnectorBindingService bindingService;

    IndependentBoardOAuthFirstProtectedRequestTransactionRunner(
            IndependentBoardOAuthMapper oauthMapper,
            IndependentBoardConnectorBindingService bindingService) {
        this.oauthMapper = oauthMapper;
        this.bindingService = bindingService;
    }

    @Transactional(
            transactionManager = BOARD_TRANSACTION_MANAGER,
            propagation = Propagation.REQUIRED,
            isolation = Isolation.REPEATABLE_READ,
            rollbackFor = Exception.class)
    public BoardConnectorBindingSnapshot activate(
            byte[] presentedAccessTokenDigest,
            String verificationMethod) {
        byte[] tokenDigest = requireDigest(presentedAccessTokenDigest);
        requireVerificationMethod(verificationMethod);
        TokenContextLocator locator;
        try {
            locator = oauthMapper.selectTokenContextLocatorByDigest(tokenDigest);
        } catch (DataAccessException lookupFailure) {
            throw new ServiceException(ACCESS_TOKEN_LOOKUP_FAILED, 503);
        } catch (RuntimeException lookupFailure) {
            throw new ServiceException(ACCESS_TOKEN_LOOKUP_FAILED, 500);
        }
        requireLocatorKey(locator);
        BoardConnectorBindingKey key = new BoardConnectorBindingKey(
                locator.getTenantId(),
                locator.getMemberId(),
                locator.getUserId(),
                locator.getProductCode());
        return bindingService.activateFirstProtectedOAuthFamily(
                key,
                locator.getUserId(),
                tokenDigest,
                verificationMethod);
    }

    private byte[] requireDigest(byte[] candidate) {
        if (candidate == null || candidate.length != BoardOAuthCrypto.SHA256_BYTES) {
            throw new ServiceException(ACCESS_TOKEN_INVALID, 401);
        }
        return candidate.clone();
    }

    private void requireVerificationMethod(String verificationMethod) {
        if (!Objects.equals(
                        verificationMethod,
                        IndependentBoardConnectorBindingService.VERIFY_INITIALIZE)
                && !Objects.equals(
                        verificationMethod,
                        IndependentBoardConnectorBindingService.VERIFY_TOOLS_LIST)) {
            throw new ServiceException(
                    IndependentBoardOAuthFirstProtectedRequestFacade.METHOD_NOT_ALLOWED,
                    403);
        }
    }

    /** Locator values discover locks only; every field is re-read under lock. */
    private void requireLocatorKey(TokenContextLocator locator) {
        if (locator == null
                || locator.getTokenId() == null || locator.getTokenId() <= 0L
                || locator.getFamilyId() == null || locator.getFamilyId().isBlank()
                || locator.getClientId() == null || locator.getClientId().isBlank()
                || locator.getTenantId() == null || locator.getTenantId() <= 0L
                || locator.getMemberId() == null || locator.getMemberId() <= 0L
                || locator.getUserId() == null || locator.getUserId() <= 0L
                || locator.getConsentIntent() == null
                || !Objects.equals(
                        locator.getProductCode(),
                        IndependentBoardEntitlementService.PRODUCT_CODE)
                || !Objects.equals(
                        locator.getSourceCode(),
                        IndependentBoardConnectorBindingService.SOURCE_CODE)
                || !Objects.equals(
                        locator.getConnectorCode(),
                        IndependentBoardConnectorBindingService.CONNECTOR_CODE)
                || !Objects.equals(locator.getIssuerUri(), BoardOAuthProfile.ISSUER)
                || !Objects.equals(locator.getResourceUri(), BoardOAuthProfile.RESOURCE)) {
            throw new ServiceException(ACCESS_TOKEN_INVALID, 401);
        }
    }
}
