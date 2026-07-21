package com.wx.fbsir.business.board.oauth.domain;

import com.wx.fbsir.business.board.oauth.BoardOAuthConsentIntent;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BoardOAuthAuthorizationCode {
    private Long id;
    private byte[] codeDigest;
    private Long authorizationRequestId;
    private String clientId;
    private String redirectUri;
    private String codeChallenge;
    private String codeChallengeMethod;
    private String issuerUri;
    private String resourceUri;
    private String productCode;
    private String sourceCode;
    private String connectorCode;
    private String scopeCanonical;
    private byte[] scopeDigest;
    private Long tenantId;
    private Long memberId;
    private Long userId;
    private byte[] principalSubjectDigest;
    private BoardOAuthConsentIntent consentIntent;
    private String status;
    private Date issuedAt;
    private Date expiresAt;
    private Date usedAt;
    private Date revokedAt;
    private Long version;
    private Date createdAt;
    private Date updatedAt;
}
