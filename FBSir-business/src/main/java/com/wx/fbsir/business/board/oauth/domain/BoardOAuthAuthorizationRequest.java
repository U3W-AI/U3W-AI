package com.wx.fbsir.business.board.oauth.domain;

import com.wx.fbsir.business.board.oauth.BoardOAuthConsentIntent;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BoardOAuthAuthorizationRequest {
    private Long id;
    private byte[] requestHandleDigest;
    private String clientId;
    private String redirectUri;
    private String codeChallenge;
    private String codeChallengeMethod;
    private byte[] stateDigest;
    private String stateKeyRef;
    private byte[] stateNonce;
    private byte[] stateCiphertext;
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
    private Date requestedAt;
    private Date expiresAt;
    private Date approvedAt;
    private Date deniedAt;
    private Date consumedAt;
    private Long version;
    private Date createdAt;
    private Date updatedAt;
}
