package com.wx.fbsir.business.board.service;

import com.wx.fbsir.business.board.domain.BoardConnectorBinding;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthAuthorizationCode;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthAuthorizationRequest;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthReceipt;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthToken;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthTokenFamily;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthClient;
import com.wx.fbsir.business.board.oauth.BoardOAuthConsentIntent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/** Package-private immutable before-image retained by a transaction lease. */
final class BoardOAuthFamilyActivationProof {
    private final Object ownerToken;
    private final BoardOAuthFamilyActivationContext context;
    private final BindingImage binding;
    private final ClientImage client;
    private final RequestImage authorizationRequest;
    private final CodeImage authorizationCode;
    private final ReceiptImage tokenFamilyCreatedReceipt;
    private final Long presentedTokenId;
    private final byte[] presentedTokenDigest;
    private final String verificationMethod;
    private final FamilyImage pendingFamily;
    private final List<TokenImage> pendingTokens;
    private final FamilyImage oldActiveFamily;
    private final List<TokenImage> oldActiveTokens;
    private String w4aReceiptId;
    private String w4aReceiptPayloadDigest;

    BoardOAuthFamilyActivationProof(
            Object ownerToken,
            BoardOAuthFamilyActivationContext context,
            BoardConnectorBinding binding,
            BoardOAuthClient client,
            BoardOAuthTokenFamily pendingFamily,
            List<BoardOAuthToken> pendingTokens,
            BoardOAuthTokenFamily oldActiveFamily,
            List<BoardOAuthToken> oldActiveTokens) {
        this(
                ownerToken,
                context,
                binding,
                client,
                null,
                null,
                null,
                null,
                null,
                null,
                pendingFamily,
                pendingTokens,
                oldActiveFamily,
                oldActiveTokens);
    }

    BoardOAuthFamilyActivationProof(
            Object ownerToken,
            BoardOAuthFamilyActivationContext context,
            BoardConnectorBinding binding,
            BoardOAuthClient client,
            BoardOAuthAuthorizationRequest authorizationRequest,
            BoardOAuthAuthorizationCode authorizationCode,
            BoardOAuthReceipt tokenFamilyCreatedReceipt,
            Long presentedTokenId,
            byte[] presentedTokenDigest,
            String verificationMethod,
            BoardOAuthTokenFamily pendingFamily,
            List<BoardOAuthToken> pendingTokens,
            BoardOAuthTokenFamily oldActiveFamily,
            List<BoardOAuthToken> oldActiveTokens) {
        this.ownerToken = ownerToken;
        this.context = context;
        this.binding = new BindingImage(binding);
        this.client = new ClientImage(client);
        this.authorizationRequest = authorizationRequest == null
                ? null
                : new RequestImage(authorizationRequest);
        this.authorizationCode = authorizationCode == null
                ? null
                : new CodeImage(authorizationCode);
        this.tokenFamilyCreatedReceipt = tokenFamilyCreatedReceipt == null
                ? null
                : new ReceiptImage(tokenFamilyCreatedReceipt);
        this.presentedTokenId = presentedTokenId;
        this.presentedTokenDigest = copy(presentedTokenDigest);
        this.verificationMethod = verificationMethod;
        this.pendingFamily = new FamilyImage(pendingFamily);
        this.pendingTokens = immutableTokenImages(pendingTokens);
        this.oldActiveFamily = oldActiveFamily == null ? null : new FamilyImage(oldActiveFamily);
        this.oldActiveTokens = immutableTokenImages(oldActiveTokens);
    }

