package com.wx.fbsir.business.board.oauth.domain;

import java.util.Date;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BoardOAuthReceipt {
    private Long id;
    private String receiptId;
    private String action;
    private String clientId;
    private Long authorizationRequestId;
    private Long authorizationCodeId;
    private String familyId;
    private Long tokenId;
    private String bindingId;
    private Long tenantId;
    private Long memberId;
    private Long userId;
    private byte[] principalSubjectDigest;
    private String actorType;
    private Long actorUserId;
    private byte[] actorSubjectDigest;
    private String correlationId;
    private byte[] payloadDigest;
    private String evidenceLevel;
    private Date createdAt;
}
