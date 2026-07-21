package com.wx.fbsir.business.board.portal.persistence;

import java.util.Date;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BoardPortalOAuthFamilyRow {
    private Long rowId;
    private String familyRef;
    private String clientRef;
    private Long tenantId;
    private String tenantName;
    private Long memberId;
    private Long userId;
    private String productCode;
    private String sourceCode;
    private String connectorCode;
    private String issuerUri;
    private String resourceUri;
    private String scopeCanonical;
    private byte[] scopeDigest;
    private String consentIntent;
    private String bindingRef;
    private Long bindingVersion;
    private String storedStatus;
    private String effectiveStatus;
    private Long currentRefreshGeneration;
    private Date issuedAt;
    private Date activatedAt;
    private Date expiresAt;
    private Date effectiveTerminatedAt;
    private Long version;
    private Boolean pendingActivationProven;

    private String clientDisplayName;
    private String clientStatus;
    private Date clientRegisteredAt;
    private Date clientExpiresAt;
    private Date clientTerminatedAt;
    private String clientIssuerUri;
    private String clientResourceUri;
    private String clientProductCode;
    private String clientSourceCode;
    private String clientConnectorCode;
    private String clientScopeCanonical;
    private byte[] clientScopeDigest;
    private String clientRedirectUri;
    private String clientTokenEndpointAuthMethod;
    private String clientGrantTypesCanonical;
    private String clientResponseTypesCanonical;

    private Long currentBindingTenantId;
    private Long currentBindingMemberId;
    private Long currentBindingUserId;
    private String currentBindingClientRef;
    private String currentBindingStatus;
    private Long currentBindingVersion;
    private Date currentBindingValidUntil;
}
