package com.wx.fbsir.business.board.oauth.domain;

import java.util.Date;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BoardOAuthTokenFamily {
    private Long id;
    private String familyId;
    private Long originAuthorizationCodeId;
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
    private String bindingId;
    private Long bindingVersion;
    private String status;
    private String lifecycleSlot;
    private Long currentRefreshGeneration;
    private Date issuedAt;
    private Date activatedAt;
    private Date expiresAt;
    private Date terminatedAt;
    private Long version;
    private Date createdAt;
    private Date updatedAt;
}