    BoardOAuthFamilyActivationContext context() { return context; }
    BindingImage binding() { return binding; }
    ClientImage client() { return client; }
    RequestImage authorizationRequest() { return authorizationRequest; }
    CodeImage authorizationCode() { return authorizationCode; }
    ReceiptImage tokenFamilyCreatedReceipt() { return tokenFamilyCreatedReceipt; }
    Long presentedTokenId() { return presentedTokenId; }
    byte[] presentedTokenDigest() { return copy(presentedTokenDigest); }
    String verificationMethod() { return verificationMethod; }
    FamilyImage pendingFamily() { return pendingFamily; }
    List<TokenImage> pendingTokens() { return pendingTokens; }
    FamilyImage oldActiveFamily() { return oldActiveFamily; }
    List<TokenImage> oldActiveTokens() { return oldActiveTokens; }
    String w4aReceiptId() { return w4aReceiptId; }
    String w4aReceiptPayloadDigest() { return w4aReceiptPayloadDigest; }

    boolean hasPresentedBearerProvenance() {
        return authorizationRequest != null
                && authorizationCode != null
                && tokenFamilyCreatedReceipt != null
                && presentedTokenId != null
                && presentedTokenId > 0L
                && presentedTokenDigest != null
                && presentedTokenDigest.length == 32
                && verificationMethod != null;
    }

    void recordW4aReceipt(
            Object ownerCapability,
            String receiptId,
            String payloadDigest) {
        if (ownerToken != ownerCapability) {
            throw new IllegalStateException("activation proof owner capability mismatch");
        }
        if (w4aReceiptId != null || w4aReceiptPayloadDigest != null) {
            throw new IllegalStateException("W4a receipt proof is already recorded");
        }
        w4aReceiptId = receiptId;
        w4aReceiptPayloadDigest = payloadDigest;
    }

    static final class BindingImage {
        final Long id;
        final String bindingId;
        final Long tenantId;
        final Long memberId;
        final Long userId;
        final String productCode;
        final String sourceCode;
        final String connectorCode;
        final String issuerUri;
        final String resourceUri;
        final String clientId;
        final String principalSubjectDigest;
        final String status;
        final String verificationMethod;
        final String evidenceDigest;
        final Date verifiedAt;
        final Date lastSeenAt;
        final Date validUntil;
        final Date revokedAt;
        final Long version;
        final Date createdAt;
        final Date updatedAt;
        final List<String> scopes;

        BindingImage(BoardConnectorBinding value) {
            this.id = value.getId();
            this.bindingId = value.getBindingId();
            this.tenantId = value.getTenantId();
            this.memberId = value.getMemberId();
            this.userId = value.getUserId();
            this.productCode = value.getProductCode();
            this.sourceCode = value.getSourceCode();
            this.connectorCode = value.getConnectorCode();
            this.issuerUri = value.getIssuerUri();
            this.resourceUri = value.getResourceUri();
            this.clientId = value.getClientId();
            this.principalSubjectDigest = value.getPrincipalSubjectDigest();
            this.status = value.getStatus();
            this.verificationMethod = value.getVerificationMethod();
            this.evidenceDigest = value.getEvidenceDigest();
            this.verifiedAt = copy(value.getVerifiedAt());
            this.lastSeenAt = copy(value.getLastSeenAt());
            this.validUntil = copy(value.getValidUntil());
            this.revokedAt = copy(value.getRevokedAt());
            this.version = value.getVersion();
            this.createdAt = copy(value.getCreatedAt());
            this.updatedAt = copy(value.getUpdatedAt());
            List<String> sortedScopes = new ArrayList<>(value.getScopes());
            Collections.sort(sortedScopes);
            this.scopes = List.copyOf(sortedScopes);
        }
    }

    static final class ClientImage {
        final Long id;
        final String clientId;
        final String clientName;
        final String issuerUri;
        final String resourceUri;
        final String productCode;
        final String sourceCode;
        final String connectorCode;
        final Integer redirectPort;
        final String redirectUri;
        final String tokenEndpointAuthMethod;
        final String grantTypesCanonical;
        final String responseTypesCanonical;
        final String scopeCanonical;
        final byte[] scopeDigest;
        final byte[] metadataDigest;
        final byte[] registrationSourceDigest;
        final String status;
        final Date registeredAt;
        final Date expiresAt;
        final Date terminatedAt;
        final Long version;
        final Date createdAt;
        final Date updatedAt;

