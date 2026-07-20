package com.wx.fbsir.business.board.oauth.domain;

import java.util.Date;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BoardOAuthClient {
    private Long id;
    private String clientId;
    private String clientName;
    private String issuerUri;
    private String resourceUri;
    private String productCode;
    private String sourceCode;
    private String connectorCode;
    private Integer redirectPort;
    private String redirectUri;
    private String tokenEndpointAuthMethod;
    private String grantTypesCanonical;
    private String responseTypesCanonical;
    private String scopeCanonical;
    private byte[] scopeDigest;
    private byte[] metadataDigest;
    private byte[] registrationSourceDigest;
    private String status;
    private Date registeredAt;
    private Date expiresAt;
    private Date terminatedAt;
    private Long version;
    private Date createdAt;
    private Date updatedAt;
}
