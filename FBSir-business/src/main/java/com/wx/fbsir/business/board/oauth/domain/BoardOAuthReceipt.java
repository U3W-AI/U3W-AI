package com.wx.fbsir.business.board.oauth.domain;

import java.util.Date;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BoardOAuthReceipt {
    private Long id;
    private String receiptId;
    private String action;
    /**
     * Receipt schema version. Existing actions remain v1 unless an action-specific
     * factory explicitly opts into the v2 security-event contract.
     */
    private Integer receiptFormatVersion = 1;
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
    private Long subjectGeneration;
    private Long resultGeneration;
    private String causationReceiptId;
    private byte[] beforeStateDigest;
    private byte[] afterStateDigest;

    /** Database-generated projection; never accepted on INSERT. */
    @Setter(AccessLevel.NONE)
    private String subjectTokenType;

    /** Database-generated uniqueness slot; never accepted on INSERT. */
    @Setter(AccessLevel.NONE)
    private String securityEventSlot;
    private Date createdAt;
}