        ClientImage(BoardOAuthClient value) {
            this.id = value.getId();
            this.clientId = value.getClientId();
            this.clientName = value.getClientName();
            this.issuerUri = value.getIssuerUri();
            this.resourceUri = value.getResourceUri();
            this.productCode = value.getProductCode();
            this.sourceCode = value.getSourceCode();
            this.connectorCode = value.getConnectorCode();
            this.redirectPort = value.getRedirectPort();
            this.redirectUri = value.getRedirectUri();
            this.tokenEndpointAuthMethod = value.getTokenEndpointAuthMethod();
            this.grantTypesCanonical = value.getGrantTypesCanonical();
            this.responseTypesCanonical = value.getResponseTypesCanonical();
            this.scopeCanonical = value.getScopeCanonical();
            this.scopeDigest = copy(value.getScopeDigest());
            this.metadataDigest = copy(value.getMetadataDigest());
            this.registrationSourceDigest = copy(value.getRegistrationSourceDigest());
            this.status = value.getStatus();
            this.registeredAt = copy(value.getRegisteredAt());
            this.expiresAt = copy(value.getExpiresAt());
            this.terminatedAt = copy(value.getTerminatedAt());
            this.version = value.getVersion();
            this.createdAt = copy(value.getCreatedAt());
            this.updatedAt = copy(value.getUpdatedAt());
        }
    }

    static final class RequestImage {
        final Long id;
        final byte[] requestHandleDigest;
        final String clientId;
        final String redirectUri;
        final String codeChallenge;
        final String codeChallengeMethod;
        final byte[] stateDigest;
        final String stateKeyRef;
        final byte[] stateNonce;
        final byte[] stateCiphertext;
        final String issuerUri;
        final String resourceUri;
        final String productCode;
        final String sourceCode;
        final String connectorCode;
        final String scopeCanonical;
        final byte[] scopeDigest;
        final Long tenantId;
        final Long memberId;
        final Long userId;
        final byte[] principalSubjectDigest;
        final BoardOAuthConsentIntent consentIntent;
        final String status;
        final Date requestedAt;
        final Date expiresAt;
        final Date approvedAt;
        final Date deniedAt;
        final Date consumedAt;
        final Long version;
        final Date createdAt;
        final Date updatedAt;

        RequestImage(BoardOAuthAuthorizationRequest value) {
            this.id = value.getId();
            this.requestHandleDigest = copy(value.getRequestHandleDigest());
            this.clientId = value.getClientId();
            this.redirectUri = value.getRedirectUri();
            this.codeChallenge = value.getCodeChallenge();
            this.codeChallengeMethod = value.getCodeChallengeMethod();
            this.stateDigest = copy(value.getStateDigest());
            this.stateKeyRef = value.getStateKeyRef();
            this.stateNonce = copy(value.getStateNonce());
            this.stateCiphertext = copy(value.getStateCiphertext());
            this.issuerUri = value.getIssuerUri();
            this.resourceUri = value.getResourceUri();
            this.productCode = value.getProductCode();
            this.sourceCode = value.getSourceCode();
            this.connectorCode = value.getConnectorCode();
            this.scopeCanonical = value.getScopeCanonical();
            this.scopeDigest = copy(value.getScopeDigest());
            this.tenantId = value.getTenantId();
            this.memberId = value.getMemberId();
            this.userId = value.getUserId();
            this.principalSubjectDigest = copy(value.getPrincipalSubjectDigest());
            this.consentIntent = value.getConsentIntent();
            this.status = value.getStatus();
            this.requestedAt = copy(value.getRequestedAt());
            this.expiresAt = copy(value.getExpiresAt());
            this.approvedAt = copy(value.getApprovedAt());
            this.deniedAt = copy(value.getDeniedAt());
            this.consumedAt = copy(value.getConsumedAt());
            this.version = value.getVersion();
            this.createdAt = copy(value.getCreatedAt());
            this.updatedAt = copy(value.getUpdatedAt());
        }
    }

