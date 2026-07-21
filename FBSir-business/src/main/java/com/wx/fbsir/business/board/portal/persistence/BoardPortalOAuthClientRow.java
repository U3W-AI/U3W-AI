package com.wx.fbsir.business.board.portal.persistence;

import java.util.Date;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BoardPortalOAuthClientRow {
    private Long rowId;
    private String clientRef;
    private String displayName;
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
    private String storedStatus;
    private String effectiveStatus;
    private Date registeredAt;
    private Date expiresAt;
    private Date effectiveTerminatedAt;
    private Long version;
}
