package com.wx.fbsir.business.board.oauth.mapper;

import com.wx.fbsir.business.board.oauth.BoardOAuthConsentIntent;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthAuthorizationCode;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthAuthorizationRequest;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthClient;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthReceipt;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthToken;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthTokenFamily;
import java.util.Date;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.annotations.Param;

public interface IndependentBoardOAuthMapper {
    int insertClient(BoardOAuthClient client);

    BoardOAuthClient selectClientForUpdate(@Param("clientId") String clientId);

    List<BoardOAuthClient> selectClientsForUpdate(
            @Param("clientIds") List<String> clientIds);

    int revokeClientIfVersion(
            @Param("clientId") String clientId,
            @Param("expectedVersion") Long expectedVersion,
            @Param("revokedAt") Date revokedAt);

    int expireClientIfVersion(
            @Param("clientId") String clientId,
            @Param("expectedVersion") Long expectedVersion,
            @Param("expiredAt") Date expiredAt);

    int terminateClientIfVersion(
            @Param("clientId") String clientId,
            @Param("expectedVersion") Long expectedVersion,
            @Param("targetStatus") String targetStatus,
            @Param("terminatedAt") Date terminatedAt);

    int insertAuthorizationRequest(BoardOAuthAuthorizationRequest request);

    BoardOAuthAuthorizationRequest selectAuthorizationRequestLocatorByHandle(
            @Param("requestHandleDigest") byte[] requestHandleDigest);

    BoardOAuthAuthorizationRequest selectAuthorizationRequestForUpdate(
            @Param("requestId") Long requestId,
            @Param("clientId") String clientId,
            @Param("requestHandleDigest") byte[] requestHandleDigest);

    BoardOAuthAuthorizationRequest selectAuthorizationRequestByIdAndClientForUpdate(
            @Param("requestId") Long requestId,
            @Param("clientId") String clientId);

    BoardOAuthAuthorizationRequest selectAuthorizationRequestByHandleForUpdate(
            @Param("requestHandleDigest") byte[] requestHandleDigest);

    int approveAuthorizationRequestIfVersion(
            @Param("request") BoardOAuthAuthorizationRequest request,
            @Param("expectedVersion") Long expectedVersion);

    int denyAuthorizationRequestIfVersion(
            @Param("request") BoardOAuthAuthorizationRequest request,
            @Param("expectedVersion") Long expectedVersion);

    int consumeAuthorizationRequestIfVersion(
            @Param("request") BoardOAuthAuthorizationRequest request,
            @Param("expectedVersion") Long expectedVersion);

    int expireAuthorizationRequestIfVersion(
            @Param("requestId") Long requestId,
            @Param("clientId") String clientId,
            @Param("expectedVersion") Long expectedVersion,
            @Param("expiredAt") Date expiredAt);

    int insertAuthorizationCode(BoardOAuthAuthorizationCode code);

    BoardOAuthAuthorizationCode selectAuthorizationCodeLocatorByDigest(
            @Param("codeDigest") byte[] codeDigest);

    TokenExchangeLockLocator selectTokenExchangeLockLocatorByDigest(
            @Param("codeDigest") byte[] codeDigest);

    BoardOAuthAuthorizationCode selectAuthorizationCodeLocatorById(
            @Param("codeId") Long codeId);

    BoardOAuthAuthorizationCode selectAuthorizationCodeForUpdate(
            @Param("codeId") Long codeId,
            @Param("clientId") String clientId,
            @Param("codeDigest") byte[] codeDigest);

    BoardOAuthAuthorizationCode selectAuthorizationCodeByIdAndClientForUpdate(
            @Param("codeId") Long codeId,
            @Param("clientId") String clientId);

    BoardOAuthAuthorizationCode selectAuthorizationCodeByDigestForUpdate(
            @Param("codeDigest") byte[] codeDigest);

    int consumeAuthorizationCodeIfVersion(
            @Param("codeId") Long codeId,
            @Param("expectedVersion") Long expectedVersion,
            @Param("usedAt") Date usedAt);

    int revokeAuthorizationCodeIfVersion(
            @Param("codeId") Long codeId,
            @Param("expectedVersion") Long expectedVersion,
            @Param("revokedAt") Date revokedAt);