    static final class CodeImage {
        final Long id;
        final byte[] codeDigest;
        final Long authorizationRequestId;
        final String clientId;
        final String redirectUri;
        final String codeChallenge;
        final String codeChallengeMethod;
        final String issuerUri;
        final String resourceUri;
        final String productCode;
        final String sourceCode;
        final String connectorCode;
        final String scopeCanonical;
        final byte[] scopeDigest;
        final Long tenantId;
        final Long memberId;
        final Long userId;
        final byte[] principalSubjectDigest;
        final BoardOAuthConsentIntent consentIntent;
        final String status;
        final Date issuedAt;
        final Date expiresAt;
        final Date usedAt;
        final Date revokedAt;
        final Long version;
        final Date createdAt;
        final Date updatedAt;

        CodeImage(BoardOAuthAuthorizationCode value) {
            this.id = value.getId();
            this.codeDigest = copy(value.getCodeDigest());
            this.authorizationRequestId = value.getAuthorizationRequestId();
            this.clientId = value.getClientId();
            this.redirectUri = value.getRedirectUri();
            this.codeChallenge = value.getCodeChallenge();
            this.codeChallengeMethod = value.getCodeChallengeMethod();
            this.issuerUri = value.getIssuerUri();
            this.resourceUri = value.getResourceUri();
            this.productCode = value.getProductCode();
            this.sourceCode = value.getSourceCode();
            this.connectorCode = value.getConnectorCode();
            this.scopeCanonical = value.getScopeCanonical();
            this.scopeDigest = copy(value.getScopeDigest());
            this.tenantId = value.getTenantId();
            this.memberId = value.getMemberId();
            this.userId = value.getUserId();
            this.principalSubjectDigest = copy(value.getPrincipalSubjectDigest());
            this.consentIntent = value.getConsentIntent();
            this.status = value.getStatus();
            this.issuedAt = copy(value.getIssuedAt());
            this.expiresAt = copy(value.getExpiresAt());
            this.usedAt = copy(value.getUsedAt());
            this.revokedAt = copy(value.getRevokedAt());
            this.version = value.getVersion();
            this.createdAt = copy(value.getCreatedAt());
            this.updatedAt = copy(value.getUpdatedAt());
        }
    }

    static final class ReceiptImage {
        final Long id;
        final String receiptId;
        final String action;
        final String clientId;
        final Long authorizationRequestId;
        final Long authorizationCodeId;
        final String familyId;
        final Long tokenId;
        final String bindingId;
        final Long tenantId;
        final Long memberId;
        final Long userId;
        final byte[] principalSubjectDigest;
        final String actorType;
        final Long actorUserId;
        final byte[] actorSubjectDigest;
        final String correlationId;
        final byte[] payloadDigest;
        final String evidenceLevel;
        final Date createdAt;

        ReceiptImage(BoardOAuthReceipt value) {
            this.id = value.getId();
            this.receiptId = value.getReceiptId();
            this.action = value.getAction();
            this.clientId = value.getClientId();
            this.authorizationRequestId = value.getAuthorizationRequestId();
            this.authorizationCodeId = value.getAuthorizationCodeId();
            this.familyId = value.getFamilyId();
            this.tokenId = value.getTokenId();
            this.bindingId = value.getBindingId();
            this.tenantId = value.getTenantId();
            this.memberId = value.getMemberId();
            this.userId = value.getUserId();
            this.principalSubjectDigest = copy(value.getPrincipalSubjectDigest());
            this.actorType = value.getActorType();
            this.actorUserId = value.getActorUserId();
            this.actorSubjectDigest = copy(value.getActorSubjectDigest());
            this.correlationId = value.getCorrelationId();
            this.payloadDigest = copy(value.getPayloadDigest());
            this.evidenceLevel = value.getEvidenceLevel();
            this.createdAt = copy(value.getCreatedAt());
        }
    }

    private static List<TokenImage> immutableTokenImages(List<BoardOAuthToken> tokens) {
        if (tokens == null) {
            return List.of();
        }
        return tokens.stream().map(TokenImage::new).toList();
    }

