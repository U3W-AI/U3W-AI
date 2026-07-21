package com.wx.fbsir.business.board.portal.persistence;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BoardPortalConnectorBindingRow {
    private Long rowId;
    private String bindingRef;
    private Long tenantId;
    private String tenantName;
    private Long memberId;
    private Long userId;
    private String productCode;
    private String sourceCode;
    private String connectorCode;
    private String issuerUri;
    private String resourceUri;
    private String clientRef;
    private String principalSubjectDigest;
    private String status;
    private String verificationMethod;
    private Date verifiedAt;
    private Date lastSeenAt;
    private Date validUntil;
    private Date revokedAt;
    private Long version;
    private Boolean entitlementActive;
    private Boolean familyActive;
    private List<String> scopes = new ArrayList<>();

    public void setScopes(List<String> scopes) {
        this.scopes = scopes == null ? new ArrayList<>() : new ArrayList<>(scopes);
    }
}
