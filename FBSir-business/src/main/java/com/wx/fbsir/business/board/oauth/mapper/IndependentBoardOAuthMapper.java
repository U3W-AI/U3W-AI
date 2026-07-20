package com.wx.fbsir.business.board.oauth.mapper;

import com.wx.fbsir.business.board.oauth.domain.BoardOAuthAuthorizationCode;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthAuthorizationRequest;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthClient;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthReceipt;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthToken;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthTokenFamily;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface IndependentBoardOAuthMapper {
    int insertClient(BoardOAuthClient client);

    BoardOAuthClient selectClientForUpdate(@Param("clientId") String clientId);

    int terminateClientIfVersion(
            @Param("clientId") String clientId,
            @Param("expectedVersion") Long expectedVersion,
            @Param("targetStatus") String targetStatus,
            @Param("terminatedAt") Date terminatedAt);

    int insertAuthorizationRequest(BoardOAuthAuthorizationRequest request);

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

    int insertAuthorizationCode(BoardOAuthAuthorizationCode code);

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

    int insertTokenFamily(BoardOAuthTokenFamily family);

    BoardOAuthTokenFamily selectTokenFamilyForUpdate(
            @Param("familyId") String familyId,
            @Param("clientId") String clientId);

    List<BoardOAuthTokenFamily> selectLiveTokenFamiliesForUpdate(
            @Param("tenantId") Long tenantId,
            @Param("memberId") Long memberId,
            @Param("userId") Long userId,
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

    int terminateTokenFamilyIfVersion(
            @Param("familyId") String familyId,
            @Param("expectedVersion") Long expectedVersion,
            @Param("targetStatus") String targetStatus,
            @Param("terminatedAt") Date terminatedAt);

    int insertToken(BoardOAuthToken token);

    BoardOAuthToken selectTokenLocatorByDigest(@Param("tokenDigest") byte[] tokenDigest);

    BoardOAuthToken selectTokenForUpdate(
            @Param("familyId") String familyId,
            @Param("tokenDigest") byte[] tokenDigest);

    List<BoardOAuthToken> selectFamilyTokensForUpdate(@Param("familyId") String familyId);

    int useRefreshTokenIfVersion(
            @Param("tokenId") Long tokenId,
            @Param("familyId") String familyId,
            @Param("expectedVersion") Long expectedVersion,
            @Param("usedAt") Date usedAt);

    int revokeActiveFamilyTokens(
            @Param("familyId") String familyId,
            @Param("revokedAt") Date revokedAt);

    int insertReceipt(BoardOAuthReceipt receipt);
}