    static final class FamilyImage {
        final Long id;
        final String familyId;
        final Long originAuthorizationCodeId;
        final String clientId;
        final Long tenantId;
        final Long memberId;
        final Long userId;
        final String productCode;
        final String sourceCode;
        final String connectorCode;
        final String issuerUri;
        final String resourceUri;
        final String scopeCanonical;
        final byte[] scopeDigest;
        final byte[] principalSubjectDigest;
        final BoardOAuthConsentIntent consentIntent;
        final String bindingId;
        final Long bindingVersion;
        final String status;
        final String lifecycleSlot;
        final Long currentRefreshGeneration;
        final Date issuedAt;
        final Date activatedAt;
        final Date expiresAt;
        final Date terminatedAt;
        final Long version;
        final Date createdAt;
        final Date updatedAt;

        FamilyImage(BoardOAuthTokenFamily value) {
            this.id = value.getId();
            this.familyId = value.getFamilyId();
            this.originAuthorizationCodeId = value.getOriginAuthorizationCodeId();
            this.clientId = value.getClientId();
            this.tenantId = value.getTenantId();
            this.memberId = value.getMemberId();
            this.userId = value.getUserId();
            this.productCode = value.getProductCode();
            this.sourceCode = value.getSourceCode();
            this.connectorCode = value.getConnectorCode();
            this.issuerUri = value.getIssuerUri();
            this.resourceUri = value.getResourceUri();
            this.scopeCanonical = value.getScopeCanonical();
            this.scopeDigest = copy(value.getScopeDigest());
            this.principalSubjectDigest = copy(value.getPrincipalSubjectDigest());
            this.consentIntent = value.getConsentIntent();
            this.bindingId = value.getBindingId();
            this.bindingVersion = value.getBindingVersion();
            this.status = value.getStatus();
            this.lifecycleSlot = value.getLifecycleSlot();
            this.currentRefreshGeneration = value.getCurrentRefreshGeneration();
            this.issuedAt = copy(value.getIssuedAt());
            this.activatedAt = copy(value.getActivatedAt());
            this.expiresAt = copy(value.getExpiresAt());
            this.terminatedAt = copy(value.getTerminatedAt());
            this.version = value.getVersion();
            this.createdAt = copy(value.getCreatedAt());
            this.updatedAt = copy(value.getUpdatedAt());
        }
    }

    static final class TokenImage {
        final Long id;
        final byte[] tokenDigest;
        final String familyId;
        final String tokenType;
        final Long generation;
        final String resourceUri;
        final String scopeCanonical;
        final byte[] scopeDigest;
        final String status;
        final Integer activeRefreshSlot;
        final Date issuedAt;
        final Date usedAt;
        final Date revokedAt;
        final Date expiresAt;
        final Long version;
        final Date createdAt;
        final Date updatedAt;

        TokenImage(BoardOAuthToken value) {
            this.id = value.getId();
            this.tokenDigest = copy(value.getTokenDigest());
            this.familyId = value.getFamilyId();
            this.tokenType = value.getTokenType();
            this.generation = value.getGeneration();
            this.resourceUri = value.getResourceUri();
            this.scopeCanonical = value.getScopeCanonical();
            this.scopeDigest = copy(value.getScopeDigest());
            this.status = value.getStatus();
            this.activeRefreshSlot = value.getActiveRefreshSlot();
            this.issuedAt = copy(value.getIssuedAt());
            this.usedAt = copy(value.getUsedAt());
            this.revokedAt = copy(value.getRevokedAt());
            this.expiresAt = copy(value.getExpiresAt());
            this.version = value.getVersion();
            this.createdAt = copy(value.getCreatedAt());
            this.updatedAt = copy(value.getUpdatedAt());
        }
    }

    private static byte[] copy(byte[] value) {
        return value == null ? null : Arrays.copyOf(value, value.length);
    }

    private static Date copy(Date value) {
        return value == null ? null : new Date(value.getTime());
    }
}
