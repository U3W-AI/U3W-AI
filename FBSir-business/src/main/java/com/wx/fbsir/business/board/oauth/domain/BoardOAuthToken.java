package com.wx.fbsir.business.board.oauth.domain;

import java.util.Date;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BoardOAuthToken {
    private Long id;
    private byte[] tokenDigest;
    private String familyId;
    private String tokenType;
    private Long generation;
    private String resourceUri;
    private String scopeCanonical;
    private byte[] scopeDigest;
    private String status;
    private Integer activeRefreshSlot;
    private Date issuedAt;
    private Date usedAt;
    private Date revokedAt;
    private Date expiresAt;
    private Long version;
    private Date createdAt;
    private Date updatedAt;
}