    int consumeAuthorizationCodeForClientIfVersion(
            @Param("codeId") Long codeId,
            @Param("clientId") String clientId,
            @Param("expectedVersion") Long expectedVersion,
            @Param("usedAt") Date usedAt);

    int revokeAuthorizationCodeForClientIfVersion(
            @Param("codeId") Long codeId,
            @Param("clientId") String clientId,
            @Param("expectedVersion") Long expectedVersion,
            @Param("revokedAt") Date revokedAt);

    int expireAuthorizationCodeIfVersion(
            @Param("codeId") Long codeId,
            @Param("clientId") String clientId,
            @Param("expectedVersion") Long expectedVersion,
            @Param("expiredAt") Date expiredAt);

    int insertTokenFamily(BoardOAuthTokenFamily family);

    BoardOAuthTokenFamily selectTokenFamilyLocator(@Param("familyId") String familyId);

    BoardOAuthTokenFamily selectTokenFamilyByOriginAuthorizationCodeLocator(
            @Param("authorizationCodeId") Long authorizationCodeId);

    BoardOAuthTokenFamily selectTokenFamilyByOriginAuthorizationCodeForUpdate(
            @Param("authorizationCodeId") Long authorizationCodeId,
            @Param("clientId") String clientId);

    BoardOAuthTokenFamily selectTokenFamilyForUpdate(
            @Param("familyId") String familyId,
            @Param("clientId") String clientId);

    BoardOAuthTokenFamily selectActiveTokenFamilyForUpdate(
            @Param("familyId") String familyId,
            @Param("clientId") String clientId);

    List<BoardOAuthTokenFamily> selectLiveTokenFamiliesForUpdate(
            @Param("tenantId") Long tenantId,
            @Param("memberId") Long memberId,
            @Param("userId") Long userId,
            @Param("productCode") String productCode,
            @Param("sourceCode") String sourceCode,
            @Param("connectorCode") String connectorCode);

    BoardOAuthTokenFamily selectActiveTokenFamilySlotForUpdate(
            @Param("tenantId") Long tenantId,
            @Param("memberId") Long memberId,
            @Param("productCode") String productCode,
            @Param("sourceCode") String sourceCode,
            @Param("connectorCode") String connectorCode);

    BoardOAuthTokenFamily selectPendingTokenFamilySlotForUpdate(
            @Param("tenantId") Long tenantId,
            @Param("memberId") Long memberId,
            @Param("productCode") String productCode,
            @Param("sourceCode") String sourceCode,
            @Param("connectorCode") String connectorCode);

    int activateTokenFamilyIfVersion(
            @Param("family") BoardOAuthTokenFamily family,
            @Param("expectedVersion") Long expectedVersion);

    int advanceTokenFamilyGenerationIfVersion(
            @Param("familyId") String familyId,
            @Param("expectedVersion") Long expectedVersion,
            @Param("expectedGeneration") Long expectedGeneration);

    int advanceTokenFamilyGenerationAtIfVersion(
            @Param("familyId") String familyId,
            @Param("clientId") String clientId,
            @Param("bindingId") String bindingId,
            @Param("bindingVersion") Long bindingVersion,
            @Param("expectedVersion") Long expectedVersion,
            @Param("expectedGeneration") Long expectedGeneration,
            @Param("rotatedAt") Date rotatedAt);

    int terminateTokenFamilyIfVersion(
            @Param("familyId") String familyId,
            @Param("expectedVersion") Long expectedVersion,
            @Param("targetStatus") String targetStatus,
            @Param("terminatedAt") Date terminatedAt);

    int revokeTokenFamilyIfVersion(
            @Param("familyId") String familyId,
            @Param("clientId") String clientId,
            @Param("expectedVersion") Long expectedVersion,
            @Param("revokedAt") Date revokedAt);

    int compromiseTokenFamilyIfVersion(
            @Param("familyId") String familyId,
            @Param("clientId") String clientId,
            @Param("expectedVersion") Long expectedVersion,
            @Param("compromisedAt") Date compromisedAt);

    int expireTokenFamilyIfVersion(
            @Param("familyId") String familyId,
            @Param("clientId") String clientId,
            @Param("expectedVersion") Long expectedVersion,
            @Param("expiredAt") Date expiredAt);

    int insertToken(BoardOAuthToken token);

