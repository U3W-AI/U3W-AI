package com.wx.fbsir.business.board.service;

import com.wx.fbsir.business.board.dto.BoardConnectorBindingSnapshot;
import com.wx.fbsir.business.board.oauth.BoardOAuthCrypto;
import com.wx.fbsir.common.exception.ServiceException;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Public, transport-neutral entrypoint for the first protected MCP request.
 *
 * <p>No HTTP route is published by this class. A future MCP adapter must also
 * prove that the bearer was supplied exactly once and only in the
 * {@code Authorization} header before calling this facade.</p>
 */
@Service
public final class IndependentBoardOAuthFirstProtectedRequestFacade {
    public static final String ROOT_TRANSACTION_REQUIRED =
            "OAUTH_FIRST_PROTECTED_ROOT_TRANSACTION_REQUIRED";
    public static final String BEARER_HEADER_INVALID =
            "OAUTH_BEARER_HEADER_INVALID";
    public static final String METHOD_NOT_ALLOWED =
            "OAUTH_FIRST_PROTECTED_METHOD_NOT_ALLOWED";

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Pattern OPAQUE_ACCESS_TOKEN =
            Pattern.compile("\\A[A-Za-z0-9_-]{43}\\z");

    private final IndependentBoardOAuthFirstProtectedRequestTransactionRunner runner;

    IndependentBoardOAuthFirstProtectedRequestFacade(
            IndependentBoardOAuthFirstProtectedRequestTransactionRunner runner) {
        this.runner = runner;
    }

    public BoardConnectorBindingSnapshot activate(
            String authorizationHeader,
            String mcpMethod) {
        requireNoInheritedTransaction();
        String verificationMethod = requireVerificationMethod(mcpMethod);
        byte[] tokenDigest = requireBearerDigest(authorizationHeader);
        return runner.activate(tokenDigest, verificationMethod);
    }

    private void requireNoInheritedTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new ServiceException(ROOT_TRANSACTION_REQUIRED, 500);
        }
    }

    private String requireVerificationMethod(String mcpMethod) {
        if ("initialize".equals(mcpMethod)) {
            return IndependentBoardConnectorBindingService.VERIFY_INITIALIZE;
        }
        if ("tools/list".equals(mcpMethod)) {
            return IndependentBoardConnectorBindingService.VERIFY_TOOLS_LIST;
        }
        throw new ServiceException(METHOD_NOT_ALLOWED, 403);
    }

    private byte[] requireBearerDigest(String authorizationHeader) {
        if (authorizationHeader == null
                || !authorizationHeader.startsWith(BEARER_PREFIX)
                || authorizationHeader.length() != BEARER_PREFIX.length() + 43) {
            throw new ServiceException(BEARER_HEADER_INVALID, 400);
        }
        String rawToken = authorizationHeader.substring(BEARER_PREFIX.length());
        if (!OPAQUE_ACCESS_TOKEN.matcher(rawToken).matches()) {
            throw new ServiceException(BEARER_HEADER_INVALID, 400);
        }
        return BoardOAuthCrypto.sha256Ascii(rawToken);
    }
}