    BoardOAuthToken selectTokenLocatorByDigest(@Param("tokenDigest") byte[] tokenDigest);

    TokenContextLocator selectTokenContextLocatorByDigest(
            @Param("tokenDigest") byte[] tokenDigest);

    BoardOAuthToken selectTokenForUpdate(
            @Param("familyId") String familyId,
            @Param("tokenDigest") byte[] tokenDigest);

    BoardOAuthToken selectTokenByIdAndDigestForUpdate(
            @Param("tokenId") Long tokenId,
            @Param("familyId") String familyId,
            @Param("tokenDigest") byte[] tokenDigest);

    List<BoardOAuthToken> selectFamilyTokensForUpdate(@Param("familyId") String familyId);

    List<BoardOAuthToken> selectActiveFamilyTokensForClientForUpdate(
            @Param("familyId") String familyId,
            @Param("clientId") String clientId);

    int useRefreshTokenIfVersion(
            @Param("tokenId") Long tokenId,
            @Param("familyId") String familyId,
            @Param("expectedVersion") Long expectedVersion,
            @Param("usedAt") Date usedAt);

    int useRefreshTokenForGenerationIfVersion(
            @Param("tokenId") Long tokenId,
            @Param("familyId") String familyId,
            @Param("expectedVersion") Long expectedVersion,
            @Param("expectedGeneration") Long expectedGeneration,
            @Param("usedAt") Date usedAt);

    int revokeActiveFamilyTokens(
            @Param("familyId") String familyId,
            @Param("revokedAt") Date revokedAt);

    int revokeActiveFamilyTokensAtLogicalTime(
            @Param("familyId") String familyId,
            @Param("revokedAt") Date revokedAt);

    int insertReceipt(BoardOAuthReceipt receipt);

    BoardOAuthReceipt selectReceiptByReceiptId(@Param("receiptId") String receiptId);

    List<BoardOAuthReceipt> selectTokenFamilyCreatedReceiptCandidatesForUpdate(
            @Param("familyId") String familyId,
            @Param("clientId") String clientId,
            @Param("authorizationCodeId") Long authorizationCodeId);

    List<BoardOAuthReceipt> selectAuthorizationCodeReplayReceiptCandidatesForUpdate(
            @Param("familyId") String familyId,
            @Param("clientId") String clientId,
            @Param("authorizationCodeId") Long authorizationCodeId);

    List<BoardOAuthReceipt> selectTokenFamilyCompromisedReceiptCandidatesForUpdate(
            @Param("familyId") String familyId,
            @Param("clientId") String clientId);

    List<BoardOAuthReceipt> selectTokenFamilyRevokedReceiptCandidatesForUpdate(
            @Param("familyId") String familyId,
            @Param("clientId") String clientId);

    /**
     * Non-authoritative lookup data used only to discover the canonical lock keys.
     * Callers must lock and re-read every referenced row before making a decision.
     */
    @Getter
    @Setter
    class TokenContextLocator {
        private Long tokenId;
        private String familyId;
        private String tokenType;
        private Long tokenGeneration;
        private String tokenStatus;
        private Long tokenVersion;
        private Date tokenIssuedAt;
        private Date tokenExpiresAt;
        private String clientId;
        private Long tenantId;
        private Long memberId;
        private Long userId;
        private String productCode;
        private String sourceCode;
        private String connectorCode;
        private String issuerUri;
        private String resourceUri;
        private String scopeCanonical;
        private byte[] scopeDigest;
        private byte[] principalSubjectDigest;
        private BoardOAuthConsentIntent consentIntent;
        private String bindingId;
        private Long bindingVersion;
        private String familyStatus;
        private Long currentRefreshGeneration;
        private Long familyVersion;
        private Date familyIssuedAt;
        private Date familyActivatedAt;
        private Date familyExpiresAt;
    }

    /**
     * Lean, non-authoritative token-exchange locator. It contains lock keys only;
     * no raw code, PKCE, state, token, receipt, or consent payload is projected.
     */
    @Getter
    @Setter
    class TokenExchangeLockLocator {
        private Long codeId;
        private Long authorizationRequestId;
        private Long tenantId;
        private Long memberId;
        private Long userId;
        private String productCode;
        private String sourceCode;
        private String connectorCode;
        private String clientId;
        private String pendingClientId;
    }
}
